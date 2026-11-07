package net.kaleidoscope.cookery.item;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.kaleidoscope.cookery.catalog.PlateServing;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;

/** PDC bridge for a recovered plated dish, including its additional drops. */
public final class PlateServingStore {
    private static final NamespacedKey KEY = new NamespacedKey("kaleidoscopecookery", "plate_serving_v1");
    private static final Gson JSON = new Gson();

    private PlateServingStore() {}

    public static void write(ItemStack stack, PlateServing serving) {
        if (stack == null || serving == null) throw new IllegalArgumentException("Plate serving target is missing");
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) throw new IllegalArgumentException("Plate serving target has no item metadata");
        JsonObject root = JsonParser.parseString(JSON.toJson(serving)).getAsJsonObject();
        root.addProperty("drops_v1", String.join("\n", serving.drops()));
        meta.getPersistentDataContainer().set(KEY, PersistentDataType.STRING, root.toString());
        stack.setItemMeta(meta);
    }

    public static PlateServing read(ItemStack stack) {
        if (stack == null || !stack.hasItemMeta()) return null;
        String encoded = stack.getItemMeta().getPersistentDataContainer().get(KEY, PersistentDataType.STRING);
        if (encoded == null) return null;
        JsonObject root = JsonParser.parseString(encoded).getAsJsonObject();
        String drops = root.has("drops_v1") ? root.get("drops_v1").getAsString() : "";
        List<String> restored = new ArrayList<>();
        if (!drops.isEmpty()) for (String id : drops.split("\\n", -1)) restored.add(id);
        root.remove("drops_v1");
        var bites = net.kaleidoscope.cookery.catalog.BiteState.decode(root.getAsJsonObject("bites").toString());
        return new PlateServing(bites, restored);
    }

    public static void clear(ItemStack stack) {
        if (stack == null || !stack.hasItemMeta()) return;
        ItemMeta meta = stack.getItemMeta();
        meta.getPersistentDataContainer().remove(KEY);
        stack.setItemMeta(meta);
    }
}
