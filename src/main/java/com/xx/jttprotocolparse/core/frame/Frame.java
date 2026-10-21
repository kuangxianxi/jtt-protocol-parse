package com.xx.jttprotocolparse.core.frame;

import com.xx.jttprotocolparse.core.result.Node;

import java.util.List;

/** 协议帧层解析结果：报文头各字段的展示节点 + 路由所需的关键值 */
public record Frame(
        List<Node> header,
        /** 数据单元之后的收尾节点（如校验码），引擎在数据体解析完后追加到输出列表尾 */
        List<Node> trailer,
        int command,
        int ack,
        String vin,
        /** 数据单元加密方式（0x01 未加密） */
        int encType,
        /** 声明的数据单元长度 */
        int declaredLen,
        /** 数据单元字节（按声成长度切片，不足则截到报文尾） */
        byte[] payload,
        /** 校验码是否一致 */
        boolean checksumOk) {
}
