# 1.21.11 / 26.2 移植与构建状态

> 基线：远程 v1.5（`origin/main` b40810a，9 工程布局）
> 方式：在当前工作区直接新建 6 个工程目录，源码已从远程 v1.5 提取完成
> 开始日期：2026-08-08  
> 最终验证：2026-08-10

## 1. 目标版本矩阵（已调研确认）

| MC 版本 | Forge | NeoForge | Fabric Loader | Fabric API | Java | 协议模式 |
|---|---|---|---|---|---|---|
| **1.21.11** | **61.2.0** | **21.11.45** | 0.19.3 | **0.141.6+1.21.11** | 21 | Mojang 官方映射 |
| **26.2** | **65.1.0** | **26.2.0.53-beta** | 0.19.3 | **0.156.0+26.2** | 25 | **非混淆**（intermediary 0.0.0，同 26.1.2 模式）|

注意：
- 本工程锁定 NeoForge **26.2.0.53-beta**
- 1.21.11 的 Forge/NeoForge 为稳定版（61.2.0 / 21.11.45）
- 26.2 无 intermediary → Fabric 工程走 26.1.2 的官方名非混淆模式

## 2. 目录结构（已提取，与远程 v1.5 文件数一致）

```
versions/1.21.11          (900 文件)  ← 远程 versions/1.21.1
versions/26.2             (904 文件)  ← 远程 versions/26.1.2
neoforge/versions/1.21.11 (897 文件)  ← 远程 neoforge/versions/1.21.1
neoforge/versions/26.2    (904 文件)  ← 远程 neoforge/versions/26.1.2
fabric/versions/1.21.11   (950 文件)  ← 远程 fabric/versions/1.21.1
fabric/versions/26.2      (898 文件)  ← 远程 fabric/versions/26.1.2
```

## 3. 各工程改造清单

### 3.1 `versions/1.21.11`（Forge 1.21.11，基于 1.21.1 工程）

| 文件 | 改动 |
|---|---|
| `gradle.properties` | `minecraft_version=1.21.11`、`forge_version=61.2.0`、`mod_version=v1.5.0-Forge-1.21.11` |
| `build.gradle` | ForgeGradle 7.x；内嵌官方 1.21.11 分支构建的 Baritone Forge 1.17.0；MixinExtras 0.5.4 |
| `META-INF/mods.toml` | 依赖 `forge [61.2.0,62)` |
| Java | 21 |

### 3.2 `versions/26.2`（Forge 26.2，基于 26.1.2 工程）

| 文件 | 改动 |
|---|---|
| `gradle.properties` | `minecraft_version=26.2`、`forge_version=65.1.0`、`mod_version=v1.5.0-Forge-26.2` |
| `build.gradle` | 继承 allJar 打包（baritone jarjar、netty 仅 socks/proxy 包、排除游戏自带包防 JPMS 冲突）|
| `META-INF/mods.toml` | 依赖 `forge [65.1.0,66)` |
| Java | 25 + `_JAVA_OPTIONS=-Djavax.net.ssl.trustStoreType=Windows-ROOT` |

### 3.3 `neoforge/versions/1.21.11`（基于 1.21.1 工程）

| 文件 | 改动 |
|---|---|
| `gradle.properties` | `neoforge_version=21.11.45`、`mod_version=v1.5.0-NeoForge-1.21.11` |
| `build.gradle` | ModDevGradle 2.0.143，已通过 `clean build` |
| `neoforge.mods.toml`（templates）| 依赖 `neoforge [21.11.45,)` |
| Java | 21 |

### 3.4 `neoforge/versions/26.2`（基于 26.1.2 工程）

| 文件 | 改动 |
|---|---|
| `gradle.properties` | `neoforge_version=26.2.0.53-beta`、`mod_version=v1.5.0-NeoForge-26.2` |
| `build.gradle` | JarJar 内嵌 baritone-neoforge-1.18.0-26.2、WebSocket 与 Netty；已通过 `clean build` |
| `neoforge.mods.toml` | 依赖 `neoforge [26.2.0.53-beta,)` |
| Java | 25 |

### 3.5 `fabric/versions/1.21.11`（基于 1.21.1 工程）

| 文件 | 改动 |
|---|---|
| `gradle.properties` | `minecraft_version=1.21.11`、`loader_version=0.19.3`、`fabric_version=0.141.6+1.21.11`、`mod_version=1.5.0-Fabric-1.21.11` |
| 映射策略 | `loom.officialMojangMappings()`，已通过编译和运行验证 |
| `fabric.mod.json` | `minecraft ~1.21.11`、`java >=21` |
| Java | 21 |

### 3.6 `fabric/versions/26.2`（基于 26.1.2 工程）

| 文件 | 改动 |
|---|---|
| `gradle.properties` | `minecraft_version=26.2`、`loader_version=0.19.3`、`fabric_version=0.156.0+26.2`、`mod_version=1.5.0-Fabric-26.2` |
| 非混淆模式 | 继承 26.1.2：无 mappings、`implementation` 依赖、无 refmap |
| `fabric.mod.json` | `minecraft ~26.2`、`java >=25` |
| Java | 25 |

## 4. 已验证的 API 差异

### 4.1 1.21.1 → 1.21.11（跨 10 个版本，风险高）

| 领域 | 预期变化 | 影响模块 |
|---|---|---|
| 渲染管线 | 1.21.2+ 逐步引入 extract/render；1.21.11 可能已全面迁移（`GuiGraphics`→`GuiGraphicsExtractor`、`blit` 需 `RenderPipelines`、8 位颜色）| 全部 GUI/HUD（26.1.2 移植经验可直接复用）|
| 输入系统 | 1.21.5 输入重构（`Input` 类、`KnownInput`）| 移动/按键模块 |
| 物品攻击范围 | 1.21.11 新增 `attack_range` 物品组件（Grim 源码有对应处理）| Killaura/Reach |
| 注册表/数据组件 | 1.21.2-1.21.10 持续演进 | 动态注册表、物品相关 |
| Forge API | 61.x 的 Forge 事件/Mixin 兼容 | 平台适配层 |
| Baritone | 已从官方 1.21.11 分支构建 Fabric/Forge/NeoForge 1.17.0，并内嵌到最终单 JAR | Baritone 集成 |

### 4.2 26.1.2 → 26.2（同系列小步升级，风险低）

| 领域 | 预期变化 | 影响模块 |
|---|---|---|
| 渲染管线 | 26.2 延续 extract/render；可能小幅 API 调整 | GUI/HUD |
| NeoForge | 26.2.0.x 仍 beta，API 可能有未稳定变动 | 平台适配层 |
| Forge | 65.1.0（64.1.0 → 65.1.0）| 平台适配层 |

## 5. 已完成的移植步骤

1. 六工程版本配置、元数据和 Java 工具链已完成。
2. 渲染、输入、注册表、网络包和加载器 API 已完成适配。
3. 1.21.11 使用 Baritone 1.17.0-1.21.11；26.2 使用带兼容补丁的 Baritone 1.18.0-26.2。
4. 三加载器均把 Baritone 内嵌到最终单 JAR，无需单独安装。
5. `build-all.ps1` 已支持构建、发布和包结构检查；`run-version-tests.ps1` 已支持 Quick Play 与后台命令验证。

## 6. 交付物

- 6 个可编译工程（Forge/NeoForge/Fabric × 1.21.11/26.2）
- 每个工程 `PORTING_TASK.md`（记录实际 API 差异与修复）
- 根 README「支持版本」表 + `build-all.ps1`/`run-version-tests.ps1` 更新
- 产物聚合 `download/` + `.test/` 实例

## 7. 最终构建与测试

```powershell
powershell -ExecutionPolicy Bypass -File scripts\build-all.ps1 `
  -Version 1.21.11 -Clean -PublishToDownload
powershell -ExecutionPolicy Bypass -File scripts\build-all.ps1 `
  -Version 26.2 -Clean -PublishToDownload

powershell -ExecutionPolicy Bypass -File scripts\run-version-tests.ps1 `
  -Version 1.21.11 -QuickPlayWorld WurstSmokeFresh `
  -BaritoneCommand '#goto 0 88 0'
powershell -ExecutionPolicy Bypass -File scripts\run-version-tests.ps1 `
  -Version 26.2 -QuickPlayWorld WurstSmokeFresh `
  -BaritoneCommand '#goto 0 88 0'
```

六个版本均通过构建、核心类与内嵌 Baritone 检查，并真实进入单人世界获得 Baritone `Going to:` 响应。本轮冒烟测试未发现阻塞发布的问题；未穷举每个功能模块的所有设置组合。

- 1.21.11 运行报告：`.test/report-20260810-024942.txt`
- 26.2 运行报告：`.test/report-20260810-025205.txt`
