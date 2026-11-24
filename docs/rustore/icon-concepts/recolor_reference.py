"""Перекраска присланной чёрно-белой иконки в палитру ГириБро."""

from collections import deque
from pathlib import Path

from PIL import Image

SRC = Path(r"C:\Users\hp\Downloads\pngtree-kettlebell-icon-in-trendy-style-isolated.jpg")
OUT = Path(__file__).parent / "06-reference-recolored.png"

BG = (20, 23, 25)             # #141719
LIME = (201, 242, 74)         # #C9F24A
LIME_DARK = (143, 184, 46)    # #8FB82E
LIME_LIGHT = (226, 250, 146)  # #E2FA92

# Полоса яркости -> цвет палитры. Чёрное тело гири становится основным лаймовым,
# серые переходы дают объём, светлые блики — светлым лаймом.
STOPS = [
    (0, LIME),
    (90, LIME_DARK),
    (170, LIME),
    (235, LIME_LIGHT),
    (255, LIME_LIGHT),
]

# Кандидаты в фон: почти белые пиксели. Связность от краёв отличит настоящий фон
# от внутренних белых бликов, которые тоже могут быть яркими.
BG_LUMA_MIN = 220


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

    pixels = im.load()
    for y in range(height):
        for x in range(width):
            index = y * width + x
            if outside[index]:
                pixels[x, y] = BG
            else:
                pixels[x, y] = ramp(lumas[index])

    im.resize((512, 512), Image.Resampling.LANCZOS).save(OUT, "PNG")
    print("Готово:", OUT.name)


if __name__ == "__main__":
    main()
