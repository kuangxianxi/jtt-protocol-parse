package com.xx.jttprotocolparse.core.descr;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.xx.jttprotocolparse.core.model.CommandSpec;
import com.xx.jttprotocolparse.core.model.CommandTable;
import com.xx.jttprotocolparse.core.model.ProtocolSpec;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.xx.jttprotocolparse.core.model.FieldSpec;
import com.xx.jttprotocolparse.core.model.SubTypeSpec;

/**
 * 协议描述加载器。
 *
 * <pre>
 * classpath:protocols/protocols.yaml                     —— 清单：协议 → 版本 → 厂商
 * classpath:protocols/&lt;协议&gt;/&lt;版本&gt;/&lt;厂商&gt;.yaml         —— 厂商完整配置（不做 overlay）
 * </pre>
 *
 * 解析时 protocol/version/vendor 必须同时登记在清单中且文件存在；不做默认回退。
 * 文件结构：命令码 → name / body（字段列表）/ subtype（类型码 → name / body）。
 */
public final class DescriptorRegistry {

    private static final ObjectMapper YAML = new ObjectMapper(new YAMLFactory())
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);

    private final Map<String, MetaEntry> metas = new LinkedHashMap<>();

    public DescriptorRegistry() {
        try (InputStream in = stream("protocols/protocols.yaml")) {
            if (in != null) {
                Manifest m = YAML.readValue(in, Manifest.class);
                if (m != null && m.protocols != null) {
                    m.protocols.forEach((id, v) -> metas.put(id, v));
                }
            }
        } catch (IOException e) {
            throw new IllegalStateException("加载 protocols/protocols.yaml 失败", e);
        }
    }

    /** 协议清单项：versions 下各自带厂商列表 */
    public record ProtocolInfo(String id, String label, List<VersionInfo> versions) {
    }

    /** 版本项：该版本支持的厂商 */
    public record VersionInfo(String version, List<String> vendors) {
    }

    private static final class Manifest {
        public Map<String, MetaEntry> protocols;
    }

    private static final class MetaEntry {
        public String label;
        public Map<String, VersionEntry> versions = new LinkedHashMap<>();
    }

    private static final class VersionEntry {
        public List<String> vendors = new ArrayList<>();
    }

    public List<ProtocolInfo> list() {
        List<ProtocolInfo> out = new ArrayList<>();
        metas.forEach((id, m) -> {
            List<VersionInfo> versions = new ArrayList<>();
            m.versions.forEach((v, ve) -> versions.add(new VersionInfo(v,
                    ve.vendors == null ? List.of() : ve.vendors)));
            out.add(new ProtocolInfo(id, m.label, versions));
        });
        return out;
    }

    /**
     * 拼装一个可解析方言：protocol/version/vendor 三者都必须在清单中登记，
     * 且 protocols/&lt;协议&gt;/&lt;版本&gt;/&lt;厂商&gt;.yaml 存在；否则返回 null（不默认、不回退）。
     */
    public ProtocolSpec dialect(String id, String version, String vendor) {
        MetaEntry meta = metas.get(id);
        if (meta == null || isBlank(version) || isBlank(vendor)) {
            return null;
        }
        VersionEntry ve = meta.versions.get(version);
        if (ve == null || ve.vendors == null || !ve.vendors.contains(vendor)) {
            return null;
        }
        ProtocolFile file = read("protocols/" + id + "/" + version + "/" + vendor + ".yaml");
        if (file == null) {
            return null;
        }
        if (file.protocol != null && !file.protocol.equals(id)) {
            return null;
        }
        if (file.version != null && !file.version.equals(version)) {
            return null;
        }
        String display = file.vendor != null && !file.vendor.isBlank() ? file.vendor : vendor;
        ProtocolSpec d = toSpec(file, display);
        resolveSubtypeRefs(d);
        ensureMapInvalidExclusion(d);
        return d;
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    // ---- YAML 根结构 ----

    private static final class ProtocolFile {
        public String protocol;
        public String version;
        public String label;
        /** 厂商文件里的展示名；加载时以调用参数为准 */
        public String vendor;

        private final CommandTable commands = new CommandTable();

        /** 根节点除元信息外的 key 全部是命令码 */
        @JsonAnySetter
        public void addCommand(String key, JsonNode value) {
            if ("commands".equals(key) || "bodies".equals(key)) {
                throw new IllegalStateException("旧版 " + key + " 结构已废弃：命令码直接写在根节点，body 内联到命令下");
            }
            commands.add(key, YAML.convertValue(value, CommandSpec.class));
        }

        CommandTable commands() {
            return commands;
        }
    }

    private static ProtocolSpec toSpec(ProtocolFile f, String vendor) {
        return new ProtocolSpec()
                .setId(f.protocol)
                .setLabel(f.label)
                .setVersion(f.version)
                .setVendor(vendor)
                .setCommands(f.commands());
    }

    /** 解析 subtypeRef：共享目标命令的子类型表；引用目标不得再引用 */
    private static void resolveSubtypeRefs(ProtocolSpec d) {
        for (CommandSpec cmd : d.getCommands().all()) {
            String ref = cmd.getSubtypeRef();
            if (ref == null) {
                continue;
            }
            if (cmd.getSubtype() != null && !cmd.getSubtype().isEmpty()) {
                throw new IllegalStateException("subtype 与 subtypeRef 不能同时声明（报文 " + cmd.getName() + "）");
            }
            CommandSpec target = d.getCommands().get(Integer.decode(ref.trim()));
            if (target == null) {
                throw new IllegalStateException("subtypeRef 指向不存在的命令: " + ref + "（报文 " + cmd.getName() + "）");
            }
            if (target.getSubtypeRef() != null) {
                throw new IllegalStateException("subtypeRef 不支持链式引用: " + ref + "（报文 " + cmd.getName() + "）");
            }
            cmd.setSubtype(target.getSubtype());
        }
    }

    /**
     * map 与 invalid 表达两类不相交的字段（纯枚举 / 数值+特殊值），
     * 同字段双表 = 配置错误，加载期直接失败（对齐「文件缺失/未登记直接失败」的严格性）。
     */
    private static void ensureMapInvalidExclusion(ProtocolSpec d) {
        for (CommandSpec cmd : d.getCommands().all()) {
            checkFields(cmd.getBody(), "命令 " + cmd.getName());
            if (cmd.getSubtype() != null) {
                for (SubTypeSpec st : cmd.getSubtype().all()) {
                    checkFields(st.getBody(), "子报文 " + st.getName());
                }
            }
        }
    }

    /** map 与 invalid 互斥；group 嵌套递归。包级可见，供测试直接驱动 */
    static void checkFields(List<FieldSpec> fields, String owner) {
        if (fields == null) {
            return;
        }
        for (FieldSpec f : fields) {
            if (f.getMap() != null && f.getInvalid() != null) {
                throw new IllegalStateException(owner + " 字段「" + f.getLabel() + "」不可同时声明 map 与 invalid");
            }
            checkFields(f.getFields(), owner);
        }
    }

    private ProtocolFile read(String path) {
        try (InputStream in = stream(path)) {
            return in == null ? null : YAML.readValue(in, ProtocolFile.class);
        } catch (IOException e) {
            throw new IllegalStateException("协议描述加载失败: " + path, e);
        }
    }

    private static InputStream stream(String path) {
        return Thread.currentThread().getContextClassLoader().getResourceAsStream(path);
    }
}
