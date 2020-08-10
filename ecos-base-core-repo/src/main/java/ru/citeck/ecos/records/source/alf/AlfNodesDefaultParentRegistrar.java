package ru.citeck.ecos.records.source.alf;

import org.alfresco.repo.security.authentication.AuthenticationUtil;
import org.alfresco.service.cmr.repository.NodeRef;
import org.alfresco.service.namespace.QName;
import org.springframework.beans.factory.annotation.Autowired;
import ru.citeck.ecos.utils.NodeUtils;

import javax.annotation.PostConstruct;
import java.util.HashMap;
import java.util.Map;

public class AlfNodesDefaultParentRegistrar {

    private AlfNodesRecordsDAO alfNodesRecordsDao;
    private Map<QName, String> defaultParents;

    private NodeUtils nodeUtils;

    @PostConstruct
    void register() {

        if (defaultParents != null) {

            AuthenticationUtil.runAsSystem(() -> {

                Map<QName, NodeRef> nodeRefs = new HashMap<>();
                defaultParents.forEach((type, node) ->
                        nodeRefs.put(type, nodeUtils.getNodeRef(node))
                );
                alfNodesRecordsDao.registerDefaultParentByType(nodeRefs);

                return null;
            });
        }
    }

    public void setDefaultParents(Map<QName, String> defaultParents) {
        this.defaultParents = defaultParents;
    }

    @Autowired
    public void setNodeUtils(NodeUtils nodeUtils) {
        this.nodeUtils = nodeUtils;
    }

    @Autowired
    public void setAlfNodesRecordsDao(AlfNodesRecordsDAO alfNodesRecordsDao) {
        this.alfNodesRecordsDao = alfNodesRecordsDao;
    }
}
