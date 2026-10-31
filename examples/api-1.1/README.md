# API 1.1 示例

[ExampleExtension](src/main/java/example/mhu/ExampleExtension.java) 演示获取服务、能力检查、注册配置与动态追踪目标、覆盖前缀、追加结算、逐观察者名称装饰、配置变化后延迟失效，以及停用时统一注销。

[ConfigurationGroupExample](src/main/java/example/mhu/ConfigurationGroupExample.java) 演示带整体校验的分组配置和存储委托。传入的 `ConfigStorage` 必须由扩展实现原子保存；命令修改 `example:interval.minimum` 时也会校验和保存完整映射。

示例源码纳入 `:api-paper:compileExamplesJava`，两版构建的 `check` 都会编译它们。示例没有自动打包、安装或启用；需要使用时复制到自己的 Paper 插件工程，并采用 [plugin.yml](src/main/resources/plugin.yml)。依赖配置和完整规则见[扩展指南](../../docs/extension-api-1.1.md)。

主示例的配置只保存在内存，不会为服务器写入文件。追踪目标返回玩家当前维度中的检查点，不会访问或生成区块。要接管核心掉落，应将对应功能策略改为 `SKIP_CORE`，并由扩展自己的 Paper 监听器实现完整效果；示例默认返回 `DEFAULT`，不会改变现有掉落。
