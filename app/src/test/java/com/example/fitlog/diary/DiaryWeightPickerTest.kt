package com.example.fitlog.diary

import androidx.compose.material3.Surface
import androidx.compose.foundation.layout.heightIn
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import com.example.fitlog.R
import com.example.fitlog.data.analysis.WeightUnit
import com.example.fitlog.data.analysis.WeightBasis
import androidx.compose.ui.text.TextLayoutResult
import com.example.fitlog.ui.theme.FitLogTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class DiaryWeightPickerTest {
    @get:Rule val compose = createComposeRule()
    private val context get() = RuntimeEnvironment.getApplication()
    private var applied: DiaryWeightValue? = null
    private var dismissed = false

    private fun show(weight: Double? = 60.0, unit: WeightUnit? = WeightUnit.KG, basis: WeightBasis? = null,
        fontScale: Float = 1f, height: Dp = 840.dp) {
        compose.setContent { CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, fontScale)) {
        FitLogTheme(dynamicColor = false) { Surface(Modifier.heightIn(max = height)) {
            DiarySetPickerContent(DiarySetEdit(DiarySetAddress(0, 0, 0), 1, DiarySetField.WEIGHT, weight, unit, 8, basis),
                onDismiss = { dismissed = true }, onWeight = { applied = it }, onReps = { error("Weight sheet changed reps") })
        } } } }
    }

    private fun click(text: String) = compose.onNodeWithText(text)
        .performSemanticsAction(SemanticsActions.OnClick) { it() }
    private fun text(id: Int) = context.getString(id)

    @Test fun poundsUseTheirOwnGridAndUnitIsCommittedOnlyOnDone() {
        show(100.0, WeightUnit.LB)
        compose.onNodeWithText("lb").assertIsOn()
        compose.onNodeWithContentDescription("105 lb").assertExists()
        assertNull(applied)
        click(text(R.string.detail_picker_done))
        assertEquals(DiaryWeightValue(100.0, WeightUnit.LB), applied)
    }

    @Test fun switchingKnownUnitsKeepsEquivalentMassWithoutRoundingToTheGrid() {
        show()
        click("lb")
        assertNull(applied)
        click(text(R.string.detail_picker_done))
        assertEquals(WeightUnit.LB, applied!!.unit)
        assertEquals(132.27735731092655, applied!!.weight!!, 1e-12)
    }

    @Test fun switchingBackRestoresTheExactOriginalValueDespitePickerRecomposition() {
        show(62.300000000004)
        click("lb"); click("kg")
        click(text(R.string.detail_picker_done))
        assertEquals(DiaryWeightValue(62.300000000004, WeightUnit.KG), applied)
    }

    @Test fun exactInputParticipatesInUnitConversionAndIsPreservedOnReturning() {
        show()
        click(text(R.string.detail_exact_input))
        compose.onNode(hasSetTextAction()).performTextReplacement("62.3")
        click("lb"); click("kg")
        click(text(R.string.detail_picker_done))
        assertEquals(DiaryWeightValue(62.3, WeightUnit.KG), applied)
    }

    @Test fun missingUnitsDefaultToKgWithAnExplicitWarningAndConvertWhenChanged() {
        show(60.0, null)
        compose.onNodeWithText("kg").assertIsOn()
        compose.onNodeWithText(text(R.string.detail_weight_assumed_kg)).assertExists()
        click("lb"); click(text(R.string.detail_picker_done))
        assertEquals(132.27735731092655, applied!!.weight!!, 1e-12)
        assertEquals(WeightUnit.LB, applied!!.unit)
    }

    @Test fun unrecordedHasNoNumericControlsAndNumericRequiresAnEnteredValue() {
        show(null, WeightUnit.UNKNOWN)
        compose.onNodeWithText(text(R.string.detail_weight_unrecorded)).assertIsOn()
        compose.onNodeWithText("kg").assertDoesNotExist()
        compose.onNodeWithText(text(R.string.detail_exact_input)).assertDoesNotExist()
        click(text(R.string.detail_weight_numeric))
        compose.onNodeWithText("kg").assertIsOn()
        compose.onNodeWithContentDescription(text(R.string.detail_weight_unfilled)).assertExists()
        compose.onNodeWithText(text(R.string.detail_picker_done)).assertIsNotEnabled()
        click(text(R.string.detail_exact_input))
        compose.onNode(hasSetTextAction()).performTextReplacement("0")
        compose.onNodeWithText(text(R.string.detail_picker_done)).assertIsEnabled()
        click(text(R.string.detail_picker_done))
        assertEquals(DiaryWeightValue(0.0, WeightUnit.KG), applied)
    }

    @Test fun changingIntervalKeepsTheSelectedOffGridValueAndAllowsAPositiveCustomInterval() {
        show(62.3, WeightUnit.KG)
        click(context.getString(R.string.detail_weight_step, "2.5 kg"))
        click("10 kg")
        compose.onNodeWithText(context.getString(R.string.detail_weight_step, "10 kg")).assertExists()
        click(context.getString(R.string.detail_weight_step, "10 kg"))
        click(text(R.string.detail_weight_step_custom))
        compose.onNode(hasSetTextAction()).performTextReplacement("1.25")
        click(text(R.string.detail_weight_step_apply))
        compose.onNodeWithText(context.getString(R.string.detail_weight_step, "1.25 kg")).assertExists()
        assertNull(applied)
        click(text(R.string.detail_picker_done))
        assertEquals(DiaryWeightValue(62.3, WeightUnit.KG), applied)
    }

    @Test fun invalidIntervalCannotBeAppliedAndCancelLeavesTheCurrentIntervalAlone() {
        show()
        click(context.getString(R.string.detail_weight_step, "2.5 kg"))
        click(text(R.string.detail_weight_step_custom))
        compose.onNode(hasSetTextAction()).performTextReplacement("0")
        compose.onNodeWithText(text(R.string.detail_weight_step_apply)).assertIsNotEnabled()
        compose.onNode(hasText(text(R.string.editor_cancel)) and hasAnyAncestor(hasTestTag("weight-step-editor")))
            .performSemanticsAction(SemanticsActions.OnClick) { it() }
        compose.onNodeWithText(context.getString(R.string.detail_weight_step, "2.5 kg")).assertExists()
        assertNull(applied)
    }

    @Test fun cancellingAfterChangingUnitAndExactValueDoesNotCommitAnything() {
        show()
        click("lb"); click(text(R.string.detail_exact_input))
        compose.onNode(hasSetTextAction()).performTextReplacement("135")
        click(text(R.string.editor_cancel))
        assertTrue(dismissed); assertNull(applied)
    }

    @Test fun bodyweightHasNoUnitIntervalOrInputAndSavesItsMeaningWithoutANumber() {
        show(null, WeightUnit.UNKNOWN, WeightBasis.BODYWEIGHT)
        compose.onNodeWithText(text(R.string.detail_weight_bodyweight)).assertIsOn()
        compose.onNodeWithText(text(R.string.detail_weight_bodyweight_help)).assertExists()
        compose.onNodeWithText("kg").assertDoesNotExist()
        compose.onNodeWithText(text(R.string.detail_exact_input)).assertDoesNotExist()
        compose.onNodeWithText(context.getString(R.string.detail_weight_step, "2.5 kg")).assertDoesNotExist()
        click(text(R.string.detail_picker_done))
        assertEquals(DiaryWeightValue(null, null, basis = WeightBasis.BODYWEIGHT), applied)
    }

    @Test fun changingTypesRetainsTheTentativeNumericValueAndIntervalResetsWithTheUnit() {
        show(62.3)
        click(context.getString(R.string.detail_weight_step, "2.5 kg")); click("10 kg")
        click("lb")
        compose.onNodeWithText(context.getString(R.string.detail_weight_step, "5 lb")).assertExists()
        click(text(R.string.detail_weight_bodyweight))
        click(text(R.string.detail_weight_unrecorded))
        click(text(R.string.detail_weight_numeric)); click("kg")
        click(text(R.string.detail_picker_done))
        assertEquals(DiaryWeightValue(62.3, WeightUnit.KG), applied)
    }

    @Test fun bodyweightToNumericClearsBodyweightMeaningAndUnrecordedKeepsOtherMeanings() {
        show(null, null, WeightBasis.BODYWEIGHT)
        click(text(R.string.detail_weight_numeric)); click(text(R.string.detail_exact_input))
        compose.onNode(hasSetTextAction()).performTextReplacement("40")
        click(text(R.string.detail_picker_done))
        assertEquals(DiaryWeightValue(40.0, WeightUnit.KG, basis = WeightBasis.UNKNOWN), applied)
    }

    @Test fun removingNumericWeightPreservesAddedLoadMeaning() {
        show(10.0, WeightUnit.KG, WeightBasis.ADDED)
        click(text(R.string.detail_weight_unrecorded)); click(text(R.string.detail_picker_done))
        assertEquals(DiaryWeightValue(null, null, basis = WeightBasis.ADDED), applied)
    }

    private fun assertSingleLineChoices() {
        for (id in listOf(R.string.detail_weight_unrecorded, R.string.detail_weight_bodyweight, R.string.detail_weight_numeric)) {
            val layouts = mutableListOf<TextLayoutResult>()
            compose.onNodeWithText(text(id), useUnmergedTree = true)
                .performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
            assertEquals(1, layouts.single().lineCount)
            val layout = layouts.single()
            assertEquals(text(id).length, layout.getLineEnd(0))
            assertTrue("${text(id)} exceeds its available width", layout.getLineRight(0) <= layout.layoutInput.constraints.maxWidth + 1f)
        }
    }

    @Test fun standardEnglishChoicesFitOnOneLineWithoutHorizontalScrolling() {
        show(); assertSingleLineChoices()
        val unrecorded = compose.onNodeWithText(text(R.string.detail_weight_unrecorded)).fetchSemanticsNode().boundsInRoot
        val bodyweight = compose.onNodeWithText(text(R.string.detail_weight_bodyweight)).fetchSemanticsNode().boundsInRoot
        assertEquals(unrecorded.top, bodyweight.top, 0f)
    }

    @Test @Config(qualifiers = "zh-rCN-w320dp-h640dp")
    fun chineseChoicesFitAndTheDoneActionRemainsReachableOnANarrowScreen() {
        show(); assertSingleLineChoices()
        compose.onNodeWithText(text(R.string.detail_picker_done)).performScrollTo().assertIsDisplayed()
    }

    @Test @Config(qualifiers = "w320dp-h480dp")
    fun narrowEnglishChoicesDoNotWrapAndActionsRemainReachable() {
        show(); assertSingleLineChoices()
        compose.onNodeWithText(text(R.string.detail_picker_done)).performScrollTo().assertIsDisplayed()
    }

    @Test @Config(qualifiers = "w320dp-h480dp")
    fun largeFontsStackChoicesWithoutWrappingAndKeepDoneReachable() {
        show(fontScale = 2f); assertSingleLineChoices()
        val unrecorded = compose.onNodeWithText(text(R.string.detail_weight_unrecorded)).fetchSemanticsNode().boundsInRoot
        val bodyweight = compose.onNodeWithText(text(R.string.detail_weight_bodyweight)).fetchSemanticsNode().boundsInRoot
        assertTrue(bodyweight.top >= unrecorded.bottom)
        compose.onNodeWithText(text(R.string.detail_picker_done)).performScrollTo().assertIsDisplayed()
    }

    @Test fun exactInputKeepsDoneReachableWhenAvailableHeightShrinksForTheKeyboard() {
        show(height = 280.dp)
        compose.onNodeWithText(text(R.string.detail_exact_input)).performScrollTo()
        click(text(R.string.detail_exact_input))
        compose.onNode(hasSetTextAction()).performScrollTo().performTextReplacement("65")
        compose.onNodeWithText(text(R.string.detail_picker_done)).performScrollTo().assertIsDisplayed()
        click(text(R.string.detail_picker_done)); assertEquals(65.0, applied!!.weight!!, 0.0)
    }
}
