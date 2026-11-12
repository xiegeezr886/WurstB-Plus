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
| src/main/java 文件 | **78,238** |
| 测试类 / 测试方法（全部工程合计） | **1,574 / 4,941** |
| 根工程源码 / 测试类 / 测试方法 | **991 / 220 / 1,439** |
| 含 v1.6 子系统的工程 | **1 / 67** |
| 含 gradle-wrapper.jar 的工程 | **67 / 67** |
| 含 LICENSE.txt 的工程 | **67 / 67** |
| mod_version 不同取值数 | **63** |

## 崩溃与安全修复矩阵（2026-11-12）

| 问题 | 当前源码覆盖 | 验证边界 |
| --- | --- | --- |
| NanoVG 非法释放 / jemalloc 崩溃 | 15 个 26.x 已回填；全部 66 份实现无错误释放 | 全仓回归闸门；游戏长时间渲染仍需实测 |
| NeoForge dangling Baritone 入口 | 1.20.5、1.20.6、1.21、1.21.1 已成对排除 Baritone 元数据与空存根 | 排除限定于依赖 zipTree，保留本模组元数据 |
| Baritone 配置遮蔽 | 移除 20 个嵌套工程的外层副本；7 个平铺工程逐项核对依赖配置 | 发布 jar 校验禁止嵌套配置同名遮蔽 |
| Reach 常量冲突 | 11 份 legacy 注入改为可选 | 冲突时超过 6 格的实体搜索会降级；不再因此阻止启动 |
| 启动期翻译 / OpenOpal | 67 份翻译已守卫；66 份旋转与 tick 初始化已守卫 | 保留客户端禁用时的旋转清理 |
| Fullbright 保存越界 | 67 个工程注册保存包装器 | 仅保存期间使用合法亮度，finally 恢复渲染值 |
| MixinExtras 旧版转换器 | 23 个 Fabric 工程内嵌 0.5.5；Loader 最低 0.17.0 | Windows 日志中的旧版缺陷；不是 Android 特有问题 |
| 原生库被资源包替换 | 根工程 FFmpeg / Skiko 使用模块资源与编译期 SHA-256 | 本机行为测试；不通过资源包管理器读取可执行内容 |
| 旧密钥反序列化 | common 与 66 个副本均加类型、深度、引用、字节、数组限制 | 验证旧密钥可迁移，恶意类型不会执行 readObject |
| NanoVG 缓存损坏 / 架构混用 | 66 份实现按版本、平台、架构和 SHA-256 隔离并原子写入 | 验证损坏修复、不同内容与架构隔离、失败不发布 |
| Accessor / Invoker 漏注册 | NeoForge 1.20.1 补 10 个；其它版本同类遗漏补 21 个 | 67 工程实际调用注册检查；同版本已注册源码比对 |
| Fabric 1.21.11 Sodium X-Ray | 新增现代方块与流体回调、可选类资源判断 | 核对 0.8.0 / 0.8.14 发布依赖；6 项回调测试，尚未游戏实测 |

源码修复不会更新已发布的旧 JAR。完整构建、游戏启动及资源包兼容性测试的实际结果见 [修复与验证记录](../report/CRASH-FIXES-2026-11-12.md)。

## 工程清单

| 工程 | 加载器 | MC | 加载器版本 | Java | Gradle | mod_version | 组 | 源码 | 测试类 | 测试方法 | v1.6 包 | wrapper | LICENSE |
| --- | --- | --- | --- | ---: | ---: | --- | --- | ---: | ---: | ---: | ---: | :---: | :---: |
| fabric | Fabric | 1.20.1 |  |  | 8.11 | 1.5.1-Fabric-1.20.1 | net.wurstpenguin | 1166 | 1 | 4 | 0/13 | yes | yes |
| fabric/versions/1.20.2 | Fabric | 1.20.2 |  |  | 8.11 | 1.5.1-Fabric-1.20.2 | net.wurstpenguin | 1162 | 0 | 0 | 0/13 | yes | yes |
| fabric/versions/1.20.3 | Fabric | 1.20.3 |  |  | 8.11 | 1.5.1-Fabric-1.20.3 | net.wurstpenguin | 1161 | 0 | 0 | 0/13 | yes | yes |
| fabric/versions/1.20.4 | Fabric | 1.20.4 |  |  | 8.11 | 1.5.1-Fabric-1.20.4 | net.wurstpenguin | 1161 | 0 | 0 | 0/13 | yes | yes |
| fabric/versions/1.20.5 | Fabric | 1.20.5 |  |  | 8.11 | 1.5.1-Fabric-1.20.5 | net.wurstpenguin | 1192 | 0 | 0 | 0/13 | yes | yes |
| fabric/versions/1.20.6 | Fabric | 1.20.6 |  |  | 8.11 | 1.5.1-Fabric-1.20.6 | net.wurstpenguin | 1192 | 0 | 0 | 0/13 | yes | yes |
| fabric/versions/1.21 | Fabric | 1.21 |  |  | 8.11 | 1.5.1-Fabric-1.21 | net.wurstpenguin | 1192 | 0 | 0 | 0/13 | yes | yes |
| fabric/versions/1.21.1 | Fabric | 1.21.1 |  |  | 8.11 | 1.5.1-Fabric-1.21.1 | net.wurstpenguin | 1192 | 41 | 106 | 0/13 | yes | yes |
| fabric/versions/1.21.10 | Fabric | 1.21.10 |  |  | 9.6.0 | 1.5.1-Fabric-1.21.10 | net.wurstpenguin | 1162 | 41 | 106 | 0/13 | yes | yes |
| fabric/versions/1.21.11 | Fabric | 1.21.11 |  |  | 9.6.0 | 1.5.1-Fabric-1.21.11 | net.wurstpenguin | 1168 | 41 | 106 | 0/13 | yes | yes |
| fabric/versions/1.21.2 | Fabric | 1.21.2 |  |  | 8.11 | 1.5.1-Fabric-1.21.2 | net.wurstpenguin | 1192 | 0 | 0 | 0/13 | yes | yes |
| fabric/versions/1.21.3 | Fabric | 1.21.3 |  |  | 9.6.0 | 1.5.1-Fabric-1.21.3 | net.wurstpenguin | 1192 | 41 | 106 | 0/13 | yes | yes |
| fabric/versions/1.21.4 | Fabric | 1.21.4 |  |  | 9.6.0 | 1.5.1-Fabric-1.21.4 | net.wurstpenguin | 1192 | 41 | 106 | 0/13 | yes | yes |
| fabric/versions/1.21.5 | Fabric | 1.21.5 |  |  | 9.6.0 | 1.5.1-Fabric-1.21.5 | net.wurstpenguin | 1191 | 41 | 106 | 0/13 | yes | yes |
| fabric/versions/1.21.6 | Fabric | 1.21.6 |  |  | 9.6.0 | 1.5.1-Fabric-1.21.6 | net.wurstpenguin | 1162 | 41 | 106 | 0/13 | yes | yes |
| fabric/versions/1.21.7 | Fabric | 1.21.7 |  |  | 9.6.0 | 1.5.1-Fabric-1.21.7 | net.wurstpenguin | 1162 | 41 | 106 | 0/13 | yes | yes |
| fabric/versions/1.21.8 | Fabric | 1.21.8 |  |  | 9.6.0 | 1.5.1-Fabric-1.21.8 | net.wurstpenguin | 1162 | 41 | 106 | 0/13 | yes | yes |
| fabric/versions/1.21.9 | Fabric | 1.21.9 |  |  | 9.6.0 | 1.5.1-Fabric-1.21.9 | net.wurstpenguin | 1162 | 41 | 106 | 0/13 | yes | yes |
| fabric/versions/26.1 | Fabric | 26.1 |  |  | 9.6.0 | 1.5.1-Fabric-26.1 | net.wurstpenguin | 1197 | 0 | 0 | 0/13 | yes | yes |
| fabric/versions/26.1.1 | Fabric | 26.1.1 |  |  | 9.6.0 | 1.5.1-Fabric-26.1.1 | net.wurstpenguin | 1197 | 0 | 0 | 0/13 | yes | yes |
| fabric/versions/26.1.2 | Fabric | 26.1.2 |  |  | 9.6.0 | 1.5.1-Fabric-26.1.2 | net.wurstpenguin | 1198 | 0 | 0 | 0/13 | yes | yes |
| fabric/versions/26.2 | Fabric | 26.2 |  |  | 9.6.0 | 1.5.1-Fabric-26.2 | net.wurstpenguin | 1204 | 0 | 0 | 0/13 | yes | yes |
| fabric/versions/26.3 | Fabric | 26.3 |  |  | 9.6.0 | 1.5.1-Fabric-26.3 | net.wurstpenguin | 1206 | 0 | 0 | 0/13 | yes | yes |
| . | Forge | 1.20.1 | 47.4.10 | 17 | 8.11 | v1.6.0-Forge-1.20.1 | net.wurstpenguin | 991 | 220 | 1439 | 13/13 | yes | yes |
| versions/1.20.2 | Forge | 1.20.2 | 48.1.0 | 17 | 8.14.4 | v1.5.1-Forge-1.20.2 | net.wurstclient | 1147 | 0 | 0 | 0/13 | yes | yes |
| versions/1.20.3 | Forge | 1.20.3 | 49.0.2 | 17 | 8.11 | v1.5.1-Forge-1.20.3 | net.wurstclient | 1161 | 0 | 0 | 0/13 | yes | yes |
| versions/1.20.4 | Forge | 1.20.4 | 49.2.0 | 17 | 8.14.4 | v1.5.1-Forge-1.20.4 | net.wurstclient | 1161 | 0 | 0 | 0/13 | yes | yes |
| versions/1.20.6 | Forge | 1.20.6 | 50.2.0 | 21 | 8.14.4 | v1.5.1-Forge-1.20.6 | net.wurstclient | 1166 | 0 | 0 | 0/13 | yes | yes |
| versions/1.21 | Forge | 1.21 | 51.0.33 | 21 | 8.11 | v1.5.1-Forge-1.21 | net.wurstpenguin | 1140 | 41 | 106 | 0/13 | yes | yes |
| versions/1.21.1 | Forge | 1.21.1 | 52.1.16 | 21 | 8.11 | v1.5.1-Forge-1.21.1 | net.wurstpenguin | 1140 | 41 | 106 | 0/13 | yes | yes |
| versions/1.21.10 | Forge | 1.21.10 | 60.1.0 | 21 | 9.4.1 | v1.5.1-Forge-1.21.10 | net.wurstpenguin | 1160 | 41 | 106 | 0/13 | yes | yes |
| versions/1.21.11 | Forge | 1.21.11 | 61.2.0 | 21 | 9.4.1 | v1.5.1-Forge-1.21.11 | net.wurstpenguin | 1163 | 41 | 106 | 0/13 | yes | yes |
| versions/1.21.3 | Forge | 1.21.3 | 53.1.0 | 21 | 9.4.1 | v1.5.1-Forge-1.21.3 | net.wurstpenguin | 1154 | 41 | 106 | 0/13 | yes | yes |
| versions/1.21.4 | Forge | 1.21.4 | 54.1.14 | 21 | 9.4.1 | v1.5.1-Forge-1.21.4 | net.wurstpenguin | 1154 | 41 | 106 | 0/13 | yes | yes |
| versions/1.21.5 | Forge | 1.21.5 | 55.1.0 | 21 | 9.4.1 | v1.5.1-Forge-1.21.5 | net.wurstpenguin | 1160 | 41 | 106 | 0/13 | yes | yes |
| versions/1.21.6 | Forge | 1.21.6 | 56.0.9 | 21 | 9.4.1 | v1.5.1-Forge-1.21.6 | net.wurstpenguin | 1159 | 41 | 106 | 0/13 | yes | yes |
| versions/1.21.7 | Forge | 1.21.7 | 57.0.3 | 21 | 9.4.1 | v1.5.1-Forge-1.21.7 | net.wurstpenguin | 1161 | 41 | 106 | 0/13 | yes | yes |
| versions/1.21.8 | Forge | 1.21.8 | 58.1.0 | 21 | 9.4.1 | v1.5.1-Forge-1.21.8 | net.wurstpenguin | 1160 | 41 | 106 | 0/13 | yes | yes |
| versions/1.21.9 | Forge | 1.21.9 | 59.0.5 | 21 | 9.4.1 | v1.5.1-Forge-1.21.9 | net.wurstpenguin | 1159 | 41 | 106 | 0/13 | yes | yes |
| versions/26.1 | Forge | 26.1 | 62.0.9 | 25 | 9.4.1 | v1.5.1-Forge-26.1 | net.wurstpenguin | 1196 | 0 | 0 | 0/13 | yes | yes |
| versions/26.1.1 | Forge | 26.1.1 | 63.0.2 | 25 | 9.4.1 | v1.5.1-Forge-26.1.1 | net.wurstpenguin | 1196 | 0 | 0 | 0/13 | yes | yes |
| versions/26.1.2 | Forge | 26.1.2 | 64.1.0 | 25 | 9.4.1 | v1.5.1-Forge-26.1.2 | net.wurstpenguin | 1196 | 0 | 0 | 0/13 | yes | yes |
| versions/26.2 | Forge | 26.2 | 65.1.0 | 25 | 9.4.1 | v1.5.1-Forge-26.2 | net.wurstpenguin | 1200 | 0 | 0 | 0/13 | yes | yes |
| versions/26.3 | Forge | 26.3 | 66.0.3 | 25 | 9.4.1 | v1.5.1-Forge-26.3 | net.wurstpenguin | 1202 | 0 | 0 | 0/13 | yes | yes |
| neoforge | NeoForge | 1.20.1 | 47.1.3 | 17 | 8.14.4 | v1.5.1-NeoForge-1.20.1 | net.wurstclient | 1145 | 0 | 0 | 0/13 | yes | yes |
| neoforge/versions/1.20.2 | NeoForge | 1.20.2 |  | 17 | 8.11 | v1.5.1-NeoForge-1.20.2 | net.wurstpenguin | 1148 | 0 | 0 | 0/13 | yes | yes |
| neoforge/versions/1.20.3 | NeoForge | 1.20.3 |  | 17 | 8.11 | v1.5.1-NeoForge-1.20.3 | net.wurstpenguin | 1162 | 0 | 0 | 0/13 | yes | yes |
| neoforge/versions/1.20.4 | NeoForge | 1.20.4 |  | 17 | 8.11 | v1.5.1-NeoForge-1.20.4 | net.wurstpenguin | 1162 | 0 | 0 | 0/13 | yes | yes |
| neoforge/versions/1.20.5 | NeoForge | 1.20.5 |  | 21 | 8.11 | v1.5.1-NeoForge-1.20.5 | net.wurstpenguin | 1166 | 0 | 0 | 0/13 | yes | yes |
| neoforge/versions/1.20.6 | NeoForge | 1.20.6 |  | 21 | 8.11 | v1.5.1-NeoForge-1.20.6 | net.wurstpenguin | 1166 | 0 | 0 | 0/13 | yes | yes |
| neoforge/versions/1.21 | NeoForge | 1.21 |  | 21 | 9.4.1 | v1.5.1-NeoForge-1.21 | net.wurstpenguin | 1140 | 41 | 106 | 0/13 | yes | yes |
| neoforge/versions/1.21.1 | NeoForge | 1.21.1 |  | 21 | 9.4.1 | v1.5.1-NeoForge-1.21.1 | net.wurstpenguin | 1140 | 41 | 106 | 0/13 | yes | yes |
| neoforge/versions/1.21.10 | NeoForge | 1.21.10 |  | 21 | 9.4.1 | v1.5.1-NeoForge-1.21.10 | net.wurstpenguin | 1159 | 41 | 106 | 0/13 | yes | yes |
| neoforge/versions/1.21.11 | NeoForge | 1.21.11 |  | 21 | 9.4.1 | v1.5.1-NeoForge-1.21.11 | net.wurstpenguin | 1163 | 41 | 106 | 0/13 | yes | yes |
| neoforge/versions/1.21.2 | NeoForge | 1.21.2 |  | 21 | 9.4.1 | v1.5.1-NeoForge-1.21.2 | net.wurstpenguin | 1139 | 41 | 106 | 0/13 | yes | yes |
| neoforge/versions/1.21.3 | NeoForge | 1.21.3 |  | 21 | 9.4.1 | v1.5.1-NeoForge-1.21.3 | net.wurstpenguin | 1139 | 41 | 106 | 0/13 | yes | yes |
| neoforge/versions/1.21.4 | NeoForge | 1.21.4 |  | 21 | 9.4.1 | v1.5.1-NeoForge-1.21.4 | net.wurstpenguin | 1139 | 41 | 106 | 0/13 | yes | yes |
| neoforge/versions/1.21.5 | NeoForge | 1.21.5 |  | 21 | 9.4.1 | v1.5.1-NeoForge-1.21.5 | net.wurstpenguin | 1151 | 41 | 106 | 0/13 | yes | yes |
| neoforge/versions/1.21.6 | NeoForge | 1.21.6 |  | 21 | 9.4.1 | v1.5.1-NeoForge-1.21.6 | net.wurstpenguin | 1159 | 41 | 106 | 0/13 | yes | yes |
| neoforge/versions/1.21.7 | NeoForge | 1.21.7 |  | 21 | 9.4.1 | v1.5.1-NeoForge-1.21.7 | net.wurstpenguin | 1159 | 41 | 106 | 0/13 | yes | yes |
| neoforge/versions/1.21.8 | NeoForge | 1.21.8 |  | 21 | 9.4.1 | v1.5.1-NeoForge-1.21.8 | net.wurstpenguin | 1159 | 41 | 106 | 0/13 | yes | yes |
| neoforge/versions/1.21.9 | NeoForge | 1.21.9 |  | 21 | 9.4.1 | v1.5.1-NeoForge-1.21.9 | net.wurstpenguin | 1159 | 41 | 106 | 0/13 | yes | yes |
| neoforge/versions/26.1 | NeoForge | 26.1 |  | 25 | 9.4.1 | 1.5.1 | net.wurstclient | 1196 | 0 | 0 | 0/13 | yes | yes |
| neoforge/versions/26.1.1 | NeoForge | 26.1.1 |  | 25 | 9.4.1 | 1.5.1 | net.wurstclient | 1196 | 0 | 0 | 0/13 | yes | yes |
| neoforge/versions/26.1.2 | NeoForge | 26.1.2 |  | 25 | 9.4.1 | 1.5.1 | net.wurstclient | 1196 | 0 | 0 | 0/13 | yes | yes |
| neoforge/versions/26.2 | NeoForge | 26.2 |  | 25 | 9.4.1 | 1.5.1 | net.wurstclient | 1201 | 0 | 0 | 0/13 | yes | yes |
| neoforge/versions/26.3 | NeoForge | 26.3 |  | 25 | 9.4.1 | 1.5.1 | net.wurstclient | 1203 | 0 | 0 | 0/13 | yes | yes |

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
| 1.5.1 | 5 |
| 1.5.1-Fabric-1.20.1 | 1 |
| 1.5.1-Fabric-1.20.2 | 1 |
| 1.5.1-Fabric-1.20.3 | 1 |
| 1.5.1-Fabric-1.20.4 | 1 |
| 1.5.1-Fabric-1.20.5 | 1 |
| 1.5.1-Fabric-1.20.6 | 1 |
| 1.5.1-Fabric-1.21 | 1 |
| 1.5.1-Fabric-1.21.1 | 1 |
| 1.5.1-Fabric-1.21.10 | 1 |
| 1.5.1-Fabric-1.21.11 | 1 |
| 1.5.1-Fabric-1.21.2 | 1 |
| 1.5.1-Fabric-1.21.3 | 1 |
| 1.5.1-Fabric-1.21.4 | 1 |
| 1.5.1-Fabric-1.21.5 | 1 |
| 1.5.1-Fabric-1.21.6 | 1 |
| 1.5.1-Fabric-1.21.7 | 1 |
| 1.5.1-Fabric-1.21.8 | 1 |
| 1.5.1-Fabric-1.21.9 | 1 |
| 1.5.1-Fabric-26.1 | 1 |
| 1.5.1-Fabric-26.1.1 | 1 |
| 1.5.1-Fabric-26.1.2 | 1 |
| 1.5.1-Fabric-26.2 | 1 |
| 1.5.1-Fabric-26.3 | 1 |
| v1.5.1-Forge-1.20.2 | 1 |
| v1.5.1-Forge-1.20.3 | 1 |
| v1.5.1-Forge-1.20.4 | 1 |
| v1.5.1-Forge-1.20.6 | 1 |
| v1.5.1-Forge-1.21 | 1 |
| v1.5.1-Forge-1.21.1 | 1 |
| v1.5.1-Forge-1.21.10 | 1 |
| v1.5.1-Forge-1.21.11 | 1 |
| v1.5.1-Forge-1.21.3 | 1 |
| v1.5.1-Forge-1.21.4 | 1 |
| v1.5.1-Forge-1.21.5 | 1 |
| v1.5.1-Forge-1.21.6 | 1 |
| v1.5.1-Forge-1.21.7 | 1 |
| v1.5.1-Forge-1.21.8 | 1 |
| v1.5.1-Forge-1.21.9 | 1 |
| v1.5.1-Forge-26.1 | 1 |
| v1.5.1-Forge-26.1.1 | 1 |
| v1.5.1-Forge-26.1.2 | 1 |
| v1.5.1-Forge-26.2 | 1 |
| v1.5.1-Forge-26.3 | 1 |
| v1.5.1-NeoForge-1.20.1 | 1 |
| v1.5.1-NeoForge-1.20.2 | 1 |
| v1.5.1-NeoForge-1.20.3 | 1 |
| v1.5.1-NeoForge-1.20.4 | 1 |
| v1.5.1-NeoForge-1.20.5 | 1 |
| v1.5.1-NeoForge-1.20.6 | 1 |
| v1.5.1-NeoForge-1.21 | 1 |
| v1.5.1-NeoForge-1.21.1 | 1 |
| v1.5.1-NeoForge-1.21.10 | 1 |
| v1.5.1-NeoForge-1.21.11 | 1 |
| v1.5.1-NeoForge-1.21.2 | 1 |
| v1.5.1-NeoForge-1.21.3 | 1 |
| v1.5.1-NeoForge-1.21.4 | 1 |
| v1.5.1-NeoForge-1.21.5 | 1 |
| v1.5.1-NeoForge-1.21.6 | 1 |
| v1.5.1-NeoForge-1.21.7 | 1 |
| v1.5.1-NeoForge-1.21.8 | 1 |
| v1.5.1-NeoForge-1.21.9 | 1 |
| v1.6.0-Forge-1.20.1 | 1 |

## v1.6 功能缺口

v1.6 新增的 13 个源码包只存在于根工程。以下工程不含任何 v1.6 包：

共 **66** 个：`fabric`、`neoforge`、`versions/1.20.2`、`versions/1.20.3`、`versions/1.20.4`、`versions/1.20.6`、`versions/1.21`、`versions/1.21.1`、`versions/1.21.10`、`versions/1.21.11`、`versions/1.21.3`、`versions/1.21.4`、`versions/1.21.5`、`versions/1.21.6`、`versions/1.21.7`、`versions/1.21.8`、`versions/1.21.9`、`versions/26.1`、`versions/26.1.1`、`versions/26.1.2`、`versions/26.2`、`versions/26.3`、`fabric/versions/1.20.2`、`fabric/versions/1.20.3`、`fabric/versions/1.20.4`、`fabric/versions/1.20.5`、`fabric/versions/1.20.6`、`fabric/versions/1.21`、`fabric/versions/1.21.1`、`fabric/versions/1.21.10`、`fabric/versions/1.21.11`、`fabric/versions/1.21.2`、`fabric/versions/1.21.3`、`fabric/versions/1.21.4`、`fabric/versions/1.21.5`、`fabric/versions/1.21.6`、`fabric/versions/1.21.7`、`fabric/versions/1.21.8`、`fabric/versions/1.21.9`、`fabric/versions/26.1`、`fabric/versions/26.1.1`、`fabric/versions/26.1.2`、`fabric/versions/26.2`、`fabric/versions/26.3`、`neoforge/versions/1.20.2`、`neoforge/versions/1.20.3`、`neoforge/versions/1.20.4`、`neoforge/versions/1.20.5`、`neoforge/versions/1.20.6`、`neoforge/versions/1.21`、`neoforge/versions/1.21.1`、`neoforge/versions/1.21.10`、`neoforge/versions/1.21.11`、`neoforge/versions/1.21.2`、`neoforge/versions/1.21.3`、`neoforge/versions/1.21.4`、`neoforge/versions/1.21.5`、`neoforge/versions/1.21.6`、`neoforge/versions/1.21.7`、`neoforge/versions/1.21.8`、`neoforge/versions/1.21.9`、`neoforge/versions/26.1`、`neoforge/versions/26.1.1`、`neoforge/versions/26.1.2`、`neoforge/versions/26.2`、`neoforge/versions/26.3`

> 这是当前最大的功能缺口。README 的版本矩阵宣称支持全部 23 个 MC 版本，
> 但只有根工程 Forge 1.20.1 具备 v1.6 能力。

