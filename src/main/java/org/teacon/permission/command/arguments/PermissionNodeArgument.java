package org.teacon.permission.command.arguments;

import com.google.gson.JsonObject;
import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import mcp.MethodsReturnNonnullByDefault;
import net.minecraft.command.CommandSource;
import net.minecraft.command.ISuggestionProvider;
import net.minecraft.command.arguments.IArgumentSerializer;
import net.minecraft.network.PacketBuffer;
import net.minecraftforge.server.permission.PermissionAPI;

import javax.annotation.ParametersAreNonnullByDefault;
import java.util.concurrent.CompletableFuture;

import static org.teacon.permission.SimplePermission.REPO;

public class PermissionNodeArgument implements ArgumentType<String> {

    private String ofGroup = null;

    public PermissionNodeArgument() {
    }

    public PermissionNodeArgument(String group) {
        this.ofGroup = group;
    }

    public static PermissionNodeArgument node() {
        return new PermissionNodeArgument();
    }

    public static PermissionNodeArgument ofGroup(String group) {
        return new PermissionNodeArgument(group);
    }

    public static String getNode(CommandContext<?> ctx, String name) {
        return ctx.getArgument(name, String.class);
    }

    @Override
    public String parse(StringReader reader) throws CommandSyntaxException {
        return reader.readString();
    }

    @Override
    @SuppressWarnings("unchecked")
    public <S> CompletableFuture<Suggestions> listSuggestions(CommandContext<S> context, SuggestionsBuilder builder) {
        if (context.getSource() instanceof CommandSource) {
            if (ofGroup == null) {
                return ISuggestionProvider.suggest(PermissionAPI.getPermissionHandler().getRegisteredNodes(), builder);
            } else {
                String group;
                try {
                    // Don't ask me why, I don't even know, but it works :(
                    group = UserGroupArgument.getUserGroup(context.getChild(), ofGroup);
                } catch (Exception e) {
                    try {
                        group = UserGroupArgument.getUserGroup(context, ofGroup);
                    } catch (Exception gg) {
                        return Suggestions.empty();
                    }
                }
                return ISuggestionProvider.suggest(REPO.getPermissionNodes(group), builder);
            }
        } else if (context.getSource() instanceof ISuggestionProvider) {
            return ((ISuggestionProvider) context.getSource()).getSuggestionsFromServer((CommandContext<ISuggestionProvider>) context, builder);
        } else {
            return Suggestions.empty();
        }
    }

    @ParametersAreNonnullByDefault
    @MethodsReturnNonnullByDefault
    public static class Serializer implements IArgumentSerializer<PermissionNodeArgument> {

        @Override
        public void write(PermissionNodeArgument argument, PacketBuffer buffer) {
            if (argument.ofGroup != null) {
                buffer.writeBoolean(true);
                buffer.writeString(argument.ofGroup);
            } else {
                buffer.writeBoolean(false);
            }
        }

        @Override
        public PermissionNodeArgument read(PacketBuffer buffer) {
            if (buffer.readBoolean()) {
                return new PermissionNodeArgument(buffer.readString(32767));
            } else {
                return new PermissionNodeArgument();
            }
        }

        @Override
        public void write(PermissionNodeArgument argument, JsonObject object) {
            if (argument.ofGroup != null) {
                object.addProperty("group", argument.ofGroup);
            }
        }
    }
}
