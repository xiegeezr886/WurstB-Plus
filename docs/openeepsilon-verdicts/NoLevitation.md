状态：不适用

## 对照证据

参考侧有对应模块 `module/movement/AntiLevitation.kt`（19 行）：每 tick 调
`player.removeActivePotionEffect(MobEffects.LEVITATION)`（1.12.2 的客户端移除写法）。

本工程的实现是**标记 + mixin**：`hacks/NoLevitationHack.java` 本身只有一个开关，
真正的屏蔽在 `mixin/ClientPlayerEntityMixin.java:334`（`noLevitationHack.isEnabled()` 时让
`hasStatusEffect`/`getStatusEffect` 对飘浮返回 false）。

| 关注点 | 参考 | 本工程 | 判定 |
| --- | --- | --- | --- |
| 机制 | 客户端删掉效果对象 | 让原版查不到该效果（`hasStatusEffect` 返回 false） | 本工程更彻底：1.13+ 飘浮的位移是在 `LivingEntity.travel()` 里按 `hasEffect(LEVITATION)` 现算的，删效果对象会在下一次同步时被服务器补回来 |
| 是否需要 mixin | 不需要 | 需要（已有） | 版本差异，不是缺陷 |

无改动。
