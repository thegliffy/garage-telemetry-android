#!/usr/bin/env python3
"""One-off README screenshot renderer. Output: docs/screenshots/*.png"""

from __future__ import annotations

import math
from pathlib import Path

from PIL import Image, ImageDraw, ImageFilter, ImageFont

OUT = Path(__file__).resolve().parent
W, H = 540, 1160
GREEN = (46, 125, 50)
GREEN_SOFT = (232, 245, 233)
RED = (198, 40, 40)
BLUE = (21, 101, 192)
ORANGE = (230, 81, 0)
YELLOW = (251, 192, 45)
BG = (250, 250, 250)
SURFACE = (255, 255, 255)
ON = (28, 27, 31)
ON_VAR = (96, 99, 99)
OUTLINE = (196, 199, 197)
NAV = (243, 247, 243)
CARD = (255, 255, 255)
TRACK = (232, 234, 237)


def font(size: int, bold: bool = False) -> ImageFont.FreeTypeFont:
    name = "DejaVuSans-Bold.ttf" if bold else "DejaVuSans.ttf"
    return ImageFont.truetype(f"/usr/share/fonts/truetype/dejavu/{name}", size)


def rounded(draw: ImageDraw.ImageDraw, box, r, fill, outline=None, width=1):
    draw.rounded_rectangle(box, radius=r, fill=fill, outline=outline, width=width)


def shadow_card(base: Image.Image, box, r=18):
    x0, y0, x1, y1 = box
    layer = Image.new("RGBA", base.size, (0, 0, 0, 0))
    d = ImageDraw.Draw(layer)
    d.rounded_rectangle((x0 + 2, y0 + 4, x1 + 2, y1 + 6), radius=r, fill=(0, 0, 0, 28))
    blurred = layer.filter(ImageFilter.GaussianBlur(4))
    base.alpha_composite(blurred)
    dd = ImageDraw.Draw(base)
    rounded(dd, box, r, CARD)


def phone(w=W, h=H, bg=BG) -> Image.Image:
    img = Image.new("RGBA", (w, h), bg + (255,))
    return img


def status_bar(draw, w=W, dark=False):
    color = (255, 255, 255) if dark else ON
    f = font(13, True)
    draw.text((24, 14), "12:41", fill=color, font=f)
    draw.text((w - 88, 14), "5G  84%", fill=color, font=f)


def nav_bar(img: Image.Image, selected: str):
    draw = ImageDraw.Draw(img)
    y = H - 88
    draw.rectangle((0, y, W, H), fill=NAV)
    draw.line((0, y, W, y), fill=OUTLINE, width=1)
    items = [("Live", 90), ("History", 270), ("Settings", 450)]
    for label, x in items:
        active = label.lower() == selected
        col = GREEN if active else ON_VAR
        # icon dots
        draw.ellipse((x - 10, y + 16, x + 10, y + 36), outline=col, width=2)
        if active:
            draw.ellipse((x - 5, y + 21, x + 5, y + 31), fill=col)
        draw.text((x, y + 46), label, fill=col, font=font(13, active), anchor="mt")


def outline_btn(draw, box, label):
    rounded(draw, box, 22, SURFACE, GREEN, 2)
    cx = (box[0] + box[2]) / 2
    cy = (box[1] + box[3]) / 2
    draw.text((cx, cy), label, fill=GREEN, font=font(15, True), anchor="mm")


def filled_btn(draw, box, label):
    rounded(draw, box, 22, GREEN)
    cx = (box[0] + box[2]) / 2
    cy = (box[1] + box[3]) / 2
    draw.text((cx, cy), label, fill=(255, 255, 255), font=font(15, True), anchor="mm")


def arc(draw, cx, cy, r, frac, color, track=TRACK, width=9, power=False, zero=0.4):
    bbox = (cx - r, cy - r, cx + r, cy + r)
    draw.arc(bbox, start=180, end=360, fill=track, width=width)
    if power:
        # fill from zero point
        a0 = 180 + zero * 180
        a1 = 180 + frac * 180
        start, end = (a1, a0) if a1 < a0 else (a0, a1)
        draw.arc(bbox, start=start, end=end, fill=color, width=width)
    else:
        draw.arc(bbox, start=180, end=180 + 180 * frac, fill=color, width=width)


def thermo(draw, x0, y0, x1, y1, frac, color):
    midy = (y0 + y1) / 2
    draw.rounded_rectangle((x0, midy - 5, x1, midy + 5), 4, fill=TRACK)
    fill_w = x0 + (x1 - x0) * frac
    draw.rounded_rectangle((x0, midy - 5, fill_w, midy + 5), 4, fill=color)


def live() -> Image.Image:
    img = phone()
    d = ImageDraw.Draw(img)
    status_bar(d)
    d.text((24, 52), "jaryo", fill=ON, font=font(32, True))
    outline_btn(d, (248, 52, 368, 96), "Car mode")
    outline_btn(d, (380, 52, 516, 96), "Charging")

    d.text((24, 118), "Connected to Vgate iCar Pro", fill=ON, font=font(16))
    outline_btn(d, (24, 152, 200, 196), "Disconnect")

    tiles = [
        ("Speed", "47", "mph", "arc", 0.47, GREEN),
        ("HV Battery", "68", "%", "arc", 0.68, GREEN),
        ("Pack Power", "32", "kW · discharging", "power", 0.52, RED),
        ("Efficiency (now)", "3.6", "mi/kWh", "num", 0.45, GREEN),
        ("Efficiency (trip)", "3.8", "mi/kWh", "num", 0.48, GREEN),
        ("Energy Remaining", "51", "kWh", "arc", 0.66, GREEN),
        ("Battery Temp", "26 – 29°C", "spread 3°C", "batt", 0.62, RED),
        ("Cabin / Outside", None, None, "climate", 0.0, GREEN),
    ]
    gap, pad = 12, 16
    cols, rows = 2, 4
    top = 218
    bot = H - 100
    tw = (W - pad * 2 - gap) / cols
    th = (bot - top - gap * (rows - 1)) / rows
    for i, (title, val, unit, kind, frac, col) in enumerate(tiles):
        c, r = i % 2, i // 2
        x0 = pad + c * (tw + gap)
        y0 = top + r * (th + gap)
        box = (x0, y0, x0 + tw, y0 + th)
        shadow_card(img, box)
        d = ImageDraw.Draw(img)
        d.text((x0 + tw / 2, y0 + 14), title, fill=ON_VAR, font=font(12), anchor="mt")
        cx, cy = x0 + tw / 2, y0 + 58
        if kind == "arc":
            arc(d, cx, cy + 8, 46, frac, col)
            d.text((cx, cy + 18), val, fill=ON, font=font(26, True), anchor="mm")
            d.text((cx, cy + 40), unit, fill=ON_VAR, font=font(11), anchor="mm")
        elif kind == "power":
            arc(d, cx, cy + 8, 46, frac, col, power=True, zero=0.4)
            d.text((cx, cy + 18), val, fill=col, font=font(26, True), anchor="mm")
            d.text((cx, cy + 42), unit, fill=col, font=font(10), anchor="mm")
        elif kind == "num":
            d.text((cx, cy + 18), val, fill=ON, font=font(32, True), anchor="mm")
            d.text((cx, cy + 48), unit, fill=ON_VAR, font=font(12), anchor="mm")
        elif kind == "batt":
            bar_y = y0 + 58
            thermo(d, x0 + 18, bar_y, x0 + tw - 18, bar_y + 16, 0.55, (255, 205, 210))
            mx = x0 + 18 + (tw - 36) * 0.48
            Mn = x0 + 18 + (tw - 36) * 0.62
            d.rectangle((mx - 2, bar_y - 2, mx + 2, bar_y + 18), fill=BLUE)
            d.rectangle((Mn - 2, bar_y - 2, Mn + 2, bar_y + 18), fill=RED)
            d.text((cx, y0 + th - 38), val, fill=ON, font=font(18, True), anchor="mm")
            d.text((cx, y0 + th - 18), unit, fill=ON_VAR, font=font(11), anchor="mm")
        elif kind == "climate":
            d.text((x0 + 16, y0 + 42), "Cabin", fill=ON_VAR, font=font(11))
            d.text((x0 + tw - 16, y0 + 42), "72°F", fill=ORANGE, font=font(14, True), anchor="ra")
            thermo(d, x0 + 16, y0 + 58, x0 + tw - 16, y0 + 70, 0.68, ORANGE)
            d.text((x0 + 16, y0 + 86), "Outside", fill=ON_VAR, font=font(11))
            d.text((x0 + tw - 16, y0 + 86), "64°F", fill=BLUE, font=font(14, True), anchor="ra")
            thermo(d, x0 + 16, y0 + 102, x0 + tw - 16, y0 + 114, 0.52, BLUE)

    nav_bar(img, "live")
    return img.convert("RGB")


def history_list() -> Image.Image:
    img = phone()
    d = ImageDraw.Draw(img)
    status_bar(d)
    d.text((24, 52), "History", fill=ON, font=font(32, True))
    rows = [
        ("Drive", "Aug 15, 2026, 10:18 AM · 28min"),
        ("Charge", "Aug 15, 2026, 1:02 PM · 22min"),
        ("Drive", "Aug 12, 2026, 7:41 AM · 41min"),
        ("Drive", "Aug 11, 2026, 5:55 PM · 18min"),
        ("Charge", "Aug 10, 2026, 4:12 PM · 35min"),
    ]
    y = 120
    for title, sub in rows:
        box = (20, y, W - 20, y + 96)
        shadow_card(img, box, 16)
        d = ImageDraw.Draw(img)
        rounded(d, box, 16, CARD, GREEN, 2)
        d.text((40, y + 28), title, fill=ON, font=font(18, True))
        d.text((40, y + 58), sub, fill=ON_VAR, font=font(13))
        y += 112
    nav_bar(img, "history")
    return img.convert("RGB")


def spark(draw, pts, box, color, extra=None, extra_color=None):
    x0, y0, x1, y1 = box
    ys = [p[1] for p in pts] + ([p[1] for p in extra] if extra else [])
    lo, hi = min(ys), max(ys)
    span = hi - lo or 1
    pad = span * 0.12
    lo, hi = lo - pad, hi + pad

    def xy(i, n, v):
        x = x0 + i / (n - 1) * (x1 - x0)
        y = y1 - (v - lo) / (hi - lo) * (y1 - y0)
        return x, y

    def polyline(series, col):
        coords = [xy(i, len(series), v) for i, v in enumerate(series)]
        draw.line(coords, fill=col, width=3)

    polyline([p[1] for p in pts], color)
    if extra:
        polyline([p[1] for p in extra], extra_color)
    draw.text((x0, y0 - 2), f"{hi:.0f}", fill=ON_VAR, font=font(10))
    draw.text((x0, y1 - 12), f"{lo:.0f}", fill=ON_VAR, font=font(10))


def history_detail() -> Image.Image:
    img = phone()
    d = ImageDraw.Draw(img)
    status_bar(d)
    d.text((24, 48), "History", fill=ON, font=font(28, True))
    outline_btn(d, (24, 96, 200, 136), "Back to list")

    # summary card
    box = (20, 152, W - 20, 390)
    shadow_card(img, box)
    d = ImageDraw.Draw(img)
    d.text((40, 170), "Drive summary", fill=ON, font=font(18, True))
    rows = [
        ("Duration", "28m 12s", ON),
        ("Distance", "14.6 mi", ON),
        ("Energy used", "4.12 kWh", RED),
        ("Regenerated", "0.38 kWh", GREEN),
        ("Efficiency", "3.72 mi/kWh", ON),
        ("Battery used", "6.1 %", ON),
    ]
    y = 206
    for k, v, c in rows:
        d.text((40, y), k, fill=ON, font=font(14))
        d.text((W - 40, y), v, fill=c, font=font(14, True), anchor="ra")
        y += 28

    d.line((20, 408, W - 20, 408), fill=OUTLINE)

    import random
    random.seed(3)
    speed = [(i, 32 + 22 * math.sin(i / 7) + random.random() * 3) for i in range(40)]
    soc = [(i, 71 - i * 0.35 + random.random()) for i in range(40)]
    tmax = [(i, 27 + 2 * math.sin(i / 9)) for i in range(40)]
    tmin = [(i, 24 + 1.4 * math.sin(i / 9 + 0.4)) for i in range(40)]

    d.text((24, 424), "Speed (mph)", fill=ON, font=font(16, True))
    spark(d, speed, (40, 456, W - 28, 560), GREEN)
    d.text((24, 580), "HV Battery (%)", fill=ON, font=font(16, True))
    spark(d, soc, (40, 612, W - 28, 716), GREEN)
    d.text((24, 736), "Battery temps  ·  red max  ·  blue min  (°C)", fill=ON, font=font(14, True))
    spark(d, tmax, (40, 768, W - 28, 880), RED, tmin, BLUE)
    d.text((W - 28, 892), "28 min", fill=ON_VAR, font=font(11), anchor="ra")

    nav_bar(img, "history")
    return img.convert("RGB")


def charge() -> Image.Image:
    img = phone()
    d = ImageDraw.Draw(img)
    status_bar(d)
    d.text((24, 48), "DC fast charge", fill=ON, font=font(26, True))
    outline_btn(d, (400, 48, 516, 96), "Close")

    import random
    random.seed(8)
    soc = [(i, 38 + i * 0.55 + random.random() * 0.2) for i in range(40)]
    kw = [(i, 48 + 8 * math.sin(i / 5) + random.random()) for i in range(40)]
    v = [(i, 692 + 6 * math.sin(i / 8)) for i in range(40)]
    tmax = [(i, 26 + i * 0.08) for i in range(40)]
    tmin = [(i, 22 + i * 0.06) for i in range(40)]

    charts = [
        ("State of charge", soc, "%", GREEN, None, None),
        ("Charging power", kw, "kW", GREEN, None, None),
        ("Pack voltage", v, "V", (84, 110, 122), None, None),
        ("Battery temps  ·  red max  ·  blue min", tmax, "°C", RED, tmin, BLUE),
    ]
    y = 120
    for title, pts, unit, col, extra, ecol in charts:
        d.text((24, y), title, fill=ON, font=font(16, True))
        spark(d, pts, (40, y + 28, W - 28, y + 148), col, extra, ecol)
        y += 178

    # flag boxes
    for i, (lab, on) in enumerate([("DC charging", True), ("Battery heater", False)]):
        x0 = 20 + i * 260
        box = (x0, y, x0 + 240, y + 90)
        fill = GREEN_SOFT if on else TRACK
        rounded(d, box, 16, fill)
        d.text((x0 + 16, y + 16), lab, fill=ON, font=font(14, True))
        d.text((x0 + 16, y + 46), "On" if on else "Off", fill=GREEN if on else ON_VAR, font=font(22, True))
    return img.convert("RGB")


def car_mode() -> Image.Image:
    w, h = 1160, 540
    img = Image.new("RGBA", (w, h), (0, 0, 0, 255))
    d = ImageDraw.Draw(img)
    status_bar(d, w, dark=True)
    tiles = [
        ("Speed", "64", "arc", 0.64),
        ("HV Battery", "61%", "arc", 0.61),
        ("Pack Power", "71", "power", 0.62),
        ("Efficiency (now)", "3.1", "num", 0),
        ("Energy Remaining", "47", "arc", 0.61),
        ("Battery Temp", "28°", "batt", 0),
        ("Tires", "42  42\n41  41", "tires", 0),
        ("Cabin / Outside", "", "climate", 0),
    ]
    pad, gap = 16, 10
    cols, rows = 4, 2
    top = 44
    tw = (w - pad * 2 - gap * (cols - 1)) / cols
    th = (h - top - pad - gap) / rows
    for i, (title, val, kind, frac) in enumerate(tiles):
        c, r = i % cols, i // cols
        x0 = pad + c * (tw + gap)
        y0 = top + r * (th + gap)
        box = (x0, y0, x0 + tw, y0 + th)
        rounded(d, box, 14, (26, 26, 26))
        d.text((x0 + tw / 2, y0 + 12), title, fill=(180, 180, 180), font=font(13), anchor="mt")
        cx, cy = x0 + tw / 2, y0 + th / 2 + 4
        if kind == "arc":
            arc(d, cx, cy - 10, 52, frac, (100, 181, 246), track=(48, 48, 48), width=10)
            d.text((cx, cy + 8), val, fill=(255, 255, 255), font=font(28, True), anchor="mm")
        elif kind == "power":
            arc(d, cx, cy - 10, 52, frac, RED, track=(48, 48, 48), width=10, power=True, zero=0.4)
            d.text((cx, cy + 8), val, fill=RED, font=font(28, True), anchor="mm")
        elif kind == "num":
            d.text((cx, cy), val, fill=(255, 255, 255), font=font(34, True), anchor="mm")
            d.text((cx, cy + 28), "mi/kWh", fill=(180, 180, 180), font=font(12), anchor="mm")
        elif kind == "batt":
            d.text((cx, cy), val, fill=(255, 255, 255), font=font(28, True), anchor="mm")
            d.text((cx, cy + 28), "max / min", fill=(180, 180, 180), font=font(12), anchor="mm")
        elif kind == "tires":
            d.multiline_text((cx, cy), val, fill=(255, 255, 255), font=font(22, True), anchor="mm", align="center", spacing=8)
            d.text((cx, y0 + th - 18), "psi  FL FR / RL RR", fill=(140, 140, 140), font=font(10), anchor="mb")
        elif kind == "climate":
            d.text((x0 + 16, y0 + 48), "Cabin  72°F", fill=ORANGE, font=font(14, True))
            thermo(d, x0 + 16, y0 + 68, x0 + tw - 16, y0 + 80, 0.68, ORANGE)
            d.text((x0 + 16, y0 + 100), "Outside  64°F", fill=(100, 181, 246), font=font(14, True))
            thermo(d, x0 + 16, y0 + 120, x0 + tw - 16, y0 + 132, 0.52, (100, 181, 246))
    return img.convert("RGB")


def icon() -> Image.Image:
    img = Image.new("RGBA", (256, 256), (18, 24, 32, 255))
    d = ImageDraw.Draw(img)
    d.rounded_rectangle((0, 0, 255, 255), 56, fill=(20, 28, 38))
    cx, cy, r = 128, 132, 78
    bbox = (cx - r, cy - r, cx + r, cy + r)
    for i in range(14):
        t = i / 13
        col = (
            int(61 + t * (124 - 61)),
            int(156 + t * (255 - 156)),
            int(253 + t * (107 - 253)),
        )
        d.arc(bbox, 150 + t * 240, 150 + (t + 0.08) * 240, fill=col, width=16)
    # needle
    ang = math.radians(40)
    x = cx + math.cos(ang) * 58
    y = cy - math.sin(ang) * 58
    d.line((cx, cy + 8, x, y), fill=(232, 238, 246), width=6)
    d.ellipse((cx - 8, cy, cx + 8, cy + 16), fill=(232, 238, 246))
    # bolt
    bolt = [(126, 96), (138, 96), (128, 118), (142, 118), (116, 156), (124, 128), (114, 128)]
    d.polygon(bolt, fill=(124, 255, 107))
    return img.convert("RGB")


def main():
    OUT.mkdir(parents=True, exist_ok=True)
    live().save(OUT / "live.png", optimize=True)
    history_list().save(OUT / "history.png", optimize=True)
    history_detail().save(OUT / "drive.png", optimize=True)
    charge().save(OUT / "charge.png", optimize=True)
    car_mode().save(OUT / "car-mode.png", optimize=True)
    icon().save(OUT / "icon.png", optimize=True)
    print("wrote", list(OUT.glob("*.png")))


if __name__ == "__main__":
    main()
