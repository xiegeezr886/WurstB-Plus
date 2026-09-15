状态：不适用

## 对照证据

参考侧有对应模块 `module/misc/AutoFish.kt`（164 行）。

| 关注点 | 参考（`AutoFish.kt:行`） | 本工程（`hacks/AutoFishHack.java`） | 判定 |
| --- | --- | --- | --- |
| 咬钩判定 | `:24,47-49,120-137` 模式 BOUNCE（浮标运动）+ SPLASH/ANY_SPLASH/ALL（音效名子串） | `:38-53` Bite mode：Sound（`FISHING_BOBBER_SPLASH` 音效包）/ Entity（浮标实体数据包） | 本工程更准；见下 |
| 音效过滤 | 只有 SPLASH 模式才判距离（`:121-123`），其它模式任何水花音都算 | `:210-215` 一律按"切比雪夫距离 ≤ Valid range"过滤 | 本工程不会把别人的咬钩算成自己的 |
| 自动抛竿 | `:74-81` `autoCast` + `castDelay`（秒） | `:147-159` 不在钓鱼状态就抛竿，`castRodTimer` 用 `Retry delay` 控制 | 等价 |
| 收竿延迟 | `:34-35` `catchDelay` 毫秒 | `:55-58` `Catch delay` tick | 等价 |
| 重新抛竿延迟 | `:36-42` `recastDelay` | `:59-63` `Retry delay` tick | 等价 |
| 随机扰动 | `:43-44,160-163` `variation` 毫秒随机 | 无 | 见建议 |
| 上钩实体 | 无 | `:170-171` `getHookedIn() != null` 也会收竿 | 本工程多一种情况 |
| 附加功能 | 无 | 鱼竿自动切换、钓鱼点管理、浅水警告、调试绘制（`hacks/autofish/*`） | 本工程明显更多 |

## 结论

本工程的 AutoFish 已经把参考能提供的判定方式都覆盖了（Sound 对应参考的 SPLASH 系、Entity 是参考没有的
更准方案），参考的 BOUNCE（靠 `motionY` 抖动猜咬钩）反而不如 Entity 模式可靠，且参考的
ANY_SPLASH/ALL 会把别的玩家的水花当成自己的咬钩（`:128-132` 匹配 `entity.generic.splash` 等通用音效）
—— 本工程不做这种放宽。没有可搬的判断，无改动。

## 建议（未做）

- 参考的 `variation`（延迟随机化，`:160-163`）没有对应实现。加随机延迟对反作弊更友好，属于功能增强；
  本工程已有 Entity/Sound 双模式与 `Valid range`，未做。
- `:243-246` 调试绘制由子模块的开关控制，默认关闭，未改。
