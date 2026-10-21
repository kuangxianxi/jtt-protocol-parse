package com.xx.jttprotocolparse.core.frame;

import com.xx.jttprotocolparse.core.result.Issue;

import java.util.List;

/** 帧层适配器 SPI：一种协议一个实现，负责拆帧、长度与校验（代码实现，不配置化） */
public interface FrameAdapter {

    /** 协议 id（与 protocols 清单一致，如 gc-32960） */
    String protocolId();

    /** 拆帧；结构性无法识别时返回 null，并把问题追加进 issues */
    Frame parse(byte[] msg, List<Issue> issues);
}
