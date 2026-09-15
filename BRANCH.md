# 1.21.5 分支

本分支是 **MC 1.21.5** 的源码快照，从 `main` 的 `6b01154` 切出，只保留该版本的工程目录：

- `versions/1.21.5/` —— Forge
- `fabric/versions/1.21.5/` —— Fabric
- `neoforge/versions/1.21.5/` —— NeoForge

其余 MC 版本已从本分支移除，以便单独浏览 / 对比该版本的移植结果。

> 工作区级的 `README.md`、`PROJECT_INDEX.md`、`docs/`、`scripts/`、`gradle/`、
> `build.gradle`、`settings.gradle` 等仍保留，因此其中的工程列表与脚本工程表会引用
> 本分支上不存在的目录——这是刻意的快照裁剪，不是错误。完整工作区见 `main`。
>
> 本分支以 `6b01154` 为父提交，历史连续，旧内容仍可通过 `git log` 找到。
