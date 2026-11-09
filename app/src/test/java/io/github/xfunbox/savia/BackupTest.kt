package io.github.xfunbox.savia

import java.time.LocalDate
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class BackupTest {
    @Test fun roundTripPreservesAllFinancialData() {
        val bill = FixedExpense("rent", "Alquiler", 50000)
        val start = LocalDate.of(2024, 2, 1)
        val cycle = Cycle("cycle", start, start.plusMonths(1), 100000, 20000, 30000, "EUR", listOf(bill))
        val data = AppData(Settings(salary = 12345, fixedExpenses = listOf(bill)), listOf(cycle),
            listOf(Movement("m", cycle.id, start, bill.amount, category = "Fijos", note = "Pagado", billId = bill.id)),
            listOf(SavingsGoal("trip", "Viaje", 123456, 23456, 1234, "USD"),
                SavingsGoal("done", "Completado", 1, MAX_MONEY, 0, "EUR")))
        val encoded = Backup.encode(data)
        assertEquals(5, JSONObject(encoded).getInt("version"))
        assertEquals(data, Backup.decode(encoded))
    }

    @Test fun rejectsMalformedUnknownOversizedAndLossyNumbers() {
        val valid = Backup.encode(AppData())
        listOf("not json", JSONObject(valid).put("version", 6).toString(), " ".repeat(5 * 1024 * 1024 + 1),
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

    @Test fun versionOnePreservesAmountsSettingsAndMovementsWithNoGoals() {
        val legacy = """{"version":1,"settings":{"salary":12345,"payday":31,"variableBudget":2345,
            "manualTarget":6789,"automaticTarget":false,"currency":"GBP","fixedExpenses":[]},
            "cycles":[{"id":"old","start":"2024-02-01","end":"2024-03-01","income":12345,
            "variableBudget":2345,"target":6789,"currency":"MXN","bills":[]}],
            "movements":[{"id":"expense","cycleId":"old","date":"2024-02-02","amount":321,
            "income":false,"category":"Otros","note":"Anterior","billId":null}]}"""
        val result = Backup.decode(legacy)
        val start = LocalDate.of(2024, 2, 1)
        assertEquals(AppData(Settings(12345, 31, 2345, 6789, false, "GBP"),
            listOf(Cycle("old", start, start.plusMonths(1), 12345, 2345, 6789, "MXN")),
            listOf(Movement("expense", "old", start.plusDays(1), 321, note = "Anterior"))), result)
        assertTrue(result.goals.isEmpty())
        assertEquals(result, Backup.decode(Backup.encode(result)))
    }

    @Test fun versionTwoRequiresGoalsAndEveryGoalFieldWithExactTypes() {
        val valid = JSONObject(Backup.encode(AppData(goals = listOf(SavingsGoal("g", "Meta", 1000, 2000, 300, "EUR")))))
            .put("version", 2).toString()
        val missingGoals = JSONObject(valid).apply { remove("goals") }.toString()
        val invalid = mutableListOf(missingGoals,
            JSONObject(valid).put("goals", JSONObject.NULL).toString(),
            JSONObject(valid).put("goals", JSONObject()).toString(),
            JSONObject(valid).put("goals", JSONArray().put(1)).toString())
        listOf("id", "name", "targetAmount", "savedAmount", "periodAmount", "currency").forEach { field ->
            invalid += JSONObject(valid).apply { getJSONArray("goals").getJSONObject(0).remove(field) }.toString()
        }
        listOf("targetAmount", "savedAmount", "periodAmount").forEach { field ->
            listOf<Any>("100", 1.5, JSONObject.NULL, -1L, MAX_MONEY + 1).forEach { value ->
                invalid += JSONObject(valid).apply { getJSONArray("goals").getJSONObject(0).put(field, value) }.toString()
            }
        }
        listOf("id" to "", "name" to " ", "currency" to "JPY", "targetAmount" to 0,
            "id" to 123, "name" to true, "currency" to JSONObject.NULL).forEach { (field, value) ->
            invalid += JSONObject(valid).apply { getJSONArray("goals").getJSONObject(0).put(field, value) }.toString()
        }
        invalid.forEach { assertThrows(IllegalArgumentException::class.java) { Backup.decode(it) } }
    }

    @Test fun versionTwoRejectsDuplicateAndExcessGoals() {
        val root = JSONObject(Backup.encode(AppData(goals = listOf(SavingsGoal("g", "Meta", 1))))).put("version", 2)
        val goal = root.getJSONArray("goals").getJSONObject(0)
        root.getJSONArray("goals").put(JSONObject(goal.toString()))
        assertThrows(IllegalArgumentException::class.java) { Backup.decode(root.toString()) }
        root.put("goals", JSONArray().apply {
            repeat(101) { put(JSONObject(goal.toString()).put("id", "goal-$it")) }
        })
        assertThrows(IllegalArgumentException::class.java) { Backup.decode(root.toString()) }
    }

    @Test fun legacyBalanceIsUndatedAndV3PreservesHistoryAndDeadline() {
        val goal = SavingsGoal("g", "Meta", 10000, 2000, 300)
        val legacy = JSONObject(Backup.encode(AppData(goals = listOf(goal)))).put("version", 2)
        legacy.getJSONArray("goals").getJSONObject(0).apply { remove("entries"); remove("targetDate") }
        val migrated = Backup.decode(legacy.toString())
        assertEquals(goal, migrated.goals.single())
        assertTrue(migrated.goals.single().entries.isEmpty())
        val date = LocalDate.of(2024, 2, 29)
        val cycle = Cycle("c", date, date.plusMonths(1), 10000)
        val entries = listOf(SavingsEntry("in", date, 1000, note = "Confirmado", cycleId = "c", additional = true),
            SavingsEntry("out", date.plusDays(1), 500, withdrawal = true, note = "Retirada"))
        val data = AppData(cycles = listOf(cycle), goals = listOf(goal.copy(entries = entries, targetDate = date.plusMonths(6))))
        assertEquals(data, Backup.decode(Backup.encode(data)))
        assertEquals(migrated, Backup.decode(Backup.encode(migrated)))
    }

    @Test fun versionThreeRejectsMissingLossyOrInvalidHistoryFields() {
        val date = LocalDate.of(2026, 1, 1)
        val valid = Backup.encode(AppData(goals = listOf(SavingsGoal("g", "Meta", 10000,
            entries = listOf(SavingsEntry("e", date, 100)), targetDate = date.plusYears(1)))))
        val invalid = mutableListOf<String>()
        listOf("entries", "targetDate").forEach { field ->
            invalid += JSONObject(valid).apply { getJSONArray("goals").getJSONObject(0).remove(field) }.toString()
        }
        listOf("id", "date", "amount", "withdrawal", "note", "cycleId", "additional").forEach { field ->
            invalid += JSONObject(valid).apply {
                getJSONArray("goals").getJSONObject(0).getJSONArray("entries").getJSONObject(0).remove(field)
            }.toString()
        }
        listOf("amount" to 1.5, "amount" to "100", "date" to "2026-02-30", "date" to 123,
            "withdrawal" to "false", "additional" to 1, "note" to true, "cycleId" to 123,
            "cycleId" to "missing", "withdrawal" to true, "amount" to 0).forEach { (field, value) ->
            invalid += JSONObject(valid).apply {
                getJSONArray("goals").getJSONObject(0).getJSONArray("entries").getJSONObject(0).put(field, value)
            }.toString()
        }
        listOf<Any>("2026-02-30", 123, "10000-01-01").forEach { value ->
            invalid += JSONObject(valid).apply { getJSONArray("goals").getJSONObject(0).put("targetDate", value) }.toString()
        }
        invalid.forEach { assertThrows(IllegalArgumentException::class.java) { Backup.decode(it) } }
    }

    @Test fun versionFivePreservesIncomeAllocationsAndRejectsBrokenLinks() {
        val date = LocalDate.of(2026, 1, 1)
        val cycle = Cycle("c", date, date.plusMonths(1), income = 10000)
        val movement = Movement("m", cycle.id, date, 5000, income = true, category = "Venta")
        val entry = SavingsEntry("e", date, 2000, note = "Asignado desde ingreso extra",
            cycleId = cycle.id, additional = true, sourceMovementId = movement.id)
        val data = AppData(cycles = listOf(cycle), movements = listOf(movement),
            goals = listOf(SavingsGoal("g", "Meta", 10000, entries = listOf(entry))))
        val encoded = Backup.encode(data)
        assertEquals(data, Backup.decode(encoded))

        val missingLink = JSONObject(encoded).apply {
            getJSONArray("goals").getJSONObject(0).getJSONArray("entries").getJSONObject(0).remove("sourceMovementId")
        }.toString()
        assertThrows(IllegalArgumentException::class.java) { Backup.decode(missingLink) }
        assertThrows(IllegalArgumentException::class.java) {
            validateData(data.copy(movements = listOf(movement.copy(amount = 1000))))
        }
        val versionFour = JSONObject(encoded).put("version", 4).apply {
            getJSONArray("goals").getJSONObject(0).getJSONArray("entries").getJSONObject(0).remove("sourceMovementId")
        }
        assertNull(Backup.decode(versionFour.toString()).goals.single().entries.single().sourceMovementId)
    }

    @Test fun versionFivePreservesSchedulesLimitsAndRejectsLossyNewFields() {
        val start = LocalDate.parse("2026-01-01")
        val settings = Settings(salary = 100000, fixedExpenses = listOf(FixedExpense("b", "Seguro", 10000, 31, 1)),
            categoryLimits = mapOf("Comida" to 10000))
        val data = AppData(settings, listOf(newCycle(settings, start, start.plusMonths(1))))
        assertEquals(data, Backup.decode(Backup.encode(data)))
        val root = JSONObject(Backup.encode(data))
        root.getJSONObject("settings").getJSONArray("fixedExpenses").getJSONObject(0).put("dueDay", 1.5)
        assertThrows(IllegalArgumentException::class.java) { Backup.decode(root.toString()) }
        val limits = JSONObject(Backup.encode(data))
        limits.getJSONObject("settings").getJSONObject("categoryLimits").put(" comida ", 1)
        assertThrows(IllegalArgumentException::class.java) { Backup.decode(limits.toString()) }
        val legacy = JSONObject(Backup.encode(data)).put("version", 3)
        val imported = Backup.decode(legacy.toString())
        assertTrue(imported.settings.categoryLimits.isEmpty())
        assertNull(imported.settings.fixedExpenses.single().dueDay)
    }
}
