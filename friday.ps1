# Friday - Voice Output Plugin (v2.0)
# Stream of Thought Mode
# Reads events.txt and speaks new lines.

Add-Type -AssemblyName System.Speech
$synthesizer = New-Object System.Speech.Synthesis.SpeechSynthesizer

# Configure Voice
$synthesizer.SelectVoiceByHints([System.Speech.Synthesis.VoiceGender]::Female)
$synthesizer.Rate = 2 # Speed up voice

$logFile = "$PSScriptRoot\events.txt"
$lastLineCount = 0

if (Test-Path $logFile) {
    $lastLineCount = (Get-Content $logFile).Count
}

Write-Host "----------------------------------------------------------------" -ForegroundColor Cyan
Write-Host "  F R I D A Y   (v2.1)   -   H I G H   P E R F O R M A N C E" -ForegroundColor Cyan
Write-Host "----------------------------------------------------------------" -ForegroundColor Cyan
Write-Host "Listening for thoughts in: $logFile" -ForegroundColor Gray

while ($true) {
    try {
        if (Test-Path $logFile) {
            $content = Get-Content -Path $logFile
            $currentLineCount = $content.Count
            
            if ($currentLineCount -gt $lastLineCount) {
                # Speak all new lines
                for ($i = $lastLineCount; $i -lt $currentLineCount; $i++) {
                    $msg = $content[$i]
                    if (-not [string]::IsNullOrWhiteSpace($msg)) {
                        $timestamp = Get-Date -Format "HH:mm:ss"
                        Write-Host "[$timestamp] Speaking: $msg" -ForegroundColor Green
                        $synthesizer.SpeakAsync($msg) | Out-Null
                    }
                }
                $lastLineCount = $currentLineCount
            }
        }
    }
    catch {
        Write-Host "Error reading log file: $_" -ForegroundColor Red
    }
    
    Start-Sleep -Milliseconds 100 # Faster polling
}
