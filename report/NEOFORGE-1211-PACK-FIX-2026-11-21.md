# Forgeulously Optimized 1.1.4：原包与换版修复对照

**锁定版原包在本宿主未通过。** CIT 的 ZIP 写入失败在游戏外也可复现；复用缓存、取消 Quick Play、修改 RRLS 配置均不能越过它。**允许换一项模组后，两处故障可分别解决**：作者的补丁器 1.3.0 避开 ZIP 写入；再保留首次加载画面，离线单人 Quick Play 不再先于方块图集进入世界。

## 如何应用与回退

仅针对此版本的**独立实例副本**。不修改 MRPACK、源模板或原存档。

1. 将实例顶层 `mods` 中的 `citresewn_neopatcher-1.1.0-1.2.2.jar` 换为 `citresewn_neopatcher-1.3.0-1.2.2.jar`，不要并存。**保留**原 `citresewn-1.2.2+1.21.jar`、Connector 和其他模组，不必手改任何 JAR 或缓存。作者 [Modrinth 发布记录](https://modrinth.com/mod/cit-resewn-neo-patcher/version/me9WAzwe) 明确说明新版不再修改 CIT JAR；[本地发布元数据与 SHA-512](../build/neo1211-packfix/v130.json)。受测新文件 SHA-256：`b9c2f506c5ca2cc2ebf5c8026daa77f0247befdfb397cbdd20d4b4209379a564`。
2. 在实例 `config/rrls.toml` 把 **`hideOverlays = "ALL"` 改成 `hideOverlays = "RELOADING"`**，保留其他值。新值保留首次加载画面，只隐藏后续重载画面。针对未自定义的原包提供[完整配置覆盖文件](../build/neo1211-packfix/overlay/config/rrls.toml)；它与原配置只有这一行不同。无需去掉 Quick Play、改启动权限或更换游戏 Java。
3. 回退：退出游戏，在**副本**中恢复旧补丁器和 `ALL`，或直接使用未动过的源实例。本次未测试带其他加装模组的组合。

## 实测矩阵（2026-11-21，本机时间）

| 独立实例 | 唯一对照变量（相对于该行基线） | 结果 |
|---|---|---|
| [stock-b](../build/neo1211-packfix/stock-b/stderr.log) | 锁定原包，默认 Quick Play | 退出 1，未登录；`BadMixinRemover.patchMixinsConfig:245` 写入只读 ZIP |
| [warm-b](../build/neo1211-packfix/warm-b/stderr.log) | 原包，仅复用此前原包诊断完成后净变化的两个 Connector 缓存 JAR | 同一栈，退出 1 |
| [menu-b](../build/neo1211-packfix/menu-b/stderr.log) | 原包，仅移除两对 Quick Play 参数 | 同一栈，退出 1，未到主菜单，未声称手动进档成功 |
| [rrls-b](../build/neo1211-packfix/rrls-b/stderr.log) | 原包，仅改 RRLS 一行 | 同一栈，退出 1。渲染设置不修 ZIP |
| [single-v130](../build/neo1211-packfix/single-v130/game/logs/latest.log) | 原包，仅把旧补丁器换成作者 1.3.0 | CIT、Defaults、补丁器均加载。18:34:19.069 加入，`modelGroups` 空指针并准备崩溃报告，18:34:22.112 方块图集才建好。有关窗和退出 0，**不算通过** |
| [fix-a](../build/neo1211-packfix/fix-a/game/logs/latest.log) | 相对 single-v130，仅改 RRLS 一行 | 18:35:11.959 方块图集就绪，18:35:18.184 加入，18:36:03.778 正常关窗，18:36:06.080 全维度保存 |
| [fix-b](../build/neo1211-packfix/fix-b/game/logs/latest.log) | 同一修复、另一份全新模板和存档副本 | 18:36:17.711 方块图集就绪，18:36:23.109 加入。完整停服顺序与退出状态见[实测摘要](../build/neo1211-packfix/evidence.json) |

## 归因与边界

- **CIT：本宿主的 Java 可写性判断是已复现的触发条件。** [Java 21 独立探针输出](../build/neo1211-packfix/zip-probe-21.log)：普通用户新建的 CIT 副本 `dosReadonly=false`、非截断写打开成功，但 `checkAccess(WRITE)` 抛 `AccessDeniedException`，`Files.isWritable=false`；外层与内层 ZIP 均只读，回写原内容也失败。[Java 25 探针](../build/neo1211-packfix/zip-probe-25.log)相同，仅用于交叉验证，未用 Java 25 启动游戏。没有证据把它推广成所有 Windows 机器的问题，或归因于某条具体 ACL。
- **为什么预热缓存不管用：** 锁定补丁器的 `BadMixinRemover.load()` 在发现阶段调用 `removeBadMixin()`；后者复制顶层 CIT JAR，打开该副本和它的内嵌 Defaults JAR，调用 `patchMixinsConfig()`。受测路径并不把已有 `.connector` 产物当作免写标志。warm-b 实测支持此判断；未找到可跳过这一步的官方设置。
- **modelGroups：证据支持资源加载时序，而非只在诊断环境下出现。** 无 agent 的 single-v130 重现了旧诊断日志中的“登录、空指针、图集完成”顺序；只改 RRLS 后顺序倒转。未验证手动选择世界是独立修复，也没有给锁定版记渲染通过。初始加载顺序不等于所有资源包和长期游玩的兼容保证。修复组仍有 Win32 `65544: Failed to create interface` 图形告警，不能声称整个图形环境无错。

### 补查：`65544: Win32: Failed to create interface`

**结论：这条写在 `Window / GL ERROR / Pre render` 下的消息来自 GLFW 初始化 Windows 手柄输入，不是 `modelGroups` 或创建 OpenGL 上下文失败。没有找到既保留手柄功能、又能修复该接口初始化的单项普通包配置。** 未新增游戏测试或改动修复补丁。

- **两个实例的记录：** 按完整错误文本计数，[fix-a](../build/neo1211-packfix/fix-a/game/logs/latest.log) 为 15,518 条，[fix-b](../build/neo1211-packfix/fix-b/game/logs/latest.log) 为 16,616 条；是日志条数，不是帧数。fix-a 的首条在 MidnightControls 加载配置、更新手柄映射时出现（第 455–460 行），早于进入世界。此归因只适用于这条完整消息，不含其他 GL 告警。
- **独立复现：** [探针](../build/neo1211-packfix/input-probe/InputProbe.java) 只用同一 Java 21、LWJGL 3.3.3 和同一 `glfw.dll`（自报 GLFW 3.4.0 Win32；SHA-256 `affa7e54eb0dedce4a5721c327c1a16035edbbd039cd402e08107d6d2d55eb1a`）。没有加载 Minecraft 或模组，没有创建游戏窗口或 OpenGL 上下文。三次 `glfwPollEvents()` 无报错；随后三次 `glfwJoystickPresent(0)` 各回调一次相同的 `65544`，均返回 `false`。[原始输出](../build/neo1211-packfix/input-probe/result.log)。[GLFW 3.4 官方实现](https://github.com/glfw/glfw/blob/3.4/src/win32_joystick.c) 在 `_glfwInitJoysticksWin32` 的 `DirectInput8Create` 失败分支发出该原文。没有测得底层 HRESULT，不能再归因为某个驱动、设备、权限或具体桌面会话。
- **为何反复出现：** 原包 MidnightControls 1.10.0.1 的 `Controller.isConnected()` 调用 `glfwJoystickPresent`；`isGamepad()` 也先调用 `isConnected()`。客户端 tick 的自动识别逻辑会查询控制器，`MidnightControlsConfig.getController()` 还可重复探测候选设备。这个调用路径和独立探针足以解释持续刷屏，但日志没有逐次调用栈，不能断言所有行仅来自此模组。没有手柄本身也不能解释接口创建报错。
- **处置边界：** 包的 `controlsMode` 已为 `DEFAULT`，`autoSwitchMode=true`。关闭自动切换会改变手柄接入行为，且不证明其他查询不再触发；屏蔽 `Window` 错误只会隐藏证据。均未实施。不变更宿主、权限、客户端或运行中的案例；此次对 fix-a、fix-b 和源模板均只读。

## 重复测试

复用本机既有的原包模板 `mPRwXMh4`、NeoForge `21.1.218` 运行库、Azul Java 21.0.13、原版 1.21.1 世界。仓库根目录运行以下 PowerShell 7 命令，每次指定**未使用**的副本名：

```powershell
pwsh -NoProfile -File build/neo1211-packfix/test.ps1 stock repeat-stock
pwsh -NoProfile -File build/neo1211-packfix/test.ps1 v130 repeat-v130
pwsh -NoProfile -File build/neo1211-packfix/test.ps1 v130-rrls repeat-fix
```

[测试脚本](../build/neo1211-packfix/test.ps1)拒绝提升权限。每次先核对模板副本 179 个文件与世界副本 12 个文件；退出后复核源模板、源档的路径及 SHA-256。每组均有 46 个顶层模组。游戏参数、进程结果和分流日志在对应实例目录；`token.txt` 记录普通权限令牌。没有 JVM agent、外部游戏服务器、系统修改、文件权限放宽或依赖项目改动。HTTP(S) 指向本机未监听端口以阻断更新请求，不运行代理程序。`stock-a`、`warm-a`、`menu-a` 是日志读取冲突的脚本试跑，未计入矩阵。

独立 ZIP 复现：使用同一 JDK 的 `javac` 将 [ZipProbe.java](../build/neo1211-packfix/ZipProbe.java) 编译到测试输出目录，以该目录为类路径运行 `ZipProbe <原始CIT文件路径> <测试目录下不存在的新JAR路径>`。只写新副本；无需启动游戏。
