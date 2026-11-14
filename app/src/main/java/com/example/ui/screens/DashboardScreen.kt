package com.example.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccountBalance
import androidx.compose.material.icons.rounded.AccountBalanceWallet
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.LockOpen
import androidx.compose.material.icons.rounded.PictureAsPdf
import androidx.compose.material.icons.rounded.Restaurant
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.ShoppingBasket
import androidx.compose.material.icons.rounded.Today
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
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
import androidx.compose.ui.unit.dp
import com.example.data.model.AuditLog
import com.example.data.model.Mess
import com.example.data.model.MessMember
import com.example.domain.FinanceCalculator
import com.example.domain.MonthlyFinanceSummary
import com.example.ui.components.FinancialOverviewChartCard
import com.example.ui.components.MemberBalanceCard
import com.example.ui.components.MonthSelectorBar
import com.example.ui.components.StatTile
import com.example.ui.components.dueMoneyColor
import com.example.ui.components.formatMonthLabel
import com.example.ui.components.positiveMoneyColor
import com.example.ui.theme.MonoFontFamily

@Composable
fun DashboardScreen(
    mess: Mess,
    currentUserId: String,
    members: List<MessMember>,
    summary: MonthlyFinanceSummary,
    auditLogs: List<AuditLog>,
    onPreviousMonth: () -> Unit,
    onNextMonth: () -> Unit,
    onCloseMonth: () -> Unit,
    onReopenMonth: () -> Unit,
    onExportPdf: () -> Unit,
    onExportCsv: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val cur = mess.currencySymbol
    val isClosed = mess.isMonthClosed(summary.monthKey)
    val role = mess.roleFor(currentUserId)
    val mySummary = summary.memberSummaries.firstOrNull { it.memberUid == currentUserId }
    var searchQuery by remember { mutableStateOf("") }
    var showClosingConfirm by remember { mutableStateOf(false) }

    val filteredMembers = remember(summary.memberSummaries, searchQuery) {
        val q = searchQuery.trim().lowercase()
        if (q.isEmpty()) summary.memberSummaries
        else summary.memberSummaries.filter { it.displayName.lowercase().contains(q) }
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            MonthSelectorBar(
                monthKey = summary.monthKey,
                isClosed = isClosed,
                onPreviousMonth = onPreviousMonth,
                onNextMonth = onNextMonth,
            )
        }

        // Hero Meal Rate & Individual Balance Card
        item {
            Card(
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    Row(
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Top,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column {
                            Text(
                                text = "Current Meal Rate",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.85f),
                            )
                            Text(
                                text = "$cur${summary.mealRateDisplay}",
                                style = MaterialTheme.typography.displaySmall.copy(fontFamily = MonoFontFamily),
                                color = MaterialTheme.colorScheme.onPrimary,
                            )
                            Text(
                                text = "Meal Expense ÷ Total Meals",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.75f),
                            )
                        }

                        if (mySummary != null) {
                            val myPos = mySummary.netBalancePaisa >= 0
                            Surface(
                                color = MaterialTheme.colorScheme.surface,
                                shape = RoundedCornerShape(16.dp),
                            ) {
                                Column(
                                    horizontalAlignment = Alignment.End,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                ) {
                                    Text(
                                        text = "Your Balance",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                    Text(
                                        text = (if (myPos) "+" else "") +
                                            FinanceCalculator.formatMoney(mySummary.netBalancePaisa, cur),
                                        style = MaterialTheme.typography.titleMedium.copy(fontFamily = MonoFontFamily),
                                        fontWeight = FontWeight.Bold,
                                        color = if (myPos) positiveMoneyColor() else dueMoneyColor(),
                                    )
                                }
                            }
                        }
                    }

                    if (summary.pendingExpenseCount > 0) {
                        Surface(
                            color = MaterialTheme.colorScheme.secondaryContainer,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(
                                text = "${summary.pendingExpenseCount} bazar/expense entry awaiting manager approval",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            )
                        }
                    }
                }
            }
        }

        // KPI Grid (3 rows x 2 cols)
        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                    StatTile(
                        label = "Active Members",
                        value = "${members.count { it.status == "active" }}/${members.size}",
                        icon = Icons.Rounded.Groups,
                        accentColor = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.weight(1f),
                    )
                    StatTile(
                        label = "Today's Meals",
                        value = FinanceCalculator.formatMealCount(summary.todayMeals),
                        icon = Icons.Rounded.Today,
                        accentColor = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.weight(1f),
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                    StatTile(
                        label = "Monthly Meals",
                        value = FinanceCalculator.formatMealCount(summary.totalMeals),
                        icon = Icons.Rounded.Restaurant,
                        accentColor = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.weight(1f),
                    )
                    StatTile(
                        label = "Total Bazar",
                        value = FinanceCalculator.formatMoney(summary.totalMealExpensePaisa, cur),
                        icon = Icons.Rounded.ShoppingBasket,
                        accentColor = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.weight(1f),
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                    StatTile(
                        label = "Total Deposits",
                        value = FinanceCalculator.formatMoney(summary.totalDepositPaisa, cur),
                        icon = Icons.Rounded.AccountBalanceWallet,
                        accentColor = MaterialTheme.colorScheme.tertiary,
                        modifier = Modifier.weight(1f),
                    )
                    StatTile(
                        label = "Mess Cash Left",
                        value = FinanceCalculator.formatMoney(summary.remainingCashPaisa, cur),
                        icon = Icons.Rounded.AccountBalance,
                        accentColor = if (summary.remainingCashPaisa >= 0) positiveMoneyColor() else dueMoneyColor(),
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }

        // Charts & Visual Cashflow
        item {
            FinancialOverviewChartCard(summary = summary, currencySymbol = cur)
        }

        // Reports & Monthly Closing Controls
        item {
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text("Reports & Month Closing", style = MaterialTheme.typography.titleMedium)
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                        OutlinedButton(
                            onClick = onExportPdf,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("export_pdf_button"),
                        ) {
                            Icon(Icons.Rounded.PictureAsPdf, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("PDF Report")
                        }
                        OutlinedButton(
                            onClick = onExportCsv,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("export_csv_button"),
                        ) {
                            Icon(Icons.Rounded.Description, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Excel / CSV")
                        }
                    }

                    if (role.canManageMembersAndClosing()) {
                        if (!isClosed) {
                            Button(
                                onClick = { showClosingConfirm = true },
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("close_month_button"),
                            ) {
                                Icon(Icons.Rounded.Lock, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(8.dp))
                                Text("Close & Lock ${formatMonthLabel(summary.monthKey)}")
                            }
                        } else {
                            OutlinedButton(
                                onClick = onReopenMonth,
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("reopen_month_button"),
                            ) {
                                Icon(Icons.Rounded.LockOpen, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(8.dp))
                                Text("Reopen ${formatMonthLabel(summary.monthKey)}")
                            }
                        }
                    }
                }
            }
        }

        // Individual Member Balances Header + Search
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Individual Balances (${filteredMembers.size})",
                    style = MaterialTheme.typography.titleMedium,
                )
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Search member balances...") },
                    leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(Icons.Rounded.Close, contentDescription = "Clear search")
                            }
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }

        if (filteredMembers.isEmpty()) {
            item {
                Text(
                    text = "No member balances match your search.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 8.dp),
                )
            }
        } else {
            items(filteredMembers, key = { it.memberUid }) { memberSummary ->
                MemberBalanceCard(
                    summary = memberSummary,
                    currencySymbol = cur,
                    isCurrentUser = memberSummary.memberUid == currentUserId,
                )
            }
        }

        // Recent Activities
        if (auditLogs.isNotEmpty()) {
            item {
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Icon(
                        imageVector = Icons.Rounded.History,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp),
                    )
                    Text("Recent Activity", style = MaterialTheme.typography.titleMedium)
                }
            }
            items(auditLogs.take(5), key = { it.id }) { log ->
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surface,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(log.details, style = MaterialTheme.typography.bodyMedium)
                        Text(
                            text = "${log.actorName} • ${log.action}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }

    if (showClosingConfirm) {
        AlertDialog(
            onDismissRequest = { showClosingConfirm = false },
            title = { Text("Close ${formatMonthLabel(summary.monthKey)}?") },
            text = {
                Text(
                    "Closing locks all meals, bazar expenses, deposits, and adjustments for ${summary.monthKey} at a meal rate of $cur${summary.mealRateDisplay}. Only owners/managers can reopen it later."
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showClosingConfirm = false
                        onCloseMonth()
                    },
                ) {
                    Text("Close & Lock")
                }
            },
            dismissButton = {
                TextButton(onClick = { showClosingConfirm = false }) { Text("Cancel") }
            },
        )
    }
}
