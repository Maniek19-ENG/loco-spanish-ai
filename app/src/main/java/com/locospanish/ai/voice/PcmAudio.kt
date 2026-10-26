package com.locospanish.ai.voice

import android.annotation.SuppressLint
import android.content.Context
import android.media.*
import android.media.audiofx.AcousticEchoCanceler
import android.media.audiofx.NoiseSuppressor
import com.locospanish.ai.model.AudioRoutePreference
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import java.util.concurrent.atomic.AtomicInteger

interface LiveAudio {
    val routeLabel: String get() = "audio telefonu"
    fun start(onInput: (ByteArray) -> Unit, onError: () -> Unit, onPlaying: (Boolean) -> Unit)
    fun setSpeed(speed:Float) {}
    fun observeLevel(listener:(Float)->Unit) {}
    fun enqueue(bytes: ByteArray): Boolean
    fun enqueueReference(bytes:ByteArray):Boolean = enqueue(bytes)
    fun interrupt()
    fun mute(value: Boolean)
    fun close()
}

@OptIn(ExperimentalCoroutinesApi::class)
class PcmAudio(context: Context, private val scope: CoroutineScope, preference: AudioRoutePreference = AudioRoutePreference.AUTO) : LiveAudio {
    private val route = AudioRoute(context, preference) { failure?.invoke() }
    override val routeLabel: String get() = route.label
    private var failure: (() -> Unit)? = null
    private var recorder: AudioRecord? = null
    private var player: AudioTrack? = null
    private var echo: AcousticEchoCanceler? = null
    private var noise: NoiseSuppressor? = null
    private var inputJob: Job? = null
    private var outputJob: Job? = null
    private var meterJob: Job? = null
    @Volatile private var micLevel=0f
    @Volatile private var playing=false
    @Volatile private var levelObserver: ((Float)->Unit)? = null
    private data class MeterWindow(val generation:Int,val start:Long,val end:Long,val level:Float)
    private val windows=java.util.concurrent.ConcurrentLinkedQueue<MeterWindow>()
    override fun observeLevel(listener:(Float)->Unit) { levelObserver=listener }

    private val epoch = AtomicInteger(0)
    private val output = PcmOutputQueue()
    @Volatile private var referencePlaying = false
    private val lock = Any()
    @Volatile private var muted = false
    @Volatile private var closed = false
    @SuppressLint("MissingPermission")
    override fun start(onInput: (ByteArray) -> Unit, onError: () -> Unit, onPlaying: (Boolean) -> Unit) {
        failure = onError; route.acquire()
        val recordMin = AudioRecord.getMinBufferSize(16000, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        check(recordMin > 0)
        val record = AudioRecord(MediaRecorder.AudioSource.VOICE_COMMUNICATION,16000,AudioFormat.CHANNEL_IN_MONO,AudioFormat.ENCODING_PCM_16BIT,maxOf(recordMin,6400))
        recorder = record; check(record.state == AudioRecord.STATE_INITIALIZED)
        if(AcousticEchoCanceler.isAvailable()) echo = AcousticEchoCanceler.create(record.audioSessionId)?.apply { enabled = true }
        if(NoiseSuppressor.isAvailable()) noise = NoiseSuppressor.create(record.audioSessionId)?.apply { enabled = true }
        val format = AudioFormat.Builder().setSampleRate(24000).setEncoding(AudioFormat.ENCODING_PCM_16BIT).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build()
        val track = AudioTrack.Builder().setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION).setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build())
            .setAudioFormat(format).setTransferMode(AudioTrack.MODE_STREAM).setBufferSizeInBytes(maxOf(9600, AudioTrack.getMinBufferSize(24000,AudioFormat.CHANNEL_OUT_MONO,AudioFormat.ENCODING_PCM_16BIT))).build()
        player = track; check(track.state == AudioTrack.STATE_INITIALIZED)
        track.play(); record.startRecording()
        inputJob = scope.launch(Dispatchers.IO) {
            try {
                val buffer = ByteArray(3200)
                while(isActive && !closed) {
                    val n = record.read(buffer,0,buffer.size)
                    if(n < 0) error("record")
                    if(n > 0) { micLevel=if(muted || referencePlaying) 0f else PcmLevel.measure(buffer,0,n);if(!muted && !referencePlaying && !closed) onInput(buffer.copyOf(n)) }
                }
            } catch(e: Exception) { if(!closed && e !is CancellationException) onError() }
        }
        outputJob = scope.launch(Dispatchers.IO) {
            try {
                var writtenFrames = 0L
                var previousEpoch = epoch.get()
                for(chunk in output.channel) {
                    val (generation,bytes,reference)=chunk
                    try {
                    if(generation != epoch.get()) continue
                    if(previousEpoch != generation) { writtenFrames = 0; previousEpoch = generation }
                    if(reference) referencePlaying=true
                    playing=true;onPlaying(true)
                    var offset = 0
                    while(offset < bytes.size && isActive && !closed && generation == epoch.get()) {
                        val n = synchronized(lock) {
                            if(closed || generation != epoch.get()) 0 else track.write(bytes,offset,minOf(960,bytes.size-offset),AudioTrack.WRITE_NON_BLOCKING)
                        }
                        if(n < 0) error("playback")
                        if(n>0) windows.add(MeterWindow(generation,writtenFrames,writtenFrames+n/2,PcmLevel.measure(bytes,offset,n)))
                        offset += n; writtenFrames += n/2
                        if(n == 0) delay(5)
                    }
                    // Keep the speaking indicator tied to playback rather than server generation.
                    while(isActive && !closed && generation == epoch.get() && (reference || output.isEmpty) &&
                        (track.playbackHeadPosition.toLong() and 0xffffffffL) < writtenFrames) delay(10)
                    if(reference) { if(generation == epoch.get()) delay(160);referencePlaying=false }
                    if(output.isEmpty && generation == epoch.get()) { playing=false;onPlaying(false) }
                    } finally { output.release(chunk) }
                }
            } catch(e: Exception) { if(!closed && e !is CancellationException) onError() }
        }
        meterJob=scope.launch(Dispatchers.IO) {
            while(isActive && !closed) {
                val head=track.playbackHeadPosition.toLong() and 0xffffffffL
                while(windows.peek()?.let { it.generation!=epoch.get() || it.end<=head } == true) windows.poll()
                val voice=windows.peek()?.takeIf { it.generation==epoch.get() && head>=it.start && head<it.end }?.level ?: 0f
                levelObserver?.invoke(if(playing) voice else if(muted) 0f else micLevel)
                delay(40)
            }
        }
    }
    override fun enqueue(bytes: ByteArray) = !closed && output.offer(epoch.get(),bytes,false)
    override fun enqueueReference(bytes:ByteArray) = !closed && output.offer(epoch.get(),bytes,true)
    internal fun pendingAudioBytes()=output.pendingBytes
    override fun interrupt() {
        referencePlaying=false;playing=false;micLevel=0f;windows.clear();levelObserver?.invoke(0f)
        synchronized(lock) {
            epoch.incrementAndGet()
            output.drain()
            player?.let { runCatching { it.pause(); it.flush(); if(!closed) it.play() } }
        }
    }
    override fun setSpeed(speed:Float) { synchronized(lock) { runCatching { player?.let { it.playbackParams=PlaybackParams().setSpeed(speed.coerceIn(.6f,1.2f)).setPitch(1f) } } } }
    override fun mute(value: Boolean) { muted = value }
    override fun close() {
        if(closed) return
        closed = true; failure = null; meterJob?.cancel();levelObserver?.invoke(0f);levelObserver=null; inputJob?.cancel(); outputJob?.cancel(); output.close()
        runCatching { recorder?.stop() }; interrupt(); runCatching { route.release() }
        val record = recorder; val track = player; val aec = echo; val ns = noise
        val capture = inputJob; val playback = outputJob; val meter=meterJob
        // Cleanup owns its short-lived scope so ViewModel cancellation cannot skip release.
        CoroutineScope(Dispatchers.IO).launch {
            capture?.join(); playback?.join(); meter?.join()
            runCatching { aec?.release() }; runCatching { ns?.release() }
            runCatching { record?.release() }; runCatching { track?.release() }
        }
    }
}
