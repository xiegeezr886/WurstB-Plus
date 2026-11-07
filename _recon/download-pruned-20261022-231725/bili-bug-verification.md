# B站评论 bug 核查报告

> **2026-10-22 修复更新**：本报告中的“仍存在”项已全部处理，详见
> [CHANGELOG.md](../CHANGELOG.md) 的「B 站反馈修复（2026-10-22）」一节：
> KillAura 全矩阵、MultiAura 轮转、主界面开关、26.x 退出看门狗、Navigator
> 设置行叠字/残留窗口、Freecam 飞行状态恢复。下文保留核查时的原始结论作为基线。

- 视频：`BV1WLha63E8L`（WurstB v1.5-Navy 发布）
- 评论总数：226（82 主评论 + 144 楼中楼）
- bug 相关候选：44 条，去重后可核查 22 项
- 核查时间：2026-10-22
- 方法：评论文本 + 图片、仓库当前代码、git 历史、本地 `run/logs` 与 `run/crash-reports`、既有验证记录
- 边界：除本地日志已覆盖的启动/进世界外，未新开客户端逐项复现；像素级/伤害数值类结论已标注证据强度

---

## 一、仍存在 / 未修复

### 1. 1.20.1 Fabric KillAura 只有 1 点伤害 —— 仍存在（66/67 工程）

- 反馈：她说这是为了挣钱，2026-09-22，`rpid=315782137809` 所在楼中楼
- 根因已在 2026-09-23 定位（`9e3632bc`）：默认 `Priority=HEALTH` 会转头打射程内血量最低的怪，
  玩家瞄准的怪只吃到横扫的 1 点伤害。
- 修复只提交到根工程 `src/`：
  - `Priority.ANGLE`：1/67（仅根工程）
  - `Hit select`：1/67（仅根工程）
- `fabric/src/main/java/net/wurstclient/hacks/KillauraHack.java:122-123` 仍是
  `Priority.values(), Priority.HEALTH`，即反馈者使用的 **1.20.1 Fabric 仍未修复**。
- 临时规避：把 KillAura 的 `Priority` 手动改成 `Angle`。

### 2. 多重杀戮光环（MultiAura）打怪无伤害 / mod 冲突 —— 未改动

- 反馈：打小就超丶皮，2026-09-21
- 全部 67 个工程的 `MultiAuraHack` 仍默认 `TargetPriority.ARMOR`、`Raycast.ENEMY`。
- `9e3632bc` 明确写了「MultiAuraHack 的 TargetPriority 默认仍为 ARMOR……未改动」。
- 与其它 mod 的冲突没有对应修复，也没有可复现清单。

### 3. 自定义主界面替换原版控件，无法关闭 —— 仍存在（67/67）

- 反馈：可乐吴宸乐，2026-09-21，`rpid=314642410625`
- 所有 67 个工程的 `TitleScreenMixin` 都执行 `clearWidgets()`，只挂 Wurst 自己的
  4 个按钮 + 齿轮菜单；没有任何「使用原版主界面」的开关。
- 这同时解释了「整合包特殊按键被大 UI 遮挡/看不见」和「ModMenu 的模组按钮消失」：
  第三方 mod 加到标题界面的按钮被整体清掉了。
- 这是当前明确的可用性缺陷/功能缺口，不是配置问题。

### 4. 退出游戏触发崩溃报告（26.2 / 26.3 Fabric）—— 仍存在

- 反馈：可乐吴宸乐，2026-09-21（「细节退出会自动闪退」）
- 本地证据（4 份独立崩溃报告）：
  - `fabric/versions/26.2/run/crash-reports/crash-2026-10-19_20.56.37-client.txt`
  - `fabric/versions/26.2/run/crash-reports/crash-2026-10-19_21.06.41-client.txt`
  - `fabric/versions/26.2/run/crash-reports/crash-2026-10-22_01.12.30-client.txt`
  - `fabric/versions/26.3/run/crash-reports/crash-2026-10-22_15.59.16-client.txt`
- 报告标题：`Client shutdown from post-main`（Minecraft 客户端关闭看门狗）。
- 线程转储显示 `DestroyJavaVM` 在等 **非 daemon** 的 Baritone `CachedWorld` 线程池：
  `pool-4-thread-1/2/3`（`baritone.cache.CachedWorld`），主线程退出后 JVM 不结束，
  看门狗超时并生成崩溃报告。
- 影响：至少 Fabric 26.2/26.3；机制上所有加载了 Baritone 缓存的 26.x 都可能命中，
  其它版本未逐一验证。
- 尚未修复：没有 shutdown hook 去中断 Baritone 的执行器，也没有 `System.exit` 兜底。

### 5. 1.21.11 Fabric 设置窗口与面板分离 —— 未修复，需复现确认

- 反馈：Rosidis，2026-10-15，`rpid=317112497265`，附截图
- 截图显示「保护」设置窗口悬浮在分类面板右侧，和左侧面板明显分离；Monlt 回复「bug」。
- 当前 `SettingsWindow.setInitialPosition` 仍按 `parent.x + parent.width + 5` 放置，
  且 1.21.11 之后没有对应修复提交。
- 未能确定是布局问题还是拖拽后的预期位置；需要同一版本、同一 GUI 风格下复现。

---

## 二、已修复

### 6. 自动村民界面点「添加」崩溃 —— 已修复（13/13 相关工程）

- 反馈：zheng51121，2026-10-02，`rpid=315782137809`
- 修复：`bcd2c8f0`（2026-10-06）修掉 3 处崩溃点：
  非法/空附魔 id、`getEnchantment()` 为 null、附魔名解引用。
- 已传播到全部 13 个含 AutoLibrarian 的 1.20.x 工程：
  `AddBookOfferScreen` 13/13 含 `maxLevelOf`，其它 62 个工程本就没有该功能。

### 7. 26.2 Fabric ESP / 自动建筑线框随视角转动 —— 已修复（26.x 全矩阵）

- 反馈：氵番某同学，2026-09-22，`rpid=318248648544`
- 根因：26.x 走 `submitCustomGeometry` 延迟提交，feature pass 又应用一次相机旋转，
  旋转被应用两次，表现为「距离正常、方向随视角以两倍速率摆动」。
- 修复：
  - `ee399ab4`（2026-09-25）：26.2 × 三加载器改用 `WurstBufferSource` 立即绘制。
  - `f5195f4c`（2026-10-19）：26.1.x 同样改为立即绘制。
  - `08a356dc`（2026-10-20）：26.x 恢复相机朝向/位置的 tracer 原点与 GUI scale 补偿。
- 当前 15 个 26.x 工程的 `RenderUtils` 全部是立即绘制路径。
- 边界：编译与启动已验证；**AutoBuild 线框没有逐像素实机复测**；MobESP 红框有对齐记录。

### 8. 进服务器约 2 秒后崩溃（Baritone IPlayerContext NPE）—— 已修复

- 反馈：砂狼白子LunarVoid 2026-09-28、江城哦 2026-10-12（26.3）、TYPEARE、玖玖玖的玖
- 根因：Baritone `InputOverrideHandler.onTick` 在 `player().input == null` 时调
  `getClass()`，进服初期玩家/世界未就绪即 NPE。
- 修复：
  - `49e0d3b7`（2026-10-18）：修补 10 个 Forge 版 Baritone jar。
  - `4410dd79`（2026-10-19）：补上 9 个同内容 jar，共 19 个。
- 验证记录：1.21.11 用 `--quickPlayMultiplayer` 成功进服，不再出现该 NPE；
  本地 1.21.8 NeoForge / Forge 日志均已在世界中运行。
- 边界：补丁是第三方 jar 的字节码修改，不是源码构建；上游更新后应优先换上游。

### 9. Baritone Mod「注入失败」弹窗 —— 已修复

- 反馈：玖玖玖的玖，2026-10-14，附错误弹窗截图
- 对应修复：1.21.4–1.21.11 的 Baritone mixin 类版本 65→61、移除
  `MixinWorldRenderer`，避免 `RenderPipelines → IRenderer.<clinit>` 强转崩溃
  （2026-10-22 变更记录）；以及上面的 onTick 补丁。
- 本地 1.21.8 NeoForge 最新日志已进入世界，无 `ExceptionInInitializerError` / mixin 失败。

### 10. 1.21.4 Fabric「注册失败 / 进不去、会崩溃」—— 已修复（09-26 那次）

- 反馈：忧郁de白猫 2026-09-25（注册失败）
- 本地崩溃报告逐字吻合：
  `fabric/versions/1.21.4/run/crash-reports/crash-2026-09-26_03.49.43-client.txt`
  - `Mixin transformation of ItemInHandRenderer failed`
  - `HeldItemRendererMixin.onApplySwingOffsetNotBlocking ... (0/1) succeeded`
- 修复：`4c5597ae`（2026-09-26）逐版本修正失效注入点，D 类说明「67 个工程真启动全部通过」。
- 当前 `HeldItemRendererMixin` 已改为多个 `@ModifyExpressionValue` / 精确 `@Inject`；
  1.21.4 在 09-26 之后没有新的崩溃报告。
- 注意：LING4212 在 2026-10-12 另发过一次「1.21.4 Fabric 进不去」并附蓝奏云日志，
  该网盘接口在本机 TLS 握手失败，日志没取到，**无法确认是否同一问题**。

### 11. GUI 按钮文字空白（设置页灰块无字）—— 已修复（67/67）

- 反馈：Rosidis，2026-10-04，`rpid=315994816529`，附截图：
  「WurstB+ Plus 设置」页的 Settings / Managers / Links 按钮只有灰色底，没有文字。
- 对应修复：`e5faa44e`（2026-10-22）
  - 自定义字体包按 `version.json` 生成正确的 `pack_format` / `min_format` / `max_format`；
  - 字体路径修正；
  - 选中字体缺失时回退内置字体；
  - 字体包不存在或未启用时，GUI 文本不再套用自定义字体（避免缺字方块/空字）。
- 覆盖 67/67 个 `GuiPreferences.java`。
- 边界：截图与修复目标高度吻合，但没有做修复前后的同场景逐像素对比。

---

## 三、无法确认 / 非缺陷

| 反馈 | 结论 |
| --- | --- |
| 1.21.8「用不了」（2026-09-23） | 信息不足。本地日志：Forge/NeoForge 1.21.8 已进世界，Fabric 1.21.8 无致命行。需具体报错/日志。 |
| 26.3「很多功能用不了」（2026-10-01） | 信息不足。可能混淆了 v1.5 工程与仅根工程才有的 v1.6 子系统；需具体功能名。 |
| 26.2「部分功能点击就闪退」（2026-10-13） | 信息不足。未提供功能名/日志；本地 26.2 未找到对应点击崩溃报告。 |
| 26.2「ModMenu 模组页空白」 | 部分可由 §3 解释：标题界面的第三方按钮被 `clearWidgets()` 清掉；进入模组页后空白仍需日志。 |
| 26.1.2 Fabric「灵魂出窍 + 创造飞行出问题」（2026-10-04） | 部分修复。`08a356dc` 恢复了 26.1.2 的相机绑定，可解释 Freecam 漂移；没有专门的创造飞行修复，`FlightHack` 仍每 tick 把 `abilities.flying` 置 false，需实机复现。 |
| 26.2「电脑性能不好会卡」（2026-09-23） | 硬件/性能，非明确缺陷；无帧数/场景数据。 |
| 「加个本体显示不兼容模组」（2026-09-26） | 多半是 Forge/NeoForge 版本区间或重复安装 Baritone；检查 `mods.toml` 的 loader/MC 区间，勿再单独放 Baritone。 |
| 1.20 版本玩 1.6 报错（2026-09-21） | 使用问题。v1.6 只有根工程 Forge 1.20.1；其它加载器/版本应使用对应 v1.5 构建。 |
| 右 Shift 打不开（2026-09-21） | 绑定存在：`right.shift -> navigator`。但 `KeybindProcessor` 在任何其它界面（标题界面、聊天等）打开时会直接忽略按键；需在游戏中无 GUI 时按。 |
| 自动搭路速度不如原版（2026-09-21） | 非缺陷。ScaffoldWalk 有 `Place delay` 设置，默认按 tick 限速；调低可加快。 |
| 「Bug 超级多」（2026-10-19） | 无具体条目，无法核查。 |

---

## 四、建议的后续动作

1. **把 KillAura 的 `Priority=ANGLE` + `Hit Select` 同步到其余 66 个工程**（当前只有根工程）。
2. **给自定义标题界面加开关**（或保留原版/第三方控件），解决特殊按键与 ModMenu 按钮消失。
3. **处理 26.x 退出看门狗崩溃**：在关闭流程中断 Baritone `CachedWorld` 的执行器，或加受控的强制退出。
4. 复现并修复 1.21.11 设置窗口分离；需要同版本同风格截图/操作步骤。
5. MultiAura 需要一条可复现的「无伤害/冲突」用例后再改，避免再次误判为伤害计算问题。
