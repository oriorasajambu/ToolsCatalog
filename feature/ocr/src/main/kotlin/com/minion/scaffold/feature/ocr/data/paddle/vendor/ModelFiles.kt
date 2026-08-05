/*
 * The one declaration kept from ente-io/mobile_ocr's ModelManager.kt
 * (@ 37aee4c4ff77c59a4ab46e272e31a53a035f628e), which was otherwise not vendored — it downloads the
 * models over HTTP, while this app ships them in the APK and extracts them with `PaddleModelAssets`.
 *
 * It lives in this package because `OcrProcessor` names it unqualified, and moving it would mean
 * editing a file that is deliberately kept byte-comparable to upstream.
 *
 * MIT License — Copyright (c) 2025 Laurens Priem. Full text in LICENSE beside this file.
 */
package com.minion.scaffold.feature.ocr.data.paddle.vendor

import java.io.File

data class ModelFiles(
    val version: String,
    val baseDir: File,
    val detectionModel: File,
    val recognitionModel: File,
    val classificationModel: File,
    val dictionaryFile: File,
)
