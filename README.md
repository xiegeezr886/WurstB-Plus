# WurstB+ Plus

基于 Wurst 代码结构扩展的 Minecraft Forge 客户端模组，支持 1.20.1、1.21.1 和 26.1.2 多版本。

## 支持版本

| Minecraft | 加载器 | 引擎版本 | Java | 工程目录 | 分支 |
| --- | --- | --- | --- | --- | --- |
| 1.20.1 | Forge | 47.4.10 | 17 | `src/` | `1.20.1` |
| 1.20.1 | NeoForge | 47.1.3 | 17 | `newforge/` | `1.20.1` |
| 1.21.1 | Forge | 52.1.16 | 21 | `versions/1.21.1/` | `1.21.1` |
| 1.21.1 | NeoForge | 21.1.244 | 21 | `newforge/versions/1.21.1/` | `1.21.1` |
| 26.1.2 | Forge | 64.1.0 | 25 | `versions/26.1.2/` | `26.1.2` |

> 26.1.2 为 Mojang 2026 年新版本命名规范（`YY.D.H`），与旧版 1.x 体系不同。

## 项目结构

```
├── src/                          # 1.20.1 Forge 主版本
├── newforge/                     # 1.20.1 NeoForge + 1.21.1 NeoForge
├── versions/
│   ├── 1.21.1/                   # 1.21.1 Forge
│   └── 26.1.2/                   # 26.1.2 Forge (2026 新版)
├── AGENTS.md                     # AI 上下文文档
├── PROJECT_INDEX.md              # 文件索引
├── README.md                     # 本文件
└── LICENSE.txt                   # GPL-3.0
```

## 快速开始

### 1.20.1 Forge
```powershell
.\gradlew.bat jarJar --console=plain
```
产物：`build/libs/WurstB+ Plus-v1.5.0-Forge-1.20.1-all.jar`

### 1.21.1 Forge
```powershell
cd versions\1.21.1
.\gradlew.bat jarJar --console=plain
```
产物：`versions/1.21.1/build/libs/WurstB+ Plus-v1.5.0-Forge-1.21.1-all.jar`

### 26.1.2 Forge
```powershell
cd versions\26.1.2
$env:_JAVA_OPTIONS = "-Djavax.net.ssl.trustAll=true"
.\gradlew.bat allJar --console=plain
```
产物：`versions/26.1.2/build/libs/WurstB+ Plus-v1.5.0-MC26.1.2-all.jar`

## 功能特性

- 197 个 Hack 模块
- 54 个命令
- ClickGUI + Navigator 双 GUI 入口
- HUD 编辑器（可拖动布局）
- 宏系统、路径点、代理管理
- 多语言翻译支持
- Discord RPC 集成
- Baritone 自动化集成

## 版本差异

| 特性 | 1.20.1 | 1.21.1 | 26.1.2 |
| --- | --- | --- | --- |
| 渲染 API | `render()` | `render()` | `extractRenderState()` |
| 图形上下文 | `GuiGraphics` | `GuiGraphics` | `GuiGraphicsExtractor` |
| 文字颜色 | 6位 hex OK | 6位 hex OK | 需 8位 `0xFFxxxxxx` |
| 纹理 blit | 标准 | 标准 | 需 `RenderPipelines` |
| `BufferUploader` | 可用 | 可用 | 已移除 |
| Mixin 数量 | 70 | 72 | 74 |

## 许可

GNU General Public License v3.0
