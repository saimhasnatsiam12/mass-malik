package com.example.data.repository

import android.content.Context
import com.example.R
import com.example.data.firebase.OperationType
import com.example.data.firebase.handleFirestoreError
import com.example.data.model.Adjustment
import com.example.data.model.Announcement
import com.example.data.model.AuditLog
import com.example.data.model.ChatMessage
import com.example.data.model.Deposit
import com.example.data.model.Expense
import com.example.data.model.JoinRequest
import com.example.data.model.MealEntry
import com.example.data.model.Mess
import com.example.data.model.MessInvite
import com.example.data.model.MessMember
import com.example.data.model.MessRole
import com.example.data.model.MonthlyClosing
import com.example.data.model.UserProfile
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import java.util.UUID

class MessRepository(private val db: FirebaseFirestore) {

    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        )
    )

    private val auth = Firebase.auth

    private fun requireUserId(): String =
        auth.currentUser?.uid
            ?: throw IllegalStateException("User must be signed in with Google before accessing Firestore.")

    private fun generateInviteCode(): String {
        val chars = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
        return buildString {
            append("MM")
            repeat(6) {
                append(chars.random())
            }
        }
    }

    // region User Profile

    fun observeUserProfile(userId: String): Flow<UserProfile?> =
        db.collection("users").document(userId)
            .snapshots()
            .map { snap ->
                if (snap.exists()) {
                    snap.toObject(UserProfile::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                } else {
                    null
                }
            }
            .catch { error ->
                handleFlowError(error, OperationType.GET, "users/$userId", userId)
            }

    suspend fun ensureUserProfile(
        displayName: String,
        email: String?,
        photoUrl: String?,
    ): Result<Unit> = runCatching {
        val uid = requireUserId()
        val ref = db.collection("users").document(uid)
        val existing = ref.get().await()
        val safeName = displayName.ifBlank { "Mess Member" }.take(80)
        if (!existing.exists()) {
            val profile = UserProfile(
                userId = uid,
                displayName = safeName,
                email = email?.take(120),
                photoUrl = photoUrl?.take(500),
                themeMode = "system",
                isDeactivated = false,
            )
            ref.set(profile.toCreateMap()).await()
        } else {
            val updates = mutableMapOf<String, Any>(
                "updatedAt" to FieldValue.serverTimestamp(),
            )
            if (existing.getString("displayName").isNullOrBlank()) {
                updates["displayName"] = safeName
            }
            if (!email.isNullOrBlank() && existing.getString("email").isNullOrBlank()) {
                updates["email"] = email.take(120)
            }
            if (!photoUrl.isNullOrBlank() && existing.getString("photoUrl").isNullOrBlank()) {
                updates["photoUrl"] = photoUrl.take(500)
            }
            ref.update(updates).await()
        }
        Unit
    }.onFailure {
        handleFirestoreError(it, OperationType.WRITE, "users/${auth.currentUser?.uid}")
    }

    suspend fun updateUserProfile(displayName: String, phone: String?): Result<Unit> = runCatching {
        val uid = requireUserId()
        val ref = db.collection("users").document(uid)
        val updates = mutableMapOf<String, Any>(
            "displayName" to displayName.trim().take(80),
            "updatedAt" to FieldValue.serverTimestamp(),
        )
        if (!phone.isNullOrBlank()) {
            updates["phone"] = phone.trim().take(30)
        } else {
            updates["phone"] = FieldValue.delete()
        }
        ref.update(updates).await()
        Unit
    }.onFailure {
        handleFirestoreError(it, OperationType.UPDATE, "users/${auth.currentUser?.uid}")
    }

    suspend fun setActiveMess(messId: String): Result<Unit> = runCatching {
        val uid = requireUserId()
        db.collection("users").document(uid)
            .update(
                mapOf(
                    "activeMessId" to messId,
                    "updatedAt" to FieldValue.serverTimestamp(),
                )
            ).await()
        Unit
    }.onFailure {
        handleFirestoreError(it, OperationType.UPDATE, "users/${auth.currentUser?.uid}")
    }

    suspend fun updateThemeMode(themeMode: String): Result<Unit> = runCatching {
        val uid = requireUserId()
        db.collection("users").document(uid)
            .update(
                mapOf(
                    "themeMode" to themeMode,
                    "updatedAt" to FieldValue.serverTimestamp(),
                )
            ).await()
        Unit
    }.onFailure {
        handleFirestoreError(it, OperationType.UPDATE, "users/${auth.currentUser?.uid}")
    }

    suspend fun setAccountDeactivated(deactivated: Boolean): Result<Unit> = runCatching {
        val uid = requireUserId()
        db.collection("users").document(uid)
            .update(
                mapOf(
                    "isDeactivated" to deactivated,
                    "updatedAt" to FieldValue.serverTimestamp(),
                )
            ).await()
        Unit
    }.onFailure {
        handleFirestoreError(it, OperationType.UPDATE, "users/${auth.currentUser?.uid}")
    }

    // endregion

    // region Multi-Mess & Invites

    fun observeUserMesses(userId: String): Flow<List<Mess>> =
        db.collection("messes")
            .whereArrayContains("memberUids", userId)
            .snapshots()
            .map { snapshot ->
                snapshot.toObjects(Mess::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                    .filter { !it.isArchived }
                    .sortedBy { it.name.lowercase() }
            }
            .catch { error ->
                handleFlowError(error, OperationType.LIST, "messes", userId)
            }

    fun observeMess(messId: String): Flow<Mess?> =
        db.collection("messes").document(messId)
            .snapshots()
            .map { snap ->
                if (snap.exists()) {
                    snap.toObject(Mess::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                } else {
                    null
                }
            }
            .catch { error ->
                handleFlowError(error, OperationType.GET, "messes/$messId", auth.currentUser?.uid)
            }

    suspend fun createMess(
        name: String,
        address: String,
        description: String,
        currencySymbol: String,
        availableSeats: Long,
        seatRentInfo: String,
        ownerDisplayName: String,
    ): Result<String> = runCatching {
        val uid = requireUserId()
        val messRef = db.collection("messes").document()
        val messId = messRef.id
        val inviteCode = generateInviteCode()
        val safeName = name.trim().take(80)
        val safeOwnerName = ownerDisplayName.ifBlank { "Owner" }.take(80)

        val messPayload = mutableMapOf<String, Any>(
            "name" to safeName,
            "currencySymbol" to currencySymbol.trim().ifBlank { "৳" }.take(6),
            "ownerId" to uid,
            "managerUids" to listOf(uid),
            "assistantUids" to emptyList<String>(),
            "memberUids" to listOf(uid),
            "inviteCode" to inviteCode,
            "allowMultipleManagers" to true,
            "availableSeats" to availableSeats.coerceIn(0L, 100L),
            "isArchived" to false,
            "closedMonths" to emptyList<String>(),
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp(),
        )
        address.trim().takeIf { it.isNotEmpty() }?.let { messPayload["address"] = it.take(200) }
        description.trim().takeIf { it.isNotEmpty() }?.let { messPayload["description"] = it.take(400) }
        seatRentInfo.trim().takeIf { it.isNotEmpty() }?.let { messPayload["seatRentInfo"] = it.take(200) }

        val memberPayload = mapOf(
            "messId" to messId,
            "userId" to uid,
            "displayName" to safeOwnerName,
            "role" to MessRole.OWNER.key,
            "status" to "active",
            "memberUids" to listOf(uid),
            "joinedAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp(),
        )

        val invitePayload = mutableMapOf<String, Any>(
            "messId" to messId,
            "messName" to safeName,
            "ownerId" to uid,
            "managerUids" to listOf(uid),
            "inviteCode" to inviteCode,
            "isActive" to true,
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp(),
        )
        address.trim().takeIf { it.isNotEmpty() }?.let { invitePayload["messAddress"] = it.take(200) }

        val logRef = messRef.collection("audit_logs").document()
        val auditPayload = mapOf(
            "messId" to messId,
            "action" to "MESS_CREATED",
            "details" to "Created mess '$safeName'",
            "actorUid" to uid,
            "actorName" to safeOwnerName,
            "memberUids" to listOf(uid),
            "createdAt" to FieldValue.serverTimestamp(),
        )

        val batch = db.batch()
        batch.set(messRef, messPayload)
        batch.set(messRef.collection("members").document(uid), memberPayload)
        batch.set(db.collection("invites").document(inviteCode), invitePayload)
        batch.set(logRef, auditPayload)
        batch.update(
            db.collection("users").document(uid),
            mapOf("activeMessId" to messId, "updatedAt" to FieldValue.serverTimestamp()),
        )
        batch.commit().await()
        messId
    }.onFailure {
        handleFirestoreError(it, OperationType.CREATE, "messes")
    }

    suspend fun updateMessSettings(
        mess: Mess,
        name: String,
        address: String,
        description: String,
        currencySymbol: String,
        allowMultipleManagers: Boolean,
        availableSeats: Long,
        seatRentInfo: String,
        actorName: String,
    ): Result<Unit> = runCatching {
        val uid = requireUserId()
        val messRef = db.collection("messes").document(mess.id)
        val updates = mutableMapOf<String, Any>(
            "name" to name.trim().take(80),
            "currencySymbol" to currencySymbol.trim().ifBlank { "৳" }.take(6),
            "allowMultipleManagers" to allowMultipleManagers,
            "availableSeats" to availableSeats.coerceIn(0L, 100L),
            "updatedAt" to FieldValue.serverTimestamp(),
        )
        updates["address"] = address.trim().take(200).ifEmpty { FieldValue.delete() }
        updates["description"] = description.trim().take(400).ifEmpty { FieldValue.delete() }
        updates["seatRentInfo"] = seatRentInfo.trim().take(200).ifEmpty { FieldValue.delete() }

        val batch = db.batch()
        batch.update(messRef, updates)
        batch.update(
            db.collection("invites").document(mess.inviteCode),
            mapOf(
                "messName" to name.trim().take(80),
                "messAddress" to address.trim().take(200),
                "updatedAt" to FieldValue.serverTimestamp(),
            ),
        )
        batch.set(
            messRef.collection("audit_logs").document(),
            auditMap(mess, "MESS_UPDATED", "Updated mess details & seat info", uid, actorName),
        )
        batch.commit().await()
        Unit
    }.onFailure {
        handleFirestoreError(it, OperationType.UPDATE, "messes/${mess.id}")
    }

    suspend fun rotateInviteCode(mess: Mess, actorName: String): Result<String> = runCatching {
        val uid = requireUserId()
        val newCode = generateInviteCode()
        val messRef = db.collection("messes").document(mess.id)
        val batch = db.batch()
        batch.delete(db.collection("invites").document(mess.inviteCode))
        val newInvite = mutableMapOf<String, Any>(
            "messId" to mess.id,
            "messName" to mess.name,
            "ownerId" to mess.ownerId,
            "managerUids" to mess.managerUids,
            "inviteCode" to newCode,
            "isActive" to true,
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp(),
        )
        mess.address?.takeIf { it.isNotBlank() }?.let { newInvite["messAddress"] = it }
        batch.set(db.collection("invites").document(newCode), newInvite)
        batch.update(
            messRef,
            mapOf("inviteCode" to newCode, "updatedAt" to FieldValue.serverTimestamp()),
        )
        batch.set(
            messRef.collection("audit_logs").document(),
            auditMap(mess, "INVITE_ROTATED", "Generated new invite code $newCode", uid, actorName),
        )
        batch.commit().await()
        newCode
    }.onFailure {
        handleFirestoreError(it, OperationType.UPDATE, "messes/${mess.id}")
    }

    suspend fun archiveMess(mess: Mess, actorName: String): Result<Unit> = runCatching {
        val uid = requireUserId()
        val messRef = db.collection("messes").document(mess.id)
        val batch = db.batch()
        batch.update(
            messRef,
            mapOf("isArchived" to true, "updatedAt" to FieldValue.serverTimestamp()),
        )
        batch.set(
            messRef.collection("audit_logs").document(),
            auditMap(mess, "MESS_ARCHIVED", "Archived mess '${mess.name}'", uid, actorName),
        )
        batch.commit().await()
        Unit
    }.onFailure {
        handleFirestoreError(it, OperationType.UPDATE, "messes/${mess.id}")
    }

    suspend fun lookupInvite(inviteCode: String): Result<MessInvite> = runCatching {
        requireUserId()
        val normalized = inviteCode.trim().uppercase()
        require(normalized.matches(Regex("^[A-Z0-9_\\-]{4,32}$"))) {
            "Enter a valid invite code (letters & numbers only)."
        }
        val snap = db.collection("invites").document(normalized).get().await()
        val invite = snap.toObject(MessInvite::class.java)
            ?: throw IllegalStateException("No active mess found for invite code '$normalized'.")
        if (!invite.isActive) {
            throw IllegalStateException("This invite code is no longer active.")
        }
        invite
    }.onFailure {
        handleFirestoreError(it, OperationType.GET, "invites/$inviteCode")
    }

    suspend fun submitJoinRequest(invite: MessInvite, applicantName: String): Result<Unit> = runCatching {
        val uid = requireUserId()
        val reqRef = db.collection("messes").document(invite.messId)
            .collection("join_requests").document(uid)
        val existing = reqRef.get().await()
        if (existing.exists() && existing.getString("status") == "pending") {
            throw IllegalStateException("You already have a pending join request for ${invite.messName}.")
        }
        if (existing.exists()) {
            reqRef.delete().await()
        }
        val payload = mapOf(
            "messId" to invite.messId,
            "applicantUid" to uid,
            "applicantName" to applicantName.ifBlank { "New Member" }.take(80),
            "inviteCode" to invite.inviteCode,
            "status" to "pending",
            "managerUids" to invite.managerUids,
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp(),
        )
        reqRef.set(payload).await()
        Unit
    }.onFailure {
        handleFirestoreError(it, OperationType.CREATE, "messes/${invite.messId}/join_requests")
    }

    fun observeJoinRequests(messId: String, managerUid: String): Flow<List<JoinRequest>> =
        db.collection("messes").document(messId).collection("join_requests")
            .whereArrayContains("managerUids", managerUid)
            .snapshots()
            .map { snapshot ->
                snapshot.toObjects(JoinRequest::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                    .filter { it.status == "pending" }
            }
            .catch { error ->
                handleFlowError(error, OperationType.LIST, "messes/$messId/join_requests", managerUid)
            }

    suspend fun approveJoinRequest(
        mess: Mess,
        request: JoinRequest,
        actorName: String,
    ): Result<Unit> = runCatching {
        val uid = requireUserId()
        val applicantUid = request.applicantUid
        val updatedMembers = (mess.memberUids + applicantUid).distinct()
        val messRef = db.collection("messes").document(mess.id)

        val batch = db.batch()
        batch.update(
            messRef,
            mapOf(
                "memberUids" to updatedMembers,
                "updatedAt" to FieldValue.serverTimestamp(),
            ),
        )
        batch.set(
            messRef.collection("members").document(applicantUid),
            mapOf(
                "messId" to mess.id,
                "userId" to applicantUid,
                "displayName" to request.applicantName.take(80),
                "role" to MessRole.MEMBER.key,
                "status" to "active",
                "memberUids" to updatedMembers,
                "joinedAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp(),
            ),
        )
        batch.update(
            messRef.collection("join_requests").document(applicantUid),
            mapOf(
                "status" to "approved",
                "updatedAt" to FieldValue.serverTimestamp(),
            ),
        )
        batch.set(
            messRef.collection("audit_logs").document(),
            mapOf(
                "messId" to mess.id,
                "action" to "MEMBER_APPROVED",
                "details" to "Approved ${request.applicantName} to join ${mess.name}",
                "actorUid" to uid,
                "actorName" to actorName,
                "memberUids" to updatedMembers,
                "createdAt" to FieldValue.serverTimestamp(),
            ),
        )
        batch.commit().await()

        syncMemberUidsAcrossSubcollections(mess.id, updatedMembers, uid)
    }.onFailure {
        handleFirestoreError(it, OperationType.UPDATE, "messes/${mess.id}/join_requests/${request.applicantUid}")
    }

    suspend fun rejectJoinRequest(mess: Mess, request: JoinRequest, actorName: String): Result<Unit> = runCatching {
        val uid = requireUserId()
        val messRef = db.collection("messes").document(mess.id)
        val batch = db.batch()
        batch.update(
            messRef.collection("join_requests").document(request.applicantUid),
            mapOf("status" to "rejected", "updatedAt" to FieldValue.serverTimestamp()),
        )
        batch.set(
            messRef.collection("audit_logs").document(),
            auditMap(mess, "MEMBER_REJECTED", "Rejected join request from ${request.applicantName}", uid, actorName),
        )
        batch.commit().await()
        Unit
    }.onFailure {
        handleFirestoreError(it, OperationType.UPDATE, "messes/${mess.id}/join_requests/${request.applicantUid}")
    }

    private suspend fun syncMemberUidsAcrossSubcollections(
        messId: String,
        updatedMembers: List<String>,
        callerUid: String,
    ) {
        val messRef = db.collection("messes").document(messId)
        val subcollections = listOf(
            "members", "meals", "expenses", "deposits",
            "adjustments", "closings", "announcements", "chat", "audit_logs",
        )
        val refs = mutableListOf<com.google.firebase.firestore.DocumentReference>()
        for (sub in subcollections) {
            val docs = messRef.collection(sub)
                .whereArrayContains("memberUids", callerUid)
                .get()
                .await()
            docs.documents.forEach { refs.add(it.reference) }
        }
        // Chunk into batches of 400 to stay below Firestore's 500-op batch limit
        for (chunk in refs.chunked(400)) {
            val batch = db.batch()
            for (ref in chunk) {
                if (ref.parent.id == "audit_logs") {
                    batch.update(ref, mapOf("memberUids" to updatedMembers))
                } else {
                    batch.update(
                        ref,
                        mapOf(
                            "memberUids" to updatedMembers,
                            "updatedAt" to FieldValue.serverTimestamp(),
                        ),
                    )
                }
            }
            batch.commit().await()
        }
    }

    // endregion

    // region Members & Roles

    fun observeMembers(messId: String, currentUserId: String): Flow<List<MessMember>> =
        db.collection("messes").document(messId).collection("members")
            .whereArrayContains("memberUids", currentUserId)
            .snapshots()
            .map { snapshot ->
                snapshot.toObjects(MessMember::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                    .sortedWith(compareBy<MessMember> {
                        when (MessRole.fromKey(it.role)) {
                            MessRole.OWNER -> 0
                            MessRole.MANAGER -> 1
                            MessRole.ASSISTANT_MANAGER -> 2
                            MessRole.MEMBER -> 3
                        }
                    }.thenBy { it.displayName.lowercase() })
            }
            .catch { error ->
                handleFlowError(error, OperationType.LIST, "messes/$messId/members", currentUserId)
            }

    suspend fun updateMemberRoleAndStatus(
        mess: Mess,
        targetMember: MessMember,
        newRole: MessRole,
        newStatus: String,
        actorName: String,
    ): Result<Unit> = runCatching {
        val uid = requireUserId()
        require(targetMember.userId != mess.ownerId || newRole == MessRole.OWNER) {
            "Transfer ownership first before changing the owner's role."
        }
        if (newRole == MessRole.MANAGER && !mess.allowMultipleManagers) {
            val otherManagers = mess.managerUids.filter { it != mess.ownerId && it != targetMember.userId }
            require(otherManagers.isEmpty()) {
                "Multiple managers are disabled in Mess Settings."
            }
        }
        val messRef = db.collection("messes").document(mess.id)
        val newManagers = mess.managerUids.toMutableSet().apply {
            add(mess.ownerId)
            if (newRole == MessRole.MANAGER) add(targetMember.userId) else if (targetMember.userId != mess.ownerId) remove(targetMember.userId)
        }.toList()
        val newAssistants = mess.assistantUids.toMutableSet().apply {
            if (newRole == MessRole.ASSISTANT_MANAGER) add(targetMember.userId) else remove(targetMember.userId)
        }.toList()

        val batch = db.batch()
        batch.update(
            messRef,
            mapOf(
                "managerUids" to newManagers,
                "assistantUids" to newAssistants,
                "updatedAt" to FieldValue.serverTimestamp(),
            ),
        )
        batch.update(
            messRef.collection("members").document(targetMember.userId),
            mapOf(
                "role" to newRole.key,
                "status" to newStatus,
                "updatedAt" to FieldValue.serverTimestamp(),
            ),
        )
        batch.update(
            db.collection("invites").document(mess.inviteCode),
            mapOf(
                "managerUids" to newManagers,
                "updatedAt" to FieldValue.serverTimestamp(),
            ),
        )
        batch.set(
            messRef.collection("audit_logs").document(),
            auditMap(
                mess,
                "ROLE_CHANGED",
                "Updated ${targetMember.displayName} to ${newRole.label} ($newStatus)",
                uid,
                actorName,
            ),
        )
        batch.commit().await()
        Unit
    }.onFailure {
        handleFirestoreError(it, OperationType.UPDATE, "messes/${mess.id}/members/${targetMember.userId}")
    }

    suspend fun transferOwnership(
        mess: Mess,
        newOwner: MessMember,
        actorName: String,
    ): Result<Unit> = runCatching {
        val uid = requireUserId()
        require(mess.ownerId == uid) { "Only the current Mess Owner can transfer ownership." }
        require(newOwner.userId != uid) { "Select another member to transfer ownership to." }

        val messRef = db.collection("messes").document(mess.id)
        val newManagers = (mess.managerUids + newOwner.userId).distinct()
        val newAssistants = mess.assistantUids.filter { it != newOwner.userId }

        val batch = db.batch()
        batch.update(
            messRef,
            mapOf(
                "ownerId" to newOwner.userId,
                "managerUids" to newManagers,
                "assistantUids" to newAssistants,
                "updatedAt" to FieldValue.serverTimestamp(),
            ),
        )
        batch.update(
            messRef.collection("members").document(uid),
            mapOf("role" to MessRole.MANAGER.key, "updatedAt" to FieldValue.serverTimestamp()),
        )
        batch.update(
            messRef.collection("members").document(newOwner.userId),
            mapOf("role" to MessRole.OWNER.key, "status" to "active", "updatedAt" to FieldValue.serverTimestamp()),
        )
        batch.set(
            messRef.collection("audit_logs").document(),
            auditMap(
                mess,
                "OWNERSHIP_TRANSFERRED",
                "Transferred Mess Ownership to ${newOwner.displayName}",
                uid,
                actorName,
            ),
        )
        batch.commit().await()
        Unit
    }.onFailure {
        handleFirestoreError(it, OperationType.UPDATE, "messes/${mess.id}")
    }

    suspend fun removeMember(
        mess: Mess,
        member: MessMember,
        actorName: String,
    ): Result<Unit> = runCatching {
        val uid = requireUserId()
        require(member.userId != mess.ownerId) { "Cannot remove the Mess Owner." }
        require(member.userId != uid) { "Use another manager or owner to remove your membership." }
        val updatedMembers = mess.memberUids.filter { it != member.userId }
        val updatedManagers = mess.managerUids.filter { it != member.userId }
        val updatedAssistants = mess.assistantUids.filter { it != member.userId }

        val messRef = db.collection("messes").document(mess.id)
        val batch = db.batch()
        batch.update(
            messRef,
            mapOf(
                "memberUids" to updatedMembers,
                "managerUids" to updatedManagers,
                "assistantUids" to updatedAssistants,
                "updatedAt" to FieldValue.serverTimestamp(),
            ),
        )
        batch.delete(messRef.collection("members").document(member.userId))
        batch.set(
            messRef.collection("audit_logs").document(),
            mapOf(
                "messId" to mess.id,
                "action" to "MEMBER_REMOVED",
                "details" to "Removed ${member.displayName} from ${mess.name}",
                "actorUid" to uid,
                "actorName" to actorName,
                "memberUids" to updatedMembers,
                "createdAt" to FieldValue.serverTimestamp(),
            ),
        )
        batch.commit().await()

        syncMemberUidsAcrossSubcollections(mess.id, updatedMembers, uid)
    }.onFailure {
        handleFirestoreError(it, OperationType.DELETE, "messes/${mess.id}/members/${member.userId}")
    }

    // endregion

    // region Meals

    fun observeMeals(messId: String, monthKey: String, currentUserId: String): Flow<List<MealEntry>> =
        db.collection("messes").document(messId).collection("meals")
            .whereArrayContains("memberUids", currentUserId)
            .whereEqualTo("monthKey", monthKey)
            .snapshots()
            .map { snapshot ->
                snapshot.toObjects(MealEntry::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                    .sortedByDescending { it.date }
            }
            .catch { error ->
                handleFlowError(error, OperationType.LIST, "messes/$messId/meals", currentUserId)
            }

    suspend fun upsertMealEntry(
        mess: Mess,
        monthKey: String,
        date: String,
        memberUid: String,
        memberName: String,
        breakfast: Double,
        lunch: Double,
        dinner: Double,
        actorName: String,
    ): Result<Unit> = runCatching {
        val uid = requireUserId()
        require(!mess.isMonthClosed(monthKey)) { "Month $monthKey is closed and locked." }
        val docId = "${date}_$memberUid"
        val messRef = db.collection("messes").document(mess.id)
        val docRef = messRef.collection("meals").document(docId)
        val total = breakfast + lunch + dinner

        val existing = docRef.get().await()
        val batch = db.batch()
        if (!existing.exists()) {
            batch.set(
                docRef,
                mapOf(
                    "messId" to mess.id,
                    "monthKey" to monthKey,
                    "date" to date,
                    "memberUid" to memberUid,
                    "memberName" to memberName.take(80),
                    "breakfast" to breakfast,
                    "lunch" to lunch,
                    "dinner" to dinner,
                    "totalMeals" to total,
                    "enteredByUid" to uid,
                    "memberUids" to mess.memberUids,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp(),
                ),
            )
            batch.set(
                messRef.collection("audit_logs").document(),
                auditMap(mess, "MEAL_LOGGED", "Logged $total meals for $memberName on $date", uid, actorName),
            )
        } else {
            batch.update(
                docRef,
                mapOf(
                    "breakfast" to breakfast,
                    "lunch" to lunch,
                    "dinner" to dinner,
                    "totalMeals" to total,
                    "enteredByUid" to uid,
                    "memberName" to memberName.take(80),
                    "updatedAt" to FieldValue.serverTimestamp(),
                ),
            )
            batch.set(
                messRef.collection("audit_logs").document(),
                auditMap(mess, "MEAL_UPDATED", "Updated $memberName on $date to B:$breakfast L:$lunch D:$dinner", uid, actorName),
            )
        }
        batch.commit().await()
        Unit
    }.onFailure {
        handleFirestoreError(it, OperationType.WRITE, "messes/${mess.id}/meals")
    }

    suspend fun deleteMealEntry(mess: Mess, meal: MealEntry, actorName: String): Result<Unit> = runCatching {
        val uid = requireUserId()
        require(!mess.isMonthClosed(meal.monthKey)) { "Month ${meal.monthKey} is closed." }
        val messRef = db.collection("messes").document(mess.id)
        val batch = db.batch()
        batch.delete(messRef.collection("meals").document(meal.id))
        batch.set(
            messRef.collection("audit_logs").document(),
            auditMap(mess, "MEAL_DELETED", "Deleted meal entry for ${meal.memberName} on ${meal.date}", uid, actorName),
        )
        batch.commit().await()
        Unit
    }.onFailure {
        handleFirestoreError(it, OperationType.DELETE, "messes/${mess.id}/meals/${meal.id}")
    }

    // endregion

    // region Bazar & Expenses

    fun observeExpenses(messId: String, monthKey: String, currentUserId: String): Flow<List<Expense>> =
        db.collection("messes").document(messId).collection("expenses")
            .whereArrayContains("memberUids", currentUserId)
            .whereEqualTo("monthKey", monthKey)
            .snapshots()
            .map { snapshot ->
                snapshot.toObjects(Expense::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                    .sortedByDescending { it.date }
            }
            .catch { error ->
                handleFlowError(error, OperationType.LIST, "messes/$messId/expenses", currentUserId)
            }

    suspend fun addExpense(
        mess: Mess,
        monthKey: String,
        date: String,
        category: String,
        description: String,
        amountPaisa: Long,
        targetMemberUid: String?,
        receiptUri: String?,
        autoApprove: Boolean,
        actorName: String,
    ): Result<Unit> = runCatching {
        val uid = requireUserId()
        require(!mess.isMonthClosed(monthKey)) { "Month $monthKey is closed." }
        require(amountPaisa > 0L) { "Amount must be greater than zero." }
        val messRef = db.collection("messes").document(mess.id)
        val docRef = messRef.collection("expenses").document()

        val status = if (autoApprove) "approved" else "pending"
        val payload = mutableMapOf<String, Any>(
            "messId" to mess.id,
            "monthKey" to monthKey,
            "date" to date,
            "category" to category,
            "description" to description.trim().take(200),
            "amountPaisa" to amountPaisa,
            "purchasedByUid" to uid,
            "purchasedByName" to actorName.ifBlank { "Member" }.take(80),
            "status" to status,
            "memberUids" to mess.memberUids,
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp(),
        )
        if (!targetMemberUid.isNullOrBlank()) payload["targetMemberUid"] = targetMemberUid
        if (!receiptUri.isNullOrBlank()) payload["receiptUri"] = receiptUri.take(500)
        if (autoApprove) payload["approvedByUid"] = uid

        val batch = db.batch()
        batch.set(docRef, payload)
        batch.set(
            messRef.collection("audit_logs").document(),
            auditMap(mess, "EXPENSE_ADDED", "Added $category expense '${description.trim()}' ($status)", uid, actorName),
        )
        batch.commit().await()
        Unit
    }.onFailure {
        handleFirestoreError(it, OperationType.CREATE, "messes/${mess.id}/expenses")
    }

    suspend fun setExpenseApproval(
        mess: Mess,
        expense: Expense,
        newStatus: String,
        actorName: String,
    ): Result<Unit> = runCatching {
        val uid = requireUserId()
        require(!mess.isMonthClosed(expense.monthKey)) { "Month ${expense.monthKey} is closed." }
        val messRef = db.collection("messes").document(mess.id)
        val batch = db.batch()
        batch.update(
            messRef.collection("expenses").document(expense.id),
            mapOf(
                "status" to newStatus,
                "approvedByUid" to uid,
                "updatedAt" to FieldValue.serverTimestamp(),
            ),
        )
        batch.set(
            messRef.collection("audit_logs").document(),
            auditMap(mess, "EXPENSE_${newStatus.uppercase()}", "Marked '${expense.description}' as $newStatus", uid, actorName),
        )
        batch.commit().await()
        Unit
    }.onFailure {
        handleFirestoreError(it, OperationType.UPDATE, "messes/${mess.id}/expenses/${expense.id}")
    }

    suspend fun deleteExpense(mess: Mess, expense: Expense, actorName: String): Result<Unit> = runCatching {
        val uid = requireUserId()
        require(!mess.isMonthClosed(expense.monthKey)) { "Month ${expense.monthKey} is closed." }
        val messRef = db.collection("messes").document(mess.id)
        val batch = db.batch()
        batch.delete(messRef.collection("expenses").document(expense.id))
        batch.set(
            messRef.collection("audit_logs").document(),
            auditMap(mess, "EXPENSE_DELETED", "Deleted expense '${expense.description}'", uid, actorName),
        )
        batch.commit().await()
        Unit
    }.onFailure {
        handleFirestoreError(it, OperationType.DELETE, "messes/${mess.id}/expenses/${expense.id}")
    }

    // endregion

    // region Deposits & Adjustments

    fun observeDeposits(messId: String, monthKey: String, currentUserId: String): Flow<List<Deposit>> =
        db.collection("messes").document(messId).collection("deposits")
            .whereArrayContains("memberUids", currentUserId)
            .whereEqualTo("monthKey", monthKey)
            .snapshots()
            .map { snapshot ->
                snapshot.toObjects(Deposit::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                    .sortedByDescending { it.date }
            }
            .catch { error ->
                handleFlowError(error, OperationType.LIST, "messes/$messId/deposits", currentUserId)
            }

    suspend fun addDeposit(
        mess: Mess,
        monthKey: String,
        date: String,
        memberUid: String,
        memberName: String,
        amountPaisa: Long,
        method: String,
        note: String?,
        actorName: String,
    ): Result<Unit> = runCatching {
        val uid = requireUserId()
        require(!mess.isMonthClosed(monthKey)) { "Month $monthKey is closed." }
        require(amountPaisa > 0L) { "Deposit amount must be positive." }
        val messRef = db.collection("messes").document(mess.id)
        val payload = mutableMapOf<String, Any>(
            "messId" to mess.id,
            "monthKey" to monthKey,
            "date" to date,
            "memberUid" to memberUid,
            "memberName" to memberName.take(80),
            "amountPaisa" to amountPaisa,
            "method" to method,
            "recordedByUid" to uid,
            "memberUids" to mess.memberUids,
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp(),
        )
        if (!note.isNullOrBlank()) payload["note"] = note.trim().take(150)

        val batch = db.batch()
        batch.set(messRef.collection("deposits").document(), payload)
        batch.set(
            messRef.collection("audit_logs").document(),
            auditMap(mess, "DEPOSIT_RECORDED", "Recorded $method deposit for $memberName", uid, actorName),
        )
        batch.commit().await()
        Unit
    }.onFailure {
        handleFirestoreError(it, OperationType.CREATE, "messes/${mess.id}/deposits")
    }

    suspend fun updateDeposit(
        mess: Mess,
        deposit: Deposit,
        newAmountPaisa: Long,
        newMethod: String,
        newNote: String?,
        actorName: String,
    ): Result<Unit> = runCatching {
        val uid = requireUserId()
        require(!mess.isMonthClosed(deposit.monthKey)) { "Month ${deposit.monthKey} is closed." }
        require(newAmountPaisa > 0L) { "Deposit amount must be positive." }
        val messRef = db.collection("messes").document(mess.id)
        val updates = mutableMapOf<String, Any>(
            "amountPaisa" to newAmountPaisa,
            "method" to newMethod,
            "updatedAt" to FieldValue.serverTimestamp(),
        )
        updates["note"] = newNote?.trim()?.take(150)?.takeIf { it.isNotEmpty() } ?: FieldValue.delete()

        val batch = db.batch()
        batch.update(messRef.collection("deposits").document(deposit.id), updates)
        batch.set(
            messRef.collection("audit_logs").document(),
            auditMap(mess, "DEPOSIT_UPDATED", "Updated deposit for ${deposit.memberName} on ${deposit.date}", uid, actorName),
        )
        batch.commit().await()
        Unit
    }.onFailure {
        handleFirestoreError(it, OperationType.UPDATE, "messes/${mess.id}/deposits/${deposit.id}")
    }

    suspend fun deleteDeposit(mess: Mess, deposit: Deposit, actorName: String): Result<Unit> = runCatching {
        val uid = requireUserId()
        require(!mess.isMonthClosed(deposit.monthKey)) { "Month ${deposit.monthKey} is closed." }
        val messRef = db.collection("messes").document(mess.id)
        val batch = db.batch()
        batch.delete(messRef.collection("deposits").document(deposit.id))
        batch.set(
            messRef.collection("audit_logs").document(),
            auditMap(mess, "DEPOSIT_DELETED", "Deleted deposit for ${deposit.memberName}", uid, actorName),
        )
        batch.commit().await()
        Unit
    }.onFailure {
        handleFirestoreError(it, OperationType.DELETE, "messes/${mess.id}/deposits/${deposit.id}")
    }

    fun observeAdjustments(messId: String, monthKey: String, currentUserId: String): Flow<List<Adjustment>> =
        db.collection("messes").document(messId).collection("adjustments")
            .whereArrayContains("memberUids", currentUserId)
            .whereEqualTo("monthKey", monthKey)
            .snapshots()
            .map { snapshot ->
                snapshot.toObjects(Adjustment::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
            }
            .catch { error ->
                handleFlowError(error, OperationType.LIST, "messes/$messId/adjustments", currentUserId)
            }

    suspend fun addAdjustment(
        mess: Mess,
        monthKey: String,
        memberUid: String,
        memberName: String,
        amountPaisa: Long,
        reason: String,
        actorName: String,
    ): Result<Unit> = runCatching {
        val uid = requireUserId()
        require(!mess.isMonthClosed(monthKey)) { "Month $monthKey is closed." }
        val messRef = db.collection("messes").document(mess.id)
        val batch = db.batch()
        batch.set(
            messRef.collection("adjustments").document(),
            mapOf(
                "messId" to mess.id,
                "monthKey" to monthKey,
                "memberUid" to memberUid,
                "memberName" to memberName.take(80),
                "amountPaisa" to amountPaisa,
                "reason" to reason.trim().take(200),
                "recordedByUid" to uid,
                "memberUids" to mess.memberUids,
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp(),
            ),
        )
        batch.set(
            messRef.collection("audit_logs").document(),
            auditMap(mess, "ADJUSTMENT_ADDED", "Added adjustment for $memberName: ${reason.trim()}", uid, actorName),
        )
        batch.commit().await()
        Unit
    }.onFailure {
        handleFirestoreError(it, OperationType.CREATE, "messes/${mess.id}/adjustments")
    }

    // endregion

    // region Monthly Closing

    fun observeClosings(messId: String, currentUserId: String): Flow<List<MonthlyClosing>> =
        db.collection("messes").document(messId).collection("closings")
            .whereArrayContains("memberUids", currentUserId)
            .snapshots()
            .map { snapshot ->
                snapshot.toObjects(MonthlyClosing::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                    .sortedByDescending { it.monthKey }
            }
            .catch { error ->
                handleFlowError(error, OperationType.LIST, "messes/$messId/closings", currentUserId)
            }

    suspend fun closeMonth(
        mess: Mess,
        monthKey: String,
        totalMealExpensePaisa: Long,
        totalMealsText: String,
        mealRateText: String,
        totalDepositPaisa: Long,
        totalExpensePaisa: Long,
        actorName: String,
    ): Result<Unit> = runCatching {
        val uid = requireUserId()
        val messRef = db.collection("messes").document(mess.id)
        val newClosed = (mess.closedMonths + monthKey).distinct().sortedDescending().take(60)
        val batch = db.batch()
        batch.update(
            messRef,
            mapOf(
                "closedMonths" to newClosed,
                "updatedAt" to FieldValue.serverTimestamp(),
            ),
        )
        batch.set(
            messRef.collection("closings").document(monthKey),
            mapOf(
                "messId" to mess.id,
                "monthKey" to monthKey,
                "totalMealExpensePaisa" to totalMealExpensePaisa,
                "totalMealsText" to totalMealsText,
                "mealRateText" to mealRateText,
                "totalDepositPaisa" to totalDepositPaisa,
                "totalExpensePaisa" to totalExpensePaisa,
                "closedByUid" to uid,
                "closedByName" to actorName.ifBlank { "Manager" }.take(80),
                "memberUids" to mess.memberUids,
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp(),
            ),
        )
        batch.set(
            messRef.collection("audit_logs").document(),
            auditMap(mess, "MONTH_CLOSED", "Closed & locked month $monthKey (Meal rate: $mealRateText)", uid, actorName),
        )
        batch.commit().await()
        Unit
    }.onFailure {
        handleFirestoreError(it, OperationType.WRITE, "messes/${mess.id}/closings/$monthKey")
    }

    suspend fun reopenMonth(mess: Mess, monthKey: String, actorName: String): Result<Unit> = runCatching {
        val uid = requireUserId()
        val messRef = db.collection("messes").document(mess.id)
        val newClosed = mess.closedMonths.filter { it != monthKey }
        val batch = db.batch()
        batch.update(
            messRef,
            mapOf("closedMonths" to newClosed, "updatedAt" to FieldValue.serverTimestamp()),
        )
        batch.delete(messRef.collection("closings").document(monthKey))
        batch.set(
            messRef.collection("audit_logs").document(),
            auditMap(mess, "MONTH_REOPENED", "Reopened month $monthKey", uid, actorName),
        )
        batch.commit().await()
        Unit
    }.onFailure {
        handleFirestoreError(it, OperationType.UPDATE, "messes/${mess.id}")
    }

    // endregion

    // region Announcements, Group Chat & Audit Logs

    fun observeAnnouncements(messId: String, currentUserId: String): Flow<List<Announcement>> =
        db.collection("messes").document(messId).collection("announcements")
            .whereArrayContains("memberUids", currentUserId)
            .snapshots()
            .map { snapshot ->
                snapshot.toObjects(Announcement::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                    .sortedWith(compareByDescending<Announcement> { it.pinned }.thenByDescending { it.createdAt?.seconds ?: 0L })
            }
            .catch { error ->
                handleFlowError(error, OperationType.LIST, "messes/$messId/announcements", currentUserId)
            }

    suspend fun postAnnouncement(
        mess: Mess,
        title: String,
        body: String,
        pinned: Boolean,
        actorName: String,
    ): Result<Unit> = runCatching {
        val uid = requireUserId()
        val messRef = db.collection("messes").document(mess.id)
        val batch = db.batch()
        batch.set(
            messRef.collection("announcements").document(),
            mapOf(
                "messId" to mess.id,
                "title" to title.trim().take(100),
                "body" to body.trim().take(1000),
                "authorUid" to uid,
                "authorName" to actorName.ifBlank { "Manager" }.take(80),
                "pinned" to pinned,
                "memberUids" to mess.memberUids,
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp(),
            ),
        )
        batch.set(
            messRef.collection("audit_logs").document(),
            auditMap(mess, "ANNOUNCEMENT_POSTED", "Posted notice: ${title.trim()}", uid, actorName),
        )
        batch.commit().await()
        Unit
    }.onFailure {
        handleFirestoreError(it, OperationType.CREATE, "messes/${mess.id}/announcements")
    }

    suspend fun deleteAnnouncement(mess: Mess, announcementId: String): Result<Unit> = runCatching {
        requireUserId()
        db.collection("messes").document(mess.id)
            .collection("announcements").document(announcementId).delete().await()
        Unit
    }.onFailure {
        handleFirestoreError(it, OperationType.DELETE, "messes/${mess.id}/announcements/$announcementId")
    }

    fun observeChatMessages(messId: String, currentUserId: String): Flow<List<ChatMessage>> =
        db.collection("messes").document(messId).collection("chat")
            .whereArrayContains("memberUids", currentUserId)
            .orderBy("createdAt", Query.Direction.ASCENDING)
            .limitToLast(100)
            .snapshots()
            .map { snapshot ->
                snapshot.toObjects(ChatMessage::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
            }
            .catch { error ->
                handleFlowError(error, OperationType.LIST, "messes/$messId/chat", currentUserId)
            }

    suspend fun sendChatMessage(mess: Mess, text: String, senderName: String): Result<Unit> = runCatching {
        val uid = requireUserId()
        val trimmed = text.trim()
        require(trimmed.isNotEmpty()) { "Message cannot be empty." }
        db.collection("messes").document(mess.id).collection("chat").document()
            .set(
                mapOf(
                    "messId" to mess.id,
                    "senderUid" to uid,
                    "senderName" to senderName.ifBlank { "Member" }.take(80),
                    "text" to trimmed.take(1000),
                    "memberUids" to mess.memberUids,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp(),
                ),
            ).await()
        Unit
    }.onFailure {
        handleFirestoreError(it, OperationType.CREATE, "messes/${mess.id}/chat")
    }

    fun observeAuditLogs(messId: String, currentUserId: String): Flow<List<AuditLog>> =
        db.collection("messes").document(messId).collection("audit_logs")
            .whereArrayContains("memberUids", currentUserId)
            .snapshots()
            .map { snapshot ->
                snapshot.toObjects(AuditLog::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                    .sortedByDescending { it.createdAt?.seconds ?: 0L }
            }
            .catch { error ->
                handleFlowError(error, OperationType.LIST, "messes/$messId/audit_logs", currentUserId)
            }

    // endregion

    private fun auditMap(
        mess: Mess,
        action: String,
        details: String,
        actorUid: String,
        actorName: String,
    ): Map<String, Any> = mapOf(
        "messId" to mess.id,
        "action" to action.take(60),
        "details" to details.take(300),
        "actorUid" to actorUid,
        "actorName" to actorName.ifBlank { "Member" }.take(80),
        "memberUids" to mess.memberUids,
        "createdAt" to FieldValue.serverTimestamp(),
    )

    private fun handleFlowError(
        error: Throwable,
        operationType: OperationType,
        path: String,
        expectedUid: String?,
    ): Nothing {
        val firestoreError = generateSequence(error) { it.cause }
            .filterIsInstance<FirebaseFirestoreException>()
            .firstOrNull()
        if (firestoreError == null && error is CancellationException) throw error
        val exceptionToLog = firestoreError ?: (error as? Exception)
        if (exceptionToLog != null && (expectedUid == null || auth.currentUser?.uid == expectedUid)) {
            handleFirestoreError(exceptionToLog, operationType, path)
        }
        throw error
    }
}
