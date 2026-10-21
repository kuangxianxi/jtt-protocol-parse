package com.xx.jttprotocolparse.core.codec;

import lombok.Data;

import java.util.LinkedHashMap;
import java.util.Map;

/** 字段解析游标：在一段缓冲区上顺序前进，并携带前文字段数值供 length/count 算式引用 */
@Data
public class Cursor {

    private final byte[] buf;
    private int pos;
    /** 前文字段（未缩放的原值），供 "$xxx" 引用 */
    private final Map<String, Long> vars = new LinkedHashMap<>();

    public Cursor(byte[] buf, int pos) {
        this.buf = buf;
        this.pos = pos;
    }

    public int remaining() {
        return buf.length - pos;
    }
}
