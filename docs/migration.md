# 旧工程迁移记录

来源：`E:\coding\mc-randomanhunt-plugin-26.3`。完成日期：2026-10-25。

## 保留范围

Paper 的配置与双语文本基础设施、权限与管理命令、参与者锁定、初始随机选人、倒计时、失败安全重置、断线重连与待重置重试、胜负和目标末地检查、身份显示与恢复、结果报告、绑定指南针、结构搜索与传送门引导、协议握手和选择授权、Paper Dialog 回退、烈焰人刷怪笼保护均从原工程迁入。

Fabric 保留原三栏 UI 的布局、中央裁切、图文透明度、双语名称、点击/重复选择/关闭行为、声音，以及全部图片、语言、shader 资源。26.3 实现保持原样；1.21.11 仅处理版本 API 差异。

迁移初版保留了原命令、Java 包、权限及通信标识，协议模块最初逐字节复制。后续按用户要求，命令改为 `/mhu`，Java 根包改为 `com.caxture.manhuntuniversal`，入口类改为 `ManhuntUniversalPlugin` / `ManhuntUniversalClient`。协议编码、`randomanhunt.admin`、Fabric Mod ID、`randomanhunt` 协议/资源/PDC 命名空间继续兼容。插件/模组显示名和外部 JAR 名称为 ManhuntUniversal；插件数据目录为 `plugins/ManhuntUniversal`。

## 排除范围

移除 `SwitchSchedule`、`RoleChange`、`DebugCommandHandler`，以及局中轮换的调度任务、状态修改入口、参数与配置校验、显示回调和语言文案。初始抽签使用原 Fisher-Yates 算法；速通者列表改为 final 的不可变列表，本局没有重新分配入口。

保留对局时间和结果记录，正常对局只有一轮历史。倒计时取消不会创建历史。没有添加手动指定角色或新玩法。

## 版本适配

| 部分 | 26.3 | 1.21.11 |
| --- | --- | --- |
| Java 字节码 | 25 / major 69 | 21 / major 65 |
| Paper 传送门定位 | 原 `World.locateNearestPoi` 调用 | 使用缓存反射调用同样的原版 POI 查询，`Occupancy.ANY`，保持原同步队列与搜索半径；不扫描方块或创建传送门 |
| Fabric 渲染 | 原 RenderPearl 管线与 `extractRenderState` | 适配 Blaze3D、`GuiGraphics` 与 `render`，同步调整 shader uniform 布局 |
| Fabric 输入 | 原主鼠标键编号 | 使用 1.21.11 的主键编号 0 |
| Fabric 网络 | 原 registry 与屏幕访问 API | 对应 `playC2S` / `playS2C` 与 `minecraft.screen` |

1.21.11 的 Paper JAR 标记 Mojang mappings，避免旧插件重映射干扰反射名称。NMS 适配独立放在该版本目录；26.3 不加载该实现。该部分依据 [Paper 运行时映射说明](https://docs.papermc.io/paper/dev/userdev/) 和实际 1.21.11 Minecraft JAR 的公共签名核对。
