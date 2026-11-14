package com.example.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.data.model.Adjustment
import com.example.data.model.Deposit
import com.example.data.model.DepositMethod
import com.example.data.model.Expense
import com.example.data.model.ExpenseCategory
import com.example.data.model.Mess
import com.example.data.model.MessMember
import com.example.domain.FinanceCalculator
import com.example.ui.components.MonthSelectorBar
import com.example.ui.components.dueMoneyColor
import com.example.ui.components.positiveMoneyColor
import com.example.ui.theme.MonoFontFamily

@Composable
fun FinanceScreen(
    mess: Mess,
    currentUserId: String,
    monthKey: String,
    todayDate: String,
    members: List<MessMember>,
    expenses: List<Expense>,
    deposits: List<Deposit>,
    adjustments: List<Adjustment>,
    onPreviousMonth: () -> Unit,
    onNextMonth: () -> Unit,
    onAddExpense: (
        date: String,
        category: String,
        description: String,
        amountPaisa: Long,
        targetMemberUid: String?,
        receiptUri: String?,
    ) -> Unit,
    onSetExpenseApproval: (Expense, String) -> Unit,
    onDeleteExpense: (Expense) -> Unit,
    onAddDeposit: (
        date: String,
        memberUid: String,
        memberName: String,
        amountPaisa: Long,
        method: String,
        note: String?,
    ) -> Unit,
    onUpdateDeposit: (
        deposit: Deposit,
        newAmountPaisa: Long,
        newMethod: String,
        newNote: String?,
    ) -> Unit,
    onDeleteDeposit: (Deposit) -> Unit,
    onAddAdjustment: (
        memberUid: String,
        memberName: String,
        amountPaisa: Long,
        reason: String,
    ) -> Unit,
    modifier: Modifier = Modifier,
) {
    val cur = mess.currencySymbol
    val isClosed = mess.isMonthClosed(monthKey)
    val canManage = mess.roleFor(currentUserId).canManageFinances()
    var selectedTab by remember { mutableIntStateOf(0) }
    var searchQuery by remember { mutableStateOf("") }
    var expenseFilter by remember { mutableStateOf("all") }

    var showAddExpense by remember { mutableStateOf(false) }
    var showAddDeposit by remember { mutableStateOf(false) }
    var editingDeposit by remember { mutableStateOf<Deposit?>(null) }
    var showAddAdjustment by remember { mutableStateOf(false) }

    val filteredExpenses = remember(expenses, searchQuery, expenseFilter) {
        val q = searchQuery.trim().lowercase()
        expenses.filter { e ->
            val matchesSearch = q.isEmpty() ||
                e.description.lowercase().contains(q) ||
                e.purchasedByName.lowercase().contains(q)
            val matchesFilter = when (expenseFilter) {
                "pending" -> e.status == "pending"
                "approved" -> e.status == "approved"
                ExpenseCategory.BAZAR.key -> e.category == ExpenseCategory.BAZAR.key
                ExpenseCategory.SHARED_FIXED.key -> e.category == ExpenseCategory.SHARED_FIXED.key
                ExpenseCategory.OTHER_INDIVIDUAL.key -> e.category == ExpenseCategory.OTHER_INDIVIDUAL.key
                else -> true
            }
            matchesSearch && matchesFilter
        }
    }

    val filteredDeposits = remember(deposits, searchQuery) {
        val q = searchQuery.trim().lowercase()
        if (q.isEmpty()) deposits
        else deposits.filter {
            it.memberName.lowercase().contains(q) || it.method.lowercase().contains(q)
        }
    }

    Scaffold(
        modifier = modifier,
        floatingActionButton = {
            if (!isClosed) {
                when (selectedTab) {
                    0 -> FloatingActionButton(
                        onClick = { showAddExpense = true },
                        modifier = Modifier.testTag("add_expense_fab"),
                    ) {
                        Icon(Icons.Rounded.Add, contentDescription = "Add bazar or expense")
                    }
                    1 -> if (canManage) {
                        FloatingActionButton(
                            onClick = { showAddDeposit = true },
                            modifier = Modifier.testTag("add_deposit_fab"),
                        ) {
                            Icon(Icons.Rounded.Add, contentDescription = "Record deposit")
                        }
                    }
                    2 -> if (mess.roleFor(currentUserId).canManageMembersAndClosing()) {
                        FloatingActionButton(
                            onClick = { showAddAdjustment = true },
                            modifier = Modifier.testTag("add_adjustment_fab"),
                        ) {
                            Icon(Icons.Rounded.Add, contentDescription = "Add adjustment")
                        }
                    }
                }
            }
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            TabRow(selectedTabIndex = selectedTab) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = { Text("Bazar & Bills") },
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = { Text("Deposits") },
                )
                Tab(
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2 },
                    text = { Text("Adjustments") },
                )
            }

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 88.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item {
                    MonthSelectorBar(
                        monthKey = monthKey,
                        isClosed = isClosed,
                        onPreviousMonth = onPreviousMonth,
                        onNextMonth = onNextMonth,
                    )
                }

                if (selectedTab < 2) {
                    item {
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            placeholder = {
                                Text(if (selectedTab == 0) "Search bazar items or buyer..." else "Search member deposits...")
                            },
                            leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                            trailingIcon = {
                                if (searchQuery.isNotEmpty()) {
                                    IconButton(onClick = { searchQuery = "" }) {
                                        Icon(Icons.Rounded.Close, contentDescription = "Clear")
                                    }
                                }
                            },
                            singleLine = true,
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }

                when (selectedTab) {
                    0 -> {
                        item {
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                val filters = listOf(
                                    "all" to "All",
                                    "pending" to "Pending",
                                    "approved" to "Approved",
                                    ExpenseCategory.BAZAR.key to "Bazar",
                                    ExpenseCategory.SHARED_FIXED.key to "Utility",
                                    ExpenseCategory.OTHER_INDIVIDUAL.key to "Individual",
                                )
                                items(filters) { (key, label) ->
                                    FilterChip(
                                        selected = expenseFilter == key,
                                        onClick = { expenseFilter = key },
                                        label = { Text(label) },
                                    )
                                }
                            }
                        }

                        if (filteredExpenses.isEmpty()) {
                            item {
                                Text(
                                    text = "No expenses recorded for this filter yet.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        } else {
                            items(filteredExpenses, key = { it.id }) { expense ->
                                ExpenseRowCard(
                                    expense = expense,
                                    currencySymbol = cur,
                                    canManage = canManage,
                                    isOwn = expense.purchasedByUid == currentUserId,
                                    isClosed = isClosed,
                                    onApprove = { onSetExpenseApproval(expense, "approved") },
                                    onReject = { onSetExpenseApproval(expense, "rejected") },
                                    onDelete = { onDeleteExpense(expense) },
                                )
                            }
                        }
                    }

                    1 -> {
                        if (filteredDeposits.isEmpty()) {
                            item {
                                Text(
                                    text = "No deposits recorded for this month.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        } else {
                            items(filteredDeposits, key = { it.id }) { deposit ->
                                DepositRowCard(
                                    deposit = deposit,
                                    currencySymbol = cur,
                                    canManage = canManage,
                                    isClosed = isClosed,
                                    onEdit = { editingDeposit = deposit },
                                    onDelete = { onDeleteDeposit(deposit) },
                                )
                            }
                        }
                    }

                    2 -> {
                        if (adjustments.isEmpty()) {
                            item {
                                Text(
                                    text = "No manual balance adjustments this month.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        } else {
                            items(adjustments, key = { it.id }) { adj ->
                                val pos = adj.amountPaisa >= 0
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
                                            Text(adj.memberName, style = MaterialTheme.typography.titleSmall)
                                            Text(adj.reason, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                        Text(
                                            text = (if (pos) "+" else "") + FinanceCalculator.formatMoney(adj.amountPaisa, cur),
                                            style = MaterialTheme.typography.titleMedium.copy(fontFamily = MonoFontFamily),
                                            fontWeight = FontWeight.Bold,
                                            color = if (pos) positiveMoneyColor() else dueMoneyColor(),
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showAddExpense) {
        AddExpenseDialog(
            defaultDate = if (todayDate.startsWith(monthKey)) todayDate else "$monthKey-01",
            members = members,
            currencySymbol = cur,
            onAdd = { date, cat, desc, paisa, targetUid, uri ->
                onAddExpense(date, cat, desc, paisa, targetUid, uri)
                showAddExpense = false
            },
            onDismiss = { showAddExpense = false },
        )
    }

    if (showAddDeposit) {
        AddOrEditDepositDialog(
            title = "Record Member Deposit",
            defaultDate = if (todayDate.startsWith(monthKey)) todayDate else "$monthKey-01",
            members = members,
            initialDeposit = null,
            currencySymbol = cur,
            onSave = { date, memberUid, memberName, paisa, method, note ->
                onAddDeposit(date, memberUid, memberName, paisa, method, note)
                showAddDeposit = false
            },
            onDismiss = { showAddDeposit = false },
        )
    }

    editingDeposit?.let { dep ->
        AddOrEditDepositDialog(
            title = "Edit Deposit — ${dep.memberName}",
            defaultDate = dep.date,
            members = members,
            initialDeposit = dep,
            currencySymbol = cur,
            onSave = { _, _, _, paisa, method, note ->
                onUpdateDeposit(dep, paisa, method, note)
                editingDeposit = null
            },
            onDismiss = { editingDeposit = null },
        )
    }

    if (showAddAdjustment) {
        AddAdjustmentDialog(
            members = members,
            currencySymbol = cur,
            onAdd = { memberUid, memberName, paisa, reason ->
                onAddAdjustment(memberUid, memberName, paisa, reason)
                showAddAdjustment = false
            },
            onDismiss = { showAddAdjustment = false },
        )
    }
}

@Composable
private fun ExpenseRowCard(
    expense: Expense,
    currencySymbol: String,
    canManage: Boolean,
    isOwn: Boolean,
    isClosed: Boolean,
    onApprove: () -> Unit,
    onReject: () -> Unit,
    onDelete: () -> Unit,
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(expense.description, style = MaterialTheme.typography.titleSmall)
                    Text(
                        text = "${ExpenseCategory.fromKey(expense.category).label} • ${expense.date} • By ${expense.purchasedByName}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = FinanceCalculator.formatMoney(expense.amountPaisa, currencySymbol),
                        style = MaterialTheme.typography.titleMedium.copy(fontFamily = MonoFontFamily),
                        fontWeight = FontWeight.Bold,
                    )
                    Surface(
                        color = when (expense.status) {
                            "approved" -> positiveMoneyColor().copy(alpha = 0.14f)
                            "rejected" -> dueMoneyColor().copy(alpha = 0.14f)
                            else -> MaterialTheme.colorScheme.secondaryContainer
                        },
                        shape = RoundedCornerShape(6.dp),
                    ) {
                        Text(
                            text = expense.status.uppercase(),
                            style = MaterialTheme.typography.labelSmall,
                            color = when (expense.status) {
                                "approved" -> positiveMoneyColor()
                                "rejected" -> dueMoneyColor()
                                else -> MaterialTheme.colorScheme.onSecondaryContainer
                            },
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        )
                    }
                }
            }

            if (!expense.receiptUri.isNullOrBlank()) {
                AsyncImage(
                    model = expense.receiptUri,
                    contentDescription = "Receipt photo for ${expense.description}",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(110.dp)
                        .clip(RoundedCornerShape(12.dp)),
                )
            }

            if (!isClosed && (canManage || (isOwn && expense.status == "pending"))) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    if (canManage && expense.status == "pending") {
                        Button(
                            onClick = onApprove,
                            colors = ButtonDefaults.buttonColors(containerColor = positiveMoneyColor()),
                            modifier = Modifier.height(36.dp),
                        ) {
                            Icon(Icons.Rounded.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Approve", style = MaterialTheme.typography.labelMedium)
                        }
                        OutlinedButton(
                            onClick = onReject,
                            modifier = Modifier.height(36.dp),
                        ) {
                            Text("Reject", style = MaterialTheme.typography.labelMedium, color = dueMoneyColor())
                        }
                    }
                    Spacer(Modifier.weight(1f))
                    IconButton(onClick = onDelete) {
                        Icon(
                            Icons.Rounded.DeleteOutline,
                            contentDescription = "Delete expense",
                            tint = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DepositRowCard(
    deposit: Deposit,
    currencySymbol: String,
    canManage: Boolean,
    isClosed: Boolean,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(deposit.memberName, style = MaterialTheme.typography.titleSmall)
                Text(
                    text = "${DepositMethod.fromKey(deposit.method).label} • ${deposit.date}" +
                        if (!deposit.note.isNullOrBlank()) " • ${deposit.note}" else "",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = FinanceCalculator.formatMoney(deposit.amountPaisa, currencySymbol),
                    style = MaterialTheme.typography.titleMedium.copy(fontFamily = MonoFontFamily),
                    fontWeight = FontWeight.Bold,
                    color = positiveMoneyColor(),
                )
                if (canManage && !isClosed) {
                    IconButton(onClick = onEdit) {
                        Icon(Icons.Rounded.Edit, contentDescription = "Edit deposit")
                    }
                    IconButton(onClick = onDelete) {
                        Icon(Icons.Rounded.DeleteOutline, contentDescription = "Delete deposit", tint = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddExpenseDialog(
    defaultDate: String,
    members: List<MessMember>,
    currencySymbol: String,
    onAdd: (String, String, String, Long, String?, String?) -> Unit,
    onDismiss: () -> Unit,
) {
    var date by remember { mutableStateOf(defaultDate) }
    var category by remember { mutableStateOf(ExpenseCategory.BAZAR) }
    var description by remember { mutableStateOf("") }
    var amountText by remember { mutableStateOf("") }
    var selectedTargetMember by remember { mutableStateOf(members.firstOrNull()) }
    var memberDropdownOpen by remember { mutableStateOf(false) }
    var receiptUri by remember { mutableStateOf<Uri?>(null) }

    val photoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
    ) { uri ->
        receiptUri = uri
    }

    val parsedPaisa = FinanceCalculator.parseMajorToPaisa(amountText)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add Bazar / Expense") },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.verticalScroll(rememberScrollState()),
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    ExpenseCategory.entries.forEach { cat ->
                        FilterChip(
                            selected = category == cat,
                            onClick = { category = cat },
                            label = {
                                Text(
                                    when (cat) {
                                        ExpenseCategory.BAZAR -> "Bazar"
                                        ExpenseCategory.SHARED_FIXED -> "Utility"
                                        ExpenseCategory.OTHER_INDIVIDUAL -> "Individual"
                                    }
                                )
                            },
                        )
                    }
                }

                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Items / Description *") },
                    placeholder = { Text("e.g., Rice 5kg, Fish, Vegetables") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("expense_description_input"),
                )
                OutlinedTextField(
                    value = amountText,
                    onValueChange = { amountText = it },
                    label = { Text("Amount ($currencySymbol) *") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("expense_amount_input"),
                )
                OutlinedTextField(
                    value = date,
                    onValueChange = { date = it },
                    label = { Text("Purchase Date (YYYY-MM-DD)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )

                if (category == ExpenseCategory.OTHER_INDIVIDUAL && members.isNotEmpty()) {
                    ExposedDropdownMenuBox(
                        expanded = memberDropdownOpen,
                        onExpandedChange = { memberDropdownOpen = !memberDropdownOpen },
                    ) {
                        OutlinedTextField(
                            value = selectedTargetMember?.displayName ?: "Select member",
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Charge to Member") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = memberDropdownOpen) },
                            modifier = Modifier
                                .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                                .fillMaxWidth(),
                        )
                        ExposedDropdownMenu(
                            expanded = memberDropdownOpen,
                            onDismissRequest = { memberDropdownOpen = false },
                        ) {
                            members.forEach { m ->
                                DropdownMenuItem(
                                    text = { Text(m.displayName) },
                                    onClick = {
                                        selectedTargetMember = m
                                        memberDropdownOpen = false
                                    },
                                )
                            }
                        }
                    }
                }

                OutlinedButton(
                    onClick = {
                        photoPicker.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Rounded.Image, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(if (receiptUri == null) "Attach Receipt Photo" else "Receipt Selected ✓")
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val paisa = parsedPaisa ?: return@Button
                    val targetUid = if (category == ExpenseCategory.OTHER_INDIVIDUAL) selectedTargetMember?.userId else null
                    onAdd(date.trim(), category.key, description.trim(), paisa, targetUid, receiptUri?.toString())
                },
                enabled = description.trim().isNotEmpty() && parsedPaisa != null && parsedPaisa > 0L,
                modifier = Modifier.testTag("confirm_add_expense_button"),
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddOrEditDepositDialog(
    title: String,
    defaultDate: String,
    members: List<MessMember>,
    initialDeposit: Deposit?,
    currencySymbol: String,
    onSave: (String, String, String, Long, String, String?) -> Unit,
    onDismiss: () -> Unit,
) {
    var selectedMember by remember { mutableStateOf(members.firstOrNull()) }
    var dropdownOpen by remember { mutableStateOf(false) }
    var date by remember { mutableStateOf(defaultDate) }
    var amountText by remember {
        mutableStateOf(
            if (initialDeposit != null) FinanceCalculator.paisaToMajor(initialDeposit.amountPaisa).toPlainString() else ""
        )
    }
    var method by remember {
        mutableStateOf(DepositMethod.fromKey(initialDeposit?.method))
    }
    var note by remember { mutableStateOf(initialDeposit?.note.orEmpty()) }

    val parsedPaisa = FinanceCalculator.parseMajorToPaisa(amountText)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.verticalScroll(rememberScrollState()),
            ) {
                if (initialDeposit == null && members.isNotEmpty()) {
                    ExposedDropdownMenuBox(
                        expanded = dropdownOpen,
                        onExpandedChange = { dropdownOpen = !dropdownOpen },
                    ) {
                        OutlinedTextField(
                            value = selectedMember?.displayName ?: "Select Member",
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Member") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = dropdownOpen) },
                            modifier = Modifier
                                .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                                .fillMaxWidth(),
                        )
                        ExposedDropdownMenu(
                            expanded = dropdownOpen,
                            onDismissRequest = { dropdownOpen = false },
                        ) {
                            members.forEach { m ->
                                DropdownMenuItem(
                                    text = { Text(m.displayName) },
                                    onClick = {
                                        selectedMember = m
                                        dropdownOpen = false
                                    },
                                )
                            }
                        }
                    }
                }

                OutlinedTextField(
                    value = amountText,
                    onValueChange = { amountText = it },
                    label = { Text("Deposit Amount ($currencySymbol) *") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("deposit_amount_input"),
                )

                Text("Payment Method", style = MaterialTheme.typography.labelMedium)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(DepositMethod.entries) { m ->
                        FilterChip(
                            selected = method == m,
                            onClick = { method = m },
                            label = { Text(m.label) },
                        )
                    }
                }

                if (initialDeposit == null) {
                    OutlinedTextField(
                        value = date,
                        onValueChange = { date = it },
                        label = { Text("Date (YYYY-MM-DD)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }

                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text("Reference / Note (optional)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val paisa = parsedPaisa ?: return@Button
                    val memberUid = initialDeposit?.memberUid ?: selectedMember?.userId ?: return@Button
                    val memberName = initialDeposit?.memberName ?: selectedMember?.displayName ?: return@Button
                    onSave(date.trim(), memberUid, memberName, paisa, method.key, note.trim().ifBlank { null })
                },
                enabled = parsedPaisa != null && parsedPaisa > 0L && (initialDeposit != null || selectedMember != null),
                modifier = Modifier.testTag("confirm_deposit_button"),
            ) {
                Text("Save Deposit")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddAdjustmentDialog(
    members: List<MessMember>,
    currencySymbol: String,
    onAdd: (String, String, Long, String) -> Unit,
    onDismiss: () -> Unit,
) {
    var selectedMember by remember { mutableStateOf(members.firstOrNull()) }
    var dropdownOpen by remember { mutableStateOf(false) }
    var amountText by remember { mutableStateOf("") }
    var isCredit by remember { mutableStateOf(true) }
    var reason by remember { mutableStateOf("") }

    val parsedPaisa = FinanceCalculator.parseMajorToPaisa(amountText)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Member Adjustment") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (members.isNotEmpty()) {
                    ExposedDropdownMenuBox(
                        expanded = dropdownOpen,
                        onExpandedChange = { dropdownOpen = !dropdownOpen },
                    ) {
                        OutlinedTextField(
                            value = selectedMember?.displayName ?: "Select Member",
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Member") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = dropdownOpen) },
                            modifier = Modifier
                                .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                                .fillMaxWidth(),
                        )
                        ExposedDropdownMenu(
                            expanded = dropdownOpen,
                            onDismissRequest = { dropdownOpen = false },
                        ) {
                            members.forEach { m ->
                                DropdownMenuItem(
                                    text = { Text(m.displayName) },
                                    onClick = {
                                        selectedMember = m
                                        dropdownOpen = false
                                    },
                                )
                            }
                        }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = isCredit,
                        onClick = { isCredit = true },
                        label = { Text("+ Credit / Refund") },
                    )
                    FilterChip(
                        selected = !isCredit,
                        onClick = { isCredit = false },
                        label = { Text("- Debit / Fine") },
                    )
                }
                OutlinedTextField(
                    value = amountText,
                    onValueChange = { amountText = it },
                    label = { Text("Amount ($currencySymbol)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = reason,
                    onValueChange = { reason = it },
                    label = { Text("Reason *") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val m = selectedMember ?: return@Button
                    val paisa = parsedPaisa ?: return@Button
                    val signed = if (isCredit) paisa else -paisa
                    onAdd(m.userId, m.displayName, signed, reason.trim())
                },
                enabled = selectedMember != null && parsedPaisa != null && parsedPaisa > 0L && reason.trim().isNotEmpty(),
            ) {
                Text("Apply")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}
