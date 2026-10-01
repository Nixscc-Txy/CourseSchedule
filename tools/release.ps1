<#
课表发版脚本: 抬 versionCode / 改 versionName -> 跑测试和 release 构建 -> 建 GitHub Release 并挂 APK

用法:
  pwsh -File tools/release.ps1 -Version 1.0.1 -Notes "修复小组件浅色过亮"
  pwsh -File tools/release.ps1 -Version 1.1.0 -NotesFile CHANGELOG.md

前提:
  - 装好 gh (https://cli.github.com) 并已完成 gh auth login
  - keystore.properties 在位, 否则 release 构建会直接报错 (这是故意的)
  - PowerShell 5.1 / 7 均可; 脚本只改 app/build.gradle.kts 并建 Release, 不会自动 commit/push
#>
param(
    [Parameter(Mandatory = $true)][string]$Version,
    [string]$Notes = "",
    [string]$NotesFile = ""
)

$ErrorActionPreference = "Stop"
$root = Split-Path -Parent $PSScriptRoot
$gradleFile = Join-Path $root "app\build.gradle.kts"

# ---- 1) 抬 versionCode, 改 versionName ----
# 必须显式指定 UTF-8: Windows PowerShell 5.1 的 Get-Content 默认按 ANSI 读, 会把文件里的中文注释读坏
$text = [System.IO.File]::ReadAllText($gradleFile, [System.Text.Encoding]::UTF8)
if ($text -notmatch 'versionCode\s*=\s*(\d+)') { throw "在 app/build.gradle.kts 里找不到 versionCode" }
$oldCode = [int]$Matches[1]
$newCode = $oldCode + 1
$text = $text -replace 'versionCode\s*=\s*\d+', "versionCode = $newCode"
$text = $text -replace 'versionName\s*=\s*"[^"]*"', ('versionName = "{0}"' -f $Version)
# 同理写回时不带 BOM (Set-Content -Encoding utf8 在 5.1 下会加 BOM)
[System.IO.File]::WriteAllText($gradleFile, $text, (New-Object System.Text.UTF8Encoding($false)))
Write-Host "versionCode: $oldCode -> $newCode    versionName -> $Version"

# ---- 2) 测试 + release 构建 ----
Push-Location $root
try {
    & .\gradlew.bat clean test assembleRelease
    if ($LASTEXITCODE -ne 0) { throw "构建失败, 已中止发版" }
} finally {
    Pop-Location
}

$apk = Join-Path $root "app\build\outputs\apk\release\app-release.apk"
if (-not (Test-Path $apk)) { throw "没找到 release 包: $apk" }

# ---- 3) 建 Release 并挂 APK ----
$tag = "v$Version"
$ghArgs = @("release", "create", $tag, $apk, "--title", "课表 $tag")
if ($NotesFile) { $ghArgs += @("--notes-file", $NotesFile) }
elseif ($Notes) { $ghArgs += @("--notes", $Notes) }
else            { $ghArgs += @("--generate-notes") }

& gh @ghArgs
if ($LASTEXITCODE -ne 0) { throw "gh release create 失败" }

Write-Host ""
Write-Host "已发布 $tag"
Write-Host "别忘了提交版本号:"
Write-Host "  git add app/build.gradle.kts"
Write-Host ("  git commit -m '发版 {0}'" -f $tag)
Write-Host "  git push"