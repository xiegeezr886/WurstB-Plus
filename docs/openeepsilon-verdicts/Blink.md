状态：不适用（参考侧无对应模块，本工程实现已自洽，零改动）

## 参考侧核对

`D:\WurstB\_oe_ref` 全仓 449 个 `.kt` 里没有任何 Blink / 假延迟发包模块：

```
Get-ChildItem -Recurse -Filter *.kt D:\WurstB\_oe_ref | Select-String -Pattern Blink -List
→ ModuleManager.kt, SkinBlinker.kt        # 都是「皮肤闪烁/模块注册表」，与发包无关
```

参考的 `LagBackCheck.kt`（player 分类）是**检测自己被拉回**的告警，不是缓存发包，方向相反。因此没有可对照的实现，判「不适用」。

## 本工程现状（自审）

`hacks/BlinkHack.java`，198 行，功能已经完整：

| 关注点 | 位置 | 结论 |
| --- | --- | --- |
| 队列上限 | `:114-118` `movePacketCount >= limit` → 关开一次（触发 flush + 清零） | 有上限，且是用户可设的既存设置 |
| 上限设 0 | `:111-112` 直接 return | 0 = 不限量，属既有设置语义 |
| 缓存哪些包 | `:126-131` 所有 `ServerboundMovePlayerPacket`；`:133-141` 开了 `allPackets` 才额外缓存 action/useItemOn/interact/useItem | 与「位置 blink」语义一致 |
| 去重 | `:154-162` 与上一包逐字段比对（onGround/rot/x/y/z），重复的不入队 | 站着不动时不会灌爆队列 |
| 世界/连接丢失 | `:103-109` player/level/connection 任一为空 → `clearQueuedPackets()` + 自动关闭 | 不会跨世界残留 |
| 关 hack | `:86-98` 先摘监听器再 `flushQueuedPackets()` | 顺序正确（不会把自己 flush 出来的包又拦回去） |
| 假人渲染 | `:49,76-` `FakePlayerEntity` | 附带功能，与参考的 `FakePlayer.kt` 无关 |

## 故意不搬

- 参考没有 Blink；`SkinBlinker.kt` / `LagBackCheck.kt` 名字相近但语义无关，不搬。
- 1.12.2 常见的「`CPacketPlayer` 全拦 + 计时器一次性补发」与 `:114-118` 的上限机制等价，本工程版本还多一层去重与失败兜底。

## 建议（未做）

1. `:114-118` 用「关开一次」来触发 flush，会顺带重置 `startTime` 之类与本 hack 无关的状态（本 hack 无）；如果有别的 hack 监听 `EnabledListener`，会看到一次多余的抖动。换成显式的 `flushQueuedPackets()` 更干净——但那是行为等价的重构，不修也不影响正确性，未动。
2. 没有实机验证：`ServerboundMovePlayerPacket` 的补发顺序、服务端的 `ClientboundPlayerPositionPacket` 纠正后的表现都需要真人跑一次才能确认，本轮只做了静态核对。
