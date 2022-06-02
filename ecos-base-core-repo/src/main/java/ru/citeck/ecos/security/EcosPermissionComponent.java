package ru.citeck.ecos.security;

import ru.citeck.ecos.node.AlfNodeInfo;

public interface EcosPermissionComponent {

    default void updateNodePermissions(AlfNodeInfo node) {}

    boolean isAttProtected(AlfNodeInfo node, String attName);

    boolean isAttVisible(AlfNodeInfo node, String attName);
}
