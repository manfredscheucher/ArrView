# ArrView spec log

Requests and decisions. Newest first.

## 2026-11-17: Phase 3b, widgets, dialogs, help

### Request (Manfred, via the phase plan)

- Widgets (columns, toggled in the toolbar and the View menu, remembered): Properties (n, crossings,
  faces, p-vector with p2 and p3 prominent, non-intersecting pairs, classes connected / intersecting /
  digon-free / pseudo great circles / cylindrical, Krupp / NonKrupp triples); Sequences (editable,
  one line per circle, Apply validates with Linhart-Ortner, reason in the error dialog, unapplied
  draft counts as unsaved); Encoding (model 1 sparse6 if intersecting, else a note why not; model 2
  sparse6; editable with Apply, Copy); Intersection graph (K_n on a circle, labels 1..n in the circle
  colours, edge ij iff i and j cross, small fixed size); Layout (max iterations, eps, Model 1/2,
  Reset to defaults).
- Examples dialog (Arrangement menu, first item, and from Welcome): the core groups as a tree, each
  set a leaf; the selected set shows description, size and for lists a member chooser (number
  1..size, previous / next); Load; no "Loading..."; state kept between openings; drawn tree arrows.
- Import / Export (sequences, model 1 / model 2 sparse6; import detects `{` / `:`), Open / Save file
  (JSON with name + sequences, or text) with PoCoView's "Save changes?" flow for Load / Import /
  Open / close / Cmd+Q; Export SVG.
- Help: General, Widgets, Formats, Keyboard shortcuts, About ArrView (generated licenses, website,
  papers). Plain short ASCII texts, a HelpClaimsTest. Welcome dialog like PoCoView.

### Decisions and interpretations

- Unsaved = the arrangement differs from the one at the last load or save, or a widget draft differs
  from the current text (compared live, as PoCoView does). **All three editors** (sequences, model 1,
  model 2) count as drafts, not only the sequences: an unapplied sparse6 edit would be lost the same
  way. Drafts stay when the arrangement changes on the canvas (as in PoCoView); applying a stale
  draft replaces the arrangement (undoable).
- Applying a draft is one undo step; text that decodes to the current arrangement just drops the draft.
- Sequences widget: the text format with one circle per line, keys right-aligned
  (`{1: [...],` / ` 2: [...]}`); lines do not wrap, the field scrolls sideways. The sparse6 fields
  wrap. Copy copies what the field shows (the draft, if any).
- Model 1 sparse6 is offered for every intersecting arrangement (also n = 2), while the Model 1
  drawing needs a face with 3 or more sides (so not n = 2), as decided in phase 3a.
- Saved files: JSON `{"name": ..., "sequences": [[...], ...]}` (list i for circle i, entries as in
  the text format) or the sequences text (`.txt`). Open file / Import read JSON (quoted key after
  `{`), sequences (`{` or `[`) or a sparse6 line (model 1 tried first, then model 2). An imported
  arrangement without a name is called "imported"; a JSON name wins over the file name.
- Examples: everything collapsed at first; a leaf's details show "n = N, M arrangements" and, for
  lists, `< [#] > of M`; an out-of-range number shows "1 to M" and disables Load. Loading a member
  names it `<set> #<i>`.
- Properties: "connected" is always yes (disconnected input is rejected). Krupp / NonKrupp count the
  triples of pairwise crossing circles.
- Intersection graph: 240 dp, vertex 1 on top, clockwise; "k of n(n-1)/2 pairs cross" above it.
- Layout settings are remembered like the other display settings. Fields apply while typing when the
  value is valid (max. iterations >= 1, eps > 0); there is no upper bound on max. iterations
  (**open point for Manfred**: a cap would avoid a long background run after a typo).
- Export dialog: format chips Sequences / Model 1 sparse6 / Model 2 sparse6 (model 1 disabled if not
  intersecting) instead of PoCoView's dropdown. Export SVG uses the drawing of the current
  arrangement and model (disabled while it is computed), 1000 x 1000, colours as on the canvas.
- About: website link, the EJC page of Linhart-Ortner, the two Felsner-Scheucher papers as plain
  citations (no DOI links, to avoid wrong links), build line, license texts. Feedback address and
  mailto as in PoCoView.
- The Welcome "Don't show again" label toggles the checkbox too (in PoCoView only the box does).
- Copy uses `LocalClipboardManager` like PoCoView; its deprecation warning stays visible (the new
  `LocalClipboard` needs platform-specific clip entries).

## 2026-11-17: Phase 3a, app shell and drawing

### Request (Manfred, via the phase plan)

- App shell ported from PoCoView: theme, menus (MenuButton, one open menu, one-shot press guard,
  Escape), split pane with draggable dividers, scrollbars, BigDialog, preferences, file open/save,
  `isWeb`; header in the menu bar (bold "ArrView", web-only subtitle "Arrangements of
  pseudocircles"); toolbar row with the status at the right (name, n, short message; info green,
  fades after 3 s); widget columns next to / below the canvas. Undo/Redo (Ctrl+Z / Ctrl+Y) for
  arrangement changes.
- Drawing: the iterated weighted Tutte embedding of draw7.sage / draw3.sage (paper: weights
  `p(A(f1)) + p(A(f2)) + q(|uv|/j)`, `p(x) = x^4`, `q(x) = x^2/10`, outer face on a regular
  polygon, iterate until the weight change is below eps or max iterations), plain Kotlin solver.
  Model 1 (primal graph, digons as touchings, intersecting only) and model 2 (vertex-edge-face
  graph, always). Default view model 1 if possible. Outer face: largest face (or draw7's maximal
  symmetry if cheap); right-click a face, "Make outer face". Smooth curves, one colour and a label
  per circle; triangles and digons filled; hover highlight. Layout off the UI thread, cancellable,
  never crashing.
- Click a triangle: flip; click a digon: collapse ("the arrangement would fall apart" if refused);
  undoable; re-layout keeping the outer face if it still exists. Zoom/pan as in PoCoView.
- Start with `Examples.start`. No "Loading..." anywhere; no text glyphs that need web fallback fonts.

### Decisions and interpretations

- **Weights: the sage code, not the paper's formula.** Taken literally (outer polygon on the unit
  circle, weights exactly `A(f1)^4 + A(f2)^4 + (|uv|/j)^2/10`), the paper's formula collapses the
  start example: after 10 iterations the smallest face has area 0 and the interior vertices end
  up in the corners of the outer polygon. draw7.sage converges (smallest / largest face area
  0.031 / 0.144 for the start example), so ArrView implements draw7.sage: plain Tutte embedding
  first (weights `1e-6`), then `w <- w + 0.1 (|uv|^2 - w) + c * sum (j A(f))^4` over the faces of
  `uv`, with `c = 1` in model 1 (draw7.sage) and `c = 0.001` in model 2 (draw3.sage). Stop when the
  normalized weight vector changes by less than eps. **To be confirmed by Manfred.**
- Settings (in `AppState.layoutSettings`, UI in phase 3b): max iterations 100 (as in sage), eps
  `1e-8` (draw3.sage; draw7.sage has `1e-10`). With these weights the change rarely drops below
  eps, so mostly all 100 iterations run: n = 6 takes 3 ms (model 1) / 21 ms (model 2) on the JVM,
  n = 14 13 / 118 ms, the largest example (Füredi–Palásti n = 42, model 2) 1.9 s.
- Safeguards not in sage: weights below `1e-12` times the largest are raised to it, non-finite ones
  become the largest; a solve with a non-finite result is dropped and the iteration stops. The
  linear system is solved by Jacobi-preconditioned conjugate gradients, warm-started from the
  previous embedding.
- **Outer face: largest face first, symmetry breaks ties.** A largest face (model 1: no digon), and
  among the largest ones the one with the most symmetries (automorphisms of the arrangement, mirror
  images included, mapping the face to itself; draw7.sage's criterion, computed on the
  combinatorial map, cheap), then the first. Maximal symmetry alone picks a triangle for the start
  example, which leaves large empty regions; the largest face (a quadrangle) looked better.
- **Curves: interpolating cubic Bezier pieces, not the paper's B-spline.** The paper takes a
  2-subdivision (points at distance `d(v)` <= 1/3 of the shortest edge at `v`) and a B-spline,
  which does not pass through the crossings. ArrView draws every arc between two crossings as one
  cubic Bezier piece through the crossings, so that the face fills and the hit tests follow the
  curves exactly. The tangent at a crossing is parallel to the line through its two neighbours on
  the circle and shortened by the turn there (factor `(1 + cos(turn)) / 2`, so sharp turns stay
  sharp, which keeps the curves inside the outer polygon); handles are a third of the chord. With
  short handles as in the paper (`d(v)`) the curves looked pinched and wavy. In model 2 the arc
  vertices only steer the tangents (curves through them zigzag), except on the sides of a digon,
  where the curve passes through them so the two sides stay apart (n = 2).
- Model 1 digons: both crossings of a digon are drawn at the middle of its (single) edge, both
  curves tangent to that edge, so they touch there; a small blue dot marks the touching point, a
  click within 10 px collapses the digon. Model 1 needs an intersecting arrangement with a face of
  at least 3 sides (not n = 2); otherwise model 2 is drawn and the Model 1 chip is disabled. The
  model chosen in the menu / toolbar is remembered (also across sessions); after a digon collapse
  the drawing falls back to model 2, and model 1 returns with undo.
- Labels: per circle at the middle of one of its longer pieces (at least half the longest),
  farthest from the centre, at least 0.15 times the drawing radius away from earlier labels.
- Colours: 12 distinct circle colours (cycled for n > 12); triangles light orange, digons light
  blue, other faces unfilled; the outer face is never filled.
- Interaction: left click on a bounded triangle flips it, on a digon collapses it, other faces do
  nothing; a click into the outer face does nothing (empty space is for panning). Right-click (or
  Ctrl+click on the Mac) on any face, the outer one included, opens its menu: "Flip triangle",
  "Collapse digon", "Make outer face" (not for the current outer face, not for digons in model 1).
- Outer face after a change: the face drawn outside before the change stays outside if it still
  exists (same sides: circle, crossing entry and direction of every side), otherwise the default.
  Undo / redo restore the arrangement with its outer face. The outer face choice itself is not an
  undo step (view only).
- An edited arrangement keeps its name with " (modified)" until it equals the loaded one again.
- The drawing is computed in a background job (one Tutte embedding per step, `yield()` in
  between, so the single-threaded web UI stays responsive); a change cancels it. The previous
  drawing stays until the new one is done; clicks on it are ignored while it shows another
  arrangement. The last 32 drawings are cached (undo / redo and model switches are instant).
- Menus (phase 3a): Arrangement (Open file..., Save file...), Edit (Undo, Redo), View (Fit, Zoom
  in / out, Model 1 / Model 2, widgets, layout auto / portrait / landscape). Examples and Help
  followed in phase 3b. Open file reads intersection sequences or a sparse6 line (model 1, else
  model 2); Save file writes the sequences (`<name>.txt`). Keys: Ctrl/Cmd+Z undo, Ctrl/Cmd+Shift+Z
  and Ctrl/Cmd+Y redo, + / - zoom, 0 / Home fit, Escape closes a menu.
- Toolbar: "Drawing" chips Model 1 / Model 2, "Widgets" chips, the status
  `name   |   n = 6   |   message`. Only ASCII in UI texts ("...", "|", "x"); the menu checkmarks
  are drawn paths.
- First widget: Properties (n, faces, p3, p2, non-intersecting pairs, class, p-vector), a minimal
  one so the widget columns work; phase 3b adds the other widgets.

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
