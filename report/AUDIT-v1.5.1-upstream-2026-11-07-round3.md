# v1.5.1 第三轮潜藏 bug 审查

2026-11-07。新增确认 **6 类问题：2 类 P1、4 类 P2**。没有修改产品源码；前两轮的 14 类问题不重复计入。全部新项均有源码执行或真实 Minecraft 资源查找证据及正常对照，没有启动游戏或执行全量构建。

## 范围与基准

对象仍为当前 `main`（`d7d4eba9b8c7a2a662cb9da1fb00f249726a9336`）及已有工作区改动中的 66 个 v1.5.1 工程，排除根目录 Forge v1.6.0。历史标签固定为 `4686900ec86ecdec88bb4eecfd730c1d74537521`，逐项核对历史与当前范围。

官方 Wurst7 1.20.1 基准为 `d3e6eeb25bfa5c6a2497aeb8c6b3a02ca8e512de`。本轮使用的缓存 [Encryption](https://github.com/Wurst-Imperium/Wurst7/blob/d3e6eeb25bfa5c6a2497aeb8c6b3a02ca8e512de/src/main/java/net/wurstclient/altmanager/Encryption.java)、[AutoArmorHack](https://github.com/Wurst-Imperium/Wurst7/blob/d3e6eeb25bfa5c6a2497aeb8c6b3a02ca8e512de/src/main/java/net/wurstclient/hacks/AutoArmorHack.java)、[LsdHack](https://github.com/Wurst-Imperium/Wurst7/blob/d3e6eeb25bfa5c6a2497aeb8c6b3a02ca8e512de/src/main/java/net/wurstclient/hacks/LsdHack.java) 均经 Git blob 核验。未声称该提交是在线最新版本。

原生凭据库迁移、Surround、Soar HUD 编辑器和 TargetShader 都是本地扩展。本报告依据上游机制、实际 Minecraft API 与本地设置契约定位缺口，不将正常产品差异或扩展缺陷归因于官方 Wurst。

## Standards：持久化安全与本地机制集成

本轴新增 **2 类 P1、1 类 P2**。两个 P1 分别涉及删除仍需使用的密钥和误替换现有密钥，触发条件不同。

### S10 · [P1] 迁移一个账号文件时删除共享旧密钥，使其他安装目录的账号无法解密

**当前 66/66；相关构造器、写入及清理方法在历史标签、HEAD 中均相同。**

定位：[Encryption.java:372](C:/Users/ui863/Documents/trae_projects/VAP/fabric/src/main/java/net/wurstclient/altmanager/Encryption.java:372)，删除密钥文件在第 378–380 行；写完一个文件即调用清理的入口在第 222 行。

`chooseEncryptionFolder()` 选取用户级共享旧密钥目录，多个 Minecraft 安装目录的旧账号文件可共用该密钥。当系统凭据库已经有一个**不同于旧密钥的主密钥**时，构造器仍能用 `legacyKey` 读取旧文件，然后用原生主密钥写入新格式。然而 `saveEncryptedFile()` 只迁移当前路径就立即删除整组共享旧密钥文件，没有确认其他安装目录的旧账号是否也已迁移，也没有另外持久保存旧密钥。

触发例：已有原生主密钥 A，恢复／导入一组由旧密钥 B 加密的多个安装目录账号及其旧密钥文件。打开第一个安装目录时，其账号可以读取并迁移；共享 B 随即被删。另一个尚未迁移的账号文件仍为 B 密文，重启后只能取得 A，失去解密能力。没有 B 的其他备份时，仅保留该密文不能恢复账号内容。

这不要求用户手动执行保存：[AltsFile.java:77](C:/Users/ui863/Documents/trae_projects/VAP/fabric/src/main/java/net/wurstclient/altmanager/AltsFile.java:77) 在成功加载后会自动保存，从而触发清理。

验证：完整未改的 Encryption 与 JSON 辅助类；生成两份合成旧账号文件、临时旧密钥和假的原生凭据库，不访问真实账号或系统凭据。两文件迁移前都可读；迁移 A 后重建 Encryption，A 可读、B 文件未改动但无法解密。原生／旧密钥相同的对照中，两个文件在清理后仍可读。

```text
CONTROL matching native/legacy key: both installations readable after first migration
REPRO different existing native key: A migrated, shared legacy key deleted,
untouched B cannot decrypt after restart
```

建议：不要以一个账号文件写入成功作为删除用户级共享旧密钥的依据。至少在主密钥与旧密钥不同时保留旧密钥，或将其单独迁移为可恢复的凭据，再决定清理范围。上游没有此原生凭据迁移／清理机制。

证据：[执行结果](C:/Users/ui863/Documents/trae_projects/VAP/.planning/2026-11-07-wurst-v151-upstream-audit/round3/encryption-harness/result.txt)、[方法范围与上游核验](C:/Users/ui863/Documents/trae_projects/VAP/.planning/2026-11-07-wurst-v151-upstream-audit/round3/owner-scope.json)。当前整个 Encryption 类因既有修复而不同于历史版本；这里的历史判断针对有问题的构造／保存／清理方法，见 [构造器核对](C:/Users/ui863/Documents/trae_projects/VAP/.planning/2026-11-07-wurst-v151-upstream-audit/round3/constructor-scope.json)。

### S11 · [P1] 命令式凭据读取错误被当作“没有密钥”，可能覆盖原有主密钥

**当前 66/66 都包含此实现；影响 macOS／Linux 等命令式凭据路径。Windows 原生凭据实现不属于本项。相关实现与历史标签、HEAD 相同。**

定位：[CommandCredentialStore.java:30](C:/Users/ui863/Documents/trae_projects/VAP/fabric/src/main/java/net/wurstclient/altmanager/credentials/CommandCredentialStore.java:30)，平台选择在 [CredentialStores.java:15](C:/Users/ui863/Documents/trae_projects/VAP/fabric/src/main/java/net/wurstclient/altmanager/credentials/CredentialStores.java:15)。

`read()` 把所有非零子进程退出码都转换成 null，没有区分“凭据不存在”和“读取操作失败”。Encryption 收到 null 后会生成新主密钥并写入同一个 service/account。若旧密钥实际上存在、读操作临时失败而随后的写操作成功，旧主密钥就会被替换，现存 GCM 账号密文随即无法解密。

这绕开了 Encryption 原有的失败保护：真正抛出 IOException 时，没有旧密钥则构造失败，不会创建替代主密钥。WindowsCredentialStore 也明确区分 ERROR_NOT_FOUND 与其他读取错误，命令式实现却没有保留这一语义。

验证执行完整、未改的 CommandCredentialStore 和 Encryption。用本地 Java 子进程模拟凭据命令：读操作退出 73、随后写操作成功。假凭据库中的旧密钥被替换，原本可正常解密的合成账号文件变得不可读。正常读取与 IOException 失败各有对照。**73 是测试故障码，不是声称实测了 security／secret-tool 的特定故障码。**

```text
CONTROL successful credential command retains key and decrypts existing account
CONTROL IOException read failure aborts construction without replacing key
REPRO read command exits73, write succeeds: existing key replaced;
existing GCM account cannot decrypt
```

建议：按平台准确区分缺失与读取失败；对无法确定的读取状态抛出 IOException，禁止据此替换主密钥。没有运行实际系统凭据工具，实测边界是“非零读取、成功写入”的确定性代码路径。

证据：[执行结果](C:/Users/ui863/Documents/trae_projects/VAP/.planning/2026-11-07-wurst-v151-upstream-audit/round3/credential-harness/result.txt)。本项及 S10 均不同于旧报告已修复的 CFB8 乱码兜底：这里是程序主动丢弃／替换仍需要的密钥，改善解密报错无法修复根因。

### S12 · [P2] Surround 的 Rotate 关闭后仍请求静默旋转

**当前及历史标签 66/66。**

定位：[SurroundHack.java:158](C:/Users/ui863/Documents/trae_projects/VAP/fabric/src/main/java/net/wurstclient/hacks/SurroundHack.java:158)，另外两个分支在第 160、162 行。

Rotate 只被声明和注册，没有任何读取。所有 Support 分支都调用三参数 `BlockPlacer.place()`，该重载固定传入 null；[BlockPlacer.java:84](C:/Users/ui863/Documents/trae_projects/VAP/fabric/src/main/java/net/wurstclient/util/BlockPlacer.java:84) 的 null 分支却无条件调用 `faceVectorPacket()`。因此关闭 Rotate 仍会请求静默旋转。

验证执行完整 Surround 和两个 place 重载的原方法体：Rotate=true、false 都放置两次并请求两次旋转。世界几何与宿主边界使用替身，没有修改生产方法体。

建议：让放置路径消费实际 Rotate 值，提供真正不请求旋转的路径；仅传 null 无效，因为 null 当前表示直接旋转。官方基准没有同名 Surround，本项是本地设置集成缺陷。

证据：[结果与正常对照](C:/Users/ui863/Documents/trae_projects/VAP/.planning/2026-11-07-wurst-v151-upstream-audit/round3/queue/surround-harness/result.txt)。

## Spec：上游操作语义与公开功能行为

本轴新增 **3 类 P2**。

### U6 · [P2] AutoArmor 的旧换装计划会脱下后来装备的鞘翅

**当前及历史标签 66/66。**

定位：[AutoArmorHack.java:204](C:/Users/ui863/Documents/trae_projects/VAP/fabric/src/main/java/net/wurstclient/hacks/AutoArmorHack.java:204)，消费入口 [InventoryActionQueue.java:80](C:/Users/ui863/Documents/trae_projects/VAP/fabric/src/main/java/net/wurstclient/util/inventory/InventoryActionQueue.java:80)。

AutoArmor 在规划时检查当前装备和 Keep elytra，但延迟任务只复验菜单 ID 与源槽的 expectedArmor，没有复验目标装备槽。规划后、下一次队列消费前，如果手工换上鞘翅而保留原源槽，旧任务仍会先 QUICK_MOVE 当前胸部装备，再点击原源槽，违反 Keep elytra=true 的保护。

这是正常输入可达的跨 tick 窗口：队列在初始化时先注册，AutoArmor 后注册，规划出来的动作在后续更新执行。两次更新之间可以操作背包；[ChestSwapHack.java:82](C:/Users/ui863/Documents/trae_projects/VAP/fabric/src/main/java/net/wurstclient/hacks/ChestSwapHack.java:82) 的同步换鞘翅路径也可改变装备而保持原计划源槽。

完整未改 AutoArmor 与 InventoryActionQueue 的探针得到：源槽未变、装备槽从皮甲换为鞘翅后，仍执行 `[6,9]`，脱鞘翅并穿胸甲。正常升级、规划时已穿鞘翅、源槽改变则取消，三个对照均通过。交互替身模拟所涉 QUICK_MOVE 行为，没有模拟全部游戏库存机制。

上游 AutoArmor 在同次 onUpdate 中读取装备并立即点击，没有本地新增的延迟窗口。建议同时保存并复验装备槽快照与当前保护条件；失效后丢弃计划并重新规划。

AutoArmor 全类 65 份与标签／HEAD 相同，另一份只存在 toughness accessor 差异，相关 validator 和动作链相同；队列 66 份均相同。证据：[执行结果](C:/Users/ui863/Documents/trae_projects/VAP/.planning/2026-11-07-wurst-v151-upstream-audit/round3/queue/inventory-harness/result.txt)、[范围及官方 blob 核验](C:/Users/ui863/Documents/trae_projects/VAP/.planning/2026-11-07-wurst-v151-upstream-audit/round3/queue/scope.json)。

### U7 · [P2] 两个 1.20.1 构建的 TargetShader 找错资源命名空间

**当前 2/66：Fabric 1.20.1、NeoForge 1.20.1；历史标签及 HEAD 同样存在。**

定位：[target_outline.json:4](C:/Users/ui863/Documents/trae_projects/VAP/fabric/src/main/resources/assets/wurst/shaders/post/target_outline.json:4)。Pulse、Gradient、Smoke 三份 JSON 的首个 pass 也使用未限定的 `target_effect`。

真实 Minecraft 1.20.1 将该名字解析为 `minecraft:shaders/program/target_effect.json`，但程序文件只打包在 `assets/wurst/shaders/program/target_effect.json`。post JSON 本身位于 wurst 命名空间，不会使其子程序自动继承命名空间。

打开 TargetShader 并让准星指向有效实体，就能通过 [TargetShaderHack.java:54](C:/Users/ui863/Documents/trae_projects/VAP/fabric/src/main/java/net/wurstclient/hacks/TargetShaderHack.java:54) 入队。PostEffectQueue 加载缺失程序后将该 Effect 加入 failedEffects，后续跳过，导致选择的效果不显示。

验证运行真实映射 MC jar 的 EffectInstance 构造器，用动态 ResourceManager 截取首次资源查询，在 GL 初始化前停止：确认请求 minecraft 路径；本地不存在该路径，wurst 路径存在。vanilla blit 查询对照命中 MC jar 内实际存在的资源。没有伪造 EffectInstance，也没有实际渲染画面。

其余 20 个有 PostChain 实现的树已使用 `wurst:target_effect`，没有扩大计数。建议按对应加载器／版本的程序名解析规则修正资源引用，再做首帧与窗口尺寸变化的真实渲染验证。

证据：[真实 MC 查询结果](C:/Users/ui863/Documents/trae_projects/VAP/.planning/2026-11-07-wurst-v151-upstream-audit/round3/resources/real-shader-lookup-result.txt)、[当前与历史资源范围](C:/Users/ui863/Documents/trae_projects/VAP/.planning/2026-11-07-wurst-v151-upstream-audit/round3/resources/historical-resource-scope.json)。

### U8 · [P2] HUD 编辑器关闭吸附后，下一次拖动又强制开启

**当前及 HEAD 66/66；历史标签确认 6 个 Fabric 树有同样逻辑。其他 60 个历史树不据此推定。**

定位：[GuiEditHUD.java:123](C:/Users/ui863/Documents/trae_projects/VAP/fabric/src/main/java/net/wurstclient/hud2/GuiEditHUD.java:123)，右键开关在第 106 行。

工具栏明确写“右键开关吸附”，右键也确实翻转 canSnap。但左键在有效 HUD 上开始拖动时无条件设为 true，立即覆盖用户刚关闭的选项。因此先右键关闭、再左键拖动，靠近屏幕边缘或其他模块仍会吸附；只有拖动途中另按右键才能临时关闭。

原样提取 mouseClicked、mouseDragged、applySnapping 三个生产方法执行。开启对照中请求 x=4 被吸附到 0；右键后 canSnap=false；接着左键拖动又变 true，仍到 0，预期保留 x=4。屏幕和模块使用替身，没有改写方法体。

建议：左键开始拖动时保留用户选定的吸附状态。历史确认的 6 树为 Fabric 1.20.1、1.20.2、1.21.4、1.21.6、1.21.11、26.3。

证据：[结果与正常对照](C:/Users/ui863/Documents/trae_projects/VAP/.planning/2026-11-07-wurst-v151-upstream-audit/round3/resources/snap-result.txt)、[66 树扫描与方法哈希](C:/Users/ui863/Documents/trae_projects/VAP/.planning/2026-11-07-wurst-v151-upstream-audit/round3/resources/scope.json)。

## 验证与未确认项

| 检查 | 结果与限制 |
| --- | --- |
| 密钥迁移及凭据命令 | 完整未改生产 Encryption、CommandCredentialStore、JSON 类；仅使用生成的临时数据和合成凭据子进程 |
| AutoArmor、Surround | 完整生产功能类；队列完整执行，BlockPlacer 使用原方法切片；均有正常对照 |
| TargetShader | 真实 Minecraft 类的资源查找，配实际资源布局及字节码确认；未运行 GL |
| HUD 吸附 | 三个原生产方法切片，真实入口可达；未启动完整界面 |
| 版本及历史范围 | 当前 66 树扫描；tag／HEAD 比较；“源码影响范围”不等于逐个客户端启动验证 |

交付前已核验 23 个本地报告引用、16 份完整生产源码副本、5 个方法切片，以及 6 组包含正常对照的成功复现记录；源码与当前工作区一致。

没有计入确认数量：嵌套设置由 CheckboxSetting 自行序列化，并未证实丢失；同名 HUD/Hack 尚无具体错误入口；无生产调用者的 DeferredActionQueue、AsyncTextureLoader 不报为用户可触发问题；矛盾的 Offhand/AutoTotem 配置不直接视作实现错误。

PostEffectQueue 首次 resize 与窗口尺寸同步仍有后续线索。1.20.1 正常路径先被上述缺资源阻断，本轮没有把后置异常算成额外独立发现；修正资源后应专项复测。

两轴分别为 **Standards：2 类 P1、1 类 P2，主要风险是密钥丢失；Spec：3 类 P2，包含延迟换装、资源加载及编辑器设置失效**。

---

## 修复记录（2026-11-07，同一轮）

6 类问题全部修复，共改动 **471 个文件实例**（Java 463 + 资源 JSON 8）。

| 编号 | 修复内容 | 覆盖 |
| --- | --- | --- |
| S10 | `Encryption.cleanupLegacyFiles` 增加 `legacyKeyDiffers()` 守卫：原生主密钥与共享旧密钥不是同一把时不再删除旧密钥文件；两者相同时仍正常清理 | 66 |
| S11 | `CommandCredentialStore.read` 区分“确认不存在”和“读取失败”：macOS 仅 44（errSecItemNotFound）返回 null，Linux 仅 `exit 1 + stderr 为空` 返回 null，其他非零一律 `IOException`，避免 Encryption 误建新主密钥 | 66 |
| S12 | `BlockPlacer` 新增带 `rotate` 标志的 5 参重载；`Surround` 在 Rotate 关闭时走不请求旋转的路径，开启时传 `RotationQueue` | 66 |
| U6 | `AutoArmor` 的延迟任务额外保存并复验装备槽快照（`expectedEquipped`），并复验 `Keep elytra`；装备槽被手动/鞘翅/ChestSwap 改动后旧计划作废 | 66 |
| U7 | 1.20.1 的 `target_outline`/`target_pulse`/`target_gradient`/`target_smoke` post JSON 首个 pass 改为 `wurst:target_effect` | 8 个 JSON（2 个构建） |
| U8 | `GuiEditHUD` 左键开始拖动不再强制 `canSnap = true`，保留用户右键设定的吸附开关 | 66 |

**实现要点**

- S10 只比较密钥字节，不改变构造函数已有的“原生密钥优先、读失败回退旧密钥”行为；共享旧密钥被保留后，其他安装目录仍能解密。
- S11 保留 `Encryption` 原失败保护：真正抛 `IOException` 且没有旧密钥时构造失败，绝不会写入替代主密钥；有旧密钥时回退使用旧密钥并保留旧密钥文件。
- S12 的 3 参/4 参 `place` 仍委托到 `rotate=true`，其他调用点行为不变。
- U6 的校验器同时检查源槽 `expectedArmor`、装备槽 `expectedEquipped` 和实时 `Keep elytra`，任一不符即丢弃整条动作链。
- U8 只删除了强制赋值，`canSnap` 的默认值与右键切换逻辑不变。

**验证**

| 检查 | 结果 |
| --- | --- |
| 独立测试：S10 迁移（不同原生密钥 / 相同密钥两个对照） | 通过：旧密钥保留、迁移文件重启后可读；相同密钥时清理成功 |
| 独立测试：S11 `exit 44` → null、`exit 73` → `IOException` | 通过 |
| 编译：`fabric`(1.20.1)、`fabric/versions/1.20.2`、`fabric/versions/1.21.6`、`fabric/versions/1.21.10`、`neoforge`、`versions/1.20.2`、根工程 v1.6.0 | `compileJava --offline` 通过 |
| 465 个改动 Java 文件（Java 21 preview 语法解析） | 0 错误 |
| 逐树标记核对 | S10/S11/S12/U6/U8 66–67、U7 8 个 JSON |

**保留事项**

- 根工程 v1.6.0 的 `SurroundHack` 是另一套实现（无 `RotationQueue` 集成），本轮未改；其 `AutoArmor` 的规划结构也不同（变量作用域与 1.20.1 分支不一致），为避免破坏 v1.6.0，已保持原样并确认编译通过。其余 S10/S11/U7/U8 修复在根工程不适用或已随 66 树覆盖。
- 26.x 仍缺 JDK 25，只能做语法解析与 API 对照，未实际编译。
- U7 只修正了资源命名空间；报告提到的 PostEffectQueue 首次 resize 时序仍需在 1.20.1 实机开 TargetShader 复测。
- 本机没有 macOS/Linux 的 `security`/`secret-tool`，S11 用 `cmd /c exit` 模拟退出码验证代码路径，未运行真实系统凭据工具。
