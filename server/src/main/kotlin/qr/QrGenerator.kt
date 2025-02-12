package com.github.spaceenthusiast.qr

import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter

class QrGenerator {
    fun generate(content: String): String {
        val size = 29
        val qr = QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, size, size)

        println(qr.width)
        println(qr.height)

        val sb = StringBuilder()
        for (x in 0 until qr.width) {
            for (y in 0 until qr.height) {
                //sb.append(if (qr[x, y]) "░░" else "██")
                sb.append(if (qr[x, y]) "\u001B[40m\u0020\u001B[0m" else "\u001B[47m\u0020\u001B[0m")
                sb.append(if (qr[x, y]) "\u001B[40m\u0020\u001B[0m" else "\u001B[47m\u0020\u001B[0m")
            }
            sb.appendLine()
        }
        val result = sb.toString()
        return result
    }
}