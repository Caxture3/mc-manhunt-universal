# 下界坐标倍率验证

2026-11-03 实现 `nether-coordinate-scale`。默认8，整数1～2147483647，全程作用于玩家与穿门实体；产品1.0.0、SDK1.2.0、协议3保持不变。使用说明见[下界坐标倍率](../nether-coordinate-scale.md)。

## 自动检查

分别执行 `gradlew.bat build -PmcVersion=1.21.11 --offline --console=plain` 和 `gradlew.bat build -PmcVersion=26.3 --offline --console=plain`，修复后的最终两次构建均成功。

| 模块 | 检查数 |
| --- | ---: |
| API / Paper API | 9 / 4 |
| Common / Protocol | 3 / 13 |
| Paper 1.21.11 / 26.3 | 321 / 321 |
| Fabric 1.21.11 / 26.3 | 10 / 10 |
| 总计 | 691 |

新增检查涵盖：

- 默认8、倍率1/4/32/整数上限；负坐标、超大乘积、世界边界、偏移边界与较小 `max-world-size`；小数/零/负数/溢出拒绝。
- 玩家/实体双向事件映射，世界/Y/朝向与找门/建门参数保留，默认8、取消、末地/命令/插件原因不改写，NORMAL/ignoreCancelled。
- 既有文件补默认值、原子保存失败回滚且不通知；IMMEDIATE 注册、对局中覆盖/锁定/关闭覆盖恢复及旧冻结快照键集合兼容。
- 指南针搜索起点与移动参考采用当前倍率，真实访问记录保持；自定义请求替换世代、丢弃旧结果并保留原冷却时间。

原有定位协调器和共享缓存测试同时通过，包含取消后真实工作未完成不能提前释放配额、共享订阅取消及旧结果不提交等情况。原生定位/POI/区块探测预算未调整。配置交互沿用现有数值配置注册与呈现流程，静态核查内部键、双语名称、Tooltip 和带尾空格的点击预填路径。

一次独立只读审查确认较小 `server.properties max-world-size` 必须纳入裁剪；已加入针对性回归并修复，生产传送监听器和追踪投影均显式使用服务器上限。无剩余审查项。

## 产物核查

更新 `dist/1.21.11/`、`dist/26.3/` 四个产品 JAR，`dist/sdk/` 六个 SDK JAR及 `dist/SHA256SUMS.txt` 十个摘要。核查元数据、仅 `/mhu` 命令与新权限、Java21/25目标、API无平台类依赖、新共享实现与默认配置打包、153个双语键/占位符一致、Fabric资源与协议保持、SDK旧消费者链接和 `git diff --check`。

日志与机器报告位于 `build/nether-coordinate-scale/` 和 `build/reports/nether-coordinate-scale-artifacts.json`，属于本地构建证据。

没有运行真实服务器/客户端联机、实际找门/建门落点、实体穿门、实际聊天点击、其他插件协作或性能基准；编译与单元验证不等同于这些运行时测试。未提交、发布或推送代码；已有外部语言文件不自动修改。
