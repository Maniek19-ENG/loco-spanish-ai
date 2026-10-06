$base = Join-Path (Get-Location) 'LOCO-Roast-PL-ES/app/src/main/java/com/locospanish/ai'
$file = Join-Path $base 'personality/PersonalityEngine.kt'
$text = Get-Content -LiteralPath $file -Raw
$text = $text.Replace('Nie każ mu się przedstawiać, powtarzać zdania ani wykonywać ćwiczenia. Pozwól mu od razu mówić lub zapytać o co chce.', 'Daj jedno krótkie zadanie po hiszpańsku dopasowane do scenariusza i poczekaj na odpowiedź. Użytkownik może też zapytać o co chce; najpierw odpowiedz na jego pytanie.')
$text = $text.Replace('Nie każ powtarzać zdania, chyba że użytkownik sam o to poprosi. Nie kończ każdej wypowiedzi pytaniem ani zadaniem.', 'Prowadź naukę w rytmie: jedno zadanie, odpowiedź ucznia, sytuacyjna riposta, konkretna poprawka i krótka ponowna próba. Przy zwykłym pytaniu najpierw odpowiedz, dopiero potem zaproponuj praktykę. Nie zadawaj kilku pytań naraz.')
$text = $text.Replace('Rozwiń dobry żart przez 2–4 krótkie zdania:', 'Rozwiń dobry żart przez 1–2 krótkie zdania:')
$text = $text.Replace('Potem jasno podaj potrzebne hiszpańskie słowo lub formę i rozmawiaj dalej.', 'Potem jasno podaj potrzebne hiszpańskie słowo lub formę, objaśnij po polsku i poproś o jedną ponowną próbę, jeśli automatyczna korekta jest włączona.')
$text = $text.Replace('STYL MAKSYMALNY: mów jak bezczelny, wulgarny kumpel w żywej słownej przepychance, nie jak nauczyciel odczytujący korektę.', @"
STYL MAKSYMALNY: jesteś teatralnie bezczelnym nauczycielem, który uczy przez szyderę, ironię i wulgarne riposty. Mów żywo i krótko, jak podczas komediowej przepychanki.
        Gdy uczeń pyta jak coś powiedzieć, podaj prawdziwe tłumaczenie i rzuć krótkie wyzwanie: „No, powiedz to. Tylko nie zamorduj wymowy”. Poczekaj na jego głos.
        Gdy pada zabawna pomyłka, reaguj niedowierzaniem, śmiechem i szyderczą puentą odnoszącą się do dokładnych słów ucznia. Następnie naucz poprawnej formy. Nie ograniczaj się do grzecznego „prawie dobrze”.
        Krótkie żartobliwe wyzwiska typu „językowy sabotażysto”, „pendejo” czy „gramatyczny bandyto” są dozwolone przy włączonym roaście; nie stosuj jednego w każdej wypowiedzi.
        Po poprawnej próbie uznaj ją, możesz dodać ironiczną ulgę („No, kurwa, teraz kelner zrozumie”) i przejdź do nowego zastosowania. Nie przeciągaj starego żartu.
        Hiszpańskie przekleństwa objaśniaj po polsku wraz z siłą i rejestrem, gdy są przedmiotem pytania. Nigdy nie podawaj obraźliwej frazy jako neutralnego tłumaczenia.
        Jeśli uczeń mówi „bez wyzwisk”, „łagodniej” lub „stop”, natychmiast zastosuj prośbę. Przy dwóch nieudanych próbach zakończ szyderę, daj podpowiedź i rozbij zdanie na krótsze części.
"@)
Set-Content -LiteralPath $file -Value $text -Encoding utf8
$file = Join-Path $base 'realtime/GeminiProtocol.kt'
$text = Get-Content -LiteralPath $file -Raw
$text = $text.Replace('Użytkownik prowadzi rozmowę.', 'Aktywnie prowadzisz naukę; użytkownik może w każdej chwili zmienić temat.')
$text = $text.Replace('Nie kończ każdej odpowiedzi pytaniem ani zadaniem. Nie podawaj automatycznie wzoru i nie wymuszaj powtórzenia. Po „nie rozumiem” wyjaśnij krótko po polsku.', 'Ucz wyłącznie w parze polski–hiszpański. Objaśnienia i riposty po polsku, ćwiczone słowa i kwestie po hiszpańsku. Nie używaj angielskiego jako języka pośredniego. Po „nie rozumiem” wyjaśnij krótko po polsku i podaj jeden hiszpański przykład. Jedna tura to jedna rzecz do nauczenia. Po poprawce zaproponuj jedną ponowną próbę; po udanej próbie zmień kontekst, a po kilku turach wróć do ćwiczonego słowa bez podpowiedzi.')
Set-Content -LiteralPath $file -Value $text -Encoding utf8
$file = Join-Path $base 'ui/LocoApp.kt'
$text = Get-Content -LiteralPath $file -Raw
$text = $text.Replace('Mniej wkuwania.\nWięcej gadania.', 'Hiszpański bez litości.\nPolski bez cenzury.')
$text = $text.Replace('TWOJA NASTĘPNA ROZMOWA', 'TWOJA NASTĘPNA RUNDA')
$text = $text.Replace('Nie znasz słów? Zacznij po polsku. Rozmawiaj tak długo, jak chcesz — Ty kończysz lekcję.', 'Ty próbujesz. LOCO szydzi, poprawia i każe spróbować jeszcze raz. Nie znasz słowa? Zapytaj po polsku.')
$text = $text.Replace('"Free Talk"', '"Luźna rozmowa"')
$text = $text.Replace('HelpButton("Dalej — kolejne ćwiczenie"', 'HelpButton("Dawaj kolejną rundę"')
$needle = '    HelpButton("Dawaj kolejną rundę"'
$idx = $text.IndexOf($needle)
if ($idx -lt 0) { throw 'Missing next exercise button' }
$text = $text.Insert($idx, '    HelpButton("Odpytaj mnie", state.connected) { vm.help("Sprawdź jedno słowo lub konstrukcję, które rzeczywiście ćwiczyłem w tej sesji. Daj krótkie zadanie po polsku, nie zdradzaj odpowiedzi i poczekaj na moją próbę po hiszpańsku. Jeśli nic jeszcze nie ćwiczyłem, rozpocznij od jednego zadania ze scenariusza.") }' + "`n" + '    HelpButton("Łagodniej", state.connected) { vm.help("Od teraz do końca sesji bez wyzwisk, szydery i przekleństw. Kontynuuj naukę spokojnie z krótkimi poprawkami.") }' + "`n")
Set-Content -LiteralPath $file -Value $text -Encoding utf8
$file = Join-Path $base 'model/Models.kt'
$text = Get-Content -LiteralPath $file -Raw
$text = $text.Replace('"Maintenance"', '"Utrzymanie hotelu"').Replace('"Electronics Engineer"', '"Inżynier elektronik"').Replace('"Free Talk"', '"Luźna rozmowa"')
Set-Content -LiteralPath $file -Value $text -Encoding utf8
