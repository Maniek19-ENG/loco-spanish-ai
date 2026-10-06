package com.locospanish.ai.model

enum class Level(val polishHelp: Int, val pace: Double, val guidance: String) {
    A1(85, .8, "Proste, naturalne zdania. Wyjaśniaj po polsku tylko wtedy, gdy jest to potrzebne lub użytkownik poprosi."),
    A2(60, .85, "Proste codzienne zdania, czas teraźniejszy i podstawy przeszłego."),
    B1(35, .9, "Krótkie opowieści, przeszłość i przyszłość, podpowiedzi na prośbę."),
    B2(15, 1.0, "Argumentacja, zdania złożone, tryb subjuntivo."),
    C1(5, 1.05, "Idiomy, niuanse, rejestr zawodowy i spontaniczna dyskusja."),
    C2(0, 1.1, "Pełna naturalność, subtelności stylistyczne i kulturowe.")
}
enum class Preset(val label: String) { TEACHER("Nauczyciel"), FRIEND("Hiszpański kumpel"), ROAST("Roast Tutor"), CUSTOM("Custom") }
enum class Profanity(val label: String) { OFF("OFF"), LIGHT("Lekkie"), STRONG("Mocne") }
data class Personality(val preset: Preset = Preset.FRIEND, val sarcasm: Int = 25, val cheek: Int = 20,
    val roast: Int = 20, val correction: Int = 60, val profanity: Profanity = Profanity.OFF,
    val roastMode: Boolean = false, val customPrompt: String = "")
data class Settings(val level: Level = Level.A1, val voice: String = "Kore", val speed: Float = .85f,
    val autoCorrection: Boolean = true, val personality: Personality = Personality(Preset.ROAST, 100, 100, 100, 75, Profanity.STRONG, true),
    val freeTierConfirmed: Boolean = false, val onboardingDone: Boolean = false, val styleRevision: Int = 1)
data class Scenario(val id: String, val title: String, val level: Level, val goal: String)
object Scenarios {
    val all = listOf(
        Scenario("daily", "Codzienna rozmowa", Level.A1, "Luźna pogawędka o dniu, planach i wszystkim, co Ci przyjdzie do głowy."),
        Scenario("bar", "Bar", Level.A1, "Wieczór w barze: zamów coś, pogadaj z barmanem, zobacz, co się wydarzy."),
        Scenario("restaurant", "Restauracja", Level.A1, "Kolacja w restauracji — menu, polecenia kelnera i niespodzianki przy stole."),
        Scenario("cafe", "Kawiarnia", Level.A1, "Kawa i przekąska, a przy okazji rozmowa z baristą lub sąsiadem przy stoliku."),
        Scenario("shopping", "Zakupy", Level.A1, "Zakupy: ceny, rozmiary, targowanie się i rady sprzedawcy."),
        Scenario("hotel", "Hotel", Level.A1, "Hotel: meldunek, prośby i drobne kłopoty, które zawsze się zdarzają."),
        Scenario("airport", "Lotnisko", Level.A2, "Podróż: lotnisko, bagaż, bramki i nieplanowane zmiany."),
        Scenario("taxi", "Taxi", Level.A1, "Jazda taksówką z kierowcą, który ma swoje zdanie o mieście."),
        Scenario("people", "Poznawanie ludzi", Level.A1, "Poznajesz kogoś nowego — rozmowa pójdzie tam, gdzie Was poniesie."),
        Scenario("date", "Randka", Level.A2, "Pierwsza randka: luz, żarty i szukanie wspólnych tematów."),
        Scenario("rent", "Wynajem mieszkania", Level.B1, "Szukasz mieszkania — pytania, negocjacje i szczegóły umowy."),
        Scenario("interview", "Rozmowa kwalifikacyjna", Level.B1, "Rozmowa o pracę: pytania, na które trzeba odpowiedzieć na gorąco."),
        Scenario("employer", "Telefon do pracodawcy", Level.A2, "Telefon w sprawie pracy: krótko, konkretnie i czasem pod presją."),
        Scenario("hotelwork", "Praca w hotelu", Level.A2, "Praca w recepcji: goście z prośbami, skargami i pytaniami."),
        Scenario("maintenance", "Utrzymanie hotelu", Level.A2, "Utrzymanie hotelu: awarie, naprawy i rozmowy z ekipą."),
        Scenario("electronics", "Inżynier elektronik", Level.B2, "Elektronika: diagnoza usterek, testy i tłumaczenie problemu po ludzku."),
        Scenario("emergency", "Sytuacja awaryjna", Level.A2, "Symulacja nagłej sytuacji: poproś o pomoc i wyjaśnij, co się stało."),
        Scenario("free", "Luźna rozmowa", Level.A1, "Bez scenariusza — wybierz temat albo pozwól LOCO coś zaproponować.")
    )
    fun get(id: String) = all.firstOrNull { it.id == id } ?: all.first()
}
data class Transcript(val id: String, val role: String, val text: String, val interrupted: Boolean = false)
data class LearningEvent(val id: String, val kind: String, val term: String, val correction: String,
    val explanation: String, val evidence: String, val at: Long = System.currentTimeMillis())
data class Session(val id: String, val startedAt: Long, val durationSeconds: Long = 0,
    val scenarioId: String, val level: Level, val completed: Boolean = false,
    val transcript: List<Transcript> = emptyList(), val learning: List<LearningEvent> = emptyList())
data class Library(val sessions: List<Session> = emptyList())
enum class VoicePhase(val label: String) { IDLE("GOTOWY"), CONNECTING("ŁĄCZĘ"), LISTENING("SŁUCHAM"), THINKING("MYŚLĘ"), SPEAKING("MÓWIĘ"), RECONNECTING("PONAWIAM"), ERROR("BRAK POŁĄCZENIA") }
data class VoiceState(val phase: VoicePhase = VoicePhase.IDLE, val connected: Boolean = false,
    val muted: Boolean = false, val message: String = "", val transcript: List<Transcript> = emptyList(),
    val learning: List<LearningEvent> = emptyList(), val completed: Boolean = false)

