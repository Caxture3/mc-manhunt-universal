# ManhuntUniversal 穹猎追杀

适用于 Minecraft Paper 服务端与 Fabric 客户端的跨版本项目，支持 1.21.11 和 26.3。

## 安装

将对应游戏版本的 Paper 插件放入服务器 `plugins/` 目录。可选的 Fabric 模组放入客户端 `mods/` 目录，并安装对应版本的 Fabric Loader 和 Fabric API。部署文件位置见[构建产物](#构建产物)。

## 命令与配置

根命令为 `/mhu`。

- `/mhu join hunter|runner|random`：所有玩家均可选队，random 清除明确选择；不带队伍时显示可点击提示。
- `/mhu start [秒数]`、`status`、`stop`、`config [key] [value]`、`debug`：需要 `manhuntuniversal.admin`，默认 OP。`status` 显示本局参与者、时间和固定身份名单。

速通者预约满额时拒绝新选择并保留原队伍；重复选择不改变顺序。空闲时降低 `runner-count` 并成功保存后，从最后加入者开始将超额选择恢复为随机。倒计时或运行中修改只影响下一局，不清除预约或触发容量提示。退出会清空该玩家的选队；结算、停止或倒计时取消后保留在线玩家的明确选择与顺序，并恢复预览颜色。开始倒计时后不允许改队，也不会因配置变化或离线而重分配本局角色。

未开局时登录会收到选队提示。指定猎人不参与速通者随机抽取；开局仍至少需要一名猎人。选择 hunter 后名称牌和 Tab 为青色，runner 为红色；切回 random 时恢复原显示。`status` 同时显示存活速通者人数。

配置列表及单项查询显示本地化名称，鼠标悬停显示内部键。点击布尔项立即切换当前值并刷新该项；点击数值项会打开聊天输入，预填 `/mhu config <内部名称> `。布尔交互再次检查权限，旧消息也按当前配置值切换；按钮有效期为一小时，过期后可重新查询配置。

`hunter-start-wait-seconds` 默认0秒；开启开局重置后，猎人传送完成进入统一的失明/冻结等待，结束后才发追踪指南针。`player-start-pos` 分别配置两队的世界出生点、个人重生点或按顺序循环使用的自定义位置，并提供点击编辑。两项在倒计时开始时冻结，详见[开局位置与猎人等待](docs/start-setup.md)。

两个角色配置互相独立，均默认为 `false`：

| 配置项 | 开启后的行为 |
| --- | --- |
| `force-random-roles` | 立即清空选队，关闭登录选队提醒和 `/mhu join`，开局统一随机分配；已锁定对局不重分配。 |
| `hide-role-assignments` | 隐藏选队和实际分配的聊天内容、Tab 和名称牌颜色；保留选队功能、个人身份标题与指南针。 |

开关实时生效，关闭强制随机不会恢复旧选择。隐藏时 `status` 不显示双方名单，结算只显示胜方与原因。详见[角色配置说明](docs/role-options.md)。

默认配置及服务端语言文件在 `plugins/ManhuntUniversal/`。原配置语义保持：`runner-count` 默认 1；两种指南针、聊天前缀和 `reset-players-at-start` 默认开启。开局重置会按旧实现清理参与者物品、状态、经验、进度并传送至出生点；可通过配置关闭。重定位距离、冷却及搜索半径在下一局生效。

`tracking-distance-refresh-seconds` 控制指南针距离文本刷新，默认 `1.0` 秒，范围 `0.1`～`60.0`，实时生效。例如 `/mhu config tracking-distance-refresh-seconds 0.1`。按 20 TPS 向上取整到 tick；显示刷新与结构／传送门查询分离，调快显示不会提高大范围搜索频率。附近已生成结构会按实际结构部件校正目标，减少沿用远处旧目标的情况，详见[追踪性能与距离说明](docs/tracking-performance.md)。

追踪下界要塞时，默认指向选定要塞内距离玩家最近的已确认烈焰人刷怪笼；未找到有效刷怪笼时沿用原有结构位置。候选来自结构元数据并检查实际方块，不扫描整座要塞，也不会为寻找刷怪笼生成地形。

`runner-structure-tracker-unlock-time` 统一配置村庄、堡垒遗迹、下界堡垒和试炼密室的追踪解锁秒数；前三项默认 `0`，`trial_chambers` 默认 `-1`（禁用）；`-1` 禁用对应结构，全禁用时不发速通者结构指南针。计时从正式开局开始，配置即时生效。例如 `/mhu config runner-structure-tracker-unlock-time.village 120`；使用 `/mhu config runner-structure-tracker-unlock-time.trial_chambers 0` 可立即开放试炼密室追踪。禁用项隐藏，待解锁项显示倒计时并禁止选择，详见[结构追踪解锁说明](docs/structure-tracker-unlock.md)。

`allow-blaze-spawner-break` 默认为 `false`，只在正式游戏 RUNNING 时禁止破坏普通烈焰人刷怪笼，包含 OP 和局外玩家。空闲、倒计时及结算后允许破坏；其他种类刷怪笼不受此规则影响。通过 `/mhu config allow-blaze-spawner-break true` 或点击配置项可即时允许破坏。

`prevent-nether-portal-destruction` 默认为 `false`，开启后仅在正式游戏期间保护下界传送门的门体和必要门框，包含创造模式与 OP。通过 `/mhu config prevent-nether-portal-destruction true` 或点击配置项开启；详见[下界传送门保护说明](docs/nether-portal-protection.md)。

`nether-coordinate-scale` 默认为 `8`，接受不小于1的整数，全程修改所有玩家与实体的下界门坐标对应倍率。例如 `/mhu config nether-coordinate-scale 4`，成功保存后立即生效，指南针跨维度投影同步更新。详见[下界坐标倍率](docs/nether-coordinate-scale.md)。

可选优化默认关闭，可通过配置列表交互或以下命令开启，仅在正式游戏期间生效：

| 配置项 | 默认值 | 作用 |
| --- | --- | --- |
| `ore-fortune-level` | `0` | 矿物虚拟时运等级，整数 0–10；0 关闭，取配置与工具已有时运的较高值，精准采集保持原样。 |
| `blaze-rod-looting-level` | `0` | 烈焰棒虚拟抢夺等级，整数 0–5；0 关闭，取配置与玩家已有抢夺的较高值，不修改武器。 |
| `adjust-piglin-bartering` | `false` | 调整为平均每 64 金锭产出约 16 珍珠、10 黑曜石、3 瓶抗火（普通与喷溅合计）；每次仍一种结果。 |
| `hunter-end-restriction-enabled` | `false` | 任一存活速通者进入末地之前，禁止本局猎人放置末影之眼或进入末地；解锁持续到本局结束。 |

```text
/mhu config ore-fortune-level 3
/mhu config blaze-rod-looting-level 3
/mhu config adjust-piglin-bartering true
/mhu config hunter-end-restriction-enabled true
```

时运、烈焰棒和交易优化对正式游戏期间的所有玩家生效，空闲和倒计时不生效。详见[优化配置与概率说明](docs/optional-optimizations.md)。

`disable-piglin-brutes=false` 可在正式游戏中禁止猪灵蛮兵生成并移除现存蛮兵，普通猪灵不受影响。正常关服/重启会通过 `runtime-state.yml` 保存和恢复当前游戏；停服期间暂停计时，RUNNING 恢复不再重置玩家。选队只在同次运行中跨对局保留，退出仍清除。详见[续局与蛮兵配置说明](docs/runtime-continuity.md)。

调试角色可用 `/mhu debug role-reshuffle` 和 `/mhu debug role-set <玩家> <runner|hunter>`，仅正式游戏接受，支持离线参与者；详见[角色调试指令](docs/role-debug-commands.md)。管理权限现仅为 `manhuntuniversal.admin`；已有权限配置需手动迁移旧授权。

## 版本矩阵

| Minecraft | Paper API | Fabric API | 编译 / 运行 Java | 客户端构建 |
| --- | --- | --- | --- | --- |
| 1.21.11 | 1.21.11-R0.1-SNAPSHOT | 0.141.6+1.21.11 | 21 | remap Loom + Mojang mappings |
| 26.3 | 26.3.build.200-stable | 0.163.0+26.3 | 25 | 非 remap Loom |

构建使用 Gradle 9.8.2、Loom 1.18.4、Fabric Loader 0.19.5。依赖集中在 [版本目录](gradle/libs.versions.toml)，除 Paper 旧版官方 SNAPSHOT 坐标外均固定版本。

## 开始开发

安装 **JDK 21 和 JDK 25**，设置 `JAVA_HOME` 为 JDK 25，并让 Gradle 能发现 JDK 21。无需全局安装 Gradle，直接用 IDE 打开根目录、导入 Gradle 项目。

```powershell
# Windows：构建全部模块与两个版本
.\gradlew.bat clean build

# 只配置并构建一个版本，CI 也使用这个入口
.\gradlew.bat build '-PmcVersion=1.21.11'
.\gradlew.bat build '-PmcVersion=26.3'

# 查看本机可发现的 JDK
.\gradlew.bat -q javaToolchains

# 启动指定版本的 Fabric 开发客户端
.\gradlew.bat '-PmcVersion=1.21.11' :fabric-1.21.11:runClient
.\gradlew.bat '-PmcVersion=26.3' :fabric-26.3:runClient
```

Linux / macOS 使用 `bash ./gradlew` 替代 `.\gradlew.bat`。如果 JDK 未被自动发现，在个人 Gradle 配置中设置 `org.gradle.java.installations.paths`；不要将本机绝对路径提交到项目。

首次构建需联网下载 Gradle、Minecraft 与 Maven 依赖；Fabric 开发客户端首次启动还需下载游戏资源。

## 目录

```text
common/                 纯 Java 公共逻辑预留区（Java 21）
protocol/               有界的客户端协议与编解码（Java 21）
api/                    公共扩展接口与不可变数据（Java 21）
api-paper/              Paper 服务入口和事件 SDK（Java 21）
paper/
  shared/src/main/      两版本共享的服务端逻辑、入口、资源
  shared/src/test/      服务端逻辑的针对性检查
  1.21.11/              :paper-1.21.11，版本适配与构建
  26.3/                 :paper-26.3，版本适配与构建
fabric/
  shared/src/main/      共享资源、非客户端专属源码
  shared/src/client/    共享客户端入口和网络 payload
  1.21.11/              :fabric-1.21.11，版本适配与构建
  26.3/                 :fabric-26.3，版本适配与构建
gradle/                 Wrapper、版本目录、公共构建配置
docs/                   使用说明、设计与验证记录
```

每个版本模块使用 Gradle 默认的 `src/main/java`、`src/main/resources`；Fabric 另有 `src/client/java`。Paper 的按列 POI 读取放在版本适配目录，共享调度器分批读取并异步筛选坐标。Fabric 将渲染、输入、网络注册和 shader 差异放在版本目录，其余源码和素材共享。

## 构建产物

构建后在各版本模块的 `build/libs/` 中取得部署 JAR。

| 模块 | 部署产物（当前版本 1.0.0） |
| --- | --- |
| Paper 1.21.11 | `paper/1.21.11/build/libs/manhunt-universal-paper-mc1.21.11-1.0.0.jar` |
| Paper 26.3 | `paper/26.3/build/libs/manhunt-universal-paper-mc26.3-1.0.0.jar` |
| Fabric 1.21.11 | `fabric/1.21.11/build/libs/manhunt-universal-fabric-mc1.21.11-1.0.0.jar` |
| Fabric 26.3 | `fabric/26.3/build/libs/manhunt-universal-fabric-mc26.3-1.0.0.jar` |

Paper JAR 放到对应版本服务器的 `plugins/`；Fabric JAR 放到对应版本客户端的 `mods/`，并安装 Fabric Loader 和对应版本 Fabric API。不要安装 `-sources.jar` 或 `-dev.jar`，也不需要单独安装 `common`、`protocol` JAR。

这四个产物分别面向对应游戏版本。插件和模组显示名为 `ManhuntUniversal`，Java 根包为 `com.caxture.manhuntuniversal`。Fabric Mod ID、资源命名空间、协议频道和绑定指南针 PDC 命名空间继续保留兼容标识 `randomanhunt`；管理权限为 `manhuntuniversal.admin`。客户端只安装对应版本的一份配套模组。

聊天前缀按客户端语言显示为 `穹猎追杀` / `Manhunt Universal`。升级不会自动改写 `plugins/ManhuntUniversal/lang/` 下的旧语言文件；已有服务器请按 [语言更新说明](docs/lobby-config.md) 手动更新相关条目。

SDK 独立版本为 **1.2.0**，六个 SDK JAR 位于 `dist/sdk/`；四个可部署插件/模组及 SHA-256 清单位于 `dist/`。扩展可通过服务端 API 注册新追踪目标，无需附加 Fabric 模组；自定义材质可选用资源包，缺失时自动回退。两端选择协议为 **3**，旧模组或未安装模组的客户端使用 Paper Dialog。强制追踪支持双方角色、任意玩家与实时目标。接入见 [API 1.1 指南](docs/extension-api-1.1.md)和 [API 1.2 追踪控制指南](docs/extension-api-1.2.md)。

## 相关说明

- [选队、配置交互和外部语言文件维护](docs/lobby-config.md)
- [速通者淘汰、身份颜色和刷怪笼保护](docs/elimination-and-colors.md)
- [下界传送门保护](docs/nether-portal-protection.md)
- [蛮兵开关、跨局选队与重启续局](docs/runtime-continuity.md)
- [矿物时运、猪灵交易和末地限制](docs/optional-optimizations.md)
- [强制随机和隐藏角色分配](docs/role-options.md)
- [扩展 API 1.1：配置、显示与动态追踪目标](docs/extension-api-1.1.md)
- [对局/角色 API 与轮换插件示例](docs/extension-api.md)
- [结构追踪解锁时间与客户端兼容](docs/structure-tracker-unlock.md)
- [结构与传送门追踪的查询方式和性能边界](docs/tracking-performance.md)

技术依据：[Paper 项目配置](https://docs.papermc.io/paper/dev/project-setup/)、[Paper Java 要求](https://docs.papermc.io/paper/getting-started/)、[Fabric Loom 插件区分](https://docs.fabricmc.net/develop/loom/)。
