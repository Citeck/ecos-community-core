package ru.citeck.ecos.utils;

import org.alfresco.service.cmr.repository.NodeRef;
import org.alfresco.service.cmr.repository.NodeService;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang.BooleanUtils;
import ru.citeck.ecos.model.EcosModel;

import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

public class PersonUtils {

    public static void excludeDisabledUsers(Collection<NodeRef> users, NodeService nodeService) {
        if (!CollectionUtils.isEmpty(users)) {
            List<NodeRef> excludedUsers = users.stream()
                .filter((user) -> isPersonDisabled(user, nodeService))
                .collect(Collectors.toList());
            users.removeAll(excludedUsers);
        }
    }

    public static boolean isPersonDisabled(NodeRef user, NodeService nodeService) {
        return BooleanUtils.isTrue((Boolean) nodeService.getProperty(user, EcosModel.PROP_IS_PERSON_DISABLED));
    }

}
