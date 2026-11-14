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
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.RestaurantMenu
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedIconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.model.MealEntry
import com.example.data.model.Mess
import com.example.data.model.MessMember
import com.example.domain.FinanceCalculator
import com.example.domain.MonthlyFinanceSummary
import com.example.ui.components.MonthSelectorBar
import com.example.ui.theme.MonoFontFamily
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@Composable
fun MealsScreen(
    mess: Mess,
    currentUserId: String,
    monthKey: String,
    selectedDate: String,
    members: List<MessMember>,
    meals: List<MealEntry>,
    summary: MonthlyFinanceSummary,
    onPreviousMonth: () -> Unit,
    onNextMonth: () -> Unit,
    onSelectDate: (String) -> Unit,
    onSaveMeal: (
        date: String,
        memberUid: String,
        memberName: String,
        breakfast: Double,
        lunch: Double,
        dinner: Double,
    ) -> Unit,
    onDeleteMeal: (MealEntry) -> Unit,
    modifier: Modifier = Modifier,
) {
    val isClosed = mess.isMonthClosed(monthKey)
    val canEditOthers = mess.roleFor(currentUserId).canManageFinances()
    var editingMember by remember { mutableStateOf<MessMember?>(null) }

    val dayMeals = remember(meals, selectedDate) {
        meals.filter { it.date == selectedDate }.associateBy { it.memberUid }
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
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

        if (isClosed) {
            item {
                Surface(
                    color = MaterialTheme.colorScheme.error.copy(alpha = 0.12f),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.padding(12.dp),
                    ) {
                        Icon(Icons.Rounded.Lock, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                        Text(
                            text = "This month is closed. Meals are locked against edits.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            }
        }

        // Date Navigator Card
        item {
            Card(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 10.dp),
                ) {
                    IconButton(onClick = { onSelectDate(shiftDate(selectedDate, -1)) }) {
                        Icon(Icons.AutoMirrored.Rounded.KeyboardArrowLeft, contentDescription = "Previous day")
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = formatDateReadable(selectedDate),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                        )
                        val dayTotal = dayMeals.values.sumOf { it.totalMeals }
                        Text(
                            text = "Day Total: ${formatDoubleMeal(dayTotal)} meals",
                            style = MaterialTheme.typography.labelMedium.copy(fontFamily = MonoFontFamily),
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                    IconButton(onClick = { onSelectDate(shiftDate(selectedDate, 1)) }) {
                        Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = "Next day")
                    }
                }
            }
        }

        item {
            Text(
                text = "Daily Meal Roster",
                style = MaterialTheme.typography.titleMedium,
            )
        }

        val activeMembers = members.filter { it.status == "active" }
        items(activeMembers, key = { it.userId }) { member ->
            val entry = dayMeals[member.userId]
            val canEditThis = !isClosed && (canEditOthers || member.userId == currentUserId)

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
                        Text(
                            text = if (member.userId == currentUserId) "${member.displayName} (You)" else member.displayName,
                            style = MaterialTheme.typography.titleSmall,
                        )
                        Spacer(Modifier.height(4.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            MealBadge("B", entry?.breakfast ?: 0.0)
                            MealBadge("L", entry?.lunch ?: 0.0)
                            MealBadge("D", entry?.dinner ?: 0.0)
                        }
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = formatDoubleMeal(entry?.totalMeals ?: 0.0),
                            style = MaterialTheme.typography.titleLarge.copy(fontFamily = MonoFontFamily),
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        Spacer(Modifier.width(8.dp))
                        if (canEditThis) {
                            IconButton(
                                onClick = { editingMember = member },
                                modifier = Modifier.testTag("edit_meal_${member.userId}"),
                            ) {
                                Icon(Icons.Rounded.Edit, contentDescription = "Edit meals for ${member.displayName}")
                            }
                        }
                        if (canEditOthers && !isClosed && entry != null) {
                            IconButton(onClick = { onDeleteMeal(entry) }) {
                                Icon(
                                    Icons.Rounded.DeleteOutline,
                                    contentDescription = "Delete meal entry",
                                    tint = MaterialTheme.colorScheme.error,
                                )
                            }
                        }
                    }
                }
            }
        }

        item {
            Spacer(Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Icon(Icons.Rounded.RestaurantMenu, contentDescription = null, tint = MaterialTheme.colorScheme.secondary, modifier = Modifier.size(18.dp))
                Text(
                    text = "Monthly Meal Summary (${formatMonthLabel(monthKey)})",
                    style = MaterialTheme.typography.titleMedium,
                )
            }
        }

        items(summary.memberSummaries, key = { "sum_${it.memberUid}" }) { m ->
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.surface,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.padding(12.dp),
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(m.displayName, style = MaterialTheme.typography.titleSmall)
                        Text(
                            text = "B ${FinanceCalculator.formatMealCount(m.breakfastCount)} • L ${FinanceCalculator.formatMealCount(m.lunchCount)} • D ${FinanceCalculator.formatMealCount(m.dinnerCount)}",
                            style = MaterialTheme.typography.bodySmall.copy(fontFamily = MonoFontFamily),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Text(
                        text = "${FinanceCalculator.formatMealCount(m.totalMeals)} meals",
                        style = MaterialTheme.typography.titleSmall.copy(fontFamily = MonoFontFamily),
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }
    }

    editingMember?.let { member ->
        val existing = dayMeals[member.userId]
        MealEditDialog(
            memberName = member.displayName,
            date = selectedDate,
            initialBreakfast = existing?.breakfast ?: 0.0,
            initialLunch = existing?.lunch ?: 1.0,
            initialDinner = existing?.dinner ?: 1.0,
            onSave = { b, l, d ->
                onSaveMeal(selectedDate, member.userId, member.displayName, b, l, d)
                editingMember = null
            },
            onDismiss = { editingMember = null },
        )
    }
}

@Composable
private fun MealBadge(label: String, count: Double) {
    Surface(
        color = if (count > 0.0) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(8.dp),
    ) {
        Text(
            text = "$label: ${formatDoubleMeal(count)}",
            style = MaterialTheme.typography.labelSmall.copy(fontFamily = MonoFontFamily),
            color = if (count > 0.0) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
        )
    }
}

@Composable
private fun MealEditDialog(
    memberName: String,
    date: String,
    initialBreakfast: Double,
    initialLunch: Double,
    initialDinner: Double,
    onSave: (Double, Double, Double) -> Unit,
    onDismiss: () -> Unit,
) {
    var breakfast by remember { mutableDoubleStateOf(initialBreakfast) }
    var lunch by remember { mutableDoubleStateOf(initialLunch) }
    var dinner by remember { mutableDoubleStateOf(initialDinner) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Meals — $memberName") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = "Date: ${formatDateReadable(date)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                MealStepperRow("Breakfast", breakfast) { breakfast = it }
                MealStepperRow("Lunch", lunch) { lunch = it }
                MealStepperRow("Dinner", dinner) { dinner = it }
                Text(
                    text = "Total: ${formatDoubleMeal(breakfast + lunch + dinner)} meals",
                    style = MaterialTheme.typography.titleMedium.copy(fontFamily = MonoFontFamily),
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onSave(breakfast, lunch, dinner) },
                modifier = Modifier.testTag("save_meal_button"),
            ) {
                Text("Save Meals")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

@Composable
private fun MealStepperRow(
    label: String,
    value: Double,
    onChange: (Double) -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(label, style = MaterialTheme.typography.titleSmall)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedIconButton(
                onClick = { onChange((value - 0.5).coerceAtLeast(0.0)) },
                modifier = Modifier.size(40.dp),
            ) {
                Icon(Icons.Rounded.Remove, contentDescription = "Decrease $label")
            }
            Text(
                text = formatDoubleMeal(value),
                style = MaterialTheme.typography.titleMedium.copy(fontFamily = MonoFontFamily),
                fontWeight = FontWeight.Bold,
            )
            OutlinedIconButton(
                onClick = { onChange((value + 0.5).coerceAtMost(20.0)) },
                modifier = Modifier.size(40.dp),
            ) {
                Icon(Icons.Rounded.Add, contentDescription = "Increase $label")
            }
        }
    }
}

private fun formatDoubleMeal(value: Double): String =
    if (value % 1.0 == 0.0) value.toInt().toString() else String.format(Locale.US, "%.1f", value)

private fun shiftDate(dateStr: String, deltaDays: Int): String {
    val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
    val cal = Calendar.getInstance()
    cal.time = runCatching { sdf.parse(dateStr) }.getOrNull() ?: Date()
    cal.add(Calendar.DAY_OF_MONTH, deltaDays)
    return sdf.format(cal.time)
}

private fun formatDateReadable(dateStr: String): String {
    val inFmt = SimpleDateFormat("yyyy-MM-dd", Locale.US)
    val outFmt = SimpleDateFormat("EEE, MMM dd, yyyy", Locale.US)
    val parsed = runCatching { inFmt.parse(dateStr) }.getOrNull() ?: return dateStr
    return outFmt.format(parsed)
}

private fun formatMonthLabel(monthKey: String): String =
    com.example.ui.components.formatMonthLabel(monthKey)
