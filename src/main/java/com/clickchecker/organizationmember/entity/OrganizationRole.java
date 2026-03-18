package com.clickchecker.organizationmember.entity;

public enum OrganizationRole {
    OWNER(3),
    ADMIN(2),
    VIEWER(1);

    private final int level;

    OrganizationRole(int level) {
        this.level = level;
    }

    public boolean isAtLeast(OrganizationRole requiredRole) {
        return this.level >= requiredRole.level;
    }
}
