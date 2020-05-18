package teaconmc.permission;

import java.util.Collection;

import com.mojang.authlib.GameProfile;

import net.minecraftforge.server.permission.DefaultPermissionLevel;
import net.minecraftforge.server.permission.IPermissionHandler;
import net.minecraftforge.server.permission.context.IContext;

public final class SimplePermissionHandler implements IPermissionHandler {

    /**
     * Fallback handler will be used when a permission is not explictly 
     * set on our hands.
     */
    private final IPermissionHandler parent;

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
    public boolean hasPermission(GameProfile profile, String node, IContext context) {
        final Boolean result = UserDataRepo.INSTANCE.hasPermission(profile.getId(), node);
        return result == null ? this.parent.hasPermission(profile, node, context) : result;
    }

    @Override
    public String getNodeDescription(String node) {
        return this.parent.getNodeDescription(node);
    }

}
