/*
 * Copyright (C) 2008-2015 Citeck LLC.
 *
 * This file is part of Citeck EcoS
 *
 * Citeck EcoS is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Citeck EcoS is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with Citeck EcoS. If not, see <http://www.gnu.org/licenses/>.
 */
package ru.citeck.ecos.deputy;

import lombok.extern.slf4j.Slf4j;
import org.alfresco.model.ContentModel;
import org.alfresco.repo.security.authentication.AuthenticationUtil;
import org.alfresco.service.cmr.repository.AssociationRef;
import org.alfresco.service.cmr.repository.ChildAssociationRef;
import org.alfresco.service.cmr.repository.NodeRef;
import org.alfresco.service.cmr.repository.NodeService;
import org.alfresco.service.cmr.security.AuthenticationService;
import org.alfresco.service.cmr.security.AuthorityService;
import org.alfresco.service.cmr.security.AuthorityType;
import org.alfresco.service.namespace.QName;
import org.alfresco.util.GUID;
import org.ehcache.Cache;
import org.ehcache.CacheManager;
import org.ehcache.config.builders.CacheConfigurationBuilder;
import org.ehcache.config.builders.ResourcePoolsBuilder;
import org.ehcache.expiry.Duration;
import org.ehcache.expiry.Expirations;
import ru.citeck.ecos.cache.EhcacheConfigurationManager;
import ru.citeck.ecos.model.DeputyModel;
import ru.citeck.ecos.orgstruct.OrgStructService;

import java.util.*;
import java.util.concurrent.TimeUnit;

@Slf4j
public class DeputyServiceImpl implements DeputyService {

    private AuthenticationService authenticationService;
    private AuthorityService authorityService;
    private NodeService nodeService;
    private OrgStructService orgStructService;
    private AuthorityHelper authorityHelper;
    private AvailabilityService availabilityService;

    private CompositeDeputyListener deputyListener;

    private NodeRef deputationRecordsRoot;
    private QName deputationRecordAssoc;
    private String roleGroupType;
    private String branchGroupType;
    private Cache<String, List<String>> usersWhoHaveThisUserDeputyCache;

    /////////////////////////////////////////////////////////////////1+++++++++++++++++++++++++0

    //                      SPRING INTERFACE                       //
    /////////////////////////////////////////////////////////////////

    public void setAuthenticationService(AuthenticationService authenticationService) {
        this.authenticationService = authenticationService;
    }

    public void setAuthorityService(AuthorityService authorityService) {
        this.authorityService = authorityService;
    }

    public void setNodeService(NodeService nodeService) {
        this.nodeService = nodeService;
    }

    public void setOrgStructService(OrgStructService orgStructService) {
        this.orgStructService = orgStructService;
    }

    public void setAuthorityHelper(AuthorityHelper authorityHelper) {
        this.authorityHelper = authorityHelper;
    }

    public void setAvailabilityService(AvailabilityService availabilityService) {
        this.availabilityService = availabilityService;
    }

    public void setDeputationRecordsRoot(NodeRef deputationRecordsRoot) {
        this.deputationRecordsRoot = deputationRecordsRoot;
    }

    public void setDeputationRecordAssoc(QName deputationRecordAssoc) {
        this.deputationRecordAssoc = deputationRecordAssoc;
    }

    public void setRoleGroupType(String roleGroupType) {
        this.roleGroupType = roleGroupType;
    }

    public void setBranchGroupType(String branchGroupType) {
        this.branchGroupType = branchGroupType;
    }


    /////////////////////////////////////////////////////////////////
    //                       ADMIN INTERFACE                       //
    /////////////////////////////////////////////////////////////////

    @Override
    public List<String> getUserDeputies(String userName) {
        NodeRef user = authorityHelper.needUser(userName);
        return getAuthorityNames(getAuthorityDeputies(user, false));
    }

    @Override
    public List<String> getUserAssistants(String userName) {
        NodeRef user = authorityHelper.needUser(userName);
        return getAuthorityNames(getAuthorityDeputies(user, true));
    }

    @Override
    public List<String> getAllUserDeputies(String userName) {
        List<String> result = new ArrayList<>();
        result.addAll(getUserAssistants(userName));
        result.addAll(getUserDeputies(userName));
        return result;
    }

    @Override
    public void addUserDeputies(String userName, List<String> deputies) {
        NodeRef user = authorityHelper.needUser(userName);
        addDeputationRecords(user, getAuthorities(deputies), false, false);
    }

    @Override
    public void addUserAssistants(String userName, List<String> deputies) {
        NodeRef user = authorityHelper.needUser(userName);
        addDeputationRecords(user, getAuthorities(deputies), false, true);
    }

    @Override
    public void removeUserDeputies(String userName, List<String> deputies) {
        NodeRef user = authorityHelper.needUser(userName);
        removeDeputationRecords(user, getAuthorities(deputies), false, false);
    }

    @Override
    public void removeUserAssistants(String userName, List<String> assistants) {
        NodeRef user = authorityHelper.needUser(userName);
        removeDeputationRecords(user, getAuthorities(assistants), false, true);
    }

    @Override
    public List<String> getUsersWhoHaveThisUserDeputy(String userName) {
        if (!usersWhoHaveThisUserDeputyCache.containsKey(userName)) {
            List<String> result = cacheUsersWhoHaveThisUserDeputy(userName);
            usersWhoHaveThisUserDeputyCache.put(userName, result);
            return result;
        } else {
            return usersWhoHaveThisUserDeputyCache.get(userName);
        }
    }

    private List<String> cacheUsersWhoHaveThisUserDeputy(String userName) {
        NodeRef user = authorityHelper.needUser(userName);
        return getAuthorityNames(getUsersDeputiedToUserImpl(user, false));
    }

    @Override
    public List<String> getRoleDeputies(String roleFullName) {
        NodeRef role = authorityHelper.needRole(roleFullName);
        return getAuthorityNames(getAuthorityDeputies(role, false));
    }

    @Override
    public List<String> getRoleAssistants(String roleFullName) {
        NodeRef role = authorityHelper.needRole(roleFullName);
        return getAuthorityNames(getAuthorityDeputies(role, true));
    }

    @Override
    public void addRoleDeputies(String roleFullName, List<String> deputies) {
        NodeRef role = authorityHelper.needRole(roleFullName);
        addDeputationRecords(role, getAuthorities(deputies), true, false);
    }

    @Override
    public void addRoleAssistants(String roleFullName, List<String> deputies) {
        NodeRef role = authorityHelper.needRole(roleFullName);
        addDeputationRecords(role, getAuthorities(deputies), true, true);
    }

    @Override
    public void removeRoleDeputies(String roleFullName, List<String> deputies) {
        NodeRef role = authorityHelper.needRole(roleFullName);
        removeDeputationRecords(role, getAuthorities(deputies), true, false);
    }

    @Override
    public void removeRoleAssistants(String roleFullName, List<String> deputies) {
        NodeRef role = authorityHelper.needRole(roleFullName);
        removeDeputationRecords(role, getAuthorities(deputies), true, true);
    }

    @Override
    public boolean isRoleDeputiedByMembers(String roleFullName) {
        NodeRef role = authorityHelper.needRole(roleFullName);
        return isRoleDeputiedByMembers(role);
    }

    @Override
    public boolean isRoleDeputiedByUser(String roleFullName, String userName) {
        NodeRef role = authorityHelper.needRole(roleFullName);
        NodeRef user = authorityHelper.needUser(userName);

        // zero - all roles are deputied by admins
        if (authorityService.isAdminAuthority(userName)) {
            return true;
        }

        // first - role should be deputied by members
        if (!isRoleDeputiedByMembers(role)) {
            return false;
        }

        // second - user should be the member of this role
        Set<String> userGroups = authorityService.getAuthoritiesForUser(userName);
        if (!userGroups.contains(roleFullName)) {
            return false;
        }

        // third - user should not be the deputy of this role
        NodeRef deputationRecord = this.getDeputationRecord(role, user, false);
        if (deputationRecord != null) {
            return false;
        }

        return true;
    }

    @Override
    public boolean isRoleDeputiedToUser(String roleFullName, String userName) {
        NodeRef role = authorityHelper.needRole(roleFullName);
        NodeRef user = authorityHelper.needUser(userName);
        return getDeputationRecord(role, user, false) != null;
    }

    @Override
    public boolean isRoleAssistedToUser(String roleFullName, String userName) {
        NodeRef role = authorityHelper.needRole(roleFullName);
        NodeRef user = authorityHelper.needUser(userName);
        return getDeputationRecord(role, user, true) != null;
    }

    @Override
    public List<String> getUserRoles(String userName) {
        NodeRef user = authorityHelper.needUser(userName);
        // get all groups where user is member
        List<String> userRoles = orgStructService.getTypedGroupsForUser(userName, roleGroupType);

        // and exclude those, where user is deputy
        List<NodeRef> deputiedRoles = getRolesDeputiedToUserImpl(user);

        // use HashSet for fast search
        Set<NodeRef> deputiedRoleSet = new HashSet<>(deputiedRoles.size());
        deputiedRoleSet.addAll(deputiedRoles);

        List<String> nonDeputiedRoles = new ArrayList<>(deputiedRoles.size());
        for (String roleName : userRoles) {
            NodeRef role = authorityHelper.getRole(roleName);
            if (!deputiedRoleSet.contains(role)) {
                nonDeputiedRoles.add(roleName);
            }
        }
        return nonDeputiedRoles;
    }

    @Override
    public List<String> getUserBranches(String userName) {
        return orgStructService.getTypedGroupsForUser(userName, branchGroupType);
    }

    @Override
    public List<String> getRoleMembers(String roleFullName) {
        // get all members of the role:
        Set<String> members = authorityService.getContainedAuthorities(AuthorityType.USER, roleFullName, false);

        // and exclude those, that are deputies
        List<String> deputies = this.getRoleDeputies(roleFullName);
        Set<String> deputiesSet = new HashSet<>(deputies);
        List<String> assistants = getRoleAssistants(roleFullName);
        deputiesSet.addAll(deputies);
        deputiesSet.addAll(assistants);
        List<String> fullMembers = new ArrayList<>(members.size());
        for (String member : members) {
            if (!deputiesSet.contains(member)) {
                fullMembers.add(member);
            }
        }

        return fullMembers;
    }

    @Override
    public List<String> getRolesDeputiedByUser(String userName) {
        NodeRef user = authorityHelper.needUser(userName);
        // get all groups where user is member
        List<String> userRoles = orgStructService.getTypedGroupsForUser(userName, roleGroupType);

        // get those, where user is deputy
        List<NodeRef> deputiedRoles = getRolesDeputiedToUserImpl(user);

        // use HashSet for fast search
        Set<NodeRef> deputiedRoleSet = new HashSet<>(deputiedRoles.size());
        deputiedRoleSet.addAll(deputiedRoles);

        List<String> managedRoles = new ArrayList<>(userRoles.size());
        for (String roleName : userRoles) {
            NodeRef role = authorityHelper.getRole(roleName);
            // role should be not deputied to user
            // and it should be deputied (managed) by members
            if (!deputiedRoles.contains(role) && isRoleDeputiedByMembers(role)) {
                managedRoles.add(roleName);
            }
        }
        return managedRoles;
    }

    @Override
    public List<String> getRolesDeputiedToUser(String userName) {
        NodeRef user = authorityHelper.needUser(userName);
        return getAuthorityNames(getRolesDeputiedToUserImpl(user));
    }

    /////////////////////////////////////////////////////////////////
    //                  CURRENT USER INTERFACE                     //
    /////////////////////////////////////////////////////////////////

    @Override
    public List<String> getCurrentUserDeputies() {
        return getUserDeputies(getCurrentUserName());
    }

    @Override
    public List<String> getCurrentUserAssistants() {
        return getUserAssistants(getCurrentUserName());
    }

    @Override
    public List<String> getAllCurrentUserDeputies() {
        return getAllUserDeputies(getCurrentUserName());
    }

    @Override
    public void addCurrentUserDeputies(List<String> deputies) {
        addUserDeputies(getCurrentUserName(), deputies);
    }

    @Override
    public void addCurrentUserAssistants(List<String> assistants) {
        addUserAssistants(getCurrentUserName(), assistants);
    }

    @Override
    public boolean isAssistantUserByUser(String roleFullName, String assistantUserName) {
        NodeRef role = authorityHelper.needUser(roleFullName);
        NodeRef user = authorityHelper.needUser(assistantUserName);
        return getDeputationRecord(role, user, true) != null;
    }

    @Override
    public boolean isAssistantToCurrentUser(String assistantUserName) {
        NodeRef user = authorityHelper.needUser(assistantUserName);
        NodeRef currentUser = authorityHelper.needUser(getCurrentUserName());
        return getDeputationRecord(currentUser, user, true) != null;
    }

    @Override
    public void removeCurrentUserDeputies(List<String> deputies) {
        removeUserDeputies(getCurrentUserName(), deputies);
    }

    @Override
    public void removeCurrentUserAssistants(List<String> assistants) {
        removeUserAssistants(getCurrentUserName(), assistants);
    }

    @Override
    public boolean isRoleDeputiedByCurrentUser(final String roleFullName) {
        return isRoleDeputiedByUser(roleFullName, getCurrentUserName());
    }

    @Override
    public boolean isRoleDeputiedToCurrentUser(final String roleFullName) {
        return isRoleDeputiedToUser(roleFullName, getCurrentUserName());
    }

    @Override
    public List<String> getCurrentUserRoles() {
        return getUserRoles(getCurrentUserName());
    }

    @Override
    public List<String> getCurrentUserBranches() {
        return getUserBranches(getCurrentUserName());
    }

    @Override
    public List<String> getRolesDeputiedByCurrentUser() {
        return getRolesDeputiedByUser(getCurrentUserName());
    }

    @Override
    public List<String> getRolesDeputiedToCurrentUser() {
        return getRolesDeputiedToUser(getCurrentUserName());
    }

    /////////////////////////////////////////////////////////////////
    //                     LISTENER INTERFACE                      //
    /////////////////////////////////////////////////////////////////

    public void addDeputyListener(DeputyListener listener) {
        deputyListener.addDeputyListener(listener);
    }

    @Override
    public void userAvailabilityChanged(final String userName) {
        AuthenticationUtil.runAsSystem(() -> {

            log.debug("Started handle user availability change event, userName: {}", userName);

            authorityHelper.needUser(userName);
            boolean available = availabilityService.getUserAvailability(userName);
            List<String> userRoles = getUserRoles(userName);
            List<String> deputiedRoles = getRolesDeputiedToUser(userName);
            if (available) {
                deputyListener.onUserAvailable(userName);
                for (String role : userRoles) {
                    deputyListener.onRoleMemberAvailable(role, userName);
                }
                for (String role : deputiedRoles) {
                    deputyListener.onRoleDeputyAvailable(role, userName);
                }
            } else {
                deputyListener.onUserUnavailable(userName);
                for (String role : userRoles) {
                    deputyListener.onRoleMemberUnavailable(role, userName);
                }
                for (String role : deputiedRoles) {
                    deputyListener.onRoleDeputyUnavailable(role, userName);
                }
            }

            log.debug("Finished handle user availability change event, userName: {}", userName);

            return null;
        });
    }

    @Override
    public void userMembershipChanged(String userName, String groupFullName) {
        if (!authorityHelper.isRole(groupFullName)) {
            return;
        }
        authorityHelper.needUser(userName);

        // check whether user is in group
        Set<String> userGroups = authorityService.getAuthoritiesForUser(userName);

        if (userGroups.contains(groupFullName)) {
            deputyListener.onRoleMemberAvailable(groupFullName, userName);
        } else {
            deputyListener.onRoleMemberUnavailable(groupFullName, userName);
        }
    }

    /////////////////////////////////////////////////////////////////
    //                       PRIVATE STUFF                         //
    /////////////////////////////////////////////////////////////////

    private String getCurrentUserName() {
        return authenticationService.getCurrentUserName();
    }

    private List<NodeRef> getAuthorities(Collection<String> authorityNames) {
        List<NodeRef> authorities = new ArrayList<>(authorityNames.size());
        for (String authorityName : authorityNames) {
            NodeRef authority = authorityHelper.getAuthority(authorityName);
            if (authority != null) {
                authorities.add(authority);
            }
        }
        return authorities;
    }

    private List<String> getAuthorityNames(Collection<NodeRef> authorities) {
        List<String> authorityNames = new ArrayList<>(authorities.size());
        for (NodeRef authority : authorities) {
            String authorityName = authorityHelper.getAuthorityName(authority);
            if (authorityName != null) {
                authorityNames.add(authorityName);
            }
        }
        return authorityNames;
    }

    // get deputies for specified authority
    private List<NodeRef> getAuthorityDeputies(NodeRef authority, boolean isAssistants) {
        List<NodeRef> deputationRecords =
            searchDeputationRecords(DeputyModel.ASSOC_DEPUTIED_AUTHORITY, authority, isAssistants);
        Map<NodeRef, NodeRef> deputyMap = mapDeputationRecordsAssocs(deputationRecords,
            DeputyModel.ASSOC_DEPUTY);
        List<NodeRef> deputies = new ArrayList<>(deputyMap.size());
        deputies.addAll(deputyMap.values());
        return deputies;
    }

    // get roles deputied to user - without checks
    private List<NodeRef> getRolesDeputiedToUserImpl(NodeRef user) {
        List<NodeRef> deputationRecords =
            searchDeputationRecords(DeputyModel.ASSOC_DEPUTY, user, false);
        Map<NodeRef, NodeRef> deputyMap = mapDeputationRecordsAssocs(deputationRecords,
            DeputyModel.ASSOC_DEPUTIED_AUTHORITY);
        return filterAuthoritiesByType(deputyMap.values(), ContentModel.TYPE_AUTHORITY_CONTAINER);
    }

    private List<NodeRef> getUsersDeputiedToUserImpl(NodeRef user, boolean isAssistants) {
        List<NodeRef> deputationRecords =
            searchDeputationRecords(DeputyModel.ASSOC_DEPUTY, user, isAssistants);
        Map<NodeRef, NodeRef> deputyMap = mapDeputationRecordsAssocs(deputationRecords,
            DeputyModel.ASSOC_DEPUTIED_AUTHORITY);
        return filterAuthoritiesByType(deputyMap.values(), ContentModel.TYPE_PERSON);
    }

    private List<NodeRef> filterAuthoritiesByType(Collection<NodeRef> authorities, QName requiredType) {
        List<NodeRef> filtered = new ArrayList<>(authorities.size());
        for (NodeRef authority : authorities) {
            QName type = nodeService.getType(authority);
            if (requiredType.equals(type)) {
                filtered.add(authority);
            }
        }
        return filtered;
    }

    // search deputation records by association
    private List<NodeRef> searchDeputationRecords(QName searchAssoc, NodeRef assocValue, boolean isAssistants) {
        if (!nodeService.exists(assocValue)) {
            return Collections.emptyList();
        }
        List<AssociationRef> assocs = nodeService.getSourceAssocs(assocValue, searchAssoc);
        List<NodeRef> deputationRecords = new ArrayList<>(assocs.size());
        for (AssociationRef assoc : assocs) {
            if (isAssistantDeputyRecord(assoc.getSourceRef()).equals(isAssistants)) {
                deputationRecords.add(assoc.getSourceRef());
            }
        }
        return deputationRecords;
    }

    // get map of deputationRecords assocs
    private Map<NodeRef, NodeRef> mapDeputationRecordsAssocs(Collection<NodeRef> deputationRecords,
                                                             QName assocName) {

        Map<NodeRef, NodeRef> results = new HashMap<>(deputationRecords.size());
        for (NodeRef deputationRecord : deputationRecords) {
            List<AssociationRef> assocs = nodeService.getTargetAssocs(deputationRecord, assocName);
            if (assocs == null || assocs.isEmpty()) {
                continue;
            }

            NodeRef authorityRef = assocs.get(0).getTargetRef();
            if (authorityRef != null && nodeService.exists(authorityRef)) {
                results.put(deputationRecord, authorityRef);
            }
        }
        return results;
    }

    // get deputation record or null, if no such
    private NodeRef getDeputationRecord(NodeRef deputiedAuthority, NodeRef deputy, boolean isAssistantRecord) {
        List<AssociationRef> assocs = nodeService.getSourceAssocs(deputy, DeputyModel.ASSOC_DEPUTY);
        for (AssociationRef assoc : assocs) {
            NodeRef deputyRecord = assoc.getSourceRef();
            Boolean isAssistant = isAssistantDeputyRecord(deputyRecord);
            if (isAssistantRecord != isAssistant) {
                continue;
            }
            List<AssociationRef> authorityRefs = nodeService.getTargetAssocs(deputyRecord, DeputyModel.ASSOC_DEPUTIED_AUTHORITY);
            if (authorityRefs == null || authorityRefs.isEmpty()) {
                continue;
            }
            if (deputiedAuthority.equals(authorityRefs.get(0).getTargetRef())) {
                return deputyRecord;
            }
        }
        return null;
    }

    private Boolean isAssistantDeputyRecord(NodeRef deputyRecord) {
        Boolean isAssistant = (Boolean) nodeService.getProperty(deputyRecord, DeputyModel.PROP_IS_ASSISTANT);
        return isAssistant == null ? false : isAssistant;
    }

    // add deputation record and return nodeRef to it
    private NodeRef addDeputationRecord(NodeRef deputiedAuthority,
                                        NodeRef deputy, boolean deputiedRole, boolean isAssistant) {

        // first search for existing record:
        NodeRef deputationRecord = getDeputationRecord(deputiedAuthority, deputy, isAssistant);
        // if it exists - simply return it
        if (deputationRecord != null && nodeService.exists(deputationRecord)) {
            return deputationRecord;
        }

        // otherwise create new deputationRecord
        QName assocName = QName.createQName(DeputyModel.NAMESPACE, GUID.generate());
        ChildAssociationRef childAssocRef = nodeService.createNode(deputationRecordsRoot,
            deputationRecordAssoc, assocName, DeputyModel.TYPE_DEPUTATION_RECORD);
        deputationRecord = childAssocRef.getChildRef();

        nodeService.createAssociation(deputationRecord, deputiedAuthority, DeputyModel.ASSOC_DEPUTIED_AUTHORITY);
        nodeService.createAssociation(deputationRecord, deputy, DeputyModel.ASSOC_DEPUTY);
        nodeService.setProperty(deputationRecord, DeputyModel.PROP_IS_ASSISTANT, isAssistant);

        // and call listener
        String authorityName = authorityHelper.getAuthorityName(deputiedAuthority);
        String deputyName = authorityHelper.getAuthorityName(deputy);
        if (isAssistant) {
            if (deputiedRole) {
                deputyListener.onRoleAssistantAdded(authorityName, deputyName);
            } else {
                deputyListener.onAssistantAdded(authorityName);
            }

        } else {
            if (deputiedRole) {
                deputyListener.onRoleDeputyAvailable(authorityName, deputyName);
            } else {
                deputyListener.onUserDeputyAvailable(authorityName, deputyName);
            }
        }

        return childAssocRef.getChildRef();
    }

    // add deputation records and return nodeRefs of them
    private List<NodeRef> addDeputationRecords(NodeRef deputiedAuthority,
                                               Collection<NodeRef> deputies, boolean deputiedRole, boolean isAssistants) {
        List<NodeRef> deputationRecords = new ArrayList<>(deputies.size());
        // then add all deputation records
        for (NodeRef deputy : deputies) {
            NodeRef deputationRecord = addDeputationRecord(deputiedAuthority, deputy, deputiedRole, isAssistants);
            deputationRecords.add(deputationRecord);
        }
        return deputationRecords;
    }

    // remove specified deputation record, if exists
    private void removeDeputationRecord(NodeRef deputiedAuthority,
                                        NodeRef deputy, boolean deputiedRole, boolean isAssistant) {
        // first search for existing record:
        NodeRef deputationRecord = getDeputationRecord(deputiedAuthority, deputy, isAssistant);
        // if it is null or does not exist - there is nothing to do
        if (deputationRecord == null || !nodeService.exists(deputationRecord)) {
            return;
        }
        String authorityName = authorityHelper.getAuthorityName(deputiedAuthority);
        String deputyName = authorityHelper.getAuthorityName(deputy);
        // otherwise perform deletion
        nodeService.deleteNode(deputationRecord);
        if (isAssistant) {
            if (deputiedRole) {
                deputyListener.onRoleAssistantRemoved(authorityName, deputyName);
            } else {
                deputyListener.onAssistantRemoved(authorityName, deputyName);
            }
        }

        // and call listener

        if (!isAssistant) {
            if (deputiedRole) {
                deputyListener.onRoleDeputyUnavailable(authorityName, deputyName);
            } else {
                deputyListener.onUserDeputyUnavailable(authorityName, deputyName);
            }
        }
    }

    // remove specified deputation records, if any
    private void removeDeputationRecords(NodeRef deputiedAuthority,
                                         Collection<NodeRef> deputies, boolean deputiedRole, boolean isAssistants) {
        // then remove all deputation records
        for (NodeRef deputy : deputies) {
            removeDeputationRecord(deputiedAuthority, deputy, deputiedRole, isAssistants);
        }
    }

    private boolean isRoleDeputiedByMembers(NodeRef role) {
        Boolean managed = (Boolean) nodeService.getProperty(role, DeputyModel.PROP_MANAGED_BY_MEMBERS);
        // role is not managed by members by default
        return Boolean.TRUE.equals(managed);
    }

    public void setDeputyListener(CompositeDeputyListener deputyListener) {
        this.deputyListener = deputyListener;
    }

    @Override
    public boolean isUserAvailable(String userName) {
        return availabilityService.getUserAvailability(userName);
    }

    @Override
    public boolean isCanDeleteDeputeOrAssistantFromRole(String roleFullName) {
        if (isRoleDeputiedByMembers(roleFullName)) {
            return true;
        }

        // zero - all roles are deputied by admins
        if (authorityService.isAdminAuthority(getCurrentUserName())) {
            return true;
        }

        // second - user should be the member of this role
        Set<String> userGroups = authorityService.getAuthoritiesForUser(getCurrentUserName());
        if (!userGroups.contains(roleFullName)) {
            return false;
        }

        if (!getRoleMembers(roleFullName).contains(getCurrentUserName())) {
            return false;
        }
        return true;
    }

    public void setCacheManager(EhcacheConfigurationManager ehcacheConfigurationManager) {
        CacheManager cacheManager = ehcacheConfigurationManager.getCacheManager();
        if (cacheManager.getCache("usersWhoHaveThisUserDeputyCache", String.class, (Class<List<String>>) (Class<?>) List.class) == null) {
            usersWhoHaveThisUserDeputyCache = cacheManager
                .createCache("usersWhoHaveThisUserDeputyCache", CacheConfigurationBuilder
                    .newCacheConfigurationBuilder(String.class, (Class<List<String>>) (Class<?>) List.class, ResourcePoolsBuilder
                        .newResourcePoolsBuilder()
                        .heap(EhcacheConfigurationManager.cacheSize))
                    .withExpiry(Expirations.timeToLiveExpiration(new Duration(EhcacheConfigurationManager.timeToLive, TimeUnit.SECONDS)))
                    .build());
        }
    }
}
