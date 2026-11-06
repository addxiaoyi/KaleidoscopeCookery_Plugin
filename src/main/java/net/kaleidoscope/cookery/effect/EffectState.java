package net.kaleidoscope.cookery.effect;

import net.kaleidoscope.cookery.catalog.CatalogJson;

public record EffectState(String id, int level, int remaining, String source, EffectState hidden) {
    public EffectState {
        if (!EffectKind.IDS.contains(id) || level < 0 || level > 255 || remaining < 1 || remaining > 72_000_000) {
            throw new IllegalArgumentException("Invalid kitchen effect: " + id);
        }
        CatalogJson.requireId(source);
        if (hidden != null && (!hidden.id.equals(id) || hidden.depth() >= 8)) {
            throw new IllegalArgumentException("Invalid hidden effect chain: " + id);
        }
    }

    private int depth() { return 1 + (hidden == null ? 0 : hidden.depth()); }

    public EffectState merge(EffectState incoming) {
        if (!id.equals(incoming.id)) throw new IllegalArgumentException("Cannot merge different effects");
        if (incoming.level > level) {
            return new EffectState(id, incoming.level, incoming.remaining, incoming.source,
                    incoming.remaining < remaining ? this : hidden);
        }
        if (incoming.level == level) {
            return incoming.remaining > remaining
                    ? new EffectState(id, level, incoming.remaining, incoming.source, hidden) : this;
        }
        if (incoming.remaining <= remaining) return this;
        return new EffectState(id, level, remaining, source, hidden == null ? incoming : hidden.merge(incoming));
    }

    public EffectState elapse(int ticks) {
        if (ticks < 0) throw new IllegalArgumentException("Negative effect time");
        EffectState nextHidden = hidden == null ? null : hidden.elapse(ticks);
        return remaining <= ticks ? nextHidden : new EffectState(id, level, remaining - ticks, source, nextHidden);
    }
}
