# Pixel-art mod icon on a 32x32 grid: a Minecraft tooltip-styled rounded card with square brackets
# "[ ]" framing an enchanted diamond sword and a golden chest badge at the bottom right.
# Usage (needs Pillow):
#   python3 docs/make-icon.py icon.png common/src/main/resources/assets/chatitems/icon.png
# Writes the 512px store icon and the 128px icon packed into the mod.
import sys
from PIL import Image

N = 32
BG, BG_GLOW = (20, 16, 36), (34, 24, 62)
BORDER_OUTER, BORDER_INNER = (44, 32, 78), (112, 68, 204)
BRACKET = (180, 166, 224)
DIAMOND_LIGHT = (140, 255, 248)
DIAMOND_MID = (58, 218, 212)
DIAMOND_DARK = (26, 138, 142)
GLINT = (212, 128, 255)
GUARD_GOLD = (246, 198, 72)
GUARD_DARK = (176, 124, 32)
HILT_WOOD = (122, 78, 42)
CHEST_TOP = (208, 142, 54)
CHEST_SIDE = (164, 104, 34)
CHEST_RIM = (72, 44, 18)
CHEST_LATCH = (244, 216, 108)

img = Image.new("RGBA", (N, N), (0, 0, 0, 0))
px = img.load()


def inside(x, y, inset):
    r = 5 - inset
    lo, hi = inset, N - 1 - inset
    if not (lo <= x <= hi and lo <= y <= hi):
        return False
    cx = min(max(x, lo + r), hi - r)
    cy = min(max(y, lo + r), hi - r)
    return (x - cx) ** 2 + (y - cy) ** 2 <= r * r + 1


# Tooltip-inspired rounded frame with a subtle purple-violet inner rim and radial glow
for y in range(N):
    for x in range(N):
        if inside(x, y, 0):
            px[x, y] = BORDER_OUTER + (255,)
        if inside(x, y, 1):
            px[x, y] = BORDER_INNER + (255,)
        if inside(x, y, 2):
            d = ((x - 14) ** 2 + (y - 14) ** 2) ** 0.5
            t = max(0.0, 1.0 - d / 16.0)
            px[x, y] = tuple(int(BG[i] + (BG_GLOW[i] - BG[i]) * t) for i in range(3)) + (255,)


def put(x, y, c):
    if 0 <= x < N and 0 <= y < N:
        px[x, y] = c + (255,)


def rect(x0, y0, x1, y1, c):
    for y in range(y0, y1 + 1):
        for x in range(x0, x1 + 1):
            put(x, y, c)


# Left bracket "["
rect(4, 6, 5, 23, BRACKET)
rect(6, 6, 7, 7, BRACKET)
rect(6, 22, 7, 23, BRACKET)

# Right bracket "]"
rect(24, 6, 25, 23, BRACKET)
rect(22, 6, 23, 7, BRACKET)
rect(22, 22, 23, 23, BRACKET)

# Diagonal enchanted diamond sword inside the brackets (tip at top-right (20, 7), pommel at (8, 19))
for i in range(9):
    bx = 12 + i
    by = 15 - i
    put(bx, by, DIAMOND_LIGHT if i % 3 != 1 else GLINT)
    put(bx - 1, by, DIAMOND_MID)
    put(bx, by + 1, DIAMOND_MID)
    put(bx - 1, by - 1, DIAMOND_DARK)
    put(bx + 1, by + 1, DIAMOND_DARK)
put(20, 7, DIAMOND_LIGHT)
put(20, 8, DIAMOND_MID)
put(19, 7, DIAMOND_MID)

# Crossguard
for dx, dy in ((-2, -2), (-1, -1), (0, 0), (1, 1), (2, 2)):
    put(11 + dx, 16 + dy, GUARD_GOLD)
    put(11 + dx, 17 + dy, GUARD_DARK)

# Hilt & pommel
put(10, 17, HILT_WOOD)
put(9, 18, HILT_WOOD)
rect(7, 19, 8, 20, GUARD_GOLD)
put(7, 20, GUARD_DARK)

# Mini inventory chest badge in bottom-right corner
rect(20, 19, 27, 26, CHEST_RIM)
rect(21, 20, 26, 22, CHEST_TOP)
rect(21, 24, 26, 25, CHEST_SIDE)
rect(23, 22, 24, 24, CHEST_LATCH)

for size, out in ((512, sys.argv[1]), (128, sys.argv[2])):
    img.resize((size, size), Image.NEAREST).save(out, "PNG", optimize=True)
