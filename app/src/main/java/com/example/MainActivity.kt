package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccountBalanceWallet
import androidx.compose.material.icons.rounded.AddBusiness
import androidx.compose.material.icons.rounded.Dashboard
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Forum
import androidx.compose.material.icons.rounded.GroupAdd
import androidx.compose.material.icons.rounded.ManageAccounts
import androidx.compose.material.icons.rounded.Restaurant
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.data.model.Mess
import com.example.data.repository.MessRepository
import com.example.ui.auth.AuthScreen
import com.example.ui.auth.GoogleSignInHelper
import com.example.ui.components.CreateMessDialog
import com.example.ui.components.EditProfileDialog
import com.example.ui.components.JoinMessDialog
import com.example.ui.components.MessSwitcherSheet
import com.example.ui.screens.CommunityScreen
import com.example.ui.screens.DashboardScreen
import com.example.ui.screens.FinanceScreen
import com.example.ui.screens.MealsScreen
import com.example.ui.screens.MessHubEmptyScreen
import com.example.ui.theme.MessMalikTheme
import com.example.ui.viewmodel.MessViewModel
import com.example.ui.viewmodel.UiState
import com.example.util.ReportExporter
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.auth
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

private enum class MessTab(val label: String, val icon: ImageVector) {
    DASHBOARD("Dashboard", Icons.Rounded.Dashboard),
    MEALS("Meals", Icons.Rounded.Restaurant),
    FINANCE("Finance", Icons.Rounded.AccountBalanceWallet),
    COMMUNITY("Community", Icons.Rounded.Forum),
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val currentUser by Firebase.auth.authStateFlow()
                .collectAsStateWithLifecycle(initialValue = Firebase.auth.currentUser)

            val user = currentUser
            if (user == null) {
                MessMalikTheme(themeMode = "system") {
                    AuthScreen(onAuthSuccess = {})
                }
            } else {
                key(user.uid) {
                    val context = LocalContext.current
                    val viewModel: MessViewModel = viewModel(
                        key = user.uid,
                        factory = viewModelFactory {
                            initializer {
                                MessViewModel(
                                    repository = MessRepository(context.applicationContext),
                                    currentUserId = user.uid,
                                    initialDisplayName = user.displayName ?: "Mess Member",
                                    initialEmail = user.email,
                                    initialPhotoUrl = user.photoUrl?.toString(),
                                )
                            }
                        },
                    )
                    MessMalikAppRoot(viewModel = viewModel)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MessMalikAppRoot(viewModel: MessViewModel) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val credentialManager = remember(context) { CredentialManager.create(context) }

    val userProfile by viewModel.userProfile.collectAsStateWithLifecycle()
    val messesState by viewModel.messesState.collectAsStateWithLifecycle()
    val activeMess by viewModel.activeMess.collectAsStateWithLifecycle()
    val members by viewModel.members.collectAsStateWithLifecycle()
    val joinRequests by viewModel.joinRequests.collectAsStateWithLifecycle()
    val meals by viewModel.meals.collectAsStateWithLifecycle()
    val expenses by viewModel.expenses.collectAsStateWithLifecycle()
    val deposits by viewModel.deposits.collectAsStateWithLifecycle()
    val adjustments by viewModel.adjustments.collectAsStateWithLifecycle()
    val announcements by viewModel.announcements.collectAsStateWithLifecycle()
    val chatMessages by viewModel.chatMessages.collectAsStateWithLifecycle()
    val auditLogs by viewModel.auditLogs.collectAsStateWithLifecycle()
    val monthlySummary by viewModel.monthlySummary.collectAsStateWithLifecycle()
    val selectedMonthKey by viewModel.selectedMonthKey.collectAsStateWithLifecycle()
    val selectedDate by viewModel.selectedDate.collectAsStateWithLifecycle()
    val feedback by viewModel.feedback.collectAsStateWithLifecycle()
    val isBusy by viewModel.isBusy.collectAsStateWithLifecycle()
    val lookedUpInvite by viewModel.lookedUpInvite.collectAsStateWithLifecycle()

    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(feedback) {
        feedback?.let { msg ->
            snackbarHostState.showSnackbar(msg.text)
            viewModel.clearFeedback()
        }
    }

    var currentTab by remember { mutableStateOf(MessTab.DASHBOARD) }
    var showCreateMess by remember { mutableStateOf(false) }
    var showJoinMess by remember { mutableStateOf(false) }
    var showMessSwitcher by remember { mutableStateOf(false) }
    var showProfileDialog by remember { mutableStateOf(false) }
    var showQuickActionsMenu by remember { mutableStateOf(false) }

    BackHandler(enabled = activeMess != null && currentTab != MessTab.DASHBOARD) {
        currentTab = MessTab.DASHBOARD
    }

    MessMalikTheme(themeMode = userProfile?.themeMode ?: "system") {
        when (val state = messesState) {
            is UiState.Loading -> {
                Scaffold(
                    contentWindowInsets = WindowInsets.safeDrawing,
                    snackbarHost = { SnackbarHost(snackbarHostState) },
                ) { innerPadding ->
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding),
                    ) {
                        CircularProgressIndicator()
                    }
                }
            }

            is UiState.Error -> {
                Scaffold(
                    contentWindowInsets = WindowInsets.safeDrawing,
                    snackbarHost = { SnackbarHost(snackbarHostState) },
                ) { innerPadding ->
                    Column(
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                            .padding(24.dp),
                    ) {
                        Text(
                            text = state.message,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.error,
                        )
                        Spacer(Modifier.size(16.dp))
                        Button(
                            onClick = {
                                GoogleSignInHelper.signOut(credentialManager, scope)
                            },
                        ) {
                            Text("Sign Out")
                        }
                    }
                }
            }

            is UiState.Success -> {
                val messes: List<Mess> = state.data
                val mess = activeMess

                if (mess == null) {
                    Scaffold(
                        contentWindowInsets = WindowInsets.safeDrawing,
                        snackbarHost = { SnackbarHost(snackbarHostState) },
                    ) { innerPadding ->
                        MessHubEmptyScreen(
                            displayName = userProfile?.displayName ?: "Member",
                            onOpenCreateMess = { showCreateMess = true },
                            onOpenJoinMess = {
                                viewModel.clearInvitePreview()
                                showJoinMess = true
                            },
                            onOpenProfile = { showProfileDialog = true },
                            modifier = Modifier.padding(innerPadding),
                        )
                    }
                } else {
                    val pendingJoinCount = if (mess.roleFor(viewModel.currentUserId).canManageMembersAndClosing()) {
                        joinRequests.size
                    } else {
                        0
                    }

                    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                        val useRail = maxWidth >= 600.dp

                        Scaffold(
                            contentWindowInsets = WindowInsets.safeDrawing,
                            snackbarHost = { SnackbarHost(snackbarHostState) },
                            topBar = {
                                TopAppBar(
                                    title = {
                                        Surface(
                                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f),
                                            shape = RoundedCornerShape(12.dp),
                                            modifier = Modifier
                                                .clickable { showMessSwitcher = true }
                                                .testTag("mess_switcher_button"),
                                        ) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                            ) {
                                                Text(
                                                    text = mess.name,
                                                    style = MaterialTheme.typography.titleMedium,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis,
                                                )
                                                Spacer(Modifier.width(4.dp))
                                                Icon(
                                                    imageVector = Icons.Rounded.ExpandMore,
                                                    contentDescription = "Switch mess",
                                                    modifier = Modifier.size(18.dp),
                                                )
                                            }
                                        }
                                    },
                                    actions = {
                                        Box {
                                            IconButton(
                                                onClick = { showQuickActionsMenu = true },
                                                modifier = Modifier.testTag("mess_actions_button"),
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Rounded.AddBusiness,
                                                    contentDescription = "Create or join mess",
                                                )
                                            }
                                            DropdownMenu(
                                                expanded = showQuickActionsMenu,
                                                onDismissRequest = { showQuickActionsMenu = false },
                                            ) {
                                                DropdownMenuItem(
                                                    text = { Text("Create New Mess") },
                                                    leadingIcon = { Icon(Icons.Rounded.AddBusiness, contentDescription = null) },
                                                    onClick = {
                                                        showQuickActionsMenu = false
                                                        showCreateMess = true
                                                    },
                                                )
                                                DropdownMenuItem(
                                                    text = { Text("Join with Invite Code") },
                                                    leadingIcon = { Icon(Icons.Rounded.GroupAdd, contentDescription = null) },
                                                    onClick = {
                                                        showQuickActionsMenu = false
                                                        viewModel.clearInvitePreview()
                                                        showJoinMess = true
                                                    },
                                                )
                                            }
                                        }
                                        IconButton(
                                            onClick = { showProfileDialog = true },
                                            modifier = Modifier.testTag("profile_button"),
                                        ) {
                                            Icon(
                                                imageVector = Icons.Rounded.ManageAccounts,
                                                contentDescription = "Account and theme settings",
                                            )
                                        }
                                    },
                                )
                            },
                            bottomBar = {
                                if (!useRail) {
                                    NavigationBar {
                                        MessTab.entries.forEach { tab ->
                                            NavigationBarItem(
                                                selected = currentTab == tab,
                                                onClick = { currentTab = tab },
                                                icon = {
                                                    if (tab == MessTab.COMMUNITY && pendingJoinCount > 0) {
                                                        BadgedBox(badge = { Badge { Text("$pendingJoinCount") } }) {
                                                            Icon(tab.icon, contentDescription = tab.label)
                                                        }
                                                    } else {
                                                        Icon(tab.icon, contentDescription = tab.label)
                                                    }
                                                },
                                                label = { Text(tab.label) },
                                                modifier = Modifier.testTag("nav_${tab.name.lowercase()}"),
                                            )
                                        }
                                    }
                                }
                            },
                        ) { innerPadding ->
                            Row(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(innerPadding),
                            ) {
                                if (useRail) {
                                    NavigationRail(modifier = Modifier.fillMaxHeight()) {
                                        MessTab.entries.forEach { tab ->
                                            NavigationRailItem(
                                                selected = currentTab == tab,
                                                onClick = { currentTab = tab },
                                                icon = {
                                                    if (tab == MessTab.COMMUNITY && pendingJoinCount > 0) {
                                                        BadgedBox(badge = { Badge { Text("$pendingJoinCount") } }) {
                                                            Icon(tab.icon, contentDescription = tab.label)
                                                        }
                                                    } else {
                                                        Icon(tab.icon, contentDescription = tab.label)
                                                    }
                                                },
                                                label = { Text(tab.label) },
                                                modifier = Modifier.testTag("rail_${tab.name.lowercase()}"),
                                            )
                                        }
                                    }
                                }

                                Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                                    when (currentTab) {
                                        MessTab.DASHBOARD -> DashboardScreen(
                                            mess = mess,
                                            currentUserId = viewModel.currentUserId,
                                            members = members,
                                            summary = monthlySummary,
                                            auditLogs = auditLogs,
                                            onPreviousMonth = { viewModel.shiftMonth(-1) },
                                            onNextMonth = { viewModel.shiftMonth(1) },
                                            onCloseMonth = { viewModel.closeSelectedMonth(mess, monthlySummary) },
                                            onReopenMonth = { viewModel.reopenSelectedMonth(mess, selectedMonthKey) },
                                            onExportPdf = {
                                                ReportExporter.exportMonthlyPdfAndShare(context, mess, monthlySummary)
                                                    .onFailure {
                                                        viewModel.showFeedback(it.localizedMessage ?: "PDF export failed", isError = true)
                                                    }
                                            },
                                            onExportCsv = {
                                                ReportExporter.exportCsvAndShare(context, mess, monthlySummary, expenses, deposits)
                                                    .onFailure {
                                                        viewModel.showFeedback(it.localizedMessage ?: "CSV export failed", isError = true)
                                                    }
                                            },
                                        )

                                        MessTab.MEALS -> MealsScreen(
                                            mess = mess,
                                            currentUserId = viewModel.currentUserId,
                                            monthKey = selectedMonthKey,
                                            selectedDate = selectedDate,
                                            members = members,
                                            meals = meals,
                                            summary = monthlySummary,
                                            onPreviousMonth = { viewModel.shiftMonth(-1) },
                                            onNextMonth = { viewModel.shiftMonth(1) },
                                            onSelectDate = { viewModel.selectDate(it) },
                                            onSaveMeal = { date, uid, name, b, l, d ->
                                                viewModel.upsertMeal(mess, date, uid, name, b, l, d)
                                            },
                                            onDeleteMeal = { meal -> viewModel.deleteMeal(mess, meal) },
                                        )

                                        MessTab.FINANCE -> FinanceScreen(
                                            mess = mess,
                                            currentUserId = viewModel.currentUserId,
                                            monthKey = selectedMonthKey,
                                            todayDate = selectedDate,
                                            members = members,
                                            expenses = expenses,
                                            deposits = deposits,
                                            adjustments = adjustments,
                                            onPreviousMonth = { viewModel.shiftMonth(-1) },
                                            onNextMonth = { viewModel.shiftMonth(1) },
                                            onAddExpense = { date, cat, desc, paisa, targetUid, uri ->
                                                viewModel.addExpense(mess, date, cat, desc, paisa, targetUid, uri)
                                            },
                                            onSetExpenseApproval = { exp, status ->
                                                viewModel.setExpenseApproval(mess, exp, status)
                                            },
                                            onDeleteExpense = { exp -> viewModel.deleteExpense(mess, exp) },
                                            onAddDeposit = { date, uid, name, paisa, method, note ->
                                                viewModel.addDeposit(mess, date, uid, name, paisa, method, note)
                                            },
                                            onUpdateDeposit = { dep, newPaisa, method, note ->
                                                viewModel.updateDeposit(mess, dep, newPaisa, method, note)
                                            },
                                            onDeleteDeposit = { dep -> viewModel.deleteDeposit(mess, dep) },
                                            onAddAdjustment = { uid, name, paisa, reason ->
                                                viewModel.addAdjustment(mess, uid, name, paisa, reason)
                                            },
                                        )

                                        MessTab.COMMUNITY -> CommunityScreen(
                                            mess = mess,
                                            currentUserId = viewModel.currentUserId,
                                            members = members,
                                            joinRequests = joinRequests,
                                            announcements = announcements,
                                            chatMessages = chatMessages,
                                            auditLogs = auditLogs,
                                            isBusy = isBusy,
                                            onPostAnnouncement = { title, body, pinned ->
                                                viewModel.postAnnouncement(mess, title, body, pinned)
                                            },
                                            onDeleteAnnouncement = { id -> viewModel.deleteAnnouncement(mess, id) },
                                            onSendChatMessage = { text -> viewModel.sendChatMessage(mess, text) },
                                            onRotateInviteCode = { viewModel.rotateInviteCode(mess) },
                                            onApproveJoinRequest = { req -> viewModel.approveJoinRequest(mess, req) },
                                            onRejectJoinRequest = { req -> viewModel.rejectJoinRequest(mess, req) },
                                            onUpdateMemberRoleAndStatus = { member, role, status ->
                                                viewModel.updateMemberRoleAndStatus(mess, member, role, status)
                                            },
                                            onTransferOwnership = { target -> viewModel.transferOwnership(mess, target) },
                                            onRemoveMember = { member -> viewModel.removeMember(mess, member) },
                                            onUpdateMessSettings = { name, addr, desc, cur, multi, seats, rentInfo ->
                                                viewModel.updateMessSettings(
                                                    mess = mess,
                                                    name = name,
                                                    address = addr,
                                                    description = desc,
                                                    currencySymbol = cur,
                                                    allowMultipleManagers = multi,
                                                    availableSeats = seats,
                                                    seatRentInfo = rentInfo,
                                                )
                                            },
                                            onArchiveMess = { viewModel.archiveMess(mess) },
                                            onShowMessage = { msg -> viewModel.showFeedback(msg) },
                                        )
                                    }
                                }
                            }
                        }
                    }

                    if (showMessSwitcher) {
                        MessSwitcherSheet(
                            messes = messes,
                            activeMessId = mess.id,
                            onSelectMess = { viewModel.switchMess(it) },
                            onOpenCreateMess = { showCreateMess = true },
                            onOpenJoinMess = {
                                viewModel.clearInvitePreview()
                                showJoinMess = true
                            },
                            onDismiss = { showMessSwitcher = false },
                        )
                    }
                }
            }
        }

        if (showCreateMess) {
            CreateMessDialog(
                isBusy = isBusy,
                onDismiss = { showCreateMess = false },
                onCreate = { name, address, desc, cur, seats, rentInfo ->
                    viewModel.createMess(name, address, desc, cur, seats, rentInfo) {
                        showCreateMess = false
                        currentTab = MessTab.DASHBOARD
                    }
                },
            )
        }

        if (showJoinMess) {
            JoinMessDialog(
                isBusy = isBusy,
                invitePreview = lookedUpInvite,
                onLookup = { code -> viewModel.lookupInvite(code) },
                onSubmitRequest = { invite ->
                    viewModel.submitJoinRequest(invite) {
                        showJoinMess = false
                    }
                },
                onDismiss = {
                    viewModel.clearInvitePreview()
                    showJoinMess = false
                },
            )
        }

        if (showProfileDialog) {
            EditProfileDialog(
                profile = userProfile,
                currentThemeMode = userProfile?.themeMode ?: "system",
                onSaveProfile = { name, phone -> viewModel.updateUserProfile(name, phone) },
                onSelectTheme = { mode -> viewModel.setThemeMode(mode) },
                onToggleDeactivated = { deactivated -> viewModel.setAccountDeactivated(deactivated) },
                onSignOut = { GoogleSignInHelper.signOut(credentialManager, scope) },
                onDismiss = { showProfileDialog = false },
            )
        }
    }
}

private fun FirebaseAuth.authStateFlow(): Flow<FirebaseUser?> = callbackFlow {
    val listener = FirebaseAuth.AuthStateListener { auth ->
        trySend(auth.currentUser)
    }
    addAuthStateListener(listener)
    awaitClose { removeAuthStateListener(listener) }
}
