package org.teacon.permission.repo;

import java.util.Collections;
import java.util.Map;
import java.util.Set;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;
import com.google.gson.annotations.SerializedName;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.StringTextComponent;

public final class UserGroup {

    /**
     * Internal name of this group.
     */
    public String name = "";

    /**
     * Prefix that will append before player's display name.
     */
    public ITextComponent prefix = new StringTextComponent("");

    /**
     * Name of the gamemode that first-time player of this group will be switched to. 
     * Default to Survival Mode ({@code survival}). Currently, valid values are:
     * <ul>
     * <li>{@code adventure}</li>
     * <li>{@code creative}</li>
     * <li>{@code spectator}</li>
     * <li>{@code survival}</li>
     * </ul>
     */
    public String mode = "survival";

    /**
     * A set of other group names to look up if a particular permission values.
     * is unset.
     */
    public Set<String> parents = Collections.emptySet();

    /**
     * A collection of permission values. When querying this map, there are 
     * three possible results:
     * <ul>
     * <li> {@link Boolean#TRUE} means permission granted. </li>
     * <li> {@link Boolean#FALSE} means permission denied. </li>
     * <li> {@code null} means "default to parents, or other permission 
     * management systems if necessary". </li>
     * </ul>
     */
    public Map<String, Boolean> permissions = Collections.emptyMap();
    
}