状态：不适用

## 对照证据
| 关注点 | 参考（_oe_ref 相对路径:行） | 本工程（相对路径:行） | 判定 |
| --- | --- | --- | --- |
| 数据来源 | `_oe_ref/src/main/kotlin/studio/coni/epsilon/module/render/Nametags.kt:89-96` 每 tick 遍历 `mc.world.playerEntities` | `src/main/java/net/wurstclient/hacks/NameTagsHack.java:122-127` 每 tick 从 EntitySnapshotManager 取 players | 等价（快照来源见下一条） |
| 快照内容 | 同上，直接读世界实体表 | `src/main/java/net/wurstclient/util/EntitySnapshotManager.java:50-59` 用 `level.entitiesForRendering()`（1.20.2 `client/multiplayer/ClientLevel.java:254-255` = `getEntities().getAll()`），**含本地玩家**（1.20.2 `client/multiplayer/ClientPacketListener.java:408` `this.level.addEntity(this.minecraft.player)`） | 本工程不自己剔除，靠原版实体渲染管线（视锥/遮挡已由原版处理） |
| 距离上限 | `Nametags.kt:90` 自建 `range`（默认 200） | `src/main/java/net/wurstclient/mixin/EntityRendererMixin.java:73-75` 沿用原版 64 格；`src/main/java/net/wurstclient/mixin/LivingEntityRendererMixin.java:85-96` 在 unlimitedRange（默认开）时把距离伪造成 1。被绕过的正是 1.20.2 `client/renderer/entity/LivingEntityRenderer.java:235-239` | 本工程是原版开关，语义与设置文案一致 |
| 是否含自己 | `Nametags.kt:92` 排除 `mc.renderViewEntity`，永远不画自己 | 快照含自己；`LivingEntityRendererMixin.java:110-113` 只在 forcePlayerNametags 时放行自己的名牌；`src/main/java/net/wurstclient/hacks/NameTagsHack.java:55-59` 文案明说会显示自己的名牌 | 本工程更正确（设置文案与实现一致） |
| 排序 | `Nametags.kt:98` 按距离升序，**只为** `:57,:104` 的 playerCount Top-N 服务 | `NameTagsHack.java:122-127` 不排序，也无 Top-N | 不适用（本工程没有"最多 N 个"语义，排序无消费方） |
| 随机剔除 | `Nametags.kt:87,93` 自建 Frustum 剔除 | 无自建剔除（沿用原版） | 不适用（参考的 Frustum 只作用于它自己画的标签；本工程走原版管线，剔除更准） |
| 隐身/潜行 | `Nametags.kt:297-308` 隐身/潜行只改文字颜色，不剔除 | `src/main/java/net/wurstclient/mixin/EntityRendererMixin.java:77-78,115-117`（`isDiscrete()`/`notSneaky`）同样只影响绘制层级 | 一致，不判为缺陷 |
| 强制显示玩家名的注入点 | 参考无对应物（它自己就是渲染方） | `LivingEntityRendererMixin.java:101-114` 注入在 `Minecraft.getInstance()`（`LivingEntityRenderer.java:240`）之前，因此越过 `:242` 隐身、`:243-260` 队伍、`:263` `renderNames()`/cameraEntity/载具检查 | 越过的这两项参考侧同样不做（`Nametags.kt:72-80` 不看 `hideGui`，`:302-303` 只给隐身玩家染色不剔除），故不判为缺陷 |
| 强制显示命名怪的注入点 | 参考无对应物 | `src/main/java/net/wurstclient/mixin/MobEntityRendererMixin.java:25-36` 注入在 GETFIELD `crosshairPickEntity` 之前 | 正确：该 GETFIELD 只有 `hasCustomName()` 为真时才可达（1.20.2 `client/renderer/entity/MobRenderer.java:30` 的 `&&` 短路），且 `super.shouldShowName()` 已通过 → 距离/隐身/F1 仍生效，"命名怪"语义准确 |
| ping 缺失 | `Nametags.kt:266-268` info 为空时拼出 `nullms` | `NameTagsHack.java:146-148` 无数据时 `-1ms` 并染灰 | 本工程更好 |
| 血量 | `Nametags.kt:271-273` `relativeHealth` + `ceil`，6 档阈值 | `NameTagsHack.java:132-141` 生命+吸收、`Math.round`、4 档阈值 | 不适用（阈值粗细不同、各自自洽；玩家 maxHealth=20 时两者数值一致） |
| 装备 | `Nametags.kt:402-429` 主手→护甲→副手 | `NameTagsHack.java:157-164` 主手→`getArmorSlots()`→副手（`getArmorSlots()` 在 1.20.1 官方映射中存在） | 不适用（同一集合，横向排列顺序不同） |
| 附魔/耐久文本 | `Nametags.kt:135-160` 附魔缩写、Van/Bind、God | `NameTagsHack.java:166-171` 只给耐久百分比，绘制在 `EntityRendererMixin.java:144-155` | 1.20.1 附魔数据驱动，缩写表不可搬；其余属渲染细节 |
| 队伍可见性设置 | 参考不处理队伍（自己直接画） | 本工程靠原版 `LivingEntityRenderer.java:243-260` 的队伍分支，forcePlayerNametags 才越过 | 不适用（本工程默认行为=原版，参考无队伍概念） |

## 实际改动
无。逐条核对后参考侧没有任何"正确性/行为改进"可搬：本工程 NameTagsHack 的数据来源、血量取整、ping 缺失值、自身/队伍/隐身语义都已自洽，`MobEntityRendererMixin` 的注入点经 1.20.2 短路顺序验证也是正确的。按简报第 7 条不做装饰性重排。

## 故意不搬
- `count`/`playerCount` Top-N（`Nametags.kt:57,98-104`）：本工程没有"最多显示 N 个"的语义，配套的排序没有消费方。
- gamemode 标签 `[C]/[I]/[A]/[S]`（`Nametags.kt:282-295`）：依赖 1.12.2 的 `isAllowEdit`，1.20.1 官方映射中不存在（`client_mappings.txt` 检索 0 命中），要做只能改走 `Abilities.mayBuild`，属新功能。
- Bot 后缀（`Nametags.kt:44,275-277`）：本工程 AntiBot 是独立 hack（从 ESP/瞄准里剔除），加后缀是新功能。
- 自定义字体 / shadow / DotGod / healthBar / alpha / xAdd / yAdd（`Nametags.kt:39-57,310-318,375-386`）：1.12.2 的 GL11 立即模式 + `FontRenderer`/`MainFontRenderer` 在 1.20.1 不存在，本工程走 `Font.drawInBatch` + `DisplayMode`（`EntityRendererMixin.java:102-117`）。
- `heldStackName` / `Info(Durability|Name)`（`Nametags.kt:45,51,148-159`）：本工程已有 showEquipment + showDurability 百分比。

## 建议（未做）
- 无必须改动项。可选项（新功能，非缺陷修复，需你决定）：参考按隐身/潜行改名牌文字色，本工程无此区分；实现要动 `src/main/java/net/wurstclient/util/NameTagRenderState.java` 与 mixin（共享文件，本次禁止）。
- 若日后想让 F1 也隐藏名牌：`LivingEntityRendererMixin.java:106-114` 需补 `Minecraft.renderNames()` 判断；但参考侧同样不看 `hideGui`，本次不判为缺陷、不动。
