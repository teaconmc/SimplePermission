package org.teacon.permission.command.arguments;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import net.minecraft.command.ISuggestionProvider;
import net.minecraft.util.text.TranslationTextComponent;
import net.minecraft.world.GameType;

import java.util.Arrays;
import java.util.concurrent.CompletableFuture;

public class GameTypeArgumentType implements ArgumentType<GameType> {
    private static final DynamicCommandExceptionType GAMETYPE_NOT_EXIST =
            new DynamicCommandExceptionType(e -> new TranslationTextComponent("command.simple_perms.error.gametype_not_exist", e));

    public static GameType getGameType(CommandContext<?> ctx, String name) {
        return ctx.getArgument(name, GameType.class);
    }

    public static GameTypeArgumentType gameType() {
        return new GameTypeArgumentType();
    }

    @Override
    public GameType parse(StringReader reader) throws CommandSyntaxException {
        String type = reader.readString();
        if (Arrays.stream(GameType.values()).noneMatch(g -> g.getName().equals(type)))
            throw GAMETYPE_NOT_EXIST.create(type);
        return GameType.getByName(type);
    }

    @Override
    public <S> CompletableFuture<Suggestions> listSuggestions(CommandContext<S> context, SuggestionsBuilder builder) {
        return ISuggestionProvider.suggest(Arrays.stream(GameType.values()).map(GameType::getName), builder);
    }
}
