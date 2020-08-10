package ru.citeck.ecos.records.source.common;

import lombok.AllArgsConstructor;
import org.alfresco.service.cmr.repository.NodeRef;
import org.alfresco.service.cmr.repository.NodeService;
import org.alfresco.service.namespace.NamespaceService;
import org.alfresco.service.namespace.QName;
import org.apache.commons.lang.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import ru.citeck.ecos.records.source.alf.AlfNodesRecordsDAO;
import ru.citeck.ecos.records2.RecordRef;
import ru.citeck.ecos.records2.graphql.meta.value.MetaField;
import ru.citeck.ecos.records2.graphql.meta.value.MetaValue;
import ru.citeck.ecos.records2.source.common.AttributesMixin;

import javax.annotation.PostConstruct;
import java.util.Collections;
import java.util.List;

@Component
public class AspectsMixin implements AttributesMixin<Class<RecordRef>, RecordRef> {
    public static final String ATTRIBUTE_NAME = "aspects";

    private NamespaceService namespaceService;
    private NodeService nodeService;
    private AlfNodesRecordsDAO alfNodesRecordsDao;

    @Autowired
    public AspectsMixin(NamespaceService namespaceService,
                        NodeService nodeService,
                        AlfNodesRecordsDAO alfNodesRecordsDao) {
        this.namespaceService = namespaceService;
        this.nodeService = nodeService;
        this.alfNodesRecordsDao = alfNodesRecordsDao;
    }

    @PostConstruct
    public void setup() {
        alfNodesRecordsDao.addAttributesMixin(this);
    }

    @Override
    public List<String> getAttributesList() {
        return Collections.singletonList(ATTRIBUTE_NAME);
    }

    @Override
    public Object getAttribute(String s, RecordRef recordRef, MetaField metaField) {
        NodeRef nodeRef = new NodeRef(recordRef.getId());
        return new AspectMetaValue(nodeRef);
    }

    @Override
    public Class<RecordRef> getMetaToRequest() {
        return RecordRef.class;
    }

    @AllArgsConstructor
    private class AspectMetaValue implements MetaValue {
        private NodeRef nodeRef;

        @Override
        public boolean has(String name) {
            if (StringUtils.isBlank(name)) {
                return false;
            }

            QName qName = QName.resolveToQName(namespaceService, name);

            return nodeService.hasAspect(nodeRef, qName);
        }
    }
}
