package com.example.fitlog.data.analysis.adapter

import com.example.fitlog.data.ai.AiProviderRepository
import com.example.fitlog.data.analysis.DiaryAnalysisRepository
import com.example.fitlog.data.analysis.DiaryParseInput
import com.example.fitlog.data.analysis.SourceKey
import com.example.fitlog.data.analysis.StoredDiaryParse
import com.example.fitlog.data.analysis.AnalysisSource
import com.example.fitlog.data.analysis.DiaryAnalysisSession
import com.example.fitlog.data.vault.MarkdownSnapshot
import com.example.fitlog.data.vault.MarkdownDocuments
import io.ktor.client.HttpClient
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal class DiaryOriginalReadException(cause: Exception) : IOException("Cannot read diary original", cause)

/** One immutable provider/credential snapshot for an entire explicitly started batch. */
internal suspend fun prepareConfiguredAnalysis(
    providers: AiProviderRepository,
    documents: MarkdownDocuments,
    analysis: DiaryAnalysisRepository,
    client: HttpClient,
    recordRead: suspend (AnalysisSource, MarkdownSnapshot) -> Unit,
): DiaryAnalysisSession {
    val selection = providers.readActive()
    val config = OpenAiModelConfig(selection.provider.baseUrl, selection.modelId)
    val parser = JsonDiaryParser(OpenAiDiaryModelSource(client, config, selection.apiKey))
    return object : DiaryAnalysisSession {
        override suspend fun read(source: AnalysisSource): DiaryParseInput {
            val snapshot = try { documents.read(source.documentUri) } catch (e: Exception) {
                if (e is CancellationException) throw e
                throw DiaryOriginalReadException(e)
            }
            recordRead(source, snapshot)
            return withContext(Dispatchers.Default) {
                DiaryParseInput.fromSnapshot(source.key, snapshot, config.extractorVersion())
            }
        }
        override suspend fun execute(input: DiaryParseInput) = analysis.parse(input, parser)
    }
}

/** One explicit attempt captures the saved selection before rereading the source; never writes Markdown. */
internal suspend fun parseConfiguredDiary(
    sourceKey: SourceKey,
    documentUri: String,
    providers: AiProviderRepository,
    documents: MarkdownDocuments,
    analysis: DiaryAnalysisRepository,
    client: HttpClient,
): StoredDiaryParse {
    val selection = providers.readActive()
    val config = OpenAiModelConfig(selection.provider.baseUrl, selection.modelId)
    val parser = JsonDiaryParser(OpenAiDiaryModelSource(client, config, selection.apiKey))
    val snapshot = try { documents.read(documentUri) } catch (e: Exception) {
        if (e is CancellationException) throw e
        throw DiaryOriginalReadException(e)
    }
    val input = withContext(Dispatchers.Default) {
        DiaryParseInput.fromSnapshot(sourceKey, snapshot, config.extractorVersion())
    }
    return analysis.parse(input, parser)
}
