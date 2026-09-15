状态：不适用

## 对照证据

参考侧有 `movement/NoSlowDown.kt`（1.12.2 版本，只处理"用物品时的减速"）。
本工程 `hacks/NoSlowdownHack.java`（143 行）覆盖了九类减速，并把判定暴露成
`shouldBypassXxx()` 给 mixin 调用。

| 关注点 | 位置 | 结论 |
| --- | --- | --- |
| 覆盖范围 | `:32-60` 用物品 / 举盾 / 灵魂沙 / 蜂蜜块 / 黏液块 / 蛛网 / 细雪 / 甜浆果丛 / 手持物减速 | 比参考多八类 |
| 判定入口 | `:77-123` 每个 `shouldBypassXxx()` 一个开关 | 清晰 |
| 手持物减速 | `:125-139` 用 `AttributeValuePlanner.calculateExcluding(instance, 负值修饰符ID)` 只排除手持物带来的**负**速度修饰符 | 正确（不会顺手把药水/信标的加速也排除掉） |
| 启用状态 | 被 mixin 调用的判定点逐个核对：`BlockMixin:54`、`ClientPlayerEntityMixin:107`、`EntityMixin:45` 都先判 `isEnabled()`；`LivingEntityMixin:28` 走 `shouldBypassItemSlowness()`，该方法自身也判 `isEnabled()`（`:122`） | **没有**"关掉模块后仍然生效"的问题 |

无改动（本行此前在覆盖表里是"待办"，代码已经是新写法，属重复列出）。

## 建议（未做）

- 除 `shouldBypassItemSlowness()` 外的 `shouldBypassXxx()` 都没有自带 `isEnabled()` 判断，依赖调用方
  （mixin）自己判。本次核对当前四个调用点都判了，但这种约定容易在新增调用点处漏掉；要更稳可以让这些
  方法统一以 `isEnabled() &&` 开头。属于防错改造，未做。
