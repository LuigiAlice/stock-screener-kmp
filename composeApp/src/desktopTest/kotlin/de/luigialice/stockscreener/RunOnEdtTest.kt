package de.luigialice.stockscreener

import javax.swing.SwingUtilities
import kotlin.test.Test
import kotlin.test.assertEquals

class RunOnEdtTest {

    @Test
    fun executesBlockDirectlyFromNonEdtThread() {
        assertEquals(42, runOnEdt { 42 })
    }

    @Test
    fun executesBlockFromEdtThreadWithoutInvokeAndWait() {
        var result: Int? = null
        SwingUtilities.invokeAndWait { result = runOnEdt { 7 } }
        assertEquals(7, result)
    }
}
