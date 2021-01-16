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
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.annotation.ParametersAreNonnullByDefault;
import java.util.concurrent.CompletableFuture;

import static org.teacon.permission.SimplePermission.REPO;

public class ParentArgumentType implements ArgumentType<ParentInput> {

    private static final Logger LOGGER = LogManager.getLogger("SimplePerms");

    private final String groupArgumentName;

    private ParentArgumentType(final String groupArgumentName) {
        this.groupArgumentName = groupArgumentName;
    }

    public static ParentArgumentType parentsOf(String group) {
        return new ParentArgumentType(group);
    }

    public static String getParent(CommandContext<CommandSource> ctx, String name) {
        return ctx.getArgument(name, ParentInput.class).getParent();
    }

    @Override
    public ParentInput parse(StringReader reader) throws CommandSyntaxException {
        String parent = reader.readString();
        return ParentInput.of(parent);
    }

    @Override
    @SuppressWarnings("unchecked")
    public <S> CompletableFuture<Suggestions> listSuggestions(CommandContext<S> context, SuggestionsBuilder builder) {
        if (context.getSource() instanceof CommandSource) {
            try {
                String group = UserGroupArgumentType.getUserGroup((CommandContext<CommandSource>) context, groupArgumentName);
                return ISuggestionProvider.suggest(REPO.parentsOf(group), builder);
            } catch (CommandSyntaxException ex) {
                LOGGER.error("Failed to give suggestions", ex);
                return Suggestions.empty();
            }
        } else if (context.getSource() instanceof ISuggestionProvider) {
            return ((ISuggestionProvider) context.getSource()).getSuggestionsFromServer((CommandContext<ISuggestionProvider>) context, builder);
        }
        return Suggestions.empty();
    }

    @ParametersAreNonnullByDefault
    @MethodsReturnNonnullByDefault
    public static class Serializer implements IArgumentSerializer<ParentArgumentType> {

        @Override
        public void write(ParentArgumentType argument, PacketBuffer buffer) {
            buffer.writeString(argument.groupArgumentName);
        }

        @Override
        public ParentArgumentType read(PacketBuffer buffer) {
            return new ParentArgumentType(buffer.readString());
        }

        @Override
        public void write(ParentArgumentType argument, JsonObject object) {
            object.addProperty("groupArgumentName", argument.groupArgumentName);
        }
    }
}