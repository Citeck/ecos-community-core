(function() {
    var nodeRef = args.nodeRef;
    if (!nodeRef) {
        status.setStatus(status.STATUS_BAD_REQUEST, "Argument nodeRef is missing");
        return;
    }

    var roleNodes = caseRoleService.getRoles(nodeRef);

    var rolesData = [];
    for (var i = 0; i < roleNodes.length; i++) {
        rolesData.push(printRole(nodeRef, roleNodes[i]));
    }

    model.result = {
        "metadata": {
            "nodeRef": nodeRef
        },
        "roles": rolesData
    }

    function printRole(documentRef, role) {

        var assignees = [];
        var roleAssignees = role.assocs['icaseRole:assignees'] || [];
        for (var i = 0; i < roleAssignees.length; i++) {
            assignees.push(printAssignee(roleAssignees[i]));
        }

        return {
            nodeRef: role.nodeRef + '',
            parent: documentRef + '',
            type: role.typeShort,
            classNames: [],
            isDocument: true,
            isContainer: false,
            attributes: {
                "cm:title": role.properties["cm:title"],
                "cm:name": role.properties["cm:name"],
                "icaseRole:varName": role.properties["icaseRole:varName"],
                "icaseRole:assignees": assignees,
            },
            permissions: {
                "Delete": true,
                "Write": true
            }
        }
    }

    function printAssignee(assignee) {
        return {
            nodeRef: assignee.nodeRef,
            type: assignee.typeShort,
            "cm:userName": assignee.properties["cm:userName"],
            "cm:firstName": assignee.properties["cm:firstName"],
            "cm:lastName": assignee.properties["cm:lastName"],
            "cm:authorityDisplayName": assignee.properties["cm:authorityDisplayName"],
            "cm:authorityName": assignee.properties["cm:authorityName"],
            permissions: {}
        }
    }

})();
