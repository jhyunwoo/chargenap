package com.chargenap.app.vehicle.pleos

import com.chargenap.app.vehicle.ChargingState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ChargingStateMapperTest {

    // ---- 1순위: 살아 있는 AAOS EV_CHARGE_STATE ----

    @Test
    fun liveAaosCharging_isCharging_evenIfSwitchSaysOff() {
        val state = mapChargingState(
            ChargingInputs(
                aaosChargeState = AaosEvChargeState.CHARGING,
                aaosChargeStateLive = true,
                chargeSwitchOn = false,
                chargeSwitchSupported = true,
                batteryPercent = 100,
            ),
        )
        assertEquals(ChargingState.CHARGING, state)
    }

    @Test
    fun liveAaosFullyCharged_isComplete() {
        val state = mapChargingState(
            ChargingInputs(
                aaosChargeState = AaosEvChargeState.FULLY_CHARGED,
                aaosChargeStateLive = true,
                chargeSwitchOn = true,
                chargeSwitchSupported = true,
                batteryPercent = 80,
            ),
        )
        assertEquals(ChargingState.COMPLETE, state)
    }

    @Test
    fun liveAaosNotCharging_connectorPlugged_isConnectedIdle() {
        val state = mapChargingState(
            ChargingInputs(
                aaosChargeState = AaosEvChargeState.NOT_CHARGING,
                aaosChargeStateLive = true,
                aaosPortConnected = true,
                aaosPortConnectedLive = true,
            ),
        )
        assertEquals(ChargingState.CONNECTED_IDLE, state)
    }

    @Test
    fun liveAaosNotCharging_liveConnectorUnplugged_isNotConnected_evenIfPortOpen() {
        val state = mapChargingState(
            ChargingInputs(
                aaosChargeState = AaosEvChargeState.NOT_CHARGING,
                aaosChargeStateLive = true,
                aaosPortConnected = false,
                aaosPortConnectedLive = true,
                sdkPortOpen = true,
                chargeSwitchOn = true,
                chargeSwitchSupported = true,
            ),
        )
        assertEquals(ChargingState.NOT_CONNECTED, state)
    }

    @Test
    fun liveAaosNotCharging_noConnectorSignal_usesSdkPortOpen() {
        fun map(portOpen: Boolean?) = mapChargingState(
            ChargingInputs(aaosChargeState = AaosEvChargeState.NOT_CHARGING, aaosChargeStateLive = true, sdkPortOpen = portOpen),
        )
        assertEquals(ChargingState.CONNECTED_IDLE, map(true))
        assertEquals(ChargingState.NOT_CONNECTED, map(false))
        assertEquals(ChargingState.NOT_CONNECTED, map(null))
    }

    @Test
    fun aaosUnknownOrError_fallsBackToSdkInference() {
        for (aaos in listOf(AaosEvChargeState.UNKNOWN, AaosEvChargeState.ERROR, 99)) {
            for (live in listOf(true, false)) {
                val charging = mapChargingState(
                    ChargingInputs(
                        aaosChargeState = aaos,
                        aaosChargeStateLive = live,
                        chargeSwitchOn = true,
                        chargeSwitchSupported = true,
                        batteryPercent = 60,
                    ),
                )
                val unknown = mapChargingState(ChargingInputs(aaosChargeState = aaos, aaosChargeStateLive = live))
                assertEquals("aaos=$aaos live=$live", ChargingState.CHARGING, charging)
                assertEquals("aaos=$aaos live=$live", ChargingState.UNKNOWN, unknown)
            }
        }
    }

    // ---- 살아 있지 않은 AAOS EV_CHARGE_STATE (D1: 에뮬레이터 고정 기본값, 2026-11-15 실측) ----

    @Test
    fun fixedFullyCharged_notLive_switchOn_isCharging() {
        val state = mapChargingState(
            ChargingInputs(
                aaosChargeState = AaosEvChargeState.FULLY_CHARGED,
                aaosChargeStateLive = false,
                chargeSwitchOn = true,
                chargeSwitchSupported = true,
                batteryPercent = 60,
            ),
        )
        assertEquals(ChargingState.CHARGING, state)
    }

    @Test
    fun fixedFullyCharged_notLive_switchUnsupported_isComplete() {
        // 차량이 SDK 충전 스위치를 주지 않으면 AAOS 값이 유일한 근거다(대체 경로 유지)
        val state = mapChargingState(
            ChargingInputs(
                aaosChargeState = AaosEvChargeState.FULLY_CHARGED,
                aaosChargeStateLive = false,
                chargeSwitchOn = null,
                chargeSwitchSupported = false,
                batteryPercent = 60,
            ),
        )
        assertEquals(ChargingState.COMPLETE, state)
    }

    @Test
    fun fixedFullyCharged_notLive_switchSupportedButMomentarilyUnreadable_isUnknown() {
        // 시작 직후 스위치 조회가 아직 안 왔거나 일시적으로 실패한 순간. 고정 FULLY_CHARGED를 COMPLETE로 내보내면
        // 세션 엔진이 수면 중인 사용자를 "충전 완료"로 깨운다
        val state = mapChargingState(
            ChargingInputs(
                aaosChargeState = AaosEvChargeState.FULLY_CHARGED,
                aaosChargeStateLive = false,
                chargeSwitchOn = null,
                chargeSwitchSupported = true,
                batteryPercent = 60,
            ),
        )
        assertEquals(ChargingState.UNKNOWN, state)
    }

    @Test
    fun emulatorScenario_fixedFullyCharged_switchOn_socRising_staysCharging() {
        val tracker = LivenessTracker<String>()
        for (percent in 53..60) {
            val live = tracker.observe("state", AaosEvChargeState.FULLY_CHARGED)
            val state = mapChargingState(
                ChargingInputs(
                    aaosChargeState = AaosEvChargeState.FULLY_CHARGED,
                    aaosChargeStateLive = live,
                    chargeSwitchOn = true,
                    chargeSwitchSupported = true,
                    batteryPercent = percent,
                ),
            )
            assertEquals("soc=$percent", ChargingState.CHARGING, state)
        }
    }

    @Test
    fun aaosBecomesLive_notChargingToCharging_thenAaosWins() {
        val tracker = LivenessTracker<String>()
        fun map(aaos: Int) = mapChargingState(
            ChargingInputs(
                aaosChargeState = aaos,
                aaosChargeStateLive = tracker.observe("state", aaos),
                chargeSwitchOn = false,
                chargeSwitchSupported = true,
                sdkPortOpen = false,
            ),
        )
        // 처음 값(기준값)만 봤을 때는 SDK 추론: 스위치 Off, 포트 닫힘
        assertEquals(ChargingState.NOT_CONNECTED, map(AaosEvChargeState.NOT_CHARGING))
        // 값이 바뀌어 살아 있다고 확인되면 SDK 스위치가 Off라도 AAOS 값을 따른다
        assertEquals(ChargingState.CHARGING, map(AaosEvChargeState.CHARGING))
        assertEquals(ChargingState.COMPLETE, map(AaosEvChargeState.FULLY_CHARGED))
    }

    // ---- 2순위: SDK 추론 ----

    @Test
    fun switchOn_below100_isCharging() {
        assertEquals(ChargingState.CHARGING, mapChargingState(ChargingInputs(chargeSwitchOn = true, batteryPercent = 99)))
    }

    @Test
    fun switchOn_at100_isComplete() {
        assertEquals(ChargingState.COMPLETE, mapChargingState(ChargingInputs(chargeSwitchOn = true, batteryPercent = 100)))
    }

    @Test
    fun switchOn_percentUnknown_isCharging() {
        assertEquals(ChargingState.CHARGING, mapChargingState(ChargingInputs(chargeSwitchOn = true, batteryPercent = null)))
    }

    @Test
    fun switchOff_connectorPlugged_isConnectedIdle() {
        val state = mapChargingState(ChargingInputs(chargeSwitchOn = false, aaosPortConnected = true, sdkPortOpen = false))
        assertEquals(ChargingState.CONNECTED_IDLE, state)
    }

    @Test
    fun switchOff_noConnectorSignal_portOpen_isConnectedIdle() {
        val state = mapChargingState(ChargingInputs(chargeSwitchOn = false, sdkPortOpen = true))
        assertEquals(ChargingState.CONNECTED_IDLE, state)
    }

    @Test
    fun switchOff_liveConnectorUnplugged_isNotConnected_evenIfPortOpen() {
        val state = mapChargingState(
            ChargingInputs(chargeSwitchOn = false, aaosPortConnected = false, aaosPortConnectedLive = true, sdkPortOpen = true),
        )
        assertEquals(ChargingState.NOT_CONNECTED, state)
    }

    @Test
    fun switchOff_fixedConnectorFalse_notLive_portOpen_isConnectedIdle() {
        // D2: 에뮬레이터의 EV_CHARGE_PORT_CONNECTED는 고정 false였다(2026-11-15 실측)
        val tracker = LivenessTracker<String>()
        repeat(3) { tracker.observe("port", false) }
        val state = mapChargingState(
            ChargingInputs(
                aaosChargeState = AaosEvChargeState.FULLY_CHARGED,
                aaosChargeStateLive = false,
                chargeSwitchOn = false,
                chargeSwitchSupported = true,
                aaosPortConnected = false,
                aaosPortConnectedLive = tracker.isLive("port"),
                sdkPortOpen = true,
            ),
        )
        assertEquals(ChargingState.CONNECTED_IDLE, state)
    }

    @Test
    fun switchOff_nothingKnownAboutPort_isNotConnected() {
        assertEquals(ChargingState.NOT_CONNECTED, mapChargingState(ChargingInputs(chargeSwitchOn = false)))
    }

    @Test
    fun switchUnreadable_isUnknown() {
        val state = mapChargingState(ChargingInputs(chargeSwitchOn = null, batteryPercent = 50, sdkPortOpen = true))
        assertEquals(ChargingState.UNKNOWN, state)
    }

    // ---- 커넥터 연결 여부 ----

    @Test
    fun isPluggedIn_liveConnectorSignal_winsOverPortOpen() {
        assertTrue(isPluggedIn(aaosPortConnected = true, aaosPortConnectedLive = true, sdkPortOpen = false))
        assertFalse(isPluggedIn(aaosPortConnected = false, aaosPortConnectedLive = true, sdkPortOpen = true))
    }

    @Test
    fun isPluggedIn_connectorSignalNotLive_eitherSignalCounts() {
        assertTrue(isPluggedIn(aaosPortConnected = false, aaosPortConnectedLive = false, sdkPortOpen = true))
        assertTrue(isPluggedIn(aaosPortConnected = true, aaosPortConnectedLive = false, sdkPortOpen = false))
        assertFalse(isPluggedIn(aaosPortConnected = false, aaosPortConnectedLive = false, sdkPortOpen = false))
        assertFalse(isPluggedIn(aaosPortConnected = false, aaosPortConnectedLive = false, sdkPortOpen = null))
    }

    @Test
    fun isPluggedIn_noConnectorSignal_usesPortOpen() {
        assertTrue(isPluggedIn(aaosPortConnected = null, aaosPortConnectedLive = false, sdkPortOpen = true))
        assertFalse(isPluggedIn(aaosPortConnected = null, aaosPortConnectedLive = false, sdkPortOpen = null))
        // 살아 있던 속성을 지금 못 읽으면(null) SDK 포트 열림으로 대체한다
        assertTrue(isPluggedIn(aaosPortConnected = null, aaosPortConnectedLive = true, sdkPortOpen = true))
    }

    // ---- 남은 충전 시간 ----

    @Test
    fun remainingMinutes_roundsUpSeconds() {
        assertEquals(1, remainingChargeMinutes(1))
        assertEquals(1, remainingChargeMinutes(60))
        assertEquals(2, remainingChargeMinutes(61))
        assertEquals(20, remainingChargeMinutes(1_200))
        assertEquals(21, remainingChargeMinutes(1_201))
    }

    @Test
    fun remainingMinutes_zeroNegativeOrUnreadable_isNull() {
        assertNull(remainingChargeMinutes(0))
        assertNull(remainingChargeMinutes(-5))
        assertNull(remainingChargeMinutes(null))
    }
}
