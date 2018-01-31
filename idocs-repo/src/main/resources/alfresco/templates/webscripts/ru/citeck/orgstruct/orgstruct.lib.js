<import resource="classpath:alfresco/templates/webscripts/ru/citeck/orgmeta/orgmeta.lib.js">

// get filter options from input arguments
function getFilterOptions() {
	var defaultEnabled = args["default"] != "false";
	var processOption = function(name) {
		return args[name] ? args[name] != "false" : defaultEnabled;
	};
	return {
		branch: processOption("branch"),
		role: processOption("role"),
		group: processOption("group"),
		user: processOption("user"),
        showDisabled: processOption("showdisabled"),
        subTypes: args.subTypes ? args.subTypes.split(',') : null,
		filter: args.filter ? new RegExp(args.filter.replace(/([*?+])/, ".$1"), "i") : null,
	};
}

function getGroupOptions(options) {
    var groupOptions = {};
    for(var i in options) {
        groupOptions[i] = options[i];
    }
    groupOptions.group = true;
    groupOptions.branch = true;
    groupOptions.role = true;
    return groupOptions;
}    

// filter groups and users as well
function filterAuthorities(allAuthorities, options) {
	var authorities = [];
	
	for(var i in allAuthorities) {
		var displayName = "";
		var authority = allAuthorities[i], 
			name = authority.shortName;
		if(authority.authorityType == "USER") {
            var enabled = people.isAccountEnabled(authority.person.properties.userName);

            var asGroup = groups.getGroupForFullAuthorityName(authority.userName);
            if (!options.user || options.subTypes && filterAuthorities(asGroup.allParentGroups, getGroupOptions(options)).length == 0) {
                continue;
            };

            // if (!options.incDisabled && authority.person.hasAspect('cm:personDisabled'))
			if (!options.showDisabled && !enabled) {
				continue;
			};

			displayName = authority.person.properties.firstName + " " + authority.person.properties.lastName;
		} else if(authority.authorityType == "GROUP") {
			var type = orgstruct.getGroupType(name),
                subType = orgstruct.getGroupSubtype(name),
				include = options[type || "group"];
            if(!include || options.subTypes && options.subTypes.indexOf(subType + "") == -1) {
                continue;
            }
			displayName = authority.displayName;
		} else {
			continue;
		}
		if(options.filter 
		&& !options.filter.test(authority.shortName)
		&& !options.filter.test(displayName))
		{
			continue;
		}
		authorities.push(authority);
	}
	return authorities;
}

function createAuthority(obj, parent) {

	// get object type:
	var authorityType = obj.authorityType || "GROUP";
	
	if(authorityType != "GROUP") {
		status.setCode(status.STATUS_NOT_SUPPORTED, "Creation of non-groups is not supported");
		return;
	}
	
	// get parent group
	var parentGroup = null;
	if(parent) {
		parentGroup = groups.getGroup(parent);
		if(!parentGroup) {
			status.setCode(status.STATUS_NOT_FOUND, "Can not find group: " + parent);
			return;
		}
	}
	
	return createGroup(obj, parentGroup);
}

function createGroup(obj, parent) {
	// get short name:
	var name = obj.shortName;

	// get group type
	var groupType = obj.groupType || "group";
	
	// get group sub type
	var groupSubType = obj.groupSubType;

	// check group sub type existence:
	if(groupType != "group") {
		if(groupSubType == null) {
			status.setCode(status.STATUS_BAD_REQUEST, groupType + " type should be specified");
			return;
		}
		var groupSubTypes = getSubTypeDictionary(groupType);
		if(typeof groupSubTypes[groupSubType] == "undefined") {
			status.setCode(status.STATUS_NOT_FOUND, groupType + " type not found: " + groupSubType);
			return;
		}
	}
	
	// if group does not exist, create it:
	var group = groups.getGroup(name);
	if(group == null) {
		var displayName = obj.displayName || name;
		if(parent == null) {
			group = groups.createRootGroup(name, displayName);
		} else {
			group = parent.createGroup(name, displayName);
		}
	}
	
	// now create typed-group:
	// but first convert from any previous typed-group
	orgstruct.convertToSimpleGroup(name);
	
	if(groupType != "group") {
		orgStruct.createTypedGroup(groupType, groupSubType, name);
	}
	
	// now update its properties:
	if(obj.displayName) {
		group.setDisplayName(obj.displayName);
	}
	
	// and add to parent:
	if(parent) {
		parent.addAuthority(group.fullName);
	}
	
	return group;
}

model.groupTypes = getAllSubTypeDictionaries();