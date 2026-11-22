# Forge 1.20.x：单人菜单建档与读回复核（2026-11-22）

**三个无候选基线均已在新 JVM 中读回同一世界并重新登录。** 以下先列 11 月 22 日的补跑，再保留 11 月 21 日的首次运行记录。只有 Pixelmon 保留了原包全部模组；SuperSMP4 和 Helios 是修订副本。三组均不证明加装客户端兼容。

## 11 月 22 日实机读回

每组复制 `menu3` 首次退出后的游戏目录到独立 `menu4`，使用原 `menu.jar` 的 `read` 阶段从世界列表选择 `New World`。复核第三方模组清单、来源描述与首次一致，读回前所有存档文件的 SHA-256 与原首轮输出逐项一致（排除 `session.lock`）。没有使用旧版世界，也没有改写测试存档。

| 包 | 首次 → 新 JVM PID | 交接文件数 / 两轮退出后的 `level.dat` SHA-256 前缀 | 新 JVM 直接证据 |
| --- | --- | --- | --- |
| Pixelmon，13 个原模组 | 38320 → 39684 | 53 / `4878a16c1d7e` → `0b0f64afdca2` | [读回日志](../build/forge-120-followup/lmwiXuDD-menu4/read/console.log)：已登录；停在 `ChooseStarterScreen`，请求暂停未能进入菜单。正常关闭测试窗口，停服后五个维度保存，[退出 0](../build/forge-120-followup/lmwiXuDD-menu4/read/exit.json)。不计菜单退出。 |
| SuperSMP4，16 个保留模组 | 30368 → 7028 | 28 / `f4ecfd949ba5` → `4bb69fd9fd00` | [读回日志](../build/forge-120-followup/mFsVWq2u-menu4/read/console.log)：已登录，从暂停菜单保存并返回标题，三个维度保存，从标题退出，[退出 0](../build/forge-120-followup/mFsVWq2u-menu4/read/exit.json)。 |
| Helios，4 个保留模组 | 42664 → 11756 | 21 / `c54e6dce3af6` → `8cc708a85c51` | [读回日志](../build/forge-120-followup/eARONeXV-menu4/read/console.log)：已登录，从暂停菜单保存并返回标题，三个维度保存，从标题退出，[退出 0](../build/forge-120-followup/eARONeXV-menu4/read/exit.json)。 |

## 固定输入

将各轮 `first/source.json` 的 `installed`（文件名及 SHA-256）与[历史模板清单](../build/forge-120-qa/three-results.json)逐项比较。下表链接含发布归档 SHA-512、依赖和实际模组清单；除列出的隔离项，`menu3` 没有新增或换版的模组。菜单驱动是 JVM agent，不计入模组数。

| 发布包 | Modrinth 版本 ID / 文件 ID | Minecraft / Forge | 原包 → `menu3` 顶层 JAR |
|---|---|---|---|
| [The Pixelmon Modpack 9.2.10](../build/forge-120-followup/lmwiXuDD-menu3/first/source.json) | `lmwiXuDD` / `wOpDpH2B` | 1.20.2 / 48.1.0 | 13 → 13，原包模组逐项相同 |
| [SuperSMP4 1.7.7](../build/forge-120-followup/mFsVWq2u-menu3/first/source.json) | `mFsVWq2u` / `zDBIG8QT` | 1.20.3 / 49.0.2 | 18 → 16，隔离 `corpsecurioscompat-1.18.x-1.20.x-Forge-2.2.2.jar` 和 `chat_heads-0.13.13-forge-1.20.4.jar` |
| [Helios 0.02](../build/forge-120-followup/eARONeXV-menu3/first/source.json) | `eARONeXV` / `SSykwBx0` | 1.20.6 / 50.2.1 | 5 → 4，隔离 `ClearLagPlus-1.0.2.jar` |

## 三个新世界实际走到哪一步

三轮 `menu3` 的 `world-before.json` 均为 `{}`。控制台均记录了从标题页点单人、点 `Create New World`、内置服务器启动及 `ForgeQA joined the game`。同轮的 `exit.json` 均为 `0`；区分退出路径的证据是按钮和服务端日志，不是退出码。

| 实例 / PID | 进入世界后的直接证据（行号指对应控制台） | 首轮退出后的存档清单（不计 `session.lock`） |
|---|---|---|
| [Pixelmon 原包，38320](../build/forge-120-followup/lmwiXuDD-menu3/first/console.log) | 859–911 建档，1144–1149 登录。1292–1298 出现 `ChooseStarterScreen`，仅记录请求暂停，未见按下「保存并退出」或返回标题。随后客户端 `Stopping!`，1299–1315 记录玩家离开、停服及全部五个维度保存。**证实关闭时保存，未证实菜单退出。** | [53 项](../build/forge-120-followup/lmwiXuDD-menu3/first/world-after.json)，`New World/level.dat` SHA-256 前缀 `4878a16c1d7e` |
| [SuperSMP4 修订副本，30368](../build/forge-120-followup/mFsVWq2u-menu3/first/console.log) | 531–540 建档，608–613 登录，629 按 `Save and Quit to Title`，636–642 三维保存，658–659 从标题页按 `Quit Game` 并退出。 | [28 项](../build/forge-120-followup/mFsVWq2u-menu3/first/world-after.json)，`level.dat` 前缀 `f4ecfd949ba5` |
| [Helios 修订副本，42664](../build/forge-120-followup/eARONeXV-menu3/first/console.log) | 300–302、557–560 建档，583–587 登录，604 按 `Save and Quit to Title`，607–617 停服并保存三维，624–625 从标题页按 `Quit Game` 并退出。 | [21 项](../build/forge-120-followup/eARONeXV-menu3/first/world-after.json)，`level.dat` 前缀 `c54e6dce3af6` |

另七次尝试的 `world-before.json` 和 `world-after.json` 都为空，没有可供复开的世界：

- **Pixelmon：** [无后缀实例](../build/forge-120-followup/lmwiXuDD/first/console.log)在 `glfw.dll+0xff5b` 原生崩溃，退出 `3221226505`；[menu](../build/forge-120-followup/lmwiXuDD-menu/first/console.log) 到 Forge 加载错误页，退出 `0`；[menu2](../build/forge-120-followup/lmwiXuDD-menu2/first/console.log) 有标题页注册记录，随后关闭，退出 `0`，未见建档或登录。
- **SuperSMP4：** [menu](../build/forge-120-followup/mFsVWq2u-menu/game/crash-reports/crash-2026-11-21_20.03.47-client.txt) 只隔离兼容桥（17 JAR），在 `chat_heads` 的 `ChatComponentMixin` 注入签名不匹配处崩溃，退出 `4294967295`；[menu2](../build/forge-120-followup/mFsVWq2u-menu2/first/console.log) 已隔离两项，客户端关闭，退出 `0`，未见世界启动。
- **Helios：** [无后缀实例](../build/forge-120-followup/eARONeXV/first/console.log)在 `glfw.dll+0x101ab` 原生崩溃，退出 `3221226505`；[menu2 第 305–340 行](../build/forge-120-followup/eARONeXV-menu2/first/console.log)保留五个模组，点单人后 ClearLagPlus 注册命令触发 `NoSuchMethodError`，停在建档准备页，退出 `0`。

## 截至 11 月 21 日的读回证据缺口（补跑前）

**三个 `menu3` 新世界都缺。** [运行脚本](../build/forge-120-followup/menu.py)虽有 `read` 分支，但 10 个实例都只有 `first` 轮记录。没有第二个 JVM 的 PID、启动前与首轮停服后相同的整档清单，也没有重新载入该档、本地玩家再次加入、再次保存和退出的日志。`menu2`、`menu3` 是从空存档开始的独立尝试，不是上一轮的读回。

同组[历史报告](FORGE-120-QA.md)采用 Quick Play 和 1.20.1 旧世界副本，与本次同版本菜单新建世界不同：

- **Pixelmon 原包**确有[历史第二进程日志](../build/forge-120-qa/lmwiXuDD/read-baseline/console.log)：输入散列吻合，游戏读出首轮标记 `…4082F88A`，但重新登录前因 `LootModifierManager` 未就绪而异常停服。退出 `0` 不改变结论：旧档标记确实读到了，完整读回验收未过，更不是本次 `New World` 的读回。
- **SuperSMP4 原包**在[历史首轮](../build/forge-120-qa/mFsVWq2u/baseline/console.log)因兼容桥缺 `curios`、`corpse` 拒载，未进档。[纯 Forge 49.0.2 对照](../build/forge-120-qa/loader-only-1203/read-baseline/console.log)读到旧标记并登录后 tick 崩溃；它没有安装 SuperSMP4，不能代替原包或修订副本的读回。
- **Helios 原包**在[历史首轮](../build/forge-120-qa/eARONeXV/baseline/console.log)写标记并登录后，同样因 `LootModifierManager` 未就绪而异常停服，虽有保存日志但非零退出，没有第二轮。该结果和本次四模组副本的首次正常保存应分别保留。
