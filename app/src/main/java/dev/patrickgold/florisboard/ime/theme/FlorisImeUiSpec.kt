/*
 * Copyright (C) 2021 Patrick Goldinger
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package dev.patrickgold.florisboard.ime.theme

import dev.patrickgold.florisboard.snygg.Snygg
import dev.patrickgold.florisboard.snygg.SnyggLevel
import dev.patrickgold.florisboard.snygg.SnyggSpec
import dev.patrickgold.florisboard.snygg.value.SnyggCutCornerShapeValue
import dev.patrickgold.florisboard.snygg.value.SnyggRectangleShapeValue
import dev.patrickgold.florisboard.snygg.value.SnyggRoundedCornerShapeValue
import dev.patrickgold.florisboard.snygg.value.SnyggSolidColorValue
import dev.patrickgold.florisboard.snygg.value.SnyggSpSizeValue

object FlorisImeUiSpec : SnyggSpec({
    element(FlorisImeUi.Keyboard) {
        property(
            name = Snygg.Background,
            level = SnyggLevel.BASIC,
            supportedValues(SnyggSolidColorValue),
        )
    }
    element(FlorisImeUi.Key) {
        property(
            name = Snygg.Background,
            level = SnyggLevel.BASIC,
            supportedValues(SnyggSolidColorValue),
        )
        property(
            name = Snygg.Foreground,
            level = SnyggLevel.BASIC,
            supportedValues(SnyggSolidColorValue),
        )
        property(
            name = Snygg.FontSize,
            level = SnyggLevel.ADVANCED,
            supportedValues(SnyggSpSizeValue),
        )
        property(
            name = Snygg.Shape,
            level = SnyggLevel.ADVANCED,
            supportedValues(SnyggRectangleShapeValue, SnyggCutCornerShapeValue, SnyggRoundedCornerShapeValue),
        )
    }
    element(FlorisImeUi.KeyHint) {
        property(
            name = Snygg.Background,
            level = SnyggLevel.BASIC,
            supportedValues(SnyggSolidColorValue),
        )
        property(
            name = Snygg.Foreground,
            level = SnyggLevel.BASIC,
            supportedValues(SnyggSolidColorValue),
        )
        property(
            name = Snygg.FontSize,
            level = SnyggLevel.ADVANCED,
            supportedValues(SnyggSpSizeValue),
        )
        property(
            name = Snygg.Shape,
            level = SnyggLevel.ADVANCED,
            supportedValues(SnyggRectangleShapeValue, SnyggCutCornerShapeValue, SnyggRoundedCornerShapeValue),
        )
    }
    element(FlorisImeUi.KeyPopup) {
        property(
            name = Snygg.Background,
            level = SnyggLevel.BASIC,
            supportedValues(SnyggSolidColorValue),
        )
        property(
            name = Snygg.Foreground,
            level = SnyggLevel.BASIC,
            supportedValues(SnyggSolidColorValue),
        )
        property(
            name = Snygg.FontSize,
            level = SnyggLevel.ADVANCED,
            supportedValues(SnyggSpSizeValue),
        )
        property(
            name = Snygg.Shape,
            level = SnyggLevel.ADVANCED,
            supportedValues(SnyggRectangleShapeValue, SnyggCutCornerShapeValue, SnyggRoundedCornerShapeValue),
        )
    }

    element(FlorisImeUi.OneHandedPanel) {
        property(
            name = Snygg.Background,
            level = SnyggLevel.BASIC,
            supportedValues(SnyggSolidColorValue),
        )
        property(
            name = Snygg.Foreground,
            level = SnyggLevel.BASIC,
            supportedValues(SnyggSolidColorValue),
        )
    }

    element(FlorisImeUi.SmartbarPrimaryRow) {
        property(
            name = Snygg.Background,
            level = SnyggLevel.BASIC,
            supportedValues(SnyggSolidColorValue),
        )
    }
    element(FlorisImeUi.SmartbarPrimaryActionRowToggle) {
        property(
            name = Snygg.Background,
            level = SnyggLevel.BASIC,
            supportedValues(SnyggSolidColorValue),
        )
        property(
            name = Snygg.Foreground,
            level = SnyggLevel.BASIC,
            supportedValues(SnyggSolidColorValue),
        )
        property(
            name = Snygg.Shape,
            level = SnyggLevel.ADVANCED,
            supportedValues(SnyggRectangleShapeValue, SnyggCutCornerShapeValue, SnyggRoundedCornerShapeValue),
        )
    }
    element(FlorisImeUi.SmartbarPrimarySecondaryRowToggle) {
        property(
            name = Snygg.Background,
            level = SnyggLevel.BASIC,
            supportedValues(SnyggSolidColorValue),
        )
        property(
            name = Snygg.Foreground,
            level = SnyggLevel.BASIC,
            supportedValues(SnyggSolidColorValue),
        )
        property(
            name = Snygg.Shape,
            level = SnyggLevel.ADVANCED,
            supportedValues(SnyggRectangleShapeValue, SnyggCutCornerShapeValue, SnyggRoundedCornerShapeValue),
        )
    }

    element(FlorisImeUi.SmartbarSecondaryRow) {
        property(
            name = Snygg.Background,
            level = SnyggLevel.BASIC,
            supportedValues(SnyggSolidColorValue),
        )
    }

    element(FlorisImeUi.SmartbarActionRow) {
        property(
            name = Snygg.Background,
            level = SnyggLevel.BASIC,
            supportedValues(SnyggSolidColorValue),
        )
    }
    element(FlorisImeUi.SmartbarActionButton) {
        property(
            name = Snygg.Foreground,
            level = SnyggLevel.BASIC,
            supportedValues(SnyggSolidColorValue),
        )
    }

    element(FlorisImeUi.SystemNavBar) {
        property(
            name = Snygg.Background,
            level = SnyggLevel.BASIC,
            supportedValues(SnyggSolidColorValue),
        )
    }
})
