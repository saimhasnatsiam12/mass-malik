package com.example.data.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

enum class MessRole(val key: String, val label: String) {
    OWNER("owner", "Mess Owner"),
    MANAGER("manager", "Mess Manager"),
    ASSISTANT_MANAGER("assistant_manager", "Assistant Manager"),
    MEMBER("member", "Regular Member");

    fun canManageFinances(): Boolean =
        this == OWNER || this == MANAGER || this == ASSISTANT_MANAGER

    fun canManageMembersAndClosing(): Boolean =
        this == OWNER || this == MANAGER

    companion object {
        fun fromKey(key: String?): MessRole =
            entries.firstOrNull { it.key == key } ?: MEMBER
    }
}

enum class ExpenseCategory(val key: String, val label: String) {
    BAZAR("bazar", "Daily Bazar (Meal)"),
    SHARED_FIXED("shared_fixed", "Shared Utility / Fixed"),
    OTHER_INDIVIDUAL("other_individual", "Individual Charge");

    companion object {
        fun fromKey(key: String?): ExpenseCategory =
            entries.firstOrNull { it.key == key } ?: BAZAR
    }
}

enum class DepositMethod(val key: String, val label: String) {
    CASH("cash", "Cash"),
    BKASH("bkash", "bKash"),
    NAGAD("nagad", "Nagad"),
    BANK("bank", "Bank Transfer"),
    OTHER("other", "Other");

    companion object {
        fun fromKey(key: String?): DepositMethod =
            entries.firstOrNull { it.key == key } ?: CASH
    }
}

@IgnoreExtraProperties
data class UserProfile(
    @DocumentId val id: String = "",
    val userId: String = "",
    val displayName: String = "",
    val email: String? = null,
    val phone: String? = null,
    val photoUrl: String? = null,
    val activeMessId: String? = null,
    val themeMode: String = "system",
    val isDeactivated: Boolean = false,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null,
) {
    fun toCreateMap(): Map<String, Any> =
        mapOf(
            "userId" to userId,
            "displayName" to displayName,
            "email" to email,
            "phone" to phone,
            "photoUrl" to photoUrl,
            "activeMessId" to activeMessId,
            "themeMode" to themeMode,
            "isDeactivated" to isDeactivated,
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp(),
        ).filterValues { it != null } as Map<String, Any>
}

@IgnoreExtraProperties
data class MessInvite(
    @DocumentId val id: String = "",
    val messId: String = "",
    val messName: String = "",
    val messAddress: String? = null,
    val ownerId: String = "",
    val managerUids: List<String> = emptyList(),
    val inviteCode: String = "",
    val isActive: Boolean = true,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null,
)

@IgnoreExtraProperties
data class Mess(
    @DocumentId val id: String = "",
    val name: String = "",
    val address: String? = null,
    val description: String? = null,
    val currencySymbol: String = "৳",
    val ownerId: String = "",
    val managerUids: List<String> = emptyList(),
    val assistantUids: List<String> = emptyList(),
    val memberUids: List<String> = emptyList(),
    val inviteCode: String = "",
    val allowMultipleManagers: Boolean = true,
    val availableSeats: Long = 0L,
    val seatRentInfo: String? = null,
    val isArchived: Boolean = false,
    val closedMonths: List<String> = emptyList(),
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null,
) {
    fun roleFor(uid: String): MessRole = when {
        uid == ownerId -> MessRole.OWNER
        uid in managerUids -> MessRole.MANAGER
        uid in assistantUids -> MessRole.ASSISTANT_MANAGER
        else -> MessRole.MEMBER
    }

    fun isMonthClosed(monthKey: String): Boolean = monthKey in closedMonths
}

@IgnoreExtraProperties
data class MessMember(
    @DocumentId val id: String = "",
    val messId: String = "",
    val userId: String = "",
    val displayName: String = "",
    val role: String = MessRole.MEMBER.key,
    val status: String = "active",
    val memberUids: List<String> = emptyList(),
    @ServerTimestamp val joinedAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null,
)

@IgnoreExtraProperties
data class JoinRequest(
    @DocumentId val id: String = "",
    val messId: String = "",
    val applicantUid: String = "",
    val applicantName: String = "",
    val inviteCode: String = "",
    val status: String = "pending",
    val managerUids: List<String> = emptyList(),
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null,
)

@IgnoreExtraProperties
data class MealEntry(
    @DocumentId val id: String = "",
    val messId: String = "",
    val monthKey: String = "",
    val date: String = "",
    val memberUid: String = "",
    val memberName: String = "",
    val breakfast: Double = 0.0,
    val lunch: Double = 0.0,
    val dinner: Double = 0.0,
    val totalMeals: Double = 0.0,
    val enteredByUid: String = "",
    val memberUids: List<String> = emptyList(),
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null,
)

@IgnoreExtraProperties
data class Expense(
    @DocumentId val id: String = "",
    val messId: String = "",
    val monthKey: String = "",
    val date: String = "",
    val category: String = ExpenseCategory.BAZAR.key,
    val description: String = "",
    val amountPaisa: Long = 0L,
    val purchasedByUid: String = "",
    val purchasedByName: String = "",
    val targetMemberUid: String? = null,
    val receiptUri: String? = null,
    val status: String = "approved",
    val approvedByUid: String? = null,
    val memberUids: List<String> = emptyList(),
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null,
)

@IgnoreExtraProperties
data class Deposit(
    @DocumentId val id: String = "",
    val messId: String = "",
    val monthKey: String = "",
    val date: String = "",
    val memberUid: String = "",
    val memberName: String = "",
    val amountPaisa: Long = 0L,
    val method: String = DepositMethod.CASH.key,
    val note: String? = null,
    val recordedByUid: String = "",
    val memberUids: List<String> = emptyList(),
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null,
)

@IgnoreExtraProperties
data class Adjustment(
    @DocumentId val id: String = "",
    val messId: String = "",
    val monthKey: String = "",
    val memberUid: String = "",
    val memberName: String = "",
    val amountPaisa: Long = 0L,
    val reason: String = "",
    val recordedByUid: String = "",
    val memberUids: List<String> = emptyList(),
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null,
)

@IgnoreExtraProperties
data class MonthlyClosing(
    @DocumentId val id: String = "",
    val messId: String = "",
    val monthKey: String = "",
    val totalMealExpensePaisa: Long = 0L,
    val totalMealsText: String = "0",
    val mealRateText: String = "0.00",
    val totalDepositPaisa: Long = 0L,
    val totalExpensePaisa: Long = 0L,
    val closedByUid: String = "",
    val closedByName: String = "",
    val memberUids: List<String> = emptyList(),
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null,
)

@IgnoreExtraProperties
data class Announcement(
    @DocumentId val id: String = "",
    val messId: String = "",
    val title: String = "",
    val body: String = "",
    val authorUid: String = "",
    val authorName: String = "",
    val pinned: Boolean = false,
    val memberUids: List<String> = emptyList(),
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null,
)

@IgnoreExtraProperties
data class ChatMessage(
    @DocumentId val id: String = "",
    val messId: String = "",
    val senderUid: String = "",
    val senderName: String = "",
    val text: String = "",
    val memberUids: List<String> = emptyList(),
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null,
)

@IgnoreExtraProperties
data class AuditLog(
    @DocumentId val id: String = "",
    val messId: String = "",
    val action: String = "",
    val details: String = "",
    val actorUid: String = "",
    val actorName: String = "",
    val memberUids: List<String> = emptyList(),
    @ServerTimestamp val createdAt: Timestamp? = null,
)
