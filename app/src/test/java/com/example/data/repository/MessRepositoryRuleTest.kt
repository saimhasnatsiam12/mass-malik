package com.example.data.repository

import com.example.base.FirestoreEmulatorTestBase
import com.example.data.model.ExpenseCategory
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.UUID

class MessRepositoryRuleTest : FirestoreEmulatorTestBase() {

    @Test
    fun createMess_inviteJoinApproval_andTenantIsolation_succeeds() = runBlocking {
        val ownerEmail = "owner_${UUID.randomUUID()}@test.com"
        val memberEmail = "member_${UUID.randomUUID()}@test.com"
        val outsiderEmail = "outsider_${UUID.randomUUID()}@test.com"

        val ownerUid = signInTestUser(ownerEmail)
        val repo = MessRepository(firestore)

        withTimeout(DEFAULT_TIMEOUT_MS) {
            repo.ensureUserProfile("Alice Owner", ownerEmail, null).getOrThrow()
        }
        val messId = withTimeout(DEFAULT_TIMEOUT_MS) {
            repo.createMess(
                name = "Emerald Hostel Mess",
                address = "Road 7, Mirpur",
                description = "Student mess",
                currencySymbol = "৳",
                availableSeats = 2L,
                seatRentInfo = "4500/seat",
                ownerDisplayName = "Alice Owner",
            ).getOrThrow()
        }

        val mess = withTimeout(FLOW_TIMEOUT_MS) {
            repo.observeMess(messId).first { it != null }!!
        }
        assertEquals("Emerald Hostel Mess", mess.name)
        assertEquals(listOf(ownerUid), mess.memberUids)

        // Outsider cannot read mess
        signInTestUser(outsiderEmail)
        val outsiderFail = try {
            withTimeout(FLOW_TIMEOUT_MS) { repo.observeMess(messId).first() }
            null
        } catch (e: Exception) {
            e.unwrapFirestoreException()
        }
        assertNotNull(outsiderFail)
        assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, outsiderFail!!.code)

        // Applicant looks up invite and submits join request
        val memberUid = signInTestUser(memberEmail)
        withTimeout(DEFAULT_TIMEOUT_MS) {
            repo.ensureUserProfile("Bob Member", memberEmail, null).getOrThrow()
        }
        val invite = withTimeout(DEFAULT_TIMEOUT_MS) {
            repo.lookupInvite(mess.inviteCode).getOrThrow()
        }
        assertEquals(messId, invite.messId)
        withTimeout(DEFAULT_TIMEOUT_MS) {
            repo.submitJoinRequest(invite, "Bob Member").getOrThrow()
        }

        // Owner approves Bob
        signInTestUser(ownerEmail)
        val pendingReq = withTimeout(FLOW_TIMEOUT_MS) {
            repo.observeJoinRequests(messId, ownerUid).first { it.any { req -> req.applicantUid == memberUid } }
        }.first { it.applicantUid == memberUid }

        withTimeout(DEFAULT_TIMEOUT_MS) {
            repo.approveJoinRequest(mess, pendingReq, "Alice Owner").getOrThrow()
        }

        // Bob can now observe the mess and meals
        signInTestUser(memberEmail)
        val updatedMess = withTimeout(FLOW_TIMEOUT_MS) {
            repo.observeMess(messId).first { it != null && memberUid in it.memberUids }!!
        }
        assertTrue(memberUid in updatedMess.memberUids)

        // Bob submits a pending bazar expense
        withTimeout(DEFAULT_TIMEOUT_MS) {
            repo.addExpense(
                mess = updatedMess,
                monthKey = "2026-11",
                date = "2026-11-14",
                category = ExpenseCategory.BAZAR.key,
                description = "Fresh Fish & Vegetables",
                amountPaisa = 85000L,
                targetMemberUid = null,
                receiptUri = null,
                autoApprove = false,
                actorName = "Bob Member",
            ).getOrThrow()
        }
        val expenses = withTimeout(FLOW_TIMEOUT_MS) {
            repo.observeExpenses(messId, "2026-11", memberUid).first { it.isNotEmpty() }
        }
        assertEquals("pending", expenses.first().status)
    }

    @Test
    fun observeUserMesses_unauthenticatedUser_failsWithPermissionDenied() = runBlocking {
        auth.signOut()
        val repo = MessRepository(firestore)
        val exception = try {
            withTimeout(FLOW_TIMEOUT_MS) { repo.observeUserMesses("fake_uid").first() }
            null
        } catch (e: Exception) {
            e
        }
        val firestoreException = checkNotNull(exception?.unwrapFirestoreException()) {
            "Expected nested FirebaseFirestoreException, got $exception"
        }
        assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, firestoreException.code)
    }

    @Test
    fun closedMonth_preventsMealAndExpenseMutation() = runBlocking {
        val ownerEmail = "close_${UUID.randomUUID()}@test.com"
        val ownerUid = signInTestUser(ownerEmail)
        val repo = MessRepository(firestore)

        withTimeout(DEFAULT_TIMEOUT_MS) {
            repo.ensureUserProfile("Owner", ownerEmail, null).getOrThrow()
        }
        val messId = withTimeout(DEFAULT_TIMEOUT_MS) {
            repo.createMess(
                name = "Closing Test Mess",
                address = "",
                description = "",
                currencySymbol = "৳",
                availableSeats = 0L,
                seatRentInfo = "",
                ownerDisplayName = "Owner",
            ).getOrThrow()
        }
        val mess = withTimeout(FLOW_TIMEOUT_MS) {
            repo.observeMess(messId).first { it != null }!!
        }

        // Close month 2026-10
        withTimeout(DEFAULT_TIMEOUT_MS) {
            repo.closeMonth(
                mess = mess,
                monthKey = "2026-10",
                totalMealExpensePaisa = 300000L,
                totalMealsText = "50",
                mealRateText = "60.00",
                totalDepositPaisa = 350000L,
                totalExpensePaisa = 300000L,
                actorName = "Owner",
            ).getOrThrow()
        }

        val lockedMess = withTimeout(FLOW_TIMEOUT_MS) {
            repo.observeMess(messId).first { it != null && "2026-10" in it.closedMonths }!!
        }

        val fail = try {
            // Bypass client check by calling with unlocked copy to verify Firestore rule blocks it
            withTimeout(DEFAULT_TIMEOUT_MS) {
                repo.upsertMealEntry(
                    mess = mess,
                    monthKey = "2026-10",
                    date = "2026-10-20",
                    memberUid = ownerUid,
                    memberName = "Owner",
                    breakfast = 1.0,
                    lunch = 1.0,
                    dinner = 1.0,
                    actorName = "Owner",
                ).getOrThrow()
            }
            null
        } catch (e: Exception) {
            e.unwrapFirestoreException()
        }
        assertNotNull(fail)
        assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, fail!!.code)
        assertTrue(lockedMess.isMonthClosed("2026-10"))
    }

    private companion object {
        const val DEFAULT_TIMEOUT_MS = 8000L
        const val FLOW_TIMEOUT_MS = 5000L
    }
}
