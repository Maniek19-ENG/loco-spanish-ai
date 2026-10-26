package com.locospanish.ai.ui
import com.locospanish.ai.model.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

/** Shared by the complete conversation screen and its integration regression. */
@OptIn(FlowPreview::class)
internal class LiveDisplay(source:StateFlow<VoiceState>,scope:CoroutineScope) {
 val lesson=source.map { LessonDisplay.from(it) }.distinctUntilChanged().stateIn(scope,SharingStarted.Eagerly,LessonDisplay.from(source.value))
 val controls=source.map { value ->
  val owner=value.transcript.lastOrNull { it.role=="user" }?.id
  value.copy(transcript=emptyList(),learning=value.learning.filter { it.id=="assessment:$owner" && it.kind in setOf("grammar","pronunciation") }.takeLast(1))
 }.distinctUntilChanged().stateIn(scope,SharingStarted.WhileSubscribed(5000),VoiceState())
 val transcript=source.map { it.transcript.takeLast(12) }.distinctUntilChanged().sample(120).stateIn(scope,SharingStarted.WhileSubscribed(5000),source.value.transcript.takeLast(12))
}
