# Forge 配置页 API

Cloth Config 18 及更新版本没有对应的官方 Forge 包。此目录保留固定官方
NeoForge 包中完整的 `me/shedaniel/clothconfig2/`、`me/shedaniel/math/` 和资源，
供对应 Minecraft 版本的 Forge 工程使用；NeoForge 工程仍嵌套官方模组。

提取脚本为 `scripts/port/extract_v152_cloth_core.py`。脚本验证固定上游 SHA-256，
逐项解析 class 常量池，仅转换 `Dist`、`OnlyIn` 注解的加载器命名空间，
保留方法字节码和常量池索引。若配置页 API 出现其他 NeoForge 引用则拒绝生成。
不合并加载器入口与 AutoConfig；SeedCrackerX 使用的完整配置页 API 均保留。
生成包按确定顺序和时间戳写入，每版 `manifest.json` 固定输入与输出哈希。

各 Forge 工程随包保留 LGPL-3.0 文本和对应来源说明，位于
`META-INF/licenses/cloth-config-core/`。对应源码为
<https://github.com/shedaniel/cloth-config>，各版本来源及重新提取方式见脚本和清单。

输入为官方 Maven 包，例如：

```powershell
python scripts/port/extract_v152_cloth_core.py 18.0.145 path/to/cloth-config-neoforge-18.0.145-neoforge.jar
```

Forge 构建会合并 API、资源并校验哈希；使用上游要求的 Button/KeyMapping 访问转换。
Forge 1.21.5 / Cloth Config 18.0.145 已在开发客户端实际打开完整配置页并截图确认。
Forge 26.3 / Cloth Config 26.3.159 也已实际打开完整配置页，并核对结构渲染开关；
记录位于 `.dsh-tmp/v152-matrix-runtime/versions-26.3/native-ui-verification.json`。
其他版本仍须进行对应客户端实机验证，编译通过不代表完成实机验收。
