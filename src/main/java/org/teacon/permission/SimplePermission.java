package org.teacon.permission;

import net.minecraft.crash.CrashReport;
import net.minecraft.crash.ReportedException;
import net.minecraft.entity.player.PlayerEntity;
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
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.network.FMLNetworkConstants;
import net.minecraftforge.server.permission.IPermissionHandler;
import net.minecraftforge.server.permission.PermissionAPI;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.teacon.permission.command.SimplePermissionCommand;
import org.teacon.permission.command.arguments.ArgumentsRegistry;
import org.teacon.permission.repo.UserDataRepo;
import org.teacon.permission.repo.UserGroup;

import java.io.IOException;
import java.nio.file.Path;

@Mod("simple_permission")
public class SimplePermission {

    private static final Logger LOGGER = LogManager.getLogger("SimplePerms");

    public static final FolderName SIMPLE_PERMS_FOLDER_NAME = new FolderName("serverconfig/simpleperms");

    public static UserDataRepo REPO;

    public SimplePermission() {
        ModLoadingContext.get().registerExtensionPoint(ExtensionPoint.DISPLAYTEST,
                () -> Pair.of(() -> FMLNetworkConstants.IGNORESERVERONLY, (serverVer, isDedicated) -> true));
        FMLJavaModLoadingContext.get().getModEventBus().addListener(ArgumentsRegistry::registerArguments);
        MinecraftForge.EVENT_BUS.addListener(SimplePermission::serverStart);
        MinecraftForge.EVENT_BUS.addListener(SimplePermission::serverStop);
        MinecraftForge.EVENT_BUS.addListener(SimplePermission::handleLogin);
        final IPermissionHandler previous = PermissionAPI.getPermissionHandler();
        LOGGER.debug("SimplePermission is going to wrap up the current permission handler {}", previous);
        PermissionAPI.setPermissionHandler(new SimplePermissionHandler(previous));
    }

    public static void serverStart(FMLServerStartingEvent event) {
        SimplePermissionCommand.register(event.getServer().getCommandManager().getDispatcher());
        Path DATA_PATH = event.getServer().func_240776_a_(SIMPLE_PERMS_FOLDER_NAME);

        try {
            REPO = new UserDataRepo(DATA_PATH);
        } catch (IOException e) {
            throw new ReportedException(new CrashReport("Failed to initialize user data repo", e));
        }
    }

    @SuppressWarnings("unused")
    public static void serverStop(FMLServerStoppingEvent event) {
        try {
            REPO.save();
        } catch (IOException e) {
            LOGGER.error("Failed to save data repo", e);
        }
    }

    public static void handleLogin(PlayerEvent.PlayerLoggedInEvent event) {
        final PlayerEntity player = event.getPlayer();
        REPO.initForFirstTime(player.getGameProfile().getId(), group ->
                player.setGameType(GameType.getByName(group.mode))
        );
        final UserGroup group = REPO.lookup(player.getGameProfile().getId());
        event.getPlayer().getPrefixes().add(new StringTextComponent(group.prefix));
    }
}