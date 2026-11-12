package ru.petproject.giribro.ui.cards

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import android.graphics.Typeface
import android.text.TextPaint
import android.text.TextUtils
import ru.petproject.giribro.data.SectionType
import ru.petproject.giribro.data.WorkoutCardContent

/**
 * Рисует вертикальную фото-карточку 1080×1350 без Compose.
 *
 * Фиксированный размер и обычный Canvas дают предсказуемый PNG для шаринга,
 * не завися от размера окна, плотности экрана и текущей темы приложения.
 */
object WorkoutCardRenderer {

    const val WIDTH = 1080
    const val HEIGHT = 1350

    private const val MARGIN = 72f
    private const val SECTION_TOP = 868f
    private const val SECTION_ROW = 84f

    private val backgroundTop = 0xFF10130D.toInt()
    private val backgroundBottom = 0xFF0B0D09.toInt()
    private val text = 0xFFE8EBDD.toInt()
    private val muted = 0xFFB9C0AC.toInt()
    private val lime = 0xFFD4FF4F.toInt()
    private val line = 0x24FFFFFF
    private val accentLine = 0x66D4FF4F

    private val regular = Typeface.create("sans-serif", Typeface.NORMAL)
    private val medium = Typeface.create("sans-serif-medium", Typeface.NORMAL)
    private val bold = Typeface.create("sans-serif", Typeface.BOLD)

    fun render(content: WorkoutCardContent): Bitmap {
        val bitmap = Bitmap.createBitmap(WIDTH, HEIGHT, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        drawBackground(canvas)
        drawHeader(canvas, content)
        drawHero(canvas, content)
        drawStats(canvas, content)
        drawSections(canvas, content)
        drawFooter(canvas)
        return bitmap
    }

    private fun drawBackground(canvas: Canvas) {
        val background = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(
                0f, 0f, WIDTH.toFloat(), HEIGHT.toFloat(),
                intArrayOf(backgroundTop, 0xFF151A10.toInt(), backgroundBottom),
                floatArrayOf(0f, 0.55f, 1f),
                Shader.TileMode.CLAMP
            )
        }
        canvas.drawRect(0f, 0f, WIDTH.toFloat(), HEIGHT.toFloat(), background)

        val glow = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = RadialGradient(
                930f, -60f, 700f,
                intArrayOf(0x32D4FF4F, 0x00D4FF4F),
                null,
                Shader.TileMode.CLAMP
            )
        }
        canvas.drawRect(0f, 0f, WIDTH.toFloat(), 760f, glow)

        val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 2f
            color = 0x1FD4FF4F
        }
        canvas.drawCircle(1010f, 120f, 270f, ring)
        canvas.drawCircle(1010f, 120f, 185f, ring)

        val bottomGlow = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = RadialGradient(
                -160f, HEIGHT + 140f, 620f,
                intArrayOf(0x1AFFC46B, 0x00FFC46B),
                null,
                Shader.TileMode.CLAMP
            )
        }
        canvas.drawRect(0f, 760f, WIDTH.toFloat(), HEIGHT.toFloat(), bottomGlow)
    }

    private fun drawHeader(canvas: Canvas, content: WorkoutCardContent) {
        val brand = paint(34f, lime, bold)
        canvas.drawText("ГИРИБРО", MARGIN, 102f, brand)

        val tagline = paint(20f, muted, medium)
        canvas.drawText("ГИРЕВОЙ ДНЕВНИК", MARGIN, 140f, tagline)

        val date = paint(28f, text, medium, Paint.Align.RIGHT)
        canvas.drawText("${content.date} ${content.year}", WIDTH - MARGIN, 102f, date)

        val weekday = paint(22f, muted, regular, Paint.Align.RIGHT)
        canvas.drawText(content.weekday, WIDTH - MARGIN, 140f, weekday)

        canvas.drawLine(MARGIN, 182f, WIDTH - MARGIN, 182f, linePaint())
        canvas.drawLine(MARGIN, 182f, MARGIN + 120f, 182f, accentPaint())

        val label = paint(21f, muted, medium)
        canvas.drawText("ТРЕНИРОВКА", MARGIN, 242f, label)

        val title = paint(52f, text, bold)
        canvas.drawText(
            ellipsize(content.title, title, WIDTH - MARGIN * 2),
            MARGIN,
            310f,
            title
        )
    }

    private fun drawHero(canvas: Canvas, content: WorkoutCardContent) {
        val label = paint(21f, muted, medium)
        canvas.drawText("ТОННАЖ", MARGIN, 392f, label)

        val value = paint(148f, lime, bold)
        canvas.drawText(content.tonnageValue, MARGIN, 548f, value)
        val valueWidth = value.measureText(content.tonnageValue)

        val unit = paint(38f, lime, medium)
        canvas.drawText(content.tonnageUnit, MARGIN + valueWidth + 18f, 540f, unit)

        val hint = paint(22f, muted, regular)
        canvas.drawText(
            "за тренировку",
            MARGIN + valueWidth + 30f + unit.measureText(content.tonnageUnit),
            540f,
            hint
        )
    }

    private fun drawStats(canvas: Canvas, content: WorkoutCardContent) {
        canvas.drawLine(MARGIN, 618f, WIDTH - MARGIN, 618f, linePaint())

        val label = paint(21f, muted, medium)
        val value = paint(44f, text, bold)
        val columns = listOf(
            Triple(MARGIN, "ПОДЪЁМЫ", content.reps),
            Triple(396f, "ВРЕМЯ", content.duration),
            Triple(720f, "БЛОКИ", content.sectionsCount)
        )
        columns.forEach { (x, columnLabel, columnValue) ->
            canvas.drawText(columnLabel, x, 676f, label)
            canvas.drawText(columnValue, x, 736f, value)
        }

        canvas.drawLine(MARGIN, 792f, WIDTH - MARGIN, 792f, linePaint())
    }

    private fun drawSections(canvas: Canvas, content: WorkoutCardContent) {
        val label = paint(21f, muted, medium)
        canvas.drawText("УПРАЖНЕНИЯ", MARGIN, 842f, label)

        if (content.hiddenSections > 0) {
            val more = paint(21f, lime, medium, Paint.Align.RIGHT)
            canvas.drawText(
                "+${content.hiddenSections} ещё",
                WIDTH - MARGIN,
                842f,
                more
            )
        }

        val sectionTitle = paint(30f, text, medium)
        val sectionDetails = paint(22f, muted, regular)
        val sectionVolume = paint(28f, lime, medium, Paint.Align.RIGHT)

        content.sections.forEachIndexed { index, section ->
            val top = SECTION_TOP + index * SECTION_ROW
            val accent = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = sectionColor(section.type)
                style = Paint.Style.FILL
            }
            canvas.drawRoundRect(
                MARGIN,
                top + 13f,
                MARGIN + 8f,
                top + 61f,
                4f,
                4f,
                accent
            )

            canvas.drawText(
                ellipsize(section.title, sectionTitle, 560f),
                MARGIN + 28f,
                top + 34f,
                sectionTitle
            )
            canvas.drawText(
                ellipsize(section.details, sectionDetails, 620f),
                MARGIN + 28f,
                top + 64f,
                sectionDetails
            )
            canvas.drawText(
                section.volume,
                WIDTH - MARGIN,
                top + 43f,
                sectionVolume
            )

            if (index < content.sections.lastIndex) {
                canvas.drawLine(
                    MARGIN + 28f,
                    top + 83f,
                    WIDTH - MARGIN,
                    top + 83f,
                    linePaint()
                )
            }
        }

        content.note?.let { note ->
            val noteTop = SECTION_TOP + content.sections.size * SECTION_ROW + 18f
            val noteLabel = paint(20f, lime, medium)
            canvas.drawText("ЗАМЕТКА", MARGIN, noteTop, noteLabel)
            val notePaint = paint(22f, muted, regular)
            val labelWidth = noteLabel.measureText("ЗАМЕТКА")
            canvas.drawText(
                ellipsize(
                    note,
                    notePaint,
                    WIDTH - MARGIN * 2 - labelWidth - 24f
                ),
                MARGIN + labelWidth + 24f,
                noteTop,
                notePaint
            )
        }
    }

    private fun drawFooter(canvas: Canvas) {
        val brand = paint(24f, lime, bold)
        canvas.drawText("giribro", MARGIN, 1322f, brand)

        val tagline = paint(19f, muted, medium, Paint.Align.RIGHT)
        canvas.drawText("ЛИЧНЫЙ ДНЕВНИК", WIDTH - MARGIN, 1322f, tagline)
    }

    private fun paint(
        size: Float,
        color: Int,
        typeface: Typeface,
        align: Paint.Align = Paint.Align.LEFT
    ): Paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = size
        this.color = color
        this.typeface = typeface
        textAlign = align
    }

    private fun linePaint(): Paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = line
        strokeWidth = 2f
    }

    private fun accentPaint(): Paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = accentLine
        strokeWidth = 4f
    }

    private fun ellipsize(value: String, paint: Paint, maxWidth: Float): String =
        TextUtils.ellipsize(
            value,
            TextPaint(paint),
            maxWidth,
            TextUtils.TruncateAt.END
        ).toString()

    private fun sectionColor(type: SectionType): Int = when (type) {
        SectionType.WARMUP -> 0xFF9CCC65.toInt()
        SectionType.JERK -> 0xFFD4FF4F.toInt()
        SectionType.SNATCH -> 0xFFFFC46B.toInt()
        SectionType.LONG_CYCLE -> 0xFF7FD1FF.toInt()
        SectionType.SWINGS -> 0xFFFF9E80.toInt()
        SectionType.GPP -> 0xFFC9A6FF.toInt()
        SectionType.COOLDOWN -> 0xFF9BB0A0.toInt()
    }
}
