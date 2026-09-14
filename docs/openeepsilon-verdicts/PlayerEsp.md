状态：已优化

## 对照证据
| 关注点 | 参考（_oe_ref 相对路径:行） | 本工程（相对路径:行） | 判定 |
| --- | --- | --- | --- |
| 2D 投影 | `_oe_ref/src/main/kotlin/studio/coni/epsilon/module/render/ESP2D.kt:310-321` `GLU.gluProject` + `glGetFloat` 读模型/投影矩阵；`:82` `setupCameraTransform` | `src/main/java/net/wurstclient/hacks/PlayerEspHack.java:204-205` 用 RenderListener 的 `poseStack.last().pose()` 与 `RenderSystem.getProjectionMatrix()`；`src/main/java/net/wurstclient/util/WorldToScreen.java:26-45` 先把角点减去相机位置再投影 | 等价：RenderEvent 在 `src/main/java/net/wurstclient/mixin/GameRendererMixin.java:99-111`（`renderLevel` 末尾）触发，此处 poseStack 正是世界渲染用的相机旋转矩阵 |
| 近平面剔除 | `ESP2D.kt:92-101` 只收 z∈[0,1) 的角点，其余角点仍参与包围盒 | `WorldToScreen.java:32-33` 只要有一个角点 `w<=0.05` 就整箱返回 null | 有差异，见"建议（未做）"第 2 条（需实机验证） |
| 距离剔除 | `ESP2D.kt:61-65`、`EntityESP.kt:100-104` 均无距离上限 | `PlayerEspHack.java:62-64,164,170` maxDistance（默认 256，16..512，平方比较） | 本工程更可控 |
| 穿墙 | `ESP2D.kt` 无此概念（永远穿墙）；`EntityESP.kt:42-62` 双次渲染 + `Chams.kt:35-42` `glDepthRange` | `PlayerEspHack.java:196-199` 3D 用 depthFunc（`src/main/java/net/wurstclient/util/RenderUtils.java:342,467`：GL_LEQUAL / GL_ALWAYS）；`:210-217` 2D 用 `BlockUtils.hasLineOfSight`（**本轮已从原版 `MC.player.hasLineOfSight` 改过来**，原因见「实际改动」） | 3D 侧与参考同类；2D 侧原有真实缺陷，本轮已修 |
| 自己 | `ESP2D.kt:43,306-308` `self` 开关，默认不画自己 | `PlayerEspHack.java:169` 恒排除 `MC.player` | 不适用：自身渲染由 `src/main/java/net/wurstclient/hacks/PlayerHaloHack.java:41-45` 负责（且仅第三人称） |
| 隐身 | `ESP2D.kt:307` 恒剔除 `isInvisible`；`EntityESP.kt:35,103` `invisible` 开关默认 true | `PlayerEspHack.java:109-111` `FilterInvisibleSetting` 默认 false（`src/main/java/net/wurstclient/settings/filters/FilterInvisibleSetting.java:20-23`） | 与 EntityESP 的默认一致（都显示隐身玩家） |
| 睡觉 | 参考无 | `PlayerEspHack.java:110` `FilterSleepingSetting` 默认 false（`FilterSleepingSetting.java:22-27`：`isSleeping()` 或 `Pose.SLEEPING`） | 本工程多一个开关，默认不改变行为 |
| 排序 | `Nametags.kt:98` 按距离排序只服务于 Top-N；`ESP2D`/`EntityESP` 本身不排序 | `PlayerEspHack.java:160-176` 不排序 | 不适用（没有 Top-N/遮挡优先级等消费方，排序是空转） |
| 队伍/宠物 | `ESP2D`/`EntityESP` 无队伍、宠物判定 | 无队伍判定；`PlayerEspHack.java:169` 只额外排除 `FakePlayerEntity` | 一致 |
| 好友色 | 参考无好友色（`ESP2D.kt:44` 单一可调色；`EntityESP.kt:58` 取 GUI 主题色） | `PlayerEspHack.java:79-80,283-290` 好友色优先；名字取 `getName().getString()`，与 `FriendsList.isFriend` 的 `getScoreboardName()` 同源（1.20.2 `world/entity/player/Player.java:1812-1814,1944-1946`） | 本工程更丰富且判定同源，无缺陷 |
| 血量/护甲数据 | `ESP2D.kt:238-279` `health/maxHealth` + 10 段分隔 | `PlayerEspHack.java:220-222` `(health+absorption)/(maxHealth+absorption)`；`:229-240` 平均护甲耐久 | 不适用（口径不同但各自自洽，参考的 `split` 是绘制细节） |
| 颜色模式 | `ESP2D.kt:36-44` fill/alpha/color；`EntityEspRenderer` 无 | `PlayerEspHack.java:66-80,288-289` + `EntityEspRenderer.java:82-97` DISTANCE/HEALTH/CUSTOM | 本工程更丰富 |
| 整模型描边(chams) | `EntityESP.kt:51-61,79-91` `renderEntityStatic`/`modelBase.render` 各画 4~5 遍 + `RenderUtils3D` 立即模式 | 无（用 depthFunc 穿透即可） | 不适用：1.12.2 的 GL11 立即模式管线在 1.20.1 不存在；隐形实体另有 `TrueSightHack.java:50-53` |
| Skeleton / WallHack | `Skeleton.kt:118-137` `GlStateManager.glBegin` 画骨骼线；`WallHack.kt:24-56` 改方块渲染 + `loadRenderers()` | 无对应物 | 不适用：前者是 1.12.2 立即模式骨架线，后者是旧版 X-ray，均与 PlayerEsp 的目标选择/可见性判定无关 |

## 实际改动
`PlayerEspHack.java:210-217`（`updateScreenBoxes` 里的穿墙判定）：

```java
// 旧
if(!throughWalls.isChecked() && !MC.player.hasLineOfSight(player))
// 新
if(!throughWalls.isChecked() && !BlockUtils.hasLineOfSight(
	MC.player.getEyePosition(), player.getEyePosition()))
```

证据：原版 `hasLineOfSight(Entity)` 有 **128 格硬上限**——1.20.2 真源
`.../unpacked/net/minecraft/world/entity/LivingEntity.java:147`
`MAX_LINE_OF_SIGHT_TEST_RANGE = 128.0`，判定在 `:2872-2878`
（`if (vec31.distanceTo(vec3) > 128.0) return false;`）；1.20.1 字节码
`bfz.B(bfj)` 同义。

新旧行为差异（具体输入）：`Max distance = 256`（默认值）、`Through walls = 关`、
开阔平地、另一名玩家相距 **150 格**且中间无任何方块。
- 旧：2D 模式 `150 > 128` → `hasLineOfSight` 恒返回 false → `continue`，**不画**该玩家的框；
  而同一设置下 3D 模式照画（3D 走 depthFunc，无距离上限）→ 同一个开关两种模式行为不一致。
- 新：`BlockUtils.hasLineOfSight`（`util/BlockUtils.java:156-159`）用的是同一条
  `ClipContext.Block.COLLIDER` 射线、无距离上限 → 150 格无遮挡时照画，与 3D 一致。

顺带修正了一处近似：原版按 `new Vec3(getX(), getEyeY(), getZ())` 取点，`getEyePosition()`
的定义完全相同，所以射线本身与旧路径等价；区别只有那条 128 格短路。「墙挡住身体但没挡住眼睛
仍算可见」这一点两种写法一致，没有被本次改动改掉。

**未实机验证**：本次只做了 `compileJava`/`test`（全绿），没有开游戏核对 150 格外 2D/3D 的
实际表现，也没有测远距离射线的开销。

## 故意不搬
- `FillMode.Gradient` / `Corners` 模式 / `SplitHealth` 分隔线（`ESP2D.kt:36,124-225,261-272`）：纯 2D 绘制风格，属装饰性改动（简报第 7 条）。
- `displayName` / `potion`(力量/虚弱) / `Health` 数字（`ESP2D.kt:40-42,226-237,274-292`）：新功能，不是对现有缺陷的修正。
- 自建 `Frustum` 剔除（`Nametags.kt:87-96` 的手法）：本工程直接用原版实体渲染管线的剔除/遮挡，比自建视锥更准。
- EntityESP 的整模型描边：见上表最后一行。

## 建议（未做）
1. ~~`PlayerEspHack.java:210` 128 格上限~~ **本轮已修**，见「实际改动」。
2. **需实机验证（改动落在共享文件，本轮未动）** `WorldToScreen.java:32-33`：只要有一个角点 `w<=0.05` 就整箱丢弃。2D 模式下与相机重叠的玩家（框跨过相机平面，例如贴身同格）会整箱消失，而 3D 仍会画。正确修法是在相机空间做近平面裁剪（Sutherland–Hodgman）再投影；只把 `w` 钳到一个小值会让 `x/w` 爆掉、方框铺满屏幕，比不画更糟，所以不确定"新行为更好"之前不动。
