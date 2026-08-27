package com.minion.scaffold.core.designsystem.component

import com.google.zxing.BinaryBitmap
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.qrcode.QRCodeReader
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

private const val SIZE_PX = 512
private const val BLACK = 0xFF000000.toInt()
private const val WHITE = 0xFFFFFFFF.toInt()

/** `مرحبا بالعالم`, as escapes — a test about encoding must not depend on the file's own encoding. */
private const val ARABIC =
    "\u0645\u0631\u062d\u0628\u0627\u0020\u0628\u0627\u0644\u0639\u0627\u0644\u0645"

/**
 * What a generated code actually contains, read back out of it.
 *
 * This exists because of a real defect. Encoding without naming a character set left zxing on its
 * ISO-8859-1 default, whose encoder writes a literal `?` for every character it cannot map — so a
 * payment code built with an Arabic merchant name was generated already broken. It scanned
 * perfectly and read back `????? ???????`, with a checksum that failed because tag 63 still carried
 * the value computed from the text the code no longer held. Every scanner in the chain was
 * innocent, which is exactly why it took so long to find.
 *
 * Reading the code back is the only assertion that would have caught it. Checking that the encoder
 * was *called* with a hint proves nothing about what came out.
 */
class QrCodeEncodingTest {

    @Test
    fun `a non-ascii payload survives being encoded`() {
        val payload = "PAY TO $ARABIC"

        assertEquals(payload, decode(payload))
    }

    /**
     * The specific shape of the defect: same length, every non-ASCII character replaced.
     *
     * Asserting the whole payload round-trips would catch this too, but not say what went wrong.
     * A failure here points straight at the substitution.
     */
    @Test
    fun `does not substitute question marks for characters it cannot map`() {
        val decoded = decode(ARABIC)

        assertEquals(ARABIC.length, decoded?.length)
        assertEquals(0, decoded?.count { it == '?' })
    }

    /**
     * An EMV payload of the kind the create tool builds, checksum and all.
     *
     * Byte-exact, because a payment payload's checksum covers its own text: one character different
     * anywhere and tag 63 no longer describes what the code says.
     */
    @Test
    fun `an emv payload with a non-ascii merchant name round-trips exactly`() {
        val payload = "00020101021152040780" + "5303360" + "5802ID" +
            "5913" + ARABIC + "6006Bekasi" + "63041235"

        assertEquals(payload, decode(payload))
    }

    /** The common case has to be untouched by the fix, not merely still working. */
    @Test
    fun `an ascii payload is unaffected`() {
        val payload = "https://example.com/pay?id=7&amount=45000"

        assertEquals(payload, decode(payload))
    }

    @Test
    fun `reports nothing for a payload no code can hold`() {
        assertNull(encodeQrMatrix("", SIZE_PX))
        assertNull(encodeQrMatrix("x".repeat(10_000), SIZE_PX))
    }

    /** Encodes [payload] and reads it straight back, the way a scanner would. */
    private fun decode(payload: String): String? {
        val matrix = encodeQrMatrix(payload, SIZE_PX) ?: return null

        val width = matrix.width
        val height = matrix.height
        val pixels = IntArray(width * height) { index ->
            if (matrix[index % width, index / width]) BLACK else WHITE
        }

        // No CHARACTER_SET hint on the way back in, deliberately. The point is that the code says
        // what it holds, so a reader given no instruction still gets it right.
        return QRCodeReader()
            .decode(BinaryBitmap(HybridBinarizer(RGBLuminanceSource(width, height, pixels))))
            .text
    }
}
