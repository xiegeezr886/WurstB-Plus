状态：已优化

## 对照证据

参考侧：`_oe_ref/src/main/kotlin/studio/coni/epsilon/module/render/FullBright.kt`。

| 关注点 | 参考（FullBright.kt:行） | 本工程（hacks/FullbrightHack.java:行） | 判定 |
| --- | --- | --- | --- |
| Gamma 方式 | `:29-30` Gamma 模式下每 tick `if (mc.gameSettings.gammaSetting <= 100f) mc.gameSettings.gammaSetting++`（1.12.2 是 0..100 刻度，加满即停） | `:82-92` 启用且方法=GAMMA 时 `setGamma(16)`；`:94-112` 关掉 fade 或差值 ≤0.5 时一步到位，否则每 tick 走 0.5 | 等价思路，本工程直接写超范围的绝对值 16（1.20.1 的 `Options.gamma()` 是 0..1 的 `OptionInstance<Double>`），靠 `ISimpleOption.forceSetValue`（`:99,104,109,111`）绕过滑条范围，比逐 tick 加满更快 |
| 方案切换 | `:22` `Mode`（Gamma/…）+ `:23` `Effects` 开关 | `:27-30` `Method`（GAMMA / NIGHT_VISION） | 本工程把两种方式做成同一设置的互斥选项，语义更清楚 |
| 夜视实现 | `:11,23` 真加 `PotionEffect` | `:133-161` 只维护本地 `nightVisionStrength`（0..1，每 tick ±0.03125），由 `ClientPlayerEntityMixin.hasStatusEffect()` 假造（`:196` 的注释指向那里） | 本工程不发真实状态效果 → 服务端看不到药水；属更强的做法，不搬参考的真加药水 |
| 关闭后还原 | `:45-54` `onEnable` 存 `prevGamma`、`onDisable` 原样还原 | `:114-131` `resetGamma(defaultGamma.getValue())`；`defaultGamma` 由 `:55-73` 启动时自动取当时的 gamma（`:68`），也允许用户自己改（`:35-38`） | 本工程把"还原到哪个值"变成设置项，比只记 `prevGamma` 更可控 |
| 监听器生命周期 | 无（Kotlin 模块开关即生效） | `:52` **构造器里**就 `EVENTS.add(UpdateListener.class, this)`，不在 `onEnable` | 必要设计：关闭后 `onUpdate` 仍要跑，才能把夜视强度淡出、并把 gamma 还原；`isChangingGamma()`（`:163-166`）里有 `isEnabled()` 守卫，不会在关闭状态继续改 gamma |
| 调试残留 | 无 | `:63`（改前）`System.out.println("Brightness started at " + gamma);` | **真实缺陷（本轮已删）**，见下 |

## 实际改动

`hacks/FullbrightHack.java`：删掉 `checkGammaOnStartup()` 里的
`System.out.println("Brightness started at " + gamma);`。

新旧行为差异（可复现）：`checkGammaOnStartup()` 在**构造器**里就 `:51,57-72` 注册了一个一次性
`UpdateListener`，它在第一个 tick 必然执行一次并自摘；旧版因此每次启动客户端都会往 stdout 打一行
`Brightness started at 0.5`（或当时的 gamma 值），新版不输出。全仓只有这一处
（`grep -r "Brightness started at" src/` 仅命中该行），属遗留调试输出。

未改动任何设置名称、默认值、范围（`Method`/`Fade`/`Default brightness` 全部原样）。

## 故意不搬

1. `FullBright.kt:30` 的 `gammaSetting++` 逐 tick 加 1、上限 100：1.12.2 的 gamma 是 0..100 刻度，
   1.20.1 是 0..1 的 `OptionInstance<Double>`。照搬刻度没有意义，本工程用超范围赋值达到同一效果。
2. `FullBright.kt:23` 的 `Effects` 开关（真加夜视药水）：本工程 `Method.NIGHT_VISION` 已覆盖同一功能，
   且实现方式（本地假造）刻意不同，不搬。
3. `FullBright.kt:45-54` 的 `prevGamma` 字段：本工程用 `defaultGamma` 设置 + `wasGammaChanged`
   （`:40,90-91,96,123`）表达同一件事，且多了"用户可以指定还原值"，不搬。

## 建议（未做）

1. `:102,120` 的 fade 阈值 0.5 是字面量：gamma 与目标差 ≤0.5 就一步到位。因为 gamma 目标 16 远大于
   起始值（通常 0..1），实际总是走渐亮分支；保留。
2. `:168-175` `getDefaultGamma()` 被 `XRayHack` 复用：如果用户把 `Default brightness` 设成 1.0，
   X-Ray 关闭时会按同一个值还原 gamma——两个 hack 共用一个设置，属既有设计（X-Ray 自己也会提高 gamma），
   未动。
3. 验证边界：本轮只跑了 `compileJava`/`test`（全绿），没有实机看亮度渐变与夜视表现。
