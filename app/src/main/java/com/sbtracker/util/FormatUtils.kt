package com.sbtracker.util

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** "Today", "Yesterday", or "Mar 5" */
fun relativeDate(ms: Long): String {
    val diff = System.currentTimeMillis() - ms
    return when {
        diff < 24 * 3_600_000L -> "Today"
        diff < 48 * 3_600_000L -> "Yesterday"
        else -> SimpleDateFormat("MMM d", Locale.getDefault()).format(Date(ms))
    }
}

/** 0→"—", <60s→"Xs", whole minutes→"Xm", else→"Xm Ys" */
fun formatDurationShort(seconds: Long): String = when {
    seconds <= 0L  -> "—"
    seconds < 60L  -> "${seconds}s"
    seconds % 60L == 0L -> "${seconds / 60}m"
    else -> "${seconds / 60}m ${seconds % 60}s"
}
