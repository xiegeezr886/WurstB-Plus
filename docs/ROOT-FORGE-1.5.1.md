# 根工程恢复 Forge 1.20.1 v1.5.1

更新日期：2026-11-17。

## 当前开发基线

仓库根目录不再维护 v1.6.0，现使用项目内
`dist/VAP-v1.5.1-Forge-1.20.1` 的完整源码和构建配置：

| 项目 | 当前值 |
| --- | --- |
| Minecraft / 加载器 | 1.20.1 / Forge 47.4.10 |
| mod_version | `v1.5.1-Forge-1.20.1` |
| JDK / Gradle | 17 / 8.11 |
| 生产打包任务 | `jarJar`（包含 `reobfJarJar`） |
| 开发目录 | 仓库根目录的 `src/` |
| 来源快照 | `dist/VAP-v1.5.1-Forge-1.20.1`，保留作为对照 |

替换了根 `src/`、`build.gradle`、`gradle.properties`、`settings.gradle`、
Gradle wrapper 和对应 Baritone 输入。根 `src/` 共 1,268 个文件，
逐文件 SHA-256 与来源快照一致，没有把旧 v1.6.0 类混入新基线。

`fabric/` 1.20.1 继续为 v1.5.2；`neoforge/`、`versions/` 和各版本子工程没有更改。
共享 `common/`、`native/`、参考源码、发布产物矩阵、仓库许可证及 Git 历史保留。
新根工程直接编译自己的源码，不再通过旧根构建配置引入 `common/`。
历史二进制所需的 `native/ffmpeg` 对应源码继续保留，但不参与当前根工程编译。

## v1.6.0 归档与恢复

旧工程移至仓库外：

```text
C:\Users\OMEN\Documents\WurstB-Plus-v1.6.0-archive-20261117
```

归档前 Git 提交：`69067e9060cf67a0b4e27de1717015586ad055e0`。
旧 `src/` 的 1,383 个源码、资源和测试文件已移动并校验 SHA-256；
归档中的 `SOURCE-MANIFEST.json` 保存路径、大小和摘要。
同时保存旧构建文件、wrapper、Baritone、common、native、许可证和开发文档。
旧根工程 JAR 和编译输出也移入归档的 `build/`，避免后续打包混入 v1.6.0 资源。
仓库内的历史 Release 目录保留。本地账号、存档与运行配置没有迁移。

需要继续开发 v1.6.0 时，在独立目录使用归档或检出上述提交；
不要直接将归档的 `src/` 覆盖到当前 v1.5.1 工程。
此备份位于本机仓库之外，不会随本仓库 push 上传。

## 构建与验证

常规构建：使用 JDK 17，在仓库根目录执行：

```powershell
./gradlew.bat compileJava jarJar --console=plain --max-workers=2
```

本次使用 `scripts/build-env.ps1` 配置本机 JDK/zipfs，
通过 `.dsh-tmp/forge151-root.gradle` 将输出隔离到 `.dsh-tmp/forge151-root-build`。
首次离线构建因插件依赖未缓存失败；在线构建成功：9 个任务执行，耗时 3 分 40 秒。
编译有 24 条弃用警告，无编译错误。构建日志：`.dsh-tmp/forge151-root-online.log`。

已验证的产物复制到：

```text
build/libs/WurstB+ Plus-v1.5.1-Forge-1.20.1.jar
SHA-256: 7362fa99ea58d6326fc449603e8fdf18f50da5a7bebcde825e5e16d08f16ef46
```

已通过 ZIP 完整性、Forge 版本元数据、入口类、Mixin/refmap、NanoVG 类、
内嵌 Baritone 和 JAR 崩溃回归检查；产物没有旧 v1.6 Skiko/背景加载器类。
源码、构建输入与基线一致性及旧源码归档完整性检查均通过。
本次没有启动游戏，因此编译成功不代表完成实机验证。

## 脚本与开发文档

- 发布矩阵脚本重新纳入根 Forge 1.20.1，`--plan` 为 67 个工程，根工程只出现一次。
- 分支同步标签改为根 v1.5.1；README、项目索引、发布手册、变更记录和状态文档同步更新。
- 依赖登记表移除旧根专用依赖，并同步仓库现有 SeedcrackerX / Baritone 26.2 声明；67 个构建文件登记检查通过。
- 状态生成脚本把历史 v1.6 同名包明确标记为包名统计，不再视作 v1.6 功能移植证明；`-Check` 通过。
- 崩溃审计读取当前根实际使用的 Encryption 源码，移除对已归档原生加载器的强制文件引用；若重新出现没有固定摘要的原生资源，仍会报错。
- 全仓崩溃审计通过，覆盖 67 个工程的启动翻译、原生缓存、账号加密、gamma、accessor 登记及适用的 Baritone / Sodium 检查。
- 仍用于共享 `common/` 的 Encryption 测试迁至 `scripts/tests/java/EncryptionTest.java`；已移出的 BundledNativeFiles 测试不再由当前脚本执行。账号迁移、NanoVG 缓存、gamma 与 Sodium 回调测试共 39 项通过，崩溃闸门单元测试 15 项通过。

## 全仓检查的现有限制

本次保持项目内 v1.5.1 基线源码原样，没有为使全仓检查变绿而修改其他版本：

- 许可检查：Forge 26.3 缺少 `META-INF/licenses/mixinextras-forge/`，不是根工程错误。
- 分类/渲染入口对齐检查：使用 Fabric 1.20.1 v1.5.2 作为基准，其他旧版本缺少音乐、种子、灵动岛等新增组件，报告 195 项差异。
- 历史渲染闸门：仍检查原来的 66 个工程，Fabric 1.20.1 有 6 项 GL 状态约束不满足；对应 15 项单元测试中 1 项失败。根 dist 快照的恢复不宣称通过该渲染闸门。

GitHub Release 与资产未在本次操作中修改。
