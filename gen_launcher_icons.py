#!/usr/bin/env python3
"""
Pure-stdlib PNG generator for LocateMe launcher icons.
Produces correctly-formed PNG files at all required mipmap densities.
No external dependencies needed — uses only struct, zlib, math.
"""
import struct, zlib, math, os

# ── Colour palette ───────────────────────────────────────────────────────────
BG          = (13,  27,  42)        # #0D1B2A  deep navy
PIN         = (79,  195, 247)       # #4FC3F7  light blue
PIN_DARK    = (13,  27,  42)        # cutout = bg colour
RING1       = (79,  195, 247,  26)  # very faint ring (α≈10%)
RING2       = (79,  195, 247,  40)  # slightly stronger ring
TEXT_BLUE   = (79,  195, 247)       # "Locate"
TEXT_WHITE  = (255, 255, 255)       # "Me"
TEXT_DIM    = (174, 204, 216, 102)  # "LIVE LOCATION" dim

# ── DPI configs ──────────────────────────────────────────────────────────────
SIZES = {
    "mdpi"   : 48,
    "hdpi"   : 72,
    "xhdpi"  : 96,
    "xxhdpi" : 144,
    "xxxhdpi": 192,
}

BASE_DIR = os.path.dirname(os.path.abspath(__file__))
RES_DIR  = os.path.join(BASE_DIR, "app", "src", "main", "res")

# ── PNG helpers ──────────────────────────────────────────────────────────────

def _chunk(tag: bytes, data: bytes) -> bytes:
    c = zlib.crc32(tag + data) & 0xFFFFFFFF
    return struct.pack(">I", len(data)) + tag + data + struct.pack(">I", c)

def make_png(pixels, width, height):
    """Encode a flat list of (R,G,B) tuples to a valid PNG bytestring."""
    raw = b""
    for y in range(height):
        raw += b"\x00"  # filter byte = None
        for x in range(width):
            r, g, b = pixels[y * width + x]
            raw += bytes([r, g, b])
    sig   = b"\x89PNG\r\n\x1a\n"
    ihdr  = _chunk(b"IHDR", struct.pack(">IIBBBBB", width, height, 8, 2, 0, 0, 0))
    idat  = _chunk(b"IDAT", zlib.compress(raw, 9))
    iend  = _chunk(b"IEND", b"")
    return sig + ihdr + idat + iend

# ── Drawing primitives ───────────────────────────────────────────────────────

def blend(bg, fg_rgb, alpha):
    """Alpha-composite fg over bg, alpha in [0,255]."""
    a = alpha / 255.0
    return tuple(int(fg_rgb[i] * a + bg[i] * (1 - a)) for i in range(3))

def dist(x1, y1, x2, y2):
    return math.sqrt((x1-x2)**2 + (y1-y2)**2)

def draw_circle_aa(pixels, w, h, cx, cy, r, colour, alpha=255, fill=True, stroke_w=0):
    """
    Rasterise a circle with 1-px anti-aliasing.
    fill=True → filled disc.  stroke_w>0 → ring of that width.
    """
    ir = int(r) + 2
    for py in range(max(0, int(cy-ir)), min(h, int(cy+ir)+1)):
        for px in range(max(0, int(cx-ir)), min(w, int(cx+ir)+1)):
            d = dist(px+0.5, py+0.5, cx, cy)
            if fill:
                aa = max(0.0, min(1.0, r - d + 0.5))
            else:
                inner = r - stroke_w
                aa = max(0.0, min(1.0, r - d + 0.5)) - max(0.0, min(1.0, inner - d + 0.5))
            if aa > 0:
                a = int(alpha * aa)
                idx = py * w + px
                pixels[idx] = blend(pixels[idx], colour, a)

def draw_pin(pixels, w, h, cx, cy, pin_r, hole_r, colour, bg_colour):
    """
    Draw the teardrop location pin.
    Upper half: circle of radius pin_r centred at (cx, cy).
    Lower half: two Bezier-approximated curves that meet at the tip.
    Then cut out a hole circle of radius hole_r centred at (cx, cy).
    """
    tip_y  = cy + pin_r * 1.55   # tip of the tear drop
    # Rasterise row by row
    for py in range(max(0, int(cy - pin_r) - 2), min(h, int(tip_y) + 2)):
        for px in range(max(0, int(cx - pin_r) - 2), min(w, int(cx + pin_r) + 2)):
            sx, sy = px + 0.5, py + 0.5
            d_from_center = dist(sx, sy, cx, cy)

            # Is point inside the teardrop body?
            in_body = False
            if sy <= cy:                   # upper half: simple disc
                in_body = d_from_center < pin_r
            else:                          # lower half: tapered region
                # Linear taper: width at row = pin_r * (1 - t) where t = (sy-cy)/(tip_y-cy)
                t = (sy - cy) / (tip_y - cy)
                half_w = pin_r * (1 - t) * (1 - t)  # quadratic taper for smooth teardrop
                in_body = abs(sx - cx) < half_w

            if not in_body:
                continue

            # Is point inside the cutout hole?
            in_hole = d_from_center < hole_r

            # Anti-alias edge
            if in_hole:
                aa = max(0.0, min(1.0, d_from_center - hole_r + 1.0))
                pixels[int(py) * w + int(px)] = blend(pixels[int(py) * w + int(px)], bg_colour, int(aa * 255))
            else:
                # Body edge AA
                # upper edge
                edge_d = pin_r - d_from_center if sy <= cy else 1.0
                aa = min(1.0, max(0.0, edge_d))
                if aa > 0:
                    pixels[int(py) * w + int(px)] = blend(pixels[int(py) * w + int(px)], colour, int(aa * 255))

def draw_pixel_text(pixels, w, h, x, y, text, colour, scale=1):
    """Tiny 5×7 pixel-font renderer (A-Z, a-z, space)."""
    FONT = {
        'L': ["10000","10000","10000","10000","10000","10000","11111"],
        'I': ["11111","00100","00100","00100","00100","00100","11111"],
        'V': ["10001","10001","10001","01010","01010","00100","00100"],
        'E': ["11111","10000","10000","11110","10000","10000","11111"],
        'O': ["01110","10001","10001","10001","10001","10001","01110"],
        'C': ["01111","10000","10000","10000","10000","10000","01111"],
        'A': ["01110","10001","10001","11111","10001","10001","10001"],
        'T': ["11111","00100","00100","00100","00100","00100","00100"],
        'N': ["10001","11001","11001","10101","10011","10011","10001"],
        ' ': ["00000","00000","00000","00000","00000","00000","00000"],
        'l': ["01100","00100","00100","00100","00100","00100","01110"],
        'o': ["00000","00000","01110","10001","10001","10001","01110"],
        'c': ["00000","00000","01111","10000","10000","10000","01111"],
        'a': ["00000","00000","01110","00001","01111","10001","01111"],
        't': ["00100","00100","01110","00100","00100","00100","00111"],
        'e': ["00000","00000","01110","10001","11111","10000","01111"],
        'M': ["10001","11011","11011","10101","10001","10001","10001"],
    }
    cx = x
    for ch in text:
        glyph = FONT.get(ch, FONT.get(ch.upper()))
        if glyph is None:
            cx += (4 + 1) * scale
            continue
        for row_i, row in enumerate(glyph):
            for col_i, bit in enumerate(row):
                if bit == '1':
                    for dy in range(scale):
                        for dx in range(scale):
                            px2 = cx + col_i * scale + dx
                            py2 = y  + row_i * scale + dy
                            if 0 <= px2 < w and 0 <= py2 < h:
                                a = colour[3] if len(colour) == 4 else 255
                                pixels[py2 * w + px2] = blend(pixels[py2 * w + px2], colour[:3], a)
        cx += (5 + 1) * scale

# ── Icon renderer ────────────────────────────────────────────────────────────

def render_icon(size):
    pixels = [BG] * (size * size)
    cx, cy = size / 2, size * 0.40   # pin centre (slightly above middle)
    pin_r  = size * 0.22
    hole_r = size * 0.095

    # Radial rings
    draw_circle_aa(pixels, size, size, cx, cy, pin_r * 2.0,  PIN, alpha=26,  fill=False, stroke_w=max(1, size//72))
    draw_circle_aa(pixels, size, size, cx, cy, pin_r * 1.45, PIN, alpha=40,  fill=False, stroke_w=max(1, size//72))

    # Pin body
    draw_pin(pixels, size, size, cx, cy, pin_r, hole_r, PIN, BG)

    # Wordmark "Locate" (blue) + "Me" (white)
    # Use pixel font; scale depends on icon size
    sc = max(1, size // 48)
    char_w = (5 + 1) * sc
    word_locate = "locate"  # lowercase draws smaller glyphs
    word_me     = "Me"
    total_w = (len(word_locate) + len(word_me)) * char_w
    tx = int((size - total_w) / 2)
    ty = int(size * 0.70)
    draw_pixel_text(pixels, size, size, tx, ty, word_locate, TEXT_BLUE + (255,), sc)
    draw_pixel_text(pixels, size, size, tx + len(word_locate) * char_w, ty, word_me, TEXT_WHITE + (255,), sc)

    # Tagline "LIVE LOCATION"
    tagline = "LIVE LOCATION"
    tag_sc  = max(1, size // 96)
    tag_w   = len(tagline) * (5 + 1) * tag_sc
    ttx     = int((size - tag_w) / 2)
    tty     = int(ty + 8 * sc + 2)
    draw_pixel_text(pixels, size, size, ttx, tty, tagline, TEXT_DIM, tag_sc)

    return pixels

# ── Main ─────────────────────────────────────────────────────────────────────

def main():
    for density, size in SIZES.items():
        out_dir = os.path.join(RES_DIR, f"mipmap-{density}")
        os.makedirs(out_dir, exist_ok=True)
        pixels = render_icon(size)
        png_data = make_png(pixels, size, size)
        for name in ("ic_launcher.png", "ic_launcher_round.png"):
            path = os.path.join(out_dir, name)
            with open(path, "wb") as f:
                f.write(png_data)
            print(f"  ✓  {density}/{name}  ({size}×{size})")

if __name__ == "__main__":
    main()
    print("\nAll launcher PNGs generated successfully.")
