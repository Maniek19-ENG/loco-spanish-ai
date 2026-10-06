package com.locospanish.ai.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.net.toUri
import android.provider.Settings as AndroidSettings
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.locospanish.ai.MainActivity
import com.locospanish.ai.model.*
import com.locospanish.ai.personality.PersonalityEngine
import com.locospanish.ai.realtime.GeminiProtocol
import com.locospanish.ai.repository.ProgressCalculator
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val Lime = Color(0xFFD4FF62)
private val Ink = Color(0xFF10120F)
private val Muted = Color(0xFFAEB4A8)
private val Panel = Color(0xFF1C211A)
private val PanelHi = Color(0xFF262D22)
private val Violet = Color(0xFFAC9DFF)
private val icons = mapOf("daily" to "☀️", "bar" to "🍹", "restaurant" to "🍽️", "cafe" to "☕", "shopping" to "🛍️", "hotel" to "🏨",
    "airport" to "✈️", "taxi" to "🚕", "people" to "🤝", "date" to "💬", "rent" to "🏠", "interview" to "💼", "employer" to "📞",
    "hotelwork" to "🛎️", "maintenance" to "🔧", "electronics" to "🔌", "emergency" to "🚑", "free" to "🎲")
@Composable private fun LevelBadge(level: Level) {
    Surface(color = Lime.copy(alpha = .16f), shape = RoundedCornerShape(50)) {
        Text(level.name, color = Lime, fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp))
    }
}

@Composable fun LocoApp(vm: LocoViewModel) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val library by vm.library.collectAsStateWithLifecycle()
    val voice by vm.voice.collectAsStateWithLifecycle()
    val active by vm.active.collectAsStateWithLifecycle()
    val editingPersonality by vm.editingPersonality.collectAsStateWithLifecycle()
    val ready by vm.loaded.collectAsStateWithLifecycle()
    val notice by vm.notice.collectAsStateWithLifecycle()
    val seconds by vm.seconds.collectAsStateWithLifecycle()
    val scenario by vm.selected.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var page by rememberSaveable { mutableStateOf("Start") }
    val pageScroll = rememberScrollState()
    LaunchedEffect(page, active, editingPersonality, settings.onboardingDone) { pageScroll.scrollTo(0) }
    var permissionDenied by rememberSaveable { mutableStateOf(false) }
    var endDialog by remember { mutableStateOf(false) }
    val microphone = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        permissionDenied = !it; if (it) vm.start()
    }
    fun begin() {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) vm.start()
        else microphone.launch(Manifest.permission.RECORD_AUDIO)
    }
    BackHandler(active) { if (editingPersonality) vm.resumeAfterPersonality() else endDialog = true }
    DisposableEffect(active) {
        val window = (context as? MainActivity)?.window
        if (active) window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose { window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
    }
    MaterialTheme(colorScheme = darkColorScheme(primary = Lime, onPrimary = Ink, background = Ink,
        surface = Ink, surfaceVariant = Panel, onSurface = Color(0xFFF2F5EB), secondary = Color(0xFFAC9DFF))) {
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFF1B2412), Ink, Ink)))) {
        Scaffold(containerColor = Color.Transparent, bottomBar = {
            if (!active && settings.onboardingDone) NavigationBar(containerColor = Panel) {
                listOf("Start" to "◉", "Scenariusze" to "▤", "Osobowość" to "✦", "Postępy" to "↗", "Ustawienia" to "⚙").forEach { (name, icon) ->
                    NavigationBarItem(selected = page == name, onClick = { page = name }, icon = { Text(icon, fontSize = 22.sp) },
                        label = { Text(name, fontSize = 10.sp, maxLines = 1) })
                }
            }
        }) { padding ->
            Column(Modifier.fillMaxSize().padding(padding).verticalScroll(pageScroll).padding(horizontal = 22.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp)) {
                if (!ready) { CircularProgressIndicator(); Text(notice.ifBlank { "Wczytuję Twoje ustawienia…" }); return@Column }
                if (!settings.onboardingDone) {
                    Header("¡HOLA!", "Zacznij od rozmowy.")
                    Orb(VoicePhase.LISTENING)
                    Text("Hiszpański od zera. Po Twojemu.", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
                    Text("Możesz mówić po polsku. LOCO podpowie zdanie po hiszpańsku i pomoże Ci je przećwiczyć.", color = Muted)
                    InfoCard("Głos generowany przez AI", "Rozmawiasz przez internet z Gemini. Użyj klucza z projektu Free Tier bez płatnych rozliczeń. Darmowy limit ustala Google. Audio i krótkie notatki z nauki trafiają do Google; historia zapisuje się na telefonie. Bez komputera i bez abonamentu API.")
                    Text("Rozmowa działa na otwartym ekranie aplikacji. Wyjście do innej aplikacji kończy sesję. Roast jest opcjonalny i możesz go wyłączyć.", color = Muted)
                    Button(onClick = { vm.update(settings.copy(onboardingDone = true)); page = "Ustawienia" }, modifier = Modifier.fillMaxWidth()) { Text("SKONFIGURUJ I ZACZNIJ") }
                } else {
                    if (notice.isNotBlank()) InfoCard("Informacja", notice)
                    if (permissionDenied) {
                        InfoCard("Potrzebny mikrofon", "Bez zgody na mikrofon rozmowa nie wystartuje. Włącz uprawnienie w ustawieniach telefonu.")
                        OutlinedButton(onClick = { context.startActivity(Intent(AndroidSettings.ACTION_APPLICATION_DETAILS_SETTINGS, "package:${context.packageName}".toUri())) }) { Text("Uprawnienia aplikacji") }
                    }
                    if (active && editingPersonality) {
                        Button(onClick = vm::resumeAfterPersonality, modifier = Modifier.fillMaxWidth()) { Text("ZASTOSUJ I WRÓĆ DO ROZMOWY") }
                        Text("Rozmowa i mikrofon są wstrzymane. Zmiany zapisują się automatycznie. Po powrocie połączymy się ponownie z nowym charakterem i zachowamy historię.", color = Muted)
                        PersonalityScreen(settings, vm)
                        Button(onClick = vm::resumeAfterPersonality, modifier = Modifier.fillMaxWidth()) { Text("ZASTOSUJ I WRÓĆ DO ROZMOWY") }
                    }
                    else if (active) LiveScreen(voice, seconds, Scenarios.get(scenario).title, settings.personality, vm) { endDialog = true }
                    else when (page) {
                        "Start" -> HomeScreen(settings, library, scenario, { vm.selected.value = it }, { begin() }, { page = "Osobowość" })
                        "Scenariusze" -> ScenarioScreen(scenario) { vm.selected.value = it; page = "Start" }
                        "Osobowość" -> PersonalityScreen(settings, vm)
                        "Postępy" -> ProgressScreen(library)
                        else -> SettingsScreen(settings, vm)
                    }
                }
            }
        }
        if (endDialog) AlertDialog(onDismissRequest = { endDialog = false }, title = { Text("Zakończyć rozmowę?") },
            text = { Text("Zapiszemy czas, transkrypcję i obserwacje nauczyciela.") },
            confirmButton = { TextButton(onClick = { endDialog = false; vm.finish() }) { Text("Zakończ") } },
            dismissButton = { TextButton(onClick = { endDialog = false }) { Text("Rozmawiaj dalej") } })
        }
    }
}
@Composable private fun Header(kicker: String, title: String) {
    Text(kicker, color = Lime, letterSpacing = 3.sp, fontSize = 12.sp, fontWeight = FontWeight.Bold)
    Text(title, style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
}
@Composable private fun InfoCard(title: String, body: String) {
    Surface(color = Panel, shape = RoundedCornerShape(22.dp), border = BorderStroke(1.dp, Color(0x14FFFFFF)), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Text(title, fontWeight = FontWeight.SemiBold); Text(body, color = Muted, style = MaterialTheme.typography.bodyMedium)
        }
    }
}
private fun duration(seconds: Long) = "%02d:%02d".format(seconds / 60, seconds % 60)
@Composable private fun HomeScreen(settings: Settings, library: Library, selected: String, select: (String) -> Unit, start: () -> Unit, personality: () -> Unit) {
    val progress = remember(library) { ProgressCalculator.calculate(library) }
    Header("LOCO / SPANISH AI", "Hiszpański bez litości.\nPolski bez cenzury.")
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        AssistChip(onClick = {}, label = { Text("${settings.level} • Twój poziom") })
        AssistChip(onClick = {}, label = { Text("${progress.streak} dni z rzędu") })
    }
    PersonalityCard(settings.personality, personality)
    Surface(color = Panel, shape = RoundedCornerShape(28.dp), border = BorderStroke(1.dp, Lime.copy(alpha = .25f))) {
        Column(Modifier.fillMaxWidth().background(Brush.linearGradient(listOf(Color(0xFF2A3818), Panel))).padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("TWOJA NASTĘPNA RUNDA", fontSize = 11.sp, letterSpacing = 2.sp, color = Muted, modifier = Modifier.weight(1f))
                LevelBadge(Scenarios.get(selected).level)
            }
            Text("${icons[selected].orEmpty()}  ${Scenarios.get(selected).title}", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Text(Scenarios.get(selected).goal, color = Muted)
            Text("Scenariusz to tylko tło. Rozmowa za każdym razem toczy się inaczej, a Ty możesz zmienić temat kiedy chcesz.", color = Lime)
            Button(onClick = start, modifier = Modifier.fillMaxWidth().heightIn(min = 58.dp), shape = RoundedCornerShape(16.dp)) { Text("ROZPOCZNIJ ROZMOWĘ", fontWeight = FontWeight.Bold) }
        }
    }
    Text("Dziś: ${duration(progress.todaySeconds)} rozmowy", style = MaterialTheme.typography.titleLarge)
    Text("Wybierz kierunek", fontWeight = FontWeight.Bold)
    Choices(listOf("free" to "🎲 Luźna rozmowa", "maintenance" to "🔧 Praca", "restaurant" to "🍽️ Restauracja", "daily" to "☀️ Codzienna rozmowa"), selected, select)
    OutlinedButton(onClick = { select(Scenarios.all.random().id) }, modifier = Modifier.fillMaxWidth()) { Text("🎲 Zaskocz mnie — losowy scenariusz") }
    InfoCard("Ostatnia sesja", library.sessions.lastOrNull()?.let { "${Scenarios.get(it.scenarioId).title} • ${duration(it.durationSeconds)}" } ?: "Twoja pierwsza rozmowa dopiero przed Tobą.")
    InfoCard("Ostatnio ćwiczone słowa", library.sessions.asReversed().flatMap { it.learning }.filter { it.kind == "word" }.map { it.term }.distinct().take(6).joinToString(" • ").ifBlank { "Tutaj pojawią się słowa z Twoich rozmów." })
}
@OptIn(ExperimentalLayoutApi::class)
@Composable private fun Choices(items: List<Pair<String, String>>, selected: String, onSelect: (String) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items.forEach { (id, label) -> FilterChip(selected = id == selected, onClick = { onSelect(id) }, label = { Text(label) }) }
    }
}
@Composable private fun ScenarioScreen(selected: String, select: (String) -> Unit) {
    Header("18 SYTUACJI", "Hiszpański do życia.")
    Text("Poziom przy scenariuszu to sugerowana trudność. LOCO dopasuje zadania do Twojego poziomu.", color = Muted)
    Scenarios.all.forEach { scenario ->
        Surface(onClick = { select(scenario.id) }, color = if (selected == scenario.id) Color(0xFF303B20) else Panel, shape = RoundedCornerShape(20.dp),
            border = BorderStroke(1.dp, if (selected == scenario.id) Lime.copy(alpha = .6f) else Color(0x14FFFFFF))) {
            Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                Text(icons[scenario.id].orEmpty(), fontSize = 30.sp)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(scenario.title, fontWeight = FontWeight.SemiBold)
                    Text(scenario.goal, color = Muted, style = MaterialTheme.typography.bodyMedium)
                }
                LevelBadge(scenario.level)
            }
        }
    }
}
@Composable private fun Orb(phase: VoicePhase) {
    val transition = rememberInfiniteTransition(label = "voice")
    val scale by transition.animateFloat(.88f, 1.07f, infiniteRepeatable(tween(if (phase == VoicePhase.SPEAKING) 480 else 1800), RepeatMode.Reverse), label = "breath")
    val tint = when(phase) { VoicePhase.SPEAKING -> Lime; VoicePhase.THINKING -> Color(0xFFAC9DFF); VoicePhase.ERROR -> Color(0xFFFF8D82); else -> Color(0xFF75C9A5) }
    val ring by transition.animateFloat(.7f, 1.25f, infiniteRepeatable(tween(if (phase == VoicePhase.SPEAKING) 900 else 2600), RepeatMode.Restart), label = "ring")
    Box(Modifier.fillMaxWidth().height(230.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(220.dp)) {
            drawCircle(tint.copy(alpha = (1.25f - ring).coerceIn(0f, 1f) * .35f), radius = size.minDimension / 2 * ring.coerceAtMost(1f), style = androidx.compose.ui.graphics.drawscope.Stroke(width = 3f))
        }
        Canvas(Modifier.size(190.dp).scale(scale)) {
            drawCircle(Brush.radialGradient(listOf(tint.copy(alpha = .9f), tint.copy(alpha = .25f), Color.Transparent)))
            drawCircle(tint.copy(alpha = .5f), radius = size.minDimension * .28f)
            drawCircle(tint, radius = size.minDimension * .12f)
        }
    }
}
@Composable private fun PersonalityCard(p: Personality, edit: () -> Unit) {
    Surface(color = Panel, shape = RoundedCornerShape(20.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("CHARAKTER LOCO • ${p.preset.label}", fontWeight = FontWeight.Bold, color = Lime)
            Text("Roast: ${if(p.roastMode) "${p.roast}/100" else "wyłączony"} • Przekleństwa: ${p.profanity.label}")
            Text("Sarkazm ${p.sarcasm} • Bezczelność ${p.cheek} • Korekta ${p.correction}", color = Muted, fontSize = 12.sp)
            OutlinedButton(onClick = edit, modifier = Modifier.fillMaxWidth()) { Text("ZMIEŃ CHARAKTER I PRZEKLEŃSTWA") }
        }
    }
}
@OptIn(ExperimentalLayoutApi::class)
@Composable private fun LiveScreen(state: VoiceState, seconds: Long, title: String, personality: Personality, vm: LocoViewModel, end: () -> Unit) {
    Header("NA ŻYWO / ${duration(seconds)}", "${icons[Scenarios.all.firstOrNull { it.title == title }?.id].orEmpty()} $title")
    PersonalityCard(personality, vm::editPersonality)
    Text(state.message, color = Muted)
    Orb(state.phase)
    Text(if (state.muted) "MIKROFON WYŁĄCZONY" else state.phase.label, modifier = Modifier.fillMaxWidth(),
        textAlign = androidx.compose.ui.text.style.TextAlign.Center, color = Lime, fontWeight = FontWeight.Bold, letterSpacing = 4.sp)
    Text("Możesz przerwać LOCO, zaczynając mówić.", color = Muted, fontSize = 13.sp)
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        OutlinedButton(onClick = vm::mute, modifier = Modifier.weight(1f)) { Text(if (state.muted) "Włącz mikrofon" else "Wycisz mikrofon") }
        Button(onClick = end, modifier = Modifier.weight(1f), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF8D82))) { Text("Zakończ") }
    }
    if (state.phase == VoicePhase.ERROR) Button(onClick = vm::retry) { Text("Spróbuj ponownie") }
    Text("Szybkie komendy", color = Muted, fontSize = 12.sp, letterSpacing = 1.sp)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        HelpButton("🔁 Powtórz", state.connected) { vm.help("Powtórz swoją ostatnią odpowiedź, bez dodawania nowego pytania.") }
        HelpButton("🐢 Wolniej", state.connected) { vm.help("Powtórz ostatnią odpowiedź znacznie wolniej, wyraźnie oddzielając słowa. Dalej mów w tym tempie.") }
        HelpButton("🤔 Nie rozumiem", state.connected) { vm.help("Nie rozumiem. Wyjaśnij krótko po polsku sens ostatniej wypowiedzi. Nie każ mi powtarzać zdania.") }
        HelpButton("🇵🇱 Po polsku", state.connected) { vm.help("Wyjaśnij ostatnią wypowiedź po polsku, z krótkim przykładem po hiszpańsku.") }
        HelpButton("📝 Odpytaj mnie", state.connected) { vm.help("Sprawdź jedno słowo lub konstrukcję, które rzeczywiście ćwiczyłem w tej sesji. Daj krótkie zadanie po polsku, nie zdradzaj odpowiedzi i poczekaj na moją próbę po hiszpańsku. Jeśli nic jeszcze nie ćwiczyłem, zacznij od czegoś z naszej rozmowy.") }
        HelpButton("😇 Łagodniej", state.connected) { vm.help("Od teraz do końca sesji bez wyzwisk, szydery i przekleństw. Kontynuuj naukę spokojnie z krótkimi poprawkami.") }
        HelpButton("🎲 Zmień temat", state.connected) { vm.help("Zmieńmy temat na coś zupełnie innego i ciekawego, nawiązującego do tej sytuacji. Nie wracaj do poprzednich pytań ani przykładów.") }
        HelpButton("➡️ Dalej", state.connected) { vm.help("Chcę kontynuować rozmowę. Poprowadź ją dalej w nową stronę, bez powtarzania wcześniejszych pytań i żartów. Nie kończ rozmowy.") }
    }
    Text("Transkrypcja", style = MaterialTheme.typography.titleLarge)
    Text("Automatyczny zapis może zawierać błędy. Tekst AI może wyprzedzać głos.", color = Muted, fontSize = 12.sp)
    if (state.transcript.isEmpty()) Text("Tutaj pojawi się Wasza rozmowa.", color = Muted)
    state.transcript.takeLast(12).forEach { Bubble(it) }
}
@Composable private fun HelpButton(label: String, enabled: Boolean, action: () -> Unit) {
    AssistChip(onClick = action, enabled = enabled, label = { Text(label) })
}
@Composable private fun Bubble(item: Transcript) {
    val mine = item.role == "user"
    Row(Modifier.fillMaxWidth(), horizontalArrangement = if (mine) Arrangement.End else Arrangement.Start) {
        Surface(color = if (mine) Color(0xFF303B20) else PanelHi, modifier = Modifier.widthIn(max = 300.dp),
            shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp, bottomStart = if (mine) 20.dp else 4.dp, bottomEnd = if (mine) 4.dp else 20.dp)) {
            Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(if (mine) "Ty" else "LOCO${if (item.interrupted) " • przerwano" else ""}", color = if (mine) Lime else Violet, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                Text(item.text.ifBlank { "Rozpoznaję wypowiedź…" })
            }
        }
    }
}
@Composable private fun PersonalityScreen(settings: Settings, vm: LocoViewModel) {
    val p = settings.personality
    val sample by vm.sample.collectAsStateWithLifecycle()
    val sampling by vm.sampling.collectAsStateWithLifecycle()
    fun change(value: Personality) = vm.update(settings.copy(personality = PersonalityEngine.normalize(value)))
    Header("TWÓJ NAUCZYCIEL", "Ustaw charakter.")
    Text("Każdy poziom wybierasz osobno. Zmiany zapisują się automatycznie. Gotowy profil ustawia wszystkie suwaki; potem możesz je dostosować.", color = Muted)
    Choices(Preset.entries.map { it.name to it.label }, p.preset.name) { change(if (it == "CUSTOM") p.copy(preset = Preset.CUSTOM) else PersonalityEngine.preset(Preset.valueOf(it))) }
    Toggle("Roast — docinki i wyśmiewanie błędów", p.roastMode) { change(p.copy(roastMode = it)) }
    Text("Przekleństwa", fontWeight = FontWeight.Bold)
    Choices(Profanity.entries.map { it.name to it.label }, p.profanity.name) { change(p.copy(profanity = Profanity.valueOf(it))) }
    Text(when(p.profanity) { Profanity.OFF -> "Bez przekleństw, nawet przy mocnym roaście."; Profanity.LIGHT -> "Lekkie przekleństwa, np. cholera."; Profanity.STRONG -> "Mocne słowa bez cenzury, np. kurwa. Nie włączają samego roastu." }, color = Muted)
    IntSlider("Sarkazm", p.sarcasm) { change(p.copy(sarcasm = it)) }
    Text("0: bez ironii • 100: cięte riposty i ironiczne porównania", color = Muted, fontSize = 12.sp)
    IntSlider("Bezczelność / złośliwość", p.cheek) { change(p.copy(cheek = it)) }
    Text("0: uprzejmie • 100: pyskato, zadziornie i bez owijania", color = Muted, fontSize = 12.sp)
    IntSlider("Intensywność roastowania", p.roast, p.roastMode) { change(p.copy(roast = it)) }
    Text(if(p.roastMode) "0: bez docinek • 100: ostry komediowy roast i wyśmiewanie wpadek" else "Roast jest wyłączony. Włącz przełącznik powyżej, aby używać tego suwaka.", color = Muted, fontSize = 12.sp)
    IntSlider("Intensywność korekty", p.correction) { change(p.copy(correction = it)) }
    Text(if(settings.autoCorrection) "0: tylko na prośbę • 100: także drobne błędy, po jednym naraz" else "Automatyczna korekta jest wyłączona w Ustawieniach. LOCO poprawia tylko na prośbę.", color = Muted, fontSize = 12.sp)
    if (p.preset == Preset.CUSTOM) OutlinedTextField(value = p.customPrompt, onValueChange = { change(p.copy(customPrompt = it.take(2000))) },
        label = { Text("CUSTOM PROMPT") }, supportingText = { Text("Preferencje stylu • ${p.customPrompt.length}/2000") }, modifier = Modifier.fillMaxWidth(), minLines = 4)
    Text("Humor reaguje na rozmowę. Jeśli utkniesz, LOCO uprości zadanie. Powiedz „bez roastu”, aby przerwać żarty.", color = Muted)
    Button(onClick = vm::preview, enabled = !sampling, modifier = Modifier.fillMaxWidth()) { Text(if(sampling) "GENERUJĘ…" else "TESTUJ OSOBOWOŚĆ") }
    Text("Próbka tekstowa Gemini dla błędu „Yo soy 31 años”, bez mikrofonu i zapisu postępu. Korzysta z darmowego limitu modelu Gemini 3.8 Flash.", color = Muted, fontSize = 12.sp)
    if(sample.isNotBlank()) InfoCard("Próbka reakcji",sample)
}
@Composable private fun IntSlider(label: String, value: Int, enabled: Boolean = true, change: (Int) -> Unit) {
    Column {
        Text("$label  $value/100", fontWeight = FontWeight.SemiBold)
        Text(if(enabled) PersonalityEngine.level(value) else "Nieaktywne", color = Muted, fontSize = 12.sp)
        Slider(value = value.toFloat(), onValueChange = { change(it.toInt()) }, valueRange = 0f..100f, enabled = enabled)
    }
}
@Composable private fun Toggle(label: String, checked: Boolean, change: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) { Text(label, Modifier.weight(1f)); Switch(checked = checked, onCheckedChange = change) }
}
@Composable private fun SettingsScreen(s: Settings, vm: LocoViewModel) {
    var token by remember { mutableStateOf("") }
    val hasToken by vm.hasAccess.collectAsStateWithLifecycle()
    var confirmDelete by remember { mutableStateOf(false) }
    Header("PO TWOJEMU", "Ustawienia.")
    Text("Język interfejsu: polski", color = Muted)
    Text("Poziom hiszpańskiego", fontWeight = FontWeight.Bold)
    Choices(Level.entries.map { it.name to it.name }, s.level.name) { val level = Level.valueOf(it); vm.update(s.copy(level = level, speed = level.pace.toFloat())) }
    Text(s.level.guidance, color = Muted)
    Text("Głos AI", fontWeight = FontWeight.Bold)
    Choices(GeminiProtocol.voices.map { it to it.replaceFirstChar { c -> c.uppercase() } }, s.voice) { vm.update(s.copy(voice = it)) }
    Text("Preferowane tempo głosu: ${"%.2f".format(s.speed)}×")
    Slider(value = s.speed, onValueChange = { vm.update(s.copy(speed = it)) }, valueRange = .6f..1.3f)
    Toggle("Automatyczna korekta", s.autoCorrection) { vm.update(s.copy(autoCorrection = it)) }
    Text("Osobowość: ${s.personality.preset.label}. Zmień ją w zakładce Osobowość.", color = Muted)
    HorizontalDivider()
    Text("Gemini • własna aplikacja", style = MaterialTheme.typography.titleLarge)
    Text("Model: "+GeminiProtocol.MODEL+". Łączy się z Google przez internet. Abonament Gemini Pro nie jest potrzebny.", color = Muted)
    val context = LocalContext.current
    OutlinedButton(onClick = { context.startActivity(Intent(Intent.ACTION_VIEW, "https://aistudio.google.com/api-keys".toUri())) }) { Text("OTWÓRZ GOOGLE AI STUDIO") }
    Text("1. Utwórz klucz w projekcie oznaczonym Free Tier. Nie włączaj rozliczeń.\n2. Wklej klucz poniżej i zapisz.\n3. Potwierdź plan projektu, potem rozpocznij rozmowę.", color = Muted)
    OutlinedTextField(value = token, onValueChange = { token = it.trim() }, label = { Text("Klucz Gemini API") }, visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth(), singleLine = true)
    Button(onClick = { vm.saveAccess(token); token = "" }, enabled = token.isNotBlank()) { Text("Zapisz klucz Gemini") }
    Text(if (hasToken) "Klucz zapisany w szyfrowanym magazynie telefonu." else "Klucz nie został jeszcze zapisany.", color = Muted)
    if (hasToken) {
        Toggle("Sprawdziłem: projekt ma Free Tier i wyłączone płatności", s.freeTierConfirmed) { vm.update(s.copy(freeTierConfirmed = it)) }
        TextButton(onClick = { vm.saveAccess("") }) { Text("Usuń klucz") }
    }
    InfoCard("Darmowy limit", "Limit zależy od projektu i dostępności Google. Po jego wyczerpaniu rozmowa zostanie zatrzymana. Aplikacja nie włącza płatności, ale nie potrafi sprawdzić planu Twojego klucza. Klucz projektu z płatnymi rozliczeniami może powodować opłaty.")
    HorizontalDivider()
    InfoCard("Prywatność", "Audio i kontekst rozmowy trafiają do Google. W darmowym API treści mogą być używane do ulepszania usług Google, zgodnie z warunkami właściwymi dla Twojego regionu. Telefon zapisuje transkrypcje i postępy, bez nagrań. Usunięcie historii tutaj nie usuwa danych po stronie Google.")
    OutlinedButton(onClick = { confirmDelete = true }) { Text("Usuń całą lokalną historię") }
    if (confirmDelete) AlertDialog(onDismissRequest = { confirmDelete = false }, title = { Text("Usunąć historię?") }, text = { Text("Usuniemy sesje, słowa, błędy i statystyki z tego telefonu. Ustawienia pozostaną.") },
        confirmButton = { TextButton(onClick = { vm.deleteHistory(); confirmDelete = false }) { Text("Usuń") } },
        dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Anuluj") } })
}
@Composable private fun ProgressScreen(library: Library) {
    val p = remember(library) { ProgressCalculator.calculate(library) }
    var visibleSessions by rememberSaveable { mutableIntStateOf(30) }
    var expanded by rememberSaveable { mutableStateOf("") }
    Header("KAŻDA ROZMOWA SIĘ LICZY", "Twoje postępy.")
    if (p.sessions == 0) { InfoCard("Jeszcze czysta karta", "Rozpocznij pierwszą rozmowę. Pokażemy tu rzeczywisty czas nauki, ćwiczone słowa i obserwacje nauczyciela."); return }
    InfoCard("${duration(p.seconds)} rozmowy", "${p.sessions} sesji • ${p.streak} dni z rzędu • ${p.repetitions} powtórek")
    InfoCard("Poznane / przećwiczone słowa (${p.words.size})", p.words.joinToString(" • ").ifBlank { "Brak zapisanych słów." })
    Text("Obserwacje AI mogą być niedokładne. Poziom zmieniasz samodzielnie w Ustawieniach.", color = Muted, fontSize = 12.sp)
    InfoCard("Najczęstsze błędy", p.errors.groupingBy { it.term }.eachCount().toList().sortedByDescending { it.second }.take(6)
        .joinToString("\n") { "${it.first} — ${it.second}×" }.ifBlank { "Brak zaobserwowanych błędów." })
    InfoCard("Najtrudniejsze słowa", p.difficult.take(8).joinToString("\n") { "${it.first} — ${it.second}×" }.ifBlank { "Brak zapisanych trudności." })
    InfoCard("Ukończone scenariusze (${p.completed.size}/18)", p.completed.joinToString(" • ") { Scenarios.get(it).title }.ifBlank { "Żaden cel nie został jeszcze potwierdzony przez nauczyciela." })
    Text("Ostatnie błędy", style = MaterialTheme.typography.titleLarge)
    p.errors.takeLast(5).asReversed().forEach { InfoCard("${it.term} → ${it.correction}", "${it.explanation}\nDowód: ${it.evidence}") }
    Text("Historia sesji", style = MaterialTheme.typography.titleLarge)
    library.sessions.asReversed().take(visibleSessions).forEach { session ->
        val date = Instant.ofEpochMilli(session.startedAt).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm"))
        OutlinedButton(onClick = { expanded = if (expanded == session.id) "" else session.id }, modifier = Modifier.fillMaxWidth()) {
            Text("$date • ${Scenarios.get(session.scenarioId).title}\n${duration(session.durationSeconds)} • ${session.level}")
        }
        if (expanded == session.id) session.transcript.forEach { InfoCard(if (it.role == "user") "Ty" else "LOCO", it.text) }
    }
    if(library.sessions.size > visibleSessions) OutlinedButton(onClick = { visibleSessions += 30 }) { Text("Pokaż wcześniejsze sesje") }
}

