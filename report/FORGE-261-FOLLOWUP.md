# Forge 26.1.x 修订单人存档复核

**三组修订配置均有完整的离线单人证据：不加候选能建档、读回；加入对应版本的普通入口 QA 候选后，能保存，并由另一 JVM 读回同档再保存。** 这是对 2026-11-21 既有记录的只读复核，不是重新运行。通过的是下表中的修订组合，不是三个未改原包加装正式发布 JAR。

## 原包与修订的区别

| Minecraft / 原包版本 ID | 原包结果 | 修订实例实际使用的 Forge 和模组 | 对照 / 加候选 JAR 数 |
|---|---|---|---|
| 26.1 / `XiFq3Ac9` | 原包 10 个 JAR；SmartOptimizer 声明了模组但加载器构造出 0 个，未登录 | [Forge 62.0.9](../build/forge-261-followup/26.1/runtime.json) 不变，仅移除 `SmartOptimizer-Forge-26.1.2.jar` | 9 / 10 |
| 26.1.1 / `QC90dvvS` | 原包 13 个 JAR；Chunky Extended 要求 `javafml` ≥ 64，预加载失败 | [Forge 63.0.2](../build/forge-261-followup/26.1.1/runtime.json) 不变，仅移除 `chunkyextended-forge-26.1.2-4.0.0.jar`；保留 Chunky | 12 / 13 |
| 26.1.2 / `yKMJtVS7` | 原包 8 个 JAR 在 `64.0.8` 能存取档；加普通入口候选时被 Forge 依赖 `[64.1.0,65)` 拒载 | 保留 8 个原包 JAR，仅将运行时升至 [Forge 64.1.0](../build/forge-261-followup/26.1.2/runtime.json)，未放宽候选依赖 | 8 / 9 |

原包判定见[原始 QA](FORGE-26-QA.md)。三个缓存 MRPACK 的 SHA-512 均与[固定清单](../build/forge-26-qa/26.1x/pins.json)一致；逐项对照包内索引，修订模板及各计入轮次保留的第三方 JAR 均匹配原 SHA-512。[准备脚本](../build/forge-261-followup/stage.py)在独立目录生成修订实例。表中加载器不仅来自配置：候选及读回的控制台均记录了对应的 `MinecraftForge v… Initialized`。JAR 数不含 QA 探针。

## 同档对照与新进程读回

每行列出四份 `result.json`，链接文字为 PID。无候选读回与候选首轮都从**建档对照退出后的同一存档**各取一份副本；候选读回则从**候选首轮退出后的存档**取副本。父轮 `after_tree` 与相应子轮 `before_tree` 逐项相等，整档指纹仅排除 `session.lock`。

| Minecraft | 建档对照 | 无候选读回 | 候选首轮 | 候选读回（新 JVM） | `LastPlayed`：建档 → 候选保存 → 候选读回再保存 |
|---|---|---|---|---|---|
| 26.1 | [43820 · control3](../build/forge-261-followup/26.1/control3/result.json) | [32696](../build/forge-261-followup/26.1/reloadControl/result.json) | [50292](../build/forge-261-followup/26.1/candidate/result.json) | [50236](../build/forge-261-followup/26.1/readback/result.json) | `1795262656371 → 1795262754546 → 1795262833988` |
| 26.1.1 | [4620 · control2](../build/forge-261-followup/26.1.1/control2/result.json) | [49692](../build/forge-261-followup/26.1.1/reloadControl/result.json) | [17888](../build/forge-261-followup/26.1.1/candidate/result.json) | [52780](../build/forge-261-followup/26.1.1/readback/result.json) | `1795262500153 → 1795262613971 → 1795262711332` |
| 26.1.2 | [47260 · control](../build/forge-261-followup/26.1.2/control/result.json) | [51864](../build/forge-261-followup/26.1.2/reloadControl/result.json) | [48172](../build/forge-261-followup/26.1.2/candidate/result.json) | [47976](../build/forge-261-followup/26.1.2/readback/result.json) | `1795262318662 → 1795262479650 → 1795262600713` |

以上 12 轮均记录玩家登录、主世界/下界/末地保存、玩家数据和区块文件更新、返回标题、退出码 0；无强杀或崩溃报告，来源检查 `source_ok=true`。未计入的早期对照为 26.1 的 `control`、`control2` 和 26.1.1 的 `control`：三份结果均为 `passed=false`，不能凭退出 0 计为通过。26.1.2 的 `precompileOnly` 没有运行结果，也不计入。另有一项对照差异：26.1.2 的两个无候选轮次使用 `single.jar` 探针，候选两轮使用 `single2.jar`，不能把两侧所有日志差异都归因于候选。

## 候选身份与哈希

三份都是基于对应 `1.5.2-Forge` 发布 JAR 的**普通入口、目录受限的 QA 派生包**，不是桩入口、未过滤的发布成品或 26.3 的关停修复候选。按[候选制作记录](../build/forge-261-followup/ordinary.py)，`HackList` 源自修订 `1e51e8d5c6d97ce5e0827dbd9ac7c5b4886aaadc`；物理删除 `ForceOpHack.class`、`ForceOpDialog.class`，仅替换 `HackList.class` 以去除注册并限制运行环境，`META-INF/mods.toml` 保持不变。[预检](../build/forge-261-followup/inspect_safe.py)递归检查探针、模组与嵌套归档的条目名和类字节。历史启动限定本地身份、回环代理，禁多人和聊天；结论不覆盖多人、其他功能或长时运行。

| Minecraft | 发布 JAR SHA-256（实算） | QA 候选 SHA-256（历史记录） | 候选首轮 → 读回的存档树 SHA-256 |
|---|---|---|---|
| [26.1 归档差异](../build/forge-261-followup/26.1/candidate/archive.json) | `bef149f9f60439cbd60965067c2781c10fa603f42da52c6bc1fca0caea772e24` | `1532959b18667cf665cab8a7e2d0a39f60f2dbd8f51f69f56ebbc23380592907` | `6ed0926c6b5b68718549e4eb165aacdedf1080381601ab6b33215f9d3a97d276` |
| [26.1.1 归档差异](../build/forge-261-followup/26.1.1/candidate/archive.json) | `57de37f367a6ea6e42b851a9df8e49dbaacacad2821c0888ab1074288b0b818b` | `47c96962d00906092d966628b310de40a3452556be44b7fe1e3066b28e4674ac` | `b60efdf7457af3a9a20bf339604725248d6f4c7d05072b70d2e6da8ad5af4581` |
| [26.1.2 归档差异](../build/forge-261-followup/26.1.2/candidate/archive.json) | `9580fda61d6343806f96f5b046c979a86da66edf5d97df5f0c2f96fc6d223d83` | `8a4b78aaeac427af739928c31666d0a4b8502f6f1ea9b7edf16d969c479105e1` | `710a55c883d5fe6bd396295a951fe43b823f856f318848419068207d0f294ec8` |

三组 Forge 安装器和补丁客户端的实算 SHA-1 分别匹配各自 `runtime.json` 的 `installer` 和 `patch`。候选摘要在各组 `candidate/archive.json`、`candidate/preflight.json`、`readback/preflight.json` 与[汇总记录](../build/forge-261-followup/verified.json)中一致；候选和读回实例中的 `qa-ordinary.jar` 均已清理，未将历史摘要当作本次重新计算的值。

**保留告警：** 三组候选的首轮及读回均在关停时打印 `InterruptedException` 栈（见 [26.1](../build/forge-261-followup/26.1/readback/console.log)、[26.1.1](../build/forge-261-followup/26.1.1/readback/console.log)、[26.1.2](../build/forge-261-followup/26.1.2/readback/console.log)）。这些栈未阻断上述保存与读回；通过不等于日志无错，也不证明所有模组功能兼容。
