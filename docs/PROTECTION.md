# 发布 JAR 混淆保护器（v2）

`tools/protector` 是独立的 Java 17 构建工具，使用 ASM 9.9.1 处理本项目的
Java 17 / 21 / 25 字节码。它不依赖 Minecraft 开发环境，不启动游戏，不修改源代码，
也不会调用根工程中向测试实例复制 JAR 的任务。

## 使用

先用对应工程正常构建**最终发布包**：Forge 的 `reobfJarJar`（包含已有重定位步骤）、
Fabric 的 `remapJar`，或对应 NeoForge 工程的最终发布任务。不要输入 `dev`、`sources`
或尚未完成游戏名称映射的中间包。

Windows，在仓库根目录运行：

```powershell
.\scripts\protect-jar.ps1 -InputJar '.\build\libs\WurstB+ Plus-v1.6.0-Forge-1.20.1.jar'
```

默认在输入文件旁输出 `原名-protected.jar`。可以显式指定：

```powershell
.\scripts\protect-jar.ps1 `
  -InputJar 'D:\release\client.jar' `
  -OutputJar 'D:\release\protected\client.jar' `
  -JavaHome 'C:\path\to\jdk-17' `
  -Config '.\tools\protector\protection.properties'
```

脚本先执行保护器测试，再处理输入。`-JavaHome` 未指定时，使用仓库公共工具链发现逻辑
（支持 `WURSTBPLUS_JAVA17`），也识别仓库 `.dsh-tmp/toolchains` 下已有的便携 JDK 17。
根 Gradle 8.11 wrapper 请用 JDK 17 或 21 运行；
这不限制输入模组的字节码版本。保护后的模组仍需要该 Minecraft 版本要求的 JDK。

Linux / macOS 或直接使用 Gradle：

```bash
./gradlew -p tools/protector test protectJar \
  -Pinput=/absolute/path/client.jar \
  -Poutput=/absolute/path/client-protected.jar \
  -Pconfig="$PWD/tools/protector/protection.properties"
```

`-Pinput`、`-Poutput`、`-Pconfig` 的相对路径基于 **tools/protector**，建议传绝对路径。
不传配置时与仓库默认配置相同。也可构建独立命令行发行目录：

```bash
./gradlew -p tools/protector test installDist
tools/protector/build/install/wurstb-protector/bin/wurstb-protector \
  /path/client.jar /path/client-protected.jar
```

Windows 的独立启动器为同目录下的 `wurstb-protector.bat`。分发该工具时需保留整个
`install/wurstb-protector` 目录，包括 `lib` 下的 ASM 依赖；模组产物不内嵌 ASM。

## 保护内容

| 处理 | 实现与限制 |
| --- | --- |
| 字符串加密 | AES-256-GCM；每包随机密钥，96 位唯一 nonce，128 位认证标签；格式域、类名与随机常量标识共同作为 AAD。处理方法中的字符串 LDC 和 StringConcatFactory 拼接模板。 |
| 超长字符串 | 加密完整 UTF-16 数据后将 Base64 密文按 60,000 字符切块；引导方法重新拼接并一次认证，不再因长度而回退明文。支持合法 class 常量的最大长度。 |
| 按需解密 | 使用 JVM ConstantDynamic 引导方法，正常执行时由 JVM 缓存结果；`intern()` 保持字符串字面量的引用比较语义。无全局明文 Map 或每帧解密循环。 |
| 私有方法重命名 | 每次构建随机名称，更新直接调用、嵌套类调用、方法句柄及 Lambda 实现引用；映射单独输出。 |
| 私有调用间接化 | 将受保护类中对已重命名私有方法的直接调用改为 invokedynamic；启用字符串加密时也加密调用目标名。用调用者 Lookup 解析原访问权限，通过 ConstantCallSite 缓存方法句柄。 |
| 数字隐藏 | int/long 使用随机掩码与 XOR；float/double 隐藏原始位模式后重建，保留负零、无穷及 NaN payload。这只是增加阅读成本，仍可被优化器还原。 |
| 调试信息清理 | 清除源文件名、源调试扩展、行号和局部变量调试信息；保留影响反射的 MethodParameters 和 API 元数据。 |
| 常量池重建 | 不复用原 class 常量池，避免加密后仍残留已替换字符串和方法名。 |
| 输出检查 | ASM 结构和基本数据流验证；方法或常量池超限直接失败，不静默输出未保护类。 |

AES 密钥分成两份保存在随机命名的运行时辅助类中；这只是提高静态提取成本，
**不是密钥保密边界**。GCM 校验能检测加密字符串载荷被改动，不能阻止攻击者替换整个解密器。
v2 将认证绑定到具体常量，因此把同一类内另一条有效密文移过来也会认证失败；
这不防止同时修改常量标识、引导代码或整个方法。`invokedynamic` 隐藏了直接调用边，
但调用者、目标类与参数类型仍可见，运行时方法句柄也可检查。未加入反调试或自定义类加载器。

## 兼容规则

默认只保护 `net.wurstclient.`，并保守处理以下接口：

- `mixin`、`mixins`、`mixinterface`、`shaded` 包、带 `@Mixin` 的类、含 native 方法的类、
  addon 包不做内容混淆。若这些类直接引用被重命名的私有方法，仅修正对应字节码引用。
- 不修改类名、字段名、公共/受保护/包可见方法名。保留反射注册表、Gson 字段名、
  Fabric/Forge/NeoForge 入口、资源路径、JNI 绑定和插件 API。
- 不重命名带注解的方法、枚举/record 类的私有方法、序列化钩子，以及含
  `$deserializeLambda$` 的类的私有方法，避免破坏 SerializedLambda 名称比对。
- 输入 class 常量及常见文本元数据中出现的标识符作为保留名称，降低反射风险。
  **动态拼接名称、外部配置及外部插件反射不能靠静态扫描穷尽**；应通过 `keepMethods`
  或 `exclude` 显式保留，并做对应功能测试。
- 模组 JSON/TOML、Mixin 配置/refmap、访问转换器、ServiceLoader 描述、许可文件、
  原生库和内嵌依赖 JAR 的内容逐字节保留。外层 ZIP 的压缩数据与时间戳会改变。
- 不处理注解里的字符串、字段 ConstantValue、未知 bootstrap 的字符串以及空串。
  字段字符串保留数量、超长字符串加密数量写入报告。
  未保护类和资源中的字符串也会保留。
- 调用间接化仅作用于本轮已重命名的私有方法；构造器、公共 API、序列化保留方法、
  外部调用与原有 Lambda bootstrap 继续使用原调用方式。被排除类只修正方法名引用，
  不植入新的间接调用。实例方法使用原接收者，静态方法保持类初始化语义。

配置 `tools/protector/protection.properties` 使用 UTF-8：

```properties
include=net.wurstclient.
exclude=net.wurstclient.addon.,net.wurstclient.some.ReflectionBridge
encryptStrings=true
renamePrivateMethods=true
maskNumbers=true
indirectPrivateCalls=true
stripDebug=true
keepMethods=lookedUpDynamically,anotherHook
```

`include` 和 `exclude` 是类内部名称的**前缀**，可用点或斜杠分隔，不是通配符。
显式 `exclude` 覆盖默认的 addon 排除项，应像示例一样保留它；内置 Mixin/native/shaded
规则始终生效。未知配置键、错误布尔值或空 include 会报错，避免拼写错误静默降低保护。
`maskIntegers` 作为 v1 配置的兼容别名保留，v2 中控制所有数字类型；同时设置两个名称
且取值冲突会报错。`indirectPrivateCalls=false` 可单独关闭新调用层。
`renamePrivateMethods=false` 时没有合格的重命名目标，调用间接化也不会生效。

## 产物与发布

成功后生成三个文件：

- `client-protected.jar`：可供启动测试的候选发布包。
- `client-protected.jar.mapping.tsv`：私有方法映射，留存于内部构建归档。
- `client-protected.jar.report.txt`：输入/输出 SHA-256、处理计数、有效配置及排除类清单。
  `calls.indirect` 是改写调用点数量，`longs/floats/doubles.masked` 是各数字类型数量；
  `strings.encrypted` 包括间接调用目标名，按类去重计数，不能直接与 v1 的字符串计数比较。

原包不变。JAR 和两个 sidecar 均拒绝覆盖，重复运行需使用新输出路径。已保护包、
带签名/签名摘要的外层包、显式外层 JPMS 模块、项目类的 multi-release 变体、
重复/不安全 ZIP 路径及超限输入会被拒绝。每个文件条目都核对实际长度和 CRC，
损坏的输入不会被重新打包成貌似正常的输出。当前限制为单条目 128 MiB、解压总量
512 MiB、100,000 条目（含目录）；在读取前检查声明长度，并在读取时限制实际长度。
输出 JAR 在全部变换、检查和报告生成完成后才发布。v1/v2 标记均拒绝重复保护。

正确顺序是：**编译 → 游戏 remap/reobf → 库重定位 → 本保护器 → 启动回归 → 签名 → 发布**。
不要再对保护后的类改名：字符串认证绑定了类名。SHA-256 报告只用于审计和比对，
不是受信任签名；如需验证发布者身份，最后使用独立可信私钥做 JAR 签名并验证证书。

首次发布应针对各目标加载器启动游戏，检查 Mixin 加载、设置保存/读取、模块切换、
插件调用以及原生渲染/视频功能。保护器的字节码测试不等同于全部 67 个工程的游戏测试。

## 测试与安全边界

```powershell
.\gradlew.bat -p tools/protector test
```

测试现场编译 fixture，保护后用独立 JVM `-Xverify:all` 执行，覆盖字符串引用比较、
拼接、中文/emoji/NUL/孤立代理项、整数边界、私有重载、Lambda、嵌套类、反射、注解、
record/enum、可序列化 Lambda、密文篡改、资源保留、输出拒绝覆盖及方法长度超限。
v2 新增最长字符串分块、同类密文交换、私有静态/实例/接口调用、同步锁、递归、
异常传播、空接收者、并发首次链接、构造前静态调用、类初始化、数字原始位模式、
Unicode 反射方法名、ZIP CRC 损坏和声明长度超限测试。
CI 使用 Java 17/21/25 分别编译和执行相应版本 fixture。

2026-11-13 的 v2 本地验证：Java 17、21、25 各 13 项测试全部通过；测试 fixture
使用对应 JDK 版本现场编译并以独立 JVM 的 `-Xverify:all` 执行。
另外对本地已有 v1.5.1 发布包进行了最终处理和资源哈希比对：

| 样本 | 处理类数 | 私有方法重命名 | 字符串加密（含调用目标） | 间接调用点 | 非 class 资源保持一致 |
| --- | ---: | ---: | ---: | ---: | ---: |
| Forge 1.20.1 | 1,494 | 2,834 | 3,548 | 3,269 | 252 |
| NeoForge 1.21.1 | 1,435 | 2,775 | 3,475 | 3,196 | 228 |
| Fabric 26.3 | 1,454 | 2,795 | 3,490 | 3,230 | 229 |

Forge 样本额外隐藏 276 个 long、2,891 个 float、5,077 个 double 常量。
本地样本、映射及报告位于 `tools/protector/build/verified-v2/`，各 JDK 的测试记录位于
`tools/protector/build/verification-v2/`，均属于忽略提交的构建输出。v1 验证记录保留在
原 `verified/` 和 `verification/` 目录；旧包不要作为 v2 的输入，应从未保护发布包重新生成。
这些结果不代表本次重编了当前模组源码，也不代表已完成游戏内启动或性能测试。

该工具提高静态阅读和批量分析成本，**不保证“高安全性”或“难以破解”达到某种可量化等级**。
拥有 JAR 和运行控制权的人仍可获取密钥、转储明文、插桩或修改分支；类/公共方法名和
整体控制流仍可见。混淆无法替代服务端授权，长期密钥不应放在客户端。
本项目公开源码及其许可证义务也不会因混淆改变，发布时仍需履行适用许可要求。
