# WurstB+ Plus NeoForge 26.1.2

这是 WurstB+ Plus v1.5.0 的 Minecraft 26.1.2 NeoForge 移植工程。

## 环境

- Minecraft 26.1.2
- NeoForge 26.1.2.87
- ModDevGradle 2.0.143
- Gradle 9.4.1
- Java 25

## 构建

```powershell
cd neoforge\versions\26.1.2
$env:JAVA_HOME = "C:\Program Files\Java\jdk-25.0.4"
$env:_JAVA_OPTIONS = "-Djavax.net.ssl.trustAll=true"
.\gradlew.bat clean build --console=plain
```

构建产物：

```text
build/libs/WurstB+ Plus-v1.5.0-NeoForge-26.1.2.jar
```

全依赖包内嵌 Java-WebSocket 1.5.3、Netty Socks 4.1.82.Final 和 Netty Proxy 4.1.82.Final。现有 Forge Baritone 与 NeoForge 26.1.2 不兼容，因此仅作为编译依赖，相关功能会在运行时安全降级。

## 开发运行

`runClient` 可能在 Minecraft 启动前因 NeoForm 资源下载器报 `PKIX path building failed`。这是本机 JDK 信任库问题，不是源码编译或模组加载错误；`clean build` 已验证通过。

迁移状态与已知限制见 [PORTING_TASK.md](PORTING_TASK.md)。
