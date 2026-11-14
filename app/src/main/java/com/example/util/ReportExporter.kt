package com.example.util

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import androidx.core.content.FileProvider
import com.example.data.model.Deposit
import com.example.data.model.Expense
import com.example.data.model.Mess
import com.example.domain.FinanceCalculator
import com.example.domain.MonthlyFinanceSummary
import java.io.File
import java.io.FileOutputStream

object ReportExporter {

    fun exportMonthlyPdfAndShare(
        context: Context,
        mess: Mess,
        summary: MonthlyFinanceSummary,
    ): Result<Unit> = runCatching {
        val reportsDir = File(context.cacheDir, "reports").apply { mkdirs() }
        val safeName = mess.name.replace(Regex("[^a-zA-Z0-9_-]"), "_").take(30)
        val file = File(reportsDir, "MessMalik_${safeName}_${summary.monthKey}.pdf")

        val pdf = PdfDocument()
        val pageInfo = PdfDocument.PageInfo.Builder(595, 842, 1).create()
        val page = pdf.startPage(pageInfo)
        val canvas = page.canvas

        val titlePaint = Paint().apply {
            color = Color.parseColor("#0F6B52")
            textSize = 20f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val subtitlePaint = Paint().apply {
            color = Color.DKGRAY
            textSize = 11f
        }
        val headerPaint = Paint().apply {
            color = Color.BLACK
            textSize = 13f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val bodyPaint = Paint().apply {
            color = Color.BLACK
            textSize = 10f
        }
        val linePaint = Paint().apply {
            color = Color.LTGRAY
            strokeWidth = 1f
        }

        var y = 48f
        canvas.drawText("Mess Malik — Monthly Financial Report", 36f, y, titlePaint)
        y += 20f
        canvas.drawText("Mess: ${mess.name}  |  Month: ${summary.monthKey}  |  Invite: ${mess.inviteCode}", 36f, y, subtitlePaint)
        y += 14f
        canvas.drawLine(36f, y, 559f, y, linePaint)
        y += 22f

        val cur = mess.currencySymbol
        canvas.drawText("Financial Overview", 36f, y, headerPaint)
        y += 18f
        canvas.drawText("Total Meal (Bazar) Expense: ${FinanceCalculator.formatMoney(summary.totalMealExpensePaisa, cur)}", 36f, y, bodyPaint)
        canvas.drawText("Total Meals: ${FinanceCalculator.formatMealCount(summary.totalMeals)}", 320f, y, bodyPaint)
        y += 16f
        canvas.drawText("Meal Rate: $cur${summary.mealRateDisplay} / meal", 36f, y, bodyPaint)
        canvas.drawText("Shared Fixed Expense: ${FinanceCalculator.formatMoney(summary.totalSharedFixedPaisa, cur)}", 320f, y, bodyPaint)
        y += 16f
        canvas.drawText("Total Deposits: ${FinanceCalculator.formatMoney(summary.totalDepositPaisa, cur)}", 36f, y, bodyPaint)
        canvas.drawText("Remaining Mess Cash: ${FinanceCalculator.formatMoney(summary.remainingCashPaisa, cur)}", 320f, y, bodyPaint)
        y += 22f
        canvas.drawLine(36f, y, 559f, y, linePaint)
        y += 20f

        canvas.drawText("Member Balances", 36f, y, headerPaint)
        y += 18f
        canvas.drawText("Member", 36f, y, headerPaint)
        canvas.drawText("Meals", 200f, y, headerPaint)
        canvas.drawText("Meal Cost", 265f, y, headerPaint)
        canvas.drawText("Deposit", 360f, y, headerPaint)
        canvas.drawText("Net Balance", 455f, y, headerPaint)
        y += 8f
        canvas.drawLine(36f, y, 559f, y, linePaint)
        y += 16f

        for (m in summary.memberSummaries) {
            if (y > 790f) break
            canvas.drawText(m.displayName.take(22), 36f, y, bodyPaint)
            canvas.drawText(FinanceCalculator.formatMealCount(m.totalMeals), 200f, y, bodyPaint)
            canvas.drawText(FinanceCalculator.formatMoney(m.mealCostPaisa, cur), 265f, y, bodyPaint)
            canvas.drawText(FinanceCalculator.formatMoney(m.depositPaisa, cur), 360f, y, bodyPaint)
            val statusLabel = if (m.netBalancePaisa >= 0) "+" else ""
            canvas.drawText("$statusLabel${FinanceCalculator.formatMoney(m.netBalancePaisa, cur)}", 455f, y, bodyPaint)
            y += 16f
        }

        pdf.finishPage(page)
        FileOutputStream(file).use { out -> pdf.writeTo(out) }
        pdf.close()

        shareFile(context, file, "application/pdf", "Mess Malik Report (${summary.monthKey})")
    }

    fun exportCsvAndShare(
        context: Context,
        mess: Mess,
        summary: MonthlyFinanceSummary,
        expenses: List<Expense>,
        deposits: List<Deposit>,
    ): Result<Unit> = runCatching {
        val reportsDir = File(context.cacheDir, "reports").apply { mkdirs() }
        val safeName = mess.name.replace(Regex("[^a-zA-Z0-9_-]"), "_").take(30)
        val file = File(reportsDir, "MessMalik_${safeName}_${summary.monthKey}.csv")
        val cur = mess.currencySymbol

        val csv = buildString {
            appendLine("Mess Malik Monthly Ledger,${escapeCsv(mess.name)},${summary.monthKey}")
            appendLine("Total Meals,${FinanceCalculator.formatMealCount(summary.totalMeals)},Meal Rate,${cur}${summary.mealRateDisplay}")
            appendLine("Total Approved Expense,${FinanceCalculator.paisaToMajor(summary.totalExpensePaisa)},Total Deposits,${FinanceCalculator.paisaToMajor(summary.totalDepositPaisa)}")
            appendLine()
            appendLine("MEMBER BALANCES")
            appendLine("Member,Role,Status,Meals,Meal Cost,Shared Utility,Other Individual,Adjustment,Deposit,Net Balance")
            summary.memberSummaries.forEach { m ->
                appendLine(
                    listOf(
                        escapeCsv(m.displayName),
                        m.role,
                        m.status,
                        FinanceCalculator.formatMealCount(m.totalMeals),
                        FinanceCalculator.paisaToMajor(m.mealCostPaisa).toPlainString(),
                        FinanceCalculator.paisaToMajor(m.sharedFixedSharePaisa).toPlainString(),
                        FinanceCalculator.paisaToMajor(m.otherIndividualPaisa).toPlainString(),
                        FinanceCalculator.paisaToMajor(m.adjustmentPaisa).toPlainString(),
                        FinanceCalculator.paisaToMajor(m.depositPaisa).toPlainString(),
                        FinanceCalculator.paisaToMajor(m.netBalancePaisa).toPlainString(),
                    ).joinToString(",")
                )
            }
            appendLine()
            appendLine("EXPENSES")
            appendLine("Date,Category,Description,Amount,Purchased By,Status")
            expenses.forEach { e ->
                appendLine(
                    listOf(
                        e.date,
                        e.category,
                        escapeCsv(e.description),
                        FinanceCalculator.paisaToMajor(e.amountPaisa).toPlainString(),
                        escapeCsv(e.purchasedByName),
                        e.status,
                    ).joinToString(",")
                )
            }
            appendLine()
            appendLine("DEPOSITS")
            appendLine("Date,Member,Method,Amount,Note")
            deposits.forEach { d ->
                appendLine(
                    listOf(
                        d.date,
                        escapeCsv(d.memberName),
                        d.method,
                        FinanceCalculator.paisaToMajor(d.amountPaisa).toPlainString(),
                        escapeCsv(d.note ?: ""),
                    ).joinToString(",")
                )
            }
        }
        file.writeText(csv)
        shareFile(context, file, "text/csv", "Mess Malik Excel/CSV (${summary.monthKey})")
    }

    private fun escapeCsv(value: String): String {
        val escaped = value.replace("\"", "\"\"")
        return "\"$escaped\""
    }

    private fun shareFile(context: Context, file: File, mimeType: String, subject: String) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, subject)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val chooser = Intent.createChooser(intent, subject).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(chooser)
    }
}
