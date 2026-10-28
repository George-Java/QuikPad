# Repository Guidelines

## Project Structure & Module Organization

Liquid Ink 2.0 is an offline Markdown editor built with Flutter. Active development belongs in `flutter_app/`:

- `lib/core/`: settings and local document storage.
- `lib/markdown/`: immutable block models, parsing, and inline scanning.
- `lib/ui/` and `lib/widgets/`: screens, preview, themes, and shared widgets.
- `test/`: parser, scrolling, outline, settings, and contrast tests.
- `assets/`: bundled Markdown; `android/`: Android host and resources.

The root `app/` and Gradle project contain archived Kotlin/Compose 1.x code. `docs/` contains design and validation notes, `samples/` contains example Markdown, and `dist/` contains packaged APKs.

## Build, Test, and Development Commands

Run these from `flutter_app/` with Flutter on `PATH`. On this Windows workspace, substitute `..\.tooling\flutter-sdk\flutter\bin\flutter.bat` for `flutter`; see the root `README.md` for JDK and Android SDK setup.

- `flutter pub get`: resolve dependencies.
- `flutter run`: launch on a connected Android device or emulator.
- `flutter analyze`: run static analysis and configured lints.
- `dart format lib test`: format Dart sources and tests.
- `flutter test`: run all Flutter tests.
- `flutter build apk --release`: produce the release APK.

## Coding Style & Naming Conventions

Use two-space Dart indentation, `snake_case.dart` filenames, `UpperCamelCase` types, and `lowerCamelCase` members. Follow `dart format` and `analysis_options.yaml`, which enables `flutter_lints`, const preferences, and `avoid_print`. Keep parsing separate from UI and storage.

Before changing preview rendering or outline navigation, read `docs/FLUTTER_REWRITE.md`. Preserve lazy block rendering and navigation by block index.

## Testing Guidelines

Use `flutter_test` with descriptive `test`/`testWidgets` names in `test/*_test.dart`. Add regression coverage for changed behavior, especially malformed Markdown, long-document scrolling, outline jumps, and settings persistence. Run a focused test with `flutter test test/markdown_parser_test.dart`, then the full suite and analyzer before submitting. No coverage threshold is configured. Validate file picking, keyboard behavior, and TalkBack on Android when affected.

## Commit & Pull Request Guidelines

This checkout has no Git history available to establish commit conventions. Use concise, imperative subjects, such as `Fix outline navigation for long documents`. Keep changes focused. PR descriptions should explain behavior changes, link relevant issues, list validation performed, and include screenshots for UI changes.

## Configuration

Keep SDK paths and credentials local. Release builds currently use debug signing; configure production signing before distribution through a store.
