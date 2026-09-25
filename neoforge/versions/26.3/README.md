# WurstB+ Plus NeoForge 26.3

这是 WurstB+ Plus v1.5.0 的 Minecraft 26.3 NeoForge 移植工程。

> **状态：本轮只完成脚手架。** 本工程由 `neoforge/versions/26.2` 克隆而来，版本锚点已改写
> 为 26.3，但**尚未编译、尚未打包、尚未启动验证**。

## 环境

- Minecraft 26.3
- NeoForge 26.3.0.16-beta（上游尚未发布 26.3 的稳定版）
- ModDevGradle 2.0.143
- Gradle 9.4.1
- Java 25

## 构建

```powershell
cd neoforge\versions\26.3
$env:JAVA_HOME = "C:\Program Files\Microsoft\jdk-25.0.4.101-hotspot"
$env:_JAVA_OPTIONS = "-Djavax.net.ssl.trustStoreType=Windows-ROOT"
.\gradlew.bat clean build --console=plain
```

构建产物：

```text
build/libs/WurstB+ Plus-v1.5.0-NeoForge-26.3.jar
```

首次构建必须联网（NeoForm 要下载并反编译 Minecraft），不要加 `--offline`。
全依赖包内嵌 Java-WebSocket 1.5.3、Netty Socks 4.1.82.Final、Netty Proxy 4.1.82.Final
和 NeoForge Baritone 1.20.0-26.3。Baritone 保留独立加载器元数据和 Mixin Connector，
无需额外安装。

## 开发运行

若 NeoForm 资源下载器报告 `PKIX path building failed`，请保留上面的 Windows 根证书库参数。

## 尚未完成

- 未跑 `compileJava`，26.3 的 API 面未经验证。预期两个破坏源：SDL3 取代 GLFW（输入层）、
  OIT 取代 Improved Transparency（渲染管线）。
- 未做游戏内启动验证，因此**不得**把产物描述为已测试。

迁移状态与已知限制见 [PORTING_TASK.md](PORTING_TASK.md)。
