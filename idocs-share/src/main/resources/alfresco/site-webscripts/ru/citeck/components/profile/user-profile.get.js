<import resource="classpath:/alfresco/site-webscripts/ru/citeck/components/invariants/view.lib.js">
<import resource="classpath:/alfresco/site-webscripts/ru/citeck/components/citeck.lib.js">
<import resource="classpath:/alfresco/templates/org/alfresco/import/alfresco-util.js">

(function() {
    var username = page.url.templateArgs["userid"];
    if (!username || username == null) {
        username = user.id;
    }

    var connector = remote.connect("alfresco");
    var url = "/api/orgstruct/authority/" + encodeURIComponent(username);
    var result = connector.call(url);
    if (result.status != 200) {
        AlfrescoUtil.error(result.status, 'Could not load user: request url = ' + url);
    }
    var currentUser = eval('(' + result + ')');

    connector = remote.connect("alfresco");
    url = '/citeck/has-permission?nodeRef=' + currentUser.nodeRef + '&permission=Write';
    result = connector.call(url);
    if (result.status != 200) {
        AlfrescoUtil.error(result.status, 'Could not check permissions for user: request url = ' + url);
    }
    var permissionResult = eval('(' + result + ')');

    model.userRef = currentUser.nodeRef;
    model.userName = username;
    model.writeMode = (permissionResult == true || permissionResult == "true");
    model.mode = (args.mode && model.writeMode) ? args.mode : "view";
    model.inlineEdit = args.inlineEdit ? (args.inlineEdit == "true") : model.writeMode;

    /*if (!(user.isAdmin || page.url.templateArgs.userid == user.id)) {
        model.inlineEdit = false;
    }*/

    var viewArgs = {
        nodeRef: currentUser.nodeRef,
        mode: model.mode
    };

    var viewData = getViewData(viewArgs);
    if(viewData == null) return;

    var view = viewData.view,
        attributeSet = getAttributeSet(args, view),
        attributes = getAttributes(view),
        attributeNames = map(attributes, function(attr) { return attr.attribute; });

    var defaultModel = {},
        publicViewProperties = [ 'class', 'id', 'kind', 'mode', 'template', 'params' ];

    defaultModel.view = {};
    for(var i in publicViewProperties) {
        var name = publicViewProperties[i];
        defaultModel.view[name] = view[name];
    }

    var invariantSet = { invariants: [], classNames: [] }, viewScopedInvariants = [];
    if (view.params.preloadInvariants == "true") {
        invariantSet = getInvariantSet(args, attributeNames),
        viewScopedInvariants = getViewScopedInvariants(view);

        if (invariantSet.model) {
            for(var name in invariantSet.model) { defaultModel[name] = invariantSet.model[name]; }
        }

        model.invariants = viewScopedInvariants.concat(invariantSet.invariants);
        model.classNames = invariantSet.classNames;
    }

    model.view = view;
    model.defaultModel = defaultModel;

    model.attributeSet = attributeSet;
    model.attributeNames = attributeNames;

    model.canBeDraft = viewData.canBeDraft;
})();
