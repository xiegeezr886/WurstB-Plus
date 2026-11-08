# 四客户端移植：第 17／18 批

Reach 与 WeatherChanger 已合并到全部 66 个 v1.5.1 工程。保留原有设置名称和默认值，新增设置与已有模块整合。

| 模块 | 本批改动 |
|---|---|
| Reach | 新增可选附加距离模式，实体和方块分别设置；现代版本在原版与属性计算后加一次距离。修正 35 个适配使用实体／方块最大值的问题，补齐 4 个 1.20.5／1.20.6 工程缺失的回调，解决 1 个早退条件导致回调失效的问题，并移除重复适配。旧版实体搜索不再固定截断到 6 格。 |
| WeatherChanger | 改为读取时覆盖当前世界的雨强和雷暴强度，服务器天气包与真实状态继续更新；关闭后恢复最新天气，避免丢失停雨通知或重复衰减雷暴强度。保留 NoWeather 优先级，隔离旧世界和其他世界。 |

五个代表工程编译通过：Fabric 1.20.1、Fabric 1.20.5、Forge 1.20.6、Forge 1.21.3、Fabric 26.3。生产源码行为测试通过，涵盖独立距离、附加模式、属性与创造模式基值、关闭、切换玩家／世界、天气服务器更新与优先级。两批各 23 个游戏版本的回调目标检查通过。最终核验确认 405 个合并输出文件哈希与 66 个工程的注册正确，66/66 个工程的当前完整源码、资源及构建配置对应的 compileJava 全部通过。完成快照为 `.planning/2026-11-07-four-client-v151-port/batch18-completed-verification.json`。

没有进行游戏内 Mixin 应用、实际瞄准／天气画面、多人服务器或第三方模组兼容性验证。全部四客户端源码清单共 427 个具体模块类，整体移植仍未完成。

复查命令：

```powershell
python -X utf8 scripts/port/test_four_client_batch17.py
python -X utf8 scripts/port/test_four_client_batch17.py --target fabric/versions/1.20.5
python -X utf8 scripts/port/test_four_client_batch17.py --target fabric/versions/26.3
python -X utf8 scripts/port/test_four_client_batch18.py
python -X utf8 scripts/port/test_four_client_batch18.py --target fabric/versions/26.3
python -X utf8 scripts/port/check_four_client_batch17_hooks.py
python -X utf8 scripts/port/check_four_client_batch18_hooks.py
python -X utf8 scripts/port/verify_four_client_batch18.py
```

后续差距包含 NoGhostBlocks 的本地预测控制、PacketCanceller 的双向选择与优先级、FO 图形化附魔规则编辑，以及尚未逐项核对的模块行为。详细覆盖清单位于 `.planning/2026-11-07-four-client-v151-port/coverage.md` 和 `module-matrix.json`。
