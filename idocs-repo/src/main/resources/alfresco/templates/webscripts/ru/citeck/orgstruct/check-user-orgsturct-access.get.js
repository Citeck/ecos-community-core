(function() {

    var userName = args.userName;
    var isActionsAllowed = false;
    var isAdminAuthority = false;

    if (userName) {
        var person = people.getPerson(userName);
        var personGroups = people.getContainerGroups(person);
        isActionsAllowed = checkUserInGroup(personGroups, "GROUP_ORGSTRUCT_ACTIONS_ACCESS");
        isAdminAuthority = checkUserInGroup(personGroups, "GROUP_ALFRESCO_ADMINISTRATORS");
    } else {
        status.setCode(status.STATUS_BAD_REQUEST, "User should be specified");
        return;
    }

    function checkUserInGroup(personGroups, groupName) {
        var checkedGroup = people.getGroup(groupName);
        for (var i in personGroups) {
            if (!personGroups.hasOwnProperty(i))
                continue;
            for (var j = 0; j < arguments.length; j++) {
                var group = people.getGroup(arguments[j]);
                if (group) {
                    if (personGroups[i].equals(checkedGroup)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }
    model.isActionsAllowed = isActionsAllowed.toString();
    model.isAdminAuthority = isAdminAuthority.toString();
})();
