#!/usr/bin/env python3
"""Refresh the data of the bundled example files composeApp/examples/*.json from the research folders.

Every example file is a JSON object (PoCoView's example format): the metadata (name, n, group,
label, description, links, source), then the encoding of its arrangement, `"mod1s6"`, `"mod2s6"`
(sparse6 strings) or `"sequences"` (one list per circle), for the triangle-saturated ones also
`"crossingColors"` (R, G, B per entry of the sequences); a file with several arrangements has the
list `"arrangements"` of such objects instead, one per line. This script rewrites only the data and
keeps the metadata of an existing file (a new file gets placeholders to fill in, and has to be
listed in composeApp/examples/index.json, which names the groups and their files).

Data: the sparse6 lines of the website and the computations (`.enc` model 1, `.mod2s6` model 2);
the colour dictionaries of the old sage code (`*.txt`: the dual graph as `{(u, v): 'colour', ...}`),
converted to model 1 sparse6; the coloured draw7 drawings of the triangle-saturated arrangements
(`*_1_1tutte7.ipe`), which give the sequences and the colour of every crossing; Koester's crossing
graph, converted by `scripts/crossing_graph_sequences.sage` (needs sage); the maxima of triangles for
n = 8, 9, intersection sequences from `../data/maxtri/`, and all arrangements with the maximum of
digons, from `../data/maxdigons/` (several lines make a set). The Gradle task generateExamples embeds
the files into the app. The connected digon-free sets are the digon-free members of the connected
ones (DIGON_FREE_MEMBERS). Provenance: doc/DEVELOPMENT.md.

    python3 scripts/import_examples.py [--research ~/github/research/pca/pseudocircles]
"""
import argparse
import json
import math
import os
import re
import subprocess
import sys

# (destination folder, source path relative to the research folder[, destination stem]); the file is
# <folder>/<stem>.json, by default the source name up to its first dot
SOURCES = [
    *[("great", f"computations/results/great/all{n}.enc.greatcircles.enc") for n in range(3, 9)],
    *[("intersecting", f"computations/results/abstract/all{n}.enc") for n in range(2, 6)],
    *[("digonfree", f"computations/results/abstract/all{n}_digonfree.enc") for n in range(3, 7)],
    *[("connected", f"website/arrangements/pseudocircles/connected/all{n}.mod2s6") for n in range(3, 6)],
    *[("mintriangles", f"website/arrangements/triangles/digonfree_minimal_triangles/{name}.txt") for name in [
        "all5_df_8_triangles", "all6_df_8_triangles",
        "all7_df_10_triangles", "all8_df_11_triangles", "all9_df_12_triangles", "some10_df_14_triangles",
        "some11_df_15_triangles", "some12_df_16_triangles", "some13_df_18_triangles", "some14_df_19_triangles",
    ]],
    *[("maxtriangles", f"website/arrangements/triangles/digonfree_maximal_triangles/{name}.enc") for name in [
        "all5_df_12_triangles", "all5_digon_13_triangles", "all6_df_20_triangles", "all7_df_29_triangles",
        "some8_df_37_triangles",
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
    ("colorings", "computations/biconnected_results/n8_koester.enc", "n7_koester"),
    ("named", "computations/results/n6_nonr_all_triangles_krupp_number2.enc"),
    ("named", "computations/results/n6_nonr_edelsbrunnerramos.enc"),
    ("named", "computations/results/n9_nonr_pappus.enc"),
]

# intersection sequences copied verbatim from the pca folder (the parent of the research folder):
# (destination folder, source path relative to the pca folder, destination stem)
PCA_SEQUENCES = [
    ("maxtriangles", "data/maxtri/n8_p3eq38.txt", "max8_digon_38_triangles"),
    ("maxtriangles", "data/maxtri/n9_p3eq50.txt", "max9_df_50_triangles"),
    ("maxtriangles", "data/maxtri/n10_p3ge61_partial.txt", "some10_df_61_triangles"),
    # all intersecting arrangements with the maximum 2n - 2 digons, one per line
    *[("maxdigons", f"data/maxdigons/n{n}_p2eq{2 * n - 2}.txt", f"all{n}_{2 * n - 2}_digons") for n in range(4, 8)],
]

# the digon-free members of these files (model 2 sparse6 lines, verbatim and in their order):
# (destination folder, source path relative to the research folder, destination stem)
DIGON_FREE_MEMBERS = [
    ("connected", f"website/arrangements/pseudocircles/connected/all{n}.mod2s6", f"all{n}_digonfree") for n in range(3, 6)
]

# the metadata keys of an example file, in this order (PoCoView's), then the data
METADATA = ["name", "n", "group", "label", "description", "links", "source"]

# the colour classes 0, 1, 2 of the drawings as colour words
COLOR_WORDS = ["R", "G", "B"]


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


def sparse6_decode(text):
    """The vertex count and the edges of a sparse6 string (the inverse of `sparse6`)."""
    data = [ord(c) - 63 for c in text.strip()[1:]]
    if data[0] < 63:
        n, data = data[0], data[1:]
    elif data[1] < 63:
        n, data = (data[1] << 12) | (data[2] << 6) | data[3], data[4:]
    else:
        n, data = sum(data[2 + i] << (6 * (5 - i)) for i in range(6)), data[8:]
    k = max(1, (n - 1).bit_length()) if n > 1 else 0
    bits = [(d >> i) & 1 for d in data for i in range(5, -1, -1)]
    edges = []
    v = 0
    pos = 0
    while pos + 1 + k <= len(bits):
        b = bits[pos]
        x = int("".join(map(str, bits[pos + 1:pos + 1 + k])) or "0", 2)
        pos += 1 + k
        if b:
            v += 1
        if x >= n or v >= n:
            break
        if x > v:
            v = x
        else:
            edges.append((x, v))
    return n, edges


def model2_is_digon_free(line):
    """Whether a model 2 sparse6 line (NEW encoding) has no digon: a face vertex (the neighbour of a
    marker leaf) is adjacent to the arcs around it, so a digon is a face vertex with 2 arcs."""
    n, edges = sparse6_decode(line)
    adj = [set() for _ in range(n)]
    for u, v in edges:
        adj[u].add(v)
        adj[v].add(u)
    faces = [next(iter(adj[leaf])) for leaf in range(n) if len(adj[leaf]) == 1]
    return all(len(adj[f]) - 1 != 2 for f in faces)


def colour_dict_to_sparse6(line):
    """`{(u, v): 'colour', ...}` (the dual graph with its edges coloured by circle) -> sparse6."""
    edges = [(int(u), int(v)) for u, v in re.findall(r"\((\d+),\s*(\d+)\)", line)]
    if not edges:
        raise ValueError(f"no edges in {line[:60]!r}")
    n = 1 + max(max(e) for e in edges)
    return sparse6(n, edges)


def parse_sequences(text):
    """ArrView's text format `{1: [2, -2], 2: [1, -1]}` (keys 1..n in order) -> lists."""
    return [[int(e) for e in l.replace(",", " ").split()] for l in re.findall(r"\[([^\]]*)\]", text)]


def ipe_colouring(text):
    """A draw7 drawing with coloured crossings -> (sequences, colour word R/G/B of every entry).

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
    return lists, [[COLOR_WORDS[c] for c in col] for col in colours]


def jlists(lists, indent):
    """Lists as JSON arrays: on one line, or one list per line after `indent`."""
    one = ["[" + ", ".join(json.dumps(e, ensure_ascii=False) for e in l) + "]" for l in lists]
    if indent is None:
        return "[" + ", ".join(one) + "]"
    return "[\n" + ",\n".join(indent + x for x in one) + "\n" + indent[:-2] + "]"


def data_members(member, indent):
    """The data of one arrangement as JSON members: its encoding, maybe the crossing colours."""
    out = []
    for key, value in member.items():
        out.append(json.dumps(key) + ": " + (jlists(value, indent) if isinstance(value, list) else json.dumps(value)))
    return out


def example_json(meta, members):
    """The file: the metadata in METADATA order, then the data (one member inline, several as a list)."""
    lines = []
    for key in METADATA:
        value = meta[key]
        if key == "links":
            text = "[]" if not value else "[\n" + ",\n".join(
                "    {\"title\": %s, \"url\": %s}" % (json.dumps(l["title"], ensure_ascii=False), json.dumps(l["url"]))
                for l in value) + "\n  ]"
        else:
            text = json.dumps(value, ensure_ascii=False)
        lines.append("  " + json.dumps(key) + ": " + text)
    if len(members) == 1:
        lines += ["  " + m for m in data_members(members[0], "    ")]
    else:
        lines.append("  \"arrangements\": [\n" + ",\n".join(
            "    {" + ", ".join(data_members(m, None)) + "}" for m in members) + "\n  ]")
    return "{\n" + ",\n".join(lines) + "\n}\n"


def write(target, folder, stem, members):
    """Writes <folder>/<stem>.json with the given members, keeping the metadata of the file there."""
    out = os.path.join(target, folder, stem + ".json")
    if os.path.exists(out):
        with open(out) as f:
            meta = json.load(f)
    else:
        n = len(members[0]["sequences"]) if "sequences" in members[0] else 0
        meta = {"name": stem, "n": n, "group": "TODO", "label": "TODO", "description": "TODO", "links": [], "source": None}
        print(f"{folder}/{stem}.json is new: fill in its metadata and list it in index.json")
    os.makedirs(os.path.dirname(out), exist_ok=True)
    with open(out, "w") as f:
        f.write(example_json(meta, members))
    size = os.path.getsize(out)
    print(f"{folder}/{stem}.json: {len(members)} arrangement(s), {size} bytes")
    return size


def main():
    parser = argparse.ArgumentParser(description=__doc__.split("\n")[0])
    parser.add_argument("--research", default=os.path.expanduser("~/github/research/pca/pseudocircles"))
    args = parser.parse_args()
    target = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "composeApp", "examples")
    total = 0
    for folder, source, *dest in SOURCES:
        path = os.path.join(args.research, source)
        name = os.path.basename(source)
        stem = dest[0] if dest else name.split(".")[0]
        if name.endswith("_1_1tutte7.ipe"):
            with open(path) as f:
                lists, colours = ipe_colouring(f.read())
            total += write(target, folder, stem, [{"sequences": lists, "crossingColors": colours}])
            continue
        if name == "n8_koester.enc":
            script = os.path.join(os.path.dirname(os.path.abspath(__file__)), "crossing_graph_sequences.sage")
            out = subprocess.run(["sage", script, path], check=True, capture_output=True, text=True).stdout
            total += write(target, folder, stem, [{"sequences": parse_sequences(out.strip().splitlines()[-1])}])
            continue
        with open(path) as f:
            lines = [line.strip() for line in f if line.strip()]
        if name.endswith(".txt"):
            lines = [colour_dict_to_sparse6(line) for line in lines]
        key = "mod2s6" if name.endswith(".mod2s6") else "mod1s6"
        total += write(target, folder, stem, [{key: line} for line in lines])
    for folder, source, stem in PCA_SEQUENCES:
        with open(os.path.join(args.research, "..", source)) as f:
            total += write(target, folder, stem, [{"sequences": parse_sequences(line)} for line in f if line.strip()])
    for folder, source, stem in DIGON_FREE_MEMBERS:
        with open(os.path.join(args.research, source)) as f:
            lines = [line.strip() for line in f if line.strip()]
        total += write(target, folder, stem, [{"mod2s6": line} for line in lines if model2_is_digon_free(line)])
    print(f"total: {total} bytes")


if __name__ == "__main__":
    sys.exit(main())
