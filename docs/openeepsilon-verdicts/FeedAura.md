状态：不适用

## 对照证据

参考侧没有对应模块（参考的 `combat/AutoMend.kt` 是自动修装备，不是喂动物）。本项为自审，
全文读过 `hacks/FeedAuraHack.java`（210 行）。

## 自审记录

| 关注点 | 位置 | 结论 |
| --- | --- | --- |
| 目标筛选 | `:113-125` 距离 + `isFood(heldStack)` + `canFallInLove` + 幼年/未驯服/马类过滤 | 正常 |
| 马类默认过滤 | `:58-62` 默认勾选并注明是绕开原版 MC-233276（马类会无限消耗物品） | 合理 |
| 交互 | `:144-173` `interactAt` 未消耗再 `interact`，`consumesAction() && shouldSwing()` 才挥手 | 与原版 `Minecraft.startUseItem()` 的 ENTITY 分支一致 |
| 每 tick 只喂一次 | `:172` 处理完 `target = null` | 正常 |
| 发包朝向 | `:139-140` `faceVectorPacket`（同时改客户端朝向，避免"隔空喂食"） | 正常 |
| 与其它 aura 互斥 | `:82-88` 开启时关闭 ClickAura/FightBot/KillauraLegit/MultiAura/Protect/TriggerBot/TpAura | 一致 |

无改动。

## 建议（未做）

- `:163-171` 没有 `return`，靠方法末尾结束；逻辑上与原版一致，只是可读性差一点，未改。
- `onRender` 里的血条颜色（`:181-188`）对 `getMaxHealth() <= 1e-5` 做了保护，但没有对
  `getHealth() > getMaxHealth()` 的情况做夹取；属显示细节，未改。
