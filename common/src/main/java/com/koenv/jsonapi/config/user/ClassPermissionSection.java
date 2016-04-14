package com.koenv.jsonapi.config.user;

import java.util.List;

public class ClassPermissionSection {
    private String name;
    private PermissionSection.Type type;
    private List<String> methods;

    private ClassPermissionSection(Builder builder) {
        this.name = builder.name;
        this.type = builder.type;
        this.methods = builder.methods;
    }

    public static Builder builder() {
        return new Builder();
    }

    public String getName() {
        return name;
    }

    public PermissionSection.Type getType() {
        return type;
    }

    public List<String> getMethods() {
        return methods;
    }

    public static class Builder {
        private String name;
        private PermissionSection.Type type;
        private List<String> methods;

        private Builder() {
        }

        public Builder name(String name) {
            this.name = name;
            return this;
        }

        public Builder type(PermissionSection.Type type) {
            this.type = type;
            return this;
        }

        public Builder methods(List<String> methods) {
            this.methods = methods;
            return this;
        }

        public ClassPermissionSection build() {
            return new ClassPermissionSection(this);
        }
    }
}
