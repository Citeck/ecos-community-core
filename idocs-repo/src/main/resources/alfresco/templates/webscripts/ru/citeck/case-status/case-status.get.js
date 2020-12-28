(function () {
    const STATUS_TYPE_CASE = 'case-status';
    const STATUS_TYPE_DOCUMENT = 'document-status';

    var caseStatus = caseStatusService.getStatusNode(args.nodeRef);
    var statusName = "";
    var statusId   = "";
    var statusType = "status";
    if (!caseStatus) {
        var node = search.findNode(args.nodeRef);
        caseStatus = node.properties['idocs:documentStatus'];
        if (caseStatus) {
            importPackage(Packages.org.springframework.extensions.surf.util);
            var statusKey = "listconstraint.idocs_constraint_documentStatus." + caseStatus;
            statusName = I18NUtil.getMessage(statusKey) || statusKey;
            statusId   = statusKey;
            statusType = STATUS_TYPE_DOCUMENT;
        }
    } else {
        if (caseStatusService.isAlfRef(caseStatus.nodeRef)) {
            statusName = caseStatus.properties['cm:title'] || caseStatus.properties['cm:name'];
            statusId   = caseStatus.properties['cm:name'];
        } else {
            statusId = caseStatus.nodeRef.getId();
            statusName = caseStatusService.getEcosStatusName(args.nodeRef, caseStatus) || statusId;
        }
        statusType = STATUS_TYPE_CASE;
    }
    model.json = {
        statusId: statusId,
        statusName: statusName,
        statusType: statusType
    };
})();


