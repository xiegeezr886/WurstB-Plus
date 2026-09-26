# 1.21.11 分支

本分支是 **MC 1.21.11** 的源码快照，由 `main` 的 `e8f26023` **自动同步**而来，只保留该版本的工程目录：

- `versions/1.21.11/` —— Forge
- `fabric/versions/1.21.11/` —— Fabric
- `neoforge/versions/1.21.11/` —— NeoForge

其余 MC 版本已从本分支移除，以便单独浏览 / 对比该版本的移植结果。

> 工作区级的 `README.md`、`PROJECT_INDEX.md`、`docs/`、`scripts/`、`gradle/`、
> `build.gradle`、`settings.gradle` 等仍保留，因此其中的工程列表与脚本工程表会引用
> 本分支上不存在的目录——这是刻意的快照裁剪，不是错误。完整工作区见 `main`。
>
> 同步方式：`main` 每次更新后，由 `.github/workflows/sync-version-branches.yml`
> 重新裁剪并生成一个**双父提交**（`-p 本分支上一提交 -p main`）。
> 因此本分支始终包含 `main` 的历史，在 GitHub 上显示为已合并而不是永久分叉。
> 不能用普通 `git merge main` 代替：那会把其他 MC 版本的工程目录带回来。
