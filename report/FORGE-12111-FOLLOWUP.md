# Forge 1.21.11 拒载复核：原包与修订基线（2026-11-21）

**原固定包仍拒载；修订副本通过离线单人保存及新 JVM 读回。** Fastly Optimised `GX9v8ztX` 固定 Forge `61.0.2`，低于候选 `wurstpenguin` 声明的 `[61.2.0,62)`。在独立副本换成项目构建指定的 `61.2.0` 后，同一安全候选可以进档。此结果不改变原包判定，也不证明未过滤的发布 JAR 或范围内所有 Forge 版本均兼容。

| 运行时 | 实例 | 结果与证据 |
|---|---|---|
| 原固定 `61.0.2` | 原包六模组 / 同包加安全候选 | [原包登录并保存](../build/forge-121-late-qa/1.21.11/baseline-screen/result.json)；[加装日志第 17–18 行](../build/forge-121-late-qa/1.21.11/candidate-repeat/console.log)明确列出需求范围和实装版本。[候选退出码虽为 0](../build/forge-121-late-qa/1.21.11/candidate-repeat/result.json)，但未到注册表、未登录，存档未变。未重跑或覆盖此组。 |
| 修订 `61.2.0` | 六模组对照，PID `47208` | [进入同一源档的副本，保存三个维度，退出 0](../build/f12111-followup/build/forge-121-late-qa/1.21.11/baseline-screen/result.json)。 |
| 修订 `61.2.0` | 六模组 + 安全候选，PID `18832` | [普通 HackList 初始化，玩家登录，停服保存，退出 0](../build/f12111-followup/build/forge-121-late-qa/1.21.11/candidate-screen/result.json)。 |
| 修订 `61.2.0` | 同一候选，新 JVM，PID `40832` | [从首次保存的副本读回，重新登录，再次保存三个维度，退出 0](../build/f12111-followup/build/forge-121-late-qa/1.21.11/readback-screen/result.json)。三轮均无强杀、无崩溃报告。 |

**读回依据。** 对照与候选首次启动前的整档 SHA-256 相同（`5035950d…`）。候选退出后与新 JVM 启动前的指纹均为 `5c6ab7d174248b5a9603f431977bee235ab033b61b1de42611b18486e504cbc2`（只排除 `session.lock`）；`LastPlayed` 为 `1794919792629 → 1795262408661 → 1795262473424`。三轮都使用原有目录限定的备份确认探针，仅点测试副本的 `Create Backup and Load`。候选两轮另报 `mixins.baritone.json` 缺少 `minVersion` 或 `requiredFeatures`；三轮都有 FPS Reducer 的 `No GLOBAL Config data!`，均未阻塞此次保存。

**输入与隔离。** [原索引、固定版本、拒载日志和结果的快照摘要](../build/f12111-followup/original-evidence/manifest.json)可回查旧证据。修订实例没有改 MRPACK 索引、第三方 JAR 或产品门槛；Forge [安装器](../build/f12111-followup/build/forge-121-late-qa/1.21.11/installer-pin.json) SHA-1 为 `ca682f0d537c65e9a112e1e7ab337095d486298f`。首次安装在下载 Mojang 映射时遇到 TLS 错误；复用同版原版缓存后，[安装器校验并生成](../build/f12111-followup/build/forge-121-late-qa/1.21.11/install.log) `61.2.0` 客户端，未复用 `61.0.2` 的 Forge 补丁客户端。

候选与原拒载组逐字节相同，SHA-256 为 `d706b1317a23047c7b993a8fbddb40c102fc428fc41163a38671631bb4859f04`。[过滤记录](../build/f12111-followup/build/forge-121-late-qa/1.21.11/candidate-screen/archive.json)及[运行前模组清单与递归检查](../build/f12111-followup/build/forge-121-late-qa/1.21.11/candidate-screen/preflight.json)确认无 ForceOP 实现、对话框及注册引用；唯一文本命中来自旧翻译 JSON。未启动未过滤的 1.21.11 发布包。游戏使用独立目录、离线身份、回环代理、本地 hosts，禁用多人和聊天，只 Quick Play 单人测试档。包内语音模组自动连接本机回环地址；未操作语音、远程服务器、网络或攻击功能，未做网络隔离审计。

## 旁证：1.21.1 / Forge 52.1.8 的 MixinExtras 错误

**无 agent 的启动观测未复现；没有无 agent 进世界结论。** 保留 `Lef9QZ23` 的九个模组和经[递归检查](../build/f12111-followup/1.21.1-noagent/preflight.json)的同一成品（SHA-256 `ed466edd4716eaa20d09d66bc7ac359d1ea371edd85d61bff9cdaa0fcb32a574`）。从旧备份还原 `PackFresh` 到自己的实例；起始 `level.dat` 与原读回轮一致。基于原读回参数移除 `-javaagent`，清除三个 JVM 注入环境变量，保留离线限制；见[实际启动参数](../build/f12111-followup/1.21.1-noagent/launch.args)。

- **有 agent：** [原建档轮](../build/forge-121-qa/1.21.1/fresh-flat/product/first/console.log)和[原读回轮](../build/forge-121-qa/1.21.1/fresh-flat/product/backup-readback/console.log)各报告两次找不到父类（`InjectorWrapperImpl`、`HandlerTransformer`），各伴随两次事件处理器构建错误。相应栈分别经 `FlatDriver.lambda$premain$1` 和 `WorldScreen.lambda$premain$1`。
- **无 agent，PID `16196`：** [控制台](../build/f12111-followup/1.21.1-noagent/console.log)可见产品入口、2,001 条配方和 HUD 初始化。在控制台、`latest.log`、`debug.log` 中，两类名和 `An error occurred building event handler` [均为零次](../build/f12111-followup/1.21.1-noagent/result.json)。Baritone 的配置错误仍出现，可与目标错误区分。观察 160 秒后正常关闭，退出 `0`；未见玩家加入或停服保存，`level.dat` 未变。

据此，旧错误与 QA agent 的启动调用路径相关；不能据本轮排除无 agent 进档后或其他运行条件下复现。未修改产品、依赖或原 QA 报告。
