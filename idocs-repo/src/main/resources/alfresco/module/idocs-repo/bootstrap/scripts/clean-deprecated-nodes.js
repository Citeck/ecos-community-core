
var nodesToClean = [
    "workspace://SpacesStore/menu-configs",
    "/cm:IDocsRoot/journal:journalMetaRoot/cm:journals/cm:menu-configs",
    "/cm:IDocsRoot/journal:journalMetaRoot/cm:journals/cm:ecos-forms",
    "/app:company_home/app:dictionary/cm:ecos-forms",
    "/cm:IDocsRoot/journal:journalMetaRoot/cm:journals/cm:ui-actions",
    "/cm:IDocsRoot/journal:journalMetaRoot/cm:journals/cm:contractors"
];

for (var i = 0; i < nodesToClean.length; i++) {
    var node = null;
    var nodePath = nodesToClean[i];
    if (nodePath.indexOf("workspace") === 0) {
        node = search.findNode(nodePath);
    } else {
        node = (search.selectNodes(nodePath) || [])[0];
    }
    if (node) {
        logger.log("[clean-deprecated-nodes.js] Remove node with path: '" + nodePath + "'");
        node.remove();
    }
}
