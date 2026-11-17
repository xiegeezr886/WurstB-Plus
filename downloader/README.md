# WDownloader

Windows 10/11 桌面下载器，直接使用 [Axolotl](https://github.com/Axolotl-Launcher/Axolotl) 的 Vue 前端组件源码构建界面，通过 WebView2 与 .NET 下载引擎连接。原样复用 Button / ButtonFrame / IconButton、图标和 home-widget.css，改造 HomeLaunchCard 的模板及卡片渐变。具体来源与修改见 [源码归属](Frontend/src/vendor/axolotl/NOTICE.md)。

白色窗口内嵌浅紫渐变卡片，无侧栏、无标语。使用客户端 `OpaiStyle.java` 的 `#B5BEF0` 强调色和 `#0C185C` 文字色。左下角齿轮打开独立设置页。启动阶段复用客户端进入世界时的浅紫色变形动画，覆盖运行库释放、WebView2 初始化和首次发布列表读取；加载完成后图形 180 毫秒淡出，主界面 420 毫秒淡入并上移 8 像素；下载进度条复用音乐播放器的细波浪线，完成或暂停时变平，支持系统减少动画设置。来源见内嵌 NeoRaven-motion-NOTICE.txt。

程序名称、窗口标题及 EXE 名称为 **WDownloader**，程序图标使用灵动岛 W 图案配白色圆角底框，包含多种尺寸以适配资源管理器和任务栏。

首页卡片使用根目录 `logo.png` 的项目字标轮廓，统一填充为 `#0C185C` 纯色，窗口左上角使用灵动岛 `DynamicIslandMod` 引用的 `fabric/src/main/resources/assets/wurstpenguin/opai/wurst-logo.png`。素材保存在 `Frontend/src/assets/`，随前端内嵌到 EXE。字标已清理字母内残留白底和轮廓白边，纯色版本保留相同透明度；灵动岛图标保留原图。

## 使用

运行 `WDownloader.exe`，页面流程为 **首页 → 发布版本/游戏版本 → 加载器 → 下载**。页面可返回，设置页关闭后回到之前的页面。保存位置、线路选择、线程数、测速与发布列表刷新均位于左下角齿轮打开的设置页。

仅分发一个 **WDownloader.exe**。双击后，启动器会校验内嵌运行库并自动释放到系统“文档”的 `WDownloader/Runtime/<内容哈希>`，完成后打开界面。后续启动复用该版本，无需 ZIP、无需手动解压或携带 DLL。若源码目录受 Windows 低完整性标签限制，请将最终 EXE 复制到桌面等普通文件夹运行。

- 正式版、预发布版均可选择，默认选中最新正式版；游戏版本和加载器根据该发布的真实资产联动，不硬编码 67 个组合。
- 默认直接保存到 **Windows 系统下载文件夹**，通过 Known Folder API 读取，支持被移动到其他磁盘的下载目录，不创建额外子目录。也可在设置中更改目录；工具会记住上次的选择。
- 下载页显示进度、已下载大小和速度。支持取消、重试、打开下载文件夹及复制 SHA-256。
- 下载器只获取本仓库的客户端 JAR。需要自行安装匹配的 Forge / NeoForge / Fabric 和 Java。

## 下载线路

内置 **115 个用户提供的镜像节点，加 GitHub 直连，共 116 条线路**。完整列表位于 `Core/MirrorHosts.txt`；中文域名转换为 IDN 地址请求。设置页显示全部镜像、实测速度、耗时和可用数量，并支持手动选择。不会导入用户粘贴列表中的历史延迟或可用状态。

设置中全量测速最多同时检测 8 条线路。普通下载先重测手动选择的源、已测速的快源和默认源，按 8 条一批探测，首批不可用时继续后续节点；找到可用线路即可开始下载，无需等待所有超时节点。测速对**选中的实际 JAR**请求前 256 KiB，校验 ZIP 文件头与分段范围，以包含连接耗时的实测速度排序。无效响应、HTML 错误页及超时线路会被排除。单次探测最多 8 秒，连接最多 15 秒，连续读取停滞最多 20 秒；下载失败或校验失败后，依次尝试其他可用源。可手动优先选择某源，失败后仍自动换源。

默认 **4 路分段并发下载**，设置可选 1 / 2 / 4 / 8 线程。各连接请求互不重叠的 HTTP Range，并按偏移写入同一个临时文件；校验每段的 Content-Range、长度及整个文件的 SHA-256。代理忽略 Range 或返回 416 时退回单线程；错误范围和文件损坏则换源。任一分段失败会取消其他连接，等待所有写入结束后才重试或清理，防止旧线程继续写入。过小文件使用单线程。

换源时从头重新下载。测速会产生少量额外流量。公共代理可用性会随地区、网络和服务状态变化，内置不代表全部实时可用；维护者可以在 `Core/MirrorHosts.txt` 更新列表。

发布列表优先请求 GitHub API，6 秒未成功后以最多 8 路并发尝试代理，代理阶段总超时为 12 秒。最近一次成功列表保存于 系统“文档”目录下的 `WDownloader/releases.json`，联网失败时回退到该缓存。首次启动没有缓存时使用 `ReleaseSnapshot.json` 中随包的真实 GitHub 发布列表，该文件保留抓取日期。设置保存在同目录 `settings.json`，WebView2 数据、临时缓存及启动错误日志也保存在该目录。文档位置通过 Windows Known Folder API 获取，支持重定向；不在 AppData 保存应用数据。API 未认证访问有 GitHub 的频率限制，不内置令牌。

## 文件完整性

只接受 `xiegeezr886/WurstB-Plus` GitHub Release 的 HTTPS 下载链接及符合项目命名规则的 JAR，拒绝目录穿越和其他仓库 URL。下载使用唯一 `.part` 临时文件，验证成功后才替换目标；取消或失败时清理临时文件，保留已有 JAR。

验证包括发布文件大小、GitHub 资产 `digest` 的 SHA-256（若提供）、ZIP 可读取性及模组元数据。未提供摘要时执行大小和结构校验，宿主保留校验结果。摘要来自 GitHub API；如果发布列表也经代理获取，摘要与文件并不构成独立可信的签名验证。元数据获取来源保留在宿主状态中。

## 构建

需要 **.NET 8 SDK、Node.js 22+、pnpm 11**，在 Windows 运行：

```powershell
pwsh -File downloader/build.ps1
```

最终发布文件为 `downloader/artifacts/release/win-x64/WDownloader.exe`。内部先构建自包含 .NET 8 应用，再由 Windows 自带 .NET Framework 的轻量启动器内嵌压缩资源；ZIP 仅是构建中间文件，不是发布包。前端与官方加载器图标均内嵌，断网可显示界面。首次释放使用唯一暂存目录、SHA-256 校验和进程间互斥，完成后原子切换目录，防止同时启动或中途中断留下半包。系统需要 .NET Framework 4.8 和 Microsoft Edge WebView2 Runtime（Windows 10/11 通常已有），无需安装 .NET 8。ARM64 可以通过 `-Runtime win-arm64` 构建，本次仅验证 x64。

```powershell
# 前端编译完成后，可单独编译宿主
dotnet build downloader/WurstBPlus.Downloader.csproj -c Release

# 不依赖第三方测试框架的下载引擎回归测试
dotnet run --project downloader/Tests/WurstBPlus.Downloader.Tests.csproj -c Release

# 附加真实联网测试：获取列表、检测所有源、下载一个正式版 JAR 并核对 SHA-256
dotnet run --project downloader/Tests/WurstBPlus.Downloader.Tests.csproj -c Release -- --live
```

核心库与 Vue 界面分离；20 项离线测试覆盖目录解析、发布过滤、API 降级、测速错误、完整性失败换源、手动源优先、取消、截断及目标文件保护，新增并发屏障验证四段真正同时运行、精确覆盖全文件、错误分段拒绝和取消全部写入。附加真实联网测试已通过四路并发下载并核对发布 SHA-256。

`Frontend/tests/ui-smoke.mjs` 使用 Playwright 测试编译后的真实前端与模拟宿主协议，覆盖全部页面、115 个镜像的选择、SVG 缩放完整性、下载完成提示隐藏、预发布组合过滤、设置往返、线程设置、取消和 800×600 布局，并输出页面截图。需要可导入的 Playwright 包及 Edge；可用 `DOWNLOADER_PLAYWRIGHT_MODULE` 和 `DOWNLOADER_BROWSER_PATH` 指定工具路径。

整体授权沿用仓库的 AGPL-3.0-or-later；Axolotl / Modrinth 复制和改造部分保留 GPL-3.0-only 授权及原作者归属。许可证内嵌 EXE，可通过设置页“开源许可”查看。上游完整源码位于本地 `reference/Axolotl`，版本为 `dae3f4cf9e7665e3ae8f65f729215338446e213e`；构建使用项目内保留的组件，不依赖该参考目录。
