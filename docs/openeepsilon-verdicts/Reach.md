状态：不适用

## 对照证据

参考侧有对应模块 `module/player/Reach.kt` —— 全文 12 行，只有一个 `ReachAdd`（0..3）滑条。

| 关注点 | 参考（`Reach.kt:行`） | 本工程（`hacks/ReachHack.java`） | 判定 |
| --- | --- | --- | --- |
| 设置粒度 | `:11` 单一 `ReachAdd`（在基础距离上加） | `:22-33` 实体距离/方块距离分开、可只对疾跑生效、可在水中禁用 | 本工程更细 |
| 判定逻辑 | 无（纯数值，由 mixin 消费） | `util/ReachPolicy.resolveEntityRange(...)`（纯函数，可单测） | 本工程已抽出策略类 |
| 落地方式 | 由参考自己的 mixin 消费 | `mixin/ClientPlayerInteractionManagerMixin.onGetReachDistance()/hasExtendedReach()` | 等价 |

## 结论

本工程已在参考之上（`util/ReachPolicy` 是纯函数 + 两个 mixin 挂点），参考没有可搬的判断，无改动。
覆盖表里的这一行是重复列出：常用 hack 表里 `Reach` 早有同样结论（"参考整个文件只有 12 行、一个
`ReachAdd` 滑条……没有可搬的判断"，状态不适用），本次与之一致。
`git log -1 -- hacks/ReachHack.java` 显示最后改动是 `512f070`（1.21.11/26.2 多版本移植），
也就是说这个实现不是本轮剩下的旧代码。

## 建议（未做）

- `:45-48` `getReachDistance()` 取实体/方块距离的较大者，作为 `hasExtendedReach()` 的口径；
  如果以后要区分"只看实体"或"只看方块"，需要拆成两个 mixin 挂点，未做。
