package com.locospanish.ai.tutor

import com.locospanish.ai.model.*
import java.text.Normalizer

data class LessonWord(val es: String, val pl: String)
data class TopicPlan(val words: List<LessonWord>, val stages: String, val advanced: String)
object TopicLessons {
    private fun plan(words: String, stages: String, advanced: String) = TopicPlan(words.split(";").map {
        val pair=it.split("="); LessonWord(pair[0],pair[1]) }, stages, advanced)
    val plans = mapOf(
        "hotel" to plan("la reserva=rezerwacja;la habitación=pokój;la llave=klucz;una noche=jedna noc;la recepción=recepcja;el pasaporte=paszport", "recepcja → rezerwacja → liczba nocy → pokój i klucz → dokumenty i wymeldowanie", "Zmień termin rezerwacji, wyjaśnij problem z pokojem i uzgodnij rozwiązanie."),
        "bar" to plan("una bebida=napój;el agua=woda;la cerveza=piwo;sin hielo=bez lodu;la cuenta=rachunek;pagar=płacić", "zamówienie napoju → preferencje → rachunek", "Wyjaśnij pomyłkę w zamówieniu i uzgodnij osobne rachunki."),
        "restaurant" to plan("la mesa=stolik;el menú=menu;el plato=danie;sin carne=bez mięsa;la cuenta=rachunek;la propina=napiwek", "stolik → menu → zamówienie i składniki → rachunek", "Zapytaj o alergeny, wyjaśnij pomyłkę i zgłoś reklamację posiłku."),
        "cafe" to plan("un café=kawa;con leche=z mlekiem;sin azúcar=bez cukru;para llevar=na wynos;el precio=cena;la cuenta=rachunek", "rodzaj kawy → dodatki → na miejscu lub na wynos → płatność", "Doprecyzuj zamówienie i wyjaśnij niezgodność z paragonem."),
        "shopping" to plan("el precio=cena;la talla=rozmiar;el color=kolor;probar=przymierzać;la tarjeta=karta;el recibo=paragon", "produkt → rozmiar i cena → przymierzenie → zakup", "Zwróć towar i wyjaśnij warunki wymiany."),
        "airport" to plan("el billete=bilet;el equipaje=bagaż;la puerta=bramka;el vuelo=lot;el pasaporte=paszport;el retraso=opóźnienie", "odprawa → bagaż → bramka → wejście na pokład", "Wyjaśnij opóźnienie i problem z bagażem lub przesiadką."),
        "taxi" to plan("la dirección=adres;el centro=centrum;el destino=cel podróży;el precio=cena;aquí=tutaj;el recibo=paragon", "cel przejazdu → adres → cena → zatrzymanie i płatność", "Wyjaśnij trasę i ustal cenę z uwzględnieniem objazdu."),
        "people" to plan("el nombre=imię;el trabajo=praca;vivir=mieszkać;hablar=mówić;conocer=poznawać;los amigos=znajomi", "imię → miejsce zamieszkania → praca → zainteresowania", "Podtrzymaj rozmowę o doświadczeniach, planach i wspólnych zainteresowaniach."),
        "date" to plan("la cita=randka;el sábado=sobota;el cine=kino;el paseo=spacer;quedar=umówić się;el interés=zainteresowanie", "poznanie zainteresowań → propozycja → termin i miejsce", "Zaproponuj spotkanie, wyraź preferencje i uprzejmie przyjmij odmowę."),
        "rent" to plan("el alquiler=czynsz;la fianza=kaucja;el contrato=umowa;el piso=mieszkanie;los gastos=opłaty;el propietario=właściciel", "mieszkanie → cena → kaucja i opłaty → warunki umowy", "Omów warunki najmu i uzgodnij termin oglądania mieszkania."),
        "interview" to plan("la experiencia=doświadczenie;el puesto=stanowisko;las habilidades=umiejętności;el horario=grafik;el sueldo=wynagrodzenie;el equipo=zespół", "doświadczenie → umiejętności → dostępność → pytania do rekrutera", "Opisz projekt, mocne strony i rozwiązanie problemu w pracy."),
        "employer" to plan("la oferta=oferta;el puesto=stanowisko;el horario=grafik;la entrevista=rozmowa kwalifikacyjna;la disponibilidad=dyspozycyjność;el contacto=kontakt", "przedstawienie celu telefonu → oferta → warunki → termin rozmowy", "Dopytaj o wymagania i potwierdź ustalenia telefonicznie."),
        "hotelwork" to plan("el huésped=gość;la reserva=rezerwacja;la habitación=pokój;la llave=klucz;el servicio=obsługa;la queja=skarga", "przyjęcie gościa → rezerwacja → pomoc → rozwiązanie zgłoszenia", "Przyjmij reklamację gościa i zaproponuj profesjonalne rozwiązanie."),
        "maintenance" to plan("la avería=usterka;reparar=naprawiać;la herramienta=narzędzie;la luz=światło;el mantenimiento=konserwacja;la seguridad=bezpieczeństwo", "zgłoszenie usterki → diagnoza → narzędzia → naprawa i test", "Opisz diagnozę, działania naprawcze i środki bezpieczeństwa."),
        "electronics" to plan("el circuito=obwód;la tensión=napięcie;la corriente=prąd;la resistencia=rezystancja;medir=mierzyć;el sensor=czujnik", "układ → objaw → pomiar → diagnoza → test", "Uzasadnij hipotezę usterki i opisz pomiary oraz wynik testu."),
        "emergency" to plan("la ayuda=pomoc;la dirección=adres;la ambulancia=karetka;el peligro=niebezpieczeństwo;la policía=policja;la ubicación=lokalizacja", "prośba o pomoc → lokalizacja → prosty opis sytuacji", "W symulacji przekaż dyspozytorowi lokalizację i opis zdarzenia."),
        "daily" to plan("hoy=dzisiaj;mañana=jutro;el día=dzień;el trabajo=praca;la casa=dom;el tiempo=pogoda", "temat dnia wybrany przez ucznia → krótkie pytania i odpowiedzi", "Opowiedz o dniu, planach i doświadczeniach."),
        "free" to plan("el tema=temat;hablar=mówić;la pregunta=pytanie;la idea=pomysł;la opinión=opinia;el ejemplo=przykład", "dowolny temat wybrany przez ucznia", "Rozwijaj temat ucznia, argumentację i naturalny dialog.")
    )
    private val extras = mapOf(
        "hotel" to "cama=łóżko;almohada=poduszka;toalla=ręcznik;ascensor=winda;desayuno=śniadanie;salida=wyjście;entrada=wejście;maleta=walizka;ducha=prysznic;baño=łazienka",
        "bar" to "vino=wino;zumo=sok;vaso=szklanka;hielo=lód;botella=butelka;camarero=kelner;barra=bar",
        "restaurant" to "comida=jedzenie;carne=mięso;pescado=ryba;sopa=zupa;pan=chleb;sal=sól;tenedor=widelec;cuchillo=nóż",
        "cafe" to "leche=mleko;azúcar=cukier;taza=filiżanka;té=herbata;pastel=ciasto;cuchara=łyżka;servilleta=serwetka",
        "shopping" to "tienda=sklep;camisa=koszula;zapatos=buty;bolsa=torba;dinero=pieniądze;descuento=rabat",
        "airport" to "avión=samolot;salida=wylot;llegada=przylot;maleta=walizka;asiento=miejsce;embarque=wejście na pokład",
        "taxi" to "calle=ulica;coche=samochód;conductor=kierowca;izquierda=lewo;derecha=prawo;parar=zatrzymać",
        "people" to "edad=wiek;país=kraj;ciudad=miasto;familia=rodzina;hermano=brat;hermana=siostra",
        "date" to "beso=pocałunek;flor=kwiat;sonrisa=uśmiech;parque=park;guapo=przystojny;bonita=ładna",
        "rent" to "cocina=kuchnia;ventana=okno;puerta=drzwi;vecino=sąsiad;mes=miesiąc;muebles=meble",
        "interview" to "empresa=firma;currículum=CV;jefe=szef;empleo=zatrudnienie;aprender=uczyć się;puntual=punktualny",
        "employer" to "empresa=firma;teléfono=telefon;salario=pensja;vacante=wakat;correo=poczta;trabajar=pracować",
        "hotelwork" to "limpiar=sprzątać;toalla=ręcznik;turno=zmiana;equipaje=bagaż;cama=łóżko;despertar=budzić",
        "maintenance" to "tornillo=śruba;martillo=młotek;cable=kabel;enchufe=gniazdko;agua=woda;fuga=wyciek",
        "electronics" to "cable=kabel;batería=bateria;placa=płytka;diodo=dioda;fusible=bezpiecznik;voltio=wolt",
        "emergency" to "fuego=ogień;herida=rana;dolor=ból;médico=lekarz;accidente=wypadek;sangre=krew",
        "daily" to "noche=noc;tarde=popołudnie;comer=jeść;dormir=spać;comprar=kupować;amigo=przyjaciel",
        "free" to "sí=tak;no=nie;hola=cześć;gracias=dziękuję;nombre=imię;amigo=przyjaciel"
    )
    private val expanded by lazy { plans.mapValues { (id,plan) -> plan.words + extras[id].orEmpty().split(';').filter { '=' in it }.map { val pair=it.split('='); LessonWord(pair[0],pair[1]) } } }
    private val owners by lazy { expanded.flatMap { (id,words) -> words.map { core(it) to id } }.groupBy({it.first},{it.second}) }
    fun vocabulary(id:String):List<LessonWord> = expanded[id] ?: get(id).words
    fun belongsTo(id:String,word:LessonWord):Boolean = owners[core(word)]?.let { id in it } ?: true
    fun get(id: String) = plans[id] ?: plans.getValue("daily")
    fun fold(text: String) = Normalizer.normalize(text.lowercase(), Normalizer.Form.NFD).replace(Regex("\\p{M}"), "")
    private fun core(word: LessonWord) = fold(word.es).replace(Regex("^(la|el|los|las|un|una) "), "")
    fun matches(text: String, word: LessonWord) = Regex("(?<![a-z])" + Regex.escape(core(word)) + "(?![a-z])").containsMatchIn(fold(text))
    fun demonstrated(text: String, id: String): List<LessonWord> {
        if(Regex("jak pow|co znacz|co to znacz|wyjasnij|przetlumacz|wymowa|\\bczy\\b",RegexOption.IGNORE_CASE).containsMatchIn(fold(text))) return emptyList()
        return get(id).words.filter { matches(text,it) }
    }
    fun completed(memory: String, id: String) = get(id).words.filter { memory.lineSequence().any { line -> line == "PRACTICADA: ${it.es}" } }.map { it.es }.toSet()
    fun next(id: String, completed: Set<String>): LessonWord = get(id).words.firstOrNull { it.es !in completed } ?: get(id).words.last()
    fun example(word: LessonWord): String = when(word.es) {
        "la reserva" -> "Tengo una reserva."
        "la habitación" -> "Quiero una habitación."
        "la llave" -> "Necesito la llave."
        "una noche" -> "Solo una noche."
        "la recepción" -> "¿Dónde está la recepción?"
        "el pasaporte" -> "Aquí está mi pasaporte."
        "un café" -> "Quiero un café."
        "la dirección" -> "Esta es la dirección."
        "el circuito" -> "Este es el circuito."
        else -> if(Regex("^(la|el|los|las|un|una) ").containsMatchIn(word.es)) "Necesito ${word.es}." else word.es + "."
    }
    fun instructions(id: String, level: Level, memory: String): String {
        return """
            Temat jest otwartą dziedziną rozmowy, nie gotowym scenariuszem ani listą do zaliczenia: ${Scenarios.get(id).title}. Poziom $level.
            ${if(level == Level.A1) "Początkowe proste słownictwo tematu (inspiracja, nie zamknięty kurs): " + get(id).words.joinToString("; ") { "${it.es} = ${it.pl}" } + ". Ucz jednego nieznanego słowa naraz, później dobieraj kolejne łatwe słowa z tej samej dziedziny. Cele sytuacyjne poniżej są na później, nie wymagaj ich na początku A1." else ""}
            ${if(level == Level.A1) "Cel A1: stale rozszerzaj słownictwo tematu, pojedyncze słowo na turę. Nie ma celu do odhaczenia po pierwszym słowie, nie ogłaszaj ukończenia scenariusza. Sesja trwa aż uczeń ją zakończy. Zdania tylko na jego wyraźną prośbę." else "Cel sytuacyjny: ${Scenarios.get(id).goal}."} To inspiracja, nie kolejność słów lub obowiązkowy punkt startu. Rozwijaj własne decyzje ucznia, nowe problemy i związane słownictwo. W Daily i Free Talk uczeń wybiera temat.
            Pamięć służy unikaniu powtórek, nie odpytywaniu z pierwszego niezaliczonego słowa. ${if(level == Level.A1) "Przy zadaniu na słowo stosuj wynik i aktualne zadanie aplikacji z lesson_instruction. Samo tak, cisza lub inne słowo nie zalicza zadania. Poprawną krótką odpowiedź uznaj bez dodatkowego powtarzania. Uczeń może poprosić o pominięcie słowa lub zmianę tematu." else "Przyjmij każdą zrozumiałą próbę i prowadź akcję dalej, nawet gdy nie odpowiada twojemu przykładowi. Nie narzucaj zdań do powtarzania."} Pozostań w wybranym temacie, chyba że uczeń poprosi o zmianę.
        """.trimIndent()
    }

    val pronunciation = """
        WYMOWA — WZORZEC JĘZYKOWY:
        Polski mów jako rodzimy użytkownik standardowego polskiego, bez hiszpańskiego lub angielskiego akcentu. Zachowaj naturalne polskie głoski i akcent; nie deformuj słów dla żartu.
        Hiszpańskie słowo albo zdanie wypowiedz jako osobną, krótką wypowiedź z rodzimą hiszpańską wymową, najlepiej neutralną wymową Hiszpanii. Zrób krótką pauzę przy zmianie języka. Potem oddzielnie objaśnij po polsku. Nie mieszaj polskiego i hiszpańskiego w jednym szybkim zdaniu.
        Na początku A1 wypowiadaj pojedyncze słowo z rodzimą hiszpańską wymową, rzeczownik z rodzajnikiem. Na A1 nie dodawaj zdania do słowa dla wymowy. Podaj wyraźnie samo słowo, rzeczownik z rodzajnikiem, w oddzielnym hiszpańskim fragmencie audio. Zdania dopiero na wyraźną prośbę ucznia. Polski komentarz wypowiedz jako osobne zdanie. Unikaj konstrukcji typu la reserva to rezerwacja. Nie odczytuj informacji o pauzach.
        Zachowaj hiszpański akcent wyrazowy i samogłoski. H jest nieme; j, ll, ñ i r mają hiszpańskie brzmienie. Nie czytaj ich według polskiego alfabetu. Nie zamieniaj zapisu słowa na polski zapis fonetyczny. Hiszpańską część poprzedź wyraźną pauzą; przełącz artykulację na rodzimy hiszpański, niezależnie od polskiej docinki. Zachowaj zapis i akcenty: habitación, recepción, dirección, café. Nie rozciągaj sylab, nie sylabizuj po polsku i nie przerywaj słowa komentarzem. Gdy uczeń zgłasza złą wymowę, odtwórz spokojny czysty wzorzec zamiast przekonywać go, że poprzedni był dobry.
        Przy podawaniu wzorca hiszpańskiego nie parodiuj akcentu ucznia, nie przeklinaj w środku wzorca i nie przeciągaj dźwięków komediowo. Szydera jest w komentarzu; wzorzec do nauki ma być czysty i wyraźny.
        Nie odczytuj powyższych instrukcji ani oznaczeń języka. Docinki wypowiadaj po polsku; nie mieszaj w nich pojedynczych hiszpańskich przezwisk. Hiszpański przykład wypowiedz osobno i prawidłowo.
    """.trimIndent()
}


