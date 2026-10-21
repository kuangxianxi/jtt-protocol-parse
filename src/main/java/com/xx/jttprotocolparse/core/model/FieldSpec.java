package com.xx.jttprotocolparse.core.model;

import lombok.Data;
import lombok.experimental.Accessors;

import java.util.List;
import java.util.Map;

/**
 * 字段定义。DSL 能力清单见 docs/设计方案.md §5.3 —— 加语法先改文档。
 * YAML 示例：{ label: 车速, type: u2, scale: 0.1, unit: km/h }
 *
 * <p>字段没有 id：节点标识、issue path、$ 变量引用统一使用 label。
 * 被 $ 引用的 label 只能包含字母/数字/中文（不要含空格或运算符）。
 * map 是「原值 → 中文」映射，未命中时直接显示原值；没有 fallback / invalid / note。
 */
@Data
@Accessors(chain = true)
@com.fasterxml.jackson.annotation.JsonIgnoreProperties(ignoreUnknown = true)
public class FieldSpec {

    private String label;

    /**
     * u1/u2/u3/u4 = 无符号整型（宽度固定）
     * bcd-time = 6B BCD 时间（YYMMDDhhmmss）
     * dec-time = 6B 十进制数值时间（年 月 日 时 分 秒各一字节数值）
     * ascii/gbk = 字符串（需 length）
     * raw = 原样保留（length 缺省 rest）
     * group = 循环块（fields + count）
     */
    private String type;

    /** 定长；"rest" = 剩余长度；"$标签" / "$标签*2+1" = 引用前文字段的简单算式 */
    private Object length;
    /** 数值缩放（如 0.1） */
    private double scale = 1.0;
    /** 数值偏移（如总电流 = 值-1000 → offset: 1000） */
    private double offset = 0.0;
    private String unit;
    /** 枚举映射：{"0x01": 启动态}；未命中时直接显示原值（如 0x7F） */
    private Map<String, String> map;
    /**
     * 特殊值映射：{"0xFFFF": 异常}。数值字段按原始字节的无符号值匹配（命中显示中文），
     * 未命中按 type 正常转换（scale/offset）；解码优先级 invalid → bits → segments → map → 数值。
     * 与 map 互斥（加载期校验）：map = 纯枚举字段，invalid = 数值 + 特殊值。
     */
    private Map<String, String> invalid;
    /** 位表映射：{"0": 运行, "2": 低压故障"}（按 bit 位号取标签） */
    private Map<String, String> bits;
    /** 位段映射：数值字段按位域合成值查 values（如 bit1~2 的 TF卡状态）；未命中显示该段十进制值。与 bits 互斥（加载期校验） */
    private List<SegmentSpec> segments;
    /** group 的循环次数（引用前文字段，如 "$驱动电机个数"） */
    private String count;
    /** 子字段（group 循环体） */
    private List<FieldSpec> fields;
}
