package com.example.fitlog.data.analysis.adapter

import com.example.fitlog.data.ai.AiProviders
import com.example.fitlog.data.ai.transport.OpenAiChatClientFactory
import com.example.fitlog.data.analysis.AnalysisSource
import com.example.fitlog.data.analysis.DiaryAnalysisSession
import com.example.fitlog.data.analysis.DiaryParseInput
import com.example.fitlog.data.analysis.StoredDiaryParse
import com.example.fitlog.data.vault.MarkdownDocuments
import com.example.fitlog.data.vault.MarkdownSnapshot
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** One immutable provider/credential snapshot for an entire explicitly started batch. */
internal class DiaryAnalysisSessionFactory(
    private val providers: AiProviders,
    private val documents: MarkdownDocuments,
    private val executeParse: suspend (DiaryParseInput, RecordingDiaryParser) -> StoredDiaryParse,
    private val chats: OpenAiChatClientFactory,
    private val computationDispatcher: kotlinx.coroutines.CoroutineDispatcher = Dispatchers.Default,
) {
    suspend fun prepare(recordRead: suspend (AnalysisSource, MarkdownSnapshot) -> Unit): DiaryAnalysisSession {
        val selection = providers.readActive()
        val modelId = selection.modelId
        val parser = JsonDiaryParser(
            OpenAiDiaryModelSource(chats.bind(selection.provider.baseUrl, selection.apiKey), modelId)
        )
        return object : DiaryAnalysisSession {
            override suspend fun read(source: AnalysisSource): DiaryParseInput {
                val snapshot = try { documents.read(source.documentUri) } catch (e: Exception) {
                    if (e is CancellationException) throw e
                    throw DiaryOriginalReadException(e)
                }
                recordRead(source, snapshot)
                return withContext(computationDispatcher) {
                    DiaryParseInput.fromSnapshot(
                        source.key,
                        snapshot,
                        diaryExtractorVersion(selection.provider.baseUrl, modelId),
                    )
                }
            }
            override suspend fun execute(input: DiaryParseInput) = executeParse(input, parser)
        }
    }
}
