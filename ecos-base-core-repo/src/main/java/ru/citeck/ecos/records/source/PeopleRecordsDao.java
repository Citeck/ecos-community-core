package ru.citeck.ecos.records.source;

import lombok.NonNull;
import org.alfresco.repo.security.authentication.AuthenticationUtil;
import org.alfresco.service.cmr.repository.NodeRef;
import org.alfresco.service.cmr.search.SearchService;
import org.alfresco.service.cmr.security.AuthorityService;
import org.alfresco.service.cmr.security.MutableAuthenticationService;
import org.apache.commons.lang.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import ru.citeck.ecos.commons.data.DataValue;
import ru.citeck.ecos.commons.data.ObjectData;
import ru.citeck.ecos.records.source.alf.AlfNodesRecordsDAO;
import ru.citeck.ecos.records.source.alf.meta.AlfNodeRecord;
import ru.citeck.ecos.records2.QueryContext;
import ru.citeck.ecos.records2.RecordMeta;
import ru.citeck.ecos.records2.RecordRef;
import ru.citeck.ecos.records2.graphql.meta.value.EmptyValue;
import ru.citeck.ecos.records2.graphql.meta.value.MetaEdge;
import ru.citeck.ecos.records2.graphql.meta.value.MetaField;
import ru.citeck.ecos.records2.graphql.meta.value.MetaValue;
import ru.citeck.ecos.records2.request.delete.RecordsDelResult;
import ru.citeck.ecos.records2.request.delete.RecordsDeletion;
import ru.citeck.ecos.records2.request.mutation.RecordsMutResult;
import ru.citeck.ecos.records2.request.mutation.RecordsMutation;
import ru.citeck.ecos.records2.request.query.RecordsQuery;
import ru.citeck.ecos.records2.request.query.RecordsQueryResult;
import ru.citeck.ecos.records2.source.dao.MutableRecordsDao;
import ru.citeck.ecos.records2.source.dao.local.LocalRecordsDao;
import ru.citeck.ecos.records2.source.dao.local.v2.LocalRecordsMetaDao;
import ru.citeck.ecos.records2.source.dao.local.v2.LocalRecordsQueryWithMetaDao;
import ru.citeck.ecos.utils.AuthorityUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Component
public class PeopleRecordsDao extends LocalRecordsDao
    implements LocalRecordsQueryWithMetaDao<PeopleRecordsDao.UserValue>,
    LocalRecordsMetaDao<Object>,
    MutableRecordsDao {

    public static final String ID = "people";
    private static final RecordRef ETYPE = RecordRef.valueOf("emodel/type@person");

    private static final String PROP_USER_NAME = "userName";
    private static final String PROP_CM_USER_NAME = "cm:" + PROP_USER_NAME;

    private static final String PROP_FULL_NAME = "fullName";
    private static final String PROP_IS_AVAILABLE = "isAvailable";
    private static final String PROP_IS_MUTABLE = "isMutable";
    private static final String PROP_IS_ADMIN = "isAdmin";
    private static final String PROP_AUTHORITIES = "authorities";
    private static final String ECOS_OLD_PASS = "ecos:oldPass";
    private static final String ECOS_PASS = "ecos:pass";
    private static final String ECOS_PASS_VERIFY = "ecos:passVerify";

    private final AuthorityUtils authorityUtils;
    private final AuthorityService authorityService;
    private final AlfNodesRecordsDAO alfNodesRecordsDao;
    private final MutableAuthenticationService authenticationService;

    @Autowired
    public PeopleRecordsDao(AuthorityUtils authorityUtils,
                            AuthorityService authorityService,
                            AlfNodesRecordsDAO alfNodesRecordsDao,
                            MutableAuthenticationService authenticationService) {
        setId(ID);
        this.authorityUtils = authorityUtils;
        this.authorityService = authorityService;
        this.alfNodesRecordsDao = alfNodesRecordsDao;
        this.authenticationService = authenticationService;
    }

    @Override
    public RecordsDelResult delete(RecordsDeletion deletion) {
        throw new UnsupportedOperationException();
    }

    @Override
    public RecordsMutResult mutateImpl(RecordsMutation mutation) {

        List<RecordMeta> handledMeta = mutation.getRecords().stream()
            .map(this::handleMeta)
            .collect(Collectors.toList());

        mutation.setRecords(handledMeta);

        return alfNodesRecordsDao.mutate(mutation);
    }

    private RecordMeta handleMeta(RecordMeta meta) {

        String username = meta.getId().getId();

        if (meta.hasAttribute(ECOS_PASS)) {
            String oldPass = meta.getAttribute(ECOS_OLD_PASS).asText();
            String newPass = meta.getAttribute(ECOS_PASS).asText();
            String verifyPass = meta.getAttribute(ECOS_PASS_VERIFY).asText();

            this.updatePassword(username, oldPass, newPass, verifyPass);

        }

        //  search and set nodeRef for requested user
        meta.setId(authorityService.getAuthorityNodeRef(username).toString());

        ObjectData attributes = meta.getAttributes();
        attributes.remove(ECOS_OLD_PASS);
        attributes.remove(ECOS_PASS);
        attributes.remove(ECOS_PASS_VERIFY);

        return meta;
    }

    /**
     * Update Alfresco's user password value
     */
    private void updatePassword(@NonNull String username,
                                String oldPass,
                                @NonNull String newPass,
                                @NonNull String verifyPass) {

        if (!newPass.equals(verifyPass)) {
            throw new RuntimeException("New password verification failed");
        }

        String currentAuthUser = AuthenticationUtil.getFullyAuthenticatedUser();
        if (StringUtils.isNotEmpty(oldPass) && currentAuthUser.equals(username)) {
            authenticationService.updateAuthentication(username, oldPass.toCharArray(), newPass.toCharArray());
        } else {
            boolean isAdmin = authorityService.isAdminAuthority(currentAuthUser);
            if (isAdmin) {
                authenticationService.setAuthentication(username, newPass.toCharArray());
            } else {
                throw new RuntimeException("Modification of user credentials is not allowed for current user");
            }
        }
    }

    @Override
    public List<Object> getLocalRecordsMeta(List<RecordRef> records, MetaField metaField) {
        return records.stream()
            .map(r -> {
                String authName = r.toString();
                if (authorityService.authorityExists(authName)) {
                    return new UserValue(authName);
                } else {
                    return EmptyValue.INSTANCE;
                }
            })
            .collect(Collectors.toList());
    }

    @Override
    public RecordsQueryResult<UserValue> queryLocalRecords(RecordsQuery query, MetaField metaField) {

        if (SearchService.LANGUAGE_FTS_ALFRESCO.equals(query.getLanguage())) {

            RecordsQueryResult<RecordRef> records = alfNodesRecordsDao.queryRecords(query);
            return new RecordsQueryResult<>(records, UserValue::new);
        }

        DataValue queryNode = query.getQuery(DataValue.class);

        if (queryNode.isNull()) {

            NodeRef ref = authorityService.getAuthorityNodeRef(authenticationService.getCurrentUserName());
            if (ref != null) {
                RecordsQueryResult<UserValue> result = new RecordsQueryResult<>();
                result.addRecord(new UserValue(ref));
                result.setTotalCount(1);
                result.setHasMore(false);
                return result;
            }
        }

        return new RecordsQueryResult<>();
    }

    public class UserValue implements MetaValue {

        private final AlfNodeRecord alfNode;
        private String userName;
        private UserAuthorities userAuthorities;

        UserValue(String userName) {
            this.userName = userName;
            NodeRef nodeRef = authorityService.getAuthorityNodeRef(userName);
            alfNode = new AlfNodeRecord(RecordRef.create("", nodeRef.toString()));
        }

        UserValue(NodeRef nodeRef) {
            this.alfNode = new AlfNodeRecord(RecordRef.create("", nodeRef.toString()));
        }

        UserValue(RecordRef recordRef) {
            this.alfNode = new AlfNodeRecord(recordRef);
        }

        @Override
        public <T extends QueryContext> void init(T context, MetaField metaField) {

            alfNode.init(context, metaField);

            if (userName == null) {
                try {
                    List<? extends MetaValue> attribute = alfNode.getAttribute(PROP_CM_USER_NAME, metaField);
                    userName = attribute.stream()
                        .findFirst()
                        .map(MetaValue::getString)
                        .orElse(null);
                } catch (Exception e) {
                    throw new RuntimeException("Error! " + alfNode.getId(), e);
                }
                if (userName == null) {
                    userName = "";
                }
            }
        }

        @Override
        public MetaEdge getEdge(@NotNull String name, @NotNull MetaField field) {
            if (alfNode == null) {
                return null;
            }
            return alfNode.getEdge(name, field);
        }

        @Override
        public String getString() {
            return getId();
        }

        @Override
        public String getId() {
            return userName;
        }

        @Override
        public String getDisplayName() {
            return alfNode.getDisplayName();
        }

        private UserAuthorities getUserAuthorities() {
            if (userAuthorities == null) {
                userAuthorities = new UserAuthorities(userName);
            }
            return userAuthorities;
        }

        @Override
        public Object getAttribute(String name, MetaField field) {

            switch (name) {
                case PROP_USER_NAME:
                    return userName;
                case PROP_FULL_NAME:
                    return alfNode.getDisplayName();
                case PROP_IS_AVAILABLE:
                    return authenticationService.getAuthenticationEnabled(userName);
                case PROP_IS_MUTABLE:
                    return authenticationService.isAuthenticationMutable(userName);
                case PROP_IS_ADMIN:
                    return authorityService.isAdminAuthority(userName);
                case PROP_AUTHORITIES:
                    return getUserAuthorities();
                case "nodeRef":
                    return alfNode != null ? alfNode.getId() : null;
            }

            return alfNode.getAttribute(name, field);
        }

        @Override
        public RecordRef getRecordType() {
            return ETYPE;
        }
    }

    private class UserAuthorities implements MetaValue {

        private final String userName;
        private Set<String> authorities;

        UserAuthorities(String userName) {
            this.userName = userName;
        }

        @Override
        public String getString() {
            return null;
        }

        @Override
        public Object getAttribute(String name, MetaField field) {
            if ("list".equals(name)) {
                return new ArrayList<>(getAuthorities());
            }
            return null;
        }

        private Set<String> getAuthorities() {
            if (authorities == null) {
                authorities = authorityUtils.getUserAuthorities(userName);
            }
            return authorities;
        }

        @Override
        public boolean has(String authority) {
            return getAuthorities().contains(authority);
        }
    }
}
