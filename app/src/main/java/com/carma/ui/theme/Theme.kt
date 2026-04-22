// ============================================================
// Datei:    Theme.kt
// Autor:    Carlo
// Erstellt: 2026-03-27
// Stand:    1.3
// Projekt:  CarMa
// Zweck:    Material3 Dark-Theme-Definition
//
// HISTORY:
// 2026-04-22  v1.3  Header hinzugefügt (Siemens-Standard)
// ============================================================
package com.carma.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val DarkColors = darkColorScheme(
    primary = BluePrimary,
    secondary = BlueSecondary,
    background = BlueBackground,
    surface = BlueSurface,
    onPrimary = TextPrimary,
    onSecondary = TextPrimary,
    onBackground = TextPrimary,
    onSurface = TextPrimary,
    error = ErrorRed
)

@Composable
fun CarmaTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DarkColors,
        typography = AppTypography,
        shapes = Shapes,
        content = content
    )
}
