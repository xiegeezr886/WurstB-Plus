# 四客户端移植：第 13／14 批

本轮沿用“重复功能合并到已有模块，补齐设置和行为，新增独有模块”的决定，将以下修复同步到全部 66 个 v1.5.1 工程。四客户端全部功能的移植仍未完成；源码清单包含 427 个具体模块类，不能把同名模块视为行为已经一致。

| 已有模块 | 合并或修复的行为 |
|---|---|
| NoRotate | 在服务器旋转包处理结束时立即恢复视角，分别处理 yaw/pitch；逐字段保留原有头部、身体与插值角度，保留传送位置，避免旧玩家或嵌套调用覆盖当前视角。 |
| PortalGodMode | 只保存最近一次传送确认；关闭时仅向原连接释放，发送前清空状态，避免跨服务器重放和发送失败后重复释放。 |
| CustomFov | 将世界 FOV、手持物品 FOV 接入实际渲染回调；世界 FOV 可与原有 Zoom 叠加，手持物品保持独立。设置即时生效，无需修改游戏自身 FOV 选项。 |
| ChatHider | 关闭后的迟到更新不再覆盖已恢复的聊天透明度；保留打开聊天时的透明度设置和上游的零透明度恢复行为。 |

适配覆盖旧版返回 double 的 GameRenderer、较新版返回 float 的 GameRenderer，以及 26.x 的 Camera 世界／手部回调。补齐了 18 个 1.21.6–1.21.11 工程缺失的原有 FOV/Zoom 渲染回调。

验证进度：四个代表版本（Fabric 1.20.1、1.21.5、1.21.10、26.3）编译通过。旧版和 26.3 的旋转／传送确认测试通过；四种版本的 FOV、Zoom 组合及聊天恢复测试通过。测试复制未改写的模块源码，并直接执行从实际渲染文件复制的回调方法。23 个映射游戏版本的回调目标检查通过。66/66 个工程的最新完整输入编译已验证通过，1056 个累计输出文件哈希与 66 个工程的注册检查一致。完成快照见 `batch14-completed-verification.json`。

本轮没有进行游戏内 Mixin 应用、实际画面、多人服务器交互或第三方模组兼容性验证。

复查命令：

```powershell
python -X utf8 scripts/port/test_four_client_batch13.py
python -X utf8 scripts/port/test_four_client_batch13.py --target fabric/versions/26.3
python -X utf8 scripts/port/test_four_client_batch14.py
python -X utf8 scripts/port/test_four_client_batch14.py --target fabric/versions/26.3
python -X utf8 scripts/port/check_four_client_batch13_hooks.py
python -X utf8 scripts/port/check_four_client_batch14_hooks.py
python -X utf8 scripts/port/verify_four_client_batch14.py
```

累计覆盖与剩余差距记录在 `.planning/2026-11-07-four-client-v151-port/coverage.md` 和 `module-matrix.json`。下轮已确认的差距包括 TimeChanger 的服务器时钟恢复、Reach 的相对距离、Sneak 的飞行条件、Parkour 的方块碰撞筛选、NoGhostBlocks 的本地预测控制，以及 PacketCanceller 的子类匹配与双向选择器。
