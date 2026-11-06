package net.kaleidoscope.deco;

import net.kaleidoscope.series.ItemIds;
import java.util.List;

public record DecoRule(List<String> inputs, boolean wand, int sneak, String target, List<String> sounds, boolean pending) {
    public DecoRule {
        inputs = List.copyOf(inputs); sounds = List.copyOf(sounds);
        if (sneak < -1 || sneak > 1 || target == null) throw new IllegalArgumentException("Invalid Deco rule");
        if (!target.isEmpty()) ItemIds.require(target);
        for (String input : inputs) if (!input.equals("*")) ItemIds.require(input.startsWith("#") ? input.substring(1) : input);
        sounds.forEach(ItemIds::require);
    }

    boolean matches(String held, boolean sneaking, java.util.function.Predicate<String> tag) {
        if (sneak != -1 && (sneak == 1) != sneaking) return false;
        if (wand && !held.equals("kaleidoscope_deco:state_tool")) return false;
        return inputs.isEmpty() || inputs.stream().anyMatch(input -> input.equals(held)
                || input.equals("*") && !held.equals("minecraft:air") || input.startsWith("#") && tag.test(input.substring(1)));
    }
}
