// ============================================================
// Datei:    Shape.kt
// Autor:    Carlo
// Erstellt: 2026-03-27
// Stand:    1.3
// Projekt:  CarMa
// Zweck:    Material3 Formgebung (RoundedCornerShapes)
//
// HISTORY:
// 2026-04-22  v1.3  Header hinzugefügt (Siemens-Standard)
// ============================================================
package com.carma.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

val Shapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(6.dp),
    medium = RoundedCornerShape(8.dp),
    large = RoundedCornerShape(12.dp),
    extraLarge = RoundedCornerShape(16.dp)
)
