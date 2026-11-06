package net.kaleidoscope.cookery.nms;

/** Weather and water-drip checks sampled on the bamboo tray's owning region thread. */
public record BambooTrayWeather(boolean wetting, boolean raining, boolean canSeeSky) {}
