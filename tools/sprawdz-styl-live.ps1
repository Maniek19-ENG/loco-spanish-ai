$ErrorActionPreference='Stop'
Write-Output 'TEST_KEY_INPUT_READY'
$key=''; while($true){$k=[Console]::ReadKey($true); if($k.Key -eq [ConsoleKey]::Enter){break}; $key+=$k.KeyChar}
function Send-Json($socket,$value,$token){
 $bytes=[Text.Encoding]::UTF8.GetBytes(($value | ConvertTo-Json -Depth 50 -Compress))
 $socket.SendAsync([ArraySegment[byte]]::new($bytes),[Net.WebSockets.WebSocketMessageType]::Text,$true,$token).GetAwaiter().GetResult() | Out-Null
}
function Read-Json($socket,$token){
 $stream=[IO.MemoryStream]::new()
 try {$buffer=[byte[]]::new(65536); do {$part=$socket.ReceiveAsync([ArraySegment[byte]]::new($buffer),$token).GetAwaiter().GetResult(); if($part.MessageType -eq [Net.WebSockets.WebSocketMessageType]::Close){throw 'Closed'}; $stream.Write($buffer,0,$part.Count)} while(-not $part.EndOfMessage); return ([Text.Encoding]::UTF8.GetString($stream.ToArray()) | ConvertFrom-Json)} finally {$stream.Dispose()}
}
$fixture='C:/Users/mariu/.codex/visualizations/2026/10/05/01a10b93-b8aa-78c0-b0ae-e6e0fb720080/LOCO-2.2.1-build/app/build/personality-verification'
$socket=[Net.WebSockets.ClientWebSocket]::new(); $socket.Options.SetRequestHeader('x-goog-api-key',$key); $key=$null
$deadline=[Threading.CancellationTokenSource]::new(90000)
try {
 $socket.ConnectAsync([Uri]'wss://generativelanguage.googleapis.com/ws/google.ai.generativelanguage.v1beta.GenerativeService.BidiGenerateContent',$deadline.Token).GetAwaiter().GetResult() | Out-Null
 Send-Json $socket (Get-Content -Raw "$fixture/roast.json" | ConvertFrom-Json) $deadline.Token
 do {$event=Read-Json $socket $deadline.Token; if($event.error){Write-Output ('API_ERROR_CODE='+$event.error.code); exit 2}} while(-not $event.setupComplete)
 $index=0
 foreach($inputText in @('Jak powiedzieć ogórek po hiszpańsku?','Quiero un pene. Chcę zamówić ogórek.','Quiero un pepino, por favor.')){
  Send-Json $socket @{clientContent=@{turns=@(@{role='user';parts=@(@{text=$inputText})});turnComplete=$true}} $deadline.Token
  $answer=''; $audioBytes=0; $audioStream=[IO.MemoryStream]::new(); $laughWritten=$false
  do {
   $event=Read-Json $socket $deadline.Token
   if($event.error){Write-Output ('API_ERROR_CODE='+$event.error.code); exit 2}
   if($event.serverContent.outputTranscription.text){$answer+=$event.serverContent.outputTranscription.text}
   foreach($part in $event.serverContent.modelTurn.parts){if($part.inlineData.data){$chunk=[Convert]::FromBase64String($part.inlineData.data); $audioBytes+=$chunk.Length; $audioStream.Write($chunk,0,$chunk.Length)}}
   if($event.toolCall){
    $responses=@($event.toolCall.functionCalls | ForEach-Object {
     $response=@{saved=$false}
     if($_.name -eq 'assess_user_turn'){
      $response.saved=$true; $v=$_.args.verdict
      $response.delivery_instruction=Get-Content -Raw "$fixture/guide-$v.txt"
      if($v -eq 'ERROR' -and -not $laughWritten){$laugh=[IO.File]::ReadAllBytes('C:/Users/mariu/OneDrive/Desktop/LOCO Spanish AI - Gemini/LOCO-Neon-Roast-PL-ES/app/src/main/res/raw/mocking_laugh.wav'); $audioStream.Write($laugh,44,$laugh.Length-44); $laughWritten=$true}
     }
     @{id=$_.id;name=$_.name;response=$response}
    })
    Send-Json $socket @{toolResponse=@{functionResponses=$responses}} $deadline.Token
   }
  } while(-not ($event.serverContent.turnComplete -and $answer.Length -gt 0))
  $index++; New-Item -ItemType Directory -Path 'probki-stylu-live' -Force | Out-Null
  $wav=Join-Path (Get-Location) "probki-stylu-live/dialog-$index.wav"
  $writer=[IO.BinaryWriter]::new([IO.File]::Create($wav))
  try {$data=$audioStream.ToArray(); $writer.Write([Text.Encoding]::ASCII.GetBytes('RIFF')); $writer.Write([int](36+$data.Length)); $writer.Write([Text.Encoding]::ASCII.GetBytes('WAVEfmt ')); $writer.Write([int]16); $writer.Write([int16]1); $writer.Write([int16]1); $writer.Write([int]24000); $writer.Write([int]48000); $writer.Write([int16]2); $writer.Write([int16]16); $writer.Write([Text.Encoding]::ASCII.GetBytes('data')); $writer.Write([int]$data.Length); $writer.Write($data)} finally {$writer.Dispose(); $audioStream.Dispose()}
  $result=@{input=$inputText;answer=$answer;audioBytes=$audioBytes;wav=$wav;bundledLaugh=$laughWritten}
  $result | ConvertTo-Json -Compress
  ($result | ConvertTo-Json -Compress) | Add-Content 'probki-stylu-live/dialogi.jsonl' -Encoding utf8
 }
} catch {$problem=($_.Exception.GetType().Name + ": " + $_.Exception.Message + " / close=" + $socket.CloseStatusDescription); $problem=[regex]::Replace($problem,"AQ\.[0-9A-Za-z_-]+|AIza[0-9A-Za-z_-]+","[UKRYTY KLUCZ]"); Write-Output ("LIVE_TEST_FAILED: "+$problem); exit 1} finally {$socket.Dispose(); $deadline.Dispose()}

