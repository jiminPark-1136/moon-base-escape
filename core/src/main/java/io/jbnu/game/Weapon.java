package io.jbnu.game;

/**
 * 무기 데이터. 반동(recoil)은 발사 순간 플레이어에게 주는 속도 변화(월드 단위/초)이다.
 * 같은 반동이라도 도달 높이는 v² / 2g 이므로 중력이 약할수록 저절로 높이 뜬다.
 */
public enum Weapon {
    //        이름       데미지 펠릿 퍼짐(°) 반동  연사(초) 탄창 재장전(초) 어깨~총구(월드 단위, Art의 팔 조각 길이 × 2)
    PISTOL("Pistol",   1,    1,   2f,    80f, 0.20f, 12,  1.0f,      36f),
    SHOTGUN("Shotgun", 1,    5,  15f,   500f, 0.80f,  4,  1.5f,      48f);

    public final String label;
    public final int damage;
    public final int pellets;
    public final float spreadDeg;
    public final float recoil;
    public final float cooldown;
    public final int magazine;
    public final float reloadTime;
    public final float barrel;

    Weapon(String label, int damage, int pellets, float spreadDeg, float recoil,
           float cooldown, int magazine, float reloadTime, float barrel) {
        this.barrel = barrel;
        this.label = label;
        this.damage = damage;
        this.pellets = pellets;
        this.spreadDeg = spreadDeg;
        this.recoil = recoil;
        this.cooldown = cooldown;
        this.magazine = magazine;
        this.reloadTime = reloadTime;
    }
}
