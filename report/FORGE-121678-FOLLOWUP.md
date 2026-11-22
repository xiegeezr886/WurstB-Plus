# Forge 1.21.6、1.21.7、1.21.8 历史补测证据（2026-11-22 核对）

**三个 Fastly Optimised 修订实例均有离线候选首次保存、新 JVM 读回并再次保存的证据；三个未删模组的固定原包均不能记为加装通过。** [旧版总表](FORGE-ROLLOUT-2026-11-21.md)及[原包矩阵与两侧 result](FORGE-121-LATE-QA.md)记录的是修订前结果。以下只补充历史记录，本次未启动客户端。候选是保留普通入口、移除 ForceOP 的 QA 副本，不是未经修改的发布 JAR。

| Minecraft / 固定 Forge / 包 ID | 未删模组的原包与加装组 | 通过的修订实例（Forge 未变） | 历史 result |
|---|---|---|---|
| 1.21.6 / 56.0.9 / `n0JKzc62` | 两组均因 FPSBooster 的 `EntityRenderDispatcherMixin` 注入签名不符而崩溃。 | 原 16 件。无候选对照移除 FPSBooster、SmartOptimizer、spark 后通过；相同删三件的[候选日志](../build/forge-121-late-qa/1.21.6/followup-first/console.log)报 Accurate BlockPlacement 注入失败。最终候选[再移除该模组](../build/forge-121-late-qa/1.21.6/followup-final/mods.json)，保留 12 件。对照与最终候选不是同一清单。 | [删三件对照](../build/forge-121-late-qa/1.21.6/followup-compare/result.json) · [删四件首次](../build/forge-121-late-qa/1.21.6/followup-final/result.json) · [读回](../build/forge-121-late-qa/1.21.6/followup-finalRead/result.json) |
| 1.21.7 / 57.0.3 / `cqbH9dCU` | 两组均在 Exordium 的 `getMainBuffer()` 因 `instance` 为空而崩溃。 | 原 11 件。[只删 Exordium](../build/forge-121-late-qa/1.21.7/followup-exoonly/console.log)仍有 SmartOptimizer 加载错误。通过的对照和候选均[只移除 SmartOptimizer](../build/forge-121-late-qa/1.21.7/followup-final/mods.json)，保留 Exordium 在内的 10 件。 | [修订对照](../build/forge-121-late-qa/1.21.7/followup-control/result.json) · [候选首次](../build/forge-121-late-qa/1.21.7/followup-final/result.json) · [读回](../build/forge-121-late-qa/1.21.7/followup-finalRead/result.json) |
| 1.21.8 / 58.1.0 / `GBWDtSdh` | 两组退出 0，但未登录或保存；候选只到普通 HackList。[仅换同版平坦档的原包对照](../build/forge-121-late-qa/1.21.8/baseline-followup-screen/result.json)也未通过。 | 原 9 件。在同版平坦测试档上，[同时移出 OptiFine J6 pre16 和 pingfix 1.0.0](../build/forge-121-late-qa/followup218.py)，保留 7 件，对照和候选均通过。不能据此认定其中某一个是唯一原因。 | [修订对照](../build/forge-121-late-qa/1.21.8/baseline-revised-screen/result.json) · [候选首次](../build/forge-121-late-qa/1.21.8/candidate-revised-screen/result.json) · [读回](../build/forge-121-late-qa/1.21.8/readback-revised-screen/result.json) |

**读回核对。** 每版最终两轮均有普通 HackList、`OfflineQA joined the game`、`Stopping server` 后的 `All dimensions are saved`；对应 result 均为退出 0、无强杀、无崩溃报告。首次的 `world_after` 与读回的 `world_before` 完整 SHA-256 相等，且 `LastPlayed` 再次前进：

| 读回控制台 | 交接存档 SHA-256（前 12 位） | `LastPlayed`：首次启动前 → 首次退出 → 读回退出 |
|---|---|---|
| [1.21.6](../build/forge-121-late-qa/1.21.6/followup-finalRead/console.log) | `3c7849465082` | `1794914814044 → 1795262839935 → 1795262935384` |
| [1.21.7](../build/forge-121-late-qa/1.21.7/followup-finalRead/console.log) | `07f17cb17fc1` | `1794914814044 → 1795262844383 → 1795262939632` |
| [1.21.8](../build/forge-121-late-qa/1.21.8/readback-revised-screen/console.log) | `6b69605afe1c` | `1 → 1795262785307 → 1795262877817` |

1.21.6 和 1.21.7 各有更早的 `followup-readback` 失败轮，[1.21.6 日志](../build/forge-121-late-qa/1.21.6/followup-readback/console.log)和[1.21.7 日志](../build/forge-121-late-qa/1.21.7/followup-readback/console.log)均报 `Offline QA: listen`。表中只配对各自的 `followup-final` 与 `followup-finalRead`，未把早期首次保存拼接成通过。
