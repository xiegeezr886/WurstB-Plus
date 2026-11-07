$ErrorActionPreference = 'Stop'
[Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12

Add-Type @"
using System;
using System.Runtime.InteropServices;
public class CredNative2 {
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

function Get-GitHubToken {
    foreach ($target in @('git:https://github.com', 'LegacyGeneric:target=git:https://github.com')) {
        $ptr = [IntPtr]::Zero
        if (-not [CredNative2]::CredRead($target, 1, 0, [ref]$ptr)) { continue }
        try {
            $cred = [Runtime.InteropServices.Marshal]::PtrToStructure($ptr, [type][CredNative2+CREDENTIAL])
            $bytes = New-Object byte[] $cred.CredentialBlobSize
            [Runtime.InteropServices.Marshal]::Copy($cred.CredentialBlob, $bytes, 0, $bytes.Length)
            $utf16 = [Text.Encoding]::Unicode.GetString($bytes).TrimEnd([char]0)
            $utf8 = [Text.Encoding]::UTF8.GetString($bytes).TrimEnd([char]0)
            foreach ($candidate in @($utf16, $utf8)) {
                if ($candidate -match '^(gh[pousr]_[A-Za-z0-9_]+|github_pat_[A-Za-z0-9_]+)$') {
                    Write-Host ('credential: user=' + $cred.UserName + ' tokenPrefix=' + $candidate.Substring(0, 4))
                    return $candidate
                }
            }
            throw ('Credential at ' + $target + ' did not look like a GitHub token')
        } finally {
            [CredNative2]::CredFree($ptr) | Out-Null
        }
    }
    throw 'No GitHub credential found'
}

$token = Get-GitHubToken
$owner = 'xiegeezr886'
$repo = 'WurstB-Plus'
$branch = 'main'
$api = 'https://api.github.com/repos/' + $owner + '/' + $repo
$headers = @{
    Authorization          = 'Bearer ' + $token
    'User-Agent'           = 'WurstB-Plus-cleanup'
    Accept                 = 'application/vnd.github+json'
    'X-GitHub-Api-Version' = '2022-11-28'
}

$fileUri = $api + '/contents/.gitignore?ref=' + $branch
$file = Invoke-RestMethod -Uri $fileUri -Headers $headers -UseBasicParsing
$content = [Text.Encoding]::UTF8.GetString([Convert]::FromBase64String(($file.content -replace '\s', '')))
if ($content -match '(?m)^/GenshinFontPack/$') {
    Write-Output '.gitignore already contains /GenshinFontPack/'
    exit 0
}

$newContent = $content.TrimEnd() + "`n`n# Unrelated local asset pack (not part of the client build)`n/GenshinFontPack/`n"
$base64 = [Convert]::ToBase64String([Text.Encoding]::UTF8.GetBytes($newContent))
$body = ConvertTo-Json -InputObject @{
    message = 'chore: ignore GenshinFontPack'
    content = $base64
    sha     = $file.sha
    branch  = $branch
} -Compress
$bodyBytes = [Text.Encoding]::UTF8.GetBytes($body)
$put = Invoke-RestMethod -Method Put -Uri ($api + '/contents/.gitignore') -Headers $headers -Body $bodyBytes -ContentType 'application/json; charset=utf-8' -UseBasicParsing
Write-Output ('new commit: ' + $put.commit.sha)

$verify = Invoke-RestMethod -Uri $fileUri -Headers $headers -UseBasicParsing
$verifyContent = [Text.Encoding]::UTF8.GetString([Convert]::FromBase64String(($verify.content -replace '\s', '')))
Write-Output ('rule present: ' + ($verifyContent -match '(?m)^/GenshinFontPack/$'))
