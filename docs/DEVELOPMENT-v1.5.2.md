# v1.5.2 开发基线

开发分支：`codex/v1.5.2`。目标版本：`v1.5.2`。

## 基线工程

- 工程根目录：[`fabric/`](../fabric/)，Minecraft **1.20.1**。
- 源码：`fabric/src/main/java/`；资源：`fabric/src/main/resources/`。
- Java **17**，Fabric Loader **0.17.0**，Fabric API **0.92.6+1.20.1**，Loom **1.9.2**。
- 起点：`main` 的 `6b2575b`，包含创建开发分支时保留的本地未提交改动。
- v1.5.2 的功能开发和首轮验证以此工程为准；跨版本移植在基线功能验证后进行。

在仓库根目录构建基线：

```powershell
.\fabric\gradlew.bat -p fabric build
```

导航器的 Rise 移植进度与验证说明见 [NAVIGATOR-RISE-v1.5.2.md](NAVIGATOR-RISE-v1.5.2.md)。

## 本地参考源码

2026-11-13 拉取各仓库默认分支，均为浅克隆（`--depth 1`），包含该提交的完整跟踪文件。
完整提交号、来源及检出信息保存在本地 `reference/clients/v1.5.2-sources.json`。

| 项目 | 本地路径 | 提交 | 当前上游工程 |
| --- | --- | --- | --- |
| [Rise-6.9.5](https://github.com/AnShanArchive/Rise-6.9.5) | `reference/clients/Rise-6.9.5/` | `f58ef9c` | Minecraft 1.8.9，Gradle，JDK 21+ |
| [NeoRaven](https://github.com/Matr1ze/NeoRaven) | `reference/clients/NeoRaven/` | `1a4ae73` | Minecraft 1.8.9 Forge，Java 8 |
| [moon-rebase](https://github.com/xiaozhuhou233/moon-rebase) | `reference/clients/moon-rebase/` | `55cd694` | Minecraft 1.8.9 MCP / LWJGL 3，Maven |
| [OpaiBounce](https://github.com/snmwdd/OpaiBounce) | `reference/clients/OpaiBounce/` | `6a6bc99` | LiquidBounce 主题、配套脚本及音乐服务；Svelte / TypeScript / Java |
| [OpenOpal](https://github.com/ZSZ7/OpenOpal/tree/main) | `reference/clients/OpenOpal/` | `0411550` | Minecraft 1.21.10 Fabric，Java 21，Yarn 映射 |
| [Epsilon](https://github.com/NekoyaHouse/Epsilon) | `reference/clients/Epsilon/` | `2fb4aad` | Minecraft 26.3 Fabric / NeoForge，Java 25，默认分支 `26.3.x` |
| [LeavesHack](https://github.com/MrBZBZ/LeavesHack) | `reference/clients/LeavesHack/` | `13daf96` | Minecraft 1.21.11 Fabric / Meteor Client 插件，Java 21+，默认分支 `main` |

`reference/` 由主仓库忽略，各参考项目保留独立 `.git/`。这些源码用于开发对照，不参与基线构建。
来源、许可证文件情况和可否删除见 [REPO-HYGIENE.md](REPO-HYGIENE.md)。
