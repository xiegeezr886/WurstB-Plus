#!/usr/bin/env python3
"""Keep the per-version snapshot branches in sync with main.

Run by `.github/workflows/sync-version-branches.yml` on every push to main,
or by hand from a normal checkout (see `--help`-ish flags at the bottom).

Each version branch is regenerated from the current main tip, filtered down to
that one MC version, and committed with TWO parents:

    -p <current branch tip>     so the branch keeps its own history
    -p <main tip>               so the branch records that main was merged in

That makes the branch a real descendant of main, so GitHub shows it as merged
into the default branch instead of permanently diverged, and the filtering is
re-applied on every sync.  A plain `git merge main` cannot replace this: it would
drag the other MC versions' project trees back into the branch.

Branch layout produced for version V:

    workspacedir/*                  README.md, CHANGELOG.md, docs/, scripts/, gradle/
    versions/V/                     Forge       (absent where Forge has no release)
    fabric/versions/V/              Fabric
    neoforge/versions/V/            NeoForge
    BRANCH.md                       note explaining the above

plus, for 1.20.1 only, the three 1.20.1 root projects src/, fabric/, neoforge/.
"""
import os
import subprocess
import sys
import tempfile

CHUNK = 30                      # index entries per git invocation (Windows arg limit)
MAIN = "main"

# branch -> project roots that branch keeps
BRANCHES = {
    "1.20.1":  ["fabric", "neoforge", "src"],
    "1.20.2":  ["versions/1.20.2", "fabric/versions/1.20.2", "neoforge/versions/1.20.2"],
    "1.20.3":  ["versions/1.20.3", "fabric/versions/1.20.3", "neoforge/versions/1.20.3"],
    "1.20.4":  ["versions/1.20.4", "fabric/versions/1.20.4", "neoforge/versions/1.20.4"],
    "1.20.5":  ["fabric/versions/1.20.5", "neoforge/versions/1.20.5"],
    "1.20.6":  ["versions/1.20.6", "fabric/versions/1.20.6", "neoforge/versions/1.20.6"],
    "1.21":    ["versions/1.21", "fabric/versions/1.21", "neoforge/versions/1.21"],
    "1.21.1":  ["versions/1.21.1", "fabric/versions/1.21.1", "neoforge/versions/1.21.1"],
    "1.21.2":  ["fabric/versions/1.21.2", "neoforge/versions/1.21.2"],
    "1.21.3":  ["versions/1.21.3", "fabric/versions/1.21.3", "neoforge/versions/1.21.3"],
    "1.21.4":  ["versions/1.21.4", "fabric/versions/1.21.4", "neoforge/versions/1.21.4"],
    "1.21.5":  ["versions/1.21.5", "fabric/versions/1.21.5", "neoforge/versions/1.21.5"],
    "1.21.6":  ["versions/1.21.6", "fabric/versions/1.21.6", "neoforge/versions/1.21.6"],
    "1.21.7":  ["versions/1.21.7", "fabric/versions/1.21.7", "neoforge/versions/1.21.7"],
    "1.21.8":  ["versions/1.21.8", "fabric/versions/1.21.8", "neoforge/versions/1.21.8"],
    "1.21.9":  ["versions/1.21.9", "fabric/versions/1.21.9", "neoforge/versions/1.21.9"],
    "1.21.10": ["versions/1.21.10", "fabric/versions/1.21.10", "neoforge/versions/1.21.10"],
    "1.21.11": ["versions/1.21.11", "fabric/versions/1.21.11", "neoforge/versions/1.21.11"],
    "26.1":    ["versions/26.1", "fabric/versions/26.1", "neoforge/versions/26.1"],
    "26.1.1":  ["versions/26.1.1", "fabric/versions/26.1.1", "neoforge/versions/26.1.1"],
    "26.1.2":  ["versions/26.1.2", "fabric/versions/26.1.2", "neoforge/versions/26.1.2"],
    "26.2":    ["versions/26.2", "fabric/versions/26.2", "neoforge/versions/26.2"],
}

LOADER_OF = {"versions": "Forge", "fabric": "Fabric", "neoforge": "NeoForge"}
NO_FORGE = {
    "1.20.5": "Forge 1.20.5 没有官方版本，本分支只有 Fabric 与 NeoForge。",
    "1.21.2": "Forge 1.21.2 没有官方版本，本分支只有 Fabric 与 NeoForge。",
}

BRANCH_NOTE = """# {v} 分支

本分支是 **MC {v}** 的源码快照，由 `main` 的 `{base}` **自动同步**而来，只保留该版本的工程目录：

{projects}

其余 MC 版本已从本分支移除，以便单独浏览 / 对比该版本的移植结果。

> 工作区级的 `README.md`、`PROJECT_INDEX.md`、`docs/`、`scripts/`、`gradle/`、
> `build.gradle`、`settings.gradle` 等仍保留，因此其中的工程列表与脚本工程表会引用
> 本分支上不存在的目录——这是刻意的快照裁剪，不是错误。完整工作区见 `main`。
>
> 同步方式：`main` 每次更新后，由 `.github/workflows/sync-version-branches.yml`
> 重新裁剪并生成一个**双父提交**（`-p 本分支上一提交 -p main`）。
> 因此本分支始终包含 `main` 的历史，在 GitHub 上显示为已合并而不是永久分叉。
> 不能用普通 `git merge main` 代替：那会把其他 MC 版本的工程目录带回来。
{extra}"""


def repo_root():
    env = os.environ.get("GITHUB_WORKSPACE")
    if env and os.path.isdir(os.path.join(env, ".git")):
        return env
    return subprocess.run(["git", "rev-parse", "--show-toplevel"],
                          capture_output=True, text=True,
                          check=True).stdout.strip()


REPO = repo_root()


def run(args, env=None, inp=None, check=True):
    r = subprocess.run(["git", *args], cwd=REPO, env=env, input=inp,
                       capture_output=True, text=True, encoding="utf-8",
                       errors="replace")
    if check and r.returncode != 0:
        print("GIT FAILED:", " ".join(args)[:200], file=sys.stderr)
        print(r.stdout[:800], r.stderr[:800], file=sys.stderr)
        raise SystemExit(1)
    return r.stdout


def ensure_identity():
    """`git commit-tree` refuses to run without a committer identity, and a bare
    CI runner has none.  Fill in a bot identity when the repo has no user.name /
    user.email configured, unless GIT_AUTHOR_* already supplies one."""
    if os.environ.get("GIT_AUTHOR_NAME") and os.environ.get("GIT_AUTHOR_EMAIL"):
        return
    for key in ("user.name", "user.email"):
        if subprocess.run(["git", "config", "--get", key], cwd=REPO,
                          capture_output=True).returncode == 0:
            continue
        default = ("github-actions[bot]" if key == "user.name"
                   else "41898282+github-actions[bot]@users.noreply.github.com")
        subprocess.run(["git", "config", key, default], cwd=REPO, check=True)
        print(f"set {key} = {default} (no identity configured)")


def ok(args, env=None):
    return subprocess.run(["git", *args], cwd=REPO, env=env,
                          capture_output=True, text=True).returncode == 0


def add_entries(chunk, env):
    """Add one batch of index entries, halving the batch if the OS rejects the
    command line as too long (Windows caps it around 32k characters)."""
    args = ["update-index", "--add"]
    for mode, sha, path in chunk:
        args += ["--cacheinfo", f"{mode},{sha},{path}"]
    try:
        run(args, env=env)
        return
    except FileNotFoundError:
        pass
    if len(chunk) == 1:
        raise SystemExit(f"single entry still too long: {chunk[0][2]}")
    half = len(chunk) // 2
    add_entries(chunk[:half], env)
    add_entries(chunk[half:], env)


def branch_keeps(path, roots, v):
    """True when `path` belongs on the `v` branch.

    `roots` holds either an explicit project directory such as
    "fabric/versions/1.21.5", or one of the bare 1.20.1 root projects
    "src" / "fabric" / "neoforge" (which only the 1.20.1 branch keeps).
    """
    parts = path.split("/")
    for r in roots:
        if "/" in r:
            if path == r or path.startswith(r + "/"):
                return True
        elif parts[0] == r and (len(parts) < 2 or parts[1] != "versions"):
            return True          # a bare 1.20.1 root project
    if parts[0] == "versions":
        return len(parts) > 1 and parts[1] == v
    if parts[0] == "src":
        return False
    if parts[0] in ("fabric", "neoforge") and len(parts) > 1 and parts[1] == "versions":
        return len(parts) > 2 and parts[2] == v
    if parts[0] in ("fabric", "neoforge"):
        return False             # another loader's 1.20.1 root project
    return True                  # workspace-level file


def label(root):
    if root == "src":
        return "`src/` —— 根目录 Forge 1.20.1 工程（v1.6.0）"
    if "/" not in root:
        return f"`{root}/` —— {LOADER_OF[root]} 1.20.1 根工程"
    return f"`{root}/` —— {LOADER_OF[root.split('/', 1)[0]]}"


def main():
    args = sys.argv[1:]
    dry = "--dry-run" in args
    push = "--no-push" not in args
    wanted = [a for a in args if not a.startswith("--")]

    if wanted:
        unknown = [v for v in wanted if v not in BRANCHES]
        if unknown:
            raise SystemExit(f"unknown branch(es): {', '.join(unknown)}")
        todo = wanted
    else:
        todo = list(BRANCHES)

    ensure_identity()

    if not ok(["rev-parse", "--verify", "--quiet", f"refs/remotes/origin/{MAIN}"]):
        run(["fetch", "origin", MAIN], check=False)
    main_sha = run(["rev-parse", f"refs/remotes/origin/{MAIN}"]).strip()
    print(f"main tip: {main_sha[:8]}")

    entries = []  # (mode, sha, path) of the current main tree
    for line in run(["ls-tree", "-r", main_sha]).splitlines():
        if not line:
            continue
        meta, path = line.split("\t", 1)
        mode, _typ, sha = meta.split()
        entries.append((mode, sha, path))
    print(f"main tracks {len(entries)} paths")

    updated, unchanged, problems = [], [], []
    leases = {}

    for v in todo:
        roots = BRANCHES[v]
        missing = [r for r in roots
                   if not any(p == r or p.startswith(r + "/") for _, _, p in entries)]
        if missing:
            problems.append(f"{v}: main has no {', '.join(missing)}")
            print(f"{v:8s} SKIP  main no longer has {', '.join(missing)}")
            continue

        keep = [(m, s, p) for (m, s, p) in entries
                if p != "BRANCH.md" and branch_keeps(p, roots, v)]

        fh = tempfile.NamedTemporaryFile("w", suffix=".index", delete=False)
        fh.close()
        index_path = fh.name
        env = dict(os.environ)
        env["GIT_INDEX_FILE"] = index_path
        try:
            run(["read-tree", "--empty"], env=env)
            for i in range(0, len(keep), CHUNK):
                add_entries(keep[i:i + CHUNK], env)
            note = BRANCH_NOTE.format(
                v=v, base=main_sha[:8],
                projects="\n".join(f"- {label(r)}" for r in roots),
                extra=(f">\n> **注意**：{NO_FORGE[v]}\n" if v in NO_FORGE else ""))
            note_sha = run(["hash-object", "-w", "--stdin"], env=env,
                           inp=note).strip()
            run(["update-index", "--add", "--cacheinfo",
                 f"100644,{note_sha},BRANCH.md"], env=env)
            tree = run(["write-tree"], env=env).strip()
        finally:
            try:
                os.unlink(index_path)
            except OSError:
                pass

        # The previous tip has to come from the remote-tracking ref: on a fresh CI
        # checkout there is no local refs/heads/<version>, and falling back to
        # "no parent" would silently drop the merge link to the old tip.
        old = None
        for ref in (f"refs/heads/{v}", f"refs/remotes/origin/{v}"):
            if ok(["rev-parse", "--verify", "--quiet", ref]):
                old = run(["rev-parse", ref]).strip()
                break

        if old and run(["rev-parse", f"{old}^{{tree}}"]).strip() == tree:
            print(f"{v:8s} up to date (tree {tree[:8]})")
            unchanged.append(v)
            continue

        parents = ["-p", old, "-p", main_sha] if old else ["-p", main_sha]
        msg = (f"{v}: sync with main {main_sha[:8]}\n\n"
               f"Regenerated from main @ {main_sha[:8]} keeping only "
               f"{', '.join(roots)}.\n"
               f"Merge parents: previous {v} tip"
               + (f" {old[:8]}" if old else " (none)")
               + f" and main {main_sha[:8]}.\n")
        commit = run(["commit-tree", tree, *parents, "-m", msg]).strip()

        if dry:
            print(f"{v:8s} DRY   would move {old[:8] if old else '-'} -> "
                  f"{commit[:8]} files={len(keep) + 1}")
            continue

        run(["update-ref", f"refs/heads/{v}", commit])
        n = len(run(["ls-tree", "-r", "--name-only", tree]).splitlines())
        print(f"{v:8s} synced {old[:8] if old else '(new)'} -> {commit[:8]} "
              f"files={n}")
        updated.append(v)
        leases[v] = old

    print(f"\nupdated={len(updated)} unchanged={len(unchanged)} "
          f"problems={len(problems)}")

    if dry or not push or not updated:
        if not push or dry:
            print("(not pushed)")
        return 0

    # Lease explicitly against the tip we built on: on a CI checkout there is no
    # local refs/remotes/origin/<branch> to fall back on, and a bare
    # --force-with-lease would then be rejected as "stale info".
    print(f"pushing {len(updated)} branch(es) ...")
    cmd = ["git", "push", "origin"]
    for v in updated:
        cmd.append(f"--force-with-lease=refs/heads/{v}:{leases[v] or ''}")
    cmd += [f"refs/heads/{v}:refs/heads/{v}" for v in updated]
    r = subprocess.run(cmd, cwd=REPO, capture_output=True, text=True,
                       encoding="utf-8", errors="replace")
    for line in (r.stdout + r.stderr).splitlines():
        if "->" in line or "rejected" in line or "error" in line.lower():
            print("  " + line.strip())
    print(f"push exit={r.returncode}")
    if r.returncode != 0:
        return 1
    print(f"done: updated={len(updated)} unchanged={len(unchanged)} "
          f"problems={len(problems)}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
