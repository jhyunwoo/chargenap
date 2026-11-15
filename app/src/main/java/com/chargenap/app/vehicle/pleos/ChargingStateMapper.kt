package com.chargenap.app.vehicle.pleos

import com.chargenap.app.vehicle.ChargingState

/**
 * AAOS `android.car.hardware.property.EvChargeState`의 값.
 * 근거: docs/sdk-notes.md 13절 표. 순수 함수와 단위 테스트가 android.car 클래스 없이 돌도록 숫자로 둔다.
 */
internal object AaosEvChargeState {
    const val UNKNOWN = 0
    const val CHARGING = 1
    const val FULLY_CHARGED = 2
    const val NOT_CHARGING = 3
    const val ERROR = 4
}

/**
 * 충전 상태 판정에 쓰는 원시 값. 읽지 못한 값은 null.
 *
 * AAOS 값의 `...Live`는 [LivenessTracker] 기준으로 "연결한 뒤 값이 바뀌는 것을 봤다"는 뜻이다. 살아 있지 않은
 * AAOS 값은 에뮬레이터의 고정 기본값일 수 있어(2026-11-15 실측) SDK 값보다 앞세우지 않는다.
 *
 * @param aaosChargeState AAOS `EV_CHARGE_STATE`. 속성이 없거나 권한이 없으면 null
 * @param aaosChargeStateLive [aaosChargeState]가 살아 있는지
 * @param chargeSwitchOn SDK `getEvChargeSwitchState`
 * @param chargeSwitchSupported SDK capability상 충전 스위치를 읽을 수 있는 차량인지. true면 스위치 값이 잠깐
 *   null이어도(시작 직후, 일시 오류) 살아 있지 않은 AAOS 충전 상태로 넘어가지 않는다
 * @param batteryPercent SDK 잔량(%)
 * @param aaosPortConnected AAOS `EV_CHARGE_PORT_CONNECTED`
 * @param aaosPortConnectedLive [aaosPortConnected]가 살아 있는지
 * @param sdkPortOpen SDK `isEvChargePortOpen`(커넥터 연결 신호가 없을 때의 대용)
 */
internal data class ChargingInputs(
    val aaosChargeState: Int? = null,
    val aaosChargeStateLive: Boolean = false,
    val chargeSwitchOn: Boolean? = null,
    val chargeSwitchSupported: Boolean = false,
    val batteryPercent: Int? = null,
    val aaosPortConnected: Boolean? = null,
    val aaosPortConnectedLive: Boolean = false,
    val sdkPortOpen: Boolean? = null,
)

/**
 * 커넥터가 꽂혀 있다고 볼지.
 * - AAOS 커넥터 연결 값이 살아 있으면 그 값.
 * - 아니면 AAOS 값이 true이거나 SDK 충전 포트가 열려 있으면 연결된 것으로 본다. 에뮬레이터의
 *   `EV_CHARGE_PORT_CONNECTED`는 고정 false여서 이 값만 믿으면 포트를 열어도 "충전기 연결 안 됨"이었다.
 * - 둘 다 없으면 연결되지 않은 것으로 본다.
 */
internal fun isPluggedIn(aaosPortConnected: Boolean?, aaosPortConnectedLive: Boolean, sdkPortOpen: Boolean?): Boolean =
    if (aaosPortConnectedLive && aaosPortConnected != null) {
        aaosPortConnected
    } else {
        aaosPortConnected == true || sdkPortOpen == true
    }

/**
 * AAOS 충전 상태를 SDK 추론보다 앞세울지.
 * - 살아 있으면 앞세운다.
 * - 살아 있지 않으면 차량이 SDK 충전 스위치를 아예 주지 않을 때(capability 없음, 값 없음)만 대체로 쓴다.
 *   스위치 capability가 있는데 잠깐 못 읽은 순간에 쓰면, 에뮬레이터의 고정 FULLY_CHARGED가 COMPLETE로 나가
 *   세션 엔진이 수면 중인 사용자를 "충전 완료"로 깨운다.
 */
internal fun trustsAaosChargeState(inputs: ChargingInputs): Boolean =
    inputs.aaosChargeState != null &&
        (inputs.aaosChargeStateLive || (inputs.chargeSwitchOn == null && !inputs.chargeSwitchSupported))

/**
 * 원시 값 → [ChargingState].
 *
 * 1. AAOS 충전 상태를 믿을 수 있고([trustsAaosChargeState]) 확정 값이면 그 값을 쓴다.
 * 2. 아니면(믿을 수 없음·없음·UNKNOWN·ERROR) SDK 충전 스위치로 추론한다. 스위치도 읽지 못하면 UNKNOWN.
 */
internal fun mapChargingState(inputs: ChargingInputs): ChargingState {
    val plugged = isPluggedIn(inputs.aaosPortConnected, inputs.aaosPortConnectedLive, inputs.sdkPortOpen)
    if (trustsAaosChargeState(inputs)) {
        when (inputs.aaosChargeState) {
            AaosEvChargeState.CHARGING -> return ChargingState.CHARGING
            AaosEvChargeState.FULLY_CHARGED -> return ChargingState.COMPLETE
            AaosEvChargeState.NOT_CHARGING ->
                return if (plugged) ChargingState.CONNECTED_IDLE else ChargingState.NOT_CONNECTED
            else -> Unit // UNKNOWN, ERROR, 정의되지 않은 값 → SDK 추론으로
        }
    }
    return when (inputs.chargeSwitchOn) {
        true -> if ((inputs.batteryPercent ?: 0) >= FULL_PERCENT) ChargingState.COMPLETE else ChargingState.CHARGING
        false -> if (plugged) ChargingState.CONNECTED_IDLE else ChargingState.NOT_CONNECTED
        null -> ChargingState.UNKNOWN
    }
}

/** AAOS `EV_CHARGE_TIME_REMAINING`(초) → 분(올림). 읽지 못했거나 0 이하면 null. */
internal fun remainingChargeMinutes(seconds: Int?): Int? {
    if (seconds == null || seconds <= 0) return null
    return (seconds + SECONDS_PER_MINUTE - 1) / SECONDS_PER_MINUTE
}

private const val FULL_PERCENT = 100
private const val SECONDS_PER_MINUTE = 60
