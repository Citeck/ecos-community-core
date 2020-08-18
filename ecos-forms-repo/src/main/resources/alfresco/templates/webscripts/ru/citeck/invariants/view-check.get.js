(function() {

    var formType = args.formType,
        formKey = args.formKey,
        formId = args.formId || args.viewId;

    if (!formType) {
        if (args.type) {
            formType = "type";
            formKey = args.type;
        } else if (args.nodeRef) {
            formType = "nodeRef";
            formKey = args.nodeRef;
        } else if (args.taskId) {
            formType = "taskId";
            formKey = args.taskId;
        } else if (args.groupAction) {
            formType = "groupAction";
            formKey = args.groupAction;
        } else if (args.withoutSavingType) {
            formType = "withoutSavingType";
            formKey = args.withoutSavingType;
        } else if (args.workflowId) {
            formType = "workflowId";
            formKey = args.workflowId;
        }
    }

    if (!formType || !formKey) {
        status.setCode(status.STATUS_BAD_REQUEST, "formType and formKey should be specified");
        return;
    }

    var formService = services.get("ecosFormServiceImpl"),
        exists = formService.hasNodeView(formType, formKey, formId, null, null),
        defaultExists = formId ? formService.hasNodeView(formType, formKey, null, null, null) : exists;

    model.result = {
        exists: exists,
        defaultExists: defaultExists,
        eformExists: hasEForm()
    };

})();

function hasEForm() {
    // Forms moved to uiserv. This webscript is deprecated
    return false;
}
