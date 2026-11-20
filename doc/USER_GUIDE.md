# ArrView user guide

ArrView shows arrangements of pseudocircles and lets you change them. Web version:
https://manfredscheucher.github.io/ArrView/

## Basics

- Click a triangle to flip it, a digon to collapse it (refused if the arrangement would fall apart).
- Click a face where two non-crossing circles both have a side to create a digon (the two circles then
  cross twice). Several options: a menu of the pairs, then a submenu of a pair's options, e.g.
  `1* 2 3* 2` (a star after the two sides pushed together). The outer face works the same.
- A "!" in the face under the mouse (for the outer face in a corner): a click does something there.
  In menus a face is written as the circles of its sides around it; pointing at an entry highlights
  the face in yellow and the two sides of a create in orange.
- Right-click a face (Ctrl+click on a Mac, long press on touch screens) for its menu: flip,
  collapse, "Create digon i j" per pair (several ways: a submenu), "Make outer face" (draws it
  outside).
- Several ways to create a digon of i and j are listed along circle i (the smaller number), in the
  order of its sequence, then along circle j.
- Drag to pan, wheel or pinch to zoom. Undo and Redo are in the Edit menu (also for Make outer face).
- While you have not zoomed or panned, the drawing is fitted again whenever the canvas changes size
  (window, widgets, dividers). After a zoom or pan it stays; Fit (Home or 0) or loading brings the
  automatic fit back.
- Drawing: model 2 (primal-dual graph, digons as regions) by default; model 1 (primal graph, legacy,
  intersecting arrangements only, a digon's circles touch) in Settings > Visualization, after a
  dialog. If the arrangement stops being intersecting, ArrView switches to model 2 with a notice.
  Help > Visualization explains the three graphs.
- Loading a file or example set with several arrangements shows a notice once per load ("Don't show
  again").

## Menus

- Arrangement: Examples..., Import..., Export..., Open file..., Save file..., Export SVG.
- Examples: the bundled arrangements by purpose, as on the pseudocircles website; an open group says
  what it is, a selected set shows its description (a list: choose the member with "-" / "+" or type
  its number). Loading a member of a set opens the
  whole set: "< i / n >" in the toolbar (or PageUp / PageDown, "," / ".") steps through it.
- Edit: Undo, Redo. View: zoom, widgets, layout. Help: General, Visualization, Widgets, Formats,
  Keyboard shortcuts, About.
- Replacing or closing a changed arrangement asks "Save changes?" (Save JSON, Save text, Discard,
  Cancel).

## Widgets

- At most 2 widgets are open by default (Settings, Max. widgets); opening another closes the one
  clicked least recently.
- Properties: n, crossings, faces, p-vector (p3 and p2 on top), non-intersecting pairs, Krupp and
  NonKrupp triples, classes.
- Encoding: the intersection sequences (one line per circle), model 1 and model 2 sparse6, each
  editable (Apply, Revert) with an (i) saying what it encodes; Copy for the sparse6 strings.
- Intersections (the intersection graph): an edge for every pair of crossing circles, dashed for a non-crossing pair that
  can create a digon; a yellow stripe (twice as strong if ambiguous) for every pair that can create or
  collapse one. Click a striped pair to do it (several ways: a menu of the faces, then submenus).
  Below it: "possible digon creates (k): 1 3 (x2), 2 4" and "possible digon collapses (k): ...";
  "(xm)" marks m ways. Click an entry to do it, like a click on the pair (several ways: the menu).
- Triangles (the triangle hypergraph): a transparent triangle per triple of circles bounding a triangle face (twice as
  strong for two or more). Click a triangle to flip it. Below it: "possible triangle flips (k): ...";
  click an entry to flip. Pointing at an entry highlights it in the graph and its faces.
  Triples are written ascending ("Flip a triangle of 1 2 3"); the menu entries are the faces in their
  own order around (e.g. 1 3 2).
- Settings: Display (colour scheme: Standard (default), rainbow, rainbow2;
  line width 0.5 to 8, default 4), Widgets (Max. widgets, default 2) and Layout (Visualization: model 2 or
  model 1, max. iterations (50)); one Reset to defaults for all. Layout changes only the drawing; its (i)
  names the papers, the (i) of Visualization opens Help > Visualization.

## Formats

- Sequences `{1: [2, -2, 3, -3], ...}`: list i has the crossings along circle i; +j where circle j
  crosses from the right of i to its left, -j the other way.
- Model 1 sparse6: the dual graph (intersecting only). Model 2 sparse6: the vertex-edge-face graph
  with a leaf at every face vertex.
- Files: JSON `{"name": ..., "sequences": [[...], ...]}` or the sequences as text. Import and Open
  file detect the format.
- Several arrangements (one sparse6 string per line, several sequences one after the other, or a JSON
  list of the objects) are loaded as a set, like an example set; Save file writes all of them.

## Keyboard shortcuts

- Esc: close the open menu
- Ctrl+Z: undo
- Ctrl+Shift+Z or Ctrl+Y: redo
- + / -: zoom in / out
- Home or 0: fit
- PageUp or , / PageDown or .: previous / next arrangement of a file or example set

On a Mac, Cmd works like Ctrl.
