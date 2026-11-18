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
| `Examples.kt` | catalogue of the bundled examples (data: `composeApp/examples/`) | |
| `Formats.kt` | text forms (sequences, model 1 / 2 sparse6), file formats (JSON, text), reading with format detection | |
| `Json.kt` | small strict JSON reader | PoCoView `core/Json.kt` |
| `Tutte.kt` | iterated weighted Tutte embedding (`IteratedTutte`, one embedding per `step()`) | `program_py3/draw7.sage`, `model2_program/draw3.sage` |
| `Layout.kt` | model 1 / model 2 graphs, outer face keys, curves, face boundaries, hit test (`Drawing`) | |
| `Symmetry.kt` | automorphisms of the map, default outer face | draw7.sage's maximal symmetry |

### UI

Ported from PoCoView (package renamed, PoCoView-specific parts dropped): `Theme.kt`,
`Menus.kt`, `SplitPane.kt`, `Scroll.kt`, `Prefs.kt`, `FileIo.kt`, `Platform.kt` (with `jsMain` /
`jvmMain` actuals). Also from PoCoView: `Dialogs.kt` (Import, Export, Save, "Save changes?", Welcome, About),
`ExamplesDialog.kt` (tree, `ExamplesState`), `Help.kt`, `Svg.kt` (Export SVG). ArrView's own:
`AppState.kt` (state, undo, drafts, saving, background layout), `ArrangementCanvas.kt` (drawing,
clicks, zoom / pan), `ActionMenu.kt` (the flip menus of the canvas and the graph views), `Panels.kt` (toolbar), `Widgets.kt` (the widgets), `App.kt`. A new widget is
an entry of `Widget` plus a branch in `WidgetContent`.

## Example data

The examples are compiled into the app: `composeApp/examples/` holds the data files (one sparse6
string per line; `.mod2s6` is model 2, `.enc`/`.mod1s6` model 1), the Gradle task
`generateExamples` embeds all of them into `GeneratedExamples.kt`, and `core/Examples.kt` names
and groups them. `ExamplesTest` fails if a file is not in the catalogue or vice versa.

To refresh the files, run `python3 scripts/import_examples.py` (reads
`~/github/research/pca/pseudocircles/`, `--research` for another place). It copies the files
verbatim, except the colour dictionaries of the old sage code (`*.txt`, the dual graph with
edges coloured by circle), which it converts to model 1 sparse6 (`.mod1s6`).

Provenance (paths below `~/github/research/pca/pseudocircles/`; the website mirror there is
identical to `~/Documents/2024/TUB/pseudocircles/website/` for these files):

| `composeApp/examples/` | Source |
|---|---|
| `great/all{3..8}.enc.greatcircles.enc` | `computations/results/great/` |
| `intersecting/all{2..5}.enc` | `computations/results/abstract/` (same as website `pseudocircles/intersecting/all{n}.mod1s6`) |
| `digonfree/all{3..6}_digonfree.enc` | `computations/results/abstract/` (same as website `intersecting/digonfree/`) |
| `connected/all{3,4,5}.mod2s6` | `website/arrangements/pseudocircles/connected/` (NEW model 2 encoding) |
| `mintriangles/*.mod1s6` | `website/arrangements/triangles/digonfree_minimal_triangles/*.txt` (converted) |
| `maxtriangles/*.enc` | `website/arrangements/triangles/digonfree_maximal_triangles/` |
| `fueredipalasti/fueredi_palasti_{6..42}.enc` | `website/arrangements/triangles/fueredi_palasti_construction/` |
| `named/n5_nonr_number*.mod2s6` | `website/arrangements/nomorecircles/connected/` |
| `named/n6_nonr_number{1,2,3}*.mod2s6` | `website/arrangements/nomorecircles/intersecting/digonfree/` |
| `named/n6_nonr_all_triangles_krupp_number2.enc`, `named/n6_nonr_edelsbrunnerramos.enc`, `named/n9_nonr_pappus.enc` | `computations/results/` |

Krupp, NonKrupp and 3-Chain are typed in as sequences in `Examples.kt`.

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
| Drawing | `LayoutTest` (every example with n <= 5, both models), `ExampleDrawingTest` (n >= 6, long lists sampled, both models, SVG) |
| Flips, collapses, undo | `DigonCreateTest`, `ActionsUiTest` (digon create, graph view clicks, "!"), `RoundTripTest`, `AppStateTest`, `EditingSessionTest` (random walks with undo / redo on 7 examples, model after edits, drawing cache) |
| Editors and drafts | `EditingStateTest`, `EditingSessionTest` (invalid input with its reason), `WidgetsUiTest`, `WidgetButtonsUiTest` (Apply, Revert, Copy, stale drafts) |
| Menus | `MenusAndDialogsUiTest` (every item clicked), `MenuBarTest` |
| Dialogs | `ExamplesDialogUiTest` (a member of every set by clicks, member chooser), `DialogsUiTest`, `MenusAndDialogsUiTest` (Import, Export, Save file, Help, About, Welcome, error, "Save changes?" per trigger) |
| Canvas | `CanvasUiTest`, `CanvasInteractionUiTest` (digon clicks in both models, refusal, face menus, hover, wheel, pinch, pan, dividers, clicks during a layout) |
| Keyboard | `CanvasInteractionUiTest` (every shortcut, off in menus and fields) |
| Widgets and toolbar | `WidgetsUiTest`, `WidgetButtonsUiTest` (Properties values, chips, close buttons, Layout fields) |
| Settings | `EditingSessionTest` (remembered and corrupt display settings), `AppStateTest`, `SplitPaneTest` |
| Files | `EditingSessionTest` (desktop read / write helpers, file names), `ExportTest` |
| Help texts | `HelpClaimsTest` (defaults, shortcuts, menus, colours, formats; also `doc/USER_GUIDE.md`) |

Not covered: the system file dialogs (Open file, Save JSON / text, Export SVG), which a test cannot
drive, and the macOS Quit handler in `main.kt`. `jsTest` is not part of `scripts/test.sh`: the
data-heavy common tests take longer than mocha's 2 s per test in the browser.

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
https://manfredscheucher.github.io/ArrView/ (one-time setup on github.com: Settings, Pages, deploy
from branch `gh-pages`, root).
