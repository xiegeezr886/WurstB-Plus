# 崩溃与安全修复记录（2026-11-12）

本记录描述最终源码状态。原始日志和诊断位置保留在 `CRASH-TRIAGE-2026-11-12.md`；`.dsh-tmp/reports/` 的证据没有删除。

## 已完成的修复

| 问题 | 修改范围与结果 |
| --- | --- |
| NanoVG / jemalloc 无效释放 | 15 个 26.x 工程去掉颜色缓存淘汰与销毁时的 `color.free()`，保持 GC 管理。实际存在 66 份 NanoVGContext，全部无错误释放；根工程没有此实现，不能把 67 个工程误作 67 份实现。 |
| NeoForge `dangling_entrypoint` | 1.20.5、1.20.6、1.21、1.21.1 的 Baritone `from(zipTree(...))` 子级排除 `META-INF/neoforge.mods.toml` 与 `BaritoneForgeModXD.class`。不在 jar 顶层排除，避免连 Wurst 元数据一起删除。 |
| 未初始化的 OpenOpal tick | 66 份 `RotationHelper.preTick()` 加 MC 空守卫；66 份 tick 注入先检查 MC 和模块注册表。禁用客户端后仍调用 preTick 清理旋转，再跳过功能更新。 |
| Baritone 配置遮蔽 | 审计全部 27 个外层副本，删除 20 个嵌套工程的副本；7 个平铺工程保留并逐项比对真实依赖。Forge 1.21/1.21.1 同时去掉自身的 Baritone 配置注册，依赖 jar 仍有配置和 MixinConnector。NeoForge 1.21.1 的平铺副本提升到 JAVA_21。 |
| Reach 常量冲突 | 11 份 legacy 注入加 `require = 0`。冲突时超过 6 格的实体搜索可能降级，不再由此阻止整合包启动；未提高优先级把故障转嫁给另一模组。 |
| 其它常量注入回归风险 | 106 处未显式声明要求的常量注入改为可选。FastBreak 原有 `require = 3` 保留，闸门以精确文件与目标方法检查此例外，保留版本漂移信号。 |
| 根工程启动期翻译 | `translateEnglish` 与 `translateMcEnglish` 补初始化守卫；HUD 注册不再同步读取译文，并移除未使用的显示名缓存。67 份翻译实现均通过检查。 |
| Fullbright / X-Ray 保存亮度 | 67 个工程注册 `OptionsMixin` 保存包装器。渲染 gamma 仍可为 16，仅在保存期间使用合法默认值，`finally` 恢复原值；替换 26.2/26.3 原来只重置、不恢复的实现。 |
| 旧 MixinExtras 转换器 | 23 个 Fabric 工程显式内嵌 MixinExtras Fabric 0.5.5，编译/处理器对齐到 0.5.5；元数据要求 Loader >=0.17.0，低于该值的开发加载器同步提升。26.x 使用新版 Loom 的 `implementation` 配置，旧版本使用 `modImplementation`。 |
| 资源包替换原生库 | 根工程 FFmpeg / Skiko 通过 `BundledNativeFiles` 从所属模块读取资源，校验编译进代码的 SHA-256，写临时文件、原子替换并复核。资源包不能替换可执行字节或信任校验值。开发者显式指定 `wurst.ffmpeg.dir` 的测试入口仍为主动指定的本地目录。 |
| 旧密钥反序列化 | common 与 66 个工程副本限制文件读取大小、对象类型、深度、引用数、流字节和数组大小；只接受 BigInteger / Number / byte[]，RSA 整数必须为正且不超过 8192 位。迁移旧文件格式保持可用。 |
| NanoVG 损坏缓存 / 架构混用 | 66 份实现按 LWJGL 版本、平台/架构 classifier、内容 SHA-256 分目录；每次核验缓存摘要；临时文件写入、原子替换、失败清理；修正 Darwin 被 `win` 子串误判，以及未知架构被当作 x64 的问题。 |
| NeoForge 1.20.1 Accessor 漏注册 | 注册 8 个功能代码仍调用的接口：ArmorItemAccessor、ChatComponentAccessor、CreativeModeInventoryScreenAccessor、EntityAccessor、FishingHookAccessor、LocalPlayerAccessor、MinecraftAccessor、MultiPlayerGameModeAccessor；另补 FogRendererAccessor 和 KeyEntryAccessor 两个 Mixin 间的调用，共 10 个。逐项核对官方 1.20.1 映射的目标成员。 |
| 同类 Accessor 遗漏回填 | 对 67 个工程扫描实际类型调用，另补 21 个注册：Forge 1.20.2/1.20.3/1.20.4/1.20.6 与 NeoForge 1.20.2–1.20.6 的 FogRendererAccessor；Fabric 1.21.6–1.21.11、26.1/26.1.1/26.1.2 的 KeyEntryAccessor；Fabric 26.1/26.1.1/26.1.2 的 ToastManagerAccessor。均与相同 MC 版本其它加载器中已注册的源码逐字相同。未把未使用的接口、同名反射辅助类或注释误作注册需求。 |
| Fabric 1.21.11 Sodium X-Ray | 新增 AbstractBlockRenderContext 的方块剔除回调和 DefaultFluidRenderer 的流体回调；保留三态事件语义及实际坐标，覆盖整次流体隐藏、选中流体穿透遮挡和新版隐藏流体启发式。查询可选类资源，不提前 Class.forName 加载 Sodium 目标。核对 Sodium 0.8.0 与 0.8.14 的实际发布 JAR 和对应源代码；未向其它 MC 版本机械复制签名。 |
| shader 缺资源 | 33 个使用自定义 fogless_lines 的工程均有 vsh/fsh；新增产物检查，从实际管线类检测 shader 需求，缺任一文件即失败。现有证据不足以认定旧日志来自旧包缺资源还是资源包兼容问题，未凭猜测修改 GLSL。 |

## 回归防线

- `scripts/audit-crash-regressions.py`：标准库实现，覆盖全部 67 个工程；检查颜色释放、翻译、初始化顺序、183 个常量注入、保存亮度注册、Baritone 配置与排除范围、MixinExtras、shader、原生缓存、密钥过滤及 DLL 信任摘要；检查 67 个工程中实际使用的 Accessor/Invoker 是否注册，并检查 1.21.11 现代 Sodium 回调和可选加载条件。
- 发布产物检查已接入 `verify-embedded-baritone.ps1`、`build-all.ps1` 和 `validate-release-jars.py`，拒绝嵌套 Baritone 的同名外层配置；检查入口/元数据不一致、shader 漏包和 Fabric 缺少内嵌 MixinExtras。
- CI 新增全工程闸门、闸门负例测试和不依赖 Minecraft 的真实 Java 行为测试。依赖登记表与自动生成状态矩阵同步更新。
- `.dsh-tmp/` 加入忽略规则，避免证据、下载的 JDK 和 Gradle 缓存进入提交。

## 已执行的验证

| 验证 | 结果及边界 |
| --- | --- |
| 全工程源码闸门 | 通过：67 工程、66 颜色实现、67 翻译、66 旋转、11 Reach、183 常量注入、67 gamma、23 Fabric、7 平铺配置、33 shader、66 原生缓存、67 旧密钥实现、67 工程的接口注册、1 个版本的现代 Sodium 适配。 |
| 闸门负例测试 | 12 项通过：嵌套注解解析、同条数不同 Mixin 名称、Java 级别、同名配置遮蔽、悬空入口、shader 漏包、缺少 MixinExtras、实际接口调用漏注册、未使用 import / 注释 / 同名辅助类排除等。 |
| Java 安全行为测试 | 12 项通过：旧密钥正常迁移/GCM 防篡改、恶意类型在 readObject 前拒绝、超大文件/数组拒绝；模块资源摘要/缓存修复/错误摘要拒绝；NanoVG 损坏修复、内容隔离、架构隔离、失败不发布及 Darwin 识别。实际生产源码经 javac --release 17 编译后执行。 |
| gamma 包装器行为测试 | 4 项通过：保存合法值、保留渲染效果、保存抛异常仍恢复、异常配置默认值仅在保存时限制范围。测试实际包装器，使用最小 Options/Wurst 边界替身；不等于 Mixin 已在游戏中织入。 |
| Sodium 回调行为测试 | 6 项通过：方块显隐/空面/未干预状态、流体整次隐藏、旧版与新版剔除方向、新版洞穴启发式、坐标保留、下次渲染及异常后状态隔离、可选类不存在时跳过且不加载目标。编译实际生产适配器，使用真实 Mixin 注解/CallbackInfo；游戏对象与事件分发边界为替身，**不等于游戏 Mixin 织入或视觉效果实测**。 |
| Sodium 依赖签名 | 下载并校验 Modrinth SHA-512：Fabric MC 1.21.11 的 Sodium 0.8.0 与 0.8.14；直接核对 class 字段/方法描述符、render 的参数顺序、旧/新流体分支，并核对对应 GitHub tag 源码与 `BOTH_EXPOSED = 3` 位标志。依赖证据在 `.dsh-tmp/sodium/`。未对全部中间版本宣称实测。 |
| NeoForge 四版本真实 Gradle CopySpec | 4 项通过。提取构建脚本实际排除规则，生成专用检查 jar 并逐项核验：Wurst 元数据恰好一份、Baritone 空入口消失、Baritone Mixin 配置保留。检查 jar 只验证打包规则，**不是可运行发布包**。 |
| 真实 processResources | NeoForge 1.21.1、Fabric 26.3 通过。 |
| 依赖版本 / 许可检查 | 全部 67 工程通过。 |
| PowerShell / Python 语法、Git 空白检查 | 修改的脚本通过语法检查；Git 使用仓库正常换行规则检查。 |

重跑独立验证：

最后一次 Java 行为测试使用 JDK 17，22 项全部通过；日志为 `.dsh-tmp/crash-unit-tests-final-jdk17.log`。

```powershell
python scripts/audit-crash-regressions.py
python -m unittest discover -s scripts/tests -p test_crash_regressions.py
python scripts/run-crash-unit-tests.py --java-home <JDK17或更高版本的目录>
pwsh -File scripts/dependency-versions.ps1 -Check
pwsh -File scripts/generate-status.ps1 -Check
```

## 尚未获得的验证结论

已下载 JDK 17/21/25 并尝试真实整工程构建，但没有生成可认定已验证的发布 JAR：

- 根 Forge 1.20.1：Minecraft/Forge 处理期间出现 `ReadOnlyFileSystemException`，最终无法取得经过 access transformer 的 Forge 编译依赖。
- NeoForge 1.21.1：`maven.neoforged.net` 的 `neoform-runtime:2.0.24` 下载连接超时；切换中央仓库优先只解决了通用依赖，无法代替 NeoForge 仓库。
- Fabric 26.3：最初因国内镜像不可连接而依赖失败；使用本地临时 init 脚本切换官方源后，Loom 生成的 Minecraft 合并/处理 jar 为空。直接把已下载的官方 client jar 加到临时编译类路径能检查大部分源码，但因没有应用 access widener，留下访问限制错误，不能视为正常构建通过。恢复本地空缓存的尝试也未解决 Loom 的处理问题。仓库的下载源未因本机排查而改写。
- Fabric 1.21.11 的 processResources 复测在 Loom 准备 intermediate mappings 阶段失败，尚未得到该版本处理后资源包的结论。
- 全仓既有 parity 检查发现 Fabric 基线多出的六个 Android/Skia 相关类未在其余 65 个版本中提供。它们不是本轮新增文件；没有用忽略项掩盖，也没有在缺少版本适配证据时盲目复制这些功能。

游戏启动、ClickGUI 动画持续渲染、反复进出世界、真实整合包冲突与资源包兼容性仍需在可构建/启动的客户端环境验证。源码修改不会修复用户手上的旧 JAR。

## 证据口径修正

`game-20261102-233743-13392.log` 的环境是 **Windows amd64 / Java 21**，不是 Android；Android Java 8 压缩包是另一份附件。Java 运行要求应区分为 1.20.1–1.20.4 使用 17、1.20.5–1.21.x 使用 21、当前 26.x 目标 25。原始诊断报告已同步更正。

`latest (3).log` 标注的是 **v1.5.0**，不能作为当前 v1.5.1 的游戏复现证据。
