# v1.5.1 / Minecraft 26.3 渲染检查

日期：2026-11-12。用户现象：界面黑屏闪动、按钮闪动、文字不显示。

2026-11-13 后续：修复已适配到其余 63 个 v1.5.1 工程，并为全部 66 份 Guard 补充 Minecraft 纹理绑定缓存同步。最终七个 GL 分支共 408 项断言通过，NeoForge 26.3 已重新构建。本文下方保留前轮的测试数和产物哈希，最新结果以[跨版本检查记录](RENDER-v1.5.1-matrix-2026-11-13.md)为准。

## 后续修复与验证（2026-11-12）

用户授权继续修复后，下述三类缺陷已修复并同步到 Forge / NeoForge / Fabric 的 **26.3** 工程。本文后续编号章节保留诊断时的行号与复现证据，不代表修后代码仍有同样问题。

- `NanoVGContext.create()` 在初始化前捕获 GL 状态，初始化失败清理也在同一保护范围内；FBO 创建失败时销毁不完整上下文。
- `GlStateGuard` 保存/恢复单元 0 的 sampler、正反面各自的 stencil 配置、像素上传缓冲与行布局、清屏颜色/模板值。NanoVG 进入绘制前解除遗留 PBO/行步长，并在清理离屏模板缓冲前启用完整写掩码，防止旧模板值裁掉文字或控件。
- `NanoVgBlitter` 每次提交租用独立纹理快照，移除八槽回绕与对象身份帧判断。新增 `NanoVgGuiRenderStateMixin`，在真实 `GuiRenderState.reset()V` 返回后回收该状态的快照；未清空的其它状态继续持有各自快照。`GameRenderer.close()V` 时释放所有快照及 sampler。
- 新增回调已在三端各自的 Mixin 配置中注册；五个修改/新增 Java 类在三端的 SHA-256 分别一致。未把这次适配机械回填到其它 Minecraft 版本。

### 验证结果

| 检查 | 结果与边界 |
| --- | --- |
| 真实 OpenGL + NanoVG 回归 | **62 项断言通过**。直接编译运行生产 Context、字体类、Guard、Blitter 和 reset 回调；Minecraft GPU/GUI 对象及 native loader 为边界替身，**不等于游戏启动或 Mixin 织入验证**。 |
| 字体及状态 | 四个内置字体加载成功；混合文本产生 **1151 个非透明像素**；正常/失败初始化恢复独立 draw/read FBO；sampler、正反面 stencil、PBO、行布局与清屏状态恢复。 |
| 快照生命周期 | 连续 12 次提交读取 `[1,2,3,4,5,6,7,8,9,10,11,12]`；不同状态和窗口缩放不覆盖未消费快照；reset 后复用，重复 reset 不重复出租，关闭后所有纹理恰好释放一次；无 GL 错误。 |
| NeoForge 26.3 完整 `build` | **通过，53 秒**；本轮实际执行 `compileJava`、资源处理、打包。`test NO-SOURCE` 不计项目测试数。使用既有本机 ZIP 写权限探测 agent，限制在任务缓存和该工程目录，不进入产物。 |
| NeoForge 新产物 | 归档检查通过；核对新增 Mixin 注册及五个相关 `.class` 与本次编译输出一致。JAR 为 **52,392,275 字节**，SHA-256：`7fe599640b472d3c08368d2a4fe4a81d2da23529f542138fdadb58a6fb75b290`。 |
| Fabric 26.3 `compileJava` | 未通过。既有 Loom 缓存问题仍使 `net.minecraft.client` 等游戏类缺失，未取得完整构建结论。未用外加官方 client JAR 冒充正常构建通过。 |
| Forge 26.3 `compileJava` | 未进入源码编译。Mavenizer 的 Java 8 Access Transformer 子进程仍遇到 `ReadOnlyFileSystemException`，不能视为源码编译失败或通过。 |
| 新 Mixin 目标 | 对真实 26.3 client JAR 核对 `GuiRenderState.reset()V` 与 `GameRenderer.close()V`；三端配置均注册回调。 |
| 全仓崩溃回归闸门 | **67 工程通过**，没有为本次修复放宽原有检查。 |

NeoForge 新产物位于 `neoforge/versions/26.3/build/libs/WurstB+ Plus-v1.5.1-NeoForge-26.3.jar`。没有安装、发布或替换用户游戏中的模组。

可重复执行的回归入口：

```powershell
python scripts/run-render263-tests.py --java-home <JDK25目录>
```

测试需要桌面 OpenGL 会话，创建隐藏 GLFW 窗口。默认使用可公开获取的 LWJGL/NanoVG **3.3.3**；26.3 工程仍使用原有 **3.4.3**，生产依赖未因测试改变。3.4.3 GLFW 诊断依赖下载返回 404，因此没有宣称已完成 3.4.3 的独立 GL 实测。测试用原生库加载器替身让 LWJGL 从测试 classpath 加载同版本 natives，没有执行生产 native 提取逻辑。

本轮结果在 `build/render263-tests/fabric-versions-26.3/result.txt`；构建日志另存为 `.dsh-tmp/render263-{fabric,neoforge,forge}-build.log`。需要在可正常构建的游戏环境中，继续验收标题/暂停/设置/ClickGUI 的持续帧画面、窗口缩放和光影兼容性，才能确认用户报告的黑屏闪动整体消失。

## 范围与结论

检查当前工作区的 `fabric/versions/26.3`、`neoforge/versions/26.3`、`versions/26.3`。三个工程的 `NanoVGContext`、`GlStateGuard`、`NanoVgBlitter` 各自跨加载器哈希一致。根目录 Forge 1.20.1 的 v1.6.0 不在本次范围。

发现三类可举证的渲染缺陷，均涉及 NanoVG 与 Minecraft GUI 管线交接。它们是当前源码缺陷；尚不能认定全部都是此次用户画面异常的直接原因。没有取得用户使用的加载器、JAR、日志、显卡及光影/渲染模组信息，也没有在游戏中复现用户现象。

历史 `v1.5.1` 标签中不存在此次检查的 `fabric/versions/26.3/.../NanoVgBlitter.java` 路径。因此本文的“v1.5.1”指当前版本号仍为 1.5.1 的源码工程，不把当前缺陷直接归因到该历史标签或用户手上的发布包。

初始诊断阶段只检查、运行探针并记录结果；后续源码修复与构建结果见上节。既有未提交修改予以保留。

## 1. 首次初始化发生在 GL 状态保护之外（P1）

位置（以下行号以 Fabric 26.3 为例）：

- `hud2/nanovg/NanoVGUI.java:82` 先执行 `NVG.create()`，第 85 行才捕获 `GlStateGuard`。
- `gui/ButtonStyleRenderer.java:66` 也直接调用 `create()`，没有包住初始化过程。
- `hud2/HudManager.java:159` 同样先创建上下文，后捕获状态。
- `hud2/nanovg/NanoVGContext.java:150` 调用 `ensureFramebuffer()`；第 329 行直接绑定新离屏 framebuffer，没有恢复进入函数前的 draw/read framebuffer。

首次创建成功后，调用方才捕获状态，此时保存的是 NanoVG 的 framebuffer。后续 guard 退出时会把这个离屏目标重新绑定回来，而不是恢复进入整个调用前的 Minecraft 目标。即使后续帧有其它代码重新绑定主目标，首次创建这条路径仍违反状态隔离约定；可能影响首次打开界面和初始化后的绘制。这里不能据此断言每一帧都会黑屏。

验证方式：追踪真实生产源码的调用顺序与所有 return 分支。此项没有执行完整 `NanoVGContext` 的游戏/原生初始化复现。

修复方向：让整个上下文初始化及失败清理处于状态保护范围，覆盖 GUI、按钮和 HUD 的所有入口；不能只保护 `beginFrame()` 之后的绘制。

## 2. 状态保护器漏恢复 sampler，并破坏背面 stencil 状态（P1/P2）

位置：

- `hud2/nanovg/NanoVGContext.java:360` 每次 `beginFrame()` 调用 `GL33.glBindSampler(0, 0)`。
- `hud2/nanovg/GlStateGuard.java:177` 起的恢复代码没有保存/恢复 `GL_SAMPLER_BINDING`。
- 同文件第 217–219 行使用 `glStencilFunc/glStencilMask/glStencilOp`，把捕获的正面 stencil 状态同时写到正面和背面；没有单独保存背面状态。

Sampler 是纹理单元状态，恢复纹理对象绑定不会恢复 sampler。若调用前 Minecraft/其它渲染器在单元 0 上绑定了 sampler，NanoVG 退出后它会变成 0。后续采样过滤、寻址等行为可能改变；这与按钮/文字纹理异常有关联，但需要游戏日志或画面复现确认实际影响。

背面 stencil 问题只在进入时正反面配置不同的情况下显现；不能把它当成所有普通菜单闪动的必然原因。

真实 OpenGL 探针执行未经修改的生产 `GlStateGuard`：

```text
GL=4.6.0 - Build 32.0.101.7084 / Intel(R) RaptorLake-S Mobile Graphics Controller
sampler expected=1 actual=0
backStencil expected=514 actual=519
glError=0
```

`514` 为 `GL_EQUAL`，`519` 为 `GL_ALWAYS`。探针先设置独立的正反面状态，再在 guard 内修改背面状态，退出后背面被覆盖成正面值。Sampler 则在 guard 内执行生产 `beginFrame()` 相同的解绑调用。

修复方向：保存/恢复单元 0 的 sampler；用 separate 接口分别恢复正反面 stencil 函数、掩码和操作。还应检查 NanoVG 上传字形时触及的像素上传状态，而不是假设当前 guard 已覆盖全部 GL 状态。

## 3. 延迟合成纹理只用 8 个槽，帧边界判断也不成立（P1）

位置：`hud2/nanovg/NanoVgBlitter.java:49–61`。

现实现把 `GuiRenderState` 对象身份变化当成新帧，只在变化时把 `nextSlot` 归零；每次提交后用 `(nextSlot + 1) % 8` 回绕。一帧内超过 8 次提交时，后面的复制会覆盖之前 GUI 元素仍引用的纹理。第 1 个元素本来应显示 pass 1，最终会显示 pass 9 的图像，可能表现为内容消失、重复、遮挡或闪动。

执行未经修改的生产 `NanoVgBlitter`，用真实 GL framebuffer、真实纹理复制和像素读取，连续提交红色通道标记 1–9，结果：

```text
expected:   [1, 2, 3, 4, 5, 6, 7, 8, 9]
ninePasses: [9, 2, 3, 4, 5, 6, 7, 8, 9]
glError=0
```

Minecraft 的 GPU/GUI 对象在该探针中是保留纹理引用的最小替身，不代表已在游戏中复现了九次提交的具体菜单。

另外，直接检查本地真实 26.3 client JAR 的字节码得到：

- `GameRenderer.gameRenderState` 是 final 字段，在构造器中创建。
- `GameRenderState.guiRenderState` 是 final 字段，在构造器中创建。
- `GuiRenderer` 保存传入的 `GuiRenderState`，每次 `render()` 末尾调用它的 `reset()`。

因此它是跨帧复用的对象，不能用身份判断每帧开始。探针调用同一个 state 的 `reset()` 后再次提交，`nextSlot` 继续前进，没有归零。

这修正了历史报告 `AUDIT-v1.5.1-upstream-2026-11-07-round2.md:174` 中“每帧新建”的错误前提。跨帧未归零本身不等于每帧覆盖；在帧内提交不超过 8 次且前一帧已消费完毕时，不能仅凭这一点认定画面必然损坏。

修复方向：接入真实帧生命周期，在确认上一帧消费完毕后重用槽；帧内按需分配独立快照，禁止覆盖尚未消费的纹理。仅增大常量不能消除这一缺陷。

## 其它检查及验证边界

- 26.3 的 MiSans Regular / Medium / Demibold 和 Icon 四个字体文件均存在于源码资源目录。这不能证明用户 JAR 中资源完整或运行时字体加载成功。
- `check-parity.ps1 -Check -Quiet` 返回 1，共 65 项类集合差异：Fabric 1.20.1 多出的六个 Android/Skia 实验类未同步。没有发现该闸门报告其它渲染入口差异。这项失败不能用来解释 26.3 的具体画面异常，也不能证明绘制行为正确。
- Fabric 1.20.1 基线本身有 `drawModules()` 仅绘制 FPSDisplay 的调试过滤，且 GLES 下直接禁用 HUD/GUI pass；这些代码不在本次 26.3 实现中，不应机械复制为 26.3 的修复方案。
- 探针使用 JDK 25、缓存中的 LWJGL 3.3.3 与真实桌面 OpenGL；26.3 工程声明 NanoVG 3.4.3。本次探针没有运行 NanoVG 本身，验证的是生产状态保护器及生产合成器的 GL 行为与纹理引用生命周期。
- 未运行整工程构建、Mixin 织入或游戏视觉验收。源码检查和探针结果不能代替用户环境复现。

## 本地证据

- 复现脚本：`.dsh-tmp/render263-probe.py`
- 原始输出：`.dsh-tmp/render263-probe/result.txt`
- 真实字节码：`.dsh-tmp/reports/render263-{GameRenderer,GameRenderState,GuiRenderer,GlCommandEncoder,GlStateManager}.txt`
- 运行命令：使用可用 Python 执行 `.dsh-tmp/render263-probe.py`；脚本使用仓库内缓存的 JDK/LWJGL，仅创建隐藏的诊断窗口。

建议后续先修复上述状态隔离和纹理生命周期，再在相同 JAR/加载器环境中对标题菜单、暂停菜单、设置、ClickGUI、多窗口界面、缩放/窗口尺寸变化进行连续帧验收，并确认日志中的字体加载及 `[HUD]/[GUI]` 异常。
