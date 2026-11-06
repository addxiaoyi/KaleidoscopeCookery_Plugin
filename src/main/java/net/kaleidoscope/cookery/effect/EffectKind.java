package net.kaleidoscope.cookery.effect;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

public enum EffectKind {
    FLATULENCE("胀气"), TUNDRA_STRIDER("冻原行者"), WARMTH("温暖"), SATIATED_SHIELD("饱腹代偿"),
    VIGOR("活力"), SULFUR("硫磺"), MUSTARD("芥末"), PRESERVATION("保鲜"), HINDER("阻碍"),
    PROJECTILE_DODGE("弹射物闪避"), INSTANT_SMELTING("即时冶炼"), VITALITY("生机");

    public static final Set<String> IDS = Arrays.stream(values()).map(EffectKind::id).collect(Collectors.toUnmodifiableSet());
    private final String title;
    EffectKind(String title) { this.title = title; }
    public String id() { return "kaleidoscope_cookery:" + name().toLowerCase(java.util.Locale.ROOT); }
    public String title() { return title; }
    public static EffectKind fromId(String id) {
        if (!IDS.contains(id)) throw new IllegalArgumentException("Unknown kitchen effect: " + id);
        return valueOf(id.substring(id.indexOf(':') + 1).toUpperCase(java.util.Locale.ROOT));
    }
}
