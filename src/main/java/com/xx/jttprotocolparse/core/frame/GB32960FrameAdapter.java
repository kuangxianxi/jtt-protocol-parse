package com.xx.jttprotocolparse.core.frame;

import com.xx.jttprotocolparse.core.hex.HexUtil;
import com.xx.jttprotocolparse.core.result.Issue;
import com.xx.jttprotocolparse.core.result.IssueCode;
import com.xx.jttprotocolparse.core.result.Node;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * GB/T 32960.3 帧层。
 *
 * <pre>
 * ## (2B) | 命令单元(1B) | 应答标志(1B) | VIN(17B) | 数据单元加密方式(1B)
 *         | 数据单元长度(2B 大端) | 数据单元(N) | 校验码(1B，命令单元至数据单元各字节异或)
 * </pre>
 *
 * 服务国标与全部厂商扩展（共用这层拆包，不因 vendor / version 分叉）。
 */
public final class GB32960FrameAdapter implements FrameAdapter {

    /** 起始符之后、数据单元之前的固定区长度：命令1 + 应答1 + VIN17 + 加密1 + 长度2 = 22 */
    private static final int HEADER_LEN = 22;
    /** 报文头命令单元节点的 label；引擎补中文名时用此常量定位 */
    public static final String COMMAND_LABEL = "命令单元";
    /** 协议 id（与 protocols.yaml 登记一致） */
    public static final String PROTOCOL_ID = "gc-32960";
    private static final int START_1 = 0x23;
    private static final int START_2 = 0x23;

    private static final Map<Integer, String> ACK = Map.of(
            0xFE, "命令",
            0x01, "成功",
            0x02, "失败",
            0x03, "异常");
    private static final Map<Integer, String> ENC = Map.of(
            0x01, "未加密",
            0x02, "RSA",
            0x03, "AES128");

    @Override
    public String protocolId() {
        return PROTOCOL_ID;
    }

    @Override
    public Frame parse(byte[] m, List<Issue> issues) {
        if (m.length < HEADER_LEN + 3 || m[0] != START_1 || m[1] != START_2) {
            issues.add(Issue.of("frame", IssueCode.FRAME_NOT_MATCH,
                    "未识别为 GB/T 32960 报文：应以 ## (0x23 0x23) 开头且总长 ≥ " + (HEADER_LEN + 3) + " 字节"));
            return null;
        }
        int command = m[2] & 0xFF;
        int ack = m[3] & 0xFF;
        String vin = vin(m);
        int enc = m[21] & 0xFF;
        int declaredLen = (m[22] & 0xFF) << 8 | m[23] & 0xFF;
        int expectedTotal = HEADER_LEN + declaredLen + 3;
        boolean lengthOk = m.length == expectedTotal;
        if (!lengthOk) {
            issues.add(Issue.of("payloadLen", IssueCode.LENGTH_MISMATCH,
                    "报文总长应为 " + expectedTotal + " 字节（数据单元长度声明 " + declaredLen + "），实际 " + m.length + " 字节"));
        }
        // 数据单元按声明长度切片；越界则截到校验码前，尽力解析
        int payloadLen = Math.max(Math.min(declaredLen, m.length - HEADER_LEN - 3), 0);
        byte[] payload = new byte[payloadLen];
        System.arraycopy(m, HEADER_LEN + 2, payload, 0, payloadLen);

        boolean checksumOk = false;
        if (lengthOk) {
            byte expect = bcc(m, m.length - 1);
            checksumOk = expect == m[m.length - 1];
            if (!checksumOk) {
                issues.add(Issue.of("checksum", IssueCode.CHECKSUM_MISMATCH, String.format(
                        "校验码不符：应为 0x%02X，收到 0x%02X", expect, m[m.length - 1])));
            }
        }
        return new Frame(headerNodes(m, command, ack, vin, enc, declaredLen, checksumOk),
                trailerNodes(m, checksumOk),
                command, ack, vin, enc, declaredLen, payload, checksumOk);
    }

    /** BCC：从命令单元（index 2）到校验码位置前逐字节异或 */
    static byte bcc(byte[] m, int toExclusive) {
        int x = 0;
        for (int i = 2; i < toExclusive; i++) {
            x ^= m[i] & 0xFF;
        }
        return (byte) x;
    }

    private static String vin(byte[] m) {
        byte[] v = new byte[17];
        System.arraycopy(m, 4, v, 0, 17);
        int end = 17;
        while (end > 0 && (v[end - 1] == 0 || v[end - 1] == ' ')) {
            end--;   // 不足 17 位尾部补 0x00/空格
        }
        String s = new String(v, 0, end, StandardCharsets.US_ASCII);
        for (char c : s.toCharArray()) {
            if (c < 0x20 || c > 0x7E) {
                return "";
            }
        }
        return s;
    }

    private static List<Node> headerNodes(byte[] m, int command, int ack, String vin, int enc, int declaredLen, boolean checksumOk) {
        List<Node> out = new ArrayList<>();
        out.add(new Node("起始符")
                .setValue("##")
                .setRaw(HexUtil.slice(m, 0, 2)));
        out.add(new Node(COMMAND_LABEL)
                .setValue(String.format("0x%02X", command))
                .setRaw(HexUtil.slice(m, 2, 1)));
        out.add(new Node("应答标志")
                .setValue(ACK.getOrDefault(ack, String.format("未知应答(0x%02X)", ack)))
                .setRaw(HexUtil.slice(m, 3, 1)));
        out.add(new Node("VIN")
                .setValue(vin.isEmpty() ? HexUtil.slice(m, 4, 17) : vin)
                .setRaw(HexUtil.slice(m, 4, 17))
                .setNote(vin.isEmpty() ? "非可读 ASCII" : null));
        out.add(new Node("数据单元加密方式")
                .setValue(ENC.getOrDefault(enc, String.format("未知加密(0x%02X)", enc)))
                .setRaw(HexUtil.slice(m, 21, 1))
                .setNote(enc != 0x01 && ENC.containsKey(enc) ? "已加密，明文解析不可用" : null));
        out.add(new Node("数据单元长度")
                .setValue(String.valueOf(declaredLen))
                .setRaw(HexUtil.slice(m, 22, 2))
                .setCheck(checksumOk ? "与实际一致" : "与实际不符"));
        return out;
    }

    /** 数据单元之后的收尾展示节点：校验码（BCC） */
    private static List<Node> trailerNodes(byte[] m, boolean checksumOk) {
        return List.of(new Node("校验码(BCC)")
                .setValue(String.format("0x%02X", m[m.length - 1] & 0xFF))
                .setRaw(HexUtil.slice(m, m.length - 1, 1))
                .setCheck(checksumOk ? "校验通过" : "校验不符"));
    }
}
