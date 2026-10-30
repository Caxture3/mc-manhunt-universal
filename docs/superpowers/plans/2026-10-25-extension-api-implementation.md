# Extension API Implementation Plan

目标：实施 `docs/extension-api-proposal.md` 六组公共能力。默认玩法不增加自动轮换；纯 Java API 21、Paper 事件 API 21，服务端两版共同实现。

## 分工与固定接口

- api / api-paper：公共合同、事件、模块构建、公共合同测试。不得修改 Paper 内部实现。
- paper extension：新增 `paper/shared/.../extension` 的服务实现、注册表、快照映射与钩子。不得修改现有 game/runtime 等文件。
- 主代理：内部状态/角色同步、协调器/生命周期、Gradle 嵌入/入口、验证与文档。

公共根包 `com.caxture.manhuntuniversal.api`：

```text
ManhuntUniversalApi.apiVersion():String; capabilities():Set<Capability>;
 matches():MatchQuery; control():MatchController; roles():RoleController; participants():ParticipantController
MatchQuery.current():Optional<MatchSnapshot>
MatchSnapshot(UUID id,long revision,MatchState state,Instant createdAt,Optional<Instant> startedAt,
 Map<UUID,ParticipantSnapshot> participants,Optional<UUID> targetEndWorldId)
ParticipantSnapshot(UUID id,String name,TeamRole role,boolean eliminated)
MatchState COUNTDOWN,RUNNING,SETTLING; TeamRole RUNNER,HUNTER; MatchWinner RUNNERS,HUNTERS
Capability MATCH_QUERY,MATCH_CONTROL,ROLE_CONTROL,PARTICIPANT_CONTROL,ELIMINATION_POLICY,VICTORY_POLICY,PAPER_EVENTS
ControlDomain MATCH,ROLES,PARTICIPANTS,ELIMINATION,VICTORY
ExtensionRegistration extends AutoCloseable: id():String; controls():Set<ControlDomain>; active():boolean; close():void
ExtensionOptions(String id,Set<ControlDomain> controls,Optional<EliminationPolicy> eliminationPolicy,Optional<VictoryPolicy> victoryPolicy)
ExtensionOptions(String id,Set<ControlDomain> controls) convenience constructor
RegistrationResult(OperationStatus status,Optional<ExtensionRegistration> registration,String message)
MutationContext(ExtensionRegistration extension,UUID sessionId,long expectedRevision,String reason)
MutationContext(ExtensionRegistration,UUID,String) defaults revision=-1
OperationStatus OK,WRONG_THREAD,SERVICE_UNAVAILABLE,INVALID_ARGUMENT,NO_ACTIVE_MATCH,STALE_SESSION,STALE_REVISION,
 INVALID_STATE,INVALID_ASSIGNMENT,PLAYER_UNAVAILABLE,CANCELLED,CONTROL_CONFLICT,UNREGISTERED_EXTENSION,BUSY,START_REJECTED
OperationResult(OperationStatus status,String message,Optional<MatchSnapshot> match); successful():boolean
MatchController.start(ExtensionRegistration,int countdownSeconds):OperationResult
MatchController.stop(MutationContext):OperationResult; finish(MutationContext,Victory):OperationResult
RoleController.assign(MutationContext,Map<UUID,TeamRole>):OperationResult
RoleController.reroll(MutationContext,int runnerCount):OperationResult
ParticipantController.eliminate(MutationContext,UUID):OperationResult; revive(MutationContext,UUID):OperationResult
Victory(MatchWinner winner,String reason)
EliminationContext(MatchSnapshot match,UUID playerId,String cause)
EliminationDecision DEFAULT,PREVENT,ELIMINATE; EliminationPolicy.decide(EliminationContext):EliminationDecision
VictoryContext(MatchSnapshot match,Victory proposed)
VictoryDecision(Action action,Optional<Victory> replacement); Action DEFAULT,DEFER,REPLACE
 static defaults(),defer(),replace(Victory); VictoryPolicy.evaluate(VictoryContext):VictoryDecision
ChangeSource(String extensionId,String reason)
```

`com.caxture.manhuntuniversal.api.paper.PaperManhuntUniversalApi extends ManhuntUniversalApi` adds `register(Plugin owner,ExtensionOptions):RegistrationResult`。服务同时注册两个接口类，纯 API 不含 Bukkit。

事件包 `com.caxture.manhuntuniversal.api.paper.event`，每个具体类独立 HandlerList；访问器采用 getMatch/getPlayerId/getCause 等 JavaBean 命名：

```text
MatchPreparingEvent(MatchSnapshot,int countdownSeconds) Cancellable
MatchCountdownStartedEvent(MatchSnapshot,int countdownSeconds)
MatchStartedEvent(MatchSnapshot)
MatchEndedEvent(MatchSnapshot,String reason,Optional<Victory>)
RolesChangingEvent(MatchSnapshot before,Map<UUID,TeamRole> proposed,ChangeSource) Cancellable
 getBefore/getProposed/getSource
RolesChangedEvent(MatchSnapshot before,MatchSnapshot after,ChangeSource) getBefore/getAfter/getSource
ParticipantEliminatingEvent(MatchSnapshot,UUID playerId,String cause) Cancellable
ParticipantEliminatedEvent(MatchSnapshot,UUID playerId,String cause)
ParticipantRevivedEvent(MatchSnapshot,UUID playerId)
TargetEndEnteredEvent(MatchSnapshot,UUID playerId,UUID worldId)
MatchFinishingEvent(MatchSnapshot,Victory) Cancellable, getVictory/setVictory
```

Paper 内部桥接 `extension.GameExtensionHooks`（由 extension 分工实现）：

```text
NONE static no-op instance; default <T> T inMutation(Supplier<T>); default boolean mutationActive()
default boolean preparing(GameSession,int) =true
default void countdown(GameSession,int); started(GameSession)
default void ended(GameSession,TerminationReason,GameResult nullable)
default boolean shouldEliminate(GameSession,UUID,String cause) = session.isRunnerAlive(id)
default void eliminated(GameSession,UUID,String cause); revived(GameSession,UUID)
default void endEntered(GameSession,UUID playerId,UUID worldId)
default Optional<GameResult> decidingVictory(GameSession,GameResult) =Optional.of(result)
```

服务实现类 `extension.PaperExtensionService(JavaPlugin,GameManager,GameStartCoordinator,ActiveGameCoordinator,Clock)` implements PaperManhuntUniversalApi,GameExtensionHooks,Listener；提供 `shutdown()`。快照映射可放单独 helper。主代理在入口设置 active.setExtensionHooks(service)、manager.onVictoryDecision(service::decidingVictory)，注册服务/监听。

内部能力由主代理提供：

```text
GameSession.revision():long; isEliminatedParticipant(UUID):boolean; livingHunterIds():List<UUID>
GameManager.assignRoles(UUID,Map<UUID,game.Role>,Instant):boolean (all roster keys; eliminated roles unchanged; living runner+hunter)
GameManager.randomRunners(List<UUID>,int):List<UUID> (0/all allowed, otherwise original algorithm)
GameManager.onVictoryDecision(BiFunction<GameSession,GameResult,Optional<GameResult>>)
GameManager.eliminateParticipant(UUID,UUID,Optional<UUID> killer,Instant):Optional<GameResult>
GameManager.reviveParticipant(UUID,UUID):boolean
GameManager.finishCustom(UUID,GameWinner,String reason,Instant):Optional<GameResult>
GameResult adds customReason():String, keeps original constructor overload; GameResultReason.CUSTOM
TerminationReason adds ROLES_CHANGED,EXTENSION_FINISHED,START_CANCELLED
GameStartCoordinator.startForApi(int):OperationStatus (reuse original start flow)
ActiveGameCoordinator.setExtensionHooks(GameExtensionHooks)
ActiveGameCoordinator.rolesChanged(GameSession):void (tracking and presentation rebuild; no resets)
ActiveGameCoordinator.eliminateForApi(UUID,UUID):boolean (full hook/spectator/cleanup path)
ActiveGameCoordinator.reviveForApi(UUID,UUID):boolean (state and spectator restoration/tracking)
ActiveGameCoordinator.finishForApi(UUID,GameWinner,String):boolean
```

## 一致性规则

- 写操作/查询/注册都在主线程，查询和 close 错线程抛 IllegalStateException，其余返回 WRONG_THREAD。所有 API 写操作及核心变更期间重入 API 写请求返回 BUSY。
- 每个 ControlDomain 同时只有一个有效注册持有；无域的观察者可共存。注册令牌按对象身份校验，禁用所属插件自动关闭。关闭不改变本局角色或复活任何人。
- ROLE assign 接收完整名单，淘汰者角色不变。角色发生改变的玩家必须在线、存活、无待重置债务；角色不变的离线者可保留。候选前后事件取消或对局变更均不提交。
- reroll 从在线且未淘汰/未死亡的候选中抽取；保留离线/淘汰者原身份。runnerCount 表示重抽后未淘汰速通者总数，减去被固定的离线速通者再抽取，允许零/全部候选，但最终至少一名未淘汰猎人与速通者。
- shouldEliminate 对原版死亡和 API 淘汰调用；DEFAULT 仅速通者原版死亡会淘汰，API 请求应默认允许任意参与者淘汰（cause=API），PREVENT/取消始终有效。ELIMINATE 可让猎人死亡也淘汰。
- 胜负策略 DEFAULT/DEFER/REPLACE 在 GM 提交结算前调用；显式 API finish 跳过默认胜负策略但仍有 MatchFinishingEvent。策略异常记录并采用安全的 PREVENT/DEFER，避免意外判负。
- 终止事件在完整清理后恰好一次，含停止/倒计时取消/停用。核心关停不允许策略阻止。
- 角色变更不清空已绑定末地、不执行开局重置、不恢复淘汰者；隐藏开关仍约束聊天/身份颜色，个人标题保留。

## 验证与进度

- [x] API 不可变数据、版本、平台隔离及事件合同测试。
- [x] 状态变更：完整名单/淘汰保留/旧局拒绝/修订号/自定义结算与延迟判胜。
- [x] 注册域冲突、关闭/禁用、事件取消/重入、失败无副作用测试。
- [x] 角色换发指南针/授权清理/隐藏显示、淘汰/复活及生命周期桥接核对。
- [x] 双版本构建及 JAR/API SDK 核对；开发者文档及可编译轮换示例。

Ruling: 继续使用当前工作目录，不提交/推送；用户已授权在此实现。默认策略遵循上述规则，接口不会自动启用任何新玩法。

Ruling: 生命周期同步期间的修改类 `/mhu` 命令和旧配置点击同样返回忙碌提示，防止 Bukkit 回调通过命令绕过 API 重入保护；只读查询可继续。倒计时激活和延迟旁观回调均覆盖同一保护范围。角色同步连续失败时停止本局并返回 SERVICE_UNAVAILABLE，不报告已完成分配。

Ruling: API 复活立即恢复逻辑状态；死亡界面中的实体复活/游戏模式恢复放在下一 tick，重新检查对局、淘汰和恢复记录。复活者已在末地时补确认绑定，末地世界切换仍复用原检查。

完成验证：clean build 和后续修复构建通过，1.21.11 / 26.3 独立入口均成功。API 6、API-Paper 4、Paper 每版 84 项检查通过，共 178 项，无失败/跳过。两项复活回调回归均观察失败后再通过。独立审查指出的倒计时重入、复活末地确认、复活再次淘汰及身份同步问题已修复并复审通过。

四个部署 JAR 与两个 SDK 的 binary/sources/Javadoc 共十个交付文件已更新到本地 dist，校验和见 dist/SHA256SUMS.txt；SDK 1.0.0 统一 Java21，Paper 包内 SDK 类与独立 SDK 逐字节一致，Fabric 不包含 API 类且资源未变。双语 117 键和占位符匹配。检查报告：build/reports/extension-api-artifacts.json。

开发指南 docs/extension-api.md 的轮换插件示例已以 Java21 编译通过。未运行游戏实例；真实服务器上的传送/复活事件时序、指南针换发及客户端显示仍需实服抽查。
