package com.music.swell.ui.components.liquidglass

import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.music.swell.data.settings.AppSettings

/**
 * True when the user asked for less motion: the app's Reduce animation switch, or
 * the system's animations turned off (animator duration scale 0). The glass morphs
 * then cross-fade quickly instead of springing and overshooting.
 */
@Composable
fun rememberReduceMotion(): Boolean {
    val context = LocalContext.current
    val appReduce by AppSettings.reduceAnimation.collectAsStateWithLifecycle()
    val systemOff = remember(context) {
        runCatching {
            Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
        }.getOrDefault(false)
    }
    return appReduce || systemOff
}
