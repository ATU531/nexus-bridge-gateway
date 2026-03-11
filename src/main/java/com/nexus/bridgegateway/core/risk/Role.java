package com.nexus.bridgegateway.core.risk;

import java.util.Set;

/**
 * 角色实体
 */
public class Role {

    private String roleId;
    private String roleName;
    private Set<Permission> permissions;

    public String getRoleId() {
        return roleId;
    }

    public void setRoleId(String roleId) {
        this.roleId = roleId;
    }

    public String getRoleName() {
        return roleName;
    }

    public void setRoleName(String roleName) {
        this.roleName = roleName;
    }

    public Set<Permission> getPermissions() {
        return permissions;
    }

    public void setPermissions(Set<Permission> permissions) {
        this.permissions = permissions;
    }

    /**
     * 检查是否拥有指定权限
     */
    public boolean hasPermission(Permission permission) {
        return permissions != null && permissions.contains(permission);
    }
}
