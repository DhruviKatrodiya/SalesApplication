package com.salesapp.mobile.ui.common

import android.widget.TextView
import androidx.core.content.ContextCompat
import com.salesapp.mobile.R

/** Colours a [TextView] styled with @style/StatusChip to look like the website status pills. */
object Chips {

    enum class Tone(val bg: Int, val fg: Int) {
        SUCCESS(R.drawable.chip_success, R.color.chip_success_fg),
        DANGER(R.drawable.chip_danger, R.color.chip_danger_fg),
        WARNING(R.drawable.chip_warning, R.color.chip_warning_fg),
        INFO(R.drawable.chip_info, R.color.chip_info_fg),
        PURPLE(R.drawable.chip_purple, R.color.chip_purple_fg),
        NEUTRAL(R.drawable.chip_neutral, R.color.chip_neutral_fg),
    }

    fun set(tv: TextView, text: String, tone: Tone) {
        tv.text = text
        tv.setBackgroundResource(tone.bg)
        tv.setTextColor(ContextCompat.getColor(tv.context, tone.fg))
    }

    /** Active / Inactive convenience. */
    fun active(tv: TextView, isActive: Boolean) =
        set(tv, if (isActive) "Active" else "Inactive", if (isActive) Tone.SUCCESS else Tone.DANGER)

    /** Order lifecycle status → website colours. */
    fun orderStatus(tv: TextView, label: String) = set(
        tv, label,
        when (label.lowercase()) {
            "delivered", "completed" -> Tone.SUCCESS
            "dispatched" -> Tone.PURPLE
            "cancelled" -> Tone.NEUTRAL
            "pending", "remaining" -> Tone.WARNING
            else -> Tone.INFO
        },
    )

    /** Payment status → website colours. */
    fun paymentStatus(tv: TextView, label: String) = set(
        tv, label,
        when (label.lowercase()) {
            "paid" -> Tone.SUCCESS
            "partial" -> Tone.WARNING
            "advance" -> Tone.INFO
            else -> Tone.WARNING
        },
    )

    /** Stock-request status → website colours. */
    fun requestStatus(tv: TextView, label: String) = set(
        tv, label,
        when (label.lowercase()) {
            "done" -> Tone.SUCCESS
            "fulfilled" -> Tone.INFO
            "cancelled", "inactive" -> Tone.NEUTRAL
            else -> Tone.WARNING
        },
    )
}
