package com.xx.jttprotocolparse.core.model;

import com.fasterxml.jackson.annotation.JsonAnySetter;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 子报文类型表：stream 型命令数据体内「数据类型字节 → 子报文」的二级路由表。
 */
public class SubTypeTable {

    private final Map<Integer, SubTypeSpec> byType = new LinkedHashMap<>();

    @JsonAnySetter
    public void add(String key, SubTypeSpec spec) {
        try {
            byType.put(Integer.decode(key.trim()), spec);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("非法子类型码: " + key, e);
        }
    }

    public SubTypeSpec get(int type) {
        return byType.get(type);
    }

    public boolean isEmpty() {
        return byType.isEmpty();
    }
}
