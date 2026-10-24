# ManhuntUniversal

支持 Minecraft **1.21.11、26.3** 的 **Paper 服务端插件 + Fabric 客户端配套模组**。已从 `E:\coding\mc-randomanhunt-plugin-26.3` 迁移除局中随机轮换角色以外的全部功能；开局随机选定速通者，每局身份固定。

## 玩法与功能

- `/rmh start` 锁定当时在线的参与者，按 `runner-count` 抽取初始速通者，其余为猎人；可使用 1 至 60 秒倒计时。
- 保留可选开局重置、失败安全传送、断线重连补重置、倒计时临时失明恢复及身份颜色/Tab 显示与清理。
- 速通者死亡时猎人获胜；首名速通者进入的末地为目标末地，该世界屠龙或满足原有无龙检查条件时速通者获胜。
- 保留绑定指南针的发放、死亡保留、禁止转移、无空间重试；猎人追踪速通者，速通者选择村庄、下界堡垒、堡垒遗迹。
- 保留结构搜索队列、缓存、跨维度已有传送门引导；不会创建或破坏传送门。
- 安装配套模组时显示原三栏图片选择界面；无模组或握手不兼容时使用 Paper Dialog。
- 保留烈焰人刷怪笼保护、双语资源、外部语言覆盖、原子配置保存、结束与停用清理。

已移除局中角色轮换计时、`/rmh debug runner-reshuffle`、轮换参数、轮换提示与次数显示；没有添加其他玩法。

## 命令与配置

命令保留 `/rmh start [秒数]`、`status`、`stop`、`config [key] [value]`，均需要 `randomanhunt.admin` 权限，默认 OP。`status` 显示本局参与者、时间和固定身份名单。

默认配置及服务端语言文件在 `plugins/ManhuntUniversal/`。原配置语义保持：`runner-count` 默认 1；两种指南针、聊天前缀和 `reset-players-at-start` 默认开启。开局重置会按旧实现清理参与者物品、状态、经验、进度并传送至出生点；可通过配置关闭。三个 `tracking-*` 参数保留原范围与下一局生效规则。`runner-switch-min-seconds` / `runner-switch-max-seconds` 已删除。

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
protocol/               原项目协议及编解码（Java 21，保持原样）
paper/
  shared/src/main/      两版本共享的服务端逻辑、入口、资源
  shared/src/test/      少量固定身份和胜负检查
  1.21.11/              :paper-1.21.11，版本适配与构建
  26.3/                 :paper-26.3，版本适配与构建
fabric/
  shared/src/main/      共享资源、非客户端专属源码
  shared/src/client/    共享客户端入口和网络 payload
  1.21.11/              :fabric-1.21.11，版本适配与构建
  26.3/                 :fabric-26.3，版本适配与构建
gradle/                 Wrapper、版本目录、公共构建配置
docs/                   设计、迁移与验证记录
```

每个版本模块使用 Gradle 默认的 `src/main/java`、`src/main/resources`；Fabric 另有 `src/client/java`。Paper 仅传送门 POI 查询有版本适配：26.3 沿用原 API，1.21.11 在原同步队列中桥接原版 POI 查询。Fabric 将渲染、输入、网络注册和 shader 差异放在版本目录，其余源码和素材共享。

## 构建产物

在各版本模块的 `build/libs/` 中取部署 JAR。本次已构建的四个部署文件另整理在 `dist/1.21.11/`、`dist/26.3/`，校验和见 `dist/SHA256SUMS.txt`；后续重新构建以 `build/libs/` 为准。

| 模块 | 部署产物（当前版本 0.1.0-SNAPSHOT） |
| --- | --- |
| Paper 1.21.11 | `paper/1.21.11/build/libs/manhunt-universal-paper-mc1.21.11-0.1.0-SNAPSHOT.jar` |
| Paper 26.3 | `paper/26.3/build/libs/manhunt-universal-paper-mc26.3-0.1.0-SNAPSHOT.jar` |
| Fabric 1.21.11 | `fabric/1.21.11/build/libs/manhunt-universal-fabric-mc1.21.11-0.1.0-SNAPSHOT.jar` |
| Fabric 26.3 | `fabric/26.3/build/libs/manhunt-universal-fabric-mc26.3-0.1.0-SNAPSHOT.jar` |

Paper JAR 放到对应版本服务器的 `plugins/`；Fabric JAR 放到对应版本客户端的 `mods/`，并安装 Fabric Loader 和对应版本 Fabric API。不要安装 `-sources.jar` 或 `-dev.jar`，也不需要单独安装 `common`、`protocol` JAR。

这四个产物分别面向对应游戏版本。插件和模组显示名为 `ManhuntUniversal`；为保留兼容性，源码包仍为 `com.caxture.randomanhunt`，Fabric Mod ID、资源命名空间、协议频道和绑定指南针 PDC 命名空间均保留 `randomanhunt`。配套模组与旧模组使用相同 Mod ID，客户端只安装对应的一份。

## 验证范围

按要求执行精简验证：两个版本编译打包、少量固定身份/结算检查，以及部署 JAR 的元数据、Java 字节码、协议和素材核对。没有运行整套旧测试或完整游戏流程。详见 [迁移记录](docs/migration.md) 与 [迁移验证](docs/migration-verification.md)。

技术依据：[Paper 项目配置](https://docs.papermc.io/paper/dev/project-setup/)、[Paper Java 要求](https://docs.papermc.io/paper/getting-started/)、[Fabric Loom 插件区分](https://docs.fabricmc.net/develop/loom/)、[Fabric 26.3 开发说明](https://www.fabricmc.net/2026/09/15/263.html)。
