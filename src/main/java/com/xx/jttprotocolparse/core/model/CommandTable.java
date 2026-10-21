package com.xx.jttprotocolparse.core.model;

import com.fasterxml.jackson.annotation.JsonAnySetter;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 命令码表：命令字节 → 命令定义。领域类承载 YAML 绑定（hex 键）。
 */
public class CommandTable {

    private final Map<Integer, CommandSpec> byCode = new LinkedHashMap<>();

    @JsonAnySetter
    public void add(String key, CommandSpec spec) {
        try {
            byCode.put(Integer.decode(key.trim()), spec);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("非法命令码: " + key, e);
        }
    }

    public CommandSpec get(int code) {
        return byCode.get(code);
    }

    public Collection<CommandSpec> all() {
        return byCode.values();
    }
}
