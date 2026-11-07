param([Parameter(Mandatory=$true)][string] $ProjectDir)

$ErrorActionPreference = 'Stop'
Set-Location 'C:\Users\ui863\Documents\trae_projects\VAP'

# 回退共享源码挂载（选项 a）：把 common/ 的副本还回该工程，并摘掉 sourceSets 那行。
# 判据同安装时：逐个 SHA-256 比对，不一致就跳过不还，绝不覆盖可疑内容。

$proj = $ProjectDir.TrimEnd('/')
Write-Host "  回退 $proj"

# 1) 摘掉 build.gradle 里那段 sourceSets 配置
$bg = Join-Path $proj 'build.gradle'
$t = [System.IO.File]::ReadAllText($bg, [System.Text.Encoding]::UTF8)
$marker = 'common/src/main/java'
if ($t.Contains($marker)) {
    # 找到那段注释块的起点（我安装时加的固定开头）并整段删除
    $start = $t.IndexOf("`r`n`r`n// ---------------------------------------------------------------------------`r`n// 共享源码")
    if ($start -lt 0) { $start = $t.IndexOf("`n`n// ---------------------------------------------------------------------------`n// 共享源码") }
    if ($start -ge 0) {
        $end = $t.IndexOf('sourceSets.main.java.srcDir', $start)
        $eol = $t.IndexOf("`n", $end)
        if ($eol -lt 0) { $eol = $t.Length }
        $t = $t.Remove($start, $eol - $start)
        [System.IO.File]::WriteAllText($bg, $t, (New-Object System.Text.UTF8Encoding($false)))
        Write-Host "  已摘除 build.gradle 的共享源目录配置"
    } else {
        Write-Host "  ** 找到关键字但定位不到注释块起点，需人工处理" -ForegroundColor Red
    }
} else { Write-Host "  build.gradle 无共享目录配置，跳过" }

# 2) 还文件
$py = 'C:\Users\ui863\.dsh\dsh-runtimes\dsh-primary-runtime\dependencies\python\python.exe'
$env:PYTHONIOENCODING = 'utf-8'
$env:PROJ_DIR = $proj
$restore = @'
import json, os, hashlib
proj = os.environ["PROJ_DIR"]
lst = json.load(open(os.path.join(os.environ["TEMP"], "safe-shared.json"), encoding="utf-8"))
back = skipped = 0
for rel in lst:
    src = os.path.join("common/src/main/java", rel.replace("/", os.sep))
    dst = os.path.join(proj, "src/main/java", rel.replace("/", os.sep))
    if not os.path.exists(src): print("  ** 共享副本缺失:", rel); skipped += 1; continue
    if os.path.exists(dst):
        if hashlib.sha256(open(src,"rb").read()).digest() == hashlib.sha256(open(dst,"rb").read()).digest():
            skipped += 1; continue
        print("  ** 目标已存在且内容不同，不覆盖:", rel); skipped += 1; continue
    os.makedirs(os.path.dirname(dst), exist_ok=True)
    open(dst, "wb").write(open(src, "rb").read())
    back += 1
print(f"  已还原 {back} 个文件，跳过 {skipped} 个")
'@
[System.IO.File]::WriteAllText((Join-Path $env:TEMP 'restore_proj.py'), $restore, (New-Object System.Text.UTF8Encoding($false)))
& $py (Join-Path $env:TEMP 'restore_proj.py')
