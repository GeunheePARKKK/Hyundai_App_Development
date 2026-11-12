# 음성 안내 클립 생성 스크립트 (Windows 전용, PowerShell 7).
# Windows 한국어 음성(Microsoft Heami)으로 부품·등급별 안내 문장을 읽어
# app/src/main/res/raw/voice_<부품>_<등급>.wav 로 저장한다.
# 안내 문구(LevelInfo)나 부품 이름(Part)을 바꾸면 이 스크립트의 문구도 맞춰 고친 뒤 다시 실행한다.
#
#   실행: pwsh tools/make_voice_clips.ps1

Add-Type -AssemblyName System.Speech

$outDir = Join-Path $PSScriptRoot '..\app\src\main\res\raw'
New-Item -ItemType Directory -Force $outDir | Out-Null

$parts = [ordered]@{
    headlamp = '전조등'
    wiper    = '와이퍼'
    tire     = '타이어 공기압'
    coolant  = '냉각수 과열'
    brake    = '브레이크'
    power    = '구동 출력 제한'
}
$levels = [ordered]@{
    3 = '3등급, 즉시 정비. 가장 가까운 정비소로 바로 가세요.'
    4 = '4등급, 즉시 정차. 안전한 곳에 정차하고 견인이나 긴급출동을 부르세요.'
}

$synth = New-Object System.Speech.Synthesis.SpeechSynthesizer
$synth.SelectVoice('Microsoft Heami Desktop')
$format = New-Object System.Speech.AudioFormat.SpeechAudioFormatInfo(
    16000,
    [System.Speech.AudioFormat.AudioBitsPerSample]::Sixteen,
    [System.Speech.AudioFormat.AudioChannel]::Mono)

foreach ($part in $parts.GetEnumerator()) {
    foreach ($level in $levels.GetEnumerator()) {
        $file = Join-Path $outDir "voice_$($part.Key)_$($level.Key).wav"
        $synth.SetOutputToWaveFile($file, $format)
        $synth.Speak("$($part.Value) 이상. $($level.Value)")
        $synth.SetOutputToNull()
        Write-Output "$(Split-Path $file -Leaf)  $([math]::Round((Get-Item $file).Length / 1KB)) KB"
    }
}
$synth.Dispose()
