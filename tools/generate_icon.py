#!/usr/bin/env python3
"""Generate the LianLianKan launcher icon from one single shape definition.

Usage:
    python tools/generate_icon.py

Both the vector track (adaptive icon, API 26+) and the raster track (legacy
icon, API 24-25) are produced from the SAME geometry below, so the two can
never drift apart. Re-run this script after touching any of the shape
constants; never hand-edit the generated files.

Outputs:
    app/src/main/res/drawable/ic_launcher_background.xml
    app/src/main/res/drawable/ic_launcher_foreground.xml
    app/src/main/res/drawable/ic_launcher_monochrome.xml
    app/src/main/res/mipmap-{m,h,xh,xxh,xxxh}dpi/ic_launcher.png        (48/72/96/144/192 px)
    app/src/main/res/mipmap-{m,h,xh,xxh,xxxh}dpi/ic_launcher_round.png  (same sizes, circular mask)

Design:
    Two identical rounded tiles, each carrying a single dot as its "pattern",
    joined by a polyline that has exactly two bends. The game clears two
    matching tiles only when a path with at most two bends connects them, so the
    icon encodes the core rule directly instead of merely decorating.

Colour provenance (read this before changing the palette below):
    PRIMARY / PRIMARY_CONTAINER / BACKGROUND are the literal ARGB values of
    ThemePalettes.byId(0).light -- the default light-blue palette in
    app/src/main/java/com/ldxy/lianliankan/ui/theme/Palette.kt -- which the
    Material 3 tonal-palette algorithm computes AT RUNTIME from the seed colour.
    A launcher icon is a static resource, so those values must be frozen here.
    If the seed colours or the tone overrides in Palette.kt ever change, re-dump
    the three roles (primary, primaryContainer, background) and update them
    below, otherwise the icon silently drifts away from the app's own colours.

Implementation notes:
    Standard library only (zlib / struct / math). A PNG is just zlib-compressed
    scan lines plus a few chunks, so Pillow is not needed. Anti-aliasing comes
    from 4x supersampling: the composition is rendered once per size family at
    4x the largest size of that family and then box-downsampled. That is exact
    (an 8x8 box average over a 4x render equals a 4x4 supersample of the
    half-size render, because the legacy composition scales uniformly) and much
    cheaper than rasterizing all five sizes separately.

    Every comment in this file is English on purpose: the scripts under tools/
    are kept pure ASCII so they survive any console code page.
"""

from __future__ import annotations

import math
import struct
import zlib
from dataclasses import dataclass
from pathlib import Path

# --------------------------------------------------------------------------
# Palette -- frozen from ThemePalettes.byId(0).light (see the module docstring)
# --------------------------------------------------------------------------
PRIMARY = 0xFF126684           # tiles and the connecting polyline
PRIMARY_CONTAINER = 0xFF8FD3F6  # adaptive background layer, and the pattern dots
BACKGROUND = 0xFFF8F9FC        # splash screen background only -- see RASTER_BACKDROP
MONOCHROME = 0xFFFFFFFF        # themed icon layer: tinted by the system

# The legacy raster icon must use the SAME field colour as the adaptive
# background layer. Using BACKGROUND here instead would mean API 24-25 users see
# a near-white icon while API 26+ users see the primaryContainer field -- two
# visibly different icons for the same app. The splash screen is a separate
# concern: it wants a near-neutral colour so the hand-off to the app's own
# background is seamless, so it keeps BACKGROUND.
RASTER_BACKDROP = PRIMARY_CONTAINER

# --------------------------------------------------------------------------
# Geometry, in the adaptive icon's 108 x 108 canvas
# --------------------------------------------------------------------------
CANVAS = 108.0
CENTER = CANVAS / 2.0

# The masking circle guaranteed by the adaptive icon spec has a 66dp diameter.
# Anything outside it may be clipped by launcher masks (circle, squircle,
# teardrop), so the whole composition is kept inside it -- see check_safe_zone.
SAFE_RADIUS = 33.0

TILE_SIDE = 22.0        # rounded tile edge length
TILE_RADIUS = 6.8       # ~31% of the side, i.e. unambiguously a "rounded" tile
TILE_GAP = 8.4          # horizontal gap between the two tiles
DOT_RADIUS = 4.2        # the "pattern" carried by each tile
STROKE = 6.2            # connecting polyline width
DROP = 11.0             # how far the polyline runs below the tiles
EXIT_INSET = 5.2        # stub distance from the tile's outer edge

# For legacy (pre-adaptive) icons the composition should fill more of the
# canvas than the 66/108 safe zone asks for: the largest content dimension is
# scaled to this fraction of the icon edge.
LEGACY_CONTENT_FRACTION = 0.72

SUPERSAMPLE = 4

RES_DIR = Path(__file__).resolve().parent.parent / "app" / "src" / "main" / "res"
REPO_ROOT = RES_DIR.parent.parent.parent.parent

# density bucket -> legacy icon edge length in px
DENSITIES = {
    "mdpi": 48,
    "hdpi": 72,
    "xhdpi": 96,
    "xxhdpi": 144,
    "xxxhdpi": 192,
}


@dataclass(frozen=True)
class Tile:
    """An axis-aligned rounded square."""

    x0: float
    y0: float
    x1: float
    y1: float
    radius: float

    @property
    def cx(self) -> float:
        return (self.x0 + self.x1) / 2.0

    @property
    def cy(self) -> float:
        return (self.y0 + self.y1) / 2.0


@dataclass(frozen=True)
class Geometry:
    tiles: tuple
    dots: tuple
    path: tuple
    content_w: float
    content_h: float


def build_geometry() -> Geometry:
    """Lay the composition out and centre it on the canvas.

    Everything is derived from TILE_SIDE / TILE_GAP / DROP / STROKE, so the
    composition stays centred no matter how those are tuned.
    """
    content_w = TILE_SIDE * 2.0 + TILE_GAP
    # The polyline ends on the tiles' bottom edge, so the stroke's round cap is
    # the lowest ink: half a stroke width below the horizontal run.
    content_h = TILE_SIDE + DROP + STROKE / 2.0

    left = CENTER - content_w / 2.0
    top = CENTER - content_h / 2.0
    bottom = top + TILE_SIDE
    right_x = left + TILE_SIDE + TILE_GAP
    run_y = bottom + DROP

    tiles = (
        Tile(left, top, left + TILE_SIDE, bottom, TILE_RADIUS),
        Tile(right_x, top, right_x + TILE_SIDE, bottom, TILE_RADIUS),
    )
    dots = ((tiles[0].cx, tiles[0].cy), (tiles[1].cx, tiles[1].cy))
    # Down from the left tile, across, up into the right tile: exactly two bends.
    path = (
        (left + EXIT_INSET, bottom),
        (left + EXIT_INSET, run_y),
        (right_x + TILE_SIDE - EXIT_INSET, run_y),
        (right_x + TILE_SIDE - EXIT_INSET, bottom),
    )
    return Geometry(tiles, dots, path, content_w, content_h)


# --------------------------------------------------------------------------
# Geometry predicates -- shared by the rasteriser and the safety check
# --------------------------------------------------------------------------
def in_rounded_rect(x: float, y: float, tile: Tile) -> bool:
    if x < tile.x0 or x > tile.x1 or y < tile.y0 or y > tile.y1:
        return False
    # Clamp to the "core" rectangle, then test against the corner radius; this
    # is the standard rounded-rectangle test (valid for side >= 2 * radius).
    cx = min(max(x, tile.x0 + tile.radius), tile.x1 - tile.radius)
    cy = min(max(y, tile.y0 + tile.radius), tile.y1 - tile.radius)
    dx = x - cx
    dy = y - cy
    return dx * dx + dy * dy <= tile.radius * tile.radius


def _segment_distance_sq(px, py, ax, ay, bx, by) -> float:
    vx = bx - ax
    vy = by - ay
    wx = px - ax
    wy = py - ay
    length_sq = vx * vx + vy * vy
    t = 0.0 if length_sq == 0.0 else (wx * vx + wy * vy) / length_sq
    t = min(1.0, max(0.0, t))
    dx = wx - t * vx
    dy = wy - t * vy
    return dx * dx + dy * dy


def near_path(x: float, y: float, points) -> bool:
    half_sq = (STROKE / 2.0) ** 2
    for index in range(len(points) - 1):
        ax, ay = points[index]
        bx, by = points[index + 1]
        if _segment_distance_sq(x, y, ax, ay, bx, by) <= half_sq:
            return True
    return False


def path_bounds(points):
    """Bounding box of the stroked polyline, for cheap rejection in the rasteriser."""
    xs = [p[0] for p in points]
    ys = [p[1] for p in points]
    half = STROKE / 2.0
    return (min(xs) - half, min(ys) - half, max(xs) + half, max(ys) + half)


def extreme_points(geo: Geometry):
    """Points of the composition that are furthest from the canvas centre.

    Only the corners of the tiles and the ends of the polyline can be extremes:
    the polyline's straight runs bend towards the centre, and the tiles' edges
    are flat. Adding the outward radius of each rounded corner / round cap
    gives the outermost ink.
    """
    points = []
    for tile in geo.tiles:
        for cx, cy in (
            (tile.x0 + tile.radius, tile.y0 + tile.radius),
            (tile.x1 - tile.radius, tile.y0 + tile.radius),
            (tile.x0 + tile.radius, tile.y1 - tile.radius),
            (tile.x1 - tile.radius, tile.y1 - tile.radius),
        ):
            dx = cx - CENTER
            dy = cy - CENTER
            norm = math.hypot(dx, dy) or 1.0
            points.append((cx + dx / norm * tile.radius, cy + dy / norm * tile.radius))
    for px, py in geo.path:
        dx = px - CENTER
        dy = py - CENTER
        norm = math.hypot(dx, dy) or 1.0
        points.append((px + dx / norm * (STROKE / 2.0), py + dy / norm * (STROKE / 2.0)))
    return points


def check_safe_zone(geo: Geometry) -> float:
    """Fail loudly if the foreground would be clipped by an adaptive icon mask.

    The 66dp circle is the only region every launcher mask is required to keep,
    so the design is bounded by it. Returning the worst-case radius lets the
    caller print the margin that is left.
    """
    worst = 0.0
    for px, py in extreme_points(geo):
        worst = max(worst, math.hypot(px - CENTER, py - CENTER))
    if worst > SAFE_RADIUS:
        raise SystemExit(
            "foreground leaves the 66dp safe circle: worst radius "
            "%.2f > %.2f -- shrink TILE_SIDE / DROP / STROKE" % (worst, SAFE_RADIUS)
        )
    return worst


# --------------------------------------------------------------------------
# VectorDrawable track
# --------------------------------------------------------------------------
def num(value: float) -> str:
    text = "%.3f" % value
    text = text.rstrip("0").rstrip(".")
    return "0" if text in ("", "-0") else text


def argb_hex(value: int) -> str:
    return "#%08X" % (value & 0xFFFFFFFF)


def rounded_rect_path(tile: Tile) -> str:
    """Path data for a rounded square, drawn clockwise from the top-left arc."""
    r = num(tile.radius)
    return (
        "M{x0},{y0} H{top_end} A{r},{r} 0 0 1 {x1},{y_top} V{y_bottom} "
        "A{r},{r} 0 0 1 {top_end},{y1} H{bottom_start} A{r},{r} 0 0 1 {x0},{y_bottom} "
        "V{y_top} A{r},{r} 0 0 1 {x0},{y0} Z"
    ).format(
        x0=num(tile.x0),
        y0=num(tile.y0),
        x1=num(tile.x1),
        y1=num(tile.y1),
        top_end=num(tile.x1 - tile.radius),      # the top edge stops here
        bottom_start=num(tile.x0 + tile.radius),  # the bottom edge stops here
        y_top=num(tile.y0 + tile.radius),        # start of both vertical edges
        y_bottom=num(tile.y1 - tile.radius),     # end of both vertical edges
        r=r,
    )


def circle_path(cx: float, cy: float, radius: float) -> str:
    r = num(radius)
    d = num(radius * 2.0)
    return "M{a},{b} a{r},{r} 0 1 0 {d},0 a{r},{r} 0 1 0 -{d},0 Z".format(
        a=num(cx - radius), b=num(cy), r=r, d=d
    )


def polyline_path(points) -> str:
    head = "M{},{}".format(num(points[0][0]), num(points[0][1]))
    body = "".join(" L{},{}".format(num(x), num(y)) for x, y in points[1:])
    return head + body


VECTOR_HEADER = (
    '<?xml version="1.0" encoding="utf-8"?>\n'
    "<!--\n"
    "  Generated by tools/generate_icon.py (do not edit by hand).\n"
    "  Comments are English here because the generator itself is kept pure\n"
    "  ASCII; the Chinese rationale lives in README.md and under doc/.\n"
    "-->\n"
)


def vector_document(body: str) -> str:
    return (
        VECTOR_HEADER
        + '<vector xmlns:android="http://schemas.android.com/apk/res/android"\n'
        + '    android:width="108dp"\n'
        + '    android:height="108dp"\n'
        + '    android:viewportWidth="108"\n'
        + '    android:viewportHeight="108">\n'
        + body
        + "</vector>\n"
    )


def background_xml() -> str:
    body = (
        "    <!-- A flat primaryContainer field: the brand colour the tiles sit on. -->\n"
        '    <path\n'
        '        android:fillColor="{}"\n'
        '        android:pathData="M0,0 H108 V108 H0 Z" />\n'
    ).format(argb_hex(PRIMARY_CONTAINER))
    return vector_document(body)


def foreground_xml(geo: Geometry) -> str:
    lines = ["    <!-- Two identical rounded tiles, one pattern dot each. -->\n"]
    for index, tile in enumerate(geo.tiles):
        lines.append(
            "    <!-- tile {} -->\n"
            '    <path\n'
            '        android:fillColor="{}"\n'
            '        android:pathData="{}" />\n'.format(
                index + 1, argb_hex(PRIMARY), rounded_rect_path(tile)
            )
        )
        cx, cy = geo.dots[index]
        lines.append(
            "    <!-- pattern dot {} -->\n"
            '    <path\n'
            '        android:fillColor="{}"\n'
            '        android:pathData="{}" />\n'.format(
                index + 1, argb_hex(PRIMARY_CONTAINER), circle_path(cx, cy, DOT_RADIUS)
            )
        )
    lines.append(
        "    <!-- The connecting polyline: down from the left tile, across,\n"
        "         up into the right tile. Exactly two bends: the core rule.\n"
        "         (XML comments may not contain a double hyphen, hence no dash.) -->\n"
        '    <path\n'
        '        android:fillColor="#00000000"\n'
        '        android:strokeColor="{}"\n'
        '        android:strokeWidth="{}"\n'
        '        android:strokeLineCap="round"\n'
        '        android:strokeLineJoin="round"\n'
        '        android:pathData="{}" />\n'.format(
            argb_hex(PRIMARY), num(STROKE), polyline_path(geo.path)
        )
    )
    return vector_document("".join(lines))


def monochrome_xml(geo: Geometry) -> str:
    """Single-colour silhouette for Android 13+ themed icons.

    Themed icons are tinted by the system, so only the shape and the alpha
    channel matter. The pattern dots become holes (evenOdd) rather than a second
    colour, which is what "monochrome" requires.
    """
    lines = [
        "    <!-- Themed icon layer: one opaque colour, tinted by the system.\n"
        "         evenOdd turns each pattern dot into a hole. -->\n"
    ]
    for index, tile in enumerate(geo.tiles):
        cx, cy = geo.dots[index]
        lines.append(
            "    <!-- tile {} with its pattern hole -->\n"
            '    <path\n'
            '        android:fillColor="{}"\n'
            '        android:fillType="evenOdd"\n'
            '        android:pathData="{} {}" />\n'.format(
                index + 1,
                argb_hex(MONOCHROME),
                rounded_rect_path(tile),
                circle_path(cx, cy, DOT_RADIUS),
            )
        )
    lines.append(
        "    <!-- The connecting polyline. -->\n"
        '    <path\n'
        '        android:fillColor="#00000000"\n'
        '        android:strokeColor="{}"\n'
        '        android:strokeWidth="{}"\n'
        '        android:strokeLineCap="round"\n'
        '        android:strokeLineJoin="round"\n'
        '        android:pathData="{}" />\n'.format(
            argb_hex(MONOCHROME), num(STROKE), polyline_path(geo.path)
        )
    )
    return vector_document("".join(lines))


# --------------------------------------------------------------------------
# PNG track
# --------------------------------------------------------------------------
def argb_channels(value: int):
    return ((value >> 16) & 0xFF, (value >> 8) & 0xFF, value & 0xFF, (value >> 24) & 0xFF)


def render_supersampled(geo: Geometry, ref_size: int, round_icon: bool) -> bytearray:
    """Rasterise the legacy composition at `ref_size * SUPERSAMPLE` resolution.

    The result is a flat RGBA buffer; callers box-downsample it to every target
    size of the same family.
    """
    ss = SUPERSAMPLE
    n = ref_size * ss
    scale = (LEGACY_CONTENT_FRACTION * ref_size) / geo.content_w

    # Canvas coordinate of the centre of supersample index i.
    offset = CENTER - ref_size / (2.0 * scale)
    step = 1.0 / (ss * scale)
    axes = [offset + (i + 0.5) * step for i in range(n)]

    tile_color = argb_channels(PRIMARY)
    dot_color = argb_channels(PRIMARY_CONTAINER)
    bg_color = argb_channels(RASTER_BACKDROP)
    transparent = (0, 0, 0, 0)

    line_x0, line_y0, line_x1, line_y1 = path_bounds(geo.path)
    tiles = geo.tiles
    dots = geo.dots
    dot_r_sq = DOT_RADIUS * DOT_RADIUS
    path_points = geo.path

    mask_center = ref_size / 2.0
    mask_r_sq = mask_center * mask_center

    buffer = bytearray(n * n * 4)
    for j in range(n):
        y = axes[j]
        dy_mask = (j + 0.5) / ss - mask_center
        row_base = j * n * 4
        for i in range(n):
            if round_icon:
                dx_mask = (i + 0.5) / ss - mask_center
                if dx_mask * dx_mask + dy_mask * dy_mask > mask_r_sq:
                    continue  # outside the round mask -> stays transparent
            x = axes[i]

            color = bg_color
            for index in range(2):
                tile = tiles[index]
                if in_rounded_rect(x, y, tile):
                    color = tile_color
                    dcx, dcy = dots[index]
                    ddx = x - dcx
                    ddy = y - dcy
                    if ddx * ddx + ddy * ddy <= dot_r_sq:
                        color = dot_color
                    break
            else:
                if line_x0 <= x <= line_x1 and line_y0 <= y <= line_y1:
                    if near_path(x, y, path_points):
                        color = tile_color

            o = row_base + i * 4
            buffer[o] = color[0]
            buffer[o + 1] = color[1]
            buffer[o + 2] = color[2]
            buffer[o + 3] = color[3]

    return buffer


def downsample(buffer: bytearray, big: int, factor: int) -> list:
    """Exact box downsampling: `factor` x `factor` average, integer arithmetic.

    Deterministic by construction: only integer additions and one truncated
    division per channel.
    """
    size = big // factor
    count = factor * factor
    rows = []
    for oy in range(size):
        row = bytearray(size * 4)
        for ox in range(size):
            sr = sg = sb = sa = 0
            for j in range(oy * factor, (oy + 1) * factor):
                base = j * big * 4 + ox * factor * 4
                for k in range(factor):
                    o = base + k * 4
                    sr += buffer[o]
                    sg += buffer[o + 1]
                    sb += buffer[o + 2]
                    sa += buffer[o + 3]
            o = ox * 4
            row[o] = sr // count
            row[o + 1] = sg // count
            row[o + 2] = sb // count
            row[o + 3] = sa // count
        rows.append(bytes(row))
    return rows


def write_png(path: Path, size: int, rows) -> None:
    """Write an 8-bit RGBA PNG. No filter types, no ancillary chunks."""
    raw = bytearray()
    for row in rows:
        raw.append(0)  # filter type 0 (None)
        raw += row

    def chunk(tag: bytes, data: bytes) -> bytes:
        return (
            struct.pack(">I", len(data))
            + tag
            + data
            + struct.pack(">I", zlib.crc32(tag + data) & 0xFFFFFFFF)
        )

    payload = b"\x89PNG\r\n\x1a\n"
    payload += chunk(b"IHDR", struct.pack(">IIBBBBB", size, size, 8, 6, 0, 0, 0))
    payload += chunk(b"IDAT", zlib.compress(bytes(raw), 9))
    payload += chunk(b"IEND", b"")
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_bytes(payload)


# --------------------------------------------------------------------------
# Entry point
# --------------------------------------------------------------------------
def write_text(path: Path, text: str) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_bytes(text.encode("utf-8"))


def main() -> None:
    geo = build_geometry()
    worst = check_safe_zone(geo)

    drawable = RES_DIR / "drawable"
    write_text(drawable / "ic_launcher_background.xml", background_xml())
    write_text(drawable / "ic_launcher_foreground.xml", foreground_xml(geo))
    write_text(drawable / "ic_launcher_monochrome.xml", monochrome_xml(geo))

    # One supersampled render per size family, then exact box downsampling.
    # 192 = 4 x 48 = 2 x 96 -> one 4x render serves 48/96/192.
    # 144 = 2 x 72        -> one 4x render serves 72/144.
    families = {192: (48, 96, 192), 144: (72, 144)}

    produced = []
    for square in (True, False):
        name = "ic_launcher.png" if square else "ic_launcher_round.png"
        for ref_size, targets in families.items():
            buffer = render_supersampled(geo, ref_size, round_icon=not square)
            for size in sorted(targets):
                factor = ref_size * SUPERSAMPLE // size
                rows = downsample(buffer, ref_size * SUPERSAMPLE, factor)
                for density, edge in DENSITIES.items():
                    if edge != size:
                        continue
                    out = RES_DIR / ("mipmap-" + density) / name
                    write_png(out, size, rows)
                    produced.append((out, size))

    print("palette  primary=%s primaryContainer=%s background=%s"
          % (argb_hex(PRIMARY), argb_hex(PRIMARY_CONTAINER), argb_hex(BACKGROUND)))
    print("content  %.1f x %.1f canvas units (safe circle radius %.1f, worst %.2f)"
          % (geo.content_w, geo.content_h, SAFE_RADIUS, worst))
    for out, size in produced:
        print("wrote    %-52s %d x %d" % (out.relative_to(REPO_ROOT).as_posix(), size, size))


if __name__ == "__main__":
    main()
