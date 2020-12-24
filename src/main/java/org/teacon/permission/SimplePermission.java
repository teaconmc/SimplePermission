package org.teacon.permission;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.text.StringTextComponent;
import net.minecraft.world.GameType;
import net.minecraft.world.storage.FolderName;
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
import org.apache.commons.lang3.tuple.Pair;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.nio.file.Files;
import java.nio.file.Path;

@Mod("simple_permission")
public class SimplePermission {

    private static final Logger LOGGER = LogManager.getLogger("SimplePerms");

    public static final FolderName SIMPLE_PERMS_FOLDER_NAME =new FolderName("serverconfig/simpleperms");

    public SimplePermission() {
        ModLoadingContext.get().registerExtensionPoint(ExtensionPoint.DISPLAYTEST,
                () -> Pair.of(() -> FMLNetworkConstants.IGNORESERVERONLY, (serverVer, isDedicated) -> true));
        MinecraftForge.EVENT_BUS.addListener(SimplePermission::serverStart);
        MinecraftForge.EVENT_BUS.addListener(SimplePermission::serverStop);
        MinecraftForge.EVENT_BUS.addListener(SimplePermission::handleLogin);
        final IPermissionHandler previous = PermissionAPI.getPermissionHandler();
        LOGGER.debug("SimplePermission is going to wrap up the current permission handler {}", previous);
        PermissionAPI.setPermissionHandler(new SimplePermissionHandler(previous));
    }

    public static void serverStart(FMLServerStartingEvent event) {
        SimplePermissionCommand.register(event.getServer().getCommandManager().getDispatcher());
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
                LOGGER.info("Did not read data from default config directory beceause it probably doesn't exist yet. ");
                LOGGER.info("Will try creating one now. ");
                try {
                    Files.createDirectory(defaultConfig);
                } catch (Exception e) {
                    LOGGER.warn("Failed to create directory {}. You may want to manually create it instead.", defaultConfig);
                    LOGGER.debug("Error details: ", e);
                }
            }
            final Path localData = server.func_240776_a_(SIMPLE_PERMS_FOLDER_NAME);
            if (Files.isDirectory(localData)) {
                try {
                    LOGGER.info("Read data from per-world config directory");
                    UserDataRepo.INSTANCE.loadFrom(localData);
                } catch (Exception e) {
                    LOGGER.error("Error while try loading data from world-specific config directory.", e);
                }
            } else {
                LOGGER.info("Did not read data from per-world config directory");
                LOGGER.info("Will try creating one now. ");
                try {
                    Files.createDirectory(localData);
                } catch (Exception e) {
                    LOGGER.warn("Failed to create directory {}. You may want to manually create it instead.", localData);
                    LOGGER.debug("Error details: ", e);
                }
            }
            UserDataRepo.INSTANCE.loading = false;
        }
    }

    public static void serverStop(FMLServerStoppingEvent event) {
        final MinecraftServer server = event.getServer();
        final Path localData = server.func_240776_a_(SIMPLE_PERMS_FOLDER_NAME);
        try {
            UserDataRepo.INSTANCE.save(localData);
        } catch (Exception e) {
            LOGGER.warn("Error occurred while saving player data, bad things may happen. BACK UP FIRST!");
            LOGGER.debug("Error details: ", e);
        }
        UserDataRepo.INSTANCE.reset();
    }

    public static void handleLogin(PlayerEvent.PlayerLoggedInEvent event) {
        final PlayerEntity player = event.getPlayer();
        UserDataRepo.INSTANCE.initForFirstTime(player.getGameProfile().getId(), group ->
                player.setGameType(GameType.getByName(group.mode))
        );
        final UserGroup group = UserDataRepo.INSTANCE.lookup(player.getGameProfile().getId());
        event.getPlayer().getPrefixes().add(new StringTextComponent(group.prefix));
    }
}