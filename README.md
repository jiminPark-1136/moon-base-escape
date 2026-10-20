# 게임 및 혼합현실 중간 프로젝트 (제목 미정)

달 연구기지에서 괴물을 피해 탈출포드까지 가는 2D 런앤건 플랫포머. LibGDX 1.13.1.
기획과 일정은 [PLAN.md](PLAN.md).

## 실행

```bash
./gradlew lwjgl3:run
```

배포용 jar: `./gradlew lwjgl3:jar` → `lwjgl3/build/libs/MidtermGame-1.0.0.jar`

## 조작

A/D 이동 · W 점프 · S 앉기(반동 50%) · 마우스 조준 · 왼쪽 클릭 사격 · R 재장전 · 1/2/Q 무기 전환 · ESC 일시정지 · Enter 재시작
테스트용: G 중력 전환 (1G → 0.58G → 0.16G)

## 코드 구조 (`core/src/main/java/io/jbnu/game`)

| 파일 | 역할 |
|---|---|
| `Main` | delta, 입력, GameState(PLAYING/PAUSED/CLEARED), 그리기 |
| `World` | 게임 로직: 이동, 앉기, 사격, 반동, 총알, 재시작 |
| `Body` | 중력 + `moveX`/`moveY` 충돌 (강의 12장 로직) |
| `Weapon` | 권총/샷건 데이터 |
| `CameraManager` | 추적 + 흔들림 (강의 10장 로직) |
| `Art` | 모든 이미지·애니메이션 로딩 (스프라이트 수정 시 여기만 보면 됨) |

반동 높이 확인: `core/src/test/java/io/jbnu/game/RecoilHeightCheck.java`

## 에셋과 라이선스 (모두 CC0)

| 경로 | 출처 |
|---|---|
| `assets/sprites/` (trooper, enemies, shots, items) | [Space soldier (sprite sheet 32x32)](https://opengameart.org/content/space-soldier-sprite-sheet-32x32) by drakzlin |
| `assets/tiles/foozle_lab_tileset.png` | [Sci-fi Lab - Tileset / Decor / Traps](https://foozlecc.itch.io/sci-fi-lab-tileset-decor-traps) by Foozle (commissioned from aimen23b) |
| `assets/backgrounds/ansimuz/` | [Sideview Sci-Fi Patreon Collection](https://opengameart.org/content/sideview-sci-fi-patreon-collection) by ansimuz (Luis Zuno) |
| `assets/backgrounds/lunar/`, `assets/tiles/lunar_tilesheet.png` | [Lunar Battle Pack](https://mattwalkden.itch.io/lunar-battle-pack) by Matt Walkden |
| `assets/flag.png` | 강의 예제 Project2 (교체 예정) |

`art-source/`에는 내려받은 원본 압축 파일을 그대로 보관한다 (PSD, 안 쓴 적·함정·장식 스프라이트 포함). 게임은 이 폴더를 읽지 않는다. `art-source/eris/`는 이전에 쓰다 교체한 [Sci-fi platform tiles](https://opengameart.org/content/sci-fi-platform-tiles) (Eris, CC0).

## 스테이지별 분위기

| 스테이지 | 배경 | 지형 |
|---|---|---|
| 1 연구동 | ansimuz 실내 기계 벽 | Foozle 연구소 타일 |
| 2 붕괴 구역 | ansimuz 격벽 + 붉은 비상등 색조 | Foozle 연구소 타일 |
| 3 달 표면 | Lunar 별(무작위 배치) + 4겹 패럴랙스 지평선 | Lunar 달 암석 |

지금은 G 키(중력 전환)를 누르면 분위기도 같이 바뀐다. 스테이지를 나누면 스테이지 번호로 바꾼다.

## 스크린샷

게임 중 **F12** → `screenshots/`에 PNG 저장 (레포트·발표용).
`java -XstartOnFirstThread -Dautoshot=<폴더> -jar lwjgl3/build/libs/MidtermGame-1.0.0.jar` → 세 분위기를 한 장씩 저장하고 종료 (macOS가 아니면 `-XstartOnFirstThread` 생략).

## 스프라이트 수정 방법

1. `assets/` 아래 PNG를 Aseprite, Piskel 등 픽셀아트 편집기로 열어 고치고 **같은 이름으로 저장**한다.
2. 스트립 규칙: 정사각형 프레임을 가로로 이어 붙인 한 줄 이미지. 프레임 크기 = 이미지 높이, 프레임 수 = 너비 ÷ 높이. 프레임을 늘리거나 줄여도 코드는 자동으로 인식한다.
3. 어느 프레임을 쓸지, 재생 속도, 타일을 어디서 잘라 쓸지, 배경 겹의 배율·높이·패럴랙스는 모두 [`Art.java`](core/src/main/java/io/jbnu/game/Art.java) 한 파일에 있다.
4. 트루퍼 스트립은 앞절반이 맨손, 뒷절반이 소총을 든 모습이다. 총은 마우스 방향으로 따로 그리므로 지금은 맨손 절반을 쓴다.
