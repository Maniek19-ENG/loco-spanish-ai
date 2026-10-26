# Stan po poprawce oceniania — 26.10.2026

Bieżący lokalny instalator: **LOCO-Spanish-AI-2.3.25-OCENA.apk**. Przekazany jako poprawka konkretnych odtworzonych błędów oceniania; nie deklarowano 100% sprawności rzeczywistej rozmowy.

## Ostatni przebieg po wszystkich zmianach

- JVM: **159 testów, 0 błędów**.
- Pełny Android: **OK (120 tests)**, 99,874 s. To końcowy pełny przebieg, nie suma częściowych powtórzeń.
- Lint: **0 błędów, 5 ostrzeżeń**.
- Release zbudowany i podpis sprawdzony. Zachowano podpis i kod instalacji 56.
- Osadzona 2.3.24 jest identyczna z zachowaną kopią; jej suma jest w BUILD-LATEST.json.
- Kopia app/src w tym folderze jest identyczna z kodem użytym do budowania i testów.

## Co faktycznie odtworzono i naprawiono

Test dla poprawnego pojedynczego `reserva` po przełączeniu zadania na `habitación` wcześniej zwracał ERROR. Zachowano powiązanie z poprzednio zaliczonym zadaniem; ponowne odebranie odpowiedzi nie tworzy błędu nowego zadania ani nie zalicza go za ucznia.

Drugi test odtworzył wypuszczenie błędnego audio „Nie, to nie to!” przed jakąkolwiek oceną wypowiedzi. Zakończenie generacji nie odblokowuje już niezweryfikowanej oceny. Factualna pomoc i odpowiedzi na zmianę tematu pozostają dostępne bez zaliczania słowa. Pełny test złapał regres blokowania „Chcę uczyć się zwierząt”; poprawiono ten wyjątek przed końcowym przebiegiem.

Dodatkowe testy sprawdzają wszystkie pojedyncze słowa z rzeczywistego słownika scenariuszy z rodzajnikiem i bez niego, odstępy/interpunkcję, twierdzące ramy odpowiedzi, dokładne zwroty oraz odrzucanie negacji i innych słów. Sprawdzenie pełnej poprawnej/niepewnej odpowiedzi poprzedza odtwarzanie; nie wypuszcza się jej wyłącznie na podstawie kilku zgodnych początkowych wyrazów transkrypcji.

## Niesprawdzone

Testy rozmów używają kontrolowanego serwera i próbnych źródeł audio. Nie potwierdzają rozpoznawania rzeczywistej mowy użytkownika, akcentu, brzmienia TTS, jakości wszystkich ripost, opóźnień sieci, limitów konta ani działania Bluetooth na jego telefonie. Nie wykonano rzeczywistej rozmowy z Gemini. Wcześniejsze zgłoszenia użytkownika dotyczące słabego roastu i stabilności w prawdziwej sesji pozostają wymaganiem do weryfikacji.

Szczegóły zmian: ../docs/NAPRAWA-OCENY-2.3.25.md. Logi przed poprawkami i końcowy log po poprawkach są obok tego pliku. Nie usuwać oryginalnych APK i nagrań, nie zastępować starej wersji nowym kodem pod dawną etykietą.
