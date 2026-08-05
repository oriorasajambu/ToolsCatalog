# PP-OCRv5 models

The PaddleOCR engine's weights, bundled rather than downloaded so the tool keeps working with no
network — the same choice already made for ML Kit's bundled model.

Fetched from `https://models.ente.io/PP-OCRv5/` and verified against the SHA-256 digests published
in [ente-io/mobile_ocr](https://github.com/ente-io/mobile_ocr)'s `ModelManager.kt`:

| File | Bytes | SHA-256 |
|---|---|---|
| `det.onnx` | 4,748,769 | `d7fe3ea74652890722c0f4d02458b7261d9f5ae6c92904d05707c9eb155c7924` |
| `rec.onnx` | 16,517,247 | `bf66820f48fa99f779974c4df78e5274a9d8e0458c4137e8c5357e40e2c3faf2` |
| `cls.onnx` | 582,663 | `f4bb53707100c5f3d59ba834eb05bb400369f20aed35d4b26807b1bfadd2a70e` |
| `ppocrv5_dict.txt` | 74,012 | `d1979e9f794c464c0d2e0b70a7fe14dd978e9dc644c0e71f14158cdf8342af1b` |

`det` finds text regions, `cls` corrects 180°-rotated crops, `rec` reads them, and the dictionary
maps the recogniser's CTC output indices back to characters. Its ~18,000 entries cover CJK as well
as Latin; that capability is neither advertised nor tested here — the reading-order grouping in
`:core:ocr` assumes left-to-right layout.

The `.onnx` files are stored via Git LFS (see `/.gitattributes`). A clone without `git-lfs` gets
pointer files, and PaddleOCR will fail to start and fall back to ML Kit.

**Licence.** The models are derived from [PaddleOCR](https://github.com/PaddlePaddle/PaddleOCR),
Apache License 2.0, © PaddlePaddle Authors. They are redistributed here unmodified.

There is no verification of these files at runtime: they ship inside a signed APK, so integrity is
already guaranteed by the package signature. The digests above are for re-fetching, not for the app.
