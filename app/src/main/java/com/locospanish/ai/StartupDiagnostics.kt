package com.locospanish.ai
import android.content.Context
object StartupDiagnostics {
 @Volatile private var installed=false
 @Synchronized fun install(context:Context) {
  if(installed) return
  installed=true
  val app=context.applicationContext
  val previous=Thread.getDefaultUncaughtExceptionHandler()
  Thread.setDefaultUncaughtExceptionHandler { thread,error ->
   runCatching {
    val cause=generateSequence(error) { it.cause }.take(12).last()
    val frames=cause.stackTrace.filter { it.className.startsWith("com.locospanish.ai") }.take(5).joinToString("\n") { "${it.className.substringAfterLast('.')}.${it.methodName}:${it.lineNumber}" }
    java.io.File(app.filesDir,"last-startup-error.txt").writeText("${cause.javaClass.simpleName}\n$frames")
   }
   previous?.uncaughtException(thread,error)
  }
 }
 fun previous(context:Context):String=runCatching {
  val file=java.io.File(context.filesDir,"last-startup-error.txt")
  if(!file.exists()) "" else file.readText().take(1000)
 }.getOrDefault("")
}
