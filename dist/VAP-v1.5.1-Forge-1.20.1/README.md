# WurstB+ Plus — v1.5.1 Forge 1.20.1 源码

本目录是 **WurstB+ Plus v1.5.1、Minecraft 1.20.1、Forge** 的完整 Gradle 工程源码。

## 来源与重建说明

- 权威来源：本仓库 `neoforge/` 根工程在 git 标签 `v1.5.1` 的源码树
  （`mod_version=v1.5.1-NeoForge-1.20.1`）。
- 该工程在 1.20.1 上实际使用的是 **ForgeGradle 6 + `net.minecraftforge:forge` + `net.minecraftforge.*` 包名**
  （NeoForge 1.20.1 与 Forge 47.x 源码兼容；两者的差异从 1.20.2 起才出现），
  因此它就是仓库内 v1.5.1 的 1.20.1 Forge 源码。
- 源码目录 `src/` 与原工程**逐字节一致**（1268 个文件，0 处差异）。

> 说明：v1.5.1 发布页上的 Forge 1.20.1 产物是由仓库外的
> `VAP-v1.5.0-1.20.1` 工程构建的（该工程带本地改动，不在本仓库、也不在本机）。
> 本目录不是那个外部工程的逐字节副本，而是仓库内同一 v1.5.1 功能集
> （四客户端移植第 1–18 批）的 1.20.1 Forge 源码。

## 相对原 `neoforge/` 工程做了哪些改动

| 文件 | 改动 |
| --- | --- |
| `gradle.properties` | `mod_version` → `v1.5.1-Forge-1.20.1` |
| `gradle.properties` | `forge_version` 47.1.3 → **47.4.10**（与 v1.5.1 发布产物一致） |
| `gradle.properties` | `lwjgl_nanovg_version` 3.3.1 → **3.3.3**（与仓库其它 1.20.1 工程一致） |
| `build.gradle` | `flatDir` 由 `${rootProject.projectDir}/..` 改为 `projectDir`，并把 `baritone-api-forge-1.20.1.jar` 复制进工程根目录，使工程可独立移动 |
| `gradle/wrapper/gradle-wrapper.properties` | Gradle 8.14.4 → **8.11**（本机已缓存、且根工程验证过 ForgeGradle 6 可用的版本） |
| `README.md` | 由 NeoForge MDK 模板说明替换为本文件 |

除此之外的构建脚本、资源、Mixin 配置、访问转换表均为原样。

## 同步记录

**2026-11-13：已同步仓库最近两个提交（HEAD `0a988187`）到本工程。**

| 提交 | 内容 | 本工程更新的文件 |
| --- | --- | --- |
| `e6cd42b2` | fix(render): stabilize NanoVG rendering across v1.5.1 versions | `src/main/java/net/wurstclient/hud2/nanovg/GlStateGuard.java`<br>`src/main/java/net/wurstclient/hud2/nanovg/NanoVGContext.java` |
| `0a988187` | fix: improve modpack compatibility and cross-version packaging | `src/main/java/net/wurstclient/mixin/ReachLegacyRendererMixin.java`<br>`src/main/java/net/wurstclient/mixin/WurstMixinConfigPlugin.java` |

同步后的 4 个文件与 git index 中记录的 blob SHA-1 完全一致（即仓库 HEAD 内容）：

- `GlStateGuard.java` → `6728e06b…`
- `NanoVGContext.java` → `5f5b2a7b…`
- `ReachLegacyRendererMixin.java` → `556de64c…`
- `WurstMixinConfigPlugin.java` → `f31b0ee9…`

说明：

- 2026-11-12 的崩溃热修（`620270ce`，含 NanoVG 错误释放修复）在创建本工程时已包含在工作区源码里，本次同步后仍然保留。
- 与 1.20.1 工程无关的改动未纳入，例如 Fabric 根工程 `HudManager` 的测试过滤清理、1.21.6+ 新增的 `NanoVgGuiRenderStateMixin`、CI/测试/报告文件。
- `ReachLegacyRendererMixin` 已从 `@ModifyConstant` 改为 `@ModifyExpressionValue(..., require = 0)`，与 `reach-entity-attributes` 组合而不是抢占常量，正是 1.20.1 整合包崩溃的修复。

## 构建

要求：JDK 17、可访问 Maven 仓库的网络（首次构建需要下载 Forge/Minecraft 依赖）。

```powershell
# 在工程目录内
.\gradlew.bat build          # 或 .\gradlew build
```

产物：`build/libs/WurstB+ Plus-v1.5.1-Forge-1.20.1.jar`
（含 `jarJar` 内嵌的 Baritone、MixinExtras、Netty、Java-WebSocket）。

开发运行：

```powershell
.\gradlew.bat runClient
```

## 已知限制

- 本机没有可用的网络，Gradle 依赖缓存也不完整，**未能在本机完成一次完整的
  `compileJava`/`build` 验证**（缺少 ForgeGradle/MixinGradle 的部分传递依赖）。
  源码本身与仓库内已发布的 v1.5.1 工程逐字节一致，构建脚本只改了上表所列内容。
- 外部 `VAP-v1.5.0-1.20.1` 工程里的本地改动（额外的 Combat* 工具类、部分 hack、
  中文翻译等）无法从本仓库还原，因此本目录不保证与发布 jar 完全一致。

## 目录

```
build.gradle                 ForgeGradle 6 构建脚本
settings.gradle              插件仓库（阿里云镜像 + Forge）
gradle.properties            MC 1.20.1 / Forge 47.4.10 / mod v1.5.1
gradlew, gradlew.bat         包装器
baritone-api-forge-1.20.1.jar  内嵌 Baritone（flatDir 依赖）
src/main/java/               ~1000 个 Java 源文件
src/main/resources/          资源、Mixin 配置、访问转换表、mods.toml
```
