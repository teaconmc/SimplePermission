package org.teacon.permission.command.arguments;

public class ParentInput {

    private final String parentName;

    private ParentInput(String parentName) {
        this.parentName = parentName;
    }

    public static ParentInput of(String name) {
        return new ParentInput(name);
    }

    public String getParent() {
        return parentName;
    }
}
