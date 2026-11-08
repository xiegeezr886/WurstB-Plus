# 四客户端移植：第 16 批

TimeChanger 已同步至全部 66 个 v1.5.1 工程。模块改为在读取时覆盖显示时间，服务器时间包、停表标志、时钟速率和实际计时继续正常更新；关闭后立即显示最新时间，避免恢复到启用时的旧值。

旧版适配 Level 时间读取和现有 NoWeather 渲染路径；26.x 仅覆盖当前世界的主世界时钟，并保留 NoWeather 时间设置的优先级。补齐 5 个 Fabric 26.x 工程漏掉的时钟 Mixin 注册。旧版 Mixin 不支持接口回调注入，因此使用项目已有的具体类接口方法实现方式。

验证通过：66/66 当前完整输入的 compileJava、221 个改动文件哈希、66 个工程注册、23 个游戏版本的目标方法及底层计时检查。四个代表版本的生产源码回调测试通过，覆盖即时设置、服务器更新、关闭、世界切换、停表与优先级。

本批未进行游戏内 Mixin 应用、画面或多人服务器验证。整体 427 个源码模块的移植仍未完成。

完成快照：`.planning/2026-11-07-four-client-v151-port/batch16-completed-verification.json`。复查命令：`python -X utf8 scripts/port/verify_four_client_batch16.py`。

第 17／18 批继续扩展了 Mixin 注册配置，因此第 16 批快照代表当时的已验证输入；后续完整工作区请使用最新的 `verify_four_client_batch18.py`。
