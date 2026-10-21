package com.xx.jttprotocolparse.core.engine;

import com.xx.jttprotocolparse.core.codec.Cursor;
import com.xx.jttprotocolparse.core.codec.FieldCodec;
import com.xx.jttprotocolparse.core.descr.DescriptorRegistry;
import com.xx.jttprotocolparse.core.frame.Frame;
import com.xx.jttprotocolparse.core.frame.FrameAdapter;
import com.xx.jttprotocolparse.core.frame.GB32960FrameAdapter;
import com.xx.jttprotocolparse.core.hex.HexUtil;
import com.xx.jttprotocolparse.core.model.CommandSpec;
import com.xx.jttprotocolparse.core.model.FieldSpec;
import com.xx.jttprotocolparse.core.model.ProtocolSpec;
import com.xx.jttprotocolparse.core.model.SubTypeSpec;
import com.xx.jttprotocolparse.core.result.Issue;
import com.xx.jttprotocolparse.core.result.IssueCode;
import com.xx.jttprotocolparse.core.result.Node;
import com.xx.jttprotocolparse.core.result.ParseResult;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 解析引擎（无状态）：hex → 拆帧 → 一级路由(命令字) → 二级路由(子报文类型) → 字段解析 → 结果树。
 * 任何问题都进 issues 并定位位置，不抛异常、不静默。
 */
public final class ParseEngine {

    private final DescriptorRegistry registry;
    private final Map<String, FrameAdapter> adapters;

    public ParseEngine(DescriptorRegistry registry, List<FrameAdapter> adapters) {
        this.registry = registry;
        this.adapters = adapters.stream().collect(java.util.stream.Collectors.toMap(FrameAdapter::protocolId, a -> a));
    }

    /** 支持一次粘贴多条：按换行拆分（对齐 JTTools「回车区分」），逐条解析（单条失败不影响其他） */
    public List<ParseResult> parse(String protocol, String version, String vendor, String input) {
        List<ParseResult> out = new ArrayList<>();
        for (String seg : input.split("\n")) {
            if (!seg.isBlank()) {
                ParseResult r = one(protocol, version, vendor, seg.trim());
                r.setOk(r.getIssues().isEmpty());   // ok 统一在此收尾计算
                out.add(r);
            }
        }
        return out;
    }

    // ------------------------------------------------------------------ 单条

    private ParseResult one(String protocol, String version, String vendor, String hex) {
        ParseResult r = new ParseResult()
                .setProtocol(protocol)
                .setVersion(version)
                .setVendor(vendor)
                .setReport("无法解析");

        byte[] msg;
        try {
            msg = HexUtil.decode(hex);
        } catch (IllegalArgumentException e) {
            r.getIssues().add(Issue.of("hex", IssueCode.HEX_INVALID, e.getMessage()));
            r.setOk(false);
            return r;
        }
        FrameAdapter adapter = adapters.get(protocol);
        if (adapter == null) {
            r.getIssues().add(Issue.of("protocol", IssueCode.UNSUPPORTED_PROTOCOL, "暂不支持的协议: " + protocol));
            packUnknown(r, "不支持的协议，报文整体原样保留", msg);
            return r;
        }
        // 拆帧在协议描述之前：即使协议 YAML 未登记，报文头与数据单元边界也是帧层已知的
        Frame frame = adapter.parse(msg, r.getIssues());
        if (frame == null) {
            packUnknown(r, "未识别为 " + adapter.protocolId() + " 报文，报文整体原样保留", msg);
            return r;
        }
        r.getNodes().addAll(frame.header());

        ProtocolSpec d = registry.dialect(protocol, version, r.getVendor());
        if (d == null) {
            r.getIssues().add(Issue.of("protocol", IssueCode.UNSUPPORTED_PROTOCOL,
                    "协议描述缺失: " + protocol + " / " + version + (r.getVendor() == null ? "" : " / " + r.getVendor())));
            packUnknown(r, "协议描述未登记，数据单元原样保留", frame.payload());
            return addTrailer(r, frame);
        }
        r.setVersion(d.getVersion());

        CommandSpec cmd = d.getCommands().get(frame.command());
        if (cmd == null) {
            r.getIssues().add(Issue.of("command", IssueCode.UNKNOWN_COMMAND,
                    String.format("未知命令字 0x%02X，数据单元 %d 字节原样保留", frame.command(), frame.payload().length)));
            packUnknown(r, String.format("未知命令字 0x%02X", frame.command()), frame.payload());
            return addTrailer(r, frame);
        }
        r.setReport(d.prefix() + " · " + cmd.getName());
        decorateCommandNode(frame, cmd);

        if (cmd.isStream()) {
            stream(frame.payload(), cmd, r);
            return addTrailer(r, frame);
        }
        if (cmd.getBody() == null) {
            noBody(cmd, frame.payload(), r);
            return addTrailer(r, frame);
        }
        parseBody(frame.payload(), cmd.getBody(), cmd.getName(), r.getNodes(), r.getIssues(), "body");
        return addTrailer(r, frame);
    }

    /** 数据体之后的收尾节点列表（校验码）追加到输出尾，保证展示顺序 = 协议字节顺序 */
    private ParseResult addTrailer(ParseResult r, Frame frame) {
        r.getNodes().addAll(frame.trailer());
        return r;
    }

    /** 未匹配统一收口：解析到此为止，剩余部分打包为「未知报文」节点展示报文原文 */
    private void packUnknown(ParseResult r, String why, byte[] rest) {
        String hex = HexUtil.encode(rest);
        r.getNodes().add(new Node("未知报文").setValue(hex).setRaw(hex).setNote(why));
    }

    /** 报文头命令节点补上中文名（节点 label 由帧层创建，故用帧层提供的常量定位） */
    private void decorateCommandNode(Frame frame, CommandSpec cmd) {
        for (Node n : frame.header()) {
            if (GB32960FrameAdapter.COMMAND_LABEL.equals(n.getLabel())) {
                n.setValue(String.format("0x%02X %s", frame.command(), cmd.getName()));
            }
        }
    }

    /** 未配置数据体：空数据单元正常；有字节则记录问题并原样保留 */
    private void noBody(CommandSpec cmd, byte[] payload, ParseResult r) {
        if (payload.length == 0) {
            return;
        }
        r.getIssues().add(Issue.of("body", IssueCode.BODY_UNEXPECTED,
                cmd.getName() + " 未配置数据体，实际 " + payload.length + " 字节，原样保留"));
        r.getNodes().add(new Node("多余数据体").setValue(HexUtil.encode(payload)));
    }

    // ------------------------------------------------- stream 型数据体（0x02/0x03）

    /**
     * 数据体 = 可选的前置 body 字段（如采集时间） + N×(数据类型1B + 信息体)。
     * 信息体没有长度字节，长度由该类型的字段定义决定；未知/未配置类型无法切包，剩余原样保留。
     */
    private void stream(byte[] payload, CommandSpec cmd, ParseResult r) {
        Cursor cur = new Cursor(payload, 0);
        if (cmd.getBody() != null) {
            int before = r.getIssues().size();
            Node prefix = new Node();
            FieldCodec.parseFields(cmd.getBody(), cur, prefix, r.getIssues(), "body");
            if (prefix.getChildren() != null) {
                r.getNodes().addAll(prefix.getChildren());
            }
            if (r.getIssues().size() > before) {
                dumpRest(cur, r.getNodes());
                return;
            }
        }

        while (cur.remaining() > 0) {
            int start = cur.getPos();
            int type = cur.getBuf()[cur.getPos()] & 0xFF;
            cur.setPos(cur.getPos() + 1);

            SubTypeSpec st = cmd.getSubtype() == null ? null : cmd.getSubtype().get(type);
            Node node = new Node(st == null ? String.format("未知子报文 0x%02X", type) : st.getName());

            if (st == null || st.getBody() == null) {
                String why = st == null ? "未知子报文类型" : "该子报文的字段待核对(M2)";
                String rest = HexUtil.slice(payload, cur.getPos(), cur.remaining());
                r.getIssues().add(Issue.of("body", IssueCode.UNKNOWN_SUBTYPE, String.format(
                        "子报文 0x%02X %s，无法确定长度，剩余 %d 字节原样保留", type, why, cur.remaining())));
                node.setValue(rest).setRaw(HexUtil.slice(payload, cur.getPos(), cur.remaining()))
                        .setNote(why + "，剩余字节原样保留");
                r.getNodes().add(node);
                return;
            }

            int before = r.getIssues().size();
            FieldCodec.parseFields(st.getBody(), cur, node, r.getIssues(), "body");
            int consumed = cur.getPos() - start;
            node.setRaw(HexUtil.slice(payload, start, consumed));
            r.getNodes().add(node);
            if (consumed <= 1) {
                r.getIssues().add(Issue.of("body", IssueCode.UNKNOWN_SUBTYPE, String.format(
                        "子报文 0x%02X 未消费任何字节，剩余 %d 字节原样保留", type, cur.remaining())));
                dumpRest(cur, r.getNodes());
                return;
            }
            if (r.getIssues().size() > before) {
                dumpRest(cur, r.getNodes());
                return;
            }
        }
    }

    // ------------------------------------------------------------------ 普通数据体

    private void parseBody(byte[] payload, List<FieldSpec> fields, String label, List<Node> sink,
                           List<Issue> issues, String pathPrefix) {
        Node section = new Node(label);
        Cursor cur = new Cursor(payload, 0);
        FieldCodec.parseFields(fields, cur, section, issues, pathPrefix);
        if (cur.remaining() > 0) {
            issues.add(Issue.of("body", IssueCode.BODY_LEFTOVER,
                    "解析后剩余 " + cur.remaining() + " 字节（字段可能与标准不符或含扩展）"));
            section.add(restNode(cur));
        }
        sink.add(section);
    }

    private Node restNode(Cursor cur) {
        return new Node("剩余部分(待核对)")
                .setValue(HexUtil.slice(cur.getBuf(), cur.getPos(), cur.remaining()))
                .setNote("TODO 待标准核对");
    }

    private void dumpRest(Cursor cur, List<Node> sink) {
        if (cur.remaining() > 0) {
            sink.add(new Node("剩余部分")
                    .setValue(HexUtil.slice(cur.getBuf(), cur.getPos(), cur.remaining()))
                    .setRaw(HexUtil.slice(cur.getBuf(), cur.getPos(), cur.remaining())));
        }
    }
}
