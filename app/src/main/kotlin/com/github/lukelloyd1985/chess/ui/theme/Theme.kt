package com.github.lukelloyd1985.chess.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

object ChessColors {
    val Background = Color(0xFF262421)
    val Surface = Color(0xFF312E2B)
    val SurfaceHigh = Color(0xFF3C3935)
    val Green = Color(0xFF81B64C)
    val GreenDark = Color(0xFF5D8F2F)
    val OnDark = Color(0xFFF1F1F1)
    val Muted = Color(0xFFA09D98)

    val BoardLight = Color(0xFFEEEED2)
    val BoardDark = Color(0xFF769656)
    val LastMove = Color(0x80F6F669)
    val Selected = Color(0xA0F6F669)
    val Check = Color(0xCCE5484D)
    val Hint = Color(0xE681B64C)

    val Best = Color(0xFF81B64C)
    val Excellent = Color(0xFF96BC4B)
    val Good = Color(0xFFA3A3A3)
    val Inaccuracy = Color(0xFFF7C631)
    val Mistake = Color(0xFFE58F2A)
    val Blunder = Color(0xFFCA3431)
}

private val scheme = darkColorScheme(
    primary = ChessColors.Green,
    onPrimary = Color.Black,
    secondary = ChessColors.GreenDark,
    background = ChessColors.Background,
    onBackground = ChessColors.OnDark,
    surface = ChessColors.Surface,
    onSurface = ChessColors.OnDark,
    surfaceVariant = ChessColors.SurfaceHigh,
    onSurfaceVariant = ChessColors.Muted,
    error = ChessColors.Blunder,
)

@Composable
fun ChessTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = scheme, content = content)
}
