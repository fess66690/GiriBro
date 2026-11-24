"""Локальная генерация 5 концептов иконки ГириБро (512x512, без внешних сервисов)."""

from pathlib import Path

from PIL import Image, ImageDraw

S = 4                      # super-sampling для сглаживания
SIZE = 512 * S
OUT = Path(__file__).parent

BG = (20, 23, 25)          # #141719
LIME = (201, 242, 74)      # #C9F24A — акцент приложения
LIME_DARK = (143, 184, 46)
LIME_LIGHT = (226, 250, 146)
OFFWHITE = (232, 240, 228)
GRAPHITE = (38, 43, 47)


def box(x1, y1, x2, y2):
    return (x1 * S, y1 * S, x2 * S, y2 * S)


def pt(x, y):
    return (x * S, y * S)


def canvas():
    img = Image.new("RGB", (SIZE, SIZE), BG)
    return img, ImageDraw.Draw(img)


def save(img, name):
    img.resize((512, 512), Image.Resampling.LANCZOS).save(OUT / name, "PNG")


def kettlebell(d, cx, cy, r, color=LIME, band=True):
    """Гиря: ручка-кольцо + тело + пояс веса."""
    hr = r * 0.60
    handle_w = max(5, int(r * 0.24))
    handle_box = box(cx - hr, cy - r * 1.38, cx + hr, cy - r * 0.10)

    # ручка и её отверстие
    d.arc(handle_box, 180, 360, fill=color, width=handle_w * S)
    d.arc(handle_box, 186, 354, fill=BG, width=max(2, int(r * 0.07)) * S)

    # тело
    d.ellipse(box(cx - r, cy - r * 0.82, cx + r, cy + r * 0.96), fill=color)

    if band:
        band_h = max(8, int(r * 0.22))
        d.rounded_rectangle(
            box(cx - r * 0.78, cy + r * 0.10, cx + r * 0.78, cy + r * 0.10 + band_h),
            radius=band_h // 2,
            fill=LIME_DARK,
        )
    # мягкий блик
    d.ellipse(
        box(cx - r * 0.55, cy - r * 0.52, cx - r * 0.10, cy - r * 0.28),
        fill=LIME_LIGHT,
    )


def checkmark(d, x, y, size, color=LIME, width=8):
    d.line(
        [
            pt(x, y + size * 0.45),
            pt(x + size * 0.34, y + size * 0.82),
            pt(x + size, y),
        ],
        fill=color,
        width=width * S,
        joint="curve",
    )


def icon_kettlebell():
    img, d = canvas()
    kettlebell(d, 256, 292, 108)
    save(img, "01-kettlebell.png")


def icon_diary():
    img, d = canvas()
    # разрезанная тетрадь
    d.rounded_rectangle(box(116, 150, 248, 372), radius=18,
                        fill=GRAPHITE, outline=LIME, width=7 * S)
    d.rounded_rectangle(box(264, 150, 396, 372), radius=18,
                        fill=GRAPHITE, outline=LIME, width=7 * S)
    d.line([pt(256, 154), pt(256, 368)], fill=LIME, width=6 * S)

    # маленькая гиря на левой странице
    kettlebell(d, 182, 258, 34, band=False)

    # строки и галочки на правой странице
    for i, y in enumerate((196, 246, 296, 346)):
        d.rounded_rectangle(box(286, y, 376, y + 15), radius=7,
                            fill=LIME_DARK if i else OFFWHITE)
    checkmark(d, 286, 190, 34, LIME, width=7)

    save(img, "02-diary-checklist.png")


def icon_timer():
    img, d = canvas()
    # внешнее кольцо
    d.ellipse(box(104, 104, 408, 408), outline=LIME_DARK, width=13 * S)
    # прогресс-дуга: верх -> правая половина
    d.arc(box(104, 104, 408, 408), 270, 360, fill=LIME, width=13 * S)
    d.arc(box(104, 104, 408, 408), 0, 120, fill=LIME, width=13 * S)

    # засечки
    import math
    for i in range(12):
        a = math.radians(i * 30 - 90)
        x1 = 256 + 132 * math.cos(a)
        y1 = 256 + 132 * math.sin(a)
        x2 = 256 + 143 * math.cos(a)
        y2 = 256 + 143 * math.sin(a)
        d.line([pt(x1, y1), pt(x2, y2)], fill=OFFWHITE, width=4 * S)

    # кнопка секундомера
    d.rounded_rectangle(box(238, 88, 274, 112), radius=10, fill=LIME)

    kettlebell(d, 256, 286, 66)
    save(img, "03-timer.png")


def icon_progress():
    img, d = canvas()
    # растущие столбцы
    bars = [
        (126, 322, 178, 396, LIME_DARK),
        (202, 282, 254, 396, LIME),
        (278, 226, 330, 396, LIME_LIGHT),
    ]
    for x1, y1, x2, y2, color in bars:
        d.rounded_rectangle(box(x1, y1, x2, y2), radius=14, fill=color)

    # линия тренда со стрелкой
    d.line(
        [pt(136, 306), pt(212, 262), pt(292, 204), pt(368, 158)],
        fill=BG,
        width=15 * S,
        joint="curve",
    )
    d.line(
        [pt(136, 306), pt(212, 262), pt(292, 204), pt(368, 158)],
        fill=LIME,
        width=9 * S,
        joint="curve",
    )
    d.polygon([pt(368, 158), pt(342, 166), pt(358, 184)], fill=LIME)

    kettlebell(d, 256, 236, 48)
    save(img, "04-progress.png")


def icon_goals():
    img, d = canvas()
    # мишень нормативов
    d.ellipse(box(132, 138, 380, 386), outline=LIME_DARK, width=11 * S)
    d.ellipse(box(168, 174, 344, 350), outline=LIME, width=11 * S)
    d.ellipse(box(204, 210, 308, 314), outline=OFFWHITE, width=9 * S)

    kettlebell(d, 256, 274, 38, band=False)

    # значок выполненной цели
    d.ellipse(box(326, 318, 400, 392), fill=LIME)
    checkmark(d, 342, 330, 42, BG, width=10)

    save(img, "05-goals.png")


if __name__ == "__main__":
    icon_kettlebell()
    icon_diary()
    icon_timer()
    icon_progress()
    icon_goals()
    print("Готово:", ", ".join(p.name for p in sorted(OUT.glob("*.png"))))
