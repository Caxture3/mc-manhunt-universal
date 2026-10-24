# 骨架验证记录

本文件为初始骨架的历史记录；迁移后的最新状态见 [迁移验证记录](migration-verification.md)。

验证日期：2026-10-24。环境：Windows、Gradle 9.8.2、JDK 25.0.4.1，JDK 21 toolchain。

## 已执行

| 命令 | 结果 |
| --- | --- |
| `.\gradlew.bat clean build --console=plain --warning-mode=all` | 成功，33 个任务执行，四个版本模块均生成部署 JAR，无 Gradle 弃用提示 |
| `.\gradlew.bat build projects -PmcVersion=1.21.11 --console=plain` | 成功；仅配置 common、protocol、paper-1.21.11、fabric-1.21.11 |
| `.\gradlew.bat build projects -PmcVersion=26.3 --console=plain` | 成功；仅配置 common、protocol、paper-26.3、fabric-26.3 |
| `.\gradlew.bat help -PmcVersion=invalid --console=plain` | 按预期失败，报告 Unsupported mcVersion 'invalid' |

## 产物检查

使用 ZIP 读取与 class 文件头检查了 README 列出的四个部署 JAR：

- 文件名均与 README 一致。
- Paper `plugin.yml` 和 Fabric `fabric.mod.json` 均包含正确版本，模板变量全部展开。
- Paper 主类与 Fabric client 入口均存在；Fabric 环境为 `client`，Minecraft 依赖为各自精确版本。
- 1.21.11 入口 class major 为 65（Java 21），26.3 为 69（Java 25）；每个 JAR 只有对应的一个空入口 class。
- Fabric 1.21.11 的构建执行了 `remapJar`，发布 manifest 的 mapping namespace 为 `intermediary`。
- Fabric 26.3 使用普通 `jar`，发布 manifest 的 mapping namespace 为 `official`。

独立只读审查已完成。发现并修复了两项构建问题：Paper Maven 仓库不能仅允许 Paper group，否则其传递依赖不可解析；Fabric 归档名称须通过 `base.archivesName` 配置才能覆盖 Loom remap 任务。修复后重新执行上述全部验证。

另将公共模块依赖声明改为 `DependencyHandler.project(String)`，消除 Gradle 对直接使用 Project 对象作为依赖的弃用提示；随后再次完成全部构建及产物检查。

当前无玩法或协议实现，因此没有业务单元测试。未启动 Minecraft 客户端 / Paper 服务器，未做游戏加载或联机验证；GitHub Actions 配置尚未在远程运行。

