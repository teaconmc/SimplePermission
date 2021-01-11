package org.teacon.permission;

import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import com.mojang.brigadier.tree.LiteralCommandNode;
import it.unimi.dsi.fastutil.objects.ObjectArrays;
import net.minecraft.command.CommandSource;
import net.minecraft.command.Commands;
import net.minecraft.command.arguments.GameProfileArgument;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.Util;
import net.minecraft.util.text.StringTextComponent;
import net.minecraft.util.text.TranslationTextComponent;
import net.minecraftforge.server.permission.PermissionAPI;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.teacon.permission.repo.UserDataRepo;

import java.io.IOException;
import java.util.Objects;

import static org.teacon.permission.SimplePermission.REPO;


public final class SimplePermissionCommand {

    private static final DynamicCommandExceptionType GROUP_NOT_EXIST
            = new DynamicCommandExceptionType(o -> new TranslationTextComponent("command.simple_perms.error.invalid_group", o));
    private static final Logger LOGGER = LogManager.getLogger("SimplePerms");

    static void register(CommandDispatcher<CommandSource> dispatcher) {
        LiteralCommandNode<CommandSource> theCommand = dispatcher.register(Commands.literal("simplepermission")
                .then(Commands.literal("group")
                        .requires(SimplePermissionCommand::check)
                        .then(Commands.argument("name", StringArgumentType.word())
                                .then(Commands.literal("assign").then(Commands.argument("player", GameProfileArgument.gameProfile())
                                        .executes(SimplePermissionCommand::addPlayerToGroup)))
                                .then(Commands.literal("unassign").then(Commands.argument("player", GameProfileArgument.gameProfile())
                                        .executes(SimplePermissionCommand::removePlayerFromGroup)))
                                .then(Commands.literal("members").executes(SimplePermissionCommand::listMembers))
                                .then(Commands.literal("grant").then(Commands.argument("permission", StringArgumentType.word())
                                        .executes(SimplePermissionCommand::grant)))
                                .then(Commands.literal("revoke").then(Commands.argument("permission", StringArgumentType.word())
                                        .executes(SimplePermissionCommand::revoke)))
                        ))
                .then(Commands.literal("reload")
                        .requires(SimplePermissionCommand::check)
                        .executes(SimplePermissionCommand::reload))
                .then(Commands.literal("groups").executes(SimplePermissionCommand::listGroups))
                .then(Commands.literal("about").executes(SimplePermissionCommand::info)));

        dispatcher.register(Commands.literal("sp").redirect(theCommand));
        dispatcher.register(Commands.literal("simpleperms").redirect(theCommand));
    }

    static boolean check(CommandSource source) {
        try {
            return PermissionAPI.hasPermission(source.asPlayer(), "command.simple_perms.manage");
        } catch (Exception e) {
            return true; // Assume it's not regular player. TODO are there other loopholes?
        }
    }

    static int info(CommandContext<CommandSource> context) {
        context.getSource().sendFeedback(new TranslationTextComponent("command.simple_perms.info.about", ObjectArrays.EMPTY_ARRAY), false);
        return Command.SINGLE_SUCCESS;
    }

    static int reload(CommandContext<CommandSource> context) {
        context.getSource().sendFeedback(new TranslationTextComponent("command.simple_perms.info.reload", ObjectArrays.EMPTY_ARRAY), true);
        Util.getServerExecutor().execute(() -> {
            try {
                SimplePermission.REPO.load();
            } catch (IOException e) {
                LOGGER.error("Failed to reload data repo", e);
                context.getSource().sendErrorMessage(new TranslationTextComponent("command.simple_perms.error.reload"));
            }
        });
        return Command.SINGLE_SUCCESS;
    }

    static int listGroups(CommandContext<CommandSource> context) {
        REPO.groups()
                .stream()
                .map(group -> new TranslationTextComponent("command.simple_perms.info.list_item", group))
                .forEach(t -> context.getSource().sendFeedback(t, true));
        return Command.SINGLE_SUCCESS;
    }

    static int addPlayerToGroup(CommandContext<CommandSource> context) throws CommandSyntaxException {
        final String group = StringArgumentType.getString(context, "name");
        if (REPO.hasGroup(group)) {
            GameProfileArgument.getGameProfiles(context, "player").stream()
                    .map(GameProfile::getId)
                    .forEach(uuid -> REPO.assignUserToGroup(uuid, group));
            return Command.SINGLE_SUCCESS;
        } else {
            throw GROUP_NOT_EXIST.create(group);
        }
    }

    static int removePlayerFromGroup(CommandContext<CommandSource> context) throws CommandSyntaxException {
        GameProfileArgument.getGameProfiles(context, "player").stream()
                .map(GameProfile::getId)
                .forEach(uuid -> REPO.assignUserToGroup(uuid, ""));
        return Command.SINGLE_SUCCESS;
    }

    static int listMembers(CommandContext<CommandSource> context) throws CommandSyntaxException {
        final String group = StringArgumentType.getString(context, "name");
        if (REPO.hasGroup(group)) {
            final CommandSource source = context.getSource();
            long count = REPO.reverseLookup(group)
                    .map(uuid -> context.getSource().getServer().getPlayerList().getPlayerByUUID(uuid))
                    .filter(Objects::nonNull)
                    .map(player -> new TranslationTextComponent("command.simple_perms.info.list_item", player.getDisplayName())
                            .appendString(" [" + player.getGameProfile().getId() + "]"))
                    .peek(msg -> source.sendFeedback(msg, true))
                    .count();
            source.sendFeedback(new TranslationTextComponent("command.simple_perms.info.total_members", count), true);
            return Command.SINGLE_SUCCESS;
        } else {
            throw GROUP_NOT_EXIST.create(group);
        }
    }

    static int grant(CommandContext<CommandSource> context) {
        context.getSource().sendFeedback(new StringTextComponent("WIP :("), false);
        return Command.SINGLE_SUCCESS;
    }

    static int revoke(CommandContext<CommandSource> context) {
        context.getSource().sendFeedback(new StringTextComponent("WIP :("), false);
        return Command.SINGLE_SUCCESS;
    }
}