# Development

## Run and test

```
./scripts/runWeb.sh       # browser with hot reload, http://localhost:8611/
./scripts/runJVM.sh       # desktop app
./scripts/test.sh         # unit tests (JVM)
./scripts/buildWeb.sh     # static site in composeApp/build/dist/js/productionExecutable/
```

`runWeb.sh` opens Chrome; `NO_CHROME=1 ./scripts/runWeb.sh` only starts the server. The port 8611
is set in `composeApp/webpack.config.d/watch.js` and in the `PORT=` line of `runWeb.sh`. Change both
if needed.

## Project layout

Everything is in the single module `composeApp`, package `org.bytefred.arrview`:

- `core/`: the combinatorics, pure Kotlin, no UI code, unit tested in `commonTest` and `jvmTest`.
- `ui/`: Compose UI. Platform entry points are in `jsMain` and `jvmMain`.

### Core

The internal data is the intersection scheme (`Arrangement`): per circle the cyclic sequence of
signed crossings, as in rusty-arrangements and Linhart–Ortner. Everything else is computed from it.

| File | Content | Ported from |
|---|---|---|
| `Arrangement.kt` | sequences, darts, `alpha`/`sigma`/face successors, faces, p-vector, text format | rusty-arrangements `src/arrangement.rs` |
| `LinhartOrtner.kt` | validity with reasons (Def. 2.4/2.5, Thm. 5.4, Thm. 4.3 for genus 0) | Linhart–Ortner, EJC 11 (2004) #R30 |
| `Canonical.kt` | canonical form (lex-min over all starts, mirror identified) | rusty-arrangements `src/canonical.rs` |
| `Sparse6.kt` | sparse6 decode/encode (nauty format) | `scripts/probe/model2.rs` (decoder) |
| `Models.kt` | model 1 (dual graph) and model 2 (vertex-edge-face graph, NEW and OLD encoding) | `program/basics.py`, `model2_program/model2basics.sage`, `model2.rs` |
| `PlanarEmbedding.kt` | faces of a planar 2-connected graph (Demoucron–Malgrange–Pertuiset), for model 1 | (new; sage used its planarity test) |
| `PlaneGraph.kt`, `Graphs.kt` | embedded graphs: primal graph, vertex-edge-face graph | |
| `Operations.kt` | intersection graph, class tests (Krupp, great, cylindrical, …), triangle flip, digon collapse, digon create, relabel/reverse/mirror | `flip_potential.rs::flip`, `test_*.sage` |
| `CellActions.kt` | what can be flipped / collapsed / created per face, pair and triple; cell descriptions for menus | |
| `Examples.kt` | the bundled examples: reads `composeApp/examples/index.json` and the JSON files | PoCoView `core/Examples.kt` |
| `Formats.kt` | text forms (sequences, model 1 / 2 sparse6), file formats (JSON objects, sets, lines, text), reading with format detection | |
| `Json.kt` | small strict JSON reader | PoCoView `core/Json.kt` |
| `Tutte.kt` | iterated weighted Tutte embedding (`IteratedTutte`, one embedding per `step()`), literal port | `program_py3/draw7.sage`, `model2_program/draw3.sage` |
| `Layout.kt` | model 1 / model 2 graphs, the B-spline curves (`graph_2_ipe`), face boundaries, hit test (`Drawing`) | draw7.sage / draw3.sage |
| `Symmetry.kt` | automorphisms of the map, default outer face (most symmetries) | draw7.sage / draw3.sage |

### UI

Ported from PoCoView (package renamed, PoCoView-specific parts dropped): `Theme.kt`,
`Menus.kt`, `SplitPane.kt`, `Scroll.kt`, `Prefs.kt`, `FileIo.kt`, `Platform.kt` (with `jsMain` /
`jvmMain` actuals). Also from PoCoView: `Dialogs.kt` (Import, Export, Save, "Save changes?", Welcome, About),
`ExamplesDialog.kt` (tree, `ExamplesState`), `Help.kt`, `Svg.kt` (Export SVG). ArrView's own:
`AppState.kt` (state, undo, drafts, saving, background layout), `ArrangementCanvas.kt` (drawing,
clicks, zoom / pan), `ActionMenu.kt` (the flip menus of the canvas and the graph views), `Panels.kt` (toolbar), `Widgets.kt` (the widgets), `App.kt`. A new widget is
an entry of `Widget` plus a branch in `WidgetContent`.

## Example data

The examples are compiled into the app: `composeApp/examples/` holds one JSON file per set
(PoCoView's example format), `index.json` lists the groups in order (id, title, description, links,
files), the Gradle task `generateExamples` embeds all of them into `GeneratedExamples.kt`, and
`core/Examples.kt` parses them at first use (members are decoded when shown). `ExamplesTest` fails
if a file is not in the index or vice versa.

A file is the metadata, then the data:

```json
{
  "name": "n6-digonfree-triangle-saturated-2",
  "n": 6,
  "group": "Triangle-saturated arrangements and colorings",
  "label": "n = 6, no. 2",
  "description": "A triangle-saturated arrangement of 6 pseudocircles.",
  "links": [{"title": "Website: ...", "url": "..."}, {"title": "Paper: ...", "url": "..."}],
  "source": "website/arrangements/colorings/triangle_sat/n6_triangle_sat_no2.enc_1_1tutte7.ipe",
  "sequences": [[5, -2, ...], ...],
  "crossingColors": [["B", "G", ...], ...]
}
```

The encoding is one attribute: `"mod1s6"` or `"mod2s6"` (a sparse6 string, as on the website) or
`"sequences"`; `"crossingColors"` (only with sequences) gives a colour word per entry. A file with
several arrangements has `"arrangements": [{"mod1s6": "..."}, ...]` (one object per line) instead.
The app reads the same objects in Open file and Import (`readArrangementFile` in `core/Formats.kt`).
Names: `n<N>-<class>-<description>`; the dialog
shows a set's links only where they differ from its group's.

To refresh the data, run `python3 scripts/import_examples.py` (reads
`~/github/research/pca/pseudocircles/`, `--research` for another place; Koester's file needs
`sage`). It rewrites only the data of each file and keeps its metadata (a new file gets placeholders
and must be added to `index.json`). It takes the sparse6 lines verbatim, except the colour
dictionaries of the old sage code (`*.txt`, the dual graph with edges coloured by circle), which it
converts to model 1 sparse6, and the conversions to sequences below. The JSON files were converted
from the former plain files by `scripts/probe/ConvertExamplesProbe.kt` (one-off); the importer
reproduces them byte for byte.

Provenance (paths below `~/github/research/pca/pseudocircles/`; the website mirror there is
identical to `~/Documents/2024/TUB/pseudocircles/website/` for these files):

| `composeApp/examples/` (`.json`) | Source |
|---|---|
| `great/all{3..8}` | `computations/results/great/` |
| `intersecting/all{2..5}` | `computations/results/abstract/` (same as website `pseudocircles/intersecting/all{n}.mod1s6`) |
| `digonfree/all{3..6}_digonfree` | `computations/results/abstract/` (same as website `intersecting/digonfree/`) |
| `connected/all{3,4,5}` | `website/arrangements/pseudocircles/connected/` (NEW model 2 encoding) |
| `mintriangles/*` | `website/arrangements/triangles/digonfree_minimal_triangles/*.txt` (converted) |
| `maxtriangles/all*`, `some*` | `website/arrangements/triangles/digonfree_maximal_triangles/` |
| `maxtriangles/max8_digon_38_triangles`, `maxtriangles/max9_df_50_triangles`, `maxtriangles/some10_df_61_triangles` | `~/github/research/pca/data/maxtri/n8_p3eq38.txt`, `n9_p3eq50.txt`, `n10_p3ge61_partial.txt` (intersection sequences; the maxima for n = 8, 9, 10 by computer search, not on the website) |
| `fueredipalasti/fueredi_palasti_{6..42}` | `website/arrangements/triangles/fueredi_palasti_construction/` |
| `named/n5_nonr_number*` | `website/arrangements/nomorecircles/connected/` |
| `named/n6_nonr_number{1,2,3}*` | `website/arrangements/nomorecircles/intersecting/digonfree/` |
| `named/n6_nonr_all_triangles_krupp_number2`, `named/n6_nonr_edelsbrunnerramos`, `named/n9_nonr_pappus` | `computations/results/` |
| `named/n6_sym6_{2,3}`, `named/n6_connected_digonfree_sym*` | `website/arrangements/nomorecircles/{intersecting,connected}/additional/` |
| `digongraphs/*` | `website/arrangements/digongraphs/data/` (model 1, although the page says primal-dual graphs) |
| `colorings/n*_triangle_sat_*` | `website/arrangements/colorings/triangle_sat/*.enc_1_1tutte7.ipe` (sequences and crossing colours R, G, B read from the drawings; `ExampleColoursDataTest` checks them against the `.enc` files, copied to `jvmTest/resources/data/colorings/`) |
| `colorings/n8_koester` | `computations/biconnected_results/n8_koester.enc` (sparse6 of the crossing graph, converted by `scripts/crossing_graph_sequences.sage`; 7 circles despite the file name) |

## Tests

`./scripts/test.sh` runs everything (a few seconds once Gradle is warm). Single classes:

```
./gradlew :composeApp:jvmTest --tests 'org.bytefred.arrview.core.DataFilesTest'
```

The data tests run on the bundled examples (`ExamplesTest`, `RoundTripTest` in `commonTest`) and on
further research files in `composeApp/src/jvmTest/resources/data/` (`DataFilesTest`):

| Copy | Original | Checked |
|---|---|---|
| `stats/all{3,4,5}.enc.stat.txt` | `computations/results/stats/` | p-vectors of the model 1 lists |
| `fingerprints/all{3,4,5}.enc.fingerprints.txt` | `computations/program/` | sequences of the old sage code = model 1 lists |
| `model2_old/all{3,4,5}.mod2s6` (OLD hub encoding) | `computations/model2_results/abstract/` | same arrangements as the NEW encoding |
| `cylindrical/all5.mod2s6.notcylindrical.mod2s6` | `computations/model2_program/noncylindrical/` | `isCylindrical` |

`RenderTest` renders the app offscreen and writes screenshots to `composeApp/build/screenshots/`
(start example in both models and portrait, examples with digons, n = 24, n = 2, every widget, every
dialog and Help page): look at them after changing the UI. UI tests with real clicks:
`CanvasUiTest`, `CanvasInteractionUiTest`, `ActionsUiTest`, `WidgetsUiTest`, `WidgetButtonsUiTest`, `DialogsUiTest`,
`MenusAndDialogsUiTest`, `ExamplesDialogUiTest`; `HelpClaimsTest` checks the Help texts and this
user guide against the code.

### What the tests cover

| Area | Tests |
|---|---|
| Core combinatorics | `ArrangementTest`, `OperationsTest`, `LinhartOrtnerTest` (every rejection reason), `SymmetryTest`, `FormatsTest`, `FilesTest`, `DataFilesTest` |
| Examples | `ExamplesTest` (sizes, valid, distinct, class), `ExampleCatalogTest` (every member through sequences, JSON, text, model 1 / 2 sparse6; group claims: Krupp triples, digon-free, min / max triangles, Füredi–Palásti, named ones) |
| Drawing | `LayoutTest` (every example with n <= 5, both models; control points, weights, B-spline as in sage), `TutteSolveTest`, `SymmetryTest`, `ExampleDrawingTest` (n >= 6, long lists sampled, both models, SVG), `DrawingSweepTest` (flips of n <= 5, geometry checked by `CurveCheck`) |
| Flips, collapses, undo | `DigonCreateTest`, `ActionsUiTest` (digon create, graph view clicks, "!"), `RoundTripTest`, `AppStateTest`, `EditingSessionTest` (random walks with undo / redo on 7 examples, model after edits, drawing cache) |
| Editors and drafts | `EditingStateTest`, `EditingSessionTest` (invalid input with its reason), `WidgetsUiTest`, `WidgetButtonsUiTest` (Apply, Revert, Copy, stale drafts) |
| Menus | `MenusAndDialogsUiTest` (every item clicked), `MenuBarTest` |
| Dialogs | `ExamplesDialogUiTest` (a member of every set by clicks, member chooser), `DialogsUiTest`, `MenusAndDialogsUiTest` (Import, Export, Save file, Help, About, Welcome, error, "Save changes?" per trigger) |
| Canvas | `CanvasUiTest`, `RefitOnResizeUiTest` (refit on resize until a zoom or pan), `CanvasInteractionUiTest` (digon clicks in both models, refusal, face menus, hover, wheel, pinch, pan, dividers, clicks during a layout) |
| Keyboard | `CanvasInteractionUiTest` (every shortcut, off in menus and fields) |
| Widgets and toolbar | `WidgetsUiTest`, `WidgetButtonsUiTest` (Properties values, chips, close buttons, Layout fields) |
| Settings | `EditingSessionTest` (remembered and corrupt display settings), `AppStateTest`, `SplitPaneTest` |
| Files | `EditingSessionTest` (desktop read / write helpers, file names), `ExportTest` |
| Help texts | `HelpClaimsTest` (defaults, shortcuts, menus, colours, formats; also `doc/USER_GUIDE.md`) |

Not covered: the system file dialogs (Open file, Save JSON / text, Export SVG), which a test cannot
drive, and the macOS Quit handler in `main.kt`. `jsTest` is not part of `scripts/test.sh`: the
data-heavy common tests take longer than mocha's 2 s per test in the browser.

### Drawing sweep

`DrawingSweepTest` (in `jvmTest`) checks a representative part; the whole sweep (every
intersecting and connected arrangement with n <= 5 and all its flips, about 4.5 min) is excluded
from `jvmTest`:

```
./gradlew :composeApp:jvmTest -PfullSweep --tests '*DrawingFullSweepTest'
```

The report is `composeApp/build/sweep/full.txt`. `SweepScreenshotTest` writes screenshots and
zoomed SVGs of known cases to `composeApp/build/sweep/screenshots/`.

### Web check

`./scripts/buildWeb.sh`, then serve the site and drive headless Chrome with
`scripts/probe/web_cdp.py` (clicks, keys, screenshots, console exceptions):

```
python3 -m http.server 8611 --bind 127.0.0.1 --directory composeApp/build/dist/js/productionExecutable
"/Applications/Google Chrome.app/Contents/MacOS/Google Chrome" --headless=new --use-angle=swiftshader \
  --enable-unsafe-swiftshader --user-data-dir=<scratch>/chrome-prof --window-size=1200,800 \
  --remote-debugging-port=9333 http://127.0.0.1:8611/
python3 scripts/probe/web_cdp.py 9333 <out-dir> steps.json
```

## Deploy to GitHub Pages

```
./scripts/deployPages.sh
git push origin gh-pages
```

`deployPages.sh` builds the site, copies it into a worktree of the `gh-pages` branch (`.gh-pages/`,
created on first use) and commits it there. It does not push. The site is served from
https://scheucher.dev/ArrView/ (one-time setup on github.com: Settings, Pages, deploy
from branch `gh-pages`, root).
