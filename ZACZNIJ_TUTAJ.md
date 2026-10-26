# LOCO — projekt do dokończenia przez inne AI

## Ostatnia poprawka oceniania

Po kolejnym zgłoszeniu odrzucania poprawnych pojedynczych słów odtworzono dwa błędy całego klienta i dodatkowe błędy rozpoznawania poprawnej odpowiedzi. Aktualny kod zawiera ich poprawki. Końcowy wynik: 159 testów JVM i pełne 120 testów Androida bez błędów. Szczegóły, logi sprzed naprawy i granice sprawdzenia: reports/STATUS.md oraz docs/NAPRAWA-OCENY-2.3.25.md. Instalator ma dopisek OCENA; nadal brak testu prawdziwej rozmowy na telefonie.

## Wcześniejsze zgłoszenia, bez deklaracji gotowości

Użytkownik chce NOWĄ działającą aplikację, a nie instalację starej wersji. Ostatnio dostarczona 2.3.25 została przez użytkownika odrzucona: brak oczekiwanych przekleństw, dopasowanych ripost i śmiechu; zgłasza też, że aplikacja nie działa poprawnie. Testy symulowane przechodziły, lecz nie przewidziały problemów rzeczywistej rozmowy. **Nie traktować tego projektu jako gotowego wydania. Nie publikować na GitHub i nie podawać nowego APK jako sprawdzonego bez testu prawdziwego Gemini.**

Ten folder zawiera aktualne źródła, testy, zasoby, Gradle Wrapper, osadzoną dokładną 2.3.24 oraz oryginalne APK 2.3.8 i 2.3.16 do porównania. Nie ma kluczy API ani prywatnego klucza podpisu. Obecna wersja w kodzie: 2.3.25, kod instalacji 56.

## Wymagania użytkownika

1. Rozmowa i nauka PL/ES. Odpowiedzi na rzeczywiste pytania, zamiast zmuszania do następnego powtórzenia.
2. Na A1 zaczynać od pojedynczych słów, nie trudnych zdań. Na wyższych poziomach adekwatne konstrukcje. Bez automatycznego kończenia po jednym słowie.
3. Scenariusze muszą zmieniać słownictwo i sytuację: hotel, bar itd. Bez jednej listy dla wszystkich scenariuszy i bez pustego „co dalej?”.
4. Tekst na ekranie musi odpowiadać zadaniu mówionemu, również z el/la i innym wymaganym rodzajnikiem. Bez „przygotowuję” wiszącego po wypowiedzeniu słowa.
5. Poprawna ocena: nie zaliczać ciszy/błędnej odpowiedzi, nie odrzucać poprawnego wariantu. Nie oceniać wymowy wyłącznie na podstawie pisowni transkrypcji.
6. Po potwierdzonym błędzie i przy mocnym roaście: szyderczy ŚMIECH, następnie ostra, zabawna, kontekstowa RIPOSTA z mocnymi przekleństwami i krótką parodią sytuacji, potem prawdziwa pomoc/poprawka. Nie sam śmiech ani stałe wyzwisko. Nie w kółko ta sama puenta.
7. Po poprawnej próbie przejść dalej. Nie wymyślać błędu do żartu. Nie śmiać się z niepewnego rozpoznania jak z potwierdzonego błędu.
8. Wysoki roast ma być bezczelny i potoczny, nie oficjalny ani ugrzeczniony. Intensywność i przekleństwa kontrolują ustawienia. Bez automatycznego wyłączania roastu; pytanie o pomoc nie oznacza prośby o wyłączenie humoru.
9. Perfekcyjna hiszpańska i polska wymowa, bez ręcznych przycisków ES/PL. Parodia w polskiej riposcie nie może psuć hiszpańskiego wzorca.
10. Szybka reakcja, bez wielokrotnych zbędnych tur, przycięć, gubienia audio i mechanicznego powtarzania tego samego słowa.
11. Pamięć poznanych słów w postępach, po ponownym uruchomieniu również. Nie odpytuj stale ze znanych słów. Pełne odpytywanie/powtórki, a nie jedno ostatnie hasło i powrót do zwykłej lekcji.
12. Ustawienia niezależne: zmiana profilu nie blokuje roastu ani korekty 100/100. Zapis i odczyt muszą działać.
13. Animowana kula reaguje wielkością na rzeczywisty poziom audio i zmienia kolor odpowiednio do stanu. Jasny, czytelny tekst na ciemnym tle.
14. Wszystkie widoczne przyciski mają działać, włącznie z Powtórz, Wolniej, Nie rozumiem, Dalej, Odpytaj, powrotem do lekcji, wyciszeniem i zakończeniem.
15. Zachować oryginalne wersje i pliki śmiechu. Aktualna osadzona wersja poprzednia to dokładna 2.3.24; wcześniejsze wymaganie zachowania 2.3.8 również pozostaje. Nie zastępować starych APK nowym kodem z tą samą etykietą.

## Potwierdzona ostatnia poprawka w źródłach

W `GeminiLiveClient.watchAssessment` ponowienie pominiętego narzędzia oceny było wykonywane wyłącznie po lokalnym dopasowaniu poprawnego słowa, na A1 lub w powtórkach. Złe odpowiedzi i pytania mogły pozostać bez reakcji aż do długiego limitu odpowiedzi. Usunięto tę blokadę. Mechanizm ponawia ocenę raz dla rzeczywistej niepustej wypowiedzi na każdym poziomie, bez przyznawania poprawności, zaliczania słowa lub przechodzenia dalej. Treść ponowienia rozróżnia pytanie od próby. Test: `everyUnassessedActualUtteranceGetsOneRecoveryWithoutAutomaticCredit`.

To naprawa konkretnej luki, nie dowód rozwiązania wszystkich skarg użytkownika. Nowego APK z tą zmianą nie przekazano jako gotowej aplikacji.

## Gdzie szukać pozostałych problemów

- `GeminiLiveClient`: `roastIssue` jest sprawdzany tylko przy pierwszym podejściu (`alignmentRetries==0`), a `command()` zeruje `roastRequested`. Ponowiona słaba odpowiedź może przejść bez oczekiwanej riposty. Nie nazywać kolejnego dopisania instrukcji naprawą, jeśli rzeczywisty dźwięk nadal pomija żart.
- `SpokenRoastPolicy.enabled`: poprzedni tryb rozmowy wyłącza nową ścieżkę roastu; porównać z ustawieniami użytkownika i zachowaniem 2.3.8. To hipoteza, nie potwierdzony stan jego telefonu.
- `RiposteLaughter.error`: pełny PCM jest wywoływany na zweryfikowany ERROR, ale testy z atrapą odtwarzacza nie dowodzą odsłuchu ani braku późniejszego wyczyszczenia kolejki przez interrupt/task/hide. Prześledzić cały przebieg z prawdziwym dźwiękiem.
- `PresentedAudio`, `LiveWordCard`, `LessonDisplay`: karta ma potwierdzić dwa narysowane kadry przed dźwiękiem. Zbadać zerowanie widoczności/kolejki, zmiany słowa, Dispose i przerwania w rzeczywistej sesji.
- `plannedRoast` jest filtrowany/odrzucany w kilku miejscach; sprawdzić, czy użyteczna riposta nie znika przy korekcie werdyktu, nowym identyfikatorze transkrypcji lub po roboczym tekście, który nie został odtworzony.
- `nativeReply` jest tylko ratunkiem dla przygotowanej polskiej riposty; nie stanowi gwarancji działania Gemini ani dostępności głosu systemowego na urządzeniu.

## Materiał porównawczy

`references/2.3.8/` zawiera oryginalny APK, nagranie i wybrane klasy smali odczytane dokładnie z tego APK. To nie komplet oryginalnych źródeł Kotlin 2.3.8. Nie podpisywać późniejszych źródeł 2.3.17 jako kodu 2.3.8.

`references/2.3.16/` zawiera oryginalny APK. Użytkownik wskazał tę wersję jako działającą u niego. Wcześniejsze wypowiedzi wspominały też działające 2.3.8/2.3.22. Zachować oryginały do porównań; nie przedstawiać powrotu jako wykonania nowego zlecenia.

Osadzony `app/src/main/assets/loco-2.3.24.apk` zachowano bajt w bajt. Jego SHA-256: defef441b9d697aa84c6dc5bb2b7f1ad816cb735a689ca57ad8d264620c1a1e5. W nim są wcześniejsze instalatory 2.3.23 i 2.3.8; `rollbackInput` otwiera je bez modyfikacji. Kod instalacji 56 i zgodny podpis są potrzebne do powrotu bez odinstalowania.

## Połączenie i bezpieczeństwo plików

Model Live to `gemini-3.8-live`, słownictwo REST: `gemini-3.8-flash`. Zweryfikowano oficjalne informacje Google 26.10.2026: Live 3.8 nadal istnieje; ma domyślnie asynchroniczne narzędzia. Nasza konfiguracja JUŻ zawiera `behavior: BLOCKING` we wszystkich deklaracjach. Nie dopisywać tego jako rzekomo nowej poprawki. Oficjalne źródło: https://ai.google.dev/gemini-api/docs/models/gemini-3.8-live . Nie dodawać nieobsługiwanych thinking_config, proactive_audio:false ani enable_affective_dialog.

Poprzedni potwierdzony regres połączenia wiązał się z pustym elementem enum dla rodzajnika. Usunięto go. Nie przywracać pustych enumów i nie dokładać wymaganych pól bez testu prawdziwego serwera.

Klucz Gemini jest wpisywany w aplikacji i szyfrowany przez Android Keystore. Nie czytać go do czatu/logów, nie dodawać do źródeł. Użytkownik nie mógł wcześniej podłączyć telefonu ani wpisać klucza w emulatorze; nie pytać ponownie o to samo bez potrzeby. Jeśli nie ma dostępu do prawdziwej sesji, oznaczyć weryfikację jako niepełną, zamiast obiecywać 100%.

## Budowanie i testy

Otwórz ten folder w Android Studio lub użyj Gradle Wrapper. JDK 21, Android SDK 37, minSdk 26. `gradlew.bat testDebugUnitTest lintRelease assembleDebug assembleAndroidTest`. Wydanie aktualizujące istniejącą instalację wymaga dotychczasowego klucza podpisu, którego celowo tu nie ma. Sposób konfiguracji: README.md i keystore.properties.example.

Raporty bieżącej weryfikacji są w `reports/`. Wyniki symulowanych rozmów nie są dowodem poprawnej wymowy, humoru, opóźnienia i rzeczywistej komunikacji z Gemini. Nie powtarzać zakończonych kontroli bez powodu; skupić się na odtwarzalnej skardze użytkownika i prawdziwej sesji.
