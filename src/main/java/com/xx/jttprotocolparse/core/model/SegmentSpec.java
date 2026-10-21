package com.xx.jttprotocolparse.core.model;

import lombok.Data;
import lombok.experimental.Accessors;

import java.util.Map;

/**
 * 位段定义：数值字段内从 start 起取 width 位，合成值查 values（值 → 中文）。
 * values 只显示命中的段；未列出的位段 = 保留位，不解析。与 bits 互斥（加载期校验）。
 * YAML 示例：{ start: 1, width: 2, values: { "0": 无TF卡, "3": 正常 } }
 */
@Data
@Accessors(chain = true)
public class SegmentSpec {

    /** 起始位号（低 0 位起） */
    private int start;
    /** 位段宽度；缺省 1 位（单双态位） */
    private int width = 1;
    /** 合成值 → 中文；键为该段十进制值，未命中显示原值 */
    private Map<String, String> values;
}
