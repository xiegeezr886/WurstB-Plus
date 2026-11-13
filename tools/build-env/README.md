# 本机构建环境补丁（zipfs 只读问题）

## 现象

在本机的 DSH 沙箱里，JVM 对**所有路径**（工作区、`%TEMP%`、新文件）都返回
`Files.isWritable(path) == false`，即使 `FileStore.isReadOnly()` 是 `false`、
直接用 `Files.write` 又能写。JDK 的 NIO zipfs 用 `Files.isWritable` 决定只读，
因此：

- `FileSystems.newFileSystem(jar)` 打开后 `isReadOnly()==true`
- 任何写 zip 的操作抛 `ReadOnlyFileSystemException`

这会打断 ForgeGradle 的 AccessTransformer（`TransformerProcessor.processJar` →
`Files.copy`）以及任何用 zipfs 写包的 Gradle 任务，导致 `compileJava` 失败：

```
Error getting artifact: ..._mapped_official_...
java.nio.file.ReadOnlyFileSystemException
```

这是沙箱环境问题，不是工程或 ForgeGradle 配置问题。

## 补丁

`zipfs-patch.jar` 只包含一个用 ASM 改过的 `jdk/nio/zipfs/ZipFileSystem.class`：

- 构造器里所有对 `readOnly` 字段的赋值强制写成 `false`
- `setReadOnly()` 变成空方法

通过 JVM 的 `--patch-module` 覆盖 JDK 自带的 `jdk.zipfs` 模块即可让 zipfs 正常可写，
不修改 JDK 安装目录，也不影响磁盘上的其它文件。

## 用法

在仓库根目录：

```powershell
$repo = (Get-Location).Path
$env:JAVA_TOOL_OPTIONS = "--patch-module=jdk.zipfs=$repo\tools\build-env\zipfs-patch.jar"
```

`JAVA_TOOL_OPTIONS` 会被 Gradle wrapper、Gradle daemon、ForgeGradle 的 AT 子进程以及
所有 Java 子进程继承，所以只需设置一次。

配合仓库已有的构建脚本：

```powershell
.\scripts\build-env.ps1            # 设置 JAVA_HOME / GRADLE_USER_HOME / JAVA_TOOL_OPTIONS
python scripts/build-v1.5-release.py
```

## 注意

- 这个补丁只应在**本机沙箱**里使用；正常 Windows 不需要，也不要把
  `zipfs-patch.jar` 打进任何发布产物。
- `--patch-module` 会改变 `jdk.zipfs` 的行为，仅对当前进程树生效，退出即失效。
- 已用 JDK 17 验证：Forge 1.20.1 工程 `compileJava` / `jarJar` 构建成功。
