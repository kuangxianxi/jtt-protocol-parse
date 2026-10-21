package com.xx.jttprotocolparse.web;

import com.xx.jttprotocolparse.core.descr.DescriptorRegistry;
import com.xx.jttprotocolparse.core.engine.ParseEngine;
import com.xx.jttprotocolparse.core.result.Issue;
import com.xx.jttprotocolparse.core.result.ParseResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/** REST 薄层：只做请求/响应装配，解析逻辑全在 core */
@RestController
@RequestMapping("/api")
public class ApiController {

    private final ParseEngine engine;
    private final DescriptorRegistry registry;

    public ApiController(ParseEngine engine, DescriptorRegistry registry) {
        this.engine = engine;
        this.registry = registry;
    }

    public static class ParseRequest {
        public String protocol = "gc-32960";
        public String version;
        public String vendor;
        public String hex;
    }

    /** 一次粘贴多条：正文按空行拆分，返回多条解析结果 */
    @PostMapping("/parse")
    public Map<String, Object> parse(@RequestBody ParseRequest req) {
        if (req.hex == null || req.hex.isBlank()) {
            ParseResult r = ParseResult.failed(req.protocol, req.version, Issue.of("hex", "HEX_EMPTY", "输入为空"));
            return Map.of("results", List.of(r));
        }
        List<ParseResult> results = engine.parse(req.protocol, req.version, req.vendor, req.hex);
        return Map.of("results", results);
    }

    /** 当前支持的协议/版本/厂商清单（由描述文件动态生成） */
    @GetMapping("/protocols")
    public List<DescriptorRegistry.ProtocolInfo> protocols() {
        return registry.list();
    }
}
