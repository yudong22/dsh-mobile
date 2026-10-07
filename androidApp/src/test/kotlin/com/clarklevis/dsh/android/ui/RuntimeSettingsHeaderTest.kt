package com.clarklevis.dsh.android.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class RuntimeSettingsHeaderTest {
    @Test
    fun subtitleJoinsDeviceAndWorkspaceWithTheReferenceSeparator() {
        assertEquals(
            "denis的MacBook M5  |  任务",
            runtimeHeaderSubtitle("denis的MacBook M5", "任务")
        )
    }

    @Test
    fun subtitleOmitsBlankAndMissingParts() {
        assertEquals("任务", runtimeHeaderSubtitle(null, "任务"))
        assertEquals("任务", runtimeHeaderSubtitle("   ", "任务"))
        assertEquals("denis的MacBook M5", runtimeHeaderSubtitle("denis的MacBook M5", null))
        assertEquals("denis的MacBook M5", runtimeHeaderSubtitle("denis的MacBook M5", "  "))
        assertEquals("", runtimeHeaderSubtitle(null, null))
    }

    @Test
    fun subtitleTrimsPartsBeforeJoining() {
        assertEquals("设备  |  工作空间", runtimeHeaderSubtitle(" 设备 ", " 工作空间 "))
    }

    @Test
    fun permissionTitleFallsBackToChineseLabels() {
        assertEquals("只读", runtimePermissionTitle("read-only"))
        assertEquals("工作区写入", runtimePermissionTitle("workspace-write"))
        assertEquals("完全访问", runtimePermissionTitle("danger-full-access"))
        assertEquals("默认", runtimePermissionTitle(null))
        assertEquals("默认", runtimePermissionTitle(""))
        assertEquals("custom-value", runtimePermissionTitle("custom-value"))
    }
}
