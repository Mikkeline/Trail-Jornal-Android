package dev.michelle.trails

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val JournalTypography = Typography(
    displaySmall = TextStyle(fontSize = 44.sp, lineHeight = 48.sp, fontWeight = FontWeight.Bold, letterSpacing = (-1).sp),
    headlineLarge = TextStyle(fontSize = 30.sp, lineHeight = 36.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.5).sp),
    headlineMedium = TextStyle(fontSize = 26.sp, lineHeight = 32.sp, fontWeight = FontWeight.SemiBold),
    headlineSmall = TextStyle(fontSize = 24.sp, lineHeight = 30.sp, fontWeight = FontWeight.SemiBold),
    titleLarge = TextStyle(fontSize = 21.sp, lineHeight = 28.sp, fontWeight = FontWeight.SemiBold),
    titleMedium = TextStyle(fontSize = 17.sp, lineHeight = 24.sp, fontWeight = FontWeight.SemiBold),
    titleSmall = TextStyle(fontSize = 15.sp, lineHeight = 22.sp, fontWeight = FontWeight.SemiBold),
    bodyLarge = TextStyle(fontSize = 16.sp, lineHeight = 25.sp),
    bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 21.sp),
    bodySmall = TextStyle(fontSize = 12.sp, lineHeight = 18.sp),
    labelLarge = TextStyle(fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.SemiBold),
    labelMedium = TextStyle(fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.Medium),
    labelSmall = TextStyle(fontSize = 11.sp, lineHeight = 16.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.6.sp),
)

@Composable
internal fun TrailTheme(dark: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    val colors = if (dark) darkColorScheme(
        primary = Color(0xFFA5D6B5), onPrimary = Color(0xFF0C3823),
        primaryContainer = Color(0xFF254D38), onPrimaryContainer = Color(0xFFD9F0DE),
        secondary = Color(0xFFC0CDAF), onSecondary = Color(0xFF2A3520),
        secondaryContainer = Color(0xFF3D4A32), onSecondaryContainer = Color(0xFFDCE8CC),
        tertiary = Color(0xFFE8B68E), onTertiary = Color(0xFF452B16),
        tertiaryContainer = Color(0xFF60452F), onTertiaryContainer = Color(0xFFFFDFC2),
        background = Color(0xFF111712), onBackground = Color(0xFFE2E9DF),
        surface = Color(0xFF182019), onSurface = Color(0xFFE2E9DF),
        surfaceVariant = Color(0xFF303C32), onSurfaceVariant = Color(0xFFBEC9BD),
        surfaceContainerLowest = Color(0xFF0D130F), surfaceContainerLow = Color(0xFF182019),
        surfaceContainer = Color(0xFF1E271F), surfaceContainerHigh = Color(0xFF283229), surfaceContainerHighest = Color(0xFF333E34),
        outline = Color(0xFF89958A), outlineVariant = Color(0xFF3E4A40),
    ) else lightColorScheme(
        primary = Color(0xFF285540), onPrimary = Color.White,
        primaryContainer = Color(0xFFDBEEDD), onPrimaryContainer = Color(0xFF173C29),
        secondary = Color(0xFF536448), onSecondary = Color.White,
        secondaryContainer = Color(0xFFE2EBD7), onSecondaryContainer = Color(0xFF283A20),
        tertiary = Color(0xFF80512F), onTertiary = Color.White,
        tertiaryContainer = Color(0xFFF5E4D3), onTertiaryContainer = Color(0xFF442B18),
        background = Color(0xFFF5F7F2), onBackground = Color(0xFF1B271F),
        surface = Color(0xFFFFFFFF), onSurface = Color(0xFF1B271F),
        surfaceVariant = Color(0xFFE7ECE3), onSurfaceVariant = Color(0xFF536054),
        surfaceContainerLowest = Color.White, surfaceContainerLow = Color(0xFFF0F4EC),
        surfaceContainer = Color(0xFFEAF0E5), surfaceContainerHigh = Color(0xFFE4EBDD), surfaceContainerHighest = Color(0xFFDDE5D7),
        outline = Color(0xFF748173), outlineVariant = Color(0xFFDEE5D8),
    )
    MaterialTheme(colorScheme = colors, typography = JournalTypography,
        shapes = Shapes(small = RoundedCornerShape(10.dp), medium = RoundedCornerShape(16.dp),
            large = RoundedCornerShape(24.dp), extraLarge = RoundedCornerShape(28.dp)), content = content)
}
