package com.projectmimir.finr

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.material3.Typography
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.TextStyle

enum class ThemeMode(val prefValue: String) {
    LIGHT("light"),
    DARK("dark"),
    MIDNIGHT("midnight");

    companion object {
        fun fromPref(value: String?): ThemeMode {
            return entries.firstOrNull { it.prefValue == value } ?: LIGHT
        }
    }
}

val Vaporwave1 = Color(0xFF2C2D3D)
val Vaporwave2 = Color(0xFFF29F05)
val Vaporwave3 = Color(0xFF295059)
val Vaporwave4 = Color(0xFFF8A3B3)
val Vaporwave5 = Color(0xFFA3F8B3)
val Vaporwave6 = Color(0xFFA3F8B3)
val Vaporwave7 = Color(0xFFFFFFFF)
val AppBackground = Color(0xFFECEEF2)
val TxnDebitAmount = Color(0xFFFF3300)
val DarkTxnDebitAmount = Color(0xFFB22400)
val TxnCreditAmount = Color(0xFF06D6A0)
val TxnCardBorder = Color(0xFFE5E7EC)
val TxnCardShadow = Color(0xFF9EA4B8)
val DailySummaryAccent = Color(0xFFD69800)
val DailySummaryShadow = Color(0xFFD59701)
val HeaderLightBg = Color(0xFF2E6D4E)
val SplashBg = Color(0xFF123524)
val MonthlySentBorder = Color(0xFFEF476F)
val MonthlySentShadow = Color(0xFF67152A)
val MonthlyReceivedBorder = Color(0xFF06D6A0)
val MonthlyReceivedShadow = Color(0xFF039971)
val DarkBg = Color(0xFF000000)
val DarkSurface = Color(0xFF1A2230)
val DarkCard = Color(0xFF1E2938)
val DarkText = Color(0xFFECEEF2)
val DarkMutedText = Color(0xFFC0CADB)
val HeaderDarkBg = Color(0xFF1D3E32)
val SplashDarkBg = Color(0xFF0B1F18)
val DarkCardBorder = Color(0xFF334256)
val DarkCardShadow = Color(0x66000000)
val DarkDailyAccent = Color(0xFFFFBB34)
val DarkDailyShadow = Color(0xFF7A5908)
val DarkMonthlySentBorder = Color(0xFFEF476F)
val DarkMonthlySentShadow = Color(0xFF2A0F16)
val DarkMonthlyReceivedBorder = Color(0xFF06D6A0)
val DarkMonthlyReceivedShadow = Color(0xFF063428)
val Midnight1 = Color(0xFFF2EBDF)
val Midnight2 = Color(0xFFAA78BF)
val Midnight3 = Color(0xFF1C3D59)
val Midnight4 = Color(0xFFB0B6D9)
val Midnight5 = Color(0xFF0D0D0D)
val MidnightBg = Midnight5
val MidnightSurface = Color(0xFF131B24)
val MidnightCard = Color(0xFF17293A)
val MidnightText = Midnight1
val MidnightMutedText = Midnight4
val MidnightHeaderBg = Midnight3
val MidnightSplashBg = Midnight5
val MidnightCardBorder = Color(0x66596180)
val MidnightCardShadow = Color(0x80000000)
val MidnightDailyAccent = Midnight2
val MidnightDailyShadow = Color(0xFF4C3258)
val MidnightMonthlySentBorder = Color(0xFFEF476F)
val MidnightMonthlySentShadow = Color(0xFF2A0F16)
val MidnightMonthlyReceivedBorder = Color(0xFF06D6A0)
val MidnightMonthlyReceivedShadow = Color(0xFF063428)

private val LightColors: ColorScheme = lightColorScheme(
    primary = Vaporwave2,
    onPrimary = Vaporwave1,
    secondary = Vaporwave5,
    onSecondary = Vaporwave1,
    tertiary = Vaporwave4,
    onTertiary = Vaporwave1,
    background = AppBackground,
    onBackground = Vaporwave1,
    surface = AppBackground,
    onSurface = Vaporwave1,
    surfaceVariant = Vaporwave7,
    onSurfaceVariant = Vaporwave1,
    primaryContainer = Vaporwave3,
    onPrimaryContainer = Vaporwave7,
    secondaryContainer = Vaporwave5,
    onSecondaryContainer = Vaporwave1,
    error = Vaporwave4,
    onError = Vaporwave1
)

private val DarkColors: ColorScheme = darkColorScheme(
    primary = Vaporwave2,
    onPrimary = Vaporwave1,
    secondary = Vaporwave5,
    onSecondary = Vaporwave1,
    tertiary = Vaporwave4,
    onTertiary = Vaporwave1,
    background = DarkBg,
    onBackground = DarkText,
    surface = DarkSurface,
    onSurface = DarkText,
    surfaceVariant = DarkCard,
    onSurfaceVariant = DarkMutedText,
    primaryContainer = HeaderDarkBg,
    onPrimaryContainer = DarkText,
    secondaryContainer = Color(0xFF245447),
    onSecondaryContainer = DarkText,
    error = Vaporwave4,
    onError = Vaporwave1
)
private val MidnightColors: ColorScheme = darkColorScheme(
    primary = Midnight2,
    onPrimary = Midnight1,
    secondary = Midnight4,
    onSecondary = Midnight5,
    tertiary = Vaporwave4,
    onTertiary = Midnight5,
    background = MidnightBg,
    onBackground = MidnightText,
    surface = MidnightSurface,
    onSurface = MidnightText,
    surfaceVariant = MidnightCard,
    onSurfaceVariant = MidnightMutedText,
    primaryContainer = MidnightHeaderBg,
    onPrimaryContainer = MidnightText,
    secondaryContainer = Midnight3,
    onSecondaryContainer = MidnightText,
    error = Vaporwave4,
    onError = Midnight5
)
private val MonoFamily = FontFamily(Font(R.font.roboto_mono))
val RobotoCondensedFamily = FontFamily(Font(R.font.roboto_condensed_regular))
private fun mono(style: TextStyle): TextStyle = style.copy(fontFamily = MonoFamily)
private val BaseTypography = Typography()
private val AppTypography = Typography(
    displayLarge = mono(BaseTypography.displayLarge),
    displayMedium = mono(BaseTypography.displayMedium),
    displaySmall = mono(BaseTypography.displaySmall),
    headlineLarge = mono(BaseTypography.headlineLarge),
    headlineMedium = mono(BaseTypography.headlineMedium),
    headlineSmall = mono(BaseTypography.headlineSmall),
    titleLarge = mono(BaseTypography.titleLarge),
    titleMedium = mono(BaseTypography.titleMedium),
    titleSmall = mono(BaseTypography.titleSmall),
    bodyLarge = mono(BaseTypography.bodyLarge),
    bodyMedium = mono(BaseTypography.bodyMedium),
    bodySmall = mono(BaseTypography.bodySmall),
    labelLarge = mono(BaseTypography.labelLarge),
    labelMedium = mono(BaseTypography.labelMedium),
    labelSmall = mono(BaseTypography.labelSmall)
)
private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(12.dp),
    extraLarge = RoundedCornerShape(12.dp)
)

val LocalThemeMode = staticCompositionLocalOf { ThemeMode.LIGHT }

@Composable
fun appThemeMode(): ThemeMode = LocalThemeMode.current

@Composable
fun appIsDarkTheme(): Boolean = appThemeMode() != ThemeMode.LIGHT

@Composable
fun appHeaderBg(): Color = when (appThemeMode()) {
    ThemeMode.LIGHT -> HeaderLightBg
    ThemeMode.DARK -> HeaderDarkBg
    ThemeMode.MIDNIGHT -> MidnightHeaderBg
}

@Composable
fun appSplashBg(): Color = when (appThemeMode()) {
    ThemeMode.LIGHT -> SplashBg
    ThemeMode.DARK -> SplashDarkBg
    ThemeMode.MIDNIGHT -> MidnightSplashBg
}

@Composable
fun appTextColor(): Color = when (appThemeMode()) {
    ThemeMode.LIGHT -> Vaporwave1
    ThemeMode.DARK -> DarkText
    ThemeMode.MIDNIGHT -> MidnightText
}

@Composable
fun debitAmountColor(): Color = when (appThemeMode()) {
    ThemeMode.LIGHT -> TxnDebitAmount
    ThemeMode.DARK -> DarkTxnDebitAmount
    ThemeMode.MIDNIGHT -> TxnDebitAmount
}

@Composable
fun txnCardBg(): Color = when (appThemeMode()) {
    ThemeMode.LIGHT -> Vaporwave7
    ThemeMode.DARK -> DarkCard
    ThemeMode.MIDNIGHT -> MidnightCard
}

@Composable
fun txnCardBorderColor(): Color = when (appThemeMode()) {
    ThemeMode.LIGHT -> TxnCardBorder
    ThemeMode.DARK -> DarkCardBorder
    ThemeMode.MIDNIGHT -> MidnightCardBorder
}

@Composable
fun txnCardShadowColor(): Color = when (appThemeMode()) {
    ThemeMode.LIGHT -> TxnCardShadow
    ThemeMode.DARK -> DarkCardShadow
    ThemeMode.MIDNIGHT -> MidnightCardShadow
}

@Composable
fun txnArrowBadgeBg(): Color = when (appThemeMode()) {
    ThemeMode.LIGHT -> AppBackground
    ThemeMode.DARK -> Color(0xFF2A3648)
    ThemeMode.MIDNIGHT -> Midnight3
}

@Composable
fun dailySummaryAccent(): Color = when (appThemeMode()) {
    ThemeMode.LIGHT -> DailySummaryAccent
    ThemeMode.DARK -> DarkDailyAccent
    ThemeMode.MIDNIGHT -> MidnightDailyAccent
}

@Composable
fun dailySummaryShadowColor(): Color = when (appThemeMode()) {
    ThemeMode.LIGHT -> DailySummaryShadow
    ThemeMode.DARK -> DarkDailyShadow
    ThemeMode.MIDNIGHT -> MidnightDailyShadow
}

@Composable
fun monthlySentBorderColor(): Color = when (appThemeMode()) {
    ThemeMode.LIGHT -> MonthlySentBorder
    ThemeMode.DARK -> DarkMonthlySentBorder
    ThemeMode.MIDNIGHT -> MidnightMonthlySentBorder
}

@Composable
fun monthlySentShadowColor(): Color = when (appThemeMode()) {
    ThemeMode.LIGHT -> MonthlySentShadow
    ThemeMode.DARK -> DarkMonthlySentShadow
    ThemeMode.MIDNIGHT -> MidnightMonthlySentShadow
}

@Composable
fun monthlyReceivedBorderColor(): Color = when (appThemeMode()) {
    ThemeMode.LIGHT -> MonthlyReceivedBorder
    ThemeMode.DARK -> DarkMonthlyReceivedBorder
    ThemeMode.MIDNIGHT -> MidnightMonthlyReceivedBorder
}

@Composable
fun monthlyReceivedShadowColor(): Color = when (appThemeMode()) {
    ThemeMode.LIGHT -> MonthlyReceivedShadow
    ThemeMode.DARK -> DarkMonthlyReceivedShadow
    ThemeMode.MIDNIGHT -> MidnightMonthlyReceivedShadow
}

@Composable
fun SmsReaderTheme(themeMode: ThemeMode = ThemeMode.LIGHT, content: @Composable () -> Unit) {
    val colors = when (themeMode) {
        ThemeMode.LIGHT -> LightColors
        ThemeMode.DARK -> DarkColors
        ThemeMode.MIDNIGHT -> MidnightColors
    }
    CompositionLocalProvider(LocalThemeMode provides themeMode) {
        MaterialTheme(
            colorScheme = colors,
            typography = AppTypography,
            shapes = AppShapes,
            content = content
        )
    }
}
