# v1.5.1 跨版本渲染检查与修复

检查时间：2026-11-12 至 2026-11-13。承接用户报告的 26.3「界面黑屏闪动、按钮闪动、文字不显示」，将同类缺陷检查扩展至其余 **63 个 v1.5.1 工程**。连同前轮三个 26.3 工程，当前覆盖 **66 个工程**；根目录 Forge 1.20.1 的 v1.6.0 不在修复范围内。

本轮完成源码修复、全部渲染源码变体的独立 OpenGL 测试及五个代表工程的完整构建。**没有启动 Minecraft 验收画面，没有安装或发布产物，不能据此宣称用户现象已在所有版本消失。**

## 修复范围

| 路径 | 工程数 | 修复 |
| --- | ---: | --- |
| 直接渲染，1.20.x 至 1.21.5 | 33 | 初始化状态保护、失败清理、sampler/正反面 stencil/像素上传状态恢复、完整离屏模板清屏 |
| GUI 延迟提交，1.21.6 至 26.3 | 33 | 上述状态修复，以及独立纹理快照、真实 reset 后回收、渲染器关闭时释放 |
| 三端 1.20.3 / 1.20.4 | 6 | 列表背景回调从继承的 `render` 改为目标类实际声明的 `renderWidget` |
| Fabric 1.20.1 | 1 | 去掉遗留的仅允许 FPSDisplay 绘制的测试过滤；保留既有 GLES/Krypton 降级和 Skia 实验路径 |

前轮 26.3 已有的修复按各版本 API 移植至另外 63 个工程；新增延迟队列回调涉及 30 个工程。没有复制 26.3 的 RenderPearl 类型到旧版，也没有向旧版直接渲染路径增加 GUI 队列回调。

### GL 与 Minecraft 纹理缓存一致性

除前轮已修的原生状态泄漏外，本轮复查官方 1.21.6 `GlDevice.createTexture` 字节码，确认分配二维纹理调用 `GlStateManager._bindTexture`，同时改变原生绑定和 Minecraft 缓存。若 guard 只用原生 GL 恢复，缓存仍可能指向新纹理，后续引擎绑定可能被错误跳过。

66 份 `GlStateGuard` 均在恢复原生绑定后，同步 Minecraft 的 active texture 与 texture binding 缓存。导入沿用各版本现有 `RenderScope` 的实际类型：旧版 `blaze3d.platform`、1.21.5 起的 `blaze3d.opengl`、26.3 的 `renderpearl.backend.opengl`。Fabric 根工程额外的 pack alignment 恢复继续保留。

## 实际游戏字节码核对

对官方客户端及映射核对以下 10 个版本：1.21.6、1.21.7、1.21.8、1.21.9、1.21.10、1.21.11、26.1、26.1.1、26.1.2、26.2。

- `GuiRenderState.reset()V` 存在，真实 `GuiRenderer` 调用该方法清空队列。
- `GameRenderer.close()V` 存在。
- 1.21.x 的状态类在 `net.minecraft.client.gui.render.state`，26.x 在 `net.minecraft.client.renderer.state.gui`；新增 Mixin 的导入与各自配置一致。

26.3 的相同目标已在前轮核对。本轮不是用测试替身是否有同名方法来判断真实 Mixin 目标。

另核对 1.20.1–1.20.4 官方映射及实际类方法：1.20.1/1.20.2 仍由列表类声明 `render`，1.20.3/1.20.4 改为 `renderWidget`。因此只修改后两个版本的六份回调。Fabric 1.20.4 编译期注解处理器曾报告旧目标不存在；构建成功不能替代 Mixin 目标检查。修后 JAR 的 refmap 正确包含 `renderWidget` 映射。

原始证据保存在 `.dsh-tmp/render-targets/`，包括官方输入 SHA-1、映射、javap 输出、`results.json` 和 `selection-results.json`。

## 真实 OpenGL 回归

直接编译未改写的生产 `NanoVGContext`、`HudFont(s)`、`GlStateGuard`，以及存在时的 `NanoVgBlitter`、状态提取 helper、reset 回调。Minecraft 的 GPU/GUI 边界、native loader 为替身；创建隐藏 GLFW 窗口，实际执行 NanoVG 绘制、纹理复制、像素读取和资源释放。测试不启动 Minecraft，不执行真实 Mixin 织入。

| 样本 | LWJGL/NanoVG | 断言数 |
| --- | --- | ---: |
| Fabric 1.20.1 特有分支 | 3.3.3 | 39 |
| NeoForge 1.20.1 通用直接渲染分支 | 3.3.1 | 39 |
| Fabric 1.21.6，无独立 sampler 的 GPU API | 3.3.3 | 66 |
| Fabric 1.21.11，独立 sampler API | 3.3.3 | 66 |
| NeoForge 26.1，TextureFormat API | 3.4.1 | 66 |
| NeoForge 26.2，GpuFormat API | 3.4.1 | 66 |
| Fabric 26.3，RenderPearl API | 3.3.3 | 66 |
| **总计** | | **408** |

每个样本四个内置字体加载成功，混合文本绘制得到 1151 个非透明像素；有延迟队列的五个样本均读回十二份独立标记 `[1,2,3,4,5,6,7,8,9,10,11,12]`。覆盖不同 GUI 状态、窗口缩放、状态复用、重复 reset、资源关闭、正常和失败初始化、PBO/行布局、正反面 stencil、sampler、纹理缓存同步与 GL 错误检查。

`run-render-matrix-tests.py --check-results` 核对测试输入文件 SHA-256，并比对整个矩阵的源码变体。仅规范化 GlStateManager 的包导入后，覆盖 **66 份 Guard 的 2 种变体、66 份 Context 的 6 种变体、33 份 Blitter 的 5 种变体**。API 签名与方法体没有被规范化，生产源码没有为测试替身改写。

最终独立 GL 复跑使用 JDK 21。JDK 25 配合旧 LWJGL 曾给出 JNI 版本警告，一次复跑进程异常退出；本机原生库提取权限探测也曾失败。测试工具改为按版本解包到自己的工作目录，并设置测试进程原生库搜索路径；没有修改系统权限或生产加载器。NeoForge 1.20.1 使用的 LWJGL 3.3.1 在 JDK 21 仍打印 JNI 版本警告，本轮断言及进程退出均正常。

26.3 生产依赖保持 3.4.3，GL 诊断样本使用可获取的 3.3.3，**不宣称 3.4.3 独立运行已验证**。Fabric 根工程未启用的 Skia 边界使用调用即失败的替身，GLES/Android 路径未实测。

复跑入口（需要桌面 OpenGL 和 JDK 21）：

```powershell
python scripts/audit-render-regressions.py
python -m unittest discover -s scripts/tests -p test_render_regressions.py
python scripts/run-render-matrix-tests.py --java-home <JDK21目录>
```

每个样本的 `result.txt` 和 `inputs.json` 位于 `build/render263-tests/<工程路径用连字符连接>/`；汇总为 `build/render263-tests/matrix-result.json`。12 项静态闸门测试覆盖初始化、PBO、模板掩码、sampler、缓存同步、错误回收时机、注册遗漏、版本包名、重复回收、固定槽回绕和继承渲染目标的负例；静态闸门及负例测试已接入 CI。

## 代表工程完整构建

最终缓存同步补丁后，以下五个工程的完整 `build` 成功；前四个属于本轮「其他版本」，26.3 为新增缓存同步后的回归。

| 工程 | JDK | 项目测试 | 发布归档检查 |
| --- | --- | --- | --- |
| Fabric 1.20.4 | 17 | NO-SOURCE | 通过，列表 refmap 已更新 |
| Fabric 1.21.11 | 21 | 106 项，零失败/错误/跳过 | 通过 |
| NeoForge 1.21.1 | 21 | 106 项，零失败/错误/跳过 | 通过 |
| NeoForge 26.2 | 25 | NO-SOURCE | 通过 |
| NeoForge 26.3 | 25 | NO-SOURCE | 通过 |

产物均包含当前修复后的 Context 与 Guard；延迟版本包含已注册的 reset Mixin。核对 Baritone、内嵌依赖、实际需要的着色器资源及许可，未打入本机 ZIP agent/helper。构建仍使用既有的、本机任务目录内受限的 ZIP 写权限探测 agent；没有将这一环境处理加入生产构建脚本。

产物字节数和 SHA-256 见下表。产物保留在各工程 `build/libs/`，没有复制到 `dist/` 或用户的游戏目录。

| 工程 | 字节数 | SHA-256 |
| --- | ---: | --- |
| `fabric/versions/1.20.4` | 53082197 | `9a429c407e20b2ea91660174d7c7dfc461b7734737abb6c3438286b2a9dcd1ea` |
| `fabric/versions/1.21.11` | 53144723 | `af27a545b5c680243900248108b8e80535ebff56c63d2ad101b12e11ad913991` |
| `neoforge/versions/1.21.1` | 52547223 | `e295948d455dd0db5e6208beefe32829ad5918c358c3bce278a34b12f1f03a25` |
| `neoforge/versions/26.2` | 52381770 | `b5e9a77058898386f348e8fdd45b053386f7a6ea77d5e2116af3006a4692bbce` |
| `neoforge/versions/26.3` | 52392354 | `cd9374c1c5fd26bc069520df11cd5c69bdeadfada267d4df109021e1ab1dc785` |

日志归档：`.dsh-tmp/render-matrix-<工程路径用连字符连接>-build.log`。结构化产物与项目测试结果：`.dsh-tmp/render-matrix-artifacts.json`。

最终全仓崩溃回归扫描 `scripts/audit-crash-regressions.py` 通过，覆盖 67 个工程；渲染静态闸门覆盖 66 个 v1.5.1 工程，12 项闸门测试通过，`git diff --check` 通过。

## 验证边界

没有对全部 66 个工程逐一完整构建；Forge 本轮以源码/API 分支检查和官方字节码核对为主，先前 Forge 26.3 的 Java 8 ZIP 子进程问题没有在本轮解决。也没有验收游戏内连续帧、标题/暂停/设置/ClickGUI、窗口缩放、光影及第三方渲染模组组合。

本轮保留现有 Android/Skia 实验和其它任务修改；已有 parity 检查的 Fabric 根工程额外实验类差异没有通过扩大白名单来掩盖。根目录 v1.6.0 未回填本轮渲染代码。修复保留在当前工作区，未提交、推送或发布。
