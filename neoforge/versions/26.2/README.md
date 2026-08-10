# WurstB+ Plus NeoForge 26.2

这是 WurstB+ Plus v1.5.0 的 Minecraft 26.2 NeoForge 移植工程。

## 环境

- Minecraft 26.2
- NeoForge 26.2.0.53-beta
- ModDevGradle 2.0.143
- Gradle 9.4.1
- Java 25

## 构建

```powershell
cd neoforge\versions\26.2
$env:JAVA_HOME = "C:\Program Files\Java\jdk-25.0.4"
$env:_JAVA_OPTIONS = "-Djavax.net.ssl.trustStoreType=Windows-ROOT"
.\gradlew.bat clean build --console=plain
```

构建产物：

```text
build/libs/WurstB+ Plus-v1.5.0-NeoForge-26.2.jar
```

全依赖包内嵌 Java-WebSocket 1.5.3、Netty Socks 4.1.82.Final、Netty Proxy 4.1.82.Final 和 NeoForge Baritone 1.18.0-26.2。Baritone 保留独立加载器元数据和 Mixin Connector，无需额外安装。

## 开发运行

若 NeoForm 资源下载器报告 `PKIX path building failed`，请保留上面的 Windows 根证书库参数。最终包已通过 `clean build`、真实客户端进世界和 Baritone `#goto 0 88 0` 测试。

迁移状态与已知限制见 [PORTING_TASK.md](PORTING_TASK.md)。
