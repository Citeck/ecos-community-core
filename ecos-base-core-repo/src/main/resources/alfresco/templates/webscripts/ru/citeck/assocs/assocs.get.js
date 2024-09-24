const ASSOCS_LIMIT = 200; //sometimes assocs count is too high

(function() {

    var assocsCount;

    function checkContentType(node, contentTypes) {
        var result = false;
        for (var i = 0; i < contentTypes.length; i++) {
            var type = contentTypes[i];
            if (type) {
                if (node.isSubType(type)) {
                    result = true;
                    break;
                }
            }
        }
        return result;
    }

    function getTitle(attributeName, dictionaryService, messageService) {
        var attQName = citeckUtils.createQName(attributeName);
        var attribute = dictionaryService.getProperty(attQName);
        if (!attribute) {
            attribute = dictionaryService.getAssociation(attQName);
        }
        return attribute ? attribute.getTitle(messageService) : null;
    }


    var dictionaryService = services.get("dictionaryService"),
        messageService = services.get("messageService"),
        ecosNodeService = services.get("ecosNodeServiceJS");


    var nodeRef = args['nodeRef'],
        assocTypes = args['assocTypes'],
        contentTypes = args['contentTypes'] == undefined ? [] : args['contentTypes'].split(','),
        addAssocs = args['addAssocs']||"true";

    if (assocTypes == undefined) assocTypes = "";
    var assocs = [];

    if(nodeRef != null && nodeRef.length != 0) {
        for each(type in assocTypes.split(','))
            if (type != '' && nodeRef != null && nodeRef.length != 0){
                var node = search.findNode(nodeRef);
                var typeQName = citeckUtils.createQName(type);
                if (node !== null) {
                    var sourceAssocs = ecosNodeService.getSourceAssocsNodeRefsLimited(nodeRef, typeQName, 0, ASSOCS_LIMIT), sourceNodeRef = [];
                    if (sourceAssocs == undefined) {
                        sourceAssocs = "";
                    } else {
                        for (var sourceIdx = 0; sourceIdx < sourceAssocs.size(); sourceIdx++) {
                            var source = search.findNode(sourceAssocs.get(sourceIdx));
                            if(source != null && source.length != 0 && source.hasPermission("Read")) {
                                if (contentTypes.length > 0 && !checkContentType(source, contentTypes))
                                    continue;

                                var titleSource = source.name;
                                if (source.properties["cm:title"] != null && source.properties["cm:title"] != "") {
                                    titleSource = source.properties["cm:title"];
                                }

                                sourceNodeRef.push({
                                    'nodeRef': source.nodeRef.toString(),
                                    'name': source.name,
                                    'isFolder': source.isSubType("cm:folder").toString(),
                                    'isContent': source.isSubType("cm:content").toString(),
                                    'source': source,
                                    'title': titleSource
                                });
                            }
                        }
                    }
                    var targetAssocs = node.assocs[type];
                    var targetNodeRef = [];
                    if (targetAssocs == undefined) {
                        targetAssocs = "";
                    } else {
                        assocsCount = Math.min(ASSOCS_LIMIT, targetAssocs.length);
                        for (var targetIdx = 0; targetIdx < assocsCount; targetIdx++) {
                            var target = targetAssocs[targetIdx];
                            if(target != null && target.length != 0 && target.hasPermission("Read")) {
                                if (contentTypes.length > 0 && !checkContentType(target, contentTypes))
                                    continue;

                                var titleTarget = target.name;
                                if (target.properties["cm:title"] != null && target.properties["cm:title"] != "") {
                                    titleTarget = target.properties["cm:title"];
                                }

                                targetNodeRef.push({
                                    'nodeRef': target.nodeRef.toString(),
                                    'name': target.name,
                                    'isFolder': target.isSubType("cm:folder").toString(),
                                    'isContent': target.isSubType("cm:content").toString(),
                                    'target': target,
                                    'title': titleTarget
                                });
                            }
                        }
                    }
                    var childAssocs = ecosNodeService.getChildAssocsNodeRefsLimited(nodeRef, typeQName, "", null, ASSOCS_LIMIT, false);
                    var childNodeRef = [];
                    if (childAssocs == undefined) {
                        childAssocs = "";
                    } else {
                        for (var childIdx = 0; childIdx < childAssocs.size(); childIdx++) {
                            var child = search.findNode(childAssocs.get(childIdx));
                            if(child != null && child.length != 0 && child.hasPermission("Read")) {
                                if (contentTypes.length > 0 && !checkContentType(child, contentTypes))
                                    continue;

                                var titleChild = child.name;
                                if (child.properties["cm:title"] != null && child.properties["cm:title"] != "") {
                                    titleChild = child.properties["cm:title"];
                                }

                                childNodeRef.push({
                                    'nodeRef': child.nodeRef.toString(),
                                    'name': child.name,
                                    'isFolder': child.isSubType("cm:folder").toString(),
                                    'isContent': child.isSubType("cm:content").toString(),
                                    'child': child,
                                    'title': titleChild
                                });
                            }
                        }
                    }
                    assocs.push({
                        'type': type,
                        'sources': sourceNodeRef,
                        'targets': targetNodeRef,
                        'children': childNodeRef
                    });
                }
            }
    }
    model.data = {
        'assocs': assocs,
        'addAssocs': addAssocs
    }

    var cacheMaxAge = args['cacheMaxAge'];

    if (cacheMaxAge) {
        cache.maxAge = cacheMaxAge;
    }

})();
