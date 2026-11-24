package rs.snoreguard.mvp

import org.junit.Assert.*
import org.junit.Test

class NightStatsTest {
    @Test fun hoursCrossMidnightChronologically() {
        assertEquals(listOf(22, 23, 0, 1, 2, 3, 4, 5, 6, 7), NightHours.orderedHours)
        assertEquals(listOf("22", "23", "00", "01", "02", "03", "04", "05", "06", "07"), NightHours.labels())
        assertEquals(0, NightHours.indexOf(22))
        assertEquals(2, NightHours.indexOf(0))
        assertEquals(9, NightHours.indexOf(7))
        assertEquals(-1, NightHours.indexOf(8))
        assertEquals(-1, NightHours.indexOf(21))
    }

    @Test fun csvEscapesQuotesCommasLineBreaksAndFormulas() {
        assertEquals("\"a,b\"", CsvCell.encode("a,b"))
        assertEquals("\"a\"\"b\"", CsvCell.encode("a\"b"))
        assertEquals("\"line\nnext\"", CsvCell.encode("line\nnext"))
        assertEquals("\"a\",\"b\"", CsvCell.row(listOf("a", "b")))
        assertEquals("\"'=2+2\"", CsvCell.encode("=2+2"))
    }
}
