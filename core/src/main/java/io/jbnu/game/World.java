package io.jbnu.game;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.utils.Array;

/** 게임 로직. 강의 10장 설계대로 World는 로직만, 카메라 연출은 CameraManager가 맡는다. */
public class World {
    public static final float WORLD_WIDTH = 3840;
    public static final float WORLD_HEIGHT = 720;

    private static final float MOVE_SPEED = 200; // 강의 260에서 낮춤 (플레이테스트 피드백)
    private static final float JUMP_SPEED = 650;
    /** 강의의 GRAVITY 1700을 지구 중력 1G로 둔다. */
    private static final float EARTH_GRAVITY = 1700;
    /** 1스테이지 인공중력 1G, 2스테이지 (1G + 달)/2, 3스테이지 달 중력. */
    public static final float[] GRAVITY_SCALES = {1f, 0.58f, 0.16f};

    private static final float START_X = 80, START_Y = 64;
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

    public final Body player = new Body(START_X, START_Y, PLAYER_W, STAND_H);
    public final Array<Rectangle> blocks = new Array<>();
    /** 출구. 닿으면 스테이지 클리어. */
    public final Rectangle flag = new Rectangle(3700, 64, 48, 56);
    public final Array<Bullet> bullets = new Array<>();
    public final Array<Enemy> enemies = new Array<>();
    public final Array<Particle> particles = new Array<>();
    public final Array<Window> windows = new Array<>();
    /** 산소 스테이션: 서 있으면 충전 + 누출 수리 */
    public final Array<Rectangle> stations = new Array<>();
    /** 창문 {유리 x, y, w, h, 방 x, y, w, h} — 재시작 때마다 새 유리로 다시 만든다. */
    private final float[][] windowSpawns = {
        {2560, 300, 128, 96, 2496, 64, 304, 592},  // 샷건 벽과 앉기 통로 사이 방
    };
    /** 적 시작 위치 {x, y, 오른쪽을 보면 1} — 재시작 때마다 여기서 다시 만든다. */
    private final float[][] enemySpawns = {
        {800, 64, 0},    // 첫 상자 뒤
        {2000, 64, 1},   // 구덩이 건너편
        {2650, 64, 0},   // 샷건 벽 뒤 (창문 방)
        {3150, 64, 1},   // 앉기 통로 뒤
    };

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

    public int gravityIndex = 0;
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

    public World() {
        // 1스테이지: 연구동 (1G 인공중력). 바닥 윗면 y = 64, 천장 아랫면 y = 656.
        // 왼쪽부터 순서대로 조작을 하나씩 가르치는 구조.
        blocks.add(new Rectangle(0, 0, 1760, 64));       // 바닥 (왼쪽)
        blocks.add(new Rectangle(1872, 0, 1968, 64));    // 바닥 (오른쪽) — 사이 112px 구덩이
        blocks.add(new Rectangle(0, 656, 3840, 64));     // 천장

        blocks.add(new Rectangle(520, 64, 64, 64));      // ① 상자: 점프 (높이 64)
        blocks.add(new Rectangle(1100, 64, 96, 96));     // ② 계단식 발판: 윗면 160
        blocks.add(new Rectangle(1300, 224, 192, 32));   //    윗면 256
        blocks.add(new Rectangle(1560, 160, 128, 32));   //    윗면 192
        //                                                  ③ x 1760~1872 구덩이: 떨어지면 처음부터 (강의 낙사 재시작)
        blocks.add(new Rectangle(2400, 64, 96, 160));    // ④ 벽: 높이 160. 1G 점프(약 119)로는 못 넘고
                                                         //    점프 + 샷건 반동(약 69)이어야 넘는다
        blocks.add(new Rectangle(2800, 116, 256, 540));  // ⑤ 낮은 통로: 바닥과 52px 틈 → 앉아서(높이 44)만 통과
        blocks.add(new Rectangle(3300, 64, 128, 64));    // ⑥ 출구 앞 발판
        blocks.add(new Rectangle(3460, 64, 128, 128));

        stations.add(new Rectangle(2300, 64, 40, 64));   // 샷건 벽 앞
        stations.add(new Rectangle(3200, 64, 40, 64));   // 앉기 통로 뒤
        reset();
    }

    public float gravity() {
        return EARTH_GRAVITY * GRAVITY_SCALES[gravityIndex];
    }

    public boolean isGameClear() {
        return cleared;
    }

    /** 강의 과제2의 재시작 로직을 확장: 위치뿐 아니라 탄약·총알·상태도 처음으로 되돌린다. */
    public void reset() {
        player.bounds.set(START_X, START_Y, PLAYER_W, STAND_H);
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
        for (float[] s : enemySpawns) enemies.add(new Enemy(s[0], s[1], s[2] > 0));
        windows.clear();
        for (float[] w : windowSpawns) {
            windows.add(new Window(new Rectangle(w[0], w[1], w[2], w[3]), new Rectangle(w[4], w[5], w[6], w[7])));
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
        facingRight = aimX >= player.bounds.x + player.bounds.width / 2;

        handleWeaponKeys();
        if (Gdx.input.isKeyJustPressed(Input.Keys.G)) {
            gravityIndex = (gravityIndex + 1) % GRAVITY_SCALES.length; // 테스트용: 3가지 중력 순환
        }

        // 1. 앉기 / 좌우 이동 / 점프 (강의: 키를 누르는 동안 속도를 매 프레임 다시 정한다)
        setCrouching(Gdx.input.isKeyPressed(Input.Keys.S) && player.grounded);
        float inputVx = 0;
        if (Gdx.input.isKeyPressed(Input.Keys.A)) inputVx -= MOVE_SPEED;
        if (Gdx.input.isKeyPressed(Input.Keys.D)) inputVx += MOVE_SPEED;
        if (crouching) inputVx *= 0.5f;
        if (Gdx.input.isKeyJustPressed(Input.Keys.W) && player.grounded && !crouching) {
            player.vy = JUMP_SPEED;
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
        if (player.moveX(dt, blocks, WORLD_WIDTH)) kickX = 0;
        player.moveY(dt, blocks);

        updateBullets(dt);
        updateEnemies(dt);
        updateOxygen(dt);
        if (playerDead()) return;

        // 4. 낭떠러지에 떨어지면 처음부터 (강의의 낙사 복귀)
        if (player.bounds.y < -100) reset();

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

    /** 총구: 스페이스 마린 스프라이트의 소총 끝 (바라보는 쪽으로 몸 중심에서 36) */
    public float muzzleX() {
        return player.bounds.x + player.bounds.width / 2 + (facingRight ? 36 : -36);
    }

    public float muzzleY() {
        return player.bounds.y + player.bounds.height * 0.6f;
    }

    private void fire() {
        float mx = muzzleX(), my = muzzleY();
        float angle = MathUtils.atan2(aimY - my, aimX - mx) * MathUtils.radiansToDegrees;

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
        boolean onMoon = gravityIndex == GRAVITY_SCALES.length - 1; // 3스테이지 = 기지 밖
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
        deathTimer = DEATH_TIME;
        Rectangle p = player.bounds;
        bleed(p.x + p.width / 2, p.y + p.height / 2, 0, 300, PLAYER_BLOOD, 30);
    }

    private void updateEnemies(float dt) {
        for (int i = enemies.size - 1; i >= 0; i--) {
            Enemy e = enemies.get(i);
            Rectangle eb = e.body.bounds;
            Window open = brokenWindowAround(eb);
            if (open != null) { // 감압: 외계인은 버티지 못하고 창문으로 끌려가 빨려 나간다
                float dx = open.centerX() - centerX(eb);
                e.pull(Math.signum(dx) * SUCTION * 1.5f * dt);
                if (Math.abs(dx) < open.glass.width / 2 && e.body.grounded) e.body.vy = 950;
                if (eb.overlaps(open.glass)) {
                    bleed(centerX(eb), eb.y + eb.height / 2, 0, 400, ALIEN_BLOOD, 20);
                    enemies.removeIndex(i);
                    continue;
                }
            }
            e.update(dt, gravity(), blocks, player.bounds);
            if (e.dead() || eb.y < -100) {
                if (e.dead()) bleed(eb.x + eb.width / 2, eb.y + eb.height / 2, 0, 300, ALIEN_BLOOD, 30);
                enemies.removeIndex(i);
                continue;
            }
            if (!playerDead() && invulnerable <= 0 && eb.overlaps(player.bounds)) hurtPlayer(eb);
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
