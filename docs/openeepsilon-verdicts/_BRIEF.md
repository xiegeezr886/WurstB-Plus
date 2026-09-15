# 审计子任务通用说明（父 agent 写给子 agent）

本文件是审计任务的**强制规范**，任何被指派审计 hack 的子 agent 都必须逐条遵守。

## 环境

- 工程根目录（本工程，要改的就是它）：`D:\WurstB\WurstB-Plus-main`
  - Forge 1.20.1 / Java 17，hack 源码在 `src\main\java\net\wurstclient\hacks\`，纯逻辑与工具在 `src\main\java\net\wurstclient\util\`，单测在 `src\test\java\net\wurstclient\util\`。
- 参考客户端（只读）：`D:\WurstB\_oe_ref` = CakeSlayers/OpenEpsilon。
  - 关键事实：**Kotlin / Minecraft 1.12.2 / Java 8 / MCP 39-1.12**。模块在 `src\main\kotlin\studio\coni\epsilon\module\`，工具在 `...\util\`。
  - 它的强项集中在**包级 exploit**（NCP / AAC / Hypixel 绕过）与 **GL11 立即模式渲染**，这两类在 1.20.1 上基本不成立（1.20.1 没有立即模式渲染管线，反作弊也换了）。
  - 因此「**不适用**」是高频且完全合格的结论。
- 已有结论请先读 `D:\WurstB\WurstB-Plus-main\docs\openeepsilon-refactor.md` 的 §0.3，避免重复已经做过的分析，也保证口径一致。

## 两个可以引用的「原版真源」（很有用，优先用它们代替猜测）

1. **仓库内自带一份 1.20.2 的反编译原版源码**（1.20.1 与 1.20.2 在这些方法上基本一致，引用时要写清是 1.20.2）：
   `D:\WurstB\WurstB-Plus-main\neoforge\versions\1.20.2\build\neoForm\neoFormJoined1.20.2-20231019.002635\steps\unzipSources\unpacked\net\minecraft\...`
   例：`...\world\entity\player\Player.java`（`attack` 的暴击判据）、`...\world\entity\Entity.java`（`checkFallDamage`）、`...\server\network\ServerGamePacketListenerImpl.java`（`handleMovePlayer`）、`...\world\item\BlockItem.java`（`canPlace`）。
2. **1.20.1 的官方映射**（用来证明某个方法/字段在 1.20.1 上确实存在，从而保证代码可编译）：
   `%USERPROFILE%\.gradle\caches\forge_gradle\minecraft_repo\versions\1.20.1\client_mappings.txt` 与 `server_mappings.txt`。
   用 grep 查方法名即可（例如 `isUnobstructed`、`getAttackStrengthScale`）。

引用这两处时请写清「文件:行」，这是本项目里最硬的证据形式。

## 硬性要求

1. **不要运行 gradle，不要运行 git，不要提交。** 父 agent 会统一编译、跑单测、提交。
2. 每一个工程侧 API（类名、方法名、字段名、构造器签名、枚举常量）都必须**真的打开文件读过**才能写进代码或结论。禁止凭记忆猜 API。参考侧也一样。
   - 结论必须能指到 `文件:行`，行号要真实（用 read / grep 得到，不要编）。
3. 允许的改动范围，仅限：
   - 修改**分配给你的** hack 文件：`src\main\java\net\wurstclient\hacks\<Hack>Hack.java`；
   - 新建**纯逻辑类**（文件名必须带你的 hack 名前缀以免和并行 agent 撞名，例如 `KillauraTargetPlanner.java`）：`src\main\java\net\wurstclient\util\<前缀><名字>.java`，**不得 import 任何 `net.minecraft.` 下的东西**，只承载可单测的算法 / 决策；
   - 为它新建 JUnit 5 测试：`src\test\java\net\wurstclient\util\<同名>Test.java`。
4. **不要修改任何已存在的共享文件**（例如 `util\RotationQueue.java`、`util\RotationSmoothing.java`、`util\inventory\*`、`WurstClient.java`、`docs\openeepsilon-refactor.md`、`hud\IngameHUD.java`……）。其他 agent 正在并行改这些包，会产生冲突。如果你认为必须改共享文件才有意义，**写进 verdict 文档的「建议（未做）」**，不要动手。
5. **不要改任何 setting 的名字、默认值、取值范围**（会破坏已有用户配置），除非有充分理由，并在文档里写明理由。
6. 风格必须与本工程一致：GPL 头注释（照抄邻居文件的写法）、**TAB 缩进**、`{` 独占一行、`if(x)` 括号前无空格、注释用简体中文。
   - 先读这三个范例再动手：
     - `src\main\java\net\wurstclient\hacks\WTapHack.java`
     - `src\main\java\net\wurstclient\util\HoleFillPolicy.java`
     - `src\test\java\net\wurstclient\util\HoleFillPolicyTest.java`
7. 「**没有值得搬的改动**」是完全合格、甚至常见的结论。**绝对不要为了交差编造改动。**
   - 不要做纯装饰性重排、不要为了「看起来重构过」而抽没有消费方的抽象、不要把 `if` 换个写法就叫优化。
   - 判据：改动必须是**可被单测或严密推理验证的正确性 / 行为改进**（修真实 bug、修边界条件、把错误的启发式换成正确的量化规则、修一个会在实机上导致错误动作的疏忽），而不是风格偏好。
   - 如果只发现「可以更快」「更优雅」这类主观优化，写进文档的「建议（未做）」并判 `不适用`。
8. 你改完的代码父 agent 会直接编译。所以：写完代码后**自己再读一遍**，确认每个用的方法/字段确实存在于你读过的类里，确认没有用到不存在的 `import`。

9. **每一条「这是 bug」的结论都必须给出具体反例**：写出具体的输入值，以及「旧行为 vs 新行为」在这组输入下的差别。
   - 如果给不出这样一组输入（也就是说新旧行为在**任何**输入下都相同），那它就不是 bug，**不许**写成 bug，更不许把等价的表达式换个写法就当成修复。
   - 真实前例：曾有 agent 声称 `hurtTime > limit` 在与上限同域时「永不成立」，从而要求改成「上限语义」。实际上 `hurtTime=10, limit=5` 时 `10 > 5` 为真，原式本来就生效；它提交的「修复」与原式逐字节等价，只是多了个纯类。这类改动会被父 agent 全部回退，并浪费一整轮。
   - 你在文档「实际改动」里写「改了什么」时，必须能写出「旧：在这组输入下是 X；新：同样输入下是 Y」。写不出来就不要改。

## 产出

每个被指派的 hack 一份：`D:\WurstB\WurstB-Plus-main\docs\openeepsilon-verdicts\<Hack>.md`（`<Hack>` 不带 `Hack` 后缀，例如 `Killaura.md`）。

格式（严格）：

```
状态：已优化 | 已重构 | 不适用 | 待办

## 对照证据
| 关注点 | 参考（_oe_ref 相对路径:行） | 本工程（相对路径:行） | 判定 |

（逐条列出你真正核对过的关注点：目标选择、旋转、时序、伤害/减伤计算、边界条件、设置项覆盖、失效场景……）

## 实际改动
（逐条：改了什么、为什么、能验证什么。没改就写「无」）

## 故意不搬
（逐条：参考有、这里不搬的东西 + 一句理由）

## 建议（未做）
（需要改共享文件 / 需要实机验证 / 超出本次范围的事项）
```

状态含义：
- `已重构`：换了算法或架构（含抽出可单测的纯逻辑类）。
- `已优化`：保留原结构，修了实现细节里的真实缺陷。
- `不适用`：参考无对应模块，或差异无意义（1.12.2 手法 / 渲染管线差异 / 本工程已经更好）。
- `待办`：你没做完（**如果出现这个状态，必须在文档里写清卡在哪、还剩什么**，不要假装完成）。

## 回复格式

最后**只回复 ≤5 行**：

1. 每个 hack 一行：`<Hack>: <状态> — <一句话理由>`
2. 一行文件清单：新建了哪些、改了哪些（用相对路径）。

不要复述文档内容，不要贴代码，不要贴大段行号。
