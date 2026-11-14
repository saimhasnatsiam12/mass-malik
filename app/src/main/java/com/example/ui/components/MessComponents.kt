package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.model.MessRole
import com.example.domain.FinanceCalculator
import com.example.domain.MemberFinanceSummary
import com.example.domain.MonthlyFinanceSummary
import com.example.ui.theme.DueRedDark
import com.example.ui.theme.DueRedLight
import com.example.ui.theme.MonoFontFamily
import com.example.ui.theme.PositiveGreenDark
import com.example.ui.theme.PositiveGreenLight
import java.math.BigDecimal

@Composable
fun positiveMoneyColor(): Color {
    val isDark = MaterialTheme.colorScheme.background.red < 0.2f
    return if (isDark) PositiveGreenDark else PositiveGreenLight
}

@Composable
fun dueMoneyColor(): Color {
    val isDark = MaterialTheme.colorScheme.background.red < 0.2f
    return if (isDark) DueRedDark else DueRedLight
}

@Composable
fun MonthSelectorBar(
    monthKey: String,
    isClosed: Boolean,
    onPreviousMonth: () -> Unit,
    onNextMonth: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f),
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
        ) {
            IconButton(
                onClick = onPreviousMonth,
                modifier = Modifier.testTag("prev_month_button"),
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowLeft,
                    contentDescription = "Previous month",
                )
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = formatMonthLabel(monthKey),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                if (isClosed) {
                    Surface(
                        color = MaterialTheme.colorScheme.error.copy(alpha = 0.14f),
                        shape = RoundedCornerShape(8.dp),
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Lock,
                                contentDescription = "Month locked",
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(13.dp),
                            )
                            Text(
                                text = "CLOSED",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.error,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    }
                }
            }

            IconButton(
                onClick = onNextMonth,
                modifier = Modifier.testTag("next_month_button"),
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                    contentDescription = "Next month",
                )
            }
        }
    }
}

@Composable
fun StatTile(
    label: String,
    value: String,
    icon: ImageVector,
    accentColor: Color = MaterialTheme.colorScheme.primary,
    modifier: Modifier = Modifier,
) {
    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = modifier,
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(accentColor.copy(alpha = 0.14f)),
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = accentColor,
                        modifier = Modifier.size(16.dp),
                    )
                }
            }
            Text(
                text = value,
                style = MaterialTheme.typography.titleLarge.copy(fontFamily = MonoFontFamily),
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
fun FinancialOverviewChartCard(
    summary: MonthlyFinanceSummary,
    currencySymbol: String,
    modifier: Modifier = Modifier,
) {
    val primaryColor = MaterialTheme.colorScheme.primary
    val secondaryColor = MaterialTheme.colorScheme.secondary
    val tertiaryColor = MaterialTheme.colorScheme.tertiary
    val trackColor = MaterialTheme.colorScheme.surfaceVariant

    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = "Monthly Cashflow & Meal Distribution",
                style = MaterialTheme.typography.titleMedium,
            )

            val maxAmount = maxOf(
                summary.totalDepositPaisa,
                summary.totalExpensePaisa,
                1L,
            ).toFloat()

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                BarMetricRow(
                    label = "Total Deposits",
                    valueText = FinanceCalculator.formatMoney(summary.totalDepositPaisa, currencySymbol),
                    fraction = (summary.totalDepositPaisa.toFloat() / maxAmount).coerceIn(0f, 1f),
                    barColor = primaryColor,
                    trackColor = trackColor,
                )
                BarMetricRow(
                    label = "Meal Bazar",
                    valueText = FinanceCalculator.formatMoney(summary.totalMealExpensePaisa, currencySymbol),
                    fraction = (summary.totalMealExpensePaisa.toFloat() / maxAmount).coerceIn(0f, 1f),
                    barColor = secondaryColor,
                    trackColor = trackColor,
                )
                BarMetricRow(
                    label = "Fixed & Individual",
                    valueText = FinanceCalculator.formatMoney(
                        summary.totalSharedFixedPaisa + summary.totalOtherIndividualPaisa,
                        currencySymbol,
                    ),
                    fraction = ((summary.totalSharedFixedPaisa + summary.totalOtherIndividualPaisa).toFloat() / maxAmount)
                        .coerceIn(0f, 1f),
                    barColor = tertiaryColor,
                    trackColor = trackColor,
                )
            }

            if (summary.memberSummaries.isNotEmpty() && summary.totalMeals.compareTo(BigDecimal.ZERO) > 0) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Meals by Member",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                val maxMemberMeals = summary.memberSummaries
                    .maxOfOrNull { it.totalMeals.toFloat() }
                    ?.coerceAtLeast(1f) ?: 1f

                Canvas(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(84.dp),
                ) {
                    val items = summary.memberSummaries.take(8)
                    val count = items.size.coerceAtLeast(1)
                    val gap = 12.dp.toPx()
                    val barWidth = ((size.width - gap * (count - 1)) / count).coerceAtLeast(10f)
                    items.forEachIndexed { idx, member ->
                        val ratio = (member.totalMeals.toFloat() / maxMemberMeals).coerceIn(0.05f, 1f)
                        val barHeight = size.height * ratio
                        val left = idx * (barWidth + gap)
                        drawRoundRect(
                            color = trackColor,
                            topLeft = Offset(left, 0f),
                            size = Size(barWidth, size.height),
                            cornerRadius = CornerRadius(8.dp.toPx(), 8.dp.toPx()),
                        )
                        drawRoundRect(
                            color = if (idx % 2 == 0) primaryColor else secondaryColor,
                            topLeft = Offset(left, size.height - barHeight),
                            size = Size(barWidth, barHeight),
                            cornerRadius = CornerRadius(8.dp.toPx(), 8.dp.toPx()),
                        )
                    }
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    summary.memberSummaries.take(8).forEach { m ->
                        Text(
                            text = m.displayName.split(" ").first().take(6),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Clip,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun BarMetricRow(
    label: String,
    valueText: String,
    fraction: Float,
    barColor: Color,
    trackColor: Color,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(text = label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                text = valueText,
                style = MaterialTheme.typography.labelMedium.copy(fontFamily = MonoFontFamily),
                fontWeight = FontWeight.SemiBold,
            )
        }
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(10.dp),
        ) {
            drawRoundRect(
                color = trackColor,
                size = size,
                cornerRadius = CornerRadius(size.height / 2f, size.height / 2f),
            )
            if (fraction > 0f) {
                drawRoundRect(
                    color = barColor,
                    size = Size(size.width * fraction, size.height),
                    cornerRadius = CornerRadius(size.height / 2f, size.height / 2f),
                )
            }
        }
    }
}

@Composable
fun MemberBalanceCard(
    summary: MemberFinanceSummary,
    currencySymbol: String,
    isCurrentUser: Boolean,
    onClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val isPositive = summary.netBalancePaisa >= 0L
    val balanceColor = if (isPositive) positiveMoneyColor() else dueMoneyColor()

    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isCurrentUser) {
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f)
            } else {
                MaterialTheme.colorScheme.surface
            },
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.weight(1f),
                ) {
                    Surface(
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f),
                        shape = CircleShape,
                        modifier = Modifier.size(36.dp),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                text = summary.displayName.take(1).uppercase(),
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    }
                    Column {
                        Text(
                            text = if (isCurrentUser) "${summary.displayName} (You)" else summary.displayName,
                            style = MaterialTheme.typography.titleSmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = MessRole.fromKey(summary.role).label,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = (if (isPositive) "+" else "") +
                            FinanceCalculator.formatMoney(summary.netBalancePaisa, currencySymbol),
                        style = MaterialTheme.typography.titleMedium.copy(fontFamily = MonoFontFamily),
                        fontWeight = FontWeight.Bold,
                        color = balanceColor,
                    )
                    Text(
                        text = if (isPositive) "Surplus" else "Due",
                        style = MaterialTheme.typography.labelSmall,
                        color = balanceColor,
                    )
                }
            }

            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth(),
            ) {
                LedgerMiniChip("Meals", FinanceCalculator.formatMealCount(summary.totalMeals))
                LedgerMiniChip("Meal Cost", FinanceCalculator.formatMoney(summary.mealCostPaisa, currencySymbol))
                LedgerMiniChip("Deposit", FinanceCalculator.formatMoney(summary.depositPaisa, currencySymbol))
                if (summary.sharedFixedSharePaisa > 0L || summary.otherIndividualPaisa > 0L) {
                    LedgerMiniChip(
                        "Other",
                        FinanceCalculator.formatMoney(
                            summary.sharedFixedSharePaisa + summary.otherIndividualPaisa,
                            currencySymbol,
                        ),
                    )
                }
            }
        }
    }
}

@Composable
private fun LedgerMiniChip(label: String, value: String) {
    Column {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.labelMedium.copy(fontFamily = MonoFontFamily),
            fontWeight = FontWeight.SemiBold,
        )
    }
}

fun formatMonthLabel(monthKey: String): String {
    val parts = monthKey.split("-")
    if (parts.size != 2) return monthKey
    val monthNum = parts[1].toIntOrNull() ?: return monthKey
    val names = listOf(
        "January", "February", "March", "April", "May", "June",
        "July", "August", "September", "October", "November", "December",
    )
    val monthName = names.getOrNull(monthNum - 1) ?: return monthKey
    return "$monthName ${parts[0]}"
}
