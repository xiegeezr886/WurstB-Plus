# 标题界面自定义背景

> 范围：v1.6 根目录 Forge 1.20.1 工程。功能入口在主菜单右上角，代码在
> `net.wurstclient.background`（13 个类）+ `net.wurstclient.gui.title.BackgroundSelectScreen`。
> 本文记录**接线与实现**，以及**哪些能力只是「导入并标记」而并没有真正生效**。

## 1. 组成

| 位置 | 职责 |
| --- | --- |
| `BackgroundManager` | 客户端级单例。持有选择、纹理与加载状态，负责整屏绘制。用**一个固定纹理 id**（`wurst:title_background`）复用同一个注册槽位，切换背景不会泄漏纹理注册 |
| `BackgroundStorage` | 磁盘库：`<wurst 文件夹>/backgrounds/<id>/media.<ext>` + `thumbnail.png` + `meta.json`。刻意不含 Minecraft 类型，可对临时目录单测 |
| `BackgroundEntry` / `BackgroundKind` | 列表条目与媒体类型（`IMAGE` / `GIF` / `VIDEO`，按扩展名判定）；内置默认背景是**虚拟条目**，从不落盘。`canPlay()` 是「本版能不能真的放出来」的唯一真值来源 |
| `BackgroundThumbnail` | 列表卡片的预览图；动图取**第一帧**（`NativeImage` 根本读不了 GIF） |
| `GifFrames` | 动图解码：ImageIO 读帧 + **逐帧合成**（帧矩形、处置方式、透明混合），并施加解码预算。纯 JDK，无 Minecraft 类型 |
| `BackgroundAnimation` | 帧时钟：由每帧延迟推出「此刻该显示第几帧」，含浏览器对 0/10 ms 延迟的 100 ms 规则。纯算术，可单测 |
| `BackgroundClip` | 解码结果（`NativeImage[]` + 帧时钟）与它自己的解码线程；`DynamicTexture` 会关掉交给它的图像，所以纹理与帧各持一份 |
| `BackgroundMotion` / `BackgroundPose` | 静图运镜：`NONE` / `KEN_BURNS` / `PARALLAX` / `DRIFT`，姿态是**时间、鼠标与视口的纯函数**（不依赖帧计数），所以掉帧时依然平滑，也能单测 |
| `BackgroundFilePicker` / `BackgroundFileChooser` | 跨平台文件选择（AWT `FileDialog` / `JFileChooser` 兜底） |
| `WallpaperEngineImporter` / `SteamLocator` / `VdfParser` / `ProjectJson` | Wallpaper Engine 导入：定位 Steam 库、解析 `libraryfolders.vdf` 与各壁纸的 `project.json`，产出候选列表（上限 500，可播放的排在前面） |
| `WePackage` / `WeTexture` / `WeScene` / `WeSceneLayout` / `WeSceneWallpaper` | Wallpaper Engine **场景包**的读取与绘制：包目录 → `.tex` 头部 → 图层树 → 画布到屏幕的换算 → 逐层上传与绘制。格式推导与边界见 [`wallpaper-engine-scene.md`](wallpaper-engine-scene.md) |
| `BackgroundSelectScreen` | 选择界面：默认背景卡片、已导入卡片（缩略图 / 标题 / 来源 / 类型徽章）、导入、Wallpaper Engine 导入、删除、运镜切换。圆角对话框；缩略图按 cover 裁切填满卡片预览区，不再拉伸（见 [title-menu.md](title-menu.md)，实机图 [background-select-ingame.png](background-select-ingame.png)） |

## 2. 绘制路径

整屏背景**只用原版 `GuiGraphics.blit`**，在 Skia 通道之前完成——与有状态的 Skia GL 后端混用没有收益，两边都要抢同一份 GL 状态。

- 裁剪按 CSS `object-fit: cover`（复用 `TwilightCoverFit.sourceRect`），所以任何宽高比的图都不会被拉伸。
- 运镜是**在 cover 之上的额外放大与平移**：`BackgroundPose.scale/offsetX/offsetY` 只做正向放大，所以平移永远不会露出图片边缘。`strengthFor()` 会按视口宽度把强度缩到 `0.5..1.5` 倍，小窗口不会得到和大窗口一样的像素位移。
- 缩放用 `setFilter(true, false)`（双线性、不重复边缘），壁纸几乎不会以原始尺寸绘制。

## 3. 加载与生命周期

- `ensureLoaded()` **从不阻塞**：未解码完的那一两帧直接返回 false，调用方画内置默认背景。
- 解码走 `AsyncTextureLoader`（后台线程解码 → 客户端线程上传）。
- 选择了一个文件夹已经不存在的背景（手删、半拷贝）时记进 `failedId`，**不再每帧重试**。
- 资源重载会释放已注册纹理：`currentTexture()` 发现纹理管理器不再持有它时，`forget()` 后重新加载。
- 选择、运镜方式与强度写入 `GuiPreferences`（`selectedBackground` / `backgroundMotion` / `backgroundMotionStrength`），随 GUI 偏好一起存盘。

## 4. 库的安全边界

`BackgroundStorage.isValidId()` 只接受「字母 / 数字 / `-` / `_`」且长度 ≤ 48、不含 `..`、不等于 `default`——这是防止**存盘里的 id 走出库目录**的那道闸门。`slugify()` 生成 id 时保留任意文字（中文标题照样得到可读的文件夹名），只是把其余字符折成 `-`。

## 4. 动图（GIF）播放

静图只是把一张图贴上去；GIF 要解码、要合成、要按帧上传，这三件事都不在
`GuiGraphics.blit` 的射程内，所以单独走一条路。

- **合成**：GIF 的每一帧不是一张完整的图，而是一个要画到画布上的矩形，而且上一帧
  可以要求「把这个矩形清掉」或「把整块画布倒回去」。`GifFrames` 按
  `none` / `doNotDispose` / `restoreToBackgroundColor` / `restoreToPrevious`
  逐帧合成，透明像素做 source-over 混合。**不做这一步，每一张局部帧看上去都会像
  花屏。**
- **预算**：一个 1080p、60 帧的 GIF 是 1.24 亿像素，光 int 数组就 500 MB。所以
  解码时按预算缩小：单边不超过 1600px、总像素不超过 1200 万、最多 150 帧；被缩小
  或截断时会在日志里写一行 `[Background] … decoded to WxH, N frames`。
- **上传**：`BackgroundAnimation` 用帧延迟算出当前该显示第几帧，**只有帧号变化时**
  才把该帧拷进纹理自己的图像并 `upload()`。浏览器对 0/10 ms 的延迟按 100 ms 处理，
  这里沿用同一规则——否则一个零延迟的两帧 GIF 会变成每帧一次全屏上传。
- **所有权**：`DynamicTexture` 的构造与 `setPixels` 都会**关掉**交给它的
  `NativeImage`，所以纹理自己持有一张图像、帧留在 `BackgroundClip` 里，每帧用
  `copyFrom` 拷过去。直接把帧交给纹理会把整个动画的第一帧之外全部关成空指针。
- **颜色通道**：`NativeImage` 的整数像素接口是 **ABGR**，从标准 ARGB 转换时必须
  交换红蓝。写错了就是一张颜色反过来的壁纸。
- **缩略图**：`NativeImage` 读不了 GIF，动图的卡片预览改用第一帧生成，否则每张导入
  的 GIF 都是一张空白卡片。
- **线程**：解码在 `WurstB-BackgroundClip` 守护线程上跑，完成后回到客户端线程建纹理；
  期间发生的切换/删除会让这次解码结果被直接丢弃并释放。

## 5. 还没做的（如实说明）

1. **视频背景不能播放。** `BackgroundKind.canPlay()` 对 `VIDEO` 返回 false：mp4 需要
   H.264 解码器，那是本项目刻意不引入的依赖。这类卡片带「视频 · 不能播放」徽章，
   点击只会在状态栏说明原因，不会静默失败。
2. **没有裁剪与对焦点设置**：构图完全由 `cover` 决定，长图只能看到中间那条。
3. **Wallpaper Engine 的网页 / 应用型壁纸**只导入其**预览图**并在卡片上注明原因
   （`Candidate.note`）。Scene 型壁纸不再是这一类：它的场景包会被导入并按图层画出来
   （见 [`wallpaper-engine-scene.md`](wallpaper-engine-scene.md)），但**只画静态
   图层**——效果着色器、粒子、时钟与音频都还没有。
4. **动图只有整个界面可见时才推进**：标题界面不显示时不会后台跑帧，重进界面会按
   挂钟时间直接跳到该显示的那一帧（不做补帧）。
5. **没有「打开背景文件夹」入口**，删库只能靠文件管理器。

## 6. 验证状态（诚实声明）

- 只做了编译与单元测试：`BackgroundMotionTest`（四种运镜的姿态、边界与 `strengthFor`
  缩放）、`BackgroundAnimationTest`（帧时钟的边界、循环、有限次循环后停在末帧、
  退化延迟规则）、`GifFramesTest`（**处置方式与透明混合逐条**、帧矩形裁剪、解码预算
  的缩放公式、以及用 ImageIO 现写一个带动画 GIF 再读回的端到端用例，含 Netscape
  循环次数）、`BackgroundStorageTest`（导入 / 列举 / 读回 / id 安全 / 删除）、
  `VdfParserTest`、`WallpaperEngineImporterTest`（视频 / 图片 / 场景型壁纸、畸形
  `project.json`、上限与排序）。全部**不依赖 Minecraft 运行时**，也不联网。
- **标题界面与选择界面已经在实机验证过**：场景壁纸渲染、缩略图卡片、圆角对话框与
  cover 裁切都跑通了（[background-select-ingame.png](background-select-ingame.png)）。
  滚动条、删除与导入这些交互仍只做过签名核对。
- **真实 Wallpaper Engine 库已经跑过一次**：在用户这台机器上扫描 Steam 库并导入了
  多个工坊条目（含中文标题的），其中暴露并修掉了一个崩溃——工坊条目 id 由标题派生，
  中文标题会让 `ResourceLocation` 抛异常把客户端崩掉（
  `crash-2026-10-05_15.15.30`），现在贴图名会先洗字符再加哈希
  （`BackgroundSelectScreen.thumbnailTextureName`，回归测试
  `BackgroundThumbnailNameTest`）。

场景那部分（`WePackage` / `WeTexture` / `WeScene`）**已经在真实的 14 MB 场景包上跑通**
（`WeSceneRealFileTest`），格式推导过程与仍未实现的部分单独记在
[`wallpaper-engine-scene.md`](wallpaper-engine-scene.md)。
