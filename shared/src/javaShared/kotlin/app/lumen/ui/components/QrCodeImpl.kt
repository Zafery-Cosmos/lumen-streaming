package app.lumen.ui.components

import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter

actual fun encodeQr(text: String): QrMatrix {
    val matrix = QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, 0, 0)
    val bits = BooleanArray(matrix.width * matrix.height)
    for (y in 0 until matrix.height) {
        for (x in 0 until matrix.width) {
            bits[y * matrix.width + x] = matrix.get(x, y)
        }
    }
    return QrMatrix(matrix.width, bits)
}
