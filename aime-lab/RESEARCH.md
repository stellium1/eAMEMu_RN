# 비루팅 Aime 모바일 경로 조사 — 2026-10-17

## 결론과 증거의 범위

확보한 maimai 패키지의 공통 카드 처리 바이너리에는 PMm OS 바이트를 사용해 모바일 경로를
선택하는 코드가 있다. 공개 HCE-F 범위의 `02FE` IDm과 `4000` System Code, 모바일 PMm을
조합하는 실험은 타당하다. **이것은 정식 기기 및 서버에서 동작한다는 증명이 아니다.**

게임 실행·서버 요청·기기 패치는 수행하지 않았다. 실제 S26+가 연결되지 않았으며 CHUNITHM
바이너리의 동일 경로도 아직 직접 검증하지 않았다. 폰 사용 목적은 새 ID 생성 후 공식 데이터 이전이다.

## 분석 대상

사용자가 지정한 공개 자료 서버의 다음 폴더에서 필요한 파일만 읽었다.

`Sega/maimaiDX (SDEZ)/14. maimai DX CiRCLE PLUS (SDEZ 1.65)/Package/`

| 파일 | SHA-256 | 비고 |
| --- | --- | --- |
| amdaemon.exe | `96acd85271ac25a4ef464c5a548ba5ff10a3ba9e567b9e1ad051b77238434baf` | 10,236,416 bytes, native x64 |
| aime_rw_adapterMD.dll | `e934e24ea827cd83698d41409a015e8bc8274b9d046d065cf5379b9521fad2be` | native x64 |
| Assembly-CSharp.dll | `a6000f8cb59629c6b4827796397b9efece900b8f6a3a61ca36dc0f4af5ee95a0` | MVID `b059da77-c9ac-4229-8694-a8b0bbffbfc6` |
| AMDaemon.NET.dll | `764114ef92d8e81448215189cf23bdaa2d3c2d3430048a2298d0bcd27fd7d645` | MVID `751684e0-c370-4505-bb4e-f5b799557fb5` |

폴더에는 MelonLoader, Mods, segatools 및 hook DLL도 함께 있다. 위 바이너리의 정품 무수정 여부는
독립 원본과 대조하지 못했다. 폴더 이름은 배포자의 표기이며 바이너리 버전의 독립 보증이 아니다.
Ghidra 12.1.4 및 REA의 실행 없는 PE/CLI 분석을 사용했다. 아래 함수 이름은 자동 생성된 이름이다.

## 관찰한 경로

1. `Assembly-CSharp.dll`의 `Manager.AimeReaderManager.Execute`, token `0x06002ba3`:
   `AMDaemon.AimeUnit.Start`, 결과·확인 상태 조회, `AcceptConfirm`, 접근 코드 및 FeliCa ID 조회를 호출한다.
   이것만으로 RF 조건을 알 수 없으므로 native amdaemon까지 추적했다.
2. `amdaemon!FUN_140347910`은 객체 `+0x31`부터 PMm OS 분류표를 초기화한다.
   `06/07 → 3`, `10..13 → 6`, `14..1F → 7`, `F0 → 1`, `F1..F7 → 5` 등이 관찰된다.
   이 로컬 라이브러리의 범위와 실제 AimeDB 서버 허용 범위는 같다고 가정하면 안 된다.
3. `FUN_140349d30`은 리더의 카드 감지 응답에서 IDm+PMm 16바이트를 복사하고,
   PMm 두 번째 바이트를 위 표에 대입한다. 분류 3/6/7은 모바일 상태 8로 진행할 수 있다.
   검사한 이 경로에서는 IDm이 `01xx`로 시작해야 한다는 비교를 찾지 못했다.
   리더 펌웨어 및 서버에 그런 검사가 없다는 뜻은 아니다.
4. `FUN_14034c440`, state `0x2E`는 모바일 확인 패킷 `A4 + IDm + 00`을 만든다.
   `FUN_14034a3f0`, case 8은 `A5` 응답을 검사한다. Lab은 이에 필요한 최소 응답을 구현한다.
   같은 함수의 AIC 경로는 `FFFF` 재폴링 응답의 `88B4`와 블록 `82` 등을 검사한다.
   모바일 경로는 이 AIC 상태와 분리돼 있다. 최초 하드웨어 폴링의 System Code는 미확인이다.
5. `FUN_140327860`은 분류 3/6/7을 모바일 결과 플래그 경로로 연결한다.
   `FUN_1403514c0`은 IDm+PMm 16바이트를 FeliCa 계정 조회 요청에 복사한다.
   본 조사에서는 해당 네트워크 요청을 보내지 않았다.

`FUN_140345460`은 샘플에서 객체 `+4`에 1을 저장한다. 이 함수 하나만으로 ‘모든 카드가 수락된다’고
해석해서는 안 된다. 후속 PMm 분류·상태 오류·서버 확인이 별도로 존재한다.

## 남은 검증

| 단계 | 현재 상태 | 다음 증거 |
| --- | --- | --- |
| 공개 API로 앱 빌드 및 프레임 처리 | 빌드/테스트 통과 | APK와 소스 |
| S26+에서 HCE-F 등록 | 기존 eAMEMu 작동은 사용자 확인, 새 앱은 미검증 | 새 앱 시작 결과 |
| 실제 송신 IDm/PMm/SC | 미검증 | 외부 NFC-F 리더 폴링 |
| 수정하지 않은 CHUNITHM/maimai 카드 인식 | 미검증 | 기기 접근 코드 화면 및 요청 기록 |
| 정식 서버 코드 발급·데이터 이전 | 미검증 | 공식 사이트 및 실제 본인 계정 확인 |

저가 PN532/USB-UART는 폴링 확인용 후보지만 실제 기기와 동일한 검사기는 아니다.
폰 한 대만으로 자신의 NFC 컨트롤러가 송신한 PMm을 독립 측정할 수 없다.
단, 실제 기기의 접근 코드 확인 화면을 이용하면 별도 구매 없이 종단 간 시험을 먼저 할 수 있다.

## 주요 자료

- [Android HostNfcFService](https://developer.android.com/reference/android/nfc/cardemulation/HostNfcFService): 정적 PMm 설정, 서비스 선택 및 명령 전달.
- [Android NfcFCardEmulation](https://developer.android.com/reference/android/nfc/cardemulation/NfcFCardEmulation): 허용되는 System Code 및 NFCID2 범위.
- [Arcade Docs: FeliCa conversion](https://sega.bsnk.me/allnet/aimedb/felica/): 리버스엔지니어링 자료, 공식 SEGA 규격은 아님.
- [AICEcho 기능 명세](https://github.com/UmiSlat/AICEcho/blob/main/docs/FUNCTIONAL_SPEC.md): PMm hook은 표준 XML을 무시하는 기기의 보완책이라고 설명.
- [SEGA 2026년 모바일 신규 등록 종료 공지](https://my-aime.net/news/detail?newsId=290).

REA 원시 증거는 작업 PC의 `C:\Users\User\aime-analysis\rea-evidence.json`에 보관했다.
게임 코드가 포함된 원시 디컴파일 결과와 게임 바이너리는 이 포크에 올리지 않는다.
