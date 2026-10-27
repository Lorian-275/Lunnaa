package com.lorian.lunna

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class UtilTest {
    @Test
    fun usuarioSeConvierteEnCorreo() {
        assertEquals("allison@lunnaa.local", emailFor("  Allison "))
        assertEquals("ana.m-2@lunnaa.local", emailFor("ana.m-2"))
        assertNull(emailFor(""))
        assertNull(emailFor("ana@x.com"))
        assertNull(emailFor("ana maria"))
    }

    @Test
    fun busquedaSinCaracteresQueRompenElFiltro() {
        assertEquals("a  b", cleanQuery("a,(b)"))
        assertEquals("100", cleanQuery("100%"))
        assertEquals("", cleanQuery("  *_ "))
    }

    @Test
    fun formatoDeTiempo() {
        assertEquals("0:00", fmtTime(0))
        assertEquals("3:05", fmtTime(185_000))
        assertEquals("61:01", fmtTime(3_661_000))
    }
}
