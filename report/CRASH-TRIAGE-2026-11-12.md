# 崩溃诊断报告（2026-11-12）

> 范围：本次提供的 6 份启动器/游戏崩溃报告、2 份 JVM `hs_err` 原生崩溃日志、4 份 `latest.log`、1 份 Android/FCL 游戏日志、1 个 Android JDK 包。
> 方法：逐份读取日志与崩溃栈 → 回到本仓库源码/构建脚本核对 → 用 grep/哈希确认影响面。
> **初始诊断阶段未修改任何源码**；此后已实施源码修复，最终状态与验证结果见 [CRASH-FIXES-2026-11-12.md](CRASH-FIXES-2026-11-12.md)。

> **后续修复记录**：两项 P0、Reach 冲突、翻译遗漏、OpenOpal 初始化、Baritone 配置遮蔽、Fabric MixinExtras、保存亮度，以及原生库与旧密钥安全风险均已处理。下文保留诊断时的源码和日志位置，不代表最终未修状态；详见修复记录。

---

## 0. 结论速览

| # | 附件 | 环境 | 崩溃点 | 根因 | 当前源码状态 | 责任方 |
|---|---|---|---|---|---|---|
| C1 | `hs_err_pid1292.log` | Windows / Fabric **26.3** / Java 25 | `NanoVGContext.getColor` → `Struct.free()` → `jemalloc.dll` | 对 GC 管理的直接缓冲调用 `free()`，把 JVM 指针交给 jemalloc | **仍存在**（15 个 26.x 工程） | 本仓库 |
| C2 | `错误报告-2026-11-10_19.19.23.zip`（含 `hs_err_pid30668.log`） | Windows / Fabric **26.2-2** / Java 26 | 同上（`drawRoundedRect` 路径） | 同上 | **仍存在** | 本仓库 |
| C3 | `错误报告-2026-11-10_23.56.54.zip` | NeoForge **1.21.1** | `Minecraft.tick` → `RotationHelper.preTick` → `MC` 为 null | 内嵌 Baritone 的 `@Mod("baritoe")` 存根与元数据不一致 → `dangling_entrypoint` → 整个 mod 容器初始化失败 | **仍存在**（`neoforge/versions/1.20.5`、`1.20.6`、`1.21`、`1.21.1`） | 本仓库打包 |
| C4 | `错误报告-2026-11-11_19.59.37.zip` | Forge **26.3** | Baritone `IRenderer.<clinit>` → `ClassCastException: RenderPipelines` | 工程根目录一份陈旧的 19 条 `mixins.baritone.json` 遮蔽了 Baritone jar 的 21 条配置，`MixinRenderPipelines` 从未应用 | **仍存在**（`versions/26.3`） | 本仓库移植 |
| C5 | `minecraft-exported-crash-info-2026-11-09T18-57-3.zip` | Fabric **1.20.1** 整合包（乌托邦 3.5.2） | `ReachLegacyRendererMixin` 注入 `(0/1)` | 与 `reach-entity-attributes` 的 `@ModifyConstant` 同优先级冲突，Mixin 跳过 → 因 `defaultRequire:1` 升级为致命 | **仍存在**（11 个 1.20.x 工程） | 本仓库 |
| C6 | `错误报告-2026-11-1_13.45.28.zip` | Fabric **26.1.2** | `WurstTranslator.translateEnglish` NPE | 启动期翻译 + `mcEnglish` 尚未赋值 | **已修**（26.1.2 已有守卫） | 已修 |
| C7 | `错误报告-2026-11-1_14.35.35.zip` | Fabric **1.21.11** | 同上（`class_1078`） | 同上 | **已修**；但**根工程 `src/`（v1.6.0）仍未修** | 部分 |
| C8 | `latest (2).log` | Fabric 26.3 / 1.5.0 | Freecam 开 → `Entity.getId()` | 26.2+ 对未分配 ID 的实体抛异常 | **已修**（26.2/26.3 改用负 ID） | 已修 |
| C9 | `latest (1).log` | Fabric 26.3 / 1.5.1 | `ClientPacketListenerAccessor` 未注册 | accessor 没写进 mixin 配置 | **已修**（26.2/26.3 已注册） | 已修 |
| C10 | `game-20261102-233743-13392.log` | Windows amd64 / Java 21 / Fabric **0.16.10** / MC 1.21 | `MixinExtras` 内部 CCE | MixinExtras 0.5.0-beta.2 对 `@Redirect` 的已知缺陷 | 第三方 | MixinExtras / 加载器 |
| C11 | `FCL-JDK8-android-aarch64-8u442-with-javac.tar(1).xz` | Android aarch64 | 无崩溃栈 | Java 8 无法运行 1.20.1+（更不可能运行 26.x） | 环境问题 | 运行时选择 |
| C12 | `latest (3).log` | Fabric 1.21.11 / 1.5.0 | `wurst:core/fogless_lines` 着色器无效 | 资源/管线加载失败；非致命 | 需复核（1.5.1 资源存在） | 待查 |

**一句话**：真正还在生产环境制造崩溃的是 **C1/C2（26.x 原生崩溃，15 个工程）**、**C3（NeoForge 内嵌 Baritone 把整个 mod 拖死，4 个工程）**、**C4（Forge 26.3 Baritone 渲染，1 个工程 + 若干同源副本）**、**C5（1.20.x 整合包冲突，11 个工程）**；C6–C9 是 11 月 1 日以来已经热修过的旧崩溃，其中 C7 的修复**漏了根工程**。

---

## 1. C1 / C2：26.x 的 jemalloc 原生崩溃（P0，最优先）

### 1.1 现象

两份 `hs_err` 的崩溃线程都是 `Render thread`，问题帧都是 `jemalloc.dll`，Java 栈都落在同一个方法：

```
C  [jemalloc.dll+0xc6b0]              (hs_err_pid1292)
C  [jemalloc.dll+0x323bb]
C  [jemalloc.dll+0x4faf]
J  org.lwjgl.system.MemoryUtil.nmemFree(J)V
j  org.lwjgl.system.Struct.free()V+4
j  net.wurstclient.hud2.nanovg.NanoVGContext.getColor(I)Lorg/lwjgl/nanovg/NVGColor;+75
j  net.wurstclient.hud2.nanovg.NanoVGContext.drawLine(FFFFFI)V+74
j  net.wurstclient.hud2.nanovg.NanoVGUI.border(FFFFI)V+17
j  net.wurstclient.clickgui2.ClickGui.p$12(...)
```

```
C  [jemalloc.dll+0x134b]              (hs_err_pid30668)
C  [jemalloc.dll+0x7a2f]
J  org.lwjgl.system.JNI.invokePV(JJ)V
J  org.lwjgl.system.jemalloc.JEmalloc.nje_free(J)V
J  org.lwjgl.system.MemoryUtil.nmemFree(J)V
j  org.lwjgl.system.Struct.free()V+4
j  net.wurstclient.hud2.nanovg.NanoVGContext.getColor(I)Lorg/lwjgl/nanovg/NVGColor;+75
j  net.wurstclient.hud2.nanovg.NanoVGContext.drawRoundedRect(FFFFFI)V+73
j  net.wurstclient.hud2.nanovg.NanoVGUI.fillRounded(...)
j  net.wurstclient.clickgui2.FlatRenderer.drawPanel(...)
j  net.wurstclient.clickgui2.ClickGui.renderTooltip(...)
```

第二份日志的 `siginfo` 是 `EXCEPTION_ACCESS_VIOLATION ... reading address 0x0000000000171988`，第一份是 `reading address 0x0`；两者都发生在 `free()` 内部读堆元数据时。

值得注意的是：两份日志来自**两台完全不同的机器**——C1 是 AMD Ryzen 7 H 255 / Radeon 780M、Java 25.0.1、Axolotl(Modrinth) 启动器；C2 是 Intel Core Ultra 7 265KF、Java 26、PCL 启动器。CPU、GPU、JVM、启动器全不同，栈却逐帧一致，可以排除“某台机器的驱动/硬件偶发”。

### 1.2 根因：`Struct.free()` 与 `Struct.create()` 的分配器不匹配

`NanoVGContext.getColor()` 的实现（以 `versions/26.3` 为例，行号 1211–1224）：

```java
if(colors.size() >= MAX_COLORS)          // MAX_COLORS = 1024，见 :49
{
    for(NVGColor color : colors.values())
        color.free();                    // ← 这里
    colors.clear();
}

NVGColor color = NVGColor.create();      // ← 与 free() 不是同一个分配器
NanoVG.nvgRGBA(..., color);
colors.put(argb, color);
```

同一文件的 `destroy()` 里还有第二处（:1276–1278）：

```java
for(NVGColor color : colors.values())
    color.free();                        // ← 关闭世界/GL 上下文时同样会崩
colors.clear();
```

LWJGL 的语义：

- `NVGColor.create()` → `Struct.create(ByteBuffer)` → `BufferUtils.createByteBuffer(SIZEOF)`，这是 **JVM 管理的直接缓冲（GC 回收）**；
- `Struct.free()` → `MemoryUtil.nmemFree(address())`，走的是 LWJGL 配置的**原生分配器**。26.x 的运行时把它配置成了 jemalloc（崩溃栈里的 `JEmalloc.nje_free` 就是证据）。

把 JVM 直接缓冲的地址交给 jemalloc 去 `free`，属于无效释放：jemalloc 会按自己的 chunk/page 元数据去解析这个地址，轻则破坏堆，重则在下一次 `free`/`malloc` 时读非法地址 → `EXCEPTION_ACCESS_VIOLATION`。这就是“崩溃点在 jemalloc、而不是在 Java 代码”的原因。

> 这个结论不是推测：仓库里 1.20.x/1.21.x 的同一文件已经修过，并且把原因写进了注释——
> `versions/1.20.4/.../NanoVGContext.java:1214-1216`：
> ```java
> // NVGColor.create() wraps a GC-managed direct ByteBuffer.
> // Struct.free() would hand that address to jemalloc and crash;
> // drop the references and let the GC reclaim them.
> colors.clear();
> ```

### 1.3 为什么 26.x 全中、老版本全不中

对全部 67 个工程的 `NanoVGContext.java` 做了哈希与关键字统计：

| 分组 | 工程数 | 文件哈希 | `color.free()` | 注释 | 状态 |
|---|---:|---|---:|---|---|
| 26.1 / 26.1.1 / 26.1.2 / 26.2 / 26.3 ×（Fabric/NeoForge/Forge） | **15** | 全部 `11A608E409DE…`（逐字节相同） | **2 处** | 无 | **崩溃** |
| 1.20.2 – 1.21.11 × 3 加载器 | 52 | 各自版本内一致 | 0 处 | 有 | 已修复 |

也就是说：**26.x 分支是从修复前的旧文件复制出来的**，13 个 26.x 工程（外加 2 个 26.1）把已经修好的 bug 又带了回来。这也解释了为什么崩溃只出现在 26.2/26.3。

### 1.4 为什么崩溃时间点很“随机”

`MAX_COLORS = 1024`。只要某个界面每帧生成不同的 ARGB（彩虹/呼吸/动画颜色、随时间变化的边框色），缓存约 1024 帧就会被填满，然后触发一次“全部 `free()`”。

- `hs_err_pid30668`：`elapsed time: 17.213834 seconds` ≈ 60 FPS 下 1032 帧；
- `hs_err_pid1292`：`elapsed time: 249.293240 seconds`（中途有静止画面，填充更慢）。

这与“打开 ClickGUI 后十几秒到几分钟内随机崩”的体感完全一致，也和两份日志都停在 ClickGUI 渲染（`drawLine` / `drawRoundedRect`）吻合。

### 1.5 修复（15 个文件，机械替换）

与已修复分支保持**逐字一致**：

```diff
--- a/<project>/src/main/java/net/wurstclient/hud2/nanovg/NanoVGContext.java
+++ b/<project>/src/main/java/net/wurstclient/hud2/nanovg/NanoVGContext.java
@@ -1211,10 +1211,11 @@
        if(colors.size() >= MAX_COLORS)
        {
-           for(NVGColor color : colors.values())
-               color.free();
+           // NVGColor.create() wraps a GC-managed direct ByteBuffer.
+           // Struct.free() would hand that address to jemalloc and crash;
+           // drop the references and let the GC reclaim them.
            colors.clear();
        }
@@ -1274,9 +1275,7 @@
            images.clear();
            textureImages.clear();

-           for(NVGColor color : colors.values())
-               color.free();
            colors.clear();

            NanoVGGL3.nvgDelete(vg);
```

需要同步的 15 个文件（内容完全相同，可脚本替换后按哈希校验）：

```
fabric/versions/26.1/…/NanoVGContext.java
fabric/versions/26.1.1/…
fabric/versions/26.1.2/…
fabric/versions/26.2/…
fabric/versions/26.3/…
neoforge/versions/26.1/…
neoforge/versions/26.1.1/…
neoforge/versions/26.1.2/…
neoforge/versions/26.2/…
neoforge/versions/26.3/…
versions/26.1/…
versions/26.1.1/…
versions/26.1.2/…
versions/26.2/…
versions/26.3/…
```

**不推荐**改成 `NVGColor.malloc()` + `free()`：那样要同时改所有 `create()` 调用点并保证每一条路径都释放，改动面远大于“去掉两次 `free`”，而且与另外 52 个工程的既有实现分叉。

### 1.6 验证

1. 替换后全仓 grep：`NanoVGContext.java` 内 `color.free()` 计数必须为 0；
2. 启动 26.2/26.3，打开 ClickGUI 并保持动画 10 分钟以上（或直接把 `MAX_COLORS` 临时改小到 32 加速复现）；
3. 反复进出世界（触发 `destroy()`）不再崩；
4. 内存：颜色缓存最多 1024 个 `NVGColor`（每个约 16 字节 + 直接缓冲），GC 会回收，不存在实际泄漏。

---

## 2. C3：NeoForge 内嵌 Baritone 把整个 mod 拖死（P0）

### 2.1 崩溃栈只是“二次伤害”

崩溃报告写的是 `RotationHelper.preTick` 的 NPE：

```
java.lang.NullPointerException: Cannot read field "player" because "net.wurstclient.WurstClient.MC" is null
  at net.wurstclient.opal.rotation.RotationHelper.preTick(RotationHelper.java:25)
  at net.minecraft.client.Minecraft.handler$zzk000$wurstpenguin$opalPreTick(Minecraft.java:3509)
  at net.minecraft.client.Minecraft.tick(...)
```

但同一份 `latest.log` 的第 34 行就给出了真正的根因：

```
24: WurstB+ Plus 1.5.1-NeoForge-1.21.1 (wurstpenguin)
34: [Render thread/FATAL] [net.neoforged.fml.ModLoader/CORE]:
    Failed to initialize mod containers: ERROR: fml.modloadingissue.javafml.dangling_entrypoint
    [baritoe; baritone.launch.BaritoneForgeModXD; ...\mods\WurstB+ Plus-v1.5.1-NeoForge-1.21.1.jar]
35-62: [ERROR] Cowardly refusing to send event ... to a broken mod state   (共 29 条)
66: [Render thread/FATAL] Unreported exception thrown!
```

### 2.2 因果链

1. `neoforge/versions/1.21.1/build.gradle:104` 依赖 `libs/baritone-api-neoforge-1.11.3-1.21.1.jar`，并在 `jar` 任务里用 `zipTree` **平铺**进产物（:168）。
2. 该 Baritone jar 同时带两样东西：
   - `META-INF/neoforge.mods.toml`：声明 `modId="baritoe"`；
   - `baritone/launch/BaritoneForgeModXD.class`：一个 471 字节、带 `@Mod("baritoe")` 的空入口存根。
3. `jar` 任务只排除了 **Forge 时代**的文件名 `META-INF/mods.toml`（:169），没有排除 `META-INF/neoforge.mods.toml`；而 `duplicatesStrategy = EXCLUDE`（:167）让 Wurst 自己的 `neoforge.mods.toml`（`wurstpenguin`）先到先得。
4. 结果：产物里**只有 `wurstpenguin` 的元数据**，却留着 `@Mod("baritoe")` 的类。NeoForge 4.x 的 `FMLJavaModLanguageProvider` 把“存在 `@Mod` 类但元数据里没有对应 modId”判为 `dangling_entrypoint`，并且**整个 mod 文件**（同一个 jar 里的 `wurstpenguin`）一起初始化失败。
5. `WurstClient.initialize()` 因此从未执行，日志里也**没有任何一条** `Starting WurstB+ Plus...`（`WurstClient.java:110` 的第一行打印）。
6. 但 Wurst 的 mixin 是由 jar manifest 的 `MixinConfigs` 在更早阶段织入的（`debug.log` 显示 23:56:39 已织入，23:56:45 才 FATAL），所以 `Minecraft.tick()` 的 HEAD 注入仍然生效；`WurstClient.MC`（`WurstClient.java:65`，唯一赋值点 :112）保持 null → 第一次 tick 就 NPE。

崩溃报告里的 `Client ticks: 0 ticks / 0.000s` 也印证了“第一次 tick 就炸”。

### 2.3 为什么只有 NeoForge 的这几个工程中招

| 工程 | Baritone 接入方式 | 元数据冲突 |
|---|---|---|
| `neoforge/versions/1.21`、`1.21.1` | `zipTree` 平铺 `libs/*.jar` | **是**（只排除 `mods.toml`） |
| `neoforge/versions/1.20.5`、`1.20.6` | `zipTree`（:155-156），排除清单与 1.21.1 完全一样 | **是**（jar 内同样是 `neoforge.mods.toml` + `@Mod("baritoe")` 存根） |
| `neoforge/versions/1.20.2`–`1.20.4` | `zipTree`，但 jar 内是 `META-INF/mods.toml`（被正确排除） | 否 |
| `neoforge/versions/1.21.2+`、`26.x` | `jarJar(implementation("baritone:..."))` | 否（嵌套 jar 是合法的第二个 mod） |
| `versions/*`（Forge） | `jarJar` + `MixinConnector` | 否 |
| `fabric/*` | `include` / `modImplementation` | 否 |

### 2.4 修复

**根治（打包层）**——`neoforge/versions/1.21.1/build.gradle:167-170`：

```diff
    zip64 = true
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
-   from { zipTree(file("libs/baritone-api-neoforge-1.11.3-1.21.1.jar")) }
+   from({ zipTree(file("libs/baritone-api-neoforge-1.11.3-1.21.1.jar")) }) {
+       // Drop Baritone's empty @Mod entrypoint and its metadata together.
+       // Scope this to Baritone so Wurst's own mod metadata is preserved.
+       exclude 'META-INF/neoforge.mods.toml', 'baritone/launch/BaritoneForgeModXD.class'
+   }
    exclude 'META-INF/*.RSA', 'META-INF/*.SF', 'META-INF/*.DSA', 'META-INF/mods.toml', 'module-info.class'
```

同步到另外三个同缺陷工程：`neoforge/versions/1.21`（:162-164）、`neoforge/versions/1.20.5`（:154-156）、`neoforge/versions/1.20.6`（:154-156）。它们的排除清单与 1.21.1 完全一样，Baritone jar 里也同样带着 `META-INF/neoforge.mods.toml` 与 `@Mod("baritoe")` 存根。

> 仓库里已有同样做法的先例：`scripts/port/Make-TestJars.ps1:48-51` 在制作测试 jar 时就会删掉 `baritone/launch/BaritoneForgeModXD.class`、`META-INF/jarjar/baritone-*.jar` 与 `mixins.baritone.json`。

**防御（代码层，防止任何初始化失败都变成 NPE）**——`neoforge/versions/1.21.1/.../opal/rotation/RotationHelper.java`：

```diff
     public static void preTick() {
-        if(player != WurstClient.MC.player || world != WurstClient.MC.level) {
+        if(WurstClient.MC == null)
+            return;
+        if(player != WurstClient.MC.player || world != WurstClient.MC.level) {
```

同时把 `OpenOpalInputMixin` 的守卫提前——当前顺序是“先调 `preTick()`，再检查 `getHax() == null`”（:23-24），守卫写反了：

```diff
     private void opalPreTick(CallbackInfo ci) {
-        net.wurstclient.opal.rotation.RotationHelper.preTick();
         if (!WurstClient.INSTANCE.isEnabled() || WurstClient.INSTANCE.getHax() == null) return;
+        net.wurstclient.opal.rotation.RotationHelper.preTick();
```

这两处能让“初始化失败”降级为“功能不可用 + 一行日志”，而不是崩游戏；但**不能替代打包修复**。

**顺带**：`neoforge/versions/1.21.1/src/main/resources/mixins.baritone.json:4` 写的是 `"compatibilityLevel": "JAVA_17"`，而 1.21.1 的 Baritone/MC 都是 Java 21（class v65）。日志反复出现 `Class version 65 required is higher than ... (JAVA_17 supports 61)`，建议改 `JAVA_21`。

---

## 3. C4：Forge 26.3 —— 陈旧 `mixins.baritone.json` 遮蔽 Baritone 真实配置（P1）

### 3.1 现象

```
java.lang.ExceptionInInitializerError
  at baritone.utils.PathRenderer.render(PathRenderer.java:96)
Caused by: java.lang.ClassCastException:
  class net.minecraft.client.renderer.RenderPipelines cannot be cast to
  class baritone.utils.accessor.IRenderPipelines
  at baritone.utils.IRenderer.<clinit>(IRenderer.java:54)
```

`IRenderPipelines` 是 Baritone 用 `MixinRenderPipelines` **注入到 `RenderPipelines` 类上的接口**。cast 失败说明这个 mixin 从未应用——不是签名不匹配，是“接口根本没加上”。

### 3.2 根因：同名配置文件覆盖

- Baritone jar（`baritone-maven/baritone/baritone-forge/1.20.0-26.3/`）自带 `mixins.baritone.json`，**21 条**，其中包含 `MixinRenderPipelines`、`MixinRenderType`；
- Wurst 的 `versions/26.3/src/main/resources/mixins.baritone.json` 是一份**19 条**的陈旧副本（`"MixinPlayerController"` 之后直接 `"MixinScreen"`，见 :27-29），**没有** `MixinRenderPipelines`；
- 这份文件被原样打进 Wurst jar 根目录（`allJar` 的 `from sourceSets.main.output`），而 Baritone 的 `BaritoneMixinConnector` 只按**名字**注册 `mixins.baritone.json`。Wurst jar 先加载，资源解析命中 Wurst 的 19 条副本。

日志给出了决定性证据：

```
debug.log:180  Registering mixin config: mixins.baritone.json source=SecureJarResource(baritoe)
debug.log:229  Preparing mixins.baritone.json (19)          ← Baritone 自带是 21
```

全部 7 个日志文件里 `MixinRenderPipelines` / `MixinRenderType` 出现次数都是 **0**，而同配置里的 `MixinMinecraft`、`MixinWorldRenderer` 正常应用（说明配置确实被读取了，只是内容不对）。

这些副本的 SHA-256 在 `versions/26.2`、`26.1.2`、`26.1.1`、`26.1`、`1.21.11` 等 Forge 工程里**完全相同**（`533A55B5…`），说明它是跨版本复制时带过来的，不是为 26.3 维护的。

### 3.3 修复

**推荐**：删除 `versions/26.3/src/main/resources/mixins.baritone.json`，让嵌套 Baritone jar 的 21 条配置成为唯一同名资源（`neoforge/versions/26.3`、`fabric/versions/26.3` 本来就没有这个文件，可对齐）。

**应急一行**（不推荐长期保留）：在 Wurst 副本的 `MixinPlayerController` 后补 `"MixinRenderPipelines",`，可修掉本次 CCE，但同名遮蔽的结构性隐患还在。

同时建议审计其余 27 个带同名副本的工程（`neoforge/versions/26.1.2`、`versions/26.2`、`versions/1.21.11` 等）：只有当该工程把 Baritone 作为**平铺 jar** 打包、且副本内容与 Baritone jar 不一致时才会出问题；`jarJar` 嵌套的工程通常解析到嵌套 jar 自己的配置，但加载顺序仍是隐患。

**防回归**：`scripts/verify-embedded-baritone.ps1` / `scripts/build-all.ps1` 的 `Test-EmbeddedBaritone` 目前只校验嵌套 jar 的 manifest 与 MC 版本，建议增加两条断言：
1. Wurst jar 根目录不得存在 `mixins.baritone.json`；
2. 若存在，必须包含 `MixinRenderPipelines`，且条数与 Baritone jar 一致。

---

## 4. C5：Fabric 1.20.1 整合包 —— Reach 常量修改器与 `reach-entity-attributes` 冲突（P1）

### 4.1 现象与证据

整合包：**Minecraft 1.20.1 + Fabric Loader 0.18.4 + WurstB+ 1.5.1-Fabric-1.20.1**（`latest.log:1`、`:694`）。

崩溃前 9 秒的关键 WARN（`latest.log:1277`，另见 `:1282`、`:1548`、`:1570`）：

```
@ModifyConstant conflict. Skipping wurstpenguin.mixins.json:ReachLegacyRendererMixin from mod wurstpenguin
->@ModifyConstant::wurstEntitySearchDistance(D)D with priority 1000,
already redirected by mixins.reach-entity-attributes.json:client.GameRendererMixin
from mod reach-entity-attributes->@ModifyConstant::getActualReachDistance(D)D with priority 1000
```

紧接着 `latest.log:1278`：

```
[Rhino] Failed to get declared fields for net.minecraft.class_310:
java.lang.RuntimeException: Mixin transformation of net.minecraft.class_757 failed
```

最终崩溃报告（`crash-...:38`）：

```
InjectionError: Critical injection failure: Constant modifier method wurstEntitySearchDistance(D)D
in wurstpenguin.mixins.json:ReachLegacyRendererMixin from mod wurstpenguin
failed injection check, (0/1) succeeded. Scanned 0 target(s).
Using refmap WurstB+ Plus-refmap.json
```

### 4.2 因果链

1. Wurst 的 `ReachLegacyRendererMixin`（`fabric/src/main/java/net/wurstclient/mixin/ReachLegacyRendererMixin.java:13`）改 `GameRenderer.pick(F)V` 里的 `6.0` 常量；
2. 整合包通过 `farmersdelight → porting_lib_base → reach-entity-attributes 2.4.0` 间接带入了**同样修改这个常量**的 `GameRendererMixin`，双方 priority 都是 1000；
3. Mixin 判定冲突，跳过 Wurst 的注入（只剩一条 WARN）；
4. 但 `wurstpenguin.mixins.json` 是 `"required": true`（:2）+ `"defaultRequire": 1`（:134），0 命中直接升级为 `InjectionError`；
5. `GameRenderer`（`class_757`）变换失败，随后 `Minecraft` 等类变换/初始化连锁失败，最终以 `rottencreatures` 入口点异常的形式报出来——**`rottencreatures`、`supplementaries`、KubeJS 都是受害者，不是原因**。

已排除：版本错配（jar 就是 1.20.1）、目标方法不存在（`_recon/mc1201decomp` 里 `pick(F)V` 与 `6.0` 都在）、refmap 缺失（Wurst 自己的 refmap 没有读取失败告警）、jecharacters 冲突（它只是外层转发）。

### 4.3 修复

**最小、可发版**：把注入声明为可选，冲突时只保留 WARN，不再崩：

```diff
-   @ModifyConstant(method = "pick(F)V", constant = @Constant(doubleValue = 6.0))
+   @ModifyConstant(method = "pick(F)V", constant = @Constant(doubleValue = 6.0),
+       require = 0)
    private double wurstEntitySearchDistance(double original)
```

11 份文件内容**逐字节相同**（`DA2F4AB72A…`），需要同步：

```
fabric/                                   ← 本次崩溃 jar 的工程
fabric/versions/1.20.2, 1.20.3, 1.20.4
neoforge/
neoforge/versions/1.20.2, 1.20.3, 1.20.4
versions/1.20.2, 1.20.3, 1.20.4
```

**代价**：在该整合包里 Wurst 的“超过 6 格实体搜索”会静默失效（方块距离仍由未被冲突的 `getPickRange` 注入生效）。这是可接受的降级，建议写进发行说明。

**更彻底**：legacy 分支改成 `@ModifyExpressionValue`（MixinExtras，可与其它 mod 的修改链式组合），或直接读写 `reach-entity-attributes` 的属性。这属于设计改动，不适合本次热修。

**不推荐**：把 priority 抬到 1500。那只是把“被跳过”的角色换给对方；如果对方也是 `defaultRequire: 1`，崩溃会以对方的名义复现（待实机验证）。

---

## 5. C6–C9：已经修过的崩溃（不要重复修）

### 5.1 C6/C7 —— 启动期翻译 NPE（66/67 已修，根工程漏了）

崩溃链：`WurstInitializer.onInitializeClient → WurstClient.initialize → HudManager.start → registerElement → HudElement.getTranslatedName → translate → translateEnglish → mcEnglish.getOrDefault`。

- `mcEnglish` 只在资源重载回调里赋值（`WurstTranslator.java:41`），而 Fabric 的 client entrypoint 在 `Minecraft.<init>` 内部执行，首次重载还没发生；
- 只有用户开启 **Force English** 时才会走 `translateEnglish`（`translate()` 的 :64-65），所以表现为“部分用户必现”；
- 26.1.2 与 1.21.11 两份报告是同一 bug，只是映射名不同（`ClientLanguage` vs `class_1078`）。

当前状态：66 个工程已加守卫并写明原因（`fabric/versions/26.1.2/.../WurstTranslator.java:89-97`）：

```java
// Early in startup (HudManager.start -> HudElement.getTranslatedName)
// the resource reload listener has not populated these maps yet.
String string = englishOnlyStrings.get(key);
if(string == null && mcEnglish != null)
    string = mcEnglish.getOrDefault(key);
if(string == null)
    return key;
```

**唯一遗漏**：根工程 `src/`（v1.6.0，Forge 1.20.1）仍是旧代码——`src/main/java/net/wurstclient/WurstTranslator.java:90-91` 无守卫，且 `src/main/java/net/wurstclient/hud2/HudManager.java:285` 仍在注册时同步取译文。Forge 侧 `FMLClientSetupEvent` 同样早于首次资源重载，开启 Force English 仍会复现同类 NPE。建议照抄 66 份的补丁（两处 `if` + 去掉注册期翻译）。

### 5.2 C8 —— Freecam 的 FakePlayer 实体 ID

`latest (2).log:465-470`：

```
java.lang.IllegalStateException: Tried to access entity ID before ID assignment
  at net.minecraft.world.entity.Entity.getId(Entity.java:446)
  at net.minecraft.client.multiplayer.ClientLevel.addEntity(ClientLevel.java:533)
  at net.wurstclient.util.FakePlayerEntity.spawn(FakePlayerEntity.java:90)
  at net.wurstclient.hacks.FreecamHack.onEnable(FreecamHack.java:81)
```

26.2 起 `Entity.getId()` 在 ID 未分配时会抛异常。当前 26.2/26.3 已改为先分配一个未使用的负 ID 再 `addEntity`（`fabric/versions/26.3/.../FakePlayerEntity.java` 的 `spawn()`，注释明确写了 26.2+ 的行为），**已修**；1.21.11 仍是旧实现但该版本不会抛，无需回填。

### 5.3 C9 —— 未注册的 accessor mixin

`latest (1).log:195-253`：

```
Mixin transformation of net.wurstclient.mixin.ClientPacketListenerAccessor failed
Illegal classload request for accessor mixin ... The mixin is missing from
wurstpenguin.mixins.json which owns package net.wurstclient.mixin.*
```

这是 1.5.1 的 26.3 构建在 `NoChatReportsOtf` 里强转 `ClientPacketListenerAccessor` 时触发的。当前 26.2/26.3 已把该类注册进配置（`fabric/versions/26.3/src/main/resources/wurstpenguin.mixins.json:122`；Forge/NeoForge 对应文件也有一份），**已修**——前提是重新构建 jar，旧 jar 不会自愈。26.1.x 没有这个类，不适用。

---

## 6. C10/C11：旧 MixinExtras 与 Android JDK 附件

### 6.1 C10 —— Windows 日志中的旧 MixinExtras `@Redirect` 缺陷

`game-20261102-233743-13392.log` 末尾：

```
Mixin transformation of net.minecraft.class_1657 failed
Caused by: java.lang.ClassCastException: class java.util.ArrayList cannot be cast to
class org.objectweb.asm.tree.AnnotationNode
  at com.llamalad7.mixinextras.wrapper.factory.FactoryRedirectWrapperMixinTransformer.transform(...)
```

- 环境：**Windows amd64 / Java 21**，MC 1.21 + **Fabric Loader 0.16.10** + Wurst 1.5.0-Fabric-1.21；该日志不是 Android，Android JDK 是另一份附件。
- 运行期 MixinExtras 是 **0.5.0-beta.2**（日志里的 `mixinextras-0.5.0-beta.2-ea86eade6c93c3c1.jar`）；
- 这是 MixinExtras 在 `@Redirect` 上的已知缺陷：旧版本（含 0.5.4）在 `FactoryRedirectWrapperMixinTransformer` 里会抛同样的 CCE，较新版本已修（参见 [MixinExtras Releases](https://github.com/LlamaLad7/MixinExtras/releases)、[Carpet-Hao-Addition 的兼容性说明](https://www.curseforge.com/minecraft/mc-mods/carpet-hao-addition)）；
- 本模组自身用了大量 `@Redirect`（仅 `fabric/` 树内就有 283 处），所以在这个旧加载器上必炸。

**修复建议**（按优先级）：

1. 把 Fabric Loader 升到 **0.17.x 或更高**，并以实际内嵌 MixinExtras 版本为准；
2. 或在构建里 `include` 一份 **MixinExtras 0.5.5+**（Fabric 端），不再依赖加载器自带版本；
3. 长期：把关键 `@Redirect` 换成 `@WrapOperation`，降低对 MixinExtras 版本的敏感度。

同时建议在 `fabric.mod.json` 里声明最低 loader 版本，避免用户用 0.16.x 启动。

### 6.2 C11 —— `FCL-JDK8-android-aarch64-8u442`

这是一个 **Java 8** 的 Android aarch64 JDK。本仓库所有工程的目标运行时都高于它：

| MC 版本 | 需要的 Java |
|---|---|
| 1.20.1–1.20.4 | 17 |
| 1.20.5–1.20.6 | 21 |
| 1.21.x / 1.21.11 | 21 |
| 26.x | 25（各 26.x 工程 `options.release = 25`） |

Java 8 连 class 文件版本都对不上，**不能用于运行本模组**。如果是在 FCL 上跑，请选 Java 17（1.20.1–1.20.4）、Java 21（1.20.5–1.21.11）或 Java 25（26.x）的 aarch64 运行时。这份 JDK 本身不是任何一份崩溃日志的原因，但会让人误以为“换 JDK 能解决”。

另外提醒：C2 的 26.2 崩溃用的是 **Java 26**（`26+35-2893`），而 26.x 工程目标是 25。建议统一用 25 LTS，避免 EA/JDK 版本差异干扰判断。

---

## 7. C12：非致命但值得跟进的日志

### 7.1 1.21.11 的 `fogless_lines` 着色器

`latest (3).log:1848-1912`：

```
Couldn't compile pipeline wurst:pipeline/wurst_esp_lines:
vertex shader wurst:core/fogless_lines was invalid
...
Couldn't find source for VERTEX shader (wurst:core/fogless_lines)
```

游戏没有崩（后续资源包被移除后继续运行），影响是 ESP 线框/深度线渲染不可用。当前 `fabric/versions/1.21.11/src/main/resources/assets/wurst/shaders/core/fogless_lines.{vsh,fsh}` 都存在，`WurstShaderPipelines.java:27-46` 也按 `wurst:core/fogless_lines` 注册。优先怀疑：

1. 该日志来自 **1.5.0** 的 jar，当时资源未打进包（1.5.1 已补）；
2. 或与日志中同时出现的资源包（`DetailedAnimationsReworked` 等）冲突；
3. 或 1.21.11 的 pipeline/ShaderSource 加载路径与 26.x 不同。

建议用 1.5.1 的 1.21.11 jar 复测，并检查 jar 内是否确实存在这两个 shader 文件。

### 7.2 `Brightness` 的越界值

`latest (2).log:729`、`latest (3).log:1876`：

```
Error saving option 亮度: Value 16.0 outside of range [0.0:1.0]
```

有功能把 gamma 写成 16.0，原版会拒绝并回退。若这是 `Brightness` hack 的实现，不能直接把渲染用 gamma 限制到 `[0,1]`，那会破坏 Fullbright。应仅在 `Options.save()` 期间使用合法的默认亮度，并在 `finally` 恢复渲染值；当前 26.2/26.3 的旧守卫也需补恢复。

---

## 8. 横向问题：67 份副本让“修过的 bug”反复回归

这批日志暴露的不是单点 bug，而是**同一份源码复制 67 次后的回填失控**：

| 现象 | 证据 |
|---|---|
| 1.20.x–1.21.x 已修好的 `NVGColor.free()`，在 26.x 原样回归 | 15 个 26.x 工程文件哈希完全相同，且都带 2 处 `free()` |
| 翻译守卫修了 66 份，漏了根工程 v1.6.0 | 66 份含 `mcEnglish != null`，`src/` 不含 |
| 26.3 的 Baritone 配置是从 26.2 复制的 19 条旧文件 | 5 个工程的 `mixins.baritone.json` SHA-256 完全一致，均缺 `MixinRenderPipelines` |
| 同一个 `ReachLegacyRendererMixin` 在 11 个工程里需要同一行 `require = 0` | 11 份文件逐字节相同 |
| 26.3 的 accessor 注册修了，但 1.5.1 的旧 jar 仍在流通 | `latest (1).log` 即旧 jar |

**建议**（成本低、收益高）：

1. 在 `scripts/` 里加三道机械检查（可挂到 `verify-all-projects.ps1` / CI）：
   - 任何 `NanoVGContext.java` 中，`NVGColor.create()` 与 `color.free()` 不得同时出现；
   - 任何可选功能的 `@ModifyConstant` 必须显式 `require = 0`；FastBreak 的三个 cooldown 写入保留已审查的 `require = 3` 契约。提高 `priority` 不能代替冲突处理；
   - 打包产物根目录不得出现与嵌入 Baritone 同名的 `mixins.baritone.json`，除非条目数与 Baritone jar 一致。
2. 给 `docs/STATUS.md` 增加一张「已知崩溃 → 修复状态矩阵」（崩溃签名 / 根因 / 已修工程 / 未修工程），避免“修了 66 个、漏 1 个”。
3. v1.6.0 根工程与 66 个 v1.5.1 工程已经分叉，修复合同时要**双向回填**；建议固定“先改模板工程 → 脚本同步 → 哈希校验”的流程。

---

## 9. 修复优先级

| 优先级 | 事项 | 改动量 | 风险 |
|---|---|---|---|
| **P0** | C1/C2：删除 15 个 26.x 工程 `NanoVGContext` 中的两处 `color.free()` | 15 文件 × 2 hunk | 极低（与 52 个已修工程一致） |
| **P0** | C3：`neoforge/versions/1.20.5`、`1.20.6`、`1.21`、`1.21.1` 的 jar 排除 `META-INF/neoforge.mods.toml` 与 `baritone/launch/BaritoneForgeModXD.class` | 4 文件 | 低（有仓库内先例） |
| **P1** | C3 防御：`RotationHelper.preTick` 空守卫 + `OpenOpalInputMixin` 守卫提前 | 66 份同源代码 | 低 |
| **P1** | C4：删除 `versions/26.3/src/main/resources/mixins.baritone.json`（并审计其余 27 份同名副本） | 1 文件 + 审计 | 低 |
| **P1** | C5：11 份 `ReachLegacyRendererMixin` 加 `require = 0` | 11 文件 × 1 行 | 低；有功能降级，需写发行说明 |
| **P1** | C7：根工程 `src/` 的翻译守卫 + 去掉 `HudManager` 注册期翻译 | 2 文件 | 低（照抄 66 份） |
| **P2** | C10：Fabric 端声明最低 loader 版本 / 内嵌 MixinExtras 0.5.5+；逐步替换 `@Redirect` | 构建配置 | 中 |
| **P2** | `mixins.baritone.json` 的 `compatibilityLevel` 改为 `JAVA_21`；C12 的 shader / Brightness 复测 | 小 | 低 |
| **P3** | 增加第 8 节的机械检查与崩溃矩阵文档 | 脚本/文档 | 低 |

---

## 10. 附录：证据索引

| 结论 | 关键证据 |
|---|---|
| C1 崩溃栈 | `hs_err_pid1292.log:8-9, 32-44`；`错误报告-2026-11-10_19.19.23/hs_err_pid30668.log:8-9, 39-50` |
| C1 源码 | `versions/26.3/.../NanoVGContext.java:49, 1211-1224, 1276-1278`；对照 `versions/1.20.4/.../NanoVGContext.java:1212-1224` |
| C3 dangling entrypoint | `错误报告-2026-11-10_23.56.54/latest.log:24, 34, 35-62, 66` |
| C3 打包 | `neoforge/versions/1.21.1/build.gradle:104, 151-171`；`libs/baritone-api-neoforge-1.11.3-1.21.1.jar` 内 `META-INF/neoforge.mods.toml` + `baritone/launch/BaritoneForgeModXD.class` |
| C3 代码 | `neoforge/versions/1.21.1/.../RotationHelper.java:25`；`.../mixin/OpenOpalInputMixin.java:21-24`；`.../WurstClient.java:65, 112` |
| C4 配置遮蔽 | `错误报告-2026-11-11_19.59.37/debug.log:180, 229`；`versions/26.3/src/main/resources/mixins.baritone.json:1-33`（缺 `MixinRenderPipelines`） |
| C5 冲突 | `minecraft-exported-crash-info.../latest.log:1, 694, 1277, 1278`；`crash-...:38`；`fabric/src/.../ReachLegacyRendererMixin.java:13` |
| C5 致命条件 | `fabric/src/main/resources/wurstpenguin.mixins.json:2, 134` |
| C6/C7 | `fabric/versions/26.1.2/.../WurstTranslator.java:89-97`；根工程 `src/.../WurstTranslator.java:90-91`、`src/.../HudManager.java:285` |
| C8 | `latest (2).log:465-470`；`fabric/versions/26.3/.../FakePlayerEntity.java` 的 `spawn()` |
| C9 | `latest (1).log:195-253`；`fabric/versions/26.3/src/main/resources/wurstpenguin.mixins.json:122` |
| C10 | `game-20261102-233743-13392.log` 末尾（`FactoryRedirectWrapperMixinTransformer`） |
| C12 | `latest (3).log:1848-1912`；`latest (2).log:729` |

---

*报告生成：2026-11-12。所有行号以当前工作区源码为准；崩溃日志中的行号是当时构建的版本，可能相差 ±4 行。*
