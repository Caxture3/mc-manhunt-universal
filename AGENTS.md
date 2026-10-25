# 项目约束

- 目标为 Minecraft 1.21.11、26.3 的 Paper 插件和 Fabric 客户端模组。
- 用户指定迁移来源为 `E:\coding\mc-randomanhunt-plugin-26.3`，迁移其除随机轮换角色以外的全部 Paper 功能和 Fabric 显示功能。旧工程已有功能不得重新实现。
- 开局前允许玩家选择 hunter/runner/random；指定速通者优先，仅从 random 补足人数，每局身份固定。缺少随机候选时拒绝开局。
- 选队按 UUID 存于内存，离线或对局结束/停止/倒计时取消清空，倒计时开始锁定。仅空闲时允许缩容清除最后加入的超额速通者选择；倒计时/运行中只保存下一局配置，不清除选择或触发 capacity-cleared。
- 选队立即预览 Tab/名称牌颜色：猎人青色、速通者红色；random/离线/清空时恢复原显示，正式游戏开始前先移除预览再记录原始状态。
- 速通者逐人淘汰：死亡后自动复活进入旁观，全部速通者死亡才判负。淘汰者不可使用指南针、被猎人追踪或绑定目标末地；断线不算死亡，重连保持淘汰。结算/停止恢复淘汰前模式。
- `allow-blaze-spawner-break=false` 默认禁止正式 RUNNING 游戏期间破坏普通烈焰人刷怪笼；空闲/倒计时不限制，true 则允许，修改实时生效。
- 可选优化仅 RUNNING 生效：`ore-fortune-level=0`（0–10，虚拟工具查询原版矿物掉落，不修改玩家工具）、`adjust-piglin-bartering=false`（单结果，64 金期望 16 珍珠/10 黑曜石/3 普通与喷溅抗火，其他结果保持相对比例）、`hunter-end-restriction-enabled=false`（存活速通者实际进入末地后本局永久解锁猎人放眼和进入限制）。
- README 面向公开 GitHub 仓库；顶部不写玩法功能清单或当前开发进度，不写本机参考工程绝对路径。
- 迁移时保留原有行为、配置语义和通信协议；版本适配仅处理必要的 API / 映射差异。不得借迁移擅自设计新玩法或协议。
- `common`、`protocol` 使用 Java 21，不得依赖 Bukkit、Paper、Fabric 或 Minecraft 类。
- 平台公共源码放在 `paper/shared`、`fabric/shared`；版本相关源码放在对应版本模块，避免在公共逻辑中散布版本判断。
- 依赖坐标统一放在 `gradle/libs.versions.toml`。1.21.11 模块编译为 Java 21，26.3 模块编译为 Java 25。
- Fabric 1.21.11 使用 remap Loom 和 Mojang mappings；26.3 使用非 remap Loom。两者必须独立验证。
- 修改构建后执行 Gradle 构建并检查产物；实际游戏运行验证与编译验证分别记录，不得混称。
- 构建产物名称保持 `manhunt-universal-*`。命令为 `/mhu`，无 `/rmh` 别名；join 对所有玩家开放，其他子命令沿用 `randomanhunt.admin`。源码包及 `randomanhunt` 协议/资源/PDC 命名空间保留。
- 本地化前缀为英文 `Manhunt Universal`、中文 `穹猎追杀`。结算文本不再生成或展示轮次历史，内部历史结构保留。
- 配置列表显示本地化名称，hover 显示内部键；布尔点击实时检查权限和当前值，数值点击预填带末尾空格的 `/mhu config <key> `。
- 不自动修改服务器上已有的外部语言文件；新默认文本和键仅写入仓库内置资源，并提供手动更新说明。
- 本次按用户要求执行精简验证：两个版本构建、角色固定的针对性检查与部署 JAR 检查；不要求全面游戏测试。

