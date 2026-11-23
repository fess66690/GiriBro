package ru.petproject.giribro.logging

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LogSanitizerTest {

    @Test
    fun `убирает content uri и пути к личным папкам`() {
        val raw = listOf(
            "Ошибка записи: content://com.android.externalstorage.documents/tree/raw%3A%2Fstorage%2Femulated%2F0%2FVideo",
            "Путь: /storage/emulated/0/Видео/подход.mp4",
            "Windows: C:\\Users\\hp\\Documents\\giribro.log",
            "java.lang.IllegalStateException: boot failed"
        ).joinToString("\n")

        val clean = LogSanitizer.clean(raw)

        assertFalse(clean.contains("content://"))
        assertFalse(clean.contains("/storage/"))
        assertFalse(clean.contains("C:\\"))
        assertTrue(clean.contains("<uri>"))
        assertTrue(clean.contains("<path>"))
        assertTrue(clean.contains("java.lang.IllegalStateException"))
    }

    @Test
    fun `сохраняет обычный стектрейс`() {
        val raw = "E/GiriBro: Не удалось сохранить тренировку\n" +
            "java.io.IOException: disk full\n" +
            "\tat ru.petproject.giribro.data.WorkoutRepository.save(WorkoutRepository.kt:42)\n"

        assertEquals(raw, LogSanitizer.clean(raw))
    }

    @Test
    fun `ограничивает размер и оставляет конец журнала`() {
        val clean = LogSanitizer.clean("x".repeat(5_000), maxChars = 100)

        assertTrue(clean.startsWith("(обрезано ранее)\n"))
        assertTrue(clean.endsWith("x"))
        assertTrue(clean.length < 200)
    }

    @Test
    fun `схлопывает длинные пустые блоки`() {
        val clean = LogSanitizer.clean("a\n\n\n\n\nb")

        assertEquals("a\n\nb", clean)
    }
}
