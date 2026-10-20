package io.jbnu.game;

import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.utils.Array;

/**
 * 크롤러 행동 확인: ① 순찰 중 발판 끝에서 돌아선다 ② 앞에 플레이어가 보이면 돌진한다
 * ③ 돌진 중에는 멈추지 않아 구덩이에 빠진다 ④ 3발 맞으면 죽는다.
 * 실행: java -ea -cp <core 클래스 + gdx jar> io.jbnu.game.EnemyCheck
 */
public class EnemyCheck {
    private static final float DT = 1f / 60f, G = 1700;

    public static void main(String[] args) {
        Array<Rectangle> floor = new Array<>();
        floor.add(new Rectangle(0, 0, 400, 64)); // x 400부터 구덩이
        Rectangle farPlayer = new Rectangle(-5000, 64, 36, 60);

        // ① 오른쪽으로 순찰하다 발판 끝(400)에서 돌아서고 떨어지지 않는다
        Enemy patrol = new Enemy(300, 64, true);
        boolean turned = false;
        for (int i = 0; i < 600; i++) {
            patrol.update(DT, G, floor, farPlayer, 10000);
            if (!patrol.facingRight) turned = true;
        }
        assert turned : "patrol should turn at ledge";
        assert patrol.body.bounds.y >= 63 : "patrol should not fall: y=" + patrol.body.bounds.y;
        System.out.println("① 순찰: 발판 끝에서 돌아섬, y=" + patrol.body.bounds.y);

        // ② ③ 구덩이 건너편 같은 높이에 플레이어 → 돌진, 멈추지 않고 달려 구덩이에 떨어진다
        //    (플레이어를 지나치면 '앞에 있음' 조건이 풀려 순찰로 돌아가므로, 건너편에 둔다)
        Enemy charger = new Enemy(200, 64, true);
        Rectangle player = new Rectangle(480, 64, 36, 60);
        charger.update(DT, G, floor, player, 10000);
        charger.update(DT, G, floor, player, 10000);
        assert charger.charging : "should charge when player in front";
        for (int i = 0; i < 300; i++) charger.update(DT, G, floor, player, 10000);
        assert charger.body.bounds.y < 0 : "charging enemy should fall into the pit: y=" + charger.body.bounds.y;
        System.out.println("②③ 돌진 후 구덩이 낙하, y=" + charger.body.bounds.y);

        // ④ 체력 3: 두 발은 버티고 세 발째 죽는다
        Enemy target = new Enemy(100, 64, true);
        target.hit(1, 0);
        target.hit(1, 0);
        assert !target.dead();
        target.hit(1, 0);
        assert target.dead();
        System.out.println("④ 3발에 사망");
        System.out.println("OK");
    }
}
