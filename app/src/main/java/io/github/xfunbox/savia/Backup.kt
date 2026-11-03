package io.github.xfunbox.savia

import java.time.LocalDate
import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener

object Backup {
    private const val MAX_BYTES = 5 * 1024 * 1024

    fun encode(data: AppData): String {
        validateData(data)
        fun bills(values: List<FixedExpense>) = JSONArray().apply {
            values.forEach { put(JSONObject().put("id", it.id).put("name", it.name).put("amount", it.amount)) }
        }
        val s = data.settings
        val settings = JSONObject().put("salary", s.salary).put("payday", s.payday)
            .put("variableBudget", s.variableBudget).put("manualTarget", s.manualTarget)
            .put("automaticTarget", s.automaticTarget).put("currency", s.currency)
            .put("fixedExpenses", bills(s.fixedExpenses))
        val cycles = JSONArray().apply {
            data.cycles.forEach {
                put(JSONObject().put("id", it.id).put("start", it.start.toString()).put("end", it.end.toString())
                    .put("income", it.income).put("variableBudget", it.variableBudget).put("target", it.target)
                    .put("currency", it.currency).put("bills", bills(it.bills)))
            }
        }
        val movements = JSONArray().apply {
            data.movements.forEach {
                put(JSONObject().put("id", it.id).put("cycleId", it.cycleId).put("date", it.date.toString())
                    .put("amount", it.amount).put("income", it.income).put("category", it.category)
                    .put("note", it.note).put("billId", it.billId ?: JSONObject.NULL))
            }
        }
        val goals = JSONArray().apply {
            data.goals.forEach {
                put(JSONObject().put("id", it.id).put("name", it.name).put("targetAmount", it.targetAmount)
                    .put("savedAmount", it.savedAmount).put("periodAmount", it.periodAmount).put("currency", it.currency))
            }
        }
        val result = JSONObject().put("version", 2).put("settings", settings)
            .put("cycles", cycles).put("movements", movements).put("goals", goals).toString()
        checkSize(result)
        return result
    }

    fun decode(text: String): AppData {
        checkSize(text)
        try {
            val tokener = JSONTokener(text)
            val root = tokener.nextValue() as? JSONObject
                ?: throw IllegalArgumentException("La copia debe contener un objeto JSON.")
            require(tokener.nextClean() == '\u0000') { "Hay contenido adicional después de la copia." }
            val version = root.number("version")
            require(version in 1L..2L) { "Versión de copia no admitida." }
            val s = root.getJSONObject("settings")
            val settings = Settings(s.number("salary"), s.number("payday").also {
                require(it in 1..31) { "El día de cobro debe estar entre 1 y 31." }
            }.toInt(), s.number("variableBudget"), s.number("manualTarget"),
                s.boolean("automaticTarget"), s.string("currency"), readBills(s.getJSONArray("fixedExpenses")))
            val cycles = root.getJSONArray("cycles").objects(1000).map {
                Cycle(it.string("id"), LocalDate.parse(it.string("start")), LocalDate.parse(it.string("end")),
                    it.number("income"), it.number("variableBudget"), it.number("target"),
                    it.string("currency"), readBills(it.getJSONArray("bills")))
            }
            val movements = root.getJSONArray("movements").objects(50000).map {
                require(it.has("billId")) { "Falta el campo billId." }
                Movement(it.string("id"), it.string("cycleId"), LocalDate.parse(it.string("date")),
                    it.number("amount"), it.boolean("income"), it.string("category"), it.string("note"),
                    if (it.isNull("billId")) null else it.string("billId"))
            }
            val goals = if (version == 1L) emptyList() else root.getJSONArray("goals").objects(100).map {
                SavingsGoal(it.string("id"), it.string("name"), it.number("targetAmount"),
                    it.number("savedAmount"), it.number("periodAmount"), it.string("currency"))
            }
            return AppData(settings, cycles, movements, goals).also(::validateData)
        } catch (e: IllegalArgumentException) {
            throw e
        } catch (e: Exception) {
            throw IllegalArgumentException("Copia inválida: comprueba el formato JSON, sus campos y fechas.", e)
        }
    }

    private fun checkSize(text: String) {
        require(text.length <= MAX_BYTES && text.toByteArray(Charsets.UTF_8).size <= MAX_BYTES) {
            "La copia supera el límite de 5 MiB."
        }
    }

    private fun readBills(array: JSONArray): List<FixedExpense> = array.objects(100).map {
        FixedExpense(it.string("id"), it.string("name"), it.number("amount"))
    }

    private fun JSONArray.objects(max: Int): List<JSONObject> {
        require(length() <= max) { "La copia contiene demasiados elementos (máximo $max)." }
        return List(length()) { getJSONObject(it) }
    }

    private fun JSONObject.number(key: String): Long {
        val value = get(key)
        require(value is Number && value !is Double && value !is Float) { "El campo $key debe ser un entero exacto." }
        return value.toString().toLongOrNull()
            ?: throw IllegalArgumentException("El campo $key está fuera de límite o no es un entero.")
    }

    private fun JSONObject.string(key: String): String = (get(key) as? String)
        ?: throw IllegalArgumentException("El campo $key debe ser texto.")

    private fun JSONObject.boolean(key: String): Boolean = (get(key) as? Boolean)
        ?: throw IllegalArgumentException("El campo $key debe ser verdadero o falso.")
}
