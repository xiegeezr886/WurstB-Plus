# SeedCrackerX 原生加载器适配

Forge / NeoForge 1.20.1 的 MIT 上游源码位于各工程的
`src/main/java/kaptainwutax/seedcrackerX/`。来源为固定版本 SeedCrackerX 2.14.4；
Mojang 映射源码恢复及本地初始化、命令、配置路径、区块与种子哈希接入说明见工程中的
`META-INF/licenses/seedcrackerx-native/NOTICE.txt`。

`libs/` 仅保存上游内嵌的九个纯 Java seedfinding 求解库，原文件的 SHA-256 固定在
`libraries.json`，构建前校验。生产包只合并 `com/seedfinding/`，不带入 Fabric 模组元数据。
各库原始许可文件随生产包保留在上述许可证目录中。

原生工程使用 Cloth Config Forge 11.0.99 提供完整上游配置页；以嵌套模组方式打包。
实际结构采集与求解结果通过 `SeedCrackerBridge` 的会话/哈希校验后才能启动 SeedMine。
默认关闭结构渲染和自动开挖，数据库上传不默认启用。

上述九库和 2.14.4 源码仅供 Minecraft 1.20.1 的两个原生加载器使用。

`2.14.6/` 保存 1.20.2–1.20.4 六个原生工程共用的 67 个 Mojang 映射上游类。
其 `manifest.json` 固定官方 2.14.6 包及只包含 `com/seedfinding/` 的提取库 SHA-256。
使用 `gradle/seedcracker-native1204.gradle` 编译共享源码并捆绑各加载器对应的
Cloth Config 12/13；`SeedPlatform` 只提供加载器配置目录，其他逻辑均为真实上游实现。
源码包含 Log.warn 重载递归和退出世界时结构保存时机的修复。
恢复钩子的入口为 `scripts/port/restore_v152_native_seed1204.py`。

`2.14.7/` 对应原生 1.20.5/1.20.6，`2.15.0/` 对应原生 1.21/1.21.1。
各自保留对应上游的结构初始化、矩阵渲染和游戏 API；每个目录均固定官方包、
提取求解库 SHA-256 和 67 个源码文件。共享构建脚本为
`gradle/seedcracker-native1206.gradle`，恢复钩子使用 `--family 1206`。
NeoGradle 与 ModDevGradle 分别采用自己的嵌套依赖机制，Cloth Config 使用原生 14/15 版本。

`2.15.1/` 对应原生 1.21.2/1.21.3，`2.15.2/` 对应原生 1.21.4。
共享构建脚本为 `gradle/seedcracker-native1214.gradle`，恢复钩子使用 `--family 1214`。
五个工程保留实际结构渲染并打包 Cloth Config 16/17；测试和递归包审计通过，实机待验证。

`2.15.3/` 对应原生 1.21.5，`2.15.4/` 对应原生 1.21.6–1.21.8。
共享构建脚本为 `gradle/seedcracker-native1218.gradle`，恢复钩子使用 `--family 1218`。
八个工程最终各通过 256 项测试与递归包审计。使用真实 GPU 线框管线绘制上游结构标记，
NeoForge 嵌套官方 Cloth Config，Forge 配置 API 的来源与适配方式见 `native/cloth-config/README.md`。

`2.15.5/` 对应 1.21.9/1.21.10，`2.15.6/` 对应 1.21.11；
`2.16.0/` 对应 26.1/26.1.1/26.1.2，`2.16.1/` 对应 26.2，`2.16.2/` 对应 26.3。
这批 16 个工程均已接入，并各通过 256 项测试和生产包递归审计。
连同 1.21.5–1.21.8 的最终复验，证据为 `.dsh-tmp/v152-native-seed-modern-final-audit.json`。
实机结果以同步计划中的最新记录为准。
渲染保留上游的相机偏移、结构边框与颜色，通过当前帧不可变快照绘制；
26.2/26.3 使用现有 WurstBufferSource 提交实际几何，避免在帧图结束后排入下一帧。

构建、实机证据和仍待验证的能力见 `docs/PORTING-v1.5.2.md`。
