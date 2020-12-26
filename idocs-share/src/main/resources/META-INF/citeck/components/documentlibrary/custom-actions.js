/*
 * Copyright (C) 2008-2015 Citeck LLC.
 *
 * This file is part of Citeck EcoS
 *
 * Citeck EcoS is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Citeck EcoS is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with Citeck EcoS. If not, see <http://www.gnu.org/licenses/>.
 */
require([
    'citeck/components/form/constraints'
], function() {

    var PopupManager = Alfresco.util.PopupManager;

    /**
     * Type metadata renderer.
     */
    YAHOO.Bubbling.fire("registerRenderer",
        {
            propertyName: "type",
            renderer: function(record, label) {
                var type = record.node.type;
                return "<span class='item'><em>" + label + "</em>" + Alfresco.util.message("type." + type.replace(":", "_")) + "</span>";
            }
        });

    /**
     * Upload new version action.
     *
     * All is like in the original version of handler,
     * except that siteId is not set, because it is not necessary for new version upload,
     * but can cause errors, when site is not accessible.
     *
     * @method onActionUploadNewVersion
     * @param record {object} Object literal representing the file or folder to be actioned
     */
    YAHOO.Bubbling.fire("registerAction", {
        actionName: "onActionUploadNewVersion",
        fn: function(record) {
            var jsNode = record.jsNode,
                displayName = record.displayName,
                nodeRef = jsNode.nodeRef,
                version = record.version;

            if (!this.fileUpload) {
                this.fileUpload = Alfresco.getFileUploadInstance();
            }

            // Show uploader for multiple files
            var description = this.msg("label.filter-description", displayName),
                extensions = "*";

            if (displayName && new RegExp(/[^\.]+\.[^\.]+/).exec(displayName)) {
                // Only add a filtering extension if filename contains a name and a suffix
                extensions = "*" + displayName.substring(displayName.lastIndexOf("."));
            }

            if (record.workingCopy && record.workingCopy.workingCopyVersion) {
                version = record.workingCopy.workingCopyVersion;
            }

            var singleUpdateConfig =
                {
                    updateNodeRef: nodeRef.toString(),
                    updateFilename: displayName,
                    updateVersion: version,
                    uploadURL: '/api/v2/citeck/upload',
                    flashUploadURL: '/api/v2/citeck/upload',
                    htmlUploadURL: '/api/v2/citeck/upload',
                    overwrite: true,
                    filter: [],
                    mode: this.fileUpload.MODE_SINGLE_UPDATE,
                    onFileUploadComplete:
                        {
                            fn: function() {
                                // do not use site, it can be unaccessible
                                var site = this.options.siteId;
                                this.options.siteId = null;
                                var callback = this.onNewVersionUploadCompleteCustom || this.onNewVersionUploadComplete;
                                callback.apply(this, arguments);
                                this.options.siteId = site;
                            },
                            scope: this
                        }
                };
            /* comment the annoying code out
            if ($isValueSet(this.options.siteId))
            {
               singleUpdateConfig.siteId = this.options.siteId;
               singleUpdateConfig.containerId = this.options.containerId;
            }
            */
            this.fileUpload.show(singleUpdateConfig);
        }
    });

    function getVersions(record, propertyName) {
        var serialized = record.node.properties[propertyName.toString()] || "";
        var userRecords = serialized.split(/[,]/);
        var keyPart = "user";
        var partNames = ["user", "versionRef", "versionLabel", "decision"];
        var result = {};
        for (var i = 0; i < userRecords.length; i++) {
            var record = {};
            var parts = userRecords[i].split(/[|]/);
            for (var j = 0; j < partNames.length && j < parts.length; j++) {
                record[partNames[j]] = parts[j];
            }
            result[record[keyPart]] = record;
        }
        return result;
    }

    YAHOO.Bubbling.fire("registerAction", {
        actionName: "onActionCompareWithConsidered",
        fn: function(record) {

            var userName = Alfresco.constants.USERNAME;

            // get consideredVersions property:
            var consideredVersions = getVersions(record, "wfcf:consideredVersions");

            if (!consideredVersions[userName]) {
                Alfresco.util.PopupManager.displayMessage({
                    text: this.msg("confirm.no-considered-versions.message")
                });
                return;
            }

            // get considerableVersions property:
            var considerableVersions = getVersions(record, "wfcf:considerableVersions");

            var considerableVersionsRef = (considerableVersions && considerableVersions[userName]
                && considerableVersions[userName].versionRef) ? considerableVersions[userName].versionRef : record.node.nodeRef;

            window.open(YAHOO.lang.substitute(Alfresco.constants.URL_PAGECONTEXT + "versions-difference?nodeRef={nodeRef}&versRef={versRef}", {
                nodeRef: considerableVersionsRef,
                versRef: consideredVersions[userName].versionRef
            }), "_blank");
        }
    });

    function onConfirmDecline(record, decision) {

        var considerableVersions = getVersions(record, "wfcf:considerableVersions");
        var confirmStatus = considerableVersions[Alfresco.constants.USERNAME];
        var considerableVersionLabel = confirmStatus && confirmStatus.versionLabel || null;
        var url = Alfresco.constants.PROXY_URI + "citeck/confirm?nodeRef=" + record.nodeRef + "&decision=" + decision;
        if (considerableVersionLabel != null) {
            url += "&versionLabel=" + considerableVersionLabel;
        }
        Alfresco.util.Ajax.jsonPost({
            url: url,
            successCallback: {
                scope: this,
                fn: function(response) {
                    YAHOO.Bubbling.fire("metadataRefresh", {
                        "highlightFile": record.fileName
                    });
                }
            }
        });
        var currentVersionLabel = record.version;
        if (currentVersionLabel != considerableVersionLabel) {
            if (considerableVersionLabel != null) {
                alert(this.msg("confirm-status.current-version-wrong.confirm", considerableVersionLabel, currentVersionLabel));
            }
            Alfresco.util.Ajax.jsonGet({
                url: Alfresco.constants.PROXY_URI + "citeck/confirm/update-considerable?nodeRef=" + record.nodeRef,
                successCallback: {
                    scope: this,
                    fn: function(response) {
                        YAHOO.Bubbling.fire("metadataRefresh", {
                            "highlightFile": record.fileName
                        });
                    }
                }
            });
        }
    }

    YAHOO.Bubbling.fire("registerAction", {
        actionName: "onActionConfirm",
        fn: function(record) {
            onConfirmDecline.call(this, record, "confirm");
        }
    });

    YAHOO.Bubbling.fire("registerAction", {
        actionName: "onActionDecline",
        fn: function(record) {
            onConfirmDecline.call(this, record, "decline");
        }
    });

    function renderConfirmStatus(record, confirmer, labelPrefix, active) {
        var consideredVersions = getVersions(record, "wfcf:consideredVersions");
        var confirmStatus = consideredVersions[confirmer];
        var consideredVersionLabel = confirmStatus && confirmStatus.versionLabel || null;
        var currentVersionLabel = record.version;

        var html = '';

        if (consideredVersionLabel == null) {
            html = this.msg(labelPrefix + ".no-decision", confirmer);
        } else if (consideredVersionLabel == currentVersionLabel) {
            html = this.msg(labelPrefix + ".current-version." + confirmStatus.decision, consideredVersionLabel);

            if (active) {
                var counterActions = {
                    "decline": {
                        "title": "document-confirm",
                        "handler": "onActionConfirm",
                        "label": "actions.document.confirm"
                    },
                    "confirm": {
                        "title": "document-decline",
                        "handler": "onActionDecline",
                        "label": "actions.document.decline"
                    }
                };
                var counterAction = counterActions[confirmStatus.decision];
                html += ' (' + '<div class="inline-action ' + counterAction.title + '" id="' + counterAction.handler + '"><a class="action-link faded">' + this.msg(counterAction.label) + '</a></div>' + ')';
            }
        } else {
            html = this.msg(labelPrefix + ".older-version." + confirmStatus.decision, consideredVersionLabel, currentVersionLabel);
        }
        return '<span class="item confirmStatus ' + (confirmStatus ? confirmStatus.decision : "not-considered") + '">' + html + '</span>';
    }

    function renderCurrentConfirm(record, confirmer, labelPrefix, active) {

        var considerableVersions = getVersions(record, "wfcf:considerableVersions");
        var considerableRecord = considerableVersions[confirmer];
        var considerableVersionLabel = considerableRecord && considerableRecord.versionLabel || null;

        var currentVersionLabel = record.version;

        var html = '';
        if (considerableVersionLabel != null) {
            if (considerableVersionLabel == currentVersionLabel) {
                html = this.msg(labelPrefix + ".considerable.current", considerableVersionLabel);
            } else {
                html = this.msg(labelPrefix + ".considerable.not-current", considerableVersionLabel, currentVersionLabel);

                if (active) {
                    var counterAction = {
                        "title": "document-update",
                        "handler": "onUpdateVersion",
                        "label": "actions.document.update"
                    };
                    html += ' (' + '<div class="inline-action ' + counterAction.title + '" id="' + counterAction.handler + '"><a class="action-link faded">' + this.msg(counterAction.label) + '</a></div>' + ')';
                }

            }
        }
        return '<span class="item">' + html + '</span>';
    }

    YAHOO.Bubbling.fire("registerRenderer", {
        propertyName: "confirmStatus",
        renderer: function(record, label) {
            return renderConfirmStatus.call(this, record, Alfresco.constants.USERNAME, "confirm-status", true);
        }
    });

    YAHOO.Bubbling.fire("registerRenderer", {
        propertyName: "currentConfirmStatus",
        renderer: function(record, label) {
            return renderCurrentConfirm.call(this, record, Alfresco.constants.USERNAME, "current-status", true);
        }
    });

    YAHOO.Bubbling.fire("registerRenderer", {
        propertyName: "correctStatus",
        renderer: function(record, label) {
            var senderField = document.getElementsByName("prop_cwf_sender")[0];
            if (senderField && senderField.value) {
                var sender = senderField.value;
                return renderConfirmStatus.call(this, record, sender, "correct-status", false);
            } else {
                return "";
            }
        }
    });


    /**
     * Block contractor action.
     *
     * @method onActionBlockContractor
     * @param asset {object} Object literal representing file or folder to be actioned
     */
    YAHOO.Bubbling.fire("registerAction", {
        actionName: "onActionBlockContractor",
        fn: function(asset) {

            var displayName = asset.displayName,
                nodeRef = new Alfresco.util.NodeRef(asset.nodeRef);

            this.modules.actions.genericAction(
                {
                    success:
                        {
                            event:
                                {
                                    name: "metadataRefresh"
                                },
                            message: this.msg("message.blockContractor.success", displayName)
                        },
                    failure:
                        {
                            message: this.msg("message.blockContractor.failure", displayName)
                        },
                    webscript:
                        {
                            name: "acm/contractorblock?nodeRef={nodeRef}",
                            stem: Alfresco.constants.PROXY_URI,
                            method: Alfresco.util.Ajax.GET,
                            params:
                                {
                                    nodeRef: nodeRef
                                }
                        },
                    config:
                        {}

                });
            /*
             var nodeRef = new Alfresco.util.NodeRef(asset.nodeRef);
                 var xmlHttp_add = new XMLHttpRequest();
                 xmlHttp_add.open("GET", Alfresco.constants.PROXY_URI + "acm/contractorblock?nodeRef=" + nodeRef, false);
                 xmlHttp_add.send(null);
                 if (xmlHttp_add.status != 200) {
                     return;
                 }
                 nodeRef = eval('(' + xmlHttp_add.responseText + ')').nodeRef;

             document.location.reload();
             */
        }
    });

    YAHOO.Bubbling.fire("registerAction", {
        actionName: "onVersionDocumentViewDetails",
        fn: function(record) {
            var considerableVersions = getVersions(record, "wfcf:considerableVersions");
            var confirmStatus = considerableVersions[Alfresco.constants.USERNAME];
            var considerableVersionRef = confirmStatus && confirmStatus.versionRef || null;
            var versRef = considerableVersionRef.replace("versionStore://", "workspace://");
            var url = "card-details?nodeRef=" + record.nodeRef + "&versRef=" + versRef;
            open(url);
        }
    });

    YAHOO.Bubbling.fire("registerAction", {
        actionName: "onDocumentConsiderableDownload",
        fn: function(record) {
            var considerableVersions = getVersions(record, "wfcf:considerableVersions");
            var confirmStatus = considerableVersions[Alfresco.constants.USERNAME];
            var considerableVersionRef = confirmStatus && confirmStatus.versionRef || null;
            var versRef = considerableVersionRef.replace("://", "/");
            var docName = record.node.properties["cm:name"];
            var url = "/share/proxy/alfresco/api/node/content/" + versRef + "/" + docName + "?a=true";
            open(url);
        }
    });

    YAHOO.Bubbling.fire("registerAction", {
        actionName: "onUpdateVersion",
        fn: function(record) {
            Alfresco.util.Ajax.jsonGet({
                url: Alfresco.constants.PROXY_URI + "citeck/confirm/update-considerable?nodeRef=" + record.nodeRef,
                successCallback: {
                    scope: this,
                    fn: function(response) {
                        YAHOO.Bubbling.fire("metadataRefresh", {
                            "highlightFile": record.fileName
                        });
                    }
                }
            });
        }
    });
    YAHOO.Bubbling.fire("registerAction", {
        actionName: "onActionRegister",
        fn: function(record) {
            Alfresco.util.Ajax.jsonPost({
                url: Alfresco.constants.PROXY_URI + "citeck/document-registration/register?nodeRef=" + record.nodeRef,
                successCallback: {
                    scope: this,
                    fn: function(response) {
                        document.location.reload(true);
                    }
                }
            });
        }
    });

    YAHOO.Bubbling.fire("registerAction", {
        actionName: "onActionCopyInPlace",
        fn: function(record) {
            var destination = record.parent.nodeRef;

            Alfresco.util.Ajax.jsonPost({
                url: Alfresco.constants.PROXY_URI + "citeck/node/copy?source=" + record.nodeRef + "&destination=" + destination,
                successCallback: {
                    scope: this,
                    fn: function(response) {
                        var resultNodeRef = response.json.copy;
                        var copyLocation = Alfresco.util.siteURL("card-details?nodeRef=" + resultNodeRef);
                        document.location = copyLocation;
                    }
                }
            });
        }
    });

    function onActionRemoveCaseItem(nodeRef, caseNodeRef, caseElementConfigName) {
        Alfresco.util.Ajax.jsonDelete({
            url: Alfresco.constants.PROXY_URI + "citeck/case/elements"
                + "?nodeRef=" + nodeRef
                + "&caseNode=" + caseNodeRef
                + "&elementType=" + caseElementConfigName,
            successCallback: {
                fn: function(response) {
                    YAHOO.Bubbling.fire("metadataRefresh");
                },
                scope: this
            },
            failureMessage: this.msg("message.delete.failure", nodeRef),
            scope: this
        });
    }

    YAHOO.Bubbling.fire("registerAction", {
        actionName: "onActionRemoveCaseItem",
        fn: function(record) {
            if (record.actionRendererAdditionalObject) {
                var scope = this,
                    nodeRef = record.nodeRef,
                    caseNodeRef = record.actionRendererAdditionalObject.caseNodeRef,
                    caseElementConfigName = record.actionRendererAdditionalObject.caseElementConfigName;

                PopupManager.displayPrompt({
                    title: this.msg("message.confirm.delete.1.title", 1),
                    text: this.msg("message.confirm.delete", (record.displayName ? record.displayName : nodeRef)),
                    noEscape: true,
                    buttons: [
                        {
                            text: this.msg("actions.button.ok"),
                            handler: function dlA_onActionOk() {
                                onActionRemoveCaseItem.call(scope, nodeRef, caseNodeRef, caseElementConfigName);
                                this.destroy();
                            }
                        },
                        {
                            text: this.msg("actions.button.cancel"),
                            handler: function dlA_onActionCancel() {
                                this.destroy();
                            },
                            isDefault: true
                        }]
                });
            }
        }
    });

    YAHOO.Bubbling.fire("registerAction", {
        actionName: "onActionCaseSaveAsTemplate",
        fn: function(record) {
            var self = this;
            return PopupManager.displayPrompt({
                title: this.msg("message.confirm.title"),
                text: this.msg("message.confirm.save-as-template"),
                buttons: [
                    {
                        text: self.msg("button.yes"),
                        handler: function() {
                            Alfresco.util.Ajax.jsonPost({
                                url: Alfresco.constants.PROXY_URI + "citeck/case/template"
                                    + "?nodeRef=" + record.nodeRef,
                                successCallback: {
                                    fn: function(response) {
                                        window.location = Alfresco.util.siteURL("card-details?nodeRef=" + response.json.template);
                                    }
                                },
                                failureCallback: {
                                    scope: this,
                                    fn: function(response) {
                                        var msg = response.json.message.substr(0, response.json.message.indexOf('('));
                                        Alfresco.util.PopupManager.displayPrompt({
                                            text: msg
                                        });
                                    }
                                }
                            });
                            this.destroy();
                        }
                    },
                    {
                        text: self.msg("button.no"),
                        handler: function() {
                            this.destroy();
                        },
                        isDefault: true
                    }
                ]
            });
        }
    });

    function onDeleteAssociations(sourceRef, targetRef, assocTypes) {
        Alfresco.util.Ajax.request({
            url: Alfresco.constants.PROXY_URI + "citeck/remove-assocs?sourceRef=" + sourceRef +
                "&targetRef=" + targetRef + "&assocTypes=" + assocTypes,
            method: Alfresco.util.Ajax.DELETE,
            successCallback: {
                fn: function(response) {
                    YAHOO.Bubbling.fire("metadataRefresh");
                },
                scope: this
            },
            failureMessage: this.msg("message.delete.failure", targetRef),
            scope: this
        });
    }

    YAHOO.Bubbling.fire("registerAction", {
        actionName: "onDeleteAssociations",
        fn: function(record) {
            if (record.actionRendererAdditionalObject) {
                var scope = this,
                    targetRef = record.nodeRef,
                    sourceRef = record.actionRendererAdditionalObject.sourceRef,
                    assocTypes = record.actionRendererAdditionalObject.assocTypes;

                Alfresco.util.PopupManager.displayPrompt({
                    title: this.msg("message.confirm.delete.1.title", 1),
                    text: this.msg("message.confirm.delete", (record.displayName ? record.displayName : nodeRef)),
                    noEscape: true,
                    buttons: [
                        {
                            text: this.msg("actions.button.ok"),
                            handler: function dlA_onActionOk() {
                                onDeleteAssociations.call(scope, sourceRef, targetRef, assocTypes);
                                this.destroy();
                            }
                        },
                        {
                            text: this.msg("actions.button.cancel"),
                            handler: function dlA_onActionCancel() {
                                this.destroy();
                            },
                            isDefault: true
                        }]
                });
            }
        }
    });

    YAHOO.Bubbling.fire("registerAction", {
        actionName: "onDocumentFillTemplateDialog",
        fn: function(record) {
            var documentRef = record.nodeRef;

            Citeck.forms.dialog(documentRef, "template", function() {
                window.location.reload();
            }, {
                forceOldDialog: true
            });

        }
    });

    YAHOO.Bubbling.fire("registerAction", {
        actionName: "onShowTaskForm",
        fn: function(record, owner) {
            var jsNode = record.jsNode,
                params = this.getAction(record, owner).params,
                formMode = params.formMode || "view";

            Alfresco.util.Ajax.jsonGet({
                url: Alfresco.constants.PROXY_URI + "citeck/invariants/view-check?taskId=" + jsNode.properties["cm:name"],
                successCallback: {
                    scope: this,
                    fn: function(response) {
                        var redirection = "/share/page/";

                        if (response.serverResponse.status == 200) {
                            var formId = response.json.exists ? "task-view-edit" : (formMode == "edit" ? "task-edit" : "task-details"),
                                requestParams = "?taskId=" + jsNode.properties["cm:name"] + "&formMode=" + formMode;

                            redirection += formId + requestParams;
                        }
                        window.location = redirection;
                    }
                }
            });
        }
    });

    function getSiteNameFromLocation(location) {
        var result = null;
        if (location) {
            if (location.site) {
                result = location.site.name;
            } else if (location.path) {
                var siteRegexp = /^\/(Sites|Сайты)\/(.+?)\/.+$/g;
                var siteArr = siteRegexp.exec(location.path);
                if (siteArr && siteArr[2])
                    result = siteArr[2];
            }
        }
        return result;
    }

    var CALENDAR_VIEW = 'day';
    YAHOO.Bubbling.fire("registerAction", {
        actionName: "onActionOpenCalendarEvent",
        fn: function(record) {
            var node = record.node,
                location = record.location;
            if (node && node.properties && node.properties['ia:fromDate'] && location) {
                var fromDate = node.properties['ia:fromDate'],
                    siteName = getSiteNameFromLocation(location);
                if (siteName) {
                    var date = Alfresco.util.formatDate(fromDate.iso8601, "yyyy-mm-dd");
                    document.location = '/share/page/site/' + siteName + '/calendar?view=' + CALENDAR_VIEW + '&date=' + date;
                }
            }
        }
    });

    Citeck.format = Citeck.format || {};
    Citeck.format.openCalendar = function() {
        return function(elCell, oRecord, oColumn, oData) {
            if (!oData) {
                elCell.innerHTML = '';
                return;
            }
            var url = null,
                data = oRecord.getData();
            if (data.nodeRef)
                url = Alfresco.constants.URL_SERVICECONTEXT + 'citeck/calendar/location?nodeRef=' + data.nodeRef;
            elCell.innerHTML = !url ? oData : '<a href="' + url + '">' + oData + '</a>';
        }
    };

    YAHOO.Bubbling.fire("registerAction", {
        actionName: "onActionUploadSignedVersion",
        fn: function(record) {
            var jsNode = record.jsNode,
                nodeRef = jsNode.nodeRef;
            var fui = Alfresco.getFileUploadInstance();
            var uploadUrl = "api/citeck/upload?assoctype=idocs:signedVersion";
            uploadUrl += '&contenttype=cm:content';
            //todo timefix
            //added for correct param  parsing
            uploadUrl += '&addedParam=param';
            var cfg = {
                destination: nodeRef.nodeRef,
                htmlUploadURL: uploadUrl,
                flashUploadURL: uploadUrl,
                onFileUploadComplete: {
                    scope: this,
                    fn: function(response) {
                        var files = response.successful;
                        if (typeof files !== 'undefined' && files.length > 0) {
                            YAHOO.Bubbling.fire("metadataRefresh");
                        } else {
                            alert(Alfresco.util.message("message.cannotupload"));
                        }
                    }
                }
            }
            //cfg.mode = fui.MODE_SINGLE_UPDATE;
            fui.show(cfg);
        }
    });

    YAHOO.Bubbling.fire("registerAction", {
        actionName: "onShowSignedVersion",
        fn: function(record) {
            var jsNode = record.jsNode,
                nodeRef = jsNode.nodeRef;

            Alfresco.util.Ajax.jsonGet({
                url: Alfresco.constants.PROXY_URI + "citeck/node?nodeRef=" + nodeRef.nodeRef,
                successCallback: {
                    scope: this,
                    fn: function(response) {
                        var redirection = '/share/page/card-details?nodeRef=' + response.json.childAssocs["idocs:signedVersion"][0];
                        window.location = redirection;
                    }
                }
            });
        }
    });


    YAHOO.Bubbling.fire("registerAction", {
        actionName: "onActionDialogForm",
        fn: function(record, owner) {
            var scope = this,
                nodeRef = record.nodeRef,
                jsNode = record.jsNode,
                params = this.getAction(record, owner).params,
                formId = (params && params.formId) ? params.formId : '';

            var successCallback = function(response) {
                // Reload the node's metadata
                var webscriptPath = "components/documentlibrary/data";
                if (Alfresco.util.isValueSet(this.options.siteId)) {
                    webscriptPath += "/site/" + encodeURIComponent(this.options.siteId)
                }
                Alfresco.util.Ajax.request({
                    url: Alfresco.util.combinePaths(Alfresco.constants.URL_SERVICECONTEXT, webscriptPath, "/node/", jsNode.nodeRef.uri) + "?view=" + this.actionsView,
                    successCallback:
                        {
                            fn: function(response) {
                                var record = response.json.item;
                                record.jsNode = new Alfresco.util.Node(response.json.item.node);

                                // Fire "renamed" event
                                YAHOO.Bubbling.fire(record.node.isContainer ? "folderRenamed" : "fileRenamed",
                                    {
                                        file: record
                                    });

                                // Fire "tagRefresh" event
                                YAHOO.Bubbling.fire("tagRefresh");

                                // Display success message
                                Alfresco.util.PopupManager.displayMessage(
                                    {
                                        text: this.msg("message.details.success")
                                    });

                                var reload = (params && params.reload) ? params.reload : false;
                                if (reload == "true") {
//                          // Issue #4236
                                    location.reload();
                                } else {
                                    // Refresh the document list...
                                    this._updateDocList.call(this);
                                    YAHOO.Bubbling.fire("metadataRefresh");
                                }
                            },
                            scope: this
                        },
                    failureCallback:
                        {
                            fn: function(response) {
                                Alfresco.util.PopupManager.displayMessage(
                                    {
                                        text: this.msg("message.details.failure")
                                    });
                            },
                            scope: this
                        }
                });
            };

            Citeck.forms.dialog(nodeRef, formId, {
                scope: this,
                fn: successCallback
            }, {
                title: (params && params.header) ? params.header : "actions.document.dialog-form"
            });
        }
    });

    YAHOO.Bubbling.fire("registerAction", {
        actionName: "onActionCaseStartTask",
        fn: function(record) {
            var scope = this,
                nodeRef = record.nodeRef;
            PopupManager.displayPrompt(
                {
                    title: this.msg("message.confirm.title", 1),
                    text: this.msg("message.confirm.task.start"),
                    noEscape: true,
                    buttons: [
                        {
                            text: this.msg("actions.button.ok"),
                            handler: function dlA_onActionOk() {
                                Alfresco.util.Ajax.jsonPost({
                                    url: Alfresco.constants.PROXY_URI + "citeck/case-activity/start-activity?nodeRef=" + record.nodeRef,
                                    successCallback: {
                                        scope: this,
                                        fn: function(response) {
                                            YAHOO.Bubbling.fire("metadataRefresh");
                                        }
                                    }
                                });
                                this.destroy();
                            }
                        },
                        {
                            text: this.msg("actions.button.cancel"),
                            handler: function dlA_onActionCancel() {
                                this.destroy();
                            },
                            isDefault: true
                        }
                    ]
                }
            );
        }
    });

    YAHOO.Bubbling.fire("registerAction", {
        actionName: "onEditNodeMetadata",
        fn: function(asset, element) {

            var nodeRef = asset.node.nodeRef;
            var oldFormUrl = "/share/page/node-edit?nodeRef=" + nodeRef;

            try {
                Citeck.forms.editRecord({
                    recordRef: nodeRef,
                    fallback: function() {
                        window.location = oldFormUrl;
                    },
                    onSubmit: function() {
                        YAHOO.Bubbling.fire("metadataRefresh");
                    }
                });
            } catch (e) {
                console.error(e);
                window.location = oldFormUrl;
            }
        }
    });

    /**
     * for Node Actions Service
     * */
    YAHOO.Bubbling.fire("registerAction", {
        actionName: "onServerAction",
        fn: function(asset, element) {
            var actionId = element.className;
            var props = asset.actionParams[actionId].actionProperties;
            var actionType = props.actionType;

            if (props.title == "Register" || props.title == "Зарегистрировать") {
                require([
                    'components/form/form',
                    'components/form/date-picker',
                    'citeck/components/form/controls/auto-number'
                ], function () {
                    Citeck.forms.dialog(asset.node.nodeRef, "register", {
                            scope: this,
                            fn: function() {
                                Alfresco.util.Ajax.jsonPost({
                                    url: Alfresco.constants.PROXY_URI + "api/lifecycle/do-transition?nodeRef=" + asset.nodeRef,
                                    successCallback: {
                                        scope: this,
                                        fn: function() {
                                            Alfresco.util.PopupManager.displayMessage({
                                                text: Alfresco.util.message("message.transitionSuccess")
                                            });
                                            _.delay(function() {
                                                window.location.reload();
                                            }, 3000);
                                        }
                                    },
                                    failureCallback: {
                                        scope: this,
                                        fn: function() {
                                            Alfresco.util.PopupManager.displayMessage({
                                                text: Alfresco.util.message("message.transitionError")
                                            });
                                        }
                                    }
                                });
                            }
                        },
                        { title: props.title, forceOldDialog: true });
                })
            } else if (actionType === "REQUEST") {
                var makeRequest = function() {
                    Alfresco.util.Ajax.request({
                        url: Alfresco.constants[props.context] + props.url,
                        method: props.requestMethod,
                        successCallback: {
                            scope: this,
                            fn: function() {
                                if (!!props.successMessage) {
                                    Alfresco.util.PopupManager.displayPrompt({
                                        text: Alfresco.util.message(props.successMessage),
                                        noEscape: true,
                                        buttons: [
                                            {
                                                text: Alfresco.util.message("button.yes"),
                                                handler: function() {
                                                    this.destroy();
                                                    window.location.reload();
                                                }
                                            }
                                        ]
                                    });
                                } else {
                                    Alfresco.util.PopupManager.displayMessage({
                                        text: Alfresco.util.message("message.transitionSuccess"),
                                        spanClass: props.successMessageSpanClass || ""
                                    });
                                    _.delay(function() {
                                        window.location.reload();
                                    }, 3000);
                                }
                            }
                        },
                        failureCallback: {
                            scope: this,
                            fn: function(response) {
                                var json = Alfresco.util.parseJSON(response.serverResponse.responseText);
                                Alfresco.util.PopupManager.displayMessage({
                                    text: json.message
                                });
                            }
                        }
                    });
                };

                if (props.confirmationMessage) {
                    Alfresco.util.PopupManager.displayPrompt({
                        title: props.title,
                        text: props.confirmationMessage,
                        noEscape: true,
                        buttons: [
                            {
                                text: this.msg("button.yes"),
                                handler: function dlA_onActionDelete_ok() {
                                    this.destroy();
                                    makeRequest();
                                }
                            },
                            {
                                text: this.msg("button.cancel"),
                                handler: function dlA_onActionDelete_cancel() {
                                    this.destroy();
                                },
                                isDefault: true
                            }]
                    });
                } else {
                    makeRequest();
                }
            } else if (actionType === "REDIRECT") {
                window.open(Alfresco.constants[props.context] + props.url, "_self");
            } else if (actionType === "CREATE_NODE") {

                var self = this;
                var getMsg = function (key) {
                    return self.msg(key) || key
                };

                require(['ecosui!record-actions'], function(actions) {

                    actions.createUserActionNode(props, function () {

                        require(['components/form/form'], function () {

                            Citeck.forms.dialog(props.nodeType, props.formId, function() {
                                YAHOO.Bubbling.fire("metadataRefresh");
                            }, {
                                "destination": props.destination,
                                "destinationAssoc": props.destinationAssoc,
                                "title": getMsg(props.title)
                            });
                        })
                    }).then(function () {
                        YAHOO.Bubbling.fire("metadataRefresh");
                    });
                });
            }
        }
    });

    YAHOO.Bubbling.fire("registerAction", {
        actionName: "onUserActionEvent",
        fn: function(record) {
            Alfresco.util.Ajax.jsonPost({
                url: Alfresco.constants.PROXY_URI + "/citeck/event/fire-events?nodeRef=" + record.nodeRef + "&eventType=user-action",
                successCallback: {
                    scope: this,
                    fn: function() {
                        Alfresco.util.PopupManager.displayMessage({
                            text: this.msg("message.transitionSuccess")
                        });
                        _.delay(function() {
                            window.location.reload();
                        }, 3000);
                    }
                },
                failureCallback: {
                    scope: this,
                    fn: function(response) {
                        var json = Alfresco.util.parseJSON(response.serverResponse.responseText);
                        Alfresco.util.PopupManager.displayMessage({
                            text: json.message
                        });
                    }
                }
            });
        }
    });

    YAHOO.Bubbling.fire("registerAction", {

        actionName: "onUserActionEventReset",

        fn: function(record) {
            Alfresco.util.Ajax.jsonPost({
                url: Alfresco.constants.PROXY_URI + "/citeck/case/activity/reset?nodeRef=" + record.nodeRef,
                successCallback: {
                    scope: this,
                    fn: function() {
                        Alfresco.util.Ajax.jsonPost({
                            url: Alfresco.constants.PROXY_URI + "/citeck/event/fire-events?nodeRef=" + record.nodeRef + "&eventType=user-action",
                            successCallback: {
                                scope: this,
                                fn: function() {
                                    Alfresco.util.PopupManager.displayMessage({
                                        text: this.msg("message.transitionSuccess")
                                    });
                                    _.delay(function() {
                                        window.location.reload();
                                    }, 3000);
                                }
                            },
                            failureCallback: {
                                scope: this,
                                fn: function(response) {
                                    var json = Alfresco.util.parseJSON(response.serverResponse.responseText);
                                    Alfresco.util.PopupManager.displayMessage({
                                        text: json.message
                                    });
                                }
                            }
                        })
                    }
                },
                failureCallback: {
                    scope: this,
                    fn: function(response) {
                        var json = Alfresco.util.parseJSON(response.serverResponse.responseText);
                        Alfresco.util.PopupManager.displayMessage({
                            text: json.message
                        });
                    }
                }
            });
        }
    });

    YAHOO.Bubbling.fire("registerAction", {

        actionName: "importCaseTemplate",

        fn: function(record) {

            var fileUpload = Alfresco.getFileUploadInstance();
            var uploadUrl = "/case/import";
            var uploadConfig = {
                destination: record.nodeRef,
                htmlUploadURL: uploadUrl,
                flashUploadURL: uploadUrl,
                mode: fileUpload.MODE_SINGLE_UPLOAD,
                username: Alfresco.constants.USERNAME,
                uploadDirectory: '/',
                filter: [{description: "Case template", extensions: "*.xml"}],
                onFileUploadComplete: {
                    scope: this,
                    fn: function(response) {
                        var files = response.successful;
                    }
                }
            };
            fileUpload.show(uploadConfig);
        }
    });

});
