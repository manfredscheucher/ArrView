"""Side-by-side SVG: left the curves of the original draw7.sage (.ipe control points), right ArrView's
(aligned by compare.py's best rotation / reflection), both as closed uniform B-splines.

Usage: python3 render.py cases/n5_181 [cx cy r]   (optional zoom window in normalised units)
"""
import math, sys
from compare import ipe_curves, arrview_faces, normalise, cyc_dist

COLORS = ["#E53935", "#1E88E5", "#43A047", "#FB8C00", "#8E24AA", "#00ACC1"]

def path(ctrl):
    m = len(ctrl); d = []
    for i in range(m):
        p0, p1, p2, p3 = (ctrl[(i + k) % m] for k in range(4))
        b0 = ((p0[0] + 4 * p1[0] + p2[0]) / 6, (p0[1] + 4 * p1[1] + p2[1]) / 6)
        b1 = ((2 * p1[0] + p2[0]) / 3, (2 * p1[1] + p2[1]) / 3)
        b2 = ((p1[0] + 2 * p2[0]) / 3, (p1[1] + 2 * p2[1]) / 3)
        b3 = ((p1[0] + 4 * p2[0] + p3[0]) / 6, (p1[1] + 4 * p2[1] + p3[1]) / 6)
        if i == 0: d.append("M%f %f" % (b0[0], -b0[1]))
        d.append("C%f %f %f %f %f %f" % (b1[0], -b1[1], b2[0], -b2[1], b3[0], -b3[1]))
    return " ".join(d) + " Z"

def main(stem, zoom):
    orig = normalise([c for _, c in ipe_curves(__import__("glob").glob(stem + ".enc_1_1tutte7.ipe")[0])])
    best = None
    for label, curves in arrview_faces(stem + ".arrview.txt"):
        ours = normalise(curves)
        for refl in (1, -1):
            for k in range(0, 360, 5):
                a = math.radians(k)
                rot = [[(math.cos(a) * x - math.sin(a) * refl * y, math.sin(a) * x + math.cos(a) * refl * y) for x, y in c] for c in ours]
                pairs = []; used = set()
                for oc in orig:
                    d, j = min((cyc_dist(oc, rc), j) for j, rc in enumerate(rot) if j not in used)
                    used.add(j); pairs.append(j)
                worst = max(cyc_dist(o, rot[j]) for o, j in zip(orig, pairs))
                if best is None or worst < best[0]: best = (worst, rot, pairs)
    _, rot, pairs = best
    cx, cy, r = zoom if zoom else (0.0, 0.0, 2.2)
    w = r / 150
    def panel(curves, colors, dx, title):
        s = '<svg x="%f" y="%f" width="%f" height="%f" viewBox="%f %f %f %f" overflow="hidden">' % (cx - r + dx, -cy - r, 2 * r, 2 * r, cx - r, -cy - r, 2 * r, 2 * r)
        s += '<rect x="%f" y="%f" width="%f" height="%f" fill="white" stroke="#ccc" stroke-width="%f"/>' % (cx - r, -cy - r, 2 * r, 2 * r, w / 2)
        for c, col in zip(curves, colors):
            s += '<path d="%s" fill="none" stroke="%s" stroke-width="%f" stroke-opacity="0.85"/>' % (path(c), col, w)
        s += '<text x="%f" y="%f" font-size="%f" font-family="sans-serif">%s</text></g>' % (cx - r * 0.95, -cy - r * 0.88, r / 12, title)
        return s.replace("</text></g>", "</text></svg>")
    # the same colour for matched circles
    colors = COLORS[:len(orig)]
    ours_colors = [None] * len(rot)
    for i, j in enumerate(pairs): ours_colors[j] = colors[i]
    svg = '<svg xmlns="http://www.w3.org/2000/svg" width="1600" height="800" viewBox="%f %f %f %f">' % (cx - r, -cy - r, 4.2 * r, 2 * r)
    svg += panel(orig, colors, 0, "draw7.sage (ipe output)")
    svg += panel(rot, ours_colors, 2.2 * r, "ArrView")
    svg += "</svg>"
    name = stem + ("_zoom" if zoom else "") + "_side_by_side.svg"
    open(name, "w").write(svg)
    print(name, "max deviation %.3g" % best[0])

main(sys.argv[1], tuple(map(float, sys.argv[2:5])) if len(sys.argv) > 4 else None)
