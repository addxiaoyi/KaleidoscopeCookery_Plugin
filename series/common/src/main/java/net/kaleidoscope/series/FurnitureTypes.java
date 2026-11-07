package net.kaleidoscope.series;

import net.momirealms.craftengine.core.entity.furniture.Furniture;
import net.momirealms.craftengine.core.entity.furniture.behavior.FurnitureBehaviorTemplate;
import net.momirealms.craftengine.core.entity.furniture.behavior.FurnitureBehaviorType;
import net.momirealms.craftengine.core.entity.furniture.behavior.FurnitureController;
import net.momirealms.craftengine.core.registry.BuiltInRegistries;
import net.momirealms.craftengine.core.registry.Registries;
import net.momirealms.craftengine.core.registry.WritableRegistry;
import net.momirealms.craftengine.core.util.Key;
import net.momirealms.craftengine.core.util.ResourceKey;

import java.util.function.Function;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public final class FurnitureTypes {
    private static final Set<Key> REGISTERED = ConcurrentHashMap.newKeySet();
    private FurnitureTypes() {}

    public static boolean register(String name, Function<Furniture, FurnitureController> controller) {
        Key id = Key.of(ItemIds.require(name));
        if (!REGISTERED.add(id)) return false;
        FurnitureBehaviorType<FurnitureBehaviorTemplate> type = new FurnitureBehaviorType<>(id,
                (definition, section) -> new FurnitureBehaviorTemplate(definition) {
                    @Override public FurnitureController createController(Furniture furniture) {
                        return controller.apply(furniture);
                    }
                });
        try {
            ((WritableRegistry<FurnitureBehaviorType<? extends FurnitureBehaviorTemplate>>)
                    BuiltInRegistries.FURNITURE_BEHAVIOR_TYPE)
                    .register(ResourceKey.create(Registries.FURNITURE_BEHAVIOR_TYPE.location(), id), type);
            return true;
        } catch (RuntimeException | LinkageError failure) {
            REGISTERED.remove(id);
            throw failure;
        }
    }
}
