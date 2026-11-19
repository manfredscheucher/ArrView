# Converts the sparse6 of a 4-regular plane graph (the crossings of an arrangement, as in
# computations/biconnected_results/n8_koester.enc) into ArrView's signed intersection sequences.
# Used by scripts/import_examples.py; the sequences are the last line of the output.
#
#     sage scripts/crossing_graph_sequences.sage FILE
#
# Circles are traced by going straight at every crossing; the sign follows ArrView's convention
# (+d in the list of c: d crosses c from its right to its left, i.e. ccw order c_fwd, d_fwd, c_bwd, d_bwd).
import sys

path = sys.argv[1]
G = Graph(open(path).read().strip())
assert G.is_planar(set_embedding=True), "not planar"
assert all(d == 4 for d in G.degree()), "not 4-regular"
emb = G.get_embedding()  # neighbours in clockwise order
rot = {v: list(reversed(emb[v])) for v in emb}  # counter-clockwise

# a dart (u, v): travel from u to v; straight ahead at v continues to the opposite neighbour
def straight(u, v):
    i = rot[v].index(u)
    return rot[v][(i + 2) % 4]

circles = []  # list of vertex cycles
seen = set()
for u in G.vertices():
    for v in rot[u]:
        if (u, v) in seen:
            continue
        cyc = []
        a, b = u, v
        while (a, b) not in seen:
            seen.add((a, b))
            seen.add((b, a))  # the same circle backwards
            cyc.append(a)
            a, b = b, straight(a, b)
        assert (a, b) == (u, v)
        circles.append(cyc)
n = len(circles)
# at every vertex: which circles pass, and their outgoing neighbour there
at = {}
for c, cyc in enumerate(circles):
    for i, v in enumerate(cyc):
        at.setdefault(v, []).append((c, cyc[i - 1], cyc[(i + 1) % len(cyc)]))
for v in at:
    assert len(at[v]) == 2 and at[v][0][0] != at[v][1][0], "a circle crosses itself at %s" % v
lists = []
for c, cyc in enumerate(circles):
    lst = []
    for i, v in enumerate(cyc):
        prev, nxt = cyc[i - 1], cyc[(i + 1) % len(cyc)]
        (d, dprev, dnxt) = [x for x in at[v] if x[0] != c][0]
        r = rot[v]
        i0 = r.index(prev)  # c_bwd at ccw position i0, c_fwd at i0 + 2
        assert r[(i0 + 2) % 4] == nxt
        sign = 1 if r[(i0 + 3) % 4] == dnxt else -1
        lst.append(sign * (d + 1))
    lists.append(lst)
print("n =", n, "crossings =", G.order(), "lengths =", [len(l) for l in lists])
pairs = {tuple(sorted((c, abs(e) - 1))) for c, l in enumerate(lists) for e in l}
print("non-crossing pairs:", [(a + 1, b + 1) for a in range(n) for b in range(a + 1, n) if (a, b) not in pairs])
print("{" + ", ".join("%d: [%s]" % (c + 1, ", ".join(str(e) for e in l)) for c, l in enumerate(lists)) + "}")
