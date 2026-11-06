package net.kaleidoscope.doll;

import net.kaleidoscope.series.InteractiveFurniture;
import net.momirealms.craftengine.core.entity.furniture.Furniture;
import org.bukkit.entity.Player;

public final class ComputerController extends InteractiveFurniture {
    private final DollPlugin dolls;

    public ComputerController(Furniture furniture, DollPlugin plugin) { super(furniture, plugin); dolls = plugin; }
    @Override protected void interact(Player player) { dolls.openComputer(player, 0, this::accessible); }
}
