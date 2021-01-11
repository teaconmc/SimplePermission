package org.teacon.permission.repo;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;

import javax.annotation.concurrent.ThreadSafe;
import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.stream.Stream;

@ThreadSafe
public final class UserDataRepo {

    private static final Gson GSON = new GsonBuilder().setLenient().create();
    private static final Type USER_LIST_TYPE = new TypeToken<Map<UUID, String>>(){}.getType();
    private static final Type GROUP_LIST_TYPE = new TypeToken<Map<String, UserGroup>>(){}.getType();

    private final Map<String, UserGroup> groups = new ConcurrentHashMap<>();
    private final Map<UUID, String> users = new ConcurrentHashMap<>();
    private UserGroup fallbackGroup = new UserGroup();

    private final Path PLAYER_DATA;
    private final Path GROUP_DATA;
    private final Path FALLBACK_GROUP;

    private volatile boolean loading = false;
    private volatile boolean saving = false;
    private volatile boolean dirty = false;

    public UserDataRepo(Path configRoot) throws IOException {
        PLAYER_DATA = configRoot.resolve("player_data.dat");
        GROUP_DATA = configRoot.resolve("group_data.dat");
        FALLBACK_GROUP = configRoot.resolve("default_group.dat");
        load();
    }

    /**
     * Load data from the root directory. Will overwrite previously loaded
     * data.
     */
    public void load() throws IOException {
        if (loading) {
            return;
        }
        loading = true;

        if (Files.exists(PLAYER_DATA)) {
            dirty = true;
            this.users.clear();
            this.users.putAll(GSON.fromJson(Files.newBufferedReader(PLAYER_DATA, StandardCharsets.UTF_8), USER_LIST_TYPE));
        }

        if (Files.exists(GROUP_DATA)) {
            dirty = true;
            this.groups.clear();
            this.groups.putAll(GSON.fromJson(Files.newBufferedReader(GROUP_DATA, StandardCharsets.UTF_8), GROUP_LIST_TYPE));
        }

        if (Files.exists(FALLBACK_GROUP)) {
            dirty = true;
            this.fallbackGroup = GSON.fromJson(Files.newBufferedReader(FALLBACK_GROUP, StandardCharsets.UTF_8), UserGroup.class);
        }

        // Initialize
        if (!Files.exists(PLAYER_DATA) || !Files.exists(GROUP_DATA) || !Files.exists(FALLBACK_GROUP)) {
            save();
        }

        loading = false;
    }

    /**
     * Save data to the root directory.
     */
    public void save() throws IOException {
        if (saving) return;
        if (!dirty) return;
        saving = true;
        Files.write(PLAYER_DATA, GSON.toJson(this.users).getBytes(StandardCharsets.UTF_8));
        Files.write(GROUP_DATA, GSON.toJson(this.groups).getBytes(StandardCharsets.UTF_8));
        Files.write(FALLBACK_GROUP, GSON.toJson(this.fallbackGroup).getBytes(StandardCharsets.UTF_8));
        dirty = false;
        saving = false;
    }

    public boolean hasGroup(String group) {
        return this.groups.containsKey(group) || group.isEmpty();
    }

    public void assignUserToGroup(UUID id, String group) {
        dirty = true;
        if (group.isEmpty()) {
            this.users.remove(id);
        } else {
            this.users.put(id, group);
        }
    }

    public Stream<UUID> reverseLookup(String group) {
        return this.users.entrySet()
                .stream()
                .filter(e -> e.getValue().equals(group))
                .map(Map.Entry::getKey);
    }

    public Stream<String> groups() {
        return this.groups.keySet().stream();
    }

    public void initForFirstTime(UUID id, Consumer<UserGroup> callback) {
        dirty = true;
        if (!this.users.containsKey(id)) {
            this.users.put(id, this.fallbackGroup.name);
            callback.accept(this.fallbackGroup);
        }
    }

    public UserGroup lookup(UUID id) {
        String group;
        if ((group = this.users.get(id)) == null) { // Just in case.
            return this.fallbackGroup;
        } else {
            return this.groups.getOrDefault(group, this.fallbackGroup);
        }
    }

    public Boolean hasPermission(UUID id, String permission) {
        final UserGroup group = this.lookup(id);
        if (group.permissions.containsKey(permission)) {
            return group.permissions.get(permission);
        } else {
            return group.parents.stream()
                    .map(this.groups::get)
                    .filter(Objects::nonNull)
                    .map(g -> g.permissions)
                    .map(p -> p.get(permission))
                    .filter(Objects::nonNull)
                    .findFirst()
                    .orElse(null);
        }
    }

}