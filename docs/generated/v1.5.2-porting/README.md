# v1.5.2 全版本同步准备清单

> 由 `scripts/port/prepare_v152.py` 生成；只表示文件差异，不表示功能同步或编译通过。

基线：`fabric/`，最近源码提交 `69067e90`。

共 67 个工程：Fabric 23、Forge 21、NeoForge 23；1 个基线，66 个同步目标。

文本比较统一 CRLF/LF。目标独有文件包含版本功能和加载器适配，须逐项保留或合并。

| 工程 | 当前版本 | 批次 | JDK | 打包任务 | 基线独有 | 同路径不同 | 目标独有 |
| --- | --- | --- | ---: | --- | ---: | ---: | ---: |
| `.` | v1.5.2-Forge-1.20.1 | B1-java17 | 17 | `jarJar` | 4 | 16 | 87 |
| `fabric` | 1.5.2-Fabric-1.20.1 | B1-java17 | 17 | `remapJar` | 0 | 0 | 0 |
| `neoforge` | v1.5.2-NeoForge-1.20.1 | B1-java17 | 17 | `jarJar` | 4 | 16 | 87 |
| `versions/1.20.2` | 1.5.2-Forge-1.20.2 | B1-java17 | 17 | `jarJar` | 23 | 86 | 23 |
| `versions/1.20.3` | 1.5.2-Forge-1.20.3 | B1-java17 | 17 | `jarJar` | 11 | 87 | 23 |
| `versions/1.20.4` | 1.5.2-Forge-1.20.4 | B1-java17 | 17 | `jarJar` | 11 | 87 | 23 |
| `versions/1.20.6` | 1.5.2-Forge-1.20.6 | B2-java21-legacy | 21 | `jarJar` | 14 | 208 | 53 |
| `versions/1.21` | 1.5.2-Forge-1.21 | B2-java21-legacy | 21 | `jarJar` | 29 | 290 | 91 |
| `versions/1.21.1` | 1.5.2-Forge-1.21.1 | B2-java21-legacy | 21 | `jarJar` | 29 | 289 | 91 |
| `versions/1.21.3` | 1.5.2-Forge-1.21.3 | B2-java21-legacy | 21 | `allJar` | 32 | 350 | 121 |
| `versions/1.21.4` | 1.5.2-Forge-1.21.4 | B2-java21-legacy | 21 | `allJar` | 38 | 357 | 121 |
| `versions/1.21.5` | 1.5.2-Forge-1.21.5 | B3-java21-components | 21 | `allJar` | 32 | 404 | 122 |
| `versions/1.21.6` | 1.5.2-Forge-1.21.6 | B4-java21-gui-state | 21 | `allJar` | 43 | 503 | 140 |
| `versions/1.21.7` | 1.5.2-Forge-1.21.7 | B4-java21-gui-state | 21 | `allJar` | 43 | 503 | 142 |
| `versions/1.21.8` | 1.5.2-Forge-1.21.8 | B4-java21-gui-state | 21 | `allJar` | 43 | 505 | 141 |
| `versions/1.21.9` | 1.5.2-Forge-1.21.9 | B5-java21-event-input | 21 | `allJar` | 44 | 521 | 143 |
| `versions/1.21.10` | 1.5.2-Forge-1.21.10 | B5-java21-event-input | 21 | `allJar` | 44 | 521 | 144 |
| `versions/1.21.11` | 1.5.2-Forge-1.21.11 | B6-java21-render-setup | 21 | `allJar` | 44 | 552 | 149 |
| `versions/26.1` | 1.5.2-Forge-26.1 | B7-java25 | 25 | `allJar` | 43 | 583 | 155 |
| `versions/26.1.1` | 1.5.2-Forge-26.1.1 | B7-java25 | 25 | `allJar` | 43 | 583 | 155 |
| `versions/26.1.2` | 1.5.2-Forge-26.1.2 | B7-java25 | 25 | `allJar` | 43 | 585 | 155 |
| `versions/26.2` | 1.5.2-Forge-26.2 | B7-java25 | 25 | `allJar` | 44 | 631 | 157 |
| `versions/26.3` | 1.5.2-Forge-26.3 | B7-java25 | 25 | `allJar` | 45 | 649 | 166 |
| `fabric/versions/1.20.2` | 1.5.2-Fabric-1.20.2 | B1-java17 | 17 | `remapJar` | 0 | 81 | 2 |
| `fabric/versions/1.20.3` | 1.5.2-Fabric-1.20.3 | B1-java17 | 17 | `remapJar` | 1 | 82 | 2 |
| `fabric/versions/1.20.4` | 1.5.2-Fabric-1.20.4 | B1-java17 | 17 | `remapJar` | 1 | 82 | 2 |
| `fabric/versions/1.20.5` | 1.5.2-Fabric-1.20.5 | B2-java21-legacy | 21 | `remapJar` | 4 | 219 | 56 |
| `fabric/versions/1.20.6` | 1.5.2-Fabric-1.20.6 | B2-java21-legacy | 21 | `remapJar` | 4 | 219 | 56 |
| `fabric/versions/1.21` | 1.5.2-Fabric-1.21 | B2-java21-legacy | 21 | `remapJar` | 6 | 275 | 57 |
| `fabric/versions/1.21.1` | 1.5.2-Fabric-1.21.1 | B2-java21-legacy | 21 | `remapJar` | 6 | 275 | 97 |
| `fabric/versions/1.21.2` | 1.5.2-Fabric-1.21.2 | B2-java21-legacy | 21 | `remapJar` | 9 | 339 | 66 |
| `fabric/versions/1.21.3` | 1.5.2-Fabric-1.21.3 | B2-java21-legacy | 21 | `remapJar` | 9 | 340 | 106 |
| `fabric/versions/1.21.4` | 1.5.2-Fabric-1.21.4 | B2-java21-legacy | 21 | `remapJar` | 14 | 347 | 106 |
| `fabric/versions/1.21.5` | 1.5.2-Fabric-1.21.5 | B3-java21-components | 21 | `remapJar` | 10 | 399 | 107 |
| `fabric/versions/1.21.6` | 1.5.2-Fabric-1.21.6 | B4-java21-gui-state | 21 | `remapJar` | 34 | 501 | 118 |
| `fabric/versions/1.21.7` | 1.5.2-Fabric-1.21.7 | B4-java21-gui-state | 21 | `remapJar` | 34 | 501 | 118 |
| `fabric/versions/1.21.8` | 1.5.2-Fabric-1.21.8 | B4-java21-gui-state | 21 | `remapJar` | 34 | 503 | 119 |
| `fabric/versions/1.21.9` | 1.5.2-Fabric-1.21.9 | B5-java21-event-input | 21 | `remapJar` | 35 | 520 | 122 |
| `fabric/versions/1.21.10` | 1.5.2-Fabric-1.21.10 | B5-java21-event-input | 21 | `remapJar` | 35 | 520 | 122 |
| `fabric/versions/1.21.11` | 1.5.2-Fabric-1.21.11 | B6-java21-render-setup | 21 | `remapJar` | 35 | 551 | 128 |
| `fabric/versions/26.1` | 1.5.2-Fabric-26.1 | B7-java25 | 25 | `jar` | 34 | 583 | 133 |
| `fabric/versions/26.1.1` | 1.5.2-Fabric-26.1.1 | B7-java25 | 25 | `jar` | 34 | 583 | 133 |
| `fabric/versions/26.1.2` | 1.5.2-Fabric-26.1.2 | B7-java25 | 25 | `jar` | 34 | 584 | 134 |
| `fabric/versions/26.2` | 1.5.2-Fabric-26.2 | B7-java25 | 25 | `jar` | 35 | 631 | 138 |
| `fabric/versions/26.3` | 1.5.2-Fabric-26.3 | B7-java25 | 25 | `jar` | 36 | 650 | 142 |
| `neoforge/versions/1.20.2` | 1.5.2-NeoForge-1.20.2 | B1-java17 | 17 | `jarJar` | 23 | 88 | 21 |
| `neoforge/versions/1.20.3` | 1.5.2-NeoForge-1.20.3 | B1-java17 | 17 | `jarJar` | 11 | 89 | 21 |
| `neoforge/versions/1.20.4` | 1.5.2-NeoForge-1.20.4 | B1-java17 | 17 | `jarJar` | 11 | 89 | 21 |
| `neoforge/versions/1.20.5` | 1.5.2-NeoForge-1.20.5 | B2-java21-legacy | 21 | `jarJar` | 14 | 223 | 50 |
| `neoforge/versions/1.20.6` | 1.5.2-NeoForge-1.20.6 | B2-java21-legacy | 21 | `jarJar` | 14 | 223 | 50 |
| `neoforge/versions/1.21` | 1.5.2-NeoForge-1.21 | B2-java21-legacy | 21 | `jar` | 29 | 290 | 89 |
| `neoforge/versions/1.21.1` | 1.5.2-NeoForge-1.21.1 | B2-java21-legacy | 21 | `jar` | 29 | 290 | 89 |
| `neoforge/versions/1.21.2` | 1.5.2-NeoForge-1.21.2 | B2-java21-legacy | 21 | `jar` | 32 | 352 | 97 |
| `neoforge/versions/1.21.3` | 1.5.2-NeoForge-1.21.3 | B2-java21-legacy | 21 | `jar` | 32 | 353 | 97 |
| `neoforge/versions/1.21.4` | 1.5.2-NeoForge-1.21.4 | B2-java21-legacy | 21 | `jar` | 33 | 361 | 97 |
| `neoforge/versions/1.21.5` | 1.5.2-NeoForge-1.21.5 | B3-java21-components | 21 | `jar` | 32 | 401 | 101 |
| `neoforge/versions/1.21.6` | 1.5.2-NeoForge-1.21.6 | B4-java21-gui-state | 21 | `jar` | 43 | 503 | 136 |
| `neoforge/versions/1.21.7` | 1.5.2-NeoForge-1.21.7 | B4-java21-gui-state | 21 | `jar` | 43 | 503 | 136 |
| `neoforge/versions/1.21.8` | 1.5.2-NeoForge-1.21.8 | B4-java21-gui-state | 21 | `jar` | 43 | 505 | 137 |
| `neoforge/versions/1.21.9` | 1.5.2-NeoForge-1.21.9 | B5-java21-event-input | 21 | `jar` | 44 | 521 | 140 |
| `neoforge/versions/1.21.10` | 1.5.2-NeoForge-1.21.10 | B5-java21-event-input | 21 | `jar` | 44 | 521 | 140 |
| `neoforge/versions/1.21.11` | 1.5.2-NeoForge-1.21.11 | B6-java21-render-setup | 21 | `jar` | 44 | 552 | 146 |
| `neoforge/versions/26.1` | 1.5.2-NeoForge-26.1 | B7-java25 | 25 | `jar` | 43 | 584 | 152 |
| `neoforge/versions/26.1.1` | 1.5.2-NeoForge-26.1.1 | B7-java25 | 25 | `jar` | 43 | 584 | 152 |
| `neoforge/versions/26.1.2` | 1.5.2-NeoForge-26.1.2 | B7-java25 | 25 | `jar` | 43 | 585 | 152 |
| `neoforge/versions/26.2` | 1.5.2-NeoForge-26.2 | B7-java25 | 25 | `jar` | 44 | 631 | 154 |
| `neoforge/versions/26.3` | 1.5.2-NeoForge-26.3 | B7-java25 | 25 | `jar` | 45 | 650 | 161 |

完整分类计数、模块注册差异和能力适配状态见 [matrix.json](matrix.json)。

准备步骤不修改工程源码、版本号或 GitHub Release。验收步骤见
[同步开发计划](../../PORTING-v1.5.2.md)。
