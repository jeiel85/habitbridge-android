# [PRD / SDD] HabitBridge: Intentional Friction & Micro-Swap — 제품·시스템 설계서

- **문서 버전**: v1.1.1 (2026-09-10) · 원본 v1.0.0(2026-09-09)은 [`docs/archive/`](archive/2026-09-09_PRD-SDD_v1.0.0_original.md)에 그대로 보관
- **변경 요약**: [`docs/CHANGELOG.md`](CHANGELOG.md) · 되돌리기 어려운 결정: [`docs/DECISIONS.md`](DECISIONS.md) · 미뤄둔 개선점: [`docs/BACKLOG.md`](BACKLOG.md)
- **프로젝트 명**: HabitBridge (해빗브릿지) — "Don't just block dopamine. Swap it intentionally."
- **플랫폼**: Android 1차(Kotlin / Jetpack Compose, minSdk 26 · target 최신). iOS는 §10.3에서 별도 판단
- **라이브 데모(웹 프로토타입)**: <https://jeiel85.github.io/habitbridge-android/> — 정책 엔진과 인터셉터 UX를 브라우저 폰 시뮬레이터로 체험
- **안드로이드 앱**: [`android/`](../android/) (Kotlin · Jetpack Compose · Room), 릴리스 APK/AAB는 [GitHub Releases](https://github.com/jeiel85/habitbridge-android/releases), 빌드·검증 절차는 [`RELEASE.md`](../RELEASE.md)
- **문서 목적**: 기존 스크린타임·디지털 디톡스 앱의 결함(차단 후 공백, 마찰 피로, 구독 강요, 프라이버시)을 극복하는 제품을, **규칙·수치·권한 정책·데이터 모델·검증 계획까지 즉시 구현 착수 가능한 수준**으로 확정한다.

> **상태 표기 규칙**
> ✅ **검증됨** — 웹 프로토타입 또는 안드로이드 앱 v1.0.0에 구현되어 테스트·실행으로 확인한 항목 (안드로이드 전용 검증은 📱로 표기)
> 🔧 **확정(미구현)** — 설계는 확정했고 안드로이드 구현에서 만들 항목
> 💭 **검토 중** — 대안이 남아 있어 실사용 테스트 결과로 결정할 항목

---

## 0. 한눈에 보기 (Executive Summary)

| 항목 | 내용 |
| :-- | :-- |
| 한 줄 컨셉 | 인스타·유튜브·틱톡을 켜는 순간 3~5초 멈춤을 주고, **차단 대신 1분짜리 가치 행동(스트레칭·단어·메모·감정 체크인)으로 스왑**하는 로컬 우선 스크린타임 앱 |
| 카테고리 | 생산성 · 디지털 웰빙 · 스크린타임 마찰(Friction) 인터셉터 |
| 레퍼런스 | 《Opal》(차단·통계), 《One Sec》(호흡 지연), 《ScreenZen》(점진 마찰) |
| 핵심 차별화 | ① 차단→스왑(마이크로 스왑 4종) ② 상황 인지형 스마트 쉴드 ③ 100% 로컬·서버 없음 ④ 1회 결제 |
| 대상 사용자 | 하루 2시간 이상 숏폼을 "무의식적으로" 보는 20~40대, 이미 차단 앱을 써봤다가 2주 안에 지운 사람 |
| 핵심 지표 | 2주 유지율 ≥ 60%, 권한 해제율 ≤ 20%, 스왑 비율(스왑+홈 복귀 ÷ 개입) ≥ 40%, 1점 리뷰 사유 "짜증" 비중 감소 |
| 가격 | 1회 결제(가격은 §8.1에서 💭), 구독·광고·데이터 판매 없음 |
| 현재 상태 | **안드로이드 앱 v1.0.0 구현 완료**(`android/`): UsageStats 감지 서비스 + 선택적 접근성 서비스, 오버레이 인터셉터, 스왑 4종, Room, 대시보드·공유 카드·.md 내보내기, 온보딩. 에뮬레이터(API 36)에서 개입→스왑→홈 복귀·우회→쿨다운·내보내기 검증. 웹 시뮬레이터는 설계 검증용으로 유지 |

---

## 1. 시장·레퍼런스 분석

### 1.1 분석 대상과 취할 것

| 레퍼런스 | 잘하는 것 | 이 제품이 취하는 것 | 피하는 것 |
| :-- | :-- | :-- | :-- |
| 《Opal》 | 세션·통계·공유 카드 UX, 브랜드 | 통계 카드의 "자랑 가능한 숫자" 구성 | 로컬 VPN 프로필 방식(배터리·트래픽 우려), 연 구독 |
| 《One Sec》 | 호흡 지연으로 반사 끊기, 지연 후 "정말 열까?" 질문 | 개입 순간의 질문 한 줄, 지연 후 선택 | 획일적 지연(상황 무관), 대안 행동 없음 |
| 《ScreenZen》 | 열수록 길어지는 지연, 무료 | 마찰 피로 방지 관점 | 통계·동기 부여 부족 |

### 1.2 바이럴 핵심 요인 (Viral Drivers)

1. **도파민 디톡스 트렌드**: 숏폼 중독·무의식 진입(Autopilot)에 대한 강한 문제 인식.
2. **인지적 마찰**: 1~5초 지연이 "스크롤 반사"를 이성적 선택으로 바꾼다는 체감.
3. **공유용 성적표**: "Focus Score 85", "오늘 2시간 15분 절약" 같은 카드가 스토리로 퍼진다.

### 1.3 기존 앱의 결함 (Pain Points) → 대응

| 결함 | 관찰 | 대응(§) |
| :-- | :-- | :-- |
| 차단 후 공백(The Void) → 풍선 효과 | 인스타를 막으면 유튜브·브라우저로 우회. 대안 행동이 없으면 실패 | 마이크로 스왑 4종을 개입 화면 안에 둔다(§4.5) |
| 마찰 피로 → 2주 내 삭제 | 급한 메신저·업무 앱에도 5초 강제 → 권한 해제 | 상황 인지 쉴드(§4.3), 허용 목록(§4.1), 우회 후 쿨다운·시간당 상한(§4.1) |
| 연 $99 구독 | 로컬 유틸리티에 과도한 구독 | 1회 결제(§8) |
| 프라이버시·배터리 | VPN 프로필, 서버 전송 | INTERNET 권한 없음, 화면 켜짐 시에만 감지(§6.6~6.7) |

---

## 2. 제품 개요 & 가치 제안

> **"Don't just block dopamine. Swap it intentionally."**

| # | 가치 제안 | 해결하는 결함 | 성공 판정 기준 | 상태 |
| :-- | :-- | :-- | :-- | :-- |
| +α1 | **From Block to Swap** — 개입 화면에서 1-Tap으로 [1분 자세 리셋] [단어 1개] [영감 퀵 메모] [감정 체크인] 중 하나를 즉시 실행하고 홈으로 | 차단 후 공백 | 개입 중 스왑 선택률 ≥ 25%, 스왑 비율 ≥ 40% | ✅ |
| +α2 | **Context-Aware Smart Shield** — 취침·회의 중은 우회 불가, 업무 시간은 5초, 일상은 3초 리마인더. 우회하면 10분 쿨다운 | 마찰 피로 | 2주 권한 해제율 ≤ 20% | ✅ |
| +α3 | **100% Local-First & Zero Server** — INTERNET 권한 없음, Room DB에만 저장, .md로 내보내기 | 프라이버시·배터리 | 데이터 안전 섹션 "수집 없음", 배터리 사용 상위 5개 앱 미진입 | 🔧 (원칙은 ✅) |
| +α4 | **Pay-Once & 오픈코어** — 정책 엔진·UI는 공개 소스, 스토어 빌드는 1회 결제 | 구독 강요 | 전환율·환불률 추적 | 🔧 |

---

## 3. 타깃·페르소나·성공 지표

### 3.1 페르소나

- **P1 "퇴근 후 릴스 2시간" (28세 직장인)**: 차단 앱을 두 번 깔았다 지웠다. 이유는 "회의 중 카톡 열 때도 5초 기다리라니". → 허용 목록·상황 인지 쉴드가 핵심.
- **P2 "공부하다 유튜브" (22세 대학생)**: 하드코어 차단은 원하지만, 자기가 정한 시간에만. → 할당량 + 취침 시간대 HARDCORE.
- **P3 "숫자를 보면 움직이는 사람" (35세)**: Focus Score·연속 일수·공유 카드가 동기. → 대시보드·카드.

### 3.2 성공 지표 정의

| 지표 | 정의 | 목표 |
| :-- | :-- | :-- |
| 스왑 비율 | (스왑 + 홈 복귀) ÷ 전체 개입 (7일) | ≥ 40% |
| 권한 유지율 | 설치 14일 후 사용 통계 접근 + 오버레이 권한이 모두 살아 있는 비율 | ≥ 80% |
| 2주 유지율 | 14일 후 앱이 설치·활성인 비율 | ≥ 60% |
| 절약 추정 | (스왑 + 홈 복귀) × 5분 | 대시보드 표시용, KPI 아님 |
| 마찰 피로 신호 | 시간당 6회 초과 개입 발생 비율, 우회 비율 상승 추세 | 감소 |

---

## 4. 기능 명세 ✅ (웹 시뮬레이터 + 안드로이드 v1.0.0)

### 4.1 인터셉션 규칙 — 언제 개입하는가 (`src/engine.js` `decide`)

앱이 포그라운드로 올라올 때 아래 순서로 판정한다. 첫 매치가 결과다.

| 순서 | 조건 | 결과 |
| :-- | :-- | :-- |
| 1 | 허용 목록(메신저·지도 등 사용자가 지정) | **통과** (마찰 없음) |
| 2 | 차단 대상이 아니거나 비활성 | 통과 |
| 3 | 이 앱을 **우회(BYPASSED)한 지 `cooldownMinutes`(기본 10분) 이내** | 통과 (쿨다운) |
| 4 | 스왑 직후 유예 `swapGraceMinutes`(기본 0) 이내 | 통과 |
| 5 | **일일 할당 소진**(오늘 사용 ≥ 할당) | 개입, 모드 QUOTA, **HARDCORE** |
| 6 | 그 외 | 개입, 쉴드 = 앱 설정이 AUTO면 상황 규칙(§4.3), 아니면 고정값 |
| 6-1 | 단, 최근 1시간 이 앱 개입이 `maxInterceptsPerHour`(6) 이상이고 쉴드가 HARDCORE가 아니면 | **SOFT로 완화** (마찰 피로 가드) |

설계 의도: 우회를 "실패"로 처벌하지 않는다. 우회했으면 10분은 조용히 두고, 한 시간에 여섯 번 열리는 앱은 마찰이 이미 "멈춤"으로 작동하지 않는 것이므로 더 세게 막지 않고 부드럽게 한다. 이것이 2주 삭제를 막는 핵심 규칙이다.

### 4.2 쉴드 레벨

| 레벨 | 대기(HOLD) | 우회(앱 계속) | 스왑·홈 |
| :-- | :-- | :-- | :-- |
| SOFT | 3초 | 대기 후 가능 | 즉시 |
| NORMAL | 5초 | 대기 후 가능 | 즉시 |
| HARDCORE | 5초 | **불가** (버튼 자체가 잠김 상태로 표시) | 즉시 |

스왑·홈 버튼은 대기 중에도 항상 활성이다. 마찰은 "앱으로 가는 길"에만 걸린다.

### 4.3 상황 인지 규칙 (Smart Shield, `evaluateContext`)

우선순위: **할당 소진 > 취침 > 회의 > 업무 > 일상**

| 모드 | 조건(기본값) | 쉴드 |
| :-- | :-- | :-- |
| QUOTA | 오늘 사용 ≥ 앱별 할당 | HARDCORE |
| SLEEP | 23:00 ~ 07:00 (자정 넘김 처리) | HARDCORE |
| MEETING | 캘린더 "바쁨" 일정 진행 중 (선택 권한) | HARDCORE |
| WORK | 평일 09:00 ~ 18:00 | NORMAL |
| FREE | 그 외 | SOFT |

- 신호 소스: 시각(기기 로컬), 요일, 캘린더(`READ_CALENDAR`, 옵트인, 거부 시 MEETING 비활성), 💭 방해금지 모드 연동, 💭 수동 "집중 모드" 토글.
- 모든 시간대·요일은 설정에서 변경 가능. 유효성: `HH:MM`, 시작=종료면 비활성.

### 4.4 인터셉터 화면 (원본 와이어프레임 유지)

```text
+-------------------------------------------------------------+
| ~/habitbridge/interceptor                                   |
+-------------------------------------------------------------+
| [!] PAUSE AND REFLECT                                       |
| Target: com.instagram.android                               |
| Mode  : Work Hours (NORMAL Shield)                          |
| > "Is this an intentional action or just a reflex?"         |
| [ 00:04s HOLD ON... ]  ← 진행 바, 끝나면 "결정할 시간"        |
| CHOOSE A MICRO-SWAP INSTEAD:                                |
| [1] 1-Min Posture Reset   [2] 1 Vocab Word                  |
| [3] Quick Inspiration Log [4] Emotion Check-in              |
| 오늘 남은 할당 12분 · 계속하면 10분 마찰 없음                 |
| [ESC] Exit to Home            [>] Continue to App (5s)      |
+-------------------------------------------------------------+
```

상태별 하단 버튼:

| 상태 | 왼쪽 | 오른쪽 |
| :-- | :-- | :-- |
| 대기 중, 우회 가능 | 홈으로 (활성) | `[>] N초 후 계속 가능` (비활성) |
| 대기 끝, 우회 가능 | 홈으로 | `[>] 앱 계속하기` (활성) |
| HARDCORE | 홈으로 | `[×] {모드}: 우회 불가` (잠김, 비활성) |

### 4.5 마이크로 스왑 4종

| 스왑 | 흐름 | 완료 조건 | 기록 |
| :-- | :-- | :-- | :-- |
| 1분 자세 리셋 | 6단계 × 10초(호흡 → 목 좌/우 → 어깨 → 가슴 → 먼 곳 보기), 원형 타이머 | 60초 완주 후 "완료" 탭. 건너뛰기는 완료로 치지 않음 | SWAPPED/STRETCH |
| 단어 1개 | 카드 앞면(단어) → 뒤집기(뜻·예문) → "외웠다". "다른 단어"로 교체 가능 | 뒤집은 뒤에만 "외웠다" 활성 | SWAPPED/VOCAB |
| 영감 퀵 메모 | 한 줄 입력(≤280자) → 저장 | 공백 제외 1자 이상 | SWAPPED/QUICK_NOTE + `quick_notes` 행 |
| 감정 체크인 | "왜 열었나?" 5개 중 1탭 (지루함·불안/스트레스·외로움·습관·피곤함) | 탭 즉시 | SWAPPED/EMOTION + emotion |

스왑 완료 → 홈으로 복귀(§4.6) → 토스트 "스왑 완료 · 절약 +5분".

### 4.6 결과 기록과 홈 복귀

| 행동 | 기록 | 다음 |
| :-- | :-- | :-- |
| 스왑 | SWAPPED, swapType, (emotion / note) | 홈 |
| 홈으로 | DISMISSED_HOME | 홈 |
| 앱 계속 | BYPASSED → 이 앱 10분 쿨다운 시작 | 오버레이 제거, 앱 사용 |

절약 추정은 로그에 저장하지 않고 **설정값(5분)으로 파생 계산**한다(원본은 로그마다 300초 저장 — 설정 변경 시 과거 통계가 어긋나므로 정정).

홈 복귀 구현은 §6.3.

### 4.7 대시보드 & Focus Score

- 오늘/최근 7일: 스왑·홈 복귀·우회 수, 절약 추정, 스왑 유형별 수, 감정 분포.
- **Focus Score (0~100) = 70% × 7일 스왑 비율 + 30% × 오늘 할당 준수율**. 개입이 하나도 없으면 "–"(점수 없음). 할당을 설정한 앱이 없으면 준수율 1.
- **연속 일수**: 하루에 (스왑+홈) ≥ 1이고 우회가 과반이 아닌 날이 이어진 수. 오늘이 아직 조건 미달이면 어제부터 센다.
- 정의를 화면에 한 줄로 노출한다(점수의 근거를 숨기지 않는다).

### 4.8 공유 카드 (1080×1920)

- 포함: 날짜, Focus Score 링, 절약 분, 스왑 비율, 연속 일수, 스왑 유형 막대, 푸터 "local-first · no server".
- **개인정보 없음**: 앱 이름, 메모 내용, 감정 기록은 넣지 않는다.
- 안드로이드: Compose → Bitmap → MediaStore Pictures/HabitBridge, 공유 시트. 웹: Canvas → PNG.

### 4.9 퀵 메모 .md 익스포트

- 위치: `Documents/HabitBridge/Notes_YYYYMMDD.md` (Scoped Storage: MediaStore Downloads/Documents 컬렉션 또는 SAF로 사용자가 폴더 지정 💭).
- 포맷: `# HabitBridge Notes YYYY-MM-DD` 아래 `- [HH:MM] 내용 _(instead of Instagram)_`.
- 내보낸 메모는 `isExportedToMd = true`. 같은 날 재내보내기는 파일을 통째로 다시 쓴다(부분 추가 대신 멱등).

### 4.10 온보딩·권한 플로우 ✅📱

| 단계 | 권한 | 필수 | 거부 시 |
| :-- | :-- | :-- | :-- |
| 1 | 사용 통계 접근(`PACKAGE_USAGE_STATS`, 설정 화면 이동) | 필수 | 앱 감지 불가 → 온보딩에서 진행 불가, 이유 설명 |
| 2 | 다른 앱 위에 표시(`SYSTEM_ALERT_WINDOW`) | 필수 | 오버레이 대신 **전체화면 알림 + 앱 자체 액티비티**로 폴백(지연 있음) 💭 |
| 3 | 알림(`POST_NOTIFICATIONS`, Android 13+) | 권장 | 포그라운드 서비스 알림 축소 표시 |
| 4 | 접근성 서비스 | **선택** | 미사용 시 감지 지연 ~1초, 홈 복귀는 인텐트 방식(§6.3) |
| 5 | 캘린더 읽기 | 선택 | MEETING 모드 비활성 |
| 6 | 배터리 최적화 제외 | 권장 | OEM별 안내 카드(§6.4) |

각 권한 화면 앞에 **왜 필요한지 한 문장 + 무엇을 하지 않는지 한 문장**을 둔다("네트워크로 아무것도 보내지 않습니다").

### 4.11 엣지케이스 표

| 상황 | 처리 |
| :-- | :-- |
| 자정을 넘기는 취침 시간대 | `inWindow`가 start > end면 wrap 처리 ✅ |
| 할당 계산의 날짜 경계 | 기기 로컬 자정 기준 `dateKey` ✅. 타임존 변경 시 그날은 두 키가 생길 수 있음 → 허용(보수적) |
| 사용자가 기기 시각을 조작 | 로컬 앱이므로 방어하지 않는다. 문서화만 |
| 대상 앱이 이미 포그라운드인 상태에서 화면 켜짐 | 켜짐 이벤트도 감지 트리거로 취급(재개입) |
| 분할 화면·PIP | 포그라운드 판정은 "마지막으로 상호작용한 앱"; PIP는 개입하지 않음 |
| 시스템 권한 다이얼로그·잠금화면 위 | 오버레이는 시스템 UI 위에 그려지지 않는다(Android 제약). 개입은 잠금 해제 후로 미룬다 |
| 오버레이가 떠 있는 동안 홈/뒤로 | 홈 = DISMISSED_HOME 기록, 뒤로 = 무시(오버레이 유지) |
| 재부팅 | BOOT_COMPLETED로 서비스 재시작, 권한 상태 재확인 |
| 앱 삭제 방어 | 하지 않는다(사용자 통제 우선, 정책 리스크 회피) |
| 쿨다운 중 다른 대상 앱 | 쿨다운은 앱별 ✅ |
| 개입 중 화면 꺼짐 | 오버레이 제거, 기록 없음 |

---

## 5. 데이터 모델 (Room) ✅📱 — 원본 정정판 (v1.0.0 스키마 = 아래, `android/app/schemas/`에 내보냄)

```kotlin
enum class ShieldLevel { AUTO, SOFT, NORMAL, HARDCORE }     // 원본 String → enum, AUTO 추가
enum class ActionTaken { SWAPPED, BYPASSED, DISMISSED_HOME }
enum class SwapType { STRETCH, VOCAB, QUICK_NOTE, EMOTION }
enum class Emotion { BORED, ANXIOUS, LONELY, HABIT, TIRED }

@Entity(tableName = "target_apps")
data class TargetAppEntity(
    @PrimaryKey val packageName: String,
    val appName: String,
    val isEnabled: Boolean = true,
    val shieldLevel: ShieldLevel = ShieldLevel.AUTO,
    val dailyQuotaMinutes: Int = 0,                 // 0 = 할당 없음
    val createdAt: Long,
)

@Entity(tableName = "allowlist")
data class AllowlistEntity(@PrimaryKey val packageName: String)

@Entity(tableName = "swap_logs", indices = [Index("timestamp"), Index("targetPackage")])
data class SwapLogEntity(
    @PrimaryKey(autoGenerate = true) val logId: Long = 0,
    val targetPackage: String,
    val timestamp: Long,
    val actionTaken: ActionTaken,
    val swappedType: SwapType?,
    val emotion: Emotion?,
    val shieldApplied: ShieldLevel,                 // 개입 당시 실제 적용된 레벨(AUTO 아님)
    val contextMode: String,                        // QUOTA | SLEEP | MEETING | WORK | FREE
    // savedTimeSeconds 제거: 설정값으로 파생 계산 (원본 정정)
)

@Entity(tableName = "quick_notes", indices = [Index("timestamp")])
data class QuickNoteEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestamp: Long,
    val content: String,
    val triggeredPackage: String,
    val isExportedToMd: Boolean = false,
)

@Entity(tableName = "usage_daily", primaryKeys = ["dateKey", "packageName"])
data class UsageDailyEntity(val dateKey: String, val packageName: String, val minutes: Int)

@Entity(tableName = "settings")                     // 단일 행
data class SettingsEntity(
    @PrimaryKey val id: Int = 1,
    val sleepStart: String = "23:00", val sleepEnd: String = "07:00",
    val workStart: String = "09:00", val workEnd: String = "18:00",
    val workDays: String = "1,2,3,4,5",
    val cooldownMinutes: Int = 10,
    val swapGraceMinutes: Int = 0,
    val savedMinutesPerAvoid: Int = 5,
    val maxInterceptsPerHour: Int = 6,
    val calendarEnabled: Boolean = false,
)

@Entity(tableName = "vocab")
data class VocabEntity(@PrimaryKey val id: Int, val word: String, val meaning: String, val example: String, val seenCount: Int = 0)
```

- 마이그레이션: v1부터 `Migration` 명시, `fallbackToDestructiveMigration` 금지(로컬 전용 데이터라 잃으면 복구 불가).
- 백업: Android Auto Backup **포함**(사용자 기기 간 이동). 메모 내용이 포함되므로 온보딩에서 고지.

---

## 6. 시스템 아키텍처 (Android) ✅📱 (v1.0.0 구현 기준, 미구현 항목은 🔧 표시)

### 6.1 포그라운드 앱 감지 — 두 경로

| 경로 | 지연 | 권한 | 정책 리스크 | 결정 |
| :-- | :-- | :-- | :-- | :-- |
| **A. UsageStatsManager 폴링** (`queryEvents`, 화면 켜짐 시 1초 주기) | ~1초 | `PACKAGE_USAGE_STATS` (설정 화면) | 낮음: Play 정책상 허용된 용도(디지털 웰빙) | **주 경로** |
| B. AccessibilityService (`TYPE_WINDOW_STATE_CHANGED`) | 즉시 | 접근성 설정 | 높음: Google Play는 접근성 API를 접근성 목적 외에 쓰는 앱에 **선언 양식·심사**를 요구하며 거절 사례가 많음 | **선택 경로** (저지연·홈 복귀 품질용) |

원본 v1.0.0은 B 단일 경로였다. A를 주 경로로 두고 B는 "켜면 더 빨라집니다"로 제공한다. 두 경로 모두 같은 `InterceptCoordinator.onForeground(pkg, source)`로 들어온다. 상세 근거는 [`DECISIONS.md` D-001](DECISIONS.md).

✅📱 v1.0.0: A = `detect/DetectionService.kt`(화면 켜짐 시 1초 `queryEvents`, `specialUse` 포그라운드 서비스), B = `detect/HabitAccessibilityService.kt`(액티비티 윈도우 전환만, 콘텐츠 미조회). 런처 판정은 **기본 홈 앱 하나**만 사용한다 — HOME 인텐트를 받는 모든 액티비티를 런처로 보면 시스템 설정 앱(`FallbackHome`)까지 제외되는 버그가 있었다.

### 6.2 오버레이

- 윈도우 타입 `TYPE_APPLICATION_OVERLAY`. **상태바·내비게이션바·시스템 다이얼로그 위에는 그려지지 않는다**(OS 제약). Android 12+에서는 다른 앱이 `HIDE_OVERLAY_WINDOWS`로 숨길 수 있다(은행·결제 앱 등) → 그 앱들은 어차피 대상이 아니다.
- 플래그: 원본의 `FLAG_NOT_TOUCH_MODAL`은 **제거**(터치가 아래 앱으로 새어 나가 "마찰"이 무력화된다). 대기·선택 상태는 `FLAG_NOT_FOCUSABLE`, 퀵 메모 입력 시에만 포커스 가능으로 전환(키보드 필요).
- 뒤로 가기: 오버레이 유지. 홈 키: 시스템이 홈으로 보내므로 DISMISSED_HOME으로 기록.
- 접근성: 오버레이 루트에 `contentDescription`·포커스 순서, TalkBack에서 카운트다운을 1초마다 읽지 않도록 `liveRegion`은 상태 변화 시점에만.

### 6.3 홈 복귀

- 원본의 `context.startActivity(HOME 인텐트)`는 **백그라운드에서의 액티비티 시작 제한**(Android 10+)에 걸릴 수 있다. `SYSTEM_ALERT_WINDOW` 보유는 문서상 예외 조건 중 하나지만 버전별로 축소되는 추세라 의존하지 않는다.
- 결정: 접근성 서비스가 켜져 있으면 `performGlobalAction(GLOBAL_ACTION_HOME)`; 아니면 HOME 인텐트를 시도하고 **실패 시 "홈 버튼을 눌러 주세요" 토스트**로 폴백. 어느 경우든 기록은 동일.
- ✅📱 v1.0.0 검증: API 36 에뮬레이터에서 접근성 없이 HOME 인텐트 경로로 스왑 완료 직후 런처 복귀 확인(`SYSTEM_ALERT_WINDOW` 보유 예외). 접근성 경로는 구현했으나 실기기 검증은 P1 항목.

### 6.4 프로세스 생존과 OEM

- 포그라운드 서비스(Android 14+: `foregroundServiceType="specialUse"` + 선언 사유). 알림은 최소 중요도, "감지 중 · 네트워크 사용 없음" 한 줄.
- `BOOT_COMPLETED` 리시버, 화면 꺼짐 시 폴링 중단(배터리), 켜짐 시 재개.
- 삼성·샤오미 등 OEM 백그라운드 제한은 온보딩 안내 카드로 대응(dontkillmyapp 기준). 해결 못 하면 "감지가 늦을 수 있음"을 솔직히 표시. 🔧 v1.0.0에는 배터리 최적화 제외 요청만 있고 OEM별 카드는 미구현(BACKLOG).

### 6.5 모듈 구조 ✅📱 (v1.0.0: 설계의 6모듈을 2모듈로 축소)

```
android/core/policy      ★ 순수 Kotlin(JVM). Policy.decide / evaluateContext / stats / focusScore / notesMarkdown — 웹 engine.js와 1:1, PolicyTest 12건
android/app
  data/                  Room 엔티티·DAO·AppDatabase(스키마 내보내기)·HabitRepository
  detect/                DetectionService(UsageStats 폴러), HabitAccessibilityService(선택), InterceptCoordinator, BootReceiver
  overlay/               OverlayController(WindowManager + ComposeView 수명주기), InterceptorScreen(대기·스왑 4종)
  ui/                    MainActivity(탭 4개), Onboarding/Dashboard/Targets/Notes/Settings 화면, Theme
  util/                  Permissions, CalendarSignal, InstalledApps, NotesExporter(MediaStore/SAF), ShareCard(Canvas→Bitmap)
```

1인 개발 규모에서 `:feature:*`·`:core:data` 분리는 빌드 시간만 늘리고 경계는 패키지로 충분하다(아키텍처 최소주의). 정책 엔진만 별도 모듈로 격리한 것이 핵심이다.

### 6.6 배터리·성능 예산

- 화면 켜짐 시 1초 폴링, `UsageEvents` 증분 조회만(마지막 타임스탬프 이후). 목표: 하루 배터리 ≤ 1%.
- 오버레이 표시까지 감지 후 300ms 이내. Compose 오버레이는 미리 생성해 두고 내용만 갱신.

### 6.7 프라이버시 원칙

- `INTERNET` 권한을 매니페스트에 넣지 않는다. Play Billing은 Play Store 앱이 통신하므로 **앱에 INTERNET 권한이 없어도 동작**한다(원본의 "INTERNET 없음 vs 인앱 결제" 모순 해소).
- 크래시 리포팅 SDK를 넣지 않는다. 대신 로컬 로그 파일 + 사용자가 원할 때 공유.
- 데이터 안전 섹션: 수집·공유 없음. 백업에 메모가 포함됨을 고지.

---

## 7. UX/UI 방향

- **톤**: Material3 다크 + 모노스페이스 "터미널" 무드는 인터셉터와 카드에만. 설정·대시보드는 일반 Material3(가독성).
- **마찰 피로 방지 원칙**: 개입 화면은 3초 안에 상황을 이해할 수 있어야 한다(대상·모드·남은 시간·선택지). 잔소리 문구 금지. 질문은 한 줄.
- **접근성**: 큰 글씨 대응, 색 + 텍스트 이중 표기(우회 잠김은 색과 `[×]` 기호), 애니메이션 축소 설정 존중, TalkBack 포커스 순서(스왑 4종 → 홈 → 계속).
- **로컬라이징**: 한국어·영어. 문자열 키 기반(웹 프로토타입과 키 공유).

---

## 8. 수익 모델 & 정책 준수

### 8.1 가격 💭

원본은 $19.99 1회 결제. 로컬 유틸리티 기준으로 높은 편이며 "구독 대비 저렴"이라는 비교 프레임이 없으면 전환이 어렵다. 후보: (a) $19.99 + 7일 체험, (b) $9.99 정가, (c) 지역 가격 차등. **P2 클로즈드 테스트에서 가격 설문 후 결정.** 어떤 경우든 구독·광고·데이터 판매는 없다.

### 8.2 오픈코어 정의

- 공개(MIT): 정책 엔진, 인터셉터 UX, 문서(이 저장소).
- 유료(스토어 빌드): 서명된 APK/AAB, 자동 업데이트, 프리미엄 단어 덱·스트레칭 가이드 콘텐츠, 지원.

### 8.3 Google Play 정책 체크리스트 🔧

- [ ] `PACKAGE_USAGE_STATS` 사용 선언(디지털 웰빙 용도) 및 프라이버시 정책 URL
- [ ] `SYSTEM_ALERT_WINDOW` 사용 사유 명시(개입 화면)
- [ ] 접근성 서비스는 선택 기능으로 분리, `isAccessibilityTool` 미선언 시 **접근성 API 선언 양식** 제출, 프롬프트에 "접근성 목적 외 사용" 고지
- [ ] 포그라운드 서비스 타입 선언(`specialUse`) 사유
- [ ] 데이터 안전 섹션: 수집 없음 / 기기 내 저장 / 백업 포함
- [ ] 결제: Play Billing 필수(구독 없음)

---

## 9. 검증 계획 & KPI

| 단계 | 방법 | 판정 기준 | 상태 |
| :-- | :-- | :-- | :-- |
| P0 | 웹 시뮬레이터 + 엔진 테스트 11건 | 규칙 표(§4.1~4.3)와 동작 일치 | ✅ |
| P1 | 내부 APK, 5명 7일 실사용 | 감지 지연 ≤ 1.5초, 오버레이 실패 0, 하루 배터리 ≤ 1% | 미실시 |
| P2 | 클로즈드 테스트 20명 14일 | 2주 유지율 ≥ 60%, 권한 유지 ≥ 80%, 스왑 비율 ≥ 40%, 가격 설문 | 미실시 |
| P3 | 프로덕션 100명 | 1점 리뷰 사유 분석, 환불률 | 미실시 |

**측정 없이 기능을 더 쌓지 않는다.** P1 전에는 iOS·위젯·소셜 기능을 시작하지 않는다.

---

## 10. 로드맵

### 10.1 현재까지 (2026-09-10)
- ✅ 정책 엔진(순수 JS) + 테스트 12건, 폰 시뮬레이터(인터셉터·스왑 4종·상황 시뮬레이터·대시보드·공유 카드·.md 익스포트), KR/EN, GitHub Pages.
- ✅📱 **안드로이드 앱 v1.0.0** (`android/`): `:core:policy` Kotlin 포팅 + 동일 테스트 벡터 12건, UsageStats 감지 서비스·선택적 접근성 서비스, 오버레이 인터셉터(HOLD·스왑 4종·우회 잠금), Room 7테이블, 온보딩·권한 플로우, 대시보드(Focus Score·연속 일수), 공유 카드(1080×1920 공유 시트), 메모 .md 내보내기(MediaStore, API<29 SAF), 부팅 재시작, KR/EN. 서명된 릴리스 APK/AAB + GitHub Actions 릴리스 파이프라인.
- 📱 에뮬레이터(Pixel 7, API 36) 검증: 온보딩 → 대상 추가 → 개입 → 감정 체크인/퀵 메모 스왑 → 홈 복귀 → 대시보드 반영, NORMAL 5초 후 우회 → 10분 쿨다운 통과, `Documents/HabitBridge/Notes_YYYYMMDD.md` 생성. 시스템 설정 앱은 오버레이를 숨기므로(OS 정책) 대상에서 제외됨을 확인.

### 10.2 안드로이드 스프린트 (원본 4스프린트 유지, 게이트 추가)

| 스프린트 | 산출물 | 게이트 |
| :-- | :-- | :-- |
| 1 (1~2주) | `:core:policy` Kotlin 포팅 + 동일 테스트 벡터, UsageStats 폴러, 오버레이 파이프라인 | 웹·Kotlin 엔진 결과 100% 일치, 감지→오버레이 ≤ 300ms |
| 2 (3~4주) | 스왑 4종, 온보딩·권한 플로우, Room | P1 시작 가능 |
| 3 (5~6주) | 대시보드·Focus Score·카드·.md 익스포트, 접근성(선택) 경로 | P1 통과 |
| 4 (7~8주) | Play 정책 체크리스트, 결제, 클로즈드 테스트 | P2 시작 |

### 10.3 iOS 💭
iOS는 서드파티가 다른 앱 위에 오버레이를 그리거나 포그라운드 앱을 감지할 수 없다. Screen Time API(FamilyControls / DeviceActivity / ManagedSettings)는 **차단 화면(Shield) 커스터마이즈는 가능하지만 앱 안에서 스왑을 실행하는 흐름은 제한**된다. 동일 UX 불가를 전제로, "쉴드 → HabitBridge 앱으로 이동 → 스왑" 흐름으로 재설계해야 하며 별도 문서로 다룬다.

---

## 11. 리스크 레지스터

| ID | 리스크 | 영향 | 대응 |
| :-- | :-- | :-- | :-- |
| R-1 | Play 접근성 API 정책으로 심사 거절 | 출시 지연 | 접근성은 선택 경로, UsageStats 주 경로(§6.1) |
| R-2 | 오버레이 권한 정책 강화·OEM 차단 | 핵심 기능 저하 | 전체화면 알림 폴백(§4.10), 감지 지연 솔직 표시 |
| R-3 | 백그라운드 제한으로 홈 복귀 실패 | UX 저하 | `performGlobalAction` 우선, 안내 폴백(§6.3) |
| R-4 | 마찰 피로 → 삭제 | 유지율 | 쿨다운·시간당 상한·허용 목록·상황 쉴드(§4.1), P2에서 수치 검증 |
| R-5 | 메모·감정 기록의 민감성 | 프라이버시 | 로컬 전용, 백업 고지, 카드에 미포함 |
| R-6 | 단어 덱·스트레칭 콘텐츠 라이선스 | 법적 | 자체 제작 또는 CC 라이선스 명시 |
| R-7 | 가격 저항($19.99) | 전환 | §8.1 설문 후 결정 |
| R-8 | iOS 동일 UX 불가 | 확장 | 별도 설계, 약속하지 않음 |
| R-9 | 1인 개발 범위 | 지연 | 게이트 로드맵, 백로그 |

---

## 12. 오픈 이슈 / 결정 대기

1. 가격과 체험 기간(§8.1) — P2 설문.
2. 오버레이 권한 거부 시 폴백 UX(전체화면 알림)의 실효성 — P1.
3. .md 저장 위치(MediaStore vs SAF 폴더 선택) — P1 사용자 피드백.
4. 방해금지 모드·수동 집중 모드 신호 추가 여부.
5. 스왑 후 유예(`swapGraceMinutes`) 기본값 0 유지 여부 — 스왑 직후 재개입이 짜증인지 P1에서 확인.

---

## 부록 A. 용어집

| 용어 | 정의 |
| :-- | :-- |
| 개입(Intercept) | 대상 앱 진입 시 오버레이를 띄우는 것 |
| 쉴드 | 개입의 강도(SOFT/NORMAL/HARDCORE) |
| 스왑 | 개입 화면에서 대안 행동을 완료하고 홈으로 돌아가는 것 |
| 우회(Bypass) | 대기 후 앱으로 계속 진입하는 것. 쿨다운을 시작 |
| 쿨다운 | 우회 후 같은 앱을 마찰 없이 열 수 있는 시간 |
| 마찰 피로 가드 | 시간당 개입 상한을 넘으면 쉴드를 SOFT로 완화하는 규칙 |
| Focus Score | 70% 스왑 비율 + 30% 할당 준수 |

## 부록 B. 문서 이력

| 버전 | 일자 | 내용 |
| :-- | :-- | :-- |
| v1.0.0 | 2026-09-09 | 최초 작성(레퍼런스 분석, 가치 제안, 스왑 4종, 와이어프레임, 아키텍처 플로우, Room 엔티티, 오버레이 코드, 4스프린트) |
| v1.1.1 | 2026-09-10 | 안드로이드 v1.0.0 구현 반영: 상태 표기(📱), §6 구현 파일·검증 결과, 모듈 구조 축소(6→2), 런처 판정 버그 기록, 부록 C 실제 파일 매핑 |
| v1.1.0 | 2026-09-10 | 개입·쉴드·상황 규칙 정밀화, Focus Score·연속 일수 정의, 엣지케이스, 데이터 모델 정정, 감지 경로 이원화(Play 정책), 오버레이 플래그·홈 복귀 정정, 권한 플로우, 프라이버시 모순 해소, 가격 재검토, 검증 계획·리스크 추가. 웹 프로토타입으로 ✅ 검증 |

## 부록 C. 웹 프로토타입 ↔ 안드로이드 매핑 (v1.0.0 실제 파일)

| 웹 (`src/`) | 안드로이드 (`android/`) | 비고 |
| :-- | :-- | :-- |
| `engine.js` decide / evaluateContext / stats / focusScore / notesMarkdown | `core/policy/.../Policy.kt` | 함수 1:1, `tests/engine.test.js` ↔ `PolicyTest.kt` 12건 |
| `data.js` 기본값 | `data/Entities.kt` `SettingsEntity` 기본값, `data/VocabSeed.kt` | 수치 단일 원천 |
| `app.js` 인터셉터 화면 | `overlay/InterceptorScreen.kt` + `overlay/OverlayController.kt` | 상태·버튼 규칙 동일(§4.4) |
| `app.js` 상황 시뮬레이터(시각·회의) | 실기기 시각 + `util/CalendarSignal.kt` | 테스트 전용 UI 없음 |
| `app.js` 가짜 앱 사용량 | `detect/InterceptCoordinator.tickUsage` (포그라운드 1초 누적) | UsageStats 집계 대신 자체 누적 |
| Canvas 공유 카드 | `util/ShareCard.kt` (Canvas → Bitmap → FileProvider 공유) | 레이아웃 동일 |
| `.md` 다운로드 | `util/NotesExporter.kt` (MediaStore Documents/HabitBridge, API<29 SAF) | 같은 날 재내보내기 시 덮어쓰기 |
| localStorage | Room `AppDatabase` (7 테이블, 스키마 v1 내보냄) | §5 |
