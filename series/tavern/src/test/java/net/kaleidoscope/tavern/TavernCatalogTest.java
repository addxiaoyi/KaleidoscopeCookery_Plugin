package net.kaleidoscope.tavern;

import com.google.gson.Gson;
import org.junit.jupiter.api.Test;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;

class TavernCatalogTest {
    @Test void packagedCatalogHasAllRecipeFamiliesAndEffectTags() throws Exception {
        try (var stream = getClass().getResourceAsStream("/catalog.json")) {
            assertNotNull(stream);
            TavernCatalog catalog = new Gson().fromJson(new InputStreamReader(stream, StandardCharsets.UTF_8), TavernCatalog.class);
            assertEquals(24, catalog.barrel().size());
            assertEquals(6, catalog.pressing().size());
            assertEquals(12, catalog.shaker().size());
            assertEquals(Set.of("ardent_heat_breakable", "grass_stealth_plants", "tomb_raider_disarmable"), catalog.effectTags().keySet());
            // Molotov is a throwable barrel output; upstream deliberately has no drink-effect table for it.
            assertEquals(java.util.List.of("kaleidoscope_tavern:molotov"), catalog.barrel().stream()
                    .map(BrewRecipe::output).filter(output -> !catalog.effects().containsKey(output)).toList());
            assertTrue(catalog.shaker().stream().allMatch(recipe -> catalog.effects().containsKey(recipe.output())));
        }
    }
}
