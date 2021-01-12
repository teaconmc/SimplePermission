package org.teacon.permission.command.arguments;

public class UserGroupInput implements IUserGroupArgument {

    private final String groupName;

    private UserGroupInput(String groupName) {
        this.groupName = groupName;
    }

    public static UserGroupInput of(String name) {
        return new UserGroupInput(name);
    }

    @Override
    public String getGroup() {
        return groupName;
    }
}
