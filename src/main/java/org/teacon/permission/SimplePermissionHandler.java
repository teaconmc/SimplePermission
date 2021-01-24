package org.teacon.permission;

import java.util.Collection;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.mojang.authlib.GameProfile;

import net.minecraft.util.text.TranslationTextComponent;
import net.minecraftforge.fml.server.ServerLifecycleHooks;
import net.minecraftforge.server.permission.DefaultPermissionLevel;
import net.minecraftforge.server.permission.IPermissionHandler;
import net.minecraftforge.server.permission.context.IContext;

import javax.annotation.Nullable;
import javax.annotation.ParametersAreNonnullByDefault;

import static org.teacon.permission.SimplePermission.REPO;

@ParametersAreNonnullByDefault
public final class SimplePermissionHandler implements IPermissionHandler {

    /**
     * Fallback handler will be used when a permission is not explicitly
     * set on our hands.
     */
    private final IPermissionHandler parent;

    private final Set<UUID> verboseList = ConcurrentHashMap.newKeySet();

    public SimplePermissionHandler(IPermissionHandler previous) {
        this.parent = previous;
    }

    @Override
    public void registerNode(String node, DefaultPermissionLevel level, String desc) {
        this.parent.registerNode(node, level, desc);
    }

    @Override
    public Collection<String> getRegisteredNodes() {
        return this.parent.getRegisteredNodes();
    }

    @Override
    public boolean hasPermission(GameProfile profile, String node, @Nullable IContext context) {
        final Boolean result = REPO.hasPermission(profile.getId(), node);
        boolean ret = result == null ? this.parent.hasPermission(profile, node, context) : result;
        if (!verboseList.isEmpty()) {
            verboseList.stream().map(ServerLifecycleHooks.getCurrentServer().getPlayerList()::getPlayerByUUID)
                    .filter(Objects::nonNull)
                    .forEach(p -> p.sendStatusMessage(
                            new TranslationTextComponent("command.simple_perms.info.verbose", p.getName().getString(), node, ret),
                            false)
                    );
        }
        return ret;
    }

    @Override
    public String getNodeDescription(String node) {
        return this.parent.getNodeDescription(node);
    }

    public void verbose(UUID uuid) {
        verboseList.add(uuid);
    }

    public boolean isVerbose(UUID uuid) {
        return verboseList.contains(uuid);
    }

    public void stopVerbose(UUID uuid) {
        verboseList.remove(uuid);
    }

}
