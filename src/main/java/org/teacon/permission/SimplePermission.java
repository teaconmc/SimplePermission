package org.teacon.permission;

import com.mojang.authlib.GameProfile;
import net.minecraft.crash.CrashReport;
import net.minecraft.crash.ReportedException;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.management.PlayerList;
import net.minecraft.util.text.IFormattableTextComponent;
import net.minecraft.world.GameType;
import net.minecraft.world.storage.FolderName;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.fml.ExtensionPoint;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.server.FMLServerStartingEvent;
import net.minecraftforge.fml.event.server.FMLServerStoppingEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.network.FMLNetworkConstants;
import net.minecraftforge.fml.server.ServerLifecycleHooks;
import net.minecraftforge.server.permission.DefaultPermissionLevel;
import net.minecraftforge.server.permission.IPermissionHandler;
import net.minecraftforge.server.permission.PermissionAPI;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.teacon.permission.command.SimplePermissionCommand;
import org.teacon.permission.command.arguments.ArgumentsRegistry;
import org.teacon.permission.repo.UserDataRepo;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Optional;

@Mod("simple_permission")
public class SimplePermission {

    private static final Logger LOGGER = LogManager.getLogger("SimplePerms");

    public static final FolderName SIMPLE_PERMS_FOLDER_NAME = new FolderName("simpleperms");

    public static UserDataRepo REPO;

    private static SimplePermissionHandler permissionHandler;

    public SimplePermission() {
        ModLoadingContext.get().registerExtensionPoint(ExtensionPoint.DISPLAYTEST,
                () -> Pair.of(() -> FMLNetworkConstants.IGNORESERVERONLY, (serverVer, isDedicated) -> true));
        FMLJavaModLoadingContext.get().getModEventBus().addListener(ArgumentsRegistry::registerArguments);
        MinecraftForge.EVENT_BUS.addListener(SimplePermission::serverStart);
        MinecraftForge.EVENT_BUS.addListener(SimplePermission::serverStop);
        MinecraftForge.EVENT_BUS.addListener(SimplePermission::onServerTick);
        MinecraftForge.EVENT_BUS.addListener(SimplePermission::registerCommands);
        MinecraftForge.EVENT_BUS.addListener(SimplePermission::handlePlayerLogin);
        MinecraftForge.EVENT_BUS.addListener(SimplePermission::onPlayerNameFormat);
    }

    public static SimplePermissionHandler getPermissionHandler() {
        return permissionHandler;
    }

    public static void serverStart(FMLServerStartingEvent event) {
        Path DATA_PATH = event.getServer().getWorldPath(SIMPLE_PERMS_FOLDER_NAME);

        PermissionAPI.registerNode(PermissionNodes.MANAGE, DefaultPermissionLevel.OP, "Management permission of simple permission");

        try {
            REPO = new UserDataRepo(event.getServer(), DATA_PATH);
            // TODO We still don't know where to call setPermissionHandler
            final IPermissionHandler previous = PermissionAPI.getPermissionHandler();
            LOGGER.debug("SimplePermission is going to wrap up the current permission handler {}", previous);
            PermissionAPI.setPermissionHandler(permissionHandler = new SimplePermissionHandler(previous));
        } catch (IOException e) {
            throw new ReportedException(new CrashReport("Failed to initialize user data repo", e));
        }
    }

    public static void registerCommands(RegisterCommandsEvent event) {
        SimplePermissionCommand.register(event.getDispatcher());
    }

    @SuppressWarnings("unused")
    public static void serverStop(FMLServerStoppingEvent event) {
        try {
            LOGGER.info("Saving simple permission data repo");
            REPO.save();
        } catch (IOException e) {
            LOGGER.error("Failed to save data repo", e);
        }
    }

    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) return;
        if (ServerLifecycleHooks.getCurrentServer().getTickCount() % 6000 == 0) {
            if (REPO.dirty()) {
                try {
                    REPO.save();
                    LOGGER.debug("Auto saving data repo");
                } catch (IOException e) {
                    LOGGER.error("Failed to save data repo", e);
                }
            }
        }
    }

    public static void handlePlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getPlayer() instanceof ServerPlayerEntity) {
            final ServerPlayerEntity player = (ServerPlayerEntity) event.getPlayer();
            final GameProfile playerGameProfile = player.getGameProfile();
            REPO.initForSpawn(playerGameProfile, group -> {
                final Optional<String> gameTypeOptional = REPO.getGameType(group);
                gameTypeOptional.ifPresent(type -> player.setGameMode(GameType.byName(type)));
                LOGGER.info("Add default group {} for player {}", group, playerGameProfile.getName());
            });
            player.refreshDisplayName();
            player.server.submitAsync(() -> {
                final PlayerList playerList = player.server.getPlayerList();
                player.connection.send(VanillaPacketUtils.displayNameUpdatePacketForAll(playerList));
            });
        }
    }

    public static void onPlayerNameFormat(PlayerEvent.NameFormat event) {
        if (event.getPlayer() instanceof ServerPlayerEntity) {
            final ServerPlayerEntity player = (ServerPlayerEntity) event.getPlayer();
            final GameProfile playerGameProfile = player.getGameProfile();
            final IFormattableTextComponent prefix = REPO.getPrefix(REPO.lookup(playerGameProfile.getId())).copy();
            final IFormattableTextComponent newDisplayName = prefix.append(event.getDisplayname());
            LOGGER.info("Update the prefix for {}: {}", playerGameProfile.getName(), newDisplayName);
            event.setDisplayname(newDisplayName);
            player.server.submitAsync(() -> {
                final PlayerList playerList = player.server.getPlayerList();
                playerList.broadcastAll(VanillaPacketUtils.displayNameUpdatePacketFor(player, newDisplayName));
            });
        }
    }
}