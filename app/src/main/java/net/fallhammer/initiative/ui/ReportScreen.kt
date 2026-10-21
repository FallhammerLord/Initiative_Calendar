package net.fallhammer.initiative.ui

import android.content.Context
import android.content.Intent
import android.graphics.Paint
import android.graphics.Typeface
import android.os.Bundle
import android.os.CancellationSignal
import android.os.Handler
import android.os.Looper
import android.os.ParcelFileDescriptor
import android.print.PageRange
import android.print.PrintAttributes
import android.print.PrintDocumentAdapter
import android.print.PrintDocumentInfo
import android.print.PrintManager
import android.print.pdf.PrintedPdfDocument
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.res.ResourcesCompat
import initiative.core.Formats
import initiative.core.Item
import initiative.core.Outcome
import initiative.core.ReportLine
import net.fallhammer.initiative.R
import java.io.FileOutputStream
import java.io.IOException
import java.time.LocalDate

/**
 * A day's report: the day's items in time order, each with what became of it,
 * to keep or to share. Plain facts in neutral words, no counts or scores. Notes
 * stay out unless switched on. Share sends it as text through Android's share
 * menu; Save PDF uses Android's print screen. Nothing is ever sent by itself.
 */
@Composable
fun ReportScreen(
    day: LocalDate,
    lines: List<ReportLine>,
    notes: Boolean,
    onNotes: (Boolean) -> Unit,
    onClose: () -> Unit,
    marks: List<Item> = emptyList(),
) {
    val context = LocalContext.current
    Screen {
        Strip {
            Title(stringResource(R.string.report_heading, Formats.day(day)), style = Type.heading, modifier = Modifier.weight(1f))
            QuietButton(stringResource(R.string.report_close), onClose)
        }
        Spacer(Modifier.height(Space.panel))
        Card(Modifier.weight(1f), inner = Space.l) {
            // The day's markers first: what kind of day it was.
            if (marks.isNotEmpty()) {
                Body(stringResource(R.string.report_marks, marks.joinToString(", ") { it.title }), color = Ink.accent, style = Type.bodySmall)
                Spacer(Modifier.height(Space.s))
            }
            if (lines.isEmpty()) {
                Body(stringResource(R.string.report_empty), style = Type.bodySmall)
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(Space.s)) {
                    items(lines, key = { it.item.id }) { ReportRow(it, notes) }
                }
            }
        }
        Spacer(Modifier.height(Space.panel))
        Card(inner = Space.l) { SettingSwitch(stringResource(R.string.report_notes), notes, onNotes) }
        Spacer(Modifier.height(Space.panel))
        ActionRow(
            Action(2f) { PrimaryButton(stringResource(R.string.report_share), { shareReport(context, day, lines, notes, marks) }, it) },
            Action { SecondaryButton(stringResource(R.string.report_pdf), { printReport(context, day, lines, notes, marks) }, it) },
        )
    }
}

/** One item: its time, title, what became of it, its steps, and its note when shown. */
@Composable
private fun ReportRow(line: ReportLine, notes: Boolean) {
    val context = LocalContext.current
    val item = line.item
    val time = @Composable { m: Modifier -> Text(timeOf(context, line), style = Type.bodySmall, color = Ink.dim, modifier = m) }
    val body = @Composable { m: Modifier ->
        Column(m) {
            Text(item.title, style = Type.bodySmall.copy(fontWeight = if (item.isAnchor) FontWeight.Bold else FontWeight.Normal), color = Ink.text)
            Text(outcomeOf(context, line), style = Type.caption, color = if (line.outcome == Outcome.DONE) Ink.accent else Ink.dim)
            item.steps.forEach { s ->
                Text(
                    stringResource(if (s.done) R.string.log_step_done else R.string.log_step_open, s.title),
                    style = Type.caption,
                    color = if (s.done) Ink.dim else Ink.text,
                )
            }
            if (notes && item.notes.isNotBlank()) Text(stringResource(R.string.report_note, item.notes), style = Type.caption, color = Ink.dim)
        }
    }
    // At large font sizes the time sits above the title, rather than squeezing it.
    if (LocalDensity.current.fontScale > LARGE_FONT) {
        Column(Modifier.fillMaxWidth()) {
            time(Modifier)
            body(Modifier)
        }
    } else {
        Row(Modifier.fillMaxWidth()) {
            time(Modifier.width(TIME_COLUMN))
            body(Modifier.weight(1f))
        }
    }
}

private fun timeOf(context: Context, line: ReportLine): String =
    line.time?.let { Formats.clock(it) } ?: context.getString(R.string.report_any_time)

private fun outcomeOf(context: Context, line: ReportLine): String = when (line.outcome) {
    Outcome.DONE -> line.item.doneAt?.let { context.getString(R.string.report_done_at, Formats.clock(it)) } ?: context.getString(R.string.report_done)
    Outcome.PASSED -> context.getString(R.string.report_passed)
    Outcome.MOVED -> context.getString(R.string.report_moved)
    Outcome.OPEN -> context.getString(R.string.report_still_open)
}

/** One line of the report as text: [indent] 1 for an item's steps and note. */
private data class ReportText(val text: String, val indent: Int = 0, val heading: Boolean = false)

private fun reportText(context: Context, day: LocalDate, lines: List<ReportLine>, notes: Boolean, marks: List<Item>): List<ReportText> = buildList {
    add(ReportText(context.getString(R.string.report_heading, Formats.day(day)), heading = true))
    if (marks.isNotEmpty()) add(ReportText(context.getString(R.string.report_marks, marks.joinToString(", ") { it.title })))
    if (lines.isEmpty()) add(ReportText(context.getString(R.string.report_empty)))
    lines.forEach { line ->
        add(ReportText(context.getString(R.string.report_line, timeOf(context, line), line.item.title, outcomeOf(context, line))))
        line.item.steps.forEach { s ->
            add(ReportText(context.getString(if (s.done) R.string.log_step_done else R.string.log_step_open, s.title), indent = 1))
        }
        if (notes && line.item.notes.isNotBlank()) add(ReportText(context.getString(R.string.report_note, line.item.notes), indent = 1))
    }
}

/** Share as plain text, through Android's share menu: the person picks the app. */
private fun shareReport(context: Context, day: LocalDate, lines: List<ReportLine>, notes: Boolean, marks: List<Item>) {
    val rows = reportText(context, day, lines, notes, marks)
    val text = rows.joinToString("\n") { "  ".repeat(it.indent) + it.text }
    val send = Intent(Intent.ACTION_SEND)
        .setType("text/plain")
        .putExtra(Intent.EXTRA_SUBJECT, rows.first().text)
        .putExtra(Intent.EXTRA_TEXT, text)
    context.startActivity(Intent.createChooser(send, context.getString(R.string.report_share_title)))
}

/** Save as PDF through Android's print screen, which asks where it goes. */
private fun printReport(context: Context, day: LocalDate, lines: List<ReportLine>, notes: Boolean, marks: List<Item>) {
    val printing = context.getSystemService(PrintManager::class.java) ?: return
    val rows = reportText(context, day, lines, notes, marks)
    printing.print(rows.first().text, ReportPrintAdapter(context.applicationContext, rows), null)
}

/** Lays the report out on letter or A4 pages, as the print screen asks, in Atkinson Hyperlegible. */
private class ReportPrintAdapter(private val context: Context, private val rows: List<ReportText>) : PrintDocumentAdapter() {
    private var attributes: PrintAttributes? = null
    private val main = Handler(Looper.getMainLooper())

    override fun onLayout(
        oldAttributes: PrintAttributes?,
        newAttributes: PrintAttributes,
        cancellationSignal: CancellationSignal?,
        callback: LayoutResultCallback,
        extras: Bundle?,
    ) {
        if (cancellationSignal?.isCanceled == true) {
            callback.onLayoutCancelled()
            return
        }
        attributes = newAttributes
        val info = PrintDocumentInfo.Builder(PDF_NAME).setContentType(PrintDocumentInfo.CONTENT_TYPE_DOCUMENT).build()
        callback.onLayoutFinished(info, newAttributes != oldAttributes)
    }

    override fun onWrite(
        pages: Array<out PageRange>,
        destination: ParcelFileDescriptor,
        cancellationSignal: CancellationSignal?,
        callback: WriteResultCallback,
    ) {
        val attrs = attributes ?: return callback.onWriteFailed(null)
        // Fonts and the file are read and written off the main thread.
        Thread {
            val doc = PrintedPdfDocument(context, attrs)
            try {
                draw(doc)
                FileOutputStream(destination.fileDescriptor).use { doc.writeTo(it) }
                main.post { callback.onWriteFinished(arrayOf(PageRange.ALL_PAGES)) }
            } catch (e: IOException) {
                main.post { callback.onWriteFailed(e.message) }
            } finally {
                doc.close()
            }
        }.start()
    }

    private fun draw(doc: PrintedPdfDocument) {
        val regular = ResourcesCompat.getFont(context, R.font.atkinson_regular) ?: Typeface.DEFAULT
        val bold = ResourcesCompat.getFont(context, R.font.atkinson_bold) ?: Typeface.DEFAULT_BOLD
        val body = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = regular; textSize = BODY_PT; color = Paper.ink.toArgb() }
        val heading = Paint(body).apply { typeface = bold; textSize = HEADING_PT }
        val indented = Paint(body).apply { color = Paper.dim.toArgb() }
        var page = doc.startPage(0)
        var number = 0
        val box = doc.pageContentRect
        var y = box.top + HEADING_PT
        rows.forEach { row ->
            val paint = when {
                row.heading -> heading
                row.indent > 0 -> indented
                else -> body
            }
            val left = box.left + row.indent * INDENT_PT
            wrap(row.text, paint, box.right - left).forEach { text ->
                if (y > box.bottom) {
                    doc.finishPage(page)
                    page = doc.startPage(++number)
                    y = box.top + BODY_PT
                }
                page.canvas.drawText(text, left, y, paint)
                y += paint.textSize * LINE
            }
            if (row.heading) y += BODY_PT
        }
        doc.finishPage(page)
    }

    /** Word by word, as many as fit in [width]. */
    private fun wrap(text: String, paint: Paint, width: Float): List<String> {
        val lines = mutableListOf<String>()
        var line = ""
        text.split(" ").forEach { word ->
            val tried = if (line.isEmpty()) word else "$line $word"
            if (line.isNotEmpty() && paint.measureText(tried) > width) {
                lines += line
                line = word
            } else {
                line = tried
            }
        }
        if (line.isNotEmpty()) lines += line
        return lines
    }

    private companion object {
        const val PDF_NAME = "day-report.pdf"

        // In points, as print is measured.
        const val BODY_PT = 12f
        const val HEADING_PT = 18f
        const val INDENT_PT = 18f
        const val LINE = 1.5f
    }
}

private val TIME_COLUMN = 96.dp
