package io.jbnu.game;

import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.utils.Array;

/**
 * 중력과 블록 충돌을 받는 사각형 하나. 강의 12장 Platformer Final의 moveX / moveY를 그대로 옮겼다.
 * 플레이어와 앞으로 추가할 적이 같은 코드를 공유한다.
 */
public class Body {
    public static final float MAX_FALL_SPEED = 900;

    public final Rectangle bounds;
    public float vx, vy;
    public boolean grounded;

    public Body(float x, float y, float width, float height) {
        bounds = new Rectangle(x, y, width, height);
    }

    /** 중력: 아래쪽 속도는 음수이며 최대 낙하 속도를 제한한다. (강의 그대로, 상수 GRAVITY만 인자로 바뀜) */
    public void applyGravity(float gravity, float dt) {
        vy = Math.max(vy - gravity * dt, -MAX_FALL_SPEED);
    }

    /** X축 이동 후 겹친 블록의 옆면에 맞춘다. 벽에 막혔으면 true. */
    public boolean moveX(float dt, Array<Rectangle> blocks, float worldWidth) {
        boolean hitWall = false;
        bounds.x += vx * dt;
        for (Rectangle block : blocks) {
            if (!bounds.overlaps(block)) continue;
            if (vx > 0) bounds.x = block.x - bounds.width;
            else if (vx < 0) bounds.x = block.x + block.width;
            hitWall = true;
        }
        float clamped = Math.max(0, Math.min(bounds.x, worldWidth - bounds.width));
        if (clamped != bounds.x) hitWall = true;
        bounds.x = clamped;
        return hitWall;
    }

    /** Y축 이동 후 착지(dy < 0) 또는 머리 충돌(dy > 0)을 처리한다. grounded는 매 프레임 다시 계산한다. */
    public void moveY(float dt, Array<Rectangle> blocks) {
        float dy = vy * dt;
        bounds.y += dy;
        grounded = false;

        for (Rectangle block : blocks) {
            if (!bounds.overlaps(block)) continue;
            if (dy < 0) { // 내려오던 중: 블록 위에 착지
                bounds.y = block.y + block.height;
                vy = 0;
                grounded = true;
            } else if (dy > 0) { // 올라가던 중: 블록 아래에 머리 충돌
                bounds.y = block.y - bounds.height;
                vy = 0;
            }
        }
    }
}
