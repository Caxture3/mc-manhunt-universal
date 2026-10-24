# 项目约束

- 目标为 Minecraft 1.21.11、26.3 的 Paper 插件和 Fabric 客户端模组。
- 用户指定迁移来源为 `E:\coding\mc-randomanhunt-plugin-26.3`，迁移其除随机轮换角色以外的全部 Paper 功能和 Fabric 显示功能。旧工程已有功能不得重新实现。
- 保留开局随机选定速通者，每局身份固定；移除局中轮换调度、手动重抽 debug、相关配置与文案。
- 迁移时保留原有行为、配置语义和通信协议；版本适配仅处理必要的 API / 映射差异。不得借迁移擅自设计新玩法或协议。
- `common`、`protocol` 使用 Java 21，不得依赖 Bukkit、Paper、Fabric 或 Minecraft 类。
- 平台公共源码放在 `paper/shared`、`fabric/shared`；版本相关源码放在对应版本模块，避免在公共逻辑中散布版本判断。
- 依赖坐标统一放在 `gradle/libs.versions.toml`。1.21.11 模块编译为 Java 21，26.3 模块编译为 Java 25。
- Fabric 1.21.11 使用 remap Loom 和 Mojang mappings；26.3 使用非 remap Loom。两者必须独立验证。
- 修改构建后执行 Gradle 构建并检查产物；实际游戏运行验证与编译验证分别记录，不得混称。
- 构建产物名称保持 `manhunt-universal-*`。迁移源码保留 `com.caxture.randomanhunt` 包、`/rmh` 命令、`randomanhunt.admin` 权限和 `randomanhunt` 协议/资源命名空间；不得因重命名破坏兼容性。
- 本次按用户要求执行精简验证：两个版本构建、角色固定的针对性检查与部署 JAR 检查；不要求全面游戏测试。

