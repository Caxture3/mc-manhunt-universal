# 开局位置配置标题语言修复

2026-11-07。位置配置界面错误地读取 `config.description.player-start-pos.title`，内置双语资源实际定义为 `config.name.player-start-pos.title`，导致显示原键名并报警。现已统一到实际名称键；同步更正文档中猎人等待时间、开局位置和下界倍率的语言键说明，没有改写已有外部语言文件。

使用实际打包资源新增两项检查：双语标题均解析为对应文本且无告警；旧外部文件缺键时使用内置文本，合法自定义文本优先且外部配置不被修改。原错误键下两项均失败，修正后两版通过。外部文件缺键仍沿用既有的一次性缺项警告规则，可按正确键手动补入。

分别执行 `gradlew.bat build -PmcVersion=1.21.11 --offline --console=plain` 和 `gradlew.bat build -PmcVersion=26.3 --offline --console=plain`，两版构建成功，全部模块733项检查通过。核对并更新 `dist/` 产物和校验和，产品1.0.0、SDK1.2.0、协议3不变。日志位于 `build/start-position-language-fix/`，机器报告为 `build/reports/start-position-language-fix-artifacts.json`。未运行实际联机验证。
