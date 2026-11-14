package com.example.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AddBusiness
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.GroupAdd
import androidx.compose.material.icons.rounded.HomeWork
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.data.model.Mess
import com.example.data.model.MessInvite
import com.example.data.model.UserProfile

@Composable
fun CreateMessDialog(
    isBusy: Boolean,
    onDismiss: () -> Unit,
    onCreate: (
        name: String,
        address: String,
        description: String,
        currencySymbol: String,
        availableSeats: Long,
        seatRentInfo: String,
    ) -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var address by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var currency by remember { mutableStateOf("৳") }
    var seats by remember { mutableStateOf("0") }
    var rentInfo by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Create New Mess") },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.verticalScroll(rememberScrollState()),
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Mess Name *") },
                    placeholder = { Text("e.g., Crescent Villa Mess") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("create_mess_name_input"),
                )
                OutlinedTextField(
                    value = address,
                    onValueChange = { address = it },
                    label = { Text("Address") },
                    placeholder = { Text("Road, Area, City") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("House Rules / Description") },
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
                OutlinedTextField(
                    value = seats,
                    onValueChange = { seats = it.filter(Char::isDigit) },
                    label = { Text("Available To-Let Seats") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = rentInfo,
                    onValueChange = { rentInfo = it },
                    label = { Text("Seat Rent & Facilities (optional)") },
                    placeholder = { Text("e.g., 4,500/seat + WiFi & Gas") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onCreate(
                        name.trim(),
                        address.trim(),
                        description.trim(),
                        currency,
                        seats.toLongOrNull() ?: 0L,
                        rentInfo.trim(),
                    )
                },
                enabled = !isBusy && name.trim().length >= 2,
                modifier = Modifier.testTag("confirm_create_mess_button"),
            ) {
                Text("Create Mess")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

@Composable
fun JoinMessDialog(
    isBusy: Boolean,
    invitePreview: MessInvite?,
    onLookup: (String) -> Unit,
    onSubmitRequest: (MessInvite) -> Unit,
    onDismiss: () -> Unit,
) {
    var code by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Join a Mess") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = "Enter the secret invite code shared by your Mess Owner or Manager.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    OutlinedTextField(
                        value = code,
                        onValueChange = { code = it.uppercase() },
                        label = { Text("Invite Code") },
                        placeholder = { Text("MM4829XK") },
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
                        singleLine = true,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("join_invite_code_input"),
                    )
                    Button(
                        onClick = { onLookup(code) },
                        enabled = !isBusy && code.trim().length >= 4,
                        modifier = Modifier.testTag("lookup_invite_button"),
                    ) {
                        Text("Find")
                    }
                }

                if (invitePreview != null) {
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Text(
                                text = invitePreview.messName,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                            )
                            if (!invitePreview.messAddress.isNullOrBlank()) {
                                Text(
                                    text = invitePreview.messAddress,
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }
                            Text(
                                text = "Code verified. Submit a join request for manager approval.",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            if (invitePreview != null) {
                Button(
                    onClick = { onSubmitRequest(invitePreview) },
                    enabled = !isBusy,
                    modifier = Modifier.testTag("submit_join_request_button"),
                ) {
                    Text("Request to Join")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MessSwitcherSheet(
    messes: List<Mess>,
    activeMessId: String?,
    onSelectMess: (String) -> Unit,
    onOpenCreateMess: () -> Unit,
    onOpenJoinMess: () -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = "Switch Mess Workspace",
                style = MaterialTheme.typography.titleLarge,
            )
            Text(
                text = "Each mess keeps strictly isolated members, meals, expenses & balances.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            messes.forEach { mess ->
                val isSelected = mess.id == activeMessId
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = if (isSelected) {
                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                    } else {
                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            onSelectMess(mess.id)
                            onDismiss()
                        },
                ) {
                    ListItem(
                        headlineContent = {
                            Text(mess.name, fontWeight = FontWeight.SemiBold)
                        },
                        supportingContent = {
                            Text("${mess.memberUids.size} members • Code: ${mess.inviteCode}")
                        },
                        leadingContent = {
                            Icon(
                                imageVector = Icons.Rounded.HomeWork,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                            )
                        },
                        trailingContent = {
                            if (isSelected) {
                                Icon(
                                    imageVector = Icons.Rounded.CheckCircle,
                                    contentDescription = "Active mess",
                                    tint = MaterialTheme.colorScheme.primary,
                                )
                            }
                        },
                    )
                }
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Button(
                    onClick = {
                        onDismiss()
                        onOpenCreateMess()
                    },
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(Icons.Rounded.AddBusiness, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.size(6.dp))
                    Text("Create Mess")
                }
                OutlinedButton(
                    onClick = {
                        onDismiss()
                        onOpenJoinMess()
                    },
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(Icons.Rounded.GroupAdd, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.size(6.dp))
                    Text("Join Mess")
                }
            }
        }
    }
}

@Composable
fun EditProfileDialog(
    profile: UserProfile?,
    currentThemeMode: String,
    onSaveProfile: (name: String, phone: String?) -> Unit,
    onSelectTheme: (String) -> Unit,
    onToggleDeactivated: (Boolean) -> Unit,
    onSignOut: () -> Unit,
    onDismiss: () -> Unit,
) {
    var name by remember(profile?.displayName) { mutableStateOf(profile?.displayName.orEmpty()) }
    var phone by remember(profile?.phone) { mutableStateOf(profile?.phone.orEmpty()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Account & Appearance") },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.verticalScroll(rememberScrollState()),
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Display Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = phone,
                    onValueChange = { phone = it },
                    label = { Text("Phone Number (optional)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (!profile?.email.isNullOrBlank()) {
                    Text(
                        text = "Signed in as ${profile?.email}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                Text("Theme Mode", style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("system" to "System", "light" to "Light", "dark" to "Dark").forEach { (key, label) ->
                        FilterChip(
                            selected = currentThemeMode == key,
                            onClick = { onSelectTheme(key) },
                            label = { Text(label) },
                        )
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Account Deactivation", style = MaterialTheme.typography.titleSmall)
                        Text(
                            text = "Pause your active status across messes",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(
                        checked = profile?.isDeactivated == true,
                        onCheckedChange = onToggleDeactivated,
                    )
                }

                Spacer(Modifier.height(4.dp))
                OutlinedButton(
                    onClick = {
                        onDismiss()
                        onSignOut()
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("sign_out_button"),
                ) {
                    Text("Sign Out", color = MaterialTheme.colorScheme.error)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onSaveProfile(name.trim().ifBlank { "Mess Member" }, phone.trim().ifBlank { null })
                    onDismiss()
                },
                enabled = name.trim().isNotEmpty(),
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        },
    )
}
