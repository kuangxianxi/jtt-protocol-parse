package com.xx.jttprotocolparse.core;

import com.xx.jttprotocolparse.core.descr.DescriptorRegistry;
import com.xx.jttprotocolparse.core.engine.ParseEngine;
import com.xx.jttprotocolparse.core.frame.GB32960FrameAdapter;
import com.xx.jttprotocolparse.core.result.Issue;
import com.xx.jttprotocolparse.core.result.Node;
import com.xx.jttprotocolparse.core.result.ParseResult;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 回归样例与 web/index.html 页面共用同一组 hex，防止改 YAML/引擎时悄悄破坏。
 * 构造：起始符 ## + 命令 + 应答 + VIN(VIN1234567890ABCD) + 未加密 + 长度 + 数据体 + BCC。
 */
class ParseEngineTest {

    private static final String VIN = "56494E3132333435363738393041424344";
    private final ParseEngine engine = new ParseEngine(new DescriptorRegistry(), List.of(new GB32960FrameAdapter()));

    // 与 web/index.html 样例保持一致
    private static final String LOGIN = "232301FE" + VIN + "0100081A0A14112300000195";
    private static final String HEARTBEAT = "232306FE" + VIN + "010000AD";

    // 实时上报：采集时间(十进制字节) + 整车数据(0x01, 20B) + 驱动电机数据(0x02, 1B个数 + 1个电机12B)
    private static final String REALTIME_BODY = "1A0A14112300"
            + "01" + "01030103E8000100000FA003E832010113880000"
            + "02" + "010101327530324E200FA003E8";
    private static final String REALTIME = frame(0x02, 0xFE, REALTIME_BODY);

    // 覆盖 0x01~0x09 与 0x80 的全量子类型样例（按协议字段逐段构造）
    private static final String ALL_SUBTYPES_BODY = "1A0A14112300"
            + "01" + "01030103E8000100000FA003E832010113880000"
            + "02" + "010101327530324E200FA003E8"
            + "03" + "0FA003E80000000232320BB80100640200640301"
            + "04" + "0100000000"
            + "05" + "00075BCD15075BCD15"
            + "06" + "01010BB801010BB8010132010132"
            + "07" + "01000000010100000001000000"
            + "08" + "01010FA003E800040001020BB80BB9"
            + "09" + "010100023232"
            + "80" + "0016000100020003000000000000011234567802AABB";

    @Test
    void vehicleLogin() {
        ParseResult r = one(LOGIN);
        assertTrue(r.isOk(), issues(r));
        assertEquals("GB/T 32960.3 新能源汽车国标 · breton · 车辆登录", r.getReport());
        assertEquals("2026-10-20 17:35:00", value(children(r), "登录时间"));
        assertEquals("1", value(children(r), "登录序号"));
    }

    @Test
    void heartbeat() {
        ParseResult r = one(HEARTBEAT);
        assertTrue(r.isOk(), issues(r));
        assertTrue(r.getBody().isEmpty());
    }

    @Test
    void realtimeVehicleData() {
        ParseResult r = one(REALTIME);
        assertTrue(r.isOk(), issues(r));
        assertEquals("2026-10-20 17:35:00", value(r.getBody(), "数据采集时间"));

        Node vehicle = r.getBody().get(1);
        assertEquals("整车数据", vehicle.getLabel());
        List<Node> fields = vehicle.getChildren();
        assertEquals("启动态", value(fields, "车辆状态"));
        assertEquals("未充电状态", value(fields, "充电状态"));
        assertEquals("纯电", value(fields, "运行模式"));
        assertEquals("100", value(fields, "车速"));         // 1000 * 0.1 km/h
        assertEquals("6553.6", value(fields, "累计里程"));    // 65536 * 0.1 km
        assertEquals("400", value(fields, "总电压"));       // 4000 * 0.1 V
        assertEquals("0", value(fields, "总电流"));          // (1000-1000) * 0.1 A
        assertEquals("50", value(fields, "SOC"));
        assertEquals("工作", value(fields, "DC-DC状态"));
        assertEquals("1", value(fields, "档位"));
        assertEquals("5000", value(fields, "绝缘电阻"));
        assertEquals("0", value(fields, "加速踏板行程值"));
        assertEquals("0", value(fields, "制动踏板状态"));

        Node motorPacket = r.getBody().get(2);
        assertEquals("驱动电机数据", motorPacket.getLabel());
        assertEquals("1", value(motorPacket.getChildren(), "驱动电机个数"));
        Node motor = node(motorPacket.getChildren(), "驱动电机总成信息列表 1");
        assertNotNull(motor, "驱动电机总成信息列表节点缺失");
        List<Node> motorFields = motor.getChildren();
        assertEquals("1", value(motorFields, "驱动电机序号"));
        assertEquals("耗电", value(motorFields, "驱动电机状态"));
        assertEquals("10", value(motorFields, "电机控制器温度"));   // 50 - 40
        assertEquals("0", value(motorFields, "驱动电机转速"));      // 30000 - 30000
        assertEquals("10", value(motorFields, "驱动电机温度"));     // 50 - 40
        assertEquals("0", value(motorFields, "驱动电机转矩"));      // (20000-20000)*0.1
        assertEquals("400", value(motorFields, "电机控制器输入电压")); // 4000*0.1
        assertEquals("0", value(motorFields, "电机控制器直流母线电流"));
    }

    @Test
    void realtimeAllSubtypes() {
        ParseResult r = one(frame(0x02, 0xFE, ALL_SUBTYPES_BODY));
        assertTrue(r.isOk(), issues(r));
        assertEquals(11, r.getBody().size(), "采集时间 + 10 个子报文");

        assertEquals("整车数据", r.getBody().get(1).getLabel());
        assertEquals("驱动电机数据", r.getBody().get(2).getLabel());
        assertEquals("燃料电池数据", r.getBody().get(3).getLabel());
        assertEquals("发动机数据", r.getBody().get(4).getLabel());
        assertEquals("车辆位置数据", r.getBody().get(5).getLabel());
        assertEquals("极值数据", r.getBody().get(6).getLabel());
        assertEquals("报警数据", r.getBody().get(7).getLabel());
        assertEquals("可充电储能装置电压数据", r.getBody().get(8).getLabel());
        assertEquals("可充电储能装置温度数据", r.getBody().get(9).getLabel());
        assertEquals("扩展部分数据", r.getBody().get(10).getLabel());

        Node fuelCell = r.getBody().get(3);
        assertEquals("2", value(fuelCell.getChildren(), "燃料电池温度探针总数"));
        assertEquals("-10", value(fuelCell.getChildren(), "氢系统中最高温度")); // 3000*0.01 - 40
        Node probe = node(fuelCell.getChildren(), "探针温度值 1");
        assertNotNull(probe);
        assertEquals("10", value(probe.getChildren(), "探针温度"));

        Node position = r.getBody().get(5);
        assertEquals("无置位", value(position.getChildren(), "定位状态"));
        assertEquals("123.456789", value(position.getChildren(), "经度"));

        Node alarm = r.getBody().get(7);
        assertEquals("一级故障", value(alarm.getChildren(), "最高报警等级"));
        assertEquals("bit0 温度差异报警", value(alarm.getChildren(), "通用报警标志"));
        Node fault = node(alarm.getChildren(), "可充电储能装置故障代码列表 1");
        assertNotNull(fault);
        assertEquals("00000001", value(fault.getChildren(), "故障代码"));

        Node battery = r.getBody().get(8);
        Node subsystem = node(battery.getChildren(), "可充电储能子系统电压信息列表 1");
        assertNotNull(subsystem);
        Node voltages = node(subsystem.getChildren(), "单体电池电压列表 1");
        assertNotNull(voltages);
        assertEquals("3", value(voltages.getChildren(), "单体电池电压"));  // 0x0BB8 * 0.001

        Node temp = r.getBody().get(9);
        Node tempItem = node(temp.getChildren(), "可充电储能子系统温度信息列表 1");
        assertNotNull(tempItem);
        Node tempList = node(tempItem.getChildren(), "可充电储能子系统温度值列表 1");
        assertNotNull(tempList);
        assertEquals("10", value(tempList.getChildren(), "温度值"));

        Node ext = r.getBody().get(10);
        assertEquals("22", value(ext.getChildren(), "自定义数据长度"));
        assertEquals("1", value(ext.getChildren(), "Can帧个数"));
        Node can = node(ext.getChildren(), "CAN帧列表 1");
        assertNotNull(can);
        assertEquals("12345678", value(can.getChildren(), "canID"));
        assertEquals("AABB", value(can.getChildren(), "Can帧数据"));
    }

    @Test
    void badChecksum_尽力解析() {
        String bad = LOGIN.substring(0, LOGIN.length() - 2) + "90";
        ParseResult r = one(bad);
        assertTrue(r.getIssues().stream().anyMatch(i -> IssueCodeOf(i).equals("CHECKSUM_MISMATCH")), issues(r));
        // 明文字段仍应解析出来（尽力解析）
        assertEquals("2026-10-20 17:35:00", value(children(r), "登录时间"));
    }

    @Test
    void unknownCommand() {
        ParseResult r = one(frame(0x7F, 0xFE, "000102"));
        assertTrue(r.getIssues().stream().anyMatch(i -> IssueCodeOf(i).equals("UNKNOWN_COMMAND")), issues(r));
        assertEquals("000102", value(r.getBody(), "数据单元(未知命令)"));
    }

    @Test
    void multiPaste() {
        List<ParseResult> rs = engine.parse("gc-32960", "2016", "breton", LOGIN + "\n\n" + HEARTBEAT + "\n1 2");
        assertEquals(3, rs.size());
        assertTrue(rs.get(0).isOk());
        assertTrue(rs.get(1).isOk());
        assertTrue(rs.get(2).getIssues().stream().anyMatch(i -> IssueCodeOf(i).equals("FRAME_NOT_MATCH")), issues(rs.get(2)));
    }

    @Test
    void heartbeatWithPayload_recordsUnexpectedBody() {
        ParseResult r = one(frame(0x06, 0xFE, "AABB"));
        assertTrue(r.getIssues().stream().anyMatch(i -> IssueCodeOf(i).equals("BODY_UNEXPECTED")), issues(r));
        assertEquals("AABB", value(r.getBody(), "多余数据体"));
    }

    @Test
    void resendUsesSameSubtypeTable() {
        ParseResult r = one(frame(0x03, 0xFE, REALTIME_BODY));
        assertTrue(r.isOk(), issues(r));
        assertEquals("GB/T 32960.3 新能源汽车国标 · breton · 补发信息上报", r.getReport());
        assertEquals("2026-10-20 17:35:00", value(r.getBody(), "数据采集时间"));
        Node packet = r.getBody().get(1);
        assertEquals("整车数据", packet.getLabel());
        assertEquals("100", value(packet.getChildren(), "车速"));
    }

    @Test
    void unknownSubtype_recordsIssueAndPreservesRest() {
        // 0x0A 未在 subtype 表中定义，无法确定长度：记录 issue，剩余字节原样保留
        ParseResult r = one(frame(0x02, 0xFE, "1A0A14112300" + "0A" + "AABBCC"));
        assertTrue(r.getIssues().stream().anyMatch(i -> IssueCodeOf(i).equals("UNKNOWN_SUBTYPE")), issues(r));
        Node packet = r.getBody().get(1);
        assertEquals("未知子报文 0x0A", packet.getLabel());
        assertEquals("AABBCC", packet.getValue());
    }

    // ---- 样例帧构造（测试专用）：保证 BCC 正确 ----

    private static String frame(int cmd, int ack, String bodyHex) {
        byte[] b = hex(bodyHex);
        byte[] vin = hex(VIN);
        byte[] out = new byte[2 + 1 + 1 + 17 + 1 + 2 + b.length + 1];
        out[0] = 0x23;
        out[1] = 0x23;
        out[2] = (byte) cmd;
        out[3] = (byte) ack;
        System.arraycopy(vin, 0, out, 4, 17);
        out[21] = 0x01;
        out[22] = (byte) (b.length >> 8);
        out[23] = (byte) b.length;
        System.arraycopy(b, 0, out, 24, b.length);
        int x = 0;
        for (int i = 2; i < out.length - 1; i++) {
            x ^= out[i] & 0xFF;
        }
        out[out.length - 1] = (byte) x;
        return hex(out);
    }

    private static String IssueCodeOf(Issue i) {
        return i.getCode();
    }

    private static byte[] hex(String s) {
        s = s.replace(" ", "");
        byte[] out = new byte[s.length() / 2];
        for (int i = 0; i < out.length; i++) {
            out[i] = (byte) Integer.parseInt(s.substring(i * 2, i * 2 + 2), 16);
        }
        return out;
    }

    private static String hex(byte[] b) {
        StringBuilder sb = new StringBuilder();
        for (byte x : b) {
            sb.append(String.format("%02X", x));
        }
        return sb.toString();
    }

    private ParseResult one(String h) {
        return engine.parse("gc-32960", "2016", "breton", h).get(0);
    }

    private static List<Node> children(ParseResult r) {
        return r.getBody().get(0).getChildren();
    }

    private static String value(List<Node> list, String label) {
        return list.stream().filter(n -> label.equals(n.getLabel())).findFirst().map(n -> n.getValue()).orElse(null);
    }

    private static Node node(List<Node> list, String label) {
        return list.stream().filter(n -> label.equals(n.getLabel())).findFirst().orElse(null);
    }

    private static String issues(ParseResult r) {
        return r.getIssues().stream().map(i -> i.getCode() + ": " + i.getMessage()).reduce((a, b) -> a + " / " + b).orElse("<无>");
    }
}
