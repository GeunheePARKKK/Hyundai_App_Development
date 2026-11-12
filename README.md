# Hyundai_App_Development

현대자동차그룹 차량용 앱 개발 공모전(Pleos Connect) 출품용 앱입니다.
주제는 "차량 자가진단 AI 정비 어드바이저"이며, 차량 고장을 감지하고 주행 환경을 반영해 정비 긴급도를 알려 줍니다.

## 폴더 구성

| 경로 | 내용 |
| --- | --- |
| `code/` | Android Studio 프로젝트 (앱 이름 CarDoctor, 패키지 `com.cardoctor.apr`) |
| `code/app/` | 앱 소스 코드 |
| `code/*.gradle.kts`, `code/gradle/` | 빌드 설정 (Gradle Scripts) |

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
2. Android Studio에서 `Open`을 누르고 **`code` 폴더**를 엽니다. (레포 최상위 폴더가 아닙니다.)
3. Gradle Sync가 끝날 때까지 기다립니다.
4. `Device Manager`에서 Pleos 가상 기기를 먼저 켜고, 차량 화면이 다 뜬 뒤에 ▶(Run)을 누릅니다.
   - 기기가 꺼진 상태에서 바로 ▶를 누르면 앱 창이 화면 밖에 만들어져 안 보일 수 있습니다. 그럴 때는 ■(Stop)을 누르고 ▶를 다시 누릅니다.

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

- 빈 프로젝트 생성 (Empty Activity, Jetpack Compose)
- Pleos Maven 저장소와 Vehicle SDK 2.3.2 추가
- 차량 데이터 권한 6개 추가: `CAR_SAFETY`, `CAR_TIRES`, `CAR_LIGHTS`, `CAR_SPEED`, `CAR_POWERTRAIN`, `READ_WINDSHIELD_WIPERS`
- Pleos Connect 에뮬레이터(v2.1)에서 `Hello Android!` 화면 실행 확인
