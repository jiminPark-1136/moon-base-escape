package io.jbnu.game;

import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.utils.Array;

/**
 * 크롤러: 바닥을 순찰하다 플레이어가 같은 높이 앞쪽에 보이면 돌진한다.
 * 이동·중력·충돌은 플레이어와 같은 Body(강의 12장 로직)를 쓴다 → 구덩이에 빠지기도 한다.
 */
public class Enemy {
    public static final float WIDTH = 60, HEIGHT = 44;
    private static final float PATROL_SPEED = 60, CHARGE_SPEED = 190;
    private static final float SIGHT_X = 320, SIGHT_Y = 80;
    public static final int MAX_HP = 3;

    public final Body body;
    public int hp = MAX_HP;
    public boolean facingRight;
    public boolean charging;
    /** 맞은 뒤 남은 깜빡임 시간 */
    public float hurtTimer;
    /** 피격 넉백 속도. 매 프레임 줄어든다. */
    private float knockback;

    public Enemy(float x, float y, boolean facingRight) {
        body = new Body(x, y, WIDTH, HEIGHT);
        this.facingRight = facingRight;
    }

    public void hit(int damage, float pushX) {
        hp -= damage;
        hurtTimer = 0.15f;
        knockback += pushX;
    }

    public boolean dead() {
        return hp <= 0;
    }

    public void update(float dt, float gravity, Array<Rectangle> blocks, Rectangle player) {
        hurtTimer = Math.max(0, hurtTimer - dt);

        // 시야: 같은 높이대, 바라보는 방향 앞쪽에 플레이어가 있으면 돌진
        Rectangle b = body.bounds;
        float dx = (player.x + player.width / 2) - (b.x + b.width / 2);
        float dy = player.y - b.y;
        boolean inFront = facingRight ? dx > 0 : dx < 0;
        charging = body.grounded && inFront && Math.abs(dx) < SIGHT_X && Math.abs(dy) < SIGHT_Y;

        float speed = charging ? CHARGE_SPEED : PATROL_SPEED;
        knockback -= knockback * Math.min(1f, 8f * dt);
        body.vx = (facingRight ? speed : -speed) + knockback;

        body.applyGravity(gravity, dt);
        boolean hitWall = body.moveX(dt, blocks, World.WORLD_WIDTH);
        body.moveY(dt, blocks);

        // 순찰 중에는 벽이나 발판 끝에서 돌아선다. 돌진 중에는 멈추지 않아 구덩이에 빠질 수 있다.
        if (hitWall || (!charging && body.grounded && !groundAhead(blocks))) facingRight = !facingRight;
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
