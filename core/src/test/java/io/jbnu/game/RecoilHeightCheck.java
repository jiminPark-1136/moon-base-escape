package io.jbnu.game;

import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.utils.Array;

/**
 * 반동 높이가 PLAN.md 표(높이 = v² / 2g)와 맞는지 Body의 실제 이동 코드로 확인한다.
 * 실행: java -ea -cp <core 클래스 + gdx jar> io.jbnu.game.RecoilHeightCheck
 */
public class RecoilHeightCheck {
    public static void main(String[] args) {
        float[][] cases = { // {반동 속도, 중력 배율, 기대 높이}
            {500, 1f, 74}, {500, 0.58f, 127}, {500, 0.16f, 460},
            {80, 1f, 2}, {80, 0.16f, 12},
        };
        Array<Rectangle> floor = new Array<>();
        floor.add(new Rectangle(0, -10, 100, 10));
        for (float[] c : cases) {
            Body body = new Body(0, 0, 32, 44);
            body.vy = c[0];
            float g = 1700 * c[1], peak = 0, dt = 1f / 60f;
            for (int i = 0; i < 600; i++) {
                body.applyGravity(g, dt);
                body.moveY(dt, floor);
                peak = Math.max(peak, body.bounds.y);
            }
            System.out.printf("recoil %.0f @ %.2fG -> peak %.1f (expected ~%.0f)%n", c[0], c[1], peak, c[2]);
            // 프레임 단위 적분이라 이론값보다 약간 낮게 나온다: 8% 또는 3px 이내면 통과
            assert Math.abs(peak - c[2]) <= Math.max(3, c[2] * 0.08f) : "recoil height off";
        }
        System.out.println("OK");
    }
}
