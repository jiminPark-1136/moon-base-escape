package io.jbnu.game;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.Animation;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.ObjectIntMap;
import com.badlogic.gdx.utils.ObjectSet;

/**
 * 모든 그림을 한 곳에서 불러온다. 스프라이트를 바꾸거나 고칠 때는 이 파일만 보면 된다.
 *
 * 스프라이트 수정 방법:
 * - 이미지 편집: assets/ 아래 PNG를 Aseprite·Piskel 등으로 열어 고친 뒤 같은 이름으로 저장
 * - 스트립 규칙: 같은 너비의 프레임을 가로로 이어 붙인 한 줄 이미지. 아래 표에 프레임 너비를 적으면
 *   프레임 수(= 이미지 너비 / 프레임 너비)는 자동으로 센다 → 프레임을 더하거나 빼도 코드 수정이 필요 없다.
 * - 캐릭터는 오른쪽을 보는 그림이다. 왼쪽은 뒤집어서 그린다.
 * - 타일·배경 좌표는 이미지 왼쪽 위 기준 픽셀이다.
 */
public class Art {
    /** 픽셀아트를 화면에 그릴 배율. 캐릭터와 지형이 같은 픽셀 크기로 보이도록 둘 다 이 값을 쓴다. */
    public static final int SCALE = 2;

    /** 지형 블록 그림: body를 반복해 채우고, 맨 위에 top 띠를 덮는다. */
    public static class Terrain {
        public final TextureRegion top, body;

        Terrain(TextureRegion top, TextureRegion body) {
            this.top = top;
            this.body = body;
        }
    }

    /** 배경 한 겹. 가로로 반복되며, parallax가 작을수록 멀리 있는 것처럼 느리게 움직인다. */
    public static class Layer {
        public final Texture texture;
        public final int scale;
        public final float parallax, y;
        public final Color tint;

        Layer(Texture texture, int scale, float parallax, float y, Color tint) {
            this.texture = texture;
            this.scale = scale;
            this.parallax = parallax;
            this.y = y;
            this.tint = tint;
        }
    }

    /** 스테이지별 분위기: 배경 겹(뒤에서 앞 순서), 지형, 빈 곳을 채울 바탕색. */
    public static class Look {
        public final Layer[] layers;
        public final Terrain terrain;
        public final Color clearColor;
        /** 별 타일들 (null이면 별 없음). Main이 무작위 위치에 흩뿌린다. */
        public final TextureRegion[] stars;

        Look(Color clearColor, Terrain terrain, TextureRegion[] stars, Layer... layers) {
            this.clearColor = clearColor;
            this.terrain = terrain;
            this.stars = stars;
            this.layers = layers;
        }
    }

    private static final String MARINE = "sprites/marine/";
    private static final String ALIEN = "sprites/alien/";

    /** 플레이어 (ansimuz 스페이스 마린: 소총을 든 채로 움직인다) */
    public final Animation<TextureRegion> idle, run, jump, shoot, die;
    /** 크롤러 (ansimuz 걷는 외계인) */
    public final Animation<TextureRegion> alienWalk, alienIdle;
    /** 애니메이션별 몸 중심의 x (프레임 왼쪽에서 픽셀). 프레임 너비가 달라도 몸이 같은 자리에 그려지게 한다. */
    public final ObjectIntMap<Animation<TextureRegion>> anchorX = new ObjectIntMap<>();
    /** 원본이 왼쪽을 보고 그려진 애니메이션 (나머지는 오른쪽). 뒤집는 방향을 정할 때 쓴다. */
    public final ObjectSet<Animation<TextureRegion>> facesLeft = new ObjectSet<>();
    /** 0: 연구동, 1: 붕괴 구역, 2: 달 표면 */
    public final Look[] looks;
    public final Texture flag;

    private final Array<Texture> textures = new Array<>();

    public Art() {
        //                    파일                               프레임 너비, 프레임 시간(초), 반복
        idle      = strip(MARINE + "space-marine-idle.png",   48, 0.15f, Animation.PlayMode.LOOP);
        run       = strip(MARINE + "space-marine-run.png",    48, 0.07f, Animation.PlayMode.LOOP);
        jump      = strip(MARINE + "space-marine-jump.png",   36, 0.08f, Animation.PlayMode.NORMAL);
        shoot     = strip(MARINE + "space-marine-shoot.png",  64, 0.06f, Animation.PlayMode.NORMAL);
        die       = strip(MARINE + "space-marine-die.png",    80, 0.15f, Animation.PlayMode.NORMAL);
        alienWalk = strip(ALIEN + "alien-enemy-walk.png",     57, 0.10f, Animation.PlayMode.LOOP);
        alienIdle = strip(ALIEN + "alien-enemy-idle.png",     48, 0.15f, Animation.PlayMode.LOOP);
        anchorX.put(idle, 16);
        anchorX.put(run, 16);
        anchorX.put(jump, 14);
        anchorX.put(shoot, 16);
        anchorX.put(die, 58);
        anchorX.put(alienWalk, 28);
        anchorX.put(alienIdle, 24);
        facesLeft.add(alienWalk);
        facesLeft.add(alienIdle);

        // 지형 (Foozle 연구소 타일: 위 8px = 경고 줄무늬, 그 아래 16px = 벽돌)
        Texture lab = load("tiles/foozle_lab_tileset.png");
        Terrain labTerrain = new Terrain(new TextureRegion(lab, 192, 0, 32, 8), new TextureRegion(lab, 192, 8, 32, 16));
        // 지형 (Lunar 팩 달 암석)
        Texture moon = load("tiles/lunar_tilesheet.png");
        // 테두리 있는 바위 블록(48x48)의 가운데 윗변과 가운데 칸
        Terrain moonTerrain = new Terrain(new TextureRegion(moon, 16, 0, 16, 16), new TextureRegion(moon, 16, 16, 16, 16));

        // 배경 (ansimuz 실내 / Lunar 달 표면). Layer(텍스처, 배율, 패럴랙스, 바닥 y, 색조)
        Layer interior = new Layer(load("backgrounds/ansimuz/interior.png"), 6, 0.3f, 24, Color.WHITE);
        Layer bulkhead = new Layer(load("backgrounds/ansimuz/bulkhead.png"), 3, 0.3f, 24,
            new Color(1f, 0.7f, 0.65f, 1f)); // 비상등 느낌의 붉은 색조
        // 별 타일 시트(16px 별 7개)를 한 칸씩 나눈다
        Texture starSheet = load("backgrounds/lunar/Star_Tiles.png");
        TextureRegion[] stars = new TextureRegion[starSheet.getWidth() / 16];
        for (int i = 0; i < stars.length; i++) stars[i] = new TextureRegion(starSheet, i * 16, 0, 16, 16);
        // 별 타일은 바탕이 불투명하다 → 하늘색을 타일 바탕색(왼쪽 위 픽셀)과 똑같이 맞춰 네모가 안 보이게 한다
        Pixmap starPixels = new Pixmap(Gdx.files.internal("backgrounds/lunar/Star_Tiles.png"));
        Color space = new Color(starPixels.getPixel(0, 0));
        starPixels.dispose();

        looks = new Look[] {
            new Look(new Color(0.09f, 0.11f, 0.15f, 1f), labTerrain, null, interior),
            new Look(new Color(0.08f, 0.06f, 0.06f, 1f), labTerrain, null, bulkhead),
            // 달 표면: 먼 레이어일수록 높게, 느리게. 가까운 레이어가 먼 레이어의 아랫부분을 덮는다.
            new Look(space, moonTerrain, stars,
                new Layer(load("backgrounds/lunar/Surface_Layer1.png"), 3, 0.10f, 352, Color.WHITE), // 먼 산
                new Layer(load("backgrounds/lunar/Surface_Layer2.png"), 3, 0.20f, 304, Color.WHITE), // 능선
                new Layer(load("backgrounds/lunar/Surface_Layer3.png"), 3, 0.35f, 144, Color.WHITE), // 크레이터
                new Layer(load("backgrounds/lunar/Surface_Layer4.png"), 3, 0.50f, 0, Color.WHITE)),  // 가까운 지면
        };

        flag = whiteSilhouette("flag.png");
    }

    private Texture load(String path) {
        Texture t = new Texture(path);
        textures.add(t);
        return t;
    }

    private Animation<TextureRegion> strip(String path, int frameWidth, float frameTime, Animation.PlayMode mode) {
        Texture t = load(path);
        if (t.getWidth() % frameWidth != 0) {
            throw new IllegalArgumentException(path + ": width " + t.getWidth() + " is not a multiple of frame width " + frameWidth);
        }
        Array<TextureRegion> frames = new Array<>();
        for (int x = 0; x < t.getWidth(); x += frameWidth) frames.add(new TextureRegion(t, x, 0, frameWidth, t.getHeight()));
        return new Animation<>(frameTime, frames, mode);
    }

    /** 검은 아이콘을 흰색 실루엣으로 바꿔 불러온다. 흰색이어야 batch.setColor로 원하는 색을 입힐 수 있다. */
    private Texture whiteSilhouette(String path) {
        Pixmap pm = new Pixmap(Gdx.files.internal(path));
        pm.setBlending(Pixmap.Blending.None); // 섞지 않고 픽셀을 그대로 덮어쓴다
        for (int y = 0; y < pm.getHeight(); y++) {
            for (int x = 0; x < pm.getWidth(); x++) {
                int alpha = pm.getPixel(x, y) & 0xff; // RGBA8888의 마지막 바이트가 알파
                pm.drawPixel(x, y, 0xffffff00 | alpha);
            }
        }
        Texture t = new Texture(pm);
        pm.dispose();
        textures.add(t);
        return t;
    }

    public void dispose() {
        for (Texture t : textures) t.dispose();
    }
}
