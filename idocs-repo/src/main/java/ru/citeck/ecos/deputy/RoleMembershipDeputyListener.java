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

import org.alfresco.service.cmr.repository.NodeRef;
import org.alfresco.service.cmr.repository.NodeService;
import org.alfresco.service.cmr.repository.StoreRef;
import org.alfresco.service.cmr.search.ResultSet;
import org.alfresco.service.cmr.search.SearchService;
import org.alfresco.service.cmr.security.AuthorityService;
import org.alfresco.service.cmr.security.AuthorityType;
import ru.citeck.ecos.model.DeputyModel;

import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Deputy Listener, that processes role membership changes.
 *
 * If role member (not deputy) becomes unavailable,
 *  this listener deputies the role to its deputies (i.e. adds deputies to the role).
 *
 * If role member (not deputy) becomes available,
 *  this listener "undeputies" the role from its deputies (i.e. removes deputies from the role).
 *
 * @author Sergey Tiunov
 */
public class RoleMembershipDeputyListener extends AbstractDeputyListener
{
	private AuthorityService authorityService;
	private AvailabilityService availabilityService;
	private SearchService searchService;
	private NodeService nodeService;

	public void setAuthorityService(AuthorityService authorityService) {
		this.authorityService = authorityService;
	}

	public void setAvailabilityService(AvailabilityService availabilityService) {
		this.availabilityService = availabilityService;
	}

	public void setSearchService(SearchService searchService) {
		this.searchService = searchService;
	}

	public void setNodeService(NodeService nodeService) {
		this.nodeService = nodeService;
	}

	@Override
	public void onRoleMemberAvailable(String roleFullName, String memberName) {
		onRoleMemberAvailabilityChanged(roleFullName, memberName, false);
	}

	@Override
	public void onRoleMemberUnavailable(String roleFullName, String memberName) {
		onRoleMemberAvailabilityChanged(roleFullName, memberName, true);
	}

	@Override
	public void onRoleAssistantAdded(String roleFullName, String assistantName) {
		RoleMembersContainer roleMembersContainer = new RoleMembersContainer(roleFullName).getAssistantsUsersAndMembers();
		Set<String> deputyUsers = roleMembersContainer.getDeputyUsers();
		deputy(roleFullName, deputyUsers);
	}

	@Override
	public void onRoleAssistantRemoved(String roleFullName, String assistantName) {
		RoleMembersContainer roleMembersContainer = new RoleMembersContainer(roleFullName).getAssistantsUsersAndMembers();
		Set<String> deputyUsers = roleMembersContainer.getDeputyUsers();
		undeputy(roleFullName, deputyUsers);
	}

	private void onRoleMemberAvailabilityChanged(String roleFullName, String memberName, boolean deputy) {
		RoleMembersContainer roleMembersContainer = new RoleMembersContainer(roleFullName).getDeputyUsersAndMembers();
		Set<String> memberUsers = roleMembersContainer.getMemberUsers();
		Set<String> deputyUsers = roleMembersContainer.getDeputyUsers();


		if(deputy && isRoleDeputationNecessary(roleFullName, memberUsers, deputyUsers)) {
			deputy(roleFullName, deputyUsers);
		}

		if(!deputy && !isRoleDeputationNecessary(roleFullName, memberUsers, deputyUsers)){
			undeputy(roleFullName, deputyUsers);
		}

	}

	private void deputy(String roleFullName, Set<String> deputyUsers) {
		Set<String> members = authorityService.getContainedAuthorities(AuthorityType.USER, roleFullName, false);
		for (String deputy : deputyUsers) {
			if (members.contains(deputy)) {
                continue;
            }
			authorityService.addAuthority(roleFullName, deputy);
		}
	}

	private void undeputy(String roleFullName, Set<String> deputyUsers) {
		Set<String> members = authorityService.getContainedAuthorities(AuthorityType.USER, roleFullName, false);
		for (String deputy : deputyUsers) {
			if (!members.contains(deputy)) {
                continue;
            }
			authorityService.removeAuthority(roleFullName, deputy);
		}
		updateDeputyEndAbsence();
	}

	// TODO encapsulate this decision into abstract predicate
	private boolean isRoleDeputationNecessary(String roleFullName, Set<String> memberUsers, Set<String> deputyUsers) {
		// check availability of full members
		for (String user : memberUsers) {
			if (availabilityService.getUserAvailability(user)) {
				return false;
			}
		}
		return true;
	}

	private List<NodeRef> getDeputyAbsenceEvent() {
		String query = "PATH:\"/app:company_home/app:dictionary/cm:absence-events/*\" " +
				"AND TYPE:\"deputy:absenceEvent\" AND @deputy\\:startAbsence:[MIN TO NOW] " +
				"AND (ISNULL:\"deputy:endAbsence\" OR @deputy\\:endAbsence:[NOW TO MAX])";
		ResultSet queryResults =
				searchService.query(StoreRef.STORE_REF_WORKSPACE_SPACESSTORE, SearchService.LANGUAGE_LUCENE, query);
		return queryResults.getNodeRefs();
	}

	private void updateDeputyEndAbsence() {
		if (!getDeputyAbsenceEvent().isEmpty()) {
			for (NodeRef nodeRef: getDeputyAbsenceEvent()) {
				nodeService.setProperty(nodeRef, DeputyModel.PROP_END_ABSENCE, new Date());
			}
		}
	}

	private class RoleMembersContainer {
		private String roleFullName;
		private Set<String> deputyUsers;
		private Set<String> memberUsers;

		public RoleMembersContainer(String roleFullName) {
			this.roleFullName = roleFullName;
		}

		public Set<String> getDeputyUsers() {
			return deputyUsers;
		}

		public Set<String> getMemberUsers() {
			return memberUsers;
		}

		public RoleMembersContainer getDeputyUsersAndMembers() {
			// get all current users in role
			Set<String> allUsers = authorityService.getContainedAuthorities(AuthorityType.USER, roleFullName, false);

			// get deputies of role
			List<String> deputiesList = deputyService.getRoleDeputies(roleFullName);

			deputyUsers = new HashSet<>(deputiesList.size());
			deputyUsers.addAll(deputiesList);

			memberUsers = new HashSet<>(allUsers.size());
			for (String user : allUsers) {
				if (!deputyUsers.contains(user)) {
					memberUsers.add(user);
				}
			}
			return this;
		}

		public RoleMembersContainer getAssistantsUsersAndMembers() {
			// get all current users in role
			Set<String> allUsers = authorityService.getContainedAuthorities(AuthorityType.USER, roleFullName, false);

			// get deputies of role
			List<String> deputiesList = deputyService.getRoleAssistants(roleFullName);

			deputyUsers = new HashSet<>(deputiesList.size());
			deputyUsers.addAll(deputiesList);

			memberUsers = new HashSet<>(allUsers.size());
			for (String user : allUsers) {
				if (!deputyUsers.contains(user)) {
					memberUsers.add(user);
				}
			}
			return this;
		}
	}
}
