# Vendored PaddleOCR pipeline

Copied from [ente-io/mobile_ocr](https://github.com/ente-io/mobile_ocr) at commit
`37aee4c4ff77c59a4ab46e272e31a53a035f628e` (2026-07-16). MIT License, © 2025 Laurens Priem — full
text in [LICENSE](LICENSE) beside this file.

## Why this is here rather than written from scratch

PaddleOCR has no official Android SDK. Running its ONNX exports means hand-writing image
normalisation, DB detection post-processing (binarise → trace contours → unclip the polygons),
angle classification and CTC decoding — several thousand lines of tensor arithmetic where a subtly
wrong constant produces plausible-looking garbage rather than an error. Ente's implementation is
already shipping, so this starts from code known to work rather than from a translation of the
Python reference.

Notably it depends on **no OpenCV** — the contour tracing and polygon unclipping are plain Kotlin,
which is the only reason this was portable at all.

## Deliberately unmodified

Two changes were made, both mechanical:

- package renamed to `com.minion.scaffold.feature.ocr.data.paddle.vendor`
- the `TextRecognizer` class renamed to `PaddleRecognitionModel`, freeing that name for this
  feature's own `TextRecognizer` seam

Nothing else. Keeping this byte-comparable to upstream means a future re-sync is a diff rather than
an archaeology exercise. **Resist tidying it** — it does not follow this repo's conventions and is
not meant to.

`MobileOcrPlugin.kt` (Flutter method-channel plumbing) and `ModelManager.kt` (downloads models over
HTTP) were not copied; the models here ship in the APK and are extracted by `PaddleModelAssets`.

## The long-term plan

The genuinely pure parts — polygon unclipping, CTC decoding, box geometry — are arithmetic on
numbers, and belong in `:core:ocr` where they can be unit-tested against fixture tensors like every
other algorithm in this codebase. They are here for now because the working version came first.
Each piece lifted out should arrive in `:core:ocr` with tests; this directory should shrink over
time.

## Testing

None of this is reachable from a JVM unit test: ONNX Runtime's native library only loads on a
device. It is verified by driving a signed release build on real hardware, which is this repo's
standing gate for release-only failures.
