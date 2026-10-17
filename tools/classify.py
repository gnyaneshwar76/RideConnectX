"""
Classify each maneuver icon from its vector geometry.

The arrowhead in these drawables is a three-point polyline whose middle point is
the tip; the shaft is a separate, longer path. Reading the tip and the shaft's
final heading is enough to tell a left turn from a right one without rendering
anything.
"""
import re
import sys
from pathlib import Path

NUM = re.compile(r"-?\d*\.?\d+")


def points(path: str):
    """Absolute points along a path, following M/m/L/l/V/v/H/h only."""
    tokens = re.findall(r"[MmLlHhVvCcAaSsQqZz]|-?\d*\.?\d+", path)
    pts, cur, cmd, i = [], (0.0, 0.0), None, 0
    while i < len(tokens):
        t = tokens[i]
        if t.isalpha():
            cmd, i = t, i + 1
            if cmd in "Zz":
                continue
            continue
        try:
            if cmd in ("M", "L"):
                cur = (float(tokens[i]), float(tokens[i + 1])); i += 2
            elif cmd in ("m", "l"):
                cur = (cur[0] + float(tokens[i]), cur[1] + float(tokens[i + 1])); i += 2
            elif cmd == "H":
                cur = (float(tokens[i]), cur[1]); i += 1
            elif cmd == "h":
                cur = (cur[0] + float(tokens[i]), cur[1]); i += 1
            elif cmd == "V":
                cur = (cur[0], float(tokens[i])); i += 1
            elif cmd == "v":
                cur = (cur[0], cur[1] + float(tokens[i])); i += 1
            else:
                # Curves and arcs: skip their parameters, keep the pen moving
                # roughly by consuming pairs until the next command.
                i += 1
                continue
            pts.append(cur)
        except (IndexError, ValueError):
            break
    return pts


def describe(paths):
    if not paths:
        return "empty"

    blob = " ".join(paths)
    circle = "a53.39" in blob or "m-53.39" in blob or "a130,130" in blob
    big_arc = "586.39" in blob or "586.42" in blob

    # The arrowhead is the shortest 3-point path; its middle point is the tip.
    heads = [p for p in paths if len(points(p)) == 3]
    tip = None
    if heads:
        head = min(heads, key=lambda p: len(p))
        a, b, c = points(head)
        # Tip is whichever end is furthest from the midpoint of the other two.
        mid_ac = ((a[0] + c[0]) / 2, (a[1] + c[1]) / 2)
        tip = b if (abs(b[0] - mid_ac[0]) + abs(b[1] - mid_ac[1])) > 1 else None

    bits = []
    if circle:
        bits.append("ROUNDABOUT")
    if big_arc:
        bits.append("big-arc")
    if tip:
        x, y = tip
        horiz = "left" if x < 120 else ("right" if x > 180 else "centre")
        vert = "up" if y < 120 else ("down" if y > 180 else "middle")
        bits.append(f"tip@{horiz}-{vert}")
    bits.append(f"{len(paths)}paths")
    return " ".join(bits)


def main(path):
    text = Path(path).read_text(encoding="utf-8", errors="replace")
    cur, icons = None, {}
    for line in text.splitlines():
        m = re.match(r"=== ic_step_(\d+) ===", line.strip())
        if m:
            cur = int(m.group(1)); icons[cur] = []
        elif cur is not None and line.strip():
            icons[cur].append(line.strip())

    for k in sorted(icons):
        print(f"{k:>3} : {describe(icons[k])}")


if __name__ == "__main__":
    main(sys.argv[1])
