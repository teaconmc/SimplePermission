package org.teacon.permission.command.arguments;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import net.minecraft.command.CommandSource;
import net.minecraft.util.text.TranslationTextComponent;

import java.util.concurrent.CompletableFuture;

import static org.teacon.permission.SimplePermission.REPO;

public class UserGroupArgumentType implements ArgumentType<IUserGroupArgument> {

    private static final DynamicCommandExceptionType GROUP_NOT_EXIST
            = new DynamicCommandExceptionType(o -> new TranslationTextComponent("command.simple_perms.error.invalid_group", o));

    public static UserGroupArgumentType userGroup() {
        return new UserGroupArgumentType();
    }

    public static String getUserGroup(CommandContext<CommandSource> ctx, String name) throws CommandSyntaxException {
        String group =  ctx.getArgument(name, IUserGroupArgument.class).getGroup();
        if (!REPO.hasGroup(group)) throw GROUP_NOT_EXIST.create(group);
        return group;
    }

    @Override
    public IUserGroupArgument parse(StringReader reader) throws CommandSyntaxException {
        return UserGroupInput.of(reader.readString());
    }

    @Override
    public <S> CompletableFuture<Suggestions> listSuggestions(CommandContext<S> context, SuggestionsBuilder builder) {
        // TODO
        return Suggestions.empty();
    }
}
