package com.linode.manager.ui.components

import java.time.Duration
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Formatting helpers shared by all screens so numbers/dates look the same everywhere. */
object Fmt {
    private val dateTime = DateTimeFormatter.ofPattern("MMM d, yyyy · HH:mm", Locale.getDefault())
    private val date = DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.getDefault())

    /** Linode API timestamps are UTC without an offset: "2026-09-12T14:29:51". */
    private fun parse(iso: String?): LocalDateTime? =
        try {
            if (iso.isNullOrBlank()) {
                null
            } else {
                LocalDateTime
                    .parse(iso.take(19))
                    .atOffset(ZoneOffset.UTC)
                    .atZoneSameInstant(ZoneId.systemDefault())
                    .toLocalDateTime()
            }
        } catch (_: Exception) {
            null
        }

    fun dateTime(iso: String?): String = parse(iso)?.format(dateTime) ?: (iso ?: "—")

    fun date(iso: String?): String = parse(iso)?.format(date) ?: (iso ?: "—")

    fun relative(iso: String?): String {
        val t = parse(iso) ?: return iso ?: ""
        val d = Duration.between(t, LocalDateTime.now())
        return when {
            d.isNegative -> dateTime(iso)
            d.toMinutes() < 1 -> "just now"
            d.toMinutes() < 60 -> "${d.toMinutes()}m ago"
            d.toHours() < 24 -> "${d.toHours()}h ago"
            d.toDays() < 7 -> "${d.toDays()}d ago"
            else -> date(iso)
        }
    }

    /** Megabytes from the API → "2 GB" / "512 MB". */
    fun mb(mb: Int?): String =
        when {
            mb == null -> "—"
            mb >= 1024 && mb % 1024 == 0 -> "${mb / 1024} GB"
            mb >= 1024 -> String.format(Locale.US, "%.1f GB", mb / 1024.0)
            else -> "$mb MB"
        }

    fun bytes(b: Long): String =
        when {
            b >= 1L shl 30 -> String.format(Locale.US, "%.1f GB", b / (1L shl 30).toDouble())
            b >= 1L shl 20 -> String.format(Locale.US, "%.1f MB", b / (1L shl 20).toDouble())
            b >= 1L shl 10 -> String.format(Locale.US, "%.0f KB", b / (1L shl 10).toDouble())
            else -> "$b B"
        }

    fun money(
        v: Double?,
        currency: String? = null,
    ): String {
        if (v == null) return "—"
        val sym = if (currency == null || currency.equals("USD", true)) "$" else "$currency "
        return if (v < 0) "-$sym${String.format(Locale.US, "%.2f", -v)}" else "$sym${String.format(Locale.US, "%.2f", v)}"
    }

    fun label(raw: String?): String = raw?.replace('_', ' ')?.replaceFirstChar { it.uppercase() } ?: "—"
}
