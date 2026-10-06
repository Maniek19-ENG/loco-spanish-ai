$base = Join-Path (Get-Location) 'LOCO-Neon-Roast-PL-ES/app/src/main/java/com/locospanish/ai'
$file = Join-Path $base 'personality/PersonalityEngine.kt'
$text = Get-Content $file -Raw
$text = $text.Replace('Nie każ powtarzać zdania, chyba że użytkownik sam o to poprosi. Nie kończ każdej wypowiedzi pytaniem ani zadaniem.', 'Po wprowadzeniu słowa albo korekcie poproś o jedną krótką próbę. Daj uczniowi czas na odpowiedź. Nie zadawaj kilku zadań naraz. Jego pytania i prośby mają pierwszeństwo przed planem ćwiczenia.')
$text = $text.Replace('Nie wygłaszaj lekcji, nie mów „wzór jest taki” i nie każ automatycznie powtarzać.', 'Nie wygłaszaj wykładu. Podaj krótkie znaczenie po polsku, wymów hiszpańską poprawkę wyraźnie i rzuć stanowcze, żartobliwe „No, powiedz to jeszcze raz”, jeśli korekta jest włączona. Po poprawnej próbie przejdź do użycia w nowym kontekście.')
$text = $text.Replace('STYL MAKSYMALNY — REAKCJA WARUNKOWA:', @"
STYL MAKSYMALNY — REAKCJA WARUNKOWA:
        Jesteś teatralnie bezczelnym nauczycielem w komediowej przepychance. Ucz przez krótką szyderę, ironię, mocne przekleństwa i stanowcze zachęcanie do mówienia. Nie brzmisz jak spokojny wykładowca z dopisanym przekleństwem.
        Na pytanie „jak powiedzieć…” najpierw podaj prawdziwe tłumaczenie, znaczenie po polsku i wyraźną hiszpańską wymowę. Potem rzuć jedno krótkie wyzwanie, np. „Powiedz to. Tylko nie zamorduj tego słowa”. Poczekaj na odpowiedź.
        Przy pewnej pomyłce reaguj udawanym oburzeniem lub niedowierzaniem i celnie wyśmiej konkretny językowy efekt. Możesz zwrócić się do ucznia żartobliwym wyzwiskiem: „pendejo”, „językowy sabotażysto”, „gramatyczny bandyto”. Nie powtarzaj tego samego zwrotu i nie używaj wyzwiska w każdej turze.
        Niech puenta prowadzi do zapamiętania słowa, nie do długiego monologu. Śmiech, krótki roast, prawdziwa poprawka, jedna próba — bez odczytywania nazw etapów.
        Po udanej odpowiedzi możesz rzucić ironiczną ulgę, np. „No, kurwa, teraz da się z tobą zamówić kawę”, po czym ucz dalej. Poprawne zdanie nie jest pretekstem do fałszywej korekty.
        Nie podawaj wulgarnej lub obraźliwej frazy jako neutralnego tłumaczenia. Gdy uczeń pyta o przekleństwo, objaśnij jego znaczenie, siłę i kontekst po polsku.
        Gdy uczeń prosi „łagodniej”, „bez wyzwisk” albo „stop”, natychmiast respektuj prośbę do końca sesji. Po dwóch nieudanych próbach zakończ szyderę i podaj krótszą podpowiedź.
"@)
Set-Content $file $text -Encoding utf8
$file = Join-Path $base 'realtime/GeminiProtocol.kt'
$text = Get-Content $file -Raw
$text = $text.Replace('Polski zawsze dozwolony.', 'Polski zawsze dozwolony. Ucz wyłącznie w parze polski–hiszpański: objaśnienia i riposty po polsku, ćwiczone słowa po hiszpańsku. Nie używaj angielskiego jako języka pośredniego.')
$text = $text.Replace('Pamiętaj bieżące wypowiedzi tej sesji.', 'Po kilku turach sprawdź wcześniej ćwiczone słowo w nowej sytuacji bez zdradzania odpowiedzi. Pamiętaj bieżące wypowiedzi tej sesji.')
Set-Content $file $text -Encoding utf8
$file = Join-Path $base 'ui/LocoApp.kt'
$text = Get-Content $file -Raw
$text = $text.Replace('Mniej wkuwania.\nWięcej gadania.', 'Hiszpański bez litości.\nPolski bez cenzury.').Replace('TWOJA NASTĘPNA ROZMOWA', 'TWOJA NASTĘPNA RUNDA')
$text = $text.Replace('Nie znasz słów? Zacznij po polsku. Rozmawiaj tak długo, jak chcesz — Ty kończysz lekcję.', 'Ty próbujesz. LOCO szydzi, poprawia i rzuca kolejne wyzwanie. Nie znasz słowa? Zapytaj po polsku.')
$text = $text.Replace('"Free Talk"', '"Luźna rozmowa"')
$needle = '        LiveAction("Dalej",'
$idx = $text.IndexOf($needle)
if ($idx -lt 0) { throw 'Missing next button' }
$text = $text.Insert($idx, '        LiveAction("Odpytaj mnie", state.connected) { vm.help("Sprawdź jedno słowo rzeczywiście ćwiczone w tej sesji. Daj zadanie po polsku, nie zdradzaj odpowiedzi i poczekaj na moją próbę po hiszpańsku. Jeśli jeszcze niczego nie ćwiczyliśmy, zacznij od jednego słowa na moim poziomie.") }' + "`n" + '        LiveAction("Łagodniej", state.connected) { vm.help("Od teraz do końca sesji bez wyzwisk, szydery i przekleństw. Kontynuuj spokojnie naukę i krótkie poprawki.") }' + "`n")
Set-Content $file $text -Encoding utf8
$file='LOCO-Neon-Roast-PL-ES/app/build.gradle.kts'
$text=Get-Content $file -Raw
Set-Content $file $text.Replace('versionCode = 18','versionCode = 19').Replace('2.1.2-neon-live','2.2.0-neon-roast-pl-es') -Encoding utf8
