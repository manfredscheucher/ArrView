# ArrView

View and explore arrangements of pseudocircles, right in the browser.

**Web app:** https://manfredscheucher.github.io/ArrView/

ArrView reads the intersection sequences and the sparse6 encodings (model 1 and model 2) of the
pseudocircles website, checks validity (Linhart–Ortner), and computes faces, p-vector, the
intersection graph and the classes (intersecting, digon-free, great, cylindrical). Triangles can be
flipped and digons collapsed. It is written in Kotlin Multiplatform (Compose) and also runs as a
desktop app.

- Specification and decisions: [doc/SPEC.md](doc/SPEC.md)
- Building, testing, deploying: [doc/DEVELOPMENT.md](doc/DEVELOPMENT.md)

## License

MIT, see [LICENSE](LICENSE).
