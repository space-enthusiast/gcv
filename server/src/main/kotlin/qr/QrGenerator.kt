package com.github.spaceenthusiast.qr

import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO

class QrGenerator {
    fun generate(content: String): String {
        val size = 29
        val qr = QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, size, size)

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

    fun generateImage(content: String): ByteArray {
        val size = 250
        val qr = QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, size, size)

        val width = qr.width
        val height = qr.height

        val bufferedImage = BufferedImage(width, height, BufferedImage.TYPE_INT_RGB)
        for (x in 0 until width) {
            for (y in 0 until height) {
                bufferedImage.setRGB(x, y, if (qr[x, y]) 0x000000 else 0xFFFFFF)
            }
        }

        val outputStream = ByteArrayOutputStream()
        ImageIO.write(bufferedImage, "png", outputStream)
        return outputStream.toByteArray()
    }
}