package com.obinot.app.utils

import android.content.Context
import android.content.Intent
import android.os.Process
import android.util.Log
import com.obinot.app.CrashActivity
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.system.exitProcess

/**
 * Captura crashes no manejados, guarda el stack trace a disco, y lanza
 * [CrashActivity] en un proceso nuevo para mostrarlo al usuario.
 *
 * El flujo es:
 *   1. Ocurre un crash no manejado en cualquier thread.
 *   2. Este handler escribe el stack trace a files/crashes/crash_*.txt
 *   3. Lanza un Intent con FLAG_ACTIVITY_NEW_TASK | FLAG_ACTIVITY_CLEAR_TASK
 *      apuntando a CrashActivity.
 *   4. Mata el proceso actual con Process.killProcess().
 *   5. Android reinicia el proceso para mostrar CrashActivity, que lee el
 *      archivo recién escrito y lo muestra.
 *
 * El punto clave del diseño: NO mostramos la pantalla dentro del proceso
 * crasheado (sería inestable). Lanzamos la activity, matamos el proceso, y
 * dejamos que Android arranque una instancia limpia.
 */
class CrashHandler(private val context: Context) : Thread.UncaughtExceptionHandler {

    companion object {
        private const val TAG = "CrashHandler"
        private const val CRASH_DIR = "crashes"
        const val EXTRA_CRASH_FILE = "crash_file_path"
    }

    /**
     * Instala el handler como default. Se llama desde Application.onCreate().
     */
    fun install() {
        Thread.setDefaultUncaughtExceptionHandler(this)
    }

    override fun uncaughtException(thread: Thread, throwable: Throwable) {
        try {
            val crashFile = saveCrashToDisk(thread, throwable)
            launchCrashActivity(crashFile)
        } catch (e: Throwable) {
            // Si incluso el handler falla, al menos logueamos y morimos.
            Log.e(TAG, "Error handling crash", e)
        } finally {
            // Matar el proceso para que Android arranque CrashActivity limpio.
            Process.killProcess(Process.myPid())
            exitProcess(10)
        }
    }

    private fun saveCrashToDisk(thread: Thread, throwable: Throwable): File {
        val crashDir = File(context.filesDir, CRASH_DIR).apply { mkdirs() }

        // Limitar a los últimos 20 archivos para no llenar el storage.
        crashDir.listFiles()?.sortedByDescending { it.lastModified() }?.drop(19)?.forEach { it.delete() }

        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val crashFile = File(crashDir, "crash_$timestamp.txt")

        val sb = StringBuilder()
        sb.append("Obinot crash report\n")
        sb.append("Timestamp: ${Date()}\n")
        sb.append("Thread: ${thread.name}\n")
        sb.append("App version: ${getAppVersion()}\n")
        sb.append("Android: ${android.os.Build.VERSION.RELEASE} (SDK ${android.os.Build.VERSION.SDK_INT})\n")
        sb.append("Device: ${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}\n")
        sb.append("\n--- STACK TRACE ---\n\n")
        sb.append(Log.getStackTraceString(throwable))

        crashFile.writeText(sb.toString())
        return crashFile
    }

    private fun getAppVersion(): String {
        return try {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "unknown"
        } catch (e: Exception) {
            "unknown"
        }
    }

    private fun launchCrashActivity(crashFile: File) {
        val intent = Intent(context, CrashActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            putExtra(EXTRA_CRASH_FILE, crashFile.absolutePath)
        }
        context.startActivity(intent)
    }
}