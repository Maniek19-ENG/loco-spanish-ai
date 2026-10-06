# LOCO Spanish AI — Gemini, wersja 2.1

Własna aplikacja Android do nauki hiszpańskiego głosem, z polskim interfejsem. Telefon łączy się bezpośrednio z Gemini. Komputer ani backend nie są potrzebne podczas używania aplikacji. Ta wersja zastępuje wcześniejszą integrację OpenAI zgodnie ze zmianą wymagań.

## Instalacja na telefonie

1. Skopiuj `LOCO-Spanish-Gemini.apk` na telefon, np. przez USB do katalogu Pobrane.
2. Otwórz plik w aplikacji Pliki / Moje pliki. Jeśli Android o to poprosi, zezwól temu menedżerowi plików na instalowanie aplikacji z tego źródła. Możesz wyłączyć tę zgodę po instalacji.
3. Jeśli stara wersja LOCO jest zainstalowana, wybierz aktualizację. Pakiet aplikacji to nadal `com.locospanish.ai`; lokalny certyfikat podpisu pozostaje ten sam.
4. Uruchom LOCO → Ustawienia → **Otwórz Google AI Studio**.
5. Zaloguj się do Google i utwórz klucz w projekcie **Free Tier, bez podłączonego konta rozliczeniowego**. Nie wybieraj „Set up billing”, „Upgrade” ani płatnego planu. Abonament Google AI Pro nie obejmuje tego API.
6. Skopiuj klucz do pola „Klucz Gemini API” w LOCO i zapisz. Nie przesyłaj go do czatu ani innym osobom.
7. Sprawdź plan projektu w AI Studio i zaznacz w LOCO potwierdzenie Free Tier.
8. Wybierz poziom i scenariusz. Naciśnij „Rozpocznij rozmowę” i zezwól na mikrofon.

Na Galaxy S24 obowiązuje ta sama instrukcja. Nie trzeba włączać komputera, instalować Pythona ani uruchamiać starego skrótu „URUCHOM LOCO”.

## Koszt i ograniczenia usługi

Google publikuje darmowy poziom dla użytych modeli, ale dostępność i limity zależą od projektu, regionu oraz aktualnych zasad. **Brak opłat wymaga projektu Free Tier bez płatnych rozliczeń.** Aplikacja nie włącza płatności i nie przełącza się automatycznie na inny model, ale nie ma możliwości niezależnego potwierdzenia planu na podstawie samego klucza. Klucz z płatnego projektu może generować opłaty. Potwierdzenie w ustawieniach jest deklaracją użytkownika, a nie techniczną blokadą rozliczeń Google.

Po błędzie limitu sesja jest zatrzymywana, mikrofon i odtwarzanie zwalniane. Ponowną próbę uruchamiasz samodzielnie. Darmowy limit nie oznacza nieograniczonych rozmów. Połączenie z Google wymaga internetu; aplikacja nie jest offline.

## Wymagania i zgodność

- Android 8.0 / API 26 lub nowszy, mikrofon, głośnik albo zgodny zestaw słuchawkowy.
- Uniwersalny APK zawiera biblioteki dla architektur Androida dostępnych w zależnościach; nie wymaga procesora konkretnej marki ani usług Google Play do samego połączenia API.
- Zalecane: telefon z co najmniej 4 GB RAM i stabilne Wi-Fi/LTE/5G. To zalecenie, nie wynik testów wszystkich urządzeń.
- Aktywne konto Google z dostępem do Gemini API i projekt z kluczem Free Tier.
- Rozmowa działa na otwartym ekranie. Przejście do innej aplikacji lub zablokowanie ekranu kończy i zapisuje sesję. Obrót ekranu zachowuje ViewModel i rozmowę.
- Nie można uczciwie zagwarantować działania na każdym modelu telefonu ani zerowego opóźnienia. Szybkość odpowiedzi zależy też od Google, sieci, temperatury i obciążenia urządzenia. Nie zmierzono opóźnień na fizycznym Galaxy S24.

## Funkcje

Pełna lista i techniczne parametry: [SPECYFIKACJA.md](SPECYFIKACJA.md). Wyniki faktycznie wykonanych kontroli: [WERYFIKACJA.md](WERYFIKACJA.md).

- Rozmowa speech-to-speech: ciągłe przesyłanie mikrofonu, wykrywanie mowy przez Google, odpowiedzi audio i przerywanie AI głosem.
- Polskie objaśnienia, hiszpańskie przykłady, poziomy A1–C2, 18 scenariuszy.
- Cztery presety osobowości, cztery suwaki, roast on/off, trzy ustawienia przekleństw, własne preferencje stylu.
- Test osobowości generuje krótką próbkę tekstową bez mikrofonu, pełnej sesji i zapisu postępów.
- Transkrypcja obu stron, wyciszanie, zakończenie, powtórzenie, wolniej, „nie rozumiem”, „wyjaśnij po polsku”.
- Historia sesji, czas, streak, słowa, trudności, błędy, powtórki i ukończone scenariusze. Brak wymyślonych danych przed pierwszą rozmową.
- Krótkie podsumowanie wcześniejszych trudności zamiast przesyłania całej historii.

## Prywatność i klucz

Klucz wpisujesz po instalacji. Nie jest zapisany w kodzie, BuildConfig, resources ani dostarczonym APK. Aplikacja szyfruje go AES-GCM kluczem z Android Keystore. Sekret idzie do Google w nagłówku `x-goog-api-key`, nie w adresie URL; nie ma loggera HTTP. Kopie zapasowe danych aplikacji są wyłączone. Osobna przestrzeń magazynu uniemożliwia użycie starego tokenu OpenAI jako klucza Gemini.

To aplikacja do osobistego użycia z własnym kluczem użytkownika. Przy publikowaniu usługi dla wielu użytkowników należy zastąpić ten mechanizm backendem wydającym krótkotrwałe tokeny; nie należy dostarczać wszystkim wspólnego klucza.

Audio i kontekst trafiają do Google. Telefon zachowuje tekst i postępy, nie nagrania. Zasady Google dla darmowego API mogą przewidywać używanie treści do ulepszania usług, z odmiennymi zasadami w wybranych regionach. Usunięcie historii w LOCO dotyczy danych telefonu. Ustawienia oraz klucz usuwa się niezależnie.

## Projekt i budowanie

Wymagania programistyczne: Android Studio obsługujące AGP 9.4, JDK 21, Android SDK Platform API 37, SDK Build Tools 36.0.0; Gradle Wrapper 9.7.1 jest dołączony. AGP korzysta z wbudowanej obsługi Kotlin; Compose compiler plugin 2.4.10. Wymagane wersje wynikają z plików Gradle, nie z globalnego Gradle w systemie.

1. Zainstaluj Android Studio z https://developer.android.com/studio.
2. Otwórz ten katalog jako projekt i poczekaj na synchronizację Gradle.
3. SDK Manager: zainstaluj platformę API 37 i Build Tools 36.0.0. Ustaw JDK 21 dla Gradle.
4. Android Studio utworzy `local.properties` z lokalizacją SDK. Plik nie jest częścią archiwum źródeł.
5. W terminalu projektu:

```powershell
.\gradlew.bat assembleDebug testDebugUnitTest lintDebug
.\gradlew.bat assembleRelease
```

APK debug: `app/build/outputs/apk/debug/app-debug.apk`.
APK do prywatnej instalacji bez debuggera: `app/build/outputs/apk/release/app-release.apk`.
Release w tym projekcie korzysta z lokalnego certyfikatu debug dla zgodności z wcześniejszym APK; nie jest to konfiguracja podpisu do Google Play. Na innym komputerze powstanie inny klucz debug, więc aktualizacja istniejącej instalacji wymaga oryginalnego certyfikatu. Nie udostępniamy prywatnego klucza w archiwum.

Backend i `.env` nie są używane w tej wersji. Dołączony `.env.example` tylko dokumentuje tę zmianę. Nie wpisuj klucza do plików projektu.

Struktura Kotlin:

```text
ui/           Compose, ViewModel, ekran rozmowy i ustawienia
voice/        AudioRecord 16 kHz, AudioTrack 24 kHz, echo cancellation
realtime/     Gemini WebSocket, REST próbki osobowości, protokół i reducer
tutor/        krótkie podsumowanie problemów z wcześniejszych sesji
personality/  presety i ograniczanie ustawień
data/         DataStore i szyfrowany magazyn klucza
model/        ustawienia, poziomy, scenariusze, transkrypcje
repository/   obliczenia rzeczywistych postępów
```

## USB i testy deweloperskie

Instalowanie APK z pliku nie wymaga debugowania USB. Do Android Studio/ADB: Ustawienia telefonu → Informacje o telefonie → Informacje o oprogramowaniu → dotknij 7 razy Numer kompilacji. W Opcjach programisty włącz Debugowanie USB. Podłącz przewód danych, odblokuj telefon i zaakceptuj pytanie o zaufanie do tego komputera.

```powershell
adb devices
adb install -r LOCO-Spanish-Gemini.apk
```

Testy jednostkowe: `gradlew.bat testDebugUnitTest`.
Testy na osobnym emulatorze: `gradlew.bat connectedDebugAndroidTest`.
**Testów instrumentacyjnych nie uruchamiaj na swoim telefonie z zapisanym kluczem:** test magazynu klucza zapisuje sztuczną wartość, po czym ją usuwa. Testy połączenia używają atrap transportu i audio, nie usług Google.

## Rozwiązywanie problemów

| Objaw | Co zrobić |
|---|---|
| Brak klucza | Utwórz klucz Gemini w AI Studio, wpisz w LOCO i potwierdź Free Tier. Stary token backendu ani klucz OpenAI nie pasują. |
| Limit wyczerpany | Poczekaj na odnowienie limitu widoczne w AI Studio. LOCO nie kupuje dodatkowych jednostek. |
| Google odrzuca sesję | Sprawdź klucz, region i dostęp projektu do `gemini-3.8-live`. Nie włączaj płatności, jeśli chcesz korzystać bez opłat. |
| Brak mikrofonu | Ustawienia Androida → Aplikacje → LOCO → Uprawnienia → Mikrofon. Sprawdź też globalny przełącznik prywatności mikrofonu. |
| Echo/przerywanie własnego głosu AI | Zmniejsz głośność, użyj słuchawek, przejdź w cichsze miejsce. Sprzętowa redukcja echa zależy od urządzenia. |
| Opóźnienia / urwane audio | Sprawdź stabilność sieci. Wyłącz oszczędzanie baterii dla czasu rozmowy. Przy przeciążeniu bufora sesja jest zatrzymywana zamiast bez końca zwiększać opóźnienie. |
| Sesja kończy się po wyjściu | To zamierzony tryb działania; nie ma nagrywania w tle. |
| Nie można zaktualizować APK | Sprawdź, czy poprzednia instalacja była podpisana tym samym certyfikatem. Nie odinstalowuj jej bez świadomości utraty lokalnej historii. |
| Nie słychać odpowiedzi | Zwiększ głośność rozmowy, sprawdź wybrane urządzenie audio, odłącz zajęty zestaw Bluetooth i rozpocznij sesję ponownie. |

## Dokumentacja użytych usług

- https://ai.google.dev/gemini-api/docs/models/gemini-3.8-live
- https://ai.google.dev/api/live
- https://ai.google.dev/api/generate-content
- https://ai.google.dev/gemini-api/docs/pricing
- https://ai.google.dev/gemini-api/docs/billing

Stan dokumentacji: 23–24 września 2026. Dostępność modeli, limity i zasady dostawcy mogą się zmienić.

