package com.example.ui.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.firebase.toFriendlyFirestoreMessage
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
import com.example.data.repository.MessRepository
import com.example.domain.FinanceCalculator
import com.example.domain.MonthlyFinanceSummary
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

sealed interface UiState<out T> {
    data object Loading : UiState<Nothing>
    data class Success<T>(val data: T) : UiState<T>
    data class Error(val message: String) : UiState<Nothing>
}

data class FeedbackMessage(
    val text: String,
    val isError: Boolean = false,
)

@OptIn(ExperimentalCoroutinesApi::class)
class MessViewModel(
    private val repository: MessRepository,
    val currentUserId: String,
    private val initialDisplayName: String,
    private val initialEmail: String?,
    private val initialPhotoUrl: String?,
) : ViewModel() {

    private val _selectedMonthKey = MutableStateFlow(currentMonthKey())
    val selectedMonthKey: StateFlow<String> = _selectedMonthKey.asStateFlow()

    private val _selectedDate = MutableStateFlow(currentDateKey())
    val selectedDate: StateFlow<String> = _selectedDate.asStateFlow()

    private val _feedback = MutableStateFlow<FeedbackMessage?>(null)
    val feedback: StateFlow<FeedbackMessage?> = _feedback.asStateFlow()

    private val _isBusy = MutableStateFlow(false)
    val isBusy: StateFlow<Boolean> = _isBusy.asStateFlow()

    private val _lookedUpInvite = MutableStateFlow<MessInvite?>(null)
    val lookedUpInvite: StateFlow<MessInvite?> = _lookedUpInvite.asStateFlow()

    init {
        viewModelScope.launch {
            repository.ensureUserProfile(initialDisplayName, initialEmail, initialPhotoUrl)
        }
    }

    val userProfile: StateFlow<UserProfile?> = repository.observeUserProfile(currentUserId)
        .safeCatch("userProfile") { null }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), null)

    val messesState: StateFlow<UiState<List<Mess>>> = repository.observeUserMesses(currentUserId)
        .map<List<Mess>, UiState<List<Mess>>> { UiState.Success(it) }
        .catch { error ->
            val root = unwrapError(error)
            Log.w(TAG, "Error observing messes", root)
            emit(UiState.Error(root.toFriendlyFirestoreMessage("Failed to load messes")))
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), UiState.Loading)

    val activeMess: StateFlow<Mess?> = combine(messesState, userProfile) { state, profile ->
        val list = (state as? UiState.Success)?.data ?: return@combine null
        val prefId = profile?.activeMessId
        list.firstOrNull { it.id == prefId } ?: list.firstOrNull()
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), null)

    val members: StateFlow<List<MessMember>> = activeMess
        .flatMapLatest { mess ->
            if (mess == null) flowOf(emptyList())
            else repository.observeMembers(mess.id, currentUserId).safeCatch("members") { emptyList() }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), emptyList())

    val joinRequests: StateFlow<List<JoinRequest>> = activeMess
        .flatMapLatest { mess ->
            if (mess == null || !mess.roleFor(currentUserId).canManageMembersAndClosing()) {
                flowOf(emptyList())
            } else {
                repository.observeJoinRequests(mess.id, currentUserId).safeCatch("joinRequests") { emptyList() }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), emptyList())

    val meals: StateFlow<List<MealEntry>> = combine(activeMess, selectedMonthKey) { mess, month -> mess to month }
        .flatMapLatest { (mess, month) ->
            if (mess == null) flowOf(emptyList())
            else repository.observeMeals(mess.id, month, currentUserId).safeCatch("meals") { emptyList() }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), emptyList())

    val expenses: StateFlow<List<Expense>> = combine(activeMess, selectedMonthKey) { mess, month -> mess to month }
        .flatMapLatest { (mess, month) ->
            if (mess == null) flowOf(emptyList())
            else repository.observeExpenses(mess.id, month, currentUserId).safeCatch("expenses") { emptyList() }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), emptyList())

    val deposits: StateFlow<List<Deposit>> = combine(activeMess, selectedMonthKey) { mess, month -> mess to month }
        .flatMapLatest { (mess, month) ->
            if (mess == null) flowOf(emptyList())
            else repository.observeDeposits(mess.id, month, currentUserId).safeCatch("deposits") { emptyList() }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), emptyList())

    val adjustments: StateFlow<List<Adjustment>> = combine(activeMess, selectedMonthKey) { mess, month -> mess to month }
        .flatMapLatest { (mess, month) ->
            if (mess == null) flowOf(emptyList())
            else repository.observeAdjustments(mess.id, month, currentUserId).safeCatch("adjustments") { emptyList() }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), emptyList())

    val closings: StateFlow<List<MonthlyClosing>> = activeMess
        .flatMapLatest { mess ->
            if (mess == null) flowOf(emptyList())
            else repository.observeClosings(mess.id, currentUserId).safeCatch("closings") { emptyList() }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), emptyList())

    val announcements: StateFlow<List<Announcement>> = activeMess
        .flatMapLatest { mess ->
            if (mess == null) flowOf(emptyList())
            else repository.observeAnnouncements(mess.id, currentUserId).safeCatch("announcements") { emptyList() }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), emptyList())

    val chatMessages: StateFlow<List<ChatMessage>> = activeMess
        .flatMapLatest { mess ->
            if (mess == null) flowOf(emptyList())
            else repository.observeChatMessages(mess.id, currentUserId).safeCatch("chat") { emptyList() }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), emptyList())

    val auditLogs: StateFlow<List<AuditLog>> = activeMess
        .flatMapLatest { mess ->
            if (mess == null) flowOf(emptyList())
            else repository.observeAuditLogs(mess.id, currentUserId).safeCatch("auditLogs") { emptyList() }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), emptyList())

    val monthlySummary: StateFlow<MonthlyFinanceSummary> = combine(
        selectedMonthKey,
        members,
        meals,
        expenses,
        deposits,
        adjustments,
    ) { args ->
        val month = args[0] as String
        @Suppress("UNCHECKED_CAST")
        val memberList = args[1] as List<MessMember>
        @Suppress("UNCHECKED_CAST")
        val mealList = args[2] as List<MealEntry>
        @Suppress("UNCHECKED_CAST")
        val expenseList = args[3] as List<Expense>
        @Suppress("UNCHECKED_CAST")
        val depositList = args[4] as List<Deposit>
        @Suppress("UNCHECKED_CAST")
        val adjustmentList = args[5] as List<Adjustment>

        FinanceCalculator.computeMonthlySummary(
            monthKey = month,
            todayDate = currentDateKey(),
            members = memberList,
            meals = mealList,
            expenses = expenseList,
            deposits = depositList,
            adjustments = adjustmentList,
        )
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
        FinanceCalculator.computeMonthlySummary(
            monthKey = currentMonthKey(),
            todayDate = currentDateKey(),
            members = emptyList(),
            meals = emptyList(),
            expenses = emptyList(),
            deposits = emptyList(),
            adjustments = emptyList(),
        ),
    )

    private fun currentActorName(): String =
        userProfile.value?.displayName?.takeIf { it.isNotBlank() }
            ?: initialDisplayName.ifBlank { "Member" }

    fun clearFeedback() {
        _feedback.value = null
    }

    fun showFeedback(text: String, isError: Boolean = false) {
        _feedback.value = FeedbackMessage(text, isError)
    }

    fun clearInvitePreview() {
        _lookedUpInvite.value = null
    }

    fun shiftMonth(delta: Int) {
        val sdf = SimpleDateFormat("yyyy-MM", Locale.US)
        val cal = Calendar.getInstance()
        cal.time = runCatching { sdf.parse(_selectedMonthKey.value) }.getOrNull() ?: Date()
        cal.add(Calendar.MONTH, delta)
        val nextMonth = sdf.format(cal.time)
        _selectedMonthKey.value = nextMonth
        // Keep selectedDate inside the selected month
        val today = currentDateKey()
        _selectedDate.value = if (today.startsWith(nextMonth)) today else "$nextMonth-01"
    }

    fun selectDate(date: String) {
        _selectedDate.value = date
        if (date.length >= 7) {
            _selectedMonthKey.value = date.substring(0, 7)
        }
    }

    fun switchMess(messId: String) {
        viewModelScope.launch {
            repository.setActiveMess(messId)
                .onFailure { _feedback.value = FeedbackMessage(it.toFriendlyFirestoreMessage("Failed to switch mess"), true) }
        }
    }

    fun setThemeMode(mode: String) {
        viewModelScope.launch {
            repository.updateThemeMode(mode)
                .onSuccess { _feedback.value = FeedbackMessage("Theme updated to ${mode.replaceFirstChar { it.uppercase() }}") }
                .onFailure { _feedback.value = FeedbackMessage(it.toFriendlyFirestoreMessage("Failed to update theme"), true) }
        }
    }

    fun updateUserProfile(displayName: String, phone: String?) {
        runAction("Profile updated") {
            repository.updateUserProfile(displayName, phone)
        }
    }

    fun setAccountDeactivated(deactivated: Boolean) {
        runAction(if (deactivated) "Account deactivated" else "Account reactivated") {
            repository.setAccountDeactivated(deactivated)
        }
    }

    fun createMess(
        name: String,
        address: String,
        description: String,
        currencySymbol: String,
        availableSeats: Long,
        seatRentInfo: String,
        onSuccess: () -> Unit = {},
    ) {
        viewModelScope.launch {
            _isBusy.value = true
            repository.createMess(
                name = name,
                address = address,
                description = description,
                currencySymbol = currencySymbol,
                availableSeats = availableSeats,
                seatRentInfo = seatRentInfo,
                ownerDisplayName = currentActorName(),
            ).onSuccess {
                _feedback.value = FeedbackMessage("Created '$name'")
                onSuccess()
            }.onFailure {
                _feedback.value = FeedbackMessage(it.toFriendlyFirestoreMessage("Failed to create mess"), true)
            }
            _isBusy.value = false
        }
    }

    fun updateMessSettings(
        mess: Mess,
        name: String,
        address: String,
        description: String,
        currencySymbol: String,
        allowMultipleManagers: Boolean,
        availableSeats: Long,
        seatRentInfo: String,
        onSuccess: () -> Unit = {},
    ) {
        runAction("Mess settings saved", onSuccess) {
            repository.updateMessSettings(
                mess = mess,
                name = name,
                address = address,
                description = description,
                currencySymbol = currencySymbol,
                allowMultipleManagers = allowMultipleManagers,
                availableSeats = availableSeats,
                seatRentInfo = seatRentInfo,
                actorName = currentActorName(),
            )
        }
    }

    fun rotateInviteCode(mess: Mess) {
        viewModelScope.launch {
            _isBusy.value = true
            repository.rotateInviteCode(mess, currentActorName())
                .onSuccess { code -> _feedback.value = FeedbackMessage("New invite code: $code") }
                .onFailure { _feedback.value = FeedbackMessage(it.toFriendlyFirestoreMessage("Failed to rotate code"), true) }
            _isBusy.value = false
        }
    }

    fun archiveMess(mess: Mess) {
        runAction("Mess '${mess.name}' archived") {
            repository.archiveMess(mess, currentActorName())
        }
    }

    fun lookupInvite(code: String) {
        viewModelScope.launch {
            _isBusy.value = true
            repository.lookupInvite(code)
                .onSuccess { _lookedUpInvite.value = it }
                .onFailure {
                    _lookedUpInvite.value = null
                    _feedback.value = FeedbackMessage(it.toFriendlyFirestoreMessage("Invite not found"), true)
                }
            _isBusy.value = false
        }
    }

    fun submitJoinRequest(invite: MessInvite, onSuccess: () -> Unit = {}) {
        viewModelScope.launch {
            _isBusy.value = true
            repository.submitJoinRequest(invite, currentActorName())
                .onSuccess {
                    _lookedUpInvite.value = null
                    _feedback.value = FeedbackMessage("Join request sent to ${invite.messName}. Waiting for manager approval.")
                    onSuccess()
                }
                .onFailure {
                    _feedback.value = FeedbackMessage(it.toFriendlyFirestoreMessage("Could not send join request"), true)
                }
            _isBusy.value = false
        }
    }

    fun approveJoinRequest(mess: Mess, request: JoinRequest) {
        runAction("Approved ${request.applicantName}") {
            repository.approveJoinRequest(mess, request, currentActorName())
        }
    }

    fun rejectJoinRequest(mess: Mess, request: JoinRequest) {
        runAction("Declined ${request.applicantName}") {
            repository.rejectJoinRequest(mess, request, currentActorName())
        }
    }

    fun updateMemberRoleAndStatus(
        mess: Mess,
        member: MessMember,
        role: MessRole,
        status: String,
    ) {
        runAction("Updated ${member.displayName}") {
            repository.updateMemberRoleAndStatus(mess, member, role, status, currentActorName())
        }
    }

    fun transferOwnership(mess: Mess, newOwner: MessMember) {
        runAction("Transferred ownership to ${newOwner.displayName}") {
            repository.transferOwnership(mess, newOwner, currentActorName())
        }
    }

    fun removeMember(mess: Mess, member: MessMember) {
        runAction("Removed ${member.displayName}") {
            repository.removeMember(mess, member, currentActorName())
        }
    }

    fun upsertMeal(
        mess: Mess,
        date: String,
        memberUid: String,
        memberName: String,
        breakfast: Double,
        lunch: Double,
        dinner: Double,
        onSuccess: () -> Unit = {},
    ) {
        val monthKey = date.take(7)
        runAction("Saved meals for $memberName", onSuccess) {
            repository.upsertMealEntry(
                mess = mess,
                monthKey = monthKey,
                date = date,
                memberUid = memberUid,
                memberName = memberName,
                breakfast = breakfast,
                lunch = lunch,
                dinner = dinner,
                actorName = currentActorName(),
            )
        }
    }

    fun deleteMeal(mess: Mess, meal: MealEntry) {
        runAction("Meal entry removed") {
            repository.deleteMealEntry(mess, meal, currentActorName())
        }
    }

    fun addExpense(
        mess: Mess,
        date: String,
        category: String,
        description: String,
        amountPaisa: Long,
        targetMemberUid: String?,
        receiptUri: String?,
        onSuccess: () -> Unit = {},
    ) {
        val monthKey = date.take(7)
        val autoApprove = mess.roleFor(currentUserId).canManageFinances()
        val msg = if (autoApprove) "Expense recorded" else "Submitted for manager approval"
        runAction(msg, onSuccess) {
            repository.addExpense(
                mess = mess,
                monthKey = monthKey,
                date = date,
                category = category,
                description = description,
                amountPaisa = amountPaisa,
                targetMemberUid = targetMemberUid,
                receiptUri = receiptUri,
                autoApprove = autoApprove,
                actorName = currentActorName(),
            )
        }
    }

    fun setExpenseApproval(mess: Mess, expense: Expense, status: String) {
        runAction("Expense marked $status") {
            repository.setExpenseApproval(mess, expense, status, currentActorName())
        }
    }

    fun deleteExpense(mess: Mess, expense: Expense) {
        runAction("Expense deleted") {
            repository.deleteExpense(mess, expense, currentActorName())
        }
    }

    fun addDeposit(
        mess: Mess,
        date: String,
        memberUid: String,
        memberName: String,
        amountPaisa: Long,
        method: String,
        note: String?,
        onSuccess: () -> Unit = {},
    ) {
        val monthKey = date.take(7)
        runAction("Deposit recorded for $memberName", onSuccess) {
            repository.addDeposit(
                mess = mess,
                monthKey = monthKey,
                date = date,
                memberUid = memberUid,
                memberName = memberName,
                amountPaisa = amountPaisa,
                method = method,
                note = note,
                actorName = currentActorName(),
            )
        }
    }

    fun updateDeposit(
        mess: Mess,
        deposit: Deposit,
        newAmountPaisa: Long,
        newMethod: String,
        newNote: String?,
        onSuccess: () -> Unit = {},
    ) {
        runAction("Deposit updated", onSuccess) {
            repository.updateDeposit(mess, deposit, newAmountPaisa, newMethod, newNote, currentActorName())
        }
    }

    fun deleteDeposit(mess: Mess, deposit: Deposit) {
        runAction("Deposit deleted") {
            repository.deleteDeposit(mess, deposit, currentActorName())
        }
    }

    fun addAdjustment(
        mess: Mess,
        memberUid: String,
        memberName: String,
        amountPaisa: Long,
        reason: String,
        onSuccess: () -> Unit = {},
    ) {
        runAction("Adjustment applied", onSuccess) {
            repository.addAdjustment(
                mess = mess,
                monthKey = _selectedMonthKey.value,
                memberUid = memberUid,
                memberName = memberName,
                amountPaisa = amountPaisa,
                reason = reason,
                actorName = currentActorName(),
            )
        }
    }

    fun closeSelectedMonth(mess: Mess, summary: MonthlyFinanceSummary) {
        runAction("Month ${summary.monthKey} closed & locked") {
            repository.closeMonth(
                mess = mess,
                monthKey = summary.monthKey,
                totalMealExpensePaisa = summary.totalMealExpensePaisa,
                totalMealsText = FinanceCalculator.formatMealCount(summary.totalMeals),
                mealRateText = summary.mealRateDisplay,
                totalDepositPaisa = summary.totalDepositPaisa,
                totalExpensePaisa = summary.totalExpensePaisa,
                actorName = currentActorName(),
            )
        }
    }

    fun reopenSelectedMonth(mess: Mess, monthKey: String) {
        runAction("Month $monthKey reopened") {
            repository.reopenMonth(mess, monthKey, currentActorName())
        }
    }

    fun postAnnouncement(mess: Mess, title: String, body: String, pinned: Boolean, onSuccess: () -> Unit = {}) {
        runAction("Announcement posted", onSuccess) {
            repository.postAnnouncement(mess, title, body, pinned, currentActorName())
        }
    }

    fun deleteAnnouncement(mess: Mess, id: String) {
        runAction("Announcement deleted") {
            repository.deleteAnnouncement(mess, id)
        }
    }

    fun sendChatMessage(mess: Mess, text: String) {
        viewModelScope.launch {
            repository.sendChatMessage(mess, text, currentActorName())
                .onFailure { _feedback.value = FeedbackMessage(it.toFriendlyFirestoreMessage("Message failed"), true) }
        }
    }

    private fun runAction(
        successMessage: String,
        onSuccess: () -> Unit = {},
        block: suspend () -> Result<Unit>,
    ) {
        viewModelScope.launch {
            _isBusy.value = true
            block()
                .onSuccess {
                    _feedback.value = FeedbackMessage(successMessage)
                    onSuccess()
                }
                .onFailure {
                    _feedback.value = FeedbackMessage(it.toFriendlyFirestoreMessage("Action failed"), true)
                }
            _isBusy.value = false
        }
    }

    private fun <T> Flow<T>.safeCatch(label: String, fallback: () -> T): Flow<T> = catch { error ->
        val firestoreError = unwrapError(error)
        if (firestoreError == null && error is CancellationException) throw error
        Log.w(TAG, "Error observing $label", firestoreError ?: error)
        emit(fallback())
    }

    private fun unwrapError(error: Throwable): Throwable {
        val firestoreError = generateSequence(error) { it.cause }
            .filterIsInstance<FirebaseFirestoreException>()
            .firstOrNull()
        if (firestoreError == null && error is CancellationException) throw error
        return firestoreError ?: error
    }

    companion object {
        private const val TAG = "MessViewModel"
        private const val STOP_TIMEOUT_MS = 5000L

        fun currentMonthKey(): String =
            SimpleDateFormat("yyyy-MM", Locale.US).format(Date())

        fun currentDateKey(): String =
            SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
    }
}
