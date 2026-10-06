$ErrorActionPreference='Stop'
Write-Output 'TEST_KEY_INPUT_READY'
$key=''; while($true){$k=[Console]::ReadKey($true); if($k.Key -eq [ConsoleKey]::Enter){break};$key+=$k.KeyChar}
function Send-Json($socket,$value,$token){$bytes=[Text.Encoding]::UTF8.GetBytes(($value|ConvertTo-Json -Depth 50 -Compress));$socket.SendAsync([ArraySegment[byte]]::new($bytes),[Net.WebSockets.WebSocketMessageType]::Text,$true,$token).GetAwaiter().GetResult()|Out-Null}
function Read-Json($socket,$token){$stream=[IO.MemoryStream]::new();try{$buffer=[byte[]]::new(65536);do{$part=$socket.ReceiveAsync([ArraySegment[byte]]::new($buffer),$token).GetAwaiter().GetResult();if($part.MessageType -eq [Net.WebSockets.WebSocketMessageType]::Close){throw 'Closed'};$stream.Write($buffer,0,$part.Count)}while(-not $part.EndOfMessage);return([Text.Encoding]::UTF8.GetString($stream.ToArray())|ConvertFrom-Json)}finally{$stream.Dispose()}}
$fixtures='C:/Users/mariu/.codex/visualizations/2026/10/05/01a10b93-b8aa-78c0-b0ae-e6e0fb720080/LOCO-2.2.1-build/app/build/topic-verification'
New-Item -ItemType Directory -Path 'probki-scenariusze-final' -Force|Out-Null
foreach($id in @('hotel','cafe','taxi','electronics')){
 $socket=[Net.WebSockets.ClientWebSocket]::new();$socket.Options.SetRequestHeader('x-goog-api-key',$key);$deadline=[Threading.CancellationTokenSource]::new(120000)
 try{
  $fixture=Get-Content -Raw "$fixtures/$id.json"|ConvertFrom-Json
  $socket.ConnectAsync([Uri]'wss://generativelanguage.googleapis.com/ws/google.ai.generativelanguage.v1beta.GenerativeService.BidiGenerateContent',$deadline.Token).GetAwaiter().GetResult()|Out-Null
  Send-Json $socket $fixture.config $deadline.Token
  do{$event=Read-Json $socket $deadline.Token;if($event.error){throw ('API '+$event.error.code)}}while(-not $event.setupComplete)
  $turns=@(@{label='lekcja';text=$fixture.command})
  foreach($turn in $turns){
   Send-Json $socket @{clientContent=@{turns=@(@{role='user';parts=@(@{text=$turn.text})});turnComplete=$true}} $deadline.Token
   $answer='';$audio=[IO.MemoryStream]::new()
   do{
    $event=Read-Json $socket $deadline.Token;if($event.error){throw ('API '+$event.error.code)}
    if($event.serverContent.outputTranscription.text){$answer+=$event.serverContent.outputTranscription.text}
    foreach($part in $event.serverContent.modelTurn.parts){if($part.inlineData.data){$chunk=[Convert]::FromBase64String($part.inlineData.data);$audio.Write($chunk,0,$chunk.Length)}}
    if($event.toolCall){$responses=@($event.toolCall.functionCalls|ForEach-Object{$response=@{saved=$false};if($_.name -eq 'assess_user_turn'){$response.saved=$true;if($turn.label -eq 'lekcja'){$response.delivery_instruction=$fixture.guide;$response.scenario_instruction=$fixture.command}};@{id=$_.id;name=$_.name;response=$response}});Send-Json $socket @{toolResponse=@{functionResponses=$responses}} $deadline.Token}
   }while(-not($event.serverContent.turnComplete -and $answer.Length -gt 0))
   $path=Join-Path (Get-Location) ("probki-scenariusze-final/$id-$($turn.label).wav")
   $writer=[IO.BinaryWriter]::new([IO.File]::Create($path));try{$data=$audio.ToArray();$writer.Write([Text.Encoding]::ASCII.GetBytes('RIFF'));$writer.Write([int](36+$data.Length));$writer.Write([Text.Encoding]::ASCII.GetBytes('WAVEfmt '));$writer.Write([int]16);$writer.Write([int16]1);$writer.Write([int16]1);$writer.Write([int]24000);$writer.Write([int]48000);$writer.Write([int16]2);$writer.Write([int16]16);$writer.Write([Text.Encoding]::ASCII.GetBytes('data'));$writer.Write([int]$data.Length);$writer.Write($data)}finally{$writer.Dispose();$audio.Dispose()}
   $result=@{scenario=$id;kind=$turn.label;answer=$answer;wav=$path};$result|ConvertTo-Json -Compress;($result|ConvertTo-Json -Compress)|Add-Content 'probki-scenariusze-final/wyniki.jsonl' -Encoding utf8
  }
 }catch{Write-Output("LIVE_TEST_FAILED: "+$id+" "+$_.Exception.GetType().Name)}finally{$socket.Dispose();$deadline.Dispose()}
}
$key=$null


