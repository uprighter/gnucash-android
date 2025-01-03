package org.gnucash.android.test.unit

import org.gnucash.android.test.unit.testutil.ShadowCrashlytics
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class) //package is required so that resources can be found in dev mode

//package is required so that resources can be found in dev mode
@Config(sdk = [21], shadows = [ShadowCrashlytics::class])
abstract class GnuCashTest {
}