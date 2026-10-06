# Specyfikacja LOCO Spanish AI 2.1 — Gemini

## Platforma

| Parametr | Wartość |
|---|---|
| Pakiet | `com.locospanish.ai` |
| Wersja | `2.1.0-gemini`, versionCode 3 |
| Minimalny Android | Android 8.0, API 26 |
| Docelowy SDK / kompilacja | API 37 |
| UI | Kotlin, Jetpack Compose, Material 3, ciemny motyw, polski |
| Stan aplikacji | AndroidViewModel, StateFlow, Coroutines |
| Dane lokalne | Preferences DataStore, JSON Gson |
| Sekret użytkownika | AES-GCM / Android Keystore, brak sekretu w APK |
| Usługa głosowa | Gemini Live API, `gemini-3.8-live` |
| Test osobowości | `gemini-3.8-flash`, generateContent |
| Transport | TLS WebSocket / HTTPS, OkHttp |
| Backend / komputer podczas rozmowy | Niewymagany |
| Internet | Wymagany |
| Konto | Własny klucz Gemini API, projekt Free Tier bez płatnych rozliczeń |

## Rozmowa

- Mikrofon: PCM signed 16-bit little-endian, mono, 16 000 Hz; porcje do 100 ms, kodowanie Base64 w protokole Google.
- Odtwarzanie: PCM signed 16-bit, mono, 24 000 Hz; oddzielna kolejka audio i praca poza wątkiem interfejsu.
- VAD: po stronie Gemini; przerwa kończąca wypowiedź ustawiona na 900 ms, aktywność użytkownika przerywa odpowiedź modelu.
- Przerwanie usuwa kolejkę odtwarzania i oznacza bieżącą transkrypcję AI jako przerwaną.
- AudioRecord VOICE_COMMUNICATION; sprzętowa AcousticEchoCanceler i NoiseSuppressor, gdy urządzenie je udostępnia.
- Limit kolejki wysyłania 256 kB; bufor odbioru ograniczony liczbą porcji. Przeciążenie zatrzymuje rozmowę z komunikatem.
- Limit nawiązania sesji 25 s; WebSocket ping co 20 s; brak automatycznych ponowień po przekroczeniu limitu Google.
- Ręczne wznowienie po awarii: ostatnie wypowiedzi i skrócona pamięć są ponownie przekazywane. Nie jest to dokładne wznowienie sesji serwerowej ani odtworzenie pominiętego audio.
- Aplikacja nie narzuca limitu 20 minut. Lekcja rozwija się po przedstawieniu się i po osiągnięciu celu scenariusza. Kończy ją użytkownik lub wyjście z aplikacji; limity Google i utrata sieci mogą przerwać połączenie. Przycisk ponowienia zachowuje zapis rozmowy.
- Statusy: łączenie, słucham, myślę, mówię, błąd. Transkrypcja jest automatyczna i może zawierać błędy.
- Wyciszenie blokuje wysyłanie mikrofonu. Zakończenie i przejście aplikacji w tło zwalniają mikrofon, głośnik i połączenie.
- Prędkość głosu to instrukcja dla modelu, nie deterministyczna zmiana częstotliwości próbkowania. Model może nie utrzymać dokładnego mnożnika.

## Nauczanie i osobowość

Poziomy A1, A2, B1, B2, C1, C2 sterują długością zdań, pomocą po polsku, trudnością gramatyki i preferowanym tempem. Instrukcje proszą o wzór przed odpowiedzią ucznia, uproszczenie po niepowodzeniu i stopniowe zwiększanie hiszpańskiego. Rzeczywiste zachowanie zależy od modelu; nie jest to certyfikowany system oceny poziomu.

Presety: Nauczyciel, Hiszpański kumpel, Roast Tutor, Custom. Suwaki 0–100: sarkazm, bezczelność, intensywność roastu i korekty. Osobny przełącznik roastu ma pierwszeństwo przed suwakiem. Przekleństwa OFF / lekkie / mocne. Własne preferencje stylu do 2000 znaków, oddzielone od nadrzędnych instrukcji. Test osobowości generuje krótką reakcję na „Yo soy 31 años”, bez sesji i postępów.

Głosy: Kore, Puck, Charon, Aoede, Fenrir, Leda. Dostępność i interpretacja instrukcji głosowych są zależne od Gemini.

## Scenariusze

| Scenariusz | Sugerowany poziom |
|---|---|
| Codzienna rozmowa | A1 |
| Bar | A1 |
| Restauracja | A1 |
| Kawiarnia | A1 |
| Zakupy | A1 |
| Hotel | A1 |
| Lotnisko | A2 |
| Taxi | A1 |
| Poznawanie ludzi | A1 |
| Randka | A2 |
| Wynajem mieszkania | B1 |
| Rozmowa kwalifikacyjna | B1 |
| Telefon do pracodawcy | A2 |
| Praca w hotelu | A2 |
| Maintenance | A2 |
| Electronics Engineer | B2 |
| Sytuacja awaryjna — symulacja językowa | A2 |
| Free Talk | A1 |

Poziom wybrany przez użytkownika ma pierwszeństwo przed sugerowaną trudnością tematu.

## Ekrany i pamięć

1. Start: poziom, dzisiejszy czas, streak, ostatnia sesja, słowa, wybór scenariusza, duży przycisk rozmowy.
2. Scenariusze: lista wszystkich 18 sytuacji, cel i sugerowany poziom.
3. Osobowość: presety, suwaki, roast, przekleństwa, Custom, tekstowy test AI.
4. Postępy: czas, liczba sesji, streak, słowa, błędy, trudności, powtórki, ukończone cele, historia i transkrypcje.
5. Ustawienia: poziom, głos, tempo, korekta, klucz Gemini, potwierdzenie Free Tier, prywatność i usuwanie historii.
6. Rozmowa: animowana kula, stan, czas, transkrypcja, wyciszanie, zakończenie, ponowna próba, powtórz, wolniej, nie rozumiem, wyjaśnij po polsku.

Narzędzia `record_learning` oraz `complete_scenario` zapisują obserwacje z wymaganym dowodem. Identyfikatory zdarzeń są deduplikowane, anulowane narzędzia cofane. Obserwacje AI nie są obiektywnym pomiarem wymowy. Czas jest zliczany tylko podczas połączenia. Przed pierwszą sesją statystyki są puste. Pamięć do kolejnej sesji ma maksymalnie 2400 znaków i osiem ostatnich różnych problemów; nie wysyła całej biblioteki.

## Granice gwarancji

APK jest przeznaczony do instalacji na zgodnych Androidach, ale nie przeszedł testów na każdym producencie, wersji Androida i urządzeniu Bluetooth. Nie ma gwarancji zerowych opóźnień, nielimitowanego darmowego API, niezmienności modeli Google ani identycznej jakości jak ChatGPT. Bez aktywnego klucza nie można potwierdzić rzeczywistej sesji głosowej z Google. Dokładny zakres wykonanych testów jest opisany w WERYFIKACJA.md.

