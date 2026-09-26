package com.mobuk.app.domain.logic

/** Finds the first cooking duration in a step ("simmer for 20 minutes", "bake 1 hour 15 mins"). */
object TimerExtractor {
    private val pattern = Regex(
        """(\d+(?:[.,]\d+)?)\s*(?:-|–|to)?\s*(\d+(?:[.,]\d+)?)?\s*(hours?|hrs?|h\b|minutes?|mins?|m\b|seconds?|secs?|s\b)""",
        RegexOption.IGNORE_CASE,
    )

    fun extractSeconds(text: String): Int? {
        var total = 0
        var found = false
        var lastEnd = -1
        for (match in pattern.findAll(text)) {
            // Only chain consecutive units ("1 hour 15 minutes"); a second unrelated duration is ignored.
            if (found && match.range.first - lastEnd > 4) break
            val first = match.groupValues[1].replace(',', '.').toDoubleOrNull() ?: continue
            val second = match.groupValues[2].replace(',', '.').toDoubleOrNull()
            val value = second ?: first
            val unit = match.groupValues[3].lowercase()
            val seconds = when {
                unit.startsWith("h") -> value * 3600
                unit.startsWith("m") -> value * 60
                else -> value
            }
            total += seconds.toInt()
            found = true
            lastEnd = match.range.last
        }
        return if (found && total in 5..(24 * 3600)) total else null
    }

    fun format(seconds: Int): String {
        val h = seconds / 3600
        val m = (seconds % 3600) / 60
        val s = seconds % 60
        return when {
            h > 0 -> "%d:%02d:%02d".format(h, m, s)
            else -> "%d:%02d".format(m, s)
        }
    }
}
