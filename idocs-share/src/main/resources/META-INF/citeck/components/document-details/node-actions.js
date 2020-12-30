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

/**
 * Node actions component.
 *
 * @namespace Alfresco
 * @class Citeck.NodeActions
 */
define([
    'js/citeck/modules/utils/citeck',
    'components/documentlibrary/actions',
    'modules/documentlibrary/doclib-actions'
], function() {

    Citeck = typeof Citeck != "undefined" ? Citeck : {};

    /**
     * YUI Library aliases
     */
    var Dom = YAHOO.util.Dom;

    /**
     * Alfresco Slingshot aliases
     */
    var $html = Alfresco.util.encodeHTML,
        $combine = Alfresco.util.combinePaths,
        $siteURL = Alfresco.util.siteURL,
        $isValueSet = Alfresco.util.isValueSet;

    /**
     * NodeActions constructor.
     *
     * @param {String} htmlId The HTML id of the parent element
     * @return {Citeck.NodeActions} The new NodeActions instance
     * @constructor
     */
    Citeck.NodeActions = function(htmlId)
    {
        Citeck.NodeActions.superclass.constructor.call(this, "Citeck.NodeActions", htmlId, ["button"]);

        // Initialise prototype properties
        this.actionsView = "details";

        // Decoupled event listeners
        YAHOO.Bubbling.on("filesPermissionsUpdated", this.doRefresh, this);
        YAHOO.Bubbling.on("metadataRefresh", this.doRefresh, this);
        YAHOO.Bubbling.on("registerAction", this.onRegisterAction, this);

        return this;
    };

    /**
     * Extend Alfresco.component.Base
     */
    YAHOO.extend(Citeck.NodeActions, Alfresco.component.Base);

    /**
     * Augment prototype with Actions module
     */
    YAHOO.lang.augmentProto(Citeck.NodeActions, Alfresco.doclib.Actions);

    /**
     * Augment prototype with main class implementation, ensuring overwrite is enabled
     */
    YAHOO.lang.augmentObject(Citeck.NodeActions.prototype,
        {
            /**
             * Object container for initialization options
             *
             * @property options
             * @type object
             */
            options:
            {
                /**
                 * Reference to the current document
                 *
                 * @property nodeRef
                 * @type string
                 */
                nodeRef: null,

                /**
                 * Current siteId, if any.
                 *
                 * @property siteId
                 * @type string
                 */
                siteId: null,

                /**
                 * ContainerId representing root container
                 *
                 * @property containerId
                 * @type string
                 * @default "documentLibrary"
                 */
                containerId: "documentLibrary",

                /**
                 * Valid inline edit mimetypes
                 * Currently allowed are plain text, HTML and XML only
                 *
                 * @property inlineEditMimetypes
                 * @type object
                 */
                inlineEditMimetypes:
                {
                    "text/plain": true,
                    "text/html": true,
                    "text/xml": true
                },

                /**
                 * Root node
                 *
                 * @property rootNode
                 * @type string
                 */
                rootNode: "alfresco://company/home",

                /**
                 * Replication URL Mapping details
                 *
                 * @property replicationUrlMapping
                 * @type object
                 */
                replicationUrlMapping: {},

                /**
                 * JSON representation of document details
                 *
                 * @property documentDetails
                 * @type object
                 */
                documentDetails: null,

                /**
                 * Whether the Repo Browser is in use or not
                 *
                 * @property repositoryBrowsing
                 * @type boolean
                 */
                repositoryBrowsing: true,

                /**
                 * Class that is applied to action link
                 *  to separate these links from default action links of document library.
                 *
                 *  @property actionLinkClass
                 *  @default "action-link"
                 */
                actionLinkClass: "action-link",

                dependencies: []
            },

            /**
             * The data for the document
             *
             * @property recordData
             * @type object
             */
            recordData: null,

            /**
             * Metadata returned by doclist data webscript
             *
             * @property doclistMetadata
             * @type object
             * @default null
             */
            doclistMetadata: null,

            /**
             * Path of asset being viewed - used to scope some actions (e.g. copy to, move to)
             *
             * @property currentPath
             * @type string
             */
            currentPath: null,

            serverActions: [],

            actionUrls: {},

            customActionsLoaded: false,

            serverActionsLoaded: false,

            /**
             * Event handler called when "onReady"
             *
             * @method: onReady
             */
            onReady: function NodeActions_onReady() {

                this.updateDocumentDetails(this.options.documentDetails);

                var displayName = this.recordData.displayName,
                    downloadUrl = this.actionUrls.downloadUrl;

                // Hook action events
                var fnActionHandler = function NodeActions_fnActionHandler(layer, args) {
                    var owner = YAHOO.Bubbling.getOwnerByTagName(args[1].anchor, "div");
                    if (owner) {
                        var me = Alfresco.util.ComponentManager.get(self.id);
                        if (typeof me[owner.id] === "function") {
                            args[1].stop = true;

                            try {
                                me[owner.id].call(me, me.recordData, owner);
                            } catch (e) {
                                Alfresco.logger.error("NodeActions_fnActionHandler", owner.id, e);
                            }
                        }
                    }
                    return true;
                };
                YAHOO.Bubbling.addDefaultAction(this.options.actionLinkClass, fnActionHandler);

                // DocLib Actions module
                this.modules.actions = new Alfresco.module.DoclibActions();

                switch (window.location.hash) {
                    case "#editOffline":
                        window.location.hash = "";
                        if (YAHOO.env.ua.ie > 6) {
                            // MSIE7 blocks the download and gets the wrong URL in the "manual download bar"
                            Alfresco.util.PopupManager.displayPrompt({
                                title: this.msg("message.edit-offline.success", displayName),
                                text: this.msg("message.edit-offline.success.ie7"),
                                buttons: [
                                    {
                                        text: this.msg("button.download"),
                                        handler: function NodeActions_oAEO_success_download() {
                                            window.location = downloadUrl;
                                            this.destroy();
                                        },
                                        isDefault: true
                                    },
                                    {
                                        text: this.msg("button.close"),
                                        handler: function NodeActions_oAEO_success_close() {
                                            this.destroy();
                                        }
                                    }
                                ]
                            });
                        } else {
                            Alfresco.util.PopupManager.displayMessage({
                                text: this.msg("message.edit-offline.success", displayName)
                            });

                            // Kick off the download 3 seconds after the confirmation message
                            YAHOO.lang.later(3000, this, function () {
                                window.location = downloadUrl;
                            });
                        }
                        break;

                    case "#editCancelled":
                        window.location.hash = "";
                        Alfresco.util.PopupManager.displayMessage({
                            text: this.msg("message.edit-cancel.success", displayName)
                        });
                        break;

                    case "#checkoutToGoogleDocs":
                        window.location.hash = "";
                        Alfresco.util.PopupManager.displayMessage({
                            text: this.msg("message.checkout-google.success", displayName)
                        });
                        break;

                    case "#checkinFromGoogleDocs":
                        window.location.hash = "";
                        Alfresco.util.PopupManager.displayMessage({
                            text: this.msg("message.checkin-google.success", displayName)
                        });
                        break;
                }

                var self = this;
                this.updateServerActions(function () {
                    self.serverActionsLoaded = true;
                    self.renderActions(self);
                });
                require(this.options.dependencies, function () {
                    self.customActionsLoaded = true;
                    self.renderActions(self);
                });
            },

            updateDocumentDetails: function (details) {

                this.options.documentDetails = details;
                // Asset data
                this.recordData = details.item;
                this.doclistMetadata = details.metadata;
                this.currentPath = this.recordData.location.path;

                // Populate convenience property
                this.recordData.jsNode = new Alfresco.util.Node(this.recordData.node);

                this.actionUrls = this.getActionUrls(this.recordData);
            },

            registerAction: function NodeActions_registerAction(actionName, fn) {
                if ($isValueSet(actionName) && $isValueSet(fn)) {
                    this.constructor.prototype[actionName] = fn;
                    return true;
                }

                return false;
            },

            renderActions: function () {

                if (!this.customActionsLoaded || !this.serverActionsLoaded) {
                    return;
                }

                // Retrieve the actionSet for this record
                var record = this.recordData,
                    actions = record.actions.concat(this.serverActions),
                    actionsEl = Dom.get(this.id + "-actionSet"),
                    actionHTML = "";

                record.actionParams = {};
                for (var i = 0, ii = actions.length; i < ii; i++) {
                    actionHTML += this.renderAction(actions[i], record);
                }

                // Token replacement (actionUrls is re-used further down)
                actionsEl.innerHTML = YAHOO.lang.substitute(actionHTML, this.actionUrls);

                Dom.addClass(actionsEl, "action-set");
                Dom.setStyle(actionsEl, "visibility", "visible");
            },

            renderAction: function dlA_renderAction(p_action, p_record)
            {
                var urlContext = Alfresco.constants.URL_RESCONTEXT + "components/documentlibrary/actions/",
                    iconStyle = 'style="background-image:url(' + urlContext + '{icon}-16.png)" ',
                    actionTypeMarkup =
                    {
                        "link": '<div class="{id}"><a title="{label}" class="simple-link" href="{href}" ' + iconStyle + '{target}><span>{label}</span></a></div>',
                        "pagelink": '<div class="{id}"><a title="{label}" class="simple-link" href="{pageUrl}" ' + iconStyle + '><span>{label}</span></a></div>',
                        "javascript": '<div class="{id}" id="{jsfunction}"><a title="{label}" class=' + this.options.actionLinkClass + ' href="#"' + iconStyle + '><span>{label}</span></a></div>'
                    };

                // Store quick look-up for client-side actions
                p_record.actionParams[p_action.id] = p_action.params;

                var markupParams =
                {
                    "id": p_action.id,
                    "icon": p_action.icon,
                    "label": $html(Alfresco.util.substituteDotNotation(this.msg(p_action.label), p_record))
                };

                // Parameter substitution for each action type
                if (p_action.type === "link")
                {
                    if (p_action.params.href)
                    {
                        markupParams.href = Alfresco.util.substituteDotNotation(p_action.params.href, p_record);
                        markupParams.target = p_action.params.target ? "target=\"" + p_action.params.target + "\"" : "";
                    }
                    else
                    {
                        Alfresco.logger.warn("Action configuration error: Missing 'href' parameter for actionId: ", p_action.id);
                    }
                }
                else if (p_action.type === "pagelink")
                {
                    if (p_action.params.page)
                    {
                        markupParams.pageUrl = Alfresco.util.substituteDotNotation(p_action.params.page, p_record);

                        /**
                         * If the page starts with a "{" character we're going to assume it's a placeholder variable
                * that will be resolved by the getActionsUrls() function. In which case, we do not want to
                * use the $siteURL() function here as that will result in a double-prefix.
                */
                        if (p_action.params.page.charAt(0) !== "{")
                        {
                            var recordSiteName = $isValueSet(p_record.location.site) ? p_record.location.site.name : null;
                            markupParams.pageUrl = $siteURL(markupParams.pageUrl,
                                {
                                    site: recordSiteName
                                });
                        }
                    }
                    else
                    {
                        Alfresco.logger.warn("Action configuration error: Missing 'page' parameter for actionId: ", p_action.id);
                    }
                }
                else if (p_action.type === "javascript")
                {
                    if (p_action.params["function"])
                    {
                        markupParams.jsfunction = p_action.params["function"];
                    }
                    else
                    {
                        Alfresco.logger.warn("Action configuration error: Missing 'function' parameter for actionId: ", p_action.id);
                    }
                }

                return YAHOO.lang.substitute(actionTypeMarkup[p_action.type], markupParams);
            },

            /**
             * Edit Offline.
             *
             * @override
             * @method onActionEditOffline
             * @param asset {object} Object literal representing file or folder to be actioned
             */
            onActionEditOffline: function NodeActions_onActionEditOffline(asset)
            {
                var displayName = asset.displayName,
                    nodeRef = new Alfresco.util.NodeRef(asset.nodeRef),
                    self = this;

                this.modules.actions.genericAction(
                    {
                        success:
                        {
                            callback:
                            {
                                fn: function NodeActions_oAEO_success(data)
                                {
                                    this.recordData.jsNode.setNodeRef(data.json.results[0].nodeRef);
                                    window.location = self.actionUrls.documentDetailsUrl + "#editOffline";
                                },
                                scope: this
                            }
                        },
                        failure:
                        {
                            message: this.msg("message.edit-offline.failure", displayName)
                        },
                        webscript:
                        {
                            method: Alfresco.util.Ajax.POST,
                            name: "checkout/node/{nodeRef}",
                            params:
                            {
                                nodeRef: nodeRef.uri
                            }
                        }
                    });
            },

            /**
             * Cancel editing.
             *
             * @override
             * @method onActionCancelEditing
             * @param asset {object} Object literal representing file or folder to be actioned
             */
            onActionCancelEditing: function NodeActions_onActionCancelEditing(asset)
            {
                var displayName = asset.displayName,
                    nodeRef = new Alfresco.util.NodeRef(asset.nodeRef),
                    self = this;

                this.modules.actions.genericAction(
                    {
                        success:
                        {
                            callback:
                            {
                                fn: function NodeActions_oACE_success(data)
                                {
                                    var oldNodeRef = this.recordData.jsNode.nodeRef.nodeRef,
                                        newNodeRef = data.json.results[0].nodeRef;
                                    this.recordData.jsNode.setNodeRef(newNodeRef);
                                    window.location = self.actionUrls.documentDetailsUrl + "#editCancelled";
                                    // ALF-16598 fix, page is not refreshed if only hash was changed, force page reload for cancel online editing
                                    if (oldNodeRef == newNodeRef)
                                    {
                                        window.location.reload();
                                    }
                                },
                                scope: this
                            }
                        },
                        failure:
                        {
                            message: this.msg("message.edit-cancel.failure", displayName)
                        },
                        webscript:
                        {
                            method: Alfresco.util.Ajax.POST,
                            name: "cancel-checkout/node/{nodeRef}",
                            params:
                            {
                                nodeRef: nodeRef.uri
                            }
                        }
                    });
            },

            /**
             * Upload new version.
             *
             * @override
             * @method onActionUploadNewVersion
             * @param asset {object} Object literal representing the file to be actioned upon
             */
            onActionUploadNewVersion: function NodeActions_onActionUploadNewVersion(asset)
            {
                var displayName = asset.displayName,
                    nodeRef = new Alfresco.util.NodeRef(asset.nodeRef),
                    version = asset.version;

                if (!this.fileUpload)
                {
                    this.fileUpload = Alfresco.getFileUploadInstance();
                }

                // Show uploader for multiple files
                var description = this.msg("label.filter-description", displayName),
                    extensions = "*";

                if (displayName && new RegExp(/[^\.]+\.[^\.]+/).exec(displayName))
                {
                    // Only add a filtering extension if filename contains a name and a suffix
                    extensions = "*" + displayName.substring(displayName.lastIndexOf("."));
                }

                if (asset.workingCopy && asset.workingCopy.workingCopyVersion)
                {
                    version = asset.workingCopy.workingCopyVersion;
                }

                var singleUpdateConfig =
                {
                    updateNodeRef: nodeRef.toString(),
                    updateFilename: displayName,
                    updateVersion: version,
                    suppressRefreshEvent: true,
                    overwrite: true,
                    uploadURL: '/api/v2/citeck/upload',
                    flashUploadURL: '/api/v2/citeck/upload',
                    htmlUploadURL: '/api/v2/citeck/upload',
                    filter: [
                        {
                            description: description,
                            extensions: extensions
                        }],
                    mode: this.fileUpload.MODE_SINGLE_UPDATE,
                    onFileUploadComplete:
                    {
                        fn: this.onNewVersionUploadCompleteCustom,
                        scope: this
                    }
                };
                if (Alfresco.util.isValueSet(this.options.siteId))
                {
                    singleUpdateConfig.siteId = this.options.siteId;
                    singleUpdateConfig.containerId = this.options.containerId;
                }
                this.fileUpload.show(singleUpdateConfig);
            },

            /**
             * Called from the uploader component after a the new version has been uploaded.
             *
             * @method onNewVersionUploadCompleteCustom
             * @param complete {object} Object literal containing details of successful and failed uploads
             */
            onNewVersionUploadCompleteCustom: function NodeActions_onNewVersionUploadCompleteCustom(complete)
            {
                var self = this;
                // Call the normal callback to post the activity data
                this.onNewVersionUploadComplete.call(this, complete);

                this.recordData.jsNode.setNodeRef(complete.successful[0].nodeRef);
                // Delay page reloading to allow time for async requests to be transmitted
                YAHOO.lang.later(0, this, function()
                {
                    window.location = self.actionUrls.documentDetailsUrl;
//                    window.location.reload(true);
                });
            },
            /**
                    * Checkout to Google Docs.
            *
            * @override
            * @method onActionCheckoutToGoogleDocs
            * @param asset {object} Object literal representing file or folder to be actioned
            */
            onActionCheckoutToGoogleDocs: function NodeActions_onActionCheckoutToGoogleDocs(asset)
            {
                var displayName = asset.displayName,
                    nodeRef = new Alfresco.util.NodeRef(asset.nodeRef),
                    path = asset.location.path,
                    fileName = asset.fileName,
                    self = this;

                var progressPopup = Alfresco.util.PopupManager.displayMessage(
                    {
                        displayTime: 0,
                        effect: null,
                        text: this.msg("message.checkout-google.inprogress", displayName)
                    });

                this.modules.actions.genericAction(
                    {
                        success:
                        {
                            callback:
                            {
                                fn: function NodeActions_oAEO_success(data)
                                {
                                    this.recordData.jsNode.setNodeRef(data.json.results[0].nodeRef);
                                    window.location = self.actionUrls.documentDetailsUrl + "#checkoutToGoogleDocs";
                                },
                                scope: this
                            },
                            activity:
                            {
                                siteId: this.options.siteId,
                                activityType: "google-docs-checkout",
                                page: "card-details",
                                activityData:
                                {
                                    fileName: fileName,
                                    path: path,
                                    nodeRef: nodeRef.toString()
                                }
                            }
                        },
                        failure:
                        {
                            callback:
                            {
                                fn: function NodeActions_oAEO_failure(data)
                                {
                                    progressPopup.destroy();
                                    Alfresco.util.PopupManager.displayMessage(
                                        {
                                            text: this.msg("message.checkout-google.failure", displayName)
                                        });
                                },
                                scope: this
                            }
                        },
                        webscript:
                        {
                            method: Alfresco.util.Ajax.POST,
                            name: "checkout/node/{nodeRef}",
                            params:
                            {
                                nodeRef: nodeRef.uri
                            }
                        }
                    });
            },

            /**
             * Check in a new version from Google Docs.
             *
             * @override
             * @method onActionCheckinFromGoogleDocs
             * @param asset {object} Object literal representing the file to be actioned upon
             */
            onActionCheckinFromGoogleDocs: function NodeActions_onActionCheckinFromGoogleDocs(asset)
            {
                var displayName = asset.displayName,
                    nodeRef = new Alfresco.util.NodeRef(asset.nodeRef),
                    originalNodeRef,
                    originalDocPage = false,
                    path = asset.location.path,
                    fileName = asset.fileName,
                    self = this;

                if (asset.jsNode.hasAspect("cm:checkedOut"))
                {
                    nodeRef = new Alfresco.util.NodeRef(asset.workingCopy.workingCopyNodeRef);
                    originalNodeRef = new Alfresco.util.NodeRef(asset.nodeRef);
                    originalDocPage = true;
                }
                else
                {
                    originalNodeRef = new Alfresco.util.NodeRef(asset.workingCopy.sourceNodeRef);
                }

                var progressPopup = Alfresco.util.PopupManager.displayMessage(
                    {
                        displayTime: 0,
                        effect: null,
                        text: this.msg("message.checkin-google.inprogress", displayName)
                    });

                this.modules.actions.genericAction(
                    {
                        success:
                        {
                            callback:
                            {
                                fn: function NodeActions_oACE_success(data)
                                {
                                    this.recordData.jsNode.setNodeRef(data.json.results[0].nodeRef);
                                    window.location = self.actionUrls.documentDetailsUrl + "#checkinFromGoogleDocs";
                                    if (originalDocPage)
                                    {
                                        window.location.reload();
                                    }
                                },
                                scope: this
                            },
                            activity:
                            {
                                siteId: this.options.siteId,
                                activityType: "google-docs-checkin",
                                page: "document-details",
                                activityData:
                                {
                                    fileName: displayName,
                                    path: path,
                                    nodeRef: originalNodeRef.toString()
                                }
                            }
                        },
                        failure:
                        {
                            fn: function NodeActions_oAEO_failure(data)
                            {
                                progressPopup.destroy();
                                Alfresco.util.PopupManager.displayMessage(
                                    {
                                        text: this.msg("message.checkin-google.failure", displayName)
                                    });
                            },
                            scope: this
                        },
                        webscript:
                        {
                            method: Alfresco.util.Ajax.POST,
                            name: "checkin/node/{nodeRef}",
                            params:
                            {
                                nodeRef: nodeRef.uri
                            }
                        }
                    });
            },

            /**
             * Delete Asset confirmed.
             *
             * @override
             * @method _onActionDeleteConfirm
             * @param asset {object} Object literal representing file or folder to be actioned
             * @private
             */
            _onActionDeleteConfirm: function NodeActions__onActionDeleteConfirm(asset)
            {
                var path = asset.location.path,
                    fileName = asset.fileName,
                    displayName = asset.displayName,
                    nodeRef = new Alfresco.util.NodeRef(asset.nodeRef),
                    callbackUrl = Alfresco.util.isValueSet(this.options.siteId) ? "documentlibrary" : "repository",
                    encodedPath = path.length > 1 ? "?path=" + encodeURIComponent(path) : "";

                this.modules.actions.genericAction(
                    {
                        success:
                        {
                            activity:
                            {
                                siteId: this.options.siteId,
                                activityType: "file-deleted",
                                page: "documentlibrary",
                                activityData:
                                {
                                    fileName: fileName,
                                    path: path,
                                    nodeRef: nodeRef.toString()
                                }
                            },
                            callback:
                            {
                                fn: function NodeActions_oADC_success(data)
                                {
                                    window.location = $siteURL(callbackUrl + encodedPath);
                                }
                            }
                        },
                        failure:
                        {
                            message: this.msg("message.delete.failure", displayName)
                        },
                        webscript:
                        {
                            method: Alfresco.util.Ajax.DELETE,
                            name: "file/node/{nodeRef}",
                            params:
                            {
                                nodeRef: nodeRef.uri
                            }
                        }
                    });
            },

            updateServerActions: function (callback) {
                var self = this;
                var requstArgs = {
                    nodeRef: this.options.nodeRef
                };
                if (this.options.siteId) {
                    requstArgs['site'] = this.options.siteId;
                }
                Alfresco.util.Ajax.jsonGet({
                    url: '/share/service/citeck/components/document-details/node-actions-details?' + $.param(requstArgs),
                    successCallback: {
                        scope: this,
                        fn: function (response) {

                            self.updateDocumentDetails(response.json.documentDetails);
                            var responseServerActions = response.json.serverActions;

                            var actions = [];

                            for (var i = 0; i < responseServerActions.length; i++) {
                                actions.push({
                                    id: "server-node-action-" + i,
                                    label: responseServerActions[i].title,
                                    icon: "task",
                                    type: "javascript",
                                    index: 65535 + i,
                                    params: {"function": "onServerAction", actionProperties: responseServerActions[i]}
                                });
                            }
                            self.serverActions = actions;
                            callback.call(self);
                        }
                    }
                });
            },

            _triggerEditOnlineAos: function NodeActions__triggerEditOnlineAos() {
                var me = this;
                var args = arguments;
                require(['js/officelauncher'], function () {
                    Alfresco.doclib.Actions.prototype._triggerEditOnlineAos.apply(me, args);
                });
            },

            /**
             * Refresh component in response to metadataRefresh event
             *
             * @method doRefresh
             */
            doRefresh: function NodeActions_doRefresh() {
                this.updateServerActions(this.renderActions);
            },

            // this function is called by document-edit-properties action
            _updateDocList: function() {
                YAHOO.Bubbling.fire("metadataRefresh");
            }

        }, true);

    return Citeck.NodeActions;
});
