package com.locospanish.ai.tutor

import kotlin.random.Random

/**
 * Loose conversation angles. A scenario is only a setting; each session starts from a
 * different angle so the same scenario never opens (or runs) the same way twice.
 */
object Variety {
    private val generic = listOf(
        "zacznij od drobnej niespodzianki albo małego problemu w tej sytuacji",
        "zacznij od pytania osobistego, które wynika z tej sytuacji",
        "zacznij w środku akcji, jakby rozmowa już trwała",
        "zacznij od żartu albo spostrzeżenia o tym miejscu",
        "zacznij od prośby o radę lub opinię ucznia",
        "zacznij od krótkiej scenki, w której masz własny charakter i humor"
    )
    private val byScenario = mapOf(
        "daily" to listOf("pogoda i plany na weekend", "co uczeń dziś jadł albo pił", "dziwny sen lub zabawna historia z dnia", "ulubiona muzyka, serial albo gra", "wspomnienie z dzieciństwa"),
        "bar" to listOf("kolejka i głośna muzyka", "kelner poleca coś dziwnego", "ktoś obok zaczyna rozmowę", "uczeń nie wie, co wybrać z karty", "wieczór po meczu"),
        "restaurant" to listOf("alergia lub dieta", "pomylone zamówienie", "uczeń prosi o polecenie dania dnia", "rezerwacja na ostatnią chwilę", "kelner opowiada o lokalnej specjalności"),
        "cafe" to listOf("poranny pośpiech", "uczeń pracuje przy laptopie i prosi o wifi", "stały klient ze swoimi dziwactwami", "nowa kawa w menu", "spotkanie ze starym znajomym"),
        "shopping" to listOf("targowanie się na bazarze", "szukanie prezentu", "zwrot wadliwej rzeczy", "przymierzalnia i rozmiary", "promocja, która nie jest taka oczywista"),
        "hotel" to listOf("rezerwacja zniknęła z systemu", "hałaśliwy sąsiad za ścianą", "prośba o późne wymeldowanie", "pytanie o plan zwiedzania miasta", "zgubiony klucz"),
        "airport" to listOf("opóźniony lot", "bagaż za ciężki", "zmiana bramki w ostatniej chwili", "pytanie przy kontroli", "zgubiona walizka"),
        "taxi" to listOf("korek i objazd", "rozmowa z gadatliwym kierowcą", "uczeń nie zna dokładnego adresu", "prośba o postój po drodze", "kierowca poleca miejsca w mieście"),
        "people" to listOf("pierwsze wrażenie na imprezie", "wspólna pasja", "skąd kto pochodzi i dlaczego tu jest", "plany na podróż", "rozmowa o rodzinie i zwierzakach"),
        "date" to listOf("wybór miejsca", "niezręczna cisza i jak ją przełamać", "wspólne zainteresowania", "komplement i jak na niego odpowiedzieć", "plan na kolejne spotkanie"),
        "rent" to listOf("mieszkanie ma ukryty minus", "negocjacja kaucji", "sąsiedzi i hałas", "umowa i terminy", "co jest w cenie"),
        "interview" to listOf("pytanie o największą porażkę", "pytanie o oczekiwania finansowe", "nagłe zadanie praktyczne", "pytanie dlaczego ta firma", "rozmowa o zespole"),
        "employer" to listOf("rozmówca jest zajęty i mówi szybko", "pytanie o warunki i grafik", "uczeń musi przesunąć termin", "pytanie o wymagane dokumenty", "zła łączność i trzeba dopytać"),
        "hotelwork" to listOf("gość narzeka na pokój", "prośba o dodatkowe łóżko", "gość pyta o atrakcje", "awaria klimatyzacji", "pomyłka w rachunku"),
        "maintenance" to listOf("cieknący kran", "zepsute światło", "pilna awaria w nocy", "zamawianie części", "tłumaczenie problemu współpracownikowi"),
        "electronics" to listOf("układ się przegrzewa", "dziwny szum w sygnale", "klient nie rozumie usterki", "dobór zasilacza", "raport z testów"),
        "emergency" to listOf("zgubiony telefon", "złe samopoczucie i szukanie apteki", "drobny wypadek na ulicy", "kradzież torby", "zagubiony w obcym mieście"),
        "free" to listOf("uczeń sam wybiera temat — zapytaj, co go ostatnio zaciekawiło", "plotki, jedzenie albo podróże", "gry, filmy lub internet", "praca i marzenia", "hipotetyczne pytanie w stylu „co by było, gdyby”")
    )

    /** Picks an angle, avoiding [last] when possible. */
    fun angle(scenarioId: String, last: String = "", random: Random = Random.Default): String {
        val pool = (byScenario[scenarioId].orEmpty().map { "temat/sytuacja: $it" } + generic)
        return pool.filter { it != last }.ifEmpty { pool }.random(random)
    }
}
