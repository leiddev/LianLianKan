# Generates the 5 short sound effects used by LianLianKan as 16-bit PCM mono WAV.
# SRS NFR-7.2 requires self-made or commercially usable assets; everything here is
# synthesized from sine waves, so there is no licensing concern.
#
# Usage:  pwsh -File tools/generate-sfx.ps1
# Output: app/src/main/res/raw/sfx_*.wav  (deterministic, safe to re-run)
#
# NOTE: keep this file ASCII-only. Windows PowerShell 5.1 reads .ps1 files as ANSI
# when there is no BOM, so non-ASCII characters would be mis-decoded and break the parse.

$ErrorActionPreference = 'Stop'

$sampleRate = 22050
$repoRoot = Split-Path $PSScriptRoot -Parent
$outDir = Join-Path $repoRoot 'app\src\main\res\raw'
if (-not (Test-Path $outDir)) { New-Item -ItemType Directory -Path $outDir -Force | Out-Null }

# Each note is a hashtable (s=start ms, d=duration ms, f=frequency Hz, a=amplitude).
# Hashtables are used instead of nested arrays because PowerShell flattens @(@(...)).
$sounds = [ordered]@{
    'sfx_select'    = @(
        @{ s = 0;   d = 60;  f = 880; a = 0.35 }
    )
    'sfx_eliminate' = @(
        @{ s = 0;   d = 70;  f = 660; a = 0.45 }
        @{ s = 60;  d = 90;  f = 990; a = 0.45 }
    )
    'sfx_error'     = @(
        @{ s = 0;   d = 160; f = 220; a = 0.40 }
        @{ s = 0;   d = 160; f = 233; a = 0.25 }
    )
    'sfx_win'       = @(
        @{ s = 0;   d = 110; f = 523; a = 0.40 }
        @{ s = 110; d = 110; f = 659; a = 0.40 }
        @{ s = 220; d = 180; f = 784; a = 0.40 }
    )
    'sfx_lose'      = @(
        @{ s = 0;   d = 150; f = 392; a = 0.40 }
        @{ s = 150; d = 250; f = 262; a = 0.40 }
    )
}

function New-Wav {
    param(
        [Parameter(Mandatory)][string] $Path,
        [Parameter(Mandatory)][array] $Notes
    )

    $totalMs = 0
    foreach ($n in $Notes) {
        $end = $n.s + $n.d
        if ($end -gt $totalMs) { $totalMs = $end }
    }

    $totalSamples = [int][Math]::Round($totalMs * $sampleRate / 1000.0)
    $dataSize = $totalSamples * 2

    $stream = New-Object System.IO.MemoryStream
    $writer = New-Object System.IO.BinaryWriter($stream)

    # ---- RIFF header ----
    $writer.Write([System.Text.Encoding]::ASCII.GetBytes('RIFF'))
    $writer.Write([int](36 + $dataSize))
    $writer.Write([System.Text.Encoding]::ASCII.GetBytes('WAVE'))
    $writer.Write([System.Text.Encoding]::ASCII.GetBytes('fmt '))
    $writer.Write([int]16)                      # PCM header size
    $writer.Write([int16]1)                     # PCM
    $writer.Write([int16]1)                     # mono
    $writer.Write([int]$sampleRate)
    $writer.Write([int]($sampleRate * 2))       # byte rate
    $writer.Write([int16]2)                     # block align
    $writer.Write([int16]16)                    # bits per sample
    $writer.Write([System.Text.Encoding]::ASCII.GetBytes('data'))
    $writer.Write([int]$dataSize)

    # ---- samples ----
    $attackSamples = [int]($sampleRate * 0.005)
    $releaseSamples = [int]($sampleRate * 0.015)

    for ($i = 0; $i -lt $totalSamples; $i++) {
        $ms = $i * 1000.0 / $sampleRate
        $value = 0.0

        foreach ($n in $Notes) {
            if ($ms -lt $n.s -or $ms -ge ($n.s + $n.d)) { continue }

            $offsetSamples = $i - [int]($n.s * $sampleRate / 1000.0)
            $noteSamples = [int]($n.d * $sampleRate / 1000.0)

            # Per-note fade in/out so notes do not click.
            $env = 1.0
            if ($offsetSamples -lt $attackSamples) { $env = $offsetSamples / [double]$attackSamples }
            $remain = $noteSamples - $offsetSamples
            if ($remain -lt $releaseSamples) { $env = [Math]::Min($env, $remain / [double]$releaseSamples) }

            $value += $n.a * $env * [Math]::Sin(2 * [Math]::PI * $n.f * ($offsetSamples / [double]$sampleRate))
        }

        $value = [Math]::Max(-1.0, [Math]::Min(1.0, $value))
        $writer.Write([int16][Math]::Round($value * 32000))
    }

    $writer.Flush()
    [System.IO.File]::WriteAllBytes($Path, $stream.ToArray())
    $writer.Dispose()
    $stream.Dispose()

    "{0,-22} {1,5} ms  {2,7} bytes" -f (Split-Path $Path -Leaf), $totalMs, (Get-Item $Path).Length
}

foreach ($name in $sounds.Keys) {
    New-Wav -Path (Join-Path $outDir "$name.wav") -Notes $sounds[$name]
}
