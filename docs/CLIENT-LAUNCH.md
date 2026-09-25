# 客户端启动环境（国内镜像）

本文件说明怎么在国内网络下把本仓库任意一个版本的客户端真正启动起来，包括镜像配置落在哪、
为什么 Forge 还要多一步、以及已经实测到什么程度。

## 一、镜像配置落在三个地方

### 1. 插件仓库 → 各工程的 `settings.gradle`

每个工程的 `settings.gradle` 里 `pluginManagement.repositories` 的**最前面**插了三个阿里云
仓库，官方仓库（Gradle 插件门户 / `maven.minecraftforge.net` / `maven.fabricmc.net`）保留在
后面兜底：

```groovy
pluginManagement {
    repositories {
        // 国内镜像：国内网络下优先命中，官方仓库保留在后面兜底
        maven { name = "AliyunPublic"; url = "https://maven.aliyun.com/repository/public" }
        maven { name = "AliyunGradlePlugin"; url = "https://maven.aliyun.com/repository/gradle-plugin" }
        maven { name = "AliyunCentral"; url = "https://maven.aliyun.com/repository/central" }
        maven { name = "MinecraftForge"; url = "https://maven.minecraftforge.net/" }
        ...
    }
}
```

> 为什么不用 Gradle 初始化脚本统一处理：实测在初始化脚本的 `settingsEvaluated` 阶段，
> `settings.pluginManagement.repositories` 是**空**的，而且 `clear()` 之后 `add()` 加不回
> 已有仓库，结果会把工程自己声明的仓库整个丢掉 —— ForgeGradle 只在官方 maven 上，插件解析
> 会直接失败（`Plugin [id: 'net.minecraftforge.gradle'] was not found`）。所以插件仓库只能写在
> `settings.gradle` 里。

### 2. 依赖仓库 → `gradle/init-mirrors.gradle`

依赖仓库没有上面的限制，用一个初始化脚本对所有工程生效：在各工程 `build.gradle` 求值之前，
把三个阿里云仓库插到 `repositories` / `buildscript.repositories` 的最前面（此时工程仓库还是
空的，脚本自己声明的官方仓库会自然追加在后面）。

启动脚本已经自动带上它；也可以手动指定：

```bash
./gradlew runClient --init-script ../../gradle/init-mirrors.gradle
```

想对所有 Gradle 构建生效（不必每次传参），把 `gradle/init-mirrors.gradle` 复制到
`~/.gradle/init.d/`。加 `-PnoMirror` 可跳过全部注入。

### 3. Minecraft 资源与依赖库 → Fabric 工程的 `gradle.properties`

Fabric 的 Loom 有官方镜像开关（`MirrorUtil` 读的是项目 `ext` 属性，`gradle.properties` 里的
值能直接被 `ext` 看到，已实测确认）。仓库里 **23 个 fabric 工程**的 `gradle.properties` 末尾
都加了这三行：

```properties
loom_libraries_base=https://bmclapi2.bangbang93.com/maven/
loom_resources_base=https://bmclapi2.bangbang93.com/assets/
loom_version_manifests=https://bmclapi2.bangbang93.com/mc/game/version_manifest_v2.json
```

**Forge（FG6/FG7）与 NeoForge（NeoGradle/ModDevGradle）没有这个开关** —— 它们的 Minecraft
下载地址写死在插件里。所以这些工程的资源与依赖库仍然走官方地址，需要下面的预取步骤。

### 4. Gradle 发行包 → 各工程的 `gradle-wrapper.properties`

67 个 wrapper 的 `distributionUrl` 指向腾讯云镜像（`mirrors.cloud.tencent.com/gradle/`），
本仓库用到的 8.11 / 8.14.4 / 9.4.1 / 9.6.0 四个版本都已实测可下载。

## 二、启动客户端

```powershell
# Windows PowerShell
.\scripts\run-client.ps1 26.2                 # Forge 26.2
.\scripts\run-client.ps1 1.21.9 fabric        # Fabric 1.21.9
.\scripts\run-client.ps1 1.20.1 neoforge      # NeoForge 1.20.1
.\scripts\run-client.ps1 26.2 -Offline        # 只用本地缓存
```

```bash
# Git Bash / Linux / macOS
./scripts/run-client.sh 26.2
./scripts/run-client.sh 1.21.9 fabric
```

仓库布局上，1.20.1 的三个加载器是各自的**根工程**（仓库根 / `fabric/` / `neoforge/`），
其余版本在 `<加载器>/versions/<版本>`，脚本按这个规则定位工程。

## 三、预取游戏资源（Forge / NeoForge 需要）

Forge 客户端启动时会自己从 `resources.download.minecraft.net` 拉资源，国内经常断在半路：

```text
Failed to download minecraft/sounds/block/nether_ore/break2.ogg
Caused by: java.io.IOException: https://resources.download.minecraft.net/40/40ab...
```

用脚本提前从 BMCLAPI 拉齐，之后再启动就不需要联网下载：

```bash
python scripts/fetch-assets.py 26.2              # 默认写 ForgeGradle 的资源目录
python scripts/fetch-assets.py 26.2 --jobs 16
python scripts/fetch-assets.py 1.21.9 --assets-dir D:/mc-assets
```

脚本要点：

- 资源是**内容寻址**的（文件名就是 sha1），所以不同版本之间共用同一份对象目录，已存在的
  文件会跳过，只补缺的。多版本轮流启动时，第二次起通常只需要补很少的文件。
- 下载后会校验 sha1 与大小，镜像失败时自动回退到官方地址。
- 默认目录是 `~/.gradle/caches/forge_gradle/assets`（Forge / NeoForge 启动时用的那份）。
  Fabric 用 Loom 自己的目录 `~/.gradle/caches/fabric-loom/assets`，但 Loom 本身已经走
  BMCLAPI，一般不需要手工预取。

## 四、实测到什么程度

| 项目 | 状态 |
| --- | --- |
| 阿里云仓库注入（插件 / 依赖 / buildscript） | 已实测：仓库列表里镜像在最前、官方仓库保留 |
| 阿里云端到端下载 | 已实测：从 `maven.aliyun.com/repository/public` 成功下载构件 |
| Loom 走 BMCLAPI | 已实测：删掉缓存清单后，Loom 从 `bmclapi2.bangbang93.com` 重新取回版本清单与 netty 等依赖 |
| 腾讯云 Gradle 发行包 | 已实测：四个版本均返回 200 |
| 资源预取脚本 | 已实测：按索引补齐全量对象，逐文件校验 sha1 |
| 客户端真正开窗 | **未验证** —— 开发环境无显示器/GL，只能跑到「引擎启动、开始取资源」这一步 |

## 五、注意事项

- **MC 26.3 的三个工程目前只有脚手架、尚未编译通过**（见 `docs/PORTING-26.2-26.3.md`），
  镜像配置已经就位，但还不能启动。
- 镜像只加速下载，不改变构建产物。构建脚本、依赖版本、AT/AW、混入配置都没有因此改动。
- 想把镜像换成其它源，改 `settings.gradle` 的三行与 `gradle/init-mirrors.gradle` 顶部的
  `ALIYUN` 列表即可；Fabric 的三行在各自 `gradle.properties`。

## 六、真启动之后才暴露的两个缺陷（已修）

编译器不检查混入的注入点与访问器目标。本仓库用的注解处理器在这个版本线上也不生成 refmap，
等于**完全不校验**——这一点我用对照实验确认过：把一个目标方法早已不存在的过时混入注册进去，
`clean compileJava` 照样 BUILD SUCCESSFUL。所以只有真正启动客户端才能发现这类问题。

把 26.2 的 Forge 客户端启动起来的过程中，撞到并修掉了两个**启动即崩**的缺陷：

### 1. 内置 Baritone 的混入配置声明了 Mixin 不认识的兼容级别

```text
org.spongepowered.asm.launch.MixinInitialisationError:
  Mixin config mixins.baritone.json specifies compatibility level JAVA_25
  which is not recognised
```

`mixins.baritone.json` 写的是 `JAVA_25`，而 Forge 26.2 自带的 Mixin 0.8.7 里
`MixinEnvironment$CompatibilityLevel` 枚举**最高只到 `JAVA_21`**，于是混入子系统初始化直接
中止，主界面都到不了。全仓库其余 baritone jar 都是 `JAVA_17`，只有 `1.18.0-26.2` 这三个是异类。

`baritone-maven/` 被 .gitignore 排除（jar 是本地产物），所以修法做成了可复现的脚本：

```bash
python scripts/patch-baritone-mixin-level.py           # 就地修正三个 jar
python scripts/patch-baritone-mixin-level.py --check   # 只检查
```

### 2. ToastManagerAccessor 指向了类型已变的字段

```text
Mixin apply failed wurst.mixins.json:ToastManagerAccessor -> ToastManager:
  No candidates were found matching queued:Ljava/util/List;
```

MC 从 1.21.3 起把 `ToastManager.queued` 的类型从 `List<Toast>` 换成了 `Deque<Toast>`
（javap 逐版本确认），而访问器仍声明返回 `List<Toast>`。Mixin 按擦除后的描述符精确匹配字段，
类型对不上就找不到候选。**26.x 线三棵树共 15 个文件**都有这个问题，已全部改为 `Deque<Toast>`
（调用方只用 `removeIf`，`Deque` 继承自 `Collection`，无需改动）。

### 附带：资源目录不是 ForgeGradle 的那个

Forge 7 / NeoForge 的开发启动器**复用原版启动器的资源目录**（Windows 上是
`%APPDATA%\.minecraft\assets`），而不是 FG6 时代的
`~/.gradle/caches/forge_gradle/assets`。启动日志里能看到实参：

```text
--assetIndex, 32, --assetsDir, C:\Users\<你>\AppData\Roaming\.minecraft\assets
```

`scripts/fetch-assets.py` 的默认目录已按此调整（FG6 的根工程用 `--assets-dir` 指回 FG 那个）。

### 修完之后的启动结果（26.2 Forge，实机）

```text
Successfully loaded Mixin Connector [baritone.launch.BaritoneMixinConnector]
Setting user: Dev
Backend library: LWJGL version 3.4.1+2
Reloading ResourceManager: vanilla, mod_resources
Starting WurstB+ Plus...
[nether-pathfinder] Loaded shared library
[HUD] Notification system started
Sound engine started
```

客户端完整启动、Wurst 与 Baritone 都已加载，窗口持续运行（测试时由超时命令终止）。
日志里 `vulkan-1.dll` 缺失属正常回退，游戏改用 OpenGL。
