package io.github.xfunbox.savia

import java.time.LocalDate
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class BackupTest {
    @Test fun roundTripPreservesAllFinancialData() {
        val bill = FixedExpense("rent", "Alquiler", 50000)
        val start = LocalDate.of(2024, 2, 1)
        val cycle = Cycle("cycle", start, start.plusMonths(1), 100000, 20000, 30000, "EUR", listOf(bill))
        val data = AppData(Settings(salary = 12345, fixedExpenses = listOf(bill)), listOf(cycle),
            listOf(Movement("m", cycle.id, start, bill.amount, category = "Fijos", note = "Pagado", billId = bill.id)))
        assertEquals(data, Backup.decode(Backup.encode(data)))
    }

    @Test fun rejectsMalformedUnknownOversizedAndLossyNumbers() {
        val valid = Backup.encode(AppData())
        listOf("not json", JSONObject(valid).put("version", 2).toString(), " ".repeat(5 * 1024 * 1024 + 1),
            JSONObject(valid).also { it.getJSONObject("settings").put("salary", 1.5) }.toString(),
            JSONObject(valid).also { it.getJSONObject("settings").put("payday", "1") }.toString(),
            JSONObject(valid).also { it.getJSONObject("settings").put("automaticTarget", "true") }.toString(),
            valid + "{}"
        ).forEach { assertThrows(IllegalArgumentException::class.java) { Backup.decode(it) } }
    }

    @Test fun decodeValidatesBeforeReturning() {
        val root = JSONObject(Backup.encode(AppData()))
        root.getJSONObject("settings").put("currency", "INVALID")
        assertThrows(IllegalArgumentException::class.java) { Backup.decode(root.toString()) }
    }
}
