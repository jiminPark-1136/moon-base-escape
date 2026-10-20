package io.jbnu.game;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.utils.Array;

/** 게임 로직. 강의 10장 설계대로 World는 로직만, 카메라 연출은 CameraManager가 맡는다. */
public class World {

    private static final float MOVE_SPEED = 200; // 강의 260에서 낮춤 (플레이테스트 피드백)
    /** 강의의 GRAVITY 1700을 지구 중력 1G로 둔다. */
    private static final float EARTH_GRAVITY = 1700;
    /** 히트박스. 32px 트루퍼를 2배로 그렸을 때 몸통에 맞춘 크기. */
    private static final float PLAYER_W = 36, STAND_H = 60, CROUCH_H = 44;
    private static final float CROUCH_RECOIL = 0.5f;
    /** 반동으로 생긴 수평 속도가 줄어드는 비율(1/초). 땅에서는 빨리, 공중에서는 천천히 멈춘다. */
    private static final float KICK_FRICTION_GROUND = 10f, KICK_FRICTION_AIR = 1.5f;
    private static final float BULLET_SPEED = 1400, BULLET_LIFE = 1f, BULLET_SIZE = 6;

    public static class Bullet {
        public final Rectangle bounds = new Rectangle(0, 0, BULLET_SIZE, BULLET_SIZE);
        public float vx, vy, life;
        public int damage;
    }

    /** 피 한 방울. 스테이지 중력을 받는다 → 달에서는 천천히 떨어진다. 블록에 닿으면 그 자리에 붙는다. */
    public static class Particle {
        public float x, y, vx, vy, life;
        public final Color color = new Color();
        public boolean stuck;
    }

    /** 스피터의 산성탄. 스테이지 중력을 받아 포물선으로 날아간다. */
    public static class Acid {
        public final Rectangle bounds = new Rectangle(0, 0, 12, 12);
        public float vx, vy, life = 4f;
    }

    /** 산소 = 체력. 0이 되면 사망 → 잠시 후 재시작. */
    public static final float MAX_OXYGEN = 100;
    /** 진공에서 초당 산소 소모: 달 표면(기본 호흡) / 창문이 깨진 방(급격한 감압) / 누출 단계마다 추가 */
    static final float DRAIN_SURFACE = 2, DRAIN_BROKEN_ROOM = 8, DRAIN_PER_LEAK = 3;
    public static final int MAX_LEAK = 3;
    private static final float STATION_REFILL = 40;
    /** 깨진 창문 쪽으로 빨아들이는 가속도(월드 단위/초²). 앉으면 절반. */
    private static final float SUCTION = 900, CROUCH_SUCTION = 0.5f;
    private static final Color GLASS = new Color(0.65f, 0.9f, 1f, 1f);
    private static final float CONTACT_DAMAGE = 20, INVULNERABLE_TIME = 1.0f, DEATH_TIME = 1.5f;
    private static final Color PLAYER_BLOOD = new Color(0.75f, 0.05f, 0.05f, 1f);
    private static final Color ALIEN_BLOOD = new Color(0.55f, 0.75f, 0.10f, 1f);
    public static final Color ACID = new Color(0.6f, 1f, 0.2f, 1f);

    public final Body player = new Body(0, 0, PLAYER_W, STAND_H);
    public final Array<Rectangle> blocks = new Array<>();
    /** 출구. 닿으면 스테이지 클리어. */
    public Rectangle flag;
    public final Array<Bullet> bullets = new Array<>();
    public final Array<Enemy> enemies = new Array<>();
    public final Array<Particle> particles = new Array<>();
    public final Array<Acid> acids = new Array<>();
    public final Array<Window> windows = new Array<>();
    /** 산소 스테이션: 서 있으면 충전 + 누출 수리 */
    public final Array<Rectangle> stations = new Array<>();
    public float oxygen;
    /** 우주복 누출 단계 0~3. 피격마다 +1, 스테이션에서 0으로 수리. */
    public int leak;
    /** 지금 플레이어 주변이 진공인지 / 스테이션에 서 있는지 (HUD 표시용) */
    public boolean inVacuum, atStation;
    /** 이번 스텝에 창문이 깨졌는지 (카메라 흔들림용) */
    public boolean windowBrokeThisStep;
    /** 남은 무적 시간 (깜빡임 표시용으로 공개) */
    public float invulnerable;
    /** 사망 후 재시작까지 남은 시간. 0이면 살아 있음. */
    public float deathTimer;
    /** 이번 스텝에 플레이어가 맞았는지 (카메라 흔들림용) */
    public boolean playerHitThisStep;

    public Weapon weapon = Weapon.PISTOL;
    public final int[] ammo = new int[Weapon.values().length];
    public float reloadTimer;
    public boolean crouching;
    public boolean facingRight = true;
    public float aimX, aimY;
    /** 이번 스텝에 발사한 무기 (카메라 흔들림용). 발사하지 않았으면 null. */
    public Weapon firedThisStep;

    private float kickX;
    private float cooldownTimer;
    private boolean cleared;

    /** 지금 스테이지 (0부터). 데이터는 Stage에 있다. */
    public int stageIndex;
    public Stage stage;
    /** 지금까지 죽은 횟수 (전체 클리어 화면에 표시) */
    public int deaths;

    public World() {
        loadStage(0);
    }

    /** 스테이지를 불러온다: 고정 지형(블록·스테이션)을 만들고 나머지는 reset()이 처음 상태로 만든다. */
    public void loadStage(int index) {
        stageIndex = index;
        stage = Stage.get(index);
        blocks.clear();
        for (float[] b : stage.blocks) blocks.add(new Rectangle(b[0], b[1], b[2], b[3]));
        stations.clear();
        for (float[] st : stage.stations) stations.add(new Rectangle(st[0], st[1], st[2], st[3]));
        flag = stage.exit;
        reset();
    }

    public boolean isLastStage() {
        return stageIndex == Stage.COUNT - 1;
    }

    public float gravity() {
        return EARTH_GRAVITY * stage.gravityScale; // 1스테이지 1G, 2스테이지 0.58G, 3스테이지 0.16G
    }

    public boolean isGameClear() {
        return cleared;
    }

    /** 강의 과제2의 재시작 로직을 확장: 지금 스테이지의 처음 상태(위치·탄약·적·창문·산소)로 되돌린다. */
    public void reset() {
        player.bounds.set(stage.startX, stage.startY, PLAYER_W, STAND_H);
        player.vx = player.vy = 0;
        player.grounded = false;
        kickX = 0;
        crouching = false;
        for (Weapon w : Weapon.values()) ammo[w.ordinal()] = w.magazine;
        reloadTimer = 0;
        cooldownTimer = 0;
        bullets.clear();
        particles.clear();
        enemies.clear();
        for (float[] s : stage.enemies) {
            Enemy.Type type = s.length > 3 ? Enemy.Type.values()[(int) s[3]] : Enemy.Type.CRAWLER; // 4번째 값: 0 크롤러, 1 드로퍼, 2 스피터
            enemies.add(new Enemy(s[0], s[1], s[2] > 0, type));
        }
        acids.clear();
        windows.clear();
        for (float[] w : stage.windows) {
            Window win = new Window(new Rectangle(w[0], w[1], w[2], w[3]), new Rectangle(w[4], w[5], w[6], w[7]));
            win.hp = (int) w[8]; // 0이면 처음부터 깨진 창문
            windows.add(win);
        }
        oxygen = MAX_OXYGEN;
        leak = 0;
        invulnerable = 0;
        deathTimer = 0;
        cleared = false;
    }

    public boolean playerDead() {
        return deathTimer > 0;
    }

    public void update(float dt, float aimWorldX, float aimWorldY) {
        firedThisStep = null;
        playerHitThisStep = false;
        windowBrokeThisStep = false;
        if (cleared || dt <= 0) return;
        updateParticles(dt);

        // 사망 연출 중: 입력을 막고 몸만 떨어지게 둔 뒤 재시작
        if (playerDead()) {
            player.vx = 0;
            player.applyGravity(gravity(), dt);
            player.moveY(dt, blocks);
            updateEnemies(dt);
            deathTimer -= dt;
            if (deathTimer <= 0) reset();
            return;
        }
        invulnerable = Math.max(0, invulnerable - dt);
        aimX = aimWorldX;
        aimY = aimWorldY;
        facingRight = aimX >= player.bounds.x + player.bounds.width / 2; // 조준점이 몸 앞쪽이면 그쪽을 본다

        handleWeaponKeys();
        if (Gdx.input.isKeyJustPressed(Input.Keys.N)) cleared = true; // 테스트용: 이 스테이지를 바로 클리어

        // 1. 앉기 / 좌우 이동 / 점프 (강의: 키를 누르는 동안 속도를 매 프레임 다시 정한다)
        setCrouching(Gdx.input.isKeyPressed(Input.Keys.S) && player.grounded);
        float inputVx = 0;
        if (Gdx.input.isKeyPressed(Input.Keys.A)) inputVx -= MOVE_SPEED;
        if (Gdx.input.isKeyPressed(Input.Keys.D)) inputVx += MOVE_SPEED;
        if (crouching) inputVx *= 0.5f;
        if (Gdx.input.isKeyJustPressed(Input.Keys.W) && player.grounded && !crouching) {
            player.vy = (float) Math.sqrt(2 * gravity() * stage.jumpHeight); // 스테이지별 점프 높이 → 속도 (1G: 650)
            player.grounded = false;
        }

        // 2. 사격과 반동
        cooldownTimer -= dt;
        if (reloadTimer > 0) {
            reloadTimer -= dt;
            if (reloadTimer <= 0) ammo[weapon.ordinal()] = weapon.magazine;
        }
        if (Gdx.input.isButtonPressed(Input.Buttons.LEFT) && cooldownTimer <= 0 && reloadTimer <= 0) {
            if (ammo[weapon.ordinal()] > 0) fire();
            else startReload();
        }

        // 3. 중력 → X축 → Y축 (강의와 같은 순서)
        player.applyGravity(gravity(), dt);
        float friction = player.grounded ? KICK_FRICTION_GROUND : KICK_FRICTION_AIR;
        kickX -= kickX * Math.min(1f, friction * dt);
        Window open = brokenWindowAround(player.bounds);
        if (open != null) { // 감압: 깨진 창문 쪽으로 끌려간다. 앉아서 버티면 절반
            float dir = Math.signum(open.centerX() - centerX(player.bounds));
            kickX += dir * SUCTION * (crouching ? CROUCH_SUCTION : 1f) * dt;
        }
        player.vx = inputVx + kickX;
        if (player.moveX(dt, blocks, stage.width)) kickX = 0;
        player.moveY(dt, blocks);
        if (player.bounds.y + player.bounds.height > stage.height) { // 천장 없는 맵(달 표면)의 위쪽 끝
            player.bounds.y = stage.height - player.bounds.height;
            player.vy = Math.min(player.vy, 0);
        }

        updateBullets(dt);
        updateEnemies(dt);
        updateAcids(dt);
        updateOxygen(dt);
        if (playerDead()) return;

        // 4. 낭떠러지에 떨어지면 처음부터 (강의의 낙사 복귀)
        if (player.bounds.y < -100) {
            deaths++;
            reset();
        }

        // 5. 깃발에 닿으면 클리어
        if (player.bounds.overlaps(flag)) cleared = true;
    }

    private void handleWeaponKeys() {
        Weapon next = weapon;
        if (Gdx.input.isKeyJustPressed(Input.Keys.NUM_1)) next = Weapon.PISTOL;
        if (Gdx.input.isKeyJustPressed(Input.Keys.NUM_2)) next = Weapon.SHOTGUN;
        if (Gdx.input.isKeyJustPressed(Input.Keys.Q)) {
            next = weapon == Weapon.PISTOL ? Weapon.SHOTGUN : Weapon.PISTOL;
        }
        if (next != weapon) {
            weapon = next;
            reloadTimer = 0; // 무기를 바꾸면 재장전 취소
        }
        if (Gdx.input.isKeyJustPressed(Input.Keys.R)) startReload();
    }

    private void startReload() {
        if (reloadTimer > 0 || ammo[weapon.ordinal()] == weapon.magazine) return;
        reloadTimer = weapon.reloadTime;
    }

    /** 앉으면 히트박스 높이가 줄어든다. 머리 위가 막혀 있으면 일어서지 못한다. */
    private void setCrouching(boolean want) {
        if (want == crouching) return;
        Rectangle b = player.bounds;
        if (!want) {
            Rectangle standing = new Rectangle(b.x, b.y, b.width, STAND_H);
            for (Rectangle block : blocks) {
                if (standing.overlaps(block)) return;
            }
        }
        crouching = want;
        b.height = want ? CROUCH_H : STAND_H;
    }

    /** 어깨(팔·총 회전축). Art의 대기 프레임 어깨(x 9, 위에서 21.5)를 월드 단위로 옮긴 값 — 발에서 53, 앉으면 75%. */
    public float shoulderX() {
        return centerX(player.bounds) + (facingRight ? -14 : 14);
    }

    public float shoulderY() {
        return player.bounds.y + (crouching ? 40 : 53);
    }

    /** 어깨에서 조준점으로 향하는 각도(도). 팔·총 그림과 총알 방향이 모두 이 값을 쓴다. */
    public float aimAngle() {
        return MathUtils.atan2(aimY - shoulderY(), aimX - shoulderX()) * MathUtils.radiansToDegrees;
    }

    /** 총구 = 어깨에서 조준 방향으로 총 길이만큼 */
    public float muzzleX() {
        return shoulderX() + MathUtils.cosDeg(aimAngle()) * weapon.barrel;
    }

    public float muzzleY() {
        return shoulderY() + MathUtils.sinDeg(aimAngle()) * weapon.barrel;
    }

    private void fire() {
        float mx = muzzleX(), my = muzzleY();
        float angle = aimAngle();

        for (int i = 0; i < weapon.pellets; i++) {
            float a = angle + MathUtils.random(-weapon.spreadDeg, weapon.spreadDeg);
            Bullet b = new Bullet();
            b.bounds.setPosition(mx - BULLET_SIZE / 2, my - BULLET_SIZE / 2);
            b.vx = MathUtils.cosDeg(a) * BULLET_SPEED;
            b.vy = MathUtils.sinDeg(a) * BULLET_SPEED;
            b.life = BULLET_LIFE;
            b.damage = weapon.damage;
            bullets.add(b);
        }
        ammo[weapon.ordinal()]--;
        cooldownTimer = weapon.cooldown;
        firedThisStep = weapon;

        // 반동: 조준 방향의 반대로 고정된 속도 변화를 준다.
        // 위로 미는 반동은 현재 속도와 겹치지 않게 max로 처리 → 도달 높이가 항상 v² / 2g
        float recoil = weapon.recoil * (crouching ? CROUCH_RECOIL : 1f);
        kickX -= MathUtils.cosDeg(angle) * recoil;
        float kickY = -MathUtils.sinDeg(angle) * recoil;
        if (kickY > 0) {
            player.vy = Math.max(player.vy, kickY);
            player.grounded = false;
        } else {
            player.vy += kickY;
        }

        if (ammo[weapon.ordinal()] == 0) startReload();
    }

    /** 총알 한 스텝 이동을 이 길이 이하로 나눠 검사한다 → 얇은 발판·창문을 뚫고 지나가지 않는다. */
    private static final float BULLET_SUBSTEP = 8;

    private void updateBullets(float dt) {
        for (int i = bullets.size - 1; i >= 0; i--) {
            Bullet b = bullets.get(i);
            b.life -= dt;
            boolean dead = b.life <= 0;
            float dist = (float) Math.sqrt(b.vx * b.vx + b.vy * b.vy) * dt;
            int steps = Math.max(1, (int) Math.ceil(dist / BULLET_SUBSTEP));
            for (int s = 0; s < steps && !dead; s++) {
                b.bounds.x += b.vx * dt / steps;
                b.bounds.y += b.vy * dt / steps;
                dead = bulletHits(b);
            }
            if (dead) bullets.removeIndex(i);
        }
    }

    /** 총알이 블록·적·창문 중 하나에 맞았으면 효과를 적용하고 true. */
    private boolean bulletHits(Bullet b) {
        for (Rectangle block : blocks) {
            if (b.bounds.overlaps(block)) return true;
        }
        for (Enemy e : enemies) {
            if (!b.bounds.overlaps(e.body.bounds)) continue;
            e.hit(b.damage, Math.signum(b.vx) * 120);
            bleed(b.bounds.x, b.bounds.y, b.vx, b.vy, ALIEN_BLOOD, 6);
            return true;
        }
        for (Window w : windows) {
            if (w.broken() || !b.bounds.overlaps(w.glass)) continue;
            bleed(b.bounds.x, b.bounds.y, -b.vx, -b.vy, GLASS, 3); // 유리 파편
            if (w.hit(b.damage)) {
                windowBrokeThisStep = true;
                bleed(w.centerX(), w.glass.y + w.glass.height / 2, 0, 0, GLASS, 40);
            }
            return true;
        }
        return false;
    }

    /** 사각형 중심이 깨진 창문 방 안에 있으면 그 창문, 아니면 null. */
    private Window brokenWindowAround(Rectangle r) {
        for (Window w : windows) {
            if (w.broken() && w.room.contains(centerX(r), r.y + r.height / 2)) return w;
        }
        return null;
    }

    private static float centerX(Rectangle r) {
        return r.x + r.width / 2;
    }

    /** 진공 여부에 따른 초당 산소 소모. 기지 안(진공 아님)에서는 줄지 않는다. */
    static float oxygenDrain(boolean onMoonSurface, boolean inBrokenRoom, int leak) {
        if (!onMoonSurface && !inBrokenRoom) return 0;
        float base = inBrokenRoom ? DRAIN_BROKEN_ROOM : DRAIN_SURFACE;
        return base + leak * DRAIN_PER_LEAK;
    }

    private void updateOxygen(float dt) {
        boolean onMoon = stage.vacuum; // 3스테이지 = 기지 밖
        boolean inRoom = brokenWindowAround(player.bounds) != null;
        inVacuum = onMoon || inRoom;
        atStation = false;
        for (Rectangle s : stations) {
            if (s.overlaps(player.bounds)) atStation = true;
        }
        if (atStation) {
            oxygen = Math.min(MAX_OXYGEN, oxygen + STATION_REFILL * dt);
            leak = 0;
        } else {
            oxygen = Math.max(0, oxygen - oxygenDrain(onMoon, inRoom, leak) * dt);
        }
        if (oxygen <= 0) killPlayer();
    }

    private void killPlayer() {
        if (playerDead()) return;
        deaths++;
        deathTimer = DEATH_TIME;
        Rectangle p = player.bounds;
        bleed(p.x + p.width / 2, p.y + p.height / 2, 0, 300, PLAYER_BLOOD, 30);
    }

    private void updateEnemies(float dt) {
        for (int i = enemies.size - 1; i >= 0; i--) {
            Enemy e = enemies.get(i);
            Rectangle eb = e.body.bounds;
            Window open = brokenWindowAround(eb);
            if (open != null) { // 감압: 외계인은 버티지 못하고 창문(바닥 높이)으로 끌려가 빨려 나간다
                float dx = open.centerX() - centerX(eb);
                e.pull(Math.signum(dx) * SUCTION * 1.5f * dt);
                if (eb.overlaps(open.glass)) {
                    bleed(centerX(eb), eb.y + eb.height / 2, 0, 400, ALIEN_BLOOD, 20);
                    enemies.removeIndex(i);
                    continue;
                }
            }
            e.update(dt, gravity(), blocks, player.bounds, stage.width);
            if (e.wantsToSpit) { // 스피터: 플레이어 몸 중심을 향해 중력 포물선으로 쏜다
                e.wantsToSpit = false;
                Acid a = new Acid();
                a.bounds.setPosition(centerX(eb) - 6, eb.y + eb.height / 2);
                float[] v = Enemy.lob(a.bounds.x, a.bounds.y, centerX(player.bounds), player.bounds.y + player.bounds.height / 2, gravity());
                a.vx = v[0];
                a.vy = v[1];
                acids.add(a);
            }
            if (e.dead() || eb.y < -100) {
                if (e.dead()) bleed(eb.x + eb.width / 2, eb.y + eb.height / 2, 0, 300, ALIEN_BLOOD, 30);
                enemies.removeIndex(i);
                continue;
            }
            if (!playerDead() && invulnerable <= 0 && eb.overlaps(player.bounds)) hurtPlayer(eb);
        }
    }

    /** 산성탄: 중력을 받아 날아가고, 블록에 닿으면 튀어 사라지고, 플레이어에 닿으면 적과 닿은 것처럼 피해를 준다. */
    private void updateAcids(float dt) {
        for (int i = acids.size - 1; i >= 0; i--) {
            Acid a = acids.get(i);
            a.life -= dt;
            a.vy -= gravity() * dt;
            a.bounds.x += a.vx * dt;
            a.bounds.y += a.vy * dt;
            boolean gone = a.life <= 0 || a.bounds.y < -100;
            for (Rectangle block : blocks) {
                if (!gone && a.bounds.overlaps(block)) gone = true;
            }
            if (!gone && !playerDead() && invulnerable <= 0 && a.bounds.overlaps(player.bounds)) {
                hurtPlayer(a.bounds);
                gone = true;
            }
            if (gone) {
                bleed(a.bounds.x + 6, a.bounds.y + 6, 0, 0, ACID, 8);
                acids.removeIndex(i);
            }
        }
    }

    /** 적과 닿으면: 산소 감소, 적 반대쪽으로 튕겨남, 잠시 무적. 산소 0이면 사망. */
    private void hurtPlayer(Rectangle source) {
        oxygen = Math.max(0, oxygen - CONTACT_DAMAGE);
        leak = Math.min(MAX_LEAK, leak + 1); // 우주복에 구멍 → 진공에서 산소가 더 빨리 샌다
        invulnerable = INVULNERABLE_TIME;
        playerHitThisStep = true;
        float dir = (player.bounds.x + player.bounds.width / 2) < (source.x + source.width / 2) ? -1 : 1;
        kickX = dir * 350;
        player.vy = Math.max(player.vy, 300);
        player.grounded = false;
        Rectangle p = player.bounds;
        bleed(p.x + p.width / 2, p.y + p.height * 0.6f, dir * 200, 200, PLAYER_BLOOD, 14);
        if (oxygen <= 0) killPlayer();
    }

    /** 피 튀기기: (vx, vy) 방향으로 퍼지는 방울 count개. */
    private void bleed(float x, float y, float vx, float vy, Color color, int count) {
        for (int i = 0; i < count; i++) {
            Particle p = new Particle();
            p.x = x;
            p.y = y;
            float speed = MathUtils.random(80, 260);
            float angle = MathUtils.atan2(vy, vx) * MathUtils.radiansToDegrees + MathUtils.random(-60f, 60f);
            if (vx == 0 && vy == 0) angle = MathUtils.random(360f);
            p.vx = MathUtils.cosDeg(angle) * speed;
            p.vy = MathUtils.sinDeg(angle) * speed;
            p.life = MathUtils.random(1.2f, 2.0f);
            p.color.set(color).mul(MathUtils.random(0.7f, 1.1f), MathUtils.random(0.7f, 1.1f), 1f, 1f);
            particles.add(p);
        }
    }

    // ponytail: 파티클마다 모든 블록과 겹침 검사(파티클 수 × 블록 수). 수백 개 이상 동시에 나오면 공간 분할로 바꾼다.
    private void updateParticles(float dt) {
        float g = gravity();
        for (int i = particles.size - 1; i >= 0; i--) {
            Particle p = particles.get(i);
            p.life -= dt;
            if (p.life <= 0) {
                particles.removeIndex(i);
                continue;
            }
            if (p.stuck) continue;
            p.vy -= g * dt;
            p.x += p.vx * dt;
            p.y += p.vy * dt;
            for (Rectangle block : blocks) {
                if (block.contains(p.x, p.y)) {
                    p.stuck = true; // 바닥·벽에 묻은 자국으로 남는다
                    break;
                }
            }
        }
    }
}
