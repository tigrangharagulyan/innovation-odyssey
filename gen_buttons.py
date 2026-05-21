import os
from pathlib import Path
from PIL import Image, ImageDraw, ImageFilter

OUTPUT_DIR = Path("/Users/rockbitegames/IdeaProjects/Odyssey/assets/ui")

def ensure_dirs():
    OUTPUT_DIR.mkdir(parents=True, exist_ok=True)

def hex_rgba(hex_str, alpha=255):
    hex_str = hex_str.lstrip("#")
    return tuple(int(hex_str[i:i+2], 16) for i in (0, 2, 4)) + (alpha,)

def build_button(filename, fill, border, glow_hex=None, glow_alpha=120):
    fill_hex, border_hex = fill, border
    SIZE = 256
    btn = Image.new("RGBA", (SIZE, SIZE), (0, 0, 0, 0))

    # Radial center glow
    if glow_hex:
        glow_layer = Image.new("RGBA", (SIZE, SIZE), (0, 0, 0, 0))
        gc = hex_rgba(glow_hex)[:3]
        cx, cy = SIZE // 2, SIZE // 2
        pixels = glow_layer.load()
        for y in range(SIZE):
            for x in range(SIZE):
                dx = (x - cx) / (SIZE * 0.55)
                dy = (y - cy) / (SIZE * 0.55)
                dist = min(1.0, (dx*dx + dy*dy) ** 0.5)
                a = int((1.0 - dist) ** 2.2 * glow_alpha)
                pixels[x, y] = gc + (a,)
        btn.alpha_composite(glow_layer)

    # Button panel
    draw = ImageDraw.Draw(btn)
    box = (10, 10, 246, 246)
    r = 32
    fill   = hex_rgba(fill_hex, 245)
    border = hex_rgba(border_hex, 255)
    draw.rounded_rectangle(box, radius=r, fill=fill, outline=border, width=3)

    # Inner subtle border highlight (top edge)
    inner_box = (13, 13, 243, 243)
    hi = hex_rgba(border_hex, 55)
    draw.rounded_rectangle(inner_box, radius=r-2, fill=None, outline=hi, width=1)

    # Corner accent marks (L-brackets in border color)
    ca = 14
    bw = 3
    bc = hex_rgba(border_hex, 220)
    # top-left
    draw.rectangle([10, 10, 10+ca, 10+bw], fill=bc)
    draw.rectangle([10, 10, 10+bw, 10+ca], fill=bc)
    # top-right
    draw.rectangle([246-ca, 10, 246, 10+bw], fill=bc)
    draw.rectangle([246-bw, 10, 246, 10+ca], fill=bc)
    # bottom-left
    draw.rectangle([10, 246-bw, 10+ca, 246], fill=bc)
    draw.rectangle([10, 246-ca, 10+bw, 246], fill=bc)
    # bottom-right
    draw.rectangle([246-ca, 246-bw, 246, 246], fill=bc)
    draw.rectangle([246-bw, 246-ca, 246, 246], fill=bc)

    out = OUTPUT_DIR / filename
    btn.save(out)
    print(f"Generated: {out.name}")

def main():
    ensure_dirs()
    states = [
        dict(filename="btn_locked.png",    fill="#080c11", border="#1b2129"),
        dict(filename="btn_available.png", fill="#0a1c30", border="#00c8ff", glow_hex="#00c8ff", glow_alpha=70),
        dict(filename="btn_buyable.png",   fill="#1a1200", border="#00e8ff", glow_hex="#ffb700", glow_alpha=130),
        dict(filename="btn_active.png",    fill="#081d3a", border="#4488ff", glow_hex="#2255cc", glow_alpha=150),
        dict(filename="btn_go.png",        fill="#021a0a", border="#00ff66", glow_hex="#00cc44", glow_alpha=140),
        dict(filename="btn_golocked.png",  fill="#02160b", border="#1a6b35"),

    ]
    for s in states:
        build_button(**s)

if __name__ == "__main__":
    main()
