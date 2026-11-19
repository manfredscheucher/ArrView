#!/usr/bin/env python3
"""Copy the bundled example files into composeApp/examples/ from the research folders.

The files are taken verbatim (one sparse6 string per line, model 1 `.enc` or model 2 `.mod2s6`),
except the colour dictionaries of the old sage code (`*.txt`: the dual graph as
`{(u, v): 'colour', ...}`), which are converted to model 1 sparse6 (`.mod1s6`), the coloured
draw7 drawings of the triangle-saturated arrangements (`*_1_1tutte7.ipe`), which give the
intersection sequences (`.seq`) and the colour class of every crossing (`.colours`), and Koester's
crossing graph, converted by `scripts/crossing_graph_sequences.sage` (needs sage). The Gradle task
generateExamples embeds every file of composeApp/examples/ into the app; the catalogue (names,
groups, descriptions) is in core/Examples.kt. Provenance: doc/DEVELOPMENT.md.

    python3 scripts/import_examples.py [--research ~/github/research/pca/pseudocircles]
"""
import argparse
import math
import os
import re
import subprocess
import sys

# (destination folder, source path relative to the research folder)
SOURCES = [
    *[("great", f"computations/results/great/all{n}.enc.greatcircles.enc") for n in range(3, 9)],
    *[("intersecting", f"computations/results/abstract/all{n}.enc") for n in range(2, 6)],
    *[("digonfree", f"computations/results/abstract/all{n}_digonfree.enc") for n in range(3, 7)],
    *[("connected", f"website/arrangements/pseudocircles/connected/all{n}.mod2s6") for n in range(3, 6)],
    *[("mintriangles", f"website/arrangements/triangles/digonfree_minimal_triangles/{name}.txt") for name in [
        "all3_df_8_triangles", "all4_df_8_triangles", "all5_df_8_triangles", "all6_df_8_triangles",
        "all7_df_10_triangles", "all8_df_11_triangles", "all9_df_12_triangles", "some10_df_14_triangles",
        "some11_df_15_triangles", "some12_df_16_triangles", "some13_df_18_triangles", "some14_df_19_triangles",
    ]],
    *[("maxtriangles", f"website/arrangements/triangles/digonfree_maximal_triangles/{name}.enc") for name in [
        "all5_df_12_triangles", "all5_digon_13_triangles", "all6_df_20_triangles", "all7_df_28_triangles",
        "all7_df_29_triangles", "some8_df_37_triangles", "some9_df_48_triangles",
    ]],
    *[("fueredipalasti", f"website/arrangements/triangles/fueredi_palasti_construction/fueredi_palasti_{k}.enc")
      for k in (6, 12, 18, 24, 30, 36, 42)],
    *[("named", f"website/arrangements/nomorecircles/connected/{name}.mod2s6") for name in [
        "n5_nonr_number1_intersecting", "n5_nonr_number2", "n5_nonr_number3", "n5_nonr_number4",
    ]],
    *[("named", f"website/arrangements/nomorecircles/intersecting/digonfree/{name}.mod2s6") for name in [
        "n6_nonr_number1_8triangles", "n6_nonr_number2", "n6_nonr_number3",
    ]],
    *[("named", f"website/arrangements/nomorecircles/intersecting/additional/{name}.mod2s6") for name in [
        "n6_sym6_2", "n6_sym6_3",
    ]],
    *[("named", f"website/arrangements/nomorecircles/connected/additional/{name}.mod2s6") for name in [
        "n6_connected_digonfree_sym4_1", "n6_connected_digonfree_sym4_2", "n6_connected_digonfree_sym8_1",
        "n6_connected_digonfree_sym8_2", "n6_connected_digonfree_sym24",
    ]],
    *[("digongraphs", f"website/arrangements/digongraphs/data/{name}.enc") for name in [
        "n4_all_types_of_digongraphs", "n5_all_types_of_digongraphs", "n6_all_types_of_digongraphs",
        "n7_all_types_of_digongraphs", "n7_all_types_of_trianglefree_digongraphs",
        "n8_digongraph_trianglefree_nonplanar",
    ]],
    *[("colorings", f"website/arrangements/colorings/triangle_sat/{name}.enc_1_1tutte7.ipe") for name in [
        "n6_triangle_sat_no1_great", "n6_triangle_sat_no2", "n7_triangle_sat_no1", "n9_triangle_sat_no1",
        "n10_triangle_sat_no1_great", "n16_triangle_sat_Simmons",
    ]],
    ("colorings", "computations/biconnected_results/n8_koester.enc"),
    ("named", "computations/results/n6_nonr_all_triangles_krupp_number2.enc"),
    ("named", "computations/results/n6_nonr_edelsbrunnerramos.enc"),
    ("named", "computations/results/n9_nonr_pappus.enc"),
]


def sparse6(n, edges):
    """nauty's sparse6 (same as SageMath's sparse6_string), edges as pairs (u, v)."""
    edges = sorted(((min(e), max(e)) for e in edges), key=lambda e: (e[1], e[0]))
    k = max(1, (n - 1).bit_length()) if n > 1 else 0
    bits = []

    def put(b, x):
        bits.append(b)
        bits.extend((x >> i) & 1 for i in range(k - 1, -1, -1))

    v = 0
    for u, w in edges:
        if w == v:
            put(0, u)
        elif w == v + 1:
            put(1, u)
            v = w
        else:
            put(1, w)
            v = w
            put(0, u)
    padding = (6 - len(bits) % 6) % 6
    if k < 6 and n == (1 << k) and v == n - 2 and padding >= k + 1:
        bits.append(0)
        bits.extend([1] * (padding - 1))
    else:
        bits.extend([1] * padding)
    if n < 63:
        head = chr(n + 63)
    elif n < 258048:
        head = "~" + "".join(chr(((n >> s) & 63) + 63) for s in (12, 6, 0))
    else:
        head = "~~" + "".join(chr(((n >> s) & 63) + 63) for s in (30, 24, 18, 12, 6, 0))
    body = "".join(chr(int("".join(map(str, bits[i:i + 6])), 2) + 63) for i in range(0, len(bits), 6))
    return ":" + head + body


def colour_dict_to_sparse6(line):
    """`{(u, v): 'colour', ...}` (the dual graph with its edges coloured by circle) -> sparse6."""
    edges = [(int(u), int(v)) for u, v in re.findall(r"\((\d+),\s*(\d+)\)", line)]
    if not edges:
        raise ValueError(f"no edges in {line[:60]!r}")
    n = 1 + max(max(e) for e in edges)
    return sparse6(n, edges)


def sequences_text(lists):
    """ArrView's one-line text format: `{1: [2, -2], 2: [1, -1]}`."""
    return "{" + ", ".join("%d: [%s]" % (c + 1, ", ".join(str(e) for e in l)) for c, l in enumerate(lists)) + "}"


def ipe_colouring(text):
    """A draw7 drawing with coloured crossings -> (sequences, colour class 0/1/2 of every entry).

    The curves are the gray B-splines (control point triples, the middle one is a crossing), the
    colours the fdisk marks at the crossings (red, green, blue = classes 0, 1, 2 as IPE_COLORS of
    the sage code).
    """
    curves = []
    for m in re.finditer(r'<path (?:layer="alpha" )?stroke="gray" pen="heavier">\n(.*?)\s*u\n</path>', text, re.S):
        pts = [tuple(float(x) for x in line.split()[:2]) for line in m.group(1).strip().split("\n")]
        if len(pts) % 3:
            raise ValueError("a curve is not a list of control point triples")
        curves.append([pts[i] for i in range(1, len(pts), 3)])
    classes = {"red": 0, "green": 1, "blue": 2}
    marks = [((float(x), float(y)), classes[col]) for x, y, col in re.findall(
        r'<use (?:layer="[^"]*" )?name="mark/fdisk\(sfx\)" pos="(\S+) (\S+)"[^>]*fill="([^"]+)"', text)]

    def mark(p):
        # the drawings were saved with rounded coordinates
        best = min(marks, key=lambda mk: (mk[0][0] - p[0]) ** 2 + (mk[0][1] - p[1]) ** 2)
        if (best[0][0] - p[0]) ** 2 + (best[0][1] - p[1]) ** 2 > 1e-4:
            raise ValueError(f"no mark at the crossing {p}")
        return best

    at = {}
    for c, cyc in enumerate(curves):
        for i, p in enumerate(cyc):
            at.setdefault(mark(p)[0], []).append((c, i))
    if len(at) != len(marks) or any(len(v) != 2 or v[0][0] == v[1][0] for v in at.values()):
        raise ValueError("the marks are not the crossings of two curves each")

    def angle(p, q):
        return math.atan2(q[1] - p[1], q[0] - p[0])

    lists, colours = [], []
    for c, cyc in enumerate(curves):
        lst, col = [], []
        for i, p in enumerate(cyc):
            pos, cls = mark(p)
            d, j = [x for x in at[pos] if x[0] != c][0]
            other = curves[d]
            # the straight-line drawing is plane: the rotation at p is the angular order of the
            # neighbouring crossings; + if it is c forward, d forward, c backward, d backward (ccw)
            rays = {"cf": cyc[(i + 1) % len(cyc)], "df": other[(j + 1) % len(other)], "cb": cyc[i - 1], "db": other[j - 1]}
            order = sorted(rays, key=lambda r: angle(p, rays[r]))
            k = order.index("cf")
            lst.append(d + 1 if order[(k + 1) % 4] == "df" else -(d + 1))
            col.append(cls)
        lists.append(lst)
        colours.append(col)
    return sequences_text(lists), sequences_text(colours)


def write(target, folder, name, lines):
    os.makedirs(os.path.join(target, folder), exist_ok=True)
    out = os.path.join(target, folder, name)
    with open(out, "w") as f:
        f.write("\n".join(lines) + "\n")
    size = os.path.getsize(out)
    print(f"{folder}/{name}: {len(lines)} lines, {size} bytes")
    return size


def main():
    parser = argparse.ArgumentParser(description=__doc__.split("\n")[0])
    parser.add_argument("--research", default=os.path.expanduser("~/github/research/pca/pseudocircles"))
    args = parser.parse_args()
    target = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "composeApp", "examples")
    total = 0
    for folder, source in SOURCES:
        path = os.path.join(args.research, source)
        name = os.path.basename(source)
        if name.endswith("_1_1tutte7.ipe"):
            stem = name[:-len(".enc_1_1tutte7.ipe")]
            with open(path) as f:
                seq, colours = ipe_colouring(f.read())
            total += write(target, folder, stem + ".seq", [seq])
            total += write(target, folder, stem + ".colours", [colours])
            continue
        if name == "n8_koester.enc":
            script = os.path.join(os.path.dirname(os.path.abspath(__file__)), "crossing_graph_sequences.sage")
            out = subprocess.run(["sage", script, path], check=True, capture_output=True, text=True).stdout
            total += write(target, folder, "n8_koester.seq", [out.strip().splitlines()[-1]])
            continue
        with open(path) as f:
            lines = [line.strip() for line in f if line.strip()]
        if name.endswith(".txt"):
            lines = [colour_dict_to_sparse6(line) for line in lines]
            name = name[:-len(".txt")] + ".mod1s6"
        total += write(target, folder, name, lines)
    print(f"total: {total} bytes")


if __name__ == "__main__":
    sys.exit(main())
