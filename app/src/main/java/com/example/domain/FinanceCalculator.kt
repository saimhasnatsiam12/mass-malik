package com.example.domain

import com.example.data.model.Adjustment
import com.example.data.model.Deposit
import com.example.data.model.Expense
import com.example.data.model.ExpenseCategory
import com.example.data.model.MealEntry
import com.example.data.model.MessMember
import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale

data class MemberFinanceSummary(
    val memberUid: String,
    val displayName: String,
    val role: String,
    val status: String,
    val breakfastCount: BigDecimal,
    val lunchCount: BigDecimal,
    val dinnerCount: BigDecimal,
    val totalMeals: BigDecimal,
    val mealCostPaisa: Long,
    val sharedFixedSharePaisa: Long,
    val otherIndividualPaisa: Long,
    val adjustmentPaisa: Long,
    val depositPaisa: Long,
    val netBalancePaisa: Long,
)

data class MonthlyFinanceSummary(
    val monthKey: String,
    val totalMeals: BigDecimal,
    val todayMeals: BigDecimal,
    val totalMealExpensePaisa: Long,
    val totalSharedFixedPaisa: Long,
    val totalOtherIndividualPaisa: Long,
    val totalExpensePaisa: Long,
    val pendingExpenseCount: Int,
    val totalDepositPaisa: Long,
    val totalAdjustmentPaisa: Long,
    val remainingCashPaisa: Long,
    val mealRateExact: BigDecimal,
    val mealRateDisplay: String,
    val memberSummaries: List<MemberFinanceSummary>,
)

/**
 * Exact decimal financial calculation engine.
 * All monetary amounts use integer minor units (Long paisa) and BigDecimal with HALF_UP rounding.
 * Floating-point arithmetic is never used for currency calculations.
 */
object FinanceCalculator {

    private val HUNDRED = BigDecimal(100)
    private val PRECISION = MathContext(16, RoundingMode.HALF_UP)

    fun paisaToMajor(paisa: Long): BigDecimal =
        BigDecimal.valueOf(paisa).divide(HUNDRED, 2, RoundingMode.HALF_UP)

    fun parseMajorToPaisa(input: String): Long? {
        val cleaned = input.trim().replace(",", "")
        if (cleaned.isEmpty()) return null
        return try {
            val bd = BigDecimal(cleaned)
            bd.multiply(HUNDRED).setScale(0, RoundingMode.HALF_UP).longValueExact()
        } catch (_: Exception) {
            null
        }
    }

    fun formatMoney(paisa: Long, currencySymbol: String): String {
        val absMajor = paisaToMajor(kotlin.math.abs(paisa))
        val symbols = DecimalFormatSymbols(Locale.US)
        val formatter = DecimalFormat("#,##0.00", symbols)
        val prefix = if (paisa < 0) "-" else ""
        return "$prefix$currencySymbol${formatter.format(absMajor)}"
    }

    fun formatDecimal(value: BigDecimal, decimals: Int = 2): String =
        value.setScale(decimals, RoundingMode.HALF_UP).toPlainString()

    fun formatMealCount(value: BigDecimal): String {
        val stripped = value.stripTrailingZeros()
        return if (stripped.scale() <= 0) {
            stripped.setScale(0, RoundingMode.HALF_UP).toPlainString()
        } else {
            stripped.toPlainString()
        }
    }

    fun computeMonthlySummary(
        monthKey: String,
        todayDate: String,
        members: List<MessMember>,
        meals: List<MealEntry>,
        expenses: List<Expense>,
        deposits: List<Deposit>,
        adjustments: List<Adjustment>,
    ): MonthlyFinanceSummary {
        val approvedExpenses = expenses.filter { it.status == "approved" }
        val pendingCount = expenses.count { it.status == "pending" }

        val totalMealExpensePaisa = approvedExpenses
            .filter { it.category == ExpenseCategory.BAZAR.key }
            .sumOf { it.amountPaisa }

        val totalSharedFixedPaisa = approvedExpenses
            .filter { it.category == ExpenseCategory.SHARED_FIXED.key }
            .sumOf { it.amountPaisa }

        val otherIndividualExpenses = approvedExpenses
            .filter { it.category == ExpenseCategory.OTHER_INDIVIDUAL.key }
        val totalOtherIndividualPaisa = otherIndividualExpenses.sumOf { it.amountPaisa }

        val totalExpensePaisa = totalMealExpensePaisa + totalSharedFixedPaisa + totalOtherIndividualPaisa
        val totalDepositPaisa = deposits.sumOf { it.amountPaisa }
        val totalAdjustmentPaisa = adjustments.sumOf { it.amountPaisa }
        val remainingCashPaisa = totalDepositPaisa - totalExpensePaisa

        var totalMeals = BigDecimal.ZERO
        var todayMeals = BigDecimal.ZERO

        for (entry in meals) {
            val entryTotal = BigDecimal.valueOf(entry.breakfast)
                .add(BigDecimal.valueOf(entry.lunch))
                .add(BigDecimal.valueOf(entry.dinner))
            totalMeals = totalMeals.add(entryTotal)
            if (entry.date == todayDate) {
                todayMeals = todayMeals.add(entryTotal)
            }
        }

        // Meal Rate = Total Meal Expenses / Total Meals
        val mealRateExact = if (totalMeals.compareTo(BigDecimal.ZERO) > 0) {
            paisaToMajor(totalMealExpensePaisa).divide(totalMeals, 6, RoundingMode.HALF_UP)
        } else {
            BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP)
        }
        val mealRateDisplay = mealRateExact.setScale(2, RoundingMode.HALF_UP).toPlainString()

        val activeMembers = members.filter { it.status == "active" }
        val sharedSplitCount = if (activeMembers.isNotEmpty()) activeMembers.size else members.size.coerceAtLeast(1)
        val perMemberSharedFixedPaisa = if (totalSharedFixedPaisa > 0L) {
            BigDecimal.valueOf(totalSharedFixedPaisa)
                .divide(BigDecimal.valueOf(sharedSplitCount.toLong()), 0, RoundingMode.HALF_UP)
                .longValueExact()
        } else {
            0L
        }

        // Also include any uid that appears in meals/deposits/expenses even if removed, so history reconciles
        val memberMap = members.associateBy { it.userId }.toMutableMap()
        val allUids = linkedSetOf<String>().apply {
            members.forEach { add(it.userId) }
            meals.forEach { add(it.memberUid) }
            deposits.forEach { add(it.memberUid) }
            adjustments.forEach { add(it.memberUid) }
            otherIndividualExpenses.forEach { exp -> exp.targetMemberUid?.takeIf { it.isNotBlank() }?.let { add(it) } }
        }

        val summaries = allUids.map { uid ->
            val member = memberMap[uid]
            val memberMeals = meals.filter { it.memberUid == uid }
            val breakfastSum = memberMeals.fold(BigDecimal.ZERO) { acc, m -> acc.add(BigDecimal.valueOf(m.breakfast)) }
            val lunchSum = memberMeals.fold(BigDecimal.ZERO) { acc, m -> acc.add(BigDecimal.valueOf(m.lunch)) }
            val dinnerSum = memberMeals.fold(BigDecimal.ZERO) { acc, m -> acc.add(BigDecimal.valueOf(m.dinner)) }
            val memberTotalMeals = breakfastSum.add(lunchSum).add(dinnerSum)

            // Individual Meal Cost = Member Meals * Meal Rate (computed in paisa with HALF_UP)
            val mealCostMajor = memberTotalMeals.multiply(mealRateExact, PRECISION)
            val mealCostPaisa = mealCostMajor.multiply(HUNDRED).setScale(0, RoundingMode.HALF_UP).longValueExact()

            val sharedShare = if (member?.status == "active" || activeMembers.isEmpty()) perMemberSharedFixedPaisa else 0L
            val individualOther = otherIndividualExpenses
                .filter { it.targetMemberUid == uid }
                .sumOf { it.amountPaisa }
            val memberAdj = adjustments
                .filter { it.memberUid == uid }
                .sumOf { it.amountPaisa }
            val memberDeposit = deposits
                .filter { it.memberUid == uid }
                .sumOf { it.amountPaisa }

            // Member Balance = Total Deposit - Individual Meal Cost - Shared Utility - Other Individual Expenses + Adjustments
            val netBalance = memberDeposit - mealCostPaisa - sharedShare - individualOther + memberAdj

            MemberFinanceSummary(
                memberUid = uid,
                displayName = member?.displayName
                    ?: memberMeals.firstOrNull()?.memberName
                    ?: deposits.firstOrNull { it.memberUid == uid }?.memberName
                    ?: "Member",
                role = member?.role ?: "member",
                status = member?.status ?: "inactive",
                breakfastCount = breakfastSum,
                lunchCount = lunchSum,
                dinnerCount = dinnerSum,
                totalMeals = memberTotalMeals,
                mealCostPaisa = mealCostPaisa,
                sharedFixedSharePaisa = sharedShare,
                otherIndividualPaisa = individualOther,
                adjustmentPaisa = memberAdj,
                depositPaisa = memberDeposit,
                netBalancePaisa = netBalance,
            )
        }.sortedByDescending { it.netBalancePaisa }

        return MonthlyFinanceSummary(
            monthKey = monthKey,
            totalMeals = totalMeals,
            todayMeals = todayMeals,
            totalMealExpensePaisa = totalMealExpensePaisa,
            totalSharedFixedPaisa = totalSharedFixedPaisa,
            totalOtherIndividualPaisa = totalOtherIndividualPaisa,
            totalExpensePaisa = totalExpensePaisa,
            pendingExpenseCount = pendingCount,
            totalDepositPaisa = totalDepositPaisa,
            totalAdjustmentPaisa = totalAdjustmentPaisa,
            remainingCashPaisa = remainingCashPaisa,
            mealRateExact = mealRateExact,
            mealRateDisplay = mealRateDisplay,
            memberSummaries = summaries,
        )
    }
}
