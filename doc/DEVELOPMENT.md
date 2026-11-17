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
| `LinhartOrtner.kt` | validity with reasons (Def. 2.4/2.5, Thm. 4.5, Thm. 5.4) | Linhart–Ortner, EJC 11 (2004) #R30 |
| `Canonical.kt` | canonical form (lex-min over all starts, mirror identified) | rusty-arrangements `src/canonical.rs` |
| `Sparse6.kt` | sparse6 decode/encode (nauty format) | `scripts/probe/model2.rs` (decoder) |
| `Models.kt` | model 1 (dual graph) and model 2 (vertex-edge-face graph, NEW and OLD encoding) | `program/basics.py`, `model2_program/model2basics.sage`, `model2.rs` |
| `PlanarEmbedding.kt` | faces of a planar 2-connected graph (Demoucron–Malgrange–Pertuiset), for model 1 | (new; sage used its planarity test) |
| `PlaneGraph.kt`, `Graphs.kt` | embedded graphs: primal graph, vertex-edge-face graph | |
| `Operations.kt` | intersection graph, class tests (Krupp, great, cylindrical, …), triangle flip, digon collapse, relabel/reverse/mirror | `flip_potential.rs::flip`, `test_*.sage` |

## Tests

`./scripts/test.sh` runs everything (a few seconds once Gradle is warm). Single classes:

```
./gradlew :composeApp:jvmTest --tests 'org.bytefred.arrview.core.DataFilesTest'
```

The data tests (`jvmTest`) read copies of the result files for n <= 5 (great circles up to 7) in
`composeApp/src/jvmTest/resources/data/`, taken from `~/github/research/pca/pseudocircles/`:

| Copy | Original |
|---|---|
| `model1/all{2,3,4,5}.enc`, `model1/all{3,4,5}_digonfree.enc`, `stats/all{3,4,5}.enc.stat.txt` | `computations/results/abstract/` |
| `fingerprints/all{3,4,5}.enc.fingerprints.txt` | `computations/program/` |
| `great/all{3..7}.enc.greatcircles.enc` | `computations/results/great/` |
| `model2_old/all{3,4,5}.mod2s6` (OLD encoding) | `computations/model2_results/abstract/` |
| `model2_new/all{3,4,5}.mod2s6` (NEW encoding) | `website/arrangements/pseudocircles/connected/` |

## Deploy to GitHub Pages

```
./scripts/deployPages.sh
git push origin gh-pages
```

`deployPages.sh` builds the site, copies it into a worktree of the `gh-pages` branch (`.gh-pages/`,
created on first use) and commits it there. It does not push. The site is served from
https://manfredscheucher.github.io/ArrView/ (one-time setup on github.com: Settings, Pages, deploy
from branch `gh-pages`, root).
