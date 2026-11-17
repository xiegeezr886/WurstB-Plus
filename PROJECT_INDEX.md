# 项目索引

> **本文档不再维护任何手写的数字。**
>
> 架构审计发现：同一批状态数字在 9 份文档里存在 **7 个互斥的工程数**、**6 个不同的测试数**，
> 根因是所有数字都是手写的、且没有东西去重新生成它们。本文件原先声称"仓库内共有 15 个独立构建工程"
> 和"另有 46 个新版本工程"，而实际是 **67 个工程**——它整份文件停留在 15 工程时代。
>
> 现在权威数字只有一个来源：自动生成的 **[docs/STATUS.md](docs/STATUS.md)**。

## 数字去哪儿看

| 你需要知道 | 去看 |
| --- | --- |
| 工程总数、MC 版本数、加载器、源码/测试文件数 | [docs/STATUS.md](docs/STATUS.md) |
| 逐工程的工具链（Java / Gradle / 加载器版本 / mod_version） | [docs/STATUS.md](docs/STATUS.md) |
| 历史 v1.6 包名是否仍存在 | [docs/STATUS.md](docs/STATUS.md) 的「历史 v1.6 包名检查」 |
| 67 个工程为什么这么组织、怎么加新 MC 版本 | [docs/PROJECT-STRUCTURE.md](docs/PROJECT-STRUCTURE.md) |
| 内嵌依赖与随包资源的第三方许可 | [THIRD-PARTY-NOTICES.md](THIRD-PARTY-NOTICES.md) |
| 功能清单（Hack / 命令 / Other Feature）与架构导航 | 本文档以下章节 |

`docs/STATUS.md` 由 `scripts/generate-status.ps1` 生成：

```powershell
pwsh -File scripts/generate-status.ps1          # 重新生成
pwsh -File scripts/generate-status.ps1 -Check   # CI：状态漂移则退出码 1
```

---

## 源码结构导航（根工程 Forge 1.20.1 v1.5.1）

2026-11-17 根工程改用项目内 `dist/VAP-v1.5.1-Forge-1.20.1`。原 v1.6.0 的视频背景、Skia、Compose、Twilight 等导航不再适用于当前根工程，历史资料保留在文档与独立备份中。完整迁移和恢复说明见 [ROOT-FORGE-1.5.1.md](docs/ROOT-FORGE-1.5.1.md)。

| 路径 | 用途 |
| --- | --- |
| `src/main/java/net/wurstclient/WurstClient.java` | 客户端生命周期 |
| `src/main/java/net/wurstclient/WurstForgeInitializer.java` | Forge 入口 |
| `src/main/java/net/wurstclient/hack/HackList.java` | 模块注册 |
| `src/main/java/net/wurstclient/clickgui2` | 其他 GUI |
| `src/main/java/net/wurstclient/hud2` | HUD |
| `src/main/java/net/wurstclient/settings/SettingsFile.java` | 设置持久化 |
| `src/main/resources/META-INF/mods.toml` | Forge 元数据 |
| `src/main/resources/wurst.mixins.json` | Mixin 配置 |
| `src/main/resources/META-INF/accesstransformer.cfg` | 访问转换 |
| `src/main/resources/assets/wurst/translations` | 语言资源 |
| `src/main/resources/META-INF/licenses` | 第三方归属 |

`native/ffmpeg/` 保留为历史 v1.6 发布包的对应源码，不参与当前 v1.5.1 根工程构建。
