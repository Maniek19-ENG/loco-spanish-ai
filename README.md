# LOCO Spanish AI

Głosowa nauka hiszpańskiego na Androidzie: polskie objaśnienia, scenariusze, poziomy A1–C2, zapis poznanych słów i regulowany charakter nauczyciela — od spokojnego do ostrego roastu.

**Aktualny kod: 2.3.25 · Android 8.0+ · kompletny projekt do dalszego rozwijania przez AI.**

## Pobierz aplikację

**[Pobierz APK 2.3.25 — poprawka OCENA](https://github.com/Maniek19-ENG/loco-spanish-ai/releases/download/v2.3.25-ocena/LOCO-Spanish-AI-2.3.25-OCENA.apk)**

[Wszystkie pliki tego wydania](https://github.com/Maniek19-ENG/loco-spanish-ai/releases/tag/v2.3.25-ocena) · [sumy SHA-256](downloads/SHA256SUMS-2.3.25-OCENA.txt)

Pliki są publiczne — pobieranie nie wymaga konta GitHub. Wydanie jest oznaczone do dalszego sprawdzenia. Przeszły **159 testów logiki i pełny przebieg 120 testów Androida**. Nie przeprowadzono rzeczywistej rozmowy z Gemini na telefonie; wyniki automatyczne nie potwierdzają jakości rozpoznawania mowy, wymowy ani każdej riposty w prawdziwej sesji.

### Instalacja i pierwsza lekcja

1. Pobierz plik z dopiskiem **OCENA** i otwórz go w aplikacji Pliki / Moje pliki na telefonie.
2. Jeśli Android poprosi, zezwól temu źródłu na instalowanie aplikacji i wybierz aktualizację. **Nie odinstalowuj LOCO ani nie czyść danych**, aby zachować postępy i ustawienia.
3. Przy pierwszym uruchomieniu zezwól na mikrofon i wpisz własny klucz Gemini API w ustawieniach. Potrzebny jest internet i dostęp do modeli Gemini używanych przez aplikację.
4. Wybierz poziom i scenariusz. Na A1 nauka zaczyna się od pojedynczych słów. Siłę docinek, przekleństwa i korektę ustawisz w osobowości nauczyciela.

## Otwórz projekt w innym AI

Przekaż AI adres tego repozytorium lub sklonuj je:

```sh
git clone https://github.com/Maniek19-ENG/loco-spanish-ai.git
```

**Punkt startowy: [ZACZNIJ_TUTAJ.md](ZACZNIJ_TUTAJ.md).** Zawiera wymagania użytkownika, stan projektu, konkretne zgłoszenia oraz miejsca wymagające dalszego sprawdzenia. [AGENTS.md](AGENTS.md) opisuje zasady pracy, [reports/STATUS.md](reports/STATUS.md) — faktyczny zakres testów, a [docs/BUDOWANIE.md](docs/BUDOWANIE.md) — budowanie i konfigurację podpisu.

Proponowane polecenie dla AI: „Przeczytaj AGENTS.md, ZACZNIJ_TUTAJ.md i reports/STATUS.md. Naprawiaj ten projekt, zachowując postępy, zasoby i oryginalne instalatory. Przed uznaniem wydania za gotowe sprawdź rzeczywistą rozmowę z Gemini. Nie zastępuj tej weryfikacji samymi testami z atrapą serwera.”

## Co jest w repozytorium

| Ścieżka | Zawartość |
| --- | --- |
| `app/src/main/` | Kod aplikacji i komplet zasobów, w tym nagranie śmiechu. |
| `app/src/test/`, `app/src/androidTest/` | Testy oceniania, lekcji, scenariuszy, ustawień, ekranu, audio i postępów. |
| `gradle/`, `gradlew`, `gradlew.bat` | Gradle Wrapper i pliki potrzebne do budowania projektu. |
| `app/src/main/assets/loco-2.3.24.apk` | Dokładna poprzednia aktualizacja do powrotu bez pobierania. |
| `references/` | Oryginalne APK 2.3.8 i 2.3.16, nagranie oraz materiał porównawczy z 2.3.8. |
| `reports/`, `docs/` | Wyniki kontroli, opis napraw i ograniczenia weryfikacji. |
| `archives/` | Instalatory zachowane z wcześniejszej publikacji. |

Katalogi kompilacji, pamięć Gradle, ustawienia konkretnego komputera, klucz Gemini i prywatny klucz podpisu nie należą do repozytorium. Do aktualizowania istniejącej instalacji potrzebny jest dotychczasowy podpis; instrukcja użycia prywatnego pliku konfiguracyjnego jest w dokumentacji budowania.

## Ostatnia poprawka oceniania

Naprawiono odrzucanie poprawnego pojedynczego słowa, które dotarło ponownie po przejściu do następnego zadania, oraz wypuszczanie błędnej oceny do audio przed sprawdzeniem wypowiedzi. Dodatkowe przypadki obejmują odstępy, rodzajniki, krótkie twierdzące ramy odpowiedzi i dokładne ćwiczone zwroty. Zachowano odpowiedzi na pytania i prośby o zmianę tematu. [Opis odtworzonych błędów i zmian](docs/NAPRAWA-OCENY-2.3.25.md).

## Powrót do zachowanej wersji

[**2.3.24 — instalator POWRÓT**](https://github.com/Maniek19-ENG/loco-spanish-ai/releases/download/v2.3.25-ocena/LOCO-Spanish-AI-2.3.24-POWROT.apk) · [2.3.8 — instalator POWRÓT](https://github.com/Maniek19-ENG/loco-spanish-ai/releases/download/v2.3.25-ocena/LOCO-Spanish-AI-2.3.8-POWROT.apk)

Powrót do 2.3.24 jest również w ustawieniach aplikacji. Osadzony instalator zachowano bajt w bajt, razem z jego wcześniejszymi instalatorami powrotu. Nie odinstalowuj aplikacji przy cofaniu aktualizacji.

## Budowanie

JDK 21 i Android SDK 37. Otwórz katalog w Android Studio albo uruchom:

```powershell
.\gradlew.bat testDebugUnitTest lintRelease assembleDebug assembleAndroidTest
```

Na Linux/macOS użyj `./gradlew`. APK debug pojawi się w `app/build/outputs/apk/debug/`. Wydanie release aktualizujące dotychczasową instalację wymaga prywatnej konfiguracji podpisu opisanej w [instrukcji budowania](docs/BUDOWANIE.md).
