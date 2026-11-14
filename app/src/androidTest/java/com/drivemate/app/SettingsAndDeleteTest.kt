package com.drivemate.app

import android.content.Context
import android.content.Intent
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.BySelector
import androidx.test.uiautomator.Direction
import androidx.test.uiautomator.UiObject2
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Runs on the Pleos emulator with the vehicle parked and speed permission granted.
 * It wipes the app's driving data, so run it on a test device only.
 */
@RunWith(AndroidJUnit4::class)
class SettingsAndDeleteTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context: Context = instrumentation.targetContext
    private val device = UiDevice.getInstance(instrumentation)
    private val storage by lazy { context.getSharedPreferences("driving", Context.MODE_PRIVATE) }
    private val file by lazy { File(context.dataDir, "shared_prefs/driving.xml") }
    private val timeout = 20_000L

    @Before
    fun parkedApp() {
        storage.edit().putBoolean("recordingPaused", false).commit()
        launch()
    }

    private fun launch() {
        context.startActivity(Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK))
        assertNotNull("vehicle must be parked", device.wait(Until.findObject(By.text("주차 중")), timeout))
    }

    private fun tap(text: String) {
        val target = device.wait(Until.findObject(By.text(text)), timeout)
        assertNotNull("'$text' not shown", target)
        target.click()
        device.waitForIdle()
    }

    private fun openSettings() {
        val gear = device.wait(Until.findObject(By.desc("설정")), timeout)
        assertNotNull("settings button not shown", gear)
        gear.click()
        assertNotNull(device.wait(Until.findObject(By.text("설정")), timeout))
    }

    private fun closeSheet() {
        device.pressBack()
        device.wait(Until.gone(By.text("닫기")), timeout)
    }

    private fun scrollTo(selector: BySelector): UiObject2? {
        repeat(8) {
            device.findObject(selector)?.let { return it }
            val scroller = device.findObject(By.scrollable(true)) ?: return null
            if (!scroller.scroll(Direction.DOWN, 0.7f)) return device.findObject(selector)
        }
        return device.findObject(selector)
    }

    @Test
    fun settingsShowsRecordingPermissionsDataAndSources() {
        openSettings()
        for (label in listOf("주행 기록", "권한", "데이터", "기록 삭제", "수집 목적·지도 출처")) {
            assertNotNull("'$label' missing", scrollTo(By.text(label)))
        }
        assertNotNull(scrollTo(By.textStartsWith("벡터 지도 OpenFreeMap")))
        assertNotNull(scrollTo(By.textStartsWith("버전 ")))
        closeSheet()
    }

    @Test
    fun pauseSurvivesAndResumeRestores() {
        openSettings()
        tap("기록 중지")
        assertNotNull(device.wait(Until.findObject(By.text("기록 다시 켜기")), timeout))
        assertTrue(file.readText().contains("name=\"recordingPaused\" value=\"true\""))
        closeSheet()

        launch()
        // Shown as "기록 중지" or, with an unreviewed trip, "주행 완료 · 기록 중지".
        assertNotNull("paused state must survive relaunch", device.wait(Until.findObject(By.textEndsWith("기록 중지")), timeout))

        openSettings()
        tap("기록 다시 켜기")
        assertNotNull(device.wait(Until.findObject(By.text("기록 중지")), timeout))
        assertTrue(file.readText().contains("name=\"recordingPaused\" value=\"false\""))
        closeSheet()
    }

    @Test
    fun deleteAllLeavesNothingToRestoreAfterRelaunch() {
        val garden = Garden().withGoal("goal-1", Garden.PRESET_GOALS[0], "trip-1", 1)
            .reflect("goal-1", "GOOD", "trip-2", 2, listOf("FLOW"), 1L)!!
            .withGoal("goal-2", Garden.PRESET_GOALS[1], "trip-2", 2)
        storage.edit()
            .putInt("sessions", 2).putBoolean("reviewed", true).putString("garden", garden.toJson()).putString("goalDraft", "draft")
            .putString("lastSessionId", "trip-2").putInt("lastCount", 1)
            .putString("lastMoments", Garden.Moment.listToJson(listOf(Garden.Moment(5000, 40, 10))))
            .putString("lastContexts", "{\"0\":\"FLOW\"}").putString("lastRoute", "[[37.49,127.03],[37.48,127.03]]")
            .putString("pendingMoments", "[]").putString("pendingRoute", "[]").putString("pendingSessionId", "stale")
            .putString("destinationName", "테스트 목적지").putString("destinationLat", "37.48").putString("destinationLon", "127.03")
            .commit()
        launch()

        openSettings()
        tap("기록 삭제")
        tap("전체 데이터 삭제")
        tap("삭제")
        device.wait(Until.gone(By.text("전체 데이터를 삭제할까요?")), timeout)

        val onDisk = file.readText()
        val records = DataReset.ALL - setOf("liveLat", "liveLon", "locationAt", "bearing", "liveSpeed", "signalAt", "vehicleState", "gearLabel", "garden")
        for (key in records) assertFalse("'$key' survived delete-all", onDisk.contains("name=\"$key\""))
        // The garden key may be recreated empty right away; it must hold no goal or reflection.
        storage.getString("garden", null)?.let { assertEquals(Garden(), Garden.fromJson(it)) }

        launch()
        assertNotNull("sessions must restart from zero", device.wait(Until.findObject(By.text("누적 0회 주행")), timeout))
        assertNotNull(device.findObject(By.text("🍃 잎 0장")))
        assertNotNull(device.findObject(By.text("어디로 떠나볼까요?")))
        assertFalse(file.readText().contains("name=\"sessions\""))
    }
}
