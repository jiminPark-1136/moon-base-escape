# 게임 및 혼합현실 중간 프로젝트

달 연구기지 탈출 2D 런앤건 플랫포머 (LibGDX 1.13.1)

![](docs/images/history/v0.4-marine-crawler.png)

- 기획·일정: [PLAN.md](PLAN.md)
- 개발 일지: [docs/DEVLOG.md](docs/DEVLOG.md)

## 실행

```bash
./gradlew lwjgl3:run
```

- jar 빌드: `./gradlew lwjgl3:jar` → `lwjgl3/build/libs/MidtermGame-1.0.0.jar`

## 조작

| 키 | 동작 |
|---|---|
| A / D | 이동 |
| W | 점프 |
| S | 앉기 (반동 50%) |
| 마우스 / 좌클릭 | 조준 / 사격 |
| R | 재장전 |
| 1 / 2 / Q | 권총 / 샷건 / 전환 |
| ESC | 일시정지 (조작표 표시) |
| Enter | 지금 스테이지 재시작 / 클리어 화면에서 다음 스테이지 |
| F12 | 스크린샷 (`screenshots/`) |
| N | 지금 스테이지 바로 클리어 (테스트용) |

시작 화면에서 아무 키나 누르면 시작.

## 스테이지

| | 이름 | 중력 | 점프 / 점프+샷건 | 특징 |
|---|---|---|---|---|
| 1 | RESEARCH LAB | 1.0G | 119 / 188 | 튜토리얼, 샷건 벽, 창문 3개, 갈림길 |
| 2 | COLLAPSED SECTOR | 0.58G | 165 / 288 | 처음부터 깨진 감압 복도, 넓은 구덩이, 수직 통로 |
| 3 | LUNAR SURFACE | 0.16G | 257 / 712 | 전체 진공, 고원·메사를 샷건 반동으로 오름, 탈출포드 |

## 핵심 요소

- 마우스 조준: 팔·총이 어깨를 축으로 회전, 무기별 총 그림 (권총 짧음 / 샷건 김)
- 중력 연동 반동: 샷건 반동으로 점프, 중력 약할수록 높이 뜸 (h = v²/2g)
- 산소 = 체력: 피격 시 감소 + 우주복 누출(최대 3단계), 진공에서만 소모, 0이면 사망
- 산소 스테이션: 충전 + 누출 수리
- 감압 창문: 맞은 탄 수만큼 내구도 감소 (권총 10발 / 샷건 2발), 깨지면 흡입 + 진공
- 크롤러: 순찰 → 플레이어 발견 시 돌진, 구덩이 낙하 가능
- 피 파티클: 스테이지 중력 적용

## 코드 구조

`core/src/main/java/io/jbnu/game/`

| 파일 | 역할 |
|---|---|
| `Main` | 입력, 게임 상태, 렌더링 |
| `World` | 이동, 사격, 반동, 피격, 재시작 |
| `Body` | 중력·충돌 (강의 12장) |
| `CameraManager` | 추적·흔들림 (강의 10장) |
| `Weapon` | 권총·샷건 수치 |
| `Enemy` | 크롤러 AI |
| `Window` | 감압 창문 내구도·균열 |
| `Stage` | 스테이지별 맵·중력·배경 데이터 |
| `Art` | 이미지·애니메이션 로딩 |

## 검증

`core/src/test/java/io/jbnu/game/`

- `RecoilHeightCheck`: 중력별 반동 높이
- `EnemyCheck`: 크롤러 행동
- `OxygenWindowCheck`: 산소 소모량, 창문 내구도
- `StageCheck`: 스테이지별 점프 높이, 샷건이 필요한 장애물

```bash
./gradlew core:testClasses
```

```bash
java -ea -cp "core/build/classes/java/main:core/build/classes/java/test:$(find ~/.gradle/caches/modules-2 -name 'gdx-1.13.1.jar' | head -1)" io.jbnu.game.EnemyCheck
```

## 버전

| 태그 | 내용 |
|---|---|
| `v0.1-prototype` | 강의 로직 이식, 반동 프로토타입 (재현) |
| `v0.2-sprites` | 첫 스프라이트 적용 (재현) |
| `v0.3-first-commit` | 1스테이지 맵, 스테이지별 배경 |

스크린샷: `docs/images/history/`

## 에셋 (글꼴 외 전부 CC0)

| 경로 | 출처 |
|---|---|
| `assets/sprites/` | [Sideview Sci-Fi Patreon Collection](https://opengameart.org/content/sideview-sci-fi-patreon-collection) — ansimuz |
| `assets/backgrounds/ansimuz/` | 위와 동일 |
| `assets/tiles/foozle_lab_tileset.png` | [Sci-fi Lab Tileset](https://foozlecc.itch.io/sci-fi-lab-tileset-decor-traps) — Foozle |
| `assets/backgrounds/lunar/`, `assets/tiles/lunar_tilesheet.png` | [Lunar Battle Pack](https://mattwalkden.itch.io/lunar-battle-pack) — Matt Walkden |
| `assets/flag.png` | 강의 예제 Project2 (교체 예정) |
| `assets/fonts/Galmuri11.ttf` | [갈무리](https://github.com/quiple/galmuri) — 이민서(quiple), **SIL OFL 1.1** (라이선스: `assets/fonts/Galmuri-OFL.md`) |

- 원본: `art-source/` (게임에서 미사용)
- 이전 버전 에셋: [Space soldier](https://opengameart.org/content/space-soldier-sprite-sheet-32x32) — drakzlin, [Sci-fi platform tiles](https://opengameart.org/content/sci-fi-platform-tiles) — Eris

## 스프라이트 수정

- `assets/` PNG 수정 후 같은 이름으로 저장
- 프레임 너비·재생 속도·타일 좌표·배경 레이어: `Art.java`
- 스트립 = 같은 너비 프레임 가로 나열, 프레임 수 자동 계산
