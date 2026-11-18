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
- Right-click a face (Ctrl+click on a Mac) for its menu: flip, collapse, "Create digon i j" per pair
  (several ways: a submenu), "Make outer face" (draws it outside).
- Several ways to create a digon of i and j are listed along circle i (the smaller number), in the
  order of its sequence, then along circle j.
- Drag to pan, wheel or pinch to zoom. Undo and Redo are in the Edit menu.
- Model 1 draws digons as touching points (intersecting arrangements of at least 3 circles), Model 2
  draws them as regions (every arrangement).

## Menus

- Arrangement: Examples..., Import..., Export..., Open file..., Save file..., Export SVG.
- Edit: Undo, Redo. View: zoom, model, widgets, layout. Help: General, Widgets, Formats,
  Keyboard shortcuts, About.
- Replacing or closing a changed arrangement asks "Save changes?" (Save JSON, Save text, Discard,
  Cancel).

## Widgets

- Properties: n, crossings, faces, p-vector (p3 and p2 on top), non-intersecting pairs, Krupp and
  NonKrupp triples, classes.
- Sequences: the intersection sequences, one line per circle. Edit, then Apply or Revert.
- Encoding: model 1 and model 2 sparse6, editable, with Copy.
- Intersection graph: an edge for every pair of crossing circles, dashed for a non-crossing pair that
  can create a digon; a yellow stripe (twice as strong if ambiguous) for every pair that can create or
  collapse one. Click a striped pair to do it (several ways: a menu of the faces, then submenus).
  Below it: "possible digon creates (k): 1 3 (x2), 2 4" and "possible digon collapses (k): ...";
  "(xm)" marks m ways.
- Triangle hypergraph: a transparent triangle per triple of circles bounding a triangle face (twice as
  strong for two or more). Click a triangle to flip it. Below it: "possible triangle flips (k): ...".
- Settings: Display (colour scheme: Standard (default), rainbow, rainbow2, rainbow3-2, rainbow3-k;
  line width 0.5 to 8, default 4) and Layout (model, max. iterations (5), eps (1e-8)); one Reset to
  defaults for all. Layout holds only the parameters of the drawing (iterated Tutte embedding; model 1 as in
  Felsner-Scheucher, Triangles and Drawings, model 2 the primal-dual graph as in Felsner-Scheucher, On
  Circularizability); they do not change the arrangement.

## Formats

- Sequences `{1: [2, -2, 3, -3], ...}`: list i has the crossings along circle i; +j where circle j
  crosses from the right of i to its left, -j the other way.
- Model 1 sparse6: the dual graph (intersecting only). Model 2 sparse6: the vertex-edge-face graph
  with a leaf at every face vertex.
- Files: JSON `{"name": ..., "sequences": [[...], ...]}` or the sequences as text. Import and Open
  file detect the format.

## Keyboard shortcuts

- Esc: close the open menu
- Ctrl+Z: undo
- Ctrl+Shift+Z or Ctrl+Y: redo
- + / -: zoom in / out
- Home or 0: fit

On a Mac, Cmd works like Ctrl.
