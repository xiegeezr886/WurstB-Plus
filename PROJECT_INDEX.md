# 项目索引

> **本文档不再维护任何手写的数字。**
>
> 架构审计发现：同一批状态数字在 9 份文档里存在 **7 个互斥的工程数**、**6 个不同的测试数**，
> 根因是所有数字都是手写的、且没有东西去重新生成它们。本文件原先声称"仓库内共有 15 个独立构建工程"
> 和"另有 46 个新版本工程"，而实际是 **67 个工程**——它整份文件停留在 15 工程时代。
>
> 现在权威数字只有一个来源：自动生成的 **[docs/STATUS.md](docs/STATUS.md)**。

## 数字去哪儿看

| 你需要知道 | 去看 |
| --- | --- |
| 工程总数、MC 版本数、加载器、源码/测试文件数 | [docs/STATUS.md](docs/STATUS.md) |
| 逐工程的工具链（Java / Gradle / 加载器版本 / mod_version） | [docs/STATUS.md](docs/STATUS.md) |
| 哪些工程缺 v1.6 功能 | [docs/STATUS.md](docs/STATUS.md) 的「v1.6 功能缺口」 |
| 67 个工程为什么这么组织、怎么加新 MC 版本 | [docs/PROJECT-STRUCTURE.md](docs/PROJECT-STRUCTURE.md) |
| 内嵌依赖与随包资源的第三方许可 | [THIRD-PARTY-NOTICES.md](THIRD-PARTY-NOTICES.md) |
| 功能清单（Hack / 命令 / Other Feature）与架构导航 | 本文档以下章节 |

`docs/STATUS.md` 由 `scripts/generate-status.ps1` 生成：

```powershell
pwsh -File scripts/generate-status.ps1          # 重新生成
pwsh -File scripts/generate-status.ps1 -Check   # CI：状态漂移则退出码 1
```

---

## 源码结构导航（根工程 Forge 1.20.1）

包职责一览。**文件数会变，故不在此列出**——需要时看 `docs/STATUS.md` 或直接数目录。

| 包 | 职责 |
| --- | --- |
| `net.wurstclient` | 客户端生命周期（`WurstClient` 枚举单例）、功能基类、共享状态 |
| `net.wurstclient.addon` | Addon 扩展系统（`ServiceLoader`） |
| `net.wurstclient.ai` | 路径搜索、垂直节点规划、路径执行 |
| `net.wurstclient.altmanager` | 账号、登录、原生凭据主密钥、账号管理界面 |
| `net.wurstclient.background` | 标题界面自定义背景：持久化、异步纹理、静图运镜、GIF/视频解码与帧时钟、Wallpaper Engine 导入 |
| `net.wurstclient.clickgui2` | ClickGUI：VAPE / SuperSoft / Epsilon 组件层、实心主题、字体偏好 |
| `net.wurstclient.command` / `commands` | 命令基础设施（含 Brigadier）与具体命令 |
| `net.wurstclient.compose` | 声明式 UI 布局树 |
| `net.wurstclient.discord` | Discord Rich Presence IPC |
| `net.wurstclient.event` / `events` | 事件管理器、订阅器与各事件接口 |
| `net.wurstclient.gui` | 标题界面（`title/`）与视觉 token / 渲染（`visual/`） |
| `net.wurstclient.hack` / `hacks` | Hack 基类、注册表、冲突与生命周期；具体 Hack 实现 |
| `net.wurstclient.hud` / `hud2` | 原 HUD / TabGUI；HUD2 系统、磨砂玻璃、指标采样、卡片编辑器与元素 |
| `net.wurstclient.keybinds` | 按键绑定（TOGGLE/HOLD/SMART）、配置与执行 |
| `net.wurstclient.macros` / `waypoints` / `proxy` | 宏、路径点、SOCKS 代理 |
| `net.wurstclient.mixin` / `mixinterface` | Mixin 注入点（专用包）与 Mixin 暴露接口 |
| `net.wurstclient.music` + `apple` | 网易云 API、播放器、账号、逐字歌词与 AMLL 视觉层 |
| `net.wurstclient.perimeter` (+`.config`/`.detect`) | 周界挖掘：区域模型、液体策略、批次、导航/交互/装备/补给、状态编排、分存配置、边界检测 |
| `net.wurstclient.render.skia` | Skiko 矢量渲染管线（含 ESP 的 Skia 通道） |
| `net.wurstclient.seed` (+`.structure`/`.search`/`.crack`/`.scan`) | 种子矿透：种子存储、纯 Java 矿物预测、结构定位、种子反解、自动观测 |
| `net.wurstclient.serverfinder` | 服务器扫描与清理界面 |
| `net.wurstclient.settings` (+`.filters`/`.filterlists`) | 设置类型、过滤器与配置文件 |
| `net.wurstclient.twilight` | Twilight Echo 音乐界面：外壳与布局几何、Skia 绘制、主题、封面缓存 |
| `net.wurstclient.update` | 更新检查（**已停用**：`WurstUpdater` 为自我注销的 stub，全部 67 个工程一致） |
| `net.wurstclient.util` (+`.render`/`.chunk`/`.esp`/`.text`/`.json`/`.anim`/`.inventory`/`.hud`) | 渲染、快照、投影、放置规划、战斗/击退规划、库存队列、GPU 遮挡、区块搜索 |

**架构约束（有测试在强制）**：`net.wurstclient.mixin` 是**专用包**，普通运行类禁止放入；
`mixin/MixinPackageIsolationTest` 会加载 `wurst.mixins.json` 并断言该目录下每个 `.java`
要么是被配置的 Mixin、要么是 Mixin 配置插件类。

## 功能注册入口

| 类别 | 注册入口 | 机制 |
| --- | --- | --- |
| Hack | `src/main/java/net/wurstclient/hack/HackList.java` | 手写字段 + 按字段名后缀 `Hack` 反射收集 |
| 命令 | `src/main/java/net/wurstclient/command/CmdList.java` | 同上，后缀 `Cmd`；Brigadier 命令需另行显式注册 |
| Other Feature | `src/main/java/net/wurstclient/other_feature/OtfList.java` | 同上，后缀 `Otf` |

> **这个机制的已知陷阱**：字段名不以对应后缀结尾会被**静默漏注册**；非对应类型但以该后缀结尾
> 会在构造期抛 `ClassCastException` 并导致启动崩溃；`hax.put(...)` 遇重名**静默覆盖**，
> 而 addon 注册路径 `registerAddonHack` 会抛异常（校验策略不对称）。
> `hacks/RadialMenuHack.java` 就是该机制的产物——它有私有构造器，只能自注册。

## 核心文件导航

| 领域 | 文件 |
| --- | --- |
| 客户端生命周期 | `src/main/java/net/wurstclient/WurstClient.java` |
| Forge 入口 | `src/main/java/net/wurstclient/WurstForgeInitializer.java` |
| Hack 基类 / 注册表 | `hack/Hack.java`、`hack/HackList.java` |
| 事件管理器 / 订阅器 | `event/EventManager.java`、`event/WurstSubscriber.java` |
| 设置基类 / 持久化 | `settings/Setting.java`、`settings/SettingsFile.java` |
| ClickGUI 选择入口 | `clickgui2/ClickGuiScreens.java` |
| 视觉 token | `gui/visual/VisualTheme.java` |
| 每 Tick 实体快照 | `util/EntitySnapshotManager.java` |
| 渲染状态作用域 | `util/render/RenderScope.java` |
| 旋转协调 | `RotationFaker.java` |
| 通用渲染 | `util/RenderUtils.java` |
| 平台工具 | `util/PlatformUtils.java` |
| Skiko 原生库加载 | `render/skia/SkikoNatives.java` |
| FFmpeg 原生库加载 | `background/FfmpegNatives.java` |

## 资源

| 路径 | 说明 |
| --- | --- |
| `src/main/resources/META-INF/mods.toml` | Forge 模组元数据（mod id `wurstpenguin`） |
| `src/main/resources/wurst.mixins.json` | 根工程客户端 Mixin 配置 |
| `src/main/resources/META-INF/accesstransformer.cfg` | Forge 访问转换 |
| `src/main/resources/assets/wurst/skiko/` | Skiko 原生库，运行时解压到 `gameDir/skiko/`（做 SHA-256 校验） |
| `src/main/resources/assets/wurst/ffmpeg/` | FFmpeg 原生库（LGPL-2.1 构建），随包发布 |
| `native/ffmpeg/` | FFmpeg 的**对应源码**：构建脚本、`COPYING.*`、JNI shim 源码 |
| `src/main/resources/assets/wurst/translations/`、`lang/` | 语言资源 |
| `src/main/resources/META-INF/licenses/cozyui/` | CozyUI / FluentEmoji / NotoSans 的归属与许可文本 |
