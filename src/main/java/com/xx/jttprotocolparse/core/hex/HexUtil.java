package com.xx.jttprotocolparse.core.hex;

/** hex 文本 <-> 字节 的归一化工具 */
public final class HexUtil {

    private HexUtil() {
    }

    /** 去掉空白后按字节解析，非法输入抛 IllegalArgumentException(code=HEX_INVALID 用) */
    public static byte[] decode(String text) {
        String s = text == null ? "" : text.replaceAll("\\s", "");
        if (s.isEmpty()) {
            throw new IllegalArgumentException("输入为空");
        }
        if (s.length() % 2 != 0) {
            throw new IllegalArgumentException("hex 长度为奇数（" + s.length() + " 位）");
        }
        byte[] out = new byte[s.length() / 2];
        for (int i = 0; i < out.length; i++) {
            int hi = digit(s.charAt(i * 2), s);
            int lo = digit(s.charAt(i * 2 + 1), s);
            out[i] = (byte) (hi << 4 | lo);
        }
        return out;
    }

    private static int digit(char c, String s) {
        int v = Character.digit(c, 16);
        if (v < 0) {
            throw new IllegalArgumentException("非法字符 '" + c + "'（应为 0-9/A-F）");
        }
        return v;
    }

    /** 大写、两位一组、无分隔（前端展示统一不带空格） */
    public static String encode(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(String.format("%02X", b));
        }
        return sb.toString();
    }

    public static String slice(byte[] buf, int off, int len) {
        int n = Math.min(Math.max(len, 0), buf.length - off);
        return encode(java.util.Arrays.copyOfRange(buf, off, off + n));
    }
}
