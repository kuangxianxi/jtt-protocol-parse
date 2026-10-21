package com.xx.jttprotocolparse.core.codec;

import com.xx.jttprotocolparse.core.hex.HexUtil;
import com.xx.jttprotocolparse.core.model.Expr;
import com.xx.jttprotocolparse.core.model.FieldSpec;
import com.xx.jttprotocolparse.core.model.SegmentSpec;
import com.xx.jttprotocolparse.core.result.Issue;
import com.xx.jttprotocolparse.core.result.IssueCode;
import com.xx.jttprotocolparse.core.result.Node;

import java.math.BigDecimal;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * 字段解码器库。类型清单收敛于设计方案 §5.3：
 * u1/u2/u3/u4（无符号）、bcd-time、dec-time、ascii/gbk、raw、group(循环块) +
 * scale/offset/unit/map/bits/segments/invalid/length("$label"/"rest")。
 * 字段没有 id：节点标识、issue path、$ 变量引用统一使用 label。
 * 解码不了的问题一律进 issues 并定位 path，不中断整体解析。
 */
public final class FieldCodec {

    private FieldCodec() {
    }

    /** 解析一段字段列表，节点挂到 parent 下 */
    public static void parseFields(List<FieldSpec> fields, Cursor cur, Node parent, List<Issue> issues, String pathPrefix) {
        for (FieldSpec f : fields) {
            String path = pathPrefix.isEmpty() ? key(f) : pathPrefix + "." + key(f);
            parseOne(f, cur, parent, issues, path);
        }
    }

    /** 解析单个字段并把节点挂到 parent；返回该节点，截断/越界时返回 null（问题已记入 issues） */
    public static Node parseOne(FieldSpec f, Cursor cur, Node parent, List<Issue> issues, String path) {
        if ("group".equals(f.getType())) {
            parseGroup(f, cur, parent, issues, path);
            return null;
        }
        int len = resolveLength(f, cur, issues, path);
        if (len < 0 || len > cur.remaining()) {
            issues.add(Issue.of(path, IssueCode.BODY_TRUNCATED,
                    f.getLabel() + " 需要 " + (len < 0 ? "定长" : len) + " 字节，仅剩 " + cur.remaining() + " 字节"));
            return null;
        }
        byte[] raw = new byte[len];
        System.arraycopy(cur.getBuf(), cur.getPos(), raw, 0, len);
        Node n = decode(f, raw, cur, issues, path);
        n.setRaw(HexUtil.encode(raw));
        if (parent != null) {
            parent.add(n);
        }
        cur.setPos(cur.getPos() + len);
        return n;
    }

    /** 长度来源：整型/定长数值、"rest"、"$expr"；未知返回 -1 */
    private static int resolveLength(FieldSpec f, Cursor cur, List<Issue> issues, String path) {
        switch (f.getType()) {
            case "u1", "s1" -> { return 1; }
            case "u2", "s2" -> { return 2; }
            case "u3" -> { return 3; }
            case "u4" -> { return 4; }
            case "bcd-time", "dec-time" -> { return 6; }
            default -> { }
        }
        Object l = f.getLength();
        if (l == null) {
            return "raw".equals(f.getType()) ? cur.remaining() : -1;
        }
        if (l instanceof Number num) {
            return num.intValue();
        }
        String s = String.valueOf(l).trim();
        if ("rest".equalsIgnoreCase(s)) {
            return cur.remaining();
        }
        Double v = Expr.eval(s, cur.getVars());
        if (v == null || v.isNaN()) {
            issues.add(Issue.of(path, IssueCode.BAD_EXPR, "长度算式无法解析: " + s));
            return cur.remaining();
        }
        return (int) Math.round(v);
    }

    /** 单字段解码（raw 字节已按长度切出） */
    private static Node decode(FieldSpec f, byte[] raw, Cursor cur, List<Issue> issues, String path) {
        Node n = new Node(f.getLabel());
        switch (f.getType()) {
            case "u1", "u2", "u3", "u4", "s1", "s2" -> decodeNumber(f, raw, n, issues, path);
            case "bcd-time" -> decodeBcdTime(raw, n);
            case "dec-time" -> decodeDecTime(raw, n);
            case "ascii" -> n.setValue(new String(raw, StandardCharsets.US_ASCII).trim());
            case "gbk" -> n.setValue(new String(raw, Charset.forName("GBK")).trim());
            default -> n.setValue(HexUtil.encode(raw));   // raw 及未识别类型一律原样保留
        }
        // 数值型把原值存入 vars 供后续 $label 长度/次数引用
        if ("u1".equals(f.getType()) || "u2".equals(f.getType()) || "u3".equals(f.getType()) || "u4".equals(f.getType())) {
            cur.getVars().put(key(f), unsigned(raw));
        } else if ("s1".equals(f.getType()) || "s2".equals(f.getType())) {
            cur.getVars().put(key(f), signed(raw));
        }
        return n;
    }

    private static void decodeNumber(FieldSpec f, byte[] raw, Node n, List<Issue> issues, String path) {
        long u = unsigned(raw);
        long signed = "s1".equals(f.getType()) || "s2".equals(f.getType()) ? signed(raw) : u;

        // 特殊值：命中显示中文，未命中落回正常解码
        String special = firstMatch(f.getInvalid(), u, issues, path, "特殊值键无法解析");
        if (special != null) {
            n.setValue(special);
            return;
        }
        // 位表
        if (f.getBits() != null && !f.getBits().isEmpty()) {
            n.setValue(bitsLabel(f.getBits(), u, raw.length));
            return;
        }
        // 位段：有 label 的段输出为子节点行，无 label 的段拼接进节点值
        if (f.getSegments() != null && !f.getSegments().isEmpty()) {
            applySegments(f.getSegments(), u, n);
            return;
        }
        // 枚举映射（按原值）；未命中直接显示原值
        if (f.getMap() != null && !f.getMap().isEmpty()) {
            String hit = firstMatch(f.getMap(), u, issues, path, "枚举键无法解析");
            n.setValue(hit != null ? hit : String.format("0x%02X", u));
            return;
        }
        // 数值：offset 后乘 scale；用 BigDecimal 做精确十进制运算，杜绝 double 二进制尾差（如 3284×0.001 → 3.2840000000000003）
        n.setValue(BigDecimal.valueOf(signed)
                .subtract(BigDecimal.valueOf(f.getOffset()))
                .multiply(BigDecimal.valueOf(f.getScale()))
                .stripTrailingZeros()
                .toPlainString());
    }

    private static void decodeBcdTime(byte[] raw, Node n) {
        int[] digits = new int[raw.length * 2];
        boolean bad = false;
        for (int i = 0; i < raw.length; i++) {
            int b = raw[i] & 0xFF;
            digits[i * 2] = b >> 4;
            digits[i * 2 + 1] = b & 0x0F;
            bad |= digits[i * 2] > 9 || digits[i * 2 + 1] > 9;
        }
        if (bad) {
            n.setValue(HexUtil.encode(raw));
            n.setNote(append(n.getNote(), "非 BCD 数据"));
            return;
        }
        if (raw.length == 6) {
            // 每字节即两位 BCD：26 → "26" 年
            n.setValue(String.format("20%02d-%02d-%02d %02d:%02d:%02d",
                    digits[0] * 10 + digits[1], digits[2] * 10 + digits[3], digits[4] * 10 + digits[5],
                    digits[6] * 10 + digits[7], digits[8] * 10 + digits[9], digits[10] * 10 + digits[11]));
        } else {
            StringBuilder sb = new StringBuilder();
            for (int d : digits) {
                sb.append(d);
            }
            n.setValue(sb.toString());
        }
    }

    /** 十进制数值时间：每字节一个数值（年 月 日 时 分 秒），年按 20xx 补全 */
    private static void decodeDecTime(byte[] raw, Node n) {
        n.setValue(String.format("%04d-%02d-%02d %02d:%02d:%02d",
                2000 + (raw[0] & 0xFF), raw[1] & 0xFF, raw[2] & 0xFF,
                raw[3] & 0xFF, raw[4] & 0xFF, raw[5] & 0xFF));
    }

    private static void parseGroup(FieldSpec f, Cursor cur, Node parent, List<Issue> issues, String path) {
        Double c = Expr.eval(f.getCount(), cur.getVars());
        if (c == null || c.isNaN()) {
            issues.add(Issue.of(path, IssueCode.BAD_EXPR, "循环次数算式无法解析: " + f.getCount()));
            return;
        }
        for (int i = 0; i < c; i++) {
            Node item = new Node(f.getLabel() + " " + (i + 1));
            parent.add(item);
            parseFields(f.getFields(), cur, item, issues, path + "[" + i + "]");
        }
    }

    // ---- 小工具 ----

    /** 字段没有 id：节点标识 / path / $ 变量统一用 label */
    private static String key(FieldSpec f) {
        return f.getLabel() != null ? f.getLabel() : "field";
    }

    /** 「原值 → 中文」表中第一个命中的映射；键非法记问题后跳过 */
    private static String firstMatch(Map<String, String> table, long u, List<Issue> issues, String path, String badKey) {
        if (table == null) {
            return null;
        }
        for (Map.Entry<String, String> e : table.entrySet()) {
            try {
                if (u == Long.decode(e.getKey().trim())) {
                    return e.getValue();
                }
            } catch (NumberFormatException ignore) {
                issues.add(Issue.of(path, IssueCode.BAD_MAP_KEY, badKey + ": " + e.getKey()));
            }
        }
        return null;
    }

    private static long unsigned(byte[] raw) {
        long v = 0;
        for (byte b : raw) {
            v = v << 8 | b & 0xFF;
        }
        return v;
    }

    private static long signed(byte[] raw) {
        long v = unsigned(raw);
        int shift = 64 - raw.length * 8;
        return v << shift >> shift;
    }

    private static String bitsLabel(Map<String, String> bits, long u, int bytes) {
        // 按位号（低 0 位起）取标签，输出置位的位
        Map<Integer, String> ordered = new TreeMap<>();
        bits.forEach((k, v) -> ordered.put(Integer.parseInt(k.trim()), v));
        List<String> on = new ArrayList<>();
        int width = bytes * 8;
        for (Map.Entry<Integer, String> e : ordered.entrySet()) {
            int bit = e.getKey();
            if (bit < width && ((u >> bit) & 1) == 1) {
                on.add(String.format("bit%d %s", bit, e.getValue()));
            }
        }
        return on.isEmpty() ? "无置位" : String.join("、", on);
    }

    /** 位段映射：有 label 的段输出为子节点行（label: 值），无 label 的段拼接进节点值；未命中显示该段十进制原值 */
    private static void applySegments(List<SegmentSpec> segments, long u, Node n) {
        List<String> inline = new ArrayList<>();
        for (SegmentSpec s : segments) {
            int value = (int) ((u >> s.getStart()) & ((1L << s.getWidth()) - 1));
            String hit = s.getValues() == null ? null : s.getValues().get(String.valueOf(value));
            String text = hit != null ? hit : String.valueOf(value);
            if (s.getLabel() == null) {
                inline.add(text);
            } else {
                n.add(new Node(s.getLabel()).setValue(text));
            }
        }
        if (!inline.isEmpty()) {
            n.setValue(String.join("；", inline));
        }
    }

    private static String append(String a, String b) {
        return a == null ? b : a + "；" + b;
    }
}
