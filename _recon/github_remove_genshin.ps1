param(
    [switch]$Apply
)

$ErrorActionPreference = 'Stop'
[Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12

Add-Type @"
using System;
using System.Runtime.InteropServices;
public class CredNative {
    [StructLayout(LayoutKind.Sequential, CharSet = CharSet.Unicode)]
    public struct CREDENTIAL {
        public uint Flags;
        public uint Type;
        [MarshalAs(UnmanagedType.LPWStr)] public string TargetName;
        [MarshalAs(UnmanagedType.LPWStr)] public string Comment;
        public System.Runtime.InteropServices.ComTypes.FILETIME LastWritten;
        public uint CredentialBlobSize;
        public IntPtr CredentialBlob;
        public uint Persist;
        public uint AttributeCount;
        public IntPtr Attributes;
        [MarshalAs(UnmanagedType.LPWStr)] public string TargetAlias;
        [MarshalAs(UnmanagedType.LPWStr)] public string UserName;
    }

    [DllImport("advapi32.dll", CharSet = CharSet.Unicode, SetLastError = true)]
    public static extern bool CredRead(string target, uint type, uint flags, out IntPtr credential);

    [DllImport("advapi32.dll", SetLastError = true)]
    public static extern void CredFree(IntPtr credential);
}
"@

function Read-GitHubToken {
    $targets = @('git:https://github.com', 'LegacyGeneric:target=git:https://github.com')
    foreach ($target in $targets) {
        $ptr = [IntPtr]::Zero
        if (-not [CredNative]::CredRead($target, 1, 0, [ref]$ptr)) {
            continue
        }
        try {
            $cred = [Runtime.InteropServices.Marshal]::PtrToStructure($ptr, [type][CredNative+CREDENTIAL])
            $bytes = New-Object byte[] $cred.CredentialBlobSize
            [Runtime.InteropServices.Marshal]::Copy($cred.CredentialBlob, $bytes, 0, $bytes.Length)

            $utf16 = [Text.Encoding]::Unicode.GetString($bytes).TrimEnd([char]0)
            $utf8 = [Text.Encoding]::UTF8.GetString($bytes).TrimEnd([char]0)

            foreach ($candidate in @($utf16, $utf8)) {
                if ($candidate -match '^(gh[pousr]_[A-Za-z0-9_]+|github_pat_[A-Za-z0-9_]+)$') {
                    Write-Host ('credential: target=' + $target + ' user=' + $cred.UserName + ' tokenPrefix=' + $candidate.Substring(0, 4) + ' tokenLength=' + $candidate.Length)
                    return $candidate
                }
            }
            throw ('Credential at ' + $target + ' did not look like a GitHub token')
        } finally {
            [CredNative]::CredFree($ptr) | Out-Null
        }
    }
    throw 'No GitHub credential found in Windows Credential Manager'
}

$token = Read-GitHubToken
$owner = 'xiegeezr886'
$repo = 'WurstB-Plus'
$branch = 'main'
$api = 'https://api.github.com/repos/' + $owner + '/' + $repo
$headers = @{
    Authorization          = 'Bearer ' + $token
    'User-Agent'           = 'WurstB-Plus-xray-cleanup'
    Accept                 = 'application/vnd.github+json'
    'X-GitHub-Api-Version' = '2022-11-28'
}

$repoInfo = Invoke-RestMethod -Uri $api -Headers $headers -UseBasicParsing
Write-Output ('repo: ' + $repoInfo.full_name + '  push=' + $repoInfo.permissions.push)

$refUri = $api + '/git/ref/heads/' + $branch
$ref = Invoke-RestMethod -Uri $refUri -Headers $headers -UseBasicParsing
$commitUri = $api + '/git/commits/' + $ref.object.sha
$commit = Invoke-RestMethod -Uri $commitUri -Headers $headers -UseBasicParsing
Write-Output ('head: ' + $ref.object.sha)
Write-Output ('tree: ' + $commit.tree.sha)

$treeUri = $api + '/git/trees/' + $commit.tree.sha + '?recursive=1'
$tree = Invoke-RestMethod -Uri $treeUri -Headers $headers -UseBasicParsing
$entries = @($tree.tree | Where-Object { $_.path -like 'GenshinFontPack/*' })
if ($entries.Count -eq 0) {
    Write-Output 'GenshinFontPack already absent from branch tree'
    exit 0
}
foreach ($entry in $entries) {
    Write-Output ('entry: ' + $entry.path + '  ' + $entry.type + '  ' + $entry.sha)
}

if (-not $Apply) {
    Write-Output 'dry run only; pass -Apply to create the removal commit'
    exit 0
}

$deletions = @()
foreach ($entry in $entries) {
    $deletions += @{
        path = $entry.path
        mode = $entry.mode
        type = $entry.type
        sha  = $null
    }
}

$treeBody = ConvertTo-Json -InputObject @{
    base_tree = $commit.tree.sha
    tree      = $deletions
} -Depth 10 -Compress
$treeBytes = [Text.Encoding]::UTF8.GetBytes($treeBody)
$newTreeUri = $api + '/git/trees'
$newTree = Invoke-RestMethod -Method Post -Uri $newTreeUri -Headers $headers -Body $treeBytes -ContentType 'application/json; charset=utf-8' -UseBasicParsing

$message = "chore: remove mistakenly committed GenshinFontPack`n`nThis directory is not part of the client build input."
$commitBody = ConvertTo-Json -InputObject @{
    message = $message
    tree    = $newTree.sha
    parents = @($ref.object.sha)
} -Depth 10 -Compress
$commitBytes = [Text.Encoding]::UTF8.GetBytes($commitBody)
$newCommitUri = $api + '/git/commits'
$newCommit = Invoke-RestMethod -Method Post -Uri $newCommitUri -Headers $headers -Body $commitBytes -ContentType 'application/json; charset=utf-8' -UseBasicParsing

$refBody = ConvertTo-Json -InputObject @{
    sha   = $newCommit.sha
    force = $false
} -Compress
$refBytes = [Text.Encoding]::UTF8.GetBytes($refBody)
$updateUri = $api + '/git/refs/heads/' + $branch
$updated = Invoke-RestMethod -Method Patch -Uri $updateUri -Headers $headers -Body $refBytes -ContentType 'application/json; charset=utf-8' -UseBasicParsing

Write-Output ('new head: ' + $updated.object.sha)

$verifyUri = $api + '/git/trees/' + $newCommit.tree.sha + '?recursive=1'
$verifyTree = Invoke-RestMethod -Uri $verifyUri -Headers $headers -UseBasicParsing
$remaining = @($verifyTree.tree | Where-Object { $_.path -like 'GenshinFontPack/*' })
Write-Output ('remaining GenshinFontPack entries: ' + $remaining.Count)
