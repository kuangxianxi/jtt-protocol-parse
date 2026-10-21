package com.xx.jttprotocolparse.core.codec;

import com.xx.jttprotocolparse.core.model.FieldSpec;
import com.xx.jttprotocolparse.core.model.SegmentSpec;
import com.xx.jttprotocolparse.core.result.IssueCode;
import com.xx.jttprotocolparse.core.result.Node;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FieldCodecTest {

    @Test
    void decTime() {
        // 年 月 日 时 分 秒各一字节数值：26, 10, 20, 17, 35, 0
        Cursor cur = new Cursor(new byte[]{0x1A, 0x0A, 0x14, 0x11, 0x23, 0x00}, 0);
        Node n = parse(cur, "dec-time");

        assertEquals("2026-10-20 17:35:00", n.getValue());
        assertEquals("1A0A14112300", n.getRaw());
        assertEquals(0, cur.remaining());
    }

    @Test
    void decTime_月日时分秒补零() {
        Cursor cur = new Cursor(new byte[]{0x1A, 0x01, 0x02, 0x03, 0x04, 0x05}, 0);
        Node n = parse(cur, "dec-time");

        assertEquals("2026-01-02 03:04:05", n.getValue());
    }

    @Test
    void 无id字段用label做节点标识与变量名() {
        Cursor cur = new Cursor(new byte[]{0x03}, 0);
        Node n = parse(cur, "u1");

        assertEquals("时间", n.getLabel());
        assertEquals(3L, cur.getVars().get("时间"));
    }

    @Test
    void map未命中直接显示原值() {
        FieldSpec f = new FieldSpec()
                .setLabel("状态")
                .setType("u1")
                .setMap(Map.of("0x01", "启动"));
        Cursor cur = new Cursor(new byte[]{0x7F}, 0);
        List<com.xx.jttprotocolparse.core.result.Issue> issues = new ArrayList<>();
        Node n = FieldCodec.parseOne(f, cur, null, issues, "状态");

        assertTrue(issues.isEmpty(), issues.toString());
        assertEquals("0x7F", n.getValue());
        assertNull(n.getNote());
    }

    @Test
    void invalid命中显示特殊值中文() {
        FieldSpec f = new FieldSpec()
                .setLabel("单体电压最低")
                .setType("u2")
                .setScale(0.001)
                .setUnit("V")
                .setInvalid(Map.of("0xFFFF", "异常"));
        Cursor cur = new Cursor(new byte[]{(byte) 0xFF, (byte) 0xFF}, 0);
        List<com.xx.jttprotocolparse.core.result.Issue> issues = new ArrayList<>();
        Node n = FieldCodec.parseOne(f, cur, null, issues, "极值");

        assertTrue(issues.isEmpty(), issues.toString());
        assertEquals("异常", n.getValue());
        assertEquals("FFFF", n.getRaw());
        assertEquals(0, cur.remaining());
    }

    @Test
    void invalid未命中按type转换() {
        FieldSpec f = new FieldSpec()
                .setLabel("单体电压最低")
                .setType("u2")
                .setScale(0.001);
        Cursor cur = new Cursor(new byte[]{0x0B, (byte) 0xB8}, 0);
        List<com.xx.jttprotocolparse.core.result.Issue> issues = new ArrayList<>();
        Node n = FieldCodec.parseOne(f, cur, null, issues, "极值");

        assertTrue(issues.isEmpty(), issues.toString());
        assertEquals("3", n.getValue());
    }

    @Test
    void segments按位段合成值映射并拼接() {
        FieldSpec f = new FieldSpec()
                .setLabel("终端状态")
                .setType("u1")
                .setSegments(List.of(
                        new SegmentSpec().setStart(0).setWidth(1)
                                .setValues(Map.of("0", "有CAN数据", "1", "无CAN数据")),
                        new SegmentSpec().setStart(1).setWidth(2)
                                .setValues(Map.of("0", "无TF卡", "1", "有TF卡，初始化", "2", "有TF卡，异常", "3", "有TF卡，正常"))));
        Cursor cur = new Cursor(new byte[]{0x06}, 0);   // bit0=0 有CAN；bit1~2=11 有TF卡正常
        List<com.xx.jttprotocolparse.core.result.Issue> issues = new ArrayList<>();
        Node n = FieldCodec.parseOne(f, cur, null, issues, "终端状态");

        assertTrue(issues.isEmpty(), issues.toString());
        assertEquals("有CAN数据；有TF卡，正常", n.getValue());
    }

    @Test
    void segments未命中显示该段十进制() {
        FieldSpec f = new FieldSpec()
                .setLabel("终端状态")
                .setType("u1")
                .setSegments(List.of(new SegmentSpec().setStart(1).setWidth(2)
                        .setValues(Map.of("0", "无TF卡", "1", "有TF卡，初始化"))));
        Cursor cur = new Cursor(new byte[]{0x04}, 0);   // bit1~2=10，values 未定义 2
        List<com.xx.jttprotocolparse.core.result.Issue> issues = new ArrayList<>();
        Node n = FieldCodec.parseOne(f, cur, null, issues, "终端状态");

        assertTrue(issues.isEmpty(), issues.toString());
        assertEquals("2", n.getValue());
    }

    @Test
    void decTime_不足6字节记问题() {
        Cursor cur = new Cursor(new byte[]{0x1A, 0x0A, 0x14, 0x11, 0x23}, 0);
        List<com.xx.jttprotocolparse.core.result.Issue> issues = new ArrayList<>();
        Node n = FieldCodec.parseOne(spec("dec-time"), cur, null, issues, "dt");

        assertNull(n);
        assertEquals(1, issues.size());
        assertEquals(IssueCode.BODY_TRUNCATED, issues.get(0).getCode());
    }

    private static Node parse(Cursor cur, String type) {
        List<com.xx.jttprotocolparse.core.result.Issue> issues = new ArrayList<>();
        Node n = FieldCodec.parseOne(spec(type), cur, null, issues, "dt");
        assertTrue(issues.isEmpty(), issues.toString());
        return n;
    }

    private static FieldSpec spec(String type) {
        return new FieldSpec().setLabel("时间").setType(type);
    }
}
