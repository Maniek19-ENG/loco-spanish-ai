# 2.3.25 — śmiech i riposta po potwierdzonym błędzie

Po potwierdzonym błędzie, przy roaście co najmniej 80/100, odtwarzane jest pełne oryginalne nagranie śmiechu: 2 sekundy, PCM mono 24 kHz. Następnie odtwarzana jest odpowiedź nauczyciela: riposta związana z wypowiedzią ucznia, objaśnienie i aktualne zadanie. Powtórne wywołanie oceny tej samej wypowiedzi nie dodaje drugiego nagrania. Kolejna rzeczywista błędna próba może dostać kolejną reakcję. Poprawna odpowiedź, pytanie i niepewne rozpoznanie nie uruchamiają tej reakcji.

Polecenie wykonania głosowego uwzględnia nagranie przed ripostą, zamiast prosić model o dodatkowy śmiech. Przy mocnych przekleństwach model otrzymuje polecenie tworzenia puenty z dokładnych słów ucznia i znaczenia pomyłki. Zachowano weryfikację kontekstu i powtórek. Przy pominięciu przygotowanej riposty odtwarzanie może poczekać do 600 ms na przygotowywaną równolegle polską odpowiedź, zamiast odrzucać ją tylko dlatego, że nie była gotowa w chwili zakończenia tury. Ponowienie odpowiedzi nie kasuje kolejki śmiechu.

Konfiguracja narzędzi połączenia Gemini pozostaje bez zmian. Wersja 2.3.24 została zachowana bajt w bajt, łącznie z jej osadzonymi starszymi instalatorami. Kod instalacji pozostaje 56, aby umożliwić powrót do oryginalnego podpisanego APK bez odinstalowania.

Testy automatyczne korzystają z kontrolowanego serwera i próbnych głosów. Nie zastępują odsłuchu rzeczywistej rozmowy z Gemini na telefonie.
