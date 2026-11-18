# ArrView spec log

Requests and decisions. Newest first.

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
