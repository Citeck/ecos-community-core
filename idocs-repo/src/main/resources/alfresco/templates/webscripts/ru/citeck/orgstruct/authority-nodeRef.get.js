<import resource="classpath:alfresco/templates/webscripts/ru/citeck/orgstruct/orgstruct.lib.js">
(function() {
	var nodeRef = args.nodeRef,
		fullName = args.fullName,
		authorityNode = null;
	if (nodeRef) {
        nodeRef = nodeRef.replace("alfresco/@", "");
		authorityNode = search.findNode(nodeRef);
	} else if(fullName) {
		if(fullName.match(/^GROUP_/)) {
			var shortName = fullName.replace(/^GROUP_/, '');
			// documentation and implementation are different
			authorityNode = people.getGroup(fullName) || people.getGroup(shortName);
		} else {
			authorityNode = people.getPerson(fullName);
		}
	} else {
		status.setCode(status.STATUS_BAD_REQUEST, "Either nodeRef or fullName should be provided");
		return;
	}

	if(authorityNode == null) {
		status.setCode(status.STATUS_NOT_FOUND, "Node was not found");
		return;
	}
	if(!authorityNode.isSubType("cm:authority")) {
		status.setCode(status.STATUS_NOT_FOUND, "Node " + authorityNode.nodeRef + " is not authority");
		return;
	}
	var authority = null;
	if(authorityNode.isSubType("cm:person")) {
		authority = groups.getUser(authorityNode.properties.userName);
	} else if(authorityNode.isSubType("cm:authorityContainer")) {
		authority = groups.getGroupForFullAuthorityName(authorityNode.properties.authorityName);
	}
	if(authority == null) {
		status.setCode(status.STATUS_NOT_FOUND, "Node " + nodeRef + " is unknown type of authority");
		return;
	}
	model.authority = authority;
})();
