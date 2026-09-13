# Twilight Echo 端口 · 接线状态

参考项目 `Px-asen/Twilight_Echo` v1.2.1（Apache-2.0）。本文件只记录**接线**
（谁调用谁、数据从哪来），布局与视觉规格见 `fidelity-map.md`，数据层见
`data-layer.md`。

## 1. 已经接上的

| 位置 | 内容 |
| --- | --- |
| `net.wurstclient.twilight.TwilightShellScreen` | 应用外壳 + 主页 + 内容页，全部几何来自 `TwilightShellLayout` / `TwilightHomeLayout` / `TwilightListLayout` |
| 播放器 | 复用进程单例 `NeteaseMusicPlayer.INSTANCE`（枚举），不是新建引擎 |
| 事件 | `init()` 订 `PlayerListener`，`removed()` 退订；监听器只拷贝基本类型，纹理只在渲染线程取 |
| 生命周期 | `init()` 会因窗口缩放重入，所以先 `removeListener` + `close()` 旧的 `NeteaseImageCache` 与 `TwilightMusicService` 再重建，避免泄漏纹理、线程与重复回调 |
| 交互 | 播放/暂停、上一首、下一首、进度条点击跳转、hero「播放全部」、歌曲行点击起播、歌单卡片点击起播、侧栏切页 |
| 主题 | 封面取色（`NeteaseImageCache.Texture.accent()` → `TwilightAccent.fromAccent`）驱动全局强调色；封面未下载完时约每 40 帧重试一次 |
| 文本 | 播放条曲名/歌手/时间来自播放器实时状态；页头标题随侧栏切换 |
| 封面真图 | 在 `TwilightSkia.end(graphics)` 之后用 `GuiGraphics.blit(...)` 把纹理贴到同一批矩形（播放条 / 主页歌单行 / 内容页行 / 歌单卡片 / 沉浸页大封面），裁剪按 CSS `object-fit: cover` 用 `TwilightCoverFit.sourceRect` 计算 |
| 封面圆角 | `TwilightCoverCache` + `TwilightCornerMask`：圆角**烘进纹理的 alpha**（4×4 超采样抗锯齿），而不是靠裁剪——`clipRoundRect` 只在 Skia region 内有效，原版 scissor 只能裁矩形，用背景色补角在玻璃/渐变背景上必然出错。蒙版在源分辨率生成、半径按绘制尺寸缩放，所以 44px 和 300px 的同一张封面圆角看起来一致，且一屏小封面共用一张蒙版纹理；下载、解码与取色仍由 `NeteaseImageCache` 负责，强调色与其它界面完全一致。为避免一屏新封面同时生成卡帧，每几毫秒最多生成一张，未生成前先画未圆角的纹理 |
| 平滑滚动 | 滚轮半行一步：整行进位到 `scrollRows`，不足一行的留在 `scrollSub`，绘制时把行矩形整体上移 `scrollOffset()`（`shifted()`），悬停/点击再把偏移加回鼠标 Y。列表在 Skia region 内用 `save()` + `clipRoundRect(...,0)` 裁剪，封面因为走原版 blit，改用 `enableScissor` 裁剪；到顶/到底时清掉不足一行的余量 |
| 沉浸播放页 | 点击播放条封面进入；自绘渐变背景 + 300px 大封面 + 曲名/歌手 + 返回/上一首/播放/下一首/进度/时间；ESC 先退出沉浸页再关界面。**歌词由现有 `AppleLyricPlayer`（类苹果 AMLL）绘制**，驱动方式照搬 `MusicLyricsHudElement`：每首歌喂一次 `setLyricLines`，然后 `setContentWidth/setContainerHeight/setPlaying/setCurrentTime/update/render`，并用 `enableScissor` 裁剪 |
| 歌单详情页 | 点歌单卡片**不再直接起播**，而是进详情页：封面 + 名称 + 曲目数 + 「播放全部」+「返回」，下面是共用行渲染器的曲目表。曲目区用 `detailListArea()` 把内容区下移 96px，避免行压到头部；ESC /「返回」/切侧栏都会退出详情页 |

## 2. 数据层：优先本地增强服务，失败回退直连

新增 `net.wurstclient.twilight.TwilightApiEndpoint` + `TwilightMusicService`，
按 `data-layer.md` 的**架构 A** 落地。

- `TwilightApiEndpoint`：基址归一化（`127.0.0.1:3000` → `http://127.0.0.1:3000`）、
  端点常量、UTF-8 查询编码。无 MC/Skia/HTTP 依赖，可单测。
- `TwilightMusicService`：真正发请求的那一半，说
  **NeteaseCloudMusicApiEnhanced** 的 REST 协议。无 MC 依赖，解析部分可单测。
- **为什么是第二个客户端而不是改造 `NeteaseCloudApi`**：现有
  `NeteaseCloudApi` 走的是 `music.163.com` 的**直连加密** weapi/eapi 端点，与
  本地服务的 REST 协议是两套东西，无法"改基址"了事。两者靠**歌曲 id** 与
  **登录 cookie** 对齐。
- 取数顺序：`TwilightShellScreen.load(...)` 先问本地服务，**服务返回空或不可达
  时才回退**到 `NeteaseMusicPlayer` 的直连方法。没有装本地服务的玩家行为与之前
  完全一致。状态栏会标出这次是「（本地增强服务）」还是「（直连）」。
- 已接的取数：每日推荐 `/recommend/songs`、推荐歌单 `/personalized`、
  歌单曲目 `/playlist/track/all`（回退 `/playlist/detail`）、音乐库 `/likelist`
  + `/song/detail`（回退 `/liked/list`）、搜索 `/cloudsearch`、私人漫游
  `/personal_fm`。
- 响应解析对**信封差异**容忍：`data.dailySongs` / `result.songs` / `data` 逐个
  尝试，取第一个数组；单条畸形记录只丢那一条，不空白整页。

## 3. 扫码登录

页头右侧「扫码登录」胶囊（未登录）/ 昵称胶囊（已登录）打开模态弹层。

- 流程：`/login/qr/key` → `/login/qr/create?qrimg=true` → 每 40 帧
  `/login/qr/check`；`800` 过期自动换一张，`802` 提示手机确认，`803` 成功后把
  `/login/qr/check` 返回的 cookie 交给
  `NeteaseMusicPlayer.loginWithCookie(cookie)`，让直连 API 与本地服务登录到**同一
  个账号**。
- 二维码**不需要自带编码器**：服务把 PNG 作为 `data:image/png;base64,...` 一起
  返回，这里只做 base64 → `NativeImage.read` → `DynamicTexture` →
  `TextureManager.register`，用 `GuiGraphics.blit` 画（`setFilter(false,false)`
  保持最近邻，避免缩放糊掉扫码）。纹理在 `removed()` 里 `release`。
- 弹层用原版矩形绘制、在所有内容之后画，因此 Skia 路径与兜底路径都能显示，且一定
  在最上层；ESC 或点击面板外关闭，打开时吞掉点击与滚轮。
- **未做**：手机验证码 / 邮箱登录（参考里有三种，这里只做了扫码）。

## 4. 入口替换（3/3 完成）

- `net/wurstclient/hacks/MusicPlayerHack.java`
- `net/wurstclient/clickgui2/component/SuperSoftClickGuiScreen.java`
- `net/wurstclient/clickgui2/epsilon/EpsilonDropdownScreen.java`

三处均改为 `new TwilightShellScreen(...)`（带父界面，ESC 返回）。`.twilight`
命令同样打开新界面。旧类 `clickgui2/screens/NeteaseMusicScreen.java` 仍在仓库中，
但已无入口引用，所以退出登录改由新界面的账号弹层承担（已登录时弹层就是账号面板，
里面有「退出登录」，退出后立刻换一张新二维码）。

`gui/visual/VisualScreenMotion` 的 `SELF_ANIMATED_SCREENS` **故意不改**：新界面
没有自绘转场，交给通用淡入即可。

## 5. 还没接的

1. **中文输入法**：搜索框只收 `charTyped`，没有 IME 支持，无法输入中文关键词。
   这是唯一一个已知的、不引入文本输入控件就无法补齐的功能，其余布局与交互都已
   对齐参考。
2. **手机验证码 / 邮箱登录**：只做了扫码（目标要求的正是扫码登录）。
3. **歌单详情的元数据**：详情页显示名称、曲目数与播放量（按网易云习惯压成万/亿），
   没有参考里的简介与创建者——`NeteasePlaylist` 只有
   `id / name / coverUrl / playCount` 四个字段，要补就得先扩数据层。

## 6. 验证状态（诚实声明）

- 只做了编译 + 单元测试：`gradlew compileJava compileTestJava test --offline`
  → `BUILD SUCCESSFUL`，123 个测试类 / 663 个测试 / 0 失败
  （`TwilightMusicServiceTest` 12 个、`TwilightApiEndpointTest` 4 个、
  `TwilightCornerMaskTest` 11 个，全部是纯解析 / URL / 蒙版数学测试，**不联网**）。
- **从未在游戏里渲染或试听过**：所有 Skia 调用、原版 `blit`、`enableScissor`、
  `NativeImage` / `DynamicTexture` / `getPixels` 用法只做过签名核对与编译验证。
- **从未连过真实的 NeteaseCloudMusicApiEnhanced 服务**：端点路径、信封字段名、
  二维码返回格式都是按协议与 `data-layer.md` 写的，并且刻意做成"多种信封都试、
  失败就回退"，但仍可能与该服务的实际返回有出入。
- 无法移植的桌面能力（液态玻璃折射、大面积 backdrop 模糊、任意字重中文、
  WASAPI 独占 / DSD / ASIO / VST3 / 托盘 / 全局媒体键 / 桌面歌词 / 投屏 / DLNA 等）
  见 `fidelity-map.md` §0 与 §4。
