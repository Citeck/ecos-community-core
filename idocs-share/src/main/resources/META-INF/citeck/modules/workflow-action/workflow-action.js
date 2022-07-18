if (Alfresco.component.WorkflowDetailsActions) {

    var userGroups, $workflow, $id, adminGroup;

    Alfresco.component.WorkflowDetailsActions.prototype.onWorkflowFormReady = function WDA_onWorkflowFormReady(layer, args)
    {
        $workflow = this.workflow;
        $id = this.id;
        adminGroup = this.options.adminGroup;

        if (!userGroups) {
            getUserGroups(Alfresco.constants.USERNAME);
        }
        else {
            setButtonVisibility();
        }
    };

    function getUserGroups(userName) {
        var url = YAHOO.lang.substitute(Alfresco.constants.PROXY_URI +
            "api/people/check-user-in-group?userName={userName}&groupFullName={groupFullName}", {
            userName: userName,
            groupFullName: adminGroup || 'GROUP_ALFRESCO_ADMINISTRATORS'
        });

        Alfresco.util.Ajax.jsonGet({
            url: url,
            successCallback: { fn: function(response) {
                setButtonVisibility(response.json);
            }},
            failureCallback: { fn: function(response) {
                userGroups = [];
                setButtonVisibility(false);
            }}
        });
    }

    function setButtonVisibility(isAdmin) {
        var isWorkflowActive = $workflow.isActive;
        var isInitiator = $workflow.initiator && $workflow.initiator.userName == Alfresco.constants.USERNAME;

        if ((isInitiator || isAdmin) && isWorkflowActive) {
            Dom.removeClass($id + "-cancel-button", "hidden");
        }

        if (isInitiator && !isWorkflowActive) {
            Dom.removeClass($id + "-delete-button", "hidden");
        }
    }
}
