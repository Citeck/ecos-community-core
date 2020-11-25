package ru.citeck.ecos.records.version;

import lombok.extern.slf4j.Slf4j;
import org.alfresco.model.ContentModel;
import org.alfresco.repo.version.VersionModel;
import org.alfresco.service.cmr.coci.CheckOutCheckInService;
import org.alfresco.service.cmr.lock.LockService;
import org.alfresco.service.cmr.lock.LockStatus;
import org.alfresco.service.cmr.repository.NodeRef;
import org.alfresco.service.cmr.repository.NodeService;
import org.alfresco.service.cmr.version.Version;
import org.alfresco.service.cmr.version.VersionHistory;
import org.alfresco.service.cmr.version.VersionService;
import org.alfresco.service.cmr.version.VersionType;
import org.alfresco.service.namespace.QName;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import ru.citeck.ecos.commons.data.DataValue;
import ru.citeck.ecos.records2.RecordMeta;
import ru.citeck.ecos.records2.RecordRef;
import ru.citeck.ecos.records2.graphql.meta.value.MetaField;
import ru.citeck.ecos.records2.request.delete.RecordsDelResult;
import ru.citeck.ecos.records2.request.delete.RecordsDeletion;
import ru.citeck.ecos.records2.request.mutation.RecordsMutResult;
import ru.citeck.ecos.records2.request.mutation.RecordsMutation;
import ru.citeck.ecos.records2.request.query.RecordsQuery;
import ru.citeck.ecos.records2.request.query.RecordsQueryResult;
import ru.citeck.ecos.records2.source.dao.local.v2.LocalRecordsCrudDao;
import ru.citeck.ecos.security.EcosPermissionService;

import java.io.Serializable;
import java.util.*;
import java.util.stream.Collectors;

import static ru.citeck.ecos.records.version.VersionRecordsConstants.*;

/**
 * @author Roman Makarskiy
 */
@Component
@Slf4j
public class VersionRecords extends LocalRecordsCrudDao<VersionDTO> {

    private final VersionService versionService;
    private final NodeService nodeService;
    private final LockService lockService;
    private final CheckOutCheckInService checkOutCheckInService;
    private final VersionFactory versionFactory;
    private final EcosPermissionService ecosPermissionService;

    @Autowired
    public VersionRecords(VersionService versionService, NodeService nodeService, LockService lockService,
                          CheckOutCheckInService checkOutCheckInService, VersionFactory versionFactory,
                          EcosPermissionService ecosPermissionService) {
        this.versionService = versionService;
        this.nodeService = nodeService;
        this.lockService = lockService;
        this.checkOutCheckInService = checkOutCheckInService;
        this.versionFactory = versionFactory;
        this.ecosPermissionService = ecosPermissionService;
    }

    private static final String ID = "version";

    {
        setId(ID);
    }

    @Override
    public List<VersionDTO> getValuesToMutate(List<RecordRef> list) {
        return getValues(list);
    }

    @Override
    public List<VersionDTO> getLocalRecordsMeta(List<RecordRef> list, MetaField metaField) {
        return getValues(list);
    }

    private List<VersionDTO> getValues(List<RecordRef> list) {
        List<VersionDTO> result = new ArrayList<>();

        for (RecordRef recordRef : list) {
            String id = recordRef.getId();
            if (StringUtils.isBlank(id)) {
                result.add(new VersionDTO());
                continue;
            }

            if (!NodeRef.isNodeRef(id)) {
                throw new IllegalArgumentException("Record id should be NodeRef format");
            }

            NodeRef nodeRef = new NodeRef(id);
            result.add(versionFactory.fromRef(nodeRef));
        }

        return result;
    }

    @Override
    public RecordsMutResult mutateImpl(RecordsMutation mutation) {
        RecordsMutResult result = new RecordsMutResult();

        for (RecordMeta meta : mutation.getRecords()) {
            if (isRevertAction(meta)) {
                String id = meta.getId().getId();

                NodeRef document = getBaseDocumentFromVersion(new NodeRef(id));

                boolean isContentProtected = ecosPermissionService.isAttributeProtected(document, "cm:content");

                if (isContentProtected) {
                    log.warn(String.format("Change version is not allowed for record <%s>", id));
                    continue;
                }

                if (documentIsLocked(document)) {
                    throw new IllegalStateException(String.format("Record <%s> is locked", id));
                }

                if (!nodeService.hasAspect(document, ContentModel.ASPECT_VERSIONABLE)) {
                    throw new IllegalStateException(String.format("Record <%s> is not versionable", id));
                }

                DataValue revert = meta.getAttribute(ATT_REVERT);

                String versionLabel = revert.has(ATT_VERSION) ? revert.get(ATT_VERSION).asText() : "";
                if (StringUtils.isBlank(versionLabel)) {
                    throw new IllegalArgumentException(String.format("On revert action, attribute: <%s> is mandatory",
                            ATT_VERSION));
                }

                String comment = revert.has(ATT_COMMENT) ? revert.get(ATT_COMMENT).asText() : "";
                boolean majorVersion = revert.has(ATT_MAJOR_VERSION) && revert.get(ATT_MAJOR_VERSION).asBoolean();

                Version version = versionService.getVersionHistory(document).getVersion(versionLabel);
                if (version == null) {
                    throw new IllegalArgumentException(String.format("Version <%s> not found", versionLabel));
                }

                NodeRef workingCopy = revert(document, version, comment, majorVersion);

                result.addRecord(new RecordMeta(workingCopy.toString()));
            }
        }

        return result;
    }

    private boolean isRevertAction(RecordMeta meta) {
        DataValue revert = meta.getAttribute(ATT_REVERT);
        return revert != null && !revert.isNull();
    }

    private NodeRef getBaseDocumentFromVersion(NodeRef versionRef) {
        Map<QName, Serializable> properties = nodeService.getProperties(versionRef);
        String uuid = (String) properties.get(ContentModel.PROP_NODE_UUID);
        return new NodeRef(SPACES_STORE_PREFIX + uuid);
    }

    private boolean documentIsLocked(NodeRef document) {
        boolean locked = false;

        if (nodeService.hasAspect(document, ContentModel.ASPECT_LOCKABLE)) {
            LockStatus status = lockService.getLockStatus(document);
            if (status == LockStatus.LOCKED || status == LockStatus.LOCK_OWNER) {
                locked = true;
            }
        }

        return locked;
    }

    private NodeRef revert(NodeRef document, Version version, String comment, boolean majorVersion) {
        if (nodeService.hasAspect(document, ContentModel.ASPECT_WORKING_COPY)) {
            checkOutCheckInService.cancelCheckout(document);
        }

        versionService.revert(document, version, false);

        NodeRef workingCopy = checkOutCheckInService.checkout(document);

        Map<String, Serializable> props = new HashMap<>(2, 1.0f);
        props.put(Version.PROP_DESCRIPTION, comment);
        props.put(VersionModel.PROP_VERSION_TYPE, majorVersion ? VersionType.MAJOR : VersionType.MINOR);

        return checkOutCheckInService.checkin(workingCopy, props);
    }

    @Override
    public RecordsQueryResult<VersionDTO> queryLocalRecords(RecordsQuery recordsQuery, MetaField metaField) {
        VersionQuery query = recordsQuery.getQuery(VersionQuery.class);
        RecordRef recordRef = query.getRecord();

        if (recordRef == null || StringUtils.isBlank(recordRef.getId())) {
            throw new IllegalArgumentException("You mus specify a record to find versions");
        }

        String id = recordRef.getId();
        if (!NodeRef.isNodeRef(id)) {
            return new RecordsQueryResult<>();
        }

        RecordsQueryResult<VersionDTO> result = new RecordsQueryResult<>();
        NodeRef document = new NodeRef(id);

        boolean isContentViewable = ecosPermissionService.isAttributeVisible(document, "cm:content");

        if (isContentViewable) {

            VersionHistory versionHistory = versionService.getVersionHistory(document);

            if (versionHistory == null || CollectionUtils.isEmpty(versionHistory.getAllVersions())) {
                VersionDTO baseVersion = versionFactory.baseVersion(new NodeRef(id));
                result.setRecords(Collections.singletonList(baseVersion));
                result.setTotalCount(1);
                return result;
            }

            List<VersionDTO> versions = versionHistory.getAllVersions()
                    .stream()
                    .map(versionFactory::fromVersion)
                    .collect(Collectors.toList());

            result.setRecords(versions);
            result.setTotalCount(versions.size());
        }

        return result;
    }

    @Override
    public RecordsMutResult save(List<VersionDTO> list) {
        return null;
    }

    @Override
    public RecordsDelResult delete(RecordsDeletion recordsDeletion) {
        return null;
    }

}
