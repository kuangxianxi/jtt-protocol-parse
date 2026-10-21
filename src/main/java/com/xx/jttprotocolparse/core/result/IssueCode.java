package com.xx.jttprotocolparse.core.result;

/** 解析问题码全集（新增码先在此登记，禁止散落硬编码字符串） */
public final class IssueCode {

    /** hex 输入问题 */
    public static final String HEX_EMPTY = "HEX_EMPTY";
    public static final String HEX_INVALID = "HEX_INVALID";
    /** 帧层（FrameAdapter） */
    public static final String FRAME_NOT_MATCH = "FRAME_NOT_MATCH";
    public static final String LENGTH_MISMATCH = "LENGTH_MISMATCH";
    public static final String CHECKSUM_MISMATCH = "CHECKSUM_MISMATCH";
    /** 路由（ParseEngine） */
    public static final String UNSUPPORTED_PROTOCOL = "UNSUPPORTED_PROTOCOL";
    public static final String UNKNOWN_COMMAND = "UNKNOWN_COMMAND";
    /** 数据体（engine/codec） */
    public static final String BODY_TRUNCATED = "BODY_TRUNCATED";
    public static final String BODY_LEFTOVER = "BODY_LEFTOVER";
    public static final String BODY_UNEXPECTED = "BODY_UNEXPECTED";
    /** stream 子报文类型未知/未配置字段，无法确定长度 */
    public static final String UNKNOWN_SUBTYPE = "UNKNOWN_SUBTYPE";
    /** DSL 配置问题 */
    public static final String BAD_EXPR = "BAD_EXPR";
    public static final String BAD_MAP_KEY = "BAD_MAP_KEY";

    private IssueCode() {
    }
}
