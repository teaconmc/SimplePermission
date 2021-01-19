package org.teacon.permission.command.arguments;

import net.minecraft.command.arguments.ArgumentSerializer;
import net.minecraft.command.arguments.ArgumentTypes;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;

public final class ArgumentsRegistry {
    public static void registerArguments(@SuppressWarnings("unused") FMLCommonSetupEvent event) {
        ArgumentTypes.register("simple_permission:user_group", UserGroupArgument.class,
                new ArgumentSerializer<>(UserGroupArgument::new));
        ArgumentTypes.register("simple_permission:group_parent", ParentArgumentType.class,
                new ParentArgumentType.Serializer());
        ArgumentTypes.register("simple_permission:gametype", GameTypeArgument.class,
                new ArgumentSerializer<>(GameTypeArgument::new));
        ArgumentTypes.register("simple_permission:permission_node", PermissionNodeArgument.class,
                new PermissionNodeArgument.Serializer());
    }
}
