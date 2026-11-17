# ArrView user guide

ArrView shows arrangements of pseudocircles and lets you change them. Web version:
https://manfredscheucher.github.io/ArrView/

## Basics

- Click a triangle to flip it, a digon to collapse it (refused if the arrangement would fall apart).
- Right-click a face (Ctrl+click on a Mac) for its menu; "Make outer face" draws it outside.
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
- Intersection graph: an edge for every pair of crossing circles.
- Layout: model, max. iterations (5), eps (1e-8), Reset to defaults.

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
