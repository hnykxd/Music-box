package dev.musicbox;

import com.google.gson.*;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.HashMap;
import java.util.Map;

/** One store per running world; all access is on the server thread. */
public final class SettingsStore {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private final Path file;
    private final Map<String, NoteSettings> notes = new HashMap<>();
    private boolean dirty;
    private boolean writable = true;

    public SettingsStore(Path file) {
        this.file = file;
        if (!Files.exists(file)) return;
        try (var reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
            if (root.get("version").getAsInt() != 1) throw new IOException("Unsupported musicbox data version");
            for (var entry : root.getAsJsonObject("notes").entrySet()) {
                JsonObject n = entry.getValue().getAsJsonObject();
                notes.put(entry.getKey(), new NoteSettings(n.get("instrument").getAsInt(), n.get("note").getAsInt()));
            }
        } catch (Exception e) {
            notes.clear();
            writable = false; // Never overwrite unreadable world data with an empty map.
            MusicBox.LOGGER.error("Cannot read {}. Music box editing disabled; original file preserved.", file, e);
        }
    }

    public NoteSettings get(String dimension, long pos) { return notes.get(key(dimension, pos)); }
    public void put(String dimension, long pos, NoteSettings settings) throws IOException {
        if (!writable) throw new IOException("World music box data is unreadable");
        String key = key(dimension, pos);
        NoteSettings previous = notes.put(key, settings);
        dirty = true;
        try { flush(); }
        catch (IOException e) {
            if (previous == null) notes.remove(key); else notes.put(key, previous);
            throw e;
        }
    }
    public void remove(String dimension, long pos) {
        if (notes.remove(key(dimension, pos)) != null) dirty = true;
    }
    public void flush() throws IOException {
        if (!dirty || !writable) return;
        Files.createDirectories(file.getParent());
        JsonObject root = new JsonObject();
        root.addProperty("version", 1);
        root.add("notes", GSON.toJsonTree(notes));
        Path temp = file.resolveSibling(file.getFileName() + ".tmp");
        Files.writeString(temp, GSON.toJson(root), StandardCharsets.UTF_8);
        try { Files.move(temp, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); }
        catch (AtomicMoveNotSupportedException e) { Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING); }
        dirty = false;
    }
    private static String key(String dimension, long pos) { return dimension + "/" + pos; }
}
