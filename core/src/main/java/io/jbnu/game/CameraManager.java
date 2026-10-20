package io.jbnu.game;

import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector2;

/**
 * 강의 10장 CameraManager: 흔들림(감쇠 + 랜덤 오프셋)에 추적(follow)을 더했다.
 * delta가 필요하므로 World가 아니라 Main이 가지고 있는다.
 */
public class CameraManager {
    /** 목표 위치를 따라가는 속도(1/초). 클수록 빠르게 붙는다. */
    private static final float FOLLOW_SPEED = 8f;

    private final Vector2 base = new Vector2(); // 흔들림을 뺀 카메라 위치
    private float shakeDuration, shakeTimer, shakeIntensity;

    /** 흔들림 시작. 이미 흔들리는 중이면 더 센 쪽을 남긴다 (연사 시에도 반응하도록 강의 코드에서 변경). */
    public void startShake(float duration, float intensity) {
        if (shakeTimer < shakeDuration && intensity < currentIntensity()) return;
        shakeDuration = duration;
        shakeTimer = 0;
        shakeIntensity = intensity;
    }

    private float currentIntensity() {
        if (shakeDuration <= 0) return 0;
        return shakeIntensity * (1f - shakeTimer / shakeDuration);
    }

    public void snapTo(OrthographicCamera camera, float x, float y, float worldW, float worldH) {
        base.set(clampX(camera, x, worldW), clampY(camera, y, worldH));
        camera.position.set(base, 0);
        camera.update();
    }

    public void update(OrthographicCamera camera, float targetX, float targetY, float worldW, float worldH, float delta) {
        // 추적: 강의의 X축 clamp에 Y축을 더하고, 순간이동 대신 부드럽게 따라간다
        float t = Math.min(1f, FOLLOW_SPEED * delta);
        base.x += (clampX(camera, targetX, worldW) - base.x) * t;
        base.y += (clampY(camera, targetY, worldH) - base.y) * t;

        float offsetX = 0, offsetY = 0;
        if (shakeTimer < shakeDuration) {
            shakeTimer += delta;
            float intensity = currentIntensity(); // 시간이 지날수록 감쇠
            offsetX = MathUtils.random(-1f, 1f) * intensity;
            offsetY = MathUtils.random(-1f, 1f) * intensity;
        }
        camera.position.set(base.x + offsetX, base.y + offsetY, 0);
        camera.update();
    }

    private static float clampX(OrthographicCamera camera, float x, float worldW) {
        float half = camera.viewportWidth / 2;
        return MathUtils.clamp(x, half, worldW - half);
    }

    private static float clampY(OrthographicCamera camera, float y, float worldH) {
        float half = camera.viewportHeight / 2;
        return MathUtils.clamp(y, half, worldH - half);
    }
}
