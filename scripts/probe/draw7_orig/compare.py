"""Compare the B-spline control points of the original draw7.sage (.ipe output) with ArrView's
(dumped by a probe test) for the same arrangement: both normalised (centroid, RMS radius), ArrView's
tried in every rotation by 2 pi k / l and reflection of the outer polygon and every maxsym outer
face; circles matched by their point sets. Prints the best maximum deviation (normalised units).

Usage: python3 compare.py cases/n5_181
"""
import math, re, sys

def ipe_curves(path):
    text = open(path).read()
    curves = []
    for m in re.finditer(r'<path layer="alpha" stroke="(\w+)" pen="heavier">\n(.*?)u\n</path>', text, re.S):
        pts = [tuple(map(float, l.split())) for l in m.group(2).strip().splitlines()]
        curves.append((m.group(1), pts))
    return curves

def arrview_faces(path):
    faces = []
    for line in open(path):
        if line.startswith("face"):
            faces.append((line.strip(), []))
        else:
            parts = line.split()
            faces[-1][1].append([tuple(map(float, p.split(","))) for p in parts[2:]])
    return faces

def normalise(curves):
    allp = [p for c in curves for p in c]
    cx = sum(p[0] for p in allp) / len(allp); cy = sum(p[1] for p in allp) / len(allp)
    r = math.sqrt(sum((p[0] - cx) ** 2 + (p[1] - cy) ** 2 for p in allp) / len(allp))
    return [[((p[0] - cx) / r, (p[1] - cy) / r) for p in c] for c in curves]

def cyc_dist(a, b):
    """max distance of two closed control polygons, best over start and direction"""
    if len(a) != len(b): return float("inf")
    n = len(a); best = float("inf")
    for rev in (False, True):
        bb = b[::-1] if rev else b
        for s in range(n):
            d = max(math.dist(a[i], bb[(i + s) % n]) for i in range(n))
            best = min(best, d)
    return best

def main(stem):
    import glob
    ipe = ipe_curves(glob.glob(stem + ".enc_1_1tutte7.ipe")[0])
    orig = normalise([c for _, c in ipe])
    best = (float("inf"), None)
    for label, curves in arrview_faces(stem + ".arrview.txt"):
        ours = normalise(curves)
        for refl in (1, -1):
            for k in range(360):  # any rotation in steps of 1 degree, refined below
                a = math.radians(k)
                rot = [[(math.cos(a) * x - math.sin(a) * refl * y, math.sin(a) * x + math.cos(a) * refl * y) for x, y in c] for c in ours]
                # match circles greedily by their first-point independent distance
                used = set(); worst = 0
                for oc in orig:
                    d, j = min((cyc_dist(oc, rc), j) for j, rc in enumerate(rot) if j not in used)
                    used.add(j); worst = max(worst, d)
                    if worst > best[0]: break
                if worst < best[0]:
                    best = (worst, (label, refl, k))
    print(stem, "max deviation %.3g (normalised units)" % best[0], best[1])

if __name__ == "__main__":
    for s in sys.argv[1:]:
        main(s)
