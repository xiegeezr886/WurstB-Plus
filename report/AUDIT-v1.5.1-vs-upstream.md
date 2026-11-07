# v1.5.1 潜藏 bug 审计（对照 Wurst 上游）

> 审计对象：WurstB+ Plus **v1.5.1**（Fabric 1.20.1 发布树 `fabric/src`）
> 对照基准：**Wurst7 v7.50.3-MC1.20.1** 源码（`reference/upstream-sources/Wurst7_1_20_1_src/Wurst7-1.20.1`）
> 方式：静态源码审计（映射归一化 diff + 语义审读 + 跨工程一致性核对）。**未运行客户端、未编译、未做运行时复现。**
> 结论是"代码层面可证"的缺陷清单，每条都给了可核对的文件与行号；建议修复前按行号复核。

---

## 一、审计基线

| 项 | 值 |
| --- | --- |
| 被审代码 | `fabric/src/main/java`，1080 个 `.java` |
| 与 tag `v1.5.1` 的差异 | 仅 6 个文件（`InvWalkHack`、`ScaffoldWalkHack`、`NanoVGContext`、`NanoVGNativeLoader`、`CreativeModeInventoryScreenAccessor`、`TestControlServer`）。发布资产是 tag 之后的构建，所以按工作区树审计 |
| 上游 | Wurst7 `v7.50.3`，602 个 `.java` |
| 两树共有路径 | 548 |
| 其中映射归一化后**语义相同** | 168（30.7%） |
| 语义有差异 | 380 |
| 仅 fork 有 | 532（新模式 / 新 HUD / 新子系统） |
| 仅上游有 | 54（旧 ClickGUI、Navigator、analytics、**全套测试**） |

**结构一致性核对（避免把"漏同步"误报成 bug）**

- Hack / Command / OtherFeature 注册：424 / 54 / 17 个类，**全部已注册**，无遗漏、无重名。
- Mixin：磁盘 88 个 `@Mixin` 类，配置登记 88 个，**零缺口**；`defaultRequire: 1`；`@Overwrite` 0 个；唯一一处 `require = 0` 与上游同源（Sinytra 兼容，见 §四）。
- 跨工程：抽样核对 `EventManager`、`Encryption`、`RotationQueue` 在 Fabric 1.20.1 / Fabric 1.20.2 / Forge 1.20.2 / Forge 1.21.11 / NeoForge 1.20.1 五棵树中**逐字节相同**。
  → 这些缺陷绝大多数位于共享逻辑层，因此修复已同步到全部工程；但**并非每个工程都包含对应代码**：55 棵版本树的 `HudItemIcons` 是 13 行 stub（无缓存，无此缺陷），根工程 v1.6.0 没有 `hacks/aura`、`hacks/scaffold` 子系统（其 Killaura/Scaffold 是另一套实现，不适用 B/C/G/A/E/M）。精确覆盖见 §八。

---

## 二、结论摘要

| # | 级别 | 位置 | 问题 | 状态 |
| --- | --- | --- | --- | --- |
| A | **中高** | `hacks/scaffold/SilenceTellyScaffoldMode` | "ST Block Fly" 只交换一次手、重置时也不换回 → 主/副手服务端错位，直到重登（opt-in 开关） | 已修复 |
| B | 中高 | `hacks/aura/OpenAura` | Killaura / MultiAura 绕过 AntiBot 与 FakePlayer 过滤 → 攻击"不存在"的客户端实体 | 已修复 |
| C | 中 | `hacks/aura/OpenAura` | 目标对象每 tick 重建 → `isAttackAvailable()` 永远为真；SWITCH 模式退化为"最高血量优先" | 已修复 |
| D | 中 | `hacks/ScaffoldWalkHack` | 开着 Scaffold 时**所有右键被吞** → 不能进食/喝药/用弓（上游无此行为） | 已修复 |
| E | 中 | `hacks/scaffold/HeypixelScaffoldMode` | 旋转未吸附鼠标灵敏度栅格；`ScaffoldUtils.getVanillaRotation()` 定义了但**零调用** | 已修复 |
| F | 中低 | `hud2/compat/HudItemIcons` | 图标缓存无上限、无淘汰、`clear()` 零调用、不删 NanoVG 图像 → 整局长跑泄漏 | 已修复 |
| G | 中低 | `hacks/aura/OpenAura` + `KillauraHack` | FOV 只比偏航、且不折半：滑条 90 实际是 ±90°（上游 ±45°），且忽略俯仰 | 已修复 |
| H | 低 | `hacks/KillauraLegitHack` | `switchAdvantage` 用 `PERCENTAGE` 显示 0–100 → GUI 显示 "1000%" | 已修复 |
| I | 低 | `hacks/KillauraHack` | `hideFakeSwings` 默认 true → "Swing range"/"Swing CPS" 默认完全无效 | 已修复 |
| J | 低 | `hacks/BlinkHack` | `player/level == null` 时先清包再禁用 → 挂起的位置包被丢弃（丢 Blink 效果） | 已修复 |
| K | 低 | `hacks/FlightHack` | 进入旁观者时丢失 `previousFlying`，禁用后不恢复 | 已修复 |
| L | 低 | `altmanager/Encryption` | 旧格式兜底用新主密钥解密（CFB8 无认证）→ 乱码被当损坏，写回空列表 | 已修复 |
| M | 低 | `hacks/scaffold/PlayerSimulation` | 克隆玩家时未复制 WASD 输入 → 落点预测忽略玩家输入 | 已修复 |

---

## 三、逐条证据

### A. [中高] ST Block Fly：手部交换只发一次且不换回

**位置**：`fabric/src/main/java/net/wurstclient/hacks/scaffold/SilenceTellyScaffoldMode.java:78-79, 1075-1091, 132`；
开关：`hacks/ScaffoldWalkHack.java:259-260`（默认 `false`，仅 Silence Telly 模式可见）。

```java
// :1075
private void updateBlockFlyBeforePlace()
{
    if(blockFlyDesyncing || WurstClient.MC.player == null)
        return;
    WurstClient.MC.player.connection.send(new ServerboundPlayerActionPacket(
        ServerboundPlayerActionPacket.Action.SWAP_ITEM_WITH_OFFHAND,
        BlockPos.ZERO, Direction.DOWN));
    blockFlyDesyncing = true;
    blockFlyTicks = 0;          // 只赋值，此后从未自增/读取
}

// :1087 —— 只改标志位，不发换回包
private void resetBlockFly()
{
    blockFlyDesyncing = false;
    blockFlyTicks = 0;
}
```

**为什么是 bug**

1. `blockFlyDesyncing` 一旦置真，只由 `resetBlockFly()` 复位，而 `resetBlockFly()` **唯一调用点是 `onDisable()`（:132）**，且它**不发 `SWAP_ITEM_WITH_OFFHAND` 回包**。
2. 于是整个会话只发生一次"客户端视角不变、服务端主副手互换"的错位：客户端认为主手是镐/剑，服务端认为主手是副手物品。后续的攻击、放块在服务端都用错物品。
3. 移植来源 OpenOpal 的对应实现是**每 16 tick 重新武装**：`if (this.blockFlyDesyncing && ++this.blockFlyTicks > 16) this.resetBlockFly();`（`reference/clients/OpenOpal/.../scaffold/mode/silencetelly/SilenceTellyScaffold.java:484-485`）——重复交换成对出现，奇偶性自然抵消。`blockFlyTicks` 在 fork 里成了只写不读的死字段，正是这段逻辑被漏掉的痕迹。

**影响**：开启 "ST Block Fly" 的用户主副手全程错位（换手/重登才恢复），属于状态性缺陷；默认关闭，故不波及所有人。

**修复建议**：在 `onTick()` 中 `if(blockFlyDesyncing && ++blockFlyTicks > 16) resetBlockFly();`（恢复与 OpenOpal 一致的奇偶抵消）；另外在 `resetBlockFly()` 里若当前处于"错位中"（奇数次交换未配对）则补发一次换回包，避免禁用后残留在错位状态。

---

### B. [中高] Killaura / MultiAura 绕过 AntiBot 与 FakePlayer 过滤

**位置**：`hacks/aura/OpenAura.java:270-288`；对照 `util/EntityUtils.java:38-45`。

```java
// OpenAura.isValidTarget —— 只有 4 个类型标志 + 好友
private static boolean isValidTarget(LivingEntity entity, int flags)
{
    Player player = WurstClient.MC.player;
    if(entity == player || !entity.isAlive() || entity.isSpectator()
        || !entity.isPickable())
        return false;
    if(WurstClient.INSTANCE.getFriends() != null
        && WurstClient.INSTANCE.getFriends().isFriend(entity))
        return false;
    ...
}

// EntityUtils.IS_ATTACKABLE —— 同一工程里其它战斗功能用的过滤器
&& e != MC.player && !(e instanceof FakePlayerEntity)
&& !WURST.getFriends().isFriend(e)
&& (!(e instanceof Player) || !WURST.getHax().antiBotHack.isBot((Player)e));
```

**为什么是 bug**：fork 自己在 `IS_ATTACKABLE` 里新增了 `FakePlayerEntity` 与 `antiBotHack.isBot()` 两条过滤，但重写后的 Killaura/MultiAura 走的是 OpenAura 的私有过滤器，两条都丢了。

- **FakePlayerEntity**：是 Wurst 的**纯客户端**实体（Blink / Freecam 会生成），服务端根本不知道这个 entity id。Killaura 锁到它是必然的（`isPickable()` 为真、血量为真），`gameMode.attack()` 会把指向未知实体的攻击包发给服务端 → 严格反作弊下就是 invalid attack / kick 级别的证据。
- **AntiBot**：`antiBotHack` 判定为 bot 的玩家仍被攻击，等于 AntiBot 白开。

**旁证**：同版本其它战斗功能（ClickAura、KillauraLegit、TriggerBot）仍走 `IS_ATTACKABLE`，所以这是重写路径独有的缺口，不是全局设计。

**修复建议**：`isValidTarget` 增加 `e != null && !(e instanceof FakePlayerEntity) && (!(e instanceof Player p) || !antiBotHack.isBot(p))`，或直接把 `OpenAura.collect` 的实体来源换成 `EntityUtils.getAttackableEntities()`。

---

### C. [中] Killaura 目标状态每 tick 重建 → 攻击窗口与 SWITCH 排序失效

**位置**：`hacks/aura/OpenAura.java:63-93, 101-132, 138-154`；消费点 `hacks/KillauraHack.java:218, 238-243, 293`。

```java
// :63  Target 持有跨 tick 才有效的状态
private long lastAttackTime;
private double lastAttackDamage;

public boolean isAttackAvailable() {
    if(lastAttackTime == 0) return true;          // 永远走这一行
    ...
}
// :126 collect() 每一轮都 new Target(...)
Target target = raycastTarget(entity, eyes, closest, ...);
targets.add(target);
```

**为什么是 bug**：`KillauraHack.onUpdate()` 每 tick 调 `OpenAura.collect()`，返回的是全新 `Target` 实例，`lastAttackTime` 恒为 0。于是：

1. `isAttackAvailable()` 恒真 —— 470 ms 攻击窗口（`ATTACK_WINDOW_MS`）从未生效。`hitSelect` 默认 `false`（`KillauraHack.java:80-81`），CPS 模式设为 Manual 时可以在目标 10 tick 受击免疫期内连续发攻击包：伤害被服务端丢弃，但攻击包全部到达，属于典型反作弊噪声。
2. `select()` 的 SWITCH 分支 `-((health * 25) + lastAttackTime)` 中 `lastAttackTime` 恒 0，排序退化为按当前血量降序 —— 与紧随其前"优先低血量"的排序意图相反，SWITCH 模式会优先打**血量最高**的目标。
3. OpenOpal 原实现把 `lastAttackData` 存在按实体 id 缓存的目标表里（`KillAuraTargeting.java:136`），跨 tick 生效；fork 移植时把状态放进了每 tick 重建的一次性对象。

**修复建议**：在 `KillauraHack` 里用 `Map<Integer, AttackState>`（entity id → lastAttackTime/damage）跨 tick 保存，或在 `OpenAura` 内按 entity 复用 `Target`。

---

### D. [中] 开着 Scaffold 时所有右键被吞（不能进食/喝药/用弓）

**位置**：`hacks/ScaffoldWalkHack.java:572-577`。

```java
@Override
public void onRightClick(RightClickEvent event)
{
    if(isEnabled() && MC.player != null && MC.screen == null)
        event.cancel();
}
```

**为什么是 bug**：`RightClickEvent` 在 `Minecraft.startUseItem()` 的 HEAD 触发且可取消（`mixin/MinecraftClientMixin.java:106-119`），无条件取消 = 整个物品使用入口被关掉：食物、药水、末影珍珠、弓、桶全部失效。上游 Scaffold（162 行，只实现 `UpdateListener`，无任何右键拦截）没有这个行为，所以这是相对上游的**功能回退**。

放块本身由 `placeBlock()` 直接调 `gameMode.useItemOn()`（:1086），不依赖原版右键；因此只需在"手持 BlockItem"时拦截即可避免双放，不必牺牲全部右键。

**修复建议**：`if(MC.player.getMainHandItem().getItem() instanceof BlockItem) event.cancel();`，或新增"仅拦截放块"开关。

---

### E. [中] Heypixel 脚手架旋转绕过鼠标灵敏度栅格

**位置**：`hacks/scaffold/HeypixelScaffoldMode.java:583-610`；`hacks/scaffold/ScaffoldUtils.java:187-192`。

```java
// HeypixelScaffoldMode.getRotation
Rotation direct = ScaffoldUtils.rotationFromBlock(pos, face);
Rotation reverseYaw = new Rotation(
    Mth.wrapDegrees(WurstClient.MC.player.getYRot() - 180.0F), direct.pitch());
...
return withDuplicateRotJitter(direct, pos, face);   // 直接返回，未过灵敏度栅格
```

```java
// ScaffoldUtils.java:187 —— 定义后全工程零调用
public static Rotation getVanillaRotation(Rotation original) { ... }
```

**为什么是 bug**：Grim/NCP 一类服务端会把玩家旋转吸附到"鼠标灵敏度允许的角度栅格"上做一致性检查。`getVanillaRotation()` 就是为此写的（内部 `patchConstantRotation` 按 `sensitivity^3 * 8 * 0.15` 取整）。`ScaffoldWalkHack.getFastRotation()` 调用了它（:1536、:1600），但 **Hypixel/Heypixel 模式自己的 `getRotation()` 没有调用**，发出去的偏航/俯仰是任意小数。`withDuplicateRotJitter` 还会再叠加抖动，进一步偏离栅格。

**旁证**：`getVanillaRotation` 在整棵 1.5.1 树中只有"定义 + 注释"两处命中，是被移植时漏接的公共方法。

**修复建议**：`direct`/`reverseYaw` 在返回前统一过 `ScaffoldUtils.getVanillaRotation(...)`，与 `getFastRotation()` 路径保持一致。

---

### F. [中低] HUD 物品图标缓存无上限、无淘汰、`clear()` 无人调用

**位置**：`hud2/compat/HudItemIcons.java:45-46, 62-64, 150-152, 168-178`。

```java
private static final Map<String, Integer> CACHE = new LinkedHashMap<>();  // 无 eviction
...
int image = NanoVG.nvgCreateImageRGBA(nvg.getHandle(), SIZE, SIZE,
    NanoVG.NVG_IMAGE_NEAREST, flipped);
CACHE.put(key(stack), image);

public static void clear() { ... CACHE.clear(); ... }   // 全工程无调用点
```

- 缓存键 = `item|damage|count|完整 NBT 字符串`（:160-166），潜影盒/成书/改名物品的 NBT 内容各不相同 → 键空间几乎无上限；每个值是一张 32×32 RGBA NanoVG 图像（约 4 KB 显存 + 句柄）。
- `clear()` 一个调用点都没有（已 grep 全树），`CACHE.clear()` 也没有 `nvgDeleteImage` → 整局游戏只增不减。
- 每次 `capture()` 里 `target.bindWrite(true)`（:121）之后的异常路径没有 `unbindWrite()`，`graphics.renderItem` 抛错会把 32×32 的 FBO 留在绑定状态。

**影响**：长时间 + 大量不同 NBT 物品时显存/内存缓慢增长；这不是崩溃级问题，但属于"越玩越卡"的典型来源。

**修复建议**：`LinkedHashMap` 加 access-order LRU（上限几百条），淘汰时 `nvgDeleteImage`；`capture()` 用 try/finally 保证 `unbindWrite()`；`clear()` 接进 NanoVG 上下文重建流程。

---

### G. [中低] Killaura 的 FOV 只比偏航、且不折半

**位置**：`hacks/aura/OpenAura.java:290-301`；`hacks/KillauraHack.java:93-95`；对照 `hacks/MultiAuraHack.java:114-117`。

```java
// OpenAura.isInFov
Rotation needed = rotationTo(eyes, entity.getBoundingBox().getCenter());
double difference = Math.abs(Mth.wrapDegrees(player.getYRot() - needed.yaw()));
return difference < fov;         // 只比 yaw；且用整值而不是半角
```

```java
// KillauraHack：滑条 1..180、默认 180，直接把整值传进去
return new OpenAura.Config(getRange(), fov.getValue(), ...);
// MultiAuraHack：先除 2，并留了注释"Wurst 用的是总角度"
fov.getValue() / 2.0D
```

**为什么是 bug**：上游 Killaura 用 `getAngleToLookVec(...) <= fov / 2.0`（完整 3D 夹角、半角）。fork 的滑条语义相同（"Only targets inside this field of view"）、数值范围却改成 1–180，且比较时既不折半也不看俯仰：

- 设 FOV = 90 → 实际是 ±90°（180° 锥），比上游宽一倍；
- 正上方/正下方的实体（俯仰差 90°）照样能锁。

**注意**：默认 180 时 `isInFov` 直接 `return true`，所以默认配置下无差别，只有用户调小 FOV 才踩到。MultiAura 的写法是对的，反证了 Killaura 这处是遗漏。

---

### H. [低] `switchAdvantage` 用 PERCENTAGE 显示 0–100

**位置**：`hacks/KillauraLegitHack.java:84-87`；`settings/SliderSetting.java:323-324`；`util/TargetTracker.java:44`。

```java
new SliderSetting("Switch advantage", "...", 10, 0, 100, 1,
    ValueDisplay.PERCENTAGE);                    // PERCENTAGE = v*100 + "%"
...
double requiredAdvantage = Math.max(0.05,
    Math.abs(currentScore) * Math.max(0, switchAdvantagePercent) / 100);
```

**为什么是 bug**：`PERCENTAGE` 的格式化是 `(int)(round(v*1e8)/1e6) + "%"`，按 **0–1** 输入设计。这里滑条是 0–100、默认 10，于是 GUI 显示 **"1000%"**（最大 "10000%"），而 `TargetTracker` 又按 `/100` 解释成 10%。显示与行为差 100 倍，用户无法据此调参。

**修复建议**：滑条改 0–1、步进 0.01，或改用 `ValueDisplay.INTEGER.withSuffix("%")`。

---

### I. [低] `hideFakeSwings` 默认 true → 两个 Swing 设置默认无效

**位置**：`hacks/KillauraHack.java:71-72, 402-416`（唯一调用点 `:231`）。

```java
private final CheckboxSetting hideFakeSwings =
    new CheckboxSetting("Hide fake swings", true);
...
private void tryFakeSwing() {
    if(hideFakeSwings.isChecked() || System.currentTimeMillis() < nextSwingTime)
        return;
```

**为什么是 bug**：默认勾选 → `tryFakeSwing()` 第一行就返回，"Swing range"/"Swing CPS" 两个滑条开箱即无效。另外这个选项名为"隐藏假挥手"，实际效果是**根本不发挥手**（不是隐藏动画），与标签不符。

---

### J. [低] Blink 在空引用时清空再禁用，导致挂起包丢失

**位置**：`hacks/BlinkHack.java:101-108`；`onDisable()` → `flushQueuedPackets()`。

```java
public void onUpdate()
{
    if(MC.player == null || MC.level == null || MC.getConnection() == null)
    {
        clearQueuedPackets();     // 先清空
        setEnabled(false);        // 再触发 onDisable → flush（已空）
        return;
    }
```

**为什么是 bug**：`flushQueuedPackets()` 本来负责把挂起的位置包发出去，但这条"空引用"分支先把队列清了，`onDisable` 里的 flush 无事可做 —— 服务端停在旧位置，玩家被拉回。触发场景：重生、维度切换、代理转服等瞬时 `player == null`。上游在同样情形会 NPE，所以这是 fork 自己加的保护引入的"新的丢包路径"。

**修复建议**：该分支只 `setEnabled(false)`，让 `onDisable()` 决定是否 flush；或在 flush 失败时才清空。

---

### K. [低] Flight 进入旁观者时丢弃待恢复状态

**位置**：`hacks/FlightHack.java:105-112, 163-168`。

```java
if(player.isSpectator())
{
    if(trackedPlayer == player)
        trackedPlayer = null;          // 没有 restore，previousFlying 被丢弃
    else
        restoreTrackedPlayer();
    return;
}
```

**为什么是 bug**：`onDisable()` 只通过 `restoreTrackedPlayer()` 恢复 `abilities.flying`。若 Flight 在创造飞行状态下开启、随后切旁观者，`trackedPlayer` 被置 null，禁用后 `previousFlying` 永远丢失——玩家得重新双击空格才能飞。

---

### L. [低/继承上游] 旧格式兜底解密用错密钥，静默产出乱码

**位置**：`altmanager/Encryption.java:251-261`；`altmanager/AltsFile.java:53-70`。

```java
private byte[] decryptLegacy(byte[] payload) throws GeneralSecurityException
{
    SecretKey key = legacyKey != null ? legacyKey : masterKey;   // 没有旧密钥就用新主密钥
    ...
    Cipher cipher = Cipher.getInstance("AES/CFB8/NoPadding");    // 无认证流密码
    return cipher.doFinal(payload);
}
```

**为什么是 bug**：CFB8 没有认证标签，错误密钥不会抛异常、只会产出乱码。`AltsFile.load()` 把 JSON 解析失败当作"文件损坏"：`renameCorrupted()`（改名 `!CORRUPTED_*`）后**立刻 `save()` 写回空列表**。触发条件是"文件是旧格式、但旧密钥文件不在了"（拷到另一个 Windows 用户 / 换机器 / 从备份只恢复了 alts 文件）。

**定性**：上游同样有"密钥丢了就当损坏、写空列表"的行为（README 里有明确警告），fork 并没有使其更糟，但新增的 `masterKey` 兜底让失败形态从"报错"变成"静默乱码"，可恢复性更差。

**修复建议**：`legacyKey == null` 时直接抛 `IOException("legacy key missing")`，让上层走"不要覆盖原文件"的分支。

---

### M. [低] 落点预测没有复制玩家输入

**位置**：`hacks/scaffold/PlayerSimulation.java:44-66`；使用点 `hacks/ScaffoldWalkHack.java:1184-1204`。

```java
private void cloneStates(LocalPlayer player)
{
    simulated.setPos(player.position());
    ... // 位置/速度/朝向/冲刺/效果都复制了
    // xxa / yya / zza 没有复制
}
public void simulateTick()
{
    simulated.travel(new Vec3(simulated.xxa, simulated.yya, simulated.zza));  // 恒为 0
}
```

**对照**：OpenOpal 原实现 `simulateTicks(tickCount, player.forwardSpeed, player.sidewaysSpeed, player.upwardSpeed)` → `callTravelMidAir(new Vec3d(sideways, upward, forward))`（`reference/clients/OpenOpal/.../simulation/PlayerSimulation.java`）。

**影响**：`updateData()` 的"模拟落点"兜底只在 `!onGround && dy <= 0` 且常规搜索失败时触发（从边缘走下桥的瞬间）。克隆体只保留惯性、不再叠加 WASD 加速度，预测落点会**偏短/偏后**。属于"偶尔放晚一格"，不会崩，但正是该兜底要解决的场景。

---

## 四、复核后判定为"非缺陷"的项（避免误报）

| 项 | 结论 |
| --- | --- |
| `EventManager` 快照列表 | 改进：`add/remove` 同步更新不可变快照，`fire` 不再每次复制；并发语义正确 |
| `Fullbright`、`NewChunks`、`PathFinder`、`AutoSword`、`Criticals`、`TriggerBot`、`ClickAura`、`CrystalAura`、`AnchorAura` | 归一化 diff 后与上游等价或更严格（逐行核对） |
| `NoFallHack` 重写 | 冷却/阈值/反作弊分支齐全，`runAfterSend` 有 `event.getPacket() != packet` 保护 |
| Blink 的 flush 语义（除 J 外） | 禁用即 flush、无重复/乱序；`allPackets` 为显式 opt-in |
| `EntityMixin` 的 `require = 0` | 与上游同源、注释明确（Sinytra Connector）；非 fork 引入 |
| `TestControlServer` | 仅 loopback + token + 显式系统属性开启 |
| 功能/混入注册 | 424/54/17 + 88/88 全量登记 |
| `OptionsSubScreenMixin.setRenderBackground(false)` | 注释即意图（隐藏列表自绘深色底），非状态泄漏 |

---

## 五、结构性风险（不单列为 bug，但会持续制造 bug）

1. **上游测试全被丢弃**：上游 `net/wurstclient/test/` 下 11 个测试类（含 E2E 客户端、NoFall/Freecam/XRay/AutoMine/Give/Modify 回归）在 1.5.1 树中不存在，`fabric/src/test` 目录**根本不存在**。本报告里 C/G/D 这类"语义回归"正是 E2E 能直接抓到的类型。
2. **死代码是移植漏接的信号**：`blockFlyTicks`（只写不读）、`getVanillaRotation()`（定义零调用）都是"移植时少接一根线"的物证。建议加一条静态检查（只写字段 / 零引用 public 方法），成本极低。
3. **66 棵源码树靠人工同步**：本次抽样显示共享逻辑目前仍一致（逐字节），但这依赖纪律；任何单点修复都必须同步 66 次。

---

## 六、修复实施方案

全部 13 项已修复，以 `fabric/src`（v1.5.1 发布树）为基准，随后同步到所有包含相同代码的工程树。

| # | 修复做法 |
| --- | --- |
| A | `updateBlockFlyBeforePlace()` 恢复 OpenOpal 的 16 tick 重武装（`++blockFlyTicks > 16` 后允许下一次交换）；新增 `blockFlySwapped` 记录奇偶，`resetBlockFly()` 在禁用时补发一次换回包，保证服务端手部布局与客户端一致。 |
| B | `OpenAura.isValidTarget` 增加 `FakePlayerEntity` 与 `antiBotHack.isBot(player)` 两条过滤，与 `EntityUtils.IS_ATTACKABLE` 对齐。 |
| C | `OpenAura` 新增 `AttackRecord(time, damage)` 与 `Target.restoreAttackState/attackRecord()`；`select(...)` 新增带 `IntFunction<AttackRecord>` 的重载，在排序前恢复历史；`KillauraHack` 用 `Map<Integer, AttackRecord>` 跨 tick 保存，攻击后写回，每 20 tick 清理离场实体，启用/禁用时清空。 |
| D | `ScaffoldWalkHack.onRightClick` 只在**主手持可放置方块**时取消右键，食物/药水/弓/珍珠/桶恢复正常。 |
| E | `HeypixelScaffoldMode.getRotation` 的 `direct` 先过 `ScaffoldUtils.getVanillaRotation(...)`，与 `getFastRotation()` 路径一致。 |
| F | `HudItemIcons` 改为 access-order `LinkedHashMap` + `MAX_CACHE = 512` LRU，淘汰与 `clear()` 都调用 `NanoVG.nvgDeleteImage`；`capture()` 用 `try/finally` 保证 `target.unbindWrite()`。 |
| G | `isInFov` 改为 3D 夹角 + 半角语义（`getAngleToLookVec <= fov/2`，`fov >= 360` 不过滤）；Killaura 的 FOV 滑条改为上游一致的 `30–360 / 默认 360 / DEGREES`；MultiAura 不再自行 `/2`。 |
| H | `KillauraLegitHack` / `AimAssistHack` 的 `switchAdvantage` 改为 `0–1 / 0.01 / 默认 0.1`；`TargetTracker` 参数改为 fraction，去掉 `/100`（根工程 v1.6.0 的增强版 tracker 同步改为 fraction）。 |
| I | `hideFakeSwings` 默认改为 `false`，"Swing range"/"Swing CPS" 开箱生效。 |
| J | Blink 的空引用分支不再 `clearQueuedPackets()`，交给 `onDisable()` 的 `flushQueuedPackets()` 发送。 |
| K | Flight 进入旁观者时统一 `restoreTrackedPlayer()`，保留并恢复 `previousFlying`。 |
| L | `decryptLegacy` 依次尝试 `legacyKey`、`masterKey`，只有明文像 JSON 才接受，否则抛 `GeneralSecurityException`；`AltsFile.load` 在读取失败时不再立刻 `save()` 覆盖，避免把"密钥不对"写成空白账号表。 |
| M | `PlayerSimulation` 保存原 `LocalPlayer`，每次 `simulateTick()` 传入玩家实时 `xxa/yya/zza`。 |

## 七、验证

| 检查 | 结果 |
| --- | --- |
| `fabric`（Fabric 1.20.1，发布树）`compileJava --offline` | **BUILD SUCCESSFUL** |
| `fabric/versions/1.20.2`（Fabric 1.20.2，hunk 级适配） | **BUILD SUCCESSFUL** |
| `neoforge`（NeoForge 1.20.1） | **BUILD SUCCESSFUL** |
| `versions/1.20.2`（Forge 1.20.2，hunk 级适配） | **BUILD SUCCESSFUL** |
| 语法解析检查（`ParseCheck`，仅 parse） | **997 个改动文件，0 语法错误** |
| 修复标记逐树核对 | 见 §八 |

## 八、同步覆盖

| 文件 | 已修复 | N/A | 缺文件 |
| --- | ---: | ---: | ---: |
| `altmanager/AltsFile.java` | 66 | 0 | 1（根工程） |
| `altmanager/Encryption.java` | 66 | 0 | 1（根工程） |
| `hacks/AimAssistHack.java` | 67 | 0 | 0 |
| `hacks/BlinkHack.java` | 66 | 0 | 1 |
| `hacks/FlightHack.java` | 67 | 0 | 0 |
| `hacks/KillauraHack.java` | 66 | 1（根工程为另一套实现） | 0 |
| `hacks/KillauraLegitHack.java` | 67 | 0 | 0 |
| `hacks/MultiAuraHack.java` | 66 | 1（根工程为另一套实现） | 0 |
| `hacks/ScaffoldWalkHack.java` | 66 | 1（根工程为另一套实现） | 0 |
| `hacks/aura/OpenAura.java` | 66 | 0 | 1（根工程无此包） |
| `hacks/scaffold/HeypixelScaffoldMode.java` | 66 | 0 | 1 |
| `hacks/scaffold/PlayerSimulation.java` | 66 | 0 | 1 |
| `hacks/scaffold/SilenceTellyScaffoldMode.java` | 66 | 0 | 1 |
| `hud2/compat/HudItemIcons.java` | 11（真实实现） | 55（13 行 stub，无缓存缺陷） | 1 |
| `util/TargetTracker.java` | 67 | 0 | 0 |

合计 **939 个文件实例**获得修复。补充说明：

- **26.2 / 26.3 的 6 棵树**：`ScaffoldWalkHack` 的右键判断用的是 26.x 的 `MC.gui.screen()`，无法套用 1.20.x 的文本锚点，已单独改写这 6 个文件（同样的语义）。
- **根工程 v1.6.0**：没有 `hacks/aura` 与 `hacks/scaffold`，其 Killaura/MultiAura 走 `CombatTargetUtils`（已含 `IS_ATTACKABLE` 与 3D 半角 FOV），Scaffold 没有右键拦截，因此 B/C/G/A/E/M 不适用；`TargetTracker` 与 `switchAdvantage` 的一致性已同步修复。
- 版本树里 `hacks/aura`、`hacks/scaffold` 等整包文件在 git 中是 **untracked**，`git status` 默认不显示这些改动；本次核对全部基于磁盘内容。
- 26.x 工程需要 JDK 25，本机只有 JDK 17，因此只做了语法解析与人工复核，未编译。

## 九、局限

- 修复按"最小行为改动"原则实施，但仍属于**未经运行时复现**的静态修复；建议在真实客户端按报告 §三 的场景逐项回归（尤其 A 的手部奇偶、C 的攻击窗口、E 的 Hypixel 旋转）。
- 未覆盖：`music/`、`background/`、`render/skia/`、`perimeter/`、`seed/` 等 532 个 fork 独有文件中的大部分。
- 上游测试套件缺失的问题仍在（见 §五），本次没有补测试；如果要防止回归，优先给 C（攻击窗口）、H（百分比）、M（预测输入）补最小单测。
