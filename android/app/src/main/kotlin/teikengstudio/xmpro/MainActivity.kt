package teikengstudio.xmpro

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.compose.ui.graphics.toArgb
import teikengstudio.xmpro.ui.ShellScreen
import teikengstudio.xmpro.ui.theme.XmproTheme
import teikengstudio.xmpro.ui.theme.AppUiScale
import kotlin.math.roundToInt

class MainActivity : ComponentActivity() {
    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(newBase)
        // long: 在界面资源入口统一缩放，独立窗口中的弹窗和原生验证码也使用同一密度；不修改系统设置或字体偏好。
        applyOverrideConfiguration(Configuration().apply {
            densityDpi = (newBase.resources.configuration.densityDpi * AppUiScale).roundToInt()
        })
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        val expectedDensity = (baseContext.resources.configuration.densityDpi * AppUiScale).roundToInt()
        // long: 用户更改系统显示大小时重建界面以重新应用比例，播放队列仍由应用级 ViewModel 持有。
        if (resources.configuration.densityDpi != expectedDensity) recreate()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val container = (application as XmproApp).container
        // Media notifications are how playback survives backgrounding on 13+.
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            registerForActivityResult(ActivityResultContracts.RequestPermission()) { }.launch(
                Manifest.permission.POST_NOTIFICATIONS,
            )
        }
        setContent {
            var oledBlack by remember { mutableStateOf(container.oledBlack()) }
            var themeMode by remember { mutableStateOf(container.themeMode()) }
            var dynamicColor by remember { mutableStateOf(container.dynamicColor()) }
            var seedColor by remember { mutableStateOf(androidx.compose.ui.graphics.Color(container.seedColor())) }
            val dark = when (themeMode) {
                "light" -> false
                "dark" -> true
                else -> isSystemInDarkTheme()
            }
            XmproTheme(
                darkTheme = dark,
                oledBlack = oledBlack,
                dynamicColor = dynamicColor,
                seed = seedColor,
            ) {
                // The window and both system bars take the theme surface colour.
                // Some ROMs paint an opaque status bar unless a colour is set, and
                // a transparent one then shows as a black band above the content.
                val surface = MaterialTheme.colorScheme.surface
                SideEffect {
                    @Suppress("DEPRECATION")
                    window.statusBarColor = surface.toArgb()
                    @Suppress("DEPRECATION")
                    window.navigationBarColor = surface.toArgb()
                    window.setBackgroundDrawable(
                        android.graphics.drawable.ColorDrawable(surface.toArgb()),
                    )
                    WindowCompat.getInsetsController(window, window.decorView).apply {
                        isAppearanceLightStatusBars = !dark
                        isAppearanceLightNavigationBars = !dark
                    }
                }
                ShellScreen(
                    container = container,
                    oledBlack = oledBlack,
                    onOledBlackChange = {
                        oledBlack = it
                        container.setOledBlack(it)
                    },
                    onThemeModeChange = { themeMode = it },
                    onAppearanceChange = { useDynamic, seed ->
                        dynamicColor = useDynamic
                        seedColor = androidx.compose.ui.graphics.Color(seed)
                    },
                )
            }
        }
    }
}
