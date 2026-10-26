> **Stan testów i granice weryfikacji:** [reports/STATUS.md](reports/STATUS.md). Ostatnia poprawka oceniania: [docs/NAPRAWA-OCENY-2.3.25.md](docs/NAPRAWA-OCENY-2.3.25.md). Rzeczywista rozmowa z Gemini na telefonie nie została zweryfikowana.

# LOCO Spanish AI

Androidowa aplikacja do głosowej nauki hiszpańskiego z polskimi objaśnieniami. Bieżąca wersja: **2.3.25** (`versionCode` 56), pakiet `com.locospanish.ai`.

Aplikacja łączy się bezpośrednio z Gemini. Klucz API użytkownik wpisuje w ustawieniach telefonu; nie jest częścią źródeł ani APK. Audio jest przesyłane do Google, a historia lekcji i postępy są zapisywane lokalnie.

## Funkcje

- Rozmowa PL/ES, scenariusze i poziomy A1–C2; na A1 nauka pojedynczych słów.
- Widoczne słowo i polskie znaczenie, przypięte nad przewijaną rozmową.
- Konfigurowalne docinki, ironia, przekleństwa i intensywność korekty.
- Poznane słowa, powtórki, odpytywanie oraz odpowiedzi na pytania ucznia.
- Animowana kula reagująca na stan rozmowy i natężenie dźwięku.
- Oryginalny instalator poprzedniej 2.3.24; dostępne są także 2.3.23 i 2.3.8.
- Pełne nagranie szyderczego śmiechu po potwierdzonym błędzie, przed kontekstową ripostą; bez śmiechu oznaczającego błąd przy poprawnej odpowiedzi.

Rozmowa i wzorce wymowy korzystają z `gemini-3.8-live`. Nowe porcje słownictwa pobiera `gemini-3.8-flash`. Błąd dodatkowego pobierania nie zamyka połączenia głosowego: zadanie może dostarczyć również model Live. Dostępność i limity modeli zależą od projektu Google. Gemini 4 Argon nie jest używany jako model głosowy.

## Uruchomienie projektu

Otwórz ten folder w Android Studio i pozwól zsynchronizować Gradle. Zweryfikowane środowisko budowania korzysta z JDK 21, Android SDK 37 i dołączonego Gradle Wrapper 9.7.1. Minimalny Android: API 26.

Windows:

```powershell
.\gradlew.bat assembleDebug
```

Linux/macOS:

```sh
chmod +x gradlew
./gradlew assembleDebug
```

APK debug powstaje w `app/build/outputs/apk/debug/`. Ścieżkę Android SDK ustaw w środowisku lub lokalnym pliku `local.properties`, którego nie dodaje się do Git. Po instalacji wpisz własny klucz Gemini w ustawieniach aplikacji i przyznaj dostęp do mikrofonu. Konto w aplikacji Gemini i projekt Gemini API mają odrębne zasady dostępu oraz rozliczeń.

## Podpisywanie wydania

Repozytorium nie zawiera prywatnego klucza podpisu ani jego haseł. Zachowanie podpisu jest konieczne, aby aktualizować istniejącą instalację bez odinstalowania i zachować możliwość powrotu do osadzonego APK 2.3.8.

1. Skopiuj `keystore.properties.example` poza repozytorium i wpisz dane własnego klucza. `storeFile` powinien wskazywać pełną ścieżkę; na Windows użyj ukośników `/`.
2. Ustaw `LOCO_SIGNING_PROPERTIES` na pełną ścieżkę do tego prywatnego pliku.
3. Uruchom `assembleRelease`.

Przykład dla PowerShell:

```powershell
$env:LOCO_SIGNING_PROPERTIES = 'C:/private/loco-signing.properties'
.\gradlew.bat assembleRelease
```

Bez konfiguracji podpisu wydanie release jest niepodpisane. Nie zastępuj oryginalnego klucza nowym, jeśli chcesz aktualizować wcześniejsze instalacje. Debug z własnym certyfikatem nie jest aktualizacją podpisanego wcześniej wydania.

## Struktura

```text
app/src/main/         kod aplikacji, zasoby i osadzony instalator powrotu
app/src/test/         testy JVM i narzędzia weryfikacji protokołu
app/src/androidTest/  testy Androida, ekranu, lekcji i dźwięku
gradle/wrapper/       Gradle Wrapper
docs/                architektura i wyniki weryfikacji
```

`app/src/main/assets/loco-2.3.24.apk` zawiera dokładną poprzednią aktualizację. W niej pozostają oryginalne instalatory 2.3.23 i 2.3.8; aplikacja otwiera je bez zmieniania ich zawartości i sprawdza sumy SHA-256. Nie usuwaj tego zasobu: jest potrzebny do powrotu bez internetu. Nagranie śmiechu znajduje się w `app/src/main/res/raw/mocking_laugh.wav` i trafia do APK automatycznie. Powrót wymaga zgodnego podpisu obu instalacji.

Pozostałe instalatory publikuj osobno jako pliki wydania, a nie w katalogu źródeł. Lokalne archiwa, CV, logi, pamięci Gradle, klucze i ustawienia komputera nie należą do repozytorium.

## Testy

Szczegóły i granice weryfikacji znajdują się w [docs/TESTY.md](docs/TESTY.md). Opis modułów: [docs/ARCHITEKTURA.md](docs/ARCHITEKTURA.md).

Testy sieciowe używają kontrolowanych odpowiedzi i przykładowego klucza. Ich wynik nie zastępuje rozmowy z prawdziwym Gemini na telefonie.

