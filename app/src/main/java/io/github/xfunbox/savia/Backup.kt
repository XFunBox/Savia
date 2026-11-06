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
            values.forEach { put(JSONObject().put("id", it.id).put("name", it.name).put("amount", it.amount)
                .put("dueDay", it.dueDay ?: JSONObject.NULL).put("dueMonth", it.dueMonth ?: JSONObject.NULL)
                .put("dueDate", it.dueDate?.toString() ?: JSONObject.NULL)) }
        }
        fun limits(values: Map<String, Long>) = JSONObject().apply { values.forEach { (key, value) -> put(key, value) } }
        val s = data.settings
        val settings = JSONObject().put("salary", s.salary).put("payday", s.payday)
            .put("variableBudget", s.variableBudget).put("manualTarget", s.manualTarget)
            .put("automaticTarget", s.automaticTarget).put("currency", s.currency)
            .put("fixedExpenses", bills(s.fixedExpenses)).put("categoryLimits", limits(s.categoryLimits))
        val cycles = JSONArray().apply {
            data.cycles.forEach {
                put(JSONObject().put("id", it.id).put("start", it.start.toString()).put("end", it.end.toString())
                    .put("income", it.income).put("variableBudget", it.variableBudget).put("target", it.target)
                    .put("currency", it.currency).put("bills", bills(it.bills)).put("categoryLimits", limits(it.categoryLimits)))
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
            data.goals.forEach { goal ->
                val entries = JSONArray().apply {
                    goal.entries.forEach {
                        put(JSONObject().put("id", it.id).put("date", it.date.toString()).put("amount", it.amount)
                            .put("withdrawal", it.withdrawal).put("note", it.note)
                            .put("cycleId", it.cycleId ?: JSONObject.NULL).put("additional", it.additional))
                    }
                }
                put(JSONObject().put("id", goal.id).put("name", goal.name).put("targetAmount", goal.targetAmount)
                    .put("savedAmount", goal.savedAmount).put("periodAmount", goal.periodAmount).put("currency", goal.currency)
                    .put("entries", entries).put("targetDate", goal.targetDate?.toString() ?: JSONObject.NULL))
            }
        }
        val closures = JSONArray().apply {
            data.closures.forEach { put(JSONObject().put("cycleId", it.cycleId).put("reviewedOn", it.reviewedOn.toString())
                .put("fingerprint", it.fingerprint).put("entryIds", JSONArray(it.entryIds))) }
        }
        val result = JSONObject().put("version", 4).put("settings", settings)
            .put("cycles", cycles).put("movements", movements).put("goals", goals).put("closures", closures).toString()
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
            require(version in 1L..4L) { "Versión de copia no admitida." }
            val s = root.getJSONObject("settings")
            val settings = Settings(s.number("salary"), s.number("payday").also {
                require(it in 1..31) { "El día de cobro debe estar entre 1 y 31." }
            }.toInt(), s.number("variableBudget"), s.number("manualTarget"),
                s.boolean("automaticTarget"), s.string("currency"), readBills(s.getJSONArray("fixedExpenses"), version),
                if (version >= 4) readLimits(s.getJSONObject("categoryLimits")) else emptyMap())
            val cycles = root.getJSONArray("cycles").objects(1000).map {
                Cycle(it.string("id"), LocalDate.parse(it.string("start")), LocalDate.parse(it.string("end")),
                    it.number("income"), it.number("variableBudget"), it.number("target"),
                    it.string("currency"), readBills(it.getJSONArray("bills"), version),
                    if (version >= 4) readLimits(it.getJSONObject("categoryLimits")) else emptyMap())
            }
            val movements = root.getJSONArray("movements").objects(50000).map {
                require(it.has("billId")) { "Falta el campo billId." }
                Movement(it.string("id"), it.string("cycleId"), LocalDate.parse(it.string("date")),
                    it.number("amount"), it.boolean("income"), it.string("category"), it.string("note"),
                    if (it.isNull("billId")) null else it.string("billId"))
            }
            val goals = if (version == 1L) emptyList() else root.getJSONArray("goals").objects(100).map {
                val entries = if (version < 3L) emptyList() else it.getJSONArray("entries").objects(50000).map { entry ->
                    require(entry.has("cycleId")) { "Falta el campo cycleId." }
                    SavingsEntry(entry.string("id"), LocalDate.parse(entry.string("date")), entry.number("amount"),
                        entry.boolean("withdrawal"), entry.string("note"),
                        if (entry.isNull("cycleId")) null else entry.string("cycleId"), entry.boolean("additional"))
                }
                if (version >= 3L) require(it.has("targetDate")) { "Falta el campo targetDate." }
                val targetDate = if (version < 3L || it.isNull("targetDate")) null else LocalDate.parse(it.string("targetDate"))
                SavingsGoal(it.string("id"), it.string("name"), it.number("targetAmount"),
                    it.number("savedAmount"), it.number("periodAmount"), it.string("currency"), entries, targetDate)
            }
            val closures = if (version < 4) emptyList() else root.getJSONArray("closures").objects(1000).map {
                val ids = it.getJSONArray("entryIds")
                require(ids.length() <= 200) { "Demasiadas entradas en el cierre." }
                PeriodClosure(it.string("cycleId"), LocalDate.parse(it.string("reviewedOn")), it.string("fingerprint"),
                    List(ids.length()) { index -> (ids.get(index) as? String) ?: error("Identificador de cierre inválido.") })
            }
            return AppData(settings, cycles, movements, goals, closures).also(::validateData)
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

    private fun readBills(array: JSONArray, version: Long): List<FixedExpense> = array.objects(100).map {
        fun optionalInt(key: String, range: IntRange): Int? {
            if (version < 4) return null
            require(it.has(key)) { "Falta el campo $key." }
            if (it.isNull(key)) return null
            val value = it.number(key)
            require(value in range.first.toLong()..range.last.toLong()) { "Campo $key fuera de límite." }
            return value.toInt()
        }
        if (version >= 4) require(it.has("dueDate")) { "Falta el campo dueDate." }
        FixedExpense(it.string("id"), it.string("name"), it.number("amount"), optionalInt("dueDay", 1..31),
            optionalInt("dueMonth", 1..12), if (version < 4 || it.isNull("dueDate")) null else LocalDate.parse(it.string("dueDate")))
    }

    private fun readLimits(value: JSONObject): Map<String, Long> {
        require(value.length() <= 100) { "Demasiados límites por categoría." }
        return value.keys().asSequence().associateWith { value.number(it) }
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
