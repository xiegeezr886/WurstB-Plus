# SeedCrackerX / OpenSeed 与 Baritone（Fabric 1.20.1）

## 来源

用户提供的 `14600hk/OpenSeed-Crack` 当前不可访问。公开参考源码已保存到 `reference/clients/OpenSeed`，来源为 [kuxiaole/OpenSeed](https://github.com/kuxiaole/OpenSeed)，固定提交 `3411dd42cbb17cf9e937f872bce7ec0cb19eeccc`；不能确认它与原地址属于同一维护者。另已拉取 [OpenSeed-Recovered-Fabric-1.21.11](https://github.com/LinKe224/OpenSeed-Recovered-Fabric-1.21.11)，未合入其二进制。

SeedCrackerX 使用官方 **2.14.4** 发布包，随客户端 JAR 嵌入并保留原始嵌套依赖。源码参考固定为 `7b556ed`（1.20）；Git 标签 `2.14.4` 当前指向后来的 1.20.2 代码，不能直接按标签构建替换。官方包 SHA-256：`cd61e1f68ca52218f61961af4ee9f97fcc62962a60acdd4525d075ac1bba42f6`。

`OpenSeedVeins` 适配公开源码的普通矿脉、散布矿脉几何算法；`SeedOrePredictor` 使用 1.20.1 原版特征顺序、放置规则和随机源。不包含 OpenSeed 认证、云端破解、上传或完整客户端。SeedCrackerX 数据库提交默认关闭。

## 使用

1. 世界分类开启 **SeedCracker**，探索并加载结构，让上游采集数据。破解时间取决于结构数据，不保证立即得到结果。
2. 在 **BaritoneMine → Ores** 选择矿物；SeedMine 共用这份列表，不必开启普通 BaritoneMine。
3. 开启 **SeedMine**，等待破解结果；也可以在 **World seed** 输入已知的有符号十进制完整种子，必须通过当前世界的种子哈希校验。
4. **Preview only** 仅显示预测，不移动。**Chunk radius** 控制已加载区块扫描半径（1–4，默认 2）。
5. **SeedCracker → Mine when cracked** 可在破解成功后自动启动 SeedMine，默认关闭。

SeedMine 优先选择客户端已确认的矿石，再尝试预测的宿主方块位置。Baritone 负责寻路和挖掘，不修改客户端世界来伪造矿石。Baritone 挖矿、行走、伐木、清理区域和 SeedMine 互斥，后启用的模块接管。关闭 SeedMine 取消自己的路径；切换世界停止挖矿，断开连接清除种子。打开界面暂停路径，背包满时停止。

## 适用范围

支持原版普通主世界和下界矿物生成，末地不支持。自定义生成、数据包、种子混淆和矿物反透视可能造成偏差。输出是候选位置：洞穴空气暴露、矿脉覆盖、已开采方块等都会影响结果，不能保证每一块矿石准确。

只扫描已加载区块，最多 8192 个候选，每 tick 最多处理 5 个矿脉并检查约 3ms 时间预算；最多渲染附近 256 个框。跳过目标及紧邻液体的目标位置，整条路线仍由 Baritone 决策。

## 验证（2026-11-16）

- 完整离线构建；新增测试覆盖种子哈希、错误/过期结果拒绝、维度会话，以及分步扫描保持原版随机数消费顺序。
- 隔离普通测试世界 `seed-mining-qa`，种子 `8675309`：通过桥接 API 注入测试结果，错误种子拒绝，正确种子接收。**这是回调联调，不是完整结构破解实测。**
- 钻石扫描 289 个候选，其中 268 个与实际钻石匹配，仅代表本次范围。
- 实测 Baritone 进入寻路/挖掘状态，关闭 SeedMine 后路径停止。发现靠近矿石会过早跳过，已删除距离完成条件，增加真实矿石优先级和模块互斥。
- 后续实测：SeedMine 实际挖掉预测到的深层钻石矿石，测试背包由 0 增至 2 颗钻石；结束后关闭模块，Baritone 路径停止。预览显示 259 个候选且路径未启动；开启 BaritoneWalk 后 SeedMine 让出 Baritone，关闭 SeedCracker 后其自动启动的 SeedMine 随之停止。
- 切换到下界仍保留同会话已校验种子，预测 39 个远古残骸候选，39 个均匹配测试区块中的方块。该样本不代表普遍准确率。
- SeedCrackerX 实际扫描测试世界的沙漠神殿，观察到破解数据从 0 增至 9/32 位（抬升数据 9/40）；单座神殿不足以恢复完整种子，本轮未完成真实破解。
- 用户 Esc 中断发生在前一轮，本轮重新构建并运行开发客户端后完成以上测试。实机关闭窗口时会保存测试世界；运行实例最终停在游戏暂停菜单。
- 用户按 Esc 停止电脑操作后未继续控制游戏。最后的挖矿修正尚未实机复测，下界远古残骸、完整结构破解和生产混淆命名环境启动仍待验证。

## 维护与许可

`SeedCrackerBridge` 接收官方 API 回调，`SeedSessionMixin` 记录登录/重生包哈希，`SeedSession` 隔离会话，`SeedOrePredictor` 和 `PlacementCursor` 重放矿物生成，`SeedMineHack` 提交 Baritone 目标。

许可证和来源记录位于 `META-INF/licenses/seedcrackerx/`、`META-INF/licenses/openseed/`。公开 OpenSeed 仓库自述为反编译重建，MIT 声明只覆盖 SeedCrackerX 部分，额外代码授权未明确；保留该说明，没有将整个镜像宣称为 MIT。
