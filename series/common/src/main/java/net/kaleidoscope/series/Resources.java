package net.kaleidoscope.series;

import com.google.gson.Gson;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.InputStreamReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

public final class Resources {
    private Resources() {}

    public static <T> T read(JavaPlugin plugin, String path, Class<T> type) {
        try (var stream = Objects.requireNonNull(plugin.getResource(path), "Missing resource: " + path);
             var reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
            return Objects.requireNonNull(new Gson().fromJson(reader, type), "Empty resource: " + path);
        } catch (IOException exception) {
            throw new IllegalStateException("Cannot read " + path, exception);
        }
    }
}
