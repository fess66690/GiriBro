"""Сводный превью-лист пяти концептов иконки."""

from pathlib import Path

from PIL import Image, ImageDraw, ImageFont

HERE = Path(__file__).parent
NAMES = [
    ("01-kettlebell.png", "1. Гиря"),
    ("02-diary-checklist.png", "2. Дневник"),
    ("03-timer.png", "3. Таймер"),
    ("04-progress.png", "4. Прогресс"),
    ("05-goals.png", "5. Цели"),
    ("06-reference-recolored.png", "6. Ваш файл в наших цветах"),
]
CELL = 260
PAD = 16
LABEL_H = 36

font_path = Path(r"C:\Windows\Fonts\segoeui.ttf")
font = ImageFont.truetype(str(font_path), 18) if font_path.exists() else ImageFont.load_default()

sheet = Image.new("RGB", (CELL * len(NAMES), CELL + LABEL_H + PAD * 2), (12, 14, 15))
draw = ImageDraw.Draw(sheet)

for index, (file, label) in enumerate(NAMES):
    icon = Image.open(HERE / file).resize((CELL - PAD * 2, CELL - PAD * 2), Image.Resampling.LANCZOS)
    x = index * CELL + PAD
    sheet.paste(icon, (x, PAD))
    draw.text((x, CELL + PAD), label, font=font, fill=(226, 250, 146))

sheet.save(HERE / "preview.png", "PNG")
print("preview.png")
