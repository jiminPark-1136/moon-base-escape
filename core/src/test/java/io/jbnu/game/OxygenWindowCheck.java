package io.jbnu.game;

import com.badlogic.gdx.math.Rectangle;

/**
 * 산소 소모 규칙과 창문 내구도 확인.
 * 실행: java -ea -cp <core 클래스 + gdx jar> io.jbnu.game.OxygenWindowCheck
 */
public class OxygenWindowCheck {
    public static void main(String[] args) {
        // 산소: 기지 안에서는 누출이 있어도 줄지 않고, 진공에서만 기본 소모 + 누출 단계당 3
        assert World.oxygenDrain(false, false, 3) == 0 : "no drain inside the base";
        assert World.oxygenDrain(true, false, 0) == 2 : "moon surface base drain";
        assert World.oxygenDrain(false, true, 0) == 5 : "broken room drain";
        assert World.oxygenDrain(false, true, 2) == 11 : "broken room + leak x2";
        System.out.println("산소 소모/초: 기지 0, 달 표면 2, 깨진 방 5, 깨진 방+누출2 11");

        // 창문: 권총(1발)은 10번째에 깨지고, 샷건(펠릿 5발)은 2번째 발사에 깨진다
        Window byPistol = new Window(new Rectangle(0, 0, 128, 96), new Rectangle(0, 0, 300, 600));
        int shots = 0;
        while (!byPistol.broken()) {
            byPistol.hit(Weapon.PISTOL.damage * Weapon.PISTOL.pellets);
            shots++;
        }
        assert shots == 10 : "pistol shots to break: " + shots;

        Window byShotgun = new Window(new Rectangle(0, 0, 128, 96), new Rectangle(0, 0, 300, 600));
        int volleys = 0;
        while (!byShotgun.broken()) {
            for (int p = 0; p < Weapon.SHOTGUN.pellets; p++) byShotgun.hit(Weapon.SHOTGUN.damage);
            volleys++;
        }
        assert volleys == 2 : "shotgun volleys to break: " + volleys;
        System.out.println("창문 파괴: 권총 " + shots + "발, 샷건 " + volleys + "발 (펠릿 전부 명중 기준)");

        // 균열 단계: 멀쩡 0 → 1~3 → 깨짐 4
        Window w = new Window(new Rectangle(), new Rectangle());
        assert w.crackLevel() == 0;
        w.hp = 9;
        assert w.crackLevel() == 1;
        w.hp = 1;
        assert w.crackLevel() == 3;
        w.hp = 0;
        assert w.crackLevel() == 4;
        System.out.println("균열 단계 0 → 1 → 3 → 4(깨짐)");
        System.out.println("OK");
    }
}
