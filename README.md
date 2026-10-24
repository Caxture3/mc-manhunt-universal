# ManhuntUniversal

支持多个 Minecraft 版本的 **Paper 服务端插件 + Fabric 客户端配套模组**。当前为开发骨架；猎人玩法及辅助功能将在确认旧工程后原样迁移。

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
.\gradlew.bat build -PmcVersion=1.21.11
.\gradlew.bat build -PmcVersion=26.3

# 查看本机可发现的 JDK
.\gradlew.bat -q javaToolchains

# 启动指定版本的 Fabric 开发客户端
.\gradlew.bat -PmcVersion=1.21.11 :fabric-1.21.11:runClient
.\gradlew.bat -PmcVersion=26.3 :fabric-26.3:runClient
```

Linux / macOS 使用 `bash ./gradlew` 替代 `.\gradlew.bat`。如果 JDK 未被自动发现，在个人 Gradle 配置中设置 `org.gradle.java.installations.paths`；不要将本机绝对路径提交到项目。

首次构建需联网下载 Gradle、Minecraft 与 Maven 依赖；Fabric 开发客户端首次启动还需下载游戏资源。

## 目录

```text
common/                 纯 Java 公共逻辑预留区（Java 21）
protocol/               原项目协议及编解码预留区（Java 21）
paper/
  shared/src/main/      两版本共享的插件入口、资源
  1.21.11/              :paper-1.21.11，版本适配与构建
  26.3/                 :paper-26.3，版本适配与构建
fabric/
  shared/src/main/      共享资源、非客户端专属源码
  shared/src/client/    共享客户端入口与后续客户端功能
  1.21.11/              :fabric-1.21.11，版本适配与构建
  26.3/                 :fabric-26.3，版本适配与构建
gradle/                 Wrapper、版本目录、公共构建配置
docs/                   骨架设计及执行记录
```

每个版本模块保留 Gradle 默认的 `src/main/java`、`src/main/resources`；Fabric 另有 `src/client/java`、`src/client/resources`。需要版本专用代码时创建这些目录，与共享源码一起编译。同一完整类名不要同时出现在共享和版本源码中。

## 构建产物

在各版本模块的 `build/libs/` 中取部署 JAR：

| 模块 | 部署产物（当前版本 0.1.0-SNAPSHOT） |
| --- | --- |
| Paper 1.21.11 | `paper/1.21.11/build/libs/manhunt-universal-paper-mc1.21.11-0.1.0-SNAPSHOT.jar` |
| Paper 26.3 | `paper/26.3/build/libs/manhunt-universal-paper-mc26.3-0.1.0-SNAPSHOT.jar` |
| Fabric 1.21.11 | `fabric/1.21.11/build/libs/manhunt-universal-fabric-mc1.21.11-0.1.0-SNAPSHOT.jar` |
| Fabric 26.3 | `fabric/26.3/build/libs/manhunt-universal-fabric-mc26.3-0.1.0-SNAPSHOT.jar` |

Paper JAR 放到对应版本服务器的 `plugins/`；Fabric JAR 放到对应版本客户端的 `mods/`，并安装 Fabric Loader 和对应版本 Fabric API。不要安装 `-sources.jar` 或 `-dev.jar`，也不需要单独安装 `common`、`protocol` JAR。

这四个产物分别面向对应游戏版本；本工程不提供跨游戏版本联机转换。当前只包含空生命周期入口，未注册通信协议，未实现玩法。插件及模组名称暂定为 `ManhuntUniversal`，Mod ID 为 `manhunt_universal`，包根为 `com.caxture.manhunt`。

## 后续迁移规则

1. 先明确旧工程路径，梳理原有行为、配置、资源及通信协议，再迁入代码。
2. 旧工程已有功能照搬；只为版本 API 差异做必要适配，不另写功能替代实现。
3. 纯 Java 逻辑进入 `common`，原协议进入 `protocol`；平台逻辑进入各平台共享源码，版本差异留在对应版本模块。
4. 协议频道、消息 ID、编码格式与握手流程以旧实现为准，当前骨架不预设。
5. 编译、元数据与打包检查通过后，分别做两个版本的 Paper 加载和 Fabric 客户端连接测试。

技术依据：[Paper 项目配置](https://docs.papermc.io/paper/dev/project-setup/)、[Paper Java 要求](https://docs.papermc.io/paper/getting-started/)、[Fabric Loom 插件区分](https://docs.fabricmc.net/develop/loom/)、[Fabric 26.3 开发说明](https://www.fabricmc.net/2026/09/15/263.html)。
