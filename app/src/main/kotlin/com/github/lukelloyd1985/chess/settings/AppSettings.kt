package com.github.lukelloyd1985.chess.settings

import android.content.Context
import androidx.compose.ui.graphics.Color
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** Board colour themes: [light] and [dark] squares. */
enum class BoardTheme(val label: String, val light: Color, val dark: Color) {
    GREEN("Green", Color(0xFFEEEED2), Color(0xFF769656)),
    BROWN("Brown", Color(0xFFF0D9B5), Color(0xFFB58863)),
    BLUE("Blue", Color(0xFFDEE3E6), Color(0xFF8CA2AD)),
    GREY("Grey", Color(0xFFDADADA), Color(0xFF8A8A8A)),
    PURPLE("Purple", Color(0xFFE9E1F2), Color(0xFF8877B7)),
    CORAL("Coral", Color(0xFFF4E4DD), Color(0xFFC57F73)),
}

/**
 * Piece artwork. [asset] is the file under assets/pieces/. With [outlineWhite] the white pieces
 * use the hollow glyphs instead of tinted solid ones.
 */
enum class PieceSet(val label: String, val asset: String, val outlineWhite: Boolean, val strokeWidth: Float) {
    CLASSIC("Classic", "classic", false, 2.5f),
    SERIF("Serif", "serif", false, 2.5f),
    OUTLINE("Outline", "classic", true, 2.5f),
    PIXEL("Pixel", "pixel", false, 1.5f),
}

/** Fill colours for the two sides. */
enum class PieceColors(val label: String, val white: Color, val black: Color) {
    STANDARD("White & Black", Color(0xFFFFFFFF), Color(0xFF1B1B1B)),
    IVORY("Ivory & Ebony", Color(0xFFF7EBD0), Color(0xFF2E1D12)),
    GOLD("Gold & Navy", Color(0xFFF4C542), Color(0xFF1F2E4F)),
}

data class SettingsState(
    val boardTheme: BoardTheme = BoardTheme.GREEN,
    val pieceSet: PieceSet = PieceSet.CLASSIC,
    val pieceColors: PieceColors = PieceColors.STANDARD,
    val showCoordinates: Boolean = true,
)

/** User preferences, persisted in SharedPreferences and observable as a flow. */
class AppSettings(context: Context) {
    private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    private val _state = MutableStateFlow(load())
    val state: StateFlow<SettingsState> = _state

    private inline fun <reified E : Enum<E>> read(key: String, default: E): E =
        prefs.getString(key, null)?.let { name -> enumValues<E>().firstOrNull { it.name == name } } ?: default

    private fun load() = SettingsState(
        boardTheme = read(KEY_THEME, BoardTheme.GREEN),
        pieceSet = read(KEY_PIECES, PieceSet.CLASSIC),
        pieceColors = read(KEY_COLORS, PieceColors.STANDARD),
        showCoordinates = prefs.getBoolean(KEY_COORDS, true),
    )

    fun setBoardTheme(v: BoardTheme) = update { it.copy(boardTheme = v) }.also { prefs.edit().putString(KEY_THEME, v.name).apply() }
    fun setPieceSet(v: PieceSet) = update { it.copy(pieceSet = v) }.also { prefs.edit().putString(KEY_PIECES, v.name).apply() }
    fun setPieceColors(v: PieceColors) = update { it.copy(pieceColors = v) }.also { prefs.edit().putString(KEY_COLORS, v.name).apply() }
    fun setShowCoordinates(v: Boolean) = update { it.copy(showCoordinates = v) }.also { prefs.edit().putBoolean(KEY_COORDS, v).apply() }

    private fun update(block: (SettingsState) -> SettingsState) {
        _state.value = block(_state.value)
    }

    private companion object {
        const val KEY_THEME = "board_theme"
        const val KEY_PIECES = "piece_set"
        const val KEY_COLORS = "piece_colors"
        const val KEY_COORDS = "show_coordinates"
    }
}
