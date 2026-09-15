状态：已优化

## 对照证据

参考侧：`_oe_ref/src/main/kotlin/studio/coni/epsilon/module/render/HoleESP.kt`（洞口判定实际在共享的
`management/HoleManager` 里，本工程没有对应物）。

| 关注点 | 参考（HoleESP.kt:行） | 本工程（hacks/HoleEspHack.java:行） | 判定 |
| --- | --- | --- | --- |
| 洞口数据来源 | `:4,63-73` 遍历共享的 `HoleManager.holeInfos`（`holeInfo.origin/center/boundingBox/type/isTwo/isFour`） | `:98-135` 自己扫 `(2r+1)² × 9`（y 从 -4 到 4）个位置 | 参考把删洞/标洞集中在共享服务里，多个模块可复用；本工程是单点实现，见「建议」1 |
| 洞形 | `:20-21,75-80` 支持 1x1、**2x1**（TWO）、**2x2**（FOUR），各有一色 | `:104-107,115-133` 只认 1x1：三格可替换 + 下方实心 + 一圈围墙 | 参考多两种形态，属新增功能（要新增设置），不搬 |
| 1x1 判据 | `:68-69` 额外要求 `isAirBlock(center + (0,2,0))` | `:104-110` 要求 `pos`、`pos.above()`、`pos.above(2)` 都可替换，且 `pos.below()` 是实心 | 等价，且本工程多一条"脚下必须实心"（更严格，不会把空中平台边缘当成洞） |
| 分类 | `:75-80` `HoleType.BEDROCK / OBBY / TWO / FOUR` | 旧 `:112-134` 两个布尔 → **有真实缺陷**，见「实际改动」 | **已修** |
| 颜色 | `:28-31` 四色（按 `Page` 显示对应页） | `:45-53` 三色（基岩/黑曜石/混合） | 保留（本工程少了 TWO/FOUR 两色，因为没有那两种洞形） |
| 数量上限 | `:24,74` `Hole Count`（默认 10，只保留最近若干） | 无上限，靠 `Range`（1..16，默认 8）限制 | 新增设置，不搬 |
| 距离口径 | `:73` `eyesPos.squareDistanceTo(holeInfo.center)` | `:96` `BlockPos.containing(MC.player.position())`（脚底）为中心扫描 | 差一两个方块量级，不构成缺陷 |
| 排除自己所在的洞 | `:33` `HideOwn` | 无 | 新增设置，不搬 |
| 渲染模式 | `:25,34-40` `RenderMode.Box/Glow`、`LowHole`、`Outline`、`Width`、`Y`、`GlowHeight`、`OutlineHeight` | `:156-163` 固定"实心盒 + 描边" | 新增设置，不搬 |
| 更新频率 | 无节流（由 HoleManager 调度） | `:89-92` 每 20 tick 重扫一次，其余 tick 用缓存 | 合理（扫一次约 2.6 万个位置，见「建议」3） |

## 实际改动

`hacks/HoleEspHack.java`：**修掉"Bedrock color 永远用不到"的分类缺陷**。

旧代码 `:122-125`：

```java
boolean obCheck = isObsidianLike(check) || isReplaceable(check);
boolean bedCheck = isBedrock(check) || isReplaceable(check);
```

而旧的 `isObsidianLike()` 把 **BEDROCK 也算作黑曜石类**（`:182-187`）。
于是 `bedrockHole == true`（四周全是基岩或空气）**必然推出** `obsidianHole == true`（基岩满足
`isObsidianLike`），渲染时 `:149` 的 `hole.bedrockHole && hole.obsidianHole` 恒为真：

- 纯基岩洞 → 涂成 `mixedColor`（混合色）
- `:151-152` 的 `else if(hole.bedrockHole) color = bedrockColor` **永远不可达**，"Bedrock color"
  这条设置是死的。

新旧行为差异（具体输入）：在 y = -59 的黑曜石平台旁用基岩搭一个 1x1 竖井（脚下基岩、四面基岩、
上方三格空气），`Bedrock color = 绿`、`Mixed color = 黄`。
- 旧：这个洞被判定为"基岩+黑曜石混合"，画**黄色**；`Bedrock color` 改成任何颜色都没有任何洞受影响。
- 新：判定为纯基岩，画**绿色**；只有四周既有基岩又有黑曜石的洞才用混合色。

新代码把三种判定拆开（`:112-134`）：

```java
bedrockHole  &= replaceable || isBedrock(check);        // 全基岩
obsidianHole &= replaceable || isObsidian(check);       // 全黑曜石（含哭泣黑曜石）
wallHole     &= replaceable || isObsidianLike(check);   // 基岩或黑曜石都算围墙
```

只有 `wallHole` 为真才记入 `holes`，纯基岩/纯黑曜石由前两个标记决定，都不是就是混合洞。
`isObsidianLike()` 现在由新的 `isObsidian()`（`OBSIDIAN`/`CRYING_OBSIDIAN`，不含基岩）与
`isBedrock()` 组合而成，语义与命名一致。

顺带一处等价化简：旧的 `:145` `if(!obsidian.isChecked() && !hole.bedrockHole && hole.obsidianHole)`
在旧标记下等价于 `!hole.bedrockHole`（每个洞至少满足一个标记），现在直接写成
`if(!obsidian.isChecked() && !hole.bedrockHole) continue;`，语义更明确：关掉 "Obsidian" 就只显示
纯基岩洞。**副作用**：混合洞含黑曜石，因此关掉 "Obsidian" 时混合洞也会被隐藏（旧代码里"混合洞"
不存在真正的实例，所以这条副作用只在修好分类之后才显现）。这一点是刻意的，写在注释里。

## 故意不搬

1. `HoleESP.kt:4,63-85` + `HoleManager` 共享洞口服务：见「建议」1，本轮先把单点实现修对。
2. `:20-21,75-80` 2x1/2x2 洞形与对应颜色：要新增设置项（简报第 5 条），不搬。
3. `:24,74` `Hole Count` 上限、`:33` `HideOwn`：同上，不搬。
4. `:25,34-40` `RenderMode.Glow` 等一整套渲染参数：新增设置，不搬。
5. `:99-101` `RenderUtils3D.drawFullBox/drawBoundingFilledBox`（立即模式）：1.20.1 不适用，不搬。

## 建议（未做）

1. **共享"洞口/安全点"判定（值得单独一轮）**：本工程至少有四处各自手写同一类判据——
   `HoleEspHack.java:104-133`（八面围墙）、`AnchorHack.java:49-52`（四面）、
   `AutoCityHack.java:152`、`HoleFillerHack.java:85,157-163`（三态判定）。
   参考把这些集中在 `HoleManager` 里，改一次全局受益。要做需要先确定各消费方对"洞"的定义
   （四面还是八面、是否要求脚下实心、是否含 2x1/2x2），本轮未做。
2. `:100` 的 y 扫描窗口是写死的 `-4..4`：如果洞底与玩家脚底相差超过 4 格（例如站在悬崖上往下看）
   就找不到。参考靠共享服务的判定范围。加设置属新增设置，未做。
3. 扫描成本：`Range = 16` 时 `(2*16+1)² × 9 ≈ 1.6 万`个位置、每个位置最多约 20 次方块查询，
   每 20 tick 一次 ≈ 每秒 1.6 万次 `getBlockState`。可接受但不便宜；加"只扫玩家所在层附近"
   之类的启发式属策略变更，未做。
4. 验证边界：本轮只做了 `compileJava`/`compileTestJava`/`test`（全绿）；洞口分类的实际画面
   （颜色是否如预期）没有在游戏里核对过。
