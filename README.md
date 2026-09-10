<div align="center">

<img src="assets/banner.svg" alt="HabitBridge — Intentional Friction & Micro-Swap" width="100%">

# HabitBridge
### Intentional Friction & Micro-Swap · 해빗브릿지

**도파민을 무작정 막지 마세요. 가치 있는 마이크로 행동으로 스왑하세요.**
로컬 우선 안드로이드 스크린타임 앱 — 설계서(PRD/SDD), 브라우저 폰 시뮬레이터, 그리고 **실제 안드로이드 앱(v1.0.0)** 이 한 저장소에 있습니다.

[![Release](https://img.shields.io/github/v/release/jeiel85/habitbridge-android?style=for-the-badge&labelColor=0b0f0e&color=34d399&label=Android%20APK)](https://github.com/jeiel85/habitbridge-android/releases/latest)
[![Live Demo](https://img.shields.io/badge/▶_Web_Demo-Phone_Simulator-60a5fa?style=for-the-badge&labelColor=0b0f0e)](https://jeiel85.github.io/habitbridge-android/)
[![PRD/SDD](https://img.shields.io/badge/📜_PRD%2FSDD-v1.1-a7bdb4?style=for-the-badge&labelColor=0b0f0e)](docs/PRD-SDD.md)

[![Android CI](https://github.com/jeiel85/habitbridge-android/actions/workflows/android-ci.yml/badge.svg)](https://github.com/jeiel85/habitbridge-android/actions/workflows/android-ci.yml)
[![Web CI](https://github.com/jeiel85/habitbridge-android/actions/workflows/ci.yml/badge.svg)](https://github.com/jeiel85/habitbridge-android/actions/workflows/ci.yml)
[![License: MIT](https://img.shields.io/badge/License-MIT-7c9a8e.svg)](LICENSE)

</div>

---

## 이 저장소는 무엇인가

《Opal》, 《One Sec》, 《ScreenZen》 같은 디지털 디톡스 앱이 왜 퍼지고 왜 2주 안에 지워지는지를 분석하고, 그 결함을 넘는 제품을 설계·구현한 프로젝트입니다.

| +α | 한 줄 |
| :-- | :-- |
| **차단 → 스왑** | 인스타·유튜브·틱톡을 켜는 순간 3~5초 멈춤. 그 화면에서 **1분 자세 리셋 · 단어 1개 · 영감 퀵 메모 · 감정 체크인** 중 하나를 1탭으로 실행하고 홈으로 |
| **상황 인지형 스마트 쉴드** | 취침·회의 중은 우회 불가, 업무 시간은 5초, 일상은 3초. 우회하면 10분은 조용히, 한 시간에 6번 넘게 열리면 오히려 완화(마찰 피로 방지) |
| **100% 로컬 · 서버 없음** | `INTERNET` 권한 자체가 없음. Room DB에만 저장, 메모는 `Documents/HabitBridge/*.md`로 내보내기 |
| **1회 결제 · 오픈코어** | 구독·광고·데이터 판매 없음. 정책 엔진과 앱은 MIT 공개 소스 |

세 산출물은 같은 규칙을 공유합니다. 정책 엔진은 웹(`src/engine.js`)과 안드로이드(`android/core/policy`)에 함수 단위로 1:1 포팅되어 있고, 같은 12개 테스트 벡터를 통과합니다.

## 안드로이드 앱 (v1.0.0)

**설치**: [Releases](https://github.com/jeiel85/habitbridge-android/releases/latest)에서 `habitbridge-1.0.0.apk`를 받아 설치합니다(Android 8.0+, minSdk 26). Play 스토어 등록 전 단계라 사이드로드입니다.

**구현된 것**

- 감지: `UsageStatsManager` 1초 폴링 포그라운드 서비스(화면 켜짐 시만, `specialUse`) + 선택적 접근성 서비스(더 빠른 감지, 홈 복귀)
- 개입: `TYPE_APPLICATION_OVERLAY` 인터셉터 — 3/5초 HOLD 진행 바, 스왑 4종, 우회 버튼(HARDCORE에서는 잠김), 우회 후 10분 쿨다운, 시간당 6회 초과 시 완화, 일일 할당 소진 시 우회 불가
- 스왑 4종: 1분 자세 리셋(6단계 타이머), 단어 카드(12개 시드 덱, 회전), 퀵 메모(280자), 감정 체크인(5종)
- 데이터: Room 7테이블(대상·허용 목록·개입 로그·메모·일일 사용량·설정·단어), 스키마 내보내기, 자동 백업 포함
- 앱 UI: 온보딩(권한 6단계, 거부 시 이유 설명), 대시보드(Focus Score 링·오늘/7일·최근 기록), 대상 앱 관리(설치 앱 검색·쉴드·할당·허용 목록), 메모(.md 내보내기), 설정(취침·업무 시간·요일·쿨다운·캘린더)
- 공유 카드 1080×1920(개인정보 없음) → 공유 시트, 부팅/업데이트 후 서비스 재시작, 한국어/영어
- 없는 것(의도적): `INTERNET` 권한, 분석 SDK, 크래시 리포터, 앱 삭제 방어

**검증(에뮬레이터 Pixel 7 · API 36)**: 온보딩 → 대상 추가 → 개입 → 스왑 → 홈 복귀 → 대시보드 반영, NORMAL 5초 후 우회 → 쿨다운 통과, 퀵 메모 → `Notes_YYYYMMDD.md` 생성까지 확인했습니다. 절차는 [`RELEASE.md`](RELEASE.md) §3 체크리스트에 있습니다. 시스템 설정 앱처럼 오버레이를 숨기는 앱(`HIDE_OVERLAY_WINDOWS`)은 OS 제약으로 개입할 수 없습니다.

**빌드**

```bash
cd android
./gradlew :core:policy:test        # 정책 엔진 테스트 12건
./gradlew :app:assembleDebug       # app/build/outputs/apk/debug/app-debug.apk
```

릴리스 서명·태그·Actions 파이프라인은 [`RELEASE.md`](RELEASE.md).

## 웹 데모(폰 시뮬레이터)에서 해볼 것

<https://jeiel85.github.io/habitbridge-android/>

1. 폰의 **Instagram** 아이콘을 누르면 터미널풍 인터셉터가 뜹니다. 5초 대기 후 `[>] 앱 계속하기`가 열립니다.
2. 오른쪽 **상황 시뮬레이터**에서 시각을 00:40으로 옮기거나 "회의 중"을 켜 보세요. 쉴드가 HARDCORE가 되어 우회 버튼이 잠깁니다.
3. 스왑 4종 중 하나를 완료하면 홈으로 돌아가고 대시보드의 Focus Score·절약 시간이 오릅니다.
4. 우회해서 앱에 들어가면 10분 쿨다운이 걸립니다. `+10분`을 누르면 다시 개입합니다.
5. 앱별 **할당량**을 넘기면 모드가 "할당 소진"으로 바뀌며 우회가 막힙니다.
6. **공유 카드** PNG와 **Notes_YYYYMMDD.md**를 내려받을 수 있습니다. 어디로도 전송되지 않습니다.

## 문서

| 문서 | 내용 |
| :-- | :-- |
| [`docs/PRD-SDD.md`](docs/PRD-SDD.md) | **설계서 v1.1** — 시장 분석, 가치 제안, 개입·쉴드·상황 규칙, 스왑 명세, Focus Score, 권한 플로우, 엣지케이스, Room 모델, 안드로이드 아키텍처(감지 경로·오버레이·홈 복귀·배터리·프라이버시), 수익 모델·Play 정책, 검증 계획, 리스크. v1.1.1에 구현 상태(📱) 반영 |
| [`docs/CHANGELOG.md`](docs/CHANGELOG.md) | 설계서 정정 목록과 앱·프로토타입 변경 이력 |
| [`docs/DECISIONS.md`](docs/DECISIONS.md) | 되돌리기 어려운 결정 7건 |
| [`docs/BACKLOG.md`](docs/BACKLOG.md) | 미뤄둔 개선점 |
| [`RELEASE.md`](RELEASE.md) | 안드로이드 빌드·서명·검증·릴리스 절차 |
| [`docs/archive/`](docs/archive/) | 2026-09-09 원본 설계서·패키지 README (내용 무수정 보관) |

## 저장소 구조

```
index.html, styles.css, src/     웹 폰 시뮬레이터 (GitHub Pages 루트) · tests/ node:test
android/
  core/policy/                   순수 Kotlin 정책 엔진 + PolicyTest (웹 engine.js와 1:1)
  app/                           안드로이드 앱 (data / detect / overlay / ui / util)
  gradle/libs.versions.toml      AGP 9.1 · Kotlin 2.2 · Compose BOM · Room 2.7
docs/                            설계서와 부속 문서
.github/workflows/               web CI · Android CI(APK 아티팩트) · Release(태그 → 서명 APK/AAB)
```

## 로드맵

- **P0 (완료)**: 정책 엔진·UX를 브라우저 시뮬레이터로 검증, 라이브 데모 공개
- **스프린트 1~4 (완료, v1.0.0)**: 안드로이드 앱 구현 + 에뮬레이터 검증 + 릴리스 파이프라인
- **P1 (다음)**: 실기기 5명 7일 — 감지 지연, OEM 백그라운드 제한, 배터리(하루 ≤ 1%), 접근성 경로
- **P2**: 클로즈드 테스트 20명 14일 — 2주 유지율 ≥ 60%, 권한 유지 ≥ 80%, 스왑 비율 ≥ 40%, 가격 설문
- **P3**: Play 정책 체크리스트·결제 → 프로덕션 100명

## 고지

《Opal》, 《One Sec》, 《ScreenZen》은 각 권리자의 제품이며 이 프로젝트는 이들과 무관한 독립 기획입니다. 웹 데모는 실제 앱을 차단하지 않고 사용 시간만 시뮬레이션합니다. 라이선스: [MIT](LICENSE)

---

<details>
<summary><strong>English</strong></summary>

### HabitBridge — Intentional Friction & Micro-Swap

A **local-first Android screen-time app**: the design document (PRD/SDD), a browser phone simulator, and the real Android app (v1.0.0) live in one repository and share one policy engine.

**Pitch.** Blocking apps fail for two reasons: the void after the block (you drift to another feed) and friction fatigue (a 5-second pause in front of your messenger during a meeting). HabitBridge keeps the pause but fills it with a **one-tap micro-swap** — a 1-minute posture reset, one vocabulary card, a one-line inspiration note, or an emotion check-in — and adapts the shield to context: no bypass during sleep hours or calendar events, 5 s during work hours, a 3 s reminder otherwise. A bypass earns 10 quiet minutes, and more than six intercepts in an hour *softens* the shield instead of nagging harder. No `INTERNET` permission, no server, pay once.

**Android app (v1.0.0).** Download the APK from [Releases](https://github.com/jeiel85/habitbridge-android/releases/latest) (Android 8.0+). Foreground detection via a 1 s `UsageStatsManager` poller (screen on only, `specialUse` foreground service) plus an optional accessibility service; a `TYPE_APPLICATION_OVERLAY` interceptor with HOLD bar, four swaps, a bypass button that locks under HARDCORE, 10-minute cool-down after a bypass, friction-fatigue softening and daily quotas; Room (7 tables), onboarding with per-permission explanations, dashboard with an explicit Focus Score, target management, Markdown note export to `Documents/HabitBridge`, a 1080×1920 share card, boot restart, KR/EN. Verified on a Pixel 7 API 36 emulator end to end (intercept → swap → home → dashboard, bypass → cool-down, note → `.md`). Build with `cd android && ./gradlew :app:assembleDebug`; signing and the tag-driven release workflow are in [`RELEASE.md`](RELEASE.md).

**Web demo.** <https://jeiel85.github.io/habitbridge-android/> — the same rules in a phone simulator with a context clock.

**Docs.** [`docs/PRD-SDD.md`](docs/PRD-SDD.md) (Korean), [`docs/CHANGELOG.md`](docs/CHANGELOG.md), [`docs/DECISIONS.md`](docs/DECISIONS.md), [`docs/BACKLOG.md`](docs/BACKLOG.md).

**Disclaimer.** Opal, One Sec and ScreenZen belong to their respective owners; this is an independent product. MIT licensed.

</details>
