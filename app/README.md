# Java Design Patterns app (Flutter)

An Android/iOS (and web) app that shows the playbook offline: six levels, every topic page with its runnable Java examples, a table of contents per page, search, bookmarks and reading progress.

## Where the content comes from

The app doesn't have its own copy of the writing. `tools/build_app_content.py` reads `mkdocs.yml` and `docs/`, pulls in the real example files, converts MkDocs-only syntax (admonitions, tabs, code annotations) to plain Markdown, and writes `app/assets/content/`. After changing any page:

```bash
python3 tools/build_app_content.py
```

CI fails if `app/assets/content` is out of date.

## Run it

```bash
cd app
flutter pub get
flutter run                 # on a connected Android/iOS device or emulator
flutter run -d chrome       # in the browser
flutter test
flutter build apk --release # Android APK in build/app/outputs/flutter-apk/
```

Every CI run on a pull request also builds a release APK and attaches it to the run as the `java-patterns-apk` artifact, so you can install it on an Android phone without a local Android SDK.

## How it's built (and the patterns it uses)

| Part | File | Pattern |
|------|------|---------|
| Content loading | `lib/content/content_repository.dart` | Repository (swap assets for a network source later) |
| Reading progress and bookmarks | `lib/progress/progress_store.dart` | Observer (`ChangeNotifier`) |
| Wiring | `lib/main.dart`, `lib/app_scope.dart` | Composition root + dependency injection via `InheritedWidget` |
| Code blocks | `lib/widgets/code_block.dart` | Decorator (wraps each block with a copy button) |

Diagrams are drawn on the website; the app shows a note in their place for now.
