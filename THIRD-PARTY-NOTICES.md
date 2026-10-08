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

#### 第 1 步的进展（2026-10-07，未完成）

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
| **Baritone**（各工程内嵌 / `baritone-maven/`） | ⚠ 见上 | 上游 | ⚠ 待补声明 |

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

`source/`（Wurst7 / LiquidBounce / Meteor / FDPClient / WurstCN / RavenBS / Rise / Vape / Grim /
NoCheatPlus 等源码与压缩包）、`_artifacts/`、`_tools/`、`_smoke/`、`.test/`、`.recon/`、
`official/`、`free0810/`、`forge-mdk/`、`wurstb_profiles/`、`_errreport/`、
以及根目录的 6 个 `.zip`。

**注意**：这些材料里含**无许可证的反编译源码**与**竞品客户端产物**。项目既有的
[移植优先级](docs/RELEASE.md) 已声明"无许可证反编译源码只核对行为，不直接复制"，
请继续保持该边界。若要共享工作区，请先清理这些目录。
