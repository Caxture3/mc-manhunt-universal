# Java 包名迁移

用户指定根包为 `com.caxture.manhuntuniversal`。此次只实施包名及入口命名迁移，扩展 API 作为设计建议提供，不新增角色轮换玩法。

- [x] 迁移 Paper/Fabric/protocol 的生产和测试包路径，更新导入、元数据入口与构建 group。
- [x] 服务端/客户端入口类使用 ManhuntUniversal 命名，协议载荷、Mod ID、资源/PDC 命名空间和权限继续兼容。
- [x] 更新当前 README/项目约束和历史记录中的现状说明，保留历史事实。
- [x] 双版本构建、现有针对性检查、JAR 类路径和部署包/校验和核对。
- [x] 根据现有角色/淘汰/追踪/展示结构，给出必要公共 API 及一致性约束的建议。

验证：133 个业务/测试文件及两个 package-info.java 共 135 个 Java 文件与迁移前逐一比较，除包名、入口名及一条异常提示品牌名称外无逻辑修改。`clean build` 与最终构建成功，两个 `-PmcVersion` 独立入口成功，每版 51 项检查通过。四个部署 JAR 均无旧 Java 包路径或常量池引用，入口可解析，Java 字节码符合目标版本，Fabric 资源与迁移前逐字节相同。部署文件及 `dist/SHA256SUMS.txt` 已更新，报告为 `build/reports/package-rename-artifacts.json`。未运行游戏实例。

扩展接口建议另见 [API 建议](../../extension-api-proposal.md)，尚未实现或发布公共 API。
