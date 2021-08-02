package org.teacon.permission.repo;

import com.google.common.collect.LinkedListMultimap;
import com.google.common.collect.ListMultimap;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.mojang.authlib.GameProfile;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.text.IFormattableTextComponent;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.util.text.TranslationTextComponent;
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
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.stream.Stream;

@ThreadSafe
public final class UserDataRepo {

    private static final Gson GSON = new GsonBuilder().registerTypeAdapter(UserGroup.class, new UserGroupTypeAdapter()).setLenient().create();
    private static final Type USER_LIST_TYPE = new TypeToken<Map<UUID, String>>() {
    }.getType();
    private static final Type GROUP_LIST_TYPE = new TypeToken<Map<String, UserGroup>>() {
    }.getType();
    private static final Type DEFAULT_GROUPS_TYPES = new TypeToken<Map<Integer, String>>() {
    }.getType();

    private final Map<String, UserGroup> groups = new ConcurrentHashMap<>();
    private final Map<UUID, String> users = new ConcurrentHashMap<>();
    private final Map<Integer, String> fallbackGroups = new TreeMap<>(Collections.singletonMap(0, ""));

    private final Path playerDataPath;
    private final Path groupDataPath;
    private final Path fallbackGroupDataPaths;

    private final Path legacyFallbackGroupDataPath;

    private final MinecraftServer server;

    private final AtomicBoolean loading = new AtomicBoolean(false);
    private final AtomicBoolean saving = new AtomicBoolean(false);

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
        if (!loading.compareAndSet(false, true)) return;

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

        loading.set(false);
    }

    /**
     * Save data to the root directory.
     */
    public void save() throws IOException {
        if (!saving.compareAndSet(false, true)) return;

        Files.createDirectories(playerDataPath.getParent());
        Files.write(playerDataPath, GSON.toJson(this.users).getBytes(StandardCharsets.UTF_8));
        Files.write(groupDataPath, GSON.toJson(this.groups).getBytes(StandardCharsets.UTF_8));
        Files.write(fallbackGroupDataPaths, GSON.toJson(this.fallbackGroups).getBytes(StandardCharsets.UTF_8));

        dirty = false;

        saving.set(false);
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
        return Collections.unmodifiableSet(this.groups.keySet());
    }

    public void initForFirstTime(@Nullable GameProfile profile, Consumer<String> callback) {
        if (profile != null) {
            String fallback = this.getFallbackGroup(profile);
            String groupName = this.users.getOrDefault(profile.getId(), "");
            if (!getGroupDeep(groupName).containsKey(fallback)) {
                users.put(profile.getId(), groupName);
                callback.accept(groupName);
                dirty = true;
            }
        }
    }

    public String lookup(UUID id) {
        return this.users.getOrDefault(id, this.getFallbackGroup(this.server.getProfileCache().get(id)));
    }

    public Boolean hasPermission(UUID id, String perm) {
        final Collection<UserGroup> groups = getGroupDeep(lookup(id)).values();
        return groups.stream().map(g -> g.permissions.get(perm)).filter(Objects::nonNull).findFirst().orElse(null);
    }

    private UserGroup getGroup(String lookup) {
        return lookup.isEmpty() ? new UserGroup() : this.groups.getOrDefault(lookup, new UserGroup());
    }

    private Map<String, UserGroup> getGroupDeep(String lookup) {
        UserGroup group = getGroup(lookup);
        Map<String, UserGroup> collected = new LinkedHashMap<>(Collections.singletonMap(lookup, group));
        for (Queue<UserGroup> queue = new ArrayDeque<>(); group != null; group = queue.poll()) {
            for (String parent : group.parents) {
                UserGroup parentGroup = groups.getOrDefault(parent, new UserGroup());
                if (!collected.containsKey(parent)) {
                    collected.put(parent, parentGroup);
                    queue.offer(parentGroup);
                }
            }
        }
        return Collections.unmodifiableMap(collected);
    }

    public void grant(String group, String permission, boolean bool) {
        if (!Boolean.valueOf(bool).equals(getGroup(group).permissions.put(permission, bool))) {
            dirty = true;
        }
    }

    public void revoke(String group, String permission) {
        if (getGroup(group).permissions.remove(permission)) {
            dirty = true;
        }
    }

    public void addParent(String group, String parent) {
        if (getGroup(group).parents.add(parent)) {
            dirty = true;
        }
    }

    public void removeParent(String group, String parent) {
        if (getGroup(group).parents.removeIf(parent::equals)) {
            dirty = true;
        }
    }

    public Stream<String> parentsOf(String group) {
        return getGroup(group).parents.stream();
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
        return getGroup(group).prefix;
    }

    public void setPrefix(String group, ITextComponent prefix) {
        if (hasGroup(group)) {
            getGroup(group).prefix = prefix;
            dirty = true;
        }
    }

    public void setFallbackGroup(int opLevel, String groupName) {
        this.fallbackGroups.put(opLevel, groupName);
        dirty = true;
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
            getGroup(group).mode = gameType.getName();
            dirty = true;
        }
    }

    public Optional<String> getGameType(String group) {
        if (hasGroup(group)) {
            return Optional.of(getGroup(group).mode);
        }
        return Optional.empty();
    }

    public Set<String> getPermissionNodes(String group) {
        if (hasGroup(group)) {
            return Collections.unmodifiableSet(getGroup(group).permissions.keySet());
        }
        return Collections.emptySet();
    }

    public ListMultimap<String, IFormattableTextComponent> getPermissionDetails(String groupId) {
        ListMultimap<String, IFormattableTextComponent> result = LinkedListMultimap.create();
        for (Map.Entry<String, UserGroup> entry : getGroupDeep(groupId).entrySet()) {
            String childGroupId = entry.getKey();
            for (Map.Entry<String, Boolean> permEntry : entry.getValue().permissions.entrySet()) {
                String perm = permEntry.getKey();
                IFormattableTextComponent item = new TranslationTextComponent(
                        "command.simple_perms.info.permission_item", permEntry.getValue(), childGroupId);
                result.put(perm, result.containsKey(perm) ? item.withStyle(TextFormatting.STRIKETHROUGH) : item);
            }
        }
        return result;
    }
}