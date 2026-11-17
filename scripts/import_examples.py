#!/usr/bin/env python3
"""Copy the bundled example files into composeApp/examples/ from the research folders.

The files are taken verbatim (one sparse6 string per line, model 1 `.enc` or model 2 `.mod2s6`),
except the colour dictionaries of the old sage code (`*.txt`: the dual graph as
`{(u, v): 'colour', ...}`), which are converted to model 1 sparse6 (`.mod1s6`). The Gradle task
generateExamples embeds every file of composeApp/examples/ into the app; the catalogue (names,
groups, descriptions) is in core/Examples.kt. Provenance: doc/DEVELOPMENT.md.

    python3 scripts/import_examples.py [--research ~/github/research/pca/pseudocircles]
"""
import argparse
import os
import re
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


def main():
    parser = argparse.ArgumentParser(description=__doc__.split("\n")[0])
    parser.add_argument("--research", default=os.path.expanduser("~/github/research/pca/pseudocircles"))
    args = parser.parse_args()
    target = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "composeApp", "examples")
    total = 0
    for folder, source in SOURCES:
        path = os.path.join(args.research, source)
        with open(path) as f:
            lines = [line.strip() for line in f if line.strip()]
        name = os.path.basename(source)
        if name.endswith(".txt"):
            lines = [colour_dict_to_sparse6(line) for line in lines]
            name = name[:-len(".txt")] + ".mod1s6"
        os.makedirs(os.path.join(target, folder), exist_ok=True)
        out = os.path.join(target, folder, name)
        with open(out, "w") as f:
            f.write("\n".join(lines) + "\n")
        size = os.path.getsize(out)
        total += size
        print(f"{folder}/{name}: {len(lines)} lines, {size} bytes")
    print(f"total: {total} bytes")


if __name__ == "__main__":
    sys.exit(main())
