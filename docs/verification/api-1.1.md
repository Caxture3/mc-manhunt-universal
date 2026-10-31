# API 1.1 验证记录

2026-11-01 完成 API 1.1.0 实现与精简交付验证。产品插件/Fabric 版本保持 0.2.0，动态目标选择协议为 3。此记录对应 API 1.1 完成时的交付快照；后续玩法改动的验证另行记录。

## 构建与检查

分别执行并通过：

```powershell
.\gradlew.bat build '-PmcVersion=1.21.11' --offline --console=plain
.\gradlew.bat build '-PmcVersion=26.3' --offline --console=plain
```

该环境 JDK 25 的默认短路径临时目录导致 Unix-domain socket 回环失败，验证时仅为本次进程设置 `JAVA_TOOL_OPTIONS` 指向工作区内较短的临时 socket 目录。这是本地构建环境处理，没有加入项目配置，也不影响部署配置。

| 模块 | 通过检查数 |
| --- | ---: |
| api | 7 |
| api-paper | 4 |
| common | 3 |
| protocol | 13 |
| Paper 1.21.11 | 251 |
| Paper 26.3 | 251 |
| Fabric 1.21.11 | 8 |
| Fabric 26.3 | 8 |
| 合计 | 545 |

两个 Fabric 模块分别使用 remap Mojang mappings 与非 remap Loom 构建。SDK 示例由 `:api-paper:compileExamplesJava` 编译。未重新运行与本次无关的完整游戏流程。

## 覆盖范围

- 父/子注册生命周期、真实父句柄验证、主线程与重入限制、冲突优先级、插件停用恢复。
- 配置完整分组校验、保存失败、旧修订、父/叶锁定、覆盖撤销、NEXT_MATCH 倒计时冻结及实际开局读取一致。
- 功能接管避免重复掉落、保持其他插件取消；文本外部配置回退、优先级与注销恢复。
- 结算追加和取消、隐藏身份上下文、个人身份标题的敏感 payload 过滤、显式可见性控制。
- 观察者身份独立快照、原始规则恢复、镜像复制过滤、适配器失败后的 create/remove/membership 重试，以及最后提供者注销时保留待恢复状态。
- 目标目录/修订、隐藏/锁定、清除选择、真实后台完成与过期结果、八项实际并发、十秒超时、完整输入共享及订阅取消。
- 原生缓存与附近结构观测/探测失效、冷却后延迟刷新、跨维度移动参照，以及既有要塞刷怪笼回退和 POI 预算检查。
- 协议 UTF-8/长度/数量边界、版本握手、旧客户端回退、目录更新、分页等宽、倒计时、资源缺失回退。
- 通过真实 Paper 服务入口、注册所有权与停用派发的无游戏集成检查：配置+文本+目标注册、两种菜单频道拦截、目录编解码、选择及注销恢复。测试中世界和服务器适配器为替身，未启动 Paper。

独立只读审查发现的 owner 过滤、敏感 payload、Dialog 回调续期、原生失效、跨维度参照、隐藏后恢复、原队伍规则、开局冻结值及长锁定原因发送回退问题均已修复并加入针对性检查。

## 产物核对

四个部署 JAR 和六个 SDK JAR 已更新到 `dist/`，其 SHA-256 已写入并重新核对 `dist/SHA256SUMS.txt`。旧 SDK 部署副本在六个新 JAR 校验通过后移除。产物名、Paper/Fabric 元数据、入口、`/mhu` 无旧别名、兼容命名空间、API 类打包及服务端/客户端隔离均已检查。

1.21.11 平台字节码为 Java 21，26.3 平台为 Java 25；公共 API/SDK 字节码仍为 Java 21。纯 API JAR 检查没有 Bukkit/Paper/Fabric/Minecraft 引用。134 个中英文语言键及占位符一致；内置配置与双语资源均与源文件一致，外部语言文件未被修改。

保留了原 API 1.0.0 编译的消费者字节码；JUnit 和直接以最终 1.1.0 SDK JAR 运行的 smoke 检查均确认旧调用仍能链接、新可选入口有默认行为。原始 fixture 来源与 SHA-256 见 [兼容测试说明](../../api/src/test/compatibility/README.md)。

本地完整日志位于 `build/api-1.1-work/`，机器可读报告为 `build/reports/extension-api-1.1-artifacts.json`，两者不提交仓库。`git diff --check` 通过。

## 未进行的验证

未启动真实服务器/客户端，未验证屏幕实际视觉、资源包接受/重载、两版原生数据包的实机行为、其他插件组合或真实服务器性能。包构造/成员签名分别依据两版映射 JAR 核对；编译与无游戏测试不能替代这些运行验证。

Javadoc 仍有缺少注释的警告；共享反射适配器有 unchecked 提示；一个环境事件测试构造器有 deprecated-for-removal 警告。均不影响本次构建通过。
