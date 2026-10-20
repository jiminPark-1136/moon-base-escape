package io.jbnu.game;

import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.utils.Array;

/**
 * 스테이지 설계 확인: 실제 Body 이동 코드로 스테이지별 점프 높이를 재서
 * "점프만으로는 못 넘고 샷건이 있으면 넘는다"는 장애물이 그대로 성립하는지 본다.
 * 실행: java -ea -cp <core 클래스 + gdx jar> io.jbnu.game.StageCheck
 */
public class StageCheck {
    private static final float DT = 1f / 60f, SHOTGUN = Weapon.SHOTGUN.recoil;

    public static void main(String[] args) {
        //            스테이지, 샷건이 필요한 높이들
        check(0, new float[] {160});            // 1: 샷건 벽
        check(1, new float[] {});               // 2: 샷건 필수 장애물 없음 (감압 복도는 앉아서 기어 통과)
        check(2, new float[] {600, 400, 300});  // 3: 고원, 메사
        System.out.println("OK");
    }

    private static void check(int index, float[] shotgunWalls) {
        Stage st = Stage.get(index);
        float g = 1700 * st.gravityScale;
        float jump = peak(g, (float) Math.sqrt(2 * g * st.jumpHeight), false);
        float boosted = peak(g, (float) Math.sqrt(2 * g * st.jumpHeight), true);
        System.out.printf("stage %d (%.2fG): jump %.0f, jump+shotgun %.0f%n", index + 1, st.gravityScale, jump, boosted);
        for (float h : shotgunWalls) {
            assert jump < h : "stage " + (index + 1) + ": height " + h + " is reachable without shotgun";
            assert boosted >= h + 10 : "stage " + (index + 1) + ": height " + h + " not reachable even with shotgun";
        }
    }

    /** 점프해서 가장 높이 올라간 높이. shotgun이면 꼭대기에서 아래로 샷건을 한 번 쏜다 (World.fire와 같은 max 처리). */
    private static float peak(float g, float jumpSpeed, boolean shotgun) {
        Array<Rectangle> floor = new Array<>();
        floor.add(new Rectangle(-100, -10, 200, 10));
        Body b = new Body(0, 0, 36, 60);
        b.vy = jumpSpeed;
        boolean fired = false;
        float top = 0;
        for (int i = 0; i < 1200; i++) {
            if (shotgun && !fired && b.vy <= 0) {
                b.vy = Math.max(b.vy, SHOTGUN);
                fired = true;
            }
            b.applyGravity(g, DT);
            b.moveY(DT, floor);
            top = Math.max(top, b.bounds.y);
        }
        return top;
    }
}
