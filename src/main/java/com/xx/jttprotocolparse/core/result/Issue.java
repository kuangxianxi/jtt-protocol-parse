package com.xx.jttprotocolparse.core.result;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 解析过程中的一个问题点，path 表示报文内定位（如 checksum / body.loginTime） */
@Data
@NoArgsConstructor
@AllArgsConstructor(staticName = "of")
public class Issue {

    private String path;
    private String code;
    private String message;
}
