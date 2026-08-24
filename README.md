# Mock MCQ Exam

Offline-first Android app for importing text-based MCQ PDFs, answering questions, autosaving every selection, resuming later, submitting, adding an answer key, calculating a score, and reviewing responses.

## First-build scope

- Android document picker; no broad storage permission
- On-device PDF text extraction
- Layered parser supporting `1.`, `1)`, `Q1`, `Q.1`, `Question 1`, inline `MCQ 1: ...`, `(a)`, `A.`, `A)`, `[A]`, and `A:` patterns
- Format-tolerant tagged-marker support for publisher prefixes, spaces/symbols, optional `#`, trailing punctuation, `Options`/`Choices` headings, and 4–6 choices
- Room persistence keyed by exam ID and question ID
- Immediate answer autosave, clear response, visited state, mark for review, question palette, and resume
- Non-destructive exam-state updates preserve imported questions when starting, navigating, resuming, and submitting
- Empty/corrupted sessions now show a recovery screen, and unexpected runtime crashes are reported on the next launch
- Persistent 180-minute active-use timer with per-second checkpoints; it pauses when the exam is left/backgrounded, safely recovers after process interruption, and restarts only when `Resume Test` is pressed
- Matching tagged answer-PDF import plus manual keys such as `1-C`, with missing keys reported rather than inferred
- Light/dark theme and content descriptions in the question palette

## Known limitation

This first build targets text-based PDFs. Scanned-only PDFs return a clear error because on-device OCR is not yet included. Complex columns, tables, images, and format-specific header/footer rules must be validated against an actual sample PDF. The parser never invents missing text and flags incomplete questions with lower confidence.

## Build

The GitHub Actions workflow installs the Android SDK, creates the Gradle 8.9 wrapper, runs unit tests, builds the debug APK, verifies that it is non-empty, and publishes `MockMCQExam.apk` with its SHA-256 checksum.

Local commands after generating the wrapper:

```bash
./gradlew clean
./gradlew testDebugUnitTest
./gradlew assembleDebug
```
