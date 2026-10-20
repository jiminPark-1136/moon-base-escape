package io.jbnu.game;

import com.badlogic.gdx.math.Rectangle;

/**
 * 감압 창문. 맞은 탄 1발당 내구도 1 감소 → 권총(1발)은 10번, 샷건(펠릿 5발)은 2번에 깨진다.
 * 깨지면 room 전체가 진공이 되어 창문 쪽으로 빨아들이고 산소를 빠르게 뺀다.
 */
public class Window {
    public static final int MAX_HP = 10;

    /** 뒷벽에 붙은 유리 (총알이 지나가면 맞는다, 몸은 통과) */
    public final Rectangle glass;
    /** 창문이 깨지면 진공이 되는 방 */
    public final Rectangle room;
    public int hp = MAX_HP;

    public Window(Rectangle glass, Rectangle room) {
        this.glass = glass;
        this.room = room;
    }

    public boolean broken() {
        return hp <= 0;
    }

    /** 이번 타격으로 깨졌으면 true. */
    public boolean hit(int damage) {
        if (broken()) return false;
        hp = Math.max(0, hp - damage);
        return broken();
    }

    /** 0 = 멀쩡, 1~3 = 균열 단계, 4 = 깨짐 */
    public int crackLevel() {
        if (broken()) return 4;
        return (int) Math.ceil((MAX_HP - hp) * 3f / MAX_HP);
    }

    public float centerX() {
        return glass.x + glass.width / 2;
    }
}
