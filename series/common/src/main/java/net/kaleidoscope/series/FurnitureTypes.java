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

public final class FurnitureTypes {
    private FurnitureTypes() {}

    public static void register(String name, Function<Furniture, FurnitureController> controller) {
        Key id = Key.of(ItemIds.require(name));
        FurnitureBehaviorType<FurnitureBehaviorTemplate> type = new FurnitureBehaviorType<>(id,
                (definition, section) -> new FurnitureBehaviorTemplate(definition) {
                    @Override public FurnitureController createController(Furniture furniture) {
                        return controller.apply(furniture);
                    }
                });
        ((WritableRegistry<FurnitureBehaviorType<? extends FurnitureBehaviorTemplate>>)
                BuiltInRegistries.FURNITURE_BEHAVIOR_TYPE)
                .register(ResourceKey.create(Registries.FURNITURE_BEHAVIOR_TYPE.location(), id), type);
    }
}
