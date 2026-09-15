状态：不适用

## 对照证据

参考侧没有对应模块（参考的 `movement/AutoWalk.kt` 只是按键保持前进，没有寻路与随机漫游）。本项为自审，
全文读过 `hacks/AntiAfkHack.java`（259 行）。

## 自审记录

| 关注点 | 位置 | 结论 |
| --- | --- | --- |
| AI 模式寻路 | `:126-178` `RandomPathFinder` + `PathProcessor`，`setFallingAllowed(false)` / `setDivingAllowed(false)` | 正常 |
| 非 AI 模式 | `:179-204` 随机目标点 + `faceVectorClientIgnorePitch` + `keyUp`/`keyJump` | 正常 |
| 关闭时按键 | `:105-112` `onDisable` → `PathProcessor.releaseControls()`；该方法内部用 `IKeyBinding.resetPressedState()`（`ai/PathProcessor.java:75-80`），且 `CONTROLS` 覆盖 keyUp/keyDown/keyLeft/keyRight/**keyJump**/keyShift（`:23-25`） | 正常，**没有**按键泄漏 |
| 飞行状态 | `:96,124` 记录并每 tick 还原 `getAbilities().flying` | 正常（防止寻路按跳跃键时意外起飞） |
| 溺水保护 | `:129-134` 水下强制按跳跃 | 正常 |
| 与 AutoFish 互斥 | `:98` 开启时关闭 AutoFish（AutoFish 侧 `:117` 反向关闭） | 双向互斥，一致 |
| 死后退出的判断 | `:118-122` `getHealth() <= 0` → 自动关闭 | 正常 |

无改动。

## 记录（未改，无法证实可触发）

`:160-163` 的条件写法有括号优先级问题：

```java
			if(processor != null
				&& !pathFinder.isPathStillValid(processor.getIndex())
				|| processor.getTicksOffPath() > 20)
```

`&&` 优先级高于 `||`，实际等价于 `(processor != null && !valid) || processor.getTicksOffPath() > 20`；
当 `processor == null` 时会去调用 `processor.getTicksOffPath()`（NPE）。按作者意图应是
`processor != null && (!valid || ... > 20)`。

**为什么只记录不改**：本次没能构造出 `processor == null` 到达该行的路径 ——
`PathFinder.getProcessor()`（`ai/PathFinder.java:645-651`）永远返回非空对象；
`processor` 在 `:156` 赋值，而到达 `:160` 只可能发生在 `:144` 分支（`!isDone() && !isFailed()`）执行过之后，
`think()` 完成后必经 `:153-156`。也就是说这是**看似可空、实际不可达**的写法问题，
按本工程"没有反例就只记录"的规矩不擅自改语义。建议后续把括号补成作者本意。

## 建议（未做）

- `:124` 每 tick 强制还原 `flying`，副作用是开着 AntiAFK 时玩家自己无法切换飞行（创造模式双击空格无效）。
  这是有意为之（防止寻路起跳触发飞行），未改。
- `:132/:199` 水下按跳跃后没有对应的松开逻辑（依赖 `PathProcessor` 或下一 tick 的分支），未改。
