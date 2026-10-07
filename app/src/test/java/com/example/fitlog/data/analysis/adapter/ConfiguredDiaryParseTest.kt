package com.example.fitlog.data.analysis.adapter

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.room.Room
import com.example.fitlog.data.ai.*
import com.example.fitlog.data.analysis.*
import com.example.fitlog.data.vault.*
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.toByteArray
import io.ktor.http.HttpHeaders
import java.io.File
import java.time.LocalDate
import java.util.Base64
import kotlinx.coroutines.*
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ConfiguredDiaryParseTest {
    @get:Rule val folder = TemporaryFolder()
    private lateinit var database: DiaryAnalysisDatabase
    private lateinit var analysis: DiaryAnalysisRepository
    private val source = SourceKey("00000000-0000-4000-8000-000000000001", "daily/note.md")
    private val connection = AiProviderConnection("one", "DeepSeek", "https://api.deepseek.com", listOf("a", "b"))
    @Before fun before() {
        database = Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), DiaryAnalysisDatabase::class.java).build()
        analysis = DiaryAnalysisRepository(database)
    }
    @After fun after() { database.close() }

    @Test fun savedSelectionReachesActualRequestAndReparsingPreservesConfirmedValues() = runTest {
        val settings = repository(backgroundScope)
        settings.save(connection, "key-one", AiModelSelection("one", "a"))
        val documents = Documents()
        val requested = mutableListOf<String>()
        createAiHttpClient(MockEngine { request ->
            assertEquals("Bearer key-one", request.headers[HttpHeaders.Authorization])
            val body = Json.parseToJsonElement(request.body.toByteArray().decodeToString()).jsonObject
            requested += body.getValue("model").jsonPrimitive.content
            assertEquals(documents.text, body.getValue("messages").jsonArray[1].jsonObject.getValue("content").jsonPrimitive.content)
            respond(completion(ANSWER))
        }).use { client ->
            val first = parseConfiguredDiary(source, "content://document", settings, documents, analysis, client)
            val firstCandidate = (first.result as DiaryParseResult.Success).analysis
            assertEquals(2, firstCandidate.sessions.single().exercises.single().sets.size)
            assertTrue(firstCandidate.issues.isEmpty())
            assertTrue(analysis.confirm(DiaryConfirmation.fromCandidate(first.parseRunId, firstCandidate,
                LocalDate.of(2026, 10, 7))) is DiaryConfirmationResult.Confirmed)
            settings.selectModel(AiModelSelection("one", "b"))
            val second = parseConfiguredDiary(source, "content://document", settings, documents, analysis, client)
            assertNotEquals(firstCandidate.parseKey.extractorVersion,
                (second.result as DiaryParseResult.Success).analysis.parseKey.extractorVersion)
            assertEquals(first.parseRunId, analysis.confirmed(source)!!.diary.parseRunId)
            assertEquals(2, analysis.readParses(source).attempts.size)
        }
        assertEquals(listOf("a", "b"), requested)
        assertEquals(2, documents.reads)
    }

    @Test fun sourceReadCannotChangeCapturedConnectionModelOrCredential() = runTest {
        val settings = repository(backgroundScope)
        settings.save(connection, "old-key", AiModelSelection("one", "a"))
        val documents = Documents().apply { gate = CompletableDeferred() }
        createAiHttpClient(MockEngine { request ->
            assertEquals("https://api.deepseek.com/chat/completions", request.url.toString())
            assertEquals("Bearer old-key", request.headers[HttpHeaders.Authorization])
            val body = Json.parseToJsonElement(request.body.toByteArray().decodeToString()).jsonObject
            assertEquals("a", body.getValue("model").jsonPrimitive.content)
            respond(completion(ANSWER))
        }).use { client ->
            val pending = async { parseConfiguredDiary(source, "content://document", settings, documents, analysis, client) }
            documents.started.await()
            settings.save(connection.copy(baseUrl = "https://new.example"), "new-key", AiModelSelection("one", "b"))
            documents.gate!!.complete(Unit)
            assertTrue(pending.await().result is DiaryParseResult.Success)
        }
        assertEquals("new-key", settings.readActive().apiKey)
    }

    @Test fun missingConfigurationUnreadableOriginalAndUnavailableDatabaseDoNotCallModel() = runTest {
        val settings = repository(backgroundScope)
        val documents = Documents()
        var requests = 0
        createAiHttpClient(MockEngine { requests++; respond(completion(ANSWER)) }).use { client ->
            try {
                parseConfiguredDiary(source, "content://document", settings, documents, analysis, client)
                fail("Missing configuration must fail")
            } catch (e: AiConfigurationException) { assertEquals(AiConfigurationFailure.NOT_CONFIGURED, e.reason) }
            assertEquals(0, documents.reads)
            settings.save(connection, "key", AiModelSelection("one", "a"))
            documents.fail = true
            try {
                parseConfiguredDiary(source, "content://document", settings, documents, analysis, client)
                fail("Source read must fail")
            } catch (_: DiaryOriginalReadException) { }
            documents.fail = false
            val context = RuntimeEnvironment.getApplication()
            val name = "unavailable-analysis.db"
            context.openOrCreateDatabase(name, 0, null).use {
                it.execSQL("CREATE TABLE incompatible_schema (value TEXT)"); it.version = 1
            }
            val incompatible = Room.databaseBuilder(context, DiaryAnalysisDatabase::class.java, name).build()
            try {
                parseConfiguredDiary(source, "content://document", settings, documents, DiaryAnalysisRepository(incompatible), client)
                fail("Incompatible database must fail")
            } catch (_: DiaryAnalysisStorageException) { }
            finally { incompatible.close(); context.deleteDatabase(name) }
        }
        assertEquals(0, requests)
    }

    private fun repository(scope: CoroutineScope) = AiProviderRepository(
        PreferenceDataStoreFactory.create(scope = scope, produceFile = { File(folder.root, "config.preferences_pb") }),
        { Base64.getEncoder().encodeToString(it.toByteArray()) }, { String(Base64.getDecoder().decode(it)) },
    )

    private class Documents : MarkdownDocuments {
        val text = "卧推 40kg 2x8"; var reads = 0; var fail = false
        var gate: CompletableDeferred<Unit>? = null
        val started = CompletableDeferred<Unit>()
        override suspend fun read(uri: String): MarkdownSnapshot {
            assertEquals("content://document", uri); reads++; started.complete(Unit); gate?.await()
            if (fail) throw java.io.IOException()
            return MarkdownSnapshot(MarkdownFile(uri, "note.md", "daily/note.md", false, "content://daily"), text, "bytes", false)
        }
        override suspend fun scan(vault: String): MarkdownScan = error("No scan expected")
        override suspend fun find(vault: String, name: String): MarkdownFile? = error("No lookup expected")
        override suspend fun create(vault: String, name: String): MarkdownFile = error("No creation expected")
        override suspend fun write(uri: String, text: String, bom: Boolean, expected: String): MarkdownSnapshot = error("No write expected")
    }

    private fun completion(answer: String) = """{"choices":[{"message":{"content":${Json.encodeToString(answer)}},"finish_reason":"stop"}]}"""
    private companion object {
        const val ANSWER = """{"schemaVersion":1,"sessions":[{"date":null,"exercises":[{"rawName":"卧推","groups":[{"weight":40,"unit":"KG","basis":"UNKNOWN","count":2,"reps":8}]}]}]}"""
    }
}
