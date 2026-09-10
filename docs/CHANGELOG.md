# 문서·프로토타입·앱 변경 이력

## 안드로이드 앱 v1.0.0 — 2026-09-10

설계서 §10.2 스프린트 1~4를 한 번에 구현한 첫 릴리스. `android/`.

- `:core:policy` — `Policy.kt`(웹 `engine.js` 1:1 포팅), `PolicyTest.kt` 12건(웹 테스트 벡터 동일).
- 감지 — `DetectionService`(UsageStats 1초 폴링, 화면 켜짐 시만, `specialUse` FGS, 부팅/업데이트 재시작), `HabitAccessibilityService`(선택, 윈도우 전환만 관찰, `performGlobalAction(HOME)`).
- 개입 — `OverlayController`(서비스 컨텍스트의 ComposeView 수명주기 소유자, 포커스 가능 오버레이), `InterceptorScreen`(HOLD 진행 바·스왑 4종·우회 잠금), 앱 전환 시 자동 해제(DISMISSED_HOME 기록).
- 데이터 — Room 7테이블(§5 그대로, `schemas/` 내보내기), 시드 단어 12개, 자동 백업 포함.
- UI — 온보딩(권한 6단계), 대시보드(Focus Score·오늘/7일·최근), 대상 앱(설치 앱 검색·쉴드·할당·허용 목록), 메모(.md 내보내기: MediaStore, API<29 SAF), 설정(시간대·요일·쿨다운·캘린더·권한 상태·서비스 제어), 공유 카드 공유 시트.
- 릴리스 — 서명된 APK/AAB, GitHub Actions(`android-ci.yml`, `release.yml`), `RELEASE.md`.

설계 대비 달라진 점(의도적):

| 설계 | 구현 | 이유 |
| :-- | :-- | :-- |
| 6개 Gradle 모듈(§6.5) | `:core:policy` + `:app`(패키지 분리) | 1인 개발에서 모듈 분리는 빌드 시간만 늘림. 정책 엔진 격리가 핵심 |
| UsageStats 집계로 사용 시간 산출 | 폴러가 포그라운드 대상 앱의 초를 직접 누적 | 개입 규칙과 같은 시계·같은 판정 기준을 쓰기 위해 |
| OEM별 백그라운드 안내 카드 | 배터리 최적화 제외 요청만 | P1 실기기 결과 후 작성(BACKLOG) |
| 텔레메트리(옵트인) | 없음 | P2 전에는 측정 항목 확정 안 됨 |

구현 중 발견한 결함: HOME 인텐트를 받는 모든 액티비티를 런처로 취급하자 시스템 설정 앱(`FallbackHome`)이 조용히 제외되었다. 기본 홈 앱 하나만 런처로 판정하도록 수정하고 PRD §6.1에 기록.


## PRD/SDD v1.1.0 — 2026-09-10

원본 v1.0.0(2026-09-09, Google Docs)을 검토한 결과, **가치 제안·스왑 4종·와이어프레임·Room 골격은 유지**하고 아래를 정정·보강했다. 원본은 [`archive/2026-09-09_PRD-SDD_v1.0.0_original.md`](archive/2026-09-09_PRD-SDD_v1.0.0_original.md)에 그대로 보관한다.

### 정정 (원본의 오류·모순·미정의)

| 항목 | 원본 | v1.1.0 |
| :-- | :-- | :-- |
| 포그라운드 감지 | AccessibilityService 단일 경로 | **UsageStatsManager 폴링을 주 경로**, 접근성은 선택. Google Play가 접근성 API의 비접근성 용도에 선언·심사를 요구해 거절 리스크가 큼 (§6.1, R-1) |
| 오버레이 플래그 | `FLAG_NOT_TOUCH_MODAL` | **제거**. 터치가 아래 앱으로 새면 마찰이 무력화된다. 입력이 필요한 퀵 메모에서만 포커스 가능 (§6.2) |
| 홈 복귀 | `context.startActivity(HOME)` | Android 10+ 백그라운드 액티비티 시작 제한 → 접근성 보유 시 `performGlobalAction(HOME)`, 아니면 인텐트 시도 후 안내 폴백 (§6.3) |
| "INTERNET 권한 없음" vs "인앱 결제 연동" | 모순 | Play Billing은 Play Store 앱이 통신하므로 앱 매니페스트에 INTERNET이 없어도 동작함을 명시 (§6.7) |
| `savedTimeSeconds = 300`을 로그마다 저장 | 설정 변경 시 과거 통계 불일치 | 로그에서 제거, **설정값(5분)으로 파생 계산** (§4.6, §5) |
| `shieldLevel: String` | 자유 문자열 | `enum ShieldLevel { AUTO, SOFT, NORMAL, HARDCORE }`, **AUTO(상황 인지) 추가** (§5) |
| 쿨다운 | "Check Blacklist & Cool-down"으로 언급만 | **우회 후 10분 통과** 규칙, 앱별 적용, 스왑은 쿨다운을 만들지 않음 (§4.1) |
| Context-Aware Shield | 캘린더·시간대 조건만 서술 | 우선순위(할당 > 취침 > 회의 > 업무 > 일상), 기본 시간대, 주말, 권한 거부 폴백 (§4.3) |
| HARDCORE | "No Bypass" 한 단어 | 대기 5초 + 계속 버튼 잠김 표시, 스왑·홈은 항상 가능 (§4.2, §4.4) |
| Focus Score | 미정의(레퍼런스 인용만) | **70% 스왑 비율 + 30% 할당 준수** 공식, 데이터 없을 때 "–" (§4.7) |
| 4.2 플로우의 `[5s Expired → Continue]` | 모든 경우 계속 가능으로 읽힘 | HARDCORE에서는 존재하지 않음을 명시 |
| iOS 확대 | 로드맵에 한 줄 | 오버레이·포그라운드 감지 불가, Screen Time API 제약으로 동일 UX 불가 → 별도 설계 대상 (§10.3) |
| 오탈자 | "기기 낸부", "윌도우 감지", "원 센컨드" | 내부, 윈도우, 원 세컨드 |

### 추가 (원본에 없던 섹션)

- §0 한눈에 보기 · §3 페르소나·성공 지표 정의
- §4.1 인터셉션 규칙 표(허용 목록, 쿨다운, 유예, 할당 소진, **시간당 6회 초과 시 SOFT 완화** — 마찰 피로 가드)
- §4.5 스왑별 완료 조건 · §4.8 공유 카드에 개인정보 미포함 · §4.9 .md 익스포트 포맷·멱등성
- §4.10 온보딩·권한 플로우(6단계, 거부 시 폴백) · §4.11 엣지케이스 표
- §5 `allowlist`, `usage_daily`, `settings`, `vocab` 엔티티, 마이그레이션·백업 정책
- §6.4 프로세스 생존(포그라운드 서비스 타입, OEM) · §6.5 모듈 구조(`:core:policy` = 웹 엔진 1:1) · §6.6 배터리 예산
- §8 가격 재검토(💭), 오픈코어 정의, Play 정책 체크리스트
- §9 검증 단계 P0~P3 · §11 리스크 레지스터 · §12 오픈 이슈 · 부록 C 웹↔안드로이드 매핑

### 수치 확정 (`src/data.js` 단일 원천)

- 쉴드: SOFT 3초 / NORMAL 5초 / HARDCORE 5초·우회 불가.
- 취침 23:00~07:00, 업무 평일 09:00~18:00, 우회 후 쿨다운 10분, 스왑 후 유예 0분, 절약 추정 5분/회, 시간당 개입 상한 6회.
- 기본 대상: Instagram 30분 · YouTube 45분 · TikTok 20분 할당. 허용 목록: KakaoTalk, Maps.

## 웹 프로토타입 v0.1.0 — 2026-09-10

- 최초 공개. 폰 시뮬레이터(홈 → 개입 → 스왑 4종 → 홈 복귀 / 우회 → 가짜 앱 사용량), 상황 시뮬레이터(시각·요일·회의), 앱별 쉴드·할당 설정, 대시보드(Focus Score·연속 일수), 공유 카드 PNG, 퀵 메모 .md 내려받기, KR/EN, localStorage 저장.
- 엔진 테스트 11건(`npm test`), GitHub Actions CI.
