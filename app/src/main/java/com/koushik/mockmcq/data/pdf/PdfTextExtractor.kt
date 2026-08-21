package com.koushik.mockmcq.data.pdf

import android.content.Context
import android.net.Uri
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.encryption.InvalidPasswordException
import com.tom_roush.pdfbox.text.PDFTextStripper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

sealed interface PdfExtractionResult {
    data class Success(val pages: List<String>) : PdfExtractionResult
    data class Failure(val message: String) : PdfExtractionResult
}

class PdfTextExtractor(private val context: Context) {
    suspend fun extract(uri: Uri, onProgress: (Int, Int) -> Unit): PdfExtractionResult =
        withContext(Dispatchers.IO) {
            try {
                PDFBoxResourceLoader.init(context)
                val input = context.contentResolver.openInputStream(uri)
                    ?: return@withContext PdfExtractionResult.Failure("The selected PDF could not be opened.")
                input.use { stream ->
                    PDDocument.load(stream).use { document ->
                        val total = document.numberOfPages
                        if (total == 0) {
                            return@withContext PdfExtractionResult.Failure("The PDF has no pages.")
                        }
                        val stripper = PDFTextStripper().apply { sortByPosition = true }
                        val pages = (1..total).map { page ->
                            stripper.startPage = page
                            stripper.endPage = page
                            val text = stripper.getText(document).orEmpty()
                            onProgress(page, total)
                            text
                        }
                        if (pages.all { it.isBlank() }) {
                            PdfExtractionResult.Failure(
                                "No selectable text was found. This PDF appears scanned; OCR is not included in this first build.",
                            )
                        } else {
                            PdfExtractionResult.Success(pages)
                        }
                    }
                }
            } catch (_: InvalidPasswordException) {
                PdfExtractionResult.Failure("Password-protected PDFs are not supported.")
            } catch (error: Exception) {
                PdfExtractionResult.Failure(error.message ?: "The PDF could not be processed.")
            }
        }
}
