package com.locospanish.ai.tutor

import com.locospanish.ai.model.Level

/** Starting material above A1: short phrases on A2, whole conversational sentences from B1 up. */
object LevelPhrases {
    private fun parse(text: String) = text.split(";").map { it.split("=", limit = 2) }.map { LessonWord(it[0].trim(), it[1].trim()) }
    private val a2 = mapOf(
        "daily" to "¿Qué tal tu día?=Jak minął twój dzień?;Estoy un poco cansado=Jestem trochę zmęczony;Hoy trabajo hasta tarde=Dziś pracuję do późna;Tengo mucha hambre=Jestem bardzo głodny",
        "bar" to "Una cerveza, por favor=Piwo, proszę;¿Cuánto cuesta una copa de vino?=Ile kosztuje kieliszek wina?;Sin hielo, por favor=Bez lodu, proszę;¿Me cobras, por favor?=Policzysz mi, proszę?",
        "restaurant" to "Una mesa para dos, por favor=Stolik dla dwóch osób, proszę;¿Qué me recomienda?=Co pan poleca?;Quiero la carne poco hecha=Chcę krwiste mięso;La cuenta, por favor=Rachunek, proszę",
        "cafe" to "Un café con leche, por favor=Kawa z mlekiem, proszę;Para llevar, por favor=Na wynos, proszę;¿Tienen leche de avena?=Macie mleko owsiane?;Quiero un cruasán también=Chcę też rogalika",
        "shopping" to "¿Cuánto cuesta esto?=Ile to kosztuje?;¿Tiene una talla más grande?=Ma pan większy rozmiar?;Solo estoy mirando=Tylko się rozglądam;¿Puedo pagar con tarjeta?=Mogę zapłacić kartą?",
        "hotel" to "Tengo una reserva=Mam rezerwację;Quiero una habitación doble=Chcę pokój dwuosobowy;¿A qué hora es el desayuno?=O której jest śniadanie?;¿Dónde está el ascensor?=Gdzie jest winda?",
        "airport" to "¿Dónde está la puerta de embarque?=Gdzie jest bramka?;Tengo una maleta para facturar=Mam walizkę do nadania;Mi vuelo sale a las diez=Mój lot odlatuje o dziesiątej;Aquí está mi pasaporte=Oto mój paszport",
        "taxi" to "Al centro, por favor=Do centrum, proszę;¿Cuánto cuesta ir al aeropuerto?=Ile kosztuje dojazd na lotnisko?;Pare aquí, por favor=Proszę się tu zatrzymać;Tengo prisa=Spieszę się",
        "people" to "¿Cómo te llamas?=Jak masz na imię?;Soy de Polonia=Jestem z Polski;¿A qué te dedicas?=Czym się zajmujesz?;Encantado de conocerte=Miło cię poznać",
        "date" to "¿Quieres tomar algo?=Chcesz się czegoś napić?;¿Qué te gusta hacer?=Co lubisz robić?;Me gusta mucho este sitio=Bardzo lubię to miejsce;¿Quedamos mañana?=Spotkamy się jutro?",
        "rent" to "¿Cuánto es el alquiler?=Ile wynosi czynsz?;¿Está amueblado el piso?=Czy mieszkanie jest umeblowane?;¿Están incluidos los gastos?=Czy opłaty są wliczone?;Quiero ver el piso=Chcę obejrzeć mieszkanie",
        "interview" to "Tengo experiencia en hoteles=Mam doświadczenie w hotelach;Hablo polaco e inglés=Mówię po polsku i po angielsku;Puedo empezar el lunes=Mogę zacząć w poniedziałek;Trabajo bien en equipo=Dobrze pracuję w zespole",
        "employer" to "Llamo por la oferta de trabajo=Dzwonię w sprawie oferty pracy;¿Sigue disponible el puesto?=Czy stanowisko jest nadal wolne?;¿Cuál es el horario?=Jakie są godziny pracy?;¿Cuánto pagan por hora?=Ile płacicie za godzinę?",
        "hotelwork" to "¿En qué puedo ayudarle?=W czym mogę pomóc?;Ahora mismo lo arreglo=Zaraz to naprawię;Lo siento mucho=Bardzo mi przykro;Aquí tiene su llave=Oto pański klucz",
        "maintenance" to "La luz no funciona=Światło nie działa;Necesito una llave inglesa=Potrzebuję klucza francuskiego;Voy a cambiar la bombilla=Wymienię żarówkę;Hay una fuga de agua=Jest wyciek wody",
        "electronics" to "El circuito no funciona=Układ nie działa;Necesito medir el voltaje=Muszę zmierzyć napięcie;Este cable está roto=Ten kabel jest uszkodzony;Voy a soldar esta pieza=Przylutuję ten element",
        "emergency" to "¡Necesito ayuda!=Potrzebuję pomocy!;Llame a una ambulancia=Proszę wezwać karetkę;Estoy en la calle Mayor=Jestem na ulicy Mayor;Me duele mucho=Bardzo mnie boli",
        "free" to "Me gusta el fútbol=Lubię piłkę nożną;¿Qué música te gusta?=Jaką muzykę lubisz?;Este fin de semana descanso=W ten weekend odpoczywam;Hace mucho calor hoy=Jest dziś bardzo gorąco"
    ).mapValues { parse(it.value) }
    private val b1 = mapOf(
        "daily" to "Ayer fui al supermercado después del trabajo=Wczoraj poszedłem do supermarketu po pracy;Mañana voy a quedar con unos amigos=Jutro umówię się ze znajomymi;Si tengo tiempo, haré deporte por la tarde=Jeśli będę miał czas, po południu poćwiczę;Últimamente duermo muy poco=Ostatnio bardzo mało śpię",
        "bar" to "Ponme otra cerveza bien fría, por favor=Daj mi jeszcze jedno bardzo zimne piwo, proszę;Anoche bebimos demasiado en este bar=Wczoraj w nocy wypiliśmy w tym barze za dużo;¿Podemos pagar por separado?=Czy możemy zapłacić osobno?;Si sigues sirviendo así, me quedo toda la noche=Jeśli dalej będziesz tak nalewać, zostanę całą noc",
        "restaurant" to "¿Este plato lleva gluten?=Czy to danie zawiera gluten?;Hemos reservado una mesa para las nueve=Zarezerwowaliśmy stolik na dziewiątą;La comida estaba buenísima, pero tardó mucho=Jedzenie było świetne, ale długo trwało;Si no es molestia, ¿nos trae más pan?=Jeśli to nie kłopot, przyniesie nam pan więcej chleba?",
        "cafe" to "¿Me lo puede poner en un vaso grande?=Może mi to pan podać w dużym kubku?;Siempre tomo el café sin azúcar=Zawsze piję kawę bez cukru;Ayer el café estaba demasiado amargo=Wczoraj kawa była zbyt gorzka;Si hay sitio, nos sentamos en la terraza=Jeśli jest miejsce, usiądziemy na tarasie",
        "shopping" to "¿Me puedo probar estos pantalones?=Mogę przymierzyć te spodnie?;Compré esta camisa ayer y quiero devolverla=Kupiłem tę koszulę wczoraj i chcę ją zwrócić;Si me hace un descuento, me llevo dos=Jeśli da mi pan rabat, wezmę dwie;Busco algo más barato pero de buena calidad=Szukam czegoś tańszego, ale dobrej jakości",
        "hotel" to "Reservé una habitación para tres noches=Zarezerwowałem pokój na trzy noce;El aire acondicionado de mi habitación no funciona=Klimatyzacja w moim pokoju nie działa;¿Podría cambiarme a una habitación más tranquila?=Czy mógłby mnie pan przenieść do cichszego pokoju?;Si es posible, quiero dejar la habitación más tarde=Jeśli to możliwe, chcę zwolnić pokój później",
        "airport" to "Mi vuelo se ha retrasado dos horas=Mój lot jest opóźniony o dwie godziny;He perdido la conexión a Madrid=Spóźniłem się na przesiadkę do Madrytu;¿Puedo llevar esta mochila en cabina?=Mogę wziąć ten plecak do kabiny?;Si cancelan el vuelo, ¿me devuelven el dinero?=Jeśli odwołają lot, oddadzą mi pieniądze?",
        "taxi" to "¿Podría ir más despacio, por favor?=Mógłby pan jechać wolniej?;La última vez pagué mucho menos por este trayecto=Ostatnim razem zapłaciłem dużo mniej za ten kurs;Si hay tráfico, vaya por otra calle=Jeśli są korki, proszę jechać inną ulicą;¿Me puede dar un recibo?=Może mi pan dać rachunek?",
        "people" to "Llevo dos años viviendo en España=Mieszkam w Hiszpanii od dwóch lat;Antes trabajaba en un hotel y ahora busco otra cosa=Wcześniej pracowałem w hotelu, a teraz szukam czegoś innego;En mi tiempo libre me gusta salir a correr=W wolnym czasie lubię biegać;Si quieres, te enseño un poco de polaco=Jeśli chcesz, nauczę cię trochę polskiego",
        "date" to "¿Te apetece ir al cine el sábado?=Masz ochotę pójść do kina w sobotę?;Lo pasé muy bien contigo la otra noche=Świetnie się z tobą bawiłem tamtego wieczoru;Si no tienes planes, te invito a cenar=Jeśli nie masz planów, zapraszam cię na kolację;Nunca había conocido a alguien tan divertido=Nigdy wcześniej nie poznałem kogoś tak zabawnego",
        "rent" to "¿Cuánto hay que pagar de fianza?=Ile trzeba zapłacić kaucji?;Me gustaría firmar un contrato de un año=Chciałbym podpisać umowę na rok;La calefacción no funcionaba cuando vine a verlo=Ogrzewanie nie działało, kiedy przyszedłem je obejrzeć;Si arreglan la ventana, me quedo con el piso=Jeśli naprawią okno, biorę mieszkanie",
        "interview" to "He trabajado cinco años en mantenimiento=Pracowałem pięć lat w utrzymaniu technicznym;En mi último trabajo me encargaba de las reparaciones=W ostatniej pracy zajmowałem się naprawami;Estoy disponible para trabajar por la noche=Jestem dostępny do pracy w nocy;Si me dan la oportunidad, aprenderé rápido=Jeśli dacie mi szansę, szybko się nauczę",
        "employer" to "Vi su anuncio en internet y me interesa mucho=Widziałem wasze ogłoszenie w internecie i bardzo mnie interesuje;¿Podríamos quedar para una entrevista esta semana?=Czy moglibyśmy umówić się na rozmowę w tym tygodniu?;Le envié mi currículum ayer por correo=Wysłałem panu wczoraj CV mailem;Si necesitan a alguien pronto, puedo empezar ya=Jeśli kogoś szybko potrzebujecie, mogę zacząć od razu",
        "hotelwork" to "Voy a avisar a mantenimiento enseguida=Zaraz powiadomię dział techniczny;Le cambiaremos de habitación si el problema continúa=Zmienimy panu pokój, jeśli problem będzie trwał;¿Cuándo empezó a fallar la ducha?=Kiedy prysznic zaczął się psuć?;Si necesita algo más, llame a recepción=Jeśli będzie pan czegoś potrzebował, proszę dzwonić na recepcję",
        "maintenance" to "He revisado la caldera y hay que cambiar una pieza=Sprawdziłem bojler i trzeba wymienić część;Ayer arreglé el aire acondicionado de la habitación doce=Wczoraj naprawiłem klimatyzację w pokoju dwanaście;Si cortan el agua, podré cambiar el grifo=Jeśli zakręcą wodę, będę mógł wymienić kran;Llevo diez años haciendo reparaciones en hoteles=Od dziesięciu lat robię naprawy w hotelach",
        "electronics" to "He medido la tensión y la fuente está fallando=Zmierzyłem napięcie i zasilacz szwankuje;Creo que el condensador se quemó por un cortocircuito=Myślę, że kondensator spalił się przez zwarcie;Si cambiamos el fusible, el equipo debería arrancar=Jeśli wymienimy bezpiecznik, urządzenie powinno się włączyć;Antes de soldar hay que desconectar la corriente=Przed lutowaniem trzeba odłączyć prąd",
        "emergency" to "Mi amigo se ha caído y no puede levantarse=Mój kolega upadł i nie może wstać;Me han robado la cartera en el metro=Ukradli mi portfel w metrze;Estamos al lado de la estación, frente a una farmacia=Jesteśmy obok dworca, naprzeciwko apteki;Si no llegan pronto, lo llevaré yo al hospital=Jeśli szybko nie przyjadą, sam zawiozę go do szpitala",
        "free" to "El fin de semana pasado fui a la playa con amigos=W zeszły weekend pojechałem na plażę ze znajomymi;Me encantaría viajar por toda España=Bardzo chciałbym podróżować po całej Hiszpanii;Si gana mi equipo, invito yo a las cervezas=Jeśli wygra moja drużyna, stawiam piwo;Cuando era niño, jugaba al fútbol todos los días=Kiedy byłem dzieckiem, codziennie grałem w piłkę"
    ).mapValues { parse(it.value) }
    fun words(scenario: String, level: Level): List<LessonWord> = when(level) {
        Level.A1 -> emptyList()
        Level.A2 -> a2[scenario] ?: a2.getValue("daily")
        else -> b1[scenario] ?: b1.getValue("daily")
    }
    fun next(scenario: String, level: Level, excluded: Collection<String>): LessonWord? {
        val blocked = excluded.map(AssessmentPolicy::key).toSet()
        return words(scenario, level).firstOrNull { AssessmentPolicy.key(it.es) !in blocked }
    }
    /** Never leaves the learner without a card: fresh material first, then unmastered items, then other scenes of the same level. */
    fun anyNext(scenario: String, level: Level, known: Collection<String>, taught: Collection<String>, current: String? = null): LessonWord? {
        val knownKeys = known.map(AssessmentPolicy::key).toSet()
        val skip = knownKeys + taught.map(AssessmentPolicy::key) + listOfNotNull(current?.let(AssessmentPolicy::key))
        fun bank(s: String) = if(level == Level.A1) LessonReserve.words(s) else words(s, level)
        val scenes = listOf(scenario) + (TopicLessons.plans.keys - scenario)
        return scenes.firstNotNullOfOrNull { s -> bank(s).firstOrNull { AssessmentPolicy.key(it.es) !in skip } }
            ?: bank(scenario).firstOrNull { AssessmentPolicy.key(it.es) !in knownKeys && AssessmentPolicy.key(it.es) != current?.let(AssessmentPolicy::key) }
    }
}
