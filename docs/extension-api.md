# 扩展插件 API

Manhunt Universal 提供公共 Java API 和 Paper 服务、事件，让依赖插件管理对局、分配角色、淘汰和复活参与者，以及接管默认判胜。核心默认仍为每局固定身份；安装核心不会自动开启定时轮换。

`api` 与 `api-paper` 的 API 版本独立于插件版本，当前为 `1.0.0`。纯 Java 包为 `com.caxture.manhuntuniversal.api`，只依赖 Java 21；Paper 服务位于 `com.caxture.manhuntuniversal.api.paper`，事件位于其 `event` 子包。内部 `game`、`runtime`、`tracking` 包不属于公共兼容接口。Fabric 网络协议不因服务端扩展改变。

## 编译依赖

在核心仓库根目录生成 SDK：

```powershell
.\gradlew.bat :api:build :api-paper:build '-PmcVersion=1.21.11'
```

将 `api/build/libs/manhunt-universal-api-1.0.0.jar` 和 `api-paper/build/libs/manhunt-universal-api-paper-1.0.0.jar` 复制到扩展工程的 `libs/`。这些是编译 SDK；服务器安装对应 Minecraft 版本的 `manhunt-universal-paper-*` 部署 JAR，核心已包含 API 类。

扩展的 `build.gradle.kts` 示例：

```kotlin
plugins { java }

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
}

dependencies {
    compileOnly(files("libs/manhunt-universal-api-1.0.0.jar"))
    compileOnly(files("libs/manhunt-universal-api-paper-1.0.0.jar"))
    compileOnly("io.papermc.paper:paper-api:1.21.11-R0.1-SNAPSHOT")
}

java { toolchain.languageVersion.set(JavaLanguageVersion.of(21)) }
tasks.withType<JavaCompile>().configureEach { options.release.set(21) }
```

面向 26.3 专用插件时，改用 Java 25 和该版本的 Paper API；SDK 本身仍为 Java 21。SDK 与 Paper API 均使用 `compileOnly`，不要再次打包或 relocate API 类，否则注册服务和事件可能使用不同的类身份。

扩展的 `src/main/resources/plugin.yml`：

```yaml
name: ExampleRoleRotation
version: '1.0.0'
main: example.mhu.ExampleRoleRotation
api-version: '1.21.11'
depend: [ManhuntUniversal]
```

## 服务和注册

在主线程通过 `getServer().getServicesManager().load(PaperManhuntUniversalApi.class)` 获取服务，先检查 `apiVersion()` 和 `capabilities()`。同一提供者也注册为 `ManhuntUniversalApi`，便于仅使用纯 Java 查询接口的代码获取入口。

调用 `api.register(this, new ExtensionOptions("example:rotation", Set.of(ControlDomain.ROLES)))` 注册所属插件和需要的控制域。返回 `RegistrationResult`；只有 `status() == OperationStatus.OK` 后才使用其中的 `registration()`。`ExtensionRegistration` 是不可伪造的注册句柄，不能自行实现来替代核心返回的对象。

| 控制域 | 对应能力 |
| --- | --- |
| `MATCH` | 开始、停止 |
| `ROLES` | 完整角色分配、随机重抽 |
| `PARTICIPANTS` | 主动淘汰、复活 |
| `ELIMINATION` | 注册 `EliminationPolicy` |
| `VICTORY` | 显式结算、注册 `VictoryPolicy` |

每个域同一时刻只允许一个有效注册持有；冲突返回 `CONTROL_CONFLICT`。只观察事件或快照可用空控制域集合注册，多名观察者可以共存。策略通过 `ExtensionOptions` 的四参数构造器传入，`Optional.empty()` 表示不提供对应策略。关闭句柄不会改变当前角色或复活玩家。扩展停用时核心会自动注销所属句柄；扩展也应在 `onDisable()` 取消自己的任务并调用 `close()`。

查询、注册、变更和 `close()` 均要求服务器主线程；错误线程上的写操作返回 `WRONG_THREAD`，查询和 `close()` 抛 `IllegalStateException`。异步任务应先通过 Bukkit scheduler 切回主线程。事件或策略回调期间嵌套调用写 API 返回 `BUSY`；需要后续动作时安排下一 tick 的任务。

## 快照和变更

`api.matches().current()` 返回 `Optional<MatchSnapshot>`；无当前对局时为空。状态为 `COUNTDOWN`、`RUNNING`、`SETTLING`。快照包含不可变参与者映射、对局 UUID、修订号、创建和开始时间、目标末地。`ParticipantSnapshot` 分别记录角色和 `eliminated`，淘汰不会更换角色。

每次写入前重新查询快照，再构造：

```java
MutationContext context = new MutationContext(
        registration, match.id(), match.revision(), "checkpoint reached");
```

`sessionId` 防止旧任务作用于新对局，`expectedRevision` 防止使用过期状态提交。三参数构造器省略修订校验（默认 `-1`），仍校验对局 UUID。返回 `OperationResult` 后检查 `successful()`、`status()`、`message()`；成功结果中的 `match()` 可用于读取提交后的快照。不要长期缓存快照作为当前状态。

| 调用 | 语义 |
| --- | --- |
| `control().start(registration, countdownSeconds)` | 复用核心开局校验、倒计时和玩家重置；调用前没有对局 UUID。 |
| `control().stop(context)` | 管理停止，执行核心清理。 |
| `control().finish(context, new Victory(winner, reason))` | 记录胜方和自定义原因；跳过默认胜负策略，仍经过可取消的结算事件。 |
| `roles().assign(context, assignments)` | 提交包含本局全体 UUID 的完整 `Map<UUID, TeamRole>`。 |
| `roles().reroll(context, runnerCount)` | 对符合条件的玩家重抽；参数为重抽后的未淘汰速通者总数。 |
| `participants().eliminate(context, playerId)` | 通过策略和事件淘汰任意参与者，统一执行旁观、追踪清理与判胜。 |
| `participants().revive(context, playerId)` | 通过标准恢复路径移除淘汰、恢复游戏模式和追踪，保留角色。 |

角色操作仅在 `RUNNING` 接受。`assign` 必须覆盖完整名单，淘汰者的角色不可改变；变更角色的玩家必须在线、存活且没有待恢复债务。保持原角色的离线玩家可保留。最终必须至少有一名未淘汰速通者和一名未淘汰猎人。

`reroll` 保留离线者和淘汰者的角色，从在线且未淘汰、未死亡的候选中抽取。固定的离线存活速通者计入 `runnerCount`；因此候选抽取数量可以为零或全部，只要最终双方仍有存活成员。轮换不会复活淘汰者、重新执行开局重置、清空目标末地或撤销本局已解锁的猎人末地访问。

核心统一更新指南针、选择授权、追踪、个人提示、Tab 和名称牌，并继续遵守 `hide-role-assignments`。`force-random-roles` 管理开局前预约；它不会自动调用扩展角色操作。

常见拒绝状态包括 `STALE_SESSION`、`STALE_REVISION`、`INVALID_STATE`、`INVALID_ASSIGNMENT`、`PLAYER_UNAVAILABLE`、`CANCELLED`、`UNREGISTERED_EXTENSION`、`CONTROL_CONFLICT`、`BUSY`、`START_REJECTED`、`SERVICE_UNAVAILABLE`。参数、权限、取消或过期校验失败时，不应将请求视为已提交；根据业务决定是否用新快照重试，避免循环重试。

## 淘汰和判胜策略

`EliminationPolicy.decide(EliminationContext)` 返回 `DEFAULT`、`PREVENT` 或 `ELIMINATE`。原版死亡的 `DEFAULT` 只淘汰速通者；API 请求的 `cause` 为 `API`，其 `DEFAULT` 允许淘汰任意参与者。`PREVENT` 或取消淘汰事件均可阻止操作；`ELIMINATE` 可让猎人死亡也进入淘汰路径。玩家断线不等于死亡，轮换不代替显式复活。

`VictoryPolicy.evaluate(VictoryContext)` 在核心提交默认胜负之前执行：

```java
VictoryDecision.defaults();
VictoryDecision.defer();
VictoryDecision.replace(new Victory(MatchWinner.RUNNERS, "objective completed"));
```

`DEFER` 使对局继续运行；已经发生的淘汰仍保留，扩展可在后续 tick 复活玩家或显式结算。策略不是持续轮询器，延期后需要扩展负责触发后续进展。`REPLACE` 替换胜方和原因。策略抛异常时核心记录错误，淘汰策略采用阻止、判胜策略采用延期，避免意外判负。管理停止和核心停用清理不能被策略阻止。

## Paper 事件

所有事件通过 Bukkit `PluginManager` 监听，使用 JavaBean 访问器，如 `getMatch()`、`getPlayerId()`、`getSource()`。事件快照同样不可变。

| 事件 | 时机与变更能力 |
| --- | --- |
| `MatchPreparingEvent` | 开局前，可取消。 |
| `MatchCountdownStartedEvent` | 开始倒计时。 |
| `MatchStartedEvent` | 正式进入运行态。 |
| `MatchEndedEvent` | 完整清理后，每局一次；包含终止原因和可选 `Victory`。覆盖正常结束、停止、倒计时取消、核心停用。 |
| `RolesChangingEvent` | 完整候选分配提交前，可取消；提供 `getBefore()`、`getProposed()` 和 `getSource()`。 |
| `RolesChangedEvent` | 角色及依赖状态同步后；提供前后快照和来源。 |
| `ParticipantEliminatingEvent` | 淘汰前，可取消。 |
| `ParticipantEliminatedEvent` | 淘汰提交后。 |
| `ParticipantRevivedEvent` | 复活和恢复提交后。 |
| `TargetEndEnteredEvent` | 存活速通者首次绑定本局目标末地。 |
| `MatchFinishingEvent` | 最终提交前，可取消，或通过 `setVictory()` 替换胜方与原因。 |

## 随机间隔轮换示例

下面是独立扩展的完整主类，文件为 `src/main/java/example/mhu/ExampleRoleRotation.java`。扩展仅持有 `ROLES` 域，每 60–180 秒提出一次轮换请求。任务由扩展自己维护；它每次重新检查注册句柄、sessionId、状态和修订号，在所有结束路径取消任务，不需要修改核心或 Fabric 模组。

```java
package example.mhu;

import com.caxture.manhuntuniversal.api.Capability;
import com.caxture.manhuntuniversal.api.ControlDomain;
import com.caxture.manhuntuniversal.api.ExtensionOptions;
import com.caxture.manhuntuniversal.api.ExtensionRegistration;
import com.caxture.manhuntuniversal.api.MatchSnapshot;
import com.caxture.manhuntuniversal.api.MatchState;
import com.caxture.manhuntuniversal.api.MutationContext;
import com.caxture.manhuntuniversal.api.OperationResult;
import com.caxture.manhuntuniversal.api.OperationStatus;
import com.caxture.manhuntuniversal.api.RegistrationResult;
import com.caxture.manhuntuniversal.api.TeamRole;
import com.caxture.manhuntuniversal.api.paper.PaperManhuntUniversalApi;
import com.caxture.manhuntuniversal.api.paper.event.MatchEndedEvent;
import com.caxture.manhuntuniversal.api.paper.event.MatchStartedEvent;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

public final class ExampleRoleRotation extends JavaPlugin implements Listener {
    private PaperManhuntUniversalApi api;
    private ExtensionRegistration registration;
    private BukkitTask task;
    private UUID sessionId;

    @Override
    public void onEnable() {
        api = getServer().getServicesManager().load(PaperManhuntUniversalApi.class);
        if (api == null || !api.apiVersion().equals("1.0.0")
                || !api.capabilities().containsAll(Set.of(
                        Capability.MATCH_QUERY, Capability.ROLE_CONTROL, Capability.PAPER_EVENTS))) {
            getLogger().severe("Compatible Manhunt Universal API unavailable");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        RegistrationResult result = api.register(this,
                new ExtensionOptions("example:rotation", Set.of(ControlDomain.ROLES)));
        if (result.status() != OperationStatus.OK) {
            getLogger().severe(result.status() + ": " + result.message());
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        registration = result.registration().orElseThrow();
        getServer().getPluginManager().registerEvents(this, this);
        api.matches().current().filter(match -> match.state() == MatchState.RUNNING)
                .ifPresent(this::begin);
    }

    @EventHandler
    public void onStarted(MatchStartedEvent event) {
        begin(event.getMatch());
    }

    @EventHandler
    public void onEnded(MatchEndedEvent event) {
        if (event.getMatch().id().equals(sessionId)) {
            cancelTask();
        }
    }

    private void begin(MatchSnapshot match) {
        cancelTask();
        sessionId = match.id();
        scheduleNext();
    }

    private void scheduleNext() {
        if (registration == null || !registration.active() || sessionId == null) {
            cancelTask();
            return;
        }
        UUID expectedSession = sessionId;
        long delayTicks = ThreadLocalRandom.current().nextLong(60, 181) * 20L;
        task = getServer().getScheduler().runTaskLater(this, () -> rotate(expectedSession), delayTicks);
    }

    private void rotate(UUID expectedSession) {
        task = null;
        if (!expectedSession.equals(sessionId) || registration == null || !registration.active()) {
            cancelTask();
            return;
        }
        MatchSnapshot match = api.matches().current().orElse(null);
        if (match == null || !match.id().equals(expectedSession) || match.state() != MatchState.RUNNING) {
            cancelTask();
            return;
        }
        int runnerCount = (int) match.participants().values().stream()
                .filter(player -> player.role() == TeamRole.RUNNER && !player.eliminated()).count();
        OperationResult result = api.roles().reroll(new MutationContext(
                registration, match.id(), match.revision(), "random interval elapsed"), runnerCount);
        if (result.successful()) {
            scheduleNext();
            return;
        }
        getLogger().info("Rotation refused: " + result.status() + ": " + result.message());
        switch (result.status()) {
            case CANCELLED, PLAYER_UNAVAILABLE, INVALID_ASSIGNMENT, STALE_REVISION, BUSY -> scheduleNext();
            default -> cancelTask();
        }
    }

    private void cancelTask() {
        if (task != null) {
            task.cancel();
            task = null;
        }
        sessionId = null;
    }

    @Override
    public void onDisable() {
        cancelTask();
        if (registration != null) {
            registration.close();
            registration = null;
        }
    }
}
```

随机抽取可能保留部分或全部原角色；示例不会强制每次抽出不同结果。需要自定义分配时，先从新快照创建完整映射，再调用 `assign`，继续保留淘汰者和不可变更者的角色。

本示例只展示扩展接入方式；核心不会装载文档中的示例代码或自动启用其轮换。编译检查只能确认接口使用正确，游戏中的指南针、旁观和显示行为仍需分别记录实际运行验证。

## 升级与语言文件

新增内置语言键 `game.roles.changed-title`、`reason.extension-finished` 和 `command.busy`；已有服务器外部语言文件不会被自动改写，缺键时使用内置回退文本。状态提交期间，从同步回调中再次执行修改类 `/mhu` 命令会被暂时拒绝；只读查询照常可用，回调可将后续修改安排到下一 tick。
