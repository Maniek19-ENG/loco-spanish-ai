# Weryfikacja 2.1.1 ROAST

Wydanie zbudowano z czystego archiwum projektu 2.1. Pakiet zachowuje
identyfikator `com.locospanish.ai` i wcześniejszy podpis. `versionCode 11`
pozwala zainstalować je jako aktualizację także na telefonie z wcześniejszym
wydaniem o wyższym numerze niż oryginalne 2.1.

Sprawdzono kompilację release, 27 testów jednostkowych (0 błędów) i lint. Próba Gemini Live
korzystała z konfiguracji wygenerowanej przez kod produkcyjny.

SHA-256 APK: `44CD1D3BB45F75C41F3EFE0CA3BA679A21969C4F24E94EE842D27FA58E157C31`.

W sesji Free Talk model:

- rozpoczął od wulgarnej zaczepki bez wymuszania przedstawienia;
- odpowiedział bezpośrednio na pytanie o zgubiony bilet;
- przy `estoy embarazado` zareagował śmiechem zapisanym w transkrypcji,
  wyjaśnił przypadkowe znaczenie i podał poprawną wypowiedź;
- po odpowiedzi o ojcostwie rozwinął ten sam żart;
- po zmianie tematu natychmiast przeszedł do pytania o peron.

W scenariuszu Kawiarnia model rozpoznał różnicę `caballo` / `café`, stworzył
żart o koniu przy ladzie, podał `un café con leche`, a po ripostcie użytkownika
kontynuował scenkę i odpowiedział, jak poprosić o rachunek.

Każda z dziewięciu odpowiedzi zawierała dane audio. Nie wykonano odsłuchu na
fizycznym telefonie, więc naturalnego brzmienia śmiechu nie potwierdzono.
Gemini generuje odpowiedzi losowo; konkretne zdania mogą być inne w kolejnej
sesji. Aplikacja wymusza styl i mechanizm reakcji, lecz nie może zagwarantować
identycznego głosu ani każdej puenty aplikacji Pingo.
