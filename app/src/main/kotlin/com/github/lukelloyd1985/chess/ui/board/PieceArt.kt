package com.github.lukelloyd1985.chess.ui.board

import android.content.Context
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.platform.LocalContext
import com.github.lukelloyd1985.chess.core.Piece
import com.github.lukelloyd1985.chess.settings.BoardTheme
import com.github.lukelloyd1985.chess.settings.PieceColors
import com.github.lukelloyd1985.chess.settings.PieceSet
import com.github.lukelloyd1985.chess.settings.SettingsState

/** Parsed vector outlines for one [PieceSet], keyed like "bK" / "wN" (see assets/pieces/README.md). */
class PieceArt(val set: PieceSet, private val paths: Map<String, Path>) {
    fun pathFor(piece: Int): Path? {
        val letter = Piece.letter(Piece.type(piece)).uppercaseChar()
        val outlined = set.outlineWhite && Piece.isWhite(piece)
        return paths[(if (outlined) "w" else "b") + letter]
    }

    companion object {
        /** Loads and parses the set's asset. Returns null if it is missing or unreadable. */
        fun load(context: Context, set: PieceSet): PieceArt? = try {
            val parser = PathParser()
            val map = HashMap<String, Path>()
            context.assets.open("pieces/${set.asset}.txt").bufferedReader().useLines { lines ->
                for (line in lines) {
                    val eq = line.indexOf('=')
                    if (eq <= 0) continue
                    val key = line.substring(0, eq)
                    map[key] = parser.parsePathString(line.substring(eq + 1)).toPath().apply {
                        // Hollow glyphs are drawn with even-odd so their inner contours stay see-through.
                        fillType = if (set.outlineWhite && key.startsWith("w")) PathFillType.EvenOdd else PathFillType.NonZero
                    }
                }
            }
            PieceArt(set, map)
        } catch (e: Exception) {
            null
        }
    }
}

/** Everything the board needs to know about how to look. */
data class BoardStyle(
    val theme: BoardTheme = BoardTheme.GREEN,
    /** Null falls back to plain text glyphs (e.g. if the artwork failed to load). */
    val art: PieceArt? = null,
    val pieceColors: PieceColors = PieceColors.STANDARD,
    val showCoordinates: Boolean = true,
)

val LocalBoardStyle = compositionLocalOf { BoardStyle() }

/** Provides the [BoardStyle] for the current [settings] to everything below it. */
@Composable
fun ProvideBoardStyle(settings: SettingsState, content: @Composable () -> Unit) {
    val context = LocalContext.current
    val art = remember(settings.pieceSet) { PieceArt.load(context, settings.pieceSet) }
    val style = remember(settings, art) {
        BoardStyle(settings.boardTheme, art, settings.pieceColors, settings.showCoordinates)
    }
    CompositionLocalProvider(LocalBoardStyle provides style, content = content)
}

/** Draws [piece] in the current [LocalBoardStyle], filling its bounds. */
@Composable
fun PieceIcon(piece: Int, modifier: Modifier = Modifier, style: BoardStyle = LocalBoardStyle.current) {
    val art = style.art ?: return
    val path = art.pathFor(piece) ?: return
    val white = Piece.isWhite(piece)
    val fill = if (white) style.pieceColors.white else style.pieceColors.black
    // Hollow glyphs are filled with the piece colour too; outline contrasts with the fill.
    val outline = if (fill.luminance() > 0.5f) Color(0xE6222222) else Color(0xCCDDDDDD)
    Canvas(modifier) {
        // The artwork is authored in a 100 x 100 box.
        scale(size.width / 100f, size.height / 100f, pivot = Offset.Zero) {
            drawPath(path, fill)
            drawPath(path, outline, style = Stroke(width = art.set.strokeWidth, join = StrokeJoin.Round))
        }
    }
}
