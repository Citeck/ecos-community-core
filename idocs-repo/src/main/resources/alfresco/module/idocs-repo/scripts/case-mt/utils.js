
var utils = {
    getFirstAssoc: function(node, assocType) {
        return (node.assocs[assocType] || [])[0];
    },

    addAssigneeToRole: function(role, authority) {
        this.addAssocs(this.getRole(role), "icaseRole:assignees", [authority]);
    },

    addAssocs: function(node, assocType, targets) {
        var existing = node.assocs[assocType] || [];
        for (var i in targets) {
            var target = targets[i];
            if (!this.containsNode(existing, target)) {
                node.createAssociation(target, assocType);
            }
        }
    },

    setAssocs: function(node, assocType, targets) {
        var diff = this.getNodesDifference(node.assocs[assocType] || [], targets);
        for (var i in diff.removed) {
            node.removeAssociation(diff.removed[i], assocType);
        }
        for (var i in diff.added) {
            node.createAssociation(diff.added[i], assocType);
        }
    },

    getNodesDifference: function(base, target) {
        var added = [], removed = [];
        for (var i = 0; i < base.length; i++) {
            if (!this.containsNode(target, base[i])) {
                removed.push(base[i]);
            }
        }
        for (var i = 0; i < target.length; i++) {
            if (!this.containsNode(base, target[i])) {
                added.push(target[i]);
            }
        }
        return {'added': added, 'removed': removed};
    },

    containsNode: function(array, node) {
        for (var i = 0; i < array.length; i++) {
            if (array[i].equals(node)) {
                return true;
            }
        }
        return false;
    },

    getRole: function(obj) {
        if (typeof obj === 'string' || obj instanceof String) {
            return this.getRoleByName(obj);
        } else {
            return obj;
        }
    },

    getRoleByName: function(name) {
        var roles = document.childAssocs['icaseRole:roles'] || [];
        for (var i in roles) {
            if (roles[i].properties['icaseRole:varName'] == name) {
                return roles[i];
            }
        }
        return null;
    },

    getRoleAssignees: function(obj) {
        var role = this.getRole(obj);
        if (role) {
            return role.assocs['icaseRole:assignees'] || [];
        } else {
            return [];
        }
    },

    runAsSystem: function(func) {
        return Packages.org.alfresco.repo.security.authentication.AuthenticationUtil.runAsSystem(func);
    },

    restartActivity: function(name) {
        var activity = this.getActivityByName(name);
        if (activity) {
            CaseActivityService.reset(activity);
            CaseActivityService.startActivity(activity);
        }
    },

    resetActivity: function(name) {
        var activity = this.getActivityByName(name);
        if (activity) CaseActivityService.reset(activity);
    },

    startActivity: function(name) {
        var activity = this.getActivityByName(name);
        if (activity) CaseActivityService.startActivity(activity);
    },

    getActivityByName: function(name) {
        return CaseActivityService.getActivityByName(document, name)
    },

    getActiveWorkflowByTaskType: function(taskType) {
        var runtimeService = services.get("activitiRuntimeService");

        var wfService = services.get("WorkflowService");
        var workflows = wfService.getWorkflowsForContent(document.nodeRef, true);

        if (!workflows) return null;

        var wfIt = workflows.iterator();
        while (wfIt.hasNext()) {
            var workflow = wfIt.next();
            var id = workflow.getId().replaceAll("activiti\\$", "");
            if (runtimeService.getVariable(id, "wfcp_formKey") == taskType) {
                return workflow;
            }
        }
        return null;
    },

    addParallelExecutionForTask: function(taskName, performer) {
        if (!performer || !taskName) return false;
        return this.addParallelExecution(this.getActiveWorkflowByTaskType(taskName), performer);
    },

    addParallelExecution: function(workflow, performer) {
        if (!workflow) return false;

        var runtimeService = services.get("activitiRuntimeService");

        var workflowId = workflow.getId().replaceAll("activiti\\$", "");
        var performers = new Packages.java.util.ArrayList();
        performers.add(performer.nodeRef);
        var command = new Packages.ru.citeck.ecos.workflow.activiti.cmd.AddParallelExecutionInstanceCmd(workflowId, 'perform-sub-process', performers);
        return runtimeService.getCommandExecutor().execute(command);
    },

    getAuthorityName: function(authority) {
        var authorityName = null;
        if (authority.isSubType("cm:person")) {
            authorityName = authority.properties['cm:userName'];
        } else if (authority.isSubType("cm:authorityContainer")) {
            authorityName = authority.properties['cm:authorityName'];
        }
        return authorityName;
    },

    getNotEmptyAuthorityName: function (authority) {
        var authorityName = null;
        if (authority.isSubType("cm:person")) {
            authorityName = authority.properties['cm:userName'];
        } else if (authority.isSubType("cm:authorityContainer")) {
            var containerIsFilled = authority.childAssocs["cm:member"] != null
                && authority.childAssocs["cm:member"].length > 0;
            if (containerIsFilled) {
                authorityName = authority.properties['cm:authorityName'];
            }
        }
        return authorityName;
    },

    isInvolvedInProcess: function(authority, workflow) {
        if (!authority || !workflow) return false;

        var query = new Packages.org.alfresco.service.cmr.workflow.WorkflowTaskQuery();
        query.setActive(true);
        query.setTaskState(Packages.org.alfresco.service.cmr.workflow.WorkflowTaskState.IN_PROGRESS);
        query.setProcessId(workflow.getId());
        query.setActorId(this.getAuthorityName(authority));
        var tasks = services.get("WorkflowService").queryTasks(query);

        return tasks && tasks.size() > 0;
    },

    getProcessComment: function () {
        var comment = null;
        var nodeService = services.get("NodeService");
        if (process.bpm_package == null || !nodeService.exists(process.bpm_package.nodeRef)) {
            return null;
        }
        if (process.bpm_package.childAssocs['wfcp:performResults'] != null
            && process.bpm_package.childAssocs['wfcp:performResults'][0].properties["wfcp:comment"]) {
            comment = process.bpm_package.childAssocs['wfcp:performResults'][0].properties["wfcp:comment"];
        }
        return comment;
    },
    getRecipientsByRole: function (role) {
        caseRoleService.updateRoles(document);
        var confirmers = utils.getRoleAssignees(role) || [];
        var recipients = confirmers.map(utils.getNotEmptyAuthorityName);
        recipients = recipients.filter(function(e){return e});
        recipients = recipients.filter(function (item, pos) {
            return recipients.indexOf(item) == pos;
        });
        return recipients;
    },
    cancelActiveTasks: function (process) {
        this.runAsSystem(function () {
            var pwfId = process["workflowinstanceid"];

            var wfService = services.get("WorkflowService");
            var workflows = wfService.getWorkflowsForContent(document.nodeRef, true);

            if (!workflows) return null;

            var wfIt = workflows.iterator();
            while (wfIt.hasNext()) {
                var wf = wfIt.next();
                var id = wf.getId();
                if (pwfId && pwfId != id) {
                    var query = new Packages.org.alfresco.service.cmr.workflow.WorkflowTaskQuery();
                    query.setActive(true);
                    query.setTaskState(Packages.org.alfresco.service.cmr.workflow.WorkflowTaskState.IN_PROGRESS);
                    query.setProcessId(wf.getId());
                    var tasks = wfService.queryTasks(query);
                    if (tasks && tasks.size() > 0) {
                        for (var i = 0; i < tasks.size(); i++) {
                            var task = tasks.get(i);
                            if (task) {
                                wfService.endTask(task.getId(), null);
                            }
                        }
                    }
                }
            }
            return null;
        })
    },

    translateDaysNumberToDate: function(daysNumber) {
        var today = new Date();
        var newDate = new Date(today);
        newDate.setDate(today.getDate() + daysNumber);
        return newDate;
    },

    resetCase: function () {
        //reset activities
        CaseActivityService.reset(document);

        historyService.removeEventsByDocument(document);

        //remove all documents
        var docs = document.childAssocs['icase:documents'] || [];
        for each(var doc in docs) {
            doc.addAspect("sys:temporary");
            doc.remove();
        }

        //remove all workflows
        var workflows = services.get('WorkflowService').getWorkflowsForContent(document.nodeRef, false);
        for (var i = 0; i < workflows.size(); i++) {
            services.get('WorkflowService').deleteWorkflow(workflows.get(i).getId());
        }

        //remove process outcome
        process["outcome"] = "";
        process.wfcp_performOutcome = "";
    },

    isTaskOutcomeEquals: function (value) {
        return process["outcome"] === value;
    },

    get18NMessage: function (id) {
        return Packages.org.springframework.extensions.surf.util.I18NUtil.getMessage(id);
    },

    iterableToJSArray: function (iterable) {
        var result = [];
        if (iterable) {
            var it = iterable.iterator();
            while (it.hasNext()) {
                result.push(it.next());
            }
        }
        return result;
    }
};

var emailUtils = {
    getRecipientsNamesByRole: function (role) {
        var recipients = {
            parentNames: [],
            childNames: []
        };
        var roleAssignees = caseRoleService.getAssignees(document, role);

        if (!roleAssignees || roleAssignees.length == 0) {
            return null;
        }

        var parent = [];
        var childs = [];

        for each (var assignee in roleAssignees) {
            parent.push(this.getAuthorityDisplayName(assignee));
            if (assignee.typeShort == "cm:authorityContainer") {
                var authorityChilds = assignee.getChildren();
                for each (var child in authorityChilds) {
                    if (child.isSubType("cm:person")) {
                        childs.push(this.getAuthorityDisplayName(child))
                    }
                }
            }

        }
        recipients.parentNames = parent.toString();
        recipients.childNames = childs.toString();
        return recipients;
    },
    getAuthorityDisplayName: function (authority) {
        var authorityName = null;
        if (authority) {
            if (authority.isSubType("cm:person")) {
                authorityName = authority.properties['cm:firstName'];
                var lastName = authority.properties['cm:lastName'];
                if (lastName) {
                    authorityName = authorityName + " " + lastName;
                }
            } else if (authority.isSubType("cm:authorityContainer")) {
                authorityName = authority.properties['cm:authorityDisplayName'];
            }
        }
        return authorityName;
    },
    getTemplate: function (templateName) {
        var templatePath = "/app:company_home/app:dictionary/app:email_templates/app:notify_email_templates";
        return search.selectNodes(templatePath + "/" + templateName)[0];
    },
    getFirstPerformerName: function () {
        var performer = process.wfcp_performers;
        if (performer && performer.size() > 0) {
            var performerNode = search.findNode(performer.get(0));
            return this.getAuthorityDisplayName(performerNode);
        } else {
            return null;
        }
    }
};

var alfrescoUtils = services.get("utilsScript");
