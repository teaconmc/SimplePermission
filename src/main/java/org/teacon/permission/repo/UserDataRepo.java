package org.teacon.permission.repo;

import com.google.common.collect.ImmutableSet;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.mojang.authlib.GameProfile;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.world.GameType;

import javax.annotation.Nullable;
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
    private static final Type DEFAULT_GROUPS_TYPES = new TypeToken<Map<Integer, String>>() {}.getType();

    private final Map<String, UserGroup> groups = new ConcurrentHashMap<>();
    private final Map<UUID, String> users = new ConcurrentHashMap<>();
    private final Map<Integer, String> fallbackGroups = new TreeMap<>(Collections.singletonMap(0, ""));

    private final Path playerDataPath;
    private final Path groupDataPath;
    private final Path fallbackGroupDataPaths;

    private final Path legacyFallbackGroupDataPath;

    private final MinecraftServer server;

    private volatile boolean loading = false;
    private volatile boolean saving = false;
    private volatile boolean dirty = false;

    public UserDataRepo(MinecraftServer server, Path configRoot) throws IOException {
        playerDataPath = configRoot.resolve("player_data.dat");
        groupDataPath = configRoot.resolve("group_data.dat");
        legacyFallbackGroupDataPath = configRoot.resolve("default_group.dat");
        fallbackGroupDataPaths = configRoot.resolve("default_groups.dat");
        this.server = server;
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

        if (Files.exists(legacyFallbackGroupDataPath)) {
            dirty = true;
            String fallbackGroupName = new String(Files.readAllBytes(legacyFallbackGroupDataPath), StandardCharsets.UTF_8);
            this.fallbackGroups.put(0, fallbackGroupName);
            Files.delete(legacyFallbackGroupDataPath);
        }

        if (Files.exists(fallbackGroupDataPaths)) {
            dirty = true;
            this.fallbackGroups.clear();
            this.fallbackGroups.put(0, "");
            this.fallbackGroups.putAll(GSON.fromJson(Files.newBufferedReader(fallbackGroupDataPaths, StandardCharsets.UTF_8), DEFAULT_GROUPS_TYPES));
        }

        // Initialize
        if (!Files.exists(playerDataPath) || !Files.exists(groupDataPath) || !Files.exists(fallbackGroupDataPaths)) {
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
        Files.write(fallbackGroupDataPaths, GSON.toJson(this.fallbackGroups).getBytes(StandardCharsets.UTF_8));
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

    public void initForFirstTime(@Nullable GameProfile gameProfile, Consumer<UserGroup> callback) {
        if (gameProfile != null) {
            String groupName = lookup(gameProfile.getId());
            if (!groupName.isEmpty()) {
                dirty = true;
                users.put(gameProfile.getId(), groupName);
                callback.accept(groups.getOrDefault(groupName, new UserGroup()));
            }
        }
    }

    public String lookup(UUID id) {
        return this.users.getOrDefault(id, this.getFallbackGroup(this.server.getProfileCache().get(id)));
    }

    public Boolean hasPermission(UUID id, String permission) {
        final UserGroup group = this.groups.getOrDefault(this.lookup(id), new UserGroup());
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

    public ITextComponent getPrefix(String group) {
        return groups.getOrDefault(group, new UserGroup()).prefix;
    }

    public ITextComponent getPrefixForUser(UUID uuid) {
        return groups.getOrDefault(this.lookup(uuid), new UserGroup()).prefix;
    }

    public void setPrefix(String group, ITextComponent prefix) {
        if (hasGroup(group)) {
            groups.getOrDefault(group, new UserGroup()).prefix = prefix;
            dirty = true;
        }
    }

    public void setFallbackGroup(int opLevel, String groupName) {
        this.fallbackGroups.put(opLevel, groupName);
    }

    public String getFallbackGroup(int opLevel) {
        return this.fallbackGroups.getOrDefault(opLevel, "");
    }

    public String getFallbackGroup(@Nullable GameProfile gameProfile) {
        if (gameProfile != null) {
            for (int opLevel = server.getProfilePermissions(gameProfile); opLevel > 0; --opLevel) {
                if (fallbackGroups.containsKey(opLevel)) {
                    return fallbackGroups.get(opLevel);
                }
            }
        }
        return fallbackGroups.get(0);
    }

    public void setGameType(String group, GameType gameType) {
        if (hasGroup(group)) {
            groups.getOrDefault(group, new UserGroup()).mode = gameType.getName();
        }
    }

    public Optional<String> getGameType(String group) {
        if (hasGroup(group)) {
            return Optional.of(groups.getOrDefault(group, new UserGroup()).mode);
        }
        return Optional.empty();
    }

    public Set<String> getPermissionNodes(String group) {
        if (hasGroup(group)) {
            return ImmutableSet.copyOf(groups.getOrDefault(group, new UserGroup()).permissions.keySet());
        }
        return Collections.emptySet();
    }
    
    public Stream<String> getPermissionDetails(String groupId) {
    	final UserGroup group = this.groups.getOrDefault(groupId, new UserGroup());
        return group.permissions.entrySet().stream().map(e -> e.getKey() + " = " + e.getValue());
    }
}