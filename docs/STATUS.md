# 项目状态（自动生成）

> **本文件由 `scripts/generate-status.ps1` 自动生成，请勿手工编辑。**
> 修改后运行 `pwsh -File scripts/generate-status.ps1` 重新生成并提交。
> CI 可用 `-Check` 模式阻止状态漂移。

架构审计发现文档里同时存在 7 个互斥的工程数、6 个不同的测试数——根因是所有数字都是手写的。
本文件把状态数字变成生成物，从机制上消除漂移。

## 汇总

| 指标 | 值 |
| --- | --- |
| 独立 Gradle 工程 | **67** |
| Minecraft 版本 | **23** |
| 加载器 | **Fabric / Forge / NeoForge** |
| src/main/java 文件 | **51,676** |
| 测试类 / 测试方法（全部工程合计） | **1,876 / 5,710** |
| 根工程源码 / 测试类 / 测试方法 | **1,061 / 193 / 1,255** |
| 含 v1.6 子系统的工程 | **1 / 67** |
| 含 gradle-wrapper.jar 的工程 | **67 / 67** |
| 含 LICENSE.txt 的工程 | **67 / 67** |
| mod_version 不同取值数 | **63** |

## 工程清单

| 工程 | 加载器 | MC | 加载器版本 | Java | Gradle | mod_version | 组 | 源码 | 测试类 | 测试方法 | v1.6 包 | wrapper | LICENSE |
| --- | --- | --- | --- | ---: | ---: | --- | --- | ---: | ---: | ---: | ---: | :---: | :---: |
| fabric | Fabric | 1.20.1 |  |  | 8.11 | 1.5.0-Fabric-1.20.1 | net.wurstpenguin | 766 | 0 | 0 | 0/13 | yes | yes |
| fabric\versions\1.20.2 | Fabric | 1.20.2 |  |  | 8.11 | 1.5.0-Fabric-1.20.2 | net.wurstpenguin | 767 | 0 | 0 | 0/13 | yes | yes |
| fabric\versions\1.20.3 | Fabric | 1.20.3 |  |  | 8.11 | 1.5.0-Fabric-1.20.3 | net.wurstpenguin | 767 | 0 | 0 | 0/13 | yes | yes |
| fabric\versions\1.20.4 | Fabric | 1.20.4 |  |  | 8.11 | 1.5.0-Fabric-1.20.4 | net.wurstpenguin | 767 | 0 | 0 | 0/13 | yes | yes |
| fabric\versions\1.20.5 | Fabric | 1.20.5 |  |  | 8.11 | 1.5.0-Fabric-1.20.5 | net.wurstpenguin | 793 | 0 | 0 | 0/13 | yes | yes |
| fabric\versions\1.20.6 | Fabric | 1.20.6 |  |  | 8.11 | 1.5.0-Fabric-1.20.6 | net.wurstpenguin | 793 | 0 | 0 | 0/13 | yes | yes |
| fabric\versions\1.21 | Fabric | 1.21 |  |  | 8.11 | 1.5.0-Fabric-1.21 | net.wurstpenguin | 793 | 0 | 0 | 0/13 | yes | yes |
| fabric\versions\1.21.1 | Fabric | 1.21.1 |  |  | 8.11 | 1.5.0-Fabric-1.21.1 | net.wurstpenguin | 793 | 51 | 135 | 0/13 | yes | yes |
| fabric\versions\1.21.10 | Fabric | 1.21.10 |  |  | 9.6.0 | 1.5.0-Fabric-1.21.10 | net.wurstpenguin | 751 | 51 | 135 | 0/13 | yes | yes |
| fabric\versions\1.21.11 | Fabric | 1.21.11 |  |  | 9.6.0 | 1.5.0-Fabric-1.21.11 | net.wurstpenguin | 751 | 51 | 135 | 0/13 | yes | yes |
| fabric\versions\1.21.2 | Fabric | 1.21.2 |  |  | 8.11 | 1.5.0-Fabric-1.21.2 | net.wurstpenguin | 792 | 0 | 0 | 0/13 | yes | yes |
| fabric\versions\1.21.3 | Fabric | 1.21.3 |  |  | 9.6.0 | 1.5.0-Fabric-1.21.3 | net.wurstpenguin | 792 | 51 | 135 | 0/13 | yes | yes |
| fabric\versions\1.21.4 | Fabric | 1.21.4 |  |  | 9.6.0 | 1.5.0-Fabric-1.21.4 | net.wurstpenguin | 792 | 51 | 135 | 0/13 | yes | yes |
| fabric\versions\1.21.5 | Fabric | 1.21.5 |  |  | 9.6.0 | 1.5.0-Fabric-1.21.5 | net.wurstpenguin | 789 | 51 | 135 | 0/13 | yes | yes |
| fabric\versions\1.21.6 | Fabric | 1.21.6 |  |  | 9.6.0 | 1.5.0-Fabric-1.21.6 | net.wurstpenguin | 750 | 51 | 135 | 0/13 | yes | yes |
| fabric\versions\1.21.7 | Fabric | 1.21.7 |  |  | 9.6.0 | 1.5.0-Fabric-1.21.7 | net.wurstpenguin | 750 | 51 | 135 | 0/13 | yes | yes |
| fabric\versions\1.21.8 | Fabric | 1.21.8 |  |  | 9.6.0 | 1.5.0-Fabric-1.21.8 | net.wurstpenguin | 751 | 51 | 135 | 0/13 | yes | yes |
| fabric\versions\1.21.9 | Fabric | 1.21.9 |  |  | 9.6.0 | 1.5.0-Fabric-1.21.9 | net.wurstpenguin | 751 | 51 | 135 | 0/13 | yes | yes |
| fabric\versions\26.1 | Fabric | 26.1 |  |  | 9.6.0 | 1.5.0-Fabric-26.1 | net.wurstpenguin | 791 | 0 | 0 | 0/13 | yes | yes |
| fabric\versions\26.1.1 | Fabric | 26.1.1 |  |  | 9.6.0 | 1.5.0-Fabric-26.1.1 | net.wurstpenguin | 791 | 0 | 0 | 0/13 | yes | yes |
| fabric\versions\26.1.2 | Fabric | 26.1.2 |  |  | 9.6.0 | 1.5.0-Fabric-26.1.2 | net.wurstpenguin | 792 | 0 | 0 | 0/13 | yes | yes |
| fabric\versions\26.2 | Fabric | 26.2 |  |  | 9.6.0 | 1.5.0-Fabric-26.2 | net.wurstpenguin | 793 | 0 | 0 | 0/13 | yes | yes |
| fabric\versions\26.3 | Fabric | 26.3 |  |  | 9.6.0 | 1.5.0-Fabric-26.3 | net.wurstpenguin | 795 | 0 | 0 | 0/13 | yes | yes |
| . | Forge | 1.20.1 | 47.4.10 | 17 | 8.11 | v1.6.0-Forge-1.20.1 | net.wurstpenguin | 1061 | 193 | 1255 | 13/13 | yes | yes |
| versions\1.20.2 | Forge | 1.20.2 | 48.1.0 | 17 | 8.14.4 | v1.5.0-Forge-1.20.2 | net.wurstclient | 752 | 0 | 0 | 0/13 | yes | yes |
| versions\1.20.3 | Forge | 1.20.3 | 49.0.2 | 17 | 8.11 | v1.5.0-Forge-1.20.3 | net.wurstclient | 767 | 0 | 0 | 0/13 | yes | yes |
| versions\1.20.4 | Forge | 1.20.4 | 49.2.0 | 17 | 8.14.4 | v1.5.0-Forge-1.20.4 | net.wurstclient | 767 | 0 | 0 | 0/13 | yes | yes |
| versions\1.20.6 | Forge | 1.20.6 | 50.2.0 | 21 | 8.14.4 | v1.5.0-Forge-1.20.6 | net.wurstclient | 766 | 0 | 0 | 0/13 | yes | yes |
| versions\1.21 | Forge | 1.21 | 51.0.33 | 21 | 8.11 | v1.5.0-Forge-1.21 | net.wurstpenguin | 741 | 51 | 135 | 0/13 | yes | yes |
| versions\1.21.1 | Forge | 1.21.1 | 52.1.16 | 21 | 8.11 | v1.5.0-Forge-1.21.1 | net.wurstpenguin | 741 | 51 | 135 | 0/13 | yes | yes |
| versions\1.21.10 | Forge | 1.21.10 | 60.1.0 | 21 | 9.4.1 | v1.5.0-Forge-1.21.10 | net.wurstpenguin | 752 | 51 | 135 | 0/13 | yes | yes |
| versions\1.21.11 | Forge | 1.21.11 | 61.2.0 | 21 | 9.4.1 | v1.5.0-Forge-1.21.11 | net.wurstpenguin | 751 | 51 | 135 | 0/13 | yes | yes |
| versions\1.21.3 | Forge | 1.21.3 | 53.1.0 | 21 | 9.4.1 | v1.5.0-Forge-1.21.3 | net.wurstpenguin | 754 | 51 | 135 | 0/13 | yes | yes |
| versions\1.21.4 | Forge | 1.21.4 | 54.1.14 | 21 | 9.4.1 | v1.5.0-Forge-1.21.4 | net.wurstpenguin | 754 | 51 | 135 | 0/13 | yes | yes |
| versions\1.21.5 | Forge | 1.21.5 | 55.1.0 | 21 | 9.4.1 | v1.5.0-Forge-1.21.5 | net.wurstpenguin | 758 | 51 | 135 | 0/13 | yes | yes |
| versions\1.21.6 | Forge | 1.21.6 | 56.0.9 | 21 | 9.4.1 | v1.5.0-Forge-1.21.6 | net.wurstpenguin | 750 | 51 | 135 | 0/13 | yes | yes |
| versions\1.21.7 | Forge | 1.21.7 | 57.0.3 | 21 | 9.4.1 | v1.5.0-Forge-1.21.7 | net.wurstpenguin | 752 | 51 | 135 | 0/13 | yes | yes |
| versions\1.21.8 | Forge | 1.21.8 | 58.1.0 | 21 | 9.4.1 | v1.5.0-Forge-1.21.8 | net.wurstpenguin | 752 | 51 | 135 | 0/13 | yes | yes |
| versions\1.21.9 | Forge | 1.21.9 | 59.0.5 | 21 | 9.4.1 | v1.5.0-Forge-1.21.9 | net.wurstpenguin | 751 | 51 | 135 | 0/13 | yes | yes |
| versions\26.1 | Forge | 26.1 | 62.0.9 | 25 | 9.4.1 | v1.5.0-Forge-26.1 | net.wurstpenguin | 790 | 0 | 0 | 0/13 | yes | yes |
| versions\26.1.1 | Forge | 26.1.1 | 63.0.2 | 25 | 9.4.1 | v1.5.0-Forge-26.1.1 | net.wurstpenguin | 790 | 0 | 0 | 0/13 | yes | yes |
| versions\26.1.2 | Forge | 26.1.2 | 64.1.0 | 25 | 9.4.1 | v1.5.0-Forge-26.1.2 | net.wurstpenguin | 790 | 0 | 0 | 0/13 | yes | yes |
| versions\26.2 | Forge | 26.2 | 65.1.0 | 25 | 9.4.1 | v1.5.0-Forge-26.2 | net.wurstpenguin | 792 | 0 | 0 | 0/13 | yes | yes |
| versions\26.3 | Forge | 26.3 | 66.0.3 | 25 | 9.4.1 | v1.5.0-Forge-26.3 | net.wurstpenguin | 794 | 0 | 0 | 0/13 | yes | yes |
| neoforge | NeoForge | 1.20.1 | 47.1.3 | 17 | 8.14.4 | v1.5.0-NeoForge-1.20.1 | net.wurstclient | 738 | 0 | 0 | 0/13 | yes | yes |
| neoforge\versions\1.20.2 | NeoForge | 1.20.2 |  | 17 | 8.11 | v1.5.0-NeoForge-1.20.2 | net.wurstpenguin | 753 | 0 | 0 | 0/13 | yes | yes |
| neoforge\versions\1.20.3 | NeoForge | 1.20.3 |  | 17 | 8.11 | v1.5.0-NeoForge-1.20.3 | net.wurstpenguin | 768 | 0 | 0 | 0/13 | yes | yes |
| neoforge\versions\1.20.4 | NeoForge | 1.20.4 |  | 17 | 8.11 | v1.5.0-NeoForge-1.20.4 | net.wurstpenguin | 768 | 0 | 0 | 0/13 | yes | yes |
| neoforge\versions\1.20.5 | NeoForge | 1.20.5 |  | 21 | 8.11 | v1.5.0-NeoForge-1.20.5 | net.wurstpenguin | 767 | 0 | 0 | 0/13 | yes | yes |
| neoforge\versions\1.20.6 | NeoForge | 1.20.6 |  | 21 | 8.11 | v1.5.0-NeoForge-1.20.6 | net.wurstpenguin | 767 | 0 | 0 | 0/13 | yes | yes |
| neoforge\versions\1.21 | NeoForge | 1.21 |  | 21 | 9.4.1 | v1.5.0-NeoForge-1.21 | net.wurstpenguin | 741 | 51 | 135 | 0/13 | yes | yes |
| neoforge\versions\1.21.1 | NeoForge | 1.21.1 |  | 21 | 9.4.1 | v1.5.0-NeoForge-1.21.1 | net.wurstpenguin | 741 | 51 | 135 | 0/13 | yes | yes |
| neoforge\versions\1.21.10 | NeoForge | 1.21.10 |  | 21 | 9.4.1 | v1.5.0-NeoForge-1.21.10 | net.wurstpenguin | 752 | 51 | 135 | 0/13 | yes | yes |
| neoforge\versions\1.21.11 | NeoForge | 1.21.11 |  | 21 | 9.4.1 | v1.5.0-NeoForge-1.21.11 | net.wurstpenguin | 752 | 51 | 135 | 0/13 | yes | yes |
| neoforge\versions\1.21.2 | NeoForge | 1.21.2 |  | 21 | 9.4.1 | v1.5.0-NeoForge-1.21.2 | net.wurstpenguin | 740 | 51 | 135 | 0/13 | yes | yes |
| neoforge\versions\1.21.3 | NeoForge | 1.21.3 |  | 21 | 9.4.1 | v1.5.0-NeoForge-1.21.3 | net.wurstpenguin | 740 | 51 | 135 | 0/13 | yes | yes |
| neoforge\versions\1.21.4 | NeoForge | 1.21.4 |  | 21 | 9.4.1 | v1.5.0-NeoForge-1.21.4 | net.wurstpenguin | 740 | 51 | 135 | 0/13 | yes | yes |
| neoforge\versions\1.21.5 | NeoForge | 1.21.5 |  | 21 | 9.4.1 | v1.5.0-NeoForge-1.21.5 | net.wurstpenguin | 750 | 51 | 135 | 0/13 | yes | yes |
| neoforge\versions\1.21.6 | NeoForge | 1.21.6 |  | 21 | 9.4.1 | v1.5.0-NeoForge-1.21.6 | net.wurstpenguin | 751 | 51 | 135 | 0/13 | yes | yes |
| neoforge\versions\1.21.7 | NeoForge | 1.21.7 |  | 21 | 9.4.1 | v1.5.0-NeoForge-1.21.7 | net.wurstpenguin | 751 | 51 | 135 | 0/13 | yes | yes |
| neoforge\versions\1.21.8 | NeoForge | 1.21.8 |  | 21 | 9.4.1 | v1.5.0-NeoForge-1.21.8 | net.wurstpenguin | 752 | 51 | 135 | 0/13 | yes | yes |
| neoforge\versions\1.21.9 | NeoForge | 1.21.9 |  | 21 | 9.4.1 | v1.5.0-NeoForge-1.21.9 | net.wurstpenguin | 752 | 51 | 135 | 0/13 | yes | yes |
| neoforge\versions\26.1 | NeoForge | 26.1 |  | 25 | 9.4.1 | 1.5.0 | net.wurstclient | 790 | 0 | 0 | 0/13 | yes | yes |
| neoforge\versions\26.1.1 | NeoForge | 26.1.1 |  | 25 | 9.4.1 | 1.5.0 | net.wurstclient | 790 | 0 | 0 | 0/13 | yes | yes |
| neoforge\versions\26.1.2 | NeoForge | 26.1.2 |  | 25 | 9.4.1 | 1.5.0 | net.wurstclient | 790 | 0 | 0 | 0/13 | yes | yes |
| neoforge\versions\26.2 | NeoForge | 26.2 |  | 25 | 9.4.1 | 1.5.0 | net.wurstclient | 793 | 0 | 0 | 0/13 | yes | yes |
| neoforge\versions\26.3 | NeoForge | 26.3 |  | 25 | 9.4.1 | 1.5.0 | net.wurstclient | 795 | 0 | 0 | 0/13 | yes | yes |

## 版本一致性

### 加载器版本取值分布

| 加载器 | 版本 | 工程数 |
| --- | --- | ---: |
| Fabric |  | 23 |
| Forge | 47.4.10 | 1 |
| Forge | 48.1.0 | 1 |
| Forge | 49.0.2 | 1 |
| Forge | 49.2.0 | 1 |
| Forge | 50.2.0 | 1 |
| Forge | 51.0.33 | 1 |
| Forge | 52.1.16 | 1 |
| Forge | 53.1.0 | 1 |
| Forge | 54.1.14 | 1 |
| Forge | 55.1.0 | 1 |
| Forge | 56.0.9 | 1 |
| Forge | 57.0.3 | 1 |
| Forge | 58.1.0 | 1 |
| Forge | 59.0.5 | 1 |
| Forge | 60.1.0 | 1 |
| Forge | 61.2.0 | 1 |
| Forge | 62.0.9 | 1 |
| Forge | 63.0.2 | 1 |
| Forge | 64.1.0 | 1 |
| Forge | 65.1.0 | 1 |
| Forge | 66.0.3 | 1 |
| NeoForge |  | 22 |
| NeoForge | 47.1.3 | 1 |

### mod_version 取值分布

| mod_version | 工程数 |
| --- | ---: |
| 1.5.0 | 5 |
| 1.5.0-Fabric-1.20.1 | 1 |
| 1.5.0-Fabric-1.20.2 | 1 |
| 1.5.0-Fabric-1.20.3 | 1 |
| 1.5.0-Fabric-1.20.4 | 1 |
| 1.5.0-Fabric-1.20.5 | 1 |
| 1.5.0-Fabric-1.20.6 | 1 |
| 1.5.0-Fabric-1.21 | 1 |
| 1.5.0-Fabric-1.21.1 | 1 |
| 1.5.0-Fabric-1.21.10 | 1 |
| 1.5.0-Fabric-1.21.11 | 1 |
| 1.5.0-Fabric-1.21.2 | 1 |
| 1.5.0-Fabric-1.21.3 | 1 |
| 1.5.0-Fabric-1.21.4 | 1 |
| 1.5.0-Fabric-1.21.5 | 1 |
| 1.5.0-Fabric-1.21.6 | 1 |
| 1.5.0-Fabric-1.21.7 | 1 |
| 1.5.0-Fabric-1.21.8 | 1 |
| 1.5.0-Fabric-1.21.9 | 1 |
| 1.5.0-Fabric-26.1 | 1 |
| 1.5.0-Fabric-26.1.1 | 1 |
| 1.5.0-Fabric-26.1.2 | 1 |
| 1.5.0-Fabric-26.2 | 1 |
| 1.5.0-Fabric-26.3 | 1 |
| v1.5.0-Forge-1.20.2 | 1 |
| v1.5.0-Forge-1.20.3 | 1 |
| v1.5.0-Forge-1.20.4 | 1 |
| v1.5.0-Forge-1.20.6 | 1 |
| v1.5.0-Forge-1.21 | 1 |
| v1.5.0-Forge-1.21.1 | 1 |
| v1.5.0-Forge-1.21.10 | 1 |
| v1.5.0-Forge-1.21.11 | 1 |
| v1.5.0-Forge-1.21.3 | 1 |
| v1.5.0-Forge-1.21.4 | 1 |
| v1.5.0-Forge-1.21.5 | 1 |
| v1.5.0-Forge-1.21.6 | 1 |
| v1.5.0-Forge-1.21.7 | 1 |
| v1.5.0-Forge-1.21.8 | 1 |
| v1.5.0-Forge-1.21.9 | 1 |
| v1.5.0-Forge-26.1 | 1 |
| v1.5.0-Forge-26.1.1 | 1 |
| v1.5.0-Forge-26.1.2 | 1 |
| v1.5.0-Forge-26.2 | 1 |
| v1.5.0-Forge-26.3 | 1 |
| v1.5.0-NeoForge-1.20.1 | 1 |
| v1.5.0-NeoForge-1.20.2 | 1 |
| v1.5.0-NeoForge-1.20.3 | 1 |
| v1.5.0-NeoForge-1.20.4 | 1 |
| v1.5.0-NeoForge-1.20.5 | 1 |
| v1.5.0-NeoForge-1.20.6 | 1 |
| v1.5.0-NeoForge-1.21 | 1 |
| v1.5.0-NeoForge-1.21.1 | 1 |
| v1.5.0-NeoForge-1.21.10 | 1 |
| v1.5.0-NeoForge-1.21.11 | 1 |
| v1.5.0-NeoForge-1.21.2 | 1 |
| v1.5.0-NeoForge-1.21.3 | 1 |
| v1.5.0-NeoForge-1.21.4 | 1 |
| v1.5.0-NeoForge-1.21.5 | 1 |
| v1.5.0-NeoForge-1.21.6 | 1 |
| v1.5.0-NeoForge-1.21.7 | 1 |
| v1.5.0-NeoForge-1.21.8 | 1 |
| v1.5.0-NeoForge-1.21.9 | 1 |
| v1.6.0-Forge-1.20.1 | 1 |

## v1.6 功能缺口

v1.6 新增的 13 个源码包只存在于根工程。以下工程不含任何 v1.6 包：

共 **66** 个：`fabric`、`neoforge`、`versions\1.20.2`、`versions\1.20.3`、`versions\1.20.4`、`versions\1.20.6`、`versions\1.21`、`versions\1.21.1`、`versions\1.21.10`、`versions\1.21.11`、`versions\1.21.3`、`versions\1.21.4`、`versions\1.21.5`、`versions\1.21.6`、`versions\1.21.7`、`versions\1.21.8`、`versions\1.21.9`、`versions\26.1`、`versions\26.1.1`、`versions\26.1.2`、`versions\26.2`、`versions\26.3`、`fabric\versions\1.20.2`、`fabric\versions\1.20.3`、`fabric\versions\1.20.4`、`fabric\versions\1.20.5`、`fabric\versions\1.20.6`、`fabric\versions\1.21`、`fabric\versions\1.21.1`、`fabric\versions\1.21.10`、`fabric\versions\1.21.11`、`fabric\versions\1.21.2`、`fabric\versions\1.21.3`、`fabric\versions\1.21.4`、`fabric\versions\1.21.5`、`fabric\versions\1.21.6`、`fabric\versions\1.21.7`、`fabric\versions\1.21.8`、`fabric\versions\1.21.9`、`fabric\versions\26.1`、`fabric\versions\26.1.1`、`fabric\versions\26.1.2`、`fabric\versions\26.2`、`fabric\versions\26.3`、`neoforge\versions\1.20.2`、`neoforge\versions\1.20.3`、`neoforge\versions\1.20.4`、`neoforge\versions\1.20.5`、`neoforge\versions\1.20.6`、`neoforge\versions\1.21`、`neoforge\versions\1.21.1`、`neoforge\versions\1.21.10`、`neoforge\versions\1.21.11`、`neoforge\versions\1.21.2`、`neoforge\versions\1.21.3`、`neoforge\versions\1.21.4`、`neoforge\versions\1.21.5`、`neoforge\versions\1.21.6`、`neoforge\versions\1.21.7`、`neoforge\versions\1.21.8`、`neoforge\versions\1.21.9`、`neoforge\versions\26.1`、`neoforge\versions\26.1.1`、`neoforge\versions\26.1.2`、`neoforge\versions\26.2`、`neoforge\versions\26.3`

> 这是当前最大的功能缺口。README 的版本矩阵宣称支持全部 23 个 MC 版本，
> 但只有根工程 Forge 1.20.1 具备 v1.6 能力。

