/*
 * Copyright (C) 2021 The Android Open Source Project
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

package com.android.server.wm.flicker.rotation

import android.platform.test.annotations.Presubmit
import com.android.server.wm.flicker.BaseTest
import com.android.server.wm.flicker.FlickerBuilder
import com.android.server.wm.flicker.FlickerTest
import com.android.server.wm.flicker.helpers.StandardAppHelper
import com.android.server.wm.flicker.helpers.setRotation
import com.android.server.wm.traces.common.component.matchers.ComponentNameMatcher
import org.junit.Test

/** Base class for app rotation tests */
abstract class RotationTransition(flicker: FlickerTest) : BaseTest(flicker) {
    protected abstract val testApp: StandardAppHelper

    /** {@inheritDoc} */
    override val transition: FlickerBuilder.() -> Unit = {
        setup { this.setRotation(flicker.scenario.startRotation) }
        teardown { testApp.exit(wmHelper) }
        transitions { this.setRotation(flicker.scenario.endRotation) }
    }

    /** {@inheritDoc} */
    @Presubmit
    @Test
    override fun visibleLayersShownMoreThanOneConsecutiveEntry() {
        flicker.assertLayers {
            this.visibleLayersShownMoreThanOneConsecutiveEntry(
                ignoreLayers =
                    listOf(
                        ComponentNameMatcher.SPLASH_SCREEN,
                        ComponentNameMatcher.SNAPSHOT,
                        ComponentNameMatcher("", "SecondaryHomeHandle")
                    )
            )
        }
    }

    /** Checks that [testApp] layer covers the entire screen at the start of the transition */
    @Presubmit
    @Test
    open fun appLayerRotates_StartingPos() {
        flicker.assertLayersStart {
            this.entry.displays.map { display ->
                this.visibleRegion(testApp).coversExactly(display.layerStackSpace)
            }
        }
    }

    /** Checks that [testApp] layer covers the entire screen at the end of the transition */
    @Presubmit
    @Test
    open fun appLayerRotates_EndingPos() {
        flicker.assertLayersEnd {
            this.entry.displays.map { display ->
                this.visibleRegion(testApp).coversExactly(display.layerStackSpace)
            }
        }
    }

    override fun cujCompleted() {
        super.cujCompleted()
        appLayerRotates_StartingPos()
        appLayerRotates_EndingPos()
    }
}
