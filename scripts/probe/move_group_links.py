"""One-off (2026-11-26): moves the group-only links of index.json into the set files they apply to.

Writes each file with import_examples.example_json, so the format is the importer's.
"""
import glob, json, os, sys
sys.path.insert(0, os.path.join(os.path.dirname(__file__), ".."))
from import_examples import example_json, METADATA

ROOT = os.path.join(os.path.dirname(__file__), "..", "..", "composeApp", "examples")
ADD = {
    "digongraphs/": {"title": "Paper: On Digons and Triangles (Felsner, Roch, Scheucher)", "url": "https://arxiv.org/abs/2208.12110"},
    "fueredipalasti/": {"title": "Paper: Arrangements of lines with a large number of triangles (Füredi, Palásti)",
                        "url": "https://doi.org/10.1090/S0002-9939-1984-0760946-2"},
}
check = "--check" in sys.argv
for path in sorted(glob.glob(os.path.join(ROOT, "*", "*.json"))):
    rel = os.path.relpath(path, ROOT)
    text = open(path).read()
    o = json.loads(text)
    meta = {k: o[k] for k in METADATA}
    members = o["arrangements"] if "arrangements" in o else [{k: v for k, v in o.items() if k not in METADATA}]
    if check:
        if example_json(meta, members) != text: print("differs:", rel)
        continue
    for prefix, link in ADD.items():
        if rel.startswith(prefix) and link["url"] not in [l["url"] for l in meta["links"]]:
            meta["links"].append(link)
            open(path, "w").write(example_json(meta, members))
            print("added to", rel)
