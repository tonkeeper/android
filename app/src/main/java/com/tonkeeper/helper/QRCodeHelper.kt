package com.tonkeeper.helper

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.MultiFormatWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import com.google.zxing.qrcode.encoder.ByteMatrix
import com.google.zxing.qrcode.encoder.Encoder
import com.google.zxing.qrcode.encoder.QRCode

object QRCodeHelper {

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)

    fun createLink(link: String, size: Int): Bitmap {
        val hints = mutableMapOf<EncodeHintType, Any>()
        hints[EncodeHintType.ERROR_CORRECTION] = ErrorCorrectionLevel.H
        hints[EncodeHintType.CHARACTER_SET] = "UTF-8"
        hints[EncodeHintType.MARGIN] = 0

        val code: QRCode = Encoder.encode(link, ErrorCorrectionLevel.H, hints)

        return renderQRImage(code, size, size)
    }

    private fun renderQRImage(code: QRCode, width: Int, height: Int): Bitmap {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)


        // Get the QR code matrix
        val input: ByteMatrix = code.matrix

        // Calculate dimensions
        val inputWidth = input.width
        val inputHeight = input.height
        val qrWidth = inputWidth
        val qrHeight = inputHeight
        val outputWidth = Math.max(width, qrWidth)
        val outputHeight = Math.max(height, qrHeight)

        // Calculate scaling factors and padding
        val multiple = Math.min(outputWidth / qrWidth, outputHeight / qrHeight)
        val leftPadding = (outputWidth - (inputWidth * multiple)) / 2
        val topPadding = (outputHeight - (inputHeight * multiple)) / 2
        val FINDER_PATTERN_SIZE = 7
        val CIRCLE_SCALE_DOWN_FACTOR = 21f / 30f
        val circleSize = (multiple * CIRCLE_SCALE_DOWN_FACTOR).toInt()

        // Iterate through each QR code module
        for (inputY in 0 until inputHeight) {
            var outputY = topPadding
            outputY += multiple * inputY
            for (inputX in 0 until inputWidth) {
                var outputX = leftPadding
                outputX += multiple * inputX
                if (input.get(inputX, inputY).toInt() == 1) {
                    if (!(inputX <= FINDER_PATTERN_SIZE && inputY <= FINDER_PATTERN_SIZE ||
                                inputX >= inputWidth - FINDER_PATTERN_SIZE && inputY <= FINDER_PATTERN_SIZE ||
                                inputX <= FINDER_PATTERN_SIZE && inputY >= inputHeight - FINDER_PATTERN_SIZE)
                    ) {
                        canvas.drawOval(
                            RectF(
                                outputX.toFloat(),
                                outputY.toFloat(),
                                (outputX + circleSize).toFloat(),
                                (outputY + circleSize).toFloat()
                            ),
                            paint
                        )
                    }
                }
            }
        }

        // Draw finder patterns
        val circleDiameter = multiple * FINDER_PATTERN_SIZE
        drawFinderPatternCircleStyle(canvas, paint, leftPadding, topPadding, circleDiameter)
        drawFinderPatternCircleStyle(
            canvas,
            paint,
            leftPadding + (inputWidth - FINDER_PATTERN_SIZE) * multiple,
            topPadding,
            circleDiameter
        )
        drawFinderPatternCircleStyle(
            canvas,
            paint,
            leftPadding,
            topPadding + (inputHeight - FINDER_PATTERN_SIZE) * multiple,
            circleDiameter
        )

        return bitmap
    }

    private fun drawFinderPatternCircleStyle(
        canvas: Canvas,
        paint: Paint,
        x: Int,
        y: Int,
        circleDiameter: Int
    ) {
        val WHITE_CIRCLE_DIAMETER = circleDiameter * 5 / 7
        val WHITE_CIRCLE_OFFSET = circleDiameter / 7
        val MIDDLE_DOT_DIAMETER = circleDiameter * 3 / 7
        val MIDDLE_DOT_OFFSET = circleDiameter * 2 / 7

        paint.color = Color.BLACK
        canvas.drawRoundRect(
            RectF(
                x.toFloat(),
                y.toFloat(),
                (x + circleDiameter).toFloat(),
                (y + circleDiameter).toFloat()
            ),
            (circleDiameter / 4f),
            (circleDiameter / 4f),
            paint
        )

        paint.color = Color.WHITE
        canvas.drawRoundRect(
            RectF(
                (x + WHITE_CIRCLE_OFFSET).toFloat(),
                (y + WHITE_CIRCLE_OFFSET).toFloat(),
                (x + WHITE_CIRCLE_OFFSET + WHITE_CIRCLE_DIAMETER).toFloat(),
                (y + WHITE_CIRCLE_OFFSET + WHITE_CIRCLE_DIAMETER).toFloat()
            ),
            (circleDiameter / 6f),
            (circleDiameter / 6f),
            paint
        )

        paint.color = Color.BLACK
        canvas.drawRoundRect(
            RectF(
                (x + MIDDLE_DOT_OFFSET).toFloat(),
                (y + MIDDLE_DOT_OFFSET).toFloat(),
                (x + MIDDLE_DOT_OFFSET + MIDDLE_DOT_DIAMETER).toFloat(),
                (y + MIDDLE_DOT_OFFSET + MIDDLE_DOT_DIAMETER).toFloat()
            ),
            (circleDiameter / 4f),
            (circleDiameter / 4f),
            paint
        )
    }

}