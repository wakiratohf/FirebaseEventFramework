#Requires -Version 5.0
<#
.SYNOPSIS
    Cài skill firebase-events-impl vào 1 project Android (Windows version).

.DESCRIPTION
    Tương đương install.sh trên Unix. Copy .claude\ + tạo docs\analytics\,
    validate dependencies, prompt nhập target path nếu chưa truyền vào.

.PARAMETER Target
    Đường dẫn Android project. Nếu bỏ trống, script sẽ prompt nhập tay
    (mặc định = thư mục hiện tại).

.EXAMPLE
    .\install.ps1
    # Prompt nhập target

.EXAMPLE
    .\install.ps1 C:\Users\Me\AndroidStudioProjects\MyApp
    # Không prompt — cài trực tiếp

.EXAMPLE
    .\install.ps1 -Target ~\code\android-app
    # Tilde expansion (~ = %USERPROFILE%)
#>
[CmdletBinding()]
param(
    [Parameter(Position = 0)]
    [string]$Target = ""
)

# UTF-8 output (Vietnamese characters)
$OutputEncoding = [System.Text.UTF8Encoding]::new()
try { [Console]::OutputEncoding = [System.Text.UTF8Encoding]::new() } catch { }

$ErrorActionPreference = "Stop"
$ScriptDir = Split-Path -Parent $MyInvocation.MyCommand.Definition

# ── Color helpers ─────────────────────────────────────────────────
function Write-Red    { param([string]$m) Write-Host $m -ForegroundColor Red }
function Write-Green  { param([string]$m) Write-Host $m -ForegroundColor Green }
function Write-Yellow { param([string]$m) Write-Host $m -ForegroundColor Yellow }
function Write-Blue   { param([string]$m) Write-Host $m -ForegroundColor Cyan }
function Write-Gray   { param([string]$m) Write-Host $m -ForegroundColor DarkGray }

Write-Blue "═══ analytics-automation installer (Windows) ═══"
Write-Host "Source: $ScriptDir"
Write-Host ""

# ── Resolve target ────────────────────────────────────────────────
if ([string]::IsNullOrWhiteSpace($Target)) {
    # Interactive mode
    $DefaultTarget = (Get-Location).Path
    Write-Gray "Nhập đường dẫn Android project (Enter = thư mục hiện tại)"
    Write-Gray "  Ví dụ:"
    Write-Gray "    C:\Users\Me\AndroidStudioProjects\MyApp"
    Write-Gray "    ~\code\my-android-app   (~ = $env:USERPROFILE)"
    Write-Gray "    . (= thư mục hiện tại)"
    $TargetInput = Read-Host "Target"
    if ([string]::IsNullOrWhiteSpace($TargetInput)) {
        $TargetInput = $DefaultTarget
    }
}
else {
    $TargetInput = $Target
}

# Tilde + env var expansion (Read-Host không tự expand ~)
if ($TargetInput.StartsWith("~")) {
    $TargetInput = $env:USERPROFILE + $TargetInput.Substring(1)
}
$TargetInput = [System.Environment]::ExpandEnvironmentVariables($TargetInput)

if (-not (Test-Path -LiteralPath $TargetInput -PathType Container)) {
    Write-Red "❌ Target directory không tồn tại: $TargetInput"
    Write-Yellow "   Tạo thư mục trước, hoặc nhập đúng path."
    exit 1
}
$TargetPath = (Resolve-Path -LiteralPath $TargetInput).Path
Write-Host "Target: $TargetPath"
Write-Host ""

# ── Validate skill source ─────────────────────────────────────────
$SkillSrc = Join-Path $ScriptDir ".claude\skills\firebase-events-impl"
if (-not (Test-Path $SkillSrc)) {
    Write-Red "❌ Không tìm thấy .claude\skills\firebase-events-impl trong $ScriptDir"
    Write-Red "   Script này phải chạy từ thư mục đã unzip analytics-automation\"
    exit 1
}

# ── Detect Android project ────────────────────────────────────────
$HasGradleKts  = Test-Path (Join-Path $TargetPath "settings.gradle.kts")
$HasGradle     = Test-Path (Join-Path $TargetPath "settings.gradle")
$HasAppDir     = Test-Path (Join-Path $TargetPath "app") -PathType Container
if (-not ($HasGradleKts -or $HasGradle -or $HasAppDir)) {
    Write-Yellow "⚠ Target không giống Android project (không thấy settings.gradle hoặc app\)"
    $ans = Read-Host "Vẫn tiếp tục? (y/N)"
    if ($ans -notmatch "^[Yy]") { exit 1 }
}

# ── Merge .claude/ (NEVER replace — preserve other skills/commands/docs/settings) ──
Write-Host ""
Write-Blue "═══ Merge skill vào .claude\ ═══"
Write-Gray "Chỉ copy 8 slash commands + 1 skill 'firebase-events-impl'."
Write-Gray "Mọi file/folder khác trong .claude\ (skills khác, docs, settings, CLAUDE.md) ĐƯỢC GIỮ NGUYÊN."
Write-Host ""

$CmdsDir = Join-Path $TargetPath ".claude\commands"
$SkillsDir = Join-Path $TargetPath ".claude\skills"
New-Item -ItemType Directory -Path $CmdsDir -Force | Out-Null
New-Item -ItemType Directory -Path $SkillsDir -Force | Out-Null

# Snapshot what user has BEFORE
$ExistingPreserved = @()
$ClaudeDir = Join-Path $TargetPath ".claude"
if (Test-Path $ClaudeDir) {
    Get-ChildItem $ClaudeDir -Recurse -File -Force -ErrorAction SilentlyContinue | ForEach-Object {
        $rel = $_.FullName.Substring($ClaudeDir.Length + 1).Replace("\", "/")
        # Skip parts we'll write to
        if ($rel -match "^commands/analytics-.*\.md$") { return }
        if ($rel -match "^skills/firebase-events-impl(/|$)") { return }
        $ExistingPreserved += $rel
    }
}

# 1. Copy 8 slash commands — overwrite same-name only
$CmdsAdded = 0; $CmdsUpdated = 0; $CmdsUnchanged = 0
$SrcCmdsDir = Join-Path $ScriptDir ".claude\commands"
Get-ChildItem $SrcCmdsDir -Filter "analytics-*.md" -File | ForEach-Object {
    $dst = Join-Path $CmdsDir $_.Name
    if (Test-Path $dst) {
        $srcHash = Get-FileHash $_.FullName -Algorithm SHA256
        $dstHash = Get-FileHash $dst -Algorithm SHA256
        if ($srcHash.Hash -eq $dstHash.Hash) {
            $CmdsUnchanged++
        }
        else {
            Copy-Item $_.FullName $dst -Force
            $CmdsUpdated++
            Write-Yellow "  ~ Updated commands\$($_.Name)"
        }
    }
    else {
        Copy-Item $_.FullName $dst -Force
        $CmdsAdded++
        Write-Green "  + Added commands\$($_.Name)"
    }
}

# 2. Replace our skill (we own this subdir entirely)
$SkillDst = Join-Path $SkillsDir "firebase-events-impl"
$SkillSrcPath = Join-Path $ScriptDir ".claude\skills\firebase-events-impl"
if (Test-Path $SkillDst) {
    Remove-Item $SkillDst -Recurse -Force
    Copy-Item $SkillSrcPath $SkillsDir -Recurse -Force
    Write-Yellow "  ~ Updated skills\firebase-events-impl\"
}
else {
    Copy-Item $SkillSrcPath $SkillsDir -Recurse -Force
    Write-Green "  + Added skills\firebase-events-impl\"
}

Write-Host ""
Write-Green "✓ Merge xong:"
Write-Host "    Commands:  +$CmdsAdded added, ~$CmdsUpdated updated, =$CmdsUnchanged unchanged"
Write-Host "    Skill:     firebase-events-impl\ (đầy đủ)"
if ($ExistingPreserved.Count -gt 0) {
    Write-Host "    Preserved: $($ExistingPreserved.Count) file/folder khác trong .claude\ (không touch)"
    $previewCount = [Math]::Min($ExistingPreserved.Count, 5)
    foreach ($p in $ExistingPreserved[0..($previewCount - 1)]) {
        Write-Gray "      .claude\$p"
    }
    if ($ExistingPreserved.Count -gt 5) {
        Write-Gray "      ... và $($ExistingPreserved.Count - 5) file/folder khác"
    }
}

# ── Create docs/analytics/ ────────────────────────────────────────
$DocsDir = Join-Path $TargetPath "docs\analytics"
New-Item -ItemType Directory -Path $DocsDir -Force | Out-Null
Write-Green "✓ Tạo docs\analytics\ (chỗ chứa event-spec.yaml + reports)"

# ── Update .gitignore ─────────────────────────────────────────────
$Gitignore = Join-Path $TargetPath ".gitignore"
$Entries = @(".claude-cache/")
if (Test-Path $Gitignore) {
    $Existing = Get-Content $Gitignore -Encoding UTF8
    foreach ($entry in $Entries) {
        if ($Existing -notcontains $entry) {
            Add-Content -LiteralPath $Gitignore -Value $entry -Encoding UTF8
            Write-Green "✓ Thêm $entry vào .gitignore"
        }
    }
}
else {
    $Entries | Out-File -LiteralPath $Gitignore -Encoding UTF8
    Write-Green "✓ Tạo .gitignore với .claude-cache/"
}

# ── Check Python ──────────────────────────────────────────────────
$Py = $null
foreach ($cmd in @("python3", "python", "py")) {
    $g = Get-Command $cmd -ErrorAction SilentlyContinue
    if ($g) { $Py = $cmd; break }
}
if (-not $Py) {
    Write-Red "❌ Không có Python — cài Python 3.11+ rồi chạy lại"
    Write-Yellow "   Download: https://www.python.org/downloads/"
    Write-Yellow "   Hoặc Microsoft Store: ms-windows-store://pdp/?productid=9NRWMJP3717K"
    exit 1
}
$PyVersion = & $Py --version 2>&1
Write-Green "✓ Python: $PyVersion"

# ── Install Python deps ───────────────────────────────────────────
Write-Host ""
$ans = Read-Host "Cài Python deps (pyyaml, jinja2) bằng pip? (Y/n)"
if ($ans -notmatch "^[Nn]") {
    $ReqFile = Join-Path $TargetPath ".claude\skills\firebase-events-impl\scripts\requirements.txt"
    & $Py -m pip install -r $ReqFile --user --quiet 2>$null
    if ($LASTEXITCODE -eq 0) {
        Write-Green "✓ Đã cài pyyaml + jinja2"
    }
    else {
        Write-Yellow "⚠ Cài pip deps lỗi. Cài tay:"
        Write-Yellow "  pip install pyyaml jinja2"
    }
}

# ── Verify skill scripts ──────────────────────────────────────────
Write-Host ""
Write-Host "═══ Verify skill scripts ═══"
Push-Location $TargetPath
try {
    & $Py ".claude\skills\firebase-events-impl\scripts\validate_spec.py" --help 2>$null | Out-Null
    if ($LASTEXITCODE -eq 0) { Write-Green "✓ validate_spec.py works" }
    & $Py ".claude\skills\firebase-events-impl\scripts\scaffold_spec.py" --help 2>$null | Out-Null
    if ($LASTEXITCODE -eq 0) { Write-Green "✓ scaffold_spec.py works" }

    Write-Host ""
    Write-Host "═══ Detect module firebase-events ═══"
    & $Py ".claude\skills\firebase-events-impl\scripts\sync_from_module.py" --check
}
finally {
    Pop-Location
}

# ── Summary ───────────────────────────────────────────────────────
Write-Host ""
Write-Green "╔══════════════════════════════════════════════════════════════╗"
Write-Green "║  ✅ Cài đặt xong!                                            ║"
Write-Green "╚══════════════════════════════════════════════════════════════╝"
Write-Host ""
Write-Host "📁 Cấu trúc:"
Write-Host "   $TargetPath\.claude\                       (skill)"
Write-Host "   $TargetPath\docs\analytics\                (output dir)"
Write-Host ""
Write-Host "🚀 Bước kế:"
Write-Host "   1. cd `"$TargetPath`""
Write-Host "   2. claude  (mở Claude Code)"
Write-Host "   3. Trong Claude Code:"
Write-Host "      > /analytics-sync-module"
Write-Host "      > /analytics-scaffold"
Write-Host "      > /analytics-setup"
Write-Host "      > /analytics-audit"
Write-Host "      > /analytics-generate --apply-missing"
Write-Host "      > /analytics-verify"
Write-Host ""
Write-Host "📖 Đọc thêm:"
Write-Host "   $ScriptDir\docs\usage-guide.md  (hướng dẫn member)"
Write-Host "   $ScriptDir\docs\faq.md          (câu hỏi thường gặp)"
