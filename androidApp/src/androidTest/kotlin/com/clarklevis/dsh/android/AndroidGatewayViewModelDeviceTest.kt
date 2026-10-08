package com.clarklevis.dsh.android

import android.graphics.Bitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.rule.GrantPermissionRule
import org.junit.Assert.assertSame
import org.junit.Rule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import kotlinx.coroutines.runBlocking

@RunWith(AndroidJUnit4::class)
class AndroidGatewayViewModelDeviceTest {
    /**
     * 测试期间预授予运行时权限，见 AndroidUiParityDeviceTest 中同名规则的说明。
     * 这里尤其关键：本类的 `scenario.recreate()` 正是被权限对话框打断后卡满 47s 超时的那条。
     */
    @get:Rule(order = 0)
    val permissionRule: GrantPermissionRule = GrantPermissionRule.grant(
        android.Manifest.permission.POST_NOTIFICATIONS,
        android.Manifest.permission.CAMERA
    )

    @Test
    fun configurationRecreationRetainsTheSameStateHolder() {
        lateinit var original: AndroidSharedStateHolder
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                original = ViewModelProvider(activity)[AndroidGatewayViewModel::class.java].stateHolder
            }
            scenario.recreate()
            scenario.onActivity { activity ->
                val recreated =
                    ViewModelProvider(activity)[AndroidGatewayViewModel::class.java].stateHolder
                assertSame(original, recreated)
            }
        }
    }

    @Test
    fun activityFinishAndFreshLaunchReuseApplicationProjectionBaseline() {
        lateinit var original: AndroidSharedStateHolder
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                original = ViewModelProvider(activity)[AndroidGatewayViewModel::class.java].stateHolder
            }
            runBlocking { original.loadFixtureAndAwaitForTest() }
            assertEquals(2, original.snapshot.conversation.size)
        }
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val reopened = ViewModelProvider(activity)[AndroidGatewayViewModel::class.java].stateHolder
                assertSame(original, reopened)
                assertEquals(2, reopened.snapshot.conversation.size)
                reopened.reset()
            }
        }
    }

    @Test
    fun oversizedVisibleThumbnailWorkingSetEvictsWithSynchronizedDeferredState() {
        lateinit var holder: AndroidSharedStateHolder
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                holder = ViewModelProvider(activity)[AndroidGatewayViewModel::class.java].stateHolder
            }
            runBlocking { holder.loadFixtureAndAwaitForTest() }
            scenario.onActivity {
                repeat(10) { index ->
                    holder.commitVisibleThumbnailForTest(
                        sessionId = "android-demo",
                        attachmentId = "large-$index",
                        bitmap = Bitmap.createBitmap(720, 720, Bitmap.Config.ARGB_8888).asImageBitmap()
                    )
                }
                assertTrue(holder.thumbnailCacheWeightForTest <= 16L * 1_024 * 1_024)
                assertEquals(AttachmentLoadState.DEFERRED, holder.attachmentStates["large-0"])
                assertTrue(holder.attachmentStates.values.none { it == AttachmentLoadState.LOADING })
                assertTrue(holder.attachmentThumbnails.size < 10)
                holder.reset()
            }
        }
    }

}
