package com.locospanish.ai.ui

import android.content.Intent
import android.provider.Settings
import androidx.core.net.toUri
import androidx.core.content.FileProvider
import androidx.compose.runtime.*
import androidx.compose.material3.*
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.*
import java.io.File
import java.security.MessageDigest

const val ROLLBACK_24_SHA256="defef441b9d697aa84c6dc5bb2b7f1ad816cb735a689ca57ad8d264620c1a1e5"
const val ROLLBACK_8_SHA256="c83d8f621e97a4a2983ed5787895944dc07c3c8342beb858293e424de99d356c"
const val ROLLBACK_23_SHA256="5cc922071993fe6914e55a7c5627d6e3408eb554b6e87cd5bc240fd6ea12a4d7"
@Composable internal fun RollbackCard() {
    RollbackVersionCard("2.3.24",ROLLBACK_24_SHA256)
    RollbackVersionCard("2.3.23",ROLLBACK_23_SHA256)
    RollbackVersionCard("2.3.8",ROLLBACK_8_SHA256)
}
@Composable private fun RollbackVersionCard(version:String,expectedHash:String) {
    val context=LocalContext.current
    val scope=rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf("") }
    Text(if(version=="2.3.24") "Poprzednia aktualizacja: $version" else "Wersja awaryjna: $version", style=MaterialTheme.typography.titleMedium)
    Text("Zachowana w aplikacji. PowrÃ³t nie wymaga usuwania LOCO ani czyszczenia postÄ™pÃ³w.")
    OutlinedButton(enabled=!busy,onClick={
        if(!context.packageManager.canRequestPackageInstalls()) {
            message="ZezwÃ³l LOCO na instalowanie aplikacji, wrÃ³Ä‡ tutaj i ponownie wybierz powrÃ³t."
            runCatching { context.startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, "package:${context.packageName}".toUri())) }
                .onFailure { message="OtwÃ³rz ustawienia telefonu i zezwÃ³l LOCO na instalowanie aplikacji." }
        } else scope.launch {
            busy=true;message="PrzygotowujÄ™ wersjÄ™ ${version}â€¦"
            try {
                val apk=withContext(Dispatchers.IO) {
                    val dir=File(context.cacheDir,"rollback").apply { mkdirs() }
                    val file=File(dir,"LOCO-${version}.apk")
                    rollbackInput(context,version).use { input -> file.outputStream().use { input.copyTo(it) } }
                    val digest=MessageDigest.getInstance("SHA-256").digest(file.readBytes()).joinToString("") { "%02x".format(it) }
                    check(digest==expectedHash)
                    file
                }
                val uri=FileProvider.getUriForFile(context,"${context.packageName}.rollback",apk)
                context.startActivity(Intent(Intent.ACTION_VIEW).setDataAndType(uri,"application/vnd.android.package-archive").addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION))
                message="PotwierdÅº instalacjÄ™ ${version} w oknie Androida. Nie odinstalowuj LOCO."
            } catch(_:Exception) { message="Nie udaÅ‚o siÄ™ uruchomiÄ‡ instalatora. UÅ¼yj zachowanego pliku LOCO-Spanish-AI-${version}-POWROT.apk." }
            finally { busy=false }
        }
    }) { Text(if(busy) "PrzygotowujÄ™â€¦" else "WRÃ“Ä† DO WERSJI ${version}") }
    if(message.isNotBlank()) Text(message)
}

/** Older installers remain byte-for-byte inside the original 2.3.24 APK. */
internal fun rollbackInput(context:android.content.Context,version:String):java.io.InputStream {
    require(version in setOf("2.3.24","2.3.23","2.3.8"))
    if(version=="2.3.24") return context.assets.open("loco-2.3.24.apk")
    val zip=java.util.zip.ZipInputStream(context.assets.open("loco-2.3.24.apk"))
    try {
        while(true) {
            val entry=zip.nextEntry ?: error("Missing preserved installer")
            if(entry.name=="assets/loco-$version.apk") return zip
        }
    } catch(error:Throwable) { zip.close();throw error }
}
