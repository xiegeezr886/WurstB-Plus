# 根工程 Forge 1.20.1 基线与移植

更新日期：2026-11-17。

当前源码已开始移植 v1.5.2，版本号为 `v1.5.2-dev-Forge-1.20.1`。
编译、146 项测试与生产打包通过；开发客户端的界面、已知种子预览、挖矿与退出清理验证通过。
生产 JAR 独立启动、完整功能回归和自动种子破解后端仍待完成。
构建方法和详细边界见 [同步计划](PORTING-v1.5.2.md)。
以下记录移植前恢复的 v1.5.1 基线，`dist/` 原始快照不变。

根目录使用项目内 `dist/VAP-v1.5.1-Forge-1.20.1` 完整源码和构建配置。
`src/` 共 1,268 个文件，逐文件 SHA-256 与该快照一致。
构建文件、wrapper 和 Baritone 输入也与基线一致。

| 项目 | 当前值 |
| --- | --- |
| Minecraft / 加载器 | 1.20.1 / Forge 47.4.10 |
| mod_version | `v1.5.1-Forge-1.20.1` |
| JDK / Gradle | 17 / 8.11 |
| 生产任务 | `jarJar`，包含 `reobfJarJar` |
| 开发目录 | 根 `src/` |

Fabric 1.20.1 为 v1.5.2；其他工程为 v1.5.1。各工程独立构建，
根工程直接编译自己的源码，不引入 `common/` sourceSet。
`native/ffmpeg` 是历史分发所需的对应源码，不参与当前根工程编译。

## 构建与验证

```powershell
./gradlew.bat compileJava jarJar --console=plain --max-workers=2
```

2026-11-17 使用 JDK 17 和隔离输出目录在线构建成功：9 个任务执行，
耗时 3 分 40 秒，24 条弃用警告，无编译错误。
日志位于本地 `.dsh-tmp/forge151-root-online.log`。

```text
build/libs/WurstB+ Plus-v1.5.1-Forge-1.20.1.jar
SHA-256: 7362fa99ea58d6326fc449603e8fdf18f50da5a7bebcde825e5e16d08f16ef46
```

已通过 ZIP 完整性、版本元数据、入口类、Mixin/refmap、NanoVG API、
内嵌 Baritone、产物崩溃检查，以及 67 工程源码崩溃审计。
共享回归测试 39 项和崩溃检查测试 15 项通过。本次没有启动游戏。

根工程以此快照作为 [v1.5.2 全版本同步](PORTING-v1.5.2.md) 的起点；
当前开发状态以本文开头及同步计划为准。
