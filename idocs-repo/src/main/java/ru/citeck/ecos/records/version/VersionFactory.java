package ru.citeck.ecos.records.version;

import org.alfresco.model.ContentModel;
import org.alfresco.service.cmr.repository.NodeRef;
import org.alfresco.service.cmr.repository.NodeService;
import org.alfresco.service.cmr.repository.StoreRef;
import org.alfresco.service.cmr.security.AuthorityService;
import org.alfresco.service.cmr.version.Version;
import org.alfresco.service.cmr.version.VersionService;
import org.alfresco.service.namespace.QName;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import ru.citeck.ecos.records.models.UserWithAvatarDto;
import ru.citeck.ecos.records2.RecordRef;
import ru.citeck.ecos.records2.RecordsService;

import java.io.Serializable;
import java.util.Date;
import java.util.Map;

/**
 * @author Roman Makarskiy
 */
@Component
public class VersionFactory {

    private static final String DOWNLOAD_URL_TEMPLATE = "/share/proxy/alfresco/citeck/print/content?nodeRef=%s";
    private static final String FIRST_VERSION = "1.0";

    private final NodeService nodeService;
    private final VersionService versionService;
    private final AuthorityService authorityService;
    private final RecordsService recordsService;

    @Autowired
    public VersionFactory(NodeService nodeService, VersionService versionService, AuthorityService authorityService,
                          RecordsService recordsService) {
        this.nodeService = nodeService;
        this.versionService = versionService;
        this.authorityService = authorityService;
        this.recordsService = recordsService;
    }

    public VersionDTO fromRef(NodeRef ref) {
        if (StoreRef.STORE_REF_WORKSPACE_SPACESSTORE.equals(ref.getStoreRef())) {
            return baseVersion(ref);
        }
        return fromVersionRef(ref);
    }

    public VersionDTO fromVersionRef(NodeRef versionRef) {
        Map<QName, Serializable> properties = nodeService.getProperties(versionRef);
        String uuid = (String) properties.get(ContentModel.PROP_NODE_UUID);
        String versionLabel = (String) properties.get(ContentModel.PROP_VERSION_LABEL);

        NodeRef docRef = new NodeRef(VersionRecordsConstants.SPACES_STORE_PREFIX + uuid);

        Version version = versionService.getVersionHistory(docRef).getVersion(versionLabel);
        return fromVersion(version);
    }

    public VersionDTO fromVersion(Version version) {
        VersionDTO dto = new VersionDTO();

        NodeRef frozenStateNodeRef = version.getFrozenStateNodeRef();

        dto.setId(frozenStateNodeRef.toString());
        dto.setName((String) nodeService.getProperty(frozenStateNodeRef, ContentModel.PROP_NAME));
        dto.setVersion(version.getVersionLabel());
        dto.setComment(version.getDescription());
        dto.setDownloadUrl(generateDownloadUrl(version.getFrozenStateNodeRef().toString()));
        dto.setModified(version.getFrozenModifiedDate());

        String frozenModifier = version.getFrozenModifier();
        UserWithAvatarDto modifier = recordsService.getMeta(RecordRef.create("",
                authorityService.getAuthorityNodeRef(frozenModifier).toString()), UserWithAvatarDto.class);
        dto.setModifier(modifier);

        return dto;
    }

    public VersionDTO baseVersion(NodeRef document) {
        VersionDTO dto = new VersionDTO();

        Map<QName, Serializable> properties = nodeService.getProperties(document);

        String creator = (String) properties.get(ContentModel.PROP_CREATOR);
        UserWithAvatarDto creatorDto = recordsService.getMeta(RecordRef.create("",
                authorityService.getAuthorityNodeRef(creator).toString()), UserWithAvatarDto.class);

        dto.setName((String) properties.get(ContentModel.PROP_NAME));
        dto.setModified((Date) properties.get(ContentModel.PROP_CREATED));
        dto.setModifier(creatorDto);
        dto.setDownloadUrl(generateDownloadUrl(document.toString()));
        dto.setId(document.toString());
        dto.setVersion(FIRST_VERSION);

        return dto;
    }

    private String generateDownloadUrl(String nodeRef) {
        return String.format(DOWNLOAD_URL_TEMPLATE, nodeRef);
    }

}
