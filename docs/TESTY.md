# Weryfikacja 2.3.17

Wydanie: 2.3.17, kod 49. Pakiet: `com.locospanish.ai`.

## Wyniki

- Kompilacja release, pakietu testowego, testów JVM i lintRelease: sukces. Lint: `No issues found`.
- 12 testów JVM: `WordPlannerTest` (3), `LessonTurnPolicyTest` (6), `ReviewSessionTest` (3).
- `GeminiIntegrationTest`: 54 przypadki, bez błędów, 20.422 s.
- Dwa testy pełnego ekranu rozmowy, trzy testy `PcmAudioTest` i test osadzonego powrotu: 6 przypadków, bez błędów, 34.277 s.

Sprawdzono: utrzymanie połączenia Live po błędzie pomocniczego pobierania, ponowne pobranie bez nowego połączenia, dostarczenie zadania przez `teach_word` po awarii osobnego źródła, start po wyczerpaniu słownika, widoczność kolejnych haseł podczas przewijania, brak zaliczania błędu i milczenia, quiz, pytania ucznia, zapis i odczyt postępów.

Testy Androida wykonano na zainstalowanym APK release. `PcmAudioTest` korzysta z rzeczywistych klas Androida: uruchomienie mikrofonu, odtwarzanie, pomiar głośności, wyciszenie, przerwanie i ponowne uruchomienie. Urządzeniem jest emulator; nie potwierdza to działania fizycznych zestawów Bluetooth ani jakości dźwięku na konkretnym telefonie.

Testy komunikacji używają kontrolowanego WebSocket, przykładowego klucza i kontrolowanych porcji słownictwa. Nie przeprowadzono rzeczywistej rozmowy z Gemini, pomiaru opóźnienia sieciowego ani odsłuchu akcentu i roastów. Wyniki nie oznaczają gwarancji bezbłędności usługi Google.

Sprawdzono instalację 2.3.17 → osadzona 2.3.8 → 2.3.17 bez odinstalowania. Zawartość powrotu odpowiada zachowanemu oryginałowi poza kodem instalacji i podpisem pakietu. Wymagany jest zgodny klucz podpisu.

## Uruchamianie testów

Przykład dla PowerShell:

```powershell
.\gradlew.bat testDebugUnitTest --tests com.locospanish.ai.WordPlannerTest --tests com.locospanish.ai.LessonTurnPolicyTest --tests com.locospanish.ai.ReviewSessionTest
.\gradlew.bat connectedDebugAndroidTest
```

Testy Androida uruchamiaj na emulatorze lub wydzielonym urządzeniu testowym. Obejmują również zapis ustawień, historię i test magazynu klucza; nie uruchamiaj ich na telefonie z własnymi danymi i kluczem produkcyjnym.

Starsze testy i pomocniczy eksport protokołu pozostawiono w źródłach. Wynik opisany powyżej dotyczy wymienionych grup, nie każdego historycznego testu JVM.
