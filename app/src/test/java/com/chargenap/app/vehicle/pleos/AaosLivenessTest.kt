package com.chargenap.app.vehicle.pleos

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * AAOS 보강 값의 "살아 있음" 판정과 그에 따른 주차 브레이크·남은 충전 시간 규칙.
 * 배경: Pleos Connect Emulator 2.1.0(2026-11-15 실측)에서 PARKING_BRAKE_ON 등은 Virtual Console이 발행하지 않는
 * 고정 기본값이었다. EV_CHARGE_TIME_REMAINING도 Virtual Console에 카드가 없어 같을 수 있다.
 */
class AaosLivenessTest {

    // ---- 살아 있음 추적기 ----

    @Test
    fun firstValue_isBaseline_notLive() {
        val tracker = LivenessTracker<String>()
        assertFalse(tracker.observe("state", AaosEvChargeState.FULLY_CHARGED))
        assertFalse(tracker.isLive("state"))
    }

    @Test
    fun sameValueRepeated_staysNotLive() {
        // 고정 기본값을 폴링으로 계속 읽거나, ON_CHANGE 구독 직후 초기 이벤트로 같은 값을 다시 읽는 경우
        val tracker = LivenessTracker<String>()
        repeat(5) { assertFalse(tracker.observe("state", AaosEvChargeState.FULLY_CHARGED)) }
        assertFalse(tracker.isLive("state"))
    }

    @Test
    fun differentValue_makesLive() {
        val tracker = LivenessTracker<String>()
        tracker.observe("state", AaosEvChargeState.NOT_CHARGING)
        assertTrue(tracker.observe("state", AaosEvChargeState.CHARGING))
        assertTrue(tracker.isLive("state"))
    }

    @Test
    fun live_staysLive_afterReturningToBaseline() {
        val tracker = LivenessTracker<String>()
        tracker.observe("brake", true)
        tracker.observe("brake", false)
        assertTrue(tracker.observe("brake", true))
        assertTrue(tracker.isLive("brake"))
    }

    @Test
    fun null_isNeitherBaselineNorChange() {
        val tracker = LivenessTracker<String>()
        assertFalse(tracker.observe("port", null))
        assertFalse(tracker.observe("port", false)) // null 뒤 첫 값이 기준값이다
        assertFalse(tracker.observe("port", null))
        assertFalse(tracker.observe("port", false))
        assertFalse(tracker.isLive("port"))
    }

    @Test
    fun keysAreIndependent() {
        val tracker = LivenessTracker<String>()
        tracker.observe("state", 2)
        tracker.observe("time", 2)
        tracker.observe("state", 1)
        assertTrue(tracker.isLive("state"))
        assertFalse(tracker.isLive("time"))
        assertFalse(tracker.isLive("never-seen"))
    }

    @Test
    fun reset_forgetsBaselineAndLiveness() {
        val tracker = LivenessTracker<String>()
        tracker.observe("state", 3)
        tracker.observe("state", 1)
        assertTrue(tracker.isLive("state"))

        tracker.reset()
        assertFalse(tracker.isLive("state"))
        assertFalse("재연결 뒤 첫 값이 새 기준값", tracker.observe("state", 1))
        assertFalse(tracker.observe("state", 1))
        assertTrue(tracker.observe("state", 3))
    }

    // ---- 주차 브레이크 (D3) ----

    @Test
    fun parkingBrake_fixedFalse_notLive_isUnknown() {
        val tracker = LivenessTracker<String>()
        repeat(3) { tracker.observe("brake", false) }
        assertNull(aaosParkingBrakeOn(false, live = tracker.isLive("brake")))
    }

    @Test
    fun parkingBrake_true_isEngaged_whetherLiveOrNot() {
        assertEquals(true, aaosParkingBrakeOn(true, live = false))
        assertEquals(true, aaosParkingBrakeOn(true, live = true))
    }

    @Test
    fun parkingBrake_falseAfterRelease_isReleased() {
        val tracker = LivenessTracker<String>()
        tracker.observe("brake", true)
        tracker.observe("brake", false)
        assertEquals(false, aaosParkingBrakeOn(false, live = tracker.isLive("brake")))
    }

    @Test
    fun parkingBrake_unreadable_isUnknown() {
        assertNull(aaosParkingBrakeOn(null, live = false))
        assertNull(aaosParkingBrakeOn(null, live = true))
    }

    // ---- 남은 충전 시간 (D4) ----

    @Test
    fun remainingTime_fixedPositive_notLive_isNull() {
        val tracker = LivenessTracker<String>()
        repeat(3) { tracker.observe("time", 1_200) }
        assertNull(liveRemainingChargeMinutes(1_200, live = tracker.isLive("time")))
    }

    @Test
    fun remainingTime_afterChange_isMinutes() {
        val tracker = LivenessTracker<String>()
        tracker.observe("time", 1_200)
        tracker.observe("time", 1_140)
        assertEquals(19, liveRemainingChargeMinutes(1_140, live = tracker.isLive("time")))
    }

    @Test
    fun remainingTime_live_zeroOrUnreadable_isNull() {
        assertNull(liveRemainingChargeMinutes(0, live = true))
        assertNull(liveRemainingChargeMinutes(null, live = true))
    }
}
