package teaconmc.permission;

import java.io.BufferedReader;
import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.FileVisitor;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.stream.Stream;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public final class UserDataRepo {

    public static final UserDataRepo INSTANCE = new UserDataRepo();

    private static final Gson GSON = new GsonBuilder().setLenient().create();
    private static final Type USER_LIST_TYPE = new TypeToken<Map<UUID, String>>(){}.getType();

    private static final Logger LOGGER = LogManager.getLogger("SimplePerms");

    /**
     * Collections of known user group definitions.
     */
    private final Map<String, UserGroup> groups = new ConcurrentHashMap<>();

    /**
     * A map that associates a player's UUID to a certain group.
     */
    private final Map<UUID, String> users = new ConcurrentHashMap<>();

    /**
     * Default group to use if a user is not explictly assigned to any group.
     */
    private UserGroup fallbackGroup = new UserGroup();

    /**
     * Marker to denote whether the data is currently being loaded or not.
     */
	volatile boolean loading;

    /**
     * Load data from the given root directory. Will overwrite previously loaded
     * data if necessary.
     * 
     * @param configRoot the root path to load data from. Must be a directory.
     */
    void loadFrom(Path configRoot) throws IOException {
        Files.walkFileTree(configRoot, new FileVisitor<Path>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                return Files.isHidden(dir) ? FileVisitResult.SKIP_SUBTREE : FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                if (!Files.isHidden(file) && file.getFileName().toString().endsWith(".json")) {
                    final UserGroup group;
                    try (BufferedReader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
                        group = GSON.fromJson(reader, UserGroup.class);
                    }
                    if (group.name.isEmpty()) {
                        final String filename = file.getFileName().toString();
                        LOGGER.warn("Gropu definition '{}' didn't set a name, falling bak to file name.", filename);
                        group.name = filename.replace(".json", "");
                    }
                    UserDataRepo.this.groups.put(group.name, group);
                    if (group.fallback) {
                        LOGGER.info("Default user group is now '{}'.'", group.name);
                        UserDataRepo.this.fallbackGroup = group;
                    }
                } else if (".player_data.dat".equals(file.getFileName().toString())) {
                    try (BufferedReader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
                        UserDataRepo.this.users.putAll(GSON.fromJson(reader, USER_LIST_TYPE));
                    }
                }
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFileFailed(Path file, IOException exc) throws IOException {
                LOGGER.debug("Failed to read {}, details: \n{}", file, exc);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                return FileVisitResult.CONTINUE;
            }
        });
    }

    void save(Path configRoot) throws IOException {
        Files.write(configRoot.resolve(".player_data.dat"), GSON.toJson(this.users).getBytes(StandardCharsets.UTF_8));
    }

    void reset() {
        this.groups.clear();
        this.users.clear();
        this.fallbackGroup = new UserGroup();
    }

    public boolean hasGroup(String group) {
        return this.groups.containsKey(group) || group.isEmpty();
    }

    public void assignUserToGroup(UUID id, String group) {
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