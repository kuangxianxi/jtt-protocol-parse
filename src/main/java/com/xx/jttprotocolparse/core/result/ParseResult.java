package com.xx.jttprotocolparse.core.result;

import lombok.Data;
import lombok.experimental.Accessors;

import java.util.ArrayList;
import java.util.List;

/** 一次解析的完整结果 */
@Data
@Accessors(chain = true)
public class ParseResult {

    private String protocol;
    private String version;
    /** 厂商标识，null/空 = 国标基准 */
    private String vendor;
    /** 报文中文全名，如「GB/T 32960.3 · 车辆登录」 */
    private String report;
    /** true = 无任何问题；否则看 issues */
    private boolean ok;
    private List<Node> header = new ArrayList<>();
    private List<Node> body = new ArrayList<>();
    private List<Issue> issues = new ArrayList<>();

    public static ParseResult failed(String protocol, String version, Issue... issues) {
        ParseResult r = new ParseResult()
                .setProtocol(protocol)
                .setVersion(version)
                .setReport("无法解析")
                .setOk(false);
        for (Issue i : issues) {
            r.getIssues().add(i);
        }
        return r;
    }
}
