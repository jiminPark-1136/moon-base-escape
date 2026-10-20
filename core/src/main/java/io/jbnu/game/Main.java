package io.jbnu.game;

import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Cursor;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.PixmapIO;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.Animation;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.GlyphLayout;
import com.badlogic.gdx.graphics.g2d.freetype.FreeTypeFontGenerator;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.ScreenUtils;
import com.badlogic.gdx.utils.viewport.FitViewport;

import java.util.zip.Deflater;

/** delta, 입력, 게임 상태, 그리기를 맡는다. 게임 규칙은 World, 카메라 연출은 CameraManager. */
public class Main extends ApplicationAdapter {
    private static final float VIEW_W = 1280, VIEW_H = 720;

    /** 강의 10장의 GameState를 일시정지까지 확장. */
    /** 강의 10장 GameState를 확장: 일시정지, 스테이지 클리어(다음 스테이지 대기), 전체 클리어 */
    private enum GameState { TITLE, PLAYING, PAUSED, STAGE_CLEAR, ALL_CLEAR }

    /** 스테이지 시작 배너를 보여 줄 남은 시간 */
    private float bannerTimer;
    /** 1스테이지부터 잰 플레이 시간 (일시정지·클리어 화면 제외) */
    private float playTime;

    private static final Color DROPPER_TINT = new Color(0.85f, 0.65f, 1f, 1f);
    private static final Color GLASS_EDGE = new Color(0.8f, 0.95f, 1f, 0.9f);

    private SpriteBatch batch;
    private BitmapFont font;
    private FreeTypeFontGenerator fontGenerator;
    private Art art;
    private Animation<TextureRegion> playerAnim;
    private float animTime;
    /** 발사 후 사격 모션을 보여 줄 남은 시간 */
    private float shootPoseTimer;
    /** 적 애니메이션용 경과 시간 */
    private float elapsed;
    private Texture pixelTexture;
    private TextureRegion pixel;
    private Cursor hiddenCursor;
    private OrthographicCamera camera;
    private FitViewport viewport;
    private final Matrix4 hudMatrix = new Matrix4().setToOrtho2D(0, 0, VIEW_W, VIEW_H);
    private final Vector3 mouse = new Vector3();

    private World world;
    private CameraManager camManager;
    private GameState currentState = GameState.TITLE; // 시작 화면: 조작법을 보여 주고 아무 키나 누르면 시작

    @Override
    public void create() {
        batch = new SpriteBatch();
        // 한글 글꼴: 갈무리11 (12px 기준 픽셀 글꼴)을 정확히 2배인 24px로 만든다. 글자는 처음 쓰일 때 만들어진다(incremental).
        fontGenerator = new FreeTypeFontGenerator(Gdx.files.internal("fonts/Galmuri11.ttf"));
        FreeTypeFontGenerator.FreeTypeFontParameter fp = new FreeTypeFontGenerator.FreeTypeFontParameter();
        fp.size = 24;
        fp.incremental = true;
        font = fontGenerator.generateFont(fp);
        art = new Art();
        playerAnim = art.idle;

        Pixmap pm = new Pixmap(1, 1, Pixmap.Format.RGBA8888);
        pm.setColor(Color.WHITE);
        pm.fill();
        pixelTexture = new Texture(pm);
        pm.dispose();
        pixel = new TextureRegion(pixelTexture);

        // 시스템 커서를 숨기고 크로스헤어를 직접 그린다
        Pixmap empty = new Pixmap(16, 16, Pixmap.Format.RGBA8888);
        hiddenCursor = Gdx.graphics.newCursor(empty, 0, 0);
        empty.dispose();
        Gdx.graphics.setCursor(hiddenCursor);

        camera = new OrthographicCamera();
        viewport = new FitViewport(VIEW_W, VIEW_H, camera);
        viewport.update(Gdx.graphics.getWidth(), Gdx.graphics.getHeight(), false);

        world = new World();
        camManager = new CameraManager();
        snapCamera();
    }

    @Override
    public void render() {
        // 큰 프레임 시간으로 한 번에 너무 멀리 이동하는 것을 제한한다 (강의 그대로)
        float dt = Math.min(Gdx.graphics.getDeltaTime(), 1f / 30f);
        input();
        logic(dt);
        draw();
        screenshots();
    }

    /**
     * -Dautoshot=폴더 로 실행하면 아래 장면을 30프레임 간격으로 차례로 만들어 찍고 종료한다 (그림 확인·레포트용).
     * 각 줄 = {파일 이름, 찍은 뒤 다음 장면을 준비하는 동작}.
     */
    private final String autoShotDir = System.getProperty("autoshot");
    private int frameCount;
    /** 자동 스크린샷 때 마우스 대신 쓸 조준 방향 (어깨 기준 오프셋). null이면 마우스. */
    private com.badlogic.gdx.math.Vector2 autoAim;
    /** 자동 스크린샷의 맵 전체 장면: 카메라 추적을 멈춘다 */
    private boolean overview;
    private final Object[][] autoShots = {
        {"title", (Runnable) () -> startStage(0)},
        {"look0", (Runnable) () -> autoAim = new com.badlogic.gdx.math.Vector2(200, 0)},
        {"arm-pistol-right", (Runnable) () -> {
            world.weapon = Weapon.SHOTGUN;
            autoAim.set(120, 160);
        }},
        {"arm-shotgun-up", (Runnable) () -> autoAim.set(-150, -110)},
        {"arm-shotgun-downleft", (Runnable) () -> {
            autoAim = null;
            world.weapon = Weapon.PISTOL;
            startStage(1);
        }},
        {"stage2-start", (Runnable) () -> startStage(2)},
        {"stage3-start", (Runnable) () -> {     // 1스테이지 창문 방이 보이는 샷건 벽 위로 이동
            startStage(0);
            bannerTimer = 0;
            world.player.bounds.setPosition(2420, 224);
            snapCamera();
        }},
        {"window-intact", (Runnable) () -> world.windows.first().hp = 4},
        {"window-cracked", (Runnable) () -> world.windows.first().hp = 0},
        {"window-broken", (Runnable) () -> {
            world.oxygen = 12;
            world.leak = 2;
        }},
        {"low-oxygen", (Runnable) () -> overviewOf(0)},
        {"stage1-overview", (Runnable) () -> overviewOf(1)},
        {"stage2-overview", (Runnable) () -> overviewOf(2)},
        {"stage3-overview", (Runnable) () -> {   // 2스테이지 감압 복도 앞: 천장의 드로퍼
            overview = false;
            camera.zoom = 1;
            startStage(1);
            bannerTimer = 0;
            world.player.bounds.setPosition(900, 64);
            snapCamera();
        }},
        {"stage2-droppers", (Runnable) () -> {   // 3스테이지 크레이터 건너편: 스피터의 산성탄
            startStage(2);
            bannerTimer = 0;
            world.player.bounds.setPosition(1500, 64);
            snapCamera();
        }},
        {"stage3-spitters", (Runnable) () -> { }},   // 산성탄이 날아가는 순간을 잡으려고 0.5초씩 더 찍는다
        {"stage3-spitters-2", (Runnable) () -> { }},
        {"stage3-spitters-2b", (Runnable) () -> { }},
        {"stage3-spitters-2c", (Runnable) () -> { }},
        {"stage3-spitters-3", (Runnable) () -> {
            startStage(1);
            currentState = GameState.STAGE_CLEAR;
        }},
        {"stage-clear", (Runnable) () -> {
            startStage(2);
            playTime = 754;
            world.deaths = 3;
            currentState = GameState.ALL_CLEAR;
        }},
        {"all-clear", null},
    };

    /** F12: 현재 화면을 screenshots/ 에 저장 (레포트·발표 자료용). */
    private void screenshots() {
        if (Gdx.input.isKeyJustPressed(Input.Keys.F12)) {
            // gradlew lwjgl3:run은 assets/ 안에서 실행되므로 프로젝트 폴더의 screenshots/로 맞춘다
            java.io.File base = new java.io.File("").getAbsoluteFile();
            if (base.getName().equals("assets")) base = base.getParentFile();
            saveScreenshot(new java.io.File(base, "screenshots/shot-" + System.currentTimeMillis() + ".png").getPath());
        }
        if (autoShotDir == null) return;
        frameCount++;
        if (frameCount % 30 != 0) return;
        Object[] shot = autoShots[frameCount / 30 - 1];
        saveScreenshot(autoShotDir + "/" + shot[0] + ".png");
        if (shot[1] == null) Gdx.app.exit();
        else ((Runnable) shot[1]).run();
    }

    /** 맵 전체가 보이게 카메라를 멀리 뺀다 (자동 스크린샷용) */
    private void overviewOf(int stage) {
        startStage(stage);
        bannerTimer = 0;
        world.oxygen = World.MAX_OXYGEN;
        overview = true;
        camera.zoom = world.stage.width / VIEW_W;
        camera.position.set(world.stage.width / 2, world.stage.height / 2, 0);
        camera.update();
    }

    private void saveScreenshot(String path) {
        Pixmap pm = Pixmap.createFromFrameBuffer(0, 0, Gdx.graphics.getBackBufferWidth(), Gdx.graphics.getBackBufferHeight());
        PixmapIO.writePNG(Gdx.files.absolute(new java.io.File(path).getAbsolutePath()), pm, Deflater.DEFAULT_COMPRESSION, true); // true: 위아래 뒤집기
        pm.dispose();
    }

    private void input() {
        if (currentState == GameState.TITLE) {
            if (Gdx.input.isKeyJustPressed(Input.Keys.ANY_KEY) || Gdx.input.justTouched()) {
                startStage(0);
                skipFrame = true; // 시작에 쓴 키(W·클릭)가 바로 점프·사격이 되지 않게 한 프레임 쉰다
            }
            return;
        }
        if (Gdx.input.isKeyJustPressed(Input.Keys.ESCAPE)) {
            if (currentState == GameState.PLAYING) currentState = GameState.PAUSED;
            else if (currentState == GameState.PAUSED) currentState = GameState.PLAYING;
        }
        if (Gdx.input.isKeyJustPressed(Input.Keys.ENTER)) {
            if (currentState == GameState.STAGE_CLEAR) {
                startStage(world.stageIndex + 1);
            } else if (currentState == GameState.ALL_CLEAR) { // 전체 클리어 후 처음부터 다시
                world.deaths = 0;
                playTime = 0;
                startStage(0);
            } else { // 플레이 중: 지금 스테이지를 처음부터
                world.reset();
                currentState = GameState.PLAYING;
                snapCamera();
            }
        }
    }

    private void startStage(int index) {
        world.loadStage(index);
        currentState = GameState.PLAYING;
        bannerTimer = 2.5f;
        snapCamera();
    }

    private void snapCamera() {
        camManager.snapTo(camera, playerCenterX(), playerCenterY(), world.stage.width, world.stage.height);
    }

    private boolean skipFrame;

    private void logic(float dt) {
        if (currentState == GameState.PAUSED) return; // 일시정지: 월드와 카메라 모두 멈춘다
        if (skipFrame) {
            skipFrame = false;
            return;
        }

        bannerTimer -= dt;
        if (currentState == GameState.PLAYING) {
            playTime += dt;
            viewport.unproject(mouse.set(Gdx.input.getX(), Gdx.input.getY(), 0)); // 화면 좌표 → 월드 좌표
            if (autoAim != null) mouse.set(world.shoulderX() + autoAim.x, world.shoulderY() + autoAim.y, 0);
            world.update(dt, mouse.x, mouse.y);
            if (world.firedThisStep == Weapon.SHOTGUN) camManager.startShake(0.15f, 8f);
            else if (world.firedThisStep == Weapon.PISTOL) camManager.startShake(0.05f, 2f);
            if (world.firedThisStep != null) shootPoseTimer = 0.15f;
            if (world.playerHitThisStep) camManager.startShake(0.25f, 12f);
            if (world.windowBrokeThisStep) camManager.startShake(0.8f, 18f);
            endingCheck();
        }
        if (!overview) camManager.update(camera, playerCenterX(), playerCenterY(), world.stage.width, world.stage.height, dt);
        updatePlayerAnim(dt);
        elapsed += dt;
    }

    /** 상태에 맞는 애니메이션을 고르고, 바뀌면 처음 프레임부터 재생한다. */
    private void updatePlayerAnim(float dt) {
        Body p = world.player;
        shootPoseTimer -= dt;
        Animation<TextureRegion> next;
        if (world.playerDead()) next = art.die;
        else if (!p.grounded) next = art.jump;
        else if (Math.abs(p.vx) > 10 && !world.crouching) next = art.run;
        else next = art.idle; // 앉기 전용 그림이 없어 대기 모션을 눌러서 그린다 (drawActor)
        if (next != playerAnim) {
            playerAnim = next;
            animTime = 0;
        }
        animTime += dt;
    }

    /** 강의 10장 endingCheck: 출구에 닿은 순간 한 번만 상태를 바꾸고 연출을 시작한다. 마지막 스테이지면 전체 클리어. */
    private void endingCheck() {
        if (world.isGameClear() && currentState == GameState.PLAYING) {
            currentState = world.isLastStage() ? GameState.ALL_CLEAR : GameState.STAGE_CLEAR;
            camManager.startShake(1.0f, 20f);
        }
    }

    private float playerCenterX() {
        Rectangle b = world.player.bounds;
        return b.x + b.width / 2;
    }

    private float playerCenterY() {
        Rectangle b = world.player.bounds;
        return b.y + b.height / 2;
    }

    private void draw() {
        Art.Look look = art.looks[world.stage.look]; // 스테이지별 배경·지형
        ScreenUtils.clear(look.clearColor);
        viewport.apply();

        batch.setProjectionMatrix(hudMatrix);
        batch.begin();
        if (look.stars != null) drawStars(look.stars);
        for (Art.Layer layer : look.layers) drawLayer(layer);
        batch.setColor(Color.WHITE);
        batch.end();

        batch.setProjectionMatrix(camera.combined);
        batch.begin();
        for (Window w : world.windows) drawWindow(w);
        for (Rectangle block : world.blocks) drawBlock(look.terrain, block);
        for (Rectangle st : world.stations) drawStation(st);
        Rectangle f = world.flag;
        batch.setColor(Color.LIME); // 원본이 검은색이라 어두운 배경에서 보이도록 밝게 칠한다
        batch.draw(art.flag, f.x, f.y, f.width, f.height);
        font.setColor(Color.LIME);
        font.draw(batch, world.isLastStage() ? "탈출포드" : "출구", f.x - 8, f.y + f.height + 28);
        font.setColor(Color.WHITE);
        batch.setColor(Color.WHITE);

        for (Enemy e : world.enemies) {
            Animation<TextureRegion> a = e.type == Enemy.Type.SPITTER ? art.alienFly : e.charging ? art.alienWalk : art.alienIdle;
            // 돌진하면 다리를 빨리 움직이고, 맞은 순간에는 빨갛게 번쩍인다. 드로퍼는 보라빛, 매달려 있으면 거꾸로
            float t = e.charging ? elapsed * 1.8f : elapsed;
            batch.setColor(e.hurtTimer > 0 ? Color.RED : e.type == Enemy.Type.DROPPER ? DROPPER_TINT : Color.WHITE);
            drawActor(a, t, e.body.bounds, e.facingRight, e.hanging ? -1f : 1f);
        }
        batch.setColor(Color.WHITE);

        // 플레이어: 맞은 뒤 무적 시간에는 깜빡인다
        boolean blink = world.invulnerable > 0 && ((int) (world.invulnerable * 10)) % 2 == 0;
        batch.setColor(1, 1, 1, blink ? 0.3f : 1f);
        drawActor(playerAnim, animTime, world.player.bounds, world.facingRight, world.crouching ? 0.75f : 1f);
        if (!world.playerDead()) drawArmAndGun(blink ? 0.3f : 1f);
        batch.setColor(Color.WHITE);

        for (World.Acid ac : world.acids) { // 산성탄: 연두색 덩어리 + 밝은 속
            Rectangle r = ac.bounds;
            batch.setColor(World.ACID);
            batch.draw(pixel, r.x, r.y, r.width, r.height);
            batch.setColor(0.9f, 1f, 0.7f, 1f);
            batch.draw(pixel, r.x + 3, r.y + 3, r.width - 6, r.height - 6);
        }

        for (World.Particle pt : world.particles) {
            batch.setColor(pt.color.r, pt.color.g, pt.color.b, Math.min(1f, pt.life));
            batch.draw(pixel, pt.x - 2, pt.y - 2, 4, 4);
        }

        batch.setColor(Color.YELLOW);
        for (World.Bullet b : world.bullets) {
            batch.draw(pixel, b.bounds.x, b.bounds.y, b.bounds.width, b.bounds.height);
        }

        if (currentState == GameState.PLAYING) drawCrosshair(world.aimX, world.aimY);
        batch.setColor(Color.WHITE);
        batch.end();

        drawHud();
    }

    /** 별 위치: 처음 한 번 고정된 시드로 만든다 (매 프레임 바뀌면 깜빡이므로). {x, y, 타일 번호} */
    private static final int STAR_COUNT = 90;
    private static final float STAR_FIELD_W = VIEW_W * 2;
    private final float[][] starPos = makeStars();

    private static float[][] makeStars() {
        java.util.Random r = new java.util.Random(42);
        float[][] p = new float[STAR_COUNT][3];
        for (float[] s : p) {
            s[0] = r.nextFloat() * STAR_FIELD_W;
            s[1] = 330 + r.nextFloat() * (VIEW_H - 330); // 지평선 위쪽에만
            s[2] = r.nextInt(1000);
        }
        return p;
    }

    private void drawStars(TextureRegion[] stars) {
        float shift = (camera.position.x * 0.03f) % STAR_FIELD_W;
        batch.setColor(Color.WHITE);
        for (float[] s : starPos) {
            float x = s[0] - shift;
            if (x < -32) x += STAR_FIELD_W;
            if (x > VIEW_W) continue;
            batch.draw(stars[(int) s[2] % stars.length], x, s[1], 32, 32);
        }
    }

    /** 배경 한 겹을 화면 좌표로 그린다. 카메라 이동의 parallax 배만큼 밀리며 가로로 반복된다. */
    private void drawLayer(Art.Layer layer) {
        float w = layer.texture.getWidth() * layer.scale;
        float h = layer.texture.getHeight() * layer.scale;
        float shift = (camera.position.x * layer.parallax) % w;
        batch.setColor(layer.tint);
        for (float x = -shift; x < VIEW_W; x += w) batch.draw(layer.texture, x, layer.y, w, h);
    }

    /**
     * 캐릭터 프레임을 SCALE배로, 몸 중심(anchorX)을 히트박스 가운데에 맞춰 발밑부터 그린다.
     * squash < 1이면 세로로 눌러 그린다 (앉기 전용 그림이 없는 동안의 대용).
     */
    private void drawActor(Animation<TextureRegion> a, float time, Rectangle b, boolean facingRight, float squash) {
        TextureRegion frame = a.getKeyFrame(time);
        int scale = art.scaleOf.get(a, Art.SCALE);
        float w = frame.getRegionWidth() * scale, h = frame.getRegionHeight() * scale * squash;
        float anchor = art.anchorX.get(a, frame.getRegionWidth() / 2) * scale;
        float cx = b.x + b.width / 2;
        float y = squash < 0 ? b.y + b.height : b.y; // 거꾸로(squash < 0)면 발이 위쪽(천장)에 붙는다
        boolean asDrawn = facingRight != art.facesLeft.contains(a); // 원본 방향 그대로 그려도 되는지
        if (asDrawn) batch.draw(frame, cx - anchor, y, w, h);
        else batch.draw(frame, cx + anchor, y, -w, h); // 좌우 반전
    }

    /**
     * 팔+총 조각을 어깨를 축으로 조준 각도만큼 돌려 그린다. 왼쪽을 볼 때는 위아래를 뒤집어
     * 총이 거꾸로 서지 않게 한다. 쏜 직후에는 총구에 화염을 그린다.
     */
    private void drawArmAndGun(float alpha) {
        TextureRegion arm = world.weapon == Weapon.SHOTGUN ? art.shotgunArm : art.pistolArm;
        float w = arm.getRegionWidth() * Art.SCALE, h = arm.getRegionHeight() * Art.SCALE;
        float originX = Art.ARM_PIVOT_X * Art.SCALE, originY = Art.ARM_PIVOT_Y * Art.SCALE;
        // 어깨: 지금 몸 프레임에 기록된 위치 (달리기 프레임은 몸이 조금 낮다)
        TextureRegion body = playerAnim.getKeyFrame(animTime);
        com.badlogic.gdx.math.Vector2 s = art.shoulder.get(body);
        float sx = world.shoulderX(), sy = world.shoulderY();
        if (s != null) {
            Rectangle b = world.player.bounds;
            float off = (s.x - art.anchorX.get(playerAnim, 0)) * Art.SCALE;
            sx = b.x + b.width / 2 + (world.facingRight ? off : -off);
            sy = b.y + s.y * Art.SCALE * (world.crouching ? 0.75f : 1f);
        }
        batch.setColor(1, 1, 1, alpha);
        batch.draw(arm, sx - originX, sy - originY, originX, originY, w, h, 1, world.facingRight ? 1 : -1, world.aimAngle());

        if (shootPoseTimer > 0) {
            float size = world.weapon == Weapon.SHOTGUN ? 18 : 10;
            float mx = world.muzzleX(), my = world.muzzleY();
            batch.setColor(1f, 0.85f, 0.3f, 1f);
            batch.draw(pixel, mx - size / 2, my - size / 2, size / 2, size / 2, size, size, 1, 1, world.aimAngle() + 45);
            batch.setColor(1f, 1f, 0.8f, 1f);
            batch.draw(pixel, mx - size / 4, my - size / 4, size / 2, size / 2);
        }
        batch.setColor(Color.WHITE);
    }

    /**
     * 블록을 타일로 채운다: body 타일을 반복하고 맨 위에 top 띠를 덮는다.
     * 타일은 Art.SCALE배로 그리고, 블록 끝에 걸치는 타일은 늘리지 않고 잘라서 그린다.
     */
    private void drawBlock(Art.Terrain t, Rectangle b) {
        tile(t.body, b.x, b.y, b.width, b.height);
        float topH = Math.min(t.top.getRegionHeight() * Art.SCALE, b.height);
        tile(t.top, b.x, b.y + b.height - topH, b.width, topH);
    }

    private void tile(TextureRegion r, float x0, float y0, float width, float height) {
        float tw = r.getRegionWidth() * Art.SCALE, th = r.getRegionHeight() * Art.SCALE;
        for (float y = y0; y < y0 + height; y += th) {
            float h = Math.min(th, y0 + height - y);
            for (float x = x0; x < x0 + width; x += tw) {
                float w = Math.min(tw, x0 + width - x);
                int srcW = (int) (w / Art.SCALE), srcH = (int) (h / Art.SCALE);
                // 잘린 타일은 아래쪽(바닥에 닿는 쪽)을 남기도록 원본의 아래에서부터 자른다
                batch.draw(r.getTexture(), x, y, w, h,
                    r.getRegionX(), r.getRegionY() + r.getRegionHeight() - srcH, srcW, srcH, false, false);
            }
        }
    }

    private void drawCrosshair(float x, float y) {
        batch.setColor(Color.RED);
        float gap = 5, len = 8, t = 2;
        batch.draw(pixel, x - gap - len, y - t / 2, len, t);
        batch.draw(pixel, x + gap, y - t / 2, len, t);
        batch.draw(pixel, x - t / 2, y - gap - len, t, len);
        batch.draw(pixel, x - t / 2, y + gap, t, len);
        batch.draw(pixel, x - 1, y - 1, 2, 2);
    }

    private void drawHud() {
        batch.setProjectionMatrix(hudMatrix);
        batch.begin();
        drawLowOxygenVignette();
        Weapon w = world.weapon;
        String reload = world.reloadTimer > 0 ? "  [재장전 중]" : "";
        font.setColor(Color.WHITE);
        font.draw(batch, String.format("%d스테이지  %s   중력 %.2fG", world.stageIndex + 1, world.stage.name, world.stage.gravityScale), 16, VIEW_H - 16);
        // 상태 표시: 왼쪽 아래 (아래에서부터 산소 → 무기 → 상태). 바탕은 글자 길이만큼만 깐다 → 캐릭터를 덜 가린다
        String weaponText = String.format("무기: %s  %d/%d%s", w.label, world.ammo[w.ordinal()], w.magazine, reload);
        String status = world.atStation ? "산소 충전 / 우주복 수리 중"
            : world.inVacuum ? "진공" : world.crouching ? "앉기 (반동 절반)" : "";
        String leakText = world.leak > 0 ? "누출 x" + world.leak : "";
        float panelW = Math.max(widthOf(weaponText), Math.max(widthOf(status), 270 + widthOf(leakText))) + 20;
        batch.setColor(0, 0, 0, 0.45f);
        batch.draw(pixel, 6, 6, panelW, status.isEmpty() ? 80 : 112);
        batch.setColor(Color.WHITE);
        drawOxygenBar(16, 20);
        font.setColor(Color.WHITE);
        font.draw(batch, weaponText, 16, 72);
        font.setColor(world.atStation ? Color.CYAN : world.inVacuum ? Color.ORANGE : Color.LIGHT_GRAY);
        font.draw(batch, status, 16, 104);
        if (world.leak > 0) {
            font.setColor(Color.RED);
            font.draw(batch, leakText, 280, 38);
        }

        font.setColor(Color.WHITE);
        if (currentState == GameState.PAUSED) {
            drawPanel();
            drawCentered("일시정지", VIEW_H - 120, 2.5f, Color.WHITE);
            drawControls(VIEW_H - 260);
            drawCentered("ESC: 계속하기", 120, 1f, Color.LIGHT_GRAY);
        }
        if (currentState == GameState.TITLE) drawTitle();
        if (currentState == GameState.PLAYING && bannerTimer > 0) drawStageBanner();
        if (currentState == GameState.STAGE_CLEAR) {
            drawPanel();
            drawCentered(String.format("%d스테이지 클리어", world.stageIndex + 1), VIEW_H / 2 + 80, 3f, Color.LIME);
            Stage next = Stage.get(world.stageIndex + 1);
            drawCentered(String.format("다음: %s  (중력 %.2fG)", next.name, next.gravityScale), VIEW_H / 2 - 30, 1.5f, Color.WHITE);
            drawCentered("Enter를 누르세요", VIEW_H / 2 - 90, 1f, Color.LIGHT_GRAY);
        }
        if (currentState == GameState.ALL_CLEAR) {
            drawPanel();
            drawCentered("탈출 성공", VIEW_H / 2 + 140, 3.5f, Color.LIME);
            drawCentered("탈출포드에 도착했다. 지구로 돌아간다.", VIEW_H / 2 + 20, 1.5f, Color.WHITE);
            drawCentered(String.format("시간 %d:%02d    사망 %d회", (int) playTime / 60, (int) playTime % 60, world.deaths),
                VIEW_H / 2 - 40, 1.5f, Color.CYAN);
            drawCentered("Enter: 1스테이지부터 다시", VIEW_H / 2 - 110, 1f, Color.LIGHT_GRAY);
        }
        batch.end();
    }

    /** 시작 화면: 제목, 줄거리 한 줄, 조작, 사격 방법. 아무 키나 누르면 1스테이지 시작. */
    private void drawTitle() {
        drawPanel();
        drawCentered("달 기지 탈출", VIEW_H - 50, 3.5f, Color.ORANGE);
        drawCentered("괴물이 점령한 달 연구기지. 탈출포드까지 가서 지구로 돌아가자.", VIEW_H - 170, 1f, Color.LIGHT_GRAY);

        float y = drawControls(VIEW_H - 240);

        y -= 20;
        drawCentered("사격", y, 1.5f, Color.ORANGE);
        drawCentered("마우스로 조준하고 왼쪽 버튼으로 쏜다.", y - 45, 1f, Color.WHITE);
        drawCentered("반동은 조준 반대쪽으로 민다: 샷건으로 아래를 쏘면 높이 뜬다.", y - 75, 1f, Color.WHITE);
        drawCentered("중력이 약할수록 더 높이 뜬다. 빗나간 탄은 창문을 깨니 조심!", y - 105, 1f, Color.WHITE);

        float blink = 0.6f + 0.4f * MathUtils.sin(elapsed * 4f);
        drawCentered("아무 키나 눌러 시작", 60, 1.5f, new Color(1, 1, 1, blink));
    }

    /** 조작표 (시작 화면·일시정지 화면 공용). 다 그린 뒤의 y를 돌려준다. */
    private float drawControls(float y) {
        String[][] keys = {
            {"A / D", "이동"}, {"W", "점프"}, {"S", "앉기 (반동 절반)"}, {"R", "재장전"},
            {"1 / 2 / Q", "권총 / 샷건 / 바꾸기"}, {"ESC", "일시정지"}, {"Enter", "스테이지 다시 시작"},
        };
        for (String[] k : keys) {
            font.setColor(Color.CYAN);
            font.draw(batch, k[0], VIEW_W / 2 - 260, y);
            font.setColor(Color.WHITE);
            font.draw(batch, k[1], VIEW_W / 2 - 80, y);
            y -= 32;
        }
        return y;
    }

    /** 스테이지 시작 배너: 스테이지 이름과 그 스테이지의 규칙 한 줄. 끝날 때 서서히 사라진다. */
    private void drawStageBanner() {
        float alpha = Math.min(1f, bannerTimer);
        String rule = world.stage.vacuum ? "진공 - 어디서나 산소가 줄어든다. 산소 스테이션을 이어서 쓰자."
            : world.stageIndex == 0 ? "인공중력 정상. 샷건으로 아래를 쏘면 반동으로 높이 뛴다."
            : "인공중력 고장. 이미 깨진 창문이 있다.";
        drawCentered(String.format("%d스테이지 - %s", world.stageIndex + 1, world.stage.name), VIEW_H - 140, 2.5f,
            new Color(1, 1, 1, alpha));
        drawCentered(String.format("중력 %.2fG   %s", world.stage.gravityScale, rule), VIEW_H - 210, 1f,
            new Color(0.7f, 0.9f, 1f, alpha));
    }

    private void drawPanel() {
        batch.setColor(0, 0, 0, 0.6f);
        batch.draw(pixel, 0, 0, VIEW_W, VIEW_H);
        batch.setColor(Color.WHITE);
    }

    private final GlyphLayout layout = new GlyphLayout();

    private float widthOf(String text) {
        layout.setText(font, text);
        return layout.width;
    }

    /** 글자를 가운데 정렬로 그린다 (scale 배 크기). */
    private void drawCentered(String text, float y, float scale, Color color) {
        font.getData().setScale(scale);
        font.setColor(color);
        layout.setText(font, text);
        font.draw(batch, layout, (VIEW_W - layout.width) / 2, y);
        font.getData().setScale(1f);
        font.setColor(Color.WHITE);
    }

    /** 산소 30% 미만: 화면 가장자리가 붉게 맥박친다. 적을수록 진하다. */
    private void drawLowOxygenVignette() {
        float ratio = world.oxygen / World.MAX_OXYGEN;
        if (ratio >= 0.3f || world.playerDead()) return;
        float strength = 0.4f + 0.6f * (0.3f - ratio) / 0.3f;         // 0.4(30%) ~ 1(0%)
        float pulse = 0.6f + 0.4f * MathUtils.sin(elapsed * 6f);
        for (int i = 0; i < 8; i++) { // 바깥에서 안쪽으로 겹겹이 옅어지는 테두리
            float t = 12 * i;
            batch.setColor(0.8f, 0f, 0f, 0.45f * strength * pulse * (8 - i) / 8f);
            batch.draw(pixel, t, t, VIEW_W - 2 * t, 12);
            batch.draw(pixel, t, VIEW_H - t - 12, VIEW_W - 2 * t, 12);
            batch.draw(pixel, t, t + 12, 12, VIEW_H - 2 * t - 24);
            batch.draw(pixel, VIEW_W - t - 12, t + 12, 12, VIEW_H - 2 * t - 24);
        }
        batch.setColor(Color.WHITE);
    }

    /**
     * 창문: 멀쩡하면 푸른 유리 + 균열 단계별 금, 깨지면 그 자리에 우주(별)를 합성해 그린다.
     * 별은 달 표면 배경의 별 타일을 재사용한다.
     */
    private void drawWindow(Window w) {
        Rectangle g = w.glass;
        float frame = 6;
        batch.setColor(0.25f, 0.27f, 0.32f, 1f);
        batch.draw(pixel, g.x - frame, g.y - frame, g.width + frame * 2, g.height + frame * 2);
        if (w.broken()) {
            Art.Look moon = art.looks[art.looks.length - 1];
            batch.setColor(moon.clearColor);
            batch.draw(pixel, g.x, g.y, g.width, g.height);
            batch.setColor(Color.WHITE);
            for (int i = 0; i < 6; i++) { // 창 안쪽에만 들어가는 고정 위치 별
                float sx = g.x + 8 + (i * 37) % (g.width - 32), sy = g.y + 8 + (i * 53) % (g.height - 32);
                batch.draw(moon.stars[i % moon.stars.length], sx, sy, 16, 16);
            }
            batch.setColor(GLASS_EDGE); // 깨진 유리 테두리 조각
            for (int i = 0; i < 5; i++) {
                batch.draw(pixel, g.x + i * g.width / 5, g.y + g.height - 6 - (i % 2) * 6, 10, 6 + (i % 2) * 6);
                batch.draw(pixel, g.x + i * g.width / 5 + 8, g.y, 8, 4 + (i % 3) * 4);
            }
        } else {
            batch.setColor(0.45f, 0.75f, 0.95f, 0.35f);
            batch.draw(pixel, g.x, g.y, g.width, g.height);
            batch.setColor(GLASS_EDGE);
            float cx = g.x + g.width * 0.4f, cy = g.y + g.height * 0.55f;
            for (int i = 0; i < w.crackLevel() * 3; i++) { // 균열 단계마다 금 3줄씩
                float len = 18 + (i * 13) % 30, angle = i * 47f;
                batch.draw(pixel, cx, cy, 0, 1, len, 2, 1, 1, angle);
            }
        }
        batch.setColor(Color.WHITE);
    }

    /** 산소 스테이션: 청록색 탱크 + O2 글자 */
    private void drawStation(Rectangle s) {
        float pulse = 0.75f + 0.25f * MathUtils.sin(elapsed * 3f);
        batch.setColor(0.15f, 0.45f, 0.55f, 1f);
        batch.draw(pixel, s.x, s.y, s.width, s.height);
        batch.setColor(0.4f * pulse, 0.95f * pulse, 1f * pulse, 1f);
        batch.draw(pixel, s.x + 6, s.y + 8, s.width - 12, s.height - 24);
        batch.setColor(Color.WHITE);
        font.setColor(Color.CYAN);
        font.draw(batch, "O2", s.x + 4, s.y + s.height + 28);
        font.setColor(Color.WHITE);
    }

    /** 산소(=체력) 게이지. 30% 미만이면 빨간색. */
    private void drawOxygenBar(float x, float y) {
        float w = 200, h = 12, ratio = world.oxygen / World.MAX_OXYGEN;
        batch.setColor(0.15f, 0.15f, 0.2f, 0.8f);
        batch.draw(pixel, x, y, w, h);
        batch.setColor(ratio < 0.3f ? Color.RED : Color.CYAN);
        batch.draw(pixel, x, y, w * ratio, h);
        batch.setColor(Color.WHITE);
        font.draw(batch, "산소", x + w + 8, y + h + 6);
    }

    @Override
    public void resize(int width, int height) {
        viewport.update(width, height, false);
    }

    @Override
    public void dispose() {
        batch.dispose();
        font.dispose();
        fontGenerator.dispose();
        art.dispose();
        pixelTexture.dispose();
        hiddenCursor.dispose();
    }
}
