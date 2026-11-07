package net.kaleidoscope.cookery.catalog;

import java.util.List;

/** Immutable plated-dish state shared by furniture interactions and item recovery. */
public record PlateServing(BiteState bites, List<String> drops) {
    public PlateServing {
        if (bites == null || drops == null) throw new IllegalArgumentException("Plate state is incomplete");
        drops = List.copyOf(drops);
        drops.forEach(CatalogJson::requireId);
    }

    public static PlateServing of(CookeryCatalog.Entry entry, QualityProfile quality) {
        if (entry == null || entry.bites() == null) return null;
        return new PlateServing(BiteState.of(entry, quality), entry.bites().drops());
    }

    public boolean empty() { return bites.isEmpty(); }

    public boolean full() { return bites.isFull(); }

    public PlateServing takeOne() { return new PlateServing(bites.consumeOne(), drops); }

    public PlateServing refillOne() { return new PlateServing(bites.refillOne(), drops); }
}
