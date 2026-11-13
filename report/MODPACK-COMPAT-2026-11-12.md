# 大型整合包兼容验证（2026-11-12）

## 固定输入

- 整合包：Prominence II: Hasturian Era 4.1.7，Modrinth 项目 `EGs3lC8D`，版本 `rt1vDkAc`。
- Minecraft 1.20.1 / Fabric Loader 0.19.3 / Azul Java 17.0.21；最大堆 8 GiB。
- Windows 11 amd64，RTX 5070 Laptop GPU；构建使用 Java 21、Gradle 8.11、Loom 1.9.2，目标 Java 17。
- 原 MRPACK：562 个清单文件，461 个顶层模组 JAR；1244 条递归 Fabric 元数据、696 个不同 ID。后两项是扫描库存，不是 Loader 最终解析后的模组数量。
- Modrinth 包自己的 FancyMenu 安装页面要求补装 FTB Quests 2001.4.18、FTB Library 2001.2.12、FTB Teams 2001.3.2、FTB XMod Compat 2.1.3。测试从 FTB 官方 Maven 下载这些指定版本，不将其二进制重新发布。完整测试包为 465 个顶层模组 JAR，加入 Wurst 后为 466 个。
- 完整 URL、MRPACK SHA-512、4 个补充依赖的 SHA-512 保存在 `scripts/modpack-profiles/prominence-ii-4.1.7.json`。
- 关键共存组件包括 Sodium 0.5.13、Iris 1.7.6、Create 6.0.8.1、reach-entity-attributes 2.4.0、FancyMenu 3.7.0、Essential、Lithium、ModernFix、Indium 和 EntityCulling。

所有下载、实例、离线测试账号和存档均位于 `.dsh-tmp/modpacks/prominence-ii/`，没有使用用户真实账号凭据或修改用户启动器实例。

## 已复现并修改

### FancyMenu 与 Wurst 主菜单互相覆盖

加入本轮修复前的 Wurst 构建后，屏幕显示 Wurst 自定义主菜单，单人游戏按钮的鼠标和键盘操作不能进入世界选择界面。整合包的 FancyMenu 同时管理标题页布局及交互控件；Wurst 在 `TitleScreen.init` 结束时清空并替换控件，破坏了同一页面的控件所有权。

修复：67 个独立工程的 `WurstMixinConfigPlugin` 均在 FancyMenu 类资源存在时跳过 Wurst 的 `TitleScreenMixin`。检查仅读取资源，不初始化 FancyMenu 类，也不增加对它的依赖。未装 FancyMenu 时继续使用 Wurst 标题页；游戏内功能注入继续保留。

修复后的实际游戏窗口恢复了整合包原安装提示页，正确显示上述缺少的 FTB 依赖，而不是覆盖为 Wurst 菜单。

### Reach 的旧式常量注入被属性模组跳过

实际启动日志：

```text
@ModifyConstant conflict. Skipping wurstpenguin.mixins.json:ReachLegacyRendererMixin
... already redirected by mixins.reach-entity-attributes.json:client.GameRendererMixin
```

此前的 `require = 0` 能避免启动崩溃，但这一段 Reach 注入仍被丢弃。本轮将 11 个相同的旧版 `ReachLegacyRendererMixin` 改为 MixinExtras `@ModifyExpressionValue`，允许与属性模组组合。

实测导出的 `GameRenderer`（运行时名 `net.minecraft.class_757`）字节码中，同一个 `6.0` 表达式先调用 reach-entity-attributes 的 `getActualReachDistance`，再调用 Wurst 的 `wurstEntitySearchDistance`。修复后的日志不再出现这条 Wurst 注入冲突。此证据验证了实际织入顺序；不等于已测量所有装备、属性加成组合的攻击距离。

## 构建和自动检查

- Fabric 1.20.1 完整 `build` 成功，包含 Java 编译、资源处理、JAR 重映射和 Access Widener 校验。本轮增量构建耗时 2 分 48 秒。
- 独立 Java/JUnit 行为测试：38 项通过，其中 16 项覆盖 67 份插件的全部 4 种源码变体，检查有/无 FancyMenu、空上下文 ClassLoader 和游戏注入保留。
- Python 测试：18 项通过，包括路径越界拒绝、哈希错误保留旧文件、已校验缓存复用、嵌套元数据读取和 Windows 原生库架构选择。
- Gradle 的 `test` 任务执行不代表已发现所有 JUnit 5 测试；上述具体测试数量来自独立 JUnit 控制台执行。

## 可复现的准备命令

在有 Python 3.11+ 的 Windows x64 环境中，从仓库目录运行：

```powershell
python scripts/modpack-compat.py --profile scripts/modpack-profiles/prominence-ii-4.1.7.json --output .dsh-tmp/modpacks/prominence-ii/instance --runtime
python scripts/modpack-compat.py --output .dsh-tmp/modpacks/prominence-ii/instance --java-home C:/path/to/jdk17 --log .dsh-tmp/modpacks/prominence-ii/console.log
```

第一条命令只用于初次准备/恢复整合包配置；复测启动时运行第二条，避免重新覆盖测试设置。测试脚本按 Windows x64 解析原生库；不宣称能准备其他平台的运行环境。

先不放 Wurst 运行基线，再将 `fabric/build/libs/WurstB+ Plus-1.5.1-Fabric-1.20.1.jar` 放入隔离实例 `mods/` 运行对照。保留所有原模组及 4 个包要求的补充依赖。

## 测试环境处理

本机自动化执行环境中 Java `Files.isWritable` 曾对实际可以非截断写入的缓存文件返回 false，使 ZIP 文件系统和 TinyRemapper 错判只读。测试使用仅位于 `.dsh-tmp/zip-probe-agent/` 的临时 Java agent，对 ZIP FS/TinyRemapper 的该项检查使用真实的非截断写入打开来复核；作用范围限于任务缓存和 Fabric 构建目录。不改变系统 ACL，不打包进发布 JAR。

JNA 的临时目录和子进程临时目录定向到隔离实例；`APPDATA`、`LOCALAPPDATA` 也仅为测试进程重定向。启动脚本筛掉 LWJGL 的 ARM64/x86 原生变体，避免同名 DLL 覆盖 x64 版本。以上为测试环境处理，不是客户端生产代码修复。

离线测试账号引起的认证 401、皮肤下载错误，以及基线也有的可选 Mixin 目标缺失，不作为新增客户端崩溃。未向 Crash Assistant、Discord 或其他第三方上传日志。

## 运行记录

| 测试阶段 | 已观察结果 |
|---|---|
| 原 MRPACK、未加入 Wurst | 完成初始化和资源加载；日志显示启动完成 |
| 原 MRPACK、加入修复前 Wurst | 到达 Wurst 主菜单；单人按钮鼠标/键盘操作不能进入世界选择 |
| 原 MRPACK、加入修复后 Wurst | 恢复 FancyMenu 页面，正确显示包要求补装的 4 个 FTB 依赖；Reach 实际字节码中两个注入串联 |
| 补齐 FTB 的完整包、未加入 Wurst | Loader 解析 700 个模组，成功进入整合包主菜单；新建 `WurstCompat-Prominence` 创造模式世界，进入世界并显示出生点建筑、HUD 和欢迎页面 |
| 补齐 FTB 的完整包、加入 Wurst | 尚未完成世界内测试 |
| 功能切换、保存重进 | 尚未完成；用户按 Escape 停止了 Computer Use |

完整基线的世界进入日志位于 `baseline-complete-console.log`（22:00:29 附近）；修复前后对照日志为 `wurst-before-menu-fix.log` 和 `wurst-fixed-mrpack-console.log`。原始控制台、完整日志、变换后的字节码和测试存档保留在 `.dsh-tmp/modpacks/prominence-ii/`。

本轮 Fabric 1.20.1 发布 JAR 大小为 53,949,708 字节，SHA-256 为 `f97af64c1b4429fdaf210bc8cbe67a6f19d56a29cd5e388dd97935c0ea4e96a0`；实际发布归档检查通过。跨工程源码检查通过，覆盖 67 个工程。当前结果不能扩展为全部版本、全部整合包的运行通过。
