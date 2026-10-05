# 标题界面自定义背景

> 范围：v1.6 根目录 Forge 1.20.1 工程。功能入口在主菜单右上角，代码在
> `net.wurstclient.background`（25 个类）+ `net.wurstclient.gui.title.BackgroundSelectScreen`。
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
| `BackgroundVideo` / `VideoPacing` | 视频背景：JCodec 解 mp4/H.264。**流式**——解码线程按挂钟一次解一帧，帧在 3 个复用缓冲里交接给渲染线程；`VideoPacing` 是纯算术的播放规则（帧率上限、脱节判定、循环取模），可单测。见第 5 节 |
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

## 6. 视频（MP4 / H.264）播放

依赖是 **`org.jcodec:jcodec:0.2.5` + `org.jcodec:jcodec-javase:0.2.5`**（纯 Java，
没有原生库，随 `jarJar` 一起打进产物；`gradle.properties` 里的 `jcodec_version`）。
两个都要：解码器与容器模型在 `jcodec`，MP4 解复用与 `org.jcodec.api.awt.AWTFrameGrab`
在 `jcodec-javase`。**本地 Gradle 缓存里原本没有这两个坐标**，是联网拉下来之后
`--offline` 才能解析的。

### 支持与不支持

| | 结果 |
| --- | --- |
| MP4 容器 + `avc1`（H.264）+ 8 位 4:2:0 | **能放** |
| HEVC（`hvc1` / `hev1`）、VP9（`vp09`）、VP8、AV1（`av01`）、MPEG-4 Part 2（`mp4v`）、ProRes、Motion JPEG | 放不了，卡片上写明编码名 |
| `avc3`（参数集在码流里的 H.264） | 放不了：这套解码器只读 `avcC` 盒子 |
| WebM / MKV / 其它容器 | 放不了：本版只认 MP4（扩展名也只收 `.mp4` / `.m4v`） |
| 音轨 | **完全忽略**。视频背景没有声音，将来也不打算有 |
| 10 位 H.264（High 10） | 大概率放不了（`AWTUtil` 的像素转换覆盖不到），会落到「解不出第一帧」 |

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

- **尺寸**：按最长边缩到 **1280x720** 以内，保持宽高比，比这更小的一律不放大。
  解码开销由源分辨率决定（JCodec 按源尺寸解），但 YUV→RGB、缩放与纹理上传都由
  目标尺寸决定，4K 源按原尺寸走光上传一帧就是 33 MB。尺寸取解码首帧的
  **裁剪后**尺寸（1080p 的编码高度常常是 1088，直接用 coded size 会多出一条黑边），
  带旋转信息的手机视频会把宽高换过来。
- **帧率上限 30fps**：距上一帧不足 1/30 秒的帧**解但不显示**（H.264 不能跳着解，
  但可以不转换、不上传）。60fps 的源就是隔一帧丢一帧。判定留了 1 毫秒取整余量，
  所以实测是 **31/s 上下**而不是正好 30——30fps 的源时间戳取整后会落在 33ms 上，
  不留余量会因为这点误差丢掉一半的帧。

### 时间以挂钟为准

每帧的 PTS 换算成「绝对显示时刻」（`轮起点 + PTS`），规则集中在 `VideoPacing`
（纯算术，可单测）：

- **该显示就显示、太近就丢、没到点就等**（`decide`），一轮放完把实测的一轮时长
  记下来（元数据里的时长未必准）再从头放，循环处不会出现时间倒流。
- **落后挂钟超过 1.5 秒就跳一次**（`seekToSecondSloppy` 到挂钟位置，再把关键帧到
  目标之间的帧解掉但不显示）。会走到这里的两种情况：界面隐藏一段时间后重新显示
  （这期间渲染线程不来取帧），以及解码速度跟不上实时。**绝不把落下的几百帧
  一帧帧解完**，那是几十秒的追赶，画面等于卡死。
- 某一帧的 PTS 远在未来（坏时间戳）时最多等 5 秒，等不满也走同一条定位路径：
  少数壁纸确实有长达数秒的静止段（PTS 上就是一个大空档），那种情况等下去是对的，
  两者只能靠这个上限区分。连续定位 8 次还没恢复播放就判定这个文件放不了。

### 性能预期（如实说明）

JCodec 是纯 Java 解码器，比原生解码慢得多，而且**解码量由源分辨率与帧率决定，
不受 720p / 30fps 上限保护**：60fps 的源要把每一帧都解出来才能取到该显示的那一帧。
预期是：**720p 以下的壁纸流畅，1080p30 大致可用，1080p60 / 4K 很可能跟不上**
（表现为掉帧、慢放，或按上面的规则周期性跳帧以保持与挂钟同步）。
上述判断**没有在实机上量过**——这台机器上只跑到了编译、单测与下面记录的
无客户端播放实测。

### 解不出第一帧就降级

探测（`BackgroundVideo.probe`）在后台线程做三件事：挡住明显不是 mp4 的文件
（自己看 box 头的 fourcc，好给出准确的错误）、让 JCodec 建出取帧器（不是 H.264 的
编码在这里被它拒掉）、**真的解出第一帧**。任何异常都在这一层被翻译成 `Reason`
枚举，**不会漏给渲染线程**：

| 情形 | `Reason` | 卡片 / 状态栏 |
| --- | --- | --- |
| 文件不在、目录被删 | `MISSING` | 「视频壁纸不能播放：文件不在了」 |
| 0 字节 | `EMPTY_FILE` | 「…文件是空的」 |
| 不是 MP4（WebM / MKV / 随机字节，扩展名改了也算） | `NOT_MP4` | 「…不是 MP4 文件」 |
| MP4 里没有视频轨道 | `NO_VIDEO_TRACK` | 「…MP4 里没有视频轨道」 |
| 编码不是 H.264 | `UNSUPPORTED_CODEC` | 「…编码不是 H.264（实际：HEVC/H.265）」 |
| 没有帧 / 解不出第一帧 | `NO_FRAME` / `DECODE_FAILED` | 「…文件损坏，或者解不出第一帧」 |

失败时的行为是固定的三条：**退回内置默认背景**、把这个条目记进 `failedId`
（不再每帧重试）、日志里留**一行** `[Background] 视频背景 <id> 不能播放：<原因>`。
放到一半坏掉（文件被删、样本损坏）走的是同一条路：解码线程标记 `failed()`，
`render()` 发现后 `forget()` 并退回内置背景，同样只记一行。

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

- 颜色转换交给 **JCodec 自己的 `AWTUtil`**：YUV→RGB 的矩阵是跟着它的解码器调的
  （视频范围、chroma 上下采样、裁剪矩形、旋转）。自己写一遍只能省一次拷贝，
  却很容易把颜色或边缘搞错。代价是每个显示的帧会多一张源尺寸的 `BufferedImage`
  垃圾，这是那个接口决定的。
- 之后仍要做 **ARGB → ABGR** 的红蓝交换（`NativeImage` 的整数像素接口是 ABGR），
  与 GIF 那条路完全一样，写错就是一张颜色反过来的壁纸。
- 纹理自己持有一张图像，视频那边按目标尺寸持有自己的复用缓冲，每帧 `copyFrom`
  拷过去——理由与 GIF 相同：`DynamicTexture` 会关掉交给它的 `NativeImage`。
- `close()` **不等解码线程**：只中断，然后把收尾交给一个后台线程，等解码线程真的
  退出之后再关缓冲。理由有两层：解码线程可能正卡在一次很长的解码里，而 `close()`
  是从渲染线程调的（切换背景、删除、资源重载都是），在那里 `join(2s)` 就是画面卡
  两秒；而缓冲那几 MB 显存晚几毫秒释放没有任何影响。等不到就只打印一行并放弃回收
  ——解码线程还卡在 JCodec/AWT 里时关掉图像会和它正在写的像素撞车，那会把客户端
  直接崩掉，漏显存比崩掉划算。

## 7. 还没做的（如实说明）

1. **视频只支持 MP4 里的 H.264**：HEVC / VP9 / AV1 / WebM / MKV 都放不了（卡片上
   会写明是哪种编码），**音轨一律忽略**。`avc3` 与 10 位 H.264 也在放不了之列，
   原因见上一节。
2. **没有裁剪与对焦点设置**：构图完全由 `cover` 决定，长图只能看到中间那条。
3. **Wallpaper Engine 的网页 / 应用型壁纸**只导入其**预览图**并在卡片上注明原因
   （`Candidate.note`）。Scene 型壁纸不再是这一类：它的场景包会被导入并按图层画出来
   （见 [`wallpaper-engine-scene.md`](wallpaper-engine-scene.md)），但**只画静态
   图层**——效果着色器、粒子、时钟与音频都还没有。
4. **动图只有整个界面可见时才推进**：标题界面不显示时不会后台跑帧，重进界面会按
   挂钟时间直接跳到该显示的那一帧（不做补帧）。视频同理，而且更彻底：不显示超过
   一秒解码线程就停下（不是空转），重新显示时按挂钟定位。
5. **视频卡片没有预览图**：`NativeImage` 读不了 mp4，ImageIO 也不行，所以工坊导入
   的视频卡片只有徽章没有缩略图（GIF 那条路会取第一帧，视频这条路还没接）。
6. **没有「打开背景文件夹」入口**，删库只能靠文件管理器。

## 8. 验证状态（诚实声明）

- 只做了编译与单元测试：`BackgroundMotionTest`（四种运镜的姿态、边界与 `strengthFor`
  缩放）、`BackgroundAnimationTest`（帧时钟的边界、循环、有限次循环后停在末帧、
  退化延迟规则）、`GifFramesTest`（**处置方式与透明混合逐条**、帧矩形裁剪、解码预算
  的缩放公式、以及用 ImageIO 现写一个带动画 GIF 再读回的端到端用例，含 Netscape
  循环次数）、`BackgroundStorageTest`（导入 / 列举 / 读回 / id 安全 / 删除）、
  `VdfParserTest`、`WallpaperEngineImporterTest`（视频 / 图片 / 场景型壁纸、畸形
  `project.json`、上限与排序）。全部**不依赖 Minecraft 运行时**，也不联网。
- **视频那部分的验证比其它部分强，但仍然不是实机验证**（`VideoPacingTest` 13 例、
  `BackgroundVideoTest` 18 例）：
  - 纯算术：帧率上限的丢帧边界、脱节判定、坏时长的循环取模、缩放尺寸与宽高比、
    ABGR 换算（含对合性）；
  - 失败路径：文件不存在 / 0 字节 / 文本 / WebM 改名成 `.mp4` / 只有 `ftyp` 的
    MP4，**逐条断言 `Reason` 且都不抛异常**；
  - **端到端**：用 JCodec 自带的编码器**现编**一段 mp4（仓库里不放任何视频文件），
    再走真实的探测与解码路径——首帧颜色在 ±24 以内（红蓝写反会差 160，抓得住）、
    时间戳单调不减、元数据（`avc1` / 320x240 / 4 帧 / 133ms）正确；
  - **播放实测（无客户端）**：把 `BackgroundVideo` 真的跑起来，640x360 / 60 帧 /
    30fps 的源在 3 秒里收到 **93 帧（≈31/s，与 30fps 上限加取整余量相符）**，
    循环点对得上（第 2553ms 的帧与第 556ms 的帧像素相同），`close()` 立即返回
    （0ms），坏文件得到 `NOT_MP4`。
  - **空闲暂停也量过**：200 帧的源播到第 22 帧后停止取帧 2.5 秒，帧号只走到
    **53（前进 31 帧 ≈ 1 秒的宽限期）**；如果解码没停，2.5 秒应当前进 75 帧左右。
    重新开始取帧后同一秒里追上了挂钟（定位后有一段追赶，帧率会短暂高于 30fps）。
- **没有启动过 Minecraft 客户端**：视频背景在真实客户端里的表现（标题界面上的观感、
  与运镜叠加、资源重载后重新加载、切换背景时解码线程的收尾）**一次都没有实机跑过**，
  上面那些数字全部来自无客户端的进程内实测。1080p60 / 4K 的性能也**没有量过**。
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
