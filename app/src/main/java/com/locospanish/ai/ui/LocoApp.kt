package com.locospanish.ai.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
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
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

private val Lime = Color(0xFF19E6D0)
private val NeonSoft = Color(0xFF80FFF2)
private val ElectricBlue = Color(0xFF77A8D9)
private val Ink = Color(0xFF010708)
private val Muted = Color(0xFF8BAEAD)
private val Panel = Color(0xE608191C)
private val PanelStrong = Color(0xFF071215)
private val Hairline = Color(0x334DFFF0)

@Composable fun LocoApp(vm: LocoViewModel) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val library by vm.library.collectAsStateWithLifecycle()
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
    val permissions = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        val granted = result.values.all { it }
        permissionDenied = !granted
        if (granted) vm.start()
    }
    fun begin() {
        val required = buildList {
            add(Manifest.permission.RECORD_AUDIO)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && settings.audioRoute == AudioRoutePreference.BLUETOOTH) add(Manifest.permission.BLUETOOTH_CONNECT)
        }
        val missing = required.filter { ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED }
        if (missing.isEmpty()) vm.start() else permissions.launch(missing.toTypedArray())
    }
    BackHandler(active) { if (editingPersonality) vm.resumeAfterPersonality() else endDialog = true }
    DisposableEffect(active) {
        val window = (context as? MainActivity)?.window
        if (active) window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose { window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
    }
    MaterialTheme(colorScheme = darkColorScheme(primary = Lime, onPrimary = Ink, background = Ink,
        onBackground = Color(0xFFE9FFFC), surface = PanelStrong, surfaceVariant = Panel, onSurface = Color(0xFFE9FFFC), secondary = ElectricBlue,
        outline = Hairline, error = Color(0xFFFF746C))) {
        Box(Modifier.fillMaxSize().background(Ink)) {
        NeonBackdrop()
        Scaffold(containerColor = Color.Transparent, contentColor = Color(0xFFE9FFFC), bottomBar = {
            if (!active && settings.onboardingDone) NavigationBar(containerColor = PanelStrong.copy(alpha = .96f), tonalElevation = 0.dp) {
                listOf("Start" to "◉", "Scenariusze" to "▤", "Osobowość" to "✦", "Postępy" to "↗", "Ustawienia" to "⚙").forEach { (name, icon) ->
                    NavigationBarItem(selected = page == name, onClick = { page = name }, icon = { Text(icon, fontSize = 22.sp) },
                        label = { Text(name, fontSize = 10.sp, maxLines = 1) })
                }
            }
        }) { padding ->
            Column(Modifier.fillMaxSize().padding(padding).then(if(active && !editingPersonality && settings.onboardingDone) Modifier else Modifier.verticalScroll(pageScroll)).padding(horizontal = 22.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp)) {
                if (!ready) { CircularProgressIndicator(); Text(notice.ifBlank { "Wczytuję Twoje ustawienia…" }); return@Column }
                if (!settings.onboardingDone) {
                    Header("¡HOLA!", "Zacznij od rozmowy.")
                    VoiceOrb(VoicePhase.LISTENING)
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
                    else if (active) LiveScreen(seconds, Scenarios.get(scenario).title, (if (settings.previousVersionMode) settings.restoredPrevious().personality else settings.forDialog().personality), vm) { endDialog = true }
                    else when (page) {
                        "Start" -> HomeScreen(settings, library, vm, scenario, { vm.selected.value = it }, { begin() }, { page = "Osobowość" }) { page = it }
                        "Scenariusze" -> ScenarioScreen(scenario) { vm.selected.value = it; page = "Start" }
                        "Osobowość" -> PersonalityScreen(settings, vm)
                        "Postępy" -> ProgressScreen(library,vm)
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
@Composable private fun NeonBackdrop() {
    Canvas(Modifier.fillMaxSize()) {
        drawRect(Brush.verticalGradient(listOf(Color(0xFF010506), Color(0xFF021013), Color(0xFF010708))))
        drawCircle(
            brush = Brush.radialGradient(
                listOf(Color(0x3322D9D0), Color(0x14138E91), Color.Transparent),
                center = Offset(size.width * .5f, size.height * .28f),
                radius = size.width * .85f
            ),
            center = Offset(size.width * .5f, size.height * .28f), radius = size.width * .85f
        )
        drawLine(ElectricBlue.copy(alpha = .55f), Offset(1f, 0f), Offset(1f, size.height), 2f)
        drawLine(ElectricBlue.copy(alpha = .55f), Offset(size.width - 1f, 0f), Offset(size.width - 1f, size.height), 2f)
    }
}
@Composable private fun Header(kicker: String, title: String) {
    Text(kicker, color = Lime, letterSpacing = 3.5.sp, fontSize = 11.sp, fontWeight = FontWeight.Bold)
    Text(title, style = MaterialTheme.typography.headlineLarge, color = Color(0xFFE9FFFC), fontWeight = FontWeight.Bold)
}
@Composable private fun InfoCard(title: String, body: String) {
    Surface(color = Panel, shape = RoundedCornerShape(22.dp), border = BorderStroke(1.dp, Hairline), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Text(title, color = NeonSoft, fontWeight = FontWeight.SemiBold); Text(body, color = Muted, style = MaterialTheme.typography.bodyMedium)
        }
    }
}
private fun duration(seconds: Long) = "%02d:%02d".format(seconds / 60, seconds % 60)
@Composable private fun HomeScreen(settings: Settings, library: Library, vm:LocoViewModel, selected: String, select: (String) -> Unit, start: () -> Unit, personality: () -> Unit, open: (String) -> Unit) {
    val progress by vm.progress.collectAsStateWithLifecycle()
    Header("LOCO / SPANISH AI", "Hiszpański bez litości.\nPolski bez cenzury.")
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        AssistChip(onClick = { open("Ustawienia") }, label = { Text("${settings.level} • zmień poziom") })
        AssistChip(onClick = { open("Postępy") }, label = { Text("${progress.streak} dni z rzędu") })
    }
    PersonalityCard(settings.forDialog().personality, personality)
    if(settings.tiktokStyle && !settings.previousVersionMode) InfoCard("STYL Z FILMIKÓW — WŁĄCZONY", "Szyderczy nauczyciel • roast 100 • mocne przekleństwa")
    Surface(color = Panel, shape = RoundedCornerShape(28.dp), border = BorderStroke(1.dp, Hairline)) {
        Column(Modifier.fillMaxWidth().padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("TWOJA NASTĘPNA RUNDA", fontSize = 11.sp, letterSpacing = 2.sp, color = Muted)
            Text(Scenarios.get(selected).title, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Text(Scenarios.get(selected).goal, color = Muted)
            Text("Ty próbujesz. LOCO szydzi, poprawia i rzuca kolejne wyzwanie. Nie znasz słowa? Zapytaj po polsku.", color = Lime)
            Button(onClick = start, modifier = Modifier.fillMaxWidth().heightIn(min = 58.dp), shape = RoundedCornerShape(16.dp)) { Text("ROZPOCZNIJ ROZMOWĘ", fontWeight = FontWeight.Bold) }
        }
    }
    Text("Dziś: ${duration(progress.todaySeconds)} rozmowy", style = MaterialTheme.typography.titleLarge)
    Text("Wybierz kierunek", fontWeight = FontWeight.Bold)
    Choices(listOf("free" to "Luźna rozmowa", "maintenance" to "Praca", "restaurant" to "Restauracja", "daily" to "Codzienna rozmowa"), selected, select)
    TextButton(onClick = { open("Scenariusze") }) { Text("WSZYSTKIE 18 SCENARIUSZY →") }
    InfoCard("Ostatnia sesja", library.sessions.lastOrNull()?.let { "${Scenarios.get(it.scenarioId).title} • ${duration(it.durationSeconds)}" } ?: "Twoja pierwsza rozmowa dopiero przed Tobą.")
    InfoCard("Ostatnio ćwiczone słowa", library.sessions.asReversed().asSequence().flatMap { it.learning.asSequence() }.filter { it.kind == "word" }.map { it.term }.distinct().take(6).joinToString(" • ").ifBlank { "Tutaj pojawią się słowa z Twoich rozmów." })
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
        Surface(onClick = { select(scenario.id) }, color = if (selected == scenario.id) Color(0xFF073337) else Panel,
            border = BorderStroke(1.dp, if (selected == scenario.id) Lime.copy(alpha = .65f) else Hairline), shape = RoundedCornerShape(18.dp)) {
            Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("${scenario.title}  /  ${scenario.level}", fontWeight = FontWeight.SemiBold)
                Text(scenario.goal, color = Muted, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}
@Composable private fun ConversationOrb(flow:kotlinx.coroutines.flow.StateFlow<Float>,phase:VoicePhase) {
    val level by flow.collectAsStateWithLifecycle()
    VoiceOrb(phase,audioLevel=level)
}
@Composable private fun PersonalityCard(p: Personality, edit: () -> Unit) {
    Surface(color = Panel, shape = RoundedCornerShape(20.dp), border = BorderStroke(1.dp, Hairline), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("CHARAKTER LOCO • ${p.preset.label}", fontWeight = FontWeight.Bold, color = Lime)
            Text("Roast: ${if(p.roastMode) "${p.roast}/100" else "wyłączony"} • Przekleństwa: ${p.profanity.label}")
            Text("Sarkazm ${p.sarcasm} • Bezczelność ${p.cheek} • Korekta ${p.correction}", color = Muted, fontSize = 12.sp)
            OutlinedButton(onClick = edit, modifier = Modifier.fillMaxWidth()) { Text("ZMIEŃ CHARAKTER I PRZEKLEŃSTWA") }
        }
    }
}
@OptIn(ExperimentalLayoutApi::class)
@Composable private fun ColumnScope.LiveScreen(seconds: Long, title: String, personality: Personality, vm: LocoViewModel, end: () -> Unit) {
    LiveConversationScreen(seconds,title,personality,vm.liveUi,vm.lessonDisplay,vm.liveTranscript,vm.audioLevel,vm::editPersonality,vm::mute,vm::retry,vm::lessonAction,end,vm::wordPresented)
}
@OptIn(ExperimentalLayoutApi::class)
@Composable internal fun ColumnScope.LiveConversationScreen(
    seconds:Long,title:String,personality:Personality,
    uiState:kotlinx.coroutines.flow.StateFlow<VoiceState>,lesson:kotlinx.coroutines.flow.StateFlow<LessonDisplay>,
    transcript:kotlinx.coroutines.flow.StateFlow<List<Transcript>>,level:kotlinx.coroutines.flow.StateFlow<Float>,
    editPersonality:()->Unit,mute:()->Unit,retry:()->Unit,action:(com.locospanish.ai.tutor.LessonAction)->Unit,end:()->Unit,onWordPresented:(String,String)->Unit={_,_->}
) {
    val state by uiState.collectAsStateWithLifecycle()
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text("LOCO / LIVE", color = Lime, letterSpacing = 3.5.sp, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            Text(title, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        }
        Surface(color = Lime.copy(alpha = .1f), border = BorderStroke(1.dp, Lime.copy(alpha = .55f)), shape = RoundedCornerShape(50)) {
            Text(duration(seconds), Modifier.padding(horizontal = 13.dp, vertical = 7.dp), color = NeonSoft, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
        }
    }
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text("${personality.preset.label} • roast ${if(personality.roastMode) personality.roast else 0}/100", color = Muted, fontSize = 12.sp)
            Text(state.message, color = Muted, fontSize = 12.sp, maxLines = if(state.phase==VoicePhase.ERROR) Int.MAX_VALUE else 1)
        }
        TextButton(onClick = editPersonality) { Text("CHARAKTER", letterSpacing = 1.sp, fontSize = 11.sp) }
    }
    LiveWordCard(lesson,retry,onWordPresented)
    Column(Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(14.dp)) {
    val lastCorrection=state.learning.lastOrNull { it.kind in setOf("grammar","pronunciation") }?.id
    var correctionVisible by remember { mutableStateOf(false) }
    LaunchedEffect(lastCorrection) { if(lastCorrection!=null) { correctionVisible=true;kotlinx.coroutines.delay(2500);correctionVisible=false } }
    ConversationOrb(level,if(correctionVisible && state.phase!=VoicePhase.ERROR) VoicePhase.ERROR else state.phase)
    Surface(color = Panel, shape = RoundedCornerShape(50), border = BorderStroke(1.dp, Hairline), modifier = Modifier.fillMaxWidth().wrapContentWidth(Alignment.CenterHorizontally)) {
        Row(Modifier.padding(horizontal = 18.dp, vertical = 9.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(9.dp)) {
            Canvas(Modifier.size(8.dp)) { drawCircle(if (state.phase == VoicePhase.ERROR) Color(0xFFFF746C) else Lime) }
            Text(if (state.muted) "MIKROFON WYŁĄCZONY" else state.phase.label, color = if (state.phase == VoicePhase.ERROR) Color(0xFFFF746C) else NeonSoft,
                fontWeight = FontWeight.Bold, letterSpacing = 3.sp, fontSize = 12.sp)
        }
    }
    Text(when { state.taskPending -> "Przygotowuję zadanie. Jeszcze nie odpowiadaj."; state.currentWord.isBlank() && state.taskError.isNotBlank() -> "Ponów pobieranie słów przyciskiem nad kulą."; state.reviewMode -> "Odpowiedz po hiszpańsku. Możesz poprosić o wyjaśnienie lub pominąć pytanie."; else -> "Odpowiedz po wzorcu słowa. Możesz też zadać pytanie." }, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center, color = Muted, fontSize = 12.sp)
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        OutlinedButton(onClick = mute, modifier = Modifier.weight(1f).heightIn(min = 52.dp), shape = RoundedCornerShape(16.dp), border = BorderStroke(1.dp, Hairline)) {
            Text(if (state.muted) "WŁĄCZ MIKROFON" else "WYCISZ", fontSize = 12.sp)
        }
        Button(onClick = end, modifier = Modifier.weight(1f).heightIn(min = 52.dp), shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF3B181B), contentColor = Color(0xFFFFA7A1))) { Text("ZAKOŃCZ", fontSize = 12.sp) }
    }
    if (state.phase == VoicePhase.ERROR) Button(onClick = retry, modifier = Modifier.fillMaxWidth()) { Text("SPRÓBUJ PONOWNIE") }
    Text("SZYBKIE PODPOWIEDZI", color = Muted, fontSize = 10.sp, letterSpacing = 2.sp)
    FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
        LiveAction("Powtórz",state.connected) { action(com.locospanish.ai.tutor.LessonAction.REPEAT) }
        LiveAction("Wolniej",state.connected) { action(com.locospanish.ai.tutor.LessonAction.SLOWER) }
        LiveAction("Nie rozumiem",state.connected) { action(com.locospanish.ai.tutor.LessonAction.EXPLAIN) }
        LiveAction("Po polsku",state.connected) { action(com.locospanish.ai.tutor.LessonAction.TRANSLATE) }
        LiveAction(if(state.reviewMode) "Wróć do nauki" else "Odpytaj mnie",state.connected) { action(if(state.reviewMode) com.locospanish.ai.tutor.LessonAction.RESUME else com.locospanish.ai.tutor.LessonAction.REVIEW) }
        LiveAction(if(state.reviewMode) "Pomiń" else "Dalej",state.connected && !state.reviewCompleted) { action(com.locospanish.ai.tutor.LessonAction.NEXT) }
    }
    Text("ROZMOWA • najnowsze na górze", color = NeonSoft, fontWeight = FontWeight.Bold, letterSpacing = 2.sp, fontSize = 12.sp)
    Text("Automatyczny zapis może zawierać błędy. Tekst AI może wyprzedzać głos.", color = Muted, fontSize = 11.sp)
    ConversationTranscript(transcript)

    }
}
@Composable internal fun LiveWordCard(flow:kotlinx.coroutines.flow.StateFlow<LessonDisplay>,retry:(()->Unit)?=null,onWordPresented:(String,String)->Unit={_,_->}) {
    val word by flow.collectAsStateWithLifecycle()
    DisposableEffect(Unit) { onDispose { onWordPresented("","") } }
    LaunchedEffect(word.word,word.meaning,word.pending,word.review) {
        if(word.word.isNotBlank() && word.meaning.isNotBlank() && !word.pending) {
            withFrameNanos { };withFrameNanos { }
            onWordPresented(word.word,word.meaning)
        }
    }
    Surface(color=PanelStrong,shape=RoundedCornerShape(24.dp),border=BorderStroke(1.dp,Brush.horizontalGradient(listOf(Lime.copy(alpha=.7f),ElectricBlue.copy(alpha=.5f)))),modifier=Modifier.fillMaxWidth()) {
    Column(Modifier.fillMaxWidth().heightIn(min=80.dp).padding(horizontal=18.dp,vertical=14.dp),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(4.dp)) {
        if(word.error.isNotBlank()) {
            Text(word.error,color=Color(0xFFFFA7A1))
            if(retry!=null) OutlinedButton(onClick=retry) { Text("POBIERZ SŁOWA PONOWNIE") }
        }
        if(word.pending) Text("Pobieram nowe hasło. Jeszcze nie odpowiadaj.",color=Muted)
        if(word.review) {
            Text(if(word.completed) "Odpytywanie zakończone" else "Odpytywanie ${word.index+1}/${word.total}",color=NeonSoft,fontWeight=FontWeight.Bold)
            Text(if(word.completed) "Poprawne: ${word.correct}/${word.total}" else "Jak powiesz po hiszpańsku „${word.meaning}”?",color=Color(0xFFE9FFFC),fontSize=21.sp,textAlign=TextAlign.Center)
        } else if(word.word.isNotBlank()) {
            Text(if(word.pending || word.error.isNotBlank()) "OSTATNIE HASŁO" else "POWIEDZ PO HISZPAŃSKU",color=Muted,fontSize=10.sp,letterSpacing=1.sp)
            Text(word.word,color=NeonSoft,fontSize=34.sp,fontWeight=FontWeight.Bold,textAlign=TextAlign.Center)
            Text(word.meaning,color=Color(0xFFCFE9E6),fontSize=17.sp,textAlign=TextAlign.Center)
        } else Text(if(word.error.isNotBlank()) "Użyj przycisku POBIERZ SŁOWA PONOWNIE powyżej." else "Pobieram zadanie…",color=Muted)
    }
    }
}
@Composable private fun ConversationTranscript(flow:kotlinx.coroutines.flow.StateFlow<List<Transcript>>) {
    val transcript by flow.collectAsStateWithLifecycle()
    if(transcript.isEmpty()) Text("Tutaj pojawi się Wasza rozmowa.",color=Muted)
    transcript.asReversed().forEach { line -> key(line.id) { ChatBubble(line) } }
}
@Composable private fun ChatBubble(line:Transcript) {
    val user=line.role=="user"
    val word=line.id.startsWith("word:")
    Box(Modifier.fillMaxWidth(),contentAlignment=if(user) Alignment.CenterEnd else Alignment.CenterStart) {
        Surface(color=if(user) Color(0xFF0B3A3C) else if(word) Color(0xFF14263A) else Panel,
            shape=RoundedCornerShape(topStart=18.dp,topEnd=18.dp,bottomStart=if(user) 18.dp else 4.dp,bottomEnd=if(user) 4.dp else 18.dp),
            border=BorderStroke(1.dp,if(user) Lime.copy(alpha=.35f) else Hairline),modifier=Modifier.fillMaxWidth(.86f).wrapContentWidth(if(user) Alignment.End else Alignment.Start)) {
            Column(Modifier.padding(horizontal=14.dp,vertical=10.dp),verticalArrangement=Arrangement.spacedBy(3.dp)) {
                Text(if(user) "TY" else if(word) "WZORZEC" else "LOCO${if(line.interrupted) " • przerwano" else ""}",color=if(user) NeonSoft else Muted,fontSize=10.sp,letterSpacing=1.5.sp,fontWeight=FontWeight.Bold)
                Text(line.text.ifBlank { "Rozpoznaję wypowiedź…" },color=Color(0xFFE9FFFC),fontSize=if(word) 20.sp else 15.sp,fontWeight=if(word) FontWeight.Bold else FontWeight.Normal)
            }
        }
    }
}
@Composable private fun LiveAction(label: String, enabled: Boolean, action: () -> Unit) {
    OutlinedButton(onClick = action, enabled = enabled, shape = RoundedCornerShape(50), border = BorderStroke(1.dp, Hairline), contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)) {
        Text(label, fontSize = 12.sp)
    }
}
@Composable private fun HelpButton(label: String, enabled: Boolean, action: () -> Unit) {
    OutlinedButton(onClick = action, enabled = enabled, modifier = Modifier.fillMaxWidth()) { Text(label) }
}
@Composable private fun PersonalityScreen(settings: Settings, vm: LocoViewModel) {
    val p = settings.personality
    val sample by vm.sample.collectAsStateWithLifecycle()
    val sampling by vm.sampling.collectAsStateWithLifecycle()
    fun change(transform: (Personality) -> Personality) = vm.changePersonality(transform)
    Header("TWÓJ NAUCZYCIEL", "Ustaw charakter.")
    Toggle("Styl z filmików — szyderczy roast", settings.tiktokStyle && !settings.previousVersionMode) { vm.setFilmStyle(it) }
    Text("Włączenie ustawia mocne docinki i przekleństwa. Profil, korekta i pozostałe suwaki pozostają niezależne; możesz je potem dostosować bez wyłączania trybu.", color = Muted)
    Text("Każdy poziom wybierasz osobno. Zmiany zapisują się automatycznie. Gotowy profil ustawia wszystkie suwaki; potem możesz je dostosować.", color = Muted)
    Choices(Preset.entries.map { it.name to it.label }, p.preset.name) {
        vm.selectPersonality(Preset.valueOf(it))
    }
    Toggle("Roast — docinki i wyśmiewanie błędów", p.roastMode) { vm.setRoast(it) }

    Text("Przekleństwa", fontWeight = FontWeight.Bold)
    Choices(Profanity.entries.map { it.name to it.label }, p.profanity.name) { val value=Profanity.valueOf(it); change { current -> current.copy(profanity = value) } }
    Text(when(p.profanity) { Profanity.OFF -> "Bez przekleństw, nawet przy mocnym roaście."; Profanity.LIGHT -> "Lekkie przekleństwa, np. cholera."; Profanity.STRONG -> "Mocne słowa bez cenzury, np. kurwa. Nie włączają samego roastu." }, color = Muted)
    IntSlider("Sarkazm", p.sarcasm) { val value=it; change { current -> current.copy(sarcasm = value) } }
    Text("0: bez ironii • 100: cięte riposty i ironiczne porównania", color = Muted, fontSize = 12.sp)
    IntSlider("Bezczelność / złośliwość", p.cheek) { val value=it; change { current -> current.copy(cheek = value) } }
    Text("0: uprzejmie • 100: pyskato, zadziornie i bez owijania", color = Muted, fontSize = 12.sp)
    IntSlider("Intensywność roastowania", p.roast, p.roastMode) { val value=it; change { current -> current.copy(roast = value) } }
    Text(if(p.roastMode) "0: bez docinek • 100: ostry komediowy roast i wyśmiewanie wpadek" else "Roast jest wyłączony. Włącz przełącznik powyżej, aby używać tego suwaka.", color = Muted, fontSize = 12.sp)
    Toggle("Automatyczna korekta", settings.autoCorrection) { vm.setAutoCorrection(it) }
    IntSlider("Intensywność korekty", p.correction) { val value=it; change { current -> current.copy(correction = value) } }
    Text(if(settings.autoCorrection) "0: tylko na prośbę • 100: także drobne błędy, po jednym naraz" else "Automatyczna korekta jest wyłączona w Ustawieniach. LOCO poprawia tylko na prośbę.", color = Muted, fontSize = 12.sp)
    if (p.preset == Preset.CUSTOM) OutlinedTextField(value = p.customPrompt, onValueChange = { val value=it.take(2000); change { current -> current.copy(customPrompt = value) } },
        label = { Text("CUSTOM PROMPT") }, supportingText = { Text("Preferencje stylu • ${p.customPrompt.length}/2000") }, modifier = Modifier.fillMaxWidth(), minLines = 4)
    Text("Humor reaguje na rozmowę. Jeśli utkniesz, LOCO uprości zadanie. Intensywność docinek zmieniasz w ustawieniach charakteru.", color = Muted)
    Button(onClick = vm::preview, enabled = !sampling, modifier = Modifier.fillMaxWidth()) { Text(if(sampling) "GENERUJĘ…" else "TESTUJ OSOBOWOŚĆ") }
    Text("Próbka tekstowa Gemini dla błędu „Yo soy 31 años”, bez mikrofonu i zapisu postępu. Korzysta z darmowego limitu modelu Gemini 3.8 Flash.", color = Muted, fontSize = 12.sp)
    if(sample.isNotBlank()) InfoCard("Próbka reakcji",sample)
}
@Composable private fun IntSlider(label: String, value: Int, enabled: Boolean = true, change: (Int) -> Unit) {
    Column {
        Text("$label  $value/100", fontWeight = FontWeight.SemiBold)
        Text(if(enabled) PersonalityEngine.level(value) else "Nieaktywne", color = Muted, fontSize = 12.sp)
        Slider(value = value.toFloat(), onValueChange = { change(kotlin.math.round(it).toInt()) }, valueRange = 0f..100f, enabled = enabled)
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
    val versionContext=LocalContext.current
    val installedVersion=remember(versionContext) { versionContext.packageManager.getPackageInfo(versionContext.packageName,0).versionName.orEmpty() }
    Text("Wersja aplikacji: $installedVersion", color = Muted)
    Text("Język interfejsu: polski", color = Muted)
    Text("Poziom hiszpańskiego", fontWeight = FontWeight.Bold)
    Choices(Level.entries.map { it.name to it.name }, s.level.name) { val level = Level.valueOf(it); vm.update(s.copy(level = level)) }
    Text(s.level.guidance, color = Muted)
    Text("Głos AI", fontWeight = FontWeight.Bold)
    Choices(GeminiProtocol.voices.map { it to it.replaceFirstChar { c -> c.uppercase() } }, s.voice) { vm.update(s.copy(voice = it)) }
    Text("Tempo głosu LOCO: ${"%.2f".format(s.speed)}×", fontWeight = FontWeight.SemiBold)
    Slider(value = s.speed, onValueChange = { vm.update(s.copy(speed = (kotlin.math.round(it*20)/20f))) }, valueRange = .6f..1.2f)
    Text("Działa od następnej rozmowy. 1.00× to naturalne tempo; przycisk Wolniej w rozmowie zwalnia dodatkowo.", color = Muted, fontSize = 12.sp)
    Toggle("Automatyczna korekta", s.autoCorrection) { vm.update(s.copy(autoCorrection = it)) }
    Text("Osobowość: ${s.personality.preset.label}. Zmień ją w zakładce Osobowość.", color = Muted)
    Text("Mikrofon i dźwięk rozmowy", fontWeight = FontWeight.Bold)
    Choices(AudioRoutePreference.entries.map { it.name to it.label }, s.audioRoute.name) { vm.update(s.copy(audioRoute = AudioRoutePreference.valueOf(it))) }
    Text("Automatycznie wybiera podłączone słuchawki. Opcja Bluetooth wymusza mikrofon i dźwięk w słuchawkach; jeśli nie są połączone, aplikacja użyje telefonu.", color = Muted)
    Toggle("Poprzedni styl rozmowy (tryb zgodności)", s.previousVersionMode) { vm.update(s.copy(previousVersionMode = it, tiktokStyle = false)) }
    Text(if (s.previousVersionMode) "Włączony poprzedni styl i zapisany charakter. Wyłącz, aby korzystać z nowych docinków." else "Działa nowy styl docinków. Ten przełącznik zmienia styl rozmowy. Powrót do poprzedniej aktualizacji 2.3.24 oraz awaryjnej 2.3.8 jest dostępny poniżej. Postępy i ustawienia pozostają w telefonie.", color = Muted)
    RollbackCard()
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
@Composable private fun ProgressScreen(library: Library,vm:LocoViewModel) {
    val p by vm.progress.collectAsStateWithLifecycle()
    var visibleSessions by rememberSaveable { mutableIntStateOf(30) }
    var expanded by rememberSaveable { mutableStateOf("") }
    Header("KAŻDA ROZMOWA SIĘ LICZY", "Twoje postępy.")
    if (p.sessions == 0) { InfoCard("Jeszcze czysta karta", "Rozpocznij pierwszą rozmowę. Pokażemy tu rzeczywisty czas nauki, ćwiczone słowa i obserwacje nauczyciela."); return }
    InfoCard("${duration(p.seconds)} rozmowy", "${p.sessions} sesji • ${p.streak} dni z rzędu • ${p.repetitions} powtórek • ${p.practiceTurns} wypowiedzi")
    InfoCard("Poznane / przećwiczone słowa (${p.words.size})", p.words.joinToString(" • ").ifBlank { "Brak zapisanych słów." })
    InfoCard("Już znasz — bez ciągłego odpytywania (${p.masteredWords.size})",p.masteredWords.joinToString(" • ").ifBlank { "Słowa pojawią się tutaj po potwierdzonym poprawnym użyciu." })
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







