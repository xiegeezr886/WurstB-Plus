# 全版本 compileJava 矩阵（65 / 65）

复验日期：2026-11-06
首次矩阵：2026-10-27
范围：除根目录 Forge 1.20.1（v1.6.0）外的全部 65 个 v1.5.1 工程。

## 2026-11-06 复验结果

在当前源码（含 1.20.5/1.20.6 的 `@Local` 尾置参数修复、26.1/26.2 的
GpuSampler blitter、Forge refmap/classes 打包修复）上重新执行 compileJava：

| 分组 | 工程数 | PASS |
| --- | ---: | ---: |
| Fabric（1.20.2–26.3） | 22 | 22 |
| Forge（1.20.2–26.3） | 20 | 20 |
| NeoForge（根 1.20.1 + 1.20.2–26.3） | 23 | 23 |
| **合计** | **65** | **65** |

NeoForge 1.20.2–1.20.6（userdev）在当前网络下无法访问
`maven.neoforged.net`，复验时使用：

1. `_recon/port/neoforge-maven` 本地仓库（从 Gradle 模块缓存镜像，并为缺少
   POM 的 artifact 生成最小 POM）。
2. `_recon/port/neoforge-local-repo.gradle`：本地仓库置前、移除不可达的
   NeoForged 仓库、补 `groovydslimprover:base`。
3. `_recon/port/skip-neo-cache.gradle`：跳过会删除离线缓存的
   `cacheVersion*` 任务。
4. 手动把 vanilla `client.jar` 放入
   `neoforge/versions/<mc>/.gradle/caches/minecraft/versions/<mc>/client.jar`。
5. 在线运行 `compileJava`，让 `neoFormListLibraries` 从 Mojang 下载 MC 库。

NeoForge 1.21+（ModDevGradle）与 26.x 可用 `--offline` 直接编译；
根 `neoforge/` 1.20.1 在线编译。

## 结果

| JDK 带 | 工程数 | PASS | FAIL |
| --- | ---: | ---: | ---: |
| JDK 17（1.20.2–1.20.4 + `neoforge/` 1.20.1） | 10 | 10 | 0 |
| JDK 21（1.20.5–1.21.11） | 40 | 40 | 0 |
| JDK 25（26.1–26.3） | 15 | 15 | 0 |
| **合计** | **65** | **65** | **0** |

`fabric/versions/1.21.6` 在第一轮矩阵中因 `GUIRenderListener` 的
`HackHudElement` 导入位于注释内而失败；修复后单工程复测 PASS，并已重跑
JDK 21 全带。

## 本轮实际同步的内容

- 新 HUD 架构整体替换：
  - `hud2/nanovg/*`（NanoVG 上下文、字体、图标、原生加载器）
  - `hud2/compat/*`（HudCompat / HudTextures / HudItemIcons 的版本适配）
  - `hud2/mods/*`（60 个 HUD 模块）
  - `hud2/HUDMod`、`SimpleHUDMod`、`HudAnimation`、`HudTheme`、
    `HudSettingsFeature`、`HypixelStatsManager`
  - `hud2/HudManager`、`GuiEditHUD`、`HudNotificationRenderer`
  - `assets/wurstpenguin/nanovg/fonts/*`
- 核心接线：
  - `Category.HUD`
  - `Hack` 移除旧 `updateHudState()` 钩子
  - `IngameHUD` 移除 `HackListHUD` / `WurstLogo`
  - `GUIRenderListener` 的 `HackHudElement` 兼容 shim
  - 删除旧 `hud2/elements/*`、`HackHudElement` 旧实现、`hud/HackListHUD`、
    `hud/WurstLogo`、旧 `HudEditorScreen` / `HudElement` / `HudLayout`
  - `TestControlServer` 适配新 HUD 管理器（1.21.11）
- 每个工程的 `build.gradle` / `gradle.properties`：
  - `hudNatives` 配置
  - `org.lwjgl:lwjgl-nanovg` 依赖与平台 natives
  - `processResources` 解包（按 loader 版本）
  - `lwjgl_nanovg_version`：1.20.1=3.3.1，1.20.2–1.20.4=3.3.2，
    1.20.5–1.21.11=3.3.3，26.1–26.2=3.4.1，26.3=3.4.3

## 2026-10-27 首轮未做的事（后续进展见上）

- 未移植 Fabric 1.20.1 的 ClickGUI / 标题界面 NanoVG 重构。
- 未移植 Scaffold 新实现、OpenAura、IMG G*/TP hack、目标 HUD 新模块
  （这些依赖本轮 HUD 之外的文件，后续单独批量）。
- 渲染尚未完成：
  - 1.21.2–1.21.8 的 `NanoVgBlitter` 为 no-op 编译桩。
  - 1.21.5+ 的 `NanoVGUI` / `HudManager` 合成路径已移除 `bindWrite`。
  - `HudTextures` / `HudItemIcons` 在多数新版本为禁用图片/物品图标的桩。
- 未做生产 jar 打包校验、未做真实启动/进世界验证。

## 复现命令

```powershell
# JDK 17 带
powershell -ExecutionPolicy Bypass -File scripts\port\Verify-CompileMatrix.ps1 -Band 17
# JDK 21 带
powershell -ExecutionPolicy Bypass -File scripts\port\Verify-CompileMatrix.ps1 -Band 21
# JDK 25 带
powershell -ExecutionPolicy Bypass -File scripts\port\Verify-CompileMatrix.ps1 -Band 25
# 仅 NeoForge 1.21+ / 26.x（离线）
powershell -ExecutionPolicy Bypass -File scripts\port\Verify-CompileMatrix.ps1 -Only "neoforge/versions/1.21*" -Offline
```

每个工程的完整日志在 `_recon/port/compile-matrix/<project>.log`，
CSV 为 `matrix-17.csv` / `matrix-21.csv` / `matrix-25.csv`。

## 工具与脚本

- `scripts/port/PortConfig.ps1`：65 个目标、版本族、JDK、NanoVG 版本、生产任务
- `scripts/port/New-PortManifest.ps1`：Fabric delta 清单
- `scripts/port/Verify-CompileMatrix.ps1`：本轮矩阵
- `_recon/port_hud_legacy.py`、`port_hud_wrapper.py`、`port_hud_native.py`：
  HUD 树复制
- `_recon/adapt_1202_1204.py`、`adapt_1205_1206.py`、`adapt_wrapper.py`、
  `to_event_input.py`、`fix_event.py`、`adapt_26x.py`：版本族适配
- `_recon/patch_nanovg_simple.py`、`fix_forge_build.py`、
  `insert_forge_deps.py`、`patch_neoforge_root.py`：构建配置
