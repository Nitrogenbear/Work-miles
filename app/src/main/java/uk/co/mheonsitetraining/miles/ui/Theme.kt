package uk.co.mheonsitetraining.miles.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import uk.co.mheonsitetraining.miles.R

/** MHE Onsite Training brand colours, sampled from the logo. Change them here to re-brand the app. */
object Brand {
    val Navy = Color(0xFF132D50)
    val Orange = Color(0xFFE26D0E)
    val Grey = Color(0xFF676767)
    val Background = Color(0xFFF4F5F7)
    val NavyTint = Color(0xFFE3E8F0)
    val OrangeTint = Color(0xFFFCEBDD)
    val Success = Color(0xFF2E7D32)
}

val Montserrat = FontFamily(
    Font(R.font.montserrat_regular, FontWeight.Normal),
    Font(R.font.montserrat_semibold, FontWeight.SemiBold),
    Font(R.font.montserrat_bold, FontWeight.Bold),
    Font(R.font.montserrat_extrabold, FontWeight.ExtraBold),
)

private val colours = lightColorScheme(
    primary = Brand.Navy,
    onPrimary = Color.White,
    primaryContainer = Brand.NavyTint,
    onPrimaryContainer = Brand.Navy,
    secondary = Brand.Orange,
    onSecondary = Color.White,
    secondaryContainer = Brand.OrangeTint,
    onSecondaryContainer = Color(0xFF6B3000),
    tertiary = Brand.Grey,
    background = Brand.Background,
    onBackground = Color(0xFF1B1F24),
    surface = Color.White,
    onSurface = Color(0xFF1B1F24),
    surfaceVariant = Color(0xFFECEEF1),
    onSurfaceVariant = Brand.Grey,
    surfaceContainer = Color.White,
    surfaceContainerLow = Color.White,
    surfaceContainerHigh = Color.White,
    outline = Color(0xFFC4C8CE),
)

private fun Typography.withFont(): Typography {
    fun TextStyle.m(weight: FontWeight? = null) = copy(fontFamily = Montserrat, fontWeight = weight ?: fontWeight)
    return copy(
        displayLarge = displayLarge.m(FontWeight.ExtraBold),
        displayMedium = displayMedium.m(FontWeight.ExtraBold),
        displaySmall = displaySmall.m(FontWeight.ExtraBold),
        headlineLarge = headlineLarge.m(FontWeight.Bold),
        headlineMedium = headlineMedium.m(FontWeight.Bold),
        headlineSmall = headlineSmall.m(FontWeight.Bold),
        titleLarge = titleLarge.m(FontWeight.Bold),
        titleMedium = titleMedium.m(FontWeight.SemiBold),
        titleSmall = titleSmall.m(FontWeight.SemiBold),
        bodyLarge = bodyLarge.m(),
        bodyMedium = bodyMedium.m(),
        bodySmall = bodySmall.m(),
        labelLarge = labelLarge.m(FontWeight.Bold).copy(letterSpacing = 0.4.sp),
        labelMedium = labelMedium.m(FontWeight.SemiBold),
        labelSmall = labelSmall.m(FontWeight.SemiBold),
    )
}

@Composable
fun MheTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = colours, typography = Typography().withFont(), content = content)
}
