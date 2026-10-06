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
| **PingFang SC**（`pingfang_light/regular/semibold.ttf`，共 31.35 MB，随每个 jar 分发） | ⚠ **未解决** | ❌ 树内**零归属、零许可声明** |

### ACTION REQUIRED（法务决策，非工程问题）

`PingFang SC`（苹果平方字体）是 **Apple 的系统字体**。其来源压缩包
`PingFangSC-main.zip` 声明 "MIT License Copyright (c) 2023 refinec"，
但该压缩包自己的 README 又把这些文件称作"苹果平方字体"——
**第三方无权对 Apple 的系统字体再许可**，因此这个 MIT 声明不能作为分发依据。

处理选项（建议按序评估）：

1. **替换为明确自由的字体**：Noto Sans SC（OFL-1.1）、思源黑体（OFL-1.1）、
   霞鹜文楷（OFL-1.1）等。视觉上需要重新调字重与字距。
2. **确认授权链**：如果确有 Apple 或权利人的书面许可，把许可文本放进
   `META-INF/licenses/`，并在本文件登记。
3. **停止随包分发**：改为运行时由用户自行提供字体文件。

在做出决策前，**不建议对外分发含这三个 TTF 的产物**。

## 五、开发期与参考材料（不随包分发）

以下目录**未被 git 跟踪**，因此不构成再分发；它们只存在于作者工作区：

`source/`（Wurst7 / LiquidBounce / Meteor / FDPClient / WurstCN / RavenBS / Rise / Vape / Grim /
NoCheatPlus 等源码与压缩包）、`_artifacts/`、`_tools/`、`_smoke/`、`.test/`、`.recon/`、
`official/`、`free0810/`、`forge-mdk/`、`wurstb_profiles/`、`_errreport/`、
以及根目录的 6 个 `.zip`。

**注意**：这些材料里含**无许可证的反编译源码**与**竞品客户端产物**。项目既有的
[移植优先级](docs/RELEASE.md) 已声明"无许可证反编译源码只核对行为，不直接复制"，
请继续保持该边界。若要共享工作区，请先清理这些目录。
