package com.xx.jttprotocolparse.core.model;

import lombok.Data;
import lombok.experimental.Accessors;

/**
 * 一个可解析「方言」= 某协议 + 某版本（厂商文件则是该厂商的完整描述）。
 * 由 DescriptorRegistry 装配而成，不是 YAML 直接映射目标。
 */
@Data
@Accessors(chain = true)
public class ProtocolSpec {

    private String id;
    /** 协议中文名，如「GB/T 32960.3 新能源汽车国标」 */
    private String label;
    private String version;
    /** null/空 = 国标基准；否则为厂商 id */
    private String vendor;
    private CommandTable commands = new CommandTable();

    /** 报文全名前缀，如「GB/T 32960.3」；vendor 后形如「GB/T 32960.3 · XX厂商」 */
    public String prefix() {
        return vendor == null ? label : label + " · " + vendor;
    }
}
