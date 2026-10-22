# 第三方组件许可与归属

本文件列出 WurstB+ Plus 随包分发或内嵌的第三方组件及其许可情况。

**为什么需要这份文件**：架构审计发现仓库此前**没有任何跟踪的 NOTICE / THIRD-PARTY 文件**，
而根工程通过 jarJar 内嵌了 18 个依赖 jar，并随包分发了 FFmpeg、Skiko、字体等二进制资源。
多个组件的许可要求保留声明（Apache-2.0 §4、MIT、BSD、LGPL），不声明即不合规。

> **状态说明**：下表的"来源"列写明每条许可是**如何核实的**。
> 标 `⚠ 未声明` 的行表示该依赖的 POM 里没有 `<licenses>` 段、其 jar 内也没有许可文件——
> 这些**必须在正式分发前人工核实**，本文件不凭空断言它们是什么许可。

---

## 一、源码许可

| 组件 | 许可 | 说明 |
| --- | --- | --- |
| WurstB+ Plus 源码 | **GPL-3.0-or-later** | 继承自 [Wurst7](https://github.com/Wurst-Imperium/Wurst7)。全文见 [LICENSE.txt](LICENSE.txt) |
| Forge MDK 模板部分 | **LGPL-2.1**（含 MCP 数据不可再分发限制） | 见 [LICENSE-Forge-MDK.txt](LICENSE-Forge-MDK.txt) |
| **LumaGlass**（移植进来的玻璃按钮渲染） | **MIT** | Copyright © 2026 Sunny5349；上游 <https://github.com/Sunny5349/LumaGlass>，移植自 0.2.9。见下 |

### 移植的第三方源码：LumaGlass

客户端里**原版控件**（按钮、滑块、复选框、列表滚动条）的底色是 LumaGlass（Minecraft
1.20.1 / Forge 的液态玻璃 UI 模组）的玻璃面板。**不是运行时依赖**：产物里没有 LumaGlass
的 jar，也没有对应的 mod 依赖声明，而是把用到的那部分源码移植进了本仓库（客户端要自包含
发布，上游也没有公共 Maven）。

许可正文随包分发在
[`src/main/resources/META-INF/licenses/lumaglass/`](src/main/resources/META-INF/licenses/lumaglass/)：
`LumaGlass-MIT.txt`（MIT 全文）与 `ATTRIBUTION.txt`（逐文件来源、上游提交、改动点）。

| 本工程 | 上游 | 关系 |
| --- | --- | --- |
| `assets/wurst/shaders/core/glass.{json,vsh,fsh}`、`blur.{json,vsh,fsh}` | `assets/lumaglass/shaders/core/` 同名文件 | `.vsh`/`.fsh` **逐字节相同**，`.json` 只改命名空间 |
| `gui/glass/GlassRenderer.java`、`GlassStyle.java`、`GlassButtons.java` | `client/GlassRenderer.java`、`api/client/GlassStyle.java`、`client/theme/VanillaGlass.java` | 改写（差异逐条记在 [docs/glass-controls.md](docs/glass-controls.md) §5） |
| `mixin/AbstractButtonMixin.java` | `mixin/client/ButtonMixin.java` | 只改按钮底色那一段 |
| `mixin/AbstractSliderButtonMixin.java` | `mixin/client/SliderMixin.java` | 只改滑块底色那一段 |
| `mixin/CheckboxMixin.java` | `mixin/client/CheckboxMixin.java` | 整个混入 |
| `mixin/SelectionListMixin.java` | `mixin/client/SelectionListMixin.java` | 只取其中的滚动条分支（列表底色与选中行未移植） |

MIT 与项目的 GPL-3.0-or-later 兼容；MIT 要求保留版权与许可声明，因此上述文本随产物分发。

> 修正记录：审计前 `LICENSE.txt` 实为 Forge MDK 的 LGPL-2.1 文本，却被 README 徽章与
> `PROJECT_INDEX.md` 当作 GPL-3.0 引用，且仓库内**没有 GPL-3.0 全文**。现已拆分为上述两个文件。

## 二、jarJar 内嵌依赖（根工程，共 18 个）

定义见根 `build.gradle`。嵌套包被 relocate 到 `net.wurstclient.shaded.*`。

| 依赖 | 版本 | 许可 | 来源 |
| --- | --- | --- | --- |
| `org.jetbrains.skiko:skiko-awt` | 0.8.19 | **Apache-2.0** | Gradle 缓存 POM `<licenses>` |
| `org.jetbrains.kotlin:kotlin-stdlib` | — | **Apache-2.0** | POM |
| `org.jetbrains.kotlinx:kotlinx-coroutines-core-jvm` | — | **Apache-2.0** | POM |
| `io.github.llamalad7:mixinextras-forge` | 0.5.4+ | **MIT** | POM |
| `org.java-websocket:Java-WebSocket` | 1.5.3 | **MIT** | POM |
| `org.jcodec:jcodec` / `jcodec-javase` | — | **FreeBSD** | POM |
| `net.sourceforge.jaadec:...` → `vorbis-support` | — | **LGPL-3.0**（POM 写 "GNU LIBRARY GENERAL PUBLIC LICENSE, Version 3.0"） | POM + jar 内 `META-INF/license.txt` |
| `javazoom:jlayer` | — | ⚠ 未声明 | POM 无 `<licenses>`，jar 内无许可文件 |
| `javazoom:mp3spi` | — | ⚠ 未声明 | 同上 |
| `com.googlecode.soundlibs:tritonus-all` | — | ⚠ 未声明 | 同上 |
| `com.googlecode.soundlibs:jorbis` | — | ⚠ 未声明 | 同上 |
| `org.jflac:jflac-codec` | — | ⚠ 未声明 | 同上 |
| `net.jthink:jaudiotagger` | — | ⚠ 未声明 | 同上 |
| `io.netty:netty-codec-socks` | 见 properties | ⚠ 未声明 | 同上（Netty 上游通常为 Apache-2.0，**需自行核实**） |
| `io.netty:netty-handler-proxy` | 见 properties | ⚠ 未声明 | 同上 |
| `com.google.zxing:core` | — | ⚠ 未声明 | 同上（上游通常为 Apache-2.0，**需自行核实**） |
| `javazoom:java-stream-player` | — | ⚠ 未声明 | 同上 |
| `baritone:baritone-api-forge` | 1.20.1 | ⚠ **jar 内无许可条目** | 上游 Baritone 为 LGPL-3.0（**推断，未在 jar 内找到声明**） |

**已完成的改进**：jarJar 通过 `META-INF/jarjar/metadata.json` 记录每个依赖的坐标与版本区间，
所以接收方可据此定位上游。但**产物内仍不含任何许可文本**。

### ACTION REQUIRED（分发前）

1. 核实上表所有 ⚠ 行的实际许可（查上游仓库的 LICENSE 文件，而非仅看 POM）。
2. 把每个内嵌依赖的许可文本打进产物，例如放到 `META-INF/licenses/<artifact>/`——
   仓库已有现成的正确范例：`src/main/resources/META-INF/licenses/cozyui/`。
3. 注意 `vorbis-support` 自称 **LGPL-3.0**，与项目主许可 GPL-3.0-or-later 不同；
   LGPL 对"允许用户替换该库"有额外要求，relocate 后的动态替换路径需要确认。

#### 第 1、2 步已完成（2026-10-07）

实测了根工程产物 `WurstB+ Plus-v1.6.0-Forge-1.20.1.jar` 的
`META-INF/jarjar/metadata.json`：**内嵌依赖 17 个**（不是 18），其中
**只有 2 个 jar 自带许可文件** —— `mixinextras-forge`（`LICENSE_MixinExtras`）与
`vorbis-support`（`META-INF/license.txt`）；**其余 15 个产物内零许可文本**。

核实许可时踩到一个坑，记下来：**这批 POM 里 `<licenses>` 元素普遍不存在**
（先按该元素解析，17 个全部报"无声明"，看起来像全都没写）。
实际许可声明在 **POM 的 XML 头部注释**里，例如 `com.google.zxing:core:3.5.3`：

    <!-- Copyright (C) 2010 ZXing authors
         Licensed under the Apache License, Version 2.0 (the "License"); ... -->

改成对 POM 全文做许可关键词识别后，从本机 Gradle 缓存
（`~/.gradle/caches/modules-2/files-2.1`）**已确认 5 个**：

| 坐标 | 许可 | 依据 |
| --- | --- | --- |
| `io.github.llamalad7:mixinextras-forge` | MIT | POM 文本 |
| `org.java-websocket:Java-WebSocket` | MIT | POM 文本 |
| `com.google.zxing:core` | Apache-2.0 | POM 头部注释 |
| `org.jetbrains.skiko:skiko-awt` | Apache-2.0 | POM 文本 |
| `org.jetbrains.kotlin:kotlin-stdlib` | Apache-2.0 | POM 文本 |

**仍未确认 12 个**，且原因分类明确：

  * 8 个 POM 文本里没有可识别的许可字串 —— `netty-codec-socks`、
    `netty-handler-proxy`（许可在**父 POM** 里，本机缓存不含父 POM）、
    `mp3spi`、`jlayer`、`jflac-codec`、`vorbis-support`、`tritonus-all`、`jorbis`；
  * 4 个**本机缓存里根本没有 POM** —— `java-stream-player`、`jaudiotagger`
    （来自 flatDir 或本地 maven 目录）、`baritone-api-forge`（来自
    `baritone-maven/` 本地坐标）、`kotlinx-coroutines-core`。

**所以第 1 步无法只靠本机缓存完成**：剩下 12 个必须去上游仓库取 LICENSE 文件
（这也正是原文写的"查上游仓库的 LICENSE 文件，而非仅看 POM"）。
第 2 步（把文本打进产物）依赖第 1 步，且需要新建
`src/main/resources/META-INF/licenses/<artifact>/`；本机只有 `cozyui` 一个范例。

## 三、随包分发的原生库

| 组件 | 许可 | 对应源码 | 状态 |
| --- | --- | --- | --- |
| **FFmpeg**（`assets/wurst/ffmpeg/` 下 7 个 DLL） | **LGPL-2.1**（随包提供 `copying-lgplv2.1.txt` + `license-ffmpeg.txt`） | **[native/ffmpeg/](native/ffmpeg/)**：`build-ffmpeg.sh`、`build-shim.sh`、`COPYING.GPLv2/GPLv3/LGPLv2.1/LGPLv3`、`src/vf_ffmpeg_jni.c` | ✅ **已修复**（此前从未入库） |
| **Skiko**（`assets/wurst/skiko/skiko-windows-x64.dll` + `icudtl.dat`） | **Apache-2.0** | 上游 JetBrains Skiko | ✅ 已入库 |
| **Baritone**（各工程内嵌 / `baritone-maven/`） | **LGPL-3.0** | 上游 | ✅ 已补（67/67 工程随包分发许可正文，见下） |

> **FFmpeg 说明**：这是本次审计中唯一一处**实质性合规缺口**。`CHANGELOG.md` 曾声称
> "可复现的 FFmpeg 构建脚本与 JNI shim 源码在仓库的 `native/ffmpeg/` 下（对应源码提供）"，
> 但 `native/` 的 git 跟踪文件数是 **0**——它没有被 gitignore，只是**从未提交**，
> 因此克隆仓库的人拿不到对应源码，违反 LGPL-2.1 第 6 条。现已提交。
>
> 同时提交的还有 `src/main/resources/assets/wurst/ffmpeg/`（随包发布的 7 个 DLL 与许可文本）——
> 此前也未跟踪，导致**全新克隆既无法构建、也无法复现已发布的产物**。
>
> 运行时的原生库加载已加固：`render/skia/SkikoNatives.java` 现在会校验目标文件的 SHA-256
> （一致则跳过重写），复制后再次校验，并做平台/架构守卫。
> `gameDir/skiko/` 是玩家可写目录，此前会被无条件覆盖并交给 `System.load()`。

## 四、字体

| 组件 | 许可 | 状态 |
| --- | --- | --- |
| **CozyUI** | **GPL-3.0** | ✅ `META-INF/licenses/cozyui/CozyUI-GPL-3.0.txt` + `ATTRIBUTION.txt`，67 个工程均附带 |
| **FluentEmoji** | **MIT** | ✅ `FluentEmoji-MIT.txt` |
| **Noto Sans** | **OFL-1.1** | ✅ `NotoSans-OFL-1.1.txt` |
| ~~PingFang SC~~ | — | ✅ **已删除（2026-10-06）**，见下 |
| **SF Pro Rounded**（`sf_pro_rounded_regular.otf`，2.29 MB） | ⚠ **未解决** | ❌ 同为 Apple 字体，树内零许可声明；**未删除**，见下 |

### ✅ 已解决：PingFang SC 已从仓库删除

`pingfang_{light,regular,semibold}.ttf`（共 31.35 MB）、对应的三个字体 provider
JSON，以及**从未被任何代码调用**的 `clickgui2/PingFangFont.java`，已全部删除。

删除理由：PingFang SC 是 **Apple 的系统字体**。其来源压缩包 `PingFangSC-main.zip`
声明 "MIT License Copyright (c) 2023 refinec"，但该压缩包自己的 README 又把这些
文件称作"苹果平方字体"——**第三方无权对 Apple 的系统字体再许可**，该声明无效。

删除后的行为变化：

- `SkiaFontManager` 不再读随包字体。`regular()` / `light()` / `semibold()` 改为
  按字重（400 / 300 / 600）向 Skia 要**系统已装**的中文字体，按
  `Microsoft YaHei UI` → `Microsoft YaHei` → `PingFang SC` → `Noto Sans CJK SC` →
  `Source Han Sans SC` → `WenQuanYi Micro Hei` → `SimHei` → `SimSun` 的顺序取第一个
  存在的；逐级回退到 Skia 默认字面，**任何一层落空都不再抛异常**（原来加载失败会
  抛 `IllegalStateException`，删掉文件后那会让歌词与 ESP 直接崩）。
- 中文渲染从"随包苹方"变为"跟随系统中文面"。Windows 上是微软雅黑、macOS 上是
  系统苹方、Linux 上通常是 Noto——这正是 `cjk()` 原本就优先选择的路径，
  随包苹方此前只是最后的兜底。**字形会变，但不会出现豆腐块。**

### ⚠ 仍未解决：SF Pro Rounded（同类问题，未在本次删除范围）

`assets/wurst/font/sf_pro_rounded_regular.otf`（2.29 MB）**同样是 Apple 的字体**
（SF Pro Rounded），由 `rise.json` 作为 `wurst:rise` provider 提供，
经 `SkiaFontManager.latin()` 用于纯拉丁文/数字（HUD 与 Twilight 界面的非中文文本）。

它与苹方属于**同一类授权问题**，本轮按指令只删了 PingFang SC。
处理方式同理，三选一：换成 OFL 字体（如 Inter / Noto Sans 的对应字重）、
确认授权链、或停止随包分发。**建议与苹方一并决策。**

## 五、开发期与参考材料（不随包分发）

以下目录**未被 git 跟踪**，因此不构成再分发；它们只存在于作者工作区：

`reference/upstream-sources/`（Wurst7 / LiquidBounce / Meteor / FDPClient / WurstCN /
RavenBS / Rise / Vape / Grim / NoCheatPlus 等源码与压缩包）、`reference/decompiled/`
（Mojang 与 Forge 的字节码 dump）、`reference/vendor/`（第三方配置与 profile）、
`reference/artifacts/`、`reference/tools/`（baritone 各版本源码树与临时工具）、
`reference/recon/`（逆向工程证据）、`reference/errreport/`（崩溃日志与缺陷报告）、
`reference/porting-stale/`、`reference/smoke/`、`.test/`、`.recon/`，
以及根目录的 5 个 `.zip`。

**注意**：这些材料里含**无许可证的反编译源码**与**竞品客户端产物**。项目既有的
[移植优先级](docs/RELEASE.md) 已声明"无许可证反编译源码只核对行为，不直接复制"，
请继续保持该边界。若要共享工作区，请先清理这些目录。

> **2026-10-13 更新：路径已归位。** 上面这批目录原先散落在仓库根目录
> （`source/`、`official/`、`free0810/`、`wurstb_profiles/`、`_artifacts/`、`_tools/`、
> `_recon/`、`_smoke/`、`_errreport/`、`_porting_stale/`、`forge-mdk/`），现已全部收进
> 单一的 `reference/` 树，由 `.gitignore` 的 `/reference/` 一条规则覆盖。
> 逐目录的来源、许可状态与"可否删除"见 [docs/REPO-HYGIENE.md](docs/REPO-HYGIENE.md)。
> 同时删除了三处死残留：重复的 `gradle/gradle/wrapper/`、空的 `libs/`、
> 只剩一个 wrapper jar 的 `forge-mdk/`。

**补记（同日完成）**：上条"必须去上游取件"的结论正确，且上游**是通的**
（`repo1.maven.org` / `maven.aliyun.com` / `raw.githubusercontent.com` 均可达）。
17 个依赖的许可已全部落实，证据分三类：14 个由 Maven Central 的 POM 解析
（其中 8 个的声明在**父 POM** 里）；2 个由上游仓库 LICENSE 正文确认
（`java-stream-player` GPL-3.0、`baritone-api-forge` LGPL-3.0）；1 个
（`jaudiotagger`）由 **jar 自带 POM** 确认 —— 其真实坐标是 `net.jthink` 而非
jarjar 元数据写的 `com.github.goxr3plus`，故 Central 查不到。

最终分布：Apache-2.0 ×6、MIT ×2、LGPL-2.1 ×5、LGPL-3.0 ×2、GPL-3.0 ×1、
LGPL(版本未声明) ×1。第 2 步已实施：`src/main/resources/META-INF/licenses/<artifact>/`
下新增 17 个目录，每个含 `ATTRIBUTION.txt`（写明许可、SPDX、**证据出处**、文本
SHA-256）与许可正文。

**发布产物已验证（2026-10-12）**：重跑 `gradlew build` 后确认许可文本确实进了发布产物 ——

    WurstB+ Plus-v1.6.0-Forge-1.20.1.jar
        55,722,520 B   EOCD: True   CRC: OK
        1823 条目 / 1516 class / jarJar 内嵌 17
        META-INF/licenses: 59 文件 / 19 组件（17 个依赖 + cozyui）

**顺带发现并修掉一个会阻塞发布的问题**：先前那次被中途杀死的构建在 `build/libs`
留下了一个**截断的** `WurstB+ Plus-v1.6.0-Forge-1.20.1.jar` —— 它大小正常（9.5 MB）、
时间正常、文件名正常，但**没有 zip 的 EOCD**，打开就报「找不到中央目录结尾记录」。
从外面完全看不出来。这类文件已实测出现两次，两次都是发布用的那个产物（不是 -dev）。

为此新增 `scripts/verify-jars.ps1`：逐个检查 EOCD、中央目录可解析性、以及
（`-Deep` 时）每个条目的 CRC；不通过就以非零码退出并提示"删掉重新构建，不要发布"。
已双向验证：对完好的 3 个产物报通过（exit 0），对人为截断的副本报出
「缺少 EOCD —— 写入被中途打断」（exit 1）。

## 六、66 个非根工程的许可补齐（2026-10-22）

此前只有根工程（Forge 1.20.1）带完整的 `META-INF/licenses/`（19 个组件）；
其余 **66 个工程只有 `cozyui`**，尽管它们实际都内嵌了 Baritone、Netty ×2 与
Java-WebSocket（NeoForge 根工程还内嵌了 MixinExtras），发布产物里没有对应许可文本。

本轮为每个非根工程写入了其**实际内嵌**的组件目录：

| 工程类型 | 补入的许可目录 |
| --- | --- |
| Fabric（23） | `baritone-api-fabric`、`netty-codec-socks`、`netty-handler-proxy`、`Java-WebSocket` |
| Forge 版本（20） | `baritone-forge`（1.21 / 1.21.1 为 `baritone-api-forge`）、`netty-codec-socks`、`netty-handler-proxy`、`Java-WebSocket`；1.20.2 / 1.20.3 / 1.20.4 / 1.20.6 / 1.21 / 1.21.1 另含 `mixinextras-forge` |
| NeoForge 版本（22） | `baritone-neoforge`（1.20.2–1.21.1 为 `baritone-api-neoforge` 本地 jar）、`netty-codec-socks`、`netty-handler-proxy`、`Java-WebSocket` |
| NeoForge 根工程（1） | 上述四项 + `mixinextras-forge` |

每个目录包含许可正文与 `ATTRIBUTION.txt`（写明许可、SPDX、证据出处与文本 SHA-256）。
Baritone 的版本号**逐工程不同**，`ATTRIBUTION.txt` 不写死版本，而是指向该工程
`build.gradle` 中的 `baritone:*` 坐标，避免版本漂移后声明失真。

`baritone-maven/` 下的 jar 本身**仍不含许可条目**；许可文本随使用它们的
工程产物分发，仓库内 67 个工程的资源目录是权威来源。上游 `cabaletta/baritone`
的 LICENSE 为 LGPL-3.0-only。

### ⚠ 新发现：nether-pathfinder 上游没有许可

Baritone 内嵌了 `dev.babbaj:nether-pathfinder`（Fabric 产物里是
`META-INF/jars/nether-pathfinder-1.4.1.jar` / `1.6.jar`，Forge/NeoForge 产物里是
shaded 的 `dev/babbaj/pathfinder/*` 类）。上游 <https://github.com/babbaj/nether-pathfinder>
**没有任何 LICENSE / COPYING / NOTICE 文件**，GitHub API 的 `license` 字段为 `null`
（2026-10-22 核实）。

这意味着**这 60 个工程实际分发了无许可授权的代码**。本轮已为这 60 个工程写入
`META-INF/licenses/nether-pathfinder/ATTRIBUTION.txt`，如实记录"上游未声明许可"，
**没有伪造许可文本**。发布前必须三选一：

1. 取得作者书面授权；
2. 替换掉该组件；
3. 不再分发内嵌它的 Baritone 构建。

受影响工程共 **63 个**：Forge 19 个、Fabric 23 个、NeoForge 21 个
（根 Forge 1.20.1 与 NeoForge 1.21.4 等少数工程未分发该组件）。
该结论由 `scripts/check-licenses.ps1` 直接读取 Baritone jar 的内嵌条目/遮蔽类核实，
不是按版本号推断。

> **未包含在本轮**：SF Pro Rounded 字体的授权问题（见第四节），仍待决策。

**仍有一处局限**：`jaudiotagger` 的**版本号是推断的**
URL 指向 LGPL-3.0，已在它的 ATTRIBUTION 里注明；(2) **只在 `-dev.jar` 里验证过**
（59 个文件 / 19 个组件），发布产物由 `reobfJar` 产出而那次构建**被环境强制终止**
（`DBG_TERMINATE_PROCESS`，非构建失败），故尚未确认许可文本进入最终发布产物。
