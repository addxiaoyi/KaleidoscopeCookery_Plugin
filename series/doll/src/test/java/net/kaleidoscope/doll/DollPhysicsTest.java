package net.kaleidoscope.doll;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class DollPhysicsTest {
    @Test void chargeRequiresFiveTicksAndCapsAtThreeBlocksPerTick() {
        assertEquals(0, DollPhysics.throwPower(4));
        assertEquals(0.1, DollPhysics.throwPower(5));
        assertEquals(1.5, DollPhysics.throwPower(20));
        assertEquals(3, DollPhysics.throwPower(Integer.MAX_VALUE));
    }
    @Test void bounceLosesEnergyAndSettlesAtSmallSpeeds() {
        assertEquals(0.5, DollPhysics.bounce(-1));
        assertEquals(0, DollPhysics.bounce(-0.1));
        assertEquals(0, DollPhysics.bounce(0.5));
        assertEquals(1.5, DollPhysics.bounce(-10));
        assertThrows(IllegalArgumentException.class, () -> DollPhysics.bounce(Double.NaN));
    }
    @Test void largerDollsHaveWeakerKnockback() {
        assertEquals(0.4, DollPhysics.knockback(1, 0.75, 1));
        assertEquals(0.1, DollPhysics.knockback(1, 1.5, 1), 0.00001);
        assertThrows(IllegalArgumentException.class, () -> DollPhysics.knockback(1, 0, 1));
    }
}
