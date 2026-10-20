package io.jbnu.game;

import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.utils.Array;

/**
 * 적 3종. 이동·중력·충돌은 플레이어와 같은 Body(강의 12장 로직)를 쓴다.
 * - 크롤러: 바닥을 순찰하다 플레이어가 같은 높이 앞쪽에 보이면 돌진 → 구덩이에 빠지기도 한다
 * - 드로퍼: 천장에 거꾸로 매달려 있다가 플레이어가 아래로 오면 떨어진다 (낙하 속도 = 스테이지 중력).
 *   착지한 뒤에는 크롤러처럼 움직인다
 * - 스피터: 공중에 떠서 산성탄을 포물선으로 쏜다. 탄도를 스테이지 중력으로 계산해 플레이어 위치에 떨어뜨린다
 */
public class Enemy {
    public enum Type { CRAWLER, DROPPER, SPITTER }

    public static final float WIDTH = 60, HEIGHT = 44;
    private static final float PATROL_SPEED = 60, CHARGE_SPEED = 190;
    private static final float SIGHT_X = 320, SIGHT_Y = 80;
    /** 드로퍼: 플레이어가 이 수평 거리 안, 아래쪽에 오면 떨어진다 */
    private static final float DROP_RANGE = 70;
    /** 스피터: 사거리, 발사 간격(초), 떠 있는 높이 흔들림 */
    private static final float SPIT_RANGE = 650, SPIT_INTERVAL = 2.2f, BOB = 10;
    public static final int MAX_HP = 3;

    public final Type type;
    public final Body body;
    public int hp;
    public boolean facingRight;
    public boolean charging;
    /** 드로퍼가 아직 천장에 매달려 있는지 */
    public boolean hanging;
    /** 맞은 뒤 남은 깜빡임 시간 */
    public float hurtTimer;
    /** 스피터: 이번 스텝에 쏠 차례면 true (World가 산성탄을 만들고 false로 돌린다) */
    public boolean wantsToSpit;
    /** 피격 넉백 속도. 매 프레임 줄어든다. */
    private float knockback;
    private float spitTimer, bobTime;
    private final float baseY;

    public Enemy(float x, float y, boolean facingRight) {
        this(x, y, facingRight, Type.CRAWLER);
    }

    public Enemy(float x, float y, boolean facingRight, Type type) {
        this.type = type;
        body = type == Type.SPITTER ? new Body(x, y, 60, 48) : new Body(x, y, WIDTH, HEIGHT);
        this.facingRight = facingRight;
        hp = type == Type.CRAWLER ? MAX_HP : 2;
        hanging = type == Type.DROPPER;
        baseY = y;
        spitTimer = SPIT_INTERVAL * MathUtils.random(0.3f, 1f); // 여러 마리가 동시에 쏘지 않게
    }

    public void hit(int damage, float pushX) {
        hp -= damage;
        hurtTimer = 0.15f;
        knockback += pushX;
        if (hanging) hanging = false; // 맞으면 천장에서 떨어진다
    }

    /** 흡입력 등 외부 힘으로 수평 속도를 더한다 (매 프레임 줄어듦). 매달린 드로퍼와 떠 있는 스피터는 버틴다. */
    public void pull(float dvx) {
        if (hanging || type == Type.SPITTER) return;
        knockback += dvx;
    }

    public boolean dead() {
        return hp <= 0;
    }

    public void update(float dt, float gravity, Array<Rectangle> blocks, Rectangle player, float worldWidth) {
        hurtTimer = Math.max(0, hurtTimer - dt);
        Rectangle b = body.bounds;
        float dx = (player.x + player.width / 2) - (b.x + b.width / 2);

        if (type == Type.SPITTER) {
            facingRight = dx > 0;
            bobTime += dt;
            b.y = baseY + MathUtils.sin(bobTime * 2f) * BOB;
            spitTimer -= dt;
            if (spitTimer <= 0 && Math.abs(dx) < SPIT_RANGE) {
                wantsToSpit = true;
                spitTimer = SPIT_INTERVAL;
            }
            return;
        }
        if (hanging) {
            if (Math.abs(dx) < DROP_RANGE && player.y < b.y) hanging = false; // 플레이어가 바로 아래로 왔다
            return;
        }

        // 시야: 같은 높이대, 바라보는 방향 앞쪽에 플레이어가 있으면 돌진
        float dy = player.y - b.y;
        boolean inFront = facingRight ? dx > 0 : dx < 0;
        charging = body.grounded && inFront && Math.abs(dx) < SIGHT_X && Math.abs(dy) < SIGHT_Y;

        float speed = charging ? CHARGE_SPEED : PATROL_SPEED;
        knockback -= knockback * Math.min(1f, 8f * dt);
        body.vx = body.grounded ? (facingRight ? speed : -speed) + knockback : knockback; // 떨어지는 동안은 걷지 않는다

        body.applyGravity(gravity, dt);
        boolean hitWall = body.moveX(dt, blocks, worldWidth);
        body.moveY(dt, blocks);

        // 순찰 중에는 벽이나 발판 끝에서 돌아선다. 돌진 중에는 멈추지 않아 구덩이에 빠질 수 있다.
        if (hitWall || (!charging && body.grounded && !groundAhead(blocks))) facingRight = !facingRight;
    }

    /**
     * 포물선 탄도: (fromX, fromY)에서 쏴서 (toX, toY)에 떨어지는 초기 속도 {vx, vy}.
     * 비행시간 T를 거리로 정하고, 등가속도 운동 y = vy·T − ½gT² 를 vy에 대해 푼다.
     * → 중력이 약하면 같은 거리라도 낮고 느리게 쏴서 큰 포물선이 된다.
     */
    public static float[] lob(float fromX, float fromY, float toX, float toY, float gravity) {
        float dx = toX - fromX, dy = toY - fromY;
        float t = MathUtils.clamp(Math.abs(dx) / 350f, 0.6f, 1.8f);
        return new float[] {dx / t, (dy + 0.5f * gravity * t * t) / t};
    }

    /** 바라보는 방향 발 앞 바로 아래에 블록이 있는지. */
    private boolean groundAhead(Array<Rectangle> blocks) {
        Rectangle b = body.bounds;
        float probeX = facingRight ? b.x + b.width + 2 : b.x - 6;
        Rectangle probe = new Rectangle(probeX, b.y - 6, 4, 4);
        for (Rectangle block : blocks) {
            if (probe.overlaps(block)) return true;
        }
        return false;
    }
}
