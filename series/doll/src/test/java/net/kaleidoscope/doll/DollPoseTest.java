package net.kaleidoscope.doll;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class DollPoseTest {
    @Test void roundTripsIndependentAxes() {
        DollPose pose = DollPose.origin().adjust(1, 1).adjust(4, -1).adjust(8, 1);
        assertEquals(pose.encode(), DollPose.decode(pose.encode()).encode());
        assertEquals(1, pose.value(0));
        assertEquals(15, pose.value(8));
    }
    @Test void cannotCreateInvisibleOrUnboundedModels() {
        DollPose pose = DollPose.origin();
        for (int i = 0; i < 100; i++) pose = pose.adjust(0, -1).adjust(4, 1);
        assertEquals(0.1f, pose.value(0));
        assertEquals(2, pose.value(4));
        assertThrows(IllegalArgumentException.class, () -> DollPose.decode("NaN,1,1,0,0,0,0,0,0"));
        assertThrows(IllegalArgumentException.class, () -> DollPose.decode("1,1,1"));
    }
}
