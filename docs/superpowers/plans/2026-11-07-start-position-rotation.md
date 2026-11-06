# 开局自定义位置方向与世界更新 Implementation Plan

> **For agentic workers:** Use superpowers:executing-plans to implement the following tasks inline, then request one final read-only review.

**Goal:** 自定义开局位置保存世界、XYZ及yaw/pitch，更新命令、列表与添加按钮，移除列表末尾的下一局提示。

**Architecture:** 扩展内部StartPosition数据，兼容缺少方向的旧记录；沿用StartupPlan冻结与现有传送、配置保存入口。命令解析和世界显示单独放在command包的小型助手中，位置与方向进入原有续局数据链。

**Tech Stack:** Java21/25、Paper共享源码、Adventure、Gradle/JUnit。

**Spec:** 用户本次四条要求及项目AGENTS.md。

## Global Constraints

- 产品1.0.0、API/SDK1.2.0、协议3；Fabric无需修改。
- NEXT_MATCH冻结、原子保存和权限/扩展配置锁继续有效；完整旧位置缺失yaw/pitch时补0，部分损坏记录仍拒绝。
- 世界UUID仍为身份，保留同世界XYZ去重及列表顺序；合法朝向为有限float。
- 添加与手动移除接受XYZ、可选yaw/pitch、可选世界；缺省world为overworld，支持单独指定world。移除继续按世界UUID与XYZ匹配。
- 聊天建议在配置消息生成时捕获玩家位置与方向；原版suggest_command内容随消息固定，重新查询可更新。世界支持overworld/the_nether/the_end、已加载世界名和UUID，扩展多世界保持区分。
- 在当前工作区完成精简验证、两版构建、交付校验，自动本地commit，不push。

## Review Focus

- 旧续局冻结映射补齐方向后仍与保存的StartupPlan一致，不重置本局目标。
- 相同坐标不同世界可共存；同世界不同朝向仍视为重复坐标。
- 未加载世界、非有限坐标/方向及单个方向参数不得落盘。
- 添加按钮带实际玩家世界，多个同维度类型世界不会被混淆。
- 循环分配、重启和离线补偿保留方向，删除按钮保留修订号及精确记录保护。

### Task 1: 数据、分配与旧快照兼容

**Files:** config/StartPosition.java、player/StartDestination.java、player/StartupPlan.java、persistence/StartupCheckpointMigration.java及对应已有测试。

- [x] 为旧记录补0、方向往返/无效方向、循环分配方向及冻结映射迁移写失败检查并运行。
- [x] 扩展StartPosition为yaw/pitch字段，保留旧构造入口；统一StartDestination方向表示，自定义分配读取保存方向；旧冻结位置映射规范化后校验。
- [x] 运行针对性检查，确认旧记录与新记录均能保存、冻结和续局。

### Task 2: 命令、展示、交付

**Files:** command/StartPositionArguments.java、command/StartPositionCommands.java、双语内置资源、docs/start-setup.md、AGENTS.md及新增命令检查。

- [x] 为省略参数、完整参数、世界别名/UUID、多世界、位置建议与格式写失败检查。
- [x] 实现参数/世界助手；列表显示世界XYZ和yaw/pitch；添加建议带完整现场数据，去除结尾提示，更新补全/用法及公开文档。
- [x] 独立构建两版，核对部署JAR/SDK/摘要；只读审查并处理重要问题，记录未进行实机联机验证；本地提交。

完成：两版构建及747项检查通过，一次独立只读审查无新增问题，交付文件与摘要已核对；实际联机点击/多世界传送/重启未执行。详见docs/verification/start-position-rotation.md。
