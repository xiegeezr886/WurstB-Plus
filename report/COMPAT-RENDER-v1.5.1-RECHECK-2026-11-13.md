# v1.5.1 兼容性与渲染复查

日期：2026-11-13。按用户补充要求，最终验证范围为本仓库的 **66 个 v1.5.1 工程**：Fabric 23、NeoForge 23、Forge 20。根目录 v1.6 不纳入结论，也未修改其源代码。仓库外独立 Forge 1.20.1 修复包不在本次范围内。

## 结论与修改

本次执行的静态检查、渲染回归和抽样归档检查均通过，未发现新的 v1.5.1 渲染回归。发现并修正中英文 README 支持矩阵中的 Java 要求：Minecraft 1.20.5、1.20.6 应为 **Java 21**，原文误写为 17。对应工程的构建配置及 Fabric 元数据本身正确，无需修改生产代码。

## 本次实际执行

| 检查 | 结果 | 边界 |
| --- | --- | --- |
| `audit-render-regressions.py` | 66 工程通过；33 个直接渲染、33 个延迟提交 | 检查状态恢复、初始化失败清理、快照回收、Mixin 注册等已知不变量 |
| `unittest discover -s scripts/tests -p test_*regressions.py` | 24 项通过 | 检查审计规则及负例，不是完整游戏测试 |
| `run-render-matrix-tests.py --java-home <JDK21>` | 7 组实际重新执行，408 项断言全部通过 | 使用桌面 OpenGL，Minecraft GPU/GUI 边界是测试替身 |
| 工具链与元数据逐工程检查 | 66 工程通过 | Java 构建级别、Fabric Java 依赖、已注册 Mixin 源文件存在性、FancyMenu 标题页兼容判断 |
| 既有 JAR 抽样检查 | 5 个通过 | ZIP CRC、加载器元数据、Mixin 配置、类文件、Baritone 及归档回归规则；本次未重新构建 |

最初启动的全仓崩溃扫描包含 v1.6，随后终止，不将未完成扫描计为通过。

OpenGL 实测环境为 Windows、Azul JDK 21.0.13、Intel RaptorLake-S Mobile Graphics Controller，驱动返回 OpenGL 4.6.0 / 32.0.101.7084。

| 渲染样本 | LWJGL | 断言 |
| --- | --- | ---: |
| Fabric 1.20.1 | 3.3.3 | 39 |
| NeoForge 1.20.1 | 3.3.1 | 39 |
| Fabric 1.21.6 | 3.3.3 | 66 |
| Fabric 1.21.11 | 3.3.3 | 66 |
| NeoForge 26.1 | 3.4.1 | 66 |
| NeoForge 26.2 | 3.4.1 | 66 |
| Fabric 26.3 | 3.3.3 | 66 |

每组混合文字绘制读回 1151 个非透明像素；五组延迟渲染样本均正确读回十二份独立纹理快照。覆盖窗口尺寸变化、状态复用、重复 reset、资源关闭、PBO/行布局、模板掩码、sampler、Minecraft 纹理缓存同步及 GL 错误检查。

矩阵脚本按当前生产源码哈希确认覆盖 66 份 GlStateGuard 的 2 种变体、66 份 NanoVGContext 的 6 种变体、33 份 NanoVgBlitter 的 5 种变体。仅规范化 GlStateManager 导入包名，不改写方法体或 API 签名。

抽样 JAR 来自以下既有构建目录：Fabric 1.20.4、Fabric 1.21.11、NeoForge 1.21.1、NeoForge 26.2、NeoForge 26.3。检查通过不代表这些产物在本次重新编译或游戏启动过。

## 未覆盖与已知限制

- 没有启动 Minecraft，因此未验收真实 Mixin 织入、连续帧画面、光影、Sodium/Iris 共存或完整整合包世界内操作。
- 没有在本次重新构建全部 66 个工程，也未验证所有发布 JAR 与当前源码一致。
- 26.3 诊断使用 LWJGL 3.3.3，生产依赖 3.4.3 的独立运行仍未覆盖。
- NeoForge 1.20.1 的 LWJGL 3.3.1 在诊断用 JDK 21 下打印 Unsupported JNI version 警告，但 39 项断言及进程退出正常；该工程的正常 Java 要求为 17。
- 本次未测试 Vulkan、GLES/Android、Linux/macOS 或其它显卡。此前文档中的验证缺口仍保留。

## 证据

- `build/render263-tests/matrix-result.json`：本次渲染汇总。
- `build/render263-tests/<工程路径以连字符连接>/result.txt` 与 `inputs.json`：逐组日志、生产源码 SHA-256、退出码。
- `build/compatibility-recheck-2026-11-13.json`：66 工程 Java 核对与 5 个既有 JAR 检查结果。

本次没有提交、推送、安装或发布产物。
