package com.dronedirector.procam.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

object Pro {
    val Amber = Color(0xFFFFB300)
    val Red = Color(0xFFFF3B30)
    val Green = Color(0xFF34C759)
    val Panel = Color(0xCC0E1013)
    val PanelSolid = Color(0xFF15181C)
    val Line = Color(0x33FFFFFF)
    val Dim = Color(0x99FFFFFF)

    val Mono = TextStyle(fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Medium, fontSize = 13.sp, color = Color.White)
    val Label = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 9.sp, letterSpacing = 1.2.sp, color = Dim)
    val Value = TextStyle(fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Color.White)
}

@Composable
fun ProCamTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Pro.Amber,
            onPrimary = Color.Black,
            secondary = Pro.Amber,
            background = Color.Black,
            surface = Pro.PanelSolid,
            onSurface = Color.White,
        ),
        content = content,
    )
}
