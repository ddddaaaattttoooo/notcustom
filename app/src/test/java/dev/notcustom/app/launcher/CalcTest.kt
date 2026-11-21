package dev.notcustom.app.launcher

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CalcTest {
    @Test
    fun evaluates() {
        assertEquals(14.0, calculate("2+3*4")!!, 0.0)
        assertEquals(20.0, calculate("(2+3)*4")!!, 0.0)
        assertEquals(2.5, calculate("5 / 2")!!, 0.0)
        assertEquals(-3.0, calculate("-1-2")!!, 0.0)
        assertEquals(6.0, calculate("2x3")!!, 0.0)
        assertEquals(1.5, calculate("1,5*1")!!, 0.0)
    }

    @Test
    fun ignoresNonMath() {
        assertNull(calculate("7"))
        assertNull(calculate("chrome"))
        assertNull(calculate("2+"))
        assertNull(calculate("(2+3"))
        assertNull(calculate("1/0"))
    }

    @Test
    fun formats() {
        assertEquals("12", formatResult(12.0))
        assertEquals("0.33333333", formatResult(1.0 / 3))
    }
}
