package teaconmc.permission;

import java.nio.file.Files;
import java.nio.file.Path;

import org.apache.commons.lang3.tuple.Pair;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import net.minecraft.server.MinecraftServer;
import net.minecraft.util.text.StringTextComponent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.fml.ExtensionPoint;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.server.FMLServerStartingEvent;
import net.minecraftforge.fml.event.server.FMLServerStoppingEvent;
import net.minecraftforge.fml.loading.FMLConfig;
import net.minecraftforge.fml.loading.FMLPaths;
import net.minecraftforge.fml.network.FMLNetworkConstants;
import net.minecraftforge.server.permission.IPermissionHandler;
import net.minecraftforge.server.permission.PermissionAPI;

@Mod("simple_permission")
public class SimplePermission {

    private static final Logger LOGGER = LogManager.getLogger("SimplePerms");

    public SimplePermission() {
        ModLoadingContext.get().registerExtensionPoint(ExtensionPoint.DISPLAYTEST,
                () -> Pair.of(() -> FMLNetworkConstants.IGNORESERVERONLY, (serverVer, isDedicated) -> true));
        MinecraftForge.EVENT_BUS.addListener(SimplePermission::serverStart);
        MinecraftForge.EVENT_BUS.addListener(SimplePermission::serverStop);
        MinecraftForge.EVENT_BUS.addListener(SimplePermission::addPrefix);
        final IPermissionHandler previous = PermissionAPI.getPermissionHandler();
        LOGGER.debug("SimplePermission is going to wrap up the current permission handler {}", previous);
        PermissionAPI.setPermissionHandler(new SimplePermissionHandler(previous));
    }

    public static void serverStart(FMLServerStartingEvent event) {
        new SimplePermissionCommand(event.getCommandDispatcher());
        reload(event.getServer());
    }

    static void reload(MinecraftServer server) {
        if (!UserDataRepo.INSTANCE.loading) {
            UserDataRepo.INSTANCE.loading = true;
            final Path defaultConfig = FMLPaths.GAMEDIR.get().resolve(FMLConfig.defaultConfigPath()).resolve("simple_perms");
            if (Files.isDirectory(defaultConfig)) {
                try {
                    LOGGER.info("Read data from default config directory");
                    UserDataRepo.INSTANCE.loadFrom(defaultConfig);
                } catch (Exception e) {
                    LOGGER.error("Error while try loading data from default config directory.", e);
                }
            } else {
                LOGGER.info("Did not read data from default config directory");
            }
            final Path localData = server.getActiveAnvilConverter().getFile(server.getFolderName(), "serverconfig").toPath().resolve("simple_perms");
            if (Files.isDirectory(localData)) {
                try {
                    LOGGER.info("Read data from per-world config directory");
                    UserDataRepo.INSTANCE.loadFrom(localData);
                } catch (Exception e) {
                    LOGGER.error("Error while try loading data from world-specific config directory.", e);
                }
            } else {
                LOGGER.info("Did not read data from per-world config directory");
            }
            UserDataRepo.INSTANCE.loading = false;
        }
    }

    public static void serverStop(FMLServerStoppingEvent event) {
        final MinecraftServer server = event.getServer();
        final Path localData = server.getActiveAnvilConverter().getFile(server.getFolderName(), "serverconfig").toPath()
                .resolve("simple_perms");
        try {
            UserDataRepo.INSTANCE.save(localData);
        } catch (Exception e) {
            LOGGER.warn("Error occured while saving player data, bad things may happen. BACK UP FIRST!");
        }
        UserDataRepo.INSTANCE.reset();
    }

    public static void addPrefix(PlayerEvent.PlayerLoggedInEvent event) {
        final UserGroup group = UserDataRepo.INSTANCE.lookup(event.getPlayer().getGameProfile().getId());
        event.getPlayer().getPrefixes().add(new StringTextComponent(group.prefix));
    }
}