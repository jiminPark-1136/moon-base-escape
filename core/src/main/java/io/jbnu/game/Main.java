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

/**
 * [스냅샷 v0.2 — 첫 CC0 스프라이트 적용 재현]
 * drakzlin 병사 애니메이션, Eris SF 타일, 도시 배경. 맵은 아직 강의 블록 배치 + 반동 테스트 탑, 총은 회전 막대.
 * (이 단계에서 발견한 문제: 깃발이 검은색이라 배경에 묻힘, 배경이 도시라 달 기지와 안 맞음 → 이후 버전에서 수정)
 * 레포트용 스크린샷을 위해 개발 일지를 바탕으로 나중에 재현한 버전이다.
 */
public class Main extends ApplicationAdapter {
    private static final float VIEW_W = 1280, VIEW_H = 720;

    private enum GameState { PLAYING, PAUSED, CLEARED }

    private SpriteBatch batch;
    private BitmapFont font;
    private Art art;
    private com.badlogic.gdx.graphics.g2d.Animation<TextureRegion> playerAnim;
    private float animTime;
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
        float dt = Math.min(Gdx.graphics.getDeltaTime(), 1f / 30f);
        input();
        logic(dt);
        draw();
        screenshots();
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
        if (currentState == GameState.PAUSED) return;
        if (currentState == GameState.PLAYING) {
            viewport.unproject(mouse.set(Gdx.input.getX(), Gdx.input.getY(), 0));
            world.update(dt, mouse.x, mouse.y);
            if (world.firedThisStep == Weapon.SHOTGUN) camManager.startShake(0.15f, 8f);
            else if (world.firedThisStep == Weapon.PISTOL) camManager.startShake(0.05f, 2f);
            if (world.isGameClear() && currentState != GameState.CLEARED) {
                currentState = GameState.CLEARED;
                camManager.startShake(1.0f, 20f);
            }
        }
        camManager.update(camera, playerCenterX(), playerCenterY(), dt);
        Body pb = world.player;
        com.badlogic.gdx.graphics.g2d.Animation<TextureRegion> next;
        if (!pb.grounded) next = art.jump;
        else if (world.crouching) next = art.crouch;
        else if (Math.abs(pb.vx) > 10) next = art.run;
        else next = art.idle;
        if (next != playerAnim) {
            playerAnim = next;
            animTime = 0;
        }
        animTime += dt;
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
        ScreenUtils.clear(0.06f, 0.06f, 0.10f, 1f);
        viewport.apply();
        batch.setProjectionMatrix(hudMatrix);
        batch.begin();
        batch.setColor(0.45f, 0.45f, 0.55f, 1f);
        batch.draw(art.background, 0, 0, VIEW_W, VIEW_H);
        batch.setColor(Color.WHITE);
        batch.end();

        batch.setProjectionMatrix(camera.combined);
        batch.begin();
        for (Rectangle block : world.blocks) drawBlock(block);
        Rectangle f = world.flag;
        batch.draw(art.flag, f.x, f.y, f.width, f.height);

        Rectangle p = world.player.bounds;
        TextureRegion frame = playerAnim.getKeyFrame(animTime);
        float size = frame.getRegionWidth() * Art.SCALE;
        float drawX = p.x + p.width / 2 - size / 2;
        if (world.facingRight) batch.draw(frame, drawX, p.y, size, size);
        else batch.draw(frame, drawX + size, p.y, -size, size);

        // 총: 총구에서 조준점 방향으로 회전한 막대
        float mx = world.muzzleX(), my = world.muzzleY();
        float angle = MathUtils.atan2(world.aimY - my, world.aimX - mx) * MathUtils.radiansToDegrees;
        float gunLength = world.weapon == Weapon.SHOTGUN ? 30 : 20;
        batch.setColor(world.weapon == Weapon.SHOTGUN ? Color.ORANGE : Color.LIGHT_GRAY);
        batch.draw(pixel, mx, my - 2, 0, 2, gunLength, 4, 1, 1, angle);

        batch.setColor(Color.YELLOW);
        for (World.Bullet b : world.bullets) batch.draw(pixel, b.bounds.x, b.bounds.y, b.bounds.width, b.bounds.height);
        if (currentState == GameState.PLAYING) drawCrosshair(world.aimX, world.aimY);
        batch.setColor(Color.WHITE);
        batch.end();

        batch.setProjectionMatrix(hudMatrix);
        batch.begin();
        Weapon w = world.weapon;
        String reload = world.reloadTimer > 0 ? "  [RELOADING]" : "";
        font.setColor(Color.WHITE);
        font.draw(batch, String.format("Gravity: %.2fG  (G: change)", World.GRAVITY_SCALES[world.gravityIndex]), 16, VIEW_H - 16);
        font.draw(batch, String.format("Weapon: %s  %d/%d%s", w.label, world.ammo[w.ordinal()], w.magazine, reload), 16, VIEW_H - 40);
        font.draw(batch, world.crouching ? "CROUCH (recoil x0.5)" : "", 16, VIEW_H - 64);
        font.setColor(Color.GRAY);
        font.draw(batch, "A/D move  W jump  S crouch  Mouse aim  LMB fire  R reload  1/2/Q weapon  ESC pause  Enter restart", 16, 24);
        font.setColor(Color.WHITE);
        if (currentState == GameState.PAUSED) font.draw(batch, "PAUSED - ESC to resume", VIEW_W / 2 - 80, VIEW_H / 2);
        if (currentState == GameState.CLEARED) font.draw(batch, "CLEAR! - Enter to restart", VIEW_W / 2 - 85, VIEW_H / 2);
        batch.end();
    }

    private void drawBlock(Rectangle b) {
        final float t = 32;
        for (float x = b.x; x < b.x + b.width; x += t) {
            float w = Math.min(t, b.x + b.width - x);
            float topH = Math.min(t, b.height);
            for (float y = b.y; y < b.y + b.height - topH; y += t) {
                float h = Math.min(t, b.y + b.height - topH - y);
                batch.draw(art.tileBody, x, y, w, h);
            }
            batch.draw(art.tileTop, x, b.y + b.height - topH, w, topH);
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

    /** -Dautoshot=폴더: 시작 화면, 반동 테스트 탑(달 중력) 화면을 찍고 종료. F12: 현재 화면 저장. */
    private final String autoShotDir = System.getProperty("autoshot");
    private int frameCount;

    private void screenshots() {
        if (Gdx.input.isKeyJustPressed(Input.Keys.F12)) saveScreenshot("screenshots/shot-" + System.currentTimeMillis() + ".png");
        if (autoShotDir == null) return;
        frameCount++;
        if (frameCount == 30) {
            saveScreenshot(autoShotDir + "/start.png");
            world.player.bounds.setPosition(2640, 220);
            world.gravityIndex = 2;
            camManager.snapTo(camera, playerCenterX(), playerCenterY());
        }
        if (frameCount == 60) {
            saveScreenshot(autoShotDir + "/tower.png");
            Gdx.app.exit();
        }
    }

    private void saveScreenshot(String path) {
        Pixmap pm = Pixmap.createFromFrameBuffer(0, 0, Gdx.graphics.getBackBufferWidth(), Gdx.graphics.getBackBufferHeight());
        PixmapIO.writePNG(Gdx.files.absolute(new java.io.File(path).getAbsolutePath()), pm, Deflater.DEFAULT_COMPRESSION, true);
        pm.dispose();
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
