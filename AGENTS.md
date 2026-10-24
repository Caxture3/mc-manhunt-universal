# 项目约束

- 当前仅搭建开发骨架，目标为 Minecraft 1.21.11、26.3 的 Paper 插件和 Fabric 客户端模组。
- 猎人玩法与辅助功能须从用户指定的旧工程迁移。旧工程已有功能不得重新实现；迁移前先确认具体来源，不因相邻目录名称自行认定。
- 迁移时保留原有行为、配置语义和通信协议；版本适配仅处理必要的 API / 映射差异。不得借迁移擅自设计新玩法或协议。
- `common`、`protocol` 使用 Java 21，不得依赖 Bukkit、Paper、Fabric 或 Minecraft 类。
- 平台公共源码放在 `paper/shared`、`fabric/shared`；版本相关源码放在对应版本模块，避免在公共逻辑中散布版本判断。
- 依赖坐标统一放在 `gradle/libs.versions.toml`。1.21.11 模块编译为 Java 21，26.3 模块编译为 Java 25。
- Fabric 1.21.11 使用 remap Loom 和 Mojang mappings；26.3 使用非 remap Loom。两者必须独立验证。
- 修改构建后执行 Gradle 构建并检查产物；实际游戏运行验证与编译验证分别记录，不得混称。
- 当前名称和包名为骨架暂定值。旧协议的频道名称不由新 Mod ID 自动推导或替换。

