package teikengstudio.xmpro.player

import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.BatteryManager
import android.os.PowerManager
import android.os.Process
import android.os.SystemClock
import android.util.Log
import java.io.File
import java.io.FileOutputStream
import java.util.Locale

/** long: 锁屏切集需要跨播放器服务和应用队列对时，日志只保留状态、耗时与系统省电概况。 */
internal object PlaybackDiagnostics {
    const val TAG = "XMProPlayback"

    private const val LOG_NAME = "playback.log"
    private const val PREVIOUS_LOG_NAME = "playback.previous.log"
    private const val MAX_LOG_BYTES = 512 * 1024L

    @Synchronized
    fun record(context: Context, event: String, vararg fields: Pair<String, Any?>) {
        val appContext = context.applicationContext
        val line = buildString {
            append("wall_ms=").append(System.currentTimeMillis())
            append(" elapsed_ms=").append(SystemClock.elapsedRealtime())
            append(" pid=").append(Process.myPid())
            append(" event=").append(token(event))
            append(" doze=").append(deviceIdle(appContext))
            append(" interactive=").append(interactive(appContext))
            append(" power_save=").append(powerSave(appContext))
            append(" battery_exempt=").append(batteryExempt(appContext))
            append(" plugged=").append(plugged(appContext))
            append(" network=").append(network(appContext))
            fields.forEach { (key, value) ->
                append(' ').append(token(key)).append('=').append(token(value))
            }
        }

        Log.i(TAG, line)
        runCatching { appendToFile(appContext, "$line\n") }
            .onFailure { Log.w(TAG, "diagnostic_file_write_failed=${it.javaClass.simpleName}") }
    }

    fun playerState(state: Int): String = when (state) {
        androidx.media3.common.Player.STATE_IDLE -> "idle"
        androidx.media3.common.Player.STATE_BUFFERING -> "buffering"
        androidx.media3.common.Player.STATE_READY -> "ready"
        androidx.media3.common.Player.STATE_ENDED -> "ended"
        else -> "unknown"
    }

    /** long: 只记录异常类型链，避免网络错误消息把播放地址或服务端内容带入诊断文件。 */
    fun causeTypes(error: Throwable?): String? = generateSequence(error) { it.cause }
        .take(6)
        .map { it.javaClass.simpleName.ifBlank { "Throwable" } }
        .joinToString(">")
        .takeIf { it.isNotEmpty() }

    fun httpStatus(error: Throwable?): Int? = generateSequence(error) { it.cause }
        .take(8)
        .filterIsInstance<androidx.media3.datasource.HttpDataSource.InvalidResponseCodeException>()
        .firstOrNull()
        ?.responseCode

    private fun appendToFile(context: Context, line: String) {
        val root = context.getExternalFilesDir(null) ?: context.filesDir
        val directory = File(root, "xmpro/diagnostics")
        if (!directory.exists() && !directory.mkdirs()) return

        val current = File(directory, LOG_NAME)
        val previous = File(directory, PREVIOUS_LOG_NAME)
        val bytes = line.toByteArray(Charsets.UTF_8)
        if (current.length() + bytes.size > MAX_LOG_BYTES) {
            if (current.exists()) {
                FileOutputStream(previous, false).use { output ->
                    current.inputStream().use { input -> input.copyTo(output) }
                }
            }
            FileOutputStream(current, false).use { }
        }
        FileOutputStream(current, true).bufferedWriter(Charsets.UTF_8).use { writer ->
            writer.write(line)
        }
    }

    private fun deviceIdle(context: Context): Boolean? = runCatching {
        context.getSystemService(PowerManager::class.java)?.isDeviceIdleMode
    }.getOrNull()

    private fun interactive(context: Context): Boolean? = runCatching {
        context.getSystemService(PowerManager::class.java)?.isInteractive
    }.getOrNull()

    private fun powerSave(context: Context): Boolean? = runCatching {
        context.getSystemService(PowerManager::class.java)?.isPowerSaveMode
    }.getOrNull()

    private fun batteryExempt(context: Context): Boolean? = runCatching {
        context.getSystemService(PowerManager::class.java)
            ?.isIgnoringBatteryOptimizations(context.packageName)
    }.getOrNull()

    private fun plugged(context: Context): Boolean? = runCatching {
        val battery = context.registerReceiver(null, android.content.IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        battery?.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0)?.let { it != 0 }
    }.getOrNull()

    private fun network(context: Context): String = runCatching {
        val manager = context.getSystemService(ConnectivityManager::class.java)
            ?: return@runCatching "unknown"
        val capabilities = manager.getNetworkCapabilities(manager.activeNetwork)
            ?: return@runCatching "none"
        val transport = when {
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "wifi"
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "cellular"
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "ethernet"
            else -> "other"
        }
        "$transport-${if (capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)) "validated" else "unvalidated"}"
    }.getOrDefault("unknown")

    private fun token(value: Any?): String = when (value) {
        null -> "null"
        is Boolean, is Number -> value.toString()
        is Enum<*> -> value.name.lowercase(Locale.ROOT)
        else -> value.toString()
            .replace(Regex("[\\s=\\r\\n]"), "_")
            .take(96)
    }
}
