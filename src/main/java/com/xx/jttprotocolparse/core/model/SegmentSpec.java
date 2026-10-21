package com.xx.jttprotocolparse.core.model;

import lombok.Data;
import lombok.experimental.Accessors;

import java.util.Map;

/**
 * 位段定义：数值字段内从 start 起取 width 位，合成值查 values（值 → 中文）。
 * 有 label 的段输出为子节点行（label: 值）；无 label 的段拼接进节点值（「；」分隔）。
 * values 只显示命中的段对应行；未命中显示该段十进制原值。与 bits 互斥（加载期校验）。
 * YAML 示例：{ label: TF卡, start: 1, width: 2, values: { "0": 无TF卡, "3": 正常 } }
 */
@Data
@Accessors(chain = true)
public class SegmentSpec {

    /** 段级显示名（可选；给出则以子节点行展示该段） */
    private String label;
    /** 起始位号（低 0 位起） */
    private int start;
    /** 位段宽度；缺省 1 位（单双态位） */
    private int width = 1;
    /** 合成值 → 中文；键为该段十进制值，未命中显示原值 */
    private Map<String, String> values;
}
