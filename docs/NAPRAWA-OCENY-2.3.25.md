# Poprawka oceniania odpowiedzi — 2.3.25

## Odtworzone błędy

1. Test klienta dla pojedynczego słowa `reserva` pokazał `ERROR` zamiast `CORRECT`, gdy odpowiedź dotarła ponownie po przejściu do `la habitación` i odtworzeniu nowego wzorca. Aplikacja porównywała wcześniej zaliczone słowo z kolejnym zadaniem. Log przed poprawką: `grading-regressions-before-repeat-fix.txt`.
2. Drugi test klienta wykazał odtworzenie „Nie, to nie to!” po `reserva`, zanim otrzymano wywołanie narzędzia oceny. Błędny komunikat trafiał do audio mimo buforowania, bo zakończenie generacji odblokowywało bufor bez autoryzacji wyniku. Log przed poprawką: `unassessed-feedback-before.txt`, oczekiwane 0 pakietów, faktycznie 1.
3. Testy logiki potwierdziły błędne odrzucanie poprawnej odpowiedzi z dodatkowymi odstępami, krótkiej ramy „Es una reserva” i dokładnego poprawnego zwrotu. Ochrona przed błędnym komunikatem nie rozpoznawała też potocznego „Nie, to nie to”. Log przed poprawką: `false-rejection-before.txt`: 3 błędy z 16 testów.

## Zmiany

- Zachowano ostatnio zaliczone słowo, niezależnie od tego, które następne zadanie zostało już pokazane lub wypowiedziane. Rozpoznana ponownie poprawna poprzednia odpowiedź nie jest oceniana jako błąd nowego zadania, nie zalicza nowego słowa i nie przechodzi jeszcze dalej. Pamięć przetrzymuje ponowne połączenie.
- Zakończona generacja nie uprawnia sama do odtwarzania oceny. Zwykła odpowiedź ucznia czeka na ocenę powiązaną z jego wypowiedzią. Pominięcie narzędzia wywołuje natychmiastową jednorazową prośbę o ocenę bez automatycznego przyznania poprawności. Rzeczowe odpowiedzi na pytania o pomoc i prośby o zmianę tematu mogą nadal być odtwarzane bez oceny, jeśli nie zawierają zarzutu błędu.
- Prawidłowe i niepewne próby nie uruchamiają wczesnego odtwarzania sprawdzanego tylko na podstawie pierwszych kilku wyrazów transkrypcji. Pełna odpowiedź jest sprawdzana przed wypuszczeniem audio. Dla potwierdzonego ERROR zachowano wcześniejsze rozpoczęcie autoryzowanej riposty.
- Rozpoznawanie poprawnej odpowiedzi uwzględnia odstępy, interpunkcję, poprawne rodzajniki, znane warianty regionalne, krótkie twierdzące ramy odpowiedzi oraz dokładny aktualny zwrot na wyższych poziomach. Negacja, pytanie i inny rzeczownik nie stają się przez to poprawną próbą.
- Rozszerzono blokowanie wypowiedzianych zaprzeczeń typu „Nie, to nie to”, „To nie ta odpowiedź”, „Nie tym razem” i „Nie trafiłeś”. Nie pozwala się także zamieniać oceny UNCERTAIN w pewne stwierdzenie błędu. Prośba o kolejną próbę po rzeczywiście niepewnym rozpoznaniu pozostaje dozwolona.

## Granice

Testy klienta korzystają z kontrolowanego serwera i nagranego w teście strumienia pakietów. Potwierdzają logikę i bramki odtwarzania; nie dowodzą poprawnego rozpoznawania mowy użytkownika ani jakości głosu i ripost rzeczywistego Gemini. Nie przeprowadzono testu mikrofonu na telefonie użytkownika. Nie deklarować usunięcia wszystkich możliwych przyczyn błędnej oceny.

Numer wersji pozostaje 2.3.25. Zachowano dokładną 2.3.24 do powrotu oraz nagranie śmiechu. Nowy instalator z tą poprawką powinien mieć odróżnialną nazwę pliku, aby nie pomylić go z wcześniej pobraną 2.3.25.
