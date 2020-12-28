const STATUS_SUCCESS = "OK";
const STATUS_ERROR = "ERROR";
const STATUS_PERMISSION_DENIED = "PERMISSION_DENIED";
const STATUS_SKIPPED = "STATUS_SKIPPED";

(function() {

    var nodes = json.get('nodes'),
        attrs = json.get('attributes'),
        skipInStatuses = json.get('skipInStatuses'),
        isChildMode = json.get('childMode'),
        permService = services.getOrNull("attributesPermissionService");

    if (!exists("nodes", nodes) ||
        !exists("attributes", attrs)) {

        return;
    }

    var statuses = [];
    if (skipInStatuses) {
        for (var i = 0; i < skipInStatuses.length(); i++) {
            var statusName = skipInStatuses.get(i).trim();
            if (statusName != null) {
                statuses.push(statusName);
            }
        }
    }

    var changeResult = groupActions.forEach(nodes, function(nodeStr) {
        var splitedNode = nodeStr.split("@");
        if(splitedNode.length > 1){
            nodeStr = splitedNode[1]; // This is necessary because search.findNode does not work with RecordRef's (returns null)
        }

        var node = search.findNode(nodeStr);

        var it = attrs.keys();

        while (it.hasNext()) {

            var key = it.next();
            var skipInThisStatus = false;

            var caseStatusNode = isChildMode == true ? caseStatusService.getStatusNodeFromPrimaryParent(node)
                : caseStatusService.getStatusNode(node),
                caseStatusRef = caseStatusNode ? caseStatusNode.nodeRef : null;

            var statusName = caseStatusService.getStatusName(node, caseStatusRef);
            if (caseStatusRef) {
                skipInThisStatus = (statuses.indexOf(statusName) >= 0);
            }

            if (!skipInThisStatus) {
                if (checkPermission(node, key, permService)) {
                    attributes.set(node, key, attrs.get(key));
                    return STATUS_SUCCESS;
                } else {
                    return STATUS_PERMISSION_DENIED;
                }
            } else {
                return STATUS_SKIPPED;
            }
        }
    });

    var actionResults = changeResult.getResults();
    var formattedResults = {};

    for (var i = 0; i < actionResults.length; i++) {

        var result = actionResults[i];

        var statusKey = result.getStatus().getKey();
        var actStatus = {};

        var it = attrs.keys();
        while (it.hasNext()) {
            actStatus[it.next()] = statusKey;
        }

        var nodeRef = result.getData();

        formattedResults[nodeRef + ""] = actStatus;
    }

    model.results = formattedResults;

})();

function checkPermission(node, attr, permService) {
    if (permService == null) {
        return true;
    }
    return permService.isFieldEditable(citeckUtils.createQName(attr), node.nodeRef, "edit");
}

function exists(name, obj) {
    if(!obj) {
        status.setCode(status.STATUS_BAD_REQUEST, "Argument '" + name + "' is required");
        return false;
    }
    return true;
}
