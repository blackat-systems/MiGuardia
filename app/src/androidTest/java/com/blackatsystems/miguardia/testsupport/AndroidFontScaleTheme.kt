package com.blackatsystems.miguardia.testsupport

import android.content.Context
import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.FontScaling
import com.blackatsystems.miguardia.ui.theme.MiGuardiaTheme

/** Explicit QA scenarios only: these are Android text sizes, not app zoom preferences. */
enum class TestFontScale(val scale: Float, val percent: Int) {
    STANDARD(1f, 100), LARGE(1.5f, 150), EXTRA_LARGE(2f, 200),
}

/** Uses Android/Compose's native SP converter, including nonlinear scaling on supported APIs.
 * The configuration belongs to a new Context; no device-wide setting or preference is changed.
 * Geometric density remains the host's density rather than scaling the entire interface. */
fun androidFontDensity(context: Context, parent: Density, fontScale: TestFontScale): Density {
    val configuration = Configuration(context.resources.configuration).apply {
        this.fontScale = fontScale.scale
    }
    val native = Density(context.createConfigurationContext(configuration))
    return object : Density, FontScaling by native {
        override val density: Float = parent.density
    }
}

@Composable
fun AndroidFontScaleTheme(
    darkTheme: Boolean = true,
    fontScale: TestFontScale? = null,
    content: @Composable () -> Unit,
) {
    val parent = LocalDensity.current
    val context = LocalContext.current
    val density = remember(context, parent, fontScale) {
        fontScale?.let { androidFontDensity(context, parent, it) } ?: parent
    }
    CompositionLocalProvider(LocalDensity provides density) {
        MiGuardiaTheme(darkTheme = darkTheme, content = content)
    }
}
