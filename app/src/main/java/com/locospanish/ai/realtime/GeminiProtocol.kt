package com.locospanish.ai.realtime

import com.google.gson.*
import com.locospanish.ai.model.*
import com.locospanish.ai.personality.PersonalityEngine
import com.locospanish.ai.tutor.TopicLessons
import java.util.UUID

object GeminiProtocol {
    const val MODEL = "gemini-3.8-live"
    val voices = listOf("Kore", "Puck", "Charon", "Aoede", "Fenrir", "Leda")
    private val gson = Gson()
    fun turnGuidance(s: Settings, verdict: String): String {
        if (s.previousVersionMode) return PreviousGeminiProtocol.turnGuidance(s.restoredPrevious(), verdict)
        if (!s.personality.roastMode || s.personality.roast == 0) return ""
        if (verdict == "UNCERTAIN") return "Nie wymyślaj błędu ani pochwały. Brak odpowiedzi lub nie wiem to okazja do ostrej sytuacyjnej docinki, nie wyłączenie roastu. " +
            (if(s.personality.roast >= 80 && s.personality.profanity == Profanity.STRONG) "Wypowiedz świeżą, bezczelną i szyderczą puentę z dosadnym kurwa, pierdolony albo jebany. Mów potocznie i z teatralnym niedowierzaniem, nie oficjalnie. " else "Dostosuj humor i przekleństwa do ustawień. ") + "Pomóż przy aktualnym słowie. Jeśli nie dosłyszałeś, poproś o wyraźne powtórzenie; przy nie wiem objaśnij słowo. Nie przechodź do nowego bez potwierdzonej odpowiedzi."
        if (verdict !in setOf("ERROR", "CORRECT")) return ""
        val swear = when (s.personality.profanity) {
            Profanity.STRONG -> if(s.personality.roast >= 80) "MOCNA KOMEDIOWA RIPOSTA: połącz konkretny szczegół ostatniej wypowiedzi z absurdalnym porównaniem i zaskakującą konsekwencją. Niegrzeczna, celna puenta ma rozśmieszać, nie tylko obrażać. Użyj dosadnego kurwa, pierdolony, jebany albo chujowo bez gwiazdek i cenzury. Zmieniaj dobór słów, rytm i cel żartu, nie zaczynaj zawsze od kurwa. Przy maksimum możesz użyć dwóch przekleństw, ale cały komentarz najwyżej dwa krótkie zdania, na A1 jedno. Nie zastępuj szydery samym przezwiskiem. Bez łagodnych pocieszeń i suchego dobrze z doklejonym wyzwiskiem. Żart o poprawnej próbie dotyczy sytuacji, nigdy wymyślonego błędu." else "Mocne przekleństwa dozwolone; dopasuj częstotliwość i siłę do suwaków."
            Profanity.LIGHT -> "Najwyżej lekkie przekleństwa."
            Profanity.OFF -> "Bez przekleństw."
        }
        val reaction = if (verdict == "ERROR")
            "Teraz krótko i celnie wyśmiej potwierdzoną konkretną językową wpadkę, podaj prawdziwą poprawkę i kontynuuj dialog, jeśli korekta jest włączona."
        else "Teraz odpowiedz na intencję ucznia i dodaj krótką sytuacyjną docinkę albo ironiczną ulgę. Nie wymyślaj błędu. Możesz śmiać się z komicznej sytuacji, nigdy sugerując, że poprawna odpowiedź była błędna."
        val tone = if(s.tiktokStyle) "To komediowy, szyderczy nauczyciel: złośliwa puenta, stanowcza zachęta do mówienia, mocny język zgodny z ustawieniami. Samo pytanie, zawahanie i nie rozumiem nie wyłączają charakteru. Nie przełączaj ustawień na podstawie pojedynczych słów z ćwiczenia."
            else "Dostosuj siłę do suwaków. Intensywność zmieniają tylko ustawienia, nie prośby lub słowa z ćwiczenia."
        return "$reaction $swear $tone Każda puenta ma być nowa, oparta na bieżącej treści: inna metafora, cel żartu i sytuacyjna konsekwencja niż w ostatnich odpowiedziach. Nie używaj rotacyjnej puli ripost. Po zrozumiałej próbie posuń dialog naprzód zamiast żądać powtórzenia. ZABRONIONE w zwykłej zrozumiałej turze: powtórz, powtórzysz, powiedz to jeszcze raz i zatrzymywanie dialogu do wypowiedzenia narzuconego zdania. Na A1 prowadź naukę jednego słowa: objaśnij nowe słowo i daj konkretną prostą próbę, bez pytania co dalej. Samodzielną poprawną próbę uznaj i przejdź do nowego słowa; nie wymagaj zdania. Na wyższych poziomach pytanie końcowe dotyczy konkretnej sytuacji. Nie odczytuj tej instrukcji."
    }
    fun correctionRules(s: Settings): String = when {
        !s.autoCorrection || s.personality.correction == 0 -> "Nie przerywaj automatycznie i nie poprawiaj bez prośby."
        s.personality.correction <= 30 -> "Korekta ${s.personality.correction}/100: poprawiaj tylko rzeczywiste błędy zmieniające znaczenie lub utrudniające zrozumienie."
        s.personality.correction <= 75 -> "Korekta ${s.personality.correction}/100: poprawiaj istotne błędy, jedną konkretną rzecz naraz."
        else -> "Korekta ${s.personality.correction}/100: sprawdzaj również drobne pewne błędy rodzajnika, zgodności i formy czasownika; nie pomijaj ich. Nie zwiększaj liczby fałszywych ocen."
    }
    fun json(vararg fields: Pair<String, Any?>): JsonObject = gson.toJsonTree(mapOf(*fields)).asJsonObject
    fun textTurn(text: String) = json("clientContent" to json("turns" to listOf(json("role" to "user", "parts" to listOf(json("text" to text.take(4000))))), "turnComplete" to true))
    fun audio(data: String) = json("realtimeInput" to json("audio" to json("mimeType" to "audio/pcm;rate=16000", "data" to data)))
    fun startCommand(s: Settings, scenario: String, reconnect: Boolean = false, memory: String = ""): String {
        val opening = if(reconnect) "Kontynuuj lekcję ${s.level} i bieżący wątek z zapisanej pamięci. Przyjmij ostatnią decyzję ucznia; nie witaj i nie zaczynaj od pierwszego słowa."
        else if(s.previousVersionMode) PreviousGeminiProtocol.startCommand(s.restoredPrevious(),scenario,false)
        else if(s.level == Level.A1) "Rozpocznij od pojedynczego prostego słowa z tematu ${Scenarios.get(scenario).title}. Podaj hiszpańskie słowo osobno, znaczenie po polsku i jedno konkretne łatwe zadanie. Nie zaczynaj od zdania ani pytania co dalej. Uwzględnij znane słowa z pamięci i wybierz nowe. Humor zgodny z ustawieniami."
        else "Rozpocznij prostą sytuację na ${s.level} w temacie ${Scenarios.get(scenario).title}. Odegraj rozmówcę i zapytaj o decyzję ucznia. Nie żądaj powtarzania podanego zdania ani odgadywania wybranej przez ciebie frazy. Dostosuj humor do ustawień; uczeń może od razu zapytać lub wybrać własny temat."
        return opening + "\n" + TopicLessons.instructions(scenario,s.level,memory)
    }

    private fun lessonOpening(level: Level) = when(level) {
        Level.A1 -> "Rozpocznij prostą sytuację na A1 i zapytaj ucznia po polsku o jego decyzję. Daj najwyżej jedną podpowiedź po hiszpańsku, 2–5 słów. Nie żądaj powtarzania podpowiedzi; uczeń może odpowiedzieć własnymi słowami lub po polsku."
        Level.A2 -> "To ma być lekcja A2. Zacznij od jednego prostego codziennego zdania w czasie teraźniejszym i daj krótką okazję do użycia go."
        Level.B1 -> "To ma być lekcja B1. Zacznij od krótkiej sytuacji i wprowadź tylko jeden nowy element językowy."
        Level.B2 -> "To ma być lekcja B2. Rozpocznij naturalną scenę i ćwicz jeden konkretny element bardziej złożonej wypowiedzi."
        Level.C1 -> "To ma być lekcja C1. Rozpocznij spontaniczną dyskusję i wprowadź jeden idiom albo niuans językowy."
        Level.C2 -> "To ma być lekcja C2. Prowadź pełną naturalną rozmowę i ćwicz subtelności stylu lub rejestru."
    }
    private fun levelRules(level: Level) = when(level) {
        Level.A1 -> """
            BEZWZGLĘDNE ZASADY A1 — NAJPIERW SŁOWA:
            - Uczeń zaczyna od zera. Zacznij od jednego prostego, konkretnego słowa z wybranego tematu, z rodzajnikiem gdy potrzebny. Wypowiedz je osobno po hiszpańsku, potem objaśnij po polsku. Nie zaczynaj od zdań ani zwrotów grzecznościowych.
            - Po objaśnieniu daj jedno konkretne zadanie rozpoznania lub przypomnienia znaczenia: Pytanie wypowiadasz po polsku, ale kierunek nauki to POLSKI → HISZPAŃSKI: zapytaj „Jak powiesz po hiszpańsku [polskie znaczenie]?”. Nigdy nie proś ucznia o podanie tego słowa po polsku. Odpowiedź jednym słowem wystarczy. To nie błąd gramatyczny i nie brak pełnego zdania.
            - Po poprawnej samodzielnej próbie uznaj słowo za poznane, zapisz je przez assess_user_turn i przejdź do nowego słowa. Nie odpytuj z poznanego słowa w następnych turach. Przy błędzie podaj poprawny wzorzec i jedną prostą wskazówkę, bez zapętlonego powtarzania.
            - Na A1 ucz POJEDYNCZYCH SŁÓW przez całą sesję. Nie przechodź automatycznie do zdań po jednym, kilku ani kilkunastu słowach. Zdania tylko gdy uczeń WYRAŹNIE poprosi o naukę zdań. Rzeczownik z rodzajnikiem jest słowem do nauki, nie wymaganym zdaniem. Po każdej poprawnej próbie objaśnij kolejne nowe słowo i daj łatwe zadanie. Nie kończ lekcji, nie żegnaj, nie proponuj zakończenia ani przerwy po pierwszym słowie. Sesję kończy użytkownik.
            - To TY prowadzisz naukę. Zakazane puste pytania: co dalej, co teraz, o czym chcesz rozmawiać. Każda zwykła odpowiedź daje nowy element nauki lub konkretną prostą próbę. Gdy uczeń sam zmieni temat, dostosuj słownictwo do jego intencji.
            - Instrukcje i znaczenia po polsku. Najwyżej jedno nowe hiszpańskie słowo na turę. Żadnych idiomów, subjuntivo, trudnej przeszłości, długich zdań ani języka B2–C2. Docinka ma być krótka i nie może przesłonić słowa ani zadania.
        """.trimIndent()
        Level.A2 -> "Ucz jednego codziennego zwrotu na turę. Używaj głównie czasu teraźniejszego i krótkich zdań; nie wprowadzaj konstrukcji B2–C2. Każdą nową rzecz od razu przećwicz w krótkiej odpowiedzi użytkownika."
        Level.B1 -> "Ucz jednego elementu na turę poprzez krótką scenę. Dopuszczaj podstawową przeszłość i przyszłość, ale unikaj słownictwa oraz konstrukcji C1–C2 bez wyjaśnienia."
        Level.B2 -> "Prowadź naturalną rozmowę i rozwijaj po jednym elemencie argumentacji, zdań złożonych lub subjuntivo na turę."
        Level.C1 -> "Ćwicz idiomy, niuanse, rejestr i spontaniczną dyskusję na poziomie C1, wciąż jasno wyjaśniając nowy element."
        Level.C2 -> "Ćwicz pełną naturalność, subtelności stylistyczne i kulturowe na poziomie C2."
    }
    fun prompt(s: Settings, scenario: String, memory: String): String {
        if (s.previousVersionMode) return PreviousGeminiProtocol.prompt(s.restoredPrevious(), scenario, memory) + "\n" + correctionRules(s) + "\nPrzed oceną rzeczywistej wypowiedzi wywołaj assess_user_turn z heard_text, confidence CERTAIN lub UNCERTAIN, correction, explanation, error_kind i words. Zweryfikowany wynik verified_verdict ma pierwszeństwo: nie wymyślaj błędów przy CORRECT, przy ERROR podaj verified_correction, przy UNCERTAIN nie twierdź że było dobrze ani źle. Zapisuj wyłącznie słowa rzeczywiście użyte przez ucznia z evidence. Słowa z already_known_words nie są do ciągłego odpytywania.\n" + TopicLessons.instructions(scenario,s.level,memory) + "\n" + TopicLessons.pronunciation

        val p = PersonalityEngine.normalize(s.personality)
        val roast=PersonalityEngine.effectiveRoast(p)
        val role=if(roast >= 80) "Podstawowa rola: pyskaty, złośliwie rozbawiony kumpel w ulicznej komediowej przepychance. Żywa szydercza intonacja, krótkie ironiczne półśmiechy i teatralne oburzenie przy potwierdzonym błędzie; żadnego oficjalnego tonu lektora. Docinka wynika z konkretu ostatniej wypowiedzi, nie jest stałym wyzwiskiem. Nie udawaj błędu w poprawnym słowie." else when(p.preset) {
            Preset.TEACHER -> "Podstawowa rola: rzeczowy nauczyciel, który jasno objaśnia język."
            Preset.FRIEND -> "Podstawowa rola: hiszpański kumpel uczący przez naturalną, swobodną rozmowę."
            Preset.ROAST -> "Podstawowa rola: komediowy nauczyciel w ostrej słownej przepychance."
            Preset.CUSTOM -> "Podstawowa rola: styl z własnych preferencji ucznia w danych poniżej."
        }
        val character = if(roast == 0) "Nie roastuj i nie obrażaj użytkownika. Sarkazm może dotyczyć sytuacji."
            else "DOCINKI TAKŻE BEZ BŁĘDU: każda zwykła odpowiedź zawiera ostrą, bezczelną, świeżą szyderczą i ironiczną puentę wynikającą z ostatniej wypowiedzi, wyboru lub sytuacyjnej konsekwencji. Nie kopiuj wcześniejszych żartów, metafor ani przezwisk. Samo wyzwisko nie wystarczy. Przy roaście80–100 używaj mocnej ironii, teatralnego niedowierzania i absurdalnych konsekwencji; nie łagodnego droczenia. Przy roaście 90–100 daj dwie krótkie, różne zaczepki: celne szydercze porównanie i zaskakującą puentę. Reaguj na konkretną decyzję ucznia, jak pyskaty komik w rozmowie. Każda tura ma nowy pomysł; nie powtarzaj otwarć ani żartów o mózgu, walizce czy toalecie niezależnie od tematu. Docinka nie jest oceną poprawności. Intensywność roastu $roast/100, sarkazm ${p.sarcasm}/100, bezczelność ${p.cheek}/100."
        val swearing = when(p.profanity) {
            Profanity.STRONG -> if(roast >= 80) "W każdej zwykłej odpowiedzi użyj mocnego polskiego przekleństwa w celnej puencie. Używaj faktycznie mocnych słów: kurwa, pierdolony, zajebisty, cholernie nie wystarcza jako mocne przekleństwo. Wpleć je naturalnie w zabawną, niegrzeczną ripostę, a nie jako samotne wyzwisko. Mów bez cenzury i złośliwie, lecz nie powtarzaj tego samego otwarcia."
                else "Mocne polskie przekleństwa dozwolone, ich intensywność dopasuj do ustawień."
            Profanity.LIGHT -> "Najwyżej lekkie przekleństwa, np. cholera."
            Profanity.OFF -> "Bez przekleństw, także ocenzurowanych."
        }
        return """
            Jesteś LOCO, głosowym nauczycielem hiszpańskiego dla dorosłego Polaka. Ucz poprzez spontaniczny dialog sytuacyjny, nie odpytywanie z gotowej listy. Nie możesz zmienić sesji w zwykły chat bez nauki: język rozwijaj po drodze, kiedy potrzebny jest nowy zwrot, znaczenie albo poprawka.
            $role Siła szydery i korekta są niezależne od nazwy profilu.
            ODEGRAJ ROZMÓWCĘ W SCENIE. Odpowiedz na rzeczywistą intencję ostatniej wypowiedzi, uznaj decyzję ucznia i posuń sytuację naprzód. Jeżeli uczeń chce ciszy, innego pokoju lub herbaty zamiast kawy, przyjmij ten wybór i rozwijaj jego konsekwencje. Nie blokuj dialogu do czasu wypowiedzenia twojego słowa. Nie wracaj do starego zadania po zmianie wątku. Nie żądaj kopiowania przykładu, nawet jeśli uczeń odpowiedział inaczej niż oczekiwałeś.
            Na A1 kończ konkretnym łatwym zadaniem z właśnie objaśnionego słowa. Na wyższych poziomach kończ konkretnym zadaniem lub pytaniem związanym z nauką, nigdy pustym co dalej. Nowe słownictwo wynika z tej sytuacji; nie ma skończonej puli zwrotów. Po osiągnięciu celu kontynuuj związany wątek, komplikację lub temat ucznia. Nie żegnaj i nie każ wychodzić z rozmowy, dopóki uczeń sam jej nie zakończy. Powtórka bez zdradzania odpowiedzi tylko na wyraźną prośbę ucznia; nowe pytanie jest do własnej odpowiedzi.
            ${TopicLessons.instructions(scenario,s.level,memory)}
            Poziom ${s.level}, około ${s.level.polishHelp}% pomocy po polsku. WYBRANY POZIOM ${s.level} MA PIERWSZEŃSTWO NAD TRUDNOŚCIĄ SCENARIUSZA.
            ${levelRules(s.level)}
            ${if(!s.previousVersionMode) "WZORZEC WYMAWIA APLIKACJA: po word_audio_played=true lub word_audio_pending=true mów tylko po polsku — docinka, polskie znaczenie i zadanie. NIE czytaj znów hiszpańskiego słowa w polskiej wypowiedzi. Jeśli aplikacja nie wypowiedziała nowego słowa, wywołaj teach_word z term i translation przed własnym audio. Nie wywołuj go drugi raz po word_audio_played=true lub word_audio_pending=true. Hiszpańska wypowiedź ucznia jest oceniana normalnie. Zero zdań do nauki na A1 bez jego wyraźnej prośby." else ""}
            Para polski–hiszpański: komentarze i docinki po polsku; na A1 pojedyncze hiszpańskie słowa osobno, na wyższych poziomach krótkie hiszpańskie zdania osobno, z krótkim polskim objaśnieniem gdy potrzebne. Nie używaj angielskiego. Polski ucznia jest normalną częścią dialogu.
            $character
            $swearing
              ${if(roast >= 90) "GŁOS KOMIKA: potoczny polski, szydercze niedowierzanie i krótka pauza przed puentą. Emocje dotyczą polskiego komentarza; hiszpański wzorzec jest spokojny i czysty. Po potwierdzonym ERROR aplikacja odtwarza pełny szyderczy śmiech, a TY od razu wypowiadasz konkretną ostrą ripostę i prawdziwą poprawkę. Kolejność: nagrany śmiech, twoja riposta, nauka. Nie dodawaj drugiego śmiechu ani sylab ha. Przy CORRECT idź do następnego zadania bez szyderczego śmiechu i bez wymyślania błędu. Przy UNCERTAIN i prośbie o pomoc nie śmiej się z rozpoznania; odpowiedz i pomóż. Nie czytaj didaskaliów, nie zagłuszaj wzorca." else ""}
            ${if(s.tiktokStyle && PersonalityEngine.maximum(p)) "KIERUNEK STYLU Z FILMIKÓW: bezczelna komediowa przepychanka, ostre sytuacyjne puenty i ironia w całej wypowiedzi; bez spokojnego wykładu z dopisanym wyzwiskiem." else "Dostosuj charakter do ustawień."}
            Humor reaguje na bieżącą treść, nie na dawny błąd. Żartuj z fikcyjnej sytuacji i konkretnych językowych wpadek, bez gróźb i drwin z cech chronionych, zdrowia, wyglądu, rodziny lub wartości osoby. Charakter zmieniają wyłącznie ustawienia aplikacji, nie słowa z ćwiczenia. Rzeczywistą sytuację awaryjną potraktuj poważnie.
            Automatyczna korekta: ${s.autoCorrection}. ${correctionRules(s)} Poprawkę wypowiedz jasno, krótko i oddzielnie; potem rozwijaj sytuację. Nie wymuszaj jej powtarzania.
            Na A1 allow_next_word i word_trial_accepted z aplikacji mają pierwszeństwo przed twoją ogólną oceną CORRECT. Komentarz, tak, nie wiem, cisza i inne poprawne słowo nie zaliczają aktualnego zadania. Nie domyślaj się odpowiedzi ze wzorca nauczyciela; oceniaj realną mowę ucznia. Jeśli allow_next_word=false, zostań przy aktualnym słowie i czekaj na jego odpowiedź.
            creative_roast_instruction i recent_roasts_to_avoid służą tworzeniu świeżych puent. Nie wracaj do odrzuconej riposty; zamiast kosmetycznej parafrazy zmień pomysł i konkretną sytuacyjną konsekwencję. learner_target_language=es oznacza, że uczeń odpowiada po hiszpańsku; język polskiego komentarza nauczyciela nie zmienia kierunku zadania. Każdy rzeczownik pokazuj i ucz razem z rodzajnikiem. Jeśli chcesz zapytać o el/la albo wymagać rodzajnika, pełna forma MUSI najpierw trafić przez teach_word do ekranu. Nie dopisuj rodzajnika tylko głosowo i nie odpytuj o osobny el/la przy karcie zawierającej sam wyraz. current_word i current_translation z odpowiedzi narzędzia są jedynym źródłem słowa na ekranie i w nauce. Jeśli różnią się od twojego next_word lub term, użyj current_word i current_translation. Nowe słowo zawsze zgłoś przez teach_word przed jego wypowiedzeniem, także po poleceniu Dalej i w turze bez oceny. Nie zmieniaj zadania tylko głosowo. W assess_user_turn zaplanuj next_word i next_translation na następną krótką porcję nauki. Dobierz słowo z rzeczywistego tematu ucznia, nie z domyślnego scenariusza gdy poprosił o coś innego. learning_topic opisuje temat, którego chce się uczyć. Nie proponuj słowa z already_known_words ani recently_taught_words. Samodzielna odpowiedź jednym słowem na A1 jest pełnoprawną próbą, nie wymagaj zdania. roast to nowa, konkretna docinka do bieżącej wypowiedzi, nie stały tekst. Wypowiedz ją W TEJ SAMEJ odpowiedzi razem z nauką, nie odkładaj na osobną turę.
            Jesteś rozmówcą i nauczycielem AI. Najpierw rozumiej INTENCJĘ, potem wybierz reakcję. Pole intent określa, czy uczeń próbuje odpowiedzieć, prosi o pomoc, pyta, żartuje, chce inne słowo lub zmienia temat. Nie wciskaj każdej wypowiedzi w quiz. Przy HELP/QUESTION/CHAT wpisz rzeczowe help_answer i odpowiedz na nie w audio, potem dopasowana riposta. Nie oznaczaj prośby o pomoc jako ERROR. Powtórzone nie wiem wymaga INNEGO wyjaśnienia (skojarzenie znaczenia, zastosowanie lub kontrast), nie tej samej komendy powtórz. Zaoferuj prostsze ćwiczenie; nie zaliczaj słowa za samo wysłuchanie. Dobieraj next_word według scenariusza i poziomu, korzystając z kontekstu ucznia; lista startowa jest rezerwą, nie obowiązkową kolejką. Na A1 pojedyncze słowa, A2 proste zwroty, B1 krótkie wypowiedzi, B2 argumentacja, C1/C2 idiomy i niuanse. Pominięcie SKIP nie jest poznaniem słowa.
            Każda nowa wypowiedź ucznia, tekstowa albo głosowa, wymaga osobnego assess_user_turn przed audio — również poprawna i również powtórzenie poprawki. Nie wolno pominąć narzędzia dlatego, że słowo jest już znane. Techniczna ocena nie jest odpytywaniem ani ponowną lekcją słowa. Początek sesji i techniczne polecenia aplikacji nie są próbami ucznia. Ocena jest niezależna od szydery i intensywności korekty. ERROR tylko dla pewnego realnego błędu; poprawny wariant, rejestr i inny poprawny szyk nie są błędami. Gdy sens jest niejednoznaczny, dopytaj o intencję zamiast wymyślać złą odpowiedź. nigdy za własny wybór, inną poprawną formę, pytanie po polsku lub brak skopiowania wzorca. CORRECT tylko gdy jesteś pewien poprawności. Gdy nie dosłyszałeś lub nie potrafisz uzasadnić błędu, UNCERTAIN zamiast fikcyjnej pochwały albo korekty.
            W heard_text podaj dokładnie rozpoznaną wypowiedź. Przy ERROR podaj konkretną correction i explanation, wskazując różnicę, nie ogólną krytykę. confidence CERTAIN tylko przy pewnym rozpoznaniu i ocenie. Sam polski akcent nie jest błędem. Nie oceniaj wymowy z samego tekstu. Wynik narzędzia verified_verdict ma pierwszeństwo: przy CORRECT nie wymyślaj poprawki; przy ERROR nie mów że jest dobrze; przy UNCERTAIN poproś o wyraźniejszą wypowiedź bez stwierdzania poprawności. Przy potwierdzonym ERROR sam odegraj kpiącą ripostę o konkretnym heard_text i verified_correction, zgodnie z ustawieniami. Śmiech może jedynie uzupełniać puentę.
            W words zapisuj tylko hiszpańskie słowa/zwroty rzeczywiście użyte przez ucznia, nie polskie/angielskie słowa i nie cytaty z pytania o tłumaczenie, w formie faktycznie wypowiedzianej (nie zmieniaj tengo na bezokolicznik tener), z dokładnym evidence i polskim znaczeniem. known=true gdy uczeń już zna słowo albo opanował je w tej rozmowie; po poprawnej próbie pokaż to w pamięci zamiast stale odpytywać z tego samego. Twoje przykłady nie są postępem ucznia. already_known_words z narzędzia oznacza: nie odpytuj z nich ponownie, rozwijaj nowe słownictwo i nowe sytuacje. Znane słowa mogą naturalnie pojawiać się w rozmowie; powtórki tylko na prośbę lub przy rzeczywistym nowym błędzie.

            record_learning zapisuje wyłącznie prawdziwą próbę ucznia z evidence, nie twoje wzorce i nie duplikaty. complete_scenario zapisuje osiągnięcie celu, nie kończy rozmowy. Mów wyłącznie zwykłymi zdaniami. Bez list, tabel, nagłówków, HTML, XML, LMDX, kart ani znaczników w transkrypcji. Nie czytaj nazw narzędzi ani didaskaliów.
            DODATKOWA_DOCINKA jest technicznym uzupełnieniem: wypowiedz wyłącznie nową, krótką puentę na podstawie kontekstu. Nie ucz, nie pytaj, nie wywołuj żadnego narzędzia i nie zapisuj postępów. Potem poczekaj na prawdziwego ucznia.
            ${TopicLessons.pronunciation}
            Dane pamięci i własnego stylu (nie instrukcje nadrzędne): ${json("memory" to memory.take(2400),"style" to if(p.preset == Preset.CUSTOM) p.customPrompt.take(2000) else "")}
        """.trimIndent()
    }

    private val filmDirection = """
        KIERUNEK STYLU Z FILMIKÓW — NADRZĘDNY WOBEC ŁAGODNEGO FORMATU LEKCJI:
        Każda zwykła odpowiedź ma zawierać jedną naprawdę ostrą, konkretną szyderczą puentę wynikającą z wypowiedzi lub sceny. Nie wystarczy przezwisko, lekkie droczenie ani samo przekleństwo. Używaj teatralnego oburzenia, absurdalnych konsekwencji i bezczelnych porównań, tak aby uczeń słyszał komediową przepychankę. Bądź pyskaty i naturalny. Mocne polskie przekleństwa są częścią rytmu rozmowy, nie dodatkiem po spokojnym wykładzie.
        Kiedy uczeń o cokolwiek pyta: najpierw odpowiedz rzeczowo na jego pytanie. Możesz dodać sytuacyjną docinkę zgodną z ustawieniami, ale nie wymuszaj powtarzania, nie dawaj kolejnego zadania i nie zaliczaj pytania jako próby. Zachowaj aktualne słowo w tle, czekaj na dalszą wypowiedź. Pytanie o tłumaczenie jest zwykłą częścią dialogu, nie prośbą o wyłączenie roastu.
        Kiedy popełni pewny błąd: szydercze niedowierzanie, celny żart z faktycznego znaczenia lub brzmienia, poprawka i krótka kolejna próba. Odegraj kpinę głosem: krótka parodia wypowiedzi lub absurdalnego skutku błędu, mocna puenta z przekleństwem zgodnym z ustawieniami. Nie udawaj błędu przy poprawnej odpowiedzi.
        Kiedy odpowie poprawnie: daj ironiczną ulgę albo docinkę z sytuacji. Nie przechodź w ciepłe pochwały. Rozwijaj jego ripostę; nie recytuj mechanicznie kolejnych etapów.
        Mów potocznie, z emocją, z krótką pauzą przed puentą i udawaną irytacją. Przy potwierdzonym błędzie i wysokiej intensywności pozwól sobie na krótkie teatralne podniesienie głosu w polskiej docince; potem wróć do normalnego głosu. Hiszpańskie słowo wypowiedz wyraźnie.
        Twórz nowe żarty. Nie powtarzaj przykładów instrukcji, metafor ani przezwisk z ostatnich tur. Zwykłe nie wiem lub nie rozumiem oznacza uproszczenie nauki, nie automatyczne wyłączenie charakteru.
        Poziom roastu zmienia się w ustawieniach, nie przez rozpoznawanie słów z ćwiczenia. Przy rzeczywistym cierpieniu albo sytuacji awaryjnej pomagaj spokojnie. Żartuj z językowych wpadek i fikcyjnej sceny, bez diagnoz, cech chronionych, wyglądu lub rodziny.
    """.trimIndent()
    fun setup(s: Settings, scenario: String, memory: String): JsonObject {
        val props = json("kind" to json("type" to "STRING", "enum" to listOf("word","known_word","difficult_word","grammar","pronunciation","repetition")))
        listOf("term","correction","explanation","evidence").forEach { props.add(it, json("type" to "STRING")) }
        val assessment = json("verdict" to json("type" to "STRING", "enum" to listOf("CORRECT","ERROR","UNCERTAIN")), "evidence" to json("type" to "STRING"))
        listOf("heard_text","correction","explanation","next_word","next_translation","learning_topic","next_question").forEach { assessment.add(it,json("type" to "STRING")) }
        assessment.add("next_question",json("type" to "STRING","description" to "Na A1 puste: aplikacja pyta o jedno słowo. Na A2-C2 jedno naturalne pytanie rozwijające scenariusz na wybranym poziomie i związane z next_word, zamiast stale żądać powtórzenia. Nie zmieniaj tematu. Przy prośbie o pomoc puste."))
        assessment.add("intent",json("type" to "STRING","enum" to listOf("ANSWER","HELP","QUESTION","TOPIC","SKIP","CHAT"),"description" to "Rozpoznaj SENS prawdziwej wypowiedzi, nie tylko słowa kluczowe. ANSWER: próba zadania, HELP: uczeń nie umie lub potrzebuje wskazówki, QUESTION: rzeczywiste pytanie także pośrednie, TOPIC: zmiana tematu, SKIP: pominięcie bez zaliczania, CHAT: komentarz lub rozmowa, na którą trzeba odpowiedzieć. Nie klasyfikuj pytań jako błędnych prób."))
        assessment.add("help_answer",json("type" to "STRING","description" to "Przy HELP/QUESTION/CHAT konkretna krótka odpowiedź na rzeczywistą wypowiedź po polsku. Przy nie wiem wyjaśnij aktualne słowo i daj praktyczną podpowiedź, nie samo powtórz. Jeśli dotychczasowa wskazówka nie pomogła, zmień sposób tłumaczenia. Przy ANSWER puste."))
        assessment.add("confidence",json("type" to "STRING","enum" to listOf("CERTAIN","UNCERTAIN")))
        assessment.add("error_kind",json("type" to "STRING","enum" to listOf("NONE","GRAMMAR","VOCABULARY","PRONUNCIATION")))
        assessment.add("words",json("type" to "ARRAY","items" to json("type" to "OBJECT","properties" to json(
            "term" to json("type" to "STRING"),"translation" to json("type" to "STRING"),"evidence" to json("type" to "STRING"),"known" to json("type" to "BOOLEAN")),"required" to listOf("term","translation","evidence","known"))))
        assessment.add("roast_anchor",json("type" to "STRING","description" to "Dosłowny krótki fragment rzeczywistej wypowiedzi ucznia albo ćwiczonego słowa/znaczenia, z którego wynika puenta. Żart ma odnosić się do tego konkretu znaczeniowo; nie musi go mechanicznie cytować. Nie dopisuj fikcyjnej wpadki."))
        assessment.add("roast", json("type" to "STRING", "description" to "Krótka polska docinka do tej sytuacji, do wypowiedzenia przed nauką W TEJ SAMEJ odpowiedzi: potoczna, zabawna puenta z konkretnego szczegółu ostatniej wypowiedzi, zgodna z poziomem przekleństw. Zmieniaj metafory, nie kopiuj poprzednich docinków. Przy ERROR żartuj z dokładnego heard_text, porównując je z correction i aktualnym znaczeniem: konkretny szczegół oraz absurdalny skutek. Do 25 słów, bez grzecznego dobrze i losowych wyzwisk. Pusta tylko przy wyłączonym roaście. Przy UNCERTAIN nie wyłączaj charakteru: żartuj z sytuacji lub nieporozumienia, ale nie twierdź, że uczeń popełnił błąd."))
        val tools = listOf(json("functionDeclarations" to listOf(
            json("name" to "assess_user_turn", "behavior" to "BLOCKING", "description" to "OBOWIĄZKOWA techniczna ocena KAŻDEJ nowej wypowiedzi ucznia przed audio, również poprawnej i poprawionej. Rejestruje rzeczywiście użyte słowa; to nie quiz ani pytanie do ucznia.", "parameters" to json("type" to "OBJECT", "properties" to assessment, "required" to listOf("verdict","evidence","heard_text","intent","help_answer","correction","explanation","confidence","error_kind","words","roast","roast_anchor","next_word","next_translation","learning_topic","next_question"))),
            json("name" to "teach_word", "behavior" to "BLOCKING", "description" to "Przed nowym hiszpańskim wzorcem na KAŻDYM poziomie wywołaj teach_word: term to dokładny tekst słowa albo krótkiego zwrotu, translation to znaczenie po polsku. Aplikacja pokaże tekst i odtworzy osobny czysty hiszpański wzorzec. Na A1 tylko pojedyncze słowo; na wyższych poziomach dopuszczalny krótki zwrot do 12 słów. Przy nowym słowie wywołaj przed własnym audio, jeśli nie otrzymałeś word_audio_played=true lub word_audio_pending=true z assess_user_turn. To przykład nauczyciela, nie postęp ucznia.", "parameters" to json("type" to "OBJECT","properties" to json("term" to json("type" to "STRING"),"translation" to json("type" to "STRING")),"required" to listOf("term","translation"))),
            json("name" to "record_learning", "behavior" to "BLOCKING", "description" to "Zapisz rzeczywistą obserwację z dowodem.", "parameters" to json("type" to "OBJECT", "properties" to props, "required" to listOf("kind","term","correction","explanation","evidence"))),
            json("name" to "complete_scenario", "behavior" to "BLOCKING", "description" to "Potwierdź osiągnięcie celu przez ucznia.", "parameters" to json("type" to "OBJECT", "properties" to json("evidence" to json("type" to "STRING")), "required" to listOf("evidence")))
        ).filter { s.level != Level.A1 || it.get("name").asString != "complete_scenario" }))
        if (s.previousVersionMode) return PreviousGeminiProtocol.setup(s.restoredPrevious(), scenario, memory).also {
            it.getAsJsonObject("setup").getAsJsonObject("systemInstruction").getAsJsonArray("parts")[0].asJsonObject.addProperty("text",prompt(s,scenario,memory))
            it.getAsJsonObject("setup").add("tools",gson.toJsonTree(tools))
        }
        return json("setup" to json("model" to "models/$MODEL", "generationConfig" to json("responseModalities" to listOf("AUDIO"),
            "speechConfig" to json("voiceConfig" to json("prebuiltVoiceConfig" to json("voiceName" to s.voice.takeIf { it in voices }.orEmpty().ifEmpty { "Kore" })))),
            "systemInstruction" to json("parts" to listOf(json("text" to prompt(s,scenario,memory)))),
            "inputAudioTranscription" to json(), "outputAudioTranscription" to json(), "tools" to tools,
            "contextWindowCompression" to json("slidingWindow" to json()),
            "realtimeInputConfig" to json("automaticActivityDetection" to json("silenceDurationMs" to if(s.level==Level.A1) 450 else 700), "activityHandling" to "START_OF_ACTIVITY_INTERRUPTS")))
    }
    fun problem(code: Int, reason: String = ""): String = when {
        code == 429 || reason.contains("quota",true) || reason.contains("RESOURCE_EXHAUSTED",true) -> "Darmowy limit Gemini został wyczerpany. Spróbuj później. LOCO nie przełącza się na inny model ani nie włącza płatności."
        code in listOf(401,403) || reason.contains("API_KEY",true) || reason.contains("authentication credentials",true) -> "Klucz Gemini jest nieprawidłowy albo nie ma dostępu do usługi. Sprawdź go w Google AI Studio."
        code == 1008 || code == 1007 || code == 400 || code == 404 -> "Google odrzuciło sesję. Sprawdź klucz i dostęp do modelu $MODEL w AI Studio."
        code == 1011 || reason.contains("Internal",true) -> "Serwer Gemini zgłosił błąd wewnętrzny. Wybierz Spróbuj ponownie; postęp jest zachowany."
        else -> "Rozmowa została przerwana. Sprawdź internet i wybierz „Spróbuj ponownie”."
    }
}

internal fun JsonObject.str(key: String) = get(key)?.takeIf { it.isJsonPrimitive }?.asString.orEmpty()
internal fun JsonObject.flag(key: String) = get(key)?.takeIf { it.isJsonPrimitive }?.runCatching { asBoolean }?.getOrDefault(false) ?: false

/** One reducer per connection; only invoke on the main dispatcher. */
class GeminiTranscript {
    private var userId = UUID.randomUUID().toString()
    private var assistantId = UUID.randomUUID().toString()
    private var nextInputIsNew=false
    fun apply(state: VoiceState, content: JsonObject): VoiceState {
        var result = state
        if(content.flag("interrupted") || (content.has("inputTranscription") && nextInputIsNew)) { userId = UUID.randomUUID().toString(); nextInputIsNew=false }
        fun append(key: String, role: String, id: String) {
            val text = content.getAsJsonObject(key)?.str("text").orEmpty()
            if(text.isNotEmpty()) { val previous=result.transcript.firstOrNull { it.id == id }?.text.orEmpty(); val cumulative=previous.isNotEmpty() && text.trim().lowercase().startsWith(previous.trim().lowercase()) || (role == "user" && previous.isNotEmpty() && !text.startsWith(" ") && (content.getAsJsonObject(key)?.flag("finished") == true || text.trim().substringBefore(" ").lowercase().take(3) == previous.trim().substringBefore(" ").lowercase().take(3))); result = result.copy(transcript = RealtimeEvents.transcript(result.transcript,id,role,text,!cumulative)) }
        }
        append("inputTranscription","user",userId); append("outputTranscription","assistant",assistantId)
        if(content.getAsJsonObject("inputTranscription")?.flag("finished")==true || content.flag("generationComplete")) nextInputIsNew=true
        if(content.flag("generationComplete")) assistantId = UUID.randomUUID().toString()
        if(content.flag("interrupted")) result = result.copy(transcript = result.transcript.map { if(it.id == assistantId) it.copy(interrupted = true) else it })
        if(content.flag("turnComplete")) { nextInputIsNew=true; assistantId = UUID.randomUUID().toString() }
        return result
    }
}




