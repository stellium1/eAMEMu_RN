# Aime Mobile Lab — 비루팅 연구용 APK

Galaxy S26+에서 새 모바일 Aime를 인식시킬 수 있는지 시험하는 독립 Android 앱입니다.
**실제 S26+ RF 송신, CHUNITHM/maimai 기기, 정식 서버 로그인은 아직 검증하지 않았습니다.**
공개 Android HCE-F API만 사용하며 루팅, Shizuku, 시스템 백업 복원, 결제 앱 변경을 요구하지 않습니다.

이 디렉터리는 eAMEMu_RN 포크에 추가한 실험 모듈입니다. 기존 React Native 화면은 아직 통합하지 않았습니다.
앱 ID는 `dev.stellium.aimelab`로, 원래 eAMEMu와 함께 설치됩니다. Android 기본 UI를 사용하므로
오래된 React Native의 네이티브 라이브러리나 16KB 페이지 크기 호환성에 의존하지 않습니다.

## 시험 순서

1. APK를 설치하고 NFC를 켭니다. 앱에 표시된 카드 ID를 먼저 백업합니다.
2. `Mobile 14`를 선택하고 시작합니다. 잠금을 풀고 앱을 앞에 둔 채 태그합니다.
3. 기기의 **접근 코드 확인 기능**을 먼저 사용합니다. 접근 코드를 표시하는지 기록합니다.
4. 성공하지 않으면 폰을 리더에서 떼고 중지 → `Mobile 17` → 시작 후 비교합니다.
5. `FF`는 기존 기본 PMm과의 비교용입니다. 정상 모바일 Aime로 인정될 것으로 기대하는 설정이 아닙니다.
6. 진단 기록을 저장하고 게임명·버전·지역판·기기에 표시된 결과를 함께 기록합니다.

기존 데이터 이전을 원하면 목적지에 새 게임 데이터를 만들지 마세요. SEGA는 이미 사용한 목적지로의
이전을 제한합니다. 접근 코드 발급/조회와 실제 신규 플레이를 구분해야 합니다.
데이터 이전은 인식과 코드 확인이 끝난 다음 본인이 공식 Aime 사이트에서 진행합니다.
이 앱은 서버에 직접 접속하거나 계정 정보를 전송하지 않습니다.

공식 일본 공지는 **2026-12-08 02:00**부터 오사이후케이타이의 Aime 신규 등록 종료를 예고합니다.
기존 이용 및 기기 변경 시 이전은 계속 지원한다고 안내합니다. 해외판 적용과 임의 HCE-F ID 허용 여부는
이 공지로 증명되지 않습니다.

- [신규 등록 종료 공지](https://my-aime.net/news/detail?newsId=290)
- [접근 코드 확인](https://my-aime.net/accesscode)
- [maimai 접근 코드 절차](https://my-aime.net/en/accesscode/detail?titleId=67)
- [CHUNITHM 접근 코드 절차](https://my-aime.net/en/accesscode/detail?titleId=33)
- [사용한 목적지로 이전할 수 없는 경우](https://support-arcade.sega.com/hc/ja/articles/16844605158041)

## 구현 범위

- 영구 저장한 무작위 48비트 접미부의 `02FE` NFCID2; 공개 API에 맞춘 System Code `4000`.
- XML PMm `1F14FFFFFFFFFFFF`, `1F17FFFFFFFFFFFF`, 비교용 `FFFFFFFFFFFFFFFF`.
  마지막 6바이트의 `FF`는 실험용 응답 시간 파라미터이며 실제 모바일 칩을 완전히 재현하지 않습니다.
- `04 → 05` Request Response(모드 0), `0C → 0D` System Code 목록.
- 분석 샘플이 보내는 `A4 / 00 → A5 / 00` 모바일 확인 응답.
- Request Service/Search Service에 서비스 없음, 블록 읽기·쓰기에 오류 응답.
  AIC 블록, MAC, 결제 서비스, 기존 카드 복제를 구현했다고 주장하지 않습니다.
- 앱이 배경으로 이동하면 중지. 복귀 후 시작 버튼을 다시 눌러야 합니다.
- 진단 로그는 최근 100개 이벤트만 메모리에 보관하고 카드 ID·패킷 본문을 제외합니다.
  ID 백업은 별도 JSON이며 다른 사람에게 공유할 진단 파일이 아닙니다.

`시작됨`은 Android API 등록 및 읽기 검증 성공입니다. 실제 PMm이 전파로 그대로 나가는지,
리더가 `FFFF` 또는 `4000` 폴링을 하는지, 서버가 ID를 허용하는지는 별도 확인이 필요합니다.
폴링은 NFC 컨트롤러가 처리하여 `processNfcFPacket` 로그에 나타나지 않을 수 있습니다.

## Windows에서 빌드

JDK 17, Android SDK Platform 35 및 Build Tools 35.0.0을 준비합니다.
Gradle, npm, NDK 또는 원저자의 서명 키는 필요하지 않습니다.

```powershell
.\build.ps1 -AndroidJar '<SDK>\platforms\android-35\android.jar' `
  -BuildTools '<SDK>\build-tools\35.0.0' -JdkRoot '<JDK17>'
```

출력: `build/AimeMobileLab.apk`. 같은 앱의 후속 빌드를 업데이트하려면
`build/lab-signing.keystore`를 보관해야 합니다. 개발용 서명이며 비밀번호는 `android`입니다.
빌드는 프로토콜 고정 벡터와 50,000개 잘못된 프레임 테스트, APK v2/v3 서명 및 정렬 검증을 실행합니다.

API 36.1 Android 에뮬레이터에서 설치, 첫 실행, 한글 화면 표시 및 HCE-F 미지원 시 오류 안내를
확인했습니다. 에뮬레이터 시험은 NFC 전파 송수신이나 실제 게임 인식을 검증하지 않습니다.

이 소스에는 게임 바이너리나 AICEcho의 구현 코드를 포함하지 않습니다.
분석 근거와 한계는 [RESEARCH.md](RESEARCH.md)를 참고하세요.
