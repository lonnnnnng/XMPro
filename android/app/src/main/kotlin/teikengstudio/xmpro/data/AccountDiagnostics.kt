package teikengstudio.xmpro.data

import android.content.Context
import android.util.Log
import java.io.File

/** long: 领取和关注日志保留到本机，连接电脑后仍能读取此前的业务失败；限制体积避免长期占空间。 */
internal object AccountDiagnostics {
    @Synchronized
    fun record(context: Context, event: String) {
        Log.i("XMProAccount", event)
        runCatching {
            val directory = File(context.getExternalFilesDir(null) ?: context.filesDir, "xmpro/diagnostics")
            if (!directory.exists() && !directory.mkdirs()) return
            val current = File(directory, "account.log")
            if (current.length() + event.length > 256 * 1024) {
                current.copyTo(File(directory, "account.previous.log"), overwrite = true)
                current.writeText("")
            }
            current.appendText(event + "\n")
        }
    }
}
