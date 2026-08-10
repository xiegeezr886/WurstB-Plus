# WurstB+ Plus 1.5.0

WurstB+ Plus 1.5.0 是由 Penguin 开发的 Wurst 增强客户端，目前支持 1.20.1、1.21.1、26.1.2 三个游戏版本的 Forge、NeoForge 与 Fabric 加载器，共 9 个发布组合。

## 构建修复

### v1.5.0 - 全平台适配与稳定性修复 (2026-08-07)

**NeoForge 1.21.1 启动即崩溃**

- 问题：启动时 `IllegalAccessError: class baritone.api.Settings cannot access class net.minecraft.world.item.Item (in module minecraft)`
- 根因：Baritone 以 jar-in-jar 库形式打包，NeoForge 21.1.x 的 JPMS 模块层将其隔离为独立模块 `baritone.api.forge`，该模块没有对 `minecraft` 模块的 read 边
- 修复：将 Baritone 类直接合并进 `wurstpenguin` 主模块，类归属主模块后即可访问 `minecraft`，不再作为独立库模块加载

**Forge 26.1.2 启动崩溃（JPMS 模块包冲突）**

- 问题：模块解析失败 `Module WurstB.Plus... contains package io.netty.util.concurrent, module io.netty.common exports`
- 根因：旧发布包把整个 netty 传递链（common/buffer/transport 等）扁平合并进主 jar，与游戏自带 netty 4.2.7 模块导出同一包
- 修复：仅合并不与其他模块冲突的 netty-codec-socks / netty-handler-proxy 自身包，依赖版本对齐游戏自带 netty 4.2.7；产物体积由 120MB 降至 32MB

**Forge 26.1.2 依赖声明错误**

- 问题：启动报 `Mod wurstpenguin requires neoforge` 无法加载
- 根因：旧发布包 mods.toml 错误声明依赖 `neoforge [26.1.2,27)`，Forge 64.1.0 环境下不存在该 mod
- 修复：依赖改为 `forge [64.1.0,65)`

**Forge 26.1.2 Baritone 注册失效**

- 问题：baritone-forge-1.18.0.jar 已嵌入但未被加载
- 根因：构建产物丢失 `META-INF/jarjar/metadata.json`（嵌套 jar 的注册清单）
- 修复：恢复 metadata.json，确认 Baritone 嵌套注册完整

**Forge/NeoForge 26.1.2 发布包资源丢失**

- 问题：发布包缺少全部 mod 资源（字体、翻译、shader、图标），Baritone 下界寻路类缺失
- 根因：打包任务沿用了为合并整个 runtimeClasspath 设计的 `exclude 'assets/**'`，把 mod 自身资源一并排除
- 修复：清理打包任务，仅合并 Baritone 类，恢复全部资源与 NetherPathfinder 类

**Fabric 26.1.2 启动崩溃（Mixin 注入失败）**

- 问题：StatusEffectInstanceMixin、ClientPlayerEntityMixin 注入失败导致启动崩溃
- 根因：MC 26.1.2 重命名了 `tickDownDuration()`（→ `mapDuration(Int2IntFunction)`）与 `hasEnoughFoodToStartSprinting()`（→ `canStartSprinting()`），旧注入目标不存在；refmap 声明指向不存在的文件（非混淆环境无需 refmap）
- 修复：更新注入目标，移除 refmap 声明，并将 mixin 清单对齐已验证的 65 项

**Fabric 1.21.1 HUD 渲染顺序错误（仅 Fabric）**

- 问题：打开半透明 ClickGUI 时，HUD 文字（TabGui、HackList 等）渲染在 GUI 下层，透过透明背景可见
- 根因：1.21.1+ 的 HUD 事件改由 `IngameHudMixin` 在 `Gui.renderTabList` HEAD 注入触发，屏幕打开时仍会派发；1.20.1 的 `HudRenderCallback` 在屏幕打开时不触发，故无此问题
- 修复：`renderTabList` 注入点增加 `WurstClient.MC.screen != null` 守卫，屏幕打开时跳过 HUD 渲染，与 1.20.1 行为一致；已同步应用至全部 6 个 1.21.1/26.1.2 工程

**模组图标更新**

- 替换全部 9 个工程的 `assets/wurst/icon.png` 为新版 Logo（等比缩放居中，400×400 透明背景）

**启动早期崩溃防护（全平台）**

- 问题：第三方整合包 / 移动端环境下，WurstClient 初始化前触发 mixin 导致空指针崩溃
- 根因：TextVisitFactoryMixin、LanguageManagerMixin、MinecraftClientMixin、TelemetryManagerMixin 直接访问 `getHax()`/`getTranslator()`/`getOtfs()` 未判空
- 修复：全部增加 null 防护；WurstMixinConfigPlugin 改用类资源存在性检查，避免提前触发类加载（修复 Supplementaries × Embeddium 的 MixinTargetAlreadyLoadedException）

### 构建链

- mixinextras 0.4.1 → 0.5.4，jarJar 版本范围放宽，解决整合包 mixinextras 版本冲突
- Forge 依赖范围 `[47.4.10,48)` → `[47,48)`，兼容 Forge 47.4.0 及更高版本
- 26.1.2 系列内嵌 baritone-forge/neoforge-1.18.0（jar-jar），Baritone 由 `baritone-maven/` 本地仓库提供
- 新增 scripts/build-all.ps1 一键构建 9 个产物；scripts/run-version-tests.ps1 在 PCL 测试环境批量真实启动验证（9/9 通过）

### 文件校验

```
WurstB+ Plus-v1.5.0-Forge-1.20.1.jar: CFC5EF862A0D822E20895AA69D952EB809D253D80CC89B2CB27172A5D2CDB9C0
WurstB+ Plus-v1.5.0-Forge-1.21.1.jar: A78BFEFA7BD7B4220EB7017827742CA477B971450CA08362D4C66C7BD32F00C8
WurstB+ Plus-v1.5.0-Forge-26.1.2.jar: 489DC385B0389AFF604B276829821E354F4FBD1EDBB384EA29708C3B4A27D5A2
WurstB+ Plus-v1.5.0-NeoForge-1.20.1.jar: 0C05A07ED15B5E4B84259D592777A00231A6C8639027E2C0E50C0E91E88ACB4D
WurstB+ Plus-v1.5.0-NeoForge-1.21.1.jar: 87622A2F7BC0692377A58B87D3B1787AC13CC12E7D87D872EB8DBD70E7452343
WurstB+ Plus-v1.5.0-NeoForge-26.1.2.jar: 5B772ED2791B2A1FE3315BF2F032BFFCC5F48B36F6D1EF306F10A85744C70BB3
WurstB+ Plus-1.5.0-Fabric-1.20.1.jar: 5382A8066844B38CE868590714F2B5F1A547DEAFFFD290934CAA3C32340F3727
WurstB+ Plus-1.5.0-Fabric-1.21.1.jar: 0E3838806FC21AC158CB3EB1550AE143F10D6066F84ADE7F73A69134ECCC110B
WurstB+ Plus-1.5.0-Fabric-26.1.2.jar: 8A2B04A5994B585BC3B8B7204DBA45BA3B5B1A6A5078CD8F10CA6F33E26865E0
```
