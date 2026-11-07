# 全版本真实启动矩阵（62 / 65）

日期：2026-11-06
范围：除根目录 Forge 1.20.1（v1.6.0）外的全部 65 个 v1.5.1 工程。
判定：真实客户端进入单人世界，日志出现 `joined the game`，且出现
`[HUD] NanoVG GL3 context created`；截图保存在
`_recon/port/evidence/nanovg/`。

## 结果

| 分组 | 工程数 | PASS | 备注 |
| --- | ---: | ---: | --- |
| Fabric（1.20.2–26.3） | 22 | 22 | 含 26.1/26.2 GpuSampler 合成 |
| Forge（1.20.2–26.3） | 20 | 20 | 使用 `--no-build-cache` 生成的 refmap |
| NeoForge（根 1.20.1 + 1.20.2–26.3） | 23 | 20 | 3 个受环境限制 |
| **合计** | **65** | **62** | |

详细逐工程状态：`_recon/port/test-report.tsv`。

## 未通过的 3 个工程

| 工程 | 现象 | 原因 / 下一步 |
| --- | --- | --- |
| `neoforge/versions/1.21.3` | `NoClassDefFoundError: LoadingOverlay` | `.test/libraries` 缺少生产版 `neoforge-21.3.97-client.jar`；用 moddev 产物替代会与 `neoforge` 模块重名。需从可访问的 NeoForged Maven 补齐该 artifact。 |
| `neoforge/versions/1.21.9` | 启动到 Bootstrap 后 `ExceptionInInitializerError` | 缺少生产版 `minecraft-client-patched-21.9.16-beta.jar`；moddev 产物不是可直接运行的 patched client。 |
| `neoforge/versions/26.3` | `Closing FML Loader` 后进程静默退出 | 同一 patched client 在 Fabric/Forge 26.3 与 NeoForge 26.1/26.2 正常；只有 NeoForge 26.3 启动链退出，待查其 early display/启动参数。 |

## 复现

```powershell
# 1) 构建生产 jar（见 BUILD-MATRIX / COMPILE-MATRIX）
# 2) 生成去掉不兼容内嵌 Baritone 的测试 jar
powershell -ExecutionPolicy Bypass -File scripts\port\Make-TestJars.ps1
# 3) 跑启动矩阵（可中断、逐工程写报告）
powershell -ExecutionPolicy Bypass -File scripts\port\Test-PortMatrix.ps1 `
    -JarDir _recon\port\jars-test -SettleSeconds 25 -TimeoutSeconds 480
# 4) 只重跑非 PASS 工程
powershell -ExecutionPolicy Bypass -File scripts\port\Test-PortMatrix.ps1 `
    -JarDir _recon\port\jars-test -TimeoutSeconds 480 -SkipPassed
```

测试环境说明：

- 每个工程用 `fabric/versions/<mc>/run/saves/xray-test` 复制成
  `.test` 实例的 `WurstSmokeFresh`，避免 26.x 世界升级弹窗和树叶随机刻崩溃。
- NeoForge 1.20.5 的世界会弹 “Worlds using Experimental Settings are not
  supported”，需点一次 “I know what I'm doing!”（本矩阵为人工点击）。
- NeoForge 1.21+ 使用可见窗口启动；Forge/NeoForge 测试 jar 去掉了会崩溃的
  内嵌 Baritone，NanoVG 验证不依赖它。
