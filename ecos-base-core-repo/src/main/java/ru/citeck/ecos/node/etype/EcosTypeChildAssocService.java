package ru.citeck.ecos.node.etype;

import lombok.RequiredArgsConstructor;
import org.alfresco.model.ContentModel;
import org.alfresco.service.cmr.repository.NodeRef;
import org.alfresco.service.cmr.repository.NodeService;
import org.alfresco.service.namespace.NamespaceService;
import org.alfresco.service.namespace.QName;
import org.apache.commons.lang.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import ru.citeck.ecos.commons.data.ObjectData;
import ru.citeck.ecos.node.EcosTypeService;
import ru.citeck.ecos.records2.RecordConstants;
import ru.citeck.ecos.records3.RecordsService;
import ru.citeck.ecos.webapp.api.entity.EntityRef;

import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

@Service
@RequiredArgsConstructor(onConstructor_={@Autowired})
public class EcosTypeChildAssocService {

    private final NodeService nodeService;
    private final EcosTypeService ecosTypeService;
    private final RecordsService recordsService;
    private final NamespaceService namespaceService;

    public QName getChildAssoc(@NotNull NodeRef parentRef,
                               @Nullable EntityRef childTypeRef,
                               @NotNull ObjectData childAtts) {

        if (childTypeRef == null || EntityRef.isEmpty(childTypeRef)) {
            return tryToEvalChildAssoc(parentRef, childAtts);
        }
        EntityRef parentTypeRef = ecosTypeService.getEcosType(parentRef);
        if (EntityRef.isEmpty(parentTypeRef)) {
            return tryToEvalChildAssoc(parentRef, childAtts);
        }
        Map<String, String> alfChildAssocs = recordsService.getAtt(
            parentTypeRef,
            "inhAttributes.alfChildAssocs?str"
        ).asMap(String.class, String.class);

        if (!alfChildAssocs.isEmpty()) {
            AtomicReference<String> assocType = new AtomicReference<>();
            ecosTypeService.forEachAsc(childTypeRef, typeDto -> {
                String childAssoc = alfChildAssocs.get(typeDto.getId());
                if (StringUtils.isNotBlank(childAssoc)) {
                    assocType.set(childAssoc);
                    return true;
                }
                return false;
            });
            if (assocType.get() != null) {
                return QName.resolveToQName(namespaceService, assocType.get());
            }
        }

        return tryToEvalChildAssoc(parentRef, childAtts);
    }

    private QName tryToEvalChildAssoc(NodeRef parentRef, ObjectData childAtts) {

        String parentAtt = childAtts.get(RecordConstants.ATT_PARENT_ATT, "");
        if (!parentAtt.isEmpty()) {
            return QName.resolveToQName(namespaceService, parentAtt);
        }
        QName parentType = nodeService.getType(parentRef);
        if (ContentModel.TYPE_CONTAINER.equals(parentType)) {
            return ContentModel.ASSOC_CHILDREN;
        } else if (ContentModel.TYPE_CATEGORY.equals(parentType)) {
            return ContentModel.ASSOC_SUBCATEGORIES;
        }
        return ContentModel.ASSOC_CONTAINS;
    }
}
