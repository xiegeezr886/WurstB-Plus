状态：已优化

## 对照证据

参考侧没有 `KillauraLegit`：它把"转视角"和"点击"拆成两个模块 `combat/AimBot.kt` 与
`combat/AutoClicker.kt`。本工程合成一个 hack。

| 关注点 | 参考（_oe_ref 相对路径:行） | 本工程（相对路径:行） | 判定 |
| --- | --- | --- | --- |
| 瞄准方式 | `AimBot.kt:79-92`：`Side.Client` 直接写 `mc.player.rotationYaw/Pitch`，`Side.Server` 发包 | `hacks/KillauraLegitHack.java:308-321 onMouseUpdate`：把算好的角度差加到**鼠标增量**上（`MouseUpdateListener`），真的动鼠标 | 本工程更 legit（参考的 Client 档是"硬设角度"，服务端能看到瞬移式转头）。没有 Server 档是刻意的：那是 packet aura 的做法 |
| 转动平滑 | `AimBot.kt:34` 只有 `factor`（0..100），实际是直接跳到目标角度 | `:285-291` `rotationSpeed`(°/s) + `RotationSmoothing.smoothWithAcceleration(..., LINEAR)`，并保留 `rotationDelta` 做加速度 | 本工程更强 |
| 目标选择 | `AimBot.kt:48-63`：每 tick 全量重排，循环里取"最后一个满足 range 的实体"，没有粘性/切换 | `:218-226` `CombatTargetUtils.get(...)` + `util/CombatTargetSession`（LiquidBounce tracker） | 本工程更强（有粘性、切换延迟、优势门限） |
| 过滤器 | `AimBot.kt:27-29`：`Players`/`Animals`/`Mobs` 三个开关 | `:111-139` `EntityFilterList`（24 项），且默认更严：`FilterSleepingSetting(true)`、`FilterFlyingSetting(0.5)`、`FilterInvisibleSetting(true)` | 本工程更强 |
| 视野 / 穿墙 | 参考没有 LOS 概念 | `:90-92` `Check line of sight` + `CombatTargetUtils.isValid(...)` 的 `BlockUtils.hasLineOfSight` | 本工程独有 |
| 攻击门槛 | `AutoClicker.kt:69-81`：只看按键 + CPS 随机延迟 | `:252-274 onHandleInput`：先 `isValidTarget` 复检，再要求 `RotationUtils.isFacingBox(...)`，才 `gameMode.attack` | 本工程不会"手还没转过去就挥" |
| 速度随机化 | `AutoClicker.kt:101-103 randomClickDelay(minCPS,maxCPS)` | `:52-59 speedRandMS`（±ms 抖动）+ `AttackSpeedSliderSetting` | 等价 |
| 抖动点击 | `AutoClicker.kt:32,38-64` 有 `JitterClick`（随机 ±1° 抖 yaw/pitch） | 无 | 见「故意不搬」 |

## 实际改动

### `Switch delay` / `Switch advantage` 加了 `visibleWhen`

`util/TargetTracker.java:36-37` 在 `sticky && currentValid` 时直接 `return target`，后面的
`switchDelay`（`:45-46`）与 `switchAdvantage`（`:48-60`）分支根本走不到；而本 hack 的
`Sticky target` 默认是 **true**（`:77-79`）。所以默认配置下这两个滑条从 4 调到 20、从 0% 调到 100%
都不产生任何行为变化。

修法（不改行为，只让设置面板说实话）：两个滑条加
`.visibleWhen(() -> !stickyTarget.isChecked())`（`:81-90`），与 `KillauraHack.java:131-140` 一致。
反例：`Sticky target` 勾着（默认）+ 把 `Switch delay` 从 4 改成 20 → 新旧行为完全相同；新版本下这个滑条
干脆不显示，只有关掉 `Sticky target` 才出现。

## 故意不搬

- `AutoClicker.kt:32,38-64` 的 `JitterClick`：随机 ±1° 抖视角属于反作弊规避手段，会直接干扰 legit 手感；
  本工程的 `RotationSmoothing`（`EASE_IN_OUT` 等曲线 + 加速度）已经避免了"完美线性转动"这一特征。
- `AimBot.kt:85-89` 的 `Side.Server` 档：`KillauraLegit` 的定义就是"用真鼠标转过去"，
  发包转头应交给 `Killaura`（`RotationMode.SILENT`）。
- `AimBot.kt:97-116` 的弓箭弹道瞄准：本工程有独立的 `BowAimbot`，不往这里塞。

## 建议（未做）

- `faceEntityClient`（`:276-299`）返回 `boolean`，但唯一调用点 `:241` 忽略它（攻击门槛在
  `onHandleInput` 里另做 `isFacingBox` 检查）。只是死代码，改动无收益，未动。
- 参考的 `AutoClicker` 还带 `RightClick`（右键连点），本工程对应的是独立的 `RightClicker`（仍待办）。
