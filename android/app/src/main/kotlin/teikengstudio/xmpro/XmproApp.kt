package teikengstudio.xmpro

import android.app.Application
import teikengstudio.xmpro.data.AppContainer
import teikengstudio.xmpro.player.PlaybackDiagnostics

/** Owns the single service graph for the process. */
class XmproApp : Application() {
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
