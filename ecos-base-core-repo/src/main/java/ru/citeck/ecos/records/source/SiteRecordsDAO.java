package ru.citeck.ecos.records.source;

import org.alfresco.repo.security.authentication.AuthenticationUtil;
import org.alfresco.service.cmr.site.SiteService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import ru.citeck.ecos.records2.RecordRef;
import ru.citeck.ecos.records2.graphql.meta.value.MetaField;
import ru.citeck.ecos.records2.graphql.meta.value.MetaValue;
import ru.citeck.ecos.records2.request.query.RecordsQuery;
import ru.citeck.ecos.records2.request.query.RecordsQueryResult;
import ru.citeck.ecos.records2.source.dao.local.LocalRecordsDAO;
import ru.citeck.ecos.records2.source.dao.local.RecordsMetaLocalDAO;
import ru.citeck.ecos.records2.source.dao.local.RecordsQueryWithMetaLocalDAO;
import ru.citeck.ecos.utils.NewUIUtils;

import java.util.List;
import java.util.stream.Collectors;

@Component
public class SiteRecordsDAO extends LocalRecordsDAO
                            implements RecordsQueryWithMetaLocalDAO<SiteRecordsDAO.SiteRecord>,
                                       RecordsMetaLocalDAO<SiteRecordsDAO.SiteRecord> {

    public static final String ID = "site";

    private final SiteService siteService;
    private final NewUIUtils newUIUtils;

    @Autowired
    public SiteRecordsDAO(SiteService siteService, NewUIUtils newUIUtils) {
        this.siteService = siteService;
        this.newUIUtils = newUIUtils;
        setId(ID);
    }

    @Override
    public List<SiteRecord> getMetaValues(List<RecordRef> records) {
        return records.stream().map(r -> new SiteRecord(r.getId())).collect(Collectors.toList());
    }

    @Override
    public RecordsQueryResult<SiteRecord> getMetaValues(RecordsQuery query) {

        List<SiteRecord> sites = siteService.listSites(AuthenticationUtil.getRunAsUser())
                                            .stream()
                                            .map(s -> new SiteRecord(s.getShortName()))
                                            .collect(Collectors.toList());

        RecordsQueryResult<SiteRecord> result = new RecordsQueryResult<>();
        result.setRecords(sites);

        return result;
    }

    public class SiteRecord implements MetaValue {

        private final String id;

        SiteRecord(String id) {
            this.id = id;
        }

        @Override
        public String getId() {
            return id;
        }

        @Override
        public Object getAttribute(String name, MetaField field) {

            switch (name) {
                case "uiType":
                    return newUIUtils.getUITypeForRecord(RecordRef.create(ID, id));
            }

            return null;
        }

        @Override
        public RecordRef getRecordType() {
            return RecordRef.create("emodel", "type", "site");
        }

        @Override
        public String getDisplayName() {
            return siteService.getSite(id).getTitle();
        }
    }
}
