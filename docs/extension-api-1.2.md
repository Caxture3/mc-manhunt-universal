# API 1.2：强制追踪

SDK **1.2.0**，插件/Fabric **1.0.0**，协议 **3**。保留 API 1.0/1.1 二进制合同；新增入口是 `api.tracking().control()`。安装现有通用 Fabric 模组即可，扩展不需要另写客户端。此接口控制已有指南针，不向选择菜单添加被强制追踪的目标。

注册前检查 `FORCED_TRACKING`、`REALTIME_PLAYER_TRACKING` 能力，并在 `ExtensionOptions` 中申请独占 `ControlDomain.TRACKING`。同一时刻仅一个注册可持有此域；它可以分别控制多个玩家。目录注册与定位器替换不要求此域，强制覆盖要求此域。

| 请求 | 效果 |
| --- | --- |
| `TrackingOverrideRequest.defaults()` | DEFAULT：恢复角色默认追踪和单独保存的手动选择 |
| `target("core:nether_fortress")` | TARGET：追踪目录内的结构/扩展目标，支持双方角色 |
| `player(uuid)` | TARGET：追踪指定玩家，包括局外玩家 |
| `players(selector)` | TARGET：实时选择玩家，例如最近猎人 |
| `disabled()` | DISABLED：保留绑定指南针，停止提供追踪方向和选取入口 |

TARGET/DISABLED 绕过角色指南针开关、菜单隐藏与结构解锁时间；依然遵守淘汰、待重置状态、`core:tracking-compass-use`/`core:tracking-compass-grant` 功能接管。不会改写配置。DEFAULT 恢复时，原选择仍须通过当前配置授权。覆盖期间手动选取/clear/refresh 被拒绝，菜单会关闭；`menu()` 仍可作为目录查询。强制目录目标如需刷新可调用原 `invalidate(owner, TrackingInvalidation.target(id))`，仍遵守定位冷却/预算。玩家目标不受结构冷却限制。

所有调用在服务端主线程执行：

```java
var match = api.matches().current().orElseThrow();
var control = api.tracking().control().snapshot(viewerId).orElseThrow();
var guard = new TrackingControlMutation(new TrackingMutation(
    new MutationContext(owner, match.id(), match.revision(), "assigned task"), viewerId),
    control.revision());
var result = api.tracking().control().apply(guard,
    TrackingOverrideRequest.target("core:village"));
if (result.successful()) taskHandle = result.value().orElseThrow();
```

`TrackingControlMutation` 必须填写精确的对局 revision 与控制 revision，不接受 -1。目录 revision 可通过原 `TrackingMutation` 第三个参数额外校验。session UUID、任一预期 revision 不符时拒绝；同一玩家替换目标、清理或关闭当前句柄都会推进控制 revision，独立于对局 revision。排队工作应保存当时的 guard，提交时不能刷新它来绕过过期检测。任务结束直接关闭它自己的句柄；旧句柄不会清除新任务。DEFAULT 返回无活动句柄。

覆盖可设置给离线且未淘汰的参赛者，重连按当前覆盖生效。切换角色、淘汰、正式结束/停止、插件注销会清除覆盖；玩家暂时退出不会清除。扩展主动关闭当前句柄也恢复 DEFAULT。服务端重启不序列化覆盖/选择器/注册；扩展负责保存自己的任务并在续局后重新注册、查询新 revision 后恢复。不要在扩展回调中关闭句柄或写 API，改为安排主线程下一 tick。

玩家选择器接收不可变 `PlayerTrackingContext`：包含观察者位置、对局及候选人 UUID/名称/在线状态/可选角色/位置。无角色表示局外玩家；淘汰者与无已知位置者不进入候选。返回候选 UUID 或 `Optional.empty()`，服务端再次校验；非法 UUID、异常、未知位置会停止指向，不会改追踪其他人。离线使用最后已知位置，不读取离线玩家文件、不加载玩家。核心续局中的参赛者位置可沿用；局外玩家观测仅在本进程、本局内保留，最多 4096 名（最近观测优先）。未观测到的离线玩家无法定位。

选择器在主线程按 `tracking-distance-refresh-seconds` 采样，首次赋值/状态刷新会立即采样；必须快速完成，不能扫世界或等待 I/O。返回目标移动后无需反复写覆盖。跨维度沿用已有传送门引导预算；同维度更新方向与距离。高频显示刷新不触发结构定位。`tracking().snapshot()` 返回当前有效目录/玩家定位，玩家 ID 表示为 `player:<UUID>`；`control().snapshot()` 返回控制 revision、模式、目录/固定玩家目标与所有者，动态选择结果在定位快照中查询。手动选择仍通过 `menu().selection()` 查询。

可编译示例见 [TrackingControlExample](../examples/api-1.1/src/main/java/example/mhu/TrackingControlExample.java)，包括最近猎人、固定玩家、结构、暂停、恢复及安全任务完成。暂停追踪使用内置语言键 `tracking.actionbar.disabled`。扩展接管指南针时，核心仅保留绑定说明，玩法提示由扩展提供。

## 正式游戏显示

扩展可以通过现有Paper显示接口替换指南针名称、Tooltip、ActionBar和结算内容。回调接收当前接收者，扩展可按玩家或语言生成文案。以下ID对应稳定的显示入口。

| ID | 频道 | 用途 |
| --- | --- | --- |
| `core:compass.name` | `ITEM_NAME` | 指南针名称 |
| `core:compass.lore` | `ITEM_LORE` | 完整Tooltip行列表 |
| `core:hunter-start.countdown` | `ACTIONBAR` | 猎人开局等待提示 |
| `core:result.chat` | `CHAT` | 结算聊天内容 |
| `core:result.title` | `TITLE` | 结算标题与副标题 |

例如，为指定玩家提供完整指南针Tooltip。

```java
api.presentation().intercept(owner,
    new PresentationSelector(Set.of(PresentationChannel.ITEM_LORE),
        "core:compass.lore", Optional.of(playerId), false),
    100, request -> PresentationDecision.replace(
        new PresentationPayload(List.of(Component.text("追踪当前任务目标")),
            Optional.empty(), Optional.empty())));
```

`Victory.reason` 为面向玩家的结算原因。填写空串或空白时，核心省略原因及其分隔符。填写具体文案时按该文案展示。扩展也可通过 `appendResult` 增补结果，或拦截 `core:result.chat` 替换整段结算。显示注册随owner句柄关闭而释放，物品显示更新可使用既有 `refresh(owner, request)` 的 `ITEMS` 范围。

已有外部语言文件中的 `tracking.compass.lore-extension` 与 `reason.extension-finished` 保留在磁盘中，核心的这两个游戏显示入口已使用省略规则。新增无原因模板为 `game.result.full-report-no-reason` 和 `game.result.hidden-report-no-reason`，需要自定义时可手动补入外部语言文件。

## 最近新增配置的API入口

以下设置已通过 `api.configuration()` 开放。`find(key)` 返回 `savedValue`、`effectiveValue` 和 `nextMatchValue`，扩展可使用现有 `lock`、`override`、`update` 与监听入口。

| 配置路径 | 数据与生效时机 |
| --- | --- |
| `player-start-pos.hunter.mode`、`player-start-pos.runner.mode` | 模式字符串，`NEXT_MATCH` |
| `player-start-pos.hunter.custom`、`player-start-pos.runner.custom` | 不可变位置映射列表，包含世界UUID/名称、XYZ及yaw/pitch，`NEXT_MATCH` |
| `hunter-start-wait-seconds` | 整数秒，`NEXT_MATCH` |
| `nether-coordinate-scale` | 整数倍率，`IMMEDIATE` |
| `tracking-distance-refresh-seconds` | 小数秒，`IMMEDIATE` |

倒计时开始后，`effectiveValue` 代表当前冻结配置，`nextMatchValue` 代表后续对局配置。无活动对局时两者反映当前生效值。位置方向完整缺失的旧列表条目会规范化为0 0。猎人等待的显示文本还可通过 `hunter-start.countdown` 文本覆盖与上表的ActionBar显示入口定制。
