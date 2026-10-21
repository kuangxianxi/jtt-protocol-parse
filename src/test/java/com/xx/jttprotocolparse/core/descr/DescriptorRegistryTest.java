package com.xx.jttprotocolparse.core.descr;

import com.xx.jttprotocolparse.core.descr.DescriptorRegistry;
import com.xx.jttprotocolparse.core.model.CommandSpec;
import com.xx.jttprotocolparse.core.model.FieldSpec;
import com.xx.jttprotocolparse.core.model.ProtocolSpec;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DescriptorRegistryTest {

    private final DescriptorRegistry registry = new DescriptorRegistry();

    @Test
    void nestedShapeBinds() {
        ProtocolSpec d = registry.dialect("gc-32960", "2016", "breton");
        assertNotNull(d);

        CommandSpec login = d.getCommands().get(0x01);
        assertEquals(2, login.getBody().size());
        assertFalse(login.isStream());

        CommandSpec realtime = d.getCommands().get(0x02);
        assertTrue(realtime.isStream());
        assertEquals(1, realtime.getBody().size());             // 采集时间前缀
        assertEquals(13, realtime.getSubtype().get(0x01).getBody().size());
        assertNotNull(realtime.getSubtype().get(0x02).getBody());
        assertNotNull(realtime.getSubtype().get(0x09));         // 子类型表绑定完整
        assertNotNull(realtime.getSubtype().get(0x80));         // 扩展部分

        CommandSpec resend = d.getCommands().get(0x03);
        assertTrue(resend.isStream());
        assertSame(realtime.getSubtype(), resend.getSubtype()); // subtypeRef 共享同一张表

        CommandSpec heartbeat = d.getCommands().get(0x06);
        assertNull(heartbeat.getBody());
        assertFalse(heartbeat.isStream());
    }

    @Test
    void missingProtocolVersionOrVendorFails() {
        assertNull(registry.dialect("gc-32960", "2016", null));
        assertNull(registry.dialect("gc-32960", "2016", "unknown"));
        assertNull(registry.dialect("gc-32960", "2015", "breton"));
        assertNull(registry.dialect("unknown", "2016", "breton"));
    }

    @Test
    void map与invalid同字段声明加载失败() {
        FieldSpec f = new FieldSpec()
                .setLabel("车辆状态")
                .setType("u1")
                .setMap(Map.of("0x01", "启动"))
                .setInvalid(Map.of("0xFF", "无效"));

        IllegalStateException e = assertThrows(IllegalStateException.class,
                () -> DescriptorRegistry.checkFields(List.of(f), "命令 整车数据"));
        assertTrue(e.getMessage().contains("不可同时声明 map 与 invalid"));
    }

    /** group 嵌套内的字段同样要被校验到 */
    @Test
    void map与invalid冲突校验覆盖group嵌套() {
        FieldSpec inner = new FieldSpec()
                .setLabel("转矩")
                .setType("s2")
                .setMap(Map.of("0x7FFF", "异常"))
                .setInvalid(Map.of("0x8000", "无效"));
        FieldSpec group = new FieldSpec()
                .setLabel("驱动电机")
                .setType("group")
                .setCount("$个数")
                .setFields(List.of(inner));

        assertThrows(IllegalStateException.class, () -> DescriptorRegistry.checkFields(List.of(group), "子报文 电机"));
    }

    @Test
    void vendorFileIsCompleteConfigNotOverlay() {
        ProtocolSpec d = registry.dialect("gc-32960", "2016", "test-vendor");
        assertNotNull(d);
        assertEquals("test-vendor", d.getVendor());
        assertTrue(d.prefix().contains("test-vendor"));

        // 厂商文件是完整描述：只有它自己声明的命令，不回落国标
        assertEquals("心跳(厂商版)", d.getCommands().get(0x06).getName());
        assertNull(d.getCommands().get(0x01));
    }
}
