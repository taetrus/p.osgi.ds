# Harness for verifying the Windows commands in the docs. Runs under pwsh 7.
# Each documented command is EXTRACTED from the doc and run unchanged (only the common
# markdown list indentation is removed). A command is found by an anchor: the exact text of
# one line of the doc, compared after trimming. An anchor that matches no line, or more than
# one line, is a FAIL: the doc changed, so the check must be updated with it.
$ErrorActionPreference = 'Continue'
$Repo = if ($env:GITHUB_WORKSPACE) { $env:GITHUB_WORKSPACE } else { (Resolve-Path "$PSScriptRoot\..\..").Path }
$LogDir = Join-Path $Repo 'verify-logs'
New-Item -ItemType Directory -Force $LogDir | Out-Null
$SummaryFile = Join-Path $LogDir 'summary.tsv'
$script:Stdins = @{}

# Returns the 1-based line numbers of the snippet. Each anchor gives one line; with one
# anchor, -Offset and -Count select a block around it (for a multi-line command).
function Find-DocLines {
    param([string[]]$All, [string]$File, [string[]]$Anchor, [int]$Offset = 0, [int]$Count = 1)
    if ($Anchor.Count -gt 1 -and ($Offset -ne 0 -or $Count -ne 1)) { throw "Offset/Count need exactly one anchor" }
    $lines = foreach ($a in $Anchor) {
        $hits = @(for ($i = 0; $i -lt $All.Count; $i++) { if ($All[$i].Trim() -ceq $a.Trim()) { $i + 1 } })
        if ($hits.Count -ne 1) { throw "anchor found $($hits.Count) times in ${File}: $a" }
        $start = $hits[0] + $Offset
        $start..($start + $Count - 1)
    }
    return , @($lines)
}

function Get-DocSnippet {
    param([string]$File, [string[]]$Anchor, [int]$Offset = 0, [int]$Count = 1, [hashtable]$Replace)
    $all = @(Get-Content -LiteralPath (Join-Path $Repo $File))
    $Lines = Find-DocLines -All $all -File $File -Anchor $Anchor -Offset $Offset -Count $Count
    $script:LastLines = $Lines
    $sel = foreach ($n in $Lines) { $all[$n - 1] }
    $sel = @($sel | Where-Object { $_ -notmatch '^\s*```' })
    $nonEmpty = @($sel | Where-Object { $_.Trim() })
    $indent = 0
    if ($nonEmpty.Count) { $indent = ($nonEmpty | ForEach-Object { $_.Length - $_.TrimStart().Length } | Measure-Object -Minimum).Minimum }
    $sel = @($sel | ForEach-Object { if ($_.Length -ge $indent) { $_.Substring($indent) } else { $_.TrimStart() } })
    if ($Replace) {
        foreach ($k in $Replace.Keys) { $sel = @($sel | ForEach-Object { $_.Replace($k, $Replace[$k]) }) }
    }
    return , $sel
}

function Write-Result {
    param([string]$Id, [string]$Where, [string]$Shell, [bool]$Pass, [string]$Note)
    $tag = if ($Pass) { 'PASS' } else { 'FAIL' }
    Write-Host ""
    Write-Host ">>>>> RESULT [$Id] $tag  $Where ($Shell) $Note"
    Add-Content -LiteralPath $SummaryFile -Value "$Id`t$tag`t$Where`t$Shell`t$Note"
}

function Show-Snippet([string[]]$Snip) {
    Write-Host "----- command as run (verbatim from doc) -----"
    foreach ($l in $Snip) { Write-Host "  | $l" }
    Write-Host "----------------------------------------------"
}

function Get-Snip {
    param([string]$File, [string[]]$Anchor, [int]$Offset = 0, [int]$Count = 1, [hashtable]$Replace, [string[]]$Literal)
    $script:LastLines = @()
    if ($Literal) { return , $Literal }
    return Get-DocSnippet -File $File -Anchor $Anchor -Offset $Offset -Count $Count -Replace $Replace
}

function Get-Where([string]$File) {
    if ($File) { return "${File}:" + ($script:LastLines -join ',') }
    return 'literal'
}

# Runs a snippet to completion and checks its output against a regex.
# cmd   : the lines go to cmd.exe on stdin, so cmd uses interactive (command-line) rules,
#         the same as a user who types or pastes them at the prompt.
# bat   : the lines go into a .bat file (batch-file rules for %).
# powershell / pwsh : the lines go into a .ps1 file, run with -File.
function Invoke-Doc {
    param([string]$Id, [string]$File, [string[]]$Anchor, [int]$Offset = 0, [int]$Count = 1, [string[]]$Literal,
        [ValidateSet('cmd', 'bat', 'powershell', 'pwsh')][string]$Shell,
        [string]$Expect, [string]$NotExpect, [hashtable]$Replace, [int]$TimeoutSec = 900,
        [string]$WorkDir = $Repo, [string]$Note = '', [switch]$Quiet)
    try { $snip = Get-Snip -File $File -Anchor $Anchor -Offset $Offset -Count $Count -Replace $Replace -Literal $Literal }
    catch { Write-Result $Id $File $Shell $false "DOC ANCHOR: $($_.Exception.Message)"; return '' }
    $where = Get-Where $File
    $base = Join-Path $LogDir $Id
    Write-Host "::group::[$Id] $where ($Shell)"
    Show-Snippet $snip
    $out = "$base.out.txt"; $err = "$base.err.txt"
    switch ($Shell) {
        'cmd' {
            $in = "$base.in.txt"; [IO.File]::WriteAllLines($in, $snip)
            $p = Start-Process cmd.exe -ArgumentList '/d', '/q' -RedirectStandardInput $in -RedirectStandardOutput $out -RedirectStandardError $err -NoNewWindow -PassThru -WorkingDirectory $WorkDir
        }
        'bat' {
            $f = "$base.bat"; [IO.File]::WriteAllLines($f, (@('@echo off') + $snip))
            $p = Start-Process cmd.exe -ArgumentList '/d', '/c', "`"$f`"" -RedirectStandardOutput $out -RedirectStandardError $err -NoNewWindow -PassThru -WorkingDirectory $WorkDir
        }
        'powershell' {
            $f = "$base.ps1"; [IO.File]::WriteAllLines($f, $snip)
            $p = Start-Process powershell.exe -ArgumentList '-NoProfile', '-ExecutionPolicy', 'Bypass', '-File', "`"$f`"" -RedirectStandardOutput $out -RedirectStandardError $err -NoNewWindow -PassThru -WorkingDirectory $WorkDir
        }
        'pwsh' {
            $f = "$base.pwsh.ps1"; [IO.File]::WriteAllLines($f, $snip)
            $p = Start-Process pwsh.exe -ArgumentList '-NoProfile', '-ExecutionPolicy', 'Bypass', '-File', "`"$f`"" -RedirectStandardOutput $out -RedirectStandardError $err -NoNewWindow -PassThru -WorkingDirectory $WorkDir
        }
    }
    $null = $p.Handle
    $timedOut = -not $p.WaitForExit($TimeoutSec * 1000)
    if ($timedOut) { & taskkill /T /F /PID $p.Id | Out-Null }
    $p.WaitForExit()
    $text = ''
    if (Test-Path $out) { $text += (Get-Content -LiteralPath $out -Raw) }
    if (Test-Path $err) { $e = Get-Content -LiteralPath $err -Raw; if ($e) { $text += "`n[stderr]`n" + $e } }
    if ($null -eq $text) { $text = '' }
    $allLines = $text -split "`r?`n"
    Write-Host "----- output (exit code $($p.ExitCode); $($allLines.Count) lines; last 60 shown) -----"
    $allLines | Select-Object -Last 60 | ForEach-Object { Write-Host "  > $_" }
    Write-Host "::endgroup::"
    $pass = (-not $timedOut) -and ($text -match $Expect)
    if ($NotExpect -and ($text -match $NotExpect)) { $pass = $false }
    $n = $Note
    if ($timedOut) { $n = "TIMEOUT $n" }
    if (-not $pass) { $n = "$n (expected /$Expect/" + $(if ($NotExpect) { ", not /$NotExpect/" } else { '' }) + ")" }
    Write-Result $Id $where $Shell $pass "exit=$($p.ExitCode) $n"
    return $text
}

# Starts a long-running snippet (a product). stdin is a pipe that stays open, so the
# OSGi console does not see EOF. Output goes to verify-logs/<Id>.log.
function Start-Doc {
    param([string]$Id, [string]$File, [string[]]$Anchor, [int]$Offset = 0, [int]$Count = 1, [string[]]$Literal,
        [ValidateSet('cmd', 'powershell', 'pwsh')][string]$Shell, [hashtable]$Replace, [string]$WorkDir = $Repo)
    try { $snip = Get-Snip -File $File -Anchor $Anchor -Offset $Offset -Count $Count -Replace $Replace -Literal $Literal }
    catch { Write-Result $Id $File $Shell $false "DOC ANCHOR: $($_.Exception.Message)"; return $null }
    $where = Get-Where $File
    $base = Join-Path $LogDir $Id
    $log = "$base.log"
    Write-Host "::group::[$Id] START $where ($Shell)"
    Show-Snippet $snip
    Write-Host "::endgroup::"
    switch ($Shell) {
        'cmd' {
            $f = "$base.start.cmd"; [IO.File]::WriteAllLines($f, $snip)
            $argLine = "/d /c `"`"$f`" > `"$log`" 2>&1`""
            $exe = 'cmd.exe'
        }
        'powershell' {
            $f = "$base.start.ps1"; [IO.File]::WriteAllLines($f, $snip)
            $argLine = "/d /c `"powershell.exe -NoProfile -ExecutionPolicy Bypass -File `"$f`" > `"$log`" 2>&1`""
            $exe = 'cmd.exe'
        }
        'pwsh' {
            $f = "$base.start.pwsh.ps1"; [IO.File]::WriteAllLines($f, $snip)
            $argLine = "/d /c `"pwsh.exe -NoProfile -ExecutionPolicy Bypass -File `"$f`" > `"$log`" 2>&1`""
            $exe = 'cmd.exe'
        }
    }
    $psi = [System.Diagnostics.ProcessStartInfo]::new($exe, $argLine)
    $psi.UseShellExecute = $false
    $psi.RedirectStandardInput = $true
    $psi.WorkingDirectory = $WorkDir
    $p = [System.Diagnostics.Process]::Start($psi)
    $script:Stdins[$Id] = $p.StandardInput
    return [pscustomobject]@{ Id = $Id; Where = $where; Shell = $Shell; Process = $p; Log = $log }
}

function Send-Console($Run, [string]$Line) {
    Write-Host "[console $($Run.Id)] << $Line"
    $Run.Process.StandardInput.WriteLine($Line)
    $Run.Process.StandardInput.Flush()
}

function Read-Log($Run) {
    if (Test-Path $Run.Log) {
        $fs = [IO.File]::Open($Run.Log, 'Open', 'Read', 'ReadWrite')
        try { return ([IO.StreamReader]::new($fs)).ReadToEnd() } finally { $fs.Dispose() }
    }
    return ''
}

function Wait-LogMatch($Run, [string]$Pattern, [int]$TimeoutSec = 120) {
    $deadline = (Get-Date).AddSeconds($TimeoutSec)
    while ((Get-Date) -lt $deadline) {
        if ((Read-Log $Run) -match $Pattern) { return $true }
        if ($Run.Process.HasExited) { Start-Sleep 1; return ((Read-Log $Run) -match $Pattern) }
        Start-Sleep 2
    }
    return $false
}

function Wait-Http([string]$Url = 'http://localhost:8080/api/greet', [int]$TimeoutSec = 150, $Run) {
    $deadline = (Get-Date).AddSeconds($TimeoutSec)
    while ((Get-Date) -lt $deadline) {
        try {
            $r = Invoke-WebRequest -UseBasicParsing -Uri $Url -TimeoutSec 5
            if ($r.StatusCode -eq 200) { Write-Host "HTTP up: $Url -> $($r.Content)"; return $true }
        } catch { }
        if ($Run -and $Run.Process.HasExited) { Write-Host "process exited early (code $($Run.Process.ExitCode))"; return $false }
        Start-Sleep 3
    }
    return $false
}

function Get-JavaCommandLines {
    @(Get-CimInstance Win32_Process -Filter "Name='java.exe'" | ForEach-Object { $_.CommandLine })
}

function Show-Log($Run, [int]$Last = 80) {
    $t = Read-Log $Run
    $l = $t -split "`r?`n"
    Write-Host "::group::[$($Run.Id)] log ($($l.Count) lines; last $Last)"
    $l | Select-Object -Last $Last | ForEach-Object { Write-Host "  > $_" }
    Write-Host "::endgroup::"
}

function Stop-AllJava {
    Get-CimInstance Win32_Process -Filter "Name='java.exe'" | ForEach-Object {
        Write-Host "stopping java pid $($_.ProcessId)"
        Stop-Process -Id $_.ProcessId -Force -ErrorAction SilentlyContinue
    }
    foreach ($k in @($script:Stdins.Keys)) { try { $script:Stdins[$k].Close() } catch { } ; $script:Stdins.Remove($k) }
    for ($i = 0; $i -lt 30; $i++) {
        $busy = Get-NetTCPConnection -State Listen -ErrorAction SilentlyContinue | Where-Object { $_.LocalPort -in 8080, 3288, 3289 }
        if (-not $busy) { break }
        Start-Sleep 1
    }
}
