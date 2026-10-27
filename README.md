<div align="center">

# ⛰️ Routee Mock

**등산 앱 QA를 책상 앞에서 끝내는 안드로이드 모의 위치 앱**

<a href="https://github.com/keepdev0919/routee-mock/releases/latest"><img src="https://img.shields.io/badge/APK_받기-v0.1-3DDC84?style=for-the-badge&logo=android&logoColor=white"/></a>

</div>

<br/>

## 왜 만들었나

벤처창업동아리 **SOPT**에서 등산 서비스 **루티(Routee)**의 PM을 맡고 있어요.

루티는 산길을 걸으며 경로와 사진을 기록하는 앱이라, 기능 하나를 확인하려 해도 **직접 나가서 걷고 사진을 찍어야** 했어요. QA 기간이 되면 정해진 시간 안에 같은 길을 계속 오가야 했고요.

기존 GPS 시뮬레이터 앱도 써 봤지만 **광고가 너무 많아서** 팀 QA에 쓸 수가 없었어요. 그래서 루티 디자인에 맞춘, 광고 없는 모의 위치 앱을 직접 만들었습니다.

## 무엇을 하나요

- 실제 등산로를 **사람이 걷는 속도로 재생**해요. 고도도 같이 바뀌어요.
- 테스트 목적별로 경로를 골라 두었어요.

| 경로 | 무엇을 확인하나 |
|---|---|
| 북한산 백운대 | 계속 오르막: 최고 고도가 꾸준히 오르는지 |
| 인왕산 넘어가기 | 올랐다 내려오기: 내려와도 최고 고도가 줄지 않는지 |
| 여의도공원 한 바퀴 | 짧고 평평: 거리 · 시간 형식, 사진 마커 |
| 관악산 종주 | 긴 산행: 장시간 기록, 백그라운드 동작 |

## 쓰는 법

1. 테스트폰에 APK를 설치해요.
2. **개발자 옵션 → 모의 위치 앱 선택**에서 Routee Mock을 골라요.
3. 경로를 고르고 **[시작]**을 누른 뒤 루티를 켜면 끝.

## 만든 방법

- Android **Mock Location API**로 위치를 주입하고, 포그라운드 서비스로 앱을 꺼도 계속 재생해요.
- 경로는 OpenStreetMap 도보 경로를 15m 간격으로 나누고, SRTM 고도 데이터를 붙여 만들었어요(`tools/build_routes.py`).
- 화면은 루티의 기존 Figma 디자인을 참고해 Jetpack Compose로 만들었어요.

<br/>

![Kotlin](https://img.shields.io/badge/Kotlin-7F52FF?style=flat-square&logo=kotlin&logoColor=white) ![Jetpack Compose](https://img.shields.io/badge/Jetpack_Compose-4285F4?style=flat-square&logo=jetpackcompose&logoColor=white) ![OpenStreetMap](https://img.shields.io/badge/OpenStreetMap-7EBC6F?style=flat-square&logo=openstreetmap&logoColor=white)

---

<sub>QA용 도구예요. 테스트 기기에서만 쓰세요. · 만든 사람 [@keepdev0919](https://github.com/keepdev0919)</sub>
