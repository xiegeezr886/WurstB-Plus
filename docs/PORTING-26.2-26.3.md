# MC 26.2 → 26.3 移植说明

本文件记录 Minecraft **26.3**（2026-09-15 发布，Wilderness Bound）三个加载器工程的建立过程、
依赖版本、已做的改写、Baritone 来源，以及尚未完成的部分。

> **状态（本文写作时）**：**三个工程的 `compileJava` 全部通过**，均为全量重编译验证、0 错误：
>
> | 树 | 源文件 | class |
> | --- | ---: | ---: |
> | `versions/26.3`（Forge） | 793 | 1037 |
> | `fabric/versions/26.3` | 794 | 1040 |
> | `neoforge/versions/26.3` | 794 | 1039 |
>
> Forge 树相对 26.2 供体改动 **93 项**；另两树按「跨加载器相同文件直接搬运」推进。
> **三个工程都还没有任何游戏内验证。**
> 详见「用上游 Wurst7 的 26.3 分支做参照」「NeoForge / Fabric 两树的移植」「移植结果与 API 差异清单」三节。

> ⚠️ **关于本文早先的「错误数递减」数据**：本文中间版本曾记录「104 → 64 → 15 → 10 → 6 → 1」
> 这样的逐轮错误数。**那套数字不可比、结论也不可靠**，原因见「关于错误计数的更正」一节。
> 唯一可信的结论是最终的全量编译 0 错误。

## 目标与范围

新增三个工程，与其余 61 个版本工程同构：

| 工程目录 | 加载器 | 目标 |
| --- | --- | --- |
| `versions/26.3/` | Forge | MC 26.3 / Forge 66.0.3 |
| `neoforge/versions/26.3/` | NeoForge | MC 26.3 / NeoForge 26.3.0.16-beta |
| `fabric/versions/26.3/` | Fabric | MC 26.3 / Fabric Loader 0.19.5 + API 0.161.0+26.3 |

**功能基线是 v1.5.0**，与其余版本工程一致。根目录 Forge 1.20.1 独有的 v1.6 子系统
（VAPE 风格 ClickGUI、网易云音乐、Skiko 渲染、周界挖掘、种子矿透，共 152 个文件）
**不在本次范围内**。

**供体选择**：三个工程各自克隆自**同加载器的 26.2 工程**。手册里的既有结论是「跨加载器移植时，
同 MC 版本的另一个加载器 > 同加载器的邻近版本」，但没有任何加载器有 26.3 的兄弟工程，
所以退化为「同加载器、邻近版本」。这一选择还有一个额外好处：26.2 的三个树整树差异只有 5 个文件，
因此 26.3 的三个树之间也保持直接可比。

## 依赖版本对照

| 依赖 | 26.2 | 26.3 | 说明 |
| --- | --- | --- | --- |
| Minecraft | 26.2 | **26.3** | 26.3 是当前最新正式版，无 26.3.1 |
| Forge | 65.1.0（recommended） | **66.0.3** | Forge 尚未发布 26.3 的 `recommended`，66.0.3 是 `latest` |
| NeoForge | 26.2.0.53-beta | **26.3.0.16-beta** | 上游仍只有 beta，无稳定版 |
| Fabric Loader | 0.19.3 | **0.19.5** | 0.19.5 是当前 stable |
| Fabric API | 0.156.0+26.2 | **0.161.0+26.3** | |
| Fabric Loom | 1.17.17 | 1.17.17 | 不变 |
| Gradle | 9.4.1（Forge/NeoForge）· 9.6.0（Fabric） | 不变 | 沿用供体的 wrapper，未换发行版 |
| Java | 25 | 25 | 三者 `options.release = 25` |
| Baritone | `1.18.0-26.2`（**补丁后**） | **`1.20.0-26.3`**（上游原生） | 见下节 |

> ⚠️ **两个加载器只有 beta / 非推荐版本**。Forge 66.0.3 没有 `recommended`，NeoForge 只有
> `26.3.0.16-beta`。这不是本次移植引入的问题——26.2 工程当初钉的 `26.2.0.53-beta` 也是 beta——
> 但意味着 26.3 的产物目前**不具备「稳定版」资格**。

## 已完成的改写

三个工程都是由 `versions/26.2`、`neoforge/versions/26.2`、`fabric/versions/26.2` 整树克隆而来
（排除 `build/`、`.gradle/`、`run/`、`logs/`），随后按下表逐项改写。改写脚本为
`D:\WurstB\_port_263.py`，每一项都断言期望命中次数，因此漏改会直接报错而不是静默通过。

### Forge（`versions/26.3`）

| 文件 | 改动 |
| --- | --- |
| `gradle.properties` | `minecraft_version` 26.2→26.3；`forge_version` 65.1.0→66.0.3；`mod_version` → `v1.5.0-Forge-26.3` |
| `build.gradle` | Baritone 坐标 → `baritone:baritone-forge:1.20.0-26.3`；`allJar` 里硬编码的 jar 路径同步改 |
| `src/main/resources/META-INF/mods.toml` | `loaderVersion="[66,)"`；forge 依赖 `[66.0.3,67)`；minecraft 依赖 `[26.3,26.4)`；描述文本 |
| `src/main/resources/META-INF/jarjar/metadata.json` | `range` → `[1.20.0-26.3,1.21)`；`artifactVersion`；`path` |
| `src/main/java/net/wurstclient/WurstClient.java` | `MC_VERSION = "26.3"`（渲染在 `WurstLogo` 上） |

### NeoForge（`neoforge/versions/26.3`）

| 文件 | 改动 |
| --- | --- |
| `gradle.properties` | `minecraft_version` → 26.3；`minecraft_version_range=[26.3]`；`neo_version` → `26.3.0.16-beta`；`mod_version` **保持 `1.5.0`**（该工程不带加载器后缀，与另两个不同） |
| `build.gradle` | Baritone 坐标 → `baritone:baritone-neoforge:1.20.0-26.3`；**硬编码**的 `archiveFileName` 里 `NeoForge-26.2` → `-26.3`（它不从属性派生，必须手改） |
| `src/main/templates/META-INF/neoforge.mods.toml` | 描述文本 |
| `src/main/java/net/wurstclient/WurstClient.java` | `MC_VERSION = "26.3"` |

### Fabric（`fabric/versions/26.3`）

| 文件 | 改动 |
| --- | --- |
| `gradle.properties` | `minecraft_version` → 26.3；`loader_version` 0.19.3→0.19.5；`fabric_version` → `0.161.0+26.3`；`mod_version` → `1.5.0-Fabric-26.3` |
| `build.gradle` | Baritone 坐标 → `baritone:baritone-api-fabric:1.20.0-26.3`；旁边那句注释同步 |
| `src/main/resources/fabric.mod.json` | `minecraft: "~26.3"`；描述文本 |
| `src/main/java/net/wurstclient/WurstClient.java` | `MC_VERSION = "26.3"` |

### 刻意**没有**改的东西

- **`pack.mcmeta` 的 `pack_format` 仍是 34**。26.3 的资源包格式已变为 `97.1`／数据包 `121.0`
  （新的点分格式），所以 34 是过期的。但仓库里**从 1.21.1 到 26.3 的 60+ 个工程全是 34**
  （只有根目录 1.20.1 是 15），说明它从未按版本跟踪过，且已通过游戏内验证的 26.2 / 1.21.11
  也用它。这是仓库级的既有陈债，不属于本次移植的改动范围，故保持原样并在此登记。
- **`compatibilityLevel` 的既存不一致**：Forge/Fabric 是 `JAVA_21`、NeoForge 是 `JAVA_25`，
  三者其实都编译到 Java 25。与 26.2 保持一致，不做「顺手统一」。
- **gradle wrapper 未换**：沿用供体的 9.4.1 / 9.6.0。
- **`WurstBufferSource.java` 里的 "26.2"**：那是「该类在 `26.2-snapshot-5` 被移除」的上游历史注释，
  不是版本锚点，**故意保留**。残留扫描对它做了白名单。
- **供体的 `libs/baritone-api-forge-26.1.2.jar` 一并复制了**。它无人引用（`flatDir` 只提供解析路径，
  没有依赖指向它），是 26.2 就有的残留。复制它是为了让 26.3 与 26.2 的树差异**只有版本锚点**这一类，
  便于 review；清理它应作为独立改动同时处理 26.2 与 26.3。

## Baritone

26.3 三个工程依赖 `baritone:{baritone-forge,baritone-neoforge,baritone-api-fabric}:1.20.0-26.3`，
由 `D:\WurstB\_install_baritone_263.py` 从 **Baritone 官方 v1.20.0 release**（发布说明即
"For Minecraft 26.3"，2026-09-23）装入本地 `baritone-maven/`，**未经任何补丁**。

| artifact | 来源资产 | 大小 | SHA-1（与 release 的 `checksums.txt` 一致） |
| --- | --- | ---: | --- |
| `baritone-api-fabric` | `baritone-api-fabric-1.20.0.jar` | 4,821,430 | `8c0de04b371a466f7c6808119ea18f5554653fb6` |
| `baritone-forge` | `baritone-api-forge-1.20.0.jar` | 4,829,584 | `7c77b45d509074022a9aa69c4efaa281db330be4` |
| `baritone-neoforge` | `baritone-api-neoforge-1.20.0.jar` | 4,829,672 | `bdb940bc53b36415310bbe50ffc32ffd228d7d01` |

三个 SHA-1 均已核对通过（注意官方 `checksums.txt` 用的是 SHA-1，不是 SHA-256）。

**为什么不需要 26.2 那套兼容补丁**：26.2 钉的 `1.18.0-26.2` 是把 Baritone 1.18.0 从 26.1
拖到 26.2 的产物——`_tools/baritone-26.2-compat` 往 jar 里注入
`Baritone/api/utils/LegacyTuple`、`LegacyTesselator`、`LegacyRenderPipelineBuilder`、`LegacyRenderType`
四个垫片类（旁边留着的 `*.jar.pre-26.2-compat` 就是补丁前的原件）。已核对：**v1.20.0 官方 jar
里没有这 4 个类**，因为它原生以 26.3 为目标。
同时已核对 v1.20.0 的 `META-INF/MANIFEST.MF` 仍带
`MixinConfigs: mixins.baritone.json` 与 `MixinConnector: baritone.launch.BaritoneMixinConnector`，
所以「Baritone 必须可被 Mixin 引导」这条校验对 26.3 依然成立。

`baritone-maven/` 被 `.gitignore` 排除，所以这一步是**本机操作**，克隆出去的仓库里没有这些 jar
（需要按 `docs/PORTING-NEW-VERSIONS.md` 重建，或重新执行上面的安装脚本 + 先下载 release 资产）。

## 构建脚本维护面同步

| 文件 | 改动 |
| --- | --- |
| `scripts/common.ps1` | JDK 映射加 `"26.3" = 25`（**不加会直接抛** `Unknown Minecraft version for JDK lookup`）；`Get-WurstbGradleProjects` 加 3 行（该表同时驱动 `doctor.ps1` 与 `seed-gradle-wrapper.ps1`） |
| `scripts/doctor.ps1` | JDK 逐版本报告的 MC 列表加 `"26.3"` |
| `scripts/build-all.ps1` | 头部注释 15 → **18** 个产物；`$projects` 加 3 行；**拆分**了原先按 `26.2` 门控的 Baritone 校验（见下）；Forge「必需类」名单加入 `26.3` |
| `scripts/run-version-tests.ps1` | `$JDKs` 加 `"26.3"`（同样会抛）；`$requireBaritone` 名单加 `"26.3"` |
| `scripts/patch-baritone-26.2.ps1` | **未改**。它硬编码 26.2 的缓存路径与 `1.18.0-26.2` 产物名，26.3 用上游原生 Baritone 不需要它；`build-all.ps1` 的调用点已加注释说明这是刻意的 |

### `build-all.ps1` 里 Baritone 校验的拆分

原来整块都在 `if ($mcVersion -eq "26.2")` 里，混了两件事，26.3 不能照搬：

- **`Legacy*` 垫片类断言** → 现在**只在 26.2**。26.3 用未打补丁的上游 jar，这些类不该存在，
  若照搬会误报失败。
- **Mixin manifest 属性断言**（`MixinConfigs` / `MixinConnector`）→ 现在**覆盖 26.2 与 26.3**。
  这是真的正确性要求，且已核对 v1.20.0 的 manifest 满足它。

`$compatibilityNote`（"26.2 compatibility verified"）保持 26.2 专属，26.3 得到空字符串——
在编译与游戏内验证完成之前，不给 26.3 加任何「已验证」字样。

## 26.3 的 API 差异（已实测，非推测）

以下结论全部来自构建产物与官方元数据，不是猜测：MC 版本 JSON（piston-meta）用于依赖库对比，
ForgeGradle 反编译/打补丁阶段产出的 `patched.jar`（26.x 非混淆，含真实类名与源码）用于 API 核对。

### 差异一：LWJGL 的 glfw 模块被 SDL3 取代 —— 输入层必须改写

| | `org.lwjgl:lwjgl-glfw` | `org.lwjgl:lwjgl-sdl` |
| --- | --- | --- |
| MC 26.2 | `3.4.1`（含各平台原生库） | 无 |
| MC 26.3 | **完全没有** | `3.4.3`（含各平台原生库） |

后果：**`import org.lwjgl.glfw.GLFW` 在 26.3 上无法解析**。每个 26.3 树里有
**40 个文件、119 处 `GLFW.*` 引用**。

有个重要的缓解事实：Mojang 把这两套常量的**名字**一一对应地搬进了自己的 `InputConstants`，
且输入层的混入目标**签名没变**。但**数值不是保值的**——`InputConstants.KEY_*` 是
**SDL scancode**，而 `GLFW.GLFW_KEY_*` 是 GLFW keycode，两者完全不同。下表是逐项实测值
（GLFW 侧取自 26.2 缓存里的 `lwjgl-glfw-3.4.1.jar`，`javap -constants`）：

| 26.2 写法 | 26.3 写法 | GLFW 值 | 26.3 值 | 值相同？ |
| --- | --- | ---: | ---: | :---: |
| `GLFW.GLFW_KEY_A` | `InputConstants.KEY_A` | 65 | 4 | ✗ |
| `GLFW.GLFW_KEY_0` | `InputConstants.KEY_0` | 48 | 39 | ✗ |
| `GLFW.GLFW_KEY_ESCAPE` | `InputConstants.KEY_ESCAPE` | 256 | 41 | ✗ |
| `GLFW.GLFW_KEY_ENTER` | `InputConstants.KEY_RETURN` | 257 | 40 | ✗ **且改名** |
| `GLFW.GLFW_KEY_BACKSPACE` | `InputConstants.KEY_BACKSPACE` | 259 | 42 | ✗ |
| `GLFW.GLFW_KEY_DELETE` | `InputConstants.KEY_DELETE` | 261 | 76 | ✗ |
| `GLFW.GLFW_KEY_UP` | `InputConstants.KEY_UP` | 265 | 82 | ✗ |
| `GLFW.GLFW_KEY_F3` | `InputConstants.KEY_F3` | 292 | 60 | ✗ |
| `GLFW.GLFW_KEY_LEFT_CONTROL` | `InputConstants.KEY_LCONTROL` | 341 | 224 | ✗ **且改名** |
| `GLFW.GLFW_KEY_RIGHT_CONTROL` | `InputConstants.KEY_RCONTROL` | 345 | 228 | ✗ **且改名** |
| `GLFW.GLFW_MOUSE_BUTTON_LEFT` | `InputConstants.MOUSE_BUTTON_LEFT` | 0 | 1 | ✗ |
| `GLFW.GLFW_MOUSE_BUTTON_RIGHT` | `InputConstants.MOUSE_BUTTON_RIGHT` | 1 | 3 | ✗ |
| `GLFW.GLFW_MOUSE_BUTTON_MIDDLE` | `InputConstants.MOUSE_BUTTON_MIDDLE` | 2 | 2 | ✓（巧合） |
| `GLFW.GLFW_PRESS` / `GLFW_RELEASE` | `InputConstants.PRESS` / `RELEASE` | 1 / 0 | 1 / 0 | ✓ |
| `GLFW.GLFW_MOD_CONTROL` | `InputConstants.MOD_CONTROL` | 2 | 192 | ✗ |
| `InputConstants.isKeyDown(MC.getWindow(), key)` | `InputConstants.isKeyDown(key)` | — | — | **签名变了：Window 参数被去掉** |
| `GLFW.glfwGetMouseButton(window.handle(), code)` | 无直接等价物 | — | — | 两处调用点，需逐个判断 |

**这为什么重要**：改名之外数值也变了，所以这不是纯文本替换。凡是把键码/鼠标键/修饰键的
**数字**当作跨层约定使用的地方（与硬编码字面量比较、跨来源互相比对、持久化、发进事件再被
别处按 GLFW 编号解读）都会**静默错位**，编译器不会报错。移植时必须逐处确认数值的来源与
消费方属于同一套编码。

26.3 的 `InputConstants` 共 167 个 `public static final int` 常量，命名组：`KEY_*`（键盘
scancode）、`KEYCODE_*`（另一套编码，含 `KEYCODE_RETURN` 等）、`MOUSE_BUTTON_*`（1–8）、
`PRESS`/`RELEASE`/`REPEAT`、`MOD_*`（`MOD_SHIFT=3`、`MOD_CONTROL=192`、`MOD_ALT=768`、
`MOD_SUPER=3072`、`MOD_CAPS_LOCK`、`MOD_NUM_LOCK`）。

混入目标类与签名**均未变**（已逐一核对 26.3 源码）：

- `MouseHandler.onButton(long handle, MouseButtonInfo, int action)`、`onScroll(long, double, double)`、`onMove(long, double, double, double, double)`、`handleAccumulatedMovement()`
- `KeyboardHandler.keyPress(long handle, int action, KeyEvent event)`
- `KeyMapping`、`Window` 都还在原包 `com.mojang.blaze3d.platform`

也就是说 `MouseHandlerMixin` / `KeyboardHandlerMixin` / `KeyBindingMixin` 的**注入点依然有效**，
破坏面被限制在「常量与静态调用的替换」这一层。

### 差异二：渲染后端整体搬迁 —— `com.mojang.blaze3d` → `com.mojang.renderpearl`

这是本次最大的变化，也正是 26.3 那项「OIT（顺序无关透明）」改动的实际形态：换来的不只是透明算法，
而是一整套重命名 + 重组 + 新增 Vulkan 后端的 GPU 抽象层。

| 26.2 | 26.3 |
| --- | --- |
| `com.mojang.blaze3d.pipeline.RenderPipeline` | `com.mojang.renderpearl.api.pipeline.RenderPipeline` |
| `com.mojang.blaze3d.pipeline.BlendFunction` | `com.mojang.renderpearl.api.pipeline.BlendFunction` |
| `com.mojang.blaze3d.pipeline.ColorTargetState` 等 | `com.mojang.renderpearl.api.pipeline.*` |
| `com.mojang.blaze3d.buffers.GpuBuffer` 等 | `com.mojang.renderpearl.api.buffers.*` |
| （无） | `com.mojang.renderpearl.backend.opengl.*`（29 个类） |
| （无） | `com.mojang.renderpearl.backend.vulkan.*`（33 个类，**新增 Vulkan 后端**） |

`renderpearl` 下共 12 个包：`api/pipeline`(17)、`api/device`(14)、`api/commands`(7)、`api/buffers`、
`api/textures`、`api/vertex`、`backend/api`、`backend/common`、`backend/opengl`、`backend/vulkan`、
`frontend/shaders`、`util`。`BlendEquation`、`BlendFactor`、`BlendOp`、`CompiledRenderPipeline`、
`GpuDeviceLossException`、`GpuOutOfMemoryException` 等是新增的。

**⚠️ 更正**：本节初稿曾断言 `RenderSetup`、`OutputTarget`、`TextureTransform`、`LayeringTransform`
这 4 个类「在 26.3 里彻底不存在，`WurstRenderLayers` 需要重写」。**那个结论是错的**，原因是我拿
`patched.jar` 做全库搜索——它只是 ForgeGradle 补丁阶段的**部分**类集，连 `RenderSetup`、
`RenderType` 这种明显还在的类都搜不到，所以「搜不到」不能推出「不存在」。

用上游 Wurst7 的 26.3 分支核对后的真实情况是：

| 类 | 26.3 状态 |
| --- | --- |
| `RenderSetup` | **仍在** `net.minecraft.client.renderer.rendertype` |
| `LayeringTransform` | **仍在** 同包 |
| `TextureTransform` | **仍在** 同包 |
| `OutputTarget` | **确实被删除** |

所以 `WurstRenderLayers` 的真实改动只是：删掉 `OutputTarget` 的导入和 3 处
`.setOutputTarget(OutputTarget.ITEM_ENTITY_TARGET)` 调用（`RenderType.create` 本身没变）。
`EasyVertexBuffer` 里那一处 `OutputTarget.ITEM_ENTITY_TARGET.getRenderTarget()` 改为
`WurstClient.MC.gameRenderer.mainRenderTarget()`。

**教训**：`patched.jar` 不是权威的 API 清单。要判断某个类/方法在 26.3 是否存在，应当用
**上游同版本分支**或**编译器的实际报错**，而不是在补丁阶段的中间产物里 grep。

影响面：每个 26.3 树里有 **104 个文件 `import com.mojang.blaze3d`**——但注意其中绝大多数
（`blaze3d.vertex.*`、`blaze3d.platform.*`、`blaze3d.systems.RenderSystem`、`blaze3d.pipeline.RenderTarget`）
**并未搬迁**，所以真正要改的只是下面那张映射表里的少数类。

`net.minecraft.client.renderer.RenderPipelines` 仍在原包（未搬迁），这一点是好消息。

### 一句话总结工作量

| 层面 | 规模 | 性质 |
| --- | --- | --- |
| 输入层 | 40 文件 / 119 处引用 | **机械替换**为主，但**常量数值不保值**（GLFW keycode → SDL scancode），需逐处确认编码一致 + 一处签名调整 + 2 处鼠标状态查询要手写 |
| 渲染层 | 104 文件 + 4 个消失的类 | **机械改 import** 为主，但 `WurstRenderLayers` 需**真正重写** |
| 混入注入点 | 目标类与签名均未变 | 不需要改注入点 |
| 整体 | 明显大于 26.1→26.2 | 26.1→26.2 没有这类整包搬迁 |

## 用上游 Wurst7 的 26.3 分支做参照（本轮最有效的降本手段）

**上游 [Wurst-Imperium/Wurst7](https://github.com/Wurst-Imperium/Wurst7) 已经完成了 26.3 的适配**——
它有 `26.3` 分支（以及 `26.4`）和 `v7.56-26.3`、`v7.55.2-MC26.3` 等 tag。所以 26.3 的 API 迁移
**不需要逆向 MC 的反编译源码**，可以直接取上游的迁移成果。

### 方法

本仓库是 Wurst 的 fork，渲染/输入这些基础设施文件与上游高度同源（忽略换行符后差异很小：
`WurstRenderLayers` 差 9 行、`EasyVertexBuffer` 27 行、`WurstShaderPipelines` 57 行；
只有 fork 自己改得多的 `RenderUtils` 差 807 行）。因此：

1. 下载上游 `26.2` 与 `26.3` 两个分支的源码；
2. `diff` 两者，得到**上游的 26.2→26.3 迁移**（138 改 + 10 增删，共 5029 行 diff）；
3. 把这个迁移按类/按 API 归类成映射表，套用到本仓库的 26.2 代码上。

这样得到的是**权威答案**，而不是猜。事后证明这一步价值极大：它把剩余错误从 **64 降到 15**。

> **顺带验证了输入层**：上游的输入层改法与本次独立做的完全一致——同样是
> `GLFW.GLFW_KEY_ENTER` → `InputConstants.KEY_RETURN`、`GLFW.GLFW_MOUSE_BUTTON_4` →
> `InputConstants.MOUSE_BUTTON_4`、`import org.lwjgl.glfw.GLFW` → `InputConstants`，
> 并且同样去掉了 `isKeyDown` 的 Window 参数（含 `isKeyDown(window, code)` → `isKeyDown(code)`
> 与跨行调用）。40 个文件的 GLFW 导入、`InputConstants` 导入的增删数量也都对得上。

### 完整的 import 搬迁映射（全部出自上游 diff）

| 26.2 | 26.3 |
| --- | --- |
| `com.mojang.blaze3d.PrimitiveTopology` | `com.mojang.renderpearl.api.pipeline.PrimitiveTopology` |
| `com.mojang.blaze3d.pipeline.BlendFunction` | `com.mojang.renderpearl.api.pipeline.BlendFunction` |
| `com.mojang.blaze3d.pipeline.ColorTargetState` | `…renderpearl.api.pipeline.ColorTargetState` |
| `com.mojang.blaze3d.pipeline.DepthStencilState` | `…renderpearl.api.pipeline.DepthStencilState` |
| `com.mojang.blaze3d.pipeline.RenderPipeline`（含嵌套 `.Snippet`） | `…renderpearl.api.pipeline.RenderPipeline` |
| `com.mojang.blaze3d.buffers.GpuBuffer` | `com.mojang.renderpearl.api.buffers.GpuBuffer` |
| `com.mojang.blaze3d.buffers.GpuBufferSlice` | `com.mojang.renderpearl.api.buffers.GpuBufferSlice` |
| `com.mojang.blaze3d.systems.RenderPass` | `com.mojang.renderpearl.api.commands.RenderPass` |
| `com.mojang.blaze3d.vertex.VertexFormat` | `com.mojang.renderpearl.api.vertex.VertexFormat` |
| `com.mojang.blaze3d.opengl.GlConst` | `com.mojang.renderpearl.backend.opengl.GlConst` |
| `com.mojang.blaze3d.opengl.GlStateManager` | `com.mojang.renderpearl.backend.opengl.GlStateManager` |

**没有搬迁、不要动的**：`com.mojang.blaze3d.pipeline.RenderTarget`、
`com.mojang.blaze3d.systems.RenderSystem`、`com.mojang.blaze3d.vertex.*`（`BufferBuilder`、
`ByteBufferBuilder`、`MeshData`、`PoseStack`、`VertexConsumer`、`DefaultVertexFormat`）、
`com.mojang.blaze3d.platform.*`（含 `InputConstants`、`Window`）、`com.mojang.blaze3d.Blaze3D`。

### 其他代码级迁移（同样出自上游 diff）

| 26.2 | 26.3 |
| --- | --- |
| `OutputTarget.ITEM_ENTITY_TARGET.getRenderTarget()` | `WurstClient.MC.gameRenderer.mainRenderTarget()` |
| `RenderSetup.builder(p).setOutputTarget(OutputTarget.ITEM_ENTITY_TARGET)` | 删掉该调用（`RenderSetup` 本身没变） |
| `renderPass.setPipeline(pipeline)` | `renderPass.setPipeline(RenderSystem.getCompiledPipeline(pipeline))`（`setPipeline` 只接受 `CompiledRenderPipeline`） |
| `stack.getItem() instanceof AxeItem` | `stack.is(ItemTags.AXES)` |
| `stack.getItem() instanceof HoeItem` | `stack.is(ItemTags.HOES)` |
| `EnderMan` | `Enderman`（**仅拼写**，包不变） |
| `net.minecraft.client.renderer.feature.NameTagFeatureRenderer` | `…feature.TextFeatureRenderer` |
| `net.minecraft.client.renderer.ItemInHandRenderer` | `net.minecraft.client.renderer.FirstPersonHandsAndItemsRenderer`（但方法签名大改，见下） |
| `LivingEntity.swing(hand)` / `ServerboundSwingPacket` | 均已移除，改为 `swing(hand, SwingAnimation, boolean)` |
| `ServerboundSwingPacket` | **不存在**（26.3 只剩 `ClientboundSwingAnimationPacket`） |

### 实施脚本与实测结果

`D:\WurstB\_fix_263_render.py` 把上表固化成一个可重跑、可审计的脚本（导入规则用 `\b` 边界，
避免 `GpuBuffer` 吃掉 `GpuBufferSlice`、`VertexFormat` 吃掉 `VertexFormatElement`）。它一次改动
18 个文件，并自带「旧包引用残留」检查（结果 0）。

四轮探针的错误数变化（⚠️ **这些数字不可比，见「关于错误计数的更正」**；只能当定性信号）：

| 轮次 | 改动 | 报告的错误数（不可靠） |
| --- | --- | ---: |
| 1 | 仅脚手架 | 104 |
| 2 | 输入层移植（GLFW → InputConstants） | 64 |
| 3 | 套用上游的 import 搬迁映射 + 代码级替换 | 15 |
| 4 | 补 5 处机械项（`Enderman`、`AxeItem`/`HoeItem`、`setPipeline`、3 个无用 import） | **10** |

**从 104 降到 10，且每一轮都零新增错误。** 上游的迁移表贡献了其中最大的一跳（64→15）。

## NeoForge / Fabric 两树的移植

Forge 树通过之后，另两个树按 `docs/PORTING-NEW-VERSIONS.md` 记录的做法推进：**同 MC 版本的兄弟加载器
之间可以直接搬运**，因为三棵树的差异被限制在一组有界的「加载器胶水」文件里。

### 分区（不靠猜，靠 diff）

先算出两个集合，结果见 `D:\WurstB\_prop_copy.txt` 与 `_prop_manual.txt`：

- **Forge 树这次改了多少**：92 个文件（另加新增的 `MouseUtils.java`）；
- **三树之间本来就不同的文件**（两两 diff 的并集）：31 个；
- **两者相交**——既被这次移植改动、又是加载器专属的：**9 个**，其中 `META-INF/mods.toml` 与
  `wurst.mixins.json` 是 Forge 专属资源（另两个加载器有自己的对应文件），所以真正要手工处理的
  java 文件是 **7 个**：`WurstShaderPipelines`、`AltManagerScreen`、`KillauraHack`、
  `MobSpawnEspHack`、`MultiAuraHack`、`RenderUtils`、`util/chunk/ChunkUtils`。
- **剩下 82 个**可以按字节复制（含 4 个翻译文件）。

一个坑：`META-INF/jarjar/` 在 diff 里被报为「整个目录只在 Forge 存在」，是按**目录**粒度的差异，
所以目录里的 `metadata.json` 混进了复制集。它是 Forge JarJar 专属，已从另两个树删除
（Fabric 用 `fabric.mod.json` 的 `include`，NeoForge 的 jarJar 元数据由 MDG 生成）。

### 做法与结果

1. 复制 82 个跨加载器相同的文件，逐文件 md5 校验三树一致（校验脚本见 `_propagate_263.py`）；
2. 对 7 个加载器相关文件跑通用的三个修复脚本（`_fix_263_input.py` / `_fix_263_iskeydown.py` /
   `_fix_263_render.py`）——它们对已修文件是幂等的，只修这几个未改的；
3. 剩下的 3 处按需手工修（两个树相同）：`KillauraHack` 的 `AxeItem` → `ItemTags.AXES`（2 处）、
   `MultiAuraHack` 删除无用 `AxeItem` 导入并把 `invulnerableTime` 换成 `getInvulnerableTime()`、
   `ChunkUtils` 的 `p.getX()/p.getZ()` → `p.x()/p.z()`；
4. 两个树的 mixin 配置各加一行 `freecam.ItemInHandRendererMixin`（用文本插入，保持最小 diff——
   先试过 JSON 往返，那会把 Fabric 的 4 空格缩进整文件重排，已回退）。

**结果**：

| 树 | `compileJava` | 产物 | 备注 |
| --- | --- | --- | --- |
| `versions/26.3`（Forge） | ✅ `BUILD SUCCESSFUL` | 793 源文件 → **1037** class | 见上文 |
| `fabric/versions/26.3` | ✅ `BUILD SUCCESSFUL`（4m 15s） | 794 → **1040** class | 首次构建含 Loom 准备 |
| `neoforge/versions/26.3` | ✅ `BUILD SUCCESSFUL`（14m 52s） | 794 → **1039** class | 首次构建含 NeoForm；需先升 MDG，见下 |

（class 数不同是因为加载器专属文件本身有差异；三者的 `src` 里 java 文件数只差 1 个。）

### NeoForge 的阻塞点与修法（NeoForm，与本站移植无关）

第一次构建在 **10m 59s 后失败**，但**编译错误为 0**——失败发生在 `:createMinecraftArtifacts`，
即 NeoGradle 用 NeoForm **重编译 NeoForge 自己打过补丁的 MC 源码**时：

```
*** Started working on recompile
 Compiling 7301 source files
 ERROR Line: 44, <..net.minecraft.core.HolderSet$1>..contents()....net.minecraft.core.HolderSet.Named..contents()
 ............; ...public in /net/minecraft/core/HolderSet.java
NodeExecutionException: Node action for recompile failed
```

这一步在**我们的代码参与之前**，所以与本次移植无关。原因是**工具链太旧**：本工程沿用了 26.2 的
**ModDevGradle 2.0.143**，它带的源码转换器是 `net.neoforged.jst:jst-cli-bundle:2.0.10`，处理不了
26.3 的源码。把 MDG 升到当前最新的 **2.0.147** 后，转换器随之升到 **2.0.11**，
`recompile` 即通过（14m 52s，0 错误）。

> **这条值得记住**：NeoForge 侧若在 `createMinecraftArtifacts` / NeoForm 的 `recompile` 上失败、
> 而自己的 `compileJava` 一个错都没有，先查 MDG 版本，而不是查代码。NeoForge 26.3 目前只有 beta
> （`26.3.0.16-beta` 为最新），工具链比对不齐是常态。

**另有一个值得记录的事实**：**上游 Wurst7 的 `26.3` 分支已经改成 Fabric 单加载器**
（`gradle.properties` 里 `mod_loader=Fabric`、插件只有 `net.fabricmc.fabric-loom`）。
也就是说 NeoForge 26.3 这一侧**没有上游参照**，只能自己趟——本次是靠 MDG 版本对齐解决的。

## 移植结果与 API 差异清单

`versions/26.3` 相对供体 `versions/26.2` 共改动 **93 项**（86 个 java 文件、7 个资源/元数据，
另新增 `util/MouseUtils.java`）。按 API 类别汇总：

| 26.2 | 26.3 | 规模 |
| --- | --- | ---: |
| `org.lwjgl.glfw.GLFW.*` | `InputConstants.*`（SDL scancode；3 处常量改名） | 40 文件 / 117 处 |
| `InputConstants.isKeyDown(Window, k)` | `isKeyDown(k)` | 9 处 |
| `GLFW.glfwGetMouseButton(h, b)` | `SDL_GetMouseState` 位掩码（新增 `MouseUtils`） | 2 处 |
| `com.mojang.blaze3d.{pipeline,buffers,systems,vertex,opengl}` 诸类 | `com.mojang.renderpearl.{api.pipeline,api.buffers,api.commands,api.vertex,backend.opengl}` | 18 文件 |
| `OutputTarget.ITEM_ENTITY_TARGET.getRenderTarget()` | `MC.gameRenderer.mainRenderTarget()` | 2 处 |
| `RenderSetup.builder(…).setOutputTarget(…)` | 删除该调用（`RenderSetup` 本身未变） | 1 处 |
| `renderPass.setPipeline(p)` | `setPipeline(RenderSystem.getCompiledPipeline(p))` | 1 处 |
| `stack.getItem() instanceof AxeItem` / `HoeItem` | `stack.is(ItemTags.AXES)` / `ItemTags.HOES` | 1 处（另 3 个无用 import） |
| `EnderMan` | `Enderman`（仅拼写） | 1 处 |
| `LivingEntity.swing(hand)` | `swing(hand, SwingAnimation, sendToSwingingEntity)` | 8 处 |
| `ServerboundSwingPacket` | **已删除** → `SwingHand.SERVER` 变空操作 | 1 文件 |
| `Util.getPlatform().openFile(f.toFile())` | `Blaze3D.openPath(path)` | 5 处 |
| `Util.getPlatform().openUri(str)` | `Blaze3D.openUri(URI.create(str))`（参数类型变成 `URI`） | 2 处 |
| `BlockState.blocksMotion()` | `state.is(BlockTags.BLOCKS_MOTION)` | 3 处 |
| `PoseStack.mulPose(Quaternionf)` | `PoseStack.rotate(Quaternionf)` | 2 处 |
| `KeyEvent.scancode()` | `KeyEvent.keycode()` | 1 处 |
| `InputConstants.Type.KEYSYM` | `Type.KEYBOARD`（枚举只剩 `KEYBOARD`/`MOUSE`） | 1 处 |
| `MC.gameRenderer.itemInHandRenderer.itemUsed(h)` | `MC.player.itemUsed(h)` | 2 处 |
| `GameRenderer.currentPostEffect()` / `clearPostEffect()` | `Player.getActivePostEffects()`；本工程该 hack 早已 stub，故直接删掉失效调用 | 4 处 |
| `PackResources` + `profile.open()` | `PackMetadataResources` + `profile.openMetadata()` | 1 文件 |
| `isValidBonemealTarget(l, p, s)` / `isBonemealSuccess(l, r, p, s)` | 各多一个 `BonemealSource.INTERACTION` 参数 | 5 处 |
| `ClientboundLevelChunkWithLightPacket.getX()/getZ()` | `x()` / `z()`（该类变成 record） | 1 处 |
| `new BlockPos(BlockPos)`（`PathPos extends BlockPos`） | `super(x, y, z)`（只剩 `BlockPos(int,int,int)`） | 1 处 |
| `new OptionsScreen(parent, options, false)` | 去掉第 3 个参数 | 1 处 |
| `Entity.invulnerableTime`（改为 private） | `getInvulnerableTime()` | 1 处 |
| `ItemInHandRenderer` | `FirstPersonHandsAndItemsRenderer`（`submitArmWithItem` 签名大改） | 3 文件 |
| `NameTagFeatureRenderer` | `TextFeatureRenderer`（`Submit` 变为 record 并实现 `TranslucentSubmit`） | 1 文件 |
| `PreparedRenderType.drawFromBuffer(info)` | `drawFromBuffer(info, renderPass)`（须新建 RenderPass） | 1 文件 |

## 关于错误计数的更正

本文中间版本记录过「104 → 64 → 15 → 10 → 6 → 1」这样的逐轮错误数。**那套数字不可比，
而且第 6 轮那个「只剩 1 个错误」是假象**，不应作为进度数据引用。原因有二：

1. **Gradle 增量编译**：只有受影响的文件（及其依赖者）会被重新编译，未重编文件里的既有错误
   根本不会出现在统计里。
2. **javac 的归因屏蔽**（更主要）：当某些文件存在**无法解析的 import** 时——比如
   `org.lwjgl.glfw` 整包消失、`ServerboundSwingPacket` 被删除——依赖这些文件的类会连带
   无法完成归因，**它们自己的错误也就不被报出**。第 6 轮的现象正是如此：`SwingHandSetting`
   的第 12 行 import 一坏，javac 就大面积沉默了。同理，第 1 轮的 104 也是**低估**（当时 40 个
   输入层文件全带着无法解析的 `glfw` import）。

**正确的做法**：删掉 `build/classes` 与 `build/tmp/compileJava` 后跑**全量**编译，并确保没有
这类「毒化」的 import 错误。本节的最终验收（0 错误 / 1037 个 class / 793 个源文件）就是这么做的。
早先那套递减数字只能当「错误在减少」的定性信号。

## 尚未完成（待办）

1. **~~最后 10 个错误（4 个 fork 特有文件）~~ —— 已完成，`compileJava` 通过。**
   四个文件都不是「改个类名」能了事的，逐项处理如下：

   | 文件 | 处理 |
   | --- | --- |
   | `settings/SwingHandSetting.java` | **上游给了答案，并纠正了本文早先的判断**。上游把该设置改名为 `InteractSwingSetting`，且语义**反转**：26.3 里 `CLIENT` = `MC.player.swing(hand, animation, false)`（animation 取自 `getItemInHand(hand).getInteractAnimation()`），`SERVER` = **空操作**（服务端挥手机制没了，唯一能表达的只剩「不播放本地动画」），上游因此**删掉了 `OFF`**。本工程保留 OFF/SERVER/CLIENT 三个值以兼容既有存档，SERVER 与 OFF 都为空操作，并**改写了文案**如实说明（上游 26.3 没有更新自己的文案，那处疏漏没有照抄）。 |
   | `mixin/noshieldoverlay/ItemInHandRendererMixin.java` | 目标类改 `FirstPersonHandsAndItemsRenderer`，并按**字节码**重新定位两个注入点：格挡那处 `ItemStack.getUseAnimation()` 仍在（全方法仅 1 次调用，在通用分支的 `switch` 之前）；不格挡那处的旧锚点 `getSwingAnimation()` **已彻底不再被调用**，改为 `AvatarRenderState.currentSwing` 字段读取（全方法仅 1 次，且正好在 `applyItemArmTransform` 之后，与原锚点位置语义一致）。 |
   | `mixin/HeldItemRendererMixin.java` | 三个 `@ModifyExpressionValue` 从「活体实体方法调用」改为「render-state 字段读取」，并按字节码确定 ordinal——方法里有**两个结构相同的「正在使用物品」判断**，第一个属于**弩**分支，只有第二个才是含 `case BLOCK` 的通用分支，所以是 `isUsingItem` #1、`useItemHand` #1、`useItemRemainingTicks` #2（它的 #0/#1 分别是弩 guard 与一处时长计算）。若不分 ordinal，弩会被错误地渲染成「正在使用」。 |
   | `mixin/SubmitNodeCollectionMixin.java` | 按上游的 26.3 迁移合并（`NameTagFeatureRenderer` → `TextFeatureRenderer`、普通路径改注入私有的 `submitNameTagPart`、两个 shadow 字段合成 `seeThrough`），并保留了本仓库比上游多出的 `wrapLabelScale` / `forceNotSneaking` 两处自有逻辑。已核对后者语义未变：`submitNameTag` 的布尔参数在 26.2 与 26.3 都由 `EntityRenderer` 传 `!state.isDiscrete`，所以「强制视为未潜行」仍然成立。另外给 `swapSeeThroughNameTagSubmit` 的强转加了 `instanceof` 兜底（上游是裸强转），渲染路径上宁可回退到原版也不抛 ClassCastException。 |

   **顺带修掉一个 fork 的既有缺陷**：`mixin/freecam/ItemInHandRendererMixin.java`（让 Freecam 的「隐藏手」设置生效）
   **从未在 `wurst.mixins.json` 里注册**，`FreecamHack.shouldHideHand()` 的唯一调用者就是它——
   也就是说这个设置在 fork 里一直不起作用。本次把它移植到新类并**注册**了。
   （`RemoteViewHack` 在本仓库没有对应的 `shouldHideHand()`，所以没有照上游那样一并处理——
   那属于新增功能，不是移植。）

2. **~~NeoForge 与 Fabric 两个树还没做任何移植~~ —— 已完成，两树 `compileJava` 均通过。**
   做法与结果见「NeoForge / Fabric 两树的移植」一节。其中 NeoForge 侧额外需要把
   ModDevGradle 从 2.0.143 升到 **2.0.147**，否则 NeoForm 的 `recompile` 会失败
   （与本工程代码无关）。
3. **`pack.mcmeta` 的 `pack_format` 仍是 34**：见上文「刻意没有改的东西」。
4. **文档矩阵与计数未更新**：`README.md`、`README.en.md`、`CHANGELOG.md`、`PROJECT_INDEX.md`、
   `docs/RELEASE.md`、`docs/PORTING-NEW-VERSIONS.md` 里的版本矩阵、徽章（`1.20.1~26.2`、
   `Gradle_projects-64`）、工程计数（19/21/21、64 个工程）都还是 26.2 时代的数字。
   这批改动与「26.3 是否已编译通过、是否发布」耦合，宜在编译验收后再一次性更新，避免文档
   先于事实宣布 26.3 可用。
5. **三个新工程的 `gradle-wrapper.jar` 需要 `git add -f`**，否则会被 `.gitignore` 的 `*.jar`
   吞掉（根级 `!gradle/wrapper/*.jar` 否正带 `/`，只锚定在仓库根，对子目录无效）。当前实测：
   `neoforge/versions/26.3/gradle/wrapper/gradle-wrapper.jar` **可正常跟踪**（它那份 `.gitignore`
   自己带了否正），而 `versions/26.3/` 与 `fabric/versions/26.3/` 的**被忽略**。
   仓库既有的做法是靠 `scripts/doctor.ps1` 检测 + `scripts/seed-gradle-wrapper.ps1` 播种来兜底
   （目前 90 个工程里只有 8 个 wrapper jar 是强制跟踪的），所以这不是新问题，但新工程要落库时
   需要有人决定：跟随既有做法（交给 seeding），还是 `git add -f`。
6. **发布期校验脚本未加 26.3**，且**刻意如此**——它们是发布期检查器，现在加会误报：
   - `tmp-recon/check-release.py`：比对 **v1.5.0 GitHub release** 的资产清单，加 26.3 会报「缺失」。
   - `tmp-recon/verify-branches.py`：遍历 `origin/<branch>`，而 26.3 分支尚不存在。
   - `scripts/sync-version-branches.py` 的 `BRANCHES` 与 `.github/workflows/sync-version-branches.yml`
     的注释：`26.3` 分支**尚未创建**。已确认 `branch_keeps()` 不会把 `versions/26.3` 泄漏进
     其他版本的快照分支（`parts[1] == v` 判定），所以暂时不加是安全的。
   这几项应在 26.3 **编译通过并决定发布**时一并补上。

## 输入层移植（已完成，仅 Forge 树）

26.3 的 `InputConstants` 与 GLFW 的常量**名字一一对应、数值完全不同**（见上表），因此这一步
不是纯文本替换，必须逐处确认数值的来源与消费方属于同一套编码。已核对：

- 键码来源是 `InputConstants.Type.MOUSE.getOrCreate(...)`、`MouseButtonInfo.button()`、
  `MouseEvent.key()` 等 26.3 自己的输入通道，全部是 SDL 编码；
- 26.3 的 `MouseHandler` 就是把 `rawButtonInfo.button()` 与 `1/2/3` 比较（左/中/右），
  与 `InputConstants` 的 `addKey(MOUSE, "key.mouse.left", 1)` 一致；
- 因此 7 处 `context.button() == GLFW.GLFW_MOUSE_BUTTON_4`（GLFW 值 **3**）改成
  `InputConstants.MOUSE_BUTTON_4`（SDL 值 **4**）指向的是**同一个物理键**，是正确的。

### 做法

| 脚本 | 作用 | 结果 |
| --- | --- | --- |
| `D:\WurstB\_fix_263_input.py` | 40 个 GLFW 导入文件的常量替换 + 导入行处理 | 常量 **117** 处；删除 GLFW 导入 **9** 个文件（这些已导入 `InputConstants`）、替换 **31** 个 |
| `D:\WurstB\_fix_263_iskeydown.py` | 去掉 `isKeyDown` 的 Window 参数 | **9** 处（含 1 处不在 GLFW 文件集内，见下） |

导入行要分两种处理：9 个文件本来就 `import InputConstants`，删掉 GLFW 导入即可；另 31 个则替换。

**`isKeyDown` 的签名变化与 GLFW 迁移无关**，会影响任何文件——`ZoomOtf.java` 早就导入了
`InputConstants`、从未用过 GLFW，但同样调用了双参形式。所以这一步扫描全部 java 文件而不是
40 个 GLFW 文件，单列一个脚本。这是第一版脚本漏掉它的原因。

### 那 2 处原生鼠标调用

`KeyBindingMixin.wurst_resetPressedState()` 与 `KeyMappingMixin.wurst_isActuallyDown()` 原本调用
`GLFW.glfwGetMouseButton(handle, code) == 1`。SDL 没有逐键查询，只有
`SDL_GetMouseState` 返回**位掩码**（SDL 按钮编号 1..8 → 位 `n-1`），所以新增
`net/wurstclient/util/MouseUtils.java` 封装这一处原生调用，两个混入共用。

选择直接查 SDL 而不是用 `MouseHandler.isLeftPressed()/isMiddlePressed()/isRightPressed()`：
后者只覆盖三个主键，而这两处的 `code` 来自用户绑定的 `KeyMapping`，鼠标 4/5 侧键是常见绑定，
用后者会造成**静默的能力退化**。

一个实测坑：`SDL_GetMouseState(FloatBuffer, FloatBuffer)` 对两个出参都执行
`Checks.checkSafe`，**传 `null` 会抛 NPE**（`Checks.CHECKS` 默认开启）。所以用
`MemoryStack` 在栈上取临时缓冲，既避开 NPE 也避免每 tick 堆分配——这两处是从 tick 监听器
调用的。

`resetPressedState()` 是活路径（AutoWalk、Sneak、SafeWalk、InvWalk、NukerLegit 等 18 处调用），
所以这里不能降级处理。

### 验证结果

`versions/26.3` 重跑 `compileJava`（⚠️ **这两个数字同样受「关于错误计数的更正」影响**）：

| | 第 1 轮（未移植输入层） | 第 2 轮（输入层已移植） |
| --- | ---: | ---: |
| 错误数 | 104 | **64** |
| 受影响文件 | 65 | **25** |
| 含 `org.lwjgl.glfw` 错误 | 40 | **0** |
| 构建耗时 | 23m 25s | 32s（缓存已热） |

错误数**正好减少 40**（与 40 处 `程序包 org.lwjgl.glfw 不存在` 精确吻合），**没有引入任何新错误**——
即 117 处常量替换、8 处 `isKeyDown`、ZoomOtf 与新的 SDL 鼠标查询全部编译通过，
且 `org.lwjgl:lwjgl-sdl` 确实在 Forge 的编译类路径上。

**尚未做**：NeoForge 与 Fabric 两个树还没跑这两个脚本。它们的 26.2 树与 Forge 只差 5 个文件，
预期结果相同，但未经验证。

## Forge 26.3 编译探针结果（第 1 轮：移植输入层之前）

命令：`versions/26.3` 下 `gradlew.bat compileJava --no-configuration-cache --no-daemon`（JDK 25）。
**`BUILD FAILED in 23m 25s`，`Task :compileJava` 报 104 个错误**。

> 这一节的 104 个错误是**输入层移植之前**的状态。其中 40 个（A 类）已在下一轮修掉，
> 当前剩余 **64 个**，见上一节。下面的分节保留 A/B/C 三类是为了记录完整的问题面。

失败点是**项目源码编译**，不是 MC 侧：ForgeGradle 的下载 / 反编译 / 打补丁 / 重编译
（`Minecraft Maven has finished, took 22:01`）全部成功。所以工程脚手架、版本锚点、
Baritone 依赖解析**都是通的**，卡住的是 26.3 的 API 变化。

错误集中在 65 个文件，且高度集中于渲染基础设施——不是均匀撒开：

| 错误数 | 文件 | 性质 |
| ---: | --- | --- |
| 14 | `WurstShaderPipelines.java` | 渲染管线注册 |
| 12 | `util/EasyVertexBuffer.java` | 底层 GPU 缓冲 |
| 6 | `util/chunk/ChunkVertexBufferCoordinator.java` | 区块顶点缓冲 |
| 5 | `mixin/noshieldoverlay/ItemInHandRendererMixin.java` | 混入目标类缺失 |
| 3 | `mixin/SubmitNodeCollectionMixin.java` | 渲染状态提交 |
| 2 各 | `util/RenderUtils.java`、`SearchHack.java`、`BaritoneLevelRendererMixin.java`、`HeldItemRendererMixin.java`、`LevelRendererMixin.java` | |
| 1 各 | 其余 40+ 个文件（含全部 `org.lwjgl.glfw` 引用者） | 多为单个 import 失败 |

### 按类归并

**A. 输入层：SDL3 移除 glfw —— 40 处**

```
40×  程序包 org.lwjgl.glfw 不存在
```

**B. 渲染层：`blaze3d` → `renderpearl` 搬迁 —— 约 30 处**

| 找不到的符号 | 出现 | 在 26.2 的原位置 | 26.3 的新位置 |
| --- | ---: | --- | --- |
| `PrimitiveTopology` | 14 | `com.mojang.blaze3d.pipeline` | `com.mojang.renderpearl.api.pipeline` |
| `RenderPipeline` | 9 | `com.mojang.blaze3d.pipeline` | `com.mojang.renderpearl.api.pipeline` |
| `VertexFormat` | 5 | `com.mojang.blaze3d.vertex` | 待定（`renderpearl.api.vertex` 下只有 `VertexFormatElement`） |
| `GpuBufferSlice` | 5 | `com.mojang.blaze3d.buffers` | `com.mojang.renderpearl.api.buffers` |
| `GpuBuffer` | 2 | `com.mojang.blaze3d.buffers` | `com.mojang.renderpearl.api.buffers` |
| `RenderPass` | 1 | `com.mojang.blaze3d.systems` | `com.mojang.renderpearl.api.commands` |
| `BlendFunction` / `ColorTargetState` / `DepthStencilState` | 各 1 | `com.mojang.blaze3d.pipeline` | `com.mojang.renderpearl.api.pipeline` |
| `Snippet` | 1 | `blaze3d` 内部 | 待定 |
| `OutputTarget` | 2 | `net.minecraft.client.renderer.rendertype` | **无对应类** |
| 程序包 `com.mojang.blaze3d.opengl` | 5 | 同名 | `com.mojang.renderpearl.backend.opengl` |

**C. 与 SDL3 / OIT 无关的常规 MC API 漂移 —— 约 15 处**

| 找不到的符号 | 出现 | 原位置 |
| --- | ---: | --- |
| `ItemInHandRenderer` | 7 | `net.minecraft.client.renderer` |
| `AxeItem` | 3 | `net.minecraft.world.item` |
| `NameTagFeatureRenderer` | 3 | `net.minecraft.client.renderer.feature` |
| `HoeItem` | 1 | `net.minecraft.world.item` |
| `EnderMan` | 1 | `net.minecraft.world.entity.monster` |
| `ServerboundSwingPacket` | 1 | `net.minecraft.network.protocol.game` |

这一类是每一轮移植都会有的重命名 / 移动，与 26.3 那两个底层改动无关，修法按往常处理。

### 探针结论对工作量的意义

- **脚手架本身是成功的**：22 分钟的 MC 侧准备全绿，104 个错误**全部**来自 26.3 的 API 变化，
  没有任何一个来自工程配置（版本锚点、依赖坐标、元数据、wrapper）。
- **工作量集中在少数文件**：`WurstShaderPipelines` + `EasyVertexBuffer` + `ChunkVertexBufferCoordinator`
  三个文件占 32 个错误，是渲染层的主战场。这两个数字在输入层修完后分别是 32/64 与 3/25。
- **输入层（A 类，40 处）已修完**，过程见上一节。它是机械替换，但**不是保值替换**——
  常量数值从 GLFW keycode 变成 SDL scancode，必须先确认每一处的数值来源与消费方同属一套编码。
- **`WurstRenderLayers` 只报 1 个错，但实际是重灾区**：错误列表里它只在第 11 行
  （`import ...rendertype.LayeringTransform`）报了一次，因为 javac 对同一文件里连续失败的
  import 只报一次。逐个数它的引用：`RenderSetup` **11 处**、`OutputTarget` 3 处、
  `LayeringTransform` 3 处，**全都在 26.3 里不存在**。所以「错误数」严重低估了这个文件，
  它是渲染层真正要重写的那个（`WurstRenderLayers` 是 19 个 hack 的渲染入口）。
- 另外三个工程（NeoForge / Fabric）预期错误形态相同——26.2 的三个树整树只差 5 个文件，
  但 26.3 的 `renderpearl` 搬迁是否会因加载器而异（例如 NeoForge 的
  `RegisterRenderPipelinesEvent` 是否还存在）**尚未验证**。
- 本次移植**远大于** 26.1→26.2 那一跳：26.1→26.2 没有整包搬迁这类改动。

## 已知继承债务（供体 26.2 同样存在，非本次引入）

- `versions/26.3/libs/baritone-api-forge-26.1.2.jar`：无人引用的旧版残留。
- 供体的 `CHANGELOG.md` 通篇是 **26.1.2** 时期的内容（26.2 工程当初就原样继承了它）。
  26.3 工程里这个文件已被改写为如实的脚手架说明，26.1.2 的历史记录保留在
  `versions/26.1.2/CHANGELOG.md`。
- `scripts/run-version-tests.ps1` 的 `$JDKs` 里，26.1.2 / 26.2 的兜底路径是
  `C:\Program Files\Java\jdk-25.0.4`，而本机 JDK 25 实际装在
  `C:\Program Files\Microsoft\jdk-25.0.4.101-hotspot`，且 `WURSTBPLUS_JAVA25` 未设置——
  即这两个条目在本机会抛 `JDK not found`（`common.ps1` 走目录扫描所以不受影响）。
  26.3 的条目已写成先探测 Microsoft 路径再兜底，但没有去改既有的两行。

## 相关文件

| 文件 | 作用 |
| --- | --- |
| `D:\WurstB\_port_263.py` | 建三个 26.3 工程的克隆 + 版本锚点改写（含残留扫描） |
| `D:\WurstB\_install_baritone_263.py` | 把 Baritone v1.20.0 装入 `baritone-maven/` 为 `1.20.0-26.3` |
| `D:\WurstB\_fix_263_input.py` | 输入层：40 个文件的 GLFW → InputConstants 常量替换 + 导入行处理 |
| `D:\WurstB\_fix_263_iskeydown.py` | 输入层：去掉 `isKeyDown` 的 Window 参数（扫全部文件） |
| `D:\WurstB\_fix_263_render.py` | 渲染层：把上游的 import 搬迁映射 + 代码级替换应用到本仓库 |
| `D:\WurstB\_fix_263_pass2.py` | 修 7 处 `swing(hand)` 旧签名 + 本次自己引入的 2 个回归（缺 import） |
| `D:\WurstB\_fix_263_pass3.py` | `openPath`/`openUri`、`blocksMotion`、`mulPose`→`rotate`、`scancode`→`keycode` 等 12 个文件 |
| `D:\WurstB\_fix_263_pass4.py` | `PathPos`/`OptionsScreen`/`invulnerableTime`/`ChunkUtils`/pack detector 等最后一批 |
| `D:\WurstB\_propagate_263.py` | 算「跨加载器相同 / 加载器专属」两个集合，并可 `--apply` 复制 |
| `D:\WurstB\_prop_copy.txt` / `_prop_manual.txt` | 上面分区结果（82 个可复制 / 9 个需手工） |
| `D:\WurstB\_probe_263_{neoforge,neoforge2,fabric}.log` | 另两个树的编译日志（neoforge2 是升 MDG 后通过那次） |
| `D:\WurstB\_audit_263_mixins.py` | 混入注册审计（入口↔文件双向核对） |
| `D:\WurstB\_restore_263_src.py` | 只重建 `versions/26.3/src`（第一版脚本正则出错后的恢复手段） |
| `D:\WurstB\_probe_libs.py` / `_agg_263.py` | 查 MC 依赖库清单 / 归并编译错误 |
| `D:\WurstB\_probe_263_forge{,2..7}.log`、`_probe_263_full{,2,3}.log` | 各轮探针原始日志（`_full3` 是最终 0 错误那次） |
| `D:\WurstB\_wurst_up\up-26.2`、`up-26.3` | **上游 Wurst7 两个分支的源码**（本次的权威参照） |
| `D:\WurstB\_wurst_up\up_262_263.diff` | 上游 26.2→26.3 的完整 diff（5029 行），本文件的映射表全部出自这里 |
| `versions/26.3/.../util/MouseUtils.java` | 新增：SDL 鼠标键状态查询（替代 `GLFW.glfwGetMouseButton`） |
| `docs/PORTING-1.21.11-26.2.md` | 上一轮同类移植说明，格式参照 |
| `docs/PORTING-NEW-VERSIONS.md` | 新版本平行移植的通用手册与工具链方案 |

> 上游分支的取得方式（网络偶发失败，需重试）：
> `curl -L -o wurst-26.3.tar.gz https://codeload.github.com/Wurst-Imperium/Wurst7/tar.gz/refs/heads/26.3`
> 上游源码是 **CRLF**，本仓库是 **LF**——比对时务必加 `--strip-trailing-cr`，
> 否则每个文件都会显示「全文不同」（最初 `WurstRenderLayers` 显示差 167 行，实际只差 9 行）。
