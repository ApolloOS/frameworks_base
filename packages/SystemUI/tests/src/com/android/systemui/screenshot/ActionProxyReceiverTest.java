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

package com.android.systemui.screenshot;

import static com.android.systemui.screenshot.ScreenshotController.ACTION_TYPE_SHARE;
import static com.android.systemui.screenshot.ScreenshotController.EXTRA_ID;
import static com.android.systemui.screenshot.ScreenshotController.EXTRA_SMART_ACTIONS_ENABLED;
import static com.android.systemui.statusbar.phone.CentralSurfaces.SYSTEM_DIALOG_REASON_SCREENSHOT;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import android.app.PendingIntent;
import android.content.Intent;
import android.os.Bundle;
import android.testing.AndroidTestingRunner;

import androidx.test.filters.SmallTest;

import com.android.systemui.SysuiTestCase;
import com.android.systemui.plugins.ActivityStarter;
import com.android.systemui.settings.FakeDisplayTracker;
import com.android.systemui.shared.system.ActivityManagerWrapper;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.stubbing.Answer;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

@RunWith(AndroidTestingRunner.class)
@SmallTest
public class ActionProxyReceiverTest extends SysuiTestCase {
    @Mock
    private ActivityManagerWrapper mMockActivityManagerWrapper;
    @Mock
    private ScreenshotSmartActions mMockScreenshotSmartActions;
    @Mock
    private PendingIntent mMockPendingIntent;
    @Mock
    private ActivityStarter mActivityStarter;

    private Intent mIntent;
    private FakeDisplayTracker mDisplayTracker = new FakeDisplayTracker(mContext);

    @Before
    public void setup() throws InterruptedException, ExecutionException, TimeoutException {
        MockitoAnnotations.initMocks(this);
        mIntent = new Intent(mContext, ActionProxyReceiver.class)
                .putExtra(ScreenshotController.EXTRA_ACTION_INTENT, mMockPendingIntent);
    }

    @Test
    public void testPendingIntentSentWithStatusBar() throws PendingIntent.CanceledException {
        ActionProxyReceiver actionProxyReceiver = constructActionProxyReceiver();
        // ensure that the pending intent call is passed through
        doAnswer((Answer<Object>) invocation -> {
            ((Runnable) invocation.getArgument(0)).run();
            return null;
        }).when(mActivityStarter).executeRunnableDismissingKeyguard(
                any(Runnable.class), isNull(), anyBoolean(), anyBoolean(), anyBoolean());

        actionProxyReceiver.onReceive(mContext, mIntent);

        verify(mMockActivityManagerWrapper).closeSystemWindows(SYSTEM_DIALOG_REASON_SCREENSHOT);
        verify(mActivityStarter).executeRunnableDismissingKeyguard(
                any(Runnable.class), isNull(), eq(true), eq(true), eq(true));
        verify(mMockPendingIntent).send(
                eq(mContext), anyInt(), isNull(), isNull(), isNull(), isNull(), any(Bundle.class));
    }

    @Test
    public void testSmartActionsNotNotifiedByDefault() {
        ActionProxyReceiver actionProxyReceiver = constructActionProxyReceiver();

        actionProxyReceiver.onReceive(mContext, mIntent);

        verify(mMockScreenshotSmartActions, never())
                .notifyScreenshotAction(anyString(), anyString(), anyBoolean(),
                        any(Intent.class));
    }

    @Test
    public void testSmartActionsNotifiedIfEnabled() {
        ActionProxyReceiver actionProxyReceiver = constructActionProxyReceiver();
        mIntent.putExtra(EXTRA_SMART_ACTIONS_ENABLED, true);
        String testId = "testID";
        mIntent.putExtra(EXTRA_ID, testId);

        actionProxyReceiver.onReceive(mContext, mIntent);

        verify(mMockScreenshotSmartActions).notifyScreenshotAction(
                testId, ACTION_TYPE_SHARE, false, null);
    }

    private ActionProxyReceiver constructActionProxyReceiver() {
        return new ActionProxyReceiver(
                mMockActivityManagerWrapper,
                mMockScreenshotSmartActions,
                mDisplayTracker,
                mActivityStarter
        );
    }
}
