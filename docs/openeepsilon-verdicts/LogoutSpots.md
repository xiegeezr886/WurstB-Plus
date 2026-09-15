状态：不适用（参考侧无对应模块，本工程实现自洽，零改动）

## 参考侧核对

`_oe_ref` 全仓没有"下线玩家最后位置"模块：`render/Waypoint.kt` 是手工坐标点（不依赖玩家上下线），`combat/AutoLog.kt` 是**自己**自动下线，`misc/AntiBot.kt` 是判定假人，`client/NotificationRender.kt` 只负责弹提示。方向都对不上，故判「不适用」，主体是自审。

## 本工程现状（自审）

`hacks/LogoutSpotsHack.java`，199 行。

| 关注点 | 位置 | 结论 |
| --- | --- | --- |
| 玩家来源 | `:92-101` `MC.level.players()`（= 服务端发下来的玩家列表，不是渲染列表），排除自己、只留 `RemotePlayer` | 用玩家列表而不是"看得见的实体"，玩家在视野外下线也能记录；`RemotePlayer` 判定同时排除了假人（`FakePlayerEntity` 不是 `RemotePlayer`） |
| 记录时机 | `:104-107` 上一 tick 还在线的玩家、这一 tick 不在 `onlinePlayers` 里 → `putIfAbsent(uuid, Spot(uuid, name, tracked.pos, now))` | 位置取的是**上一 tick 观测到的** `tracked.pos`（`:98-99` 每 tick 用 `player.position()` 刷新），即真正的"最后可见位置"；`putIfAbsent` 保证同一次下线只记一条、且保留最早的时间戳 |
| 重新上线 | `:100` `spots.remove(uuid)` | 玩家一上线就清掉提示，不会留着过期的点 |
| 换世界/换维度 | `:79-83` `onWorldChange` 同时 `spots.clear()` 与 `trackedPlayers.clear()` | 两个 map 都清，不会把上个世界的坐标带到下个世界（这是此类 hack 最常见的残留 bug，本工程没有） |
| 空世界守卫 | `:88-89` `MC.level == null` 直接 return | 退出世界的那一帧不会 NPE |
| 上限与淘汰 | `:44-45` `Max spots`（1..200，默认 50）；`:112-121` 超过上限时反复找 `time` 最小的删掉 | 按"最早记为下线"淘汰，语义正确（不是按距离或随机） |
| 渲染 | `:126-176` 画方框；`:174-197` `showName`（默认 true）时再画名字 | `showName` 关闭只影响文字，方框仍在，符合选项语义 |

## 实际改动

无。没有可举证的新旧行为差异，按简报第 9 条不动代码。

## 故意不搬

参考侧无对应模块。

## 建议（未做）

1. `:103` 用 `System.currentTimeMillis()` 记录时间，`:117` 用 `s.time` 做淘汰排序。时间只用于"谁最早"，不需要墙钟；用单调递增的计数器可以避免系统时间被调整（NTP 回拨）时排序抖动。构造不出实际影响的输入（`time` 只参与比较），未做。
2. `:104-107` 只对"上一 tick 在线、这一 tick 不在"的玩家记点：如果玩家在**本 hack 开启之前**就下线了，不会有记录（因为 `trackedPlayers` 是空的）。这是"启用后才开始追踪"的既有语义，符合预期，不改。
3. `:113-121` 的淘汰是 O(n) 找最小、循环到不超限，n ≤ 200+，可忽略。
4. 验证边界：零改动，只做了静态核对；多维度切换与服务器换图的实机表现没有验证。
