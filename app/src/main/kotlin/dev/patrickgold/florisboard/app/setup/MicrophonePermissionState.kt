/*
 * Copyright (C) 2025 The FlorisBoard Contributors
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

package dev.patrickgold.florisboard.app.setup

/**
 * The [MicrophonePermissionState] is used to determine the status of the microphone permission.
 * The default value is [NOT_SET].
 * This value is updated to [GRANTED] or [DENIED] depending on what the user selects.
 */
enum class MicrophonePermissionState {
    NOT_SET,
    GRANTED,
    DENIED;
}
