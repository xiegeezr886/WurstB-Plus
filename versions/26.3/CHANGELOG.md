# WurstB+ Plus v1.5.0 MC26.3 更新日志

## 版本概述

Minecraft 26.3（Wilderness Bound）Forge 66.0.3 移植工程，由 `versions/26.2` 克隆而来。
功能基线仍是 **v1.5.0**，与其余版本工程一致（不含仅在根目录 Forge 1.20.1 里的 v1.6 子系统）。

## 状态：尚未发布

本工程目前只是脚手架，**没有可发布的产物**：

- 版本锚点已改写为 26.3，但尚未编译、尚未打包。
- 未做游戏内启动验证。

因此这里没有「修复与改动」清单可写——26.3 相对 26.2 的 API 差异，要等编译探针跑完
才能如实登记。26.1.2 时期的历史改动记录见 `versions/26.1.2/CHANGELOG.md`；供体
`versions/26.2/CHANGELOG.md` 曾原样继承那份文件（内容通篇是 26.1.2），本工程已改写为
上面这段如实说明。

## 系统要求

- Minecraft 26.3
- Forge 66.0.3
- Java 25
- Windows 10/11 x64

## 构建信息

- 构建系统：ForgeGradle 7.x / Gradle 9.4.1
- 映射：Mojang 官方映射
- 产物：`versions/26.3/build/libs/WurstB+ Plus-v1.5.0-Forge-26.3.jar`（尚未构建）

## 已知问题

待编译探针跑完后登记。已知需要实测确认的两处 26.3 底层改动：SDL3 取代 GLFW（输入层）、
OIT 取代 Improved Transparency（渲染管线）。详见 `PORTING_TASK.md`。
