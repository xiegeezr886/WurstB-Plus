状态：不适用

## 对照证据

参考侧有 `render/TrueSight.kt`（让隐身的实体显形）。本工程 `hacks/TrueSightHack.java`（56 行）
用整套实体过滤器（17 条 `Filter*Setting`）决定"哪些隐身实体要显示"，消费点
`EntityMixin.java:95`（`onIsInvisibleTo`）。

| 关注点 | 位置 | 结论 |
| --- | --- | --- |
| 开关 | `:50-53` `shouldBeVisible` = `isEnabled() && entityFilters.testOne(entity)` | 正确（判定在方法内部，不依赖调用方） |
| 过滤器默认值 | `:20-41` 全部按"可见"（`genericVision(false)`）初始化 | 与参考的"默认全部显示"一致 |
| 攻击检测类实体 | 敌对/中立/猪灵等用 `AttackDetectingEntityFilter.Mode.OFF` | 正常（不依赖"是否在攻击你"） |

无改动。
