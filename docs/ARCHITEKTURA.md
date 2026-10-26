# Architektura

Aplikacja jest pojedynczym modułem Androida, napisanym w Kotlinie z interfejsem Jetpack Compose.

- `ui/`: ekrany, `LocoViewModel`, osobne strumienie stanu karty zadania, kontrolek i transkrypcji.
- `realtime/`: połączenie Gemini Live, protokół narzędzi, niezależne pobieranie porcji słownictwa i instrukcje wypowiedzi.
- `voice/`: mikrofon PCM 16 kHz, odtwarzanie 24 kHz, wybór urządzenia audio i osobne hiszpańskie wzorce.
- `tutor/`: dobór słów, ocena prób, intencje użytkownika, quiz i wykluczanie poznanych haseł.
- `data/`: lokalny zapis ustawień, historii i szyfrowany magazyn klucza API.
- `model/`: stany rozmowy, ustawienia, scenariusze i zdarzenia nauki.
- `repository/`: podsumowanie rzeczywistych postępów.

## Zadanie i rozmowa

Karta zadania ma stan niezależny od dopisywanej transkrypcji. Po wyczerpaniu lokalnego słownika klient pobiera porcję nowych słów z polskimi znaczeniami. Samo pobranie ani odtworzenie wzorca nie oznacza poznania słowa.

Błąd pomocniczego pobierania nie zamyka Gemini Live. Klient prosi działający model głosowy o zgłoszenie zadania przez `teach_word`, aby tekst pojawił się przed ćwiczeniem. Pytania ucznia są rozpatrywane osobno od prób odpowiedzi. Zapis postępu wymaga rzeczywistej wypowiedzi oraz przyjętej oceny.

Wzorce hiszpańskie powstają w oddzielnym hiszpańskim kontekście. Ich kontrola tekstu i formatu PCM nie jest akustyczną oceną wymowy ucznia ani gwarancją doskonałego akcentu modelu.

## Powrót do 2.3.8

`RollbackCard` udostępnia osadzony plik przez `FileProvider` i sprawdza jego SHA256. APK powrotu zachowuje kod aplikacji 2.3.8 oraz ma kod instalacji zgodny z nowszym wydaniem. Przy zmianie zasobu trzeba spójnie zaktualizować kod wersji, oczekiwany hash oraz test osadzonego instalatora. Wymagany jest ten sam certyfikat podpisu. Prywatny klucz pozostaje poza repozytorium.
