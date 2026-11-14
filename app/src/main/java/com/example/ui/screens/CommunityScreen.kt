package com.example.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Archive
import androidx.compose.material.icons.rounded.Campaign
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Hotel
import androidx.compose.material.icons.rounded.ManageAccounts
import androidx.compose.material.icons.rounded.PersonRemove
import androidx.compose.material.icons.rounded.PushPin
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.data.model.Announcement
import com.example.data.model.AuditLog
import com.example.data.model.ChatMessage
import com.example.data.model.JoinRequest
import com.example.data.model.Mess
import com.example.data.model.MessMember
import com.example.data.model.MessRole
import com.example.ui.components.positiveMoneyColor
import com.example.ui.theme.MonoFontFamily
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun CommunityScreen(
    mess: Mess,
    currentUserId: String,
    members: List<MessMember>,
    joinRequests: List<JoinRequest>,
    announcements: List<Announcement>,
    chatMessages: List<ChatMessage>,
    auditLogs: List<AuditLog>,
    isBusy: Boolean,
    onPostAnnouncement: (title: String, body: String, pinned: Boolean) -> Unit,
    onDeleteAnnouncement: (String) -> Unit,
    onSendChatMessage: (String) -> Unit,
    onRotateInviteCode: () -> Unit,
    onApproveJoinRequest: (JoinRequest) -> Unit,
    onRejectJoinRequest: (JoinRequest) -> Unit,
    onUpdateMemberRoleAndStatus: (MessMember, MessRole, String) -> Unit,
    onTransferOwnership: (MessMember) -> Unit,
    onRemoveMember: (MessMember) -> Unit,
    onUpdateMessSettings: (
        name: String,
        address: String,
        description: String,
        currencySymbol: String,
        allowMultipleManagers: Boolean,
        availableSeats: Long,
        seatRentInfo: String,
    ) -> Unit,
    onArchiveMess: () -> Unit,
    onShowMessage: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val currentRole = mess.roleFor(currentUserId)
    val canManageFinances = currentRole.canManageFinances()
    val canManageMembers = currentRole.canManageMembersAndClosing()
    val isOwner = currentRole == MessRole.OWNER

    var selectedSubTab by remember { mutableIntStateOf(0) }
    var showNewAnnouncement by remember { mutableStateOf(false) }
    var showEditMessDialog by remember { mutableStateOf(false) }
    var showArchiveConfirm by remember { mutableStateOf(false) }
    var editingMember by remember { mutableStateOf<MessMember?>(null) }
    var removingMember by remember { mutableStateOf<MessMember?>(null) }
    var transferTarget by remember { mutableStateOf<MessMember?>(null) }

    Scaffold(
        modifier = modifier,
        floatingActionButton = {
            if (selectedSubTab == 0 && canManageFinances) {
                FloatingActionButton(
                    onClick = { showNewAnnouncement = true },
                    modifier = Modifier.testTag("add_announcement_fab"),
                ) {
                    Icon(Icons.Rounded.Add, contentDescription = "Post announcement")
                }
            }
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            val requestBadge = if (canManageMembers && joinRequests.isNotEmpty()) {
                " (${joinRequests.size})"
            } else {
                ""
            }
            ScrollableTabRow(
                selectedTabIndex = selectedSubTab,
                edgePadding = 12.dp,
            ) {
                Tab(
                    selected = selectedSubTab == 0,
                    onClick = { selectedSubTab = 0 },
                    text = { Text("Notices") },
                )
                Tab(
                    selected = selectedSubTab == 1,
                    onClick = { selectedSubTab = 1 },
                    text = { Text("Group Chat") },
                )
                Tab(
                    selected = selectedSubTab == 2,
                    onClick = { selectedSubTab = 2 },
                    text = { Text("Members & Seats$requestBadge") },
                )
                Tab(
                    selected = selectedSubTab == 3,
                    onClick = { selectedSubTab = 3 },
                    text = { Text("Audit Log") },
                )
            }

            when (selectedSubTab) {
                0 -> AnnouncementsTab(
                    announcements = announcements,
                    canManage = canManageFinances,
                    onDelete = onDeleteAnnouncement,
                )

                1 -> GroupChatTab(
                    currentUserId = currentUserId,
                    messages = chatMessages,
                    onSend = onSendChatMessage,
                )

                2 -> MembersAndSeatsTab(
                    mess = mess,
                    currentUserId = currentUserId,
                    isOwner = isOwner,
                    canManageMembers = canManageMembers,
                    members = members,
                    joinRequests = joinRequests,
                    isBusy = isBusy,
                    onCopyInviteCode = {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                        clipboard?.setPrimaryClip(ClipData.newPlainText("Mess Malik Invite Code", mess.inviteCode))
                        onShowMessage("Invite code ${mess.inviteCode} copied")
                    },
                    onRotateInviteCode = onRotateInviteCode,
                    onEditSettings = { showEditMessDialog = true },
                    onApproveJoinRequest = onApproveJoinRequest,
                    onRejectJoinRequest = onRejectJoinRequest,
                    onEditMember = { editingMember = it },
                    onRequestTransfer = { transferTarget = it },
                    onRequestRemove = { removingMember = it },
                    onRequestArchive = { showArchiveConfirm = true },
                )

                3 -> AuditLogTab(auditLogs = auditLogs)
            }
        }
    }

    if (showNewAnnouncement) {
        PostAnnouncementDialog(
            onPost = { title, body, pinned ->
                onPostAnnouncement(title, body, pinned)
                showNewAnnouncement = false
            },
            onDismiss = { showNewAnnouncement = false },
        )
    }

    if (showEditMessDialog) {
        EditMessSettingsDialog(
            mess = mess,
            isBusy = isBusy,
            onSave = { name, address, desc, cur, allowMulti, seats, rentInfo ->
                onUpdateMessSettings(name, address, desc, cur, allowMulti, seats, rentInfo)
                showEditMessDialog = false
            },
            onDismiss = { showEditMessDialog = false },
        )
    }

    editingMember?.let { member ->
        EditMemberRoleDialog(
            member = member,
            allowMultipleManagers = mess.allowMultipleManagers || isOwner,
            onSave = { role, status ->
                onUpdateMemberRoleAndStatus(member, role, status)
                editingMember = null
            },
            onDismiss = { editingMember = null },
        )
    }

    removingMember?.let { member ->
        AlertDialog(
            onDismissRequest = { removingMember = null },
            title = { Text("Remove Member?") },
            text = {
                Text("Remove ${member.displayName} from ${mess.name}? Historical ledger entries stay intact, and they will lose access to this mess.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        onRemoveMember(member)
                        removingMember = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                ) {
                    Text("Remove")
                }
            },
            dismissButton = {
                TextButton(onClick = { removingMember = null }) { Text("Cancel") }
            },
        )
    }

    transferTarget?.let { member ->
        AlertDialog(
            onDismissRequest = { transferTarget = null },
            title = { Text("Transfer Ownership?") },
            text = {
                Text("Transfer Mess Owner authority for ${mess.name} to ${member.displayName}? You will become a Mess Manager.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        onTransferOwnership(member)
                        transferTarget = null
                    },
                ) {
                    Text("Transfer Ownership")
                }
            },
            dismissButton = {
                TextButton(onClick = { transferTarget = null }) { Text("Cancel") }
            },
        )
    }

    if (showArchiveConfirm) {
        AlertDialog(
            onDismissRequest = { showArchiveConfirm = false },
            title = { Text("Archive ${mess.name}?") },
            text = {
                Text("Archiving hides ${mess.name} from active workspaces for all members. This action is restricted to the Mess Owner.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        onArchiveMess()
                        showArchiveConfirm = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                ) {
                    Text("Archive Mess")
                }
            },
            dismissButton = {
                TextButton(onClick = { showArchiveConfirm = false }) { Text("Cancel") }
            },
        )
    }
}

@Composable
private fun AnnouncementsTab(
    announcements: List<Announcement>,
    canManage: Boolean,
    onDelete: (String) -> Unit,
) {
    if (announcements.isEmpty()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(
                imageVector = Icons.Rounded.Campaign,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(44.dp),
            )
            Spacer(Modifier.height(10.dp))
            Text("No announcements yet", style = MaterialTheme.typography.titleMedium)
            Text(
                text = "Managers and assistant managers can post house notices, bazar schedules, and utility reminders.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 88.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(announcements, key = { it.id }) { item ->
            Card(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (item.pinned) {
                        MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.45f)
                    } else {
                        MaterialTheme.colorScheme.surface
                    },
                ),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.weight(1f),
                        ) {
                            if (item.pinned) {
                                Icon(
                                    imageVector = Icons.Rounded.PushPin,
                                    contentDescription = "Pinned notice",
                                    tint = MaterialTheme.colorScheme.secondary,
                                    modifier = Modifier.size(16.dp),
                                )
                            }
                            Text(
                                text = item.title,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                        if (canManage) {
                            IconButton(onClick = { onDelete(item.id) }) {
                                Icon(
                                    imageVector = Icons.Rounded.DeleteOutline,
                                    contentDescription = "Delete announcement",
                                    tint = MaterialTheme.colorScheme.error,
                                )
                            }
                        }
                    }
                    Text(item.body, style = MaterialTheme.typography.bodyMedium)
                    Text(
                        text = "Posted by ${item.authorName} • ${formatTimestamp(item.createdAt?.toDate())}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun GroupChatTab(
    currentUserId: String,
    messages: List<ChatMessage>,
    onSend: (String) -> Unit,
) {
    var draft by remember { mutableStateOf("") }
    val listState = rememberLazyListState()

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.lastIndex)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .imePadding(),
    ) {
        LazyColumn(
            state = listState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (messages.isEmpty()) {
                item {
                    Text(
                        text = "Start the conversation with your messmates.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                items(messages, key = { it.id }) { msg ->
                    val isMine = msg.senderUid == currentUserId
                    Row(
                        horizontalArrangement = if (isMine) Arrangement.End else Arrangement.Start,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Surface(
                            shape = RoundedCornerShape(
                                topStart = 16.dp,
                                topEnd = 16.dp,
                                bottomStart = if (isMine) 16.dp else 4.dp,
                                bottomEnd = if (isMine) 4.dp else 16.dp,
                            ),
                            color = if (isMine) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.surface
                            },
                            tonalElevation = if (isMine) 0.dp else 1.dp,
                            modifier = Modifier.widthIn(max = 310.dp),
                        ) {
                            Column(
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                                verticalArrangement = Arrangement.spacedBy(3.dp),
                            ) {
                                if (!isMine) {
                                    Text(
                                        text = msg.senderName,
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary,
                                    )
                                }
                                Text(
                                    text = msg.text,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = if (isMine) {
                                        MaterialTheme.colorScheme.onPrimary
                                    } else {
                                        MaterialTheme.colorScheme.onSurface
                                    },
                                )
                                Text(
                                    text = formatTimestamp(msg.createdAt?.toDate()),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (isMine) {
                                        MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.72f)
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    },
                                )
                            }
                        }
                    }
                }
            }
        }

        HorizontalDivider()
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
        ) {
            OutlinedTextField(
                value = draft,
                onValueChange = { draft = it },
                placeholder = { Text("Message messmates...") },
                maxLines = 3,
                shape = RoundedCornerShape(22.dp),
                modifier = Modifier
                    .weight(1f)
                    .testTag("chat_message_input"),
            )
            Surface(
                color = if (draft.trim().isNotEmpty()) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.surfaceVariant
                },
                shape = RoundedCornerShape(24.dp),
                modifier = Modifier.size(48.dp),
            ) {
                IconButton(
                    onClick = {
                        val trimmed = draft.trim()
                        if (trimmed.isNotEmpty()) {
                            onSend(trimmed)
                            draft = ""
                        }
                    },
                    enabled = draft.trim().isNotEmpty(),
                    modifier = Modifier.testTag("send_chat_button"),
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.Send,
                        contentDescription = "Send chat message",
                        tint = if (draft.trim().isNotEmpty()) {
                            MaterialTheme.colorScheme.onPrimary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun MembersAndSeatsTab(
    mess: Mess,
    currentUserId: String,
    isOwner: Boolean,
    canManageMembers: Boolean,
    members: List<MessMember>,
    joinRequests: List<JoinRequest>,
    isBusy: Boolean,
    onCopyInviteCode: () -> Unit,
    onRotateInviteCode: () -> Unit,
    onEditSettings: () -> Unit,
    onApproveJoinRequest: (JoinRequest) -> Unit,
    onRejectJoinRequest: (JoinRequest) -> Unit,
    onEditMember: (MessMember) -> Unit,
    onRequestTransfer: (MessMember) -> Unit,
    onRequestRemove: (MessMember) -> Unit,
    onRequestArchive: () -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // Mess Overview, Invite Code & To-Let Seat Card
        item {
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(mess.name, style = MaterialTheme.typography.titleLarge)
                            if (!mess.address.isNullOrBlank()) {
                                Text(
                                    text = mess.address,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                        if (canManageMembers) {
                            IconButton(
                                onClick = onEditSettings,
                                modifier = Modifier.testTag("edit_mess_settings_button"),
                            ) {
                                Icon(Icons.Rounded.Edit, contentDescription = "Edit mess settings")
                            }
                        }
                    }

                    if (!mess.description.isNullOrBlank()) {
                        Text(
                            text = mess.description,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }

                    // Invite Code Row
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                        ) {
                            Column {
                                Text(
                                    text = "Secret Invite Code",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Text(
                                    text = mess.inviteCode,
                                    style = MaterialTheme.typography.titleMedium.copy(fontFamily = MonoFontFamily),
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                            }
                            Row {
                                IconButton(
                                    onClick = onCopyInviteCode,
                                    modifier = Modifier.testTag("copy_invite_code_button"),
                                ) {
                                    Icon(Icons.Rounded.ContentCopy, contentDescription = "Copy invite code")
                                }
                                if (canManageMembers) {
                                    IconButton(
                                        onClick = onRotateInviteCode,
                                        enabled = !isBusy,
                                        modifier = Modifier.testTag("rotate_invite_code_button"),
                                    ) {
                                        Icon(Icons.Rounded.Refresh, contentDescription = "Rotate invite code")
                                    }
                                }
                            }
                        }
                    }

                    // To-Let / Available Seats Row
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.padding(12.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Hotel,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.secondary,
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = if (mess.availableSeats > 0) {
                                        "To-Let: ${mess.availableSeats} Seat(s) Available"
                                    } else {
                                        "No Vacant Seats (Full)"
                                    },
                                    style = MaterialTheme.typography.titleSmall,
                                    color = if (mess.availableSeats > 0) {
                                        positiveMoneyColor()
                                    } else {
                                        MaterialTheme.colorScheme.onSurface
                                    },
                                )
                                if (!mess.seatRentInfo.isNullOrBlank()) {
                                    Text(
                                        text = mess.seatRentInfo,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Pending Join Requests (Managers/Owners)
        if (canManageMembers && joinRequests.isNotEmpty()) {
            item {
                Text(
                    text = "Pending Join Requests (${joinRequests.size})",
                    style = MaterialTheme.typography.titleMedium,
                )
            }
            items(joinRequests, key = { it.id }) { req ->
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.4f)),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.padding(14.dp),
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(req.applicantName, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                            Text("Requested via ${req.inviteCode}", style = MaterialTheme.typography.bodySmall)
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            IconButton(
                                onClick = { onApproveJoinRequest(req) },
                                enabled = !isBusy,
                                modifier = Modifier.testTag("approve_join_${req.applicantUid}"),
                            ) {
                                Icon(Icons.Rounded.Check, contentDescription = "Approve request", tint = positiveMoneyColor())
                            }
                            IconButton(
                                onClick = { onRejectJoinRequest(req) },
                                enabled = !isBusy,
                                modifier = Modifier.testTag("reject_join_${req.applicantUid}"),
                            ) {
                                Icon(Icons.Rounded.Close, contentDescription = "Decline request", tint = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                }
            }
        }

        item {
            Text(
                text = "Members & Roles (${members.size})",
                style = MaterialTheme.typography.titleMedium,
            )
        }

        items(members, key = { it.userId }) { member ->
            val memberRole = MessRole.fromKey(member.role)
            val isSelf = member.userId == currentUserId
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.padding(14.dp),
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (isSelf) "${member.displayName} (You)" else member.displayName,
                            style = MaterialTheme.typography.titleSmall,
                        )
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(top = 4.dp),
                        ) {
                            Surface(
                                color = MaterialTheme.colorScheme.primaryContainer,
                                shape = RoundedCornerShape(6.dp),
                            ) {
                                Text(
                                    text = memberRole.label,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                )
                            }
                            if (member.status != "active") {
                                Surface(
                                    color = MaterialTheme.colorScheme.error.copy(alpha = 0.14f),
                                    shape = RoundedCornerShape(6.dp),
                                ) {
                                    Text(
                                        text = "INACTIVE",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.error,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                    )
                                }
                            }
                        }
                    }

                    if (canManageMembers && memberRole != MessRole.OWNER) {
                        Row {
                            IconButton(onClick = { onEditMember(member) }) {
                                Icon(Icons.Rounded.ManageAccounts, contentDescription = "Manage ${member.displayName}'s role")
                            }
                            if (isOwner && !isSelf) {
                                IconButton(onClick = { onRequestTransfer(member) }) {
                                    Icon(Icons.Rounded.SwapHoriz, contentDescription = "Transfer ownership to ${member.displayName}")
                                }
                            }
                            if (!isSelf) {
                                IconButton(onClick = { onRequestRemove(member) }) {
                                    Icon(
                                        Icons.Rounded.PersonRemove,
                                        contentDescription = "Remove ${member.displayName}",
                                        tint = MaterialTheme.colorScheme.error,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        if (isOwner) {
            item {
                Spacer(Modifier.height(6.dp))
                OutlinedButton(
                    onClick = onRequestArchive,
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("archive_mess_button"),
                ) {
                    Icon(Icons.Rounded.Archive, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Archive Mess")
                }
            }
        }
    }
}

@Composable
private fun AuditLogTab(auditLogs: List<AuditLog>) {
    var query by remember { mutableStateOf("") }
    val filtered = remember(auditLogs, query) {
        val q = query.trim().lowercase()
        if (q.isEmpty()) auditLogs
        else auditLogs.filter {
            it.details.lowercase().contains(q) ||
                it.actorName.lowercase().contains(q) ||
                it.action.lowercase().contains(q)
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text("Search audit trail...") },
                leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                trailingIcon = {
                    if (query.isNotEmpty()) {
                        IconButton(onClick = { query = "" }) {
                            Icon(Icons.Rounded.Close, contentDescription = "Clear")
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth(),
            )
        }

        if (filtered.isEmpty()) {
            item {
                Text(
                    text = "No matching activity logs.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            items(filtered, key = { it.id }) { log ->
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surface,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Text(log.details, style = MaterialTheme.typography.bodyMedium)
                        Text(
                            text = "${log.actorName} • ${log.action} • ${formatTimestamp(log.createdAt?.toDate())}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PostAnnouncementDialog(
    onPost: (title: String, body: String, pinned: Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    var title by remember { mutableStateOf("") }
    var body by remember { mutableStateOf("") }
    var pinned by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Post Announcement") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Title *") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = body,
                    onValueChange = { body = it },
                    label = { Text("Notice Details *") },
                    minLines = 3,
                    maxLines = 5,
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = pinned, onCheckedChange = { pinned = it })
                    Text("Pin to top of notices", style = MaterialTheme.typography.bodyMedium)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onPost(title.trim(), body.trim(), pinned) },
                enabled = title.trim().isNotEmpty() && body.trim().isNotEmpty(),
            ) {
                Text("Post")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

@Composable
private fun EditMessSettingsDialog(
    mess: Mess,
    isBusy: Boolean,
    onSave: (String, String, String, String, Boolean, Long, String) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by remember { mutableStateOf(mess.name) }
    var address by remember { mutableStateOf(mess.address.orEmpty()) }
    var description by remember { mutableStateOf(mess.description.orEmpty()) }
    var currency by remember { mutableStateOf(mess.currencySymbol) }
    var allowMultipleManagers by remember { mutableStateOf(mess.allowMultipleManagers) }
    var seats by remember { mutableStateOf(mess.availableSeats.toString()) }
    var rentInfo by remember { mutableStateOf(mess.seatRentInfo.orEmpty()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Mess Settings & To-Let") },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.verticalScroll(rememberScrollState()),
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Mess Name *") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = address,
                    onValueChange = { address = it },
                    label = { Text("Address") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Description / Rules") },
                    maxLines = 2,
                    modifier = Modifier.fillMaxWidth(),
                )
                Text("Currency", style = MaterialTheme.typography.labelMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("৳", "₹", "$", "€").forEach { symbol ->
                        FilterChip(
                            selected = currency == symbol,
                            onClick = { currency = symbol },
                            label = { Text(symbol) },
                        )
                    }
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Allow Multiple Managers", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                    Switch(checked = allowMultipleManagers, onCheckedChange = { allowMultipleManagers = it })
                }
                OutlinedTextField(
                    value = seats,
                    onValueChange = { seats = it.filter(Char::isDigit) },
                    label = { Text("Available Seats (To-Let)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = rentInfo,
                    onValueChange = { rentInfo = it },
                    label = { Text("Seat Rent & Facilities") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onSave(
                        name.trim(),
                        address.trim(),
                        description.trim(),
                        currency,
                        allowMultipleManagers,
                        seats.toLongOrNull() ?: 0L,
                        rentInfo.trim(),
                    )
                },
                enabled = !isBusy && name.trim().length >= 2,
            ) {
                Text("Save Settings")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

@Composable
private fun EditMemberRoleDialog(
    member: MessMember,
    allowMultipleManagers: Boolean,
    onSave: (MessRole, String) -> Unit,
    onDismiss: () -> Unit,
) {
    var role by remember { mutableStateOf(MessRole.fromKey(member.role)) }
    var isActive by remember { mutableStateOf(member.status == "active") }

    val selectableRoles = remember(allowMultipleManagers) {
        if (allowMultipleManagers) {
            listOf(MessRole.MANAGER, MessRole.ASSISTANT_MANAGER, MessRole.MEMBER)
        } else {
            listOf(MessRole.ASSISTANT_MANAGER, MessRole.MEMBER)
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Manage ${member.displayName}") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Role", style = MaterialTheme.typography.labelLarge)
                selectableRoles.forEach { r ->
                    FilterChip(
                        selected = role == r,
                        onClick = { role = r },
                        label = { Text(r.label) },
                    )
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Active Member", style = MaterialTheme.typography.bodyMedium)
                    Switch(checked = isActive, onCheckedChange = { isActive = it })
                }
            }
        },
        confirmButton = {
            Button(onClick = { onSave(role, if (isActive) "active" else "inactive") }) {
                Text("Update")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

private fun formatTimestamp(date: Date?): String {
    if (date == null) return "Just now"
    return SimpleDateFormat("MMM dd, hh:mm a", Locale.US).format(date)
}
