package com.xx.jttprotocolparse.core.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;
import lombok.experimental.Accessors;

import java.util.List;

/**
 * 命令码定义（一级路由：命令字节 → 本类）。
 *
 * <p>body 与 subtype 可以并存：body 是子报文之前的固定前缀（如 0x02 的采集时间），
 * subtype 是「类型1B + 长度2B + 数据体」的循环路由表。有 subtype / subtypeRef 即视为 stream。
 */
@Data
@Accessors(chain = true)
public class CommandSpec {

    private String name;

    /** 数据体字段；null = 未配置（心跳或待核对） */
    private List<FieldSpec> body;

    /** 复用同文件另一命令的子类型表（如 0x03 复用 0x02） */
    private String subtypeRef;

    /** 子报文路由表（YAML 绑定 / 解析结果同一承载；subtypeRef 在 Registry 解析后指向共享实例） */
    private SubTypeTable subtype = new SubTypeTable();

    /** 有子类型表即走 stream 解析（不写 stream 开关） */
    @JsonIgnore
    public boolean isStream() {
        return subtypeRef != null || (subtype != null && !subtype.isEmpty());
    }
}
