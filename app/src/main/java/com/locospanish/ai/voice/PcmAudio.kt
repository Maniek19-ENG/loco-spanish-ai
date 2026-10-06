package com.locospanish.ai.voice

import android.annotation.SuppressLint
import android.content.Context
import android.media.*
import android.media.audiofx.AcousticEchoCanceler
import android.media.audiofx.NoiseSuppressor
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import java.util.concurrent.atomic.AtomicInteger

interface LiveAudio {
    fun start(onInput: (ByteArray) -> Unit, onError: () -> Unit, onPlaying: (Boolean) -> Unit)
    fun enqueue(bytes: ByteArray): Boolean
    fun interrupt()
    fun mute(value: Boolean)
    fun close()
}

@OptIn(ExperimentalCoroutinesApi::class)
class PcmAudio(context: Context, private val scope: CoroutineScope) : LiveAudio {
    private val route = AudioRoute(context) { failure?.invoke() }
    private var failure: (() -> Unit)? = null
    private var recorder: AudioRecord? = null
    private var player: AudioTrack? = null
    private var echo: AcousticEchoCanceler? = null
    private var noise: NoiseSuppressor? = null
    private var inputJob: Job? = null
    private var outputJob: Job? = null
    private val epoch = AtomicInteger(0)
    private val output = Channel<Pair<Int,ByteArray>>(120)
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
                    if(n > 0 && !muted && !closed) onInput(buffer.copyOf(n))
                }
            } catch(e: Exception) { if(!closed && e !is CancellationException) onError() }
        }
        outputJob = scope.launch(Dispatchers.IO) {
            try {
                var writtenFrames = 0L
                var previousEpoch = epoch.get()
                for((generation,bytes) in output) {
                    if(generation != epoch.get()) continue
                    if(previousEpoch != generation) { writtenFrames = 0; previousEpoch = generation }
                    onPlaying(true)
                    var offset = 0
                    while(offset < bytes.size && isActive && !closed && generation == epoch.get()) {
                        val n = synchronized(lock) {
                            if(closed || generation != epoch.get()) 0 else track.write(bytes,offset,bytes.size-offset,AudioTrack.WRITE_NON_BLOCKING)
                        }
                        if(n < 0) error("playback")
                        offset += n; writtenFrames += n/2
                        if(n == 0) delay(5)
                    }
                    // Keep the speaking indicator tied to playback rather than server generation.
                    while(isActive && !closed && generation == epoch.get() && output.isEmpty &&
                        (track.playbackHeadPosition.toLong() and 0xffffffffL) < writtenFrames) delay(10)
                    if(output.isEmpty && generation == epoch.get()) onPlaying(false)
                }
            } catch(e: Exception) { if(!closed && e !is CancellationException) onError() }
        }
    }
    override fun enqueue(bytes: ByteArray) = !closed && bytes.size <= 192000 && output.trySend(epoch.get() to bytes).isSuccess
    override fun interrupt() {
        synchronized(lock) {
            epoch.incrementAndGet()
            while(output.tryReceive().isSuccess) { }
            player?.let { runCatching { it.pause(); it.flush(); if(!closed) it.play() } }
        }
    }
    override fun mute(value: Boolean) { muted = value }
    override fun close() {
        if(closed) return
        closed = true; failure = null; inputJob?.cancel(); outputJob?.cancel(); output.close()
        runCatching { recorder?.stop() }; interrupt(); route.release()
        val record = recorder; val track = player; val aec = echo; val ns = noise
        val capture = inputJob; val playback = outputJob
        // Cleanup owns its short-lived scope so ViewModel cancellation cannot skip release.
        CoroutineScope(Dispatchers.IO).launch {
            capture?.join(); playback?.join()
            runCatching { aec?.release() }; runCatching { ns?.release() }
            runCatching { record?.release() }; runCatching { track?.release() }
        }
    }
}
