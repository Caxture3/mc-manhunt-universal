# 追踪控制 API 1.2 验证

2026-11-01 完成强制追踪控制。SDK **1.2.0**，产品 **1.0.0**，协议 **3**。公共入口、边界与示例见 [API 1.2 指南](../extension-api-1.2.md)。无需附加 Fabric 模组，也没有改变原生定位预算或网络协议。

## 构建与针对性检查

分别执行 `gradlew.bat build -PmcVersion=1.21.11 --offline --console=plain` 和 `gradlew.bat build -PmcVersion=26.3 --offline --console=plain`，最终两次均为 BUILD SUCCESSFUL。

| 模块 | 检查数 |
| --- | ---: |
| API | 9 |
| Paper API | 4 |
| Common | 3 |
| Protocol | 13 |
| Paper 1.21.11 | 309 |
| Paper 26.3 | 309 |
| Fabric 1.21.11 | 10 |
| Fabric 26.3 | 10 |
| 总计 | 667 |

覆盖双方角色、目录注册/隐藏、手动选择保留、所有权与独占域、session/match/control revision、旧句柄替换、清理后旧任务拒绝、局外/离线位置、淘汰过滤、候选快照独立性与容量、实时选择器改变目标、无效结果/异常/重入写入等情况。编译检查的示例包含结构、固定玩家、最近猎人、暂停、恢复和延迟提交 guard。

一次独立只读审查发现三项问题，均通过回归测试复现后修复：

- 离线淘汰改为在提交淘汰事件前清除覆盖，复活不恢复旧任务。
- 生效策略回调后再次检查注册与状态，所有者在回调中注销不会留下孤立绑定或抛出提交异常。
- 暂停/无目标保留空目标的 lodestone 组件，避免单世界服务端指南针指向原点或出生点。

原 SDK 1.0、1.1 消费者字节码均保留，JUnit 和直接加载最终 1.2.0 SDK JAR 的 smoke 检查通过；fixture 来源与 SHA-256 见 [兼容说明](../../api/src/test/compatibility/README.md)。`TrackingRegistry.control()` 为默认可选入口，原构造器及方法未改变。

## 交付检查

`dist/1.21.11/`、`dist/26.3/` 四个产品 JAR 和 `dist/sdk/` 六个 1.2.0 SDK JAR 已刷新。确认新版六个 SDK 存在且检查通过后移除旧版 SDK 部署副本；`dist/SHA256SUMS.txt` 十个条目全部重新计算并核对。

核对插件/模组元数据、命令和权限、字节码版本（平台分别 Java 21/25，API Java 21）、API 无平台依赖、新接口在二进制/源码/Javadoc 中存在、服务端/客户端类隔离、152 个双语键与占位符一致、结构默认配置和原背景资源保持，以及 `git diff --check`。

本次没有运行真实 Paper/Fabric 联机、客户端指南针画面、单世界实际显示、跨维度传送门引导、资源包或性能基准测试；编译/单元检查不能视为这些运行时验证。选择器是主线程回调，扩展作者仍须避免阻塞与世界扫描。未提交、发布或推送代码。
