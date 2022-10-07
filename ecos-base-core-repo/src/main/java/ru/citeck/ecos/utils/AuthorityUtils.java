package ru.citeck.ecos.utils;

import org.alfresco.model.ContentModel;
import org.alfresco.service.cmr.dictionary.DictionaryService;
import org.alfresco.service.cmr.repository.InvalidNodeRefException;
import org.alfresco.service.cmr.repository.NodeRef;
import org.alfresco.service.cmr.repository.NodeService;
import org.alfresco.service.cmr.security.AuthenticationService;
import org.alfresco.service.cmr.security.AuthorityService;
import org.alfresco.service.cmr.security.AuthorityType;
import org.alfresco.service.namespace.QName;
import org.alfresco.util.Pair;
import org.apache.commons.lang.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import ru.citeck.ecos.commons.data.DataValue;
import ru.citeck.ecos.node.DisplayNameService;
import ru.citeck.ecos.records.source.PeopleRecordsDao;
import ru.citeck.ecos.records.source.alf.meta.AlfNodeRecord;
import ru.citeck.ecos.records2.RecordRef;

import java.io.Serializable;
import java.util.*;
import java.util.stream.Collectors;

@Component
public class AuthorityUtils {

    private static final String MODEL_PERSON_PREFIX = "emodel/person@";
    private static final String MODEL_GROUP_PREFIX = "emodel/authority-group@";
    private static final String PEOPLE_PREFIX = PeopleRecordsDao.ID + "@";
    private static final String ALFRESCO_PEOPLE_PREFIX = "alfresco/" + PEOPLE_PREFIX;

    private static final List<Pair<String, String>> AUTHORITY_REFS_PREFIXES_WITH_REPLACEMENT = Arrays.asList(
        new Pair<>(MODEL_PERSON_PREFIX, ""),
        new Pair<>(MODEL_GROUP_PREFIX, "GROUP_"),
        new Pair<>(ALFRESCO_PEOPLE_PREFIX, ""),
        new Pair<>(PEOPLE_PREFIX, "")
    );

    private AuthorityService authorityService;
    private AuthenticationService authenticationService;
    private NodeService nodeService;
    private DisplayNameService displayNameService;
    private DictionaryService dictionaryService;

    public Set<String> getContainedUsers(NodeRef rootRef, boolean immediate) {
        return getContainedUsers(getAuthorityName(rootRef), immediate);
    }

    public Set<String> getContainedUsers(String rootName, boolean immediate) {
        return authorityService.getContainedAuthorities(AuthorityType.USER, rootName, immediate);
    }

    @Nullable
    public String getAuthorityName(NodeRef authority) {
        Map<QName, Serializable> properties = nodeService.getProperties(authority);
        String name = (String) properties.get(ContentModel.PROP_AUTHORITY_NAME);
        if (StringUtils.isBlank(name)) {
            name = (String) properties.get(ContentModel.PROP_USERNAME);
        }
        return name;
    }

    @NotNull
    public String getAuthorityNameNotNull(@NotNull Object authority) {
        String authorityName = getAuthorityName(authority);
        if (StringUtils.isBlank(authorityName)) {
            throw new IllegalArgumentException("Authority name can't be calculated for authority: " + authority);
        }
        return authorityName;
    }

    public String getAuthorityName(@Nullable Object authority) {
        if (authority == null) {
            return null;
        }
        if (authority instanceof NodeRef) {
            return getAuthorityName((NodeRef) authority);
        }
        String authorityStr = anyAuthorityToNormalizedStr(authority);
        if (StringUtils.isBlank(authorityStr)) {
            return null;
        }
        if (authorityStr.startsWith(NodeUtils.WORKSPACE_SPACES_STORE_PREFIX)) {
            return getAuthorityName(new NodeRef(authorityStr));
        }
        return normalizePrefixedAuthority(authorityStr);
    }

    public Set<String> getUserAuthorities(String userName) {
        if (StringUtils.isBlank(userName)) {
            return Collections.emptySet();
        }
        Set<String> groups = authorityService.getAuthoritiesForUser(userName);
        Set<String> result = new HashSet<>(groups);
        result.add(userName);
        return result;
    }

    /**
     * Get the authorities that contain the given authority,
     *
     * For example, this method can be used find out all the authorities that contain a
     * group or user.
     *
     * @param authorityName -
     *            the name of the authority for which the containing authorities
     *            are required.
     * @return Set<String>
     */
    public Set<String> getContainingAuthorities(String authorityName) {
        return authorityService.getContainingAuthoritiesInZone(null, authorityName,
            AuthorityService.ZONE_APP_DEFAULT,
            null, 1000);
    }

    public Set<NodeRef> getContainingAuthoritiesRefs(String authorityName) {
        return getNodeRefs(getContainingAuthorities(authorityName));
    }

    public Set<NodeRef> getNodeRefs(Set<String> authorities) {
        return authorities.stream()
            .map(a -> Optional.ofNullable(getNodeRef(a)))
            .filter(Optional::isPresent)
            .map(Optional::get)
            .collect(Collectors.toSet());
    }

    public String getDisplayName(String authority) {

        if (StringUtils.isBlank(authority)) {
            return authority;
        }

        NodeRef authorityNodeRef = authorityService.getAuthorityNodeRef(authority);
        if (authorityNodeRef == null) {
            return authority;
        }

        String result = displayNameService.getDisplayName(authorityNodeRef);
        if (StringUtils.isBlank(result)) {
            result = authority;
        }
        return result;
    }

    public boolean isAuthorityRef(Object authority) {
        String authorityStr = anyAuthorityToNormalizedStr(authority);
        if (StringUtils.isBlank(authorityStr)) {
            return false;
        }
        if (NodeUtils.isNodeRefWithUuid(authorityStr)) {
            try {
                QName type = nodeService.getType(new NodeRef(authorityStr));
                return dictionaryService.isSubClass(type, ContentModel.TYPE_AUTHORITY);
            } catch (InvalidNodeRefException e) {
                return false;
            }
        }
        return AUTHORITY_REFS_PREFIXES_WITH_REPLACEMENT.stream()
            .map(Pair::getFirst)
            .anyMatch(prefix -> authorityStr.startsWith(prefix) && !authorityStr.endsWith("@"));
    }

    @NotNull
    public NodeRef getNodeRefNotNull(Object authority) {
        NodeRef nodeRef = getNodeRef(authority);
        if (nodeRef == null) {
            throw new RuntimeException("Authority is not found: " + authority);
        }
        return nodeRef;
    }

    @Nullable
    public NodeRef getNodeRef(Object authority) {
        if (authority instanceof NodeRef) {
            return (NodeRef) authority;
        }
        return getNodeRefByNormalizedStrRef(anyAuthorityToNormalizedStr(authority));
    }

    @Nullable
    public NodeRef getNodeRef(String authority) {
        return getNodeRefByNormalizedStrRef(anyAuthorityToNormalizedStr(authority));
    }

    @Nullable
    private NodeRef getNodeRefByNormalizedStrRef(String authority) {
        if (StringUtils.isBlank(authority)) {
            return null;
        }
        if (authority.startsWith(NodeUtils.WORKSPACE_SPACES_STORE_PREFIX)) {
            return new NodeRef(authority);
        }
        return authorityService.getAuthorityNodeRef(normalizePrefixedAuthority(authority));
    }

    @NotNull
    private String normalizePrefixedAuthority(@NotNull String authority) {
        if (authority.contains("@") && !authority.endsWith("@")) {
            for (Pair<String, String> prefix : AUTHORITY_REFS_PREFIXES_WITH_REPLACEMENT) {
                if (authority.startsWith(prefix.getFirst())) {
                    return authority.replaceFirst(prefix.getFirst(), prefix.getSecond());
                }
            }
        }
        return authority;
    }

    @NotNull
    private String anyAuthorityToNormalizedStr(@Nullable Object authority) {
        String authorityStr = "";
        if (authority == null) {
            return authorityStr;
        }
        if (authority instanceof String) {
            authorityStr = (String) authority;
        } else if (authority instanceof RecordRef || authority instanceof NodeRef) {
            authorityStr = authority.toString();
        } else if (authority instanceof DataValue && ((DataValue) authority).isTextual()) {
            authorityStr = ((DataValue) authority).asText();
        }
        if (authorityStr.startsWith(AlfNodeRecord.NODE_REF_SOURCE_ID_PREFIX) && !authorityStr.endsWith("@")) {
            authorityStr = authorityStr.substring(AlfNodeRecord.NODE_REF_SOURCE_ID_PREFIX.length());
        }
        return authorityStr;
    }

    public Set<NodeRef> getUserAuthoritiesRefs(String userName) {
        return getNodeRefs(getUserAuthorities(userName));
    }

    public Set<String> getUserAuthorities() {
        return getUserAuthorities(authenticationService.getCurrentUserName());
    }

    public Set<NodeRef> getUserAuthoritiesRefs() {
        return getUserAuthoritiesRefs(authenticationService.getCurrentUserName());
    }

    @Autowired
    public void setDisplayNameService(DisplayNameService displayNameService) {
        this.displayNameService = displayNameService;
    }

    @Autowired
    @Qualifier("authorityService")
    public void setAuthorityService(AuthorityService authorityService) {
        this.authorityService = authorityService;
    }

    @Autowired
    public void setNodeService(NodeService nodeService) {
        this.nodeService = nodeService;
    }

    @Autowired
    public void setAuthenticationService(AuthenticationService authenticationService) {
        this.authenticationService = authenticationService;
    }

    @Autowired
    public void setDictionaryService(DictionaryService dictionaryService) {
        this.dictionaryService = dictionaryService;
    }
}
