# 跨版本兼容性抽查（2026-11-12）

累计抽查 7 个工程，覆盖 Fabric、NeoForge、Forge，以及 1.20.x、1.21.x、26.x，均已完成完整 `build` 和实际发布 JAR 检查。此前 Forge 26.3 的 Java 8 ZIP 文件系统阻塞已解决，随后发现的 LICENSE 重复打包错误也已修复；额外执行 `allJar` 生成可分发包。本轮没有启动这些样本的游戏，结果不能视为大型整合包运行通过。

## 样本结果

| 工程 | 完整构建 | 工程测试 XML | 发布归档检查 |
|---|---|---|---|
| Fabric 1.20.4 | 通过，7 分 51 秒 | 无测试 XML，不计测试通过数 | 通过 |
| Fabric 1.20.6 | 修复后通过，1 分 46 秒；重新输出 JAR 1 分 8 秒 | `test NO-SOURCE`，不计测试通过数 | 通过；已删除的 Mixin 类不再残留 |
| Fabric 1.21.11 | 再次构建通过，1 分 14 秒 | 106 项，失败/错误/跳过均为 0 | 通过 |
| Fabric 26.2 | 通过，2 分 23 秒 | `test NO-SOURCE`，不计测试通过数 | 通过 |
| NeoForge 1.21.1 | 再次构建通过，45 秒 | 106 项，失败/错误/跳过均为 0 | 通过 |
| NeoForge 26.3 | 修复后通过，56 秒；再次构建 19 秒 | `test NO-SOURCE`，不计测试通过数 | 通过；LICENSE 文件存在 |
| Forge 26.3 | 修复后 `build` 通过，32 秒；随后 `build allJar` 通过，31 秒 | `test NO-SOURCE`，不计测试通过数 | 通过；LICENSE 仅一份，Baritone 嵌套元数据指向实际文件 |

NeoForge 26.3 首次成功构建记录 `Configuration cache entry stored.`，再次构建记录 `Reusing configuration cache.` 和 `Configuration cache entry reused.`。

Forge 26.3 首次准备上游游戏/Forge 构建输入耗时约 11 分 50 秒；表内 32 秒和 31 秒为修复 LICENSE 打包后复用这些输入的构建耗时。抽查期间工作区还存在其他来源的更新；保留这些改动，产物哈希以最后一次归档核验为准，构建耗时来自本次抽查保存的日志。

## 抽查发现与修复

### Fabric 1.21.11 的 Mixin 注册遗漏及遗留类

首次构建编译通过，但 `MixinPackageIsolationTest` 报告 `AvatarMixin` 未注册，106 项测试中有 1 项失败。

核对源码调用和本次构建使用的 Mojang 映射游戏 JAR 后，在 `wurstpenguin.mixins.json` 补注册 `AvatarMixin`、`EntityCullingMixin`、`KeyboardHandlerMixin`，恢复主手、实体遮挡和按键事件回调。通过 `javap` 核对的目标为：

```text
Avatar.getMainArm()Lnet/minecraft/world/entity/HumanoidArm;
EntityRenderDispatcher.shouldRender(Entity, Frustum, DDD)Z
KeyboardHandler.keyPress(JILnet/minecraft/client/input/KeyEvent;)V
```

删除未注册的旧 `IngameHudMixin`、`FluidRendererMixin`。它们包含旧方法签名，分别已有注册的 `GuiMixin` 和 `xray/LiquidBlockRendererMixin` 承担新版实现。

修复后完整构建成功，41 份测试 XML 合计 106 项全部通过。现代 Sodium 方块和流体 X-Ray 适配均已注册；配置名称中的 `/` 按包分隔符规范化后检查通过。

### 可选 Sodium 探测提前定义目标类

发现 32 份 `WurstMixinConfigPlugin` 仍用 `Class.forName(targetClassName, false, loader)` 判断目标是否存在。`false` 只阻止初始化，仍会加载和定义目标类，存在干扰 Mixin 变换时序的风险。

将这 32 份实现改为探测目标 `.class` 资源，不改变原有可选 Mixin 选择条件。其余 35 份本来已使用资源探测。

独立 JUnit 测试使用禁止 `loadClass` 的 ClassLoader，验证存在资源时启用、不存在时跳过，并覆盖全部 4 种插件源码变体。独立测试总计 42 项通过。本项是静态加载时序风险修复，本轮未复现具体游戏崩溃。

### NeoForge 26.3 的配置缓存打包失败

首次构建在 `jar` 任务失败：LICENSE 重命名闭包在执行阶段访问 `project`，违反已启用的 Gradle configuration cache 约束。

将重命名目标在配置阶段求值，修复后完成编译、打包，并成功存储和复用配置缓存。相同一行修复回填到 NeoForge 26.1、26.1.1、26.1.2、26.2；这 4 个兄弟工程没有分别完整构建，不计入已通过样本。

### 40 份旧列表空回调仍指向旧渲染方法

Fabric 1.20.6 首次构建虽然成功，但 Mixin 注解处理器警告：`AbstractSelectionList` 找不到 `render(Lnet/minecraft/client/gui/GuiGraphics;IIF)V`。使用实际 Mojang 映射游戏 JAR 的 `javap` 输出确认，该类声明的是 `renderWidget`，没有声明旧 `render` 方法。

对应 `wurst$transparentBackground` 方法体为空，既没有背景处理，也没有其它副作用；保留强制注入只增加目标查找失败的风险。因此删除 40 份源码完全相同的旧空回调类及其注册项。保留较老版本确实调用 `setRenderBackground(false)` 的实现，也保留 26.x 使用不同提取入口的实现。不能把这项静态证据描述为 40 个版本都已实测启动崩溃。

修复后 Fabric 1.20.6 重新编译通过，旧注入警告消失。Fabric 1.21.11、NeoForge 1.21.1 也重新构建，各 106 项测试通过。核对发布 JAR，三者均已不含删除的 `AbstractSelectionListMixin.class`。

旧版 Loom 覆盖 Fabric 1.20.6 的现有重映射 JAR 时曾保留已删除类；开发 JAR 和散装类输出已经清理，发布 JAR 仍有残留。删除该生成产物后重新执行构建，最终核验确认残留消失。该操作仅针对工程 `build/libs/` 下的生成文件。

### Forge 26.x 重复打包 LICENSE

Forge 26.3 编译通过后，`jar` 任务因重复条目 `LICENSE-WurstB+ Plus.txt` 失败。该文件已位于资源目录，构建脚本又从根目录 `LICENSE.txt` 重命名加入同一目标。

确认 Forge 26.1、26.1.1、26.1.2、26.2、26.3 的两份 LICENSE 内容均逐字节一致后，移除这 5 个工程中冗余的根目录复制块，保留资源目录中的许可证文件。Forge 26.3 完整构建及 `allJar` 均通过，发布 JAR 只含一份该文件；另外 4 个兄弟工程未分别完整构建。

## 检查范围与证据

7 个样本均未发现仍被调用但未注册的 Accessor/Invoker；新加样本的配置引用均有对应源码。全仓库检查未发现对 `NVGColor.create()` 对象错误调用 `color.free()`，已检查的可选目标探测均不再使用 `Class.forName`。NeoForge 1.21.1 平铺的 Baritone Mixin 配置与实际依赖 JAR 一致。

7 个发布 JAR 通过 `scripts/audit-crash-regressions.py` 的 `check_archive()` 检查：Baritone 配置遮蔽、空入口与元数据匹配、按实际类推导的着色器资源，以及 Fabric 内嵌 MixinExtras 版本。Forge 26.3 审计的是 `allJar` 生成的发布包，不将开发 JAR 充作可分发包。

删除旧空回调后全仓库回归扫描再次通过，覆盖 67 个工程、66 份颜色缓存和原生库缓存实现、67 份翻译与密钥迁移实现、172 处常量修改注入、7 份平铺 Baritone 配置，以及被调用 Accessor 的注册检查。后续 LICENSE 打包修复经过实际构建和归档核验。`git diff --check` 通过。发布 JAR 未混入测试用 ZIP agent/helper 类。

构建日志位于 `.dsh-tmp/sample-*.log`；修复前失败日志另存为 `*-before-fix.log`。结构化结果为 `.dsh-tmp/version-sample-sources.json`、`.dsh-tmp/version-sample-artifacts.json`，独立测试日志为 `.dsh-tmp/version-sample-unit-tests.log`。

全仓库回归扫描日志为 `.dsh-tmp/version-sample-regression-audit.log`；NeoForge 26.3 首次成功日志另存为 `sample-neoforge-versions-26.3-first-success.log`，常规同名日志记录第二次配置缓存复用构建。

继续抽查的结构化结果为 `.dsh-tmp/version-sample-continued.json`，再次全仓库扫描日志为 `.dsh-tmp/version-sample-continued-regression-audit.log`。Fabric 1.20.6 旧方法签名证据保存在 `.dsh-tmp/abstract-selection-list-1.20.6-javap.txt`，40 个修复工程列表为 `.dsh-tmp/removed-empty-list-mixins.json`。Forge LICENSE 失败日志为 `sample-versions-26.3-before-license-fix.log`，修复后 `build` 日志为 `sample-versions-26.3-build-success.log`，常规同名日志记录最终 `build allJar`。

| 产物（各工程 `build/libs/`） | 字节数 | SHA-256 |
|---|---:|---|
| `WurstB+ Plus-1.5.1-Fabric-1.20.4.jar` | 53,082,104 | `cc2dd2a440e00e46591763c74ace84cac403db189080e51c7bceaaaa2cdfa3e4` |
| `WurstB+ Plus-1.5.1-Fabric-1.20.6.jar` | 53,085,225 | `9b56252387337b050b90427bb81464597229caa67243d3224a570f08e7570a1c` |
| `WurstB+ Plus-1.5.1-Fabric-1.21.11.jar` | 53,144,670 | `6771d10a579a0a0c67e1cc56eb417bbd75131a64378316ad32291935b648fec5` |
| `WurstB+ Plus-1.5.1-Fabric-26.2.jar` | 53,008,683 | `ff41d803d2cb075ff48528b888c817c04cf2121069d143ba05c564e52ada69ee` |
| `WurstB+ Plus-v1.5.1-NeoForge-1.21.1.jar` | 52,547,152 | `2680c623b86c9b8cbf6e9e7bade4cb7cd6307754f10d78d18546efcee94c5edb` |
| `WurstB+ Plus-v1.5.1-NeoForge-26.3.jar` | 52,392,275 | `7fe599640b472d3c08368d2a4fe4a81d2da23529f542138fdadb58a6fb75b290` |
| `WurstB+ Plus-v1.5.1-Forge-26.3.jar` | 52,433,174 | `463563ba2fef37771703ca3a36e81f6cd5aa270d72f053ef3c5c5e9889bf97c8` |

## 已解决的 Forge 26.3 环境阻塞

最初 ForgeGradle Mavenizer 启动 Java 8 Access Transformer 处理游戏构建输入时，`com.sun.nio.zipfs.ZipFileSystem` 抛出 `ReadOnlyFileSystemException`。本机 `Files.isWritable` 对实际可以非截断写打开的任务缓存返回 false。

Java 8 是构建工具子进程使用的运行时，26.3 游戏和工程目标仍为 Java 25。

成功构建使用了仅存于 `.dsh-tmp/` 的临时 ZIP 写权限探测 agent，限定任务缓存及相应工程的 `build`、`.gradle` 目录，以非截断写打开复核错误的只读判定。不修改系统 ACL，不打入发布 JAR。继续处理中补充了 Java 8 兼容的 helper 和 transformer，按运行时选择 Java 8 或 Java 25 实现，使 Mavenizer 子进程也能通过 ZIP 写入检查。随后完成上游源码编译、客户端源码编译和发布打包。

## 交付状态

修复保留在当前工作区，本轮未提交或推送。未修改 `dist/`。完整整合包的游戏运行进度另见 `MODPACK-COMPAT-2026-11-12.md`；其中加入 Wurst 后的世界内功能与保存重进测试仍未完成。
