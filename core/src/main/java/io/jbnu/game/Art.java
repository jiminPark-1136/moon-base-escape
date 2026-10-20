package io.jbnu.game;

import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.Animation;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.utils.Array;

/** [스냅샷 v0.2] 첫 CC0 스프라이트: drakzlin 병사 + Eris SF 타일 + 도시 배경. */
public class Art {
    public static final int SCALE = 2;
    private static final String TROOPER = "sprites/trooper/";

    public final Animation<TextureRegion> idle, run, jump, crouch;
    public final TextureRegion tileTop, tileBody;
    public final Texture background, flag;
    private final Array<Texture> textures = new Array<>();

    public Art() {
        idle   = strip(TROOPER + "spr_trooper_idle_strip4.png",    0, 2, 0.40f, Animation.PlayMode.LOOP);
        run    = strip(TROOPER + "spr_trooper_run_strip12.png",    0, 6, 0.09f, Animation.PlayMode.LOOP);
        jump   = strip(TROOPER + "spr_trooper_jump_strip8.png",    0, 4, 0.10f, Animation.PlayMode.NORMAL);
        crouch = strip(TROOPER + "spr_trooper_crouch_strip8.png",  0, 4, 0.05f, Animation.PlayMode.NORMAL);
        Texture tiles = load("tiles/scifi_platformTiles_32x32.png");
        tileTop = new TextureRegion(tiles, 32, 416, 32, 32);
        tileBody = new TextureRegion(tiles, 32, 480, 32, 32);
        background = load("backgrounds/scifi_platform_BG1.jpg");
        flag = load("flag.png");
    }

    private Texture load(String path) {
        Texture t = new Texture(path);
        textures.add(t);
        return t;
    }

    private Animation<TextureRegion> strip(String path, int first, int count, float frameTime, Animation.PlayMode mode) {
        Texture t = load(path);
        int size = t.getHeight();
        Array<TextureRegion> frames = new Array<>();
        for (int i = first; i < first + count; i++) frames.add(new TextureRegion(t, i * size, 0, size, size));
        return new Animation<>(frameTime, frames, mode);
    }

    public void dispose() {
        for (Texture t : textures) t.dispose();
    }
}
