package org.teacon.permission.repo;

import com.google.gson.*;
import net.minecraft.util.text.ITextComponent;

import java.lang.reflect.Type;
import java.util.concurrent.ConcurrentHashMap;

public class UserGroupTypeAdapter implements JsonSerializer<UserGroup>, JsonDeserializer<UserGroup> {
    private final Gson gson = new GsonBuilder()
            .registerTypeAdapter(ITextComponent.class, new ITextComponent.Serializer())
            .create();

    @Override
    public UserGroup deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) throws JsonParseException {
        UserGroup group = gson.fromJson(json, UserGroup.class);
        UserGroup ret = new UserGroup();
        ret.name = group.name;
        ret.prefix = group.prefix;
        ret.mode = group.mode;
        ret.permissions = new ConcurrentHashMap<>(group.permissions);
        ret.parents = ConcurrentHashMap.newKeySet();
        ret.parents.addAll(group.parents);
        return ret;
    }

    @Override
    public JsonElement serialize(UserGroup src, Type typeOfSrc, JsonSerializationContext context) {
        return gson.toJsonTree(src);
    }
}
