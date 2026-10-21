# AGENTS.md — jtt-protocol-parse

> 协作者/Agent 施工指南。**完整设计见 `docs/设计方案.md`（唯一权威文档，改设计先改它）**。

## 项目是什么

输入一段完整的 16 进制国标报文 → 输出：
1. **报文中文名**（如「实时信息上报 · 整车数据」）
2. **字段级明文**（中文标签 + 工程值 + 单位/枚举中文 + 原始 hex 对照）

首个协议 **GB/T 32960.3-2016**（新能源国标），预留多协议 / 多版本 / 厂商扩展。

## 工作铁律（优先级最高，违反即做错）

1. **不要过度实现**：不为"未来可能需要"写代码、不建备用抽象层。一个抽象只有出现第二处真实需求才允许提出来。
2. **简洁、可读性高**：命名达意、方法短小、注释只写"为什么"；报文/字段中文名与标准原文一致。
3. **代码规范**：
   - 用 Lombok（@Data + @Accessors(chain)）减样板，不写手生成器/公共字段；
   - 跨层结构用领域类承载：`CommandTable / SubTypeTable / ProtocolSpec / Frame`，禁止在引擎与加载器里裸操作 Map；
   - 问题码集中在 `core/result/IssueCode`，禁止散落硬编码字符串；
   - 后端依赖仅 `spring-boot-starter-web` + jackson-yaml + lombok；前端零构建（Vue 3 本地 vendor，无 npm/Vite）。
4. **零数据库**：解析引擎无状态；协议描述是只读 YAML，随 Git 管理，永不入库。
5. **DSL 收敛**：YAML 描述能力只许用设计方案 §5.3 清单内的语法；加新语法 = 先改文档再实现。

## 架构速览

- 单 Maven 模块，包名 `com.xx` 不变：
  - `src/main/java/com/xx/jttprotocolparse/core/model/`：协议描述模型（YAML 直接映射的目标）
  - `core/`：解析引擎 hex→frame→route→fields→result，纯 Java 零 Spring 依赖
  - `web/`：REST 薄层（`/api/parse`、`/api/protocols`），只装配 core
- **帧层是代码**：每协议一个 `FrameAdapter`（拆包/长度/校验），厂商与版本共用，不复制。
- **字段层是配置**：`resources/protocols/gc-32960/<版本>/<厂商>.yaml`，单文件嵌套：命令码 → `body`（字段列表）/ `subtype`（类型码 → name / body）；两者可并存，body 是子报文前的固定前缀。字段不写 `id`，节点/path/`$` 引用统一用 `label`。每个厂商一份**完整独立文件**，不做 overlay；`protocols.yaml` 按「协议 → 版本 → 厂商」登记，未登记或文件缺失直接解析失败。
- 前端 `web/` 目录：Vue 3 零构建单页（`index.html` + `vendor/vue.global.prod.js`）。

## 施工纪律

- **改报文内容 = 只改 YAML + 补 samples 回归样例**，不动 Java；加协议才写新 FrameAdapter。
- 解析是无状态纯计算：`hex in → ParseResult out`；解析发现的校验错/未知值**必须写入 issues 并定位位置**，不静默、不抛异常中断解析。
- historical note：不确定的字段**禁止凭记忆编造**，先用 `raw` 保留原样 + 注释 `TODO: 待标准核对`。
- 每个报文类型至少一条真实/构造样例测试（samples 回归），防止改 YAML 时悄悄破坏。

## 里程碑状态

| 里程碑 | 说明 | 状态 |
|---|---|---|
| M1 | 引擎骨架 + 32960 帧层 + 基础报文（登录/心跳/实时上报外层/整车数据）+ REST + 页面 | ✅ 完成（回归测试全绿） |
| M2 | 全数据体（驱动电机/燃料电池/发动机/位置/极值/报警/储能）——需对标准原文核对 | 待办（子类型已登记，body 留空即 raw 占位） |
| M3 | 前端联调完善 | 待办 |
| M4 | 厂商独立配置文件联调（等真实厂商报文样例） | 待办 |
