package ru.citeck.ecos.records.source.alf.assoc.dao;

import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.alfresco.model.ContentModel;
import org.alfresco.service.cmr.dictionary.AssociationDefinition;
import org.alfresco.service.cmr.repository.NodeRef;
import org.alfresco.service.cmr.security.AuthorityService;
import org.alfresco.service.namespace.QName;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import ru.citeck.ecos.context.lib.auth.AuthContext;
import ru.citeck.ecos.utils.AuthorityUtils;

import java.util.Collections;
import java.util.Set;

@Slf4j
@Component
@RequiredArgsConstructor(onConstructor_={@Autowired})
public class AlfCmMemberAssocDao implements AlfAssocDao {

    private static final Set<QName> Q_NAMES = Collections.singleton(ContentModel.ASSOC_MEMBER);

    private final AuthorityUtils authorityUtils;
    private final AuthorityService authorityService;

    @Override
    public void create(NodeRef sourceRef, NodeRef targetRef, AssociationDefinition assoc) {
        AuthoritiesInfo authoritiesInfo = getAuthoritiesInfo(sourceRef, targetRef);
        log.info("[" + AuthContext.getCurrentUser() + "] Add member: " + authoritiesInfo);
        authorityService.addAuthority(authoritiesInfo.parentName, authoritiesInfo.childName);
    }

    @Override
    public void remove(NodeRef sourceRef, NodeRef targetRef, AssociationDefinition assoc) {
        AuthoritiesInfo authoritiesInfo = getAuthoritiesInfo(sourceRef, targetRef);
        log.info("[" + AuthContext.getCurrentUser() + "] Remove member: " + authoritiesInfo);
        authorityService.removeAuthority(authoritiesInfo.parentName, authoritiesInfo.childName);
    }

    private AuthoritiesInfo getAuthoritiesInfo(NodeRef sourceRef, NodeRef targetRef) {

        String parentAuthName = authorityUtils.getAuthorityName(sourceRef);
        if (StringUtils.isBlank(parentAuthName)) {
            throw new IllegalArgumentException("Parent of cm:member association " +
                "is not an authority. NodeRef: " + sourceRef);
        }
        String childAuthName = authorityUtils.getAuthorityName(targetRef);
        if (StringUtils.isBlank(childAuthName)) {
            throw new IllegalArgumentException("Child of cm:member association " +
                "is not an authority. NodeRef: " + targetRef);
        }
        return new AuthoritiesInfo(parentAuthName, childAuthName);
    }

    @Override
    public Set<QName> getQNames() {
        return Q_NAMES;
    }

    @Override
    public float getOrder() {
        return 100f;
    }

    @Data
    @RequiredArgsConstructor
    private static class AuthoritiesInfo {

        private final String parentName;
        private final String childName;

        @Override
        public String toString() {
            return "{" +
                "\"parent\":\"" + parentName + "\"," +
                "\"child\":\"" + childName + "\"" +
                "}";
        }
    }
}
