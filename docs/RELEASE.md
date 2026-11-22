# 发布与维护手册

更新日期：2026-11-18。用户下载与安装说明见 [README](../README.md)，本次变更见 [v1.5.2 正式版说明](RELEASE-v1.5.2.md)。

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
| [v1.5.2](https://github.com/xiegeezr886/WurstB-Plus/releases/tag/v1.5.2) | 67 个 JAR：Forge 21、Fabric 23、NeoForge 23 | 标签提交 `f9d6be04`；[逐文件大小和 SHA-256](v1.5.2-release-assets.json) |

发布时须使用当前源码的生产任务重新构建，按实际 JAR 的元数据和 SHA-256 核验后上传。

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
| Forge 1.20.1–1.21.1 | `jarJar`，包含重混淆任务 |
| Forge 1.21.3 及以后 | `allJar` |
| Fabric 1.20.1–1.21.11 | `remapJar` |
| Fabric 26.x | `jar` |
| NeoForge 根工程 1.20.1 | `jarJar`，沿用该工程的 ForgeGradle 构建 |
| NeoForge 其余版本 | `jar`，核对其嵌套依赖输出 |

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
