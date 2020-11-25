package ru.citeck.ecos.utils;

import org.alfresco.model.ContentModel;
import org.alfresco.repo.security.authentication.AuthenticationUtil;
import org.alfresco.service.cmr.repository.NodeRef;
import org.alfresco.service.cmr.repository.NodeService;
import org.alfresco.service.cmr.security.AuthorityService;
import org.alfresco.service.cmr.security.AuthorityType;
import org.alfresco.service.namespace.QName;
import org.apache.commons.lang.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import ru.citeck.ecos.node.DisplayNameService;

import java.io.Serializable;
import java.util.*;
import java.util.stream.Collectors;

@Component
public class AuthorityUtils {

    private AuthorityService authorityService;
    private NodeService nodeService;
    private DisplayNameService displayNameService;

    public Set<String> getContainedUsers(NodeRef rootRef, boolean immediate) {
        return getContainedUsers(getAuthorityName(rootRef), immediate);
    }

    public Set<String> getContainedUsers(String rootName, boolean immediate) {
        return authorityService.getContainedAuthorities(AuthorityType.USER, rootName, immediate);
    }

    public String getAuthorityName(NodeRef authority) {
        Map<QName, Serializable> properties = nodeService.getProperties(authority);
        String name = (String) properties.get(ContentModel.PROP_AUTHORITY_NAME);
        if (StringUtils.isBlank(name)) {
            name = (String) properties.get(ContentModel.PROP_USERNAME);
        }
        return name;
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

    public NodeRef getNodeRef(String authority) {
        if (authority == null) {
            return null;
        }
        if (authority.startsWith("workspace://SpacesStore/")) {
            return new NodeRef(authority);
        }
        return authorityService.getAuthorityNodeRef(authority);
    }

    public Set<NodeRef> getUserAuthoritiesRefs(String userName) {
        return getNodeRefs(getUserAuthorities(userName));
    }

    public Set<String> getUserAuthorities() {
        return getUserAuthorities(AuthenticationUtil.getRunAsUser());
    }

    public Set<NodeRef> getUserAuthoritiesRefs() {
        return getUserAuthoritiesRefs(AuthenticationUtil.getRunAsUser());
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
}
