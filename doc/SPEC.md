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
- Validity of edited sequences (Linhart–Ortner, EJC 11 (2004) #R30): Def. 2.4 (i)–(iii) (allowed
  and unique entries, `+k` in list `i` iff `-i` in list `k`), Def. 2.5 (v) (two circles cross
  twice or not at all), Def. 2.4 (iv) (connected), Def. 5.1 / Thm. 5.4 (consistent) and Thm. 4.3
  for genus 0 (Euler characteristic `V - E + F = 2`, which alone decides representability on the
  sphere). Every rejection names the condition and the circles/entries involved. Thm. 4.5 (ii)
  ("some crossing is not within any circle") is not used: it characterizes drawings in the plane
  with all circles oriented counterclockwise, while here orientations are arbitrary (the first
  version used it and wrongly rejected mirror images and flips).
- sparse6 strings written by ArrView (model 1 and model 2) use an arbitrary vertex numbering, so they
  are equal to the research/website strings (sage `canonical_label().sparse6_string()`) only up to
  isomorphism. Open point for the UI phase: canonical labelling of exported strings, if wanted.
- Ortner's 4-tuple criterion (Manfred: checking all 4-tuples suffices, given a cyclic order per
  pseudocircle, to know that a description is an arrangement of pseudocircles): the source is not
  available locally, so it is NOT implemented. It awaits the source from Manfred.
- Drawing: model 1 view = iterated weighted Tutte embedding of the primal graph (digons as
  touchings); model 2 view = the same on the vertex-edge-face graph (digons as real 2-gons). The
  outer face can be chosen (default: as in draw7, maximal symmetry or the largest face).
- Bundled examples (phase 2): compiled into the app, no runtime loading. The data files are in
  `composeApp/examples/` (copied by `scripts/import_examples.py`, sources in
  `doc/DEVELOPMENT.md`); the Gradle task `generateExamples` embeds them as strings; the catalogue
  (names, groups, descriptions) is `core/Examples.kt`. Lists are stored compactly as the original
  sparse6 lines and decoded on demand, members addressed by index ("#17 of 278"), so large lists
  need no tree rows. Raw data: 505 KB (limit ~2 MB).
- Example names: `n<N>-<class>-<description>`, class `great`, `digonfree` (intersecting and
  digon-free), `intersecting` or `connected`; a list member is `<name> #<index + 1>`.
- Example groups and contents (all complete lists unless "some"):
  - Pseudo great circles: n = 3..8 (1, 1, 1, 4, 11, 135).
  - Intersecting, digon-free: n = 3..6 (1, 2, 14, 2131). n = 6 is 140 KB, so it is included.
  - Intersecting: n = 2..5 (1, 2, 8, 278).
  - Connected (model 2): n = 3..5 (3, 21, 984), 208 KB.
  - Triangle minimizing (website `?show=triangles`, digon-free): n = 3..9 all, n = 10..14 some (the
    317 for n = 10 included, 81 KB after conversion to sparse6).
  - Triangle maximizing: the website files (n = 5: 12 triangles digon-free and 13 with a digon,
    n = 6: 20, n = 7: 28 and 29, n = 8: 37 and n = 9: 48, the last two "some").
  - Füredi–Palásti construction: n = 6, 12, ..., 42 (one each).
  - Named: Krupp, NonKrupp, 3-Chain (typed in as sequences), the non-circularizable ones of the
    website (n = 5 numbers 1–4, n = 6 numbers 1–3 digon-free), `n6_nonr_all_triangles_krupp_number2`,
    `n6_nonr_edelsbrunnerramos` and the Pappus one (n = 9). `n6_edelsbrunner_nonr` is left out: it is
    isomorphic to `n6_nonr_edelsbrunnerramos` (checked once with a probe; the file is not bundled).
- Start example (TO BE CONFIRMED by Manfred, who said "something like the N6 triangle"):
  `n6-digonfree-nonr-8-triangles` = `n6_nonr_number1_8triangles`, the non-circularizable digon-free
  arrangement of 6 pseudocircles with 8 triangles. The tests show it is the only digon-free
  arrangement of 6 pseudocircles with 8 triangles (the same as the website's triangle minimizer
  `all6_df_8_triangles`), so "N6 triangle" (N₆^Δ) most likely means it. Alternatives: the four
  triangle maximizers `n6-digonfree-20-triangles` (one of them a great-circle arrangement) and
  `n6-intersecting-nonr-all-triangles-krupp`.
