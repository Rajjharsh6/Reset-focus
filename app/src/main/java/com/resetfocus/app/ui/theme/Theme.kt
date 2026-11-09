package com.resetfocus.app.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val Scheme = darkColorScheme(
    primary = Color(0xFF8B8BFF), onPrimary = Color(0xFF0B0B0F),
    secondary = Color(0xFF3DDC97), onSecondary = Color(0xFF04140D),
    background = Color(0xFF0B0B0F), onBackground = Color(0xFFECECF1),
    surface = Color(0xFF15151B), onSurface = Color(0xFFECECF1),
    surfaceVariant = Color(0xFF1E1E27), onSurfaceVariant = Color(0xFFA0A0AE),
    surfaceContainer = Color(0xFF15151B), surfaceContainerHigh = Color(0xFF1E1E27),
    outline = Color(0xFF2E2E38), error = Color(0xFFFF8A80),
)

@Composable
fun ResetFocusTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = Scheme,
        typography = Typography(),
        shapes = Shapes(medium = RoundedCornerShape(20.dp), large = RoundedCornerShape(28.dp)),
        content = content,
    )
}
