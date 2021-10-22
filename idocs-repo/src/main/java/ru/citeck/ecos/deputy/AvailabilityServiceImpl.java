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
import org.alfresco.repo.security.authentication.AuthenticationUtil;
import org.alfresco.service.cmr.repository.NodeRef;
import org.alfresco.service.cmr.repository.NodeService;
import org.alfresco.service.cmr.repository.StoreRef;
import org.alfresco.service.cmr.search.ResultSet;
import org.alfresco.service.cmr.search.SearchParameters;
import org.alfresco.service.cmr.search.SearchService;
import org.alfresco.service.cmr.security.AuthenticationService;
import ru.citeck.ecos.model.DeputyModel;
import ru.citeck.ecos.utils.TransactionUtils;

import java.util.List;

@Slf4j
public class AvailabilityServiceImpl implements AvailabilityService {

    private static final String NODES_TO_UPDATE_TXN_KEY = AvailabilityServiceImpl.class.getName() + ".nodesToUpdate";

    private AuthenticationService authenticationService;
    private NodeService nodeService;
    private AuthorityHelper authorityHelper;
    private SearchService searchService;

    /////////////////////////////////////////////////////////////////
    //                      SPRING INTERFACE                       //
    /////////////////////////////////////////////////////////////////

    public void setAuthenticationService(AuthenticationService authenticationService) {
        this.authenticationService = authenticationService;
    }

    public void setNodeService(NodeService nodeService) {
        this.nodeService = nodeService;
    }

    public void setAuthorityHelper(AuthorityHelper authorityHelper) {
        this.authorityHelper = authorityHelper;
    }

    public void setSearchService(SearchService searchService) {
        this.searchService = searchService;
    }

    /////////////////////////////////////////////////////////////////
    //                       ADMIN INTERFACE                       //
    /////////////////////////////////////////////////////////////////

    @Override
    public boolean getUserAvailability(String userName) {
        NodeRef person = authorityHelper.needUser(userName);
        if (person == null) {
            throw new IllegalArgumentException("No such user: " + userName);
        }
        return getUserAvailability(person);
    }

    @Override
    public boolean getUserAvailability(NodeRef userRef) {
        Boolean available = (Boolean) nodeService.getProperty(userRef, DeputyModel.PROP_AVAILABLE);
        // user is available by default
        return !Boolean.FALSE.equals(available);
    }

    public String getUserUnavailableAutoAnswer(String userName) {
        if (!getUserAvailability(userName)) {
            List<NodeRef> events = getUserAbsenceEvents(userName).getNodeRefs();
            if (events.isEmpty()) {
                return null;
            }

            String autoAnswer = (String) nodeService.getProperty(events.get(0), DeputyModel.PROP_AUTO_ANSWER);
            return autoAnswer != null ? autoAnswer : "";
        }
        return null;
    }

    private ResultSet getUserAbsenceEvents(String userName) {
        NodeRef person = authorityHelper.needUser(userName);
        if (person == null) {
            throw new IllegalArgumentException("No such user: " + userName);
        }
        String query = "TYPE:\"deputy:selfAbsenceEvent\"" +
            " AND (@deputy\\:endAbsence:{NOW TO MAX} OR ISNULL:\"deputy:endAbsence\")" +
            " AND @deputy\\:eventFinished:false" +
            " AND @deputy\\:user_added:\"" + person.toString() +
            "\"";
        final SearchParameters searchParameters = new SearchParameters();
        searchParameters.setLanguage(SearchService.LANGUAGE_LUCENE);
        searchParameters.addStore(StoreRef.STORE_REF_WORKSPACE_SPACESSTORE);
        searchParameters.addSort(new SearchParameters.SortDefinition(SearchParameters.SortDefinition.SortType.FIELD, "startAbsence", false));
        searchParameters.setQuery(query);
        return AuthenticationUtil.runAsSystem(() -> searchService.query(searchParameters));
    }

    @Override
    public void setUserAvailability(String userName, boolean availability) {
        NodeRef person = authorityHelper.needUser(userName);
        setUserAvailability(person, availability);
        // note: deputy listener, related to availability change, should be called via behaviour mechanism
    }

    @Override
    public void setUserAvailabilityAsync(String userName, boolean availability) {
        NodeRef person = authorityHelper.needUser(userName);
        setUserAvailabilityAsync(person, availability);
        // note: deputy listener, related to availability change, should be called via behaviour mechanism
    }

    @Override
    public void setUserAvailability(NodeRef user, boolean availability) {
        nodeService.setProperty(user, DeputyModel.PROP_AVAILABLE, availability);
    }

    @Override
    public void setUserAvailabilityAsync(NodeRef user, boolean availability) {
        TransactionUtils.processBatchAfterCommit(NODES_TO_UPDATE_TXN_KEY, user, (users) -> {
            for (NodeRef userNodeRef : users) {
                if (nodeService.exists(userNodeRef)) {
                    nodeService.setProperty(userNodeRef, DeputyModel.PROP_AVAILABLE, availability);
                }
            }
        }, null);
    }

    /////////////////////////////////////////////////////////////////
    //                  CURRENT USER INTERFACE                     //
    /////////////////////////////////////////////////////////////////

    @Override
    public boolean getCurrentUserAvailability() {
        return getUserAvailability(getCurrentUserName());
    }

    @Override
    public void setCurrentUserAvailability(boolean availability) {
        setUserAvailability(getCurrentUserName(), availability);
    }

    @Override
    public void setCurrentUserAvailabilityAsync(boolean availability) {
        setUserAvailabilityAsync(getCurrentUserName(), availability);
    }

    /////////////////////////////////////////////////////////////////
    //                       PRIVATE STUFF                         //
    /////////////////////////////////////////////////////////////////

    private String getCurrentUserName() {
        return authenticationService.getCurrentUserName();
    }

}
