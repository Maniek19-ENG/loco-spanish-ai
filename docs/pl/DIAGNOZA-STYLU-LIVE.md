# Weryfikacja rzeczywistego stylu LOCO — potrzebne dane z telefonu

Nie potwierdzono, że Gemini LIVE realizuje roast z wersji 2.2.1. Testy jednostkowe potwierdziły konfigurację, migrację i powrót do wcześniejszego zachowania, ale nie odpowiedzi modelu.

Do rozpoznania przyczyny potrzebny jest krótki fragment rzeczywistej transkrypcji: wypowiedź ucznia i pełna odpowiedź LOCO, numer zainstalowanej wersji oraz stan przełącznika powrotu do 2.2.0. Nie jest potrzebny klucz API w czacie.

Podgląd osobowości nie odtwarza sesji LIVE: korzysta z innego modelu, a jego instrukcje narzędzi są sprzeczne (system każe wywołać assess_user_turn, tekst próby zabrania narzędzi). Nie należy używać go jako dowodu zachowania głosu LIVE.

Przed kolejnym wydaniem należy sprawdzić rzeczywiste odpowiedzi w trzech sytuacjach: zwykłe pytanie, poprawna wypowiedź oraz pewna pomyłka językowa. Ocenić konkretność docinki, siłę ironii, poprawność nauki i słyszalną realizację. Powrót do 2.2.0 ma zostać zachowany.
