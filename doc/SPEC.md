# ArrView spec log

Requests and decisions. Newest first.

## 2026-11-24: Terms & privacy note (web)

Request (Manfred, relayed by turingmachine-ef): every GitHub Pages site gets the approved "Terms &
privacy" text, word for word the "Draft" section of misc/turingmachine/doc/privacy-github-pages.md,
with the "(Compose sites only)" paragraph but without that label. It goes in the About dialog, web
build only, as in ktoy. "Please let me know" points to the feedback e-mail.

Decisions:
- `ui/TermsPrivacy.kt`, the same in ArrView, PoCoView and RotSysView: heading "Terms & privacy",
  then "Privacy" (4 paragraphs) and "Terms" (1 paragraph, the draft's two lines joined, because
  "please let me know" is a link in it). Shown after the project links, before the build line.
- "privacy statement" links to GitHub's privacy statement, as in the draft; "please let me know"
  opens the same mailto as the feedback box.
- The CSP line in index.html stays (see DEVELOPMENT.md, "No third-party requests").
- Later the same day (Manfred): collapsed by default like the license texts, a TextButton
  "Terms & privacy" / "Hide terms & privacy" in place of the heading.

## 2026-11-24: Maximum number of digons

### Request (Manfred, via the coordinator)

New group "Maximum number of digons" in Combinatorial properties, after Maximum number of triangles
and before Digongraphs: all simple intersecting arrangements of n = 4..7 pseudocircles with the
maximum p_2 = 2n - 2 of digons (Grünbaum's conjecture, proved by Ackerman, Damásdi, Keszegh,
Pinchasi, Raffay, SoCG 2025, https://doi.org/10.4230/LIPIcs.SoCG.2025.2). Data:
`pca/data/maxdigons/n{n}_p2eq{2n-2}.txt`. n = 7 only if it adds at most about 1 MB.

### Done

- Group `max-digons` with the paper as its link; files `maxdigons/all{n}_{2n-2}_digons.json`
  (`n<N>-intersecting-<2n-2>-digons`, "n = 7, 12 digons (maximum)", "All intersecting arrangements of
  7 pseudocircles with the maximum of 12 digons."), written by `scripts/import_examples.py`
  (`PCA_SEQUENCES`, one arrangement per line; all other files unchanged). Sizes 1, 1, 13, 1743.
- n = 7 added: its file is 574 KB (34 KB gzipped); the bundled examples grow from about 0.73 MB to 1.31 MB.
- Tests: sizes, digon count 2n - 2 and intersecting for every member, and for n = 4, 5 the sets are
  exactly the members of `n<N>-intersecting-all` with the most digons (which is 2n - 2).
- Interpretation: the n = 4, 5 sets (one arrangement each) also say "All ...", as the max-triangles
  n = 5 one with 13 triangles.

## 2026-11-24: Examples in supergroups

### Request (Manfred, via the coordinator; the same in PoCoView and RotSysView)

Restructure the examples into a two-level tree of supergroups and groups, uniform with PoCoView
(bab3601) and RotSysView (9d12683). The database after Table 1 of Felsner, Scheucher, "Arrangements
of Pseudocircles: On Circularizability" (https://arxiv.org/abs/1712.02149), with the connected
digon-free arrangements n = 3..5 as a new group (Table 1: 1, 3, 30). Examples may appear in several
groups.

### Tree

- Database (links: the website's database page, On Circularizability): Great pseudocircles
  (n = 3..8), Intersecting, digon-free (3..6), Intersecting (2..5), Connected, digon-free (3..5, new),
  Connected (3..5).
- Combinatorial properties: Minimum number of triangles, Maximum number of triangles, Digongraphs
  (Maximum number of digons follows later: one more group in `index.json`).
- Non-circularizable (links: No more circle representations, On Circularizability): n = 5 (connected,
  intersecting) with the four N_5; n = 6, digon-free intersecting (no. 1 = N_6^Delta, 2, 3); n = 6,
  intersecting with digons (every-triangle-Krupp, Edelsbrunner–Ramos, symmetry 6 no. 2, 3); n = 6,
  connected digon-free (symmetry 24, 8 no. 1, 8 no. 2, 4 no. 1, 4 no. 2; the file `..._sym8_2` is
  no. 1); n = 9, great (non-Pappus).
- Constructions: Füredi–Palásti, Triangle-saturated arrangements and colourings.

### Schema

PoCoView's `index.json`: `{"supergroups": [{id, title, description?, links?, groups: [{id, title,
description?, links?, files}]}], "links"?}`. Supergroup ids unique, group ids unique among all
groups, not only within their supergroup (`Examples.group(id)` looks a group up by its id alone); the
loader rejects duplicates and files that are not bundled. Kept group ids: min-triangles, max-triangles, digon-graphs,
furedi-palasti, colorings; new: great, intersecting-digonfree, intersecting, connected-digonfree,
connected, nonr-5, nonr-6-digonfree, nonr-6-intersecting, nonr-6-connected, nonr-9-great; supergroups
database, combinatorial, non-circularizable, constructions. Details in DEVELOPMENT.md.

### UI decisions (uniform for the three apps)

- Two-level tree: supergroup headers at depth 0, groups at depth 1, sets at depth 2, 16 dp per level.
- Supergroup header: drawn arrow, SemiBold, titleLarge, title only; when open its description
  (bodySmall) and links, then its groups.
- Group header: bodyLarge SemiBold, title only, no count (795b004); when open its description and its
  links, unless their URLs are exactly the supergroup's (compared as sets of URLs), then its sets. A
  set shows only the links whose URL its group and supergroup do not show already.
- Leaves unchanged: label, plus " (size)" for more than one member.
- Everything collapsed at first; open nodes, selection and scroll position kept while the app runs.
  Collapsing the supergroup or group that holds the selection clears it (Load disabled); collapsing
  others keeps it.

### Decisions and interpretations

- Links: on the supergroup when all its groups share them (Database, Non-circularizable); Combinatorial
  properties and Constructions have none, their groups keep their own. A group's links never repeat
  its supergroup's (`ExamplesTest`); a set shows only links its group and supergroup do not show.
- Descriptions are optional. Database groups got one short definition each; the Non-circularizable
  groups none (the title says it); Combinatorial properties and Constructions none.
- Group titles are the tree's names: "Füredi–Palásti" (was "Füredi–Palásti construction"). The
  supergroup is "Non-circularizable" (was the group "Non-circularizable arrangements"). The ranges
  n = 3-8 etc. are not in the titles (the leaves say n).
- A file's `"group"` key now names a group or supergroup that lists it: database files keep
  "Database", the non-circularizable ones say "Non-circularizable", Füredi–Palásti "Füredi–Palásti".
- A file in several groups is one leaf per group (its own selection); `Examples.sets` has each file once.
- Connected, digon-free: `connected/all{3,4,5}_digonfree.json` (`n<N>-connected-digonfree-all`),
  the digon-free lines of the connected files, written by `scripts/import_examples.py`
  (`DIGON_FREE_MEMBERS`). Sizes 1, 3, 30 as in Table 1; `subclassesAgree` checks they are exactly the
  digon-free connected ones.
- `doc/img/examples.png` is RenderTest's `dialog-examples-tree.png` (every supergroup open).

## 2026-11-24: Examples cleanup

### Request (Manfred, via the coordinator)

Fixes to the example catalogue from a critical review (texts, labels, links, Koester's file name).

### Done

- Triangle-saturated means every edge lies on exactly one triangle (Chiu et al.), not every crossing
  on a triangle: group description fixed; `triangleSaturatedColouringsAreProper` checks the edge
  property, `koesterHasSevenCirclesAndOneNonCrossingPair` that Koester's has 5 edges on no triangle.
- Labels "n = N, <class>, <what>" in Non-circularizable and the colourings ("n = 5, connected,
  no. 2", "n = 9, great, non-Pappus", "n = 16, great, Simmons"). The website's "additional" files
  are named by symmetry as in the circularizability paper (N_6^{i6:k}, N_6^{c4:k}, N_6^{c8:k},
  N_6^{c24}): "n = 6, intersecting, symmetry 6, no. 2", "n = 6, connected digon-free, sym. 8,
  no. 1" (45-character limit). The paper's figure files carry the website's names, and the paper
  shows N_6^{c8:1} in `misc_con6_sym8_2` and N_6^{c8:2} in `misc_con6_sym8_1` (checked by the
  non-crossing pairs in the figures and the data: 1 and 2), so `n6_connected_digonfree_sym8_2` is
  "sym. 8, no. 1" and `..._sym8_1` is "no. 2". Order unchanged.
- Descriptions: maxima for n = 5, 6, 7 ("with the maximum of ..."; n = 7: all three digon-free);
  n = 8 minimum (the first below Grünbaum's 2n - 4, Triangles and Drawings Sec. 2); N_5^2 (Linhart
  and Ortner 2005); non-Pappus (the only non-circularizable one up to 9 great pseudocircles);
  every-triangle-Krupp (the other one besides N_6^2, which now says so too); the 6-circle minimum
  and N_6^Delta point to each other; n = 6 great no. 1 has the maximum 20 triangles.
- Füredi–Palásti group: 2n(n-3)/3 triangles, great pseudocircles doubled from the line
  arrangements; link to the Füredi–Palásti paper. Digongraphs: link to On Digons and Triangles.
  Koester: link to Koester's paper.
- Links: great/* carry the database page like the other database sets; the n = 8, 9, 10 maxima
  from the computer search carry the Triangles and Drawings paper (no website page).
- Spelling: the app says "colour", so the group title is now "Triangle-saturated arrangements
  and colourings" (link titles keep the website's and paper's spelling).
- `colorings/n8_koester.json` is now `n7_koester.json` (explicit stem in `import_examples.py`).

## 2026-11-21: Fixes from the manual walkthrough

### Request (Manfred)

"ja bitte" to the list of issues found while writing the manual; item 9 for consistency with PoCoView;
items 10 and 11 ("jo"), also in PoCoView and RotSysView.

### Done

1. Example groups are ordered by n: "Maximum number of triangles" had n = 8, 38 after n = 9, 48;
   "Triangle-saturated ..." had Koester (n = 7) last. The database stays ordered by class, then n.
   `ExampleCatalogTest.groupsAreOrderedByN`.
2. "n = 5, 13 triangles, with digons", triple 2 4 5: not a bug. It is drawn, but each of its sides is
   also a side of another triple (2-4 of 1 2 4, 4-5 of 1 4 5, 2-5 of 2 3 5), so it only shows as a
   darker fill; pointing at the list entry outlines it.
3. Opening or closing any dialog (also the model 1 / model 2 notices, "Save changes?", the sets
   notice and the error dialog) clears the canvas hover (and the outer face's "!"). Not reproduced in
   headless Chrome or the JVM (a dialog opening already sent the canvas an Exit there); the clear on
   close covers a hover left over while a dialog is open. `CanvasInteractionUiTest.aDialogClearsTheHover`.
4. The model 1 editor of the Encoding widget is hidden while the arrangement is not intersecting (the
   note stays), as model 1 elsewhere (drawing falls back to model 2, Export disables the chip).
5. "Don't show again" in every dialog (was "Do not show again" in the model dialogs).
6. Links: https://scheucher.dev/ArrView/ (README, user guide, DEVELOPMENT.md, deploy script).
7. Help General: "The Edit menu has Undo and Redo; Make outer face can be undone like a flip."
8. Help Visualization: the dual and primal-dual figures draw the outer cell's vertex outside the
   drawing, with its edges routed around it without crossings (each on its own circle, the farther
   from the vertex the larger), instead of stubs. Captions say so. `HelpFiguresTest`.
9. Examples dialog: collapsing the group that holds the selection clears it, so Load is off;
   collapsing another group keeps it (PoCoView's `ExamplesState.toggle` / `treeKeys`).
   `ExamplesDialogUiTest.collapsingTheSelectionsGroupDisablesLoad`.
10. Desktop save (Save file and Export SVG): an existing file is replaced only after "<name> already
    exists. Replace it?" (Yes / No); No shows the file chooser again (`chooseSaveFile`, `SaveFileTest`).
11. Menu items that open a dialog end in "...": Export SVG..., the Help pages (General..., ...) and
    About ArrView... (as in RotSysView); Help, user guide and tests follow.

### Claude's choices

- 4: a model 1 edit made while intersecting is kept but hidden, does not count as unsaved while hidden,
  and comes back when the arrangement is intersecting again (undo, but also any other change to an
  intersecting arrangement, e.g. an applied sequences edit). Load and set switches drop it without
  "Save changes?". Dropping it as soon as it is hidden would lose the typed text. Confirm?
- 8: the outer vertex sits in the widest angular gap between the outer arcs; edges have rounded corners.

## 2026-11-21: Keyboard shortcuts, line width 5 px

### Request (Manfred)

"shortcuts vlt bei allen? ctrl+z undo und so standard zeugs? ctrl s speichern" (all apps, done
first in RotSysView) and "denk bei arrview sind auch 5 px default, oder sollten zumindest sein".

### Decisions

- Ctrl+O = Arrangement > Open file... (with the unsaved-changes question, as the menu), Ctrl+S =
  Save file...; Cmd works like Ctrl. Both also work while a text field has focus, so the browser
  does not open its own Open or Save page. Ctrl+Z, Ctrl+Shift+Z, Ctrl+Y existed.
- Menu entries show their key on the right (Ctrl+O, Ctrl+S, Ctrl+Z, Ctrl+Shift+Z, 0, +, −).
- Ported literally from RotSysView (FileKeys / handleFileKey in App.kt, MenuItem(shortcut)).
- Line width default 5 px (was 4).

## 2026-11-20: SVG export

### Request (Manfred)

After "Export SVG does not draw the crossing colours": "jo mach bitte svg export sinnvoll".
Read as: the SVG shows the drawing as the canvas shows it, without UI marks (hover, highlights, "!").

### Decisions

- Export SVG draws the coloured crossings as the canvas does (disc radius 5 on a black rim of
  radius 6.5, same positions via `coloredCrossings`), while they are shown (unedited member).
- Compared canvas and SVG: faces, curves (scheme, line width), model, outer face, touching points
  and labels already matched; the crossing colours were the only drawing difference. There is no
  other picture export (no Ipe); the Export dialog's text formats (sequences, sparse6) carry no
  colours, Save file does (`"crossingColors"`).
- `ExportTest.svgShowsTheCrossingColours`: the 6 coloured examples, one disc per crossing in its colour.

## 2026-11-20: Refit on resize, sequences (i), examples as JSON, coloured crossings

### Request (Manfred)

Refit on resize "sounds reasonable"; the sequences (i) text: yes (sign from the circles'
orientation, +j: circle j crosses circle i from the right of i to its left); colours only for the 6
coloured examples, compare PoCoView's coloured point sets; "json should become standard ... every
arrangement a json string, analogous to PoCoView with metadata, the json string is just one
attribute, maybe there are more".

### Decisions

1. Refit on resize, PoCoView's semantics (its commit 08889e1): every canvas size change (window,
   widget opened or closed, divider, portrait) refits the drawing, unless the user zoomed or panned
   since the last fit (wheel, pinch, two-finger pan, dragging, +/- keys, View zoom; all through
   `ViewTransform.zoomAt` / `pan`); then the view keeps its centre. Every fit turns it back on: Fit
   (menu, Home, 0), loading (example, file, Import), switching set members, and Apply in the
   Encoding widget, which now fits like PoCoView's Apply. Flips, collapses, creates, undo and redo
   never count and never refit. `RefitOnResizeUiTest`.
2. Sequences (i): "List i gives the crossings along circle i in its orientation. +j: circle j
   crosses circle i from the right of i to its left; -j: the other way." (Confirms the open point of
   2026-11-19.)
3. Examples as JSON (PoCoView's example format): `composeApp/examples/index.json` lists the groups
   (id, title, description, links, files); one JSON file per set, metadata first (name, n, group,
   label, description, links, source), then the data. The encoding is one attribute: `"mod1s6"`,
   `"mod2s6"` (sparse6 strings as on the website) or `"sequences"`; a set with several arrangements
   has `"arrangements": [{"mod1s6": ...}, ...]`, one object per line. The catalogue moved from
   `Examples.kt` into these files. Folders and file stems stay (`connected/all5.json`,
   `colorings/n6_triangle_sat_no2.json`, ...).
   `scripts/import_examples.py` rewrites the data and keeps the metadata of each file; it
   reproduces the converted files byte for byte.
4. App files: Save writes `{"name", "n", "sequences"}` (plus `"crossingColors"`), a set as the
   example files do. Open file and Import read that, `"mod1s6"` / `"mod2s6"` instead of
   `"sequences"`, one JSON object per line (PoCoView's set format), the older JSON list, and all
   plain formats as before. Other keys are ignored; a wrong `"n"` is an error.
5. Coloured crossings: `"crossingColors"`, one colour word per entry of the sequences, PoCoView's
   tokens R, G, B with PoCoView's colours (`tokenColor`; other words draw nothing). Only the 6
   triangle-saturated examples have them. They are drawn while the shown member is as loaded (an
   edit removes them, undo brings them back), also for files and imports that carry them. No
   toggle, as PoCoView has none for coloured point sets.

### Claude's choices

- Singletons are flat objects (as PoCoView's examples), sets wrap the members in `"arrangements"`
  instead of PoCoView's one object per line, so the set's metadata is not repeated per member.
- Link titles carry the kind ("Website: ...", "Paper: ..."); a set shows only links its group
  does not show.
- `"crossingColors"` (not `"colors"`, which is PoCoView's number of colours).

### Decided later

- Set-level metadata (members of large sets carry only their encoding) is fine. Manfred: "so wie
  dus für sinnvoll empfindest passt scho".
- RotSysView gets the whole outer-face handling too (Manfred: "glaub rotsysview braucht auch das
  ganze outer face zeugs jo"); handed to the RotSysView session.

### Open questions

- File names keep the old stems (`colorings/n8_koester.json` holds `n7-connected-koester`); PoCoView
  names files after the example. Rename? Done 2026-11-24: `colorings/n7_koester.json`.
- PoCoView's `"reference"` and `"colors"` (count) keys are not used. Add `"colors": 3` to the
  coloured files?
- Export SVG does not draw the crossing colours (it did not before either). Done, see "SVG export".

## 2026-11-20: Maximum triangles for n = 8 and 9

### Request (Manfred)

"gib das 9er bitte bei arrview in die examples dazu, das 8er müsst eh scho drin sein" (add the n = 9
maximum to the examples; the n = 8 one was thought to be in already, but it was not, so both were added).

### Done

- Group "Maximum number of triangles", after the website's sets: `n8-intersecting-38-triangles`
  ("n = 8, 38 triangles, with digons", the unique intersecting arrangement of 8 pseudocircles with
  the maximum number 38 of triangles) and `n9-digonfree-50-triangles` ("n = 9, 50 triangles", the
  unique one of 9 with 50, digon-free), both by computer search. Data: `pca/data/maxtri/n8_p3eq38.txt`
  and `n9_p3eq50.txt`, copied as `maxtriangles/max8_digon_38_triangles.seq` and
  `max9_df_50_triangles.seq` (also by `scripts/import_examples.py`). Not on the website: no page,
  no paper of their own.
- `n10-digonfree-61-triangles` ("n = 10, 61 triangles"): an arrangement of 10 pseudocircles with the
  maximum 61 (complete search for p_3 >= 62 found none, 2026-11-21); not known whether it is unique,
  hence `some10_`. Data: `pca/data/maxtri/n10_p3ge61_partial.txt`, copied as
  `maxtriangles/some10_df_61_triangles.json` by `scripts/import_examples.py`.
- The group description now says the website's examples for n = 8, 9 are digon-free and not the
  maximum; the website's sets `some8_df_37` and `some9_df_48` no longer say "(maybe the maximum)".
- `ExampleCatalogTest` checks 38 triangles with a digon, 50 and 61 triangles digon-free, and that the
  website's n = 8, 9 sets stay below.

## 2026-11-19: UI overhaul

### Request (Manfred)

UI as slim as possible but understandable. Model 2 is the default and is chosen only in Settings;
model 1 is legacy and asks first; widgets renamed and merged; explanations into (i) popovers; a Help
page on the three graphs; a limit of open widgets; PoCoView's notice for files with several
arrangements; a note instead of a crash when the browser has no WebGL.

### Decisions (as implemented)

1. Model 2 is the default (`AppState.preferredModel`, Reset). The toolbar's "Drawing: Model 1 /
   Model 2" chips and the View menu's model items are gone. Settings, Layout: dropdown
   "Visualization" with "Model 2: primal-dual graph" and "Model 1: primal graph (legacy)"
   (`DrawModel.option`); the (i) next to it opens Help > Visualization. The toolbar label "Widgets"
   stays.
2. Choosing model 1 (`AppState.chooseModel`): dialog "Model 1", "Model 1 is the legacy view. It can
   only show intersecting arrangements.", "Do not show again", OK / Cancel; OK switches. Not
   intersecting (`hasModel1` false: some pair does not cross, or n = 2): the dialog always shows,
   "Model 1 can only show intersecting arrangements. This arrangement is not intersecting.", only OK,
   model 2 stays. Arrangements with digons are intersecting and allowed.
3. Whenever model 1 is chosen and the next arrangement has no model 1 (digon collapse, load, import,
   open file, example, set step, undo, redo, applied text): dialog "The arrangement is no longer
   intersecting. Switching to model 2." with "Do not show again" and OK; the change waits for OK,
   then model 2 is chosen (stays chosen, also after undo) and the change is done; suppressed:
   silently. Both flags are stored with the other settings (`model1Confirm`, `model1Fallback`, "off"
   once ticked; localStorage on the web, Java preferences on the desktop).
4. Widgets "Triangle hypergraph" -> "Triangles", "Intersection graph" -> "Intersections" (chips,
   titles, View menu, Help, user guide, tests; `Widget.TRIANGLES`, `Widget.INTERSECTIONS`). Stored ids
   of the old names still open the new widgets.
5. "Sequences" merged into "Encoding": sequences, then model 1 sparse6, then model 2 sparse6, each
   heading with an (i) (`SEQUENCES_INFO`, `MODEL1_INFO`, `MODEL2_INFO`), checked against the code and
   the website's information page (see "Decisions to confirm").
6. Help page "Visualization" (Help menu after General, and the (i) of Visualization): the primal,
   dual and primal-dual graph, each a figure drawn in the app (`HelpFigures.kt`: the start example
   n6-digonfree-nonr-8-triangles in model 2 with faint curves and the graph on top: primal = crossings
   and arcs; dual = a dot per cell, an edge through every arc between its two cells; primal-dual =
   crossing (black), arc (green) and cell (blue) vertices with their incidence edges), then one
   paragraph each on model 1, model 2 and the iteration, then the two Felsner-Scheucher papers and the
   website's information page.
7. Settings: the Layout note is an (i) after the heading (`LAYOUT_INFO`: two sentences and the
   papers). eps is no longer in the GUI and no longer stored (stays in `LayoutSettings` with its
   default). Max. iterations stays. No "Implementation" dropdown.
8. At most 2 widgets are open by default; opening one more closes the least recently clicked one
   (a press anywhere in a widget column or opening it counts), ported from PoCoView
   (`AppState.maxColumns`, `touchWidget`). Settings, group "Widgets": "Max. widgets: 2" with - / +
   (1 .. number of widgets), PoCoView's wording; lowering it closes the least recently clicked; stored as
   `maxWidgets`; Reset sets 2.
9. GUI texts: the Layout note and the Encoding explanations went into (i) popovers (`InfoButton`);
   Help General's model paragraph became two sentences pointing to Help > Visualization; Help >
   Widgets lost the model description and eps in its Settings paragraph; "Save changes?" names the
   Encoding widget; Help General's examples sentence describes the examples by purpose. Toolbar,
   status line and the other dialogs had no explanation longer than one short sentence (the Welcome
   text is Manfred's wording, unchanged).
10. Sets notice (PoCoView's `SetsLoadedDialog`, ported): after loading a file or example set with
   several arrangements: "k arrangements loaded", "Showing the first of k. Switch between them with
   the arrows < > at the top (or PageUp / PageDown)." ("arrangement i" when another member is shown),
   "Don't show again" (stored `setsNotice`), OK.
11. Web (Chrome crash "reading 'getParameter'": Skiko gets no WebGL context, e.g. hardware
   acceleration off; reproduced with `--disable-webgl`): `index.html` tests
   `getContext('webgl2') || getContext('webgl')` before it loads the app; without WebGL it shows
   "ArrView needs WebGL. Turn on hardware acceleration in the browser settings (Chrome: Settings ->
   System -> Use graphics acceleration) and reload." The error handler maps the getParameter crash to
   the same note. Same change in PoCoView, ktoy, ksat-chess and the ksat-extra playground.

### Decisions to confirm

- The sequences (i): the draft said "the outer face fixes the orientation; + entered, - left". In
  the code the sign belongs to the circles' own orientation (from the input), not to the outer face:
  +j means circle j crosses from the right of circle i to its left. The (i) says that.
- The model 2 (i) says what the code writes: arc-crossing and arc-cell edges (no crossing-cell
  edges) and a leaf at every cell vertex. The model 1 (i) points to the website's information page.
- The switch-to-model-2 notice is titled "Model 2"; a click outside it counts as OK (it has no
  Cancel); a click outside the model 1 dialog counts as Cancel.
- The WebGL probe accepts webgl as well as webgl2 (as asked); Skiko asks for webgl2 only, so a
  browser with WebGL 1 only still crashes, then the error handler shows the note.
- Checkbox labels: "Do not show again" in the model dialogs (Manfred's wording), "Don't show again" in
  the Welcome dialog and the sets notice (as before / as in PoCoView). Unify? Done 2026-11-21: "Don't
  show again" everywhere.
- Not done (reviewer suggestions, not asked for): clickable links for the papers and the information
  page on Help > Visualization; merging the duplicate digon-create paragraphs of Help General.
- The figures leave out the outer cell's vertex (dual and primal-dual: stubs), as the model 2
  drawing does. Changed 2026-11-21: the vertex is drawn outside, its edges go around.

## 2026-11-19: Examples by purpose

### Request (Manfred, via the coordinator)

Reorganise the Examples by purpose, following the menu of the pseudocircles website: database,
minimum / maximum number of triangles, Füredi–Palásti, non-circularizable (`?show=nomorecircles`),
triangle-saturated arrangements and colourings (`?show=colorings`), Koester's arrangement
(`computations/biconnected_results/n8_koester.enc`, paper arXiv:2205.08181 Fig. 8b) and any other
group with data. Every group gets one or two sentences and links (website page, paper); every
example a short label understandable before expanding and a description after. Show the vertex
colouring of the triangle-saturated arrangements if the data exists, do not invent colourings.

### Decisions

- One level of groups, each with its sets as leaves (the old "Sets" / "Singletons" nodes are gone).
  An open group starts with its description and links; the selected set keeps its info area
  (description, n, count, member chooser, links, data path) as before.
- Groups, in the order of the website's menu: Database (17: connected n = 3..5, intersecting
  2..5, digon-free 3..6, great 3..8), Minimum number of triangles (12), Maximum number of triangles
  (7), Füredi–Palásti construction (7), Non-circularizable arrangements (17), Triangle-saturated
  arrangements and colorings (7), Digongraphs (6), and last "Krupp, NonKrupp, 3-Chain" (3, typed
  in; linked to the website's information page, which uses the Krupp as its example).
- New data: the website's additional non-circularizable n = 6 arrangements (`n6_sym6_2`, `n6_sym6_3`
  and the five `n6_connected_digonfree_sym*`; `n6_edelsbrunner_nonr` stays out, it is isomorphic to
  the bundled Edelsbrunner–Ramos file), the six digongraph files, the six triangle-saturated
  arrangements and Koester's. All-triangles-Krupp and Pappus (from the computations, not on the
  website) moved into Non-circularizable, without a website link.
- Colourings: the website's drawings (`*.enc_1_1tutte7.ipe`, draw7 with coloured crossing marks,
  classes red / green / blue) are the only colouring data. `scripts/import_examples.py` reads the
  curves and marks and writes the sequences (`.seq`) and the class of every crossing entry
  (`.colours`); a test checks each against the website's `.enc` (isomorphic), that the colouring
  is proper, the same at both entries of a crossing, and that every crossing is on a triangle.
  The canvas draws a filled disc at every crossing in its colour when the shown arrangement is
  exactly a stored coloured example (after an edit, no discs). No toggle.
- Koester's arrangement: no colouring data on disk (`n8_koester.enc.chi4+.txt` is empty; it needs 4
  colours anyway). The file is the sparse6 of the crossing graph (40 vertices, 4-regular), neither
  model 1 nor model 2; `scripts/crossing_graph_sequences.sage` converts it. It has 7 circles (one
  pair does not cross), as the paper says; the file name says n8. It goes into the colourings group.
- Paper of the colourings group: arXiv:2205.08181, title as on arXiv ("Coloring circle
  arrangements: New 4-chromatic planar graphs"). The website names it "New 4-Vertex-Critical Planar
  Graphs" and links arXiv:1712.02149 (the circularizability paper) by mistake.
- Digongraphs: the page's paper link is "arxiv.org/abs/todo", so the group links only the page. The
  files are model 1 (dual graphs) although the page says primal-dual graphs.
- Not bundled: connected n = 6 and intersecting n = 6 (too large), biconnected n = 5 and the circle /
  ellipse representations (coordinates, which ArrView does not use).

### Interpretations (to confirm)

- "Names as on the website": group titles and terms as on the website (e.g. "colorings",
  "Digongraphs"); labels readable instead of file names, the file is under "Data:".
- Labels, after a fresh reviewer compared with ksat-chess and ktoy ("too little vs too much"):
  group headers without counts (they counted sets, the leaves count arrangements); leaves
  "n = N, ..." with commas ("n = 7, 10 triangles", "n = 5, no. 2", "n = 7, Koester"), the class
  first in the database ("Intersecting, n = 5 (278)"), the count of a list in parentheses. The
  website's `sym*` files are "additional no. k" as in the page's section titles (Claude does not
  know what "sym6" means exactly). Paper links short: "Triangles and Drawings (Felsner, Scheucher)".
- The selected set shows its website / paper links only where they differ from its group's (the
  group intro shows them once, right above); no extra "crossings coloured" line (the group says it).

## 2026-11-19: Layout implementation: classic / optimized

### Request (Manfred)

Find the right weights faster than the classic weight update: an extrapolation / acceleration
scheme on the weight history, so fewer iterations are needed; "classic" and "optimized" as a
dropdown. Condition (coordinator): the optimized picture must agree with classic (100 iterations,
eps 1e-10) within 1e-3 of the drawing radius on the bundled examples and a sample of n = 3..5.

### Result: not shipped, classic stays

Tried Anderson acceleration (memory 3, 5, 8; on the weights and on their logarithms) with the
face factor `j` as in sage, frozen at 50 or 100, and the "limit" map (area term only). Probe:
`scripts/probe/layout_accel/` (`tutte_anderson.patch` on `core/Tutte.kt`, then `run.sh`;
`run.sh limit` for the long runs). 210 cases (all bundled sets first/last member, every 6th
member of the n = 3..5 sweep sets, both models). Largest deviation from classic 100 / 1e-10:

| run | mean iterations | worst deviation (of the radius) |
|---|---|---|
| classic, 50 it, eps 1e-8 (the default) | 35.9 | 1.6e-1 |
| Anderson, true `j` | 40.4 | 1.05 |
| Anderson, `j` = 100 | 24.8 | 2.2e-1 |
| Anderson, `j` = 50 | 24.1 | 2.6e-1 |
| Anderson, `j` = 100, memory 3 | 21.8 | 2.3e-1 |
| Anderson, `j` = 100, not in logs | 24.4 | 4.3e-1 |
| Anderson, area term only (the limit) | 22.6 | 6.8e-1 |

No scheme meets 1e-3; neither does the classic default itself. Reason: with the face term
`(j A)^4` the classic iteration is not a fixed-point iteration whose result is reached early. It
drifts towards a limit `L` (`j -> infinity`: the weights point along the area term) and the error
shrinks only like `1/j^4`. Classic with eps 0, deviation from 1000 iterations:

| case | 50 it | 100 it | 200 it | 400 it | classic 100 / 1e-10 stops at |
|---|---|---|---|---|---|
| start example, model 1 | 1.2e-5 | 6.8e-7 | 3.9e-8 | 2.3e-9 | 80 |
| start example, model 2 | 1.2e-2 | 8.1e-4 | 4.4e-5 | 2.5e-6 | 100 |
| Pappus, model 2 | 4.9e-2 | 7.1e-3 | 4.4e-4 | 2.5e-5 | 100 |
| n5-intersecting-all #1, model 2 | 4.2e-3 | 2.5e-4 | 1.4e-5 | 8.4e-7 | 94 |
| n5-connected-all #1, model 1 | 6.8e-7 | 3.6e-8 | 2.0e-9 | 1.2e-10 | 51 |

So the classic picture is "the trajectory where it stopped", and that point depends on the
iteration count and eps. An accelerated scheme can land on `L` (Anderson on the limit map reaches
`L` to 1e-8 in 39 to 88 embeddings in 4 of the 5 cases above, vs. hundreds for classic), but not
on an arbitrary point of the slow trajectory; on Pappus it went to a different picture (1.3).

**Question for Manfred:** which picture is "right": the classic one at 50 / 100 iterations
(then there is nothing to accelerate without changing the picture), or the limit `L` (then
"optimized" = a fast solver for `L`, with its own checks, giving pictures that differ visibly from
the current 50-iteration default in model 2)? Nothing changed in the app; no dropdown added.

## 2026-11-19: Curves and layout ported literally, 50 iterations, sets

### Request (Manfred)

The curves were buggy and self-intersecting (the Krupp, n3-intersecting-all #1 with a triangle
flipped: in model 1 the blue curve was a figure 8), and the curve construction was not his
original code. Port the original B-splines and the layout literally from
`program_py3/draw7.sage` (model 1) and `model2_program/draw3.sage` (model 2), no own schemes, no own
safeguards that change the result; in model 2 the start example's outer cell was "a 4-gon and
weirdly rotated". Max. iterations default 50. Colour schemes only Standard, rainbow and rainbow2.
Undo covers "Make outer face". Navigation through files and example sets with several
arrangements as in PoCoView. A big test: every intersecting and connected arrangement with
n = 3, 4, 5 and every single flip the app offers, valid by Linhart-Ortner and drawn cleanly.

### What deviated before, what is ported now

- Curves: before, one cubic Bezier piece per arc through the crossings with ArrView's own tangent
  rule. Now the B-spline of `graph_2_ipe` (draw7.sage 54-81, draw3.sage 71-100): per circle a closed
  uniform cubic B-spline (Ipe's `u`), control points `xl, v1, xr` per vertex `v1` of the circle,
  `xl = v1 + l0 (v0 - v1)`, `l0 = sqrt(min(distances[v1]) / |v0 - v1|^2) / 3` (`xr` with `v2`),
  `distances[v]` the squared lengths of all circle edges at `v`. Vertex sequences as
  `compute_pseudocircles`: model 1 the crossings of the circle, model 2 crossings and arc vertices
  alternately. Converted exactly to Bezier pieces (`(P0 + 4P1 + P2) / 6, (2P1 + P2) / 3, (P1 + 2P2) / 3,
  (P1 + 4P2 + P3) / 6`), so canvas, SVG, hit tests, labels and face fills use the same curve.
  Faces are bounded by the curve pieces between the geometric crossings (found near each crossing
  vertex); labels sit on the middle of a longer such piece.
- Weights: before `0.1 (|uv|^2 - w)` and the polygon area of the bounded faces only, plus guards
  for tiny weights. Now as sage: `w + 0.1 (dist2^2 - w)` (`mypoly(dist2)`, `mypoly(x) = x^2`), plus
  `(it * A(f))^4` (model 2: times 0.001) for both faces of the edge, `A` the convex hull area
  (scipy's `ConvexHull(...).volume`), the outer face included. The stop vector runs over the edges
  of `G` (model 1: a digon's double edge counts twice). Guards removed.
- Outer face: before "a largest face, symmetry breaks ties". Now draw7.sage 304-327 / draw3.sage
  172-191: the face with the most automorphisms (model 1: of `G2` with pendant edges at the face,
  model 2: of the vertex-edge-face graph without the face vertex). Counted as map automorphisms;
  `SymmetryTest` checks them against a plain graph automorphism count (all n <= 4, every 25th of
  n = 5, the start example, n = 2).
  Changed 2026-11-20 (Manfred: "2 kriterien in serie: zuerst kandidaten für outer cells die maximale
  symmetrie, und dann davon den größten", since a larger outer cell gives a rounder drawing that uses
  more of the area): among the most symmetric faces the one with the most sides, the first of those.
  sage takes the first most symmetric face in its own face order; the draw7 comparison
  (`Draw7ComparisonDump`) passes each most symmetric face explicitly, so it is unaffected.
- The start example in model 2: its triangles have 3 symmetries, its quadrangles 1, so draw3.sage
  draws a triangle outside. Before, ArrView drew a quadrangle outside: in model 2 its polygon has 8
  corners (crossings and arc vertices alternate), `a0 = pi/8 + pi/2`, so the four crossings sit
  22.5 degrees off the axes, the tilted square Manfred saw. draw3.sage would draw a 4-gon outer face
  the same way.
- Unchanged, as in the sage code: outer polygon `a0 = pi/l + pi/2`, `ai = a0 + 2 pi i / l` on the
  unit circle; first iteration with weights `1e-6`; the stop test before the embedding.
- Solver: the warm-started conjugate gradients stay (sage: `M.pseudoinverse() * b`).
  `TutteSolveTest` compares them with Gaussian elimination on sage's matrix after every iteration
  (up to Pappus, model 2, 290 vertices): equal to 1e-9 of the drawing size. For that the CG
  tolerance went from 1e-10 to 1e-13.

### Remaining deviations (with reason)

- Model 1 with digons, as draw7.sage draws them (Manfred: "touchings sind dort"; the py2 original
  `program/draw7.sage` has no `assert(2 not in deg)`, only the py3 copy has it): `G` is the
  multigraph with the digon's two edges, `G2` the simple graph (a digon is one edge), and both
  circles' B-splines run through the digon's two crossings, so they share a straight piece (the
  span `v1, xr, xl, v2` is the same for both). Checked against the original, see below.
- eps: one setting, default 1e-8 (draw3.sage). draw7.sage stops at 1e-10. **Question for Manfred:**
  per-model defaults (1e-10 model 1, 1e-8 model 2)?
- Max. iterations 50 (Manfred; sage: 100).
- Ties of the symmetry: sage takes the first face in its own face order (draw3: vertex names
  sorted as strings), which cannot be reproduced; ArrView takes the first in its face order.
  Likewise the corner where the outer polygon starts and its direction (sage's face lists) only
  rotate the drawing by a multiple of `2 pi / l` or mirror it; ArrView keeps the arrangement's
  orientation.
- Graph automorphisms are counted as map automorphisms, equal for 3-connected graphs (Whitney);
  checked against a plain count on the cases above, not proven for all.
- A solve with a non-finite result stops the iteration (keeps the last positions); sage has no
  such check (it would draw NaN). Only a crash guard.
- Face fills: draw7.sage fills triangles as straight triangles of the crossings; ArrView fills
  triangles and digons along the curves (Manfred: the same curve everywhere).

### The sweep (`DrawingSweepTest`, `DrawingFullSweepTest`)

Every intersecting and connected arrangement with n = 3, 4, 5 and every triangle flip, digon
collapse and digon create the app offers; each result valid by Linhart-Ortner, and every drawing
(model 1 where it exists, model 2) checked by `CurveCheck`: finite, no curve crosses itself, two
curves cross twice if the circles cross, else not at all; a model 1 digon pair touches. The checker
flattens the Bezier pieces (64 points each), counts proper segment crossings (rounding noise of
collinear segments ignored) and treats places closer than 1e-5 of the drawing as contact zones
(crossing if the curve changes sides of the other one there, else touching); `CurveCheckTest`
tests it on constructed curves. Result of the full sweep (4.5 min): 17772
arrangements, 23858 drawings, no defect except model 1 digons: of 17529 model 1 digons, 320 cross
visibly (one pair each in 320 drawings) and 1976 below a pixel; all others touch. All results valid.

- Model 2: no defects. Model 1 without digons: no defects. Krupp and its flips, the start example
  and the bundled n = 6 examples: clean (no visible defect).
- Model 1 digons: most touch cleanly. Some cross twice so close to the shared piece that it is
  below a pixel (contact zones of 1e-3 of the drawing see a touching). Some cross visibly: the
  curve that comes in at the sharper angle cuts the corner less and crosses the other one before
  joining the shared piece (e.g. n5-intersecting-all #181, circles 1 and 3 at the corners of the
  outer square; screenshots in `composeApp/build/sweep/screenshots/` from `SweepScreenshotTest`).
  The original draws exactly the same (next section); the sweep lists these and fails only on
  other defects.
- jvmTest runs a representative part (all n = 3, 4, every 20th of n = 5, with all flips; about
  25 s). The full sweep is excluded from `jvmTest`: `./gradlew :composeApp:jvmTest -PfullSweep
  --tests '*DrawingFullSweepTest'`, report in `composeApp/build/sweep/full.txt`.

### Model 1 digons checked against the original draw7.sage

`scripts/probe/draw7_orig/` holds `program_py3/draw7.sage`, `basics.py` and `ipestyle.txt` with two
changes: the `assert(2 not in deg)` commented out (not in the py2 original), and
`Graph(dual_edges, multiedges=True)` in `compute_planar_dual_graph` (Sage 10.9 silently drops a
repeated edge, so the digon's second edge got lost and `compute_pseudocircles` failed; the Sage of
these scripts kept it, otherwise they could not have drawn digons). Run with Sage 10.9 on five
n5-intersecting-all members (#181, #6 and #48 with a visible crossing near a touching, #2 below a
pixel, #1 clean):

```
cd scripts/probe/draw7_orig
sage draw7.sage cases/n5_181.enc          # writes cases/n5_181.enc_1_1tutte7.ipe
./gradlew :composeApp:jvmTest -Pdraw7Dump --tests '*Draw7ComparisonDump'   # ArrView: cases/*.arrview.txt
python3 compare.py cases/n5_181 cases/n5_6 cases/n5_48 cases/n5_2 cases/n5_1
python3 render.py cases/n5_181 [-1.2 -1.07 0.12]   # side-by-side SVG (optional zoom)
```

ArrView with sage's settings (100 iterations, eps 1e-10) and the same outer face writes the same
control points: largest deviation 1e-10 to 9e-10 of the drawing's radius, after aligning by a
rotation / reflection of the outer polygon (sage's face lists start elsewhere; for #2 sage took
another of the equally symmetric faces). So the crossings near the touchings, e.g. #181's circles
at the corners of the outer square, are in the original output too; the side-by-side renderings
show both identical. Nothing in ArrView changed (only `ArrangementLayout.controlPoints`, which
`curves` uses, is public now for the dump).

### Settings, undo, sets

- `LayoutSettings.maxIterations` 50; Help and the user guide take it from the code / say 50.
- Colour schemes Standard, rainbow, rainbow2 (rainbow3-2, rainbow3-k removed); a stored removed
  scheme falls back to the default (unknown enum name).
- "Make outer face" is an undo step (not if the face already is outside).
- Sets (PoCoView's `sets`, `setIndex`, `showSet`): opening or importing text with several
  arrangements (sparse6 lines, several sequences one after the other, a JSON list of the objects)
  keeps all of them; loading an example member opens the whole set at that member. Toolbar
  `< i / n >`, PageUp / PageDown and `,` / `.`; title `name · set i/n`. As in PoCoView: a member's
  edits stay while another is shown, switching clears the undo history (it belongs to one member),
  an unapplied widget edit asks "Save changes?" before a switch, unsaved means some member differs
  from its loaded or saved version, and Save writes every member.
- Claude's choices (to confirm): the file formats for several arrangements (JSON: a list of the
  one-arrangement objects named `<name> #i`; text: the sequences separated by an empty line); a
  load stays undoable (ArrView's) and restores the previous set; members are decoded only when
  shown (n6-digonfree-all has 2131); after a load or a set step the canvas takes the keyboard focus
  again (the web check showed PageUp doing nothing after a click on `>`).

## 2026-11-18: Long press opens the face menu

Manfred: "On the phone a long press is like a right-click, so make the browser do that too." A touch
screen has no right-click, so a long press opens the same face menu as a right-click.

- Ported from ktoy (`EditGestures`): `MenuGestures(rightClick, longPress)` per platform with
  `LocalMenuGestures` for tests. Web: right-click and long press. JVM desktop: right-click only
  (like ktoy).
- A one-finger press held still for the long-press timeout (Compose's `longPressTimeoutMillis`) on
  any face, the outer face included, opens its menu at the press position. Moving more than the
  touch slop before that pans as before; after the menu opened, moving and releasing do nothing (no
  flip, collapse or create). A short tap keeps its action.
- Help (General and Shortcuts) and the user guide say "long press on touch screens".

## 2026-11-18: Welcome text says what pseudocircles are

Manfred: the Welcome dialog and the first Help paragraph briefly explain arrangements of
pseudocircles (same style as PoCoView's welcome). Final wording (Manfred's last version):
"ArrView is a viewer for arrangements of pseudocircles: closed curves where every two curves meet at
most twice and cross at each meeting point. The curves need not be circles (hence "pseudo"); only the
combinatorics counts. Change an arrangement by flips (click a triangle or a digon) and explore and
visualise its combinatorial properties." Feedback box and buttons stay.

Implementation: `WELCOME_TEXT` is the Welcome text. Help General starts with its first two sentences
(`PSEUDOCIRCLES_INTRO`), then "ArrView shows connected arrangements (together the curves are
connected)." and the colours; the click sentences follow in the next Help paragraph as before
(Claude's choice).

## 2026-11-18: Examples dialog: info area, singletons, website structure

### Request (Manfred)

The Examples dialog shows an info area for the selected entry, as PoCoView's; the description texts
are "not good". A set with one arrangement gets no number selection. Two levels: Sets and Singletons.
"Look at how the examples are best structured and labelled; also link the website and the
circularizability paper: https://www3.math.tu-berlin.de/diskremath/pseudocircles/".

### Decisions

- Info area as in PoCoView's Examples dialog: description, n, number of arrangements, source.
  Descriptions rewritten short and clear (simple stays simple, no padding); a fresh reviewer checks
  that they are uniform and not wordy.
- One arrangement: no − [#] +, only the note "1 arrangement".
- Two levels: "Sets" with the groups (a group may have a single member for some n, e.g. great
  n = 3; it stays in Sets), then "Singletons" with the named single arrangements.
- The website (local mirror first) is the reference for grouping and labels, as far as ArrView has
  the data; where the bundled data or computations/results differ from the website, the data wins
  and the differences are reported. Link the set's page on the website (LinkText) and the paper
  "On Circularizability" where the set comes from it; only verified links. If the website cannot be
  read: stop that part and report.

### Implementation (2026-11-18)

- Reference read: the website mirror `~/Documents/2024/TUB/pseudocircles/website/arrangements`
  (same as `~/github/research/pca/pseudocircles/website`): main page, pseudocircles (database) page
  with the table, triangles page, nomorecircles page. The DOIs of both papers are taken from the
  main page (Triangles and Drawings: 10.1007/s00454-020-00173-4; On Circularizability:
  10.1007/s00454-019-00077-y). Page links `?show=pseudocircles|triangles|nomorecircles` follow the
  site's index.php.
- Groups in the website's order: Connected, Intersecting, Digon-free intersecting, Great
  pseudocircles (the database page and its table), then Minimum number of triangles, Maximum number
  of triangles, Füredi–Palásti construction (the triangles page). Singletons: Krupp, NonKrupp,
  3-Chain, the non-circularizable ones (nomorecircles page), all-triangles-Krupp, Edelsbrunner-Ramos,
  Pappus.
- Info area (below the selected leaf, as PoCoView's): description; "n = 5, 278 arrangements" or
  "n = 5, 1 arrangement"; the member chooser for lists; "Website: <page title>" and "Paper: <title>"
  links where they apply; without a website page "Data: <path>" or "Typed in as intersection
  sequences". A link "Most of the data is from the pseudocircles website" at the top of the dialog.
- Tests: `ExamplesDialogUiTest.twoLevelsAndTheInfoArea` and the existing ones (every set loaded by
  clicks through the two levels), `ExamplesTest.websitePagesAndPapers`.

### Claude's choices (to confirm)

- Website page links: database (connected, intersecting, digon-free), triangles (min, max,
  Füredi–Palásti), nomorecircles (the n = 5 and n = 6 non-circularizable singletons and
  Edelsbrunner-Ramos, which the page lists as n6_edelsbrunner_nonr, isomorphic). Paper links: On
  Circularizability for the database sets, the great pseudocircles (counts in the paper's table) and
  the nomorecircles ones; Triangles and Drawings for the triangle sets.
- No links for the great pseudocircle files (on the server as great.zip, but no page links them;
  the database page has only their counts), the all-triangles-Krupp and Pappus arrangements (not on
  the website; not verified in the paper), and the typed-in Krupp, NonKrupp, 3-Chain.
- Every set shows "Data: <path>" (also those with a website page: intersecting and digon-free come
  from computations/results, not the website's files).
- The non-circularizable n = 5 and n = 6 arrangements are Singletons as the request says, although
  the website presents them as two sets ("the four ... n = 5", "the three ... n = 6").
- Triangle group titles shortened from the website headings ("Digonfree arrangement
  minimizing/maximizing the number of triangles", "Fueredi Palasti Construction"); the maximum group
  is not called digon-free since it contains all5_digon_13.
- In a menu of several triples, a triple with one triangle is one entry labelled with the
  (ascending) triple; its click flips that triangle.
- The −/+ buttons have 8 dp horizontal padding (ktoy's compact buttons) instead of Material's 24 dp
  and a minimum width of 40 dp (square) instead of 58 dp.
- Descriptions: "The Krupp: 3 great circles.", "The NonKrupp: ...", "The 3-Chain: ..." (name first);
  "all triangles Krupp, number 2" (from the file name) and "p3/n^2 tends to 2/3" dropped.
- Group titles: "Great pseudocircles" as on the website (the Properties widget still says "pseudo
  great circles"). Leaf names unchanged (`n5-intersecting-all`, ...).
- Maximum triangles: n = 8, 9 described as "maybe the maximum" (website: "might be optimal"); the
  "all" files only say how many triangles (the website does not say which of the two n = 7 files,
  28 and 29 triangles, is the maximum).

### Differences between the website and the bundled data

- Great pseudocircles: the database page shows only the counts n = 3..7 (1, 1, 1, 4, 11); the
  files are on the server (great.zip, n = 3..10) but not linked. ArrView has n = 3..8 from
  computations/results (n = 8: 135, as in great.zip).
- Intersecting n = 2 (1 arrangement): bundled; the website table starts at n = 3 (its file list has
  all2).
- The website has more than ArrView bundles (by choice of size): connected n = 6, intersecting
  n = 6, digon-free n = 7, the additional non-circularizable n = 6 arrangements (of the intersecting
  ones only Edelsbrunner-Ramos is bundled; none of the digon-free connected ones).

### Open questions

- What does "all triangles Krupp" (file n6_nonr_all_triangles_krupp_number2) mean? Its description
  says only "non-circularizable intersecting, n = 6" now.
- Maximum number of triangles, n = 7: two "all" files with 28 and 29 triangles, both digon-free; which
  one is the maximum? The descriptions only give the numbers. Bundled counts agree with the website table where both exist.

## 2026-11-18: Compact number fields

### Request (Manfred)

The member selection in the Examples dialog ("< [#] > of M") is not nice, and the integer fields are
far too big (Material's OutlinedTextField: 280 dp minimum width, 56 dp high). Number inputs compact
everywhere, as in ktoy; space wasted nowhere.

### Decisions

- Model: ktoy's StepsPerSecInput (`SimulatorScreen.kt`): a BasicTextField with a 1 dp grey rounded
  border, as high as the buttons next to it (ButtonDefaults.MinHeight), mono style, text vertically
  centred, "−" / "+" buttons on either side. Copy that approach.
- One shared composable (CompactNumberField): width fits the longest allowed value (digits of the
  maximum plus padding), no 280 dp minimum; invalid input gets a red border, the hint ("at least 1",
  "1 to M") as small text below or beside, without making the field big.
- Examples: "− [ 17 ] +  of 2131"; buttons step, disabled at the ends; typing jumps; out of range
  shows "1 to M" and disables Load.
- Every other number field uses it (Settings: Max. iterations, eps). eps without −/+.

### Implementation (2026-11-18)

- `ui/NumberField.kt`: `CompactNumberField(state, value, parse, error, maxChars, tag, steppers)`,
  width measured from `maxChars` zeros in the mono style plus 2 x 6 dp padding; the hint in small red
  text below. `ParsedField` (the Material field) is gone.
- Settings: "Max. iterations" and "eps" as rows (label left, field right, like the colour scheme
  row); Max. iterations with −/+ (− off at 1). Tags unchanged (`max-iterations`, `eps`,
  `example-index`).
- Tests: `SettingsUiTest.compactNumberFields` (height = button height, width < 140 dp, −/+, typing,
  limits, hints), the Examples tests click "−" / "+".

### Claude's choices (to confirm)

- Max. iterations has no maximum: the field is wide enough for 6 digits (longer input scrolls in the
  field); eps for 12 characters (the input limit).
- The buttons are Material OutlinedButtons with "−" (U+2212) and "+", as ktoy's (not compact) ones.

## 2026-11-18: Triples ascending, faces oriented

### Request (Manfred; decided with a reviewer, confirmed by Manfred)

"1 5 3" meant a triple in the list and in the group headings, but an oriented face in the menu
entries; a menu with a single triple never named it. Make it uniform.

### Decisions

- Triples are always written ascending: in the list ("possible triangle flips (3): 1 2 3, 1 2 4,
  3 5 6 (x2)"), in group headings and in menu titles. (xm) counts the triangle faces of the triple.
- The menu title names the triple: "Flip a triangle of 3 5 6", wherever such a menu opens with a
  single triple (list click, hypergraph click, canvas if applicable). Several triples under the
  pointer keep their submenus, headed by the ascending triple.
- The menu entries are the triangle faces in their oriented description ("3 5 6", "3 6 5"); equal
  descriptions numbered as now. The Krupp: "possible triangle flips (1): 1 2 3 (x8)", eight entries
  under "Flip a triangle of 1 2 3". A single face still flips directly.
- Check the digon side for the same issue and fix it if inconsistent.

### Implementation (2026-11-18)

- `triangleHeading(triples)`: "Flip a triangle of a b c" (sorted) for one triple, else "Flip a
  triangle"; used by the hypergraph click and the list click. The list, the group entries and their
  submenu headings were already ascending (sorted triples); the entries are the oriented cell
  descriptions (`cellDescription`), the outer face marked " (outer face)" as everywhere.
- Digon side checked, already uniform: pairs ascending in the lists, in the canvas pair entries
  ("1 3", "Create digon 1 3"), in the headings ("Create a digon of 1 3", "Collapse a digon of 1 3")
  and as submenu headings; the entries are the oriented cell descriptions (with stars for creates).
  No change needed.
- The canvas has no triple menus (a triangle face flips on click; its right-click item is "Flip
  triangle").

## 2026-11-18: Clickable action lists

### Request (Manfred)

The entries of "possible digon creates / collapses / triangle flips" below the graphs become
clickable.

### Decisions

- A click on an entry does exactly what a click on that pair's stripe or that triple does: a unique
  entry acts directly, an ambiguous one opens the same menu / submenu.
- Hovering an entry highlights it in the graph and its cell(s) on the canvas, like hovering the graph.
- The link style of the app (LinkText) or an equivalent clearly clickable style; "(xm)" and the
  counts stay.

### Implementation (2026-11-18)

- Each entry ("1 3 (x2)") is a link (LinkText's colour, underlined, hand cursor); the prefix, the
  commas and "none" stay plain text, the line wraps (FlowRow). The click goes through the same code
  as the graph (`actOnPair`, `actOnTriples`); a menu opens below the entry.
- Tests (`ActionsUiTest`): unique create, collapse, ambiguous create with its menu, triple with one
  triangle, triple with two (menu), hover highlights, Ctrl+Z right after an entry click.

### Claude's choices (to confirm)

- Hovering the graph itself highlights nothing (it never did); "like hovering the graph" is read
  as: the entry's pair (an orange line over its stripe) or triple (an orange outline) in the graph,
  and all cells of its actions (every create cell of the pair, its digon, or every triangle of the
  triple) in yellow on the canvas, as hovering a menu entry does.
- A comma stays on the line of its entry when the list wraps.
- The entries react to a tap but do not take the keyboard focus (an entry may disappear with its
  click, and Ctrl+Z must still work); they have a button role for accessibility.

## 2026-11-18: Rainbow schemes in OKLCH

### Request (Manfred)

All rainbow variants contain a yellow that is bad on white; compute them in OKLCH (he chose it from
rendered previews, rows B, C, D of `scripts/probe/colour_schemes_preview.py`).

### Decisions

- rainbow, rainbow2, rainbow3-2, rainbow3-k keep their structure (hue sweep, falling lightness for
  rainbow2, k bands for rainbow3), computed in OKLCH instead of HSL: chroma C = 0.16, hue =
  i/n*360 + 25 degrees, lightness L between Lmin = 0.42 and Lmax = 0.72; rainbow fixed L = 0.60;
  rainbow2 from Lmax down to Lmin; rainbow3 the same band formula with Lmin/Lmax. sRGB clamped to
  [0, 1] after the conversion.
- Exact reference: `scripts/probe/colour_schemes_preview.py` (`oklch`, `rainbow_ok`, `rainbow2_ok`,
  `rainbow3k_ok`; rainbow3-2 = the rainbow3 band formula with k = 2).
- ArrView deviates from blurred-memory's formulas on purpose (Manfred, 2026-11-18); blurred-memory
  is unchanged.

### Implementation (2026-11-18)

- `core/ColorPalettes.kt` replaced: `ColorPalette.lightness(n, i)` (band formula of blurred-memory
  kept) and `oklchToArgb` (the script's matrices; gamma, then clamp; channels `round(x * 255)` with
  round half to even like Python's `round`). `ColorPaletteTest`: colours of all four schemes at six
  (n, i) equal the script's output, plus opacity, never black, neighbours differ, lightness
  structure (rainbow fixed, rainbow2 falling, rainbow3-2 bands alternating).

## 2026-11-18: Note on the Layout settings

Manfred: say explicitly that the Layout group holds the parameters of the iterated Tutte drawing, i.e.
visualisation only, and refer to the papers (triangles and drawings; circularizability for model 2 with
the primal-dual graphs). Done as a small note under the Layout heading of the Settings widget and in
Help and USER_GUIDE.

## 2026-11-18: Lists of possible actions below the graphs

### Request (Manfred)

Below the intersection graph and the triangle hypergraph, show all pairs and triples where an action
is possible, e.g. "possible triangle flips (13): ..." followed by the 13 entries. Format clarified
with a reviewer and confirmed by Manfred.

### Decisions

- Intersection graph, two lines below the graph:
  - "possible digon creates (k): 1 3 (x2), 2 4, 2 5"
  - "possible digon collapses (k): 1 2, 3 4"
- Triangle hypergraph, one line below the graph:
  - "possible triangle flips (k): 1 2 3 (x2), 1 2 4, 3 4 6"
- k is the number of listed pairs or triples (entries, not options).
- Each pair or triple is written as space-separated circle numbers, ascending inside. Entries are
  separated by ", " and sorted numerically (lexicographic on the numbers).
- "(xm)" marks a pair or triple with m >= 2 options, i.e. the ones that open a menu or are drawn
  double strength. It is the same set and the same count as the menus. Collapses can never be
  ambiguous, so they never get one.
- Empty list: "possible digon creates (0): none". Lines are always shown.
- Small text in the same style as the count line above the graph. Plain text, not clickable (the
  entries became links: "Clickable action lists"),
  wrapping; the widget scrolls if needed, no truncation.
- The lists must be exactly the pairs/triples that get stripes/triangles and are clickable (same
  source: CellActions). Test that on several examples, including a big one.

### Implementation (2026-11-18)

- `CellActions.createPairs`, `collapsePairs`, `triangleTriples` (entries with their numbers of
  options, sorted), `actionPairs` (the striped and clickable pairs; the intersection graph now uses
  it for stripes and hits). `possibleList(...)` writes a line. Tests: `DigonCreateTest`
  (lists = stripes / triangles on 300 samples and the n = 24, 42 Fueredi-Palasti examples),
  `ActionsUiTest` (the lines in the widgets), `HelpClaimsTest`.

### Claude's choices (to confirm)

- The lines use the same text style as the count line (bodyMedium), below the graph canvas.

### Open questions

- "(xm) ... the same set as the menus": for the hypergraph, a click where several triangles overlap
  also opens a menu for triples with one cell. "(xm)" follows "drawn double strength" (m >= 2 cells).

## 2026-11-18: Colour scheme and line width, Settings widget

### Request (Manfred)

Line width and colour spectrum adjustable. Port the colour schemes of ~/github/blurred-memory (several
are implemented there) and offer the scheme as a dropdown; the colours are computed from n.

### Decisions

- (Formulas superseded by "Rainbow schemes in OKLCH".) Port
  `blurred-memory/shared/src/commonMain/kotlin/org/bytefred/blurredmemory/shared/ColorPalettes.kt`
  unchanged in its formulas: rainbow, rainbow2, rainbow3-2, rainbow3-k (see blurred-memory
  docs/11-color-palettes.md), with count = n and index = circle index. Also port its unit tests
  (ColorPaletteTest) where they apply.
- Dropdown entries: Standard (ArrView's current fixed colours), rainbow, rainbow2, rainbow3-2,
  rainbow3-k. Default rainbow2 (Manfred); changed to Standard (Manfred, 2026-11-18).
- Line width: slider 0.5 to 8, default 3 (Manfred; changed to 4 on 2026-11-18), for the curves on the canvas and in the SVG export.
- Placement (Manfred: "Layout is really settings, layout is a subgroup"; decided with a reviewer): the
  Layout widget becomes "Settings", in the same toolbar slot. It has two subgroups with headings in
  PoCoView's style (copy it exactly):
  - "Display": Colour scheme dropdown, then Line width.
  - "Layout": Model 1/2 chips, Max. iterations, eps.

  One "Reset to defaults" at the bottom resets everything in the widget, as in PoCoView. The View menu
  item and the remembered open-widget pref follow the rename (an old stored "Layout" id still opens
  Settings).
- The scheme colours everything that uses the circle colours: curves, labels, SVG export, and the
  vertices of the intersection graph and of the triangle hypergraph. Colour scheme and line width are
  remembered like the other display settings.

### Implementation (2026-11-18)

- `core/ColorPalettes.kt` and `ColorPaletteTest` copied from blurred-memory (only the package and a
  provenance note changed). `ui/ColorScheme.kt`: Standard + the four palettes, `color(c, n)`.
- Settings widget as PoCoView's Display widget: "Display" heading (titleSmall), Colour scheme
  (PoCoView's `SelectField` dropdown), "Line width: 3.0 px" + slider; 12 dp space, "Layout" heading,
  model chips, Max. iterations, eps; 8 dp space, Reset to defaults (always enabled, as in PoCoView).
- Prefs `colorScheme` (enum name, unknown -> default) and `lineWidth` (clamped to 0.5..8).
- Tests: `SettingsUiTest` (dropdown by clicks with pixel check of the graph vertices, slider by
  swipes with more inked canvas pixels, Reset, remembered and corrupt prefs, old "LAYOUT" id, SVG).

### Claude's choices (to confirm)

- Reset to defaults also sets the model back to Model 1 (the chips are in the widget; "everything in
  the widget").
- The SVG uses the same line width number as the canvas (the SVG is 1000 x 1000, the canvas in
  pixels), so the SVG curves look relatively thinner on a big canvas.
- The colour scheme does not change the triangle/digon fills, the stripes or the highlights.
- Unlike PoCoView's Display widget, the first subgroup has a heading too ("Display"), as the spec says.
- As in PoCoView, Reset to defaults is always enabled; the colour scheme dropdown is a row with the
  label left and the field (150 dp) right, like PoCoView's dropdowns.

### Open questions

- "Layout" now names two things: the Settings subgroup (model, iterations, eps) and the View menu
  heading for auto / portrait / landscape (how the widgets are arranged). Rename the View menu one
  (e.g. "Window layout")?
- `SelectField` (PoCoView) and `ColorPalettes.kt` (blurred-memory) are now copies in ArrView;
  candidates for kotlin-common. PoCoView's line width label has the "3" instead of "3.0" web issue
  fixed here with `oneDecimal`.

## 2026-11-18: Create options ordered along the circle, creates in the right-click menu

### Request (Manfred)

"It would be cool to order the cells along the circle when there are several possibilities for a
digon create; and a right-click on a cell should offer the digon creates too: show the options of that
cell, i.e. which pairs are possible, and if ambiguous for a pair (there can be several within the cell)
list them."

### Decisions

- Where several cells are listed for a create of i,j (intersection graph menu), they are ordered
  along circle i (the smaller index), in the direction of its sequence, starting at its first edge.
  Options inside one cell are ordered the same way (by the position of the pushed edge of i along
  circle i, then of j along circle j). Claude's reading, to confirm.
- Right-click face menu: besides its current items, the face's digon creates: one entry per pair,
  a pair with several options in the cell gets a submenu with the starred descriptions (same labels,
  hover highlight and submenu mechanics as the left-click menu).

### Implementation (2026-11-18)

- `Arrangement.sidePosition(a)`: the index of a side's arc (crossing k to the next one) in its
  circle's list; `createOrder`: by the side of i, then the side of j. `CellActions.createsOf(i, j)`
  is in this order, so the intersection graph menu lists its cells by the first side of i (in list
  order) from which a create through that cell starts, and the options inside a cell (submenu) in
  the same order. The canvas menus (left and right click) list the pairs by i, then j, and the
  options of a pair in this order.
- Right-click menu, top to bottom: Flip triangle / Collapse digon (as before), then one entry
  "Create digon i j" per pair (pairs by i, then j; a pair with several options opens the starred
  options as a submenu; with one option the entry does it), then Make outer face. It now uses the
  same menu as the left click (hover highlight, submenu column, Escape, outside click).
- Tests: `DigonCreateTest.createsOfAPairAreOrderedAlongTheCircles`, `ActionsUiTest`
  (`graphMenuOfCellsWithSubmenus` checks the on-screen order of cells and options,
  `rightClickMenuListsTheCreatesOfTheFace`: single create, submenu, order).

### Claude's choices (to confirm)

- "Starting at its first edge": the first edge of circle i is the arc from the first to the second
  entry of its list; a side traversed backwards counts with its arc's position.
- Right-click entries are labelled "Create digon 1 3" (verb as the other items "Flip triangle",
  "Collapse digon"); they come after Flip / Collapse and before Make outer face. Unlike the left
  click, the pair level is kept even when the face has only one pair ("one entry per pair").
- A cell that circle i touches several times sits in the graph menu at its first touch (the first
  side of i, in list order, from which a create through it starts). The two cells on both sides of
  the same arc of i have the same position there; then the side of j decides.
- The right-click menu is now the same menu as the left click, so pointing at Flip triangle,
  Collapse digon or Make outer face highlights the cell too.

## 2026-11-18: Outer face clickable, submenus for ambiguous creates

### Request (Manfred)

The outer face must be clickable on the canvas too (flip, collapse, create, with the "!" on hover):
"!" whenever an action is possible in the hovered cell, to suggest to the user that something can be
done. If a circle occurs several times on a cell, a digon create of a pair in that cell is ambiguous
(possibly on both sides, e.g. 1 and 5 in "1 2 2 1 3 3 1 4 5 6 6 5 7 7 5 8 8 5 9"); the user picks
which edges to merge.

### Decisions (Manfred's answers, 2026-11-18)

- Several options: a menu with submenus (pair -> its options on the canvas; pair -> cell -> its
  options in the intersection graph). Going back from the menu to the canvas to pick edges was
  rejected ("silly"). The two edges to push are marked with a star instead of brackets, e.g.
  "1* 2 2 1 3 3 1 4 5* 6 6 5 7 7 5 8 8 5 9". Hovering an entry highlights the cell (and its two edges).
- Outer face "!": in the corner (top left, top right, bottom left, bottom right) where there is room;
  arrangements are rounded, so the corners have more room than the middle.

### Implementation (2026-11-18)

- A click into the outer face (no drag) flips, collapses or creates like in any other cell; a drag
  still pans. The right-click face menu is unchanged.
- Menus: canvas cell -> pairs ("1 3"; with only one pair its options directly) -> options; graph
  pair -> cells (with only one cell its options directly) -> options; hypergraph hit triples ->
  cells (one triple: its cells directly). A level with a single option is skipped: such an entry is
  that option. Options are the cell description with a star after the two pushed sides
  (`cellDescription(f, marked)`), no brackets. Hovering an entry highlights its cell(s) in yellow
  and the two sides of a create in orange. Escape or a click outside closes the menu.
- Tests: `ActionsUiTest` (outer triangle flip and outer create by clicks, the "!" in a corner, pair ->
  options and cell -> options submenus, star labels, hover highlight with sides, Escape).

### Claude's choices (to confirm)

- Corner rule: the four canvas corners moved 28 px inwards; of those that lie in the outer face,
  the one farthest from the curves (distance to points sampled along all curves). No corner in the
  outer face (zoomed far in): no "!".
- Submenu = a second column inside the same popup, to the right of the entries, opened by pointing
  at (or clicking) a group entry, with a drawn ">" on group entries. The column has its room from
  the start (an empty area until a group is pointed at), so the menu does not move or grow under
  the pointer when a submenu opens. Pointing at a single-option entry of the first column closes
  the submenu.
- Group entries: canvas pair "1 3"; a pair with one option keeps the label "1 3" (a click does it,
  pointing at it highlights its sides). Graph: a cell with several options shows its plain
  description (no stars); a cell with one option shows that option's starred description.
  Hypergraph: triple "1 3 5"; pointing at it highlights all its triangle cells.
- The canvas heading names the pair when there is only one ("Create a digon of 1 3"). The submenu
  column repeats the group entry as its heading. Entries of the outer face carry " (outer face)"
  everywhere (canvas too). Collapse entries in the graph menu are never grouped (a pair has at most
  one collapsible digon anyway).
- Escape: the menu takes the keyboard focus and closes on Escape itself (besides the popup's own
  dismissal); a click outside closes it without acting on the canvas.
- A click in empty space (the outer face) that moves less than 4 px is a click, so it acts on the
  outer face; dragging pans as before.
- Highlight of the two sides: orange `#FFB300`, 10 px wide under the curves. The highlight stays
  until the pointer is on another entry or the menu closes.
- Not tested by clicks: a canvas cell with one pair and several options (options directly). No such
  cell occurs among the connected arrangements with n <= 5, nor after up to four digon creates on
  them. The graph case (one cell, several options) is tested.
- Note on the example in the request: "1 2 2 1 ..." has two consecutive sides on circle 2, which a
  cell cannot have (two sides of one circle meet only at a crossing of two circles); the star
  notation is used as given.

## 2026-11-18: Digon create, actions in the graph views, triangle hypergraph

### Request (Manfred)

"What is missing is the digon-create flip (digon collapse works already)." In the intersection graph,
draw the edge of a pair that could create a digon dashed. Put a wide yellow stripe behind the edge of
every pair i,j that can do a digon create or a digon collapse, and make it clickable so that a click
performs the flip. Add a view "Triangle hypergraph": the n vertices on a circle as in the
intersection graph, but instead of edges draw the triangle cells i,j,k of the arrangement (a 3-uniform
hypergraph). A triple with several triangle cells is drawn twice as strong. Clicking a triangle flips it.

### Decisions (Manfred's answers, 2026-11-18)

- Digon create: two circles i,j that do not cross and both have an edge on a common cell; pushing
  one across the other gives two new crossings and a new digon. Inverse of digon collapse.
- Canvas: hovering a cell where an action is possible shows a "!" in the cell. A triangle flips and
  a digon collapses on click as before. A cell with exactly one digon create flips on click; a cell
  with several create options opens a menu to choose which pair to merge.
- Intersection graph: dashed edge for a non-crossing pair that can create a digon (non-crossing
  pairs that cannot stay undrawn). Wide yellow stripe behind every pair that can create or collapse.
  Ambiguous pairs (several cells to create through, or several digons to collapse) get a stripe twice
  as strong. Click on a pair: unique -> the flip happens; ambiguous -> a menu to choose the cell.
  No "!" in the graph views.
- Triangle hypergraph: own widget/menu item named "Triangle hypergraph"; vertices as in the
  intersection graph; one triangle per triple that bounds a triangle cell; a triple with two or more
  triangle cells is drawn twice as strong (no further scaling). Triangles are filled transparent, so overlaps add up
  and many triangles are visible as a darker area (the whole n-gon may be covered). A click collects all triangles under
  the pointer: a single triple with a single cell flips directly, otherwise a menu lists every hit
  triple with its cells.
- Cells in menus are described by the circles of their boundary edges in cyclic order, always
  separated by spaces, e.g. "1 2 1 3 1 3 1 4 1 4 1 5 1 5". Hovering a menu entry highlights that cell in the
  canvas.

### Implementation (2026-11-18)

- Core: `DigonCreate(face, i, j, dartI, dartJ)`, `digonCreates()` / `digonCreatesIn(f)` (every face,
  every non-crossing pair with a side on it, every choice of those sides), `digonCreate(op)`: each of
  the two sides gets two new entries `-o, +o` in list order (`o` the other circle, negated if the
  other side is a backward dart). `CellActions` (per arrangement): creates per face and pair,
  collapsible digons, triangles per triple. `cellDescription(f, marked)`.
- Tests: `DigonCreateTest` (every create of the n <= 4 sets, all of `n5-connected-all`, samples of the
  others up to n = 14: valid, +2 crossings, +2 faces, one digon of the pair, p2 + 1 minus the digons
  across the two sides, collapse of the new digon gives the exact sequences back; collapse then create
  gives the original back exactly (up to list rotation) through the face the digon merged into),
  `ActionsUiTest` (clicks). Sample sets: every set with n <= 4 or at most 30 members and
  `n5-connected-all` in full, about 25 members of the others up to n = 14.

- Finding: a pair never has two collapsible digons. Two digons of one pair use three or four of its
  four arcs, so one circle crosses no other one and a collapse would cut it off. So "several digons
  to collapse" does not occur; double stripes and graph menus come from creates only (the code
  handles collapses generally; `DigonCreateTest` checks this on all samples).

### Claude's choices (to confirm)

- "Possible" means the flip succeeds: a digon whose collapse is refused (the arrangement would fall
  apart) gets no "!", no stripe and is not counted for ambiguity.
- Outer face: (superseded by the entry "Outer face clickable" above: the outer face is clickable on
  the canvas like every other cell, with its "!" in a corner.) The graph views include the outer face (creates through it, a triangle as outer face); in
  menus it is marked " (outer face)", and its highlight is a thick yellow line along its boundary.
- Ambiguous = more than one option: in the canvas a cell with two creates of the same pair (one of
  the circles has several sides on it) opens the menu too; in the graph such a pair is "several
  cells to create through" (double stripe, menu).
- Menu texts (the brackets and the canvas entries are superseded by the stars and submenus of the
  entry above): canvas heading "Create a digon", entries `1 3: [1] 2 [3] 2` (pair, then the cell with
  the two sides of the create in brackets). Graph: heading "Create a digon of 1 3" or "Collapse a
  digon of 1 3", entries the cells (creates with the two sides in brackets). Hypergraph: heading
  "Flip a triangle", entries `1 2 3: 1 3 2` (triple, then the cell). Equal entries get " (1)",
  " (2)", ... The cell description starts at its lexicographically smallest rotation, in the
  boundary order of the face (face to the right of its darts).
- Highlight of a hovered menu entry: yellow `#FFD600` at alpha 0.53 filled over the cell (model 1
  digon: a yellow disc around its touching dot).
- "!": a drawn badge (dark teal disc of radius 9 px with a white bar and dot, no font), at the grid
  point of the cell farthest from its boundary; for a model 1 digon 16 px above the touching dot.
- Intersection graph: stripe `#FFD600` at alpha 0.4, 10 dp wide; twice as strong = drawn twice.
  Dashed edge 5 dp dash / 4 dp gap, same colour as the solid edges. A click hits the nearest striped
  pair within 8 dp of its edge.
- Triangle hypergraph: fill orange `#FB8C00` at alpha 0.18 with a thin outline at alpha 0.4, a triple
  with >= 2 triangle cells drawn twice. Above it "p3 triangles on k triples". A click on a point in a
  single triangle of a triple with one cell flips; otherwise a menu of all hit triples and cells.
- Status after a create: "Created a digon of i and j".
- Menu order: canvas create menu by pair (i, then j); graph menu by face; hypergraph menu by triple.
- "Triangle hypergraph" sits after "Intersection graph" in the toolbar chips and the View menu.
- The canvas "!" sits at the grid point farthest from the boundary (24 x 24 grid over the face); for
  a face too thin for the grid, the middle of its widest horizontal chord.
- The right-click face menu is unchanged (no create entries there; see open questions).

### Extra requests (Manfred, 2026-11-18)

- Web header subtitle "Arrangement viewer" instead of "Arrangements of pseudocircles".
- Toolbar status separator "  ·  " as in PoCoView instead of "   |   " (replaces the ASCII-only
  rule of phase 3a for this character; checked to render on the web).

### Open questions

- Should the right-click face menu also list the digon creates of the face?
- Should creates through the outer face be possible on the canvas? (Answered: yes, see above.)

## 2026-11-17: Broad tests

### Request (Manfred)

"write sensible broad tests, like in the other repos: examples, features, buttons, dialogs, everything
that is implemented".

### Decisions and interpretations

- Every example member goes through all text forms; drawings: all of n <= 5 (LayoutTest), for
  n >= 6 lists up to 25 fully, longer ones sampled (first, last, three in between).
- Every menu item and every widget / dialog button is clicked once with an assertion on its effect,
  except those that open the system file dialog (not drivable in a test; the state side of saving and
  opening is tested).
- Copy in the tests goes to a fake clipboard, never the real one.
- `jsTest` stays out of `scripts/test.sh` (mocha's 2 s limit per test; see doc/DEVELOPMENT.md).
- Overview per area: doc/DEVELOPMENT.md, "What the tests cover".

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
  value is valid (max. iterations >= 1, eps > 0); no upper bound on max. iterations (Manfred).
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
- Settings (in `AppState.layoutSettings`, UI in phase 3b): max iterations 5 (Manfred, 2026-11-17;
  sage uses 100), eps
  `1e-8` (draw3.sage; draw7.sage has `1e-10`). With these weights the change rarely drops below
  eps, so mostly all iterations run; with 100 iterations n = 6 takes 3 ms (model 1) / 21 ms (model 2) on the JVM,
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
  nothing; a click into the outer face does nothing (empty space is for panning; superseded on
  2026-11-18: the outer face acts like the other cells). Right-click (or
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
