# QuikPad 2.3 · Flutter

Offline-first Markdown reader/editor. No login, no cloud initialization, no
uploaded documents or images.

## Current features

- Local `.md` / `.markdown` / `.txt` import; private working copies; auto-save,
  save, export, rename and delete.
- Source / split / preview modes.
- H1–H3 outline using real block indexes and `ScrollablePositionedList`.
- Multiple reading backgrounds (including parchment), independent of global
  light/dark; liquid-glass switch, intensity and motion controls.
- CommonMark/GFM parsing through `markdown` + `html`: raw HTML headings, links,
  images, tables, lists, blockquotes, code, entities, autolinks, task lists,
  footnotes.
- Relative local images beside the current Markdown document. Android SAF
  sibling access is attempted; where the provider denies it, a one-time
  persistent folder association is offered.
- Code fences highlighted through `highlight`/`allLanguages` with bounded
  windows: C/C++, Python, Java, PHP, JavaScript/TypeScript, HTML/XML, CSS,
  YAML/TOML/INI, Shell/PowerShell, Go, Rust, Kotlin, Swift, Dart, SQL, JSON and
  180+ more.
- Dark-mode glass edge: continuous neutral overlay ring, top-only sheen,
  reduced shadow and no deformation on non-interactive cards.

## Layout

```
lib/
├── main.dart / app.dart
├── core/        settings, document store, local assets, diagnostics
├── markdown/    model, CommonMark/GFM parser, inline parser, highlighter
├── ui/          home, editor, preview, outline, settings, theme
└── widgets/     glass/refractive surface, images, code text
test/            parser/HTML/image/scroll/outline/glass/settings tests
assets/          logo, welcome, glass maps
shaders/         liquid_glass.frag
```

## Run and verify

From this directory, using the local SDK in this workspace:

```powershell
..\.tooling\flutter-sdk\flutter\bin\flutter.bat pub get
..\.tooling\flutter-sdk\flutter\bin\flutter.bat analyze
..\.tooling\flutter-sdk\flutter\bin\flutter.bat test
..\.tooling\flutter-sdk\flutter\bin\flutter.bat build apk --release
```

Current result: `flutter analyze` 0 issues; `flutter test` 51 passed, 1 skipped
(the host does not expose Impeller shader image filters).

The release APK is published at `../dist/QuikPad-2.3.0.apk`.
See `../docs/QUIKPAD_2_3.md` for the edge-line root cause, HTML/image/highlighting
details, and device acceptance notes.
