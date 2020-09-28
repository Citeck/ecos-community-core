package ru.citeck.ecos.security;

import ru.citeck.ecos.node.AlfNodeInfo;

public interface EcosPermissionComponent {

    boolean isAttProtected(AlfNodeInfo node, String attName);

    boolean isAttVisible(AlfNodeInfo node, String attName);
}
