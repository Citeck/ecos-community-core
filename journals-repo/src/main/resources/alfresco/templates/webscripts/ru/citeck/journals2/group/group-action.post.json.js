(function() {

    var jsonData = jsonUtils.toObject(json),
        nodes = jsonData.nodes,
        actionId = jsonData.actionId,
        params = jsonData.params,
        groupType = jsonData.groupType || "selected",
        query = jsonData.query,
        language = jsonData.language || null,
        sourceId = jsonData.sourceId || '',
        sortBy = jsonData.sortBy || [],
        consistency = jsonData.consistency || 'DEFAULT',
        excludedRecords = jsonData.excludedRecords,
        actionResults,
        actionResultsData,
        records;

    if (!exists("nodes", nodes) ||
        !exists("attributes", params) ||
        !exists("actionId", actionId)) {
        return;
    }

    var results = [];
    if (groupType == "selected") {

        records = [];
        for (var idx in nodes) {
            records.push(Packages.ru.citeck.ecos.records2.RecordRef.valueOf(nodes[idx]));
        }

        actionResultsData = groupActions.execute(records, {
            params: params,
            actionId: actionId
        });

        actionResults = actionResultsData.getResults();

        for (var idx in actionResults) {
            var result = actionResults[idx];
            results.push({
                nodeRef: result.data.toString(),
                status: result.status.key,
                message: result.status.message,
                url: result.status.url
            });
        }
    } else {

        var groupActionConfig = {
            params: params,
            async: true,
            actionId: actionId
        };

        records = recordsService.getIterableRecordsForGroupAction({
                sourceId: sourceId,
                query: query,
                sortBy: sortBy,
                language: language || 'predicate',
                consistency: consistency
            },
            groupActionConfig,
            {excludedRecords: excludedRecords});

        actionResultsData = groupActions.execute(records, groupActionConfig);

        actionResults = actionResultsData.getResults();

        if (actionResults && actionResults.length > 0) {
            for (var idx in actionResults) {
                var result = actionResults[idx];
                results.push({
                    nodeRef: result.data.toString(),
                    status: result.status.key,
                    message: result.status.message,
                    url: result.status.url
                });
            }
        } else {
            results.push({
                nodeRef: "",
                status: "OK",
                message: msg.get("group-action.filtered.started.title"),
                url: ""
            })
        }
    }

    var error = null;
    if (actionResultsData) {
        error = actionResultsData.getCancelCause();
    }

    model.json = {
        results: results,
        error: error
    };

})();

function exists(name, obj) {
    if(!obj) {
        status.setCode(status.STATUS_BAD_REQUEST, "Argument '" + name + "' is required");
        return false;
    }
    return true;
}
