package io.jbnu.game;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
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

    public final Body player = new Body(START_X, START_Y, PLAYER_W, STAND_H);
    public final Array<Rectangle> blocks = new Array<>();
    /** 출구. 닿으면 스테이지 클리어. */
    public final Rectangle flag = new Rectangle(3700, 64, 48, 56);
    public final Array<Bullet> bullets = new Array<>();

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
        cleared = false;
    }

    public void update(float dt, float aimWorldX, float aimWorldY) {
        firedThisStep = null;
        if (cleared || dt <= 0) return;
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
        player.vx = inputVx + kickX;
        if (player.moveX(dt, blocks, WORLD_WIDTH)) kickX = 0;
        player.moveY(dt, blocks);

        updateBullets(dt);

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

    public float muzzleX() {
        return player.bounds.x + player.bounds.width / 2;
    }

    public float muzzleY() {
        return player.bounds.y + player.bounds.height * 0.65f;
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

    // ponytail: 총알은 한 스텝에 최대 약 47px 이동(1400 × 1/30)이라 24px 두께 발판을 뚫을 수 있다.
    // 얇은 벽이나 창문을 맞혀야 할 때 스텝을 나누거나 선분-사각형 교차 검사로 바꾼다.
    private void updateBullets(float dt) {
        for (int i = bullets.size - 1; i >= 0; i--) {
            Bullet b = bullets.get(i);
            b.bounds.x += b.vx * dt;
            b.bounds.y += b.vy * dt;
            b.life -= dt;
            boolean dead = b.life <= 0;
            for (Rectangle block : blocks) {
                if (b.bounds.overlaps(block)) {
                    dead = true;
                    break;
                }
            }
            if (dead) bullets.removeIndex(i);
        }
    }
}
