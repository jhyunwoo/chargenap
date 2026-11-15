# 진행 상황

단계가 끝날 때마다 한 일, 남은 일, 막힌 점을 적는다. (최종 갱신 2026-11-15, KVM 서버 에뮬레이터 실행 검증. 1차 접수 제출본은 커밋 `b3cb644`)

## 요약

| 단계 | 상태 | 비고 |
| --- | --- | --- |
| 0. 개발 환경 | 완료 | JDK 17·Android SDK·Gradle(홈 디렉터리 설치). Pleos Connect Emulator 2.1.0을 Windows(WHPX 가속)에 설치 |
| 1. 프로젝트 셋업 | 완료 | CLAUDE.md, 서브에이전트, docs 뼈대 |
| 2. SDK 검증 | 완료 | 미확인 15개 중 14개 확인(SDK 공개 소스·선언·문서), 실행 확인은 아래 "에뮬레이터 검증" 참고 |
| 3. 스캐폴딩과 도메인 | 완료 | 세션 엔진·추정기·안전 가드, 가상 차량 |
| 4. 프로토타입 화면과 연결 | 완료 | 화면 7종, 통합, 에뮬레이터 설치·실행 확인 |
| 5. 1차 제출 패키징 | 완료 | release APK(`apk/전현우_ChargeNap_release.apk`), README, 시연 영상, 기획서, 접수양식 |
| 6. 실제 차량 연동 | **에뮬레이터 실행 검증(2026-11-15), 결함 3건 발견** | KVM 서버에서 Vehicle 서비스 연결·실제 잔량·기어·도어·충전 스위치·공조 설정/복구·주행 가능 거리 확인. AAOS 보강 값(충전 상태·커넥터·주차 브레이크)이 에뮬레이터 고정값이라 생기는 결함 3건 → 아래 "KVM 서버 에뮬레이터 실행 검증" |
| 7. 음성 | 구현 완료, **명령 인식은 실행 검증 미완** | KVM 서버 에뮬레이터에는 온디바이스 TTS·STT 엔진이 있다. 앱 요청으로 한국어 TTS 생성, STT 인식 루프 동작 확인. 서버에서 마이크 입력을 넣지 못해 "20분만 재워줘" 인식은 미확인 |
| 8. 완성도 | 완료 | 2,560×1,440 스냅샷 31장, 리포트, 설정 저장, 포그라운드 서비스 |
| 9. 최종 검증 | 단위 테스트·코드 리뷰 완료 | 에뮬레이터 시나리오(실차 값)와 20분 실제 길이 세션은 2차 PT 전 과제 |
| 개발 서버 이전 | 완료 (2026-11-15) | Vultr Bare Metal(KVM). Optimized Cloud(VOC)는 중첩 가상화가 없어 에뮬레이터 불가. 남은 작업은 `docs/next-steps.md` |

## 검증 기록

### 통과

| 항목 | 방법 | 결과 |
| --- | --- | --- |
| 빌드 | `./gradlew assembleDebug assembleRelease` | 성공 |
| 단위 테스트 | `./gradlew testDebugUnitTest` | 418개 통과, 실패 0 (영상·애니메이션 프레임 캡처 19개는 평소 건너뜀) |
| 세션 엔진 | 정상 흐름, 충전 완료 조기 기상, 충전 중단, 도어 열림, 기어 변경, "5분 더" 허용·거절, 캐빈 일부 실패, 동의 후 도어 열림 시 동의 무효화, 복구 실패 재시도 | 통과 |
| 추정기 | SDK 값 우선, 표본 추정, 표본 부족, 증가율 0, 경계값(14→호흡, 15→10분, 27→20분, 45→25분, 10분 미만→제안 없음) | 통과 |
| 차량 연동(순수 로직) | 충전 상태 매핑, 리클라인 목표 계산, 풍량·온도 변환, 시트 정지 재전송, 조회·리스너 값 경쟁, AAOS 기어 UNKNOWN | 통과 |
| 음성 | 킥오프 4.5 예문 전부, 맥락별 "없어요"/"있어요", 한글 숫자, 질문 전 답 무시, 바인더(가짜 음성 장치) | 통과 |
| 화면 | 2,560×1,440 스냅샷 31장 직접 확인(21:9·4:3 포함), 터치만으로 한 바퀴(`ChargeNapScreensTest`) | 잘림·겹침 없음, 통과 |
| 앱 실행 | `MainActivitySmokeTest`: 실제 Application·AppContainer·MainActivity, Vehicle Service 없음 → 데모 대체 → 제안 | 통과 |
| 포그라운드 서비스·시계 | 세션 상태별 서비스 필요 여부, 기기 시각 변경에도 세션 시계 단조 증가 | 통과 |
| 저장 실패 | 설정·기록 저장 IOException 시 앱이 죽지 않음 | 통과 |
| 코드 리뷰(안전) | 독립 리뷰로 9건 발견 → 전부 수정·테스트 추가 | 완료 |
| release APK | `apksigner verify`, 에뮬레이터 설치(`adb install`) | 서명 확인, 설치 성공 |
| 에뮬레이터 실행 | Pleos Connect Emulator 2.1.0(2,560×1,440, 160dpi)에서 실행, 데모 모드 화면·DEMO 조작 패널 동작 | 확인(`docs/screenshots/emulator/`) |
| 규칙 점검 | `vehicle/` 밖 Vehicle SDK import 없음, `voice/` 밖 STT·TTS import 없음, `session/`에 Android import 없음, 화면이 게이트웨이 구현체를 참조하지 않음, 키스토어 미추적 | 통과 |

### KVM 서버 에뮬레이터 실행 검증 (2026-11-15)

환경: Vultr Bare Metal(Xeon E-2286G 6코어 12스레드, 메모리 31GB, Ubuntu 24.04, `/dev/kvm`). 저장소를 새로 받아(`b3cb644`) 설치. Pleos Connect Emulator 2.1.0(`RELEASE.release_connect_v2.1.0`, Android 14), AVD RAM 4GB, cold boot, 창 없이(`scripts/server/emu.sh up`). 앱은 Pleos 런처의 오른쪽 영역(멀티 윈도, **1682×1210**)에 뜬다. 화면은 `docs/screenshots/emulator/real_*.png`.

| 검증 | 방법 | 결과 |
| --- | --- | --- |
| 빌드·단위 테스트(깨끗한 클론) | `./gradlew testDebugUnitTest` | 437개 중 418개 실행, 실패 0 (영상용 19개 skipped) |
| release APK = 소스 | 서버에서 `assembleRelease`(서명 없음) 후 커밋된 APK와 비교 | `classes*.dex`·`resources.arsc`·`AndroidManifest.xml` SHA-256 모두 일치(서명만 다름) |
| 에뮬레이터 부팅·`car_service` | `emu.sh up`, `emu.sh status`, `dumpsys car_service` | 부팅 완료, `car_service not ready` 0건, 차량 HAL 패닉 0건. **노트북에서 막혔던 원인은 이미지가 아니라 그 환경이었다** |
| Vehicle 서비스 연결 | 커밋된 release APK 설치·실행 | DEMO 배지 없음, 실제 잔량 53% 표시, 잔량 구독으로 실시간 갱신 |
| 시나리오 1: 충전 감지·추정·제안 | Port Open + Charge On + SOC 45초마다 1% | 권한(`android.car.permission.CAR_ENERGY`) **거부 시 통과**: "충전 중" → "남은 시간을 재는 중"(15/20분) → "80%까지 약 28분 남았어요 · 20분 파워냅 어때요?". 남은 19분일 때 "10분 파워냅"(규칙대로). **허용 시 실패(결함 D1)** |
| 안전 확인 | 세션 시작 | **실패(결함 D3)**: 주차 브레이크 "풀려 있어요"에서 멈춰 시작 불가 |
| 아래 행부터 | 검증용 빌드(서버 사본에서만 `parkingBrakeOn = null`, 커밋 안 함)로 진행 | 주차 브레이크 "확인할 수 없어 건너뜀" → 뒷좌석 질문 → [없어요] |
| 시나리오 2: 캐빈 세팅·복구 | Virtual Console `hvac`·`seat` 상태 기록 | 공조: 수면 23℃ 적용, 기상 21℃·풍량 6(서늘한 바람), 복구 22℃·풍량 3 **확인**. 시트: 아래 "에뮬레이터 한계" |
| 시나리오 3: 도어 열림 | 수면 중 `door rear_left open` → `closed` | "문이 열려 휴식을 멈췄어요 / 문을 닫으면 시트를 되돌려요", 닫으면 공조 복구 **통과** |
| 시나리오 4: 기어 D | 수면 중 `gear D` | 2초 안에 "기어가 바뀌어 휴식을 멈췄어요", 공조 복구 **통과** |
| 시나리오 5: 충전 스위치 Off | 수면 중 `charge off` | "충전이 멈춰서 깨워 드렸어요", 기상 프리셋 → 복구, 브리핑(잔량·주행 가능 거리 240km·쉰 시간) **통과** |
| 충전 완료 5분 전 기상 | 수면 중 SOC 74% | "충전이 곧 끝나요"로 기상·복구·브리핑 **통과** |
| 타이머 기상 | 10분 세션(잔량 고정) | 약 10분 뒤(42초 간격 기록) "맞춰 둔 시간이 다 됐어요", 복구, "쉰 시간 10분" **통과** |
| 시나리오 6: 시트 capability false | — | **재현 불가**: 에뮬레이터는 운전석 등받이 capability를 지원(영역 1·4·16·64, 0..100, MOVE −1..1)으로 준다 |
| 리포트·설정·브리핑 화면 | 1682×1210 실제 창 | 잘림·겹침 없음 |
| 음성 | logcat `[GAAS]`, `[CAAS]` | 온디바이스 TTS가 앱 문장을 생성(`speaker=seoyoon`, ko_KR), STT가 제안·안전 확인·수면 상태에서 인식 루프 실행. 창 없는 에뮬레이터가 PulseAudio에 붙지 않아(`Could not init 'pa' audio driver`) 음성 명령 주입은 못 함 → **명령 인식 미검증** |

**결함** (원인: Virtual Console이 아래 AAOS 속성을 발행하지 않아 VHAL 기본값에 고정. `cmd car_service inject-vhal-event`는 user 빌드라 막힘)

- **D1 (치명)** `android.car.permission.CAR_ENERGY`를 허용하면 `EV_CHARGE_STATE` 고정값이 SDK 추론보다 우선해 상태가 "충전 완료"에 고정, 충전해도 제안이 뜨지 않는다. 처음 실행 때 이 권한을 묻기 때문에 대부분 이 경로로 간다.
- **D2 (경미)** `EV_CHARGE_PORT_CONNECTED` 고정 false가 SDK 충전 포트 열림보다 우선해, 포트 Open + 스위치 Off가 "충전기 연결 안 됨"으로 보인다.
- **D3 (치명)** `PARKING_BRAKE_ON` 고정 false라 안전 확인에서 "주차 브레이크를 걸어 주세요"로 대기, 실제 차량 경로로는 세션을 시작할 수 없다. 텔테일(`ParkingBrake`)을 켜도 다른 경로라 바뀌지 않는다.
- (잠재) `EV_CHARGE_TIME_REMAINING`도 고정 기본값이면 가짜 남은 시간을 믿을 수 있다.

**수정 (같은 날, vehicle-integration-dev)**: AAOS 보강 값은 "연결한 뒤 값이 바뀌는 것을 본(live)" 경우에만 권위 있는 값으로 쓴다(`vehicle/pleos/AaosLiveness.kt`의 `LivenessTracker`).

- 충전 상태: SDK 충전 스위치 추론이 우선. AAOS `EV_CHARGE_STATE`는 live일 때, 또는 차량이 충전 스위치 자체를 주지 않을 때만 우선(스위치를 잠깐 못 읽은 순간에 고정 "충전 완료"로 수면 중 사용자를 깨우지 않게).
- 커넥터: AAOS 값이 live면 그 값, 아니면 AAOS true 또는 SDK 충전 포트 열림.
- 주차 브레이크: 체결(true)은 그대로, 풀림(false)은 live일 때만 믿고 아니면 "알 수 없음" → SafetyGuard가 건너뛰고 기어 P만 본다(킥오프 4.4). SafetyGuard는 그대로.
- 남은 충전 시간: live이고 0보다 클 때만.
- 단위 테스트 459개 중 440개 실행(새 22개), 실패 0.
- **수정 debug 빌드로 재확인(권한 `CAR_ENERGY` 허용, 우회 패치 없음)**: 포트 닫힘 → "충전기 연결 안 됨", 포트 열림·스위치 끔 → "충전 대기 중", 충전 → "80%까지 약 28분 남았어요 · 20분 파워냅 어때요?", 안전 확인 "주차 브레이크: 확인할 수 없어 건너뜀" → 뒷좌석 질문 → 수면, 도어 열림 중단·복구, 기어 D 중단·복구, 충전 끔 조기 기상·브리핑 모두 통과(`docs/screenshots/emulator/fixed_*.png`). 수정한 **release APK는 아직 다시 만들지 않았다**(서명 키가 노트북에만 있다).
- 남은 위험: 실차에서 앱 연결 뒤 값이 한 번도 바뀌지 않으면 그 AAOS 값은 쓰지 않는다(주차 브레이크가 처음부터 풀려 있으면 대기 대신 기어 P만으로 시작). 주차 브레이크가 고정 true인 환경에서는 "확인했어요"로 표시된다. AAOS 기어 대체 값에는 live 판정을 넣지 않았다. 이 에뮬레이터에서는 AAOS `GEAR_SELECTION`이 Virtual Console 기어 변경에 따라 실제로 갱신됨을 확인했다(`car_service` 이벤트, P=4·D=8). SDK 기어를 못 읽으면서 AAOS 기어가 고정 P인 환경이 있다면 시작 조건이 느슨해질 수 있다.

**에뮬레이터 한계(앱 결함 아님)**

- 시트 등받이 위치(`SEAT_BACKREST_ANGLE_1_POS`)가 VHAL에서 0에 고정된다. Virtual Console의 `recline` 위치 값과 액추에이터 이동은 콘솔 내부 값만 바꾸고 AAOS에는 `..._MOVE` 이벤트만 나간다. AOSP 정의상 min(0)은 완전히 젖힌 위치라, 앱은 "이미 누워 있음"으로 판단해 움직이지 않는다(올바른 동작). 그래서 시트 리클라인·정지·복구는 이 에뮬레이터에서 끝까지 확인할 수 없다. 앱의 방향 가정(작은 값 = 누움, 눕히기 = `BACKWARD`)은 AOSP `VehicleProperty.aidl` 정의와 일치한다.
- 시스템 IME(`ai.umos.inputmethod`)가 부팅 직후 한 번 죽는다(앱과 무관).

### 에뮬레이터 검증 — 개발 PC에서 관찰한 것과 막힌 점 (2026-11-12, 예전 기록)

개발 PC: Windows 11 + WSL2, 메모리 8GB, i7-9750H, GTX 1650. 에뮬레이터는 Windows 쪽에서 WHPX 가속으로 실행했다(WSL 안에서는 `/dev/kvm` 권한이 없어 부팅 불가).

1. **앱 설치·실행·SDK 바인딩: 확인.** `Vehicle.initialize()` → VehicleService 바인딩(`ServiceConnected`) → 첫 capability 요청까지 로그로 확인했다.
2. **VehicleService가 요청을 처리하지 않음.** 서비스 로그가 계속 `isActive false`이고, 앱의 첫 capability 요청이 서비스 큐에 쌓인 채 응답이 오지 않았다. 앱은 연결 대기(30초) 후 사유를 표시하고 데모 모드로 대체했다(설계대로).
3. **원인: Android `car_service`가 준비되지 않음.** 부팅 직후 Pleos 차량 HAL 서비스(`vehicle_ext`, `vehicle_diagnostic`, `diagnostic_report`, `adas`, `placesense`, `profile` — Rust SDV 서비스)가 초기화(`App::new`)에서 패닉으로 종료되기를 수백 번 반복했고, Pleos 시스템 앱 전체가 `car_service not ready, waited for car service (ms):8015000`을 기록했다. RAM 2GB·3GB, GPU 소프트웨어·하드웨어 설정을 바꿔 세 번 콜드 부팅해도 같았다. 시스템 이미지 쪽 문제라 앱에서 해결할 수 없다.
4. **Virtual Console.** HTTP API(`/vehicle/gear`, `/vehicle/ev-battery`, `/vehicle/body`, `/vehicle/seat`, `/vehicle/hvac`, `/vehicle/power-system/simulator`)로 값 주입이 된다. 단 브라우저 세션 하트비트(`POST /browser/session`, 1초 주기)가 있어야 하고, 없으면 `{"error":"emulator_inactive"}`. `scripts/vconsole.sh`가 이를 처리한다. 전원 상태(`power_system`)는 기본으로 발행되지 않아 `On`으로 직접 설정했다.
5. **음성.** 에뮬레이터에는 Pleos 음성 서비스(CAAS)만 있고 온디바이스 엔진이 없어 TTS·STT 모두 `ON_DEVICE_ENGINE_ERROR`. 서버 모드는 Pleos Playground 앱 등록(Client ID/Secret)이 필요하다. 앱은 음성이 꺼진 상태로 터치만으로 동작했다.
6. **화면 캡처.** `-gpu host`에서는 `screencap`이 흰 화면, 소프트웨어 렌더링에서는 부하(load 40 이상)로 녹화가 어렵다. 그래서 시연 영상의 앱 장면은 실제 화면 코드에 세션 상태를 엔진 흐름 순서대로 넣으며 캡처했다(`DemoVideoClips`, 30fps). 에뮬레이터 실행 화면은 `docs/screenshots/emulator/`에 있다.

### 2차 PT 전에 할 일 (실행 검증)

2026-11-15 KVM 서버 검증으로 1(연결·실제 잔량)·2(제안, 권한 거부 경로)·4(도어·기어·충전 중단)는 확인했다. 남은 것:

1. 결함 D1~D3은 고쳤고 수정 debug 빌드로 권한 허용 경로를 재확인했다. 남은 것: 노트북에서 `scripts/package-release.sh`로 **수정한 release APK**를 만들어 같은 시나리오를 한 번 더 확인하고, README "검증 상태"를 갱신.
2. 시트 리클라인·정지·복구: 이 에뮬레이터는 등받이 위치를 VHAL에 내보내지 않아 확인할 수 없다. 단위 테스트(`SeatMoverTest`)와 공조 경로 실측으로 대신하고, 발표에서는 이 한계를 그대로 말한다.
3. 음성 명령 인식: 마이크가 있는 PC의 에뮬레이터(창 모드)나 scrcpy 오디오 전달로 "20분만 재워줘"·"없어요"·"깨워줘" 확인.
4. 20분 실제 길이 세션 1회.

## 단계별 기록

### SDK 검증 — 설계를 바꾼 발견

- Vehicle SDK 2.3.2에는 충전 상태·남은 충전 시간·주차 브레이크 API가 없다 → 충전 스위치·잔량·포트로 추론, 잔량 변화 속도로 추정, AAOS 속성은 읽을 수 있을 때만 보조로.
- 시트 등받이는 절대 위치 지정이 없다 → 방향 이동 + 위치 구독 + 정지 명령(정지가 확인될 때까지 재전송).
- 에뮬레이터 화면은 2,560×1,440 **160dpi**(1dp = 1px) → 화면은 1280dp 너비 기준 설계 캔버스로 그린다.

### 구현

- 계층별 서브에이전트가 각자 git worktree에서 구현하고 메인 세션이 병합했다. 계획과 계약: `docs/superpowers/plans/2026-11-11-chargenap.md`.
- 독립 코드 리뷰에서 안전 관련 결함 9건(동의 무효화 누락, 백그라운드 감시 중단 가능성, 벽시계 타이머, STOP 실패 처리, 복구 재시도, 값 경쟁, AAOS 기어 UNKNOWN, 음성 답 순서, 저장 예외)을 찾아 모두 고쳤다.
- 실제 에뮬레이터에서 찾아 고친 것: 연결 대기 8초 → 30초(서비스 콜드 스타트), 데모 60배속 → 30배속·시작 잔량 30%(느린 기기에서 조작할 틈), 데모 충전 재시작.

### 시연 영상·앱 애니메이션 개선 (2026-11-12)

- 앱: 화면 전환(밀려 들어옴·어두워짐·밝아짐), 홈 링 차오름·숫자 카운트, 안전 확인 체크 그리기, 준비 중 시트 젖힘·바람 흐름, 수면 호흡 원·숫자 구르기·물결, 기상 새벽빛, 브리핑 카운트업, 중단 경고 흔들림·복구 체크, 리포트 막대 자라남, 안내 떠오름. 안전 동작(세션 흐름·제어 순서)은 바꾸지 않았다. 단위 테스트 418개 통과.
- 영상: 모션 그래픽 중심으로 다시 만들었다(2분 2초, 15장면, 심사 기준별 구성은 `docs/demo-script.md`). 앱 장면은 `DemoVideoClips`로 실제 화면 코드를 30fps 연속 캡처했고, 내레이션은 ElevenLabs "Jian", 화면 요소는 내레이션 단어 시각에 맞췄다. 음성 조작 장면은 재현 화면이라고 화면에 적었다.
- release APK를 애니메이션이 들어간 코드로 다시 빌드했다.

### 알려진 한계

- 실제 SDK 호출 코드(`PleosVehicleGateway`, `PleosVoiceController`)는 컴파일, SDK 공개 소스 대조, 가짜 포트 단위 테스트, 에뮬레이터 바인딩까지 확인했다. 차량 값 읽기·제어 실행 검증은 위 "2차 PT 전에 할 일".
- SDK 추론 경로에서는 잔량 100%일 때만 `COMPLETE`다. 목표 잔량(기본 80%) 도달은 엔진이 추정치로 판단해 깨운다.
- 시트 영역은 운전석 단독 키(`ROW_1_LEFT`, 좌핸들 가정)만 쓴다.
- 연결에 실패해 데모로 대체된 뒤에는 앱을 다시 시작해야 실제 차량에 다시 연결한다.
