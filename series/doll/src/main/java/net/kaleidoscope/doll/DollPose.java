package net.kaleidoscope.doll;

import java.util.Locale;

public final class DollPose {
    public static final int AXES = 9;
    private final float[] values;

    private DollPose(float[] values) {
        if (values.length != AXES) throw new IllegalArgumentException("Doll pose requires nine axes");
        this.values = values.clone();
        for (int i = 0; i < AXES; i++) {
            if (!Float.isFinite(values[i]) || values[i] < min(i) || values[i] > max(i)) throw new IllegalArgumentException("Invalid doll pose axis: " + i);
        }
    }

    public static DollPose origin() { return new DollPose(new float[]{1, 1, 1, 0, 0, 0, 0, 0, 0}); }
    public float value(int axis) { return values[axis]; }
    public DollPose adjust(int axis, int direction) {
        if (axis < 0 || axis >= AXES || (direction != 1 && direction != -1)) throw new IllegalArgumentException("Invalid pose adjustment");
        float[] next = values.clone();
        float step = axis < 3 ? 0.1f : axis < 6 ? 0.05f : 15f;
        next[axis] = Math.clamp(next[axis] + direction * step, min(axis), max(axis));
        return new DollPose(next);
    }
    public String vector(int first) { return String.format(Locale.ROOT, "%f,%f,%f", values[first], values[first + 1], values[first + 2]); }
    public String encode() {
        var text = new java.util.StringJoiner(",");
        for (float value : values) text.add(Float.toString(value));
        return text.toString();
    }
    public static DollPose decode(String text) {
        String[] parts = text.split(",", -1);
        if (parts.length != AXES) throw new IllegalArgumentException("Invalid saved doll pose length");
        float[] values = new float[AXES];
        for (int i = 0; i < AXES; i++) values[i] = Float.parseFloat(parts[i]);
        return new DollPose(values);
    }
    private static float min(int axis) { return axis < 3 ? 0.1f : axis < 6 ? -2 : -180; }
    private static float max(int axis) { return axis < 3 ? 4 : axis < 6 ? 2 : 180; }
}
