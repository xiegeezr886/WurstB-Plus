状态：不适用

## 对照证据

参考侧有 `render/FullBright.kt` / `render/AntiBlind.kt`（1.12.2 改的是"失明/黑暗"效果层）。
本工程 `hacks/AntiBlindHack.java`（28 行）是标记模块，逻辑分在四个 mixin 里，已逐个核对都判了
`isEnabled()`：

- `BackgroundRendererMixin.java:57`（失明/黑暗的雾）
- `LightmapTextureManagerMixin.java:29`（光照贴图）
- `WorldRendererMixin.java:44`（区块可见性）
- `ClientPlayerEntityMixin.java:337`（`MobEffects.DARKNESS` 的 `hasStatusEffect`）

| 关注点 | 结论 |
| --- | --- |
| 四个调用点都受开关控制 | 是（无"关掉仍生效"问题） |
| 覆盖 1.19+ 的黑暗效果（监守者/幽匿） | 是（`ClientPlayerEntityMixin:337`） |

无改动。
