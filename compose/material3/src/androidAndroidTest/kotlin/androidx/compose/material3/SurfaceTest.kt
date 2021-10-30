/*
 * Copyright 2021 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package androidx.compose.material3

import android.os.Build
import androidx.compose.foundation.gestures.forEachGesture
import androidx.compose.foundation.interaction.Interaction
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.testutils.assertShape
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.MediumTest
import androidx.test.filters.SdkSuppress
import com.google.common.truth.Truth
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@MediumTest
@RunWith(AndroidJUnit4::class)
class SurfaceTest {

    @get:Rule
    val rule = createComposeRule()

    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.O)
    @Test
    fun noTonalElevationColorIsSetOnNonElevatedSurfaceColor() {
        var absoluteTonalElevation: Dp = 0.dp
        var surfaceColor: Color = Color.Unspecified
        rule.setMaterialContent {
            surfaceColor = MaterialTheme.colorScheme.surface
            Box(
                Modifier
                    .size(10.dp, 10.dp)
                    .semantics(mergeDescendants = true) {}
                    .testTag("box")
            ) {
                Surface(color = surfaceColor, tonalElevation = 0.dp) {
                    absoluteTonalElevation = LocalAbsoluteTonalElevation.current
                    Box(Modifier.fillMaxSize())
                }
            }
        }

        rule.runOnIdle {
            Truth.assertThat(absoluteTonalElevation).isEqualTo(0.dp)
        }

        rule.onNodeWithTag("box")
            .captureToImage()
            .assertShape(
                density = rule.density,
                shape = RectangleShape,
                shapeColor = surfaceColor,
                backgroundColor = Color.White
            )
    }

    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.O)
    @Test
    fun tonalElevationColorIsSetOnElevatedSurfaceColor() {
        var absoluteTonalElevation: Dp = 0.dp
        var surfaceTonalColor: Color = Color.Unspecified
        var surfaceColor: Color
        rule.setMaterialContent {
            surfaceColor = MaterialTheme.colorScheme.surface
            Box(
                Modifier
                    .size(10.dp, 10.dp)
                    .semantics(mergeDescendants = true) {}
                    .testTag("box")
            ) {
                Surface(color = surfaceColor, tonalElevation = 2.dp) {
                    absoluteTonalElevation = LocalAbsoluteTonalElevation.current
                    Box(Modifier.fillMaxSize())
                }
                surfaceTonalColor =
                    MaterialTheme.colorScheme.surfaceColorAtElevation(absoluteTonalElevation)
            }
        }

        rule.runOnIdle {
            Truth.assertThat(absoluteTonalElevation).isEqualTo(2.dp)
        }

        rule.onNodeWithTag("box")
            .captureToImage()
            .assertShape(
                density = rule.density,
                shape = RectangleShape,
                shapeColor = surfaceTonalColor,
                backgroundColor = Color.White
            )
    }

    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.O)
    @Test
    fun tonalElevationColorIsNotSetOnNonSurfaceColor() {
        var absoluteTonalElevation: Dp = 0.dp
        rule.setMaterialContent {
            Box(
                Modifier
                    .size(10.dp, 10.dp)
                    .semantics(mergeDescendants = true) {}
                    .testTag("box")
            ) {
                Surface(color = Color.Green, tonalElevation = 2.dp) {
                    Box(Modifier.fillMaxSize())
                    absoluteTonalElevation = LocalAbsoluteTonalElevation.current
                }
            }
        }

        rule.runOnIdle {
            Truth.assertThat(absoluteTonalElevation).isEqualTo(2.dp)
        }

        rule.onNodeWithTag("box")
            .captureToImage()
            .assertShape(
                density = rule.density,
                shape = RectangleShape,
                shapeColor = Color.Green,
                backgroundColor = Color.White
            )
    }

    @Test
    fun absoluteElevationCompositionLocalIsSet() {
        var outerElevation: Dp? = null
        var innerElevation: Dp? = null
        rule.setMaterialContent {
            Surface(tonalElevation = 2.dp) {
                outerElevation = LocalAbsoluteTonalElevation.current
                Surface(tonalElevation = 4.dp) {
                    innerElevation = LocalAbsoluteTonalElevation.current
                }
            }
        }

        rule.runOnIdle {
            Truth.assertThat(outerElevation).isEqualTo(2.dp)
            Truth.assertThat(innerElevation).isEqualTo(6.dp)
        }
    }

    /**
     * Tests that composed modifiers applied to Surface are applied within the changes to
     * [LocalContentColor], so they can consume the updated values.
     */
    @Test
    fun contentColorSetBeforeModifier() {
        var contentColor: Color = Color.Unspecified
        val expectedColor = Color.Blue
        rule.setMaterialContent {
            CompositionLocalProvider(LocalContentColor provides Color.Red) {
                Surface(
                    Modifier.composed {
                        contentColor = LocalContentColor.current
                        Modifier
                    },
                    tonalElevation = 2.dp,
                    contentColor = expectedColor,
                    content = {}
                )
            }
        }

        rule.runOnIdle {
            Truth.assertThat(contentColor).isEqualTo(expectedColor)
        }
    }

    @Test
    fun clickableOverload_semantics() {
        val count = mutableStateOf(0)
        rule.setMaterialContent {
            Surface(
                modifier = Modifier.testTag("surface"),
                role = Role.Checkbox,
                onClick = { count.value += 1 }
            ) {
                Text("${count.value}")
                Spacer(Modifier.size(30.dp))
            }
        }
        rule.onNodeWithTag("surface")
            .assertHasClickAction()
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Checkbox))
            .assertIsEnabled()
            // since we merge descendants we should have text on the same node
            .assertTextEquals("0")
            .performClick()
            .assertTextEquals("1")
    }

    @Test
    fun clickableOverload_clickAction() {
        val count = mutableStateOf(0f)
        rule.setMaterialContent {
            Surface(
                modifier = Modifier.testTag("surface"),
                onClick = { count.value += 1 }
            ) {
                Spacer(Modifier.size(30.dp))
            }
        }
        rule.onNodeWithTag("surface")
            .performClick()
        Truth.assertThat(count.value).isEqualTo(1)

        rule.onNodeWithTag("surface")
            .performClick()
            .performClick()
        Truth.assertThat(count.value).isEqualTo(3)
    }

    @Test
    fun clickableOverload_enabled_disabled() {
        val count = mutableStateOf(0f)
        val enabled = mutableStateOf(true)
        rule.setMaterialContent {
            Surface(
                modifier = Modifier.testTag("surface"),
                enabled = enabled.value,
                onClick = { count.value += 1 }
            ) {
                Spacer(Modifier.size(30.dp))
            }
        }
        rule.onNodeWithTag("surface")
            .assertIsEnabled()
            .performClick()

        Truth.assertThat(count.value).isEqualTo(1)
        rule.runOnIdle {
            enabled.value = false
        }

        rule.onNodeWithTag("surface")
            .assertIsNotEnabled()
            .performClick()
            .performClick()
        Truth.assertThat(count.value).isEqualTo(1)
    }

    @Test
    fun clickableOverload_interactionSource() {
        val interactionSource = MutableInteractionSource()

        var scope: CoroutineScope? = null

        rule.mainClock.autoAdvance = false

        rule.setContent {
            scope = rememberCoroutineScope()
            Surface(
                modifier = Modifier.testTag("surface"),
                onClick = {},
                interactionSource = interactionSource
            ) {
                Spacer(Modifier.size(30.dp))
            }
        }

        val interactions = mutableListOf<Interaction>()

        scope!!.launch {
            interactionSource.interactions.collect { interactions.add(it) }
        }

        rule.runOnIdle {
            Truth.assertThat(interactions).isEmpty()
        }

        rule.onNodeWithTag("surface")
            .performTouchInput { down(center) }

        // Advance past the tap timeout
        rule.mainClock.advanceTimeBy(100)

        rule.runOnIdle {
            Truth.assertThat(interactions).hasSize(1)
            Truth.assertThat(interactions.first()).isInstanceOf(PressInteraction.Press::class.java)
        }

        rule.onNodeWithTag("surface")
            .performTouchInput { up() }

        rule.runOnIdle {
            Truth.assertThat(interactions).hasSize(2)
            Truth.assertThat(interactions.first()).isInstanceOf(PressInteraction.Press::class.java)
            Truth.assertThat(interactions[1]).isInstanceOf(PressInteraction.Release::class.java)
            Truth.assertThat((interactions[1] as PressInteraction.Release).press)
                .isEqualTo(interactions[0])
        }
    }

    // TODO(b/198216553): Add surface_blockClicksBehind test from M2 after Button is added.

    // regression test for b/189411183
    @Test
    fun surface_allowsFinalPassChildren() {
        val hitTested = mutableStateOf(false)
        rule.setContent {
            Box(Modifier.fillMaxSize()) {
                Surface(
                    Modifier.fillMaxSize().testTag("surface"),
                ) {
                    Box(
                        Modifier
                            .fillMaxSize()
                            .testTag("clickable")
                            .pointerInput(Unit) {
                                forEachGesture {
                                    awaitPointerEventScope {
                                        hitTested.value = true
                                        val event = awaitPointerEvent(PointerEventPass.Final)
                                        Truth.assertThat(event.changes[0].consumed.downChange)
                                            .isFalse()
                                    }
                                }
                            }
                    )
                }
            }
        }
        rule.onNodeWithTag("clickable")
            .performTouchInput {
                down(center)
                up()
            }
        Truth.assertThat(hitTested.value).isTrue()
    }
}
