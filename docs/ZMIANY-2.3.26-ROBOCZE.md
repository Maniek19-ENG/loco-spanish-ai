# Zmiany robocze po 2.3.25 (niewydane, niepodpisane)

Kod w gałęzi `claude/kind-gates-jnt4sb`. Wersja i kod instalacji NIE zostały zmienione (nadal 2.3.25 / 56) — nadanie numeru wydania wymaga podpisu użytkownika i realnego testu z Gemini.

## Opóźnienia ("myśli za długo", "laguje")

- Przyczyna: w trakcie lekcji całe audio odpowiedzi Gemini było buforowane (`provisionalAudio`) aż do `generationComplete`, więc uczeń nie słyszał nic, dopóki model nie wygenerował całej wypowiedzi. Gdy tekst nie przechodził reguł (np. styl roastu), odpowiedź była wyrzucana i generowana drugi raz.
- Teraz: odpowiedź po zweryfikowanej ocenie (`assess_user_turn`) jest odtwarzana porcja po porcji. Buforowany jest szkic sprzed oceny, odpowiedzi na polecenia aplikacji (start, przyciski) oraz — przy mocnym roaście — tylko pierwsze zdanie, dopóki riposta nie zostanie potwierdzona w transkrypcji; potem reszta płynie od razu. Tekst każdej porcji jest sprawdzany przed odtworzeniem. Jeśli w trakcie odtwarzania tekst zaprzecza zweryfikowanej ocenie (np. "źle" przy CORRECT) albo pyta o inne słowo niż na ekranie, audio jest przerywane i uruchamiana jest dotychczasowa naprawa.
- Usłyszanej już odpowiedzi nie generuje się ponownie wyłącznie z powodu stylu riposty.

## Rozumienie wypowiedzi

- Koniec wypowiedzi (VAD): A1 450 → 750 ms, pozostałe 700 → 850 ms. 450 ms ucinało np. "la… habitación" po samym "la".
- A1 nadpisywało ocenę modelu dokładnym porównaniem transkrypcji. Transkrypcja Gemini często zapisuje hiszpański fonetycznie ("abitación", "cerbeza", "yave"), co kończyło się fałszywym ERROR i wyśmianiem. Dodano klucz brzmieniowy (nieme h, b/v/w, ll/y, c/z/s, j/g) — taki zapis jest CORRECT. Bliska, ale inna forma (np. "ano" zamiast "año") daje UNCERTAIN (prośba o powtórzenie, bez śmiechu), nie ERROR.

## Opcje i wygląd

- Kula zmieniała kolor na czerwony na stałe po pierwszym zapisanym słowie (`learning.isNotEmpty()`); teraz błysk 2,5 s tylko przy nowej poprawce.
- Suwak tempa głosu w nowym trybie nic nie robił; teraz ustawia tempo odtwarzania (domyślnie 1.0×, niezależnie od poziomu). "Wolniej" zmniejsza o 0,25×.
- Chipy poziomu/serii na ekranie startowym nic nie robiły; teraz otwierają Ustawienia/Postępy.
- Nowy wygląd: dryfująca "aurora", szklane karty, gradientowe przyciski i akcenty, kafelki statystyk, pulsujący wskaźnik stanu, rozmowa jako dymki (najnowsze na górze), większa karta słowa.

## Budowanie i testy

- Środowisko chmurowe nie ma dostępu do `dl.google.com` (Android SDK / Google Maven), dlatego dodano `.github/workflows/android.yml`: testy JVM, lint, debug APK i testy na emulatorze na serwerach GitHub. APK debug jest artefaktem przebiegu (podpis debug — nie zaktualizuje wersji zainstalowanej z podpisem użytkownika).
- Logikę bez UI sprawdzono lokalnie na klasach Androida z Robolectric (Maven Central): 140 testów JVM bez błędów (3 klasy wymagające DataStore uruchamia dopiero CI).
- Wynik CI (run 40314810800): testy JVM, lint, debug APK — OK; testy na emulatorze API 34: 120/120 OK. Oryginalna 2.3.25 (d6c5f3c) w tym samym CI: 120/120 OK.
- 11 testów emulatora początkowo padło po włączeniu strumieniowania (odtwarzanie przed walidacją); poprawiono kod, nie testy. `StartupTest` okazał się niestabilny czasowo (osobno przechodził); dodano w nim wyłącznie zapis ekranu do logcat (`LocoDiag`) przy przekroczeniu czasu, bez zmiany warunków.

## Niesprawdzone

Brak realnej rozmowy z Gemini, odsłuchu i pomiaru opóźnienia na telefonie. Testy używają atrapy serwera. Nie deklarować 100% sprawności.
