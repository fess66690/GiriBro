package ru.petproject.giribro.logging

/** Убирает из логов путь к личным файлам и ограничивает размер вложения. */
object LogSanitizer {

    const val MAX_CHARS = 200_000

    private val URI_REGEX = Regex("""(?:content|file|android\.resource)://[^\s"')]+""")
    private val PATH_REGEX = Regex(
        """(?:/(?:storage|data|sdcard|mnt|user|home)/[^\s"')]+)|(?:[A-Za-z]:\\[^\s"')]+)"""
    )
    private val BLANK_LINES_REGEX = Regex("\n{3,}")

    fun clean(text: String, maxChars: Int = MAX_CHARS): String {
        val safeLimit = maxChars.coerceAtLeast(1)
        val cleaned = text
            .replace(URI_REGEX, "<uri>")
            .replace(PATH_REGEX, "<path>")
            .replace(BLANK_LINES_REGEX, "\n\n")
        return if (cleaned.length <= safeLimit) {
            cleaned
        } else {
            "(обрезано ранее)\n" + cleaned.takeLast(safeLimit)
        }
    }
}
