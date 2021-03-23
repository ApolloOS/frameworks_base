/*
 * Copyright (C) 2020 The Android Open Source Project
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

package android.view.translation;

import android.os.IBinder;
import android.os.IRemoteCallback;
import android.os.ResultReceiver;
import android.view.autofill.AutofillId;
import android.view.translation.TranslationContext;
import android.view.translation.TranslationSpec;
import com.android.internal.os.IResultReceiver;

import java.util.List;

/**
 * Mediator between apps being translated and translation service implementation.
 *
 * {@hide}
 */
oneway interface ITranslationManager {
    void onTranslationCapabilitiesRequest(int sourceFormat, int destFormat,
         in ResultReceiver receiver, int userId);
    void onSessionCreated(in TranslationContext translationContext,
         int sessionId, in IResultReceiver receiver, int userId);

    void updateUiTranslationState(int state, in TranslationSpec sourceSpec,
         in TranslationSpec targetSpec, in List<AutofillId> viewIds, IBinder token, int taskId,
         int userId);
    // deprecated
    void updateUiTranslationStateByTaskId(int state, in TranslationSpec sourceSpec,
         in TranslationSpec targetSpec, in List<AutofillId> viewIds, int taskId, int userId);

    void registerUiTranslationStateCallback(in IRemoteCallback callback, int userId);
    void unregisterUiTranslationStateCallback(in IRemoteCallback callback, int userId);
}
