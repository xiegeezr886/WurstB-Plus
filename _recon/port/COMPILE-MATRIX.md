# 全版本 compileJava 矩阵（65 / 65）

日期：2026-10-27
范围：除根目录 Forge 1.20.1（v1.6.0）外的全部 65 个 v1.5.1 工程。

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

## 本轮未做的事（按“先 compileJava，冒烟后面再说”）

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
