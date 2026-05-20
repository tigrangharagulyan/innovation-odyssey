from pathlib import Path
from PIL import Image, ImageDraw, ImageFilter
import math
import random


ROOT = Path(__file__).resolve().parents[1]
ASSET_UI = ROOT / "assets" / "ui"
ASSET_BG = ROOT / "assets" / "backgrounds"
ASSET_ENGINEERING = ROOT / "assets" / "engineering"
W, H = 1280, 720


def ensure_dirs():
    ASSET_UI.mkdir(parents=True, exist_ok=True)
    ASSET_BG.mkdir(parents=True, exist_ok=True)
    ASSET_ENGINEERING.mkdir(parents=True, exist_ok=True)


def hex_rgba(value, alpha=255):
    value = value.lstrip("#")
    if len(value) == 6:
        return tuple(int(value[i:i + 2], 16) for i in (0, 2, 4)) + (alpha,)
    return tuple(int(value[i:i + 2], 16) for i in (0, 2, 4, 6))


def vertical_gradient(size, top, bottom):
    img = Image.new("RGBA", size)
    px = img.load()
    for y in range(size[1]):
        t = y / max(1, size[1] - 1)
        color = tuple(int(top[i] * (1 - t) + bottom[i] * t) for i in range(4))
        for x in range(size[0]):
            px[x, y] = color
    return img


def rounded_panel(size, fill_top, fill_bottom, border, glow, inset):
    base = Image.new("RGBA", size, (0, 0, 0, 0))
    draw = ImageDraw.Draw(base)
    rect = (10, 10, size[0] - 10, size[1] - 10)

    glow_layer = Image.new("RGBA", size, (0, 0, 0, 0))
    glow_draw = ImageDraw.Draw(glow_layer)
    glow_draw.rounded_rectangle(rect, radius=30, outline=glow, width=8)
    glow_layer = glow_layer.filter(ImageFilter.GaussianBlur(10))
    base.alpha_composite(glow_layer)

    fill = vertical_gradient(size, fill_top, fill_bottom)
    mask = Image.new("L", size, 0)
    ImageDraw.Draw(mask).rounded_rectangle(rect, radius=28, fill=255)
    fill.putalpha(mask)
    base.alpha_composite(fill)

    draw.rounded_rectangle(rect, radius=28, outline=border, width=5)
    draw.rounded_rectangle((18, 18, size[0] - 18, size[1] - 18), radius=22, outline=inset, width=2)
    return base


def soft_glow(draw, xy, radius, color):
    layer = Image.new("RGBA", (radius * 4, radius * 4), (0, 0, 0, 0))
    d = ImageDraw.Draw(layer)
    d.ellipse((radius, radius, radius * 3, radius * 3), fill=color)
    layer = layer.filter(ImageFilter.GaussianBlur(radius // 2))
    return layer, (xy[0] - radius * 2, xy[1] - radius * 2)


def add_circuitry(img, seed=42):
    rng = random.Random(seed)
    draw = ImageDraw.Draw(img)
    cyan = hex_rgba("4fe5ff", 90)
    teal = hex_rgba("31a1b8", 70)
    for _ in range(22):
        x = rng.randint(0, W - 200)
        y = rng.randint(0, H - 60)
        horizontal = rng.random() < 0.65
        if horizontal:
            length = rng.randint(80, 260)
            draw.line((x, y, x + length, y), fill=cyan, width=3)
            draw.ellipse((x + length - 10, y - 10, x + length + 10, y + 10), outline=teal, width=3)
        else:
            length = rng.randint(80, 220)
            draw.line((x, y, x, y + length), fill=cyan, width=3)
            draw.ellipse((x - 10, y + length - 10, x + 10, y + length + 10), outline=teal, width=3)


def add_starfield(img, seed=7):
    rng = random.Random(seed)
    draw = ImageDraw.Draw(img)
    for _ in range(220):
        x = rng.randint(0, W - 1)
        y = rng.randint(0, H - 1)
        c = rng.randint(180, 255)
        draw.ellipse((x, y, x + 2, y + 2), fill=(c, c, 255, 210))
    for _ in range(14):
        x = rng.randint(80, W - 80)
        y = rng.randint(80, H - 80)
        glow, pos = soft_glow(draw, (x, y), 10, (110, 220, 255, 160))
        img.alpha_composite(glow, pos)
        draw.line((x - 10, y, x + 10, y), fill=(255, 255, 255, 200), width=1)
        draw.line((x, y - 10, x, y + 10), fill=(255, 255, 255, 200), width=1)


def draw_planet(draw, center, radius, colors):
    x, y = center
    for i in range(radius, 0, -1):
        t = i / radius
        color = tuple(int(colors[0][k] * t + colors[1][k] * (1 - t)) for k in range(3)) + (255,)
        draw.ellipse((x - i, y - i, x + i, y + i), fill=color)
    draw.arc((x - radius - 18, y - radius - 18, x + radius + 18, y + radius + 18),
             start=205, end=335, fill=(120, 235, 255, 180), width=6)


def draw_ship(draw, x, y, scale=1.0, accent=(98, 233, 255, 255)):
    body = [
        (x, y), (x + 70 * scale, y - 18 * scale), (x + 190 * scale, y - 10 * scale),
        (x + 255 * scale, y - 2 * scale), (x + 300 * scale, y + 12 * scale),
        (x + 250 * scale, y + 28 * scale), (x + 140 * scale, y + 35 * scale),
        (x + 55 * scale, y + 30 * scale),
    ]
    draw.polygon(body, fill=(165, 185, 210, 255), outline=(50, 65, 90, 255))
    draw.rectangle((x + 55 * scale, y - 10 * scale, x + 130 * scale, y + 28 * scale),
                   fill=(90, 110, 130, 255), outline=(50, 65, 90, 255))
    draw.rectangle((x + 150 * scale, y - 4 * scale, x + 225 * scale, y + 18 * scale),
                   fill=(80, 220, 255, 180))
    draw.rectangle((x - 12 * scale, y + 4 * scale, x + 35 * scale, y + 16 * scale), fill=accent)


def menu_background():
    img = vertical_gradient((W, H), hex_rgba("07131d"), hex_rgba("08101d"))
    add_circuitry(img, seed=9)
    draw = ImageDraw.Draw(img)
    draw.rounded_rectangle((52, 30, 560, 690), radius=28, fill=(18, 27, 43, 214), outline=(117, 231, 255, 255), width=4)
    draw.rounded_rectangle((66, 45, 545, 675), radius=22, outline=(255, 215, 110, 180), width=5)
    draw.line((110, 122, 235, 122), fill=(92, 221, 255, 255), width=4)
    draw.line((380, 122, 505, 122), fill=(92, 221, 255, 255), width=4)
    glow, pos = soft_glow(draw, (270, 320), 44, (72, 235, 255, 160))
    img.alpha_composite(glow, pos)
    ship_draw = ImageDraw.Draw(img)
    draw_ship(ship_draw, 155, 275, 1.05)
    img.save(ASSET_BG / "menu_bg.png")


def map_background():
    img = vertical_gradient((W, H), hex_rgba("0a1022"), hex_rgba("111630"))
    add_starfield(img, seed=33)
    draw = ImageDraw.Draw(img)
    draw.rounded_rectangle((110, 38, 1170, 682), radius=30, outline=(120, 231, 255, 255), width=4)
    points = [(225, 540), (320, 430), (440, 500), (590, 355), (770, 395), (915, 265), (1060, 455)]
    for i in range(len(points) - 1):
        draw.line((*points[i], *points[i + 1]), fill=(95, 205, 255, 170), width=3)
    for px, py in points:
        glow, pos = soft_glow(draw, (px, py), 12, (130, 220, 255, 150))
        img.alpha_composite(glow, pos)
        draw.ellipse((px - 5, py - 5, px + 5, py + 5), fill=(255, 250, 220, 255))
    draw.line((420, 330, 830, 470), fill=(199, 170, 120, 120), width=18)
    draw_planet(draw, (330, 310), 36, ((226, 192, 106), (156, 118, 62)))
    draw_planet(draw, (900, 520), 60, ((134, 233, 255), (48, 102, 145)))
    draw_ship(draw, 525, 460, 0.55)
    img.save(ASSET_BG / "map_bg.png")


def arrival_background():
    img = vertical_gradient((W, H), hex_rgba("1a120a"), hex_rgba("0b1222"))
    draw = ImageDraw.Draw(img)
    draw.rounded_rectangle((60, 32, 1220, 690), radius=28, outline=(255, 188, 85, 255), width=4)
    add_starfield(img, seed=66)
    draw_planet(draw, (960, 245), 210, ((204, 245, 255), (90, 158, 122)))
    glow, pos = soft_glow(draw, (862, 246), 62, (81, 221, 255, 110))
    img.alpha_composite(glow, pos)
    draw_ship(draw, 610, 255, 1.0, accent=(110, 235, 255, 255))
    draw_ship(draw, 988, 360, 0.34, accent=(104, 235, 255, 255))
    draw_ship(draw, 1022, 430, 0.28, accent=(104, 235, 255, 255))
    img.save(ASSET_BG / "arrival_bg.png")


def flight_background():
    img = vertical_gradient((W, H), hex_rgba("06101d"), hex_rgba("060915"))
    add_starfield(img, seed=17)
    draw = ImageDraw.Draw(img)
    for i in range(9):
        y = 120 + i * 56
        draw.line((80, y, 1200, y + random.randint(-10, 10)), fill=(30, 70, 120, 45), width=2)
    glow, pos = soft_glow(draw, (640, 360), 110, (56, 180, 255, 70))
    img.alpha_composite(glow, pos)
    img.save(ASSET_BG / "flight_bg.png")


def engineering_background():
    img = vertical_gradient((W, H), hex_rgba("8cc8f0"), hex_rgba("516f8d"))
    draw = ImageDraw.Draw(img)
    draw.rectangle((0, 0, W, 100), fill=(36, 56, 64, 235))
    draw.rectangle((0, H - 118, W, H), fill=(63, 73, 88, 255))
    for x0 in (84, 210, 846, 972):
        draw.rectangle((x0, 94, x0 + 22, 610), fill=(120, 136, 152, 235), outline=(44, 55, 70, 255))
        draw.rectangle((x0 + 22, 94, x0 + 120, 610), fill=(205, 230, 246, 30))
    draw.rectangle((96, 94, 1080, 610), outline=(52, 66, 84, 255), width=4)
    for y in (158, 226, 294, 362, 430, 498, 566):
        draw.line((108, y, 1066, y), fill=(255, 255, 255, 34), width=2)
    for i in range(8):
        px = 150 + i * 114
        draw.line((px, 106, px, 598), fill=(255, 255, 255, 24), width=2)
    for lamp_x, lamp_y in ((164, 278), (190, 370), (836, 214), (842, 450), (964, 336), (964, 544)):
        glow, pos = soft_glow(draw, (lamp_x, lamp_y), 18, (255, 233, 94, 130))
        img.alpha_composite(glow, pos)
        draw.ellipse((lamp_x - 8, lamp_y - 14, lamp_x + 8, lamp_y + 2), fill=(255, 228, 110, 255), outline=(112, 92, 20, 255))
        draw.rectangle((lamp_x - 2, lamp_y + 2, lamp_x + 2, lamp_y + 16), fill=(255, 240, 170, 220))
    img.save(ASSET_ENGINEERING / "engineering_bg.png")


def engineering_ring(size=512):
    img = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    draw = ImageDraw.Draw(img)
    cx = cy = size // 2
    outer = size * 0.43
    inner = size * 0.28
    glass_inner = size * 0.22

    for i in range(18, 0, -1):
        inset = i * 2
        alpha = max(8, 95 - i * 4)
        draw.ellipse((cx - outer - inset, cy - outer - inset, cx + outer + inset, cy + outer + inset),
                     outline=(96, 206, 255, alpha), width=2)

    for r in range(int(outer), int(inner), -1):
        t = (r - inner) / max(1.0, (outer - inner))
        shade = int(214 * t + 148 * (1 - t))
        draw.ellipse((cx - r, cy - r, cx + r, cy + r),
                     outline=(shade, shade, shade + 6, 255), width=2)

    for idx in range(16):
        ang = math.tau * idx / 16.0
        x0 = cx + math.cos(ang) * inner * 0.95
        y0 = cy + math.sin(ang) * inner * 0.95
        x1 = cx + math.cos(ang) * outer * 0.98
        y1 = cy + math.sin(ang) * outer * 0.98
        draw.line((x0, y0, x1, y1), fill=(88, 94, 102, 255), width=4)

    draw.ellipse((cx - inner, cy - inner, cx + inner, cy + inner), fill=(74, 84, 92, 245), outline=(26, 32, 40, 255), width=4)
    for ring in (0.92, 0.78, 0.64):
        rr = inner * ring
        draw.ellipse((cx - rr, cy - rr, cx + rr, cy + rr), outline=(102, 108, 116, 220), width=3)

    for idx in range(10):
        ang = math.tau * idx / 10.0 + 0.08
        x = cx + math.cos(ang) * inner * 0.68
        y = cy + math.sin(ang) * inner * 0.68
        draw.rounded_rectangle((x - 34, y - 10, x + 34, y + 10), radius=6,
                               fill=(118, 125, 132, 255), outline=(60, 66, 74, 255), width=2)

    # translucent spinning blades
    blade = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    bdraw = ImageDraw.Draw(blade)
    for angle in (24, 144, 264):
        rad = math.radians(angle)
        ax = cx + math.cos(rad) * glass_inner * 0.18
        ay = cy + math.sin(rad) * glass_inner * 0.18
        bx = cx + math.cos(rad + 0.24) * outer * 0.76
        by = cy + math.sin(rad + 0.24) * outer * 0.76
        cx2 = cx + math.cos(rad - 0.24) * outer * 0.76
        cy2 = cy + math.sin(rad - 0.24) * outer * 0.76
        bdraw.polygon([(ax, ay), (bx, by), (cx2, cy2)], fill=(240, 250, 255, 120))
    blade = blade.filter(ImageFilter.GaussianBlur(1))
    img.alpha_composite(blade)

    draw.ellipse((cx - glass_inner, cy - glass_inner, cx + glass_inner, cy + glass_inner),
                 outline=(225, 236, 244, 240), width=4)
    draw.ellipse((cx - 72, cy - 72, cx + 72, cy + 72), fill=(215, 224, 230, 248), outline=(74, 88, 100, 255), width=5)
    draw.ellipse((cx - 40, cy - 40, cx + 40, cy + 40), fill=(170, 180, 188, 255), outline=(68, 78, 88, 255), width=4)
    draw.ellipse((cx - 15, cy - 15, cx + 15, cy + 15), fill=(235, 242, 246, 255), outline=(120, 128, 136, 255), width=2)
    img.save(ASSET_ENGINEERING / "centrifuge_ring.png")


def engineering_bumper(size=64):
    img = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    draw = ImageDraw.Draw(img)
    base_y = 46
    draw.rectangle((16, base_y, 48, base_y + 6), fill=(75, 82, 95, 255), outline=(22, 28, 40, 255))
    for i in range(5):
        y0 = base_y - i * 5
        y1 = y0 - 5
        if i % 2 == 0:
            draw.line((22, y0, 42, y1), fill=(58, 62, 72, 255), width=3)
        else:
            draw.line((42, y0, 22, y1), fill=(58, 62, 72, 255), width=3)
    draw.rounded_rectangle((12, 10, 52, 24), radius=8, fill=(226, 88, 72, 255), outline=(66, 28, 26, 255), width=2)
    glow, pos = soft_glow(draw, (32, 24), 12, (255, 215, 105, 110))
    img.alpha_composite(glow, pos)
    img.save(ASSET_ENGINEERING / "bumper.png")


def engineering_gravity_field(size=256):
    img = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    draw = ImageDraw.Draw(img)
    c = size // 2
    for r in range(110, 35, -8):
        alpha = int(max(12, 170 - (110 - r) * 1.6))
        draw.ellipse((c - r, c - r, c + r, c + r), outline=(65, 231, 255, alpha), width=4)
    glow = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    gdraw = ImageDraw.Draw(glow)
    gdraw.ellipse((c - 34, c - 34, c + 34, c + 34), fill=(95, 225, 255, 150))
    glow = glow.filter(ImageFilter.GaussianBlur(18))
    img.alpha_composite(glow)
    draw.ellipse((c - 16, c - 16, c + 16, c + 16), fill=(184, 250, 255, 255))
    img.save(ASSET_ENGINEERING / "gravity_field.png")


def draw_thick_line(draw, p0, p1, width, fill):
    draw.line((p0[0], p0[1], p1[0], p1[1]), fill=fill, width=width)
    r = max(1, width // 2)
    draw.ellipse((p0[0] - r, p0[1] - r, p0[0] + r, p0[1] + r), fill=fill)
    draw.ellipse((p1[0] - r, p1[1] - r, p1[0] + r, p1[1] + r), fill=fill)


def engineering_intern(path, line_color, head_fill, accent, pose):
    size = 96
    img = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    draw = ImageDraw.Draw(img)

    glow = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    gdraw = ImageDraw.Draw(glow)
    gdraw.ellipse((18, 8, 78, 90), fill=accent)
    glow = glow.filter(ImageFilter.GaussianBlur(16))
    img.alpha_composite(glow)

    head_center = (49, 22)
    neck = (49, 38)
    hip = (49, 60)

    if pose == 0:
        left_hand, right_hand = (26, 48), (72, 42)
        left_foot, right_foot = (34, 81), (68, 73)
    elif pose == 1:
        left_hand, right_hand = (20, 54), (70, 34)
        left_foot, right_foot = (30, 78), (72, 84)
    else:
        left_hand, right_hand = (34, 40), (78, 57)
        left_foot, right_foot = (26, 75), (61, 85)

    left_elbow = ((neck[0] + left_hand[0]) // 2 - 4, (neck[1] + left_hand[1]) // 2 + 5)
    right_elbow = ((neck[0] + right_hand[0]) // 2 + 6, (neck[1] + right_hand[1]) // 2 + 4)
    left_knee = ((hip[0] + left_foot[0]) // 2 - 3, (hip[1] + left_foot[1]) // 2 + 7)
    right_knee = ((hip[0] + right_foot[0]) // 2 + 3, (hip[1] + right_foot[1]) // 2 + 6)

    outline = (26, 30, 36, 255)
    for w, color in ((9, outline), (6, line_color)):
        draw_thick_line(draw, neck, hip, w, color)
        draw_thick_line(draw, neck, left_elbow, w, color)
        draw_thick_line(draw, left_elbow, left_hand, w, color)
        draw_thick_line(draw, neck, right_elbow, w, color)
        draw_thick_line(draw, right_elbow, right_hand, w, color)
        draw_thick_line(draw, hip, left_knee, w, color)
        draw_thick_line(draw, left_knee, left_foot, w, color)
        draw_thick_line(draw, hip, right_knee, w, color)
        draw_thick_line(draw, right_knee, right_foot, w, color)

    head_r = 16
    draw.ellipse((head_center[0] - head_r - 2, head_center[1] - head_r - 2,
                  head_center[0] + head_r + 2, head_center[1] + head_r + 2), fill=outline)
    draw.ellipse((head_center[0] - head_r, head_center[1] - head_r,
                  head_center[0] + head_r, head_center[1] + head_r), fill=head_fill)
    # simple face like the reference interns rather than helmets
    draw.ellipse((head_center[0] - 1, head_center[1] - 1, head_center[0] + 1, head_center[1] + 1), fill=(84, 60, 42, 130))
    img.save(path)


def save_panels():
    rounded_panel((960, 620),
                  hex_rgba("101b31", 232), hex_rgba("19263e", 232),
                  hex_rgba("7de7ff"), hex_rgba("53d8ff", 130), hex_rgba("ffc76b", 120)).save(ASSET_UI / "card_large.png")
    rounded_panel((720, 500),
                  hex_rgba("11192b", 228), hex_rgba("19253e", 228),
                  hex_rgba("75dfff"), hex_rgba("4fcfff", 120), hex_rgba("ffc05d", 115)).save(ASSET_UI / "card_medium.png")
    rounded_panel((640, 140),
                  hex_rgba("7ab9be", 255), hex_rgba("bd8acb", 255),
                  hex_rgba("f4d16e"), hex_rgba("ffd86e", 170), hex_rgba("dffdf8", 160)).save(ASSET_UI / "button_primary.png")
    rounded_panel((640, 140),
                  hex_rgba("506e95", 255), hex_rgba("395377", 255),
                  hex_rgba("82d4ff"), hex_rgba("79d7ff", 165), hex_rgba("dff5ff", 130)).save(ASSET_UI / "button_secondary.png")
    rounded_panel((640, 140),
                  hex_rgba("3c4352", 220), hex_rgba("313848", 220),
                  hex_rgba("748299"), hex_rgba("6584a0", 100), hex_rgba("afb9cb", 80)).save(ASSET_UI / "button_disabled.png")
    rounded_panel((540, 180),
                  hex_rgba("213651", 228), hex_rgba("24304b", 228),
                  hex_rgba("68ccf4"), hex_rgba("56d0ff", 115), hex_rgba("bff3ff", 105)).save(ASSET_UI / "badge_panel.png")


def main():
    ensure_dirs()
    menu_background()
    map_background()
    arrival_background()
    flight_background()
    engineering_background()
    engineering_ring()
    engineering_bumper()
    engineering_gravity_field()
    for pose in range(3):
        engineering_intern(
            ASSET_ENGINEERING / f"intern_normal_{pose}.png",
            line_color=(238, 162, 75, 255),
            head_fill=(226, 187, 149, 255),
            accent=(255, 203, 122, 105),
            pose=pose,
        )
        engineering_intern(
            ASSET_ENGINEERING / f"intern_cyber_{pose}.png",
            line_color=(82, 186, 255, 255),
            head_fill=(208, 229, 240, 255),
            accent=(92, 225, 255, 105),
            pose=pose,
        )
    save_panels()


if __name__ == "__main__":
    main()
