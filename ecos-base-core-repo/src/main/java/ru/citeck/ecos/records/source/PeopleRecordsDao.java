package ru.citeck.ecos.records.source;

import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.alfresco.model.ContentModel;
import org.alfresco.repo.security.authentication.AuthenticationUtil;
import org.alfresco.rest.framework.core.exceptions.PermissionDeniedException;
import org.alfresco.service.cmr.repository.NodeRef;
import org.alfresco.service.cmr.search.SearchService;
import org.alfresco.service.cmr.security.AuthorityService;
import org.alfresco.service.cmr.security.AuthorityType;
import org.alfresco.service.cmr.security.MutableAuthenticationService;
import org.alfresco.service.cmr.security.PersonService;
import org.alfresco.service.namespace.NamespaceService;
import org.alfresco.service.namespace.QName;
import org.apache.commons.lang.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import ru.citeck.ecos.commons.data.DataValue;
import ru.citeck.ecos.commons.data.ObjectData;
import ru.citeck.ecos.model.EcosModel;
import ru.citeck.ecos.records.source.alf.AlfNodesRecordsDAO;
import ru.citeck.ecos.records.source.alf.meta.AlfNodeRecord;
import ru.citeck.ecos.records2.QueryContext;
import ru.citeck.ecos.records2.RecordMeta;
import ru.citeck.ecos.records2.RecordRef;
import ru.citeck.ecos.records2.graphql.meta.value.*;
import ru.citeck.ecos.records2.graphql.meta.value.field.EmptyMetaField;
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
import ru.citeck.ecos.records3.record.atts.value.AttValue;
import ru.citeck.ecos.utils.AuthorityUtils;

import java.io.Serializable;
import java.util.*;
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
    private static final String PROP_IS_DISABLED = "isDisabled";
    private static final String PROP_AUTHORITIES = "authorities";
    private static final String ECOS_OLD_PASS = "ecos:oldPass";
    private static final String ECOS_PASS = "ecos:pass";
    private static final String ECOS_PASS_VERIFY = "ecos:passVerify";
    private static final String GROUPS = "groups";

    private static final String GROUP_USERS_PROFILE_ADMIN = "GROUP_USERS_PROFILE_ADMIN";
    private static final String PERMS_WRITE = "Write";
    private static final String ADMIN_USERNAME = "admin";

    private final AuthorityUtils authorityUtils;
    private final AuthorityService authorityService;
    private final AlfNodesRecordsDAO alfNodesRecordsDao;
    private final NamespaceService namespaceService;
    private final MutableAuthenticationService authenticationService;
    private final PersonService personService;

    @Autowired
    public PeopleRecordsDao(AuthorityUtils authorityUtils,
                            PersonService personService,
                            AuthorityService authorityService,
                            NamespaceService namespaceService,
                            AlfNodesRecordsDAO alfNodesRecordsDao,
                            MutableAuthenticationService authenticationService) {
        setId(ID);
        this.authorityUtils = authorityUtils;
        this.personService = personService;
        this.authorityService = authorityService;
        this.namespaceService = namespaceService;
        this.alfNodesRecordsDao = alfNodesRecordsDao;
        this.authenticationService = authenticationService;
    }

    @Override
    public RecordsDelResult delete(RecordsDeletion deletion) {
        throw new UnsupportedOperationException();
    }

    @Override
    public RecordsMutResult mutateImpl(RecordsMutation mutation) {

        RecordsMutResult fullMutResult = new RecordsMutResult();

        for (RecordMeta record : mutation.getRecords()) {

            String personUserName = record.getId().getId();

            RecordMeta handledMeta = this.handleMetaBeforeMutation(record);
            RecordsMutation newMutation = new RecordsMutation(mutation);
            newMutation.setRecords(Collections.singletonList(handledMeta));

            RecordsMutResult mutResult;
            if (personUserName.equals(authenticationService.getCurrentUserName())
                || AuthenticationUtil.isRunAsUserTheSystemUser()
                || authorityService.hasAdminAuthority()) {

                mutResult = alfNodesRecordsDao.mutate(newMutation);

            } else {
                UserValue userValue = new UserValue(record.getId());
                userValue.init(QueryContext.getCurrent(), EmptyMetaField.INSTANCE);
                if (userValue.getPermissions().has(PERMS_WRITE)) {
                    mutResult = AuthenticationUtil.runAs(
                        () -> alfNodesRecordsDao.mutate(newMutation),
                        ADMIN_USERNAME
                    );
                } else {
                    throw new PermissionDeniedException();
                }
            }
            fullMutResult.merge(mutResult);
        }

        return fullMutResult;
    }

    private RecordMeta handleMetaBeforeMutation(RecordMeta meta) {

        String username = meta.getId().getId();
        boolean createIfNotExists = false;

        if (username.isEmpty()) {
            DataValue id = meta.getAtt("id");
            if (id.isTextual() && StringUtils.isNotBlank(id.asText())) {
                username = id.asText();
                createIfNotExists = true;
            }
        }
        if (username.isEmpty()) {
            throw new RuntimeException("UserName can't be empty for person mutation");
        }

        NodeRef personRef = personService.getPersonOrNull(username);
        if (personRef == null) {
            if (!createIfNotExists) {
                throw new RuntimeException("User doesn't exists: " + username);
            }
            Map<QName, Serializable> props = new HashMap<>();
            props.put(ContentModel.PROP_USERNAME, username);
            personRef = personService.createPerson(props);

        } else {

            if (meta.hasAttribute(ECOS_PASS)) {
                String oldPass = meta.getAttribute(ECOS_OLD_PASS).asText();
                String newPass = meta.getAttribute(ECOS_PASS).asText();
                String verifyPass = meta.getAttribute(ECOS_PASS_VERIFY).asText();

                this.updatePassword(username, oldPass, newPass, verifyPass);
            }
        }

        //  search and set nodeRef for requested user
        meta.setId(personRef.toString());

        ObjectData attributes = meta.getAttributes();
        attributes.remove(ECOS_OLD_PASS);
        attributes.remove(ECOS_PASS);
        attributes.remove(ECOS_PASS_VERIFY);

        return meta;
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
        private QueryContext queryContext;

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

            queryContext = context;
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
                case PROP_IS_DISABLED:
                    String isDisabledProp = EcosModel.PROP_IS_PERSON_DISABLED.toPrefixString(namespaceService);
                    return alfNode.getAttribute(isDisabledProp, field);
                case PROP_AUTHORITIES:
                    return getUserAuthorities();
                case "nodeRef":
                    return alfNode != null ? alfNode.getId() : null;
                case GROUPS:
                    return getUserGroups(userName, queryContext, field);
            }

            return alfNode.getAttribute(name, field);
        }

        @Override
        public RecordRef getRecordType() {
            return ETYPE;
        }

        public boolean isAdmin() {
            return authorityService.isAdminAuthority(userName);
        }

        public UserPermissions getPermissions() {
            return new UserPermissions(this);
        }
    }

    @RequiredArgsConstructor
    private class UserPermissions implements AttValue {

        private final UserValue userValue;

        @Nullable
        @Override
        public String asText() throws Exception {
            return null;
        }

        @Override
        public boolean has(String permission) {
            if (PERMS_WRITE.equalsIgnoreCase(permission)
                && !authorityService.hasAdminAuthority()
                && authorityService.getAuthorities().contains(GROUP_USERS_PROFILE_ADMIN)
                && !Objects.equals(authenticationService.getCurrentUserName(), userValue.userName)) {

                return !userValue.isAdmin();
            }
            return userValue.alfNode.getPermissions().has(permission);
        }
    }

    private List<AlfNodeRecord> getUserGroups(String userName, QueryContext context, MetaField metaField) {
        return authorityService.getContainingAuthoritiesInZone(
            AuthorityType.GROUP,
            userName,
            AuthorityService.ZONE_APP_DEFAULT,
            null,
            1000
        ).stream().map(groupId -> {
            NodeRef nodeRef = authorityService.getAuthorityNodeRef(groupId);
            AlfNodeRecord record = new AlfNodeRecord(RecordRef.create("", nodeRef.toString()));
            record.init(context, metaField);
            return record;
        }).collect(Collectors.toList());
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
