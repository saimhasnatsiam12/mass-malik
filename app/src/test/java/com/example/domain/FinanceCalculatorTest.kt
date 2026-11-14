package com.example.domain

import com.example.data.model.Adjustment
import com.example.data.model.Deposit
import com.example.data.model.Expense
import com.example.data.model.ExpenseCategory
import com.example.data.model.MealEntry
import com.example.data.model.MessMember
import org.junit.Assert.assertEquals
import org.junit.Test
import java.math.BigDecimal

class FinanceCalculatorTest {

    @Test
    fun `computeMonthlySummary calculates exact BigDecimal meal rate and member balances`() {
        val members = listOf(
            MessMember(userId = "u1", displayName = "Amina", role = "owner", status = "active"),
            MessMember(userId = "u2", displayName = "Rahim", role = "member", status = "active"),
        )
        val meals = listOf(
            MealEntry(memberUid = "u1", memberName = "Amina", date = "2026-11-14", breakfast = 1.0, lunch = 1.0, dinner = 1.0, totalMeals = 3.0),
            MealEntry(memberUid = "u2", memberName = "Rahim", date = "2026-11-14", breakfast = 0.0, lunch = 1.0, dinner = 1.0, totalMeals = 2.0),
        )
        val expenses = listOf(
            // Approved bazar: 250.00 -> Meal Rate = 250.00 / 5 = 50.00
            Expense(category = ExpenseCategory.BAZAR.key, amountPaisa = 25000L, status = "approved"),
            // Pending bazar must NOT be counted yet
            Expense(category = ExpenseCategory.BAZAR.key, amountPaisa = 99900L, status = "pending"),
            // Shared utility: 100.00 -> 50.00 each
            Expense(category = ExpenseCategory.SHARED_FIXED.key, amountPaisa = 10000L, status = "approved"),
            // Individual charge for u2: 20.00
            Expense(category = ExpenseCategory.OTHER_INDIVIDUAL.key, amountPaisa = 2000L, targetMemberUid = "u2", status = "approved"),
        )
        val deposits = listOf(
            Deposit(memberUid = "u1", amountPaisa = 30000L), // 300.00
            Deposit(memberUid = "u2", amountPaisa = 10000L), // 100.00
        )
        val adjustments = listOf(
            Adjustment(memberUid = "u2", amountPaisa = 500L, reason = "Guest refund"), // +5.00
        )

        val summary = FinanceCalculator.computeMonthlySummary(
            monthKey = "2026-11",
            todayDate = "2026-11-14",
            members = members,
            meals = meals,
            expenses = expenses,
            deposits = deposits,
            adjustments = adjustments,
        )

        assertEquals(0, summary.totalMeals.compareTo(BigDecimal("5")))
        assertEquals("50.00", summary.mealRateDisplay)
        assertEquals(25000L, summary.totalMealExpensePaisa)
        assertEquals(1, summary.pendingExpenseCount)

        val u1 = summary.memberSummaries.first { it.memberUid == "u1" }
        // u1: 300.00 - (3 * 50.00) - 50.00 = +100.00 (10000 paisa)
        assertEquals(15000L, u1.mealCostPaisa)
        assertEquals(5000L, u1.sharedFixedSharePaisa)
        assertEquals(10000L, u1.netBalancePaisa)

        val u2 = summary.memberSummaries.first { it.memberUid == "u2" }
        // u2: 100.00 - (2 * 50.00) - 50.00 - 20.00 + 5.00 = -65.00 (-6500 paisa due)
        assertEquals(10000L, u2.mealCostPaisa)
        assertEquals(-6500L, u2.netBalancePaisa)
    }
}
