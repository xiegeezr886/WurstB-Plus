# 第三方组件许可与归属

## SeedCrackerX / OpenSeed

Fabric 1.20.1 内嵌 [SeedCrackerX](https://github.com/19MisterX98/SeedcrackerX) 官方 2.14.4 发布包（MIT），保留其嵌套库及元数据，新增独立结果桥接和 Baritone 联动。普通/散布矿脉重放算法改编自 [kuxiaole/OpenSeed](https://github.com/kuxiaole/OpenSeed) 提交 `3411dd42cbb17cf9e937f872bce7ec0cb19eeccc` 的 OreXrayManager。该公开仓库声明为反编译重建，MIT 只适用于 SeedCrackerX 部分，其额外代码授权未明确，不能宣称整个仓库均为 MIT。原用户提供的 OpenSeed-Crack 地址不可访问，无法确认此镜像的继承关系。随包保留 `META-INF/licenses/seedcrackerx/`、`META-INF/licenses/openseed/` 来源与许可说明；没有引入 OpenSeed 原版 JAR、认证或云端上传功能。详见 [适配说明](docs/SEED-MINING-v1.5.2.md)。

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
| WurstB+ Plus 整体源码 | **AGPL-3.0-or-later** | 全文见 [LICENSE.txt](LICENSE.txt) |
| Wurst7 上游部分 | **GPL-3.0-or-later** | 继承自 [Wurst7](https://github.com/Wurst-Imperium/Wurst7)；GPL 全文见 [LICENSE-GPL-3.0.txt](LICENSE-GPL-3.0.txt)。GPL-3.0 代码并入 AGPL-3.0 作品 |
| Forge MDK 模板部分 | **LGPL-2.1**（含 MCP 数据不可再分发限制） | 见 [LICENSE-Forge-MDK.txt](LICENSE-Forge-MDK.txt) |
| **LumaGlass**（移植进来的玻璃按钮渲染） | **MIT** | Copyright © 2026 Sunny5349；上游 <https://github.com/Sunny5349/LumaGlass>，移植自 0.2.9。见下 |
| **FO** 部分模块 | **CC0-1.0** | 上游 <https://github.com/jialebot6666/FO>；可自由改编 |
| **InvincibleMachineGun** 部分模块 | **GPL-3.0** | 上游 <https://github.com/adaxiaohu/InvincibleMachineGun>；与整体 AGPL-3.0 兼容 |
| **LeavesHack** 部分模块 | **AGPL-3.0** | 上游 <https://github.com/MrBZBZ/LeavesHack>；与整体许可一致 |
| **Meteor Client** 部分模块 | **GPL-3.0** | Copyright (c) Meteor Development；上游 <https://github.com/MeteorDevelopment/meteor-client>；按现有 Wurst 模块合并或原生移植 |

2026-11-07 的 v1.5.1 移植批次在全部 66 个目标工程的
`src/main/resources/META-INF/licenses/{fo,invincible-machine-gun,leaves-hack,meteor-client}/`
附带对应源码快照的完整 `UPSTREAM-LICENSE.txt` 和 `ATTRIBUTION.txt`。
归属文件列出已移植行为、原始模块 SHA-256 和本地适配说明；这不表示四个客户端已完成全量行为移植。

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

MIT 与项目的 AGPL-3.0-or-later 兼容；MIT 要求保留版权与许可声明，因此上述文本随产物分发。

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
| `io.github.llamalad7:mixinextras-fabric` | 0.5.5 | **MIT** | 内嵌 jar 的 `LICENSE_MixinExtras`；23 个 Fabric 工程另附 `META-INF/licenses/mixinextras-fabric/` |
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
3. 注意 `vorbis-support` 自称 **LGPL-3.0**，与项目主许可 AGPL-3.0-or-later 不同；
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
RavenBS / Rise / Vape / Grim / NoCheatPlus 等源码与压缩包）、`reference/clients/`
（行为对照用的客户端源码与反编译材料：Rise、NeoRaven、moon-rebase、OpaiBounce、OpenOpal、
Epsilon、LeavesHack 共 7 个 GitHub 浅克隆；LiquidBounce Nextgen 与 SoarClient 7.1.2 两个
用户包；以及 JelloBounce / RedFix / Solstice 的反编译与修复产物，逐项来源与许可见
[借物表](reference/clients/README.md)）、`reference/decompiled/`
（Mojang 与 Forge 的字节码 dump）、`reference/vendor/`（第三方配置与 profile）、
`reference/artifacts/`、`reference/tools/`（baritone 各版本源码树与临时工具）、
`reference/recon/`（逆向工程证据）、`reference/errreport/`（崩溃日志与缺陷报告）、
`reference/porting-stale/`、`reference/smoke/`、`.test/`、`.recon/`，
以及根目录的 5 个 `.zip`。

`reference/clients/` 里 Rise-6.9.5 根目录没有许可文件，RedFix 与 Solstice 的上游也未在
参考包内声明许可，这些材料只用于核对行为/格式，**不得直接复制进本项目**；其余客户端分别
遵循其根目录的 GPL-3.0 / AGPL-3.0 / LGPL-3.0 / Apache-2.0 许可证。

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

---

## 五、Wallpaper Engine 图标

`src/main/resources/assets/wurst/textures/gui/fdp/wallpaper_engine.png` 取自
Wallpaper Engine 的官方 Steam 商店 logo（app 431960 的 `logo.png`），裁剪左侧图标、
缩放到 88×88 并重新着色为白色。Wallpaper Engine 及其图标版权归 Kristjan Skutta /
Wallpaper Engine 所有；此处仅用于在背景选择界面标识「从该来源导入」，不表示任何
隶属或背书关系。


## LeavesHack v1.5.2 移植

基于 [MrBZBZ/LeavesHack](https://github.com/MrBZBZ/LeavesHack) 提交 `13daf96bc5998d745fb34debe6f8ea98c403b11b`，
将除 Aura、ScaffoldPlus 外的 29 个注册模块及所需工具、渲染和注入逻辑适配到
`fabric/` 的 Minecraft 1.20.1 基线。上游许可为 AGPL-3.0-or-later。
已有对应模块以 Leaves 实现替换；两项排除模块保留原实现。
模块映射和版本差异见 [移植说明](docs/LEAVESHACK-v1.5.2.md)，
随包署名、源码哈希和许可证位于 `META-INF/licenses/leaves-hack/`。

## LiquidBounce Nextgen 开始菜单布局参考

Fabric 1.20.1 开始菜单参考本地 `reference/clients/LiquidBounce-Nextgen-main/src-theme/src/routes/menu/title/Title.svelte`、`buttons/MainButton.svelte`（GPL-3.0）的横排圆形按钮、其他功能页和错峰进出动效，以 Java/NanoVG 重新实现。按用户最终要求保留上一版按钮尺寸、现有图标，按钮材质恢复 Nextgen 白色半透填充、描边和悬停光晕；背景使用用户提供的浅紫图片，按钮以中文为主（Realms、ClickGUI、Navigator 保留英文）、去除时钟，左上角采用用户提供的 W 标志。未引入上游图标、背景、字体或品牌。随包许可与来源见 `META-INF/licenses/liquidbounce-nextgen/`。主题来源：https://github.com/DFH-clouds/LiquidBounce-Nextgen ；上游：https://github.com/CCBlueX/LiquidBounce 。

## Opai 药水效果 HUD

Fabric 1.20.1 的 `PotionStatus` 参考 [snmwdd/OpaiBounce](https://github.com/snmwdd/OpaiBounce) 的 `src/routes/hud/elements/Effects.svelte`，适配独立卡片、着色渐变、格式与动效。17 张药水图标来自其 `public/opai/samsara/potion/`，保留 alpha 并转换为白色蒙版，以复现 CSS mask 着色。上游声明涉及 CCBlueX、Opai / StylesFoundation 与 Samsara contributors；许可与原始 NOTICE 随包保留在 `META-INF/licenses/opai-effects/`。详见 [移植说明](docs/POTION-HUD-OPAI-v1.5.2.md)。

## RedFix HackList 与分类

Fabric 1.20.1 的启用模块列表参考本地 RedFix / LiquidBounce 的 ArrayList 主题，移植分类渐变色、条目布局和 200ms 动画，模块分类采用其八类体系。漏洞图标由上游 SVG 转为 PNG。上游 GPL-3.0-or-later 许可及署名位于 `META-INF/licenses/redfix-hud/`；字体沿用 MiSans。详见 [移植说明](docs/HACKLIST-REDFIX-v1.5.2.md)。

## Moon CGUI 交互参考

Fabric 1.20.1 的现有 CGUI 借鉴 `xiaozhuhou233/moon-rebase` 默认 Modern 模式的模块展开区域、绑定标签、开关渐变和滑块反馈，以本地 NanoVG/设置系统实现，保留现有布局及分类。上游 Apache-2.0 许可与说明随包位于 `META-INF/licenses/moon-cgui/`。详见 [改动说明](docs/CGUI-MOON-v1.5.2.md)。

## Moon 账户管理界面

Fabric 1.20.1 账户管理界面参考 xiaozhuhou233/moon-rebase 的 AccountManagerScreen 和 MoonMenuButton（Apache-2.0），适配现有账号后端、浅紫色主题与全局壁纸。许可和署名随包位于 META-INF/licenses/moon-account/；详情见 docs/ACCOUNT-MANAGER-MOON-v1.5.2.md。

## Opai 世界与服务器选择菜单

Fabric 1.20.1 世界和服务器选择界面的布局、条目及卡片翻转参考
[snmwdd/OpaiBounce](https://github.com/snmwdd/OpaiBounce) 的
`src/opai/ListScreen.svelte`、`listScreen.scss`、`menuMotion.ts` 和 `opai.scss`，
以 Java/NanoVG 适配现有 Minecraft 后端。账户管理器共用列表与翻转动效，三个列表采用中文按钮，
按钮材质保留客户端原有黑色半透圆角样式，开始菜单采用用户指定的先渐隐再翻转顺序。随包许可及上游 NOTICE 位于
`META-INF/licenses/opai-menus/`；范围与验证见 [移植说明](docs/MENUS-OPAI-v1.5.2.md)。

## Moon 流转辉光

新增 Logo HUD 的无背景图片布局参考同一 Moon 源码的 `WatermarkHudRenderer.renderLogo()`（Apache-2.0），替换为用户提供的开始菜单图标，保持图片比例。详见 [Logo HUD 说明](docs/LOGO-HUD-MOON-v1.5.2.md)。

HUD 与 PlayerESP / MobESP 的流转着色参考 [moon-rebase](https://github.com/xiaozhuhou233/moon-rebase) 固定提交 `55cd694e898025531de94e61e169ba6eb221f9f3` 的 `FlowingGlow` 和 `flowing_glow.frag`。已将空间/时间着色公式适配为 GLSL 150，并使用当前客户端的 NanoVG 与 Minecraft 模型渲染。Apache-2.0 许可证、文件来源及 SHA-256 位于 `META-INF/licenses/moon-glow/`；具体差异见 [移植记录](docs/MOON-GLOW-v1.5.2.md)。

## Epsilon / NeoRaven 战斗与 Scaffold

Fabric 1.20.1 战斗模块与 Scaffold 适配 [Matr1ze/NeoRaven](https://github.com/Matr1ze/NeoRaven) 和 [NekoyaHouse/Epsilon](https://github.com/NekoyaHouse/Epsilon) 的目标排序、延时轮换、范围分离和攻击冷却行为。上游 GPL-3.0 许可与固定提交信息随包位于 `META-INF/licenses/neoraven/`、`META-INF/licenses/epsilon/`；旧 OpenOpal 转向与特效保留原署名。范围、模块文件映射与差异见 [战斗迁移说明](docs/COMBAT-EPSILON-v1.5.2.md)。

## NeoRaven 音乐播放器

Fabric 1.20.1 音乐播放器改编自 NeoRaven 提交 `1a4ae731837f28087c263763cf176d9ec0261c37`（GPL-3.0），移植音乐管理、网易云接口、歌词与播放逻辑，并适配现有 NanoVG 界面和灵动岛。Material Icons 字体保留 Apache-2.0 许可证。捆绑依赖 JLayer 1.0.1.4（LGPL-2.1-or-later）和 ZXing core 3.5.3（Apache-2.0）；许可证、来源和改动说明分别随包位于 `META-INF/licenses/neoraven/`、`jlayer/`、`zxing/`。未分发音乐录音。详见 [音乐播放器移植说明](docs/MUSIC-NEORAVEN-v1.5.2.md)。

## Opai 歌词 HUD

独立 MusicLyrics HUD 改编自 [snmwdd/OpaiBounce](https://github.com/snmwdd/OpaiBounce) 的 LyricScroller、lyricSlide 和 MusicHud，适配现有播放器与 HUD 编辑器。上游许可证、完整 NOTICE、固定提交与来源哈希随包保留在 `META-INF/licenses/opai-lyrics/`；没有复制音乐服务、字体或录音。详见 [适配说明](docs/MUSIC-LYRICS-OPAI-v1.5.2.md)。

## NeoRaven 世界加载动画

Fabric 1.20.1 的世界加载动画改编自 [Matr1ze/NeoRaven](https://github.com/Matr1ze/NeoRaven) 提交 `1a4ae731837f28087c263763cf176d9ec0261c37` 的 `LoadingOverlay`、`M3LoadingIndicator` 与 `m3_loading.fsh`（GPL-3.0）。保留四种 SDF 形态和动画时序，适配 GLSL 150、现有背景与字体。许可证和改编说明随包位于 `META-INF/licenses/neoraven/`；范围及验证见 [加载动画说明](docs/LOADING-NEORAVEN-v1.5.2.md)。
