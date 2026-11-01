# 扩展 API 1.1 使用指南

SDK 版本为 **1.1.0**，插件与 Fabric 模组版本为 **1.0.0**。API 1.0 的对局、角色、淘汰、胜负策略和事件仍可用，原有二进制调用保留。扩展通过 Bukkit `ServicesManager` 获取 `PaperManhuntUniversalApi`，检查 `apiVersion()` 的主版本和所需 `capabilities()`，再注册插件绑定的 `ExtensionRegistration`。基础依赖和角色轮换示例见[原有接口指南](extension-api.md)。

扩展可以注册服务端追踪目标，直接出现在配套 Fabric 模组与原版 Paper Dialog 中。玩家只需更新本项目的配套模组，无需安装该扩展的额外 Fabric 模组。

## 依赖与生命周期

将 `dist/sdk/` 中两个非 sources、非 javadoc 的 SDK JAR 作为 `compileOnly` 依赖，同时依赖对应版本 Paper API；不要把 SDK 打包或重定位到扩展中。SDK 使用 Java 21。扩展的 `plugin.yml` 添加 `depend: [ManhuntUniversal]`。仓库配置了 Maven publication，可手动执行 `:api:publishToMavenLocal :api-paper:publishToMavenLocal`；未发布到公共 Maven 仓库。

```java
var api = getServer().getServicesManager().load(PaperManhuntUniversalApi.class);
// 检查 api != null、兼容的 1.x 版本，以及需要的 Capability。
var result = api.register(this, new ExtensionOptions("example:mode", Set.of()));
if (result.status() != OperationStatus.OK) throw new IllegalStateException(result.message());
var owner = result.registration().orElseThrow();
```

新能力入口为 `configuration()`、`features()`、`texts()`、`presentation()`、`tracking()`。注册返回 `ExtensionResult<ExtensionHandle>`；只有成功时才取 `value()`。子句柄可单独关闭，父句柄关闭或插件停用会撤销所有子注册、取消相关请求并恢复当前下层行为。关闭不回滚扩展已经保存的配置、不改变胜负或玩家角色。扩展仍应在 `onDisable()` 取消自己安排的任务并关闭父句柄。

服务查询、注册、修改和关闭均在服务端主线程执行；离线线程上的修改返回 `WRONG_THREAD`，查询或关闭抛出 `IllegalStateException`。不可变快照可以留存给后台计算。核心调用扩展回调时再次执行写操作会返回 `BUSY`，需要后续修改时安排到下一 tick。不要在回调里等待 future，也不要对核心内部对象或缓存进行反射修改。

不同层按优先级选择最高有效值，同优先级的冲突注册返回 `CONTROL_CONFLICT`。扩展异常会被隔离并记录；清理与恢复不会被显示取消或玩法策略阻止。旧的独占控制域继续用于对局与角色写入；新注册也必须使用真实有效的父句柄。

## 配置注册、锁定与覆盖

`configuration().register(owner, definition)` 注册命名空间键，如 `example:interval`。内置类型为严格布尔、整数、有限小数、文本、枚举、字符串列表和分组映射，也可实现 `ConfigType<T>` 的解析、完整值校验和补全。值须归一化为标量、列表或字符串键映射；核心会生成不可变副本。

`ConfigDefinition<T>` 提供本地化名称/说明、默认值、类型、`ConfigStorage<T>`、生效时机和单位。**扩展负责自己的存储**；`save` 必须保证失败时旧数据不变，成功后才触发变更回调和核心副作用。不要在主线程的存储委托执行长时间 I/O。

分组配置在 `/mhu config` 展开，点击子项预填点路径，例如 `/mhu config example:interval.minimum `。叶子修改先组装整个新映射，再整体校验并调用一次存储委托，跨字段约束不会被绕过。可编译的完整示例见 [ConfigurationGroupExample](../examples/api-1.1/src/main/java/example/mhu/ConfigurationGroupExample.java)。

| 方法 | 用途 |
| --- | --- |
| `list()` / `find(key)` | 查询名称、说明、保存值、有效值、下一局值、修订号及锁定信息 |
| `update(owner, key, value, expectedConfigRevision)` | 通过统一校验和原子保存路径修改；`-1` 仅跳过配置修订检查 |
| `lock(owner, key, priority, reason)` | 禁用命令、旧点击按钮和 API 写入；父节点锁定覆盖所有子项 |
| `override(owner, key, provider, priority)` | 覆盖有效值，不改写保存值；撤销后重新使用当前下层值 |
| `refresh(owner, key)` | 外部输入改变后显式重算动态覆盖 |
| `listen(owner, listener)` | 观察成功提交的变化；回调后续写入安排到下一 tick |

`ConfigSnapshot` 区分 `savedValue`、`effectiveValue` 和 `nextMatchValue`。`NEXT_MATCH` 在进入倒计时锁定，本局继续使用锁定值，修改仅影响下一局；`IMMEDIATE` 立即生效；`RUNNING_ONLY` 的效果由对应运行边界控制。核心所有现有配置均已接入，保留原范围与时机：例如 runner-count 不会重抽本局身份，隐藏身份即时更新，距离刷新不会提高定位频率。

## 接管核心功能

配置覆盖用于改参数，功能策略用于跳过核心的某项效果：

```java
api.features().policy(owner, CoreFeatureIds.ORE_FORTUNE, 100,
        context -> FeatureDecision.SKIP_CORE);
```

随后由扩展自己的 Paper 监听器执行替代行为。`SKIP_CORE` 只停止该核心处理，不会执行替代代码、不绕过 RUNNING 边界，也不会取消其他插件已作出的事件取消。返回 `DEFAULT` 使用核心行为；策略失败回退核心。`FeatureContext` 是不可变数据，环境事件可没有玩家。

稳定功能 ID 为 `core:ore-fortune`、`core:blaze-rod-looting`、`core:piglin-bartering`、`core:piglin-brute-suppression`、`core:blaze-spawner-protection`、`core:nether-portal-protection`、`core:hunter-end-restriction`、`core:tracking-compass-grant`、`core:tracking-compass-use`，对应常量位于 `CoreFeatureIds`。

## 文本、结算与显示

`texts().register` 注册扩展双语默认文本，`texts().override` 为指定键提供按接收者生成的运行时覆盖。解析顺序为：有效的扩展覆盖 → 有效的服务器外部语言文件 → 注册默认文本/内置回退。覆盖不写回语言文件；撤销立即恢复下层。旧格式及占位符继续使用；Paper 的 `component` 层支持 Adventure 富文本、点击和悬停。

前缀键为 `prefix`，角色名称为 `identity.hunter` / `identity.runner`，其他现有键见内置语言文件。改显示名称不会修改实际角色。纯文本 provider 使用 `TextContext`；富文本 provider 使用 `PaperTextContext`。

`presentation().intercept(owner, selector, priority, provider)` 可按频道、稳定 ID 前缀和接收者过滤输出，决定沿用、替换或取消。频道包括聊天、标题、Actionbar、Tab、Nametag、物品名/描述、Paper/Fabric 菜单及声音。常用 ID 包括 `core:result.chat`、`core:role.title`；普通语言消息 ID 为 `core:text.<语言键>`。其余 ID 可查看当前 `PresentationRequest.id()`，应优先用频道或明确 ID 匹配，避免依赖内部调用顺序。

`appendResult(owner, ResultSection)` 追加结构化结算段落：负顺序位于核心结算前，非负位于后，同序按扩展/段落 ID 稳定排序。接收 `ResultSnapshot`，可读取胜方、原因、时间、最终参与者及淘汰状态。整个结算显示也可替换/取消，但不会改变已确定胜负、结束事件或玩家恢复。核心不再显示轮次历史。

`refresh(owner, RefreshRequest)` 按玩家合并刷新 `TEXT`、`MENU`、`ITEMS`、`IDENTITY` 或 `LOCATING`。文本、物品和界面刷新不会重新定位；需要重查目标时显式指定 `LOCATING`，仍遵守重定位冷却。

隐藏身份开启时，普通显示回调收到的角色、结算名单/击杀者和敏感 payload 会先过滤。个人身份标题仍向本人显示，但普通拦截者看不到其敏感原文。需要刻意突破此规则的扩展必须申请独占 `ControlDomain.ROLE_VISIBILITY`，同时在相应 selector、结算或身份 provider 显式选择 `revealRoles`；仅修改前缀或通用样式无需该权限。Bukkit 本身不是沙箱，拥有完整服务端权限的插件仍能自行观察世界或显示信息，这个规则约束的是核心提供的显示上下文。

## 按观察者显示身份

`presentation().identity(owner, priority, revealRoles, provider)` 接收观察者、目标、过滤后的对局和当前 fallback，返回独立 `ObserverIdentityStyle`：Tab 名称、是否列出、排序、Nametag 前后缀/颜色/可见性。一个目标对不同观察者可有不同外观；实际玩家名和侧边栏不被改写。未指定名称牌可见规则时继承当前真实队伍规则。

两版服务端各有包适配器；主线程生成不可变快照，网络线程只镜像数据，不调用扩展或 Bukkit。扩展卸载后恢复当前服务器状态。适配器失败时隐私隐藏采取保守处理并记日志；实际客户端包、其他记分板插件及重连组合仍需要游戏运行验证。

## 动态追踪目标

`TrackingTargetDefinition` 使用稳定命名空间 ID、本地化主/副标题及说明、排序、目标维度、外观、可用性策略和定位 provider。内置 ID 为 `core:village`、`core:bastion_remnant`、`core:nether_fortress`、`core:trial_chambers`，通过适配器继续调用原有结构算法；替换下界要塞定位器后可用 `LocateResult.core()` 显式委托原算法，保留最近烈焰人刷怪笼及其回退逻辑。

```java
api.tracking().register(owner, new TrackingTargetDefinition(
        "example:checkpoint", LocalizedText.bilingual("Checkpoint", "检查点"),
        TrackingWorld.current(), (request, cancellation) ->
            CompletableFuture.completedFuture(LocateResult.found(
                new WorldPosition(request.origin().worldId(), 100, 70, 100), "example:checkpoint"))));
```

可用性区分隐藏、可见锁定、计时锁定和可选；可按玩家任务进度返回不同结果。禁用项从菜单移除，锁定项不可选择；服务器再次校验角色、淘汰、指南针授权、对局 ID、目录修订号和当前可用性，不信任客户端倒计时。`select` 幂等选择；界面重复点击已选目标会清除。扩展卸载或目标变为不可用会清除旧选择。

`replaceLocator` 可替换现有目标的定位器，`availability` 可覆盖其可用性。`TrackingMutation` 包含旧 API 的 `MutationContext`、玩家和目录修订号。`snapshot` 区分逻辑目标 `destination`、实际指向 `guidance`、距离、状态和来源；跨维度时 guidance 可以是传送门，不必等于目标结构。

`TrackingSelectionChangedEvent`、`TrackingAvailabilityChangedEvent`、`TrackingTargetChangedEvent` 通过 Paper 发布；倒计时逐秒变化不重复发布可用性语义事件。`invalidate(owner, scope)` 支持目标/玩家范围，使共享缓存与请求失效；重新请求仍遵守冷却。原生结构共享观察会按目标世界一起失效，避免其他订阅者继续保留过期结果。

## 定位线程、缓存与限制

定位 provider **由主线程进入**，返回 `CompletionStage<LocateResult>`。可以立即返回坐标，也可以先在主线程提取数据，再让自己的后台执行器处理纯数据。不要把 Bukkit 世界查询直接放在异步线程；核心不会替扩展自动保证其后台代码安全。future 完成后核心在主线程非阻塞轮询，检查会话、请求版本、玩家状态、世界和注册仍有效，才提交结果。

| 限制 | 值与语义 |
| --- | --- |
| 动态目录 | 最多 32 个目标，包含 4 个内置目标（最多再注册28个扩展目标） |
| 定位并发 | 最多 8 项实际未完成工作，等待队列最多 128 |
| 超时 | 入队起 10 秒；超时/取消后真实工作完成前仍占并发配额 |
| 缓存 | 自定义目标正结果 30 秒、负结果 5 秒，最多 512 条 |
| 共享 | 默认不共享；显式 sharedCacheKey 还必须匹配完整请求身份、目标、修订号、世界、玩家/原点/半径等 |
| 线协议 | 单条最多 64 KiB，严格 UTF-8；名称 64、说明/锁定原因 128 Unicode 码点 |

`CancellationToken` 是协作取消信号，扩展应尽快停止无用工作；核心不强制取消扩展的 future，也不会提前归还真实 I/O 配额。定位失败、异常或超时有明确状态；只有内置目标支持原算法 fallback，自定义目标没有对应原生定位器。退出、淘汰、换维度、卸载世界、换局、撤销目标/策略后，旧结果不能回写新状态。

原生静态结构定位仍同步；原生传送门仍采用每 tick 64 列/2ms 软预算，结构补读最多两项实际异步 I/O。这些预算与扩展定位的八项配额独立。高频距离显示只读缓存。详见[追踪性能说明](tracking-performance.md)。

## Fabric 与资源包

选择协议升级为 **3**，hello 外形、频道和 Mod ID 保留；版本不匹配、未安装模组或发送失败时回退 Paper Dialog。两种菜单使用同一份服务端目录；已安装模组每页最多四个等宽选项，可翻页，保持选中项和实时锁定倒计时。旧协议客户端不会收到动态目录。

扩展可选择内置主题、ARGB 颜色、原版物品图标，或指定现有资源 ID（例如 `example:textures/gui/checkpoint.png`）。自定义材质可放在服务端资源包的 `assets/example/textures/gui/checkpoint.png`，由服务器原有资源包机制提供，制作、托管及玩家接受由服主负责。协议不传图片、URL、NBT 或客户端代码。

资源包是可选增强。未提供、被拒绝、缺失材质或资源重载后不可用时，模组使用主题/颜色回退，目标仍然可选择。仅需现有主题和原版图标时完全无需资源包，也无需附加 Fabric 模组。

## 示例与验证范围

[示例目录](../examples/api-1.1/README.md) 的 Java 源码纳入 Gradle 编译检查，覆盖配置、分组校验、文本/结算、观察者显示和自定义追踪目标。新能力有针对性的合同、生命周期、配置保存、隐私、异步、协议和界面状态测试；旧 API 消费者字节码保存在测试资源中，以校验 1.0 二进制兼容。

构建与测试记录见[本次验证记录](verification/api-1.1.md)。编译检查不等同于游戏验证：未运行真实客户端画面、资源包接受/重载、网络包与其他插件的组合兼容测试，也未进行实服性能采样。

核心现支持正常重启续局：已有会话在服务注册完成时即可查询；扩展须在自身 onEnable 检查当前会话并恢复自己的任务/数据。RUNNING 续局不重发新开局事件。见[续局职责说明](runtime-continuity.md)。

`roles().assign` 与 `roles().reroll` 现支持离线且未淘汰的参与者；在线死亡/待重置者仍保留安全限制。离线变更在重连时应用，未淘汰状态、待重置记录、末地解锁和已知位置会保留，见[角色接口规则](extension-api.md)。
