"""Собирает финальные иконки из 6-го варианта: магазин + адаптивный foreground."""

from collections import deque
from pathlib import Path

from PIL import Image

ROOT = Path(__file__).resolve().parents[3]
SRC = Path(r"C:\Users\hp\Downloads\pngtree-kettlebell-icon-in-trendy-style-isolated.jpg")
STORE_ICON = ROOT / "docs" / "rustore" / "icon-512.png"
RES = ROOT / "app" / "src" / "main" / "res"

BG = (20, 23, 25)             # #141719
LIME = (201, 242, 74)         # #C9F24A
LIME_DARK = (143, 184, 46)    # #8FB82E
LIME_LIGHT = (226, 250, 146)  # #E2FA92

STOPS = [
    (0, LIME),
    (90, LIME_DARK),
    (170, LIME),
    (235, LIME_LIGHT),
    (255, LIME_LIGHT),
]
BG_LUMA_MIN = 220
SAFE_FRACTION = 0.58          # влезает в круг безопасной зоны 66/108 dp
DENSITIES = [
    ("mdpi", 1),
    ("hdpi", 1.5),
    ("xhdpi", 2),
    ("xxhdpi", 3),
    ("xxxhdpi", 4),
]


def ramp(luma: int):
    for (l0, c0), (l1, c1) in zip(STOPS, STOPS[1:]):
        if luma <= l1:
            t = (luma - l0) / (l1 - l0) if l1 > l0 else 0.0
            t = max(0.0, min(1.0, t))
            return tuple(round(a + (b - a) * t) for a, b in zip(c0, c1))
    return STOPS[-1][1]


def background_mask(width: int, height: int, lumas) -> bytearray:
    total = width * height
    candidate = bytearray(lumas[i] >= BG_LUMA_MIN for i in range(total))
    outside = bytearray(total)
    queue = deque()

    def add(index: int):
        if candidate[index] and not outside[index]:
            outside[index] = 1
            queue.append(index)

    for x in range(width):
        add(x)
        add((height - 1) * width + x)
    for y in range(height):
        add(y * width)
        add(y * width + width - 1)

    while queue:
        index = queue.popleft()
        x = index % width
        y = index // width
        if x > 0:
            add(index - 1)
        if x < width - 1:
            add(index + 1)
        if y > 0:
            add(index - width)
        if y < height - 1:
            add(index + width)

    return outside


def main():
    im = Image.open(SRC).convert("RGB")
    width, height = im.size
    lumas = [round((r + g + b) / 3) for r, g, b in im.getdata()]
    outside = background_mask(width, height, lumas)

    # Иконка для RuStore: тот же вид, что и 6-й концепт.
    store = im.copy()
    store_px = store.load()
    for y in range(height):
        for x in range(width):
            index = y * width + x
            store_px[x, y] = BG if outside[index] else ramp(lumas[index])
    store.resize((512, 512), Image.Resampling.LANCZOS).save(STORE_ICON, "PNG")

    # Foreground для адаптивной иконки: только гиря, прозрачный фон.
    fg = Image.new("RGBA", (width, height), (0, 0, 0, 0))
    fg_px = fg.load()
    for y in range(height):
        for x in range(width):
            index = y * width + x
            if outside[index]:
                continue
            luma = lumas[index]
            # Мягкий край там, где серый переходил в белый фон.
            alpha = 255 if luma < 200 else max(0, round(255 * (BG_LUMA_MIN - luma) / 20))
            if alpha:
                fg_px[x, y] = ramp(luma) + (alpha,)

    left, top, right, bottom = fg.getbbox()
    pad = round(max(right - left, bottom - top) * 0.04)
    crop = fg.crop((left - pad, top - pad, right + pad, bottom + pad))

    for qualifier, factor in DENSITIES:
        size = round(108 * factor)
        target = round(size * SAFE_FRACTION)
        scale = target / max(crop.size)
        resized = crop.resize(
            (max(1, round(crop.width * scale)), max(1, round(crop.height * scale))),
            Image.Resampling.LANCZOS,
        )
        layer = Image.new("RGBA", (size, size), (0, 0, 0, 0))
        layer.paste(resized, ((size - resized.width) // 2, (size - resized.height) // 2))
        folder = RES / f"drawable-{qualifier}"
        folder.mkdir(parents=True, exist_ok=True)
        layer.save(folder / "ic_launcher_foreground.png", "PNG")
        print(f"{qualifier}: {size}x{size}, гиря {resized.width}x{resized.height}")

    print("Готово:", STORE_ICON.relative_to(ROOT))


if __name__ == "__main__":
    main()
