# 标题界面自定义背景

> 范围：v1.6 根目录 Forge 1.20.1 工程。功能入口在主菜单右上角，代码在
> `net.wurstclient.background`（13 个类）+ `net.wurstclient.gui.title.BackgroundSelectScreen`。
> 本文记录**接线与实现**，以及**哪些能力只是「导入并标记」而并没有真正生效**。

## 1. 组成

| 位置 | 职责 |
| --- | --- |
| `BackgroundManager` | 客户端级单例。持有选择、纹理与加载状态，负责整屏绘制。用**一个固定纹理 id**（`wurst:title_background`）复用同一个注册槽位，切换背景不会泄漏纹理注册 |
| `BackgroundStorage` | 磁盘库：`<wurst 文件夹>/backgrounds/<id>/media.<ext>` + `thumbnail.png` + `meta.json`。刻意不含 Minecraft 类型，可对临时目录单测 |
| `BackgroundEntry` / `BackgroundKind` | 列表条目与媒体类型（`IMAGE` / `GIF` / `VIDEO`，按扩展名判定）；内置默认背景是**虚拟条目**，从不落盘 |
| `BackgroundThumbnail` | 列表卡片的预览图 |
| `BackgroundMotion` / `BackgroundPose` | 静图运镜：`NONE` / `KEN_BURNS` / `PARALLAX` / `DRIFT`，姿态是**时间、鼠标与视口的纯函数**（不依赖帧计数），所以掉帧时依然平滑，也能单测 |
| `BackgroundFilePicker` / `BackgroundFileChooser` | 跨平台文件选择（AWT `FileDialog` / `JFileChooser` 兜底） |
| `WallpaperEngineImporter` / `SteamLocator` / `VdfParser` / `ProjectJson` | Wallpaper Engine 导入：定位 Steam 库、解析 `libraryfolders.vdf` 与各壁纸的 `project.json`，产出候选列表（上限 500，可播放的排在前面） |
| `BackgroundSelectScreen` | 选择界面：默认背景卡片、已导入卡片（缩略图 / 标题 / 来源 / 类型徽章）、导入、Wallpaper Engine 导入、删除、运镜切换 |

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

## 5. 还没做的（如实说明）

1. **GIF / 视频背景不能播放。** `BackgroundKind` 认 `GIF` 与 `VIDEO`，导入流程会接受它们，选择界面还会给这类卡片打上「GIF」/「视频」徽章；但绘制路径只有 `NativeImage.read`（stb_image，只认 PNG/JPEG/BMP/TGA）。选中这类背景的结果是解码失败 → 记进 `failedId` → **回退到内置默认背景**，并且不会重试。这是当前最明显的缺口，先记在这里，不要当成已实现。
2. **没有裁剪与对焦点设置**：构图完全由 `cover` 决定，长图只能看到中间那条。
3. **Wallpaper Engine 的场景 / 网页 / 应用型壁纸**只导入其**预览图**并在卡片上注明原因（`Candidate.note`）；视频型壁纸导入 `project.json` 里的视频文件，但同样受第 1 条限制。
4. **没有「打开背景文件夹」入口**，删库只能靠文件管理器。

## 6. 验证状态（诚实声明）

- 只做了编译与单元测试：`BackgroundMotionTest`（四种运镜的姿态、边界与 `strengthFor` 缩放）、`BackgroundStorageTest`（导入 / 列举 / 读回 / id 安全 / 删除，针对临时目录）、`VdfParserTest`、`WallpaperEngineImporterTest`（视频 / 图片 / 场景型壁纸、畸形 `project.json`、上限与排序）。全部**不依赖 Minecraft 运行时**，也不联网。
- **从未在游戏里看过**：`blit` 的裁剪参数、`NativeImage` / `DynamicTexture` / `TextureManager` 的用法、以及标题界面上的实际排版都只做过签名核对与编译验证。`BackgroundSelectScreen` 的按钮命中、滚动与卡片布局同样没有实机验证。
- **从未在真实的 Wallpaper Engine 库上跑过**：`libraryfolders.vdf` 与 `project.json` 的字段按公开格式与 `WallpaperEngineImporterTest` 的样例解析，真实库的字段差异可能让某些壁纸被判为不可导入。
