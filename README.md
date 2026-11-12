# Hyundai_App_Development

현대자동차그룹 차량용 앱 개발 공모전(Pleos Connect) 출품용 앱입니다.
주제는 "차량 자가진단 AI 정비 어드바이저"이며, 차량 고장을 감지하고 주행 환경을 반영해 정비 긴급도를 알려 줍니다.

## 폴더 구성

| 경로 | 내용 |
| --- | --- |
| `app/` | 앱 소스 코드 (앱 이름 CarDoctor, 패키지 `com.cardoctor.apr`) |
| `*.gradle.kts`, `gradle.properties`, `gradle/` | 빌드 설정 (Android Studio의 Gradle Scripts) |

레포 최상위 폴더가 곧 Android Studio 프로젝트입니다.

## 실행 방법

### 사전 준비 (PC마다 한 번)

1. Android Studio 설치
2. Pleos Connect 에뮬레이터 이미지 설치
   - `SDK Manager` > `SDK Update Sites`에 아래 주소 추가
     `https://nexus-playground.pleos.ai/repository/raw-releases/connect-avd/2.x/crp-sys-img.xml`
   - `SDK Platforms`에서 `Show Package Details`를 켜고 Android 14 아래의 Pleos 이미지 설치 (약 4GB)
3. `Device Manager`에서 Pleos 이미지를 쓰는 가상 기기 생성

자세한 절차는 [Pleos 개발 문서](https://document.pleos.ai/)를 참고하세요.

### 앱 실행

1. 레포를 받습니다.
   ```
   git clone https://github.com/GeunheePARKKK/Hyundai_App_Development
   ```
2. Android Studio에서 `Open`을 누르고 받은 `Hyundai_App_Development` 폴더를 엽니다.
3. Gradle Sync가 끝날 때까지 기다립니다.
4. `Device Manager`에서 Pleos 가상 기기를 먼저 켜고, 차량 화면이 다 뜬 뒤에 ▶(Run)을 누릅니다.
   - 기기가 꺼진 상태에서 바로 ▶를 누르면 앱 창이 화면 밖에 만들어져 안 보일 수 있습니다. 그럴 때는 ■(Stop)을 누르고 ▶를 다시 누릅니다.

### AI 설명 켜기 (선택)

AI 설명은 Gemini API 키가 있어야 동작합니다. 키가 없어도 앱은 실행되고, 그때는 기본 설명이 나옵니다.

1. [Google AI Studio](https://aistudio.google.com/apikey)에서 API 키를 발급받습니다.
2. 프로젝트 최상위의 `local.properties` 파일 맨 아래에 한 줄을 추가합니다.
   ```
   GEMINI_API_KEY=발급받은_키
   ```
3. Android Studio에서 ▶로 다시 실행합니다.

`local.properties`는 깃에 올라가지 않는 파일입니다. 키를 다른 파일에 적거나 커밋하지 마세요. 모델을 바꾸려면 같은 파일에 `GEMINI_MODEL=모델이름`을 추가합니다(기본값 `gemini-3.5-flash-lite`).

### 빌드 환경

| 항목 | 버전 |
| --- | --- |
| Gradle | 9.6.0 |
| Android Gradle Plugin | 9.5.1 |
| Kotlin | 2.2.10 |
| compileSdk / targetSdk | 37 |
| minSdk | 28 |
| Pleos Vehicle SDK | `ai.pleos.playground:Vehicle:2.3.2` |

## 변경 이력

최신 변경이 위에 옵니다.

### 2026-11-12

- 화면 디자인을 어두운 고급차 계기판 느낌으로 전면 교체 (`ui/Design.kt` 추가, `ui/HomeScreen.kt` 재작성)
  - 어두운 바탕에 은색 글자와 샴페인색 강조 하나만 쓰고, 경고 색(노랑·빨강)만 눈에 띄게 함
  - 색과 공통 부품(구역 제목, 알약 버튼, 주 버튼, 구분선)을 `Design.kt`에 모아 화면 전체에서 같은 것을 씀
  - 상태 배너를 색으로 꽉 채운 상자에서 색 띠와 옅은 번짐으로 변경, 등급 배지와 고장 목록을 테두리 중심으로 변경
  - 화면 전환을 밑줄 탭으로, 켜고 끄는 버튼을 알약 모양으로 통일
  - 차량 그림을 가운데가 은은하게 밝은 무대 위에 올림
  - AI 설명 아래에 "최종 판단은 정비 전문가에게" 안내 문구 추가
- 3D 차량 모델을 각진 상자 형태에서 곡면 차체로 교체 (`ui/Car3D.kt`)
  - 앞뒤 방향으로 잘게 나눈 단면을 이어 붙여 둥근 앞뒤, 기울어진 앞·뒤 유리, 위로 좁아지는 지붕을 표현
  - 옆 유리와 가운데 기둥, 파노라마 선루프, 모서리를 감싸는 전조등·후미등, 그릴, 사이드미러, 휠 하우스 추가
  - 차체에 반사광을 넣어 금속 느낌을 냄
- 3D 차량 뷰 추가 (`ui/Car3D.kt`)
  - 끌어서 회전, 두 손가락 또는 `+`/`−` 버튼으로 확대·축소, `↺`로 처음 각도로 복귀
  - 고장 난 부품(전조등, 와이퍼·앞 유리, 타이어, 브레이크, 보닛)을 등급 색으로 표시하고, 부위를 누르면 진단 상세가 열림
  - 외부 3D 라이브러리나 모델 파일 없이 코드로 만든 저폴리곤 차량을 Canvas에 직접 그림
  - 홈 화면에서 `3D` / `평면` 버튼으로 기존 평면 그림과 전환
- 실시간 환경, 홈 환경 칩, 음성 안내 추가
  - 실시간 시각·날씨(`env/LiveEnvironmentReader.kt`): Open-Meteo(무료, 키 없음)에서 현재 날씨와 일출·일몰을 5분마다 받아 야간·악천후를 자동 판단. 인터넷이 안 되면 한국 시각만으로 낮·밤 판단
  - 기준 위치는 에뮬레이터 지도의 기본 차량 위치(성남시 수정구)로 고정
  - 홈 화면에 현재 환경 칩(주간/야간, 강수, 도로)과 출처 표시
  - 시연 패널 `환경` 줄에 `실시간 시각·날씨` 스위치 추가. 시나리오 A·B·C 버튼은 정해진 상황을 보여 주기 위해 이 스위치를 끄고, `초기화`는 다시 켬
  - 음성 안내(`voice/VoiceGuide.kt`): 가장 긴급한 고장이 3등급 이상이 되면 "브레이크 이상. 3등급, 즉시 정비. …"를 재생. 오른쪽 위 `음성 안내` 버튼으로 끌 수 있음
  - 에뮬레이터에 일반 앱이 쓸 수 있는 음성 합성 엔진이 없어, Windows 한국어 음성으로 미리 만든 파일 12개(`res/raw/voice_*.wav`)를 재생. 만드는 스크립트는 `tools/make_voice_clips.ps1`
  - 깜빡임 애니메이션이 4등급이 아닐 때도 계속 돌던 것을 4등급일 때만 돌도록 수정
- Gemini AI 설명이 `AI 응답 실패`로 나오던 문제 수정
  - 원인: 기본 모델 `gemini-3.8-flash`가 무료 등급에서 분당 5회 제한에 걸리거나 응답 없이 시간 초과됨
  - 기본 모델을 `gemini-3.5-flash-lite`로 변경 (실제 호출로 응답 확인)
  - 호출 제한(429)에 걸리면 8초 뒤 한 번 재시도, 버튼을 누른 뒤 호출까지의 대기를 1.2초로 늘림, 시간 제한을 45초로 늘림
  - 응답에 섞여 나오는 강조 기호(`**`) 제거
- 기획안에 맞춰 화면 보완, Gemini AI 설명 추가
  - 상황을 반영한 설명 문구(`diagnosis/Explainer.kt`): "지금은 낮이라 운행에 지장이 없습니다"처럼 현재 환경을 문장에 넣음
  - 등급 색과 권고 문구를 기획안 등급표대로 변경: 1·2등급 노랑, 3·4등급 빨강, 4등급은 배너와 차량 그림이 깜빡임 (음성 안내는 아직 없음)
  - 자가진단 화면 추가: 위쪽 `자가진단` 탭 → `전체 점검` 버튼, 기능별(엔진·냉각, 제동, 타이어, 등화, 시야, 구동·배터리) 진행 상태와 결과. 이상 항목을 누르면 진단 상세로 이동
  - 차량 그림의 부위를 누르면 그 부품의 진단 상세가 열림 (정상 부위는 `정상`으로 표시)
  - Gemini API로 AI 설명 생성(`ai/GeminiExplainer.kt`). 등급과 권고 행동은 규칙으로 정한 값을 그대로 전달하고 설명 문장만 생성. 키가 없거나 실패하면 기본 설명을 표시
  - `INTERNET` 권한 추가
  - Gemini 호출은 API 키 없이 빌드만 확인했고, 실제 응답은 아직 확인하지 않음
- 차량 그림을 사실적으로 다시 그림 (`ui/CarDiagram.kt`로 분리)
  - 곡선 차체와 금속 음영, 앞·옆·뒤 유리, 파노라마 선루프, 사이드미러, 전조등, 후미등 추가
  - 고장 난 부품은 등급 색으로 칠하고 해당 위치에 같은 색 원으로 표시
- 에뮬레이터에서 차량 값 읽기 확인 후 보완
  - 확인 결과(Pleos Connect 에뮬레이터 v2.1): 전조등·와이퍼·출력 제한 경고는 읽힘, 브레이크액 수위는 이 차량에서 미지원, 경고등은 권한 없음
  - 출력 제한 경고(`hasPowerDownWarningIndicator`) 읽기 추가. 켜지면 새 부품 `구동 출력 제한`(기본 심각도 3) 고장으로 판정
  - `차량 값` 줄에서 실패 원인을 `권한 없음`, `미지원 차량`, `읽기 실패`로 구분해 표시
- 실제 차량 값 연결 (`vehicle/VehicleReader.kt`)
  - Vehicle SDK에서 2초마다 읽는 값: 브레이크액 수위, 전조등(하향등) 상태, 앞 유리 와이퍼 상태, 경고등(Telltales)
  - 브레이크액이 `LOW`·`CRITICALLY_LOW`·`ERROR`이면 브레이크 고장으로 판정
  - 전조등이 켜져 있으면 야간, 와이퍼가 동작 중이면 비로 보고 환경 가중치에 반영
  - 차량에서 읽은 값과 시연 패널에서 넣은 값을 합쳐서 판정하고, 읽은 값은 시연 패널 맨 위 `차량 값` 줄에 표시
  - 와이퍼 읽기용 `CAR_WINDOWS` 권한 추가
  - 제약: 경고등과 실제 타이어 공기압은 SDK에서 시스템 앱 전용 권한(`PRIVATE_CAR_SAFETY`)으로 막혀 있음. 경고등은 읽기를 시도하고 실패하면 `읽기 실패`로 표시
- v1 첫 화면 추가
  - `diagnosis/AdviceEngine.kt`: 긴급도 판정 로직 (기본 심각도 + 환경 가중치 → 1~4등급, 판정 근거 목록 반환)
  - `diagnosis/Model.kt`: 부품 5종(전조등, 와이퍼, 타이어 공기압, 냉각수 과열, 브레이크), 주행 환경, 등급별 권고 문구
  - `ui/HomeScreen.kt`: 차량 상태·차량 그림·고장 목록, 정비 권고와 판정 근거, 시연 패널(시나리오 A·B·C, 고장·환경 직접 선택)
  - `AdviceEngineTest.kt`: 시나리오 A·B·C와 판정 규칙 단위 테스트 6개
  - 아직 실제 차량 값(Vehicle SDK)은 연결하지 않았고, 시연 패널에서 넣은 값으로만 동작
- 프로젝트를 `code/` 폴더에서 레포 최상위로 이동 (레포 폴더를 바로 열어 실행할 수 있게 함)
- 빈 프로젝트 생성 (Empty Activity, Jetpack Compose)
- Pleos Maven 저장소와 Vehicle SDK 2.3.2 추가
- 차량 데이터 권한 6개 추가: `CAR_SAFETY`, `CAR_TIRES`, `CAR_LIGHTS`, `CAR_SPEED`, `CAR_POWERTRAIN`, `READ_WINDSHIELD_WIPERS`
- Pleos Connect 에뮬레이터(v2.1)에서 `Hello Android!` 화면 실행 확인
