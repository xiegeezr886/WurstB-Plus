# 发布与维护手册

更新日期：2026-11-22。用户下载与安装说明见 [README](../README.md)；[v1.5.2 正式版说明](RELEASE-v1.5.2.md)记录既有版本，不代表本次候选已经发布。

## 当前工程与版本

仓库有 67 个独立 Gradle 工程，覆盖 23 个 Minecraft 版本：

| 加载器 | 根工程 | 版本子工程 | 合计 |
| --- | --- | ---: | ---: |
| Forge | `.`，MC 1.20.1 | `versions/` 20 个 | 21 |
| Fabric | `fabric/`，MC 1.20.1 | `fabric/versions/` 22 个 | 23 |
| NeoForge | `neoforge/`，MC 1.20.1 | `neoforge/versions/` 22 个 | 23 |

Fabric 1.20.1 为 **v1.5.2** 功能基线。67 个工程均已同步源码并采用 1.5.2 正式编号。
根 Forge 的迁移起点是项目内 dist 快照，见 [根工程历史说明](ROOT-FORGE-1.5.1.md)。
Forge 没有官方 1.20.5 / 1.21.2 工程；这两个 MC 版本只有 Fabric 和 NeoForge。

完整工程、工具链和源码/测试计数由 [STATUS.md](STATUS.md) 自动生成。
v1.5.2 的兼容性和验收条件见
[同步计划](PORTING-v1.5.2.md)及[逐工程清单](generated/v1.5.2-porting/README.md)。

## GitHub Release

| 发布 | 内容 | 定位 |
| --- | --- | --- |
| [v1.5.0](https://github.com/xiegeezr886/WurstB-Plus/releases/tag/v1.5.0) | 67 个历史资产 | 历史发布 |
| [v1.5.1](https://github.com/xiegeezr886/WurstB-Plus/releases/tag/v1.5.1) | 67 个 JAR | 历史全矩阵发布 |
| [v1.5.2](https://github.com/xiegeezr886/WurstB-Plus/releases/tag/v1.5.2) | 以页面资产为准 | 正式版本编号；本地构建与远端上传分别验收 |

发布时须使用当前源码的生产任务重新构建，按实际 JAR 的元数据和 SHA-256 核验后上传。

### 67 个组合的发布前检查

“全线”指 21 个 Forge、23 个 Fabric、23 个 NeoForge 构建组合，不是 67 个不同的 Minecraft 版本。版本列表、包数或文件名相符都不能单独证明修复已经发布。准备 GitHub 提交时应从已审阅的源码提交构建，使用独立工作目录，记录提交 SHA；不要把本地缓存、游戏存档、日志、账户文件、第三方包或 QA 探针提交进 Git。

1. 在仓库根运行 `python scripts/build-v1.5-release.py --plan`，核对三条线的工程数、JDK、Gradle 任务。此命令不执行构建。修复发布应使用新版本号，并保持源码声明、成品元数据与新标签一致；不要覆盖历史 Release。
2. 按下表选择各工程的生产任务。检查 Gradle 退出码、测试 XML、实际产物的加载器标记与内嵌依赖。开发包、旧缓存、白名单测试件及仅修改了文件名的包不能作正式产物。
3. 将同一候选提交的生产 JAR 放入一个**仅含本批次**的目录。用 Python 3.11 或更高版本运行离线预检：它核对精确资产名、顶层加载器描述文件中的 `wurstpenguin` 版本和目标依赖，并在源码、JAR 与内嵌 JAR 中查找已知禁用实现和注册引用（ForceOP、CrashChest、Throw）。NeoForge 26.x 的包内版本可为纯版本号，但仍需匹配加载器和 MC 依赖。非零退出须先处理；零退出不是完整代码安全审计、签名/来源证明或实机验收。
4. 为每个待发布组合记录候选 JAR 的 SHA-256、整合包版本及加载器、原包对照、首次保存和新进程读回结果。确认读回的输入与首次输出一致；有崩溃报告、看门狗终止或未进世界，不得计为通过。修订整合包和原包分开记录；只验证某个 GUI 探针不能代表完整客户端。

```powershell
# 示例仅用于检查既有版本；不访问 GitHub，不申请凭据，不上传。
python scripts/release_preflight.py --tag v1.5.2 --dist build/release-v1.5
python -m unittest discover -s scripts/tests -p 'test_*.py'
```

上传工具不再默认选择历史标签；资产操作在读取凭据前先执行同一个预检。`--only` 仅匹配完整工程名，不能用 `Forge-1.21` 代表整个 1.21 系列。目录中的 JAR 数量即使是 67，旧版本也会被拒绝。`--dry-run` 仍可能读取远端信息；只做本地检查请使用上面的预检脚本。

### 本轮验收状态（2026-11-22）

本地旧产物目录 67 个 JAR 的包内版本均为 1.5.1。它们不是本轮审查提交的新成品，以下历史结果不能转记给新 JAR。本轮未发布或替换 GitHub 资产。

| Forge 历史候选 / 固定包 ID | 复核结果及限制 |
| --- | --- |
| 1.21.6 `n0JKzc62`、1.21.7 `cqbH9dCU`、1.21.8 `GBWDtSdh` | 修订副本中的普通入口 QA 候选均完成首次保存和新 JVM 同档读回。1.21.6 最终候选比对照多移除一件；1.21.7 移除 SmartOptimizer；1.21.8 同时移除 OptiFine 与 pingfix。原包加装均未通过。 |
| 1.21.11 `GX9v8ztX` | 原锁定 Forge 61.0.2 拒载。六个原模组不变，修订到 61.2.0 后，候选保存并读回。 |
| 26.1 `XiFq3Ac9`、26.1.1 `QC90dvvS`、26.1.2 `yKMJtVS7` | 修订后的无候选对照和普通入口 QA 候选均保存并读回。前两组各移除一个不兼容模组；第三组保留八个模组，仅升级到 Forge 64.1.0。 |
| Pixelmon `lmwiXuDD`、SuperSMP4 `mFsVWq2u`、Helios `eARONeXV` | 11 月 22 日实测：三个无候选实例均由新 JVM 打开上次同版本菜单创建的世界并登录。SuperSMP4、Helios 的删减副本能从菜单再次保存并退出；Pixelmon 的 13 个原模组不变，停在初始选择界面，正常关窗后五个维度均保存。三组退出码 0。不能算作加装客户端验收。 |

要解除发布阻塞，应以完成安全审查的源码为起点，按组合生成独立产物与 SHA-256，再以**同一份**候选 JAR 完成锁定整合包对照、首次保存和新 JVM 读回。

## 构建与运行

各工程相互独立，根 `settings.gradle` 不会包含所有版本。
按工程设置 JDK 后，在该工程目录运行其 wrapper。

| MC 范围 | JDK |
| --- | ---: |
| 1.20.1–1.20.4 | 17 |
| 1.20.5–1.21.11 | 21 |
| 26.x | 25 |

根 Forge 1.20.1：

```powershell
./gradlew.bat compileJava jarJar --console=plain --max-workers=2
```

Fabric 1.20.1：

```powershell
./fabric/gradlew.bat -p fabric build --console=plain --max-workers=2
```

生产任务按加载器和版本选择：

| 工程 | 生产任务 |
| --- | --- |
| Forge 1.20.1–1.21.1（仅现有工程） | `jarJar`；以该工程实际配置的后续任务为准，不统一追加 `reobfJarJar` |
| Forge 1.21.3 及以后 | `allJar` |
| Fabric 1.20.1–1.21.11 | `remapJar` |
| Fabric 26.x | `jar` |
| NeoForge 根工程 1.20.1 | `jarJar`，沿用该工程的 ForgeGradle 构建 |
| NeoForge 其余版本 | 矩阵脚本调用 `build`，按各工程配置核对主产物和 JarJar 元数据；不要默认取 plain/dev JAR |

`build` 可用于执行额外检查，不能把 `compileJava` 当作完整发布验收。
存在测试源码时，要求测试实际执行并核对 JUnit XML；`SKIPPED` 不算通过。
构建前避免无目的执行 `clean`，部分加载器的生成输入位于 `build/`。
首次构建需要联网解析插件、Minecraft 和依赖。

本机便携 JDK/zipfs 配置可通过 `scripts/build-env.ps1` 加载；
也可设置 `WURSTBPLUS_JAVA17`、`WURSTBPLUS_JAVA21`、`WURSTBPLUS_JAVA25`。
完整构建计划入口：

```powershell
python scripts/build-v1.5-release.py --plan
```

该脚本的名称沿用现有维护流程，包含 67 个工程；当前版本号来自各自
`gradle.properties`，不是对整个矩阵已升级的声明。
`scripts/build-all.ps1` 是历史 18 工程子集，不用于全矩阵发布。

## 打包验收

1. 核对本次构建退出码、来源提交和实际产物时间，使用隔离输出避免混入旧类。
2. 检查 ZIP/CRC、加载器元数据和客户端版本，包含入口类、Mixin 配置及有效 refmap。
3. 核对 Baritone、MixinExtras、音乐解码依赖、字体、shader、原生库和第三方许可。
4. 按 MC/API 代际核对 Mixin 方法描述符、accessor 注册及可选模组回调。
5. 使用该产物启动游戏，验证菜单、进出世界、GUI/HUD、配置保存、战斗/搭路及支持的音乐/种子功能。
6. 记录没有验证或不支持的能力，确认后才上传对应 Release 资产。

完整性和静态检查入口：

```powershell
pwsh -File scripts/dependency-versions.ps1 -Check
pwsh -File scripts/generate-status.ps1 -Check
pwsh -File scripts/check-licenses.ps1 -Quiet
python scripts/audit-crash-regressions.py
python scripts/audit-render-regressions.py
pwsh -File scripts/check-parity.ps1 -Check
```

当前许可检查、历史渲染检查和跨版本 parity 检查有已记录问题；
详见 [同步计划的前置问题](PORTING-v1.5.2.md#前置问题)。
不得通过忽略失败、扩大白名单或隐藏能力来宣称验收完成。

## 功能与兼容性记录

当前功能说明以 [v1.5.2 开发文档](DEVELOPMENT-v1.5.2.md)为入口，
包括菜单、导航器、CGUI、Soar HUD / Rise TargetHUD、浅紫主题、音乐、加载动画、
Epsilon / NeoRaven / LeavesHack 战斗适配和种子挖矿。
这些说明目前适用于 Fabric 1.20.1，不能外推为其他 66 个工程已实现。

NanoVG、GL 状态、X-Ray 和 Vulkan 的既有实测记录见：

- [渲染矩阵复核](../report/COMPAT-RENDER-v1.5.1-RECHECK-2026-11-13.md)
- [全版本渲染修复](../report/RENDER-v1.5.1-matrix-2026-11-13.md)
- [26.3 渲染修复](../report/RENDER-v1.5.1-26.3-2026-11-12.md)
- [Baritone 与启动测试](CLIENT-LAUNCH.md)

历史结果只证明对应源码和产物，移植后需要重新验证。
许可与来源见 [THIRD-PARTY-NOTICES](../THIRD-PARTY-NOTICES.md)；
可选 JAR 保护流程见 [PROTECTION.md](PROTECTION.md)。
