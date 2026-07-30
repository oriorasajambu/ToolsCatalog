package com.minion.scaffold.core.emv.parser

import com.minion.scaffold.core.emv.EmvSamples
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

internal class EmvCrc16Test {

    /**
     * The published check value for CRC-16/CCITT-FALSE.
     *
     * This is the test that pins the variant down. XMODEM, KERMIT, GENIBUS and MCRF4XX all share
     * the `0x1021` polynomial and all produce a four-digit result here; only CCITT-FALSE produces
     * `29B1`. Without this assertion, swapping in the wrong variant would still pass every
     * payload-shaped test that compares the implementation against itself.
     */
    @Test
    fun `matches the standard CCITT-FALSE check vector`() {
        assertEquals("29B1", EmvCrc16.compute("123456789"))
    }

    @Test
    fun `initial value is returned for empty input`() {
        assertEquals("FFFF", EmvCrc16.compute(""))
    }

    @Test
    fun `computes the checksum of a live payload over the range excluding the checksum itself`() {
        val checksummedRange = EmvSamples.QRIS_DYNAMIC.dropLast(4)

        assertEquals("3D58", EmvCrc16.compute(checksummedRange))
    }

    /**
     * The range must include tag 63's own `6304` header. Stopping short of it is the classic
     * implementation error: the result is stable and looks like a checksum, but never matches a
     * payload produced by anyone else.
     */
    @Test
    fun `excluding the checksum tag header produces a different value`() {
        val withoutCrcHeader = EmvSamples.QRIS_DYNAMIC.dropLast(8)

        assertEquals("3D58", EmvCrc16.compute(EmvSamples.QRIS_DYNAMIC.dropLast(4)))
        assertNotEquals("3D58", EmvCrc16.compute(withoutCrcHeader))
    }

    @Test
    fun `output is always four uppercase hexadecimal characters`() {
        val checksum = EmvCrc16.compute("a")

        assertEquals(4, checksum.length)
        assertEquals(checksum.uppercase(), checksum)
    }
}
