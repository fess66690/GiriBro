"""Показывает слои адаптивной иконки и результат в масках лаунчера."""

from pathlib import Path

from PIL import Image, ImageDraw, ImageFont

HERE = Path(__file__).parent
FG = (HERE.parent.parent.parent / "app" / "src" / "main" / "res" / "drawable-xxxhdpi" / "ic_launcher_foreground.png")
BG = (20, 23, 25)
CELL = 300
GAP = 20
LABEL = 34

font_path = Path(r"C:\Windows\Fonts\segoeui.ttf")
font = ImageFont.truetype(str(font_path), 18) if font_path.exists() else ImageFont.load_default()

foreground = Image.open(FG).convert("RGBA").resize((CELL, CELL), Image.Resampling.LANCZOS)


def checkerboard(size=CELL, step=20):
    img = Image.new("RGBA", (size, size), (235, 235, 235, 255))
    d = ImageDraw.Draw(img)
    for y in range(0, size, step):
        for x in range(0, size, step):
            if (x // step + y // step) % 2 == 0:
                d.rectangle((x, y, x + step - 1, y + step - 1), fill=(205, 205, 205, 255))
    return img


background = Image.new("RGBA", (CELL, CELL), BG + (255,))

composite = background.copy()
composite.alpha_composite(foreground)

circle = Image.new("RGBA", (CELL, CELL), (0, 0, 0, 0))
mask = Image.new("L", (CELL, CELL), 0)
ImageDraw.Draw(mask).ellipse((0, 0, CELL - 1, CELL - 1), fill=255)
circle.paste(composite, (0, 0), mask)

squircle = Image.new("RGBA", (CELL, CELL), (0, 0, 0, 0))
mask = Image.new("L", (CELL, CELL), 0)
ImageDraw.Draw(mask).rounded_rectangle((0, 0, CELL - 1, CELL - 1), radius=68, fill=255)
squircle.paste(composite, (0, 0), mask)

fg_panel = checkerboard()
fg_panel.alpha_composite(foreground)

panels = [
    ("Background", background),
    ("Foreground", fg_panel),
    ("Круг", circle),
    ("Скруглённый квадрат", squircle),
]

sheet = Image.new("RGB", (CELL * len(panels) + GAP * (len(panels) + 1), CELL + LABEL + GAP * 2), (12, 14, 15))
draw = ImageDraw.Draw(sheet)
for i, (title, img) in enumerate(panels):
    x = GAP + i * (CELL + GAP)
    sheet.paste(img.convert("RGB"), (x, GAP))
    draw.text((x, CELL + GAP), title, font=font, fill=(226, 250, 146))

out = HERE / "adaptive-preview.png"
sheet.save(out, "PNG")
print(out.name)
