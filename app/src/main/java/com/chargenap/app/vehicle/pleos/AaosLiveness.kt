package com.chargenap.app.vehicle.pleos

/**
 * AAOS 보강 속성이 "살아 있는지" 가린다. 살아 있다 = 연결한 뒤 처음 읽은 값(기준값)과 다른 값을 한 번이라도 봤다.
 *
 * 왜 필요한가: Pleos Connect Emulator 2.1.0에서 `EV_CHARGE_STATE`, `EV_CHARGE_PORT_CONNECTED`, `PARKING_BRAKE_ON`은
 * 읽히기는 하지만 Virtual Console이 발행하지 않는 고정 기본값이었다(2026-11-15 실측, docs/sdk-notes.md 13절).
 * `EV_CHARGE_TIME_REMAINING`도 Virtual Console에 카드가 없어 같을 수 있다(값은 미확인). 읽히는 값을 그대로 믿으면
 * 충전 상태가 늘 "충전 완료", 주차 브레이크가 늘 "풀림"이 되어 세션을 시작할 수 없다. 그래서 값이 실제로 움직이는
 * 것을 본 속성만 권위 있는 값으로 쓴다.
 *
 * - null(못 읽음)은 기준값도 변화도 아니다.
 * - ON_CHANGE 구독 직후 오는 초기 이벤트는 현재 값 그대로라 다시 읽어도 기준값과 같으므로 변화로 세지 않는다.
 * - 한 번 살아 있으면 [reset] 전까지 계속 살아 있다(기준값으로 돌아와도).
 * - 스레드 안전하지 않다. 호출자가 동기화한다.
 */
internal class LivenessTracker<K : Any> {
    private val baseline = HashMap<K, Any>()
    private val live = HashSet<K>()

    /**
     * 읽은 값을 기록한다.
     * @return 기록한 뒤 [key]가 살아 있는지
     */
    fun observe(key: K, value: Any?): Boolean {
        if (value != null) {
            val first = baseline.getOrPut(key) { value }
            if (first != value) live += key
        }
        return key in live
    }

    fun isLive(key: K): Boolean = key in live

    /** 연결을 끊을 때 부른다. 다음 연결에서 처음 읽은 값이 새 기준값이 된다. */
    fun reset() {
        baseline.clear()
        live.clear()
    }
}

/**
 * AAOS `PARKING_BRAKE_ON` → `ParkState.parkingBrakeOn`.
 * - true(체결)는 그대로 true. 시작을 막지 않는 값이다.
 * - false(풀림)는 시작을 막으므로(SafetyGuard FAIL → 대기) 살아 있을 때만 믿는다. 아니면 null
 *   → SafetyGuard가 "확인할 수 없어 건너뜀"(SKIPPED)으로 보고 기어 P만으로 판정한다(킥오프 4.4).
 */
internal fun aaosParkingBrakeOn(value: Boolean?, live: Boolean): Boolean? = when (value) {
    true -> true
    false -> if (live) false else null
    null -> null
}

/** AAOS `EV_CHARGE_TIME_REMAINING`(초) → 분. 살아 있고 0보다 클 때만 값이 있다. 고정 기본값은 남은 시간이 아니다. */
internal fun liveRemainingChargeMinutes(seconds: Int?, live: Boolean): Int? =
    if (live) remainingChargeMinutes(seconds) else null
