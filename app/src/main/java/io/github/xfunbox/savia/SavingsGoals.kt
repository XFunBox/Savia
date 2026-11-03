package io.github.xfunbox.savia

data class SavingsGoal(
    val id: String = "", val name: String = "", val targetAmount: Long = 0,
    val savedAmount: Long = 0, val periodAmount: Long = 0, val currency: String = "EUR"
)
