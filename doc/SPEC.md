# ArrView spec log

Requests and decisions. Newest first.

## 2026-11-17: First version, arrangements of pseudocircles

### Request (Manfred, summarized)

A web app (Kotlin Multiplatform + Compose, like PoCoView; GitHub Pages) to explore arrangements of
pseudocircles interactively. Later possibly a general "ArrView" with modes for lines (y = kx + d, no
vertical lines), pseudolines, circles (center, radius) and pseudocircles. Focus now: pseudocircles,
with the data of the pseudocircles website / the results folders.

- Drawing like `draw7` (iterated weighted Tutte embedding, smooth curves), faces coloured.
- Click a triangle: combinatorial triangle flip. Click a digon: the digon flips over, the two
  pseudocircles become disjoint (only if the arrangement stays connected).
- Show the intersection orders (like the λ matrix in PoCoView) and the sparse6 string; both are
  editable (unlike λ in PoCoView, the combinatorics is the whole data here). Invalid input is
  rejected with an explanation (validity: Linhart–Ortner).
- Properties: p-vector (p2, p3, … ; p2 and p3 are the most relevant), number of non-intersecting
  pairs, and an intersection graph widget: K_n straight-line drawing, vertices labelled 1..n, edge
  ij only if circles i and j cross.
- Not only pairwise intersecting arrangements: connected ones too (model 2). No touchings as
  a concept of their own; digons may be drawn as touchings (as in the old model 2 drawings).
- Model 1 <-> model 2: conversion and switching the drawing between both (intersecting / great
  only; connected only model 2).
- Examples prepared from the results folder, grouped:
  - pseudo great circles (all triples Krupp)
  - intersecting + digon-free
  - intersecting (model 1)
  - connected (model 2)
  General (disconnected) arrangements are left out on purpose.
- Settings for the layout (iterations etc.); goal: nice pictures, not many iterations.
- Header like ksat / PoCoView: "ArrView" + web-only subtitle.
- Concepts may be taken over from PoCoView (GUI), ksat-chess (loading from strings), the old
  sage programs and rusty-arrangements.

### Sources

- Old sage code: `~/github/research/pca/pseudocircles/computations/` (`program/basics.py`,
  `program/draw7.sage`, `program_py3/`, `model2_program/` incl. `convert_to_model2.sage`,
  `convert_back_to_model1.sage`, `model2basics.sage`, `draw3.sage`), public subset in
  `~/github/pseudocircle-arrangements-tools/pseudocircles_intersecting/`.
- Rust port: `~/github/rusty-arrangements` (intersection sequences, faces, triangle flip,
  canonical form, sparse6 + model 2 decoding in `scripts/probe/model2.rs`).
- Validity: Linhart–Ortner, EJC 11 (2004) #R30, in `~/github/research/pca/literature`.
- Website: https://www3.math.tu-berlin.de/diskremath/pseudocircles/ (formats on `?show=information`).
- Drawing method: Felsner & Scheucher, "Arrangements of Pseudocircles: Triangles and Drawings",
  Section "Iterated Tutte Embeddings".

### Decisions and interpretations (first version)

- Repo `~/github/ArrView`, origin `git@github.com:manfredscheucher/ArrView.git` (Manfred pushes).
  Web dev-server port 8611. Subtitle: "Arrangements of pseudocircles".
- Internal data: the signed intersection sequences per pseudocircle (as in rusty-arrangements and
  Linhart–Ortner). Everything else (primal graph, faces, model 1 / model 2 graphs, sparse6) is
  computed from them.
- Formats read and written: intersection sequences (`{1: [2, -2, 3, -3], ...}`), model 1 sparse6
  (dual graph, intersecting only), model 2 sparse6 (primal-dual graph; the website's encoding with
  one pendant leaf per face; the old hub encoding is read as well).
- Validity of edited sequences: Linhart–Ortner Def. 2.4/2.5 and Thm. 4.5 (Euler characteristic 2
  and the "within" condition) plus Thm. 5.4 (consistency). Note: the "all 4-tuples must be
  correct" condition Manfred mentioned was not found in the Linhart material; the conditions
  above are what the papers state. To be confirmed by Manfred.
- Drawing: model 1 view = iterated weighted Tutte embedding of the primal graph (digons as
  touchings); model 2 view = the same on the vertex-edge-face graph (digons as real 2-gons). The
  outer face can be chosen (default: as in draw7, maximal symmetry or the largest face).
- Bundled examples: complete lists for small n, selected sets for larger n (bundle size limits
  of a web page); the exact choice is recorded in `doc/DEVELOPMENT.md`.
