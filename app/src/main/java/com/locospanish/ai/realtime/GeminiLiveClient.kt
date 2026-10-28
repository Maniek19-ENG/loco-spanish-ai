package com.locospanish.ai.realtime

import android.content.Context
import android.util.Base64
import com.google.gson.*
import com.locospanish.ai.model.*
import com.locospanish.ai.voice.*
import com.locospanish.ai.tutor.*
import com.locospanish.ai.tutor.TopicLessons
import com.locospanish.ai.tutor.AssessmentPolicy
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.channels.Channel
import okhttp3.*
import okio.ByteString
import java.util.concurrent.TimeUnit

/** 0.5 s of 24 kHz 16-bit mono audio. */
private const val STREAM_PREBUFFER_BYTES=24000

class GeminiLiveClient(
    context: Context, private val scope: CoroutineScope,
    private val http: WebSocket.Factory = OkHttpClient.Builder().connectTimeout(20,TimeUnit.SECONDS).readTimeout(0,TimeUnit.SECONDS).pingInterval(20,TimeUnit.SECONDS).build(),
    private val endpoint: String = "wss://generativelanguage.googleapis.com/ws/google.ai.generativelanguage.v1beta.GenerativeService.BidiGenerateContent",
    private val audioFactory: (AudioRoutePreference) -> LiveAudio = { PcmAudio(context,scope,it) },
    private val supplementFactory: (Context) -> SupplementSpeaker = { RoastSpeech(it) },
    private val responseTimeoutMs: Long = 12000,
    private val wordSpeakerFactory: (String,String) -> SupplementSpeaker = { key, voice -> SpanishReferenceSpeech(context.applicationContext,key,voice) },
    private val wordPlanner:suspend (String,String,Level,Collection<String>)->List<LessonWord> = { key,topic,level,excluded -> GeminiWordPlanner().generate(key,topic,level,excluded) },
    private val taskTimeoutMs:Long=15000,
    private val wordCache:LessonWordCache?=LessonWordCache(context),
    private val assessmentRetryMs:Long=1800,
    private val waitForLessonCard:Boolean=false
) {
    private val mutable = MutableStateFlow(VoiceState())
    val state = mutable.asStateFlow()
    private val mutableAudioLevel=MutableStateFlow(0f)
    val audioLevel=mutableAudioLevel.asStateFlow()
    private val review=ReviewSession()
    private val reviewBank=mutableListOf<LessonWord>()
    private var savedLessonWord:LessonWord?=null
    private var controlAction:LessonAction?=null
    private var sessionSpeed=1f

    private var incoming:Channel<String>?=null
    private var incomingJob:Job?=null
    private var socket: WebSocket? = null
    private var audio: LiveAudio? = null
    private var presentedAudio:PresentedAudio?=null
    private var displayedTask:Pair<String,String>?=null
    private val appContext=context.applicationContext
    private var nativeReply:Deferred<ByteArray?>?=null
    private var nativeReplyOwner:String?=null
    private var nativeReplyRevision=-1L
    private var nativeReplyScript=""
    private var timeout: Job? = null
    private var responseTimeout: Job? = null
    @Volatile private var epoch = 0
    private var transcript = GeminiTranscript()
    private val toolIds = mutableSetOf<String>()
    private val cancelledTools = mutableSetOf<String>()
    private val completionIds = mutableSetOf<String>()
    private var roastSpeech: SupplementSpeaker? = null
    private var supplementJob: Job? = null
    private var answerText = ""
    private var plannedRoast = ""
    private val riposteLaughter=RiposteLaughter(context.applicationContext)
    private var verdict = "CORRECT"
    private var gentleSession = false
    private var roastIndex = 0
    private val recentRoasts=ArrayDeque<String>()
    private var readingSupplement = false
    private var supplementForUserId: String? = null
    private var lessonScenario = "daily"
    private val lessonCompleted = mutableSetOf<String>()
    private val knownVocabulary = mutableSetOf<String>()
    private val knownWordLabels = mutableMapOf<String,String>()
    private var assessedUserId: String? = null
    private var assessedCorrection = ""
    private var controlTurn = false
    private val toolLearningIds = mutableMapOf<String,MutableSet<String>>()
    private val baselineKnownVocabulary = mutableSetOf<String>()
    private val taughtVocabulary = mutableSetOf<String>()
    private val toolResponses = mutableMapOf<String,JsonObject>()
    private var pendingLessonWord: String? = null
    private var requestedLearningTopic = ""
    private val provisionalAudio = mutableListOf<ByteArray>()
    private var provisionalBytes = 0
    private var provisionalAssistantId: String? = null
    private var lessonRevision=0L
    private var bufferedTaskRevision:Long?=null
    private var alignmentRetries=0
    private var approvedSpokenScript=""
    private var streamedBytes=0
    private var streamDeviation=false
    private var streamHalted=false
    private var roastGateOpen=false
    private var replyFinalized=false
    private var openingPromptPending=true
    private var roastRequested=false
    private var roastIsHelp=false
    private var dialogueReplyOnly=false
    private var helpAttempts=0
    private var lastHelpOwner:String?=null
    private var roastWord:LessonWord?=null
    private var roastHeard=""
    private var helpReferenceJob:Job?=null
    private var helpReferenceTurn:Pair<String,String>?=null
    private var failedHelpReferenceTurn:Pair<String,String>?=null
    private var wordSpeaker: SupplementSpeaker? = null
    private var spokenWordTurn: Pair<String,String>? = null
    private var referenceRevision=-1L
    private var assessmentWatchdog:Job?=null
    private var assessmentNudgedFor:String?=null
    private var deliveringTool = false
    private var forceWordReplay = false
    private var referenceGate = CompletableDeferred<Unit>()
    private var wordJob: Job? = null
    private var prefetchJob:Job?=null
    private var prefetchWord:String?=null
    private var wordsOnlyLesson=false
    private var queuedWordTurn: Pair<String,String>? = null
    private var referenceOwner: String? = null
    private var skipWordRequested=false
    private var taskJob:Job?=null
    private var taskRequestTopic=""
    private var liveTaskRequest=""
    private var materialRetryAt=0L
    private var taskKey=""
    private var taskSettings=Settings()
    private val generatedWords=mutableMapOf<String,MutableList<LessonWord>>()
    private var activeLessonWord: com.locospanish.ai.tutor.LessonWord? = null
    private var acceptedWordUserId: String? = null
    private var lastAcceptedWord:LessonWord?=null
    private val acceptedWordResponses = mutableMapOf<String,JsonObject>()

    fun preparing() { mutable.update { it.copy(phase=VoicePhase.CONNECTING,connected=false,message="Przygotowuję rozmowę…") } }
    fun start(settings: Settings, key: String, scenario: String, memory: String, reset: Boolean = true, retriesRemaining: Int = 2, knownWords: List<String> = emptyList(), reviewWords:List<LessonWord> = emptyList(), recentComments:List<String> = emptyList()) {
        val resumedPresented=if(reset) null else lastPresentedWord
        val resumedWord=if(reset) null else activeLessonWord ?: state.value.currentWord.takeIf { it.isNotBlank() && AssessmentPolicy.key(it) !in knownWords.map(AssessmentPolicy::key) }?.let { com.locospanish.ai.tutor.LessonWord(it,state.value.currentMeaning) }
        val resumedOwner=if(reset) null else acceptedWordUserId
        val resumedAccepted=if(reset) null else lastAcceptedWord
        val resumedResponses=if(reset) emptyMap() else acceptedWordResponses.toMap()
        close(); lastPresentedWord=resumedPresented; activeLessonWord=resumedWord;acceptedWordUserId=resumedOwner;lastAcceptedWord=resumedAccepted;acceptedWordResponses.putAll(resumedResponses); val generation = epoch
        wordsOnlyLesson=settings.level==Level.A1 && !settings.previousVersionMode
        if(!settings.previousVersionMode) wordSpeaker=wordSpeakerFactory(key,settings.voice)
        if(reset) { gentleSession=false;roastIndex=0;recentRoasts.clear();recentRoasts.addAll(recentComments.takeLast(24));review.stop();reviewBank.clear();reviewBank.addAll(reviewWords);savedLessonWord=null;sessionSpeed=settings.speed.takeIf { it>0f }?.coerceIn(.6f,1.2f) ?: 1f;controlAction=null }
        if(reset) { mutable.value = VoiceState(); toolIds.clear(); completionIds.clear(); toolResponses.clear(); taughtVocabulary.clear(); requestedLearningTopic = ""; pendingLessonWord = null; provisionalAudio.clear(); provisionalBytes = 0; provisionalAssistantId = null }
        if(reset) { knownVocabulary.clear(); knownWordLabels.clear(); baselineKnownVocabulary.clear(); toolLearningIds.clear() }
        knownWords.forEach { knownWordLabels[AssessmentPolicy.key(it)]=it }
        knownVocabulary.addAll(knownWords.map(AssessmentPolicy::key))
        baselineKnownVocabulary.addAll(knownWords.map(AssessmentPolicy::key))
        assessedUserId = null
        lessonScenario = scenario
        taskKey=key;taskSettings=settings
        if(key.isBlank()) { fail("Wpisz klucz Gemini w Ustawieniach.");return }
        if(reset) generatedWords.clear()
        if(activeLessonWord==null && !review.active) {
            val first=LessonTurnPolicy.next(scenario,null,knownVocabulary,taughtVocabulary,wordsOnly=settings.level==Level.A1,preferScenario=true)
            if(first!=null) setWord(first) else requestTask(generation)
        }
        lessonCompleted.clear(); lessonCompleted.addAll(TopicLessons.completed(memory,scenario))
        lessonCompleted.addAll(state.value.learning.filter { it.id.startsWith("scenario:$scenario:") }.map { it.term })
        transcript = GeminiTranscript(); cancelledTools.clear()
        mutable.update { it.copy(phase = VoicePhase.CONNECTING, connected = false, message = "Łączę z Gemini Live…") }
        if(key.isBlank()) { fail("Wpisz klucz Gemini w Ustawieniach."); return }
        var retryScheduled = false
        fun connectionFailed(code: Int, reason: String = "") {
            if(!retryScheduled && retriesRemaining > 0 && (code == 1011 || code == 0)) {
                retryScheduled = true
                val retryWord=activeLessonWord; val retryPresented=lastPresentedWord; val retryOwner=acceptedWordUserId;val retryAccepted=lastAcceptedWord;val retryResponses=acceptedWordResponses.toMap()
                close(); activeLessonWord=retryWord;lastPresentedWord=retryPresented;acceptedWordUserId=retryOwner;lastAcceptedWord=retryAccepted;acceptedWordResponses.putAll(retryResponses); val retryEpoch = epoch
                mutable.update { it.copy(connected = false, phase = VoicePhase.RECONNECTING, message = "Gemini przerwało połączenie. Ponawiam z zachowaniem rozmowy…") }
                scope.launch {
                    delay(if(retriesRemaining == 2) 1000 else 2000)
                    if(epoch == retryEpoch) start(settings,key,scenario,memory,false,retriesRemaining-1,knownVocabulary.toList().map { knownWordLabels[it] ?: it })
                }
            } else if(!retryScheduled) fail(GeminiProtocol.problem(code,reason))
        }
        fun dispatch(action: suspend () -> Unit) { scope.launch { if(epoch == generation) action() } }
        val resume = if(reset) "" else state.value.transcript.takeLast(6).joinToString("\n") { "${it.role}: ${it.text.take(250)}" }
        val request = Request.Builder().url(endpoint).header("x-goog-api-key",key).build()
        val messages=Channel<String>(256);incoming=messages
        incomingJob=scope.launch {
            for(raw in messages) {
                if(epoch!=generation) break
                try {
                    val parsed=withContext(Dispatchers.Default) { JsonParser.parseString(raw).asJsonObject }
                    if(epoch==generation) {
                        if(parsed.has("_connection_closed")) connectionFailed(parsed.get("_connection_closed").asInt,parsed.str("_reason"))
                        else event(parsed,generation,reset,settings,scenario,memory)
                    }
                } catch(cancelled:CancellationException) { throw cancelled }
                catch(_:SecurityException) { fail("Brak dostępu do mikrofonu. Włącz uprawnienie w ustawieniach telefonu.") }
                catch(_:Exception) { fail("Odebrano nieprawidłową odpowiedź Gemini. Spróbuj ponownie.") }
            }
        }
        fun queueFailure(code:Int,reason:String="") {
            if(epoch==generation && messages.trySend(GeminiProtocol.json("_connection_closed" to code,"_reason" to reason).toString()).isFailure)
                dispatch { connectionFailed(code,reason) }
        }
        socket = http.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) { dispatch {
                webSocket.send(GeminiProtocol.setup(settings,scenario,("Ostatnia rozmowa:\n" + resume + "\nWcześniejsza nauka:\n" + memory).take(2400)).toString())
            } }
            override fun onMessage(webSocket: WebSocket, text: String) {
                if(epoch==generation && messages.trySend(text).isFailure) dispatch { fail("Odpowiedzi napływają zbyt szybko. Spróbuj ponownie; postęp jest zachowany.") }
            }
            override fun onMessage(webSocket: WebSocket, bytes: ByteString) = onMessage(webSocket,bytes.utf8())
            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) { queueFailure(response?.code ?: 0) }
            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) { webSocket.close(code,null);queueFailure(code,reason) }
            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) { queueFailure(code,reason) }
        })
        timeout = scope.launch { delay(25000); if(epoch == generation && !state.value.connected) fail("Upłynął czas łączenia. Sprawdź internet, klucz i dostępność Gemini.") }
    }
    private suspend fun event(e: JsonObject, generation: Int, reset: Boolean, settings: Settings, scenario: String, memory: String) {
        if(e.has("error")) { val error = e.getAsJsonObject("error"); fail(GeminiProtocol.problem(error.get("code")?.asInt ?: 0,error.str("message"))); return }
        if(e.has("setupComplete")) {
            timeout?.cancel(); mutable.update { it.copy(connected = true,phase = VoicePhase.THINKING,message = "Przygotowuję pierwsze słowo…") }
            val output=audioFactory(settings.audioRoute)
            val device=if(waitForLessonCard) PresentedAudio(output).also { presentedAudio=it;it.task(activeLessonWord?.es.orEmpty(),activeLessonWord?.pl.orEmpty());displayedTask?.let { card -> it.presented(card.first,card.second) } } else output
            audio = device
            device.observeLevel { level -> scope.launch { if(epoch == generation && state.value.connected) mutableAudioLevel.value=level } }
            device.start(onInput = { bytes ->
                if(epoch == generation && !state.value.muted && !openingPromptPending) {
                    val ws = socket
                    if(ws != null && (ws.queueSize() > 256000 || !ws.send(GeminiProtocol.audio(Base64.encodeToString(bytes,Base64.NO_WRAP)).toString()))) {
                        scope.launch { if(epoch == generation) fail("Sieć nie nadąża za rozmową. Spróbuj ponownie przy lepszym połączeniu.") }
                    }
                }
            }, onError = { scope.launch { if(epoch == generation) fail("Mikrofon lub głośnik jest niedostępny. Sprawdź uprawnienia i spróbuj ponownie.") } },
                onPlaying = { playing -> scope.launch { if(epoch == generation && state.value.connected) mutable.update { it.copy(phase = if(playing) VoicePhase.SPEAKING else if(activeLessonWord==null && !review.active) VoicePhase.THINKING else VoicePhase.LISTENING) } } })
            if(sessionSpeed!=1f) device.setSpeed(sessionSpeed)
            device.mute(state.value.muted)
            mutable.update { it.copy(message = "Gemini Live • ${device.routeLabel}") }
            val initial=if(review.active) review.current else activeLessonWord
            if(initial!=null) setWord(initial)
            else { if(review.active) command(review.instruction()) else if(state.value.taskError.isNotBlank()) requestTaskFromLive() else requestTask(generation);return }
            referenceGate.complete(Unit) // The first reference must not wait for a silent Live turn.
            val pronounced=!review.active && initial?.let { queueWord(it.es,"start",generation) } == true
            if(epoch != generation || (reset && state.value.transcript.any { it.role == "user" })) return
            val opening=if(review.active) review.instruction() else if(initial != null && pronounced) "Aplikacja odtwarza już pierwsze słowo ${initial.es}. Teraz po polsku daj nową sytuacyjną docinkę, znaczenie ${initial.pl} i zapytaj: ${com.locospanish.ai.tutor.LessonTurnPolicy.question(initial)} Uczeń odpowiada po hiszpańsku. Nie czytaj ponownie hiszpańskiego słowa, nie dodawaj zdania ani zakończenia lekcji." else GeminiProtocol.startCommand(settings, scenario, reconnect = !reset || memory.contains("KONTYNUACJA_LEKCJI"), memory = memory)+"\nAktualne zadanie widoczne na ekranie: ${initial.es} — ${initial.pl}. Ucz tego hasła i nie zmieniaj go wyłącznie głosowo."
            command(opening,interruptAudio = !pronounced)
        }
        e.getAsJsonObject("serverContent")?.let contentBlock@ { content ->
            mutable.update { transcript.apply(it,content).let { updated -> activeLessonWord?.let { word -> updated.copy(currentWord=word.es,currentMeaning=word.pl) } ?: updated } }
            if(content.flag("interrupted")) { approvedSpokenScript="";streamedBytes=0;streamDeviation=false;streamHalted=false;roastGateOpen=false;provisionalAudio.clear(); provisionalBytes = 0; provisionalAssistantId = null; if(openingPromptPending || activeLessonWord==null) awaitResponse() else responseTimeout?.cancel(); supplementJob?.cancel(); answerText = ""; plannedRoast = ""; audio?.interrupt(); mutable.update { it.copy(phase = if(openingPromptPending || (activeLessonWord==null && !review.active)) VoicePhase.THINKING else VoicePhase.LISTENING) } }
            if(content.has("outputTranscription")) {
                answerText = state.value.transcript.lastOrNull { it.role == "assistant" && !it.id.startsWith("word:") }?.text.orEmpty()
                if(deliveringTool || ((!controlTurn || content.has("inputTranscription")) && assessedUserId != state.value.transcript.lastOrNull { it.role == "user" }?.id))
                    provisionalAssistantId = state.value.transcript.lastOrNull { it.role == "assistant" && !it.id.startsWith("word:") }?.id
                if(streamedBytes>0 && !streamHalted && streamedReplyContradicts()) {
                    // Cut a streamed reply as soon as its text contradicts the verified grade or the task on screen.
                    streamHalted=true;audio?.interrupt()
                }
            }
            if(content.has("inputTranscription")) {
                replyFinalized=false;roastRequested=false;dialogueReplyOnly=false
                approvedSpokenScript="";streamedBytes=0;streamDeviation=false;streamHalted=false;roastGateOpen=false
                alignmentRetries=0
                controlTurn = false;skipWordRequested=false;controlAction=null
                val currentOwner=state.value.transcript.lastOrNull { it.role == "user" }?.id
                if(currentOwner != referenceOwner) { wordJob?.cancel();queuedWordTurn=null;referenceGate=CompletableDeferred();referenceOwner=currentOwner }
                if(state.value.transcript.lastOrNull { it.role == "user" }?.id != supplementForUserId) readingSupplement = false
                val input = state.value.transcript.lastOrNull { it.role == "user" }?.text.orEmpty()
                watchAssessment(currentOwner,generation)
                helpReferenceJob?.cancel()
                if(currentOwner!=null) helpReferenceJob=scope.launch {
                    if(content.getAsJsonObject("inputTranscription")?.flag("finished")!=true) delay(350)
                    val latest=state.value.transcript.lastOrNull { it.role=="user" }
                    val target=activeLessonWord
                    if(epoch==generation && latest?.id==currentOwner && target!=null && LearnerIntent.currentWordReference(latest.text,target))
                        queueHelpReference(target,currentOwner,generation)
                }
                supplementJob?.cancel()
            }
            val parts = content.getAsJsonObject("modelTurn")?.getAsJsonArray("parts")
            parts?.forEach { part -> part.asJsonObject.getAsJsonObject("inlineData")?.let { data ->
                if(((!state.value.taskPending && (activeLessonWord!=null || review.active)) || dialogueReplyOnly || LearnerIntent.questionInsteadOfTrial(state.value.transcript.lastOrNull { it.role=="user" }?.text.orEmpty(),activeLessonWord)) && !content.flag("interrupted") && data.str("mimeType").startsWith("audio/pcm")) {
                    val pcm = Base64.decode(data.str("data"),Base64.DEFAULT)
                    val currentUser = state.value.transcript.lastOrNull { it.role == "user" }
                    // Only an unassessed draft is held back; the assessed reply streams at once instead of waiting for the whole generation.
                    // A chunk whose text already contradicts the grade or the card is never played.
                    if(!streamHalted && streamedReplyContradicts()) { streamHalted=true;if(streamedBytes>0) audio?.interrupt() }
                    if(deliveringTool || streamHalted || holdForRoast() || (controlTurn && validateLessonSpeech()) || (!controlTurn && currentUser != null && assessedUserId != currentUser.id)) {
                        if(provisionalBytes + pcm.size > 1024 * 1024) { fail("Gemini nie zakończyło oceny wypowiedzi. Spróbuj ponownie."); return }
                        if(provisionalAudio.isEmpty() && streamedBytes==0) bufferedTaskRevision=lessonRevision
                        provisionalAudio.add(pcm); provisionalBytes += pcm.size
                    } else if(streamedBytes==0 && provisionalBytes+pcm.size<STREAM_PREBUFFER_BYTES) {
                        // Collect ~0.5 s before the first sound, so network jitter cannot chop the start of a streamed reply.
                        if(provisionalAudio.isEmpty()) bufferedTaskRevision=lessonRevision
                        provisionalAudio.add(pcm); provisionalBytes += pcm.size
                    } else if(streamedBytes==0 && provisionalAudio.isNotEmpty()) {
                        provisionalAudio.add(pcm); provisionalBytes += pcm.size
                        val size=provisionalBytes;flushProvisionalAudio();streamedBytes+=size
                    } else if(audio?.enqueue(pcm) != true) fail("Odtwarzanie audio nie nadąża. Spróbuj ponownie.")
                    else { if(streamedBytes==0) bufferedTaskRevision=lessonRevision;streamedBytes+=pcm.size;openingPromptPending=false;responseTimeout?.cancel() }
                }
            } }
            if(holdForRoast() && provisionalAudio.isNotEmpty() && content.has("outputTranscription") && roastHeardInDraft()) {
                // The riposte is confirmed in the first sentence: play it and stream the rest instead of waiting for the whole turn.
                roastGateOpen=true
                if(!streamedReplyContradicts()) { val size=provisionalBytes;flushProvisionalAudio();streamedBytes+=size }
            }
            if(approvedSpokenScript.isNotBlank() && provisionalAudio.isNotEmpty() && !streamDeviation && verdict=="ERROR") {
                val authorized=controlTurn || state.value.transcript.lastOrNull { it.role=="user" }?.let { assessedUserId==it.id } != false
                if(streamedBytes>0 && answerText.isNotBlank() && !SpokenReplyPlan.compatible(approvedSpokenScript,answerText)) {
                    // Preserve already verified speech; hold only the unverified continuation.
                    streamDeviation=true
                } else if(authorized && SpokenReplyPlan.ready(approvedSpokenScript,answerText)) {
                    val size=provisionalBytes
                    flushProvisionalAudio();streamedBytes+=size
                }
            }
            if(content.has("inputTranscription") && state.value.phase != VoicePhase.SPEAKING) { mutable.update { it.copy(phase = VoicePhase.THINKING) }; awaitResponse() }
            if((content.flag("generationComplete") || content.flag("turnComplete")) && !replyFinalized && !content.flag("interrupted")) {
                replyFinalized=true
                val target=activeLessonWord
                val referenceHelp=currentWordHelpRequested()
                val actualUser=state.value.transcript.lastOrNull { it.role=="user" }
                // Buffering alone is not authorization: a completed model turn can still omit the grade.
                val unassessedTrial=!controlTurn && actualUser!=null && assessedUserId!=actualUser.id
                val helpWithoutGrade=actualUser!=null && (referenceHelp || LearnerIntent.questionInsteadOfTrial(actualUser.text,target) || WordAttemptPolicy.newTopic(actualUser.text))
                if(unassessedTrial && (!helpWithoutGrade || SpokenTeachingPolicy.assertsError(answerText))) {
                    val draft=provisionalAssistantId
                    if(draft!=null) mutable.update { value -> value.copy(transcript=value.transcript.filterNot { it.id==draft }) }
                    provisionalAudio.clear();provisionalBytes=0;provisionalAssistantId=null;bufferedTaskRevision=null
                    answerText="";approvedSpokenScript=""
                    mutable.update { it.copy(phase=VoicePhase.THINKING) }
                    if(!e.has("toolCall")) watchAssessment(actualUser?.id,generation,immediate=true)
                    return@contentBlock
                }
                val correctTrial=actualUser!=null && ((assessedUserId==actualUser.id && verdict=="CORRECT") || acceptedWordUserId==actualUser.id)
                val wrongTask=validateLessonSpeech() && target!=null && (provisionalAudio.isNotEmpty() || streamedBytes>0) &&
                    (bufferedTaskRevision!=lessonRevision || LessonSpeechContract.mismatch(answerText,target))
                val uncertainTrial=actualUser!=null && assessedUserId==actualUser.id && verdict=="UNCERTAIN"
                val falseRejection=(correctTrial && SpokenTeachingPolicy.rejectsCorrectAnswer(answerText)) ||
                    ((referenceHelp || uncertainTrial) && SpokenTeachingPolicy.assertsError(answerText))
                // A reply already heard is not regenerated for style alone; that would only add another turn.
                var roastIssue=if(roastRequested && verdict=="ERROR" && !referenceHelp && !roastIsHelp && alignmentRetries==0 && streamedBytes==0) SpokenRoastPolicy.replyIssue(taskSettings,answerText,plannedRoast,verdict,recentRoasts,roastHeard,roastWord) else ""
                val emptyJoke=!taskSettings.previousVersionMode && SpokenTeachingPolicy.onlyLaughter(answerText)
                val referenceProvided=target!=null && (referenceWasPlayed(target.es) || (actualUser!=null && queuedWordTurn==(actualUser.id to target.es)))
                val missingHelp=referenceHelp && target!=null && answerText.isNotBlank() &&
                    (!SpokenTeachingPolicy.answersCurrentWord(answerText,target) || (!referenceProvided && !AssessmentPolicy.contains(answerText,target.es)))
                if(roastIssue.isNotBlank() && !wrongTask && !falseRejection && !missingHelp && !emptyJoke && streamedBytes==0 &&
                    actualUser!=null && nativeReplyOwner==actualUser.id && nativeReplyRevision==lessonRevision && nativeReply!=null && nativeReply?.isCancelled!=true) {
                    val pcm=withTimeoutOrNull(600) { nativeReply?.await() }
                    if(pcm!=null && pcm.isNotEmpty()) {
                        provisionalAudio.clear();provisionalBytes=0
                        answerText=nativeReplyScript
                        val spokenId=state.value.transcript.lastOrNull { it.role=="assistant" && !it.id.startsWith("word:") }?.id ?: "native:${actualUser.id}"
                        mutable.update { it.copy(transcript=RealtimeEvents.transcript(it.transcript,spokenId,"assistant",answerText,false)) }
                        if(audio?.enqueue(pcm)==true) { streamedBytes=pcm.size;roastIssue="";responseTimeout?.cancel() }
                    }
                }
                if((wrongTask || falseRejection || missingHelp || emptyJoke || roastIssue.isNotBlank()) && target!=null) {
                    if(streamedBytes>0) audio?.interrupt()
                    val draft=state.value.transcript.lastOrNull { it.role=="assistant" && !it.id.startsWith("word:") }?.id
                    mutable.update { value -> value.copy(transcript=value.transcript.filterNot { it.id==draft }) }
                    provisionalAudio.clear();provisionalBytes=0;bufferedTaskRevision=null;provisionalAssistantId=null
                    answerText="";responseTimeout?.cancel()
                    if(alignmentRetries++==0) {
                        val repair=when {
                            referenceHelp -> "Uczeń prosi o pomoc, NIE odpowiadał na zadanie. Najpierw odpowiedz dokładnie: ${SpokenTeachingPolicy.helpAnswer(target)} Podaj jedną wskazówkę, POTEM świeża docinka zgodna z ustawieniami. Nie kończ na śmiechu, nie oceniaj pytania, nie zmieniaj słowa."
                            falseRejection -> if(correctTrial) "Poprawna odpowiedź ucznia została potwierdzona. Nie mów, że było źle, nie proś o kolejne powtórzenie zaliczonej próby i nie wymyślaj błędu. Następne zadanie: "+LessonSpeechContract.instruction(target)+" Krótka sytuacyjna docinka, potem to pytanie." else "Nie potwierdzono błędu ucznia. Nie twierdź, że było źle, nie wyśmiewaj zmyślonego błędu i nie zaliczaj słowa. Krótko wyjaśnij aktualne zadanie lub odpowiedz na pytanie. "+LessonSpeechContract.instruction(target)
                            roastIssue.isNotBlank() -> roastIssue+" Wymyśl NOWĄ, ostrą sytuacyjną puentę. Nie wymyślaj błędu przy poprawnej próbie. Kontekst: "+GeminiProtocol.json("learner" to roastHeard,"attempted_word" to roastWord?.es,"attempted_meaning" to roastWord?.pl,"verdict" to verdict)+". "+SpokenRoastPolicy.freshInstruction(taskSettings,true)+" "+ReplyDelivery.performance(taskSettings,verdict)+" Potem: "+LessonSpeechContract.instruction(target)
                            emptyJoke -> "Nie odtwarzaj samego śmiechu. Wymyśl jedną świeżą, konkretną i zabawną ripostę z bieżącej sytuacji, z przekleństwami i parodią zgodnymi z ustawieniami. Krótki śmiech może towarzyszyć puencie. Potem: "+LessonSpeechContract.instruction(target)
                            else -> "Poprzednie pytanie nie pasowało do ekranu i NIE zostało odtworzone. "+LessonSpeechContract.instruction(target)+" Wypowiedz świeżą krótką docinkę zgodną z ustawieniami, potem wyłącznie to pytanie. Nie wybieraj nowego hasła."
                        }
                        command(repair,interruptAudio=false)
                        if(referenceHelp) queueHelpReference(target,actualUser?.id ?: "help",generation)
                        else queueWord(target.es,actualUser?.id ?: "start",generation)
                    } else {
                        referenceGate.complete(Unit)
                        if(referenceHelp) queueHelpReference(target,actualUser?.id ?: "help",generation)
                        else queueWord(target.es,actualUser?.id ?: "start",generation)
                        mutable.update { it.copy(phase=VoicePhase.LISTENING,message=if(referenceHelp) SpokenTeachingPolicy.helpAnswer(target) else LessonTurnPolicy.question(target)) }
                    }
                    return
                }
                if(provisionalAudio.isNotEmpty() || streamedBytes>0) responseTimeout?.cancel()
                // If no tool was called (e.g. a request to change topic), deliver the single answer.
                val hadSpeech=provisionalAudio.isNotEmpty() || streamedBytes>0
                flushProvisionalAudio()
                val joke=answerText.substringBefore("Jak powiesz",answerText)
                val realJoke=SpokenRoastPolicy.contextualPrimary(settings,joke,verdict,recentRoasts,roastHeard,roastWord).isNotBlank() ||
                    (plannedRoast.isNotBlank() && SpokenRoastPolicy.delivered(answerText,plannedRoast))
                if(hadSpeech && actualUser!=null && assessedUserId==actualUser.id) {
                    riposteLaughter.pcm(settings,actualUser.id,verdict,answerText,realJoke,roastIsHelp || referenceHelp)?.let { laugh -> audio?.enqueue(laugh) }
                }
                referenceGate.complete(Unit)
                provisionalAudio.clear(); provisionalBytes = 0; provisionalAssistantId = null;bufferedTaskRevision=null;alignmentRetries=0
                approvedSpokenScript="";streamedBytes=0;streamDeviation=false;streamHalted=false;roastGateOpen=false
                pendingLessonWord?.let { if(AssessmentPolicy.contains(answerText,it)) taughtVocabulary.add(it) }; pendingLessonWord = null
                TopicLessons.get(scenario).words.filter { TopicLessons.matches(answerText,it) }.forEach { taughtVocabulary.add(it.es) }
                if(state.value.phase == VoicePhase.THINKING && activeLessonWord!=null && (answerText.isNotBlank() || spokenWordTurn!=null)) mutable.update { it.copy(phase = VoicePhase.LISTENING) }
                // Docinka i nauka należą do tej samej odpowiedzi. Nie uruchamiaj osobnej tury.
                if(answerText.isNotBlank()) { recentRoasts.addLast(answerText.take(900));while(recentRoasts.size>12) recentRoasts.removeFirst() }
                answerText = ""; plannedRoast = ""; verdict = "CORRECT"
            }
        }
        e.getAsJsonObject("toolCallCancellation")?.getAsJsonArray("ids")?.forEach { id ->
            cancelledTools.add(id.asString)
            completionIds.remove(id.asString)
            val removeIds=toolLearningIds.remove(id.asString).orEmpty()+id.asString
            mutable.update { it.copy(learning = it.learning.filterNot { item -> item.id in removeIds }, completed = completionIds.isNotEmpty()) }
            knownVocabulary.clear();knownVocabulary.addAll(baselineKnownVocabulary)
            state.value.learning.filter { it.kind in setOf("known_word","word","repetition") && it.evidence.isNotBlank() }.groupBy { AssessmentPolicy.key(it.term) }
                .filterValues { items -> items.isNotEmpty() }.keys.forEach { knownVocabulary.add(it) }
        }
        if(!e.flag("_assessment_ready") && !readingSupplement && !controlTurn) {
            val actual=state.value.transcript.lastOrNull { it.role == "user" }?.text.orEmpty()
            val needsTranscript=e.getAsJsonObject("toolCall")?.getAsJsonArray("functionCalls")?.any { call ->
                val fn=call.asJsonObject
                val heard=fn.getAsJsonObject("args")?.str("heard_text").orEmpty()
                val a=AssessmentPolicy.speech(actual);val h=AssessmentPolicy.speech(heard)
                fn.str("name") == "assess_user_turn" && a.isNotBlank() && h != a && h.startsWith(a)
            } ?: false
            if(needsTranscript) {
                val deferred=GeminiProtocol.json("toolCall" to e.getAsJsonObject("toolCall"),"_assessment_ready" to true)
                scope.launch { delay(300);if(epoch == generation && state.value.connected) event(deferred,generation,reset,settings,scenario,memory) }
                return
            }
        }
        e.getAsJsonObject("toolCall")?.getAsJsonArray("functionCalls")?.let { calls ->
            val responses = calls.filter { it.asJsonObject.str("id") !in cancelledTools }.sortedBy { if(it.asJsonObject.str("name") == "assess_user_turn") 0 else 1 }.map { call ->
                val fn = call.asJsonObject; val id = fn.str("id"); val name = fn.str("name"); val args = fn.getAsJsonObject("args") ?: JsonObject()
                toolResponses[id]?.takeIf { id !in cancelledTools }?.let { return@map it.deepCopy() }
                if(name == "assess_user_turn" && settings.level == com.locospanish.ai.model.Level.A1 && !controlTurn) {
                    val owner=state.value.transcript.lastOrNull { it.role == "user" }?.id
                    if(owner != null && owner == acceptedWordUserId) {
                        val cached=acceptedWordResponses[owner]?.deepCopy() ?: GeminiProtocol.json("response" to GeminiProtocol.json("saved" to false))
                        cached.addProperty("id",id);cached.addProperty("name",name)
                        cached.getAsJsonObject("response").addProperty("allow_next_word",false)
                        cached.getAsJsonObject("response").addProperty("duplicate_attempt",true)
                        cached.getAsJsonObject("response").addProperty("lesson_instruction",if(review.active) review.instruction() else WordAttemptPolicy.hold(activeLessonWord))
                        return@map cached
                    }
                }
                var advanceWord=false
                var requestedTopicChange=false
                var learnerQuestion=false
                var previousAnswer: com.locospanish.ai.tutor.LessonWord? = null
                var saved = false
                var assessment: com.locospanish.ai.tutor.Assessment? = null
                var teachingWord: com.locospanish.ai.tutor.LessonWord? = null
                val user = if(controlTurn) null else state.value.transcript.lastOrNull { it.role == "user" }
                val attemptedWord=activeLessonWord
                learnerQuestion=LearnerIntent.questionInsteadOfTrial(user?.text.orEmpty(),activeLessonWord) || LearnerIntent.currentWordReference(user?.text.orEmpty(),activeLessonWord) || currentWordHelpRequested()
                val intent=DialogueIntent.read(args,user?.text.orEmpty(),activeLessonWord)
                learnerQuestion=learnerQuestion || DialogueIntent.replyOnly(intent)
                val helpTurn=learnerQuestion || controlAction in setOf(LessonAction.EXPLAIN,LessonAction.TRANSLATE)
                if(name=="assess_user_turn") { dialogueReplyOnly=helpTurn;if(helpTurn && activeLessonWord==null) presentedAudio?.allowReplyWithoutTask() }
                if(intent==DialogueIntent.Kind.SKIP && user!=null && !review.active) skipWordRequested=true
                if(name=="assess_user_turn") { roastRequested=SpokenRoastPolicy.enabled(settings);roastIsHelp=helpTurn;roastWord=attemptedWord;roastHeard=user?.text.orEmpty() }
                val fresh = id.isNotEmpty() && id !in cancelledTools && toolIds.add(id)
                if(fresh && name == "assess_user_turn") {
                    assessmentWatchdog?.cancel();assessmentWatchdog=null
                    deliveringTool = true
                    // Discard a premature draft, not the validated answer that follows this tool.
                    if(provisionalAudio.isNotEmpty() || provisionalAssistantId!=null) {
                        val draftId = provisionalAssistantId
                        if(draftId != null) mutable.update { state -> state.copy(transcript=state.transcript.map { if(it.id == draftId) it.copy(text="") else it }) }
                        answerText = ""
                    }
                    provisionalAudio.clear(); provisionalBytes = 0; provisionalAssistantId = null
                    val original = AssessmentPolicy.evaluate(user?.text.orEmpty(),args)
                    val modelHeard=if(args.str("verdict").uppercase()=="CORRECT" && args.str("confidence")=="CERTAIN") args.str("heard_text") else ""
                    learnerQuestion=helpTurn
                    previousAnswer=if(review.active) null else activeLessonWord?.let { active -> lastAcceptedWord?.takeIf { it.es!=active.es && WordAttemptPolicy.directAnswer(it,user?.text.orEmpty()) } ?: lastPresentedWord?.takeIf { previous -> previous.es != active.es && spokenWordTurn?.second != active.es && WordAttemptPolicy.directAnswer(previous,user?.text.orEmpty()) } }
                    val checked=if(intent in setOf(DialogueIntent.Kind.SKIP,DialogueIntent.Kind.TOPIC)) Assessment("UNCERTAIN",user?.text.orEmpty(),explanation="Uczeń zmienia ćwiczenie lub temat. Uszanuj prośbę bez oceniania i bez zaliczania słowa.") else if(learnerQuestion) Assessment("UNCERTAIN",user?.text.orEmpty(),explanation="Uczeń zadał pytanie. Odpowiedz na pytanie, nie oceniaj go jak próby słowa.") else if(previousAnswer != null) com.locospanish.ai.tutor.Assessment("CORRECT",user?.text.orEmpty(),explanation="To poprawna odpowiedź na poprzednie słowo, a nie błąd nowego zadania. Nie zaliczaj nowego hasła; przypomnij bieżące pytanie bez zarzucania błędu.") else if(review.active || (settings.level == com.locospanish.ai.model.Level.A1 && !settings.previousVersionMode) || activeLessonWord?.let { WordAttemptPolicy.directAnswer(it,user?.text.orEmpty()) }==true) com.locospanish.ai.tutor.WordAttemptPolicy.evaluate(activeLessonWord,user?.text.orEmpty(),original,args.str("confidence") == "CERTAIN" && AssessmentPolicy.speech(args.str("heard_text")) == AssessmentPolicy.speech(user?.text.orEmpty()),modelHeard) else original
                    requestedTopicChange=!review.active && !learnerQuestion && user != null && (intent==DialogueIntent.Kind.TOPIC || com.locospanish.ai.tutor.WordAttemptPolicy.newTopic(user.text))
                    advanceWord=!learnerQuestion && previousAnswer == null && user != null && com.locospanish.ai.tutor.WordAttemptPolicy.canAdvance(activeLessonWord,user.text,checked,modelHeard)
                    if(advanceWord) { acceptedWordUserId=user?.id;lastAcceptedWord=attemptedWord }
                    assessment = checked
                    val verdict = if(readingSupplement) "CORRECT" else checked.verdict
                    this.verdict = verdict; plannedRoast = if(verdict == args.str("verdict").uppercase() || (verdict == "UNCERTAIN" && args.str("verdict").uppercase() == "CORRECT")) args.str("roast").take(320) else ""
                    if(!readingSupplement) {
                        assessedUserId=user?.id; assessedCorrection=checked.correction
                        var vocabulary=if(!learnerQuestion && (settings.level != com.locospanish.ai.model.Level.A1 || advanceWord)) AssessmentPolicy.vocabulary(args,checked,user?.id.orEmpty()) else emptyList()
                        if(advanceWord && user != null && activeLessonWord != null && (com.locospanish.ai.tutor.WordAttemptPolicy.matches(activeLessonWord!!,user.text) || com.locospanish.ai.tutor.WordAttemptPolicy.canAdvance(activeLessonWord,user.text,checked,modelHeard)) && vocabulary.none { AssessmentPolicy.key(it.term) == AssessmentPolicy.key(activeLessonWord!!.es) }) {
                            val target=activeLessonWord!!
                            vocabulary=vocabulary+LearningEvent("vocab:${user.id}:${AssessmentPolicy.key(target.es)}","known_word",AssessmentPolicy.display(target.es),"",target.pl,user.text)
                        }
                        val newEvents=vocabulary.filter { item -> state.value.learning.none { it.id == item.id } }
                        mutable.update { it.copy(learning=it.learning+newEvents) }
                        toolLearningIds.getOrPut(id) { mutableSetOf() }.addAll(newEvents.map { it.id })
                        newEvents.filter { it.kind in setOf("known_word","word") }.forEach { knownVocabulary.add(AssessmentPolicy.key(it.term));knownWordLabels[AssessmentPolicy.key(it.term)]=it.term }
                        state.value.learning.filter { it.kind in setOf("word","repetition") && it.evidence.isNotBlank() }.distinctBy { it.id }
                            .groupBy { AssessmentPolicy.key(it.term) }.filterValues { it.size >= 2 }.keys.forEach { knownVocabulary.add(it) }
                        if(verdict == "ERROR" && settings.autoCorrection && settings.personality.correction > 0 && user != null) {
                            val kind=if(args.str("error_kind") == "PRONUNCIATION") "pronunciation" else "grammar"
                            val eventId="assessment:${user.id}"
                            toolLearningIds.getOrPut(id) { mutableSetOf() }.add(eventId)
                            if(state.value.learning.none { it.id == eventId }) mutable.update { it.copy(learning=it.learning+LearningEvent(eventId,kind,checked.heard.take(150),checked.correction.take(200),checked.explanation.take(300),user.text.take(300))) }
                        }
                    }
                    // The verified verdict alone triggers laughter; it never grades the attempt.
                    if(!readingSupplement) riposteLaughter.error(settings,user?.id,verdict,helpTurn)?.let { audio?.enqueueReference(it) }
                    saved = true
                } else if(fresh && !readingSupplement && (args.str("evidence").isNotBlank() || name == "teach_word")) {
                    when(name) {
                        "record_learning" -> if(!helpTurn && args.str("kind") in setOf("word","known_word","difficult_word","grammar","pronunciation","repetition") && args.str("term").isNotBlank()) {
                            val errorRecord=args.str("kind") in setOf("grammar","pronunciation")
                            val wordRecord=args.str("kind") in setOf("word","known_word","repetition")
                            val grounded=user != null && AssessmentPolicy.speech(user.text).contains(AssessmentPolicy.speech(args.str("evidence")))
                            if((wordRecord && settings.level == com.locospanish.ai.model.Level.A1 && acceptedWordUserId != user?.id) || !grounded || (errorRecord && (!settings.autoCorrection || settings.personality.correction == 0 || this.verdict != "ERROR" || assessedUserId != user?.id)) ||
                                (wordRecord && (AssessmentPolicy.isLearningQuestion(user?.text.orEmpty()) || assessedUserId != user?.id || this.verdict == "UNCERTAIN" || !AssessmentPolicy.contains(user?.text.orEmpty(),args.str("term")) || (this.verdict == "ERROR" && !AssessmentPolicy.contains(assessedCorrection,args.str("term"))))))
                                return@map GeminiProtocol.json("id" to id,"name" to name,"response" to GeminiProtocol.json("saved" to false))
                            val eventId=when { errorRecord -> "assessment:${user?.id}";wordRecord -> "vocab:${user?.id}:${AssessmentPolicy.key(args.str("term"))}";else -> id }
                            if(state.value.learning.none { it.id == eventId }) {
                                val item = LearningEvent(eventId,args.str("kind"),if(wordRecord) AssessmentPolicy.display(args.str("term").take(150)) else args.str("term").take(150),args.str("correction").take(200),args.str("explanation").take(300),args.str("evidence").take(300))
                                mutable.update { it.copy(learning = it.learning + item) }
                                toolLearningIds.getOrPut(id) { mutableSetOf() }.add(eventId)
                                if(wordRecord) { knownVocabulary.add(AssessmentPolicy.key(item.term));knownWordLabels[AssessmentPolicy.key(item.term)]=item.term }
                            }
                            saved = true
                        }
                        "teach_word" -> {
                            val proposed=TeachingForm.complete(LessonWord(args.str("term"),args.str("translation")),args.str("article"))
                            TeachingForm.refine(activeLessonWord,proposed)?.takeIf { it!=activeLessonWord }?.let { setWord(it) }
                            val word=if(helpTurn) activeLessonWord else if(review.active) review.current else if(skipWordRequested) com.locospanish.ai.tutor.LessonTurnPolicy.next(scenario,proposed,knownVocabulary,taughtVocabulary+listOfNotNull(activeLessonWord?.es),requestedLearningTopic.isBlank(),settings.level == Level.A1,preferScenario=settings.level == Level.A1 && requestedLearningTopic.isBlank()) else if(settings.level==Level.A1 && activeLessonWord != null && !(user != null && com.locospanish.ai.tutor.WordAttemptPolicy.newTopic(user.text))) activeLessonWord else if(forceWordReplay && controlTurn) proposed else if(spokenWordTurn == ((user?.id ?: "start") to proposed.es)) proposed else if(AssessmentPolicy.isLearningQuestion(user?.text.orEmpty())) proposed else com.locospanish.ai.tutor.LessonTurnPolicy.next(scenario,proposed,knownVocabulary,taughtVocabulary,requestedLearningTopic.isBlank(),settings.level == Level.A1,preferScenario=requestedLearningTopic.isBlank())
                            teachingWord=word; skipWordRequested=false; if(word != null) setWord(word)
                            if(!helpTurn && !review.active && word != null && word.es.isNotBlank()) saved=queueWord(word.es,user?.id ?: "start",generation,id)
                        }
                        "complete_scenario" -> if(!review.active && settings.level != com.locospanish.ai.model.Level.A1) { completionIds.add(id); mutable.update { it.copy(completed = true) }; saved = true }
                    }
                }
                val response = GeminiProtocol.json("saved" to saved)
                if(name == "teach_word") {
                    teachingWord?.let { response.addProperty("current_word",it.es);response.addProperty("current_translation",it.pl);response.addProperty("lesson_instruction","Ucz WYŁĄCZNIE ${it.es} — ${it.pl}. To słowo widoczne na ekranie. Nie używaj innego słowa z własnych args.") }

                    val currentSpoken=spokenWordTurn?.takeIf { referenceWasPlayed(it.second) }?.second
                    response.addProperty("word_audio_played",currentSpoken != null)
                    response.addProperty("word_audio_pending",saved && currentSpoken == null)
                    response.addProperty("delivery_instruction",if(currentSpoken != null) "W tej lekcji aplikacja wypowiedziała już $currentSpoken. Nie wracaj do starej propozycji z args. Po polsku objaśnij właściwe słowo i daj proste zadanie, bez powtarzania hiszpańskiego audio." else if(teachingWord == null) "Propozycja jest znana lub nie jest pojedynczym słowem. Wybierz INNE pojedyncze słowo z aktualnego tematu i wywołaj teach_word. Nie kończ lekcji i nie czytaj starego słowa ani zdania." else if(saved) "Aplikacja przygotowuje hiszpańskie słowo i odtworzy je po komentarzu. Mów teraz tylko po polsku: świeża docinka zgodna z ustawieniami, polskie znaczenie i jedno łatwe zadanie. Nie czytaj ponownie hiszpańskiego słowa ani zdania. Nie kończ lekcji." else "Wzorzec audio niedostępny, podaj spokojnie pojedyncze słowo z rodzimą hiszpańską wymową i znaczenie po polsku, potem proste zadanie. Nie kończ lekcji.")
                }
                if(fresh && name == "assess_user_turn" && !readingSupplement) {
                    assessment?.let { checked -> AssessmentPolicy.feedback(checked,user != null,settings.autoCorrection && settings.personality.correction > 0).entrySet().forEach { (key,value) -> response.add(key,value) } }
                    response.add("already_known_words",GeminiProtocol.json("words" to knownVocabulary.toList().takeLast(80).map { knownWordLabels[it] ?: it }).get("words"))
                    response.add("already_known_in_this_turn",GeminiProtocol.json("words" to args.get("words")?.takeIf { it.isJsonArray }?.asJsonArray?.mapNotNull { it.takeIf { w -> w.isJsonObject }?.asJsonObject?.str("term") }?.filter { AssessmentPolicy.key(it) in knownVocabulary }.orEmpty()).get("words"))
                    response.addProperty("vocabulary_instruction","To już znane słowa ucznia, także z wcześniejszych rozmów. Nie odpytuj z nich stale i nie zaczynaj nauki od nich. Używaj ich naturalnie jako podstawy nowych sytuacji i nowych słów.")
                    val proposedTopic = args.str("learning_topic").trim().take(120)
                    if(proposedTopic.isNotBlank() && requestedTopicChange) requestedLearningTopic = proposedTopic
                    val proposed = TeachingForm.complete(LessonWord(args.str("next_word"), args.str("next_translation")),args.str("next_article"))
                    // A requested new domain must never fall back to the old scenario's nouns.
                    val holdWord=if(review.active) !advanceWord else learnerQuestion || (settings.level == com.locospanish.ai.model.Level.A1 && activeLessonWord != null && !advanceWord && !requestedTopicChange && !skipWordRequested)
                    val selectedNext = if(review.active && advanceWord) review.advance(true) else if(holdWord) activeLessonWord else if(requestedLearningTopic.isNotBlank() && proposed.es.isBlank() && assessment?.verdict != "ERROR") null else
                        com.locospanish.ai.tutor.LessonTurnPolicy.next(scenario,proposed,knownVocabulary,taughtVocabulary+if(skipWordRequested) listOfNotNull(activeLessonWord?.es) else emptyList(),requestedLearningTopic.isBlank(),settings.level == com.locospanish.ai.model.Level.A1,if(assessment?.verdict == "ERROR") assessment?.correction.orEmpty() else "",preferScenario=requestedLearningTopic.isBlank())
                    val next=selectedNext ?: if(!review.active && !holdWord) bufferedWord() ?:
                        if(requestedLearningTopic.isBlank()) LessonReserve.next(scenario,knownVocabulary+taughtVocabulary) else null else null
                    if(next != null) setWord(next) else if(!holdWord && !review.active) { activeLessonWord=null;requestTask(generation) }
                    if(selectedNext==null && next!=null && generatedWords[taskTopic()].orEmpty().size<3) fetchMaterial(generation)
                    response.addProperty("current_word",next?.es.orEmpty());response.addProperty("current_translation",next?.pl.orEmpty())
                    skipWordRequested=false
                    if(review.active) syncReview()
                    pendingLessonWord = next?.es
                    response.add("recently_taught_words",GeminiProtocol.json("words" to taughtVocabulary.toList().takeLast(80)).get("words"))
                    response.addProperty("allow_next_word",!holdWord)
                    response.addProperty("word_trial_accepted",advanceWord)
                    if(holdWord && assessment?.verdict == "CORRECT") response.addProperty("assessment_instruction","Zdanie lub komentarz może być poprawny, ale nie zaliczono odpowiedzi na ćwiczone słowo. Nie chwal wykonania zadania i nie przechodź dalej.")
                    response.addProperty("lesson_instruction",if(holdWord) com.locospanish.ai.tutor.WordAttemptPolicy.hold(activeLessonWord) else com.locospanish.ai.tutor.LessonTurnPolicy.instruction(next,requestedLearningTopic))
                    val wordPlayed=if(helpTurn || (review.active && assessment?.verdict!="ERROR")) false else if(holdWord && next != null && user != null && (assessment?.verdict == "ERROR" || com.locospanish.ai.tutor.WordAttemptPolicy.wantsReference(user.text) || previousAnswer != null)) queueWord(next.es,user.id,generation,id) else if(holdWord) next?.let { referenceWasPlayed(it.es) || queuedWordTurn?.second==it.es } == true else if(next != null && !settings.previousVersionMode) queueWord(next.es,user?.id ?: "start",generation,id) else false
                    if(epoch != generation || id in cancelledTools) return@map GeminiProtocol.json("id" to id,"name" to name,"response" to GeminiProtocol.json("saved" to false))
                    response.addProperty("word_audio_played",next?.let { referenceWasPlayed(it.es) } == true)
                    response.addProperty("word_audio_pending",wordPlayed && next!=null && !referenceWasPlayed(next.es) && queuedWordTurn?.second==next.es)
                    response.addProperty("pronunciation_instruction",if(next!=null && referenceWasPlayed(next.es)) "Wzorzec ${next.es} został już odtworzony w tej lekcji. Nie czytaj go ponownie i nie żądaj jego mechanicznego powtarzania. Kontynuuj po polsku: riposta, wyjaśnienie i aktualne pytanie." else if(wordPlayed) "Aplikacja obsługuje wzorzec ${next?.es}, który zostanie odtworzony po twoim komentarzu. TERAZ mów WYŁĄCZNIE PO POLSKU: docinka, polskie znaczenie i pytanie o słowo. Nie odczytuj ponownie hiszpańskiego słowa ani zdania, nie dodawaj transliteracji. Na A1 odpowiedź ucznia ma być jednym słowem; na pozostałych poziomach dopasuj do aktualnego wzorca. Nie kończ lekcji." else TopicLessons.pronunciation)
                    val previousRoasts=(recentRoasts.toList()+state.value.transcript.filter { it.role=="assistant" && !it.id.startsWith("word:") }.takeLast(12).map { it.text }).filter { it.isNotBlank() }.distinct().takeLast(16)
                    val anchor=args.str("roast_anchor").trim()
                    val anchored=anchor.length>=3 && listOf(user?.text.orEmpty(),attemptedWord?.es.orEmpty(),attemptedWord?.pl.orEmpty()).any { AssessmentPolicy.contains(it,anchor) }
                    val freshRoast=if(anchored) SpokenRoastPolicy.freshPrimary(settings,SpokenRoastPolicy.primary(settings,plannedRoast,assessment?.verdict ?: "UNCERTAIN",previousRoasts),assessment?.verdict ?: "UNCERTAIN",previousRoasts)
                        else SpokenRoastPolicy.contextualPrimary(settings,plannedRoast,assessment?.verdict ?: "UNCERTAIN",previousRoasts,user?.text.orEmpty(),attemptedWord)
                    val rejectedRoast=plannedRoast.isNotBlank() && freshRoast.isBlank()
                    if(rejectedRoast) { recentRoasts.addLast(plannedRoast.take(320));while(recentRoasts.size>12) recentRoasts.removeFirst() }
                    plannedRoast=if(assessment?.verdict=="ERROR") freshRoast else ""
                    response.addProperty("planned_roast",plannedRoast)
                    response.add("recent_roasts_to_avoid",com.google.gson.Gson().toJsonTree(previousRoasts.takeLast(8).map { it.take(220) }))
                    response.addProperty("roast_needs_new_idea",rejectedRoast)
                    response.addProperty("creative_roast_instruction",SpokenRoastPolicy.freshInstruction(settings,rejectedRoast))
                    response.addProperty("roast_brief",SpokenRoastPolicy.creativeBrief(roastIndex++,previousRoasts))
                    response.add("roast_context",GeminiProtocol.json("learner" to user?.text.orEmpty().take(400),"attempted_word" to attemptedWord?.es.orEmpty(),"attempted_meaning" to attemptedWord?.pl.orEmpty(),"verified_correction" to assessment?.correction.orEmpty(),"next_word" to next?.es.orEmpty(),"next_meaning" to next?.pl.orEmpty(),"verified_verdict" to assessment?.verdict.orEmpty()))
                    response.addProperty("roast_delivery", "Jeśli planned_roast nie jest puste, wypowiedz je dosłownie przed nowym słowem, z szyderczą intonacją. Nie zastępuj go grzeczną pochwałą ani łagodniejszym tekstem. To ta sama odpowiedź audio, bez dodatkowej tury. Przy potwierdzonym błędzie i włączonej korekcie możesz krótko teatralnie podnieść głos w polskiej docince; hiszpańska poprawka pozostaje spokojna. Przy CORRECT żart dotyczy sytuacji, nie jakości wymowy, akcentu lub fikcyjnego błędu. Potem od razu nauka i konkretne zadanie.")
                    if(requestedLearningTopic.isBlank()) response.addProperty("scenario_instruction","Pozostań w temacie ${Scenarios.get(scenario).title}; nowe słowa i pytania dopasuj do niego.")
                    response.addProperty("conversation_context", state.value.transcript.takeLast(4).joinToString("\n") { "${it.role}: ${it.text.take(180)}" })
                    response.addProperty("continue_dialogue", if(holdWord) com.locospanish.ai.tutor.WordAttemptPolicy.hold(activeLessonWord) else "Odpowiedz na ostatnią wypowiedź i posuń sytuację naprzód. Nowe szczegóły i słownictwo; nie wracaj do poprzedniego zadania. Nowa puenta nawiązuje do konkretnej treści; nie powtarzaj żartów z conversation_context.")
                }
                if(name in setOf("assess_user_turn","teach_word")) {
                    response.addProperty("learner_question",if(settings.level!=Level.A1 && !review.active) args.str("next_question").trim().take(350).ifBlank { LessonTurnPolicy.question(activeLessonWord) } else LessonTurnPolicy.question(activeLessonWord))
                    response.addProperty("learner_target_language","es")
                    response.addProperty("language_instruction",com.locospanish.ai.tutor.LessonTurnPolicy.languageInstruction)
                }
                if(review.active) {
                    syncReview()
                    response.addProperty("review_mode",true)
                    response.addProperty("review_completed",review.finished)
                    response.addProperty("lesson_instruction",review.instruction())
                    response.addProperty("continue_dialogue",review.instruction())
                    response.addProperty("pronunciation_instruction",if(assessment?.verdict=="ERROR") "Popraw aktualny błąd i poproś o ponowną próbę TEGO słowa. Nie przechodź do zwykłej lekcji." else "Nie czytaj odpowiedzi po hiszpańsku ani nowego wzorca. Tylko pytanie po polsku o wskazane słowo, bez zdradzania odpowiedzi.")
                    response.addProperty("delivery_instruction",review.instruction())
                    response.addProperty("learner_question",if(review.finished) "" else LessonTurnPolicy.question(review.current))
                }
                if(helpTurn) {
                    val helpUser=user ?: state.value.transcript.lastOrNull { it.role=="user" }?.takeIf { currentWordHelpRequested() }
                    response.add("roast_context",GeminiProtocol.json("learner" to helpUser?.text.orEmpty(),"word" to activeLessonWord?.es.orEmpty(),"meaning" to activeLessonWord?.pl.orEmpty(),"help_request" to true,"verified_verdict" to "UNCERTAIN"))
                    val referenceHelp=activeLessonWord?.takeIf { !review.finished && (LearnerIntent.currentWordReference(helpUser?.text.orEmpty(),it) || controlAction==LessonAction.REPEAT) }
                    val modelHelp=args.str("help_answer").trim().take(700)
                    val helpAnswer=modelHelp.ifBlank { referenceHelp?.let { "Po hiszpańsku: ${it.es}. Po polsku: ${it.pl}." }.orEmpty() }
                    activeLessonWord?.let { target -> mutable.update { it.copy(currentWord=target.es,currentMeaning=target.pl,taskPending=false,taskError="") } }
                    if(helpAnswer.isNotBlank()) mutable.update { it.copy(message=helpAnswer) }
                    if(helpUser!=null && lastHelpOwner!=helpUser.id) { helpAttempts++;lastHelpOwner=helpUser.id }
                    response.addProperty("help_attempt",helpAttempts)
                    response.addProperty("adaptation_instruction","To prośba o pomoc numer $helpAttempts przy tym słowie. Sprawdź historię: nie powtarzaj poprzedniej wskazówki ani żartu. Wyjaśnij inaczej, prościej i konkretnie. Nie żądaj w kółko powtórzenia. Jeśli uczeń chce pominąć, uszanuj SKIP bez zaliczania słowa.")
                    var helpAudio=false
                    if(referenceHelp!=null) {
                        // Pronunciation help is delivered by the validated Spanish reference speaker,
                        // even when the Live model forgets the answer and only makes a joke.
                        helpAudio=queueHelpReference(referenceHelp,helpUser?.id ?: "help",generation)
                    }
                    response.addProperty("allow_next_word",false)
                    response.addProperty("learner_question","")
                    response.addProperty("lesson_instruction","Uczeń pyta albo prosi o wyjaśnienie. Odpowiedz teraz na jego rzeczywistą prośbę: ${user?.text.orEmpty()}. Wyjaśnij jasno po polsku; przykład po hiszpańsku tylko jeśli pomaga odpowiedzieć. NIE żądaj powtórzenia słowa i NIE stawiaj teraz kolejnego zadania. Zachowaj aktualne zadanie w tle do następnej próby. Nie traktuj pytania jako błędu.")
                    response.addProperty("help_answer",helpAnswer)
                    response.addProperty("help_instruction",if(referenceHelp!=null) "Uczeń prosi o pomoc przy ${referenceHelp.es} — ${referenceHelp.pl}. Powiedz jasno po polsku znaczenie i jedną użyteczną wskazówkę. "+(if(helpAudio) "Aplikacja odtwarza hiszpański wzorzec. " else if(referenceWasPlayed(referenceHelp.es)) "Wzorzec został już wypowiedziany; nie czytaj go znowu. " else "Wypowiedz poprawnie hiszpańskie słowo ${referenceHelp.es}, a następnie jego znaczenie. ")+"Nie zastępuj nauki śmiechem. Po pomocy humor wyłącznie zgodny z ustawieniami charakteru, bez udawania błędu." else "Odpowiedz konkretnie na rzeczywiste pytanie: ${user?.text.orEmpty()}. Nie zastępuj odpowiedzi śmiechem lub ripostą. Pytanie o inne słowo ma otrzymać odpowiedź o tamtym słowie, nie automatyczny wzorzec aktualnego zadania.")
                    response.addProperty("word_audio_pending",helpAudio)
                    response.addProperty("word_audio_played",false)
                    response.addProperty("pronunciation_instruction",if(helpAudio) "Aplikacja odtwarza poprawny hiszpański wzorzec aktualnego słowa. Teraz objaśnij znaczenie po polsku, potem świeża krótka docinka. Nie pomijaj odpowiedzi." else "Najpierw odpowiedz na pytanie ucznia, nie odtwarzaj niezwiązanego wzorca.")
                    response.addProperty("continue_dialogue","Odpowiedz na pytanie i czekaj. Bez odpytywania i bez nowego słowa.")
                    response.addProperty("delivery_instruction","Najpierw nauka i odpowiedź na rzeczywiste pytanie, potem docinka. Bez nowego zadania, bez oceniania pytania jak błędu.")
                    response.addProperty("assessment_instruction","To pytanie lub prośba o pomoc, nie błędna odpowiedź. Nie proś o wyraźniejsze powtórzenie zamiast odpowiedzi.")
                }
                if(name in setOf("assess_user_turn","teach_word")) {
                    val spokenVerdict=assessment?.verdict ?: if(user!=null && assessedUserId==user.id) this.verdict else "UNCERTAIN"
                    if(!response.has("roast_context")) response.add("roast_context",GeminiProtocol.json("learner" to user?.text.orEmpty().take(240),"word" to activeLessonWord?.es.orEmpty(),"meaning" to activeLessonWord?.pl.orEmpty(),"verified_verdict" to spokenVerdict))
                    if(!response.has("planned_roast")) response.addProperty("planned_roast",if(spokenVerdict=="ERROR") SpokenRoastPolicy.contextualPrimary(settings,plannedRoast,spokenVerdict,recentRoasts,user?.text.orEmpty(),attemptedWord) else "")
                    if(!response.has("recent_roasts_to_avoid")) response.add("recent_roasts_to_avoid",com.google.gson.Gson().toJsonTree(recentRoasts.toList().takeLast(8).map { it.take(220) }))
                    response.addProperty("delivery_instruction",ReplyDelivery.instruction(settings,response.str("planned_roast"),spokenVerdict,helpTurn,if(review.active) review.instruction() else ""))
                }
                GeminiProtocol.json("id" to id,"name" to name,"response" to response).also { if(fresh) toolResponses[id]=it.deepCopy(); if(advanceWord && user != null) acceptedWordResponses[user.id]=it.deepCopy() }
            }
            if(responses.isNotEmpty()) {
                replyFinalized=false
                approvedSpokenScript="";
                if(deliveringTool) {
                    val draftId=provisionalAssistantId
                    if(draftId != null) mutable.update { state -> state.copy(transcript=state.transcript.map { if(it.id == draftId) it.copy(text="") else it }) }
                    provisionalAudio.clear();provisionalBytes=0;provisionalAssistantId=null;deliveringTool=false
                }
                responses.forEach { item ->
                    if(item.str("name") in setOf("assess_user_turn","teach_word")) {
                        val body=item.getAsJsonObject("response")
                        activeLessonWord?.let { target ->
                            body.addProperty("current_word",target.es);body.addProperty("current_translation",target.pl)
                            body.addProperty("task_revision",lessonRevision)
                            if(body.str("learner_question").isNotBlank()) {
                                if(settings.level==Level.A1 || review.active) body.addProperty("learner_question",LessonTurnPolicy.question(target))
                                body.addProperty("task_contract",if(settings.level==Level.A1 || review.active) LessonSpeechContract.instruction(target) else "Aktualny wzorzec na ekranie: ${target.es} — ${target.pl}. Rozwijaj temat ${taskTopic()} na poziomie ${settings.level.name}. Zadaj learner_question jako naturalny rozmówca, nie narzucaj mechanicznego powtarzania.")
                                val script=SpokenReplyPlan.script(body.str("planned_roast"),target,body.str("verified_verdict"),body.str("assessment_reason"),false,review.finished,body.str("learner_question"))
                                if(script.isNotBlank()) {
                                    body.addProperty("spoken_script",script)
                                    body.addProperty("performance_instruction",ReplyDelivery.performance(settings,body.str("verified_verdict")))
                                    body.addProperty("delivery_instruction",body.str("delivery_instruction")+" spoken_script to sprawdzony szkic odpowiedzi, nie nakaz mechanicznego czytania. Zachowaj planned_roast i końcowe pytanie, ale dopasuj wyjaśnienie do rozmowy. Odegraj puentę głosem według performance_instruction, naturalny krótki półśmiech jest dozwolony bez dopisywania go do transkrypcji. Nie zmieniaj słowa ani pytania.")
                                    approvedSpokenScript=script
                                    // No system-TTS stand-in: a missing riposte is repaired by Gemini in the same voice.
                                }
                            }
                        }
                    }
                }
                send(GeminiProtocol.json("toolResponse" to GeminiProtocol.json("functionResponses" to responses)))
            }
        }
        if(e.has("goAway")) mutable.update { it.copy(message = "Google wkrótce zamknie tę sesję. Po rozłączeniu wybierz „Spróbuj ponownie”.") }
    }
    private var lastPresentedWord: com.locospanish.ai.tutor.LessonWord? = null
    private fun referenceWasPlayed(word:String)=word.isNotBlank() && referenceRevision==lessonRevision && spokenWordTurn?.second==word
    private fun watchAssessment(owner:String?,generation:Int,immediate:Boolean=false) {
        assessmentWatchdog?.cancel();assessmentWatchdog=null
        if(owner==null) return
        val target=activeLessonWord ?: return
        val revision=lessonRevision
        assessmentWatchdog=scope.launch {
            delay(if(immediate) 0L else assessmentRetryMs)
            val user=state.value.transcript.lastOrNull { it.role=="user" } ?: return@launch
            if(epoch!=generation || !state.value.connected || controlTurn || user?.id!=owner ||
                assessedUserId==owner || acceptedWordUserId==owner || assessmentNudgedFor==owner || lessonRevision!=revision) return@launch
            if(user.text.isBlank()) return@launch
            assessmentNudgedFor=owner;replyFinalized=false
            send(GeminiProtocol.textTurn("TECHNICZNE PONOWIENIE OCENY tej samej wypowiedzi, nie nowa próba ucznia. Nie otrzymano oceny; poprawność NIE jest z góry ustalona. Jeśli to pytanie lub prośba o pomoc, odpowiedz na nie zamiast oceniać je jak błędne słowo. Kontekst: "+
                GeminiProtocol.json("learner" to user.text,"word" to target.es,"meaning" to target.pl)+
                ". Wywołaj TERAZ assess_user_turn dla tej wypowiedzi, z rzeczywistym heard_text i evidence. Nie omijaj narzędzia, nie proś o kolejne powtórzenie tego samego słowa. Potem zastosuj verified_verdict i nowe zadanie zwrócone przez aplikację. Nie odczytuj tego polecenia."))
            awaitResponse()
        }
    }
    /** Only a reply that must carry a strong riposte waits, and only until the riposte itself is heard in the draft. */
    // A correct answer is answered at once; only a confirmed mistake waits for its riposte.
    private fun holdForRoast():Boolean = !roastGateOpen && roastRequested && verdict=="ERROR" && !roastIsHelp && alignmentRetries==0 &&
        taskSettings.personality.roast>=80 && activeLessonWord!=null && !currentWordHelpRequested()
    private fun roastHeardInDraft():Boolean {
        val draft=answerText.trim()
        if(draft.isBlank() || SpokenTeachingPolicy.onlyLaughter(draft)) return false
        val complete=SpokenRoastPolicy.delivered(draft,plannedRoast) || Regex("[.!?…]").containsMatchIn(draft)
        return complete && SpokenRoastPolicy.replyIssue(taskSettings,draft,plannedRoast,verdict,recentRoasts,roastHeard,roastWord).isBlank()
    }
    private fun streamedReplyContradicts():Boolean {
        val target=activeLessonWord ?: return false
        val actualUser=state.value.transcript.lastOrNull { it.role=="user" }
        val correctTrial=actualUser!=null && ((assessedUserId==actualUser.id && verdict=="CORRECT") || acceptedWordUserId==actualUser.id)
        val uncertainTrial=actualUser!=null && assessedUserId==actualUser.id && verdict=="UNCERTAIN"
        return (correctTrial && SpokenTeachingPolicy.rejectsCorrectAnswer(answerText)) ||
            ((uncertainTrial || currentWordHelpRequested()) && SpokenTeachingPolicy.assertsError(answerText)) ||
            (validateLessonSpeech() && LessonSpeechContract.mismatch(answerText,target))
    }
    private fun flushProvisionalAudio() {
        if(provisionalAudio.isEmpty()) return
        val joined=ByteArray(provisionalBytes);var offset=0
        provisionalAudio.forEach { part -> part.copyInto(joined,offset);offset+=part.size }
        provisionalAudio.clear();provisionalBytes=0
        if(audio?.enqueue(joined)!=true) fail("Odtwarzanie audio nie nadąża. Spróbuj ponownie.")
        else { openingPromptPending=false;responseTimeout?.cancel() }
    }
    private fun currentWordHelpRequested():Boolean = controlAction !in setOf(LessonAction.NEXT,LessonAction.REVIEW,LessonAction.RESUME) && activeLessonWord?.let {
        LearnerIntent.currentWordReference(state.value.transcript.lastOrNull { entry -> entry.role=="user" }?.text.orEmpty(),it)
    } == true
    private fun queueHelpReference(word:LessonWord,owner:String,generation:Int):Boolean {
        val turn=owner to word.es
        val request=state.value.transcript.lastOrNull { it.role=="user" && it.id==owner }?.text.orEmpty()
        if(referenceWasPlayed(word.es) && !LearnerIntent.explicitReference(request)) return false
        if(failedHelpReferenceTurn==turn) return false
        if(helpReferenceTurn==turn && (queuedWordTurn==turn || spokenWordTurn==turn)) return true
        helpReferenceTurn=turn;forceWordReplay=true;referenceGate.complete(Unit)
        return queueWord(word.es,owner,generation)
    }
    private fun referenceFailed(word:String,owner:String,generation:Int) {
        if(epoch!=generation || !state.value.connected) return
        val target=activeLessonWord?.takeIf { it.es==word } ?: return
        if(helpReferenceTurn==(owner to word) && failedHelpReferenceTurn!=(owner to word)) {
            failedHelpReferenceTurn=owner to word
            command("Wzorzec wymowy aplikacji jest niedostępny. Uczeń prosił o pomoc. TERAZ sam wypowiedz wyraźnie po hiszpańsku ${target.es}, potem po polsku znaczenie ${target.pl}. Użyj naturalnej hiszpańskiej wymowy. Dopiero po odpowiedzi krótka świeża docinka zgodna z ustawieniami. Nie oceniaj pytania i nie kończ na śmiechu.")
        } else if(alignmentRetries<2) mutable.update { it.copy(message="Wzorzec hiszpański jest chwilowo niedostępny. Użyj Powtórz.") }
    }
    private fun validateLessonSpeech():Boolean = !taskSettings.previousVersionMode && activeLessonWord!=null &&
        !dialogueReplyOnly && controlAction !in setOf(LessonAction.EXPLAIN,LessonAction.TRANSLATE) &&
        (controlTurn || !LearnerIntent.questionInsteadOfTrial(state.value.transcript.lastOrNull { it.role=="user" }?.text.orEmpty(),activeLessonWord))
    private fun queueWord(requestedWord: String, turn: String, generation: Int, toolId: String? = null): Boolean {
        val word=activeLessonWord?.es?.takeIf { AssessmentPolicy.key(it)==AssessmentPolicy.key(requestedWord) } ?: requestedWord
        if(!forceWordReplay && referenceWasPlayed(word)) return true
        if(!forceWordReplay && (spokenWordTurn == (turn to word) || queuedWordTurn == (turn to word))) return true
        val speaker=wordSpeaker ?: return false
        if(prefetchWord!=word) { prefetchJob?.cancel();prefetchJob=null;prefetchWord=null }
        wordJob?.cancel();queuedWordTurn=turn to word
        forceWordReplay=false
        wordJob=scope.launch {
            try {
                val pcm=withContext(Dispatchers.IO) { speaker.synthesize(word) }
                referenceGate.await()
                activeLessonWord?.takeIf { it.es==word }?.let { presentedAudio?.awaitPresented(it.es,it.pl) }
                if(epoch != generation || !state.value.connected || pcm.isEmpty() || (toolId != null && toolId in cancelledTools) || activeLessonWord?.es != word) return@launch
                if(audio?.enqueueReference(pcm) == true) {
                    referenceRevision=lessonRevision
                    openingPromptPending=false
                    spokenWordTurn=turn to word;lastPresentedWord=activeLessonWord; activeLessonWord?.let { target -> mutable.update { it.copy(currentWord=target.es,currentMeaning=target.pl) } };taughtVocabulary.add(word)
                    mutable.update { it.copy(transcript=RealtimeEvents.transcript(it.transcript,"word:$turn:$word","assistant",word,false)) }
                    if(wordsOnlyLesson && !review.active && requestedLearningTopic.isBlank()) {
                        val next=LessonTurnPolicy.next(lessonScenario,null,knownVocabulary,taughtVocabulary,wordsOnly=true,preferScenario=true)
                        if(next!=null && next.es!=word) {
                            prefetchJob?.cancel();prefetchWord=next.es
                            prefetchJob=scope.launch(Dispatchers.IO) {
                                try { speaker.prepare(next.es) }
                                catch(cancelled:CancellationException) { throw cancelled }
                                catch(_:Exception) { /* A missed warm-up does not interrupt the current lesson. */ }
                            }
                        }
                    }
                }
            } catch(cancelled:CancellationException) { if(cancelled !is TimeoutCancellationException) throw cancelled else referenceFailed(word,turn,generation) }
            catch(_:Exception) { referenceFailed(word,turn,generation) }
            finally { if(queuedWordTurn == (turn to word)) queuedWordTurn=null }
        }
        return true
    }
    private fun send(value: JsonObject) { if(socket?.send(value.toString()) != true) fail("Nie udało się wysłać wiadomości. Spróbuj ponownie.") }
    private fun addOptionalRoast(settings: Settings, generation: Int) {
        if(answerText.isBlank()) return
        if(readingSupplement) { readingSupplement = false; return }
        val user = if(controlTurn) "" else state.value.transcript.lastOrNull { it.role == "user" }?.text.orEmpty()
        val recent = state.value.transcript.filter { it.role == "assistant" }.takeLast(8).joinToString("\n") { it.text }
        val spokenText = SpokenRoastPolicy.supplement(settings, user, verdict, answerText, plannedRoast, lessonScenario, recent)
        if(spokenText.isBlank()) return
        supplementJob?.cancel()
        supplementJob = scope.launch {
            if(epoch == generation && state.value.connected) {
                readingSupplement = true
                supplementForUserId = state.value.transcript.lastOrNull { it.role == "user" }?.id
                mutable.update { it.copy(message = "Gemini Live • jeden głos polski i hiszpański") }
                send(GeminiProtocol.textTurn(spokenText))
            }
        }
    }
    private fun awaitResponse() {
        responseTimeout?.cancel(); val generation = epoch
        responseTimeout = scope.launch {
            delay(if(activeLessonWord==null && state.value.taskPending) maxOf(responseTimeoutMs,taskTimeoutMs+2000) else responseTimeoutMs)
            if(epoch == generation && state.value.connected) {
                // Use the same bounded reconnect path as a failed transport, preserving the lesson.
                mutable.update { it.copy(message="Brak odpowiedzi głosowej. Ponawiam połączenie…") }
                incoming?.trySend(GeminiProtocol.json("_connection_closed" to 1011,"_reason" to "Brak odpowiedzi głosowej").toString())
            }
        }
    }
    private fun taskTopic()=requestedLearningTopic.ifBlank { Scenarios.get(lessonScenario).title }
    private fun bufferedWord():LessonWord? {
        val excluded=(knownVocabulary+taughtVocabulary).map(AssessmentPolicy::key).toSet()
        val words=generatedWords.getOrPut(taskTopic()) { wordCache?.read(taskTopic(),taskSettings.level,excluded)?.toMutableList() ?: mutableListOf() }
        words.removeAll { AssessmentPolicy.key(it.es) in excluded }
        return if(words.isEmpty()) null else words.removeAt(0)
    }
    private fun requestTask(generation:Int=epoch) {
        bufferedWord()?.let { deliverTask(it,generation);return }
        val reserve=if(requestedLearningTopic.isBlank()) LessonReserve.next(lessonScenario,knownVocabulary+taughtVocabulary) else null
        if(reserve!=null) deliverTask(reserve,generation)
        else mutable.update { it.copy(taskPending=true,taskError="",phase=if(it.connected) VoicePhase.THINKING else it.phase,message="Przygotowuję nowe słowo z tematu: ${taskTopic()}") }
        fetchMaterial(generation)
        if(reserve==null) requestTaskFromLive()
    }
    private fun fetchMaterial(generation:Int) {
        val topic=taskTopic()
        if(taskJob?.isActive==true && taskRequestTopic==topic) return
        if(android.os.SystemClock.elapsedRealtime()<materialRetryAt && taskRequestTopic==topic) return
        taskJob?.cancel();taskJob=null;taskRequestTopic=topic
        val key=taskKey;val excluded=(knownVocabulary+taughtVocabulary+listOfNotNull(activeLessonWord?.es)).toList();val settings=taskSettings
        taskJob=scope.launch {
            try {
                val words=withTimeout(taskTimeoutMs) { withContext(Dispatchers.IO) { wordPlanner(key,topic,settings.level,excluded) } }
                if(epoch!=generation || taskTopic()!=topic || review.active) return@launch
                val blocked=(knownVocabulary+taughtVocabulary+listOfNotNull(activeLessonWord?.es)).map(AssessmentPolicy::key).toSet()
                val valid=words.filter { (requestedLearningTopic.isNotBlank() || TopicLessons.belongsTo(lessonScenario,it)) && it.es.isNotBlank() && it.pl.isNotBlank() && AssessmentPolicy.key(it.es) !in blocked && (settings.level!=Level.A1 || LessonTurnPolicy.singleWord(it.es)) }.distinctBy { AssessmentPolicy.key(it.es) }
                check(valid.isNotEmpty())
                wordCache?.save(topic,settings.level,valid)
                generatedWords[topic]=(generatedWords[topic].orEmpty()+valid).distinctBy { AssessmentPolicy.key(it.es) }.toMutableList()
                taskJob=null;materialRetryAt=0L
                if(activeLessonWord==null) bufferedWord()?.let { deliverTask(it,generation) }
            } catch(error:Exception) {
                if(error is CancellationException && error !is TimeoutCancellationException) throw error
                if(epoch==generation && taskTopic()==topic) {
                    taskJob=null;materialRetryAt=android.os.SystemClock.elapsedRealtime()+20000
                    if(activeLessonWord==null) {
                        mutable.update { it.copy(taskPending=false,phase=if(it.connected) VoicePhase.THINKING else it.phase,taskError="Nie pobrano nowych słów. Rozmowa nie została rozłączona. Możesz ponowić pobieranie.") }
                        requestTaskFromLive()
                    }
                }
            }
        }
    }
    private fun deliverTask(next:LessonWord,generation:Int) {
        setWord(next)
        if(state.value.connected) {
            command("Aktualne zadanie: ${next.es} — ${next.pl}. "+(if(wordSpeaker!=null) "Hiszpański wzorzec odtworzy aplikacja osobno. Mów teraz wyłącznie po polsku: świeża docinka zgodna z ustawieniami, znaczenie i pytanie. " else "Ucz wyłącznie tego hasła i podaj poprawny hiszpański wzorzec. ")+LessonTurnPolicy.question(next),interruptAudio=true)
            if(openingPromptPending) referenceGate.complete(Unit)
            queueWord(next.es,state.value.transcript.lastOrNull { it.role=="user" }?.id ?: "start",generation)
        }
    }
    private fun requestTaskFromLive() {
        if(!state.value.connected || review.active || activeLessonWord!=null || liveTaskRequest==taskTopic()) return
        if(deliveringTool) {
            val generation=epoch
            scope.launch { yield();if(epoch==generation && !deliveringTool) requestTaskFromLive() }
            return
        }
        liveTaskRequest=taskTopic()
        command("Dobierz nowe hasło z tematu: ${taskTopic()}. ZANIM wypowiesz przykład lub pytanie, wywołaj teach_word z term i translation. Aplikacja musi pokazać hiszpański tekst i polskie znaczenie przed ćwiczeniem. Na A1 pojedyncze słowo, nie zdanie. Nie używaj poznanych ani już ćwiczonych słów: "+(knownVocabulary+taughtVocabulary).toList().takeLast(100).joinToString(", "),interruptAudio=true)
    }
    fun retryTask() { if(state.value.connected && state.value.taskError.isNotBlank()) { liveTaskRequest="";materialRetryAt=0L;requestTask() } }
    private fun setWord(proposed:LessonWord?) {
        val word=proposed?.let { TeachingForm.complete(it) }
        if(word==null && !review.active) { activeLessonWord=null;requestTask();return }
        liveTaskRequest=""
        if(activeLessonWord!=word) { lessonRevision++;alignmentRetries=0;helpAttempts=0;lastHelpOwner=null }
        activeLessonWord=word
        presentedAudio?.task(word?.es.orEmpty(),word?.pl.orEmpty())
        if(review.finished) presentedAudio?.allowReplyWithoutTask()
        mutable.update { it.copy(currentWord=word?.es.orEmpty(),currentMeaning=word?.pl.orEmpty(),taskPending=false,taskError="") }
    }
    private fun syncReview() {
        mutable.update { it.copy(reviewMode=review.active,reviewCompleted=review.finished,reviewIndex=review.index,reviewTotal=review.total,reviewCorrect=review.correct) }
    }
    fun action(action:LessonAction) {
        if(!state.value.connected) return
        when(action) {
            LessonAction.REVIEW -> {
                if(review.active) return
                val learned=state.value.learning.filter { it.kind in setOf("known_word","word","repetition") }.mapNotNull { e ->
                    val meaning=reviewBank.firstOrNull { AssessmentPolicy.key(it.es)==AssessmentPolicy.key(e.term) }?.pl ?: e.explanation.takeIf { it.isNotBlank() && it.length<=100 }
                    meaning?.let { LessonWord(e.term,it) }
                }
                val known=knownVocabulary.mapNotNull(WordKnowledge::lookup)
                val taught=taughtVocabulary.mapNotNull(WordKnowledge::lookup)
                if(!review.start(reviewBank+learned+known+taught,activeLessonWord?.es)) {
                    mutable.update { it.copy(message="Nie ma jeszcze zapisanych słów do odpytywania. Najpierw przećwicz słowo w lekcji.") };return
                }
                savedLessonWord=activeLessonWord;setWord(review.current);lastPresentedWord=null;syncReview()
            }
            LessonAction.RESUME -> {
                if(!review.active) return
                review.stop();syncReview()
                setWord(savedLessonWord?.takeUnless { AssessmentPolicy.key(it.es) in knownVocabulary } ?: LessonTurnPolicy.next(lessonScenario,null,knownVocabulary,taughtVocabulary,wordsOnly=true,preferScenario=requestedLearningTopic.isBlank()))
                savedLessonWord=null
            }
            LessonAction.NEXT -> {
                if(review.active) { if(review.finished) return;setWord(review.advance(false));syncReview() }
                else { activeLessonWord?.let { taughtVocabulary.add(it.es) };setWord(LessonTurnPolicy.next(lessonScenario,null,knownVocabulary,taughtVocabulary,wordsOnly=true,preferScenario=requestedLearningTopic.isBlank())) }
            }
            LessonAction.SLOWER -> { sessionSpeed=maxOf(.6f,minOf(sessionSpeed,1f)-.25f);audio?.setSpeed(sessionSpeed) }
            else -> Unit
        }
        val target=activeLessonWord
        val request=when(action) {
            LessonAction.REVIEW -> review.instruction()
            LessonAction.RESUME,LessonAction.NEXT -> if(review.active) review.instruction() else LessonTurnPolicy.instruction(target,requestedLearningTopic)
            LessonAction.REPEAT -> if(review.active) "Powtórz tylko pytanie odpytywania, bez odpowiedzi. ${review.instruction()}" else "Powtórz objaśnienie aktualnego słowa i pytanie. Nie wybieraj nowego słowa. ${LessonTurnPolicy.question(target)}"
            LessonAction.SLOWER -> if(review.active) "Mów wolniej. Powtórz samo pytanie, bez odpowiedzi. ${review.instruction()}" else "Powtórz wolniej objaśnienie aktualnego słowa. Nie zmieniaj zadania. ${LessonTurnPolicy.question(target)}"
            LessonAction.EXPLAIN -> "Wyjaśnij krótko po polsku sens aktualnego słowa i co mam zrobić. Nie każ mi teraz powtarzać i nie dodawaj nowego zadania."
            LessonAction.TRANSLATE -> "Odpowiedz po polsku: wyjaśnij znaczenie ostatniej wypowiedzi i aktualnego słowa. Nie przechodź dalej i nie wymagaj powtarzania."
        }
        command(request)
        skipWordRequested=false;controlAction=action
        if(!review.active && target!=null && action in setOf(LessonAction.REPEAT,LessonAction.SLOWER,LessonAction.RESUME,LessonAction.NEXT)) {
            forceWordReplay=true;queueWord(target.es,"start",epoch)
        }
    }
    fun command(text: String, interruptAudio: Boolean = true) { if(state.value.connected) { assessmentWatchdog?.cancel();assessmentWatchdog=null;roastRequested=false;replyFinalized=false;approvedSpokenScript="";streamedBytes=0;streamDeviation=false;streamHalted=false;roastGateOpen=false; skipWordRequested=Regex("kontynuować lekcję|następne słowo|nowe słowo|^dalej$",RegexOption.IGNORE_CASE).containsMatchIn(text); if(interruptAudio) { wordJob?.cancel();queuedWordTurn=null;referenceGate=CompletableDeferred() }; forceWordReplay = interruptAudio && Regex("^(?:powtórz|powtorz|wolniej|wymow|pronuncia|repite)\\b",RegexOption.IGNORE_CASE).containsMatchIn(text); controlTurn = true; awaitResponse(); supplementJob?.cancel(); if(interruptAudio) audio?.interrupt(); send(GeminiProtocol.textTurn(text)); mutable.update { it.copy(phase = VoicePhase.THINKING) } } }
    fun wordPresented(word:String,meaning:String) {
        displayedTask=if(word.isBlank() || meaning.isBlank()) null else word to meaning
        if(displayedTask==null) presentedAudio?.hidden() else presentedAudio?.presented(word,meaning)
    }
    private fun prepareNativeReply(script:String,owner:String?,generation:Int) {
        nativeReply?.cancel();nativeReply=null
        nativeReplyOwner=owner;nativeReplyRevision=lessonRevision;nativeReplyScript=script
        if(owner==null || script.isBlank()) return
        nativeReply=scope.async {
            try {
                withTimeoutOrNull(1800) {
                    val speaker=roastSpeech ?: supplementFactory(appContext).also { roastSpeech=it }
                    withContext(Dispatchers.IO) { speaker.synthesize(script) }
                }?.takeIf { epoch==generation }
            } catch(cancelled:CancellationException) { if(cancelled !is TimeoutCancellationException) throw cancelled else null }
            catch(_:Exception) { null }
        }
    }
    fun mute() {
        val muted = !state.value.muted; audio?.mute(muted); mutable.update { it.copy(muted = muted) }
        if(muted && state.value.connected) send(GeminiProtocol.json("realtimeInput" to GeminiProtocol.json("audioStreamEnd" to true)))
    }
    private fun fail(message: String) { close(); mutable.update { it.copy(phase = VoicePhase.ERROR,connected = false,taskPending=false,message = message) } }
    private fun close() { nativeReply?.cancel();nativeReply=null;nativeReplyOwner=null;nativeReplyScript="";riposteLaughter.reset();liveTaskRequest="";materialRetryAt=0L;assessmentWatchdog?.cancel();assessmentWatchdog=null;assessmentNudgedFor=null;referenceRevision=-1L;helpAttempts=0;lastHelpOwner=null;dialogueReplyOnly=false;roastRequested=false;roastWord=null;roastHeard="";openingPromptPending=true;replyFinalized=false;approvedSpokenScript="";streamedBytes=0;streamDeviation=false;streamHalted=false;roastGateOpen=false; helpReferenceJob?.cancel();helpReferenceJob=null;helpReferenceTurn=null;failedHelpReferenceTurn=null;taskJob?.cancel();taskJob=null;taskKey="";prefetchJob?.cancel();prefetchJob=null;prefetchWord=null;incomingJob?.cancel();incomingJob=null;incoming?.close();incoming=null;mutableAudioLevel.value=0f; epoch++; wordJob?.cancel();wordJob=null;queuedWordTurn=null;referenceGate.cancel();referenceGate=CompletableDeferred();referenceOwner=null; deliveringTool=false; forceWordReplay=false; wordSpeaker?.close(); wordSpeaker=null; spokenWordTurn=null; activeLessonWord=null; skipWordRequested=false; lastPresentedWord=null; acceptedWordUserId=null;lastAcceptedWord=null; acceptedWordResponses.clear(); provisionalAudio.clear(); provisionalBytes = 0; provisionalAssistantId = null; supplementJob?.cancel(); supplementJob = null; roastSpeech?.let { runCatching { it.close() } }; roastSpeech = null; readingSupplement = false; answerText = ""; plannedRoast = ""; verdict = "CORRECT"; timeout?.cancel(); timeout = null; responseTimeout?.cancel(); responseTimeout = null; socket?.cancel(); socket = null; runCatching { audio?.close() }; audio = null;presentedAudio=null }
    fun stop() { close(); mutable.update { it.copy(phase = VoicePhase.IDLE,connected = false,muted = false,message = "Rozmowa zakończona.") } }
}







