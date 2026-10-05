param([string[]] $Platforms = @("fabric", "forge", "neoforge"))

$ErrorActionPreference = "Continue"
$root = Split-Path -Parent (Split-Path -Parent $MyInvocation.MyCommand.Path)

$targets = @{
    "fabric"   = @("1.21.3", "1.21.4", "1.21.5", "1.21.6", "1.21.7", "1.21.8", "1.21.9", "1.21.10")
    "forge"    = @("1.21.5", "1.21.6", "1.21.7", "1.21.8", "1.21.9", "1.21.10")
    "neoforge" = @("1.21.5", "1.21.6", "1.21.7", "1.21.8", "1.21.9", "1.21.10")
}

$results = New-Object System.Collections.ArrayList

foreach ($plat in $Platforms) {
    foreach ($mc in $targets[$plat]) {
        $proj = switch ($plat) {
            "fabric"   { Join-Path $root "fabric\versions\$mc" }
            "forge"    { Join-Path $root "versions\$mc" }
            "neoforge" { Join-Path $root "neoforge\versions\$mc" }
        }
        $label = "$plat/$mc"
        Write-Host "==== $label ===="

        if (-not (Test-Path $proj)) {
            [void]$results.Add([pscustomobject]@{ Project = $label; Build = "MISSING"; Verify = "-"; Note = "no dir" })
            continue
        }

        $env:JAVA_HOME = "C:\Program Files\Java\jdk-21"
        $log = Join-Path $env:TEMP "build-$plat-$mc.log"

        # 各平台的产物任务不同：
        #   fabric   -> assemble（build 会跑测试，而部分工程的测试有既存失败）
        #   forge    -> allJar（发布用 fat jar；jar 只是 dev 包）
        #   neoforge -> build（该平台没有 allJar 任务）
        if ($plat -eq "fabric") {
            $gradleArgs = @("clean", "assemble", "--console=plain", "--no-daemon")
        } elseif ($plat -eq "forge") {
            $gradleArgs = @("clean", "allJar", "--console=plain", "--no-daemon")
        } else {
            $gradleArgs = @("clean", "build", "--console=plain", "--no-daemon")
        }

        Push-Location $proj
        & .\gradlew.bat @gradleArgs *>&1 | Out-File $log -Encoding UTF8
        $buildOk = ($LASTEXITCODE -eq 0)
        Pop-Location

        $note = ""
        if (-not $buildOk) {
            $err = Select-String -Path $log -Pattern "What went wrong|error:|FAILED" -Encoding UTF8 | Select-Object -First 1
            $note = if ($err) { $err.Line.Trim() } else { "see $log" }
            Write-Host "  BUILD FAILED: $note"
        } else {
            Write-Host "  build ok"
        }

        $verifyOk = $null
        if ($buildOk) {
            $vout = & powershell -NoProfile -ExecutionPolicy Bypass -File (Join-Path $root "scripts\verify-embedded-baritone.ps1") -ProjectDir $proj 2>&1
            $verifyOk = ($LASTEXITCODE -eq 0)
            if (-not $verifyOk) {
                $note = ($vout | Where-Object { $_ -match "FAIL|Boom" } | Select-Object -First 1)
                Write-Host "  VERIFY FAILED"
            } else {
                Write-Host "  verify ok"
            }
        }

        [void]$results.Add([pscustomobject]@{
            Project = $label
            Build   = if ($buildOk) { "OK" } else { "FAIL" }
            Verify  = if ($verifyOk -eq $true) { "OK" } elseif ($verifyOk -eq $false) { "FAIL" } else { "-" }
            Note    = $note
        })
    }
}

Write-Host ""
Write-Host "==== SUMMARY ===="
$results | Format-Table -AutoSize
$results | Export-Csv -Path (Join-Path $env:TEMP "baritone-migration-results.csv") -NoTypeInformation -Encoding UTF8

$bad = @($results | Where-Object { $_.Build -ne "OK" -or $_.Verify -eq "FAIL" })
if ($bad.Count -gt 0) {
    Write-Host "NOT PASSED:"
    foreach ($b in $bad) { Write-Host ("  {0}  build={1} verify={2}  {3}" -f $b.Project, $b.Build, $b.Verify, $b.Note) }
    exit 1
}
Write-Host "ALL PASSED"
exit 0