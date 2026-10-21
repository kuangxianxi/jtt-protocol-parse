package com.xx.jttprotocolparse.core.result;

import lombok.Data;
import lombok.experimental.Accessors;

import java.util.ArrayList;
import java.util.List;

/**
 * 解析结果树上的一个节点：字段（label=中文名，value=工程值/枚举中文，raw=原始 hex 片段），
 * 或一个分组/循环/子包（children）。
 */
@Data
@Accessors(chain = true)
public class Node {

    private String id;
    private String label;
    private String value;
    private String unit;
    /** 该节点对应的原始 hex 片段 */
    private String raw;
    /** 补充说明（如「原值 0x01」「非 BCD 数据」「字段待核对」） */
    private String note;
    /** 校验类结论（如「校验通过」），仅报文头使用 */
    private String check;
    /** 该节点自身的问题（如与声明长度不符） */
    private String issue;
    private List<Node> children;

    public Node() {
    }

    public Node(String id, String label) {
        this.id = id;
        this.label = label;
    }

    /** 挂子节点；children 懒初始化 */
    public Node add(Node child) {
        if (children == null) {
            children = new ArrayList<>();
        }
        children.add(child);
        return this;
    }
}
