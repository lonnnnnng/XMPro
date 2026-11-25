package teikengstudio.xmpro

import android.app.Application
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import teikengstudio.xmpro.data.AppContainer
import teikengstudio.xmpro.player.PlaybackDiagnostics

/** Owns the single service graph and app-wide playback queue for the process. */
class XmproApp : Application(), ViewModelStoreOwner {
    // long: Activity 可能在媒体播放服务仍运行时被回收，队列消费者必须随播放进程而非界面存活。
    override val viewModelStore = ViewModelStore()

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        PlaybackDiagnostics.record(this, "app_process_started")
        container = AppContainer(this)
        // A crash during a debit leaves `submitting` receipts; reconcile them
        // locally before any playback or download can start.
        runCatching { container.ledger.recoverAll() }
    }
}
