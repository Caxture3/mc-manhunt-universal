# 角色调试与 1.0.0 交付验证

2026-11-01 完成 `/mhu debug role-reshuffle` 与用户确认的新语法 `/mhu debug role-set <玩家> <runner|hunter>`。管理权限统一为 `manhuntuniversal.admin`，旧节点无回退；产品版本 1.0.0，独立 SDK 1.1.0，协议 3。

## 已执行的检查

角色核心及命令分别先观察缺失实现导致的 RED，再实现并运行 GREEN。角色变更沿用现有 API 的完整名单校验、事件取消/二次校验、同步与失败处理；未另写一套玩家状态切换流程。

新增检查覆盖：

- 只修改指定离线参与者，保留原扩展 ROLES token、配置和预约；事件来源为 `core:debug`。
- 最后一名未淘汰成员、淘汰目标和在线未就绪目标的拒绝；同角色只重播展示，无修订/历史/角色事件或追踪重置。
- 固定随机数让初次抽签等于旧名单时，fallback 实际交换角色；没有替代组合时不改动状态。
- 取消、嵌套修改、过期会话/修订及错误线程保护。
- 新权限授予的非 OP 可用；仅旧权限不能执行或获取补全；元数据仅注册新节点。
- 用法、旧命令名拒绝、闲置/倒计时限制、大小写忽略的固定名单解析、无在线查询的离线目标、错误角色及上下文补全。
- 隐藏身份时成功反馈不包含玩家/角色占位符，错误状态使用本地化文案。

分别执行并通过：

```powershell
.\gradlew.bat build '-PmcVersion=1.21.11' --offline --console=plain
.\gradlew.bat build '-PmcVersion=26.3' --offline --console=plain
```

| 模块 | 通过检查数 |
| --- | ---: |
| api / api-paper / common / protocol | 7 / 4 / 3 / 13 |
| Paper 1.21.11 / 26.3 | 288 / 288 |
| Fabric 1.21.11 / 26.3 | 8 / 8 |
| 合计 | 619 |

一次独立只读审查未发现本次 diff 引入的 Critical/Important 问题。审查覆盖 API 行为保留、共享提交、调试事件、ROLES 所有权、离线/淘汰保护、有限随机 fallback、新权限及隐藏反馈。

## 交付核对

已更新四个产品 1.0.0 JAR 和六个 SDK 1.1.0 JAR，位于 `dist/1.21.11/`、`dist/26.3/` 和 `dist/sdk/`。确认替代 JAR 存在并检查通过后移除四个旧 0.2.0 部署副本。`dist/SHA256SUMS.txt` 的十个条目与最终 JAR 清单完全一致，全部摘要重新核对。

检查了产品/游戏版本、Paper/Fabric 入口、无旧命令别名、权限默认 OP、服务端/客户端隔离和字节码：1.21.11 为 Java 21，26.3 平台为 Java 25，公共 SDK 仍为 Java 21。服务端所有 class 均不含旧权限节点，MhuCommand、RegistryConfigCommands 和 DebugCommandHandler 的执行/点击权限均包含新节点，避免常量内联残留。

148 个双语键及占位符一致，默认配置和内置语言与源码一致；外部语言文件未修改。最终 SDK 再次通过旧 1.0 消费者字节码链接检查。`git diff --check` 通过。

本地日志和机器报告分别为 `build/role-debug/`、`build/reports/role-debug-1.0.0-artifacts.json`，不提交仓库。没有自动提交或发布。

## 未执行

未启动真实服务器/客户端，未执行实机 debug 指令、离线重连或扩展组合联机验证。构建与替身边界测试不等同于游戏运行验证；用户已有外部权限和语言配置仍需手动维护。
