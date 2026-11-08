# 四客户端移植：第 15 批

Parkour、Sneak 已合并到全部 66 个 v1.5.1 工程，保留原有默认行为。

| 模块 | 本批改动 |
|---|---|
| Parkour | 增加可选的仅方块碰撞检测，使用下移、水平缩进的包围盒判断支撑，避免实体碰撞阻止边缘起跳；保留原深度、边缘距离和潜行设置。 |
| Sneak | 飞行暂停选项同时作用于 Legit、Packet 模式；切换模式释放旧状态，关闭恢复实际按键，包状态绑定原玩家，避免重生或切服后释放到新玩家。 |

66/66 个工程的最新完整源码、资源和构建配置对应的 compileJava 已通过；132 个改动文件哈希与模块注册检查通过。1.20.1、26.3 的未改写生产源码行为测试通过，覆盖实际 Minecraft AABB 几何、模式切换、断线、按键恢复与新旧潜行包格式。

Forge 1.21.10 配置阶段的联网超时通过任务专用 init script 向 Mavenizer 传入 `--offline` 解决，未修改产品构建配置。另有一个工程的 Mixin 配置被其他工作更新，已保留修改并补跑成功。验证快照为 `.planning/2026-11-07-four-client-v151-port/batch15-completed-verification.json`。

本批未进行游戏内运行或多人服务器验证。四客户端共 427 个具体模块类的整体移植仍在进行，覆盖清单不将同名功能直接视为已完成。

复查：`python -X utf8 scripts/port/verify_four_client_batch15.py`；行为测试为 `test_four_client_batch15.py`，可加 `--target fabric/versions/26.3`。
