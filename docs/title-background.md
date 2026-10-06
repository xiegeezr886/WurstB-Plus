# 标题界面自定义背景

> 范围：v1.6 根目录 Forge 1.20.1 工程。功能入口在主菜单右上角，代码在
> `net.wurstclient.background`（29 个类）+ `net.wurstclient.gui.title.BackgroundSelectScreen`。
> 本文记录**接线与实现**，以及**哪些能力只是「导入并标记」而并没有真正生效**。

## 1. 组成

| 位置 | 职责 |
| --- | --- |
| `BackgroundManager` | 客户端级单例。持有选择、纹理与加载状态，负责整屏绘制。用**一个固定纹理 id**（`wurst:title_background`）复用同一个注册槽位，切换背景不会泄漏纹理注册 |
| `BackgroundStorage` | 磁盘库：`<wurst 文件夹>/backgrounds/<id>/media.<ext>` + `thumbnail.png` + `meta.json`。刻意不含 Minecraft 类型，可对临时目录单测 |
| `BackgroundEntry` / `BackgroundKind` | 列表条目与媒体类型（`IMAGE` / `GIF` / `VIDEO` / `SCENE`，按扩展名判定）；内置默认背景是**虚拟条目**，从不落盘。`canPlay()` 是「这个**类型**本版在原理上能不能放」的判定（今天四种都能），**某个视频文件**能不能放由 `BackgroundVideo.probe(Path)` 决定 |
| `BackgroundThumbnail` | 列表卡片的预览图；动图取**第一帧**（`NativeImage` 根本读不了 GIF） |
| `GifFrames` | 动图解码：ImageIO 读帧 + **逐帧合成**（帧矩形、处置方式、透明混合），并施加解码预算。纯 JDK，无 Minecraft 类型 |
| `BackgroundAnimation` | 帧时钟：由每帧延迟推出「此刻该显示第几帧」，含浏览器对 0/10 ms 延迟的 100 ms 规则。纯算术，可单测 |
| `BackgroundClip` | 解码结果（`NativeImage[]` + 帧时钟）与它自己的解码线程；`DynamicTexture` 会关掉交给它的图像，所以纹理与帧各持一份 |
| `BackgroundVideo` / `VideoPacing` / `Mp4Probe` / `FfmpegNatives` / `FfmpegVideoDecoder` | 视频背景：**自带的精简版 FFmpeg 7.1.1（LGPL，仅解码器）原生解码** mp4。**流式**——解码线程按挂钟一次解一帧，帧在 3 个复用缓冲里交接给渲染线程；`VideoPacing` 是纯算术的播放规则（帧率上限、脱节判定、循环取模），可单测；`Mp4Probe` 只读文件头，用来区分「没有视频轨道 / 编码放不了 / 文件坏了」。见第 6 节 |
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
- 视频与其它三种多一条：帧是**边放边解**的，所以 `forget()`（切换 / 删除 / 资源重载 / 客户端退出）必须停掉解码线程。`BackgroundVideo.close()` 会中断并等它退出；纹理仍然由管理器按同一个槽位释放（见第 6 节）。

## 4. 库的安全边界

`BackgroundStorage.isValidId()` 只接受「字母 / 数字 / `-` / `_`」且长度 ≤ 48、不含 `..`、不等于 `default`——这是防止**存盘里的 id 走出库目录**的那道闸门。`slugify()` 生成 id 时保留任意文字（中文标题照样得到可读的文件夹名），只是把其余字符折成 `-`。

## 5. 动图（GIF）播放

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

## 6. 视频（MP4）播放

解码器是**随模组发布的精简版 FFmpeg 7.1.1（LGPL、仅解码器）+ 一个自写的 JNI
shim**。上一版用的是纯 Java 的 JCodec，同一段 4K 素材只有 **3.8 fps**（实测），
所以换成了原生解码；JCodec 的两个依赖、解包任务与相关代码**已经全部删掉**
（`build.gradle` 里不再有 `unpackJcodec` / `jcodecUnpack` / `classesDirs.from(...)`，
`gradle.properties` 里不再有 `jcodec_version`）。构建方式、configure 行与许可分析见
[`../native/ffmpeg/README.md`](../native/ffmpeg/README.md)。

### native 怎么发布、怎么加载

- **7 个 DLL**（`avcodec-61` / `avformat-61` / `avutil-59` / `swscale-8` / `zlib1` /
  `libwinpthread-1` / `vf_ffmpeg`，合计 **7,605,466 字节 = 7.25 MiB**）作为**普通资源**
  放在 `src/main/resources/assets/wurst/ffmpeg/`，两种许可文本
  （`copying-lgplv2.1.txt`、`license-ffmpeg.txt`，内容与 `native/ffmpeg/` 下的
  `COPYING.LGPLv2.1` / `LICENSE.md` 逐字节相同，只改了名）与它们放在一起、随 jar
  一起分发。改名是因为 Minecraft 的资源路径只允许小写（`[a-z0-9_.-/]`），
  `COPYING.LGPLv2.1` 这种带大写的名字在开发环境里会让资源扫描直接抛异常。
- 运行时 `FfmpegNatives` 把它们解压到 `gameDir/ffmpeg/`，再按**依赖顺序**逐个
  `System.load`：`libwinpthread-1` → `zlib1` → `avutil-59` → `swscale-8` →
  `avcodec-61` → `avformat-61` → `vf_ffmpeg`。顺序不是讲究：Windows 只从进程搜索
  路径解析 DLL 自己的导入，不看清 DLL 所在的目录，所以 `avutil` 之前必须先有
  `libwinpthread-1`、`avformat` 之前必须先有 `zlib1`，否则报「找不到依赖库」。
- 刻意**不走 jarJar / minecraftLibrary**（与 Skiko 同一条理由）：jarJar 会重定位资源
  路径，而 DLL 之间是按文件名互相依赖的。命令行排障可以用
  `-Dwurst.ffmpeg.dir=<目录>` 直接指定目录、跳过解压；Gradle 的 `test` 任务就是把它
  指向 `native/ffmpeg/dll`，所以解码用例在无客户端下真跑。
- **LGPL 义务**：这套构建是 LGPL v2.1-or-later，**没有启用任何 GPL / nonfree 组件**
  （`build-ffmpeg.sh` 里有构建期闸门）。FFmpeg 以**动态库**形式链接，用户可以自行替换
  `avcodec-61.dll`；可复现的构建脚本、shim 源码与 configure 行在 `native/ffmpeg/` 下。

### 支持与不支持

| | 结果 |
| --- | --- |
| MP4 容器 + H.264（`avc1` / `avc3`）、HEVC（`hvc1` / `hev1`）、VP9（`vp09`）、MPEG-4 Part 2（`mp4v`）、Motion JPEG（`jpeg`） | **能放**（8 位 4:2:0 实测；10 位见下） |
| AV1（`av01`） | **只有硬件解码**。FFmpeg 里没有软件 AV1 解码器（`libavcodec/av1dec.c` 在没有 hwaccel 时直接返回 ENOSYS），所以**需要一块支持 AV1 解码的显卡**；没有就会落到「这个编码本版放不了（实际：AV1）」 |
| VP8（`vp08`）、ProRes | 放不了：这套构建**没有**编进这两个解码器，卡片上写明编码名 |
| WebM / MKV / AVI / 其它容器 | 放不了：本版只认 MP4（扩展名也只收 `.mp4` / `.m4v`）。FFmpeg 本身带了 Matroska / AVI 解复用，但没接线 |
| 音轨 | **完全忽略**。视频背景没有声音，将来也不打算有 |
| 10 位 / HDR | **没有测过**。转换路径（`swscale` → `AV_PIX_FMT_RGBA`）是 8 位的，10 位源会走一次没验证过的降位转换 |
| 带旋转矩阵的手机竖拍视频 | **会横过来显示**：旧的 JCodec 路径会按 `tkhd` 的旋转信息转正，现在这套构建没开 filter，shim 也不读 display matrix。未实现，见第 7 节 |

### 为什么是流式的

一段 10 秒的 720p30 视频解成位图是 **300 帧 × 3.7 MB ≈ 1.1 GB**，GIF 那条
「先全解完再放」的路在这里直接不成立。所以：

- 解码线程（`WurstB-BackgroundVideo`，每实例一个守护线程）**按挂钟一帧帧解**，
  只有到点的那一帧才做 YUV→RGB、缩放与写像素。
- 解好的帧在 **3 个复用缓冲**里交接：一个在解、一个等渲染线程取、一个在拷进纹理。
  缓冲只有这么多，内存因此有上界。
- **背景没在显示就不解码**：渲染线程超过 1 秒不来取帧（标题界面不在了、玩家进了
  游戏），解码线程直接停下等；重新显示时挂钟已经走过去，按下面的规则定位。
  没有这一步的话，一段 1080p 的视频会在玩家游戏里一直解码，白烧小半个核心。
- 渲染线程 `takeFrame()` 取最新的一帧拷进纹理再 `recycle()` 还回去，**只有真的
  有新帧时才 `upload()`**。

### 尺寸与帧率

### 尺寸：这一次修掉的「模糊」

- **按绘制尺寸解，不再固定 720p**。壁纸是整屏画的，所以「绘制尺寸」就是窗口的
  **帧缓冲**尺寸（`Window.getWidth()/getHeight()`，物理像素）；**不是** GUI 缩放后的
  逻辑尺寸——那张图会被放大 `guiScale` 倍画到屏幕上，按逻辑尺寸解等于自己先糊一道。
  目标尺寸取三者最小：**绘制尺寸**、**源尺寸**（永不放大）、**上限 2560x1440**。
- **上限为什么是 2560x1440**：「上传尺寸变大几乎不要钱」这个说法**实测不成立**——
  解码本身由源分辨率决定，但 `swscale` 的缩放与显存带宽是按输出像素算的。同一台
  机器、同一段 4K/16fps 素材：输出 720p 时软解 235 fps、1080p 142 fps、
  1440p 95.5 fps；硬解 115 / 92 / 68 fps。1440p 是「画质够（2K 屏一像素对一像素、
  4K 屏只放大 1.5 倍）且端到端仍有 30fps 上限两倍余量」的那个点，代价是 3 个复用
  缓冲 3×14.7 MB（加上纹理与解码缓冲约 74 MB；按 4K 输出要 165 MB 以上）。
- **窗口变了就跟着换**：`takeFrame()` 每帧都会重新算一次目标尺寸，解码线程按它换
  一套复用缓冲并调 `FfmpegVideoDecoder.setOutputSize`。两次换尺寸之间至少隔 500ms
  （拖窗口边缘时尺寸每帧都在变）。旧池里那些缓冲**不能立刻关**——渲染线程可能正
  拿着其中一张在拷——所以空闲的直接关、还在外面的记进 retired，等 `recycle()` 还
  回来再关。
- **纹理必须跟着换尺寸**：`BackgroundManager.render` 发现视频的尺寸与纹理不一致时
  会用同一个纹理槽位重建一张纹理（`matchVideoSize`）。不换的话 `NativeImage.copyFrom`
  的尺寸检查会让画面**停在旧的那一帧**上，看起来就是「缩放窗口之后视频冻住了」。
- **换尺寸时 swscale 的上下文必须跟着丢（原生侧的坑）**：shim 里缓存的 swscale
  上下文原来只在**源**格式/尺寸变化时重建，而 `setOutputSize` 改的是**输出**尺寸，
  于是 `sws_scale` 仍按旧尺寸写像素、却用新 stride 排——行全部错位，画面撕裂成"两帧
  混在一起"的样子。输出缩小时更糟：它会**写过 Java `byte[]` 的末尾**（那块数组是
  经 `GetPrimitiveArrayCritical` 拿到的堆上对象）。Java 侧两条都发现不了，因为
  `setOutputSize` 返回的字节数是**算**出来的，`bytes != frameBytes` 那条校验恒过。
  这个坑只在**后续**换尺寸时会出现（第一次 `setOutputSize` 时 `h->sws` 还是 NULL），
  所以"加载时正常、一拖窗口就花"。修法与实机验证见
  [`../native/ffmpeg/README.md`](../native/ffmpeg/README.md) 第 9 节第 5 条。
- **帧率上限 30fps**：距上一帧不足 1/30 秒的帧**解但不显示**（H.264 不能跳着解，
  但可以不转换、不上传）。60fps 的源就是隔一帧丢一帧。判定留了 1 毫秒取整余量，
  所以实测是 **31/s 上下**而不是正好 30——30fps 的源时间戳取整后会落在 33ms 上，
  不留余量会因为这点误差丢掉一半的帧。

### 时间以挂钟为准（时间轴是自己算的）

规则集中在 `VideoPacing`（纯算术，可单测），但**帧的显示时刻不再是容器里的 PTS**：
shim 只交图像、不给逐帧时间戳，所以第 n 帧的时刻是 `n * 1000 / fps`（fps 取容器
帧率，拿不到就用「帧数 / 时长」推，再拿不到按 30fps）。代价是可变帧率的源会有累积
偏差；收益是时间轴**一定单调向前**——旧那条路上「坏时间戳跳到几小时后」的失败模式
不存在了。

- **该显示就显示、太近就丢、没到点就等**（`decide`），一轮放完把实测的一轮时长
  记下来（元数据里的时长未必准）再从头放，循环处不会出现时间倒流。
- **落后挂钟超过 1.5 秒就重新对齐一次**：把这一轮的起点挪到当下（`epoch = now - pts`），
  画面继续往下放，只是把落后的时间丢掉。会走到这里的两种情况：界面隐藏一段时间后
  重新显示（这期间渲染线程不来取帧），以及解码速度跟不上实时。**绝不把落下的几百帧
  一帧帧解完**，那是几十秒的追赶，画面等于卡死。
  之所以不是「跳到挂钟位置」：这套 native 接口只有 `seekToStart()`（回第 0 帧），
  没有任意定位。差别是「接着放」与「跳到该放的位置」，前者不会跳回片头。
- 某一帧远在未来时最多等 5 秒（`MAX_WAIT_MS`），这条上限现在只用来兜系统时钟被往
  回调（NTP 校时）——按帧率算出来的时间轴不可能自己跳到几小时后。

### 性能实测（无客户端，走 Java 路径）

素材是仓库里那段真实壁纸 `run/wurst/backgrounds/ayaka-sakura-spirit/media.mp4`
（**3840x2160 H.264 High L5.1，16 fps，369 帧，23.06 s**），测的是
`FfmpegVideoDecoder` 逐帧解 + 逐像素写进 `NativeImage`（模组里真正做的那两步），
每次都是一整遍 369 帧，跑的工具就是仓库里的 `FfmpegVideoDecoderTest`（Java 路径，
不是 ffmpeg 命令行）：

| 输出尺寸 | 软解 | 硬解（d3d11va） | 每帧写 NativeImage | 端到端（软解 / 硬解） |
| --- | ---: | ---: | ---: | ---: |
| 1280x720 | 196.7 fps | 113.3 fps | 1.3 / 0.7 ms | 157 / 105 fps |
| 1920x1080 | 137.1 fps | 88.6 fps | 2.3 / 1.7 ms | 104 / 77 fps |
| **2560x1440（本版上限）** | **87.5 fps** | **65.9 fps** | **3.7 / 3.1 ms** | **66 / 55 fps** |
| 3840x2160（源尺寸） | 224.8 fps | 53.5 fps | 8.5 / 7.1 ms | 77 / 39 fps |

同一台机器上重复跑会有 **5~10% 的抖动**（早几轮里 720p 软解到过 235 fps、1440p 软解
95.5 fps），上表是同一轮里跑齐的一组；最差的那一组也仍然在 30fps 上限的两倍以上。
每一帧的 `bytes/frame` 都等于 `宽×高×4`，采样到的 alpha 恒为 255，三个通道都跑满
0..255，软解与硬解的均值一致（1440p 下都是 R=137.4 G=94.1 B=105.5）——**不是空白帧、
也不是乱数据**。

两个反直觉但可解释的数字：① **4K 原生解码反而比缩到 1440p 快**——`swscale` 在尺寸
相同时只做 YUV→RGBA 转换（SIMD 快路），一旦要缩放就要跑缩放器的多抽头滤波，
`SWS_FAST_BILINEAR` 在 2160→1440 这种非整数比上比转换还贵；② 硬解随输出像素线性
变慢（GPU→CPU 回读是按输出算的），所以硬解在 4K 上只剩 53 fps。

**仍然要如实说明**：解码量由**源**分辨率与帧率决定，30fps 上限只挡住"上传"，挡不住
"解码"——60fps 的源每一帧都要解出来才能取到该显示的那一帧。所以 4K60 的壁纸在软解
（约 95 fps）上勉强、硬解（约 68 fps）上跟不上，会表现为慢放或周期性重新对齐。

### 硬件解码是可选的

先按 D3D11VA 打开（设备建不出来时 shim 自己退回软解），解到一半返回负值就用软解
**重开接着放**（最多 2 次，之后再失败就判这个文件放不了）。这条降级路径**实测会被
走到**：仓库里那段 320x240 的 baseline H.264 素材在 D3D11VA 上第一帧就返回
`AVERROR_INVALIDDATA`（-1094995529），而软解完全正常。因此解码探测也是"硬件解不出
第一帧就再用纯软件试一次"，否则一个能放的文件会被判成放不了。

### 解不出第一帧就降级

探测（`BackgroundVideo.probe`）在后台线程做四件事：挡住明显不是 mp4 的文件（自己看
box 头）、用 `Mp4Probe` 扫一眼盒子结构（有没有视频轨道、什么编码、多少帧）、**真的
打开解码器**、**真的解出第一帧**。任何异常都在这一层被翻译成 `Reason` 枚举，
**不会漏给渲染线程**：

| 情形 | `Reason` | 卡片 / 状态栏 |
| --- | --- | --- |
| 文件不在、目录被删 | `MISSING` | 「视频壁纸不能播放：文件不在了」 |
| 0 字节 | `EMPTY_FILE` | 「…文件是空的」 |
| 不是 MP4（WebM / MKV / 随机字节，扩展名改了也算） | `NOT_MP4` | 「…不是 MP4 文件」 |
| MP4 里没有视频轨道 | `NO_VIDEO_TRACK` | 「…MP4 里没有视频轨道」 |
| 编码这套构建没有（VP8 / ProRes），或者只有硬解的 AV1 在这台机器上开不出来 | `UNSUPPORTED_CODEC` | 「…这个编码本版放不了（实际：AV1）」 |
| 没有样本 / 解不出第一帧 / 解码中途坏了 | `NO_FRAME` / `DECODE_FAILED` | 「…文件损坏，或者解不出第一帧」 |

`Reason` 枚举与旧版一一对应，选择界面的文案表没有改；只有
`wurst.background.video_codec` 那句话从「编码不是 H.264」改成了「这个编码本版放不了」
——后者现在才成立。

这里有一条**踩过坑的约束**：第一次尝试（硬解优先）**成功**那条路必须显式返回。
原来的写法是单分支——`if(probe == null){…软解重试…}` 之后不管成败直接
`return diagnose(track, attempt.failure())`——于是硬解一次就成功的文件带着**空原因**
进诊断，实机上**每一个**视频都被判成 `DECODE_FAILED / avc1 / `（原因栏是空的），
而解码器其实完全正常。现在的写法是先 `if(attempt.probe() != null) return ...`，
软解重试只在第一次真的失败时才跑。回归用例见第 8 节。

失败时的行为是固定的三条：**退回内置默认背景**、把这个条目记进 `failedId`
（不再每帧重试）、日志里留**一行** `[Background] 视频背景 <id> 不能播放：<原因>`。
放到一半坏掉（文件被删、样本损坏、解码器挂了）走的是同一条路：解码线程标记
`failed()`，`render()` 发现后 `forget()` 并退回内置背景，同样只记一行。

### 选择界面为什么要先探测

`canPlay()` 拿不到文件路径，所以它只能回答「这个**类型**本版能不能放」——
现在四种都能。**某个视频文件**能不能放是文件的属性，由 `probe(Path)` 决定：

- 卡片渲染时按条目 id 发起一次异步探测并缓存结果（和缩略图同一套做法，
  画卡片不会每帧读磁盘）。探测期间徽章是「视频 · 检测中…」。
- 能放 → 徽章只写「视频」，点一下就选中；放不了 → 「视频 · 不能播放」，
  点击在状态栏里说明具体原因，**不允许选中**（否则管理器会退回内置背景，
  看起来就是「点了没反应」）。
- 注意工坊扫描那句「可播放 N 张」是按 `project.json` 的类型算的（`Candidate.note`
  为空即算可播放），**不是探测结果**：一个 HEVC 的视频壁纸仍会被算进那一栏，
  要等卡片上的探测、或者真的选中时才会被挡住。

### 颜色与所有权

- **不需要通道交换，也不需要翻转行序。**shim 交出来的是紧密排列的 8 位 RGBA
  （内存字节序 R,G,B,A）、行序自上而下，与 `NativeImage.Format.RGBA` 完全一致。
  这一点是实测过的，不是照抄旧路径：同一段 mp4 用旧的 JCodec 路径与新的 FFmpeg
  路径各解一遍、逐像素比对——**正着放平均差 2.6（三通道合计），上下翻转 450.8、
  左右翻转 461.6、红蓝互换 217.7**。旧的 `argbToAbgr()` 是为了补
  `BufferedImage.getRGB()` 的打包 ARGB，与行序无关，所以随 JCodec 一起删掉了。
- **逐像素写进 `NativeImage`**：`NativeImage` 没有公开底层指针，唯一的批量入口
  `applyToPixelsInPlace` 也是逐像素回调，所以逐像素 `setPixelRGBA` 就是最快的公开
  写法（实测 1080p 1.7ms/帧、1440p 3.4ms/帧，已经算在上面那张端到端表里）。
- 纹理自己持有一张图像，视频那边按目标尺寸持有自己的复用缓冲，每帧 `copyFrom`
  拷过去——理由与 GIF 相同：`DynamicTexture` 会关掉交给它的 `NativeImage`。
- `close()` **不等解码线程**：只中断，然后把收尾交给一个后台线程，等解码线程真的
  退出之后再关缓冲。理由有两层：解码线程可能正卡在一次很长的解码里，而 `close()`
  是从渲染线程调的（切换背景、删除、资源重载都是），在那里 `join(2s)` 就是画面卡
  两秒；而缓冲那几十 MB 晚几毫秒释放没有任何影响。等不到就只打印一行并放弃回收
  ——解码线程还卡在解码器里时关掉图像会和它正在写的像素撞车，那会把客户端
  直接崩掉，漏显存比崩掉划算。

## 7. 还没做的（如实说明）

1. **视频只支持 MP4 容器**：能放 H.264 / HEVC / VP9 / MPEG-4 Part 2 / Motion JPEG，
   **AV1 需要显卡支持**（这套构建没有软件 AV1 解码器）；VP8 与 ProRes 没编进来；
   WebM / MKV / AVI 虽然 FFmpeg 自己能解复用，但本版没接线（扩展名也只收 `.mp4` /
   `.m4v`）。**音轨一律忽略**。
2. **带旋转矩阵的视频会横过来显示**：旧的 JCodec 路径会按 `tkhd` 的旋转信息把画面
   转正，换到 FFmpeg 之后没有做这件事——这套构建没开 filter，shim 也不读 display
   matrix。要做的话有两条路：给 shim 加一次 `sws_scale` 之外的旋转（要改 native 并
   重新构建），或者在 Java 侧拷贝那一趟顺便转（像素索引换算，代价与现在同量级）。
3. **10 位 / HDR 没有测过**：转换路径是 8 位的，10 位源会走一次没验证过的降位转换。
4. **没有裁剪与对焦点设置**：构图完全由 `cover` 决定，长图只能看到中间那条。
5. **Wallpaper Engine 的网页 / 应用型壁纸**只导入其**预览图**并在卡片上注明原因
   （`Candidate.note`）。Scene 型壁纸不再是这一类：它的场景包会被导入并按图层画出来
   （见 [`wallpaper-engine-scene.md`](wallpaper-engine-scene.md)），但**只画静态
   图层**——效果着色器、粒子、时钟与音频都还没有。
6. **动图只有整个界面可见时才推进**：标题界面不显示时不会后台跑帧，重进界面会按
   挂钟时间直接跳到该显示的那一帧（不做补帧）。视频同理，而且更彻底：不显示超过
   一秒解码线程就停下（不是空转），重新显示时接着往下放。
7. **视频卡片没有预览图**：`NativeImage` 读不了 mp4，ImageIO 也不行，所以工坊导入
   的视频卡片只有徽章没有缩略图（GIF 那条路会取第一帧，视频这条路还没接）。
8. **没有「打开背景文件夹」入口**，删库只能靠文件管理器。

## 8. 验证状态（诚实声明）

- 只做了编译与单元测试：`BackgroundMotionTest`（四种运镜的姿态、边界与 `strengthFor`
  缩放）、`BackgroundAnimationTest`（帧时钟的边界、循环、有限次循环后停在末帧、
  退化延迟规则）、`GifFramesTest`（**处置方式与透明混合逐条**、帧矩形裁剪、解码预算
  的缩放公式、以及用 ImageIO 现写一个带动画 GIF 再读回的端到端用例，含 Netscape
  循环次数）、`BackgroundStorageTest`（导入 / 列举 / 读回 / id 安全 / 删除）、
  `VdfParserTest`、`WallpaperEngineImporterTest`（视频 / 图片 / 场景型壁纸、畸形
  `project.json`、上限与排序）。全部**不依赖 Minecraft 运行时**，也不联网。
- **视频那部分的验证比其它部分强**（`VideoPacingTest` 13 例、
  `BackgroundVideoTest` 24 例、`Mp4ProbeTest` 10 例、
  `FfmpegVideoDecoderNativeTest` 6 例、`FfmpegNativesTest` 5 例）：
  - 纯算术：帧率上限的丢帧边界、脱节判定、坏时长的循环取模、上传尺寸策略
    （按绘制尺寸 / 上限 2560x1440 / 永不放大 / 没有窗口时的兜底）；
  - 失败路径：文件不存在 / 0 字节 / 文本 / WebM 改名成 `.mp4` / 只有 `ftyp` 的
    MP4，**逐条断言 `Reason` 且都不抛异常**；
  - 盒子扫描：现搭的 mp4 结构（视频轨道在音轨之后、64 位长度字段、长度写 0、
    截断、随机字节、全零）都要给出正确的编码 / 帧数 / "读不通"，且不转圈；
  - **端到端（真解码）**：素材是随仓库发布的 `src/test/resources/.../fixture-*.mp4`
    （四段共约 262 KB；旧版本用 JCodec 的编码器现编，换掉 JCodec 之后编不出新素材了
    ——这套构建只带解码器）。四象限素材（左上红 / 右上绿 / 左下蓝 / 右下白）逐象限
    断言"哪一路占优"，红蓝写反或行序翻转都会红；另有元数据检查（`avc1` / 320x240 /
    4 帧 / 133ms）与 `seekToStart` 之后能再解一遍；**第四段是 720p**
    （`fixture-hd.mp4`，1280x720 / 30 帧 / 1 秒），专门用来走"硬解第一次就成功"
    那条分支——320x240 那三段天生到不了那里，这正是上面那个 bug 能溜过测试的原因；
  - **播放实测（无客户端）**：把 `BackgroundVideo` 真的跑起来——30fps 的源在 1.5 秒
    里收到 20~45 帧（**30fps 上限**生效；上限没了会远超 60），`close()` 立即返回；
    **背景不显示时解码线程真的停下**：停 2.5 秒不取帧之后，再 2 秒里只多解了 ≤5 帧
    （没停的话 30fps 的源会解 60 帧上下），而且重新显示后还能接着放；
    **窗口尺寸变化**：把"绘制尺寸"从 1280x720 改成 160x120 再改回来，视频的尺寸
    跟着换、交出来的帧与新尺寸一致、旧池的缓冲被安全回收；
  - **硬件降级的实测**：那段 320x240 素材在 D3D11VA 上第一帧就返回
    `AVERROR_INVALIDDATA`，播放用例能通过本身就证明"硬件失败 → 重开软解 → 接着放"
    这条路径真的走通了。
- **视频背景已经在实机跑通**（2026-10-06，dev 客户端 1296x672，Forge 1.20.1）：4K
  （源 3840x2160、369 帧 / 23.0 秒）的 H.264 壁纸正常播放，日志里只有一行
  `[Background] Video <id>: 1195x672 (源 3840x2160), 369 帧 / 23062ms, 上限 30fps`
  ——上传尺寸跟着窗口走（就是窗口的帧缓冲尺寸），不再是旧的 1280x720 上限，所以不糊；
  选择界面里视频卡片全部显示「视频」（可播放），**没有一张是「不能播放」**
  （[background-video-ingame.png](background-video-ingame.png)、
  [background-video-badges.png](background-video-badges.png)）。
  仍然**没有**实机验证的：旋转视频、10 位 / HDR、AV1、播放时的 CPU 占用，以及
  "资源重载之后重新加载"。
- **拖动窗口已经实机验证**（2026-10-06，同一台机器）：把窗口依次改成
  1000x560 / 1536x800 / 800x620 / 1296x672，解码尺寸每次都在**一帧内**跟上
  （996x560 / 1422x800 / 800x450 / 1195x672），四张截图画面都是完整、正确 cover
  裁切的视频帧（[background-video-resize.png](background-video-resize.png) 是
  800x620 那张：屏幕 1.29:1、纹理 16:9，裁得最狠的一张）。修之前 1536x800 右边会
  留一条没被覆盖的残留、800x620 整幅撕裂。
- **视频"每隔几秒抖一下"**：这一条**只做到部分定位**，如实说明。能确定的是解码
  侧没有问题——用假的 60fps 渲染循环把 `超时空辉夜姬4k`（4K HEVC、23.976fps、
  2.37 小时）跑满 20 秒，**479 帧 / 23.9 fps、平均间隔 41.8ms（正好等于帧间隔
  41.708ms）、0 次重新对齐**；实机加打点跑了整场也只记录到一次 >90ms 的交帧间隔
  （开机第一帧）。但上面那个 swscale 的坑会**在缩小输出时往 Java 堆里写过 1.7 MB**，
  这种损坏完全可以表现为不定期的卡顿——所以先把那个修掉了，**没有单独复现出抖动
  本身**，也没有在修后长时间观察确认它不再出现。如果还会抖，下一步该量的地方是
  渲染侧每次 `texture.upload()` 的间隔（这次用的打点方式：
  `[Background]`/`[dbg]` 行 + `advanceVideo` 里的间隔计时）。

- **实机曾经整批误判成「不能播放」，原因不在解码器**：`BackgroundVideo.probe` 里
  **成功那条路漏了返回**——原来的写法是 `if(probe == null){…软解重试…}` 之后不管
  成败都落到 `diagnose(track, attempt.failure())`，于是"硬解第一次就成功"的文件
  带着**空原因**进了诊断，卡片与日志一律显示 `DECODE_FAILED / avc1 / `，看起来就像
  解码器全坏了（native 其实一切正常：7 个 DLL 哈希一致、`ensure()` 返回 true、
  `nativeOpen` 空错误、硬解 60~190 fps）。
  单元测试没抓到它，是因为三段素材都是 **320x240**，而这个尺寸的 D3D11VA 在第一帧
  就失败（见上文），测试跑的其实是"硬解失败 → 软解重试成功"这条**能**正确返回的
  分支。现在补了 720p 的 `fixture-hd.mp4`（实测 `hardware: d3d11va (ACTIVE)`）与
  回归用例 `BackgroundVideoTest.probesAnHdFixtureThatHardwareCanDecode`：**把 bug
  放回去，这条用例就会红，失败信息正好是 `DECODE_FAILED / avc1 / `**（已实测）。
  另外 `libraryFailure()` 的兜底文案从「解码器打不开」改成「原因不明（解码库正常）」
  ——旧文案会在库明明正常时也写进日志，把排查方向整个带偏。
- **标题界面与选择界面已经在实机验证过**：场景壁纸渲染、缩略图卡片、圆角对话框与
  cover 裁切都跑通了（[background-select-ingame.png](background-select-ingame.png)）。
  滚动条、删除与导入这些交互仍只做过签名核对（视频相关的交互同样只做过签名核对）。
- **真实 Wallpaper Engine 库已经跑过一次**：在用户这台机器上扫描 Steam 库并导入了
  多个工坊条目（含中文标题的），其中暴露并修掉了一个崩溃——工坊条目 id 由标题派生，
  中文标题会让 `ResourceLocation` 抛异常把客户端崩掉（
  `crash-2026-10-05_15.15.30`），现在贴图名会先洗字符再加哈希
  （`BackgroundSelectScreen.thumbnailTextureName`，回归测试
  `BackgroundThumbnailNameTest`）。

场景那部分（`WePackage` / `WeTexture` / `WeScene`）**已经在真实的 14 MB 场景包上跑通**
（`WeSceneRealFileTest`），格式推导过程与仍未实现的部分单独记在
[`wallpaper-engine-scene.md`](wallpaper-engine-scene.md)。
