package com.clarklevis.dsh.android.ui

import com.clarklevis.dsh.android.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 通知状态的唯一真相是系统。
 *
 * 这些用例钉住三件事：优先级的判定（未授权必须压过 `areNotificationsEnabled`）、
 * 文案映射、以及「只有 ALLOWED 才算能通知」——上报给网关的值由 `canNotify` 派生，
 * 判错就会让用户在系统里关掉通知后仍然收到推送。
 */
class NotificationSystemStateTest {

    @Test
    fun grantedAndEnabledIsAllowed() {
        assertEquals(
            NotificationSystemState.ALLOWED,
            notificationSystemState(notificationsEnabled = true, permissionGranted = true)
        )
    }

    @Test
    fun missingPermissionOutranksTheEnabledFlag() {
        // 有些 ROM 在未授权时 areNotificationsEnabled() 仍返回 true。若按它判断就会
        // 误报「已允许」，而通知实际弹不出来——所以权限必须优先。
        assertEquals(
            NotificationSystemState.PERMISSION_NOT_GRANTED,
            notificationSystemState(notificationsEnabled = true, permissionGranted = false)
        )
    }

    @Test
    fun userDisabledWithPermissionIsDisabledByUser() {
        assertEquals(
            NotificationSystemState.DISABLED_BY_USER,
            notificationSystemState(notificationsEnabled = false, permissionGranted = true)
        )
    }

    @Test
    fun bothOffStillReportsMissingPermission() {
        // 两个都关时，用户第一件要做的事是授权限，所以报「未允许」更有指导性。
        assertEquals(
            NotificationSystemState.PERMISSION_NOT_GRANTED,
            notificationSystemState(notificationsEnabled = false, permissionGranted = false)
        )
    }

    @Test
    fun onlyAllowedCanNotify() {
        assertTrue(NotificationSystemState.ALLOWED.canNotify)
        assertFalse(NotificationSystemState.PERMISSION_NOT_GRANTED.canNotify)
        assertFalse(NotificationSystemState.DISABLED_BY_USER.canNotify)
    }

    @Test
    fun everyStateMapsToItsOwnLabel() {
        val labels = NotificationSystemState.entries.map { notificationStateLabelRes(it) }
        assertEquals("两个状态共用了同一文案：$labels", labels.size, labels.toSet().size)
        assertEquals(R.string.settings_notification_allowed, notificationStateLabelRes(NotificationSystemState.ALLOWED))
        assertEquals(
            R.string.settings_notification_not_granted,
            notificationStateLabelRes(NotificationSystemState.PERMISSION_NOT_GRANTED)
        )
        assertEquals(
            R.string.settings_notification_disabled_by_user,
            notificationStateLabelRes(NotificationSystemState.DISABLED_BY_USER)
        )
    }
}
