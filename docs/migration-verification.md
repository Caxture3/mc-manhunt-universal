# 迁移验证记录

日期：2026-10-25。范围按用户要求保持精简，没有运行旧工程完整测试套件、Paper 服务器或 Minecraft 客户端。

## 构建

| 命令 | 结果 |
| --- | --- |
| `.\gradlew.bat clean build --console=plain` | 成功，39 个任务执行，生成四个部署 JAR |
| `.\gradlew.bat build '-PmcVersion=1.21.11' --console=plain` | 成功，独立配置并构建 1.21.11 |
| `.\gradlew.bat build '-PmcVersion=26.3' --console=plain` | 成功，独立配置并构建 26.3 |

两个 Paper 模块各执行 4 项针对性检查，合计 8 项通过：初始抽签及固定名单、无轮换入口与参数、速通者死亡和单轮结算、猎人死亡/非目标末地不结算与目标龙结算、取消倒计时不生成历史且可再开局（相关断言分组在 4 个测试中）。

## 部署包检查

- 1.21.11 的全部 class 不高于 Java 21 / major 65，26.3 不高于 Java 25 / major 69。
- Paper 与 Fabric 均包含原协议类，入口及元数据正确展开；Fabric 为 client 环境且限制精确 Minecraft 版本。
- 服务端包不含 Fabric 实现，客户端包不含 Paper 插件类；测试 class 和 Minecraft 类没有混入发布包。
- Fabric 1.21.11 为 `intermediary` remap 产物，26.3 为 `official` 普通 JAR。
- Paper 1.21.11 manifest 包含 `paperweight-mappings-namespace: mojang`；版本 POI 适配的 NMS 公共签名已通过实际 named JAR 的 `javap` 核对，并进行独立只读审查。
- 旧协议 11 个源文件逐字节一致；原 Fabric 素材均存在，26.3 资源逐字节相同，1.21.11 只按版本修改 shader 布局。
- 两份服务端语言 YAML 可解析且均为 82 个匹配键；轮换配置及文案已删除。
- 指南针 PDC 显式保留 `randomanhunt` 命名空间，不受新插件显示名影响。

独立源码审查确认，轮换移除没有误删倒计时、龙探测、指南针搜索、重连复位、胜负或停止清理流程。

## 交付

四个部署 JAR 已复制到 `dist/1.21.11/`、`dist/26.3/`，与各模块最终构建输出逐字节相同。SHA-256 位于 `dist/SHA256SUMS.txt`，详细产物大小及校验和记录在 `build/reports/migration-artifacts.json`。

游戏加载、GPU shader 渲染、真实世界 POI 搜索和多人联机流程尚未实测；本记录不将编译和静态核对表述为游戏内验收。CI 配置保留，但本次没有触发远程工作流。
