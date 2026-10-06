# 第二轮审计报告

**审计对象**：`C:\Users\ui863\Documents\trae_projects\VAP`
**第一轮报告**：[ARCHITECTURE_AUDIT.md](ARCHITECTURE_AUDIT.md)
**本轮提交范围**：`aaa55951..HEAD`（6 个提交）
**方法**：对第一轮的修复做**对抗式验证**——目标是证伪，不是确认。一个独立代理逐条追踪调用链，并把 Minecraft 1.20.1 的行为与 ForgeGradle 缓存中**真实的 Mojmap 反编译源码**对照。

---

## 一、结论摘要

第一轮修复**大部分成立**，但对抗式复核抓出了**两个由我引入的真实回归**，其中一个是 HIGH（会让客户端崩溃）。

| 类别 | 数量 |
| --- | --- |
| 本轮修复的缺陷 | 22 项（见 §二） |
| 复核判定为 SOUND 的修复 | 5 项 |
| 复核判定 SUSPECT 的修复 | 1 项（3 个真实缺陷） |
| **复核判定 BROKEN 的修复** | **1 项（HIGH，已修）** |
| **复核判定 FALSIFIED 的结论** | **1 项（C5 新克隆可构建性，已修）** |
| 复核发现的**修复自身引入的**新缺陷 | **2 项（已修）+ 2 项健壮性（已修）** |
| 仍未解决 / 需要构建或法务决策 | 见 §五 |

> **诚实结论**：这一轮证明了"改完就算修好"是错的。
> 若不跑第二轮对抗式复核，会带着**两个**缺陷进入主干：
> 一个是 SkikoNatives 改失败契约后留下的**必崩路径**（HIGH），
> 另一个是我上一轮"新克隆可构建全部 67 个工程"的结论**本身就是错的**——
> 9 个工程实际引用的 Baritone 1.18.0 产物从未入库，而我在 `git status` 里
> **反复看到过**那几行 `??` 却没有处理。

---

## 二、本轮已修复并验证的缺陷

全部有 commit 记录。`验证方式` 列写明**是如何核实的**，不写"应该没问题"。

### P0 · 合规

| # | 缺陷 | 修法 | 验证方式 |
| --- | --- | --- | --- |
| 1 | `LICENSE.txt` 实为 Forge MDK 的 **LGPL-2.1**，却被徽章与 `PROJECT_INDEX.md` 当作 GPL-3.0 引用；仓库内**没有** GPL-3.0 全文 | 根 `LICENSE.txt` 换成 GPL-3.0 全文；原文本保留为 `LICENSE-Forge-MDK.txt` | 文件内容与 GPL-3.0 官方文本**逐字节一致**：`git hash-object` = `f288702d2fa16d3cdf0035b15a9fcbc552cd88e7`，即公认的 canonical GPL-3.0 blob（674 行）。<br>（订正：本报告初版写"553 行"，那是 `Get-Content \| Measure-Object -Line` 的计数假象；已按 blob 哈希复核，字节级一致） |
| 2 | **33 个工程的 LICENSE 是错的许可文本**（全是 LGPL-2.1，28255/28256 字节）——其 jar 内许可与自身 `mods.toml` 声明矛盾 | 全部同步为 GPL-3.0 全文 | 逐文件 MD5 与根 `LICENSE.txt` 比对，**67/67 一致** |
| 3 | **28 个工程 `from("LICENSE")` 指向不存在的文件**（Gradle 对缺失路径静默不报错），jar 内既无许可也无提示 | 按各 `build.gradle` 实际引用的文件名补齐（fabric 线补 `LICENSE`，其余 `LICENSE.txt`） | 补齐后复查：**0 个工程缺许可文件** |
| 4 | **FFmpeg 的 LGPL「对应源码」从未入库**：`CHANGELOG.md:48` 声称在 `native/ffmpeg/`，实测跟踪文件数 **0**（未被 ignore，只是没提交）→ 克隆拿不到，实质违反 LGPL-2.1 §6 | 提交构建脚本、`COPYING.*`、JNI shim 源码 | `git ls-files native/ffmpeg` 非空；`native/ffmpeg/dll/`（构建产物）确认**未**被误提交 |
| 5 | 随包发布的 7 个 FFmpeg DLL **未跟踪且不在已构建的发布 jar 内** | 提交 `src/main/resources/assets/wurst/ffmpeg/` | `git ls-files` 确认 9 个文件入库 |
| 6 | 5 个工程 `mod_license=All Rights Reserved`，与自身 `mods.toml` 的 `GPL-3.0-or-later` 直接冲突 | 统一为 `GPL-3.0-or-later` | 全仓扫描：**0 处** `All Rights Reserved` 作为许可值（仅剩 MDK 模板注释行） |
| 7 | 仓库**没有任何跟踪的 NOTICE / THIRD-PARTY 文件**，而产物内嵌 18 个依赖 jar 并分发 FFmpeg/Skiko/字体 | 新增 `THIRD-PARTY-NOTICES.md` | 逐条标注**如何核实**；未声明许可的依赖列为 ACTION REQUIRED，不凭空断言 |

### P0 · 仓库卫生与可构建性

| # | 缺陷 | 修法 | 验证方式 |
| --- | --- | --- | --- |
| 8 | **60 个子工程的 `gradle-wrapper.jar` 只靠「已在索引里」才没被丢弃**（被 `.gitignore:21 *.jar` 匹配，`!gradle/wrapper/*.jar` 因含斜杠只锚定根目录） | `.gitignore` 改为 jar 白名单式，加 `!**/gradle/wrapper/gradle-wrapper.jar` | `git check-ignore --no-index` 对 5 种嵌套深度逐一验证；24 项 ignore 规则断言全过 |
| 9 | `neoforge/` 是**唯一**没有跟踪 wrapper jar 的工程（`README` 却声称每个工程都有） | 提交之 | 67/67 覆盖（`docs/STATUS.md` 自动统计） |
| 10 | **11 个工程在新克隆下无法构建**：Baritone 依赖只存在于被 gitignore 的本地目录 | 提交 `fabric/baritone-maven`（8 jar）、`fabric/libs`、根与 `neoforge/versions/1.21{,.1}` 的 jar | **实测**：`git archive` 只导出跟踪文件 → `fabric/` 的 `gradlew compileJava` **BUILD SUCCESSFUL in 3m26s**，日志含 `:remapping 54 mods`（即含内嵌 Baritone）。此前该项目在此场景下必然失败 |
| 11 | `.gitignore` 缺 `official/`、`free0810/`、`wurstb_profiles/`、`_errreport/`、`_recon/`、`.gradle-compose-cache/` 等规则；`!newforge/*/libs/*.jar` 是死规则 | 补齐；删除死规则 | `git status` 中不再出现这些目录 |
| 12 | `.gradle-compose-cache/` 的 **14 个 Gradle 缓存文件被跟踪** | `git rm -r --cached` + 加 ignore | 跟踪数归 0 |

### P0/P1 · 构建正确性

| # | 缺陷 | 修法 | 验证方式 |
| --- | --- | --- | --- |
| 13 | **5 个 NeoForge 工程的依赖根本解析不到**：`files("baritone-api-forge-1.20.1.jar")` + `flatDir { dirs "." }`，但该 jar 只在仓库根目录 → 干净状态下**无法构建** | 以 `gradle/wurstb-versions.properties` 为根标记向上定位，显式解析；失败即抛异常（不再静默用不存在的路径） | 花括号配平；依赖版本闸门通过 |
| 14 | **同 5 个工程的产物缺 Baritone 运行时**：实测 28.2 MB / 内嵌 jar **0** / `baritone/*` 类 **0**（对照 `neoforge/versions/1.21`：30.0 MB / 3 / 495），而 mod 自身代码与 mixin 配置都引用它 | 照搬同族可用工程的 `zipTree(baritoneJar)` 内嵌 + `zip64` + 排除项；`MixinConfigs` 补 `mixins.baritone.json` | ZIP 中央目录逐条比对；结构配平 |
| 15 | `neoforge/versions/26.x` 的 `archiveFileName` **把版本号与 MC 版本都写成字面量** → 升级后留下文件名过期的产物 | 改为由 `archivesName` + `project.version` + `minecraft_version` 推导 | 推导结果与磁盘上现存 5 个 jar 名**逐一核对一致**（不会改名） |
| 16 | 同 5 个工程的 jar **完全不含 GPL-3.0 文本** | 补 `from("LICENSE.txt")` + 实体文件 | 补齐后 67/67 |
| 17 | **零共享构建逻辑**：67 份 `build.gradle` 共 9,883 行 / 758 种不同文本（11.07× 行冗余）；升级一个依赖要手工改最多 67 处；无 version catalog / lockfile | 新增 `gradle/wurstb-versions.properties`（27 个坐标的单一来源）+ `scripts/dependency-versions.ps1`（`-Check` 闸门 / `-Bump` 跨工程升级 / `-Report` 矩阵） | `-Check` 通过；**实测闸门会失败**（注入一行假的登记项 → 退出码 1）；`-Bump` 预演准确定位 netty 4.1.82 的 **30 个文件**，且**不改动磁盘**（`git diff` 仅 1 个既存 WIP 文件） |

### P1 · 代码缺陷

| # | 缺陷 | 修法 | 复核判定 |
| --- | --- | --- | --- |
| 18 | `AutoStealHack` / `ChatTranslatorHack` 在后台线程直接操作 GUI 与网络（改菜单、发包、改渲染线程遍历的聊天队列） | 改走 `MC.execute`；AutoSteal 用 `ReentrantLock`+`Condition` 做无忙等交接 | ChatTranslator **SOUND**；AutoSteal **SUSPECT**（见 §三） |
| 19 | `SkikoNatives` 每次启动无条件重写 26.5 MB 原生库、无校验、无平台守卫、失败契约不一致 | 加 SHA-256（一致则跳过）、落盘后复校、平台守卫、统一失败契约 | **BROKEN → 已修**（见 §三） |
| 20 | `EventManager` 每次事件分发都分配并遍历一个永远为空的 map；每次功能开关沿继承链 `getDeclaredMethods()` | `isEmpty()` 短路 + 按类缓存注解扫描结论 | **SOUND** |
| 21 | `AsyncTextureLoader` 拒绝策略异常直接抛给调用方；`read` 返回 0 时自旋；关池后永久失败 | 转成失败的 future、修读循环、关池后按需重建 | **SOUND** |
| 22 | `EntityOcclusionCuller.close()` 非幂等（重复删 GL 对象） | `delete()` 归零 id + `reset()` | **SOUND** |
| 23 | `RadialMenuHack` 在**每帧** `onRenderGUI` 里取静态锁 + `TreeMap` 查找 | 注册点移到一次性初始化 + 实例缓存 | 注册**SOUND**；但设置持久化 **FALSE → 已修**（见 §三） |
| 24 | `Setting` 基类反向依赖 `clickgui2.Component`（55 个 settings 文件的基类） | **未修**（见 §五，需要跨层重构） | — |
| 25 | 文档里 7 个互斥的工程数、6 个不同的测试数（根因：全是手写） | 新增 `scripts/generate-status.ps1` 生成 `docs/STATUS.md`（支持 `-Check` 供 CI 拦截漂移）；`PROJECT_INDEX.md` 重写为不再维护手写数字；新增 `docs/PROJECT-STRUCTURE.md`；README 中英文修正计数与 26.3 | 生成结果：67 工程 / 51,677 源码 / 5,709 测试方法；wrapper 与 LICENSE 均 67/67 |

---

## 三、第二轮对抗式复核的发现

### 3.1 BROKEN（HIGH）— SkikoNatives 的失败契约改动会崩客户端 **（已修）**

上一轮把 `ensure()` 从「首次抛、之后返回 `false`」改成「永远抛」，**没有检查全部调用方**。

- `TwilightSurface.begin:96` 的 `ensure()` 调用在它自己的 `try` 块（`:111`）**之外**。
- 异常逃逸链（复核逐跳追踪）：`TwilightShellScreen.render`（`:550`，550–694 内**无任何 try`）→ `GameRenderer` → `runTick` → 被 `Minecraft.run` 的 `catch(Throwable)` 接住 → **崩溃报告，且此后每帧重复崩**。
- **非 Windows 平台必崩**（新增的平台守卫正好在 `try` 之外抛）。Windows 上也有变体：只要早先有调用方（`EspSkia` 会 `catch` 并缓存 FALSE）吸收过第一次失败，旧契约下后续调用返回 `false` 可优雅降级，新契约下则抛异常 → 下次打开 Twilight 界面即崩。
- 触发入口：`MusicPlayerHack.onEnable` / `.twilight` 命令 / ClickGUI 按钮。

**已修**：把 `ensure()` 包进 `try/catch(Throwable)`，失败即 `failed=true` 返回 `null`，回落原版渲染 —— 这正是该层原本的职责。

> 这一条是**我上一轮自己引入的回归**。它同时说明：改动一个"失败契约"时，
> 必须把**全部**调用方找出来并逐个判断，而不是只看被改动的那一个类。

### 3.2 FALSE（MEDIUM）— RadialMenuHack 的设置根本不会持久化 **（已修）**

上一轮把注册点从每帧移到 `initialize()`，但放在了 `:200`，而 `SettingsFile` 在 `:148` 构造时对功能表做**一次性快照**（`SettingsFile.java:43,51-53`，`load()` 与 `save()` 都只遍历那份快照）。

后果：`RadialMenuHack` 的 `entries` 设置**既不会被读取也不会被写回**，用户自定义条目每次重启静默丢回 `DEFAULT_ENTRIES`。而我上一轮写的注释恰好声称相反（"settings.json 都会自动包含它"）。

**已修**：`get()` 移到 `:145`，即 `HackList` 构造之后、`SettingsFile` 之前。实测确认：`hax = new HackList` 在 `:136`、`RadialMenuHack.get()` 在 `:145`、`new SettingsFile` 在 `:156`。

### 3.3 SUSPECT（MEDIUM-LOW）— AutoSteal 的共享信号会串台 **（未修，见 §五）**

`clicked` 字段与 `Condition` 被**所有 worker 共用**，无法把一次信号与某一次具体点击配对。

失败场景：W1（steal）刚 `MC.execute(clickSlot(A))` 但尚未 park；用户点"存储" → `interrupt()` 杀掉 W1，而 `clickSlot(A)` 仍在客户端队列里 → W2（store）排队 B 并 park → 客户端线程执行 A（界面未变，**真的点了**）并置 `clicked=true` → W2 把它当成自己的完成。结果：一次"存储"里混进一次偷取、且节流坍塌（A 与 B 同帧点击）。

**未修原因**：正确修法需要给每次点击发一个 token 并让 worker 只认自己的 token（或改成单 worker 串行队列），属于设计改动；当前窗口 ≤ 1 帧、且只在"偷取中切到存储"这一种快速操作下命中。已记入待办。

### 3.4 FALSIFIED（P0）— 「新克隆可构建全部 67 个工程」这个结论本身是错的 **（已修）**

我在上一轮把 #10 列为"已修复并**实测**验证"。复核证明这个结论**只对抽测的那一个工程成立**。

**反例**：`baritone-maven` 下 26.x 线用的**裸 `1.18.0`** 产物**从未提交过**
（`git log --all -- baritone-maven/baritone/baritone-forge/1.18.0/` 为空），
而 **9 个已提交的** `build.gradle` 引用它们：

| 工程 | 引用 |
| --- | --- |
| `versions/26.1{,.1,.2}` | `baritone:baritone-forge:1.18.0`（且 `shadowJar` 还直接 `from()` 该 jar 路径） |
| `fabric/versions/26.1{,.1,.2}` | `baritone:baritone-api-fabric:1.18.0` |
| `neoforge/versions/26.1{,.1,.2}` | `baritone:baritone-neoforge:1.18.0` |

它们**没有被 ignore**（`.gitignore:54` 的 `!baritone-maven/**/*.jar` 已正确放行），
只是**从未 `git add`** —— 属于漏加，不是规则问题。

**为什么漏掉**：仓库里已跟踪 `1.18.0-26.2` 与 `1.20.0-26.3` 变体，所以 26.2/26.3 能正常构建，
裸 `1.18.0` 就被忽略了。而 `git status` **一直把这几项列为 `??`**，我在本轮多次看到那几行
却没有处理——这是一个真实的注意力失误，不是信息不足。

**已修**（`a256f1b0`）：补齐 6 个文件（3 jar + 3 pom，约 15.1 MB），
外加其余未被跟踪的 Baritone 构建输入（`fabric/versions/1.21.1/libs`、
`neoforge/versions/26.1.2/libs`、`neoforge/versions/1.21.1/baritone-api-forge-1.21.2.jar`、
`versions/1.20.{2,3,4,6}` 的 4 个 mc 变体 jar），并忽略三处确认无人引用的散落物。

**已验证**：
- 9 个工程的 `baritone-maven` URL 逐个解析后**全部指向仓库根的 `baritone-maven/`**，
  补入的产物正在那里（不是补错了仓库）；
- `git archive HEAD -- baritone-maven` 导出**仅跟踪文件**的树后，12 个 `1.18.0` 相关文件全部在场。

**教训**：`git status` 里的 `??` 就是缺陷清单。我在本轮反复输出过它却没有读完——
下一次应当把"未跟踪且未被忽略、且被某个 build 文件引用"做成**自动闸门**，而不是靠人眼。

### 3.5 其余 LOW / INFO

| 项 | 说明 | 状态 |
| --- | --- | --- |
| `SkikoNatives` `os.arch` 大小写敏感比较（`os.name` 已小写化）→ 报 `AMD64` 的 JVM 被误判 | 已修（统一小写） |
| `SkikoNatives` `digestToHex` 在持续返回 0 的流上自旋 | 已修（加 `read == 0` 守卫） |
| `SkikoNatives` 失败被永久 latch，无重试；资源流读两次，中间发生资源重载会 latch 一个**虚假的**永久失败 | 未修（需设计重试语义） |
| `SkikoNatives` 跳过拷贝是 TOCTOU（`System.load` 在其后），javadoc 的"不会把来路不明的 DLL 交给 System.load"言过其实 | 文档需收敛措辞 |
| `AutoSteal.awaitClick()` 无超时、也无法独立得知"界面已关"；队列在关服时不再 drain → worker 永久 park（daemon，无 JVM 退出影响） | 未修 |
| `EntityCullingHack.reset()` 只是 `close()` 的别名；上一轮声称的"跨世界释放实体强引用"**实际什么都没改**（`close()` 的 `queries.clear()` 本来就释放了） | 描述已失准，需订正 |
| `AsyncTextureLoader` 拒绝路径下回调在**调用线程**内联执行（可能是渲染线程）；`read==0` 注释误导；重建池后线程名重复 | 未修 |
| `EventManager` 的优化针对一条**生产环境从不执行**的路径（`Hack.subscribeEvents()` 全仓无调用，`@WurstSubscribe` 在 `src/main` 出现 0 次）→ 提交信息里的性能理由是**不可证实的** | 需订正措辞 |
| `ChatTranslator` 在执行时才重读 `getConnection()`，跨服务器切换时存在投递到另一服务器的窗口 | 未修 |
| `TwilightSkia.begin:109-110` 同样未包 `ensure()`，但目前**不可达**（唯一调用点走 vanilla 分支） | 潜在同类缺陷 |

---

## 四、本轮验证方式的说明

| 验证手段 | 覆盖 | 局限 |
| --- | --- | --- |
| `gradlew compileJava --offline`（根工程） | 全部 Java 改动**可编译** | 不验证运行期行为 |
| `gradlew compileJava`（从**仅跟踪文件**导出的 `fabric/` 树） | **新克隆可构建性** | 只抽测 1 个（此前必失败的）工程 |
| ZIP 中央目录逐条比对 | NeoForge 产物缺 Baritone | 针对**既存**产物，不是重新构建的 |
| `git check-ignore --no-index` + 24 项断言 | ignore 规则正确性 | — |
| MD5 逐文件比对 | 67/67 许可一致 | — |
| 依赖闸门 `-Check` 的**失败路径**（注入假登记项 → 退出码 1） | 闸门真的会拦 | — |
| 对抗式代码复核（对照真实反编译源码逐跳追踪调用链） | 7 项代码修复 | 未启动游戏 |

**未做**：未启动游戏；未重新构建 67 个工程（ForgeGradle 单工程以十分钟计）；未跑 `gradlew test`。

### 一处凭据说明

**另一个并发进程**（同时在这个 `fix/baritone-1.21.7-official-v2` 分支上工作）在我提交 `fe3c550e` 时把它的改动（`versions/1.21.7` 等 3 棵树的 `AbstractBlockStateMixin`/`FluidRendererMixin` + mixins.json + `scripts/compare-official-mixins.ps1`）**已 staged**，因此被我的提交一并带走。文件内容完整无误、没有丢失，只是提交信息没有描述它们。发现后我改用「显式 pathspec + `-F <消息文件>`」提交，后续 4 个提交都只含我自己的文件。

**我选择不改写历史**（`reset` + 重组），因为该分支正被另一个进程使用，改写可能干扰它。如需拆分，可以随时做。

---

## 五、仍未解决的事项

### ⚠ 立即需要注意：工作区里存在「提交一半就会弄坏构建」的状态

这不是本轮引入的，而是**另一个并发进程正在进行的开发**（分支 `fix/baritone-1.21.7-official-v2`），
但它是当前工作区里最危险的状态，必须写明。两组：

| # | 已修改（跟踪）的文件 | 它们依赖的**未跟踪**文件 | 后果 |
| --- | --- | --- | --- |
| 1 | `src/main/java/net/wurstclient/background/BackgroundVideo.java`（引用新类 20 处）、根 `build.gradle` | `background/{FfmpegNatives,FfmpegVideoDecoder,Mp4Probe}.java` + 4 个测试类 + `src/test/resources/**/fixture-*.mp4`（共 11 个） | 只提交 `BackgroundVideo.java` + `build.gradle` → **根工程编译失败** |
| 2 | 15 个工程的 `wurst*.mixins.json`（`versions/`、`fabric/versions/`、`neoforge/versions/` 下的 `1.21.{6,8,9,10,11}`） | 同目录 `mixin/{AbstractBlockStateMixin,FluidRendererMixin}.java`（共 30 个） | 只提交 mixin 配置 → 这 15 个工程 **Mixin 加载失败** |

**当前 HEAD 是自洽的**：`HEAD:BackgroundVideo.java` 用的是 JCodec（0 处引用新类），
`HEAD:versions/1.21.8/.../wurst.mixins.json` 也不含那两个条目——所以**克隆 HEAD 能编译**。
危险只在「部分提交」时出现。

> 我在本轮提交时对每个提交都用**显式 pathspec**，因此没有把这两组带进来；
> 但 `fe3c550e` 那次（唯一一次没用 pathspec）已经把 `1.21.7` 的三个工程**连同其源码**
> 一起提交了——那一组是自洽的，没有问题。

**建议**：提交这两组工作时，务必把改动文件与它依赖的未跟踪文件放在同一个提交里；
或者先把未跟踪文件 `git add` 进去再改配置。

### 需要构建验证（我无法在本轮完成）

1. **5 个 NeoForge 产物的 Baritone 内嵌未经构建验证**。修法照搬同族可用工程、结构已配平，但需要下载 NeoForge 20.2.93 等并反编译 Minecraft 才能真构建。
2. **`fabric` 之外的 66 个工程未做新克隆构建验证**。只抽测了 `fabric`（此前必失败的那个）。
3. **`org.spongepowered:mixin` 0.8.5（根）vs 0.8.7（12 个）的冲突未裁决**。已登记在 `gradle/wurstb-versions.properties` 并标注为待决策；改它需要实际构建验证 Mixin refmap 生成结果。
4. **引入共享 Gradle 约定脚本**（把 758 种非空行真正收敛成一处）本轮**刻意没做**：一个共享脚本出错会同时弄坏 67 个单次构建以十分钟计的工程。当前方案（登记表 + 闸门 + 一键升级）拿到同样的收益而把风险留在文本层。

### 需要法务决策

5. **PingFang SC 字体**（31.35 MB，随每个 jar 分发）：树内零许可声明，来源压缩包以 MIT 名义再许可 Apple 系统字体，该声明无效。`THIRD-PARTY-NOTICES.md` 已列为未解决项并给出三个选项（换 OFL 字体 / 确认授权链 / 停止随包分发）。**在决策前不建议对外分发含这三个 TTF 的产物。**
6. **18 个内嵌依赖的许可文本未打进产物**。其中 `vorbis-support` 自称 LGPL-3.0，与项目主许可不同，对"允许用户替换该库"有额外要求。`THIRD-PARTY-NOTICES.md` 已列出需逐一核实的清单与正确范例（`META-INF/licenses/cozyui/`）。

### 结构性债务（本轮未动，第一轮已详述）

7. **67 份源码副本**：物理 51,677 个 `.java` 承载 2,958 份不同内容（冗余 10.9×）；**683 个文件（57.3%）在所有出现它的工程里逐字节相同**，从未被版本化定制。收拢路径见 `docs/PROJECT-STRUCTURE.md` §二。
8. **`Setting` 基类依赖 `clickgui2.Component`**（`Setting.java:23,210`，55 个 settings 文件的基类，21 个直接 import `clickgui2`）：这是"4 套 GUI 无法收敛"的根本原因，删任何一个 GUI 都会打断 settings 层。
9. **22 个版本快照分支 × 67 个目录**，同一份代码存两份；无任何机制把源码变更同步到那 66 个目录。
10. **无构建/测试 CI**：`.github/` 仅 1 个 workflow，只重建版本分支。
11. **`hacks/`（278 文件）零测试**；`mixin/`（75 文件）5.3%。
12. **76.8% 的跟踪字节是重复 PNG**（字体图集复制 67 份，唯一内容仅 45.2 MB）。

---

## 六、建议的下一步

| 优先级 | 动作 |
| --- | --- |
| 1 | 构建验证 5 个 NeoForge 工程（含新增的 Baritone 内嵌），并抽测其余加载器各 1 个 |
| 2 | 把 `-Check`（依赖闸门）与 `generate-status.ps1 -Check`（状态闸门）接进 CI；同时加一个最小 `compileJava` job |
| 3 | 裁决 `mixin` 版本冲突；把登记表里 7 个多版本坐标收敛到最少 |
| 4 | ~~法务：PingFang 字体决策~~ **已删除（2026-10-06）**；仍需补齐内嵌依赖的许可文本，并决策同样属于 Apple 字体的 **SF Pro Rounded**（`rise.json`） |
| 5 | 修 AutoSteal 的共享信号串台（token 配对或单 worker） |
| 6 | 解 `Setting → clickgui2.Component` 耦合 —— 这是收敛 GUI 的前置条件 |
| 7 | 把 683 个"零变体"文件提取为共享 sourceSet（不改逻辑、只删重复） |
