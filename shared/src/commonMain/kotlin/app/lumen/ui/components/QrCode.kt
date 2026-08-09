package app.lumen.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color

/** Matrice binaire d'un QR code — carrée, un bit par module. */
data class QrMatrix(val size: Int, val bits: BooleanArray)

expect fun encodeQr(text: String): QrMatrix

/** Affiche un QR code en dessinant chaque module directement (pas de bitmap). */
@Composable
fun QrCodeView(text: String, modifier: Modifier = Modifier) {
    val matrix = remember(text) { encodeQr(text) }
    Canvas(modifier.aspectRatio(1f).background(Color.White)) {
        val cell = size.width / matrix.size
        for (y in 0 until matrix.size) {
            for (x in 0 until matrix.size) {
                if (matrix.bits[y * matrix.size + x]) {
                    drawRect(
                        color = Color.Black,
                        topLeft = Offset(x * cell, y * cell),
                        size = Size(cell, cell),
                    )
                }
            }
        }
    }
}
