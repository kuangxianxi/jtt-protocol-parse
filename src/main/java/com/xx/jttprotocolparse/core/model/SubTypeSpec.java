package com.xx.jttprotocolparse.core.model;

import lombok.Data;
import lombok.experimental.Accessors;

import java.util.List;

/** 子报文类型定义：stream 型命令按「数据类型字节」二次路由到这里 */
@Data
@Accessors(chain = true)
public class SubTypeSpec {

    /** 中文名（如 整车数据 / 厂商扩展报文-XX） */
    private String name;

    /** 数据体字段；null = 该子类型字段尚未核对，按 raw 保留 */
    private List<FieldSpec> body;
}
