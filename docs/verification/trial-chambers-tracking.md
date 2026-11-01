# 试炼密室追踪与四列界面验证

2026-11-01 新增 `core:trial_chambers`，默认配置为 `runner-structure-tracker-unlock-time.trial_chambers: -1`。按用户补充要求，Fabric 每页最多四个等宽选项，四个结构全部启用时同页显示；动态 Paper Dialog 最多四列。产品 1.0.0、SDK 1.1.0、协议 3 保持不变。

## 实现与检查

试炼密室使用主世界 `Structure.TRIAL_CHAMBERS` 定位，沿用已有同步原版定位、共享缓存/合并、附近结构部件校正与两版结构引用适配器。没有新增世界查询算法或改变原生查询预算。

旧配置补齐禁用的新字段，原有三个值保持不变。试炼密室复用正式开始时间、隐藏/锁定、服务端选项校验、解锁通知和指南针授予/撤回规则。四个内置目标合计占用目录32项中的4项，最多再注册28个扩展目标。旧三目标帧/枚举不变，新目标只走动态目录；legacy 校验显式拒绝该选项，旧播报器跳过它。

配置/目标目录与布局均有 RED→GREEN 记录。新增和调整检查覆盖：

- 默认 -1、旧配置迁移、单项保存及非法值、只有试炼启用与全部禁用。
- 正式开始时间及精确解锁边界，锁定选择拒绝、解锁后选择、再禁用清除选择。
- 两版 native 结构引用只匹配 minecraft:trial_chambers，主世界目录元数据、原生定位委托与协议3目录往返。
- 四内置加28扩展的容量边界，四项同页、五项翻页、32项八页、等宽/点击边界和删项后页码回退。
- 试炼背景存在/缺失时的材质/颜色回退；背景缺失不影响可选状态。
- 原有保存失败测试补齐第四字段，并检查写入委托确实被调用，避免仅因 schema 不完整而假通过。

分别执行 `build '-PmcVersion=1.21.11' --offline --console=plain` 和 `build '-PmcVersion=26.3' --offline --console=plain`，均通过：

| 模块 | 通过检查数 |
| --- | ---: |
| api / api-paper / common / protocol | 7 / 4 / 3 / 13 |
| Paper 1.21.11 / 26.3 | 296 / 296 |
| Fabric 1.21.11 / 26.3 | 10 / 10 |
| 合计 | 639 |

一次独立只读审查未发现本次改动引入的 Critical/Important 问题。

## 资源与产物

背景来自用户指定参考项目的 `fabric/src/main/resources/assets/manhuntamongus/textures/background/compass_selector/trial_chambers.png`，原样复制到本项目共享资源。大小809817字节、2560×1440，SHA-256：

```text
56639b70a5e151a0afcfce8af3bfa8463f5f5531949bb32e92c04ab92cdf20f8
```

两版 Fabric 部署 JAR 内同名资源均与此摘要一致；没有修改像素或重新编码。`javap` 核对两份最终 Fabric JAR 的 `OPTIONS_PER_PAGE = 4`。

四个部署和六个 SDK JAR 已更新，`dist/SHA256SUMS.txt` 摘要及十项完整清单均复核。校验默认四字段 YAML、150 个中英文键及占位符、资源与源码一致、管理权限、元数据、字节码及平台隔离；最终 SDK 的旧1.0消费者链接检查通过。`git diff --check` 通过。日志和机器报告分别为 `build/trial-chambers/`、`build/reports/trial-chambers-artifacts.json`，不提交仓库。

## 未执行

未启动实际服务器/客户端，未进行试炼密室实服定位、四列界面截图、资源包重载或性能采样。构建和逻辑检查不等同于游戏运行验证；原版首次定位的同步开销仍然存在。外部语言文件没有自动修改。
