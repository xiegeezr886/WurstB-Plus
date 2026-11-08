# 根目录 Forge 1.20.1 生产包

仅修改 `C:/Users/ui863/Documents/trae_projects/VAP-v1.5.0-1.20.1` 根工程，保持 `v1.5.1-Forge-1.20.1`。已将四客户端移植的前 18 批合并到 86 个文件，并保留该工程已有的界面、模块与配置修改。8 个子工程的 7144 份源码／配置文件哈希均未改变。

生产包：`build/libs/WurstB+ Plus-v1.5.1-Forge-1.20.1.jar`，48,664,131 字节。同目录已生成 `.jar.sha256` 校验文件。

`compileJava`、`jarJar`、`reobfJarJar` 均通过。直接执行当前生产源码的第 13–18 批行为测试通过。包内检查覆盖新增模块类、107 项 Mixin 注册、SRG 映射、Java 17 字节码、5 个内嵌依赖、元数据及 ZIP 完整性。

完整测试集未通过：`compileTestJava` 被 9 个已有且本次未修改的 HUD／ScreenRegistry 测试文件阻塞，引用了缺失类或旧接口。生产包已独立构建成功；未进行游戏内运行验证。

四客户端的整体移植尚未完成。本次将已有的已验证批次补入指定根工程，不代表全部 427 个源码模块均已实现。

SHA-256：`71d5f9656ab73e8a90651ea522d34f8ee27fb47f9a4329640a393791af3b7d78`

构建记录：`.planning/2026-11-07-four-client-v151-port/external-v151/production-package.log`；核验记录：同目录 `production-verification.json`。
