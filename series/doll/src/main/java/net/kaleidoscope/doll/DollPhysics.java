package net.kaleidoscope.doll;

final class DollPhysics {
    private DollPhysics() {}
    static double throwPower(int ticks) { return ticks < 5 ? 0 : Math.clamp((ticks - 5) * 0.1, 0.1, 3); }
    static double bounce(double fallingSpeed) {
        if (!Double.isFinite(fallingSpeed)) throw new IllegalArgumentException("Invalid doll velocity");
        return fallingSpeed < -0.12 ? Math.min(1.5, -fallingSpeed * 0.5) : 0;
    }
    static double knockback(double speed, double size, double force) {
        if (!Double.isFinite(speed + size + force) || size <= 0 || speed < 0 || force < 0) throw new IllegalArgumentException("Invalid knockback");
        return Math.min(speed * 0.4, 1) * Math.min(Math.pow(0.75 / size, 2), 1) * force;
    }
}
