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
    private enum GameState { PLAYING, PAUSED, CLEARED }

    private static final Color GLASS_EDGE = new Color(0.8f, 0.95f, 1f, 0.9f);

    private SpriteBatch batch;
    private BitmapFont font;
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
    private GameState currentState = GameState.PLAYING;

    @Override
    public void create() {
        batch = new SpriteBatch();
        font = new BitmapFont();
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
        camManager.snapTo(camera, playerCenterX(), playerCenterY());
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
        {"look0", (Runnable) () -> autoAim = new com.badlogic.gdx.math.Vector2(200, 0)},
        {"arm-pistol-right", (Runnable) () -> {
            world.weapon = Weapon.SHOTGUN;
            autoAim.set(120, 160);
        }},
        {"arm-shotgun-up", (Runnable) () -> autoAim.set(-150, -110)},
        {"arm-shotgun-downleft", (Runnable) () -> {
            autoAim = null;
            world.weapon = Weapon.PISTOL;
            world.gravityIndex = 1;
        }},
        {"look1", (Runnable) () -> world.gravityIndex = 2},
        {"look2", (Runnable) () -> {           // 창문 방이 보이는 샷건 벽 위로 이동
            world.gravityIndex = 0;
            world.player.bounds.setPosition(2420, 224);
            camManager.snapTo(camera, playerCenterX(), playerCenterY());
        }},
        {"window-intact", (Runnable) () -> world.windows.first().hp = 4},
        {"window-cracked", (Runnable) () -> world.windows.first().hp = 0},
        {"window-broken", (Runnable) () -> {
            world.oxygen = 12;
            world.leak = 2;
        }},
        {"low-oxygen", (Runnable) () -> {        // 맵 전체가 보이게 카메라를 멀리 뺀다
            world.oxygen = World.MAX_OXYGEN;
            overview = true;
            camera.zoom = World.WORLD_WIDTH / VIEW_W;
            camera.position.set(World.WORLD_WIDTH / 2, World.WORLD_HEIGHT / 2, 0);
            camera.update();
        }},
        {"map-overview", null},
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

    private void saveScreenshot(String path) {
        Pixmap pm = Pixmap.createFromFrameBuffer(0, 0, Gdx.graphics.getBackBufferWidth(), Gdx.graphics.getBackBufferHeight());
        PixmapIO.writePNG(Gdx.files.absolute(new java.io.File(path).getAbsolutePath()), pm, Deflater.DEFAULT_COMPRESSION, true); // true: 위아래 뒤집기
        pm.dispose();
    }

    private void input() {
        if (Gdx.input.isKeyJustPressed(Input.Keys.ESCAPE)) {
            if (currentState == GameState.PLAYING) currentState = GameState.PAUSED;
            else if (currentState == GameState.PAUSED) currentState = GameState.PLAYING;
        }
        if (Gdx.input.isKeyJustPressed(Input.Keys.ENTER)) {
            world.reset();
            currentState = GameState.PLAYING;
            camManager.snapTo(camera, playerCenterX(), playerCenterY());
        }
    }

    private void logic(float dt) {
        if (currentState == GameState.PAUSED) return; // 일시정지: 월드와 카메라 모두 멈춘다

        if (currentState == GameState.PLAYING) {
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
        if (!overview) camManager.update(camera, playerCenterX(), playerCenterY(), dt);
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

    /** 강의 10장 endingCheck: 클리어 순간 한 번만 상태를 바꾸고 연출을 시작한다. */
    private void endingCheck() {
        if (world.isGameClear() && currentState != GameState.CLEARED) {
            currentState = GameState.CLEARED;
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
        // 스테이지 분위기: 지금은 중력 단계(G 키)와 같이 바뀐다. 3스테이지 분리 시 스테이지 번호로 바꾼다.
        Art.Look look = art.looks[world.gravityIndex];
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
        batch.setColor(Color.WHITE);

        for (Enemy e : world.enemies) {
            Animation<TextureRegion> a = e.charging ? art.alienWalk : art.alienIdle;
            // 돌진하면 다리를 빨리 움직이고, 맞은 순간에는 빨갛게 번쩍인다
            float t = e.charging ? elapsed * 1.8f : elapsed;
            batch.setColor(e.hurtTimer > 0 ? Color.RED : Color.WHITE);
            drawActor(a, t, e.body.bounds, e.facingRight, 1f);
        }
        batch.setColor(Color.WHITE);

        // 플레이어: 맞은 뒤 무적 시간에는 깜빡인다
        boolean blink = world.invulnerable > 0 && ((int) (world.invulnerable * 10)) % 2 == 0;
        batch.setColor(1, 1, 1, blink ? 0.3f : 1f);
        drawActor(playerAnim, animTime, world.player.bounds, world.facingRight, world.crouching ? 0.75f : 1f);
        if (!world.playerDead()) drawArmAndGun(blink ? 0.3f : 1f);
        batch.setColor(Color.WHITE);

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
        float w = frame.getRegionWidth() * Art.SCALE, h = frame.getRegionHeight() * Art.SCALE * squash;
        float anchor = art.anchorX.get(a, frame.getRegionWidth() / 2) * Art.SCALE;
        float cx = b.x + b.width / 2;
        boolean asDrawn = facingRight != art.facesLeft.contains(a); // 원본 방향 그대로 그려도 되는지
        if (asDrawn) batch.draw(frame, cx - anchor, b.y, w, h);
        else batch.draw(frame, cx + anchor, b.y, -w, h); // 좌우 반전
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
        String reload = world.reloadTimer > 0 ? "  [RELOADING]" : "";
        font.setColor(Color.WHITE);
        font.draw(batch, String.format("Gravity: %.2fG  (G: change)", World.GRAVITY_SCALES[world.gravityIndex]), 16, VIEW_H - 16);
        font.draw(batch, String.format("Weapon: %s  %d/%d%s", w.label, world.ammo[w.ordinal()], w.magazine, reload), 16, VIEW_H - 40);
        font.draw(batch, world.crouching ? "CROUCH (recoil x0.5)" : "", 16, VIEW_H - 88);
        drawOxygenBar(16, VIEW_H - 76);
        if (world.leak > 0) {
            font.setColor(Color.RED);
            font.draw(batch, "SUIT LEAK x" + world.leak, 270, VIEW_H - 64);
        }
        if (world.atStation) {
            font.setColor(Color.CYAN);
            font.draw(batch, "O2 REFILL / SUIT REPAIRED", 16, VIEW_H - 108);
        } else if (world.inVacuum) {
            font.setColor(Color.ORANGE);
            font.draw(batch, "VACUUM", 16, VIEW_H - 108);
        }
        font.setColor(Color.GRAY);
        font.draw(batch, "A/D move  W jump  S crouch  Mouse aim  LMB fire  R reload  1/2/Q weapon  ESC pause  Enter restart", 16, 24);

        font.setColor(Color.WHITE);
        if (currentState == GameState.PAUSED) font.draw(batch, "PAUSED - ESC to resume", VIEW_W / 2 - 80, VIEW_H / 2);
        if (currentState == GameState.CLEARED) font.draw(batch, "CLEAR! - Enter to restart", VIEW_W / 2 - 85, VIEW_H / 2);
        batch.end();
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
        font.draw(batch, "O2", s.x + 8, s.y + s.height + 18);
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
        font.draw(batch, "O2", x + w + 8, y + h);
    }

    @Override
    public void resize(int width, int height) {
        viewport.update(width, height, false);
    }

    @Override
    public void dispose() {
        batch.dispose();
        font.dispose();
        art.dispose();
        pixelTexture.dispose();
        hiddenCursor.dispose();
    }
}
