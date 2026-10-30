# 结构追踪解锁计时

本次实施独立功能；已保存的 API 1.1 标准仍暂不实施。

## 行为

- 一个 YAML 映射 `runner-structure-tracker-unlock-time`，含 village、bastion_remnant、nether_fortress。整数秒，-1 禁用，0 立即可用，其他非负数为正式开局后的等待时间。默认三个值均为 0，保留既有默认行为。
- 指令以点路径修改子项，父项可查询完整映射；列表为同一组配置，子项本地化/悬停/数值预填保持现有交互。
- 修改成功后立即按本局 startedAt 重算，不计入开局倒计时，不因换角/复活重置。保存失败不改变生效值。
- 全 -1 时不发速通者指南针，已有指南针移除并关闭菜单；猎人指南针不受影响。新禁用/重新锁定的已选目标停止追踪。
- 禁用结构不显示；锁定结构显示倒计时、不能选择。服务端所有提交均复核状态、身份、授权和当前解锁状态。
- 倒计时 >=3600000ms 为 x.xh，>=60000ms 为 x.xmin，否则为向上取整的整数 s；一位小数使用 Locale.ROOT，不显示锁定中的 0s。
- Fabric 余下 1/2/3 个选项等分画布宽度。锁定时不产生 hover 提亮，允许光标变化时显示禁止指针；正中央倒计时使用该选项实际英文标题字号，水平/垂直居中。
- Paper Dialog 过滤禁用项，锁定按钮无 action，标签后追加倒计时，每秒更新。Esc/退出后停止刷新，不重新弹出。

## 固定跨模块合同

protocol 协议版本提升至 2，频道/Mod ID 不改。hello 数据形状不改，版本不兼容则回退原版 Dialog。

```text
protocol.TrackingAvailability(long villageMillis,long netherFortressMillis,long bastionRemnantMillis)
 validates each >=-1; -1 hidden,0 unlocked,>0 remaining
 remainingMillis(TrackingSelectionTarget); visible(target); unlocked(target); hasVisibleOptions(); static allUnlocked()
TrackingSelectionOpen adds TrackingAvailability availability as last component; old 4arg constructor delegates allUnlocked()
TrackingSelectionUpdate(int protocolVersion,UUID sessionId,long authorizationId,TrackingSelectionTarget currentSelection,TrackingAvailability availability)
 new message type6, same selection fields/state as open; updates existing matching UI, never opens a new UI
common.CountdownText.format(long remainingMillis):String
```

Paper presenter 与 publisher：

```text
TrackingSelectionPresenter.show(Player,UUID,long,TrackingTargetId,TrackingAvailability,Consumer<TrackingTargetId>,Runnable dismiss)
TrackingSelectionPresenter.update(Player,UUID,long,TrackingTargetId,TrackingAvailability)
 existing close/invalidate retained
TrackingClientPublisher.openTrackingSelection(Player,UUID,long,TrackingSelectionTarget,TrackingAvailability)
TrackingClientPublisher.updateTrackingSelection(Player,UUID,long,TrackingSelectionTarget,TrackingAvailability)
PaperTrackingClientPublisher.setCloseHandler(BiConsumer<Player,TrackingSelectionClose>)
```

客户端 Esc/界面替换发送现有 TrackingSelectionClose 作为取消；服务端版本校验后交给 closeHandler。选择成功、服务端关闭、断线不发送多余取消。服务端 router/presenter 记录活动授权，关闭后忽略后续更新。

Root 为 TrackingSelectionAuthorizationRegistry 增加 current(UUID)，为 CompassTrackingService 增加 cancelRunnerSelection(Player,UUID,long)。每秒 tick 为仍有有效授权的界面传最新状态；客户端每帧使用 System.nanoTime 推进收到的剩余时间，不依赖两机墙上时钟相同。配置保存触发即时 tick。

## 分工和验证

- [x] protocol/common：状态 DTO、编解码/版本、倒计时文本与针对性测试。
- [x] Fabric：两版布局/计时/鼠标状态/控制器更新与关闭，纯模型测试。
- [x] Paper 展示：publisher/router/Dialog 的状态同步和取消，避免旧回调和 Esc 重开，相关测试。
- [x] Root：配置原子保存、命令父项/子项、服务端可用性/发放/点击验证、文档和整合。
- [x] 两版构建、针对性检查、协议/资源/SDK 兼容与交付包核对；不执行完整游戏测试。

Ruling: 默认全部 0；运行中修改即时生效，以既有 startedAt 为基准。上述默认不改变旧服务器开局即可追踪的行为。

Ruling: Paper 原版空 action 按钮仍执行 Dialog 的 afterAction，故使用 NONE 防止锁定点击关闭界面；合法选择/退出由服务端显式关闭。Esc 在清理授权前发送有序 closeDialog，防止在途刷新包重新打开界面。回调按窗口身份和授权校验并缓存轮换，避免每秒注册大量回调。

验证：clean build、最终修复构建与两个 -PmcVersion 独立入口均通过。common 3、protocol 10、API 6、API-Paper 4、两版 Paper 各 102、两版 Fabric 各 5 项针对性检查，共 237 项全部通过。Esc 关闭顺序回归已观察失败后通过；独立代码审查发现的问题已修复并复审通过。

四个部署 JAR 和现有 SDK 已更新至本地 dist，校验和为 dist/SHA256SUMS.txt。所有部署包含协议 2，Java 字节码符合两版目标；Fabric 资源与修改前逐字节一致，SDK 仍为 1.0.0，API 1.1 计划文件内容未变。双语 123 键及占位符一致，映射配置 15 个顶层项/17 个叶项。报告：build/reports/structure-unlock-artifacts.json。

未运行游戏实例。实际 Fabric 倒计时绘制/鼠标形状、原版 Dialog 刷新/关闭时序及真实背包指南针发放需实服抽查；编译和模型/协议检查与实服验证分别记录。
