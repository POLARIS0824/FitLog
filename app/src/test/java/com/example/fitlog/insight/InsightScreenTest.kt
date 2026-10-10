package com.example.fitlog.insight

import androidx.compose.material3.Surface
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.core.net.toUri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelStore
import com.example.fitlog.R
import com.example.fitlog.data.analysis.ConfirmedDiaryReader
import com.example.fitlog.data.analysis.ConfirmedDiaryRecord
import com.example.fitlog.data.analysis.SourceKey
import com.example.fitlog.data.insight.*
import com.example.fitlog.data.vault.VaultConfigState
import com.example.fitlog.ui.theme.FitLogTheme
import java.io.IOException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.After
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp")
class InsightScreenTest {
    @get:Rule val compose = createComposeRule()
    private val models = ViewModelStore()
    private val aliases = MutableStateFlow(ExerciseAliases())
    private var failSave = false
    private val opened = mutableListOf<SourceKey>()
    private fun text(id: Int) = RuntimeEnvironment.getApplication().getString(id)
    private val aliasStore = object : ExerciseAliasStore {
        override fun observe(vaultId: String): Flow<ExerciseAliases> = aliases
        override suspend fun save(vaultId: String, previousPrimaryKey: String?, replacement: ExerciseAliasGroup?) {
            if (failSave) throw IOException()
            aliases.value = aliases.value.replacing(previousPrimaryKey, replacement)
        }
    }
    @After fun after() { compose.runOnUiThread { models.clear() } }

    private fun render(records: List<ConfirmedDiaryRecord>, exercise: Boolean = false): InsightViewModel {
        lateinit var vm: InsightViewModel
        compose.runOnUiThread {
            vm = InsightViewModel(MutableStateFlow(VaultConfigState.Configured("content://vault".toUri(), INSIGHT_VAULT)),
                { MutableStateFlow(insightIndex(*records.toTypedArray())) }, object : ConfirmedDiaryReader {
                    override fun observeConfirmedDiaries(vaultId: String) = MutableStateFlow(records)
                }, aliasStore, SavedStateHandle(), refreshIndex = { error("Opening a screen must not scan") },
                localDate = { INSIGHT_TODAY })
            models.put("insight", vm)
        }
        compose.setContent { FitLogTheme(dynamicColor = false) { Surface {
            if (exercise) ExerciseHistoryScreen(vm, "bench", opened::add, {}, {})
            else InsightScreen(vm, {}, opened::add, {})
        } } }
        compose.waitUntil(10_000) { vm.data != null }
        compose.waitForIdle()
        return vm
    }

    private fun scrollTo(label: String) {
        compose.onAllNodes(hasScrollToIndexAction()).onLast().performScrollToNode(hasText(label))
    }

    // Exercise dialog accessibility actions; physical touch delivery remains device acceptance.
    private fun SemanticsNodeInteraction.activate() = performSemanticsAction(SemanticsActions.OnClick) { it() }

    @Test fun dayWithOneDiaryOpensItDirectly() {
        render(listOf(insightRecord("one.md")))
        scrollTo(text(R.string.insight_view_days))
        compose.onNodeWithText(text(R.string.insight_view_days)).performClick()
        compose.onNode(hasText(insightDate(INSIGHT_TODAY)) and hasClickAction() and hasAnyAncestor(isDialog()))
            .assertIsDisplayed().activate()
        compose.runOnIdle { assertEquals(listOf(SourceKey(INSIGHT_VAULT, "one.md")), opened) }
    }

    @Test fun dayWithSeveralDiariesRequiresChoosingTheCorrectSource() {
        render(listOf(insightRecord("a.md"), insightRecord("b.md")))
        scrollTo(text(R.string.insight_view_days))
        compose.onNodeWithText(text(R.string.insight_view_days)).performClick()
        compose.onNode(hasText(insightDate(INSIGHT_TODAY)) and hasClickAction() and hasAnyAncestor(isDialog()))
            .assertIsDisplayed().activate()
        compose.runOnIdle { assertTrue(opened.isEmpty()) }
        compose.onNodeWithText("b.md").activate()
        compose.runOnIdle { assertEquals(listOf(SourceKey(INSIGHT_VAULT, "b.md")), opened) }
    }

    @Test fun aliasSaveFailureKeepsSelectionsAndManualSaveMergesTheRecords() {
        val vm = render(listOf(insightRecord("a.md"), insightRecord("b.md", name = "卧推")), exercise = true)
        compose.onNodeWithText(text(R.string.insight_manage_aliases)).performClick()
        compose.onNodeWithText("卧推").activate()
        scrollTo(text(R.string.insight_alias_save))
        failSave = true
        compose.onNodeWithText(text(R.string.insight_alias_save)).activate()
        scrollTo(text(R.string.insight_alias_save_failed))
        compose.onNodeWithText(text(R.string.insight_alias_save_failed)).assertIsDisplayed()
        compose.runOnIdle { assertTrue(aliases.value.groups.isEmpty()) }
        failSave = false
        compose.onNodeWithText(text(R.string.insight_alias_save)).activate()
        compose.waitUntil(10_000) { vm.data?.aliases?.groups?.size == 1 }
        compose.onNodeWithText(text(R.string.insight_alias_cancel)).assertDoesNotExist()
        compose.runOnIdle {
            assertEquals(setOf("bench", "卧推"), aliases.value.groups.single().members.toSet())
            assertEquals(2, vm.data!!.exercise("bench", vm.range)!!.occurrences.size)
        }
    }
}
