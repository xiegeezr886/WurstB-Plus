# Forge 26.3 离线单人 QA · 2026-11-21

**Fastly Optimised b-26.3：原包与物理移除 ForceOP 的修复候选均通过首次保存和新进程同档读回。** 包版本 ID `YA8iKYaF`，包内清单为 Minecraft `26.3` / Forge `66.0.4`。26.2 未操作。

| 组合 | 首次保存 | 新 JVM 读回再保存 | 两轮退出码 | 缓存线程中断栈（首次 / 读回） |
|---|---|---|---|---|
| 原包，8 个 JAR | [通过](../build/forge-26-qa/baseline-final/result.json) | [通过](../build/forge-26-qa/baseline-readback/result.json) | 0 / 0 | 0 / 0 |
| 去除 ForceOP 的对照，9 个 JAR | [通过](../build/forge-26-qa/candidate-save/result.json) | [通过](../build/forge-26-qa/candidate-readback/result.json) | 0 / 0 | 2 / 2 |
| 同一对照加关停修复，9 个 JAR | [通过](../build/forge-26-qa/fixed-save/result.json) | [通过](../build/forge-26-qa/fixed-readback/result.json) | 0 / 0 | 0 / 0 |

## 存档证据

- 核对缓存 MRPACK 的 SHA-512、内外索引及 8 个客户端 JAR 的 SHA-512。每个首次实例复制同一个 26.3 原版源世界；读回实例复制对应首次实例退出后写出的世界，除 `session.lock` 外逐文件比对。源存档与模板在每轮前后哈希相同。
- 只有测试副本可确认：探针检查自身所在 QA 根目录、当前工作目录和实际游戏目录，只在匹配实验性世界标题的 `BackupConfirmScreen` 调用 **Create Backup and Load**。原包和修复候选首次均创建了 ZIP 备份，读回均未再次确认。
- 六次均以本地身份登录，走游戏自己的保存退出路径；停服后记录主世界、下界和末地的保存，返回标题后关闭窗口，无强杀、无崩溃报告。该包未打印 `All dimensions are saved`，故 JSON 的 `saved`（是否出现该字面量）为 `false`；判定基于返回标题、维度和玩家文件实际变化及新 JVM 的成功读回。
- 原包 `LastPlayed`：`1794645960802 → 1795259176062 → 1795259212530`。修复候选：`1794645960802 → 1795259304341 → 1795259359601`。每条链上第二个进程的 `before_tree` 等于第一个进程的 `after_tree`。

## 客户端修复与边界

对照两轮退出时，Baritone 的定时保存线程在 `sleep`、打包线程在 `take` 收到中断，并各自打印一条异常栈。原包没有这两个工作线程。新增 [26.3 专用 Mixin](../versions/26.3/src/main/java/net/wurstclient/mixin/BaritoneCacheInterruptMixin.java)：仅当 Baritone 执行器正在关闭时不把中断打印为错误，恢复线程中断标记，沿原处理路径退出；非关闭期间的中断和其他异常仍可见。它不改变缓存写盘逻辑。对比[修复前日志](../build/forge-26-qa/candidate-readback/console.log)和[修复后日志](../build/forge-26-qa/fixed-readback/console.log)。这修复的是误报，不是已证实的存档损坏。

使用已对照发布摘要的 Forge 1.5.2 JAR 制作测试副本。删除 `ForceOpHack.class`、`ForceOpDialog.class` 和 `HackList` 的注册字段及构造调用，不留占位类；启动前检查归档及注册类。候选带一次性目录检查，启动器禁用多人和聊天，使用本地身份、独立临时目录、本地 hosts 及无服务的回环代理。没有加入远程服务器，没有测试其他功能或长时运行。离线身份及 Realms、Forge 更新检查告警不计为保存失败；候选还会报告 Baritone 自带 Mixin 配置缺少最低版本，未改动此第三方元数据。

修复候选 SHA-256：`5bb964fea6276d442ea4a7f513662f9e33d8afd974576376f6867bfb2c641124`。仅编译并注入本次涉及的类，未声称整项目构建通过。见[归档差异](../build/forge-26-qa/fixed-archive.json)。测试候选 JAR 已清理，不交付可运行客户端。

未计分的诊断保留在同一证据目录：`baseline` 为直接关闭窗口；`baseline-save` 已返回标题但早期判据只识别特定日志字面量；`candidate` 为 QA 目录保护误拦截。后者没有登录，不归因为产品故障。

## 26.1.x 原包离线实测（2026-11-21）

三个原包均按包内 Forge 版本启动了独立的 Java 25 客户端。确认 MRPACK 和安装到游戏副本的模组均匹配 SHA-512；核对原版客户端、Forge 安装器、二进制补丁输出与资源索引的 SHA-1。26.1 使用该版安装器给出的参数，不套用后两版的 `--store` 和 `--marker`。[三个包的哈希及来源](../build/forge-26-qa/26.1x/pins.json)。只运行原包，不加装 WurstB、不连接多人服务器；没有移除导致失败的模组。

| Minecraft | 版本 ID | Forge / 原包 JAR 数 | 单人结果与证据 |
|---|---|---|---|
| 26.1 | `XiFq3Ac9` | `62.0.9` / 10 | **未通过**。`SmartOptimizer-Forge-26.1.2.jar` 的清单声明 `smartoptimizer`，加载器构造出 0 个模组，停在 FML 错误界面。未登录、无存档可读回。[控制台](../build/forge-26-qa/26.1x/26.1/base/console.log) · [崩溃报告](../build/forge-26-qa/26.1x/26.1/base/game/crash-reports/crash-2026-11-21_19.16.52-fml.txt) |
| 26.1.1 | `QC90dvvS` | `63.0.2` / 13 | **未通过**。`chunkyextended-forge-26.1.2-4.0.0.jar` 要求 `javafml` 64 或更高，锁定的 Forge 只提供 `63.0.2`。预加载失败，未登录、无存档可读回。[崩溃报告](../build/forge-26-qa/26.1x/26.1.1/base/game/crash-reports/crash-2026-11-21_19.16.46-fml.txt) |
| 26.1.2 | `yKMJtVS7` | `64.0.8` / 8 | **通过**。[首次在原包中新建单人世界并保存](../build/forge-26-qa/26.1x/26.1.2/base/result.json)；[新 JVM 读回同档再保存](../build/forge-26-qa/26.1x/26.1.2/reload/result.json)。两次均登录、保存三个维度并返回标题，退出 `0`、无强杀和崩溃报告。 |

26.1、26.1.1 的错误窗口关闭后，进程也都返回 `0`；这不是单人通过。26.1.2 的两轮 PID 分别为 `46268` 和 `52228`。读回起点逐文件复核（排除 `session.lock`），存档树 SHA-256 为 `c54d829f78251312cdc8cf39aeceea7b0bcb2a05cd553900444641c8bc4034af`，与首轮退出后相同；再次保存后 `LastPlayed` 从 `1795259830430` 增至 `1795259860236`，玩家数据和区块文件实际更新。模板及读回来源未改变。原包仍报告 `entity_cleaner:kill_commands/eggs`、`fireball` 两个函数解析失败，不据此认定全部模组功能正常。
### 26.1.2 普通入口候选：锁定 Forge 拒载

在 `yKMJtVS7` 原包的 8 个模组之外，仅加入 1 个候选，使用已通过基线的存档副本做了一次离线启动。候选基于已核对发布 SHA-256 的 `1.5.2-Forge-26.1.2`：物理删除 ForceOP 实现、对话框及注册引用，保留正式入口，`META-INF/mods.toml` 与发布包逐字节一致。[归档差异](../build/forge-26-qa/26.1x/26.1.2/ordinary/archive.json)。

**未通过，停止于依赖检查。** Forge 报告 `wurstpenguin` 要求 Forge `[64.1.0,65)`，原包锁定 `64.0.8`。[FML 崩溃报告](../build/forge-26-qa/26.1x/26.1.2/ordinary/game/crash-reports/crash-2026-11-21_19.34.58-fml.txt)；[运行记录](../build/forge-26-qa/26.1x/26.1.2/ordinary/result.json)。没有玩家登录、保存或读回，存档树哈希及 `LastPlayed` 均未变。关闭错误窗口后退出 `0`，无强杀。不降低依赖声明，不升级原包 Forge，不外推到其他版本；临时候选 JAR 已删除。
