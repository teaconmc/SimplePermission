package org.teacon.permission.repo;

import com.google.common.collect.ImmutableSet;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.reflect.TypeToken;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.world.GameType;

import javax.annotation.concurrent.ThreadSafe;
import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.stream.Stream;

@SuppressWarnings("NonAtomicOperationOnVolatileField")
@ThreadSafe
public final class UserDataRepo {

    private static final Gson GSON = new GsonBuilder().registerTypeAdapter(UserGroup.class, new UserGroupTypeAdapter()).setLenient().create();
    private static final Type USER_LIST_TYPE = new TypeToken<Map<UUID, String>>() {}.getType();
    private static final Type GROUP_LIST_TYPE = new TypeToken<Map<String, UserGroup>>() {}.getType();

    private final Map<String, UserGroup> groups = new ConcurrentHashMap<>();
    private final Map<UUID, String> users = new ConcurrentHashMap<>();
    private UserGroup fallbackGroup = new UserGroup();

    private final Path playerDataPath;
    private final Path groupDataPath;
    private final Path fallbackGroupDataPath;

    private volatile boolean loading = false;
    private volatile boolean saving = false;
    private volatile boolean dirty = false;

    public UserDataRepo(Path configRoot) throws IOException {
        playerDataPath = configRoot.resolve("player_data.dat");
        groupDataPath = configRoot.resolve("group_data.dat");
        fallbackGroupDataPath = configRoot.resolve("default_group.dat");
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

        if (Files.exists(playerDataPath)) {
            dirty = true;
            this.users.clear();
            this.users.putAll(GSON.fromJson(Files.newBufferedReader(playerDataPath, StandardCharsets.UTF_8), USER_LIST_TYPE));
        }

        if (Files.exists(groupDataPath)) {
            dirty = true;
            this.groups.clear();
            this.groups.putAll(GSON.fromJson(Files.newBufferedReader(groupDataPath, StandardCharsets.UTF_8), GROUP_LIST_TYPE));
        }

        if (Files.exists(fallbackGroupDataPath)) {
            dirty = true;
            String fallbackGroupName = new String(Files.readAllBytes(fallbackGroupDataPath), StandardCharsets.UTF_8);
            this.fallbackGroup = groups.getOrDefault(fallbackGroupName, new UserGroup());
        }

        // Initialize
        if (!Files.exists(playerDataPath) || !Files.exists(groupDataPath) || !Files.exists(fallbackGroupDataPath)) {
            save();
        }

        loading = false;
    }

    /**
     * Save data to the root directory.
     */
    public void save() throws IOException {
        if (saving) return;
        saving = true;
        Files.createDirectories(playerDataPath.getParent());
        Files.write(playerDataPath, GSON.toJson(this.users).getBytes(StandardCharsets.UTF_8));
        Files.write(groupDataPath, GSON.toJson(this.groups).getBytes(StandardCharsets.UTF_8));
        Files.write(fallbackGroupDataPath, this.fallbackGroup.name.getBytes(StandardCharsets.UTF_8));
        dirty = false;
        saving = false;
    }

    public boolean dirty() {
        return dirty;
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

    public Set<String> groups() {
        return ImmutableSet.copyOf(this.groups.keySet());
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

    public void grant(String group, String permission, boolean bool) {
        final UserGroup userGroup = this.groups.get(group);
        if (userGroup == null) return;
        dirty = true;
        userGroup.permissions.put(permission, bool);
    }

    public void revoke(String group, String permission) {
        final UserGroup userGroup = this.groups.get(group);
        if (userGroup == null) return;
        dirty |= userGroup.permissions.remove(permission);
    }

    public void addParent(String group, String parent) {
        final UserGroup userGroup = this.groups.get(group);
        if (group == null) return;
        dirty |= userGroup.parents.add(parent);
    }

    public void removeParent(String group, String parent) {
        final UserGroup userGroup = this.groups.get(group);
        if (userGroup == null) return;
        dirty |= userGroup.parents.removeIf(parent::equals);
    }

    public Stream<String> parentsOf(String group) {
        if (!groups.containsKey(group)) return Stream.empty();
        return groups.get(group).parents.stream();
    }

    public void createGroup(String name) {
        if (hasGroup(name)) return;
        UserGroup group = new UserGroup();
        group.name = name;
        group.parents = ConcurrentHashMap.newKeySet();
        group.permissions = new ConcurrentHashMap<>();
        groups.put(name, group);
        dirty = true;
    }

    public String getPrefix(String group) {
        return groups.getOrDefault(group, fallbackGroup).prefix.toString();
    }

    public void setPrefix(String group, String prefix) {
        if (hasGroup(group)) {
            groups.get(group).prefix = GSON.fromJson(prefix, JsonElement.class);
            dirty = true;
        }
    }

    public void setFallbackGroup(String groupName) {
        if (hasGroup(groupName)) {
            this.fallbackGroup = groups.get(groupName);
        }
    }

    public String getFallbackGroup() {
        return this.fallbackGroup.name;
    }

    public void setGameType(String group, GameType gameType) {
        if (hasGroup(group)) {
            groups.get(group).mode = gameType.getName();
        }
    }

    public String getGameType(String group) {
        return groups.getOrDefault(group, fallbackGroup).mode;
    }

    public Set<String> getPermissionNodes(String group) {
        if (hasGroup(group)) {
            return ImmutableSet.copyOf(groups.get(group).permissions.keySet());
        }
        return Collections.emptySet();
    }
}