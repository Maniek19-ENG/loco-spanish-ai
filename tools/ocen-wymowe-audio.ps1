$ErrorActionPreference='Stop'
Write-Output 'TEST_KEY_INPUT_READY'
$key='';while($true){$k=[Console]::ReadKey($true);if($k.Key -eq [ConsoleKey]::Enter){break};$key+=$k.KeyChar}
foreach($name in @('hotel')){
 $path="probki-scenariusze-final/$name-lekcja.wav"
 if(!(Test-Path $path)){continue}
 $audio=[Convert]::ToBase64String([IO.File]::ReadAllBytes((Resolve-Path $path)))
 $instruction='Oceń rzeczywiste AUDIO, a nie tylko prawdopodobne słowa. To lektor uczący hiszpańskiego Polaka. Wypisz usłyszane zdania polskie i hiszpańskie. Oceń osobno wymowę polską i hiszpańską, obcy akcent, akcent wyrazowy, polskie głoski i ewentualne spolszczanie hiszpańskich słów. Rozróżnij poprawne warianty regionalne hiszpańskiego od błędów. Podaj konkretne słowo i błąd, jeśli rzeczywiście go słychać. Nie zakładaj poprawności i nie oceniaj jakości na podstawie tekstu. Jeśli nie masz pewności, wyraź to. Nie oceniaj humoru. Odpowiedz krótko po polsku.'
 $body=@{contents=@(@{role='user';parts=@(@{text=$instruction},@{inlineData=@{mimeType='audio/wav';data=$audio}})});generationConfig=@{temperature=0.0;maxOutputTokens=1800}}|ConvertTo-Json -Depth 20 -Compress
 try{$r=Invoke-RestMethod -Uri 'https://generativelanguage.googleapis.com/v1beta/models/gemini-3.8-flash:generateContent' -TimeoutSec 40 -Method Post -Headers @{'x-goog-api-key'=$key} -ContentType 'application/json' -Body ([Text.Encoding]::UTF8.GetBytes($body));$text=($r.candidates[0].content.parts|Where-Object {-not $_.thought}|ForEach-Object {$_.text}) -join '';@{scenario=$name;review=$text}|ConvertTo-Json -Compress;@{scenario=$name;review=$text}|ConvertTo-Json -Compress|Add-Content 'probki-scenariusze-final/ocena-audio.jsonl' -Encoding utf8}catch{Write-Output("AUDIO_REVIEW_FAILED: "+$name+" HTTP="+$_.Exception.Response.StatusCode)}
}
$key=$null

