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
| 封面真图 | 在 `TwilightSkia.end(graphics)` 之后用 `GuiGraphics.blit(...)` 把 `NeteaseImageCache` 的纹理贴到同一批矩形（播放条 / 主页歌单行 / 内容页行 / 歌单卡片 / 沉浸页大封面），裁剪按 CSS `object-fit: cover` 用 `TwilightCoverFit.sourceRect` 计算。**不是圆角**：blit 无法裁成圆角矩形，圆角仍由底下的占位方块提供 |
| 沉浸播放页 | 点击播放条封面进入；自绘渐变背景 + 300px 大封面 + 曲名/歌手 + 返回/上一首/播放/下一首/进度/时间；ESC 先退出沉浸页再关界面。**歌词由现有 `AppleLyricPlayer`（类苹果 AMLL）绘制**，驱动方式照搬 `MusicLyricsHudElement`：每首歌喂一次 `setLyricLines`，然后 `setContentWidth/setContainerHeight/setPlaying/setCurrentTime/update/render`，并用 `enableScissor` 裁剪 |

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
- 歌单详情页仍是当前队列（点卡片直接起播），见 §4。

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
但已无入口引用，因此**退出登录目前只能从新界面**——已登录时弹层就是账号面板，
但面板内还没有「退出登录」按钮（`PLAYER.logout()` 可用），这是已知缺口。

`gui/visual/VisualScreenMotion` 的 `SELF_ANIMATED_SCREENS` **故意不改**：新界面
没有自绘转场，交给通用淡入即可。

## 5. 还没接的

1. **滚动**：已支持滚轮，但**按整行换内容**（矩形不动、内容移动），不是像素级
   平滑滚动，也没有滚动条——因为 Skia region 内部没有裁剪，平移矩形会让内容压到
   上方标题上。
2. **封面圆角**：需要改用 Skia 侧绘制纹理才能做圆角裁剪。
3. **歌单详情页**：当前点卡片直接起播，没有参考里的详情页（封面 / 简介 / 曲目
   表 / 播放全部按钮）。
4. **中文输入法**：搜索框只收 `charTyped`，没有 IME 支持，无法输入中文关键词。
5. **账号面板里的退出登录**：见 §4。
6. **手机验证码 / 邮箱登录**：见 §3。

## 6. 验证状态（诚实声明）

- 只做了编译 + 单元测试：`gradlew compileJava compileTestJava test --offline`
  → `BUILD SUCCESSFUL`，122 个测试类 / 652 个测试 / 0 失败
  （其中 `TwilightMusicServiceTest` 12 个、`TwilightApiEndpointTest` 4 个，
  全部是纯解析/URL 测试，**不联网**）。
- **从未在游戏里渲染或试听过**：所有 Skia 调用、原版 `blit`、`NativeImage` /
  `DynamicTexture` 用法只做过签名核对。
- **从未连过真实的 NeteaseCloudMusicApiEnhanced 服务**：端点路径、信封字段名、
  二维码返回格式都是按协议与 `data-layer.md` 写的，并且刻意做成"多种信封都试、
  失败就回退"，但仍可能与该服务的实际返回有出入。
- 无法移植的桌面能力（液态玻璃折射、大面积 backdrop 模糊、任意字重中文、
  WASAPI 独占 / DSD / ASIO / VST3 / 托盘 / 全局媒体键 / 桌面歌词 / 投屏 / DLNA 等）
  见 `fidelity-map.md` §0 与 §4。
