/*
 * Copyright (C) 2008-2016 Citeck LLC.
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
define([
    'jquery',
    'citeck/utils/knockout.utils',
    'citeck/components/journals2/journals',
    'lib/knockout'
], function (jq, koutils, Journals, ko) {

        var PopupManager = Alfresco.util.PopupManager,
            koclass = koutils.koclass,
            JournalsWidget = koclass('JournalsWidget'),
            JournalsPage = koclass('JournalsPage', JournalsWidget),
            Record = koclass('Record'),
            msg = Alfresco.util.message,
            Dom = YAHOO.util.Dom;

        JournalsPage
        // load filter method
            .property('loadFilterMethod', String)
            .load('loadFilterMethod', function () {
                this.loadFilterMethod("onclick")
            })
            .computed('filterVisibility', function () {
                switch (this.loadFilterMethod()) {
                    case "onstart":
                    case "loaded":
                        return true;

                    case "onclick":
                    default:
                        return false;
                }
                ;
            })

            // menu
            .property('currentMenu', String)
            .method('toggleToolbarMenu', function (menu) {
                if (menu == "filter") this.loadFilterMethod("loaded");

                if (this.currentMenu() == menu) {
                    this.currentMenu('');
                } else {
                    this.currentMenu(menu);
                }
            })

            // actions support
            .method('executeAction', function (action) {
                var vms = this.selectedRecords(),
                    records = [];
                if (action.isDoclib()) {
                    for (var i in vms) {
                        var vm = vms[i],
                            loaded = vm.doclib.loaded(),
                            record = vm.doclib();
                        if (record) {
                            records.push(record);
                        } else if (!loaded) {
                            koutils.subscribeOnce(vm.doclib, _.partial(this.executeAction, action), this);
                            return;
                        } else {
                            throw new Error("doclib actions can be executed only on doclib nodes");
                        }
                    }
                } else {
                    records = vms;
                }
                this.actionsRuntime[action.func()](records, action);
            })

            // add user interaction for save and remove methods:
            .method('saveFilter', function () {
                this.userInteraction.simulateChange();
                if (!this._filter().valid()) return;
                this.userInteraction.askTitle({
                    callback: {
                        scope: this,
                        fn: function (title) {
                            this._filter().title(title);
                            this.$super.saveFilter();
                        }
                    },
                    title: this.msg("title.save-filter"),
                    text: this.msg("label.save-filter"),
                });
            })
            .method('removeFilter', function (filter) {
                this.userInteraction.askConfirm({
                    callback: {
                        scope: this,
                        fn: function () {
                            this.$super.removeFilter(filter);
                        },
                    },
                    text: this.msg("message.confirm.delete", filter.title()),
                    title: this.msg("message.confirm.delete.1.title"),
                });
            })
            .method('saveSettings', function () {
                if (!this._settings().valid()) return;
                this.userInteraction.askTitle({
                    callback: {
                        scope: this,
                        fn: function (title) {
                            this._settings().title(title);
                            this.$super.saveSettings();
                        }
                    },
                    title: this.msg("title.save-settings"),
                    text: this.msg("label.save-settings"),
                });
            })
            .method('removeSettings', function (settings) {
                this.userInteraction.askConfirm({
                    callback: {
                        scope: this,
                        fn: function () {
                            this.$super.removeSettings(settings);
                        },
                    },
                    text: this.msg("message.confirm.delete", settings.title()),
                    title: this.msg("message.confirm.delete.1.title"),
                });
            })
            .method('removeRecord', function (record) {
                this.userInteraction.askConfirm({
                    callback: {
                        scope: this,
                        fn: function () {
                            this.$super.removeRecord(record);
                        },
                    },
                    text: this.msg("message.confirm.delete", record.attributes()['cm:name']),
                    title: this.msg("message.confirm.delete.1.title"),
                });
            })
            .method('removeRecords', function (records) {
                this.userInteraction.askConfirm({
                    callback: {
                        scope: this,
                        fn: function () {
                            this.$super.removeRecords(records);
                        },
                    },
                    text: this.msg("message.confirm.delete.description", records.length),
                    title: this.msg("message.confirm.delete.title"),
                });
            })
            .method('performSearch', function () {
                this.userInteraction.simulateChange();
                this.$super.performSearch();
            })
            .method('applyCriteria', function () {
                this.userInteraction.simulateChange();
                this.$super.applyCriteria();
            })

            .method('toggleSidebar', function (data, event) {
                $("#alf-filters").toggle();
                $("#alfresco-journals #alf-content .toolbar .sidebar-toggle").toggleClass("yui-button-selected");
            })
        ;

        var JournalsPageWidget = function (htmlid) {
            JournalsPageWidget.superclass.constructor.call(this,
                "Citeck.widgets.JournalsPage",
                htmlid,
                ["button", "menu", "history", "paginator", "dragdrop"],
                JournalsPage);

            // doclib actions parameters:
            this.currentPath = "/";
            this.options.containerId = "documentLibrary",
                this.options.rootNode = "alfresco://company/home";

            this.viewModel.actionsRuntime = this;
            this.viewModel.userInteraction = this;
        };

        YAHOO.extend(JournalsPageWidget, Journals, {

            onReady: function () {
                JournalsPageWidget.superclass.onReady.apply(this, arguments);

                _.reduce(['metadataRefresh', 'fileDeleted', 'folderDeleted', 'filesDeleted'], function (memo, eventName) {
                    YAHOO.Bubbling.on(eventName, function (layer, args) {
                        this.viewModel.performSearch()
                    }, this);
                }, null, this);

                YAHOO.Bubbling.on('removeJournalRecord', function (layer, args) {
                    this.viewModel.removeRecord(new Record(args[1]));
                }, this);
            },

            simulateChange: function () {
                // simulate change on hidden fields,
                // to force view models update

                $('#' + this.id + '-filter-criteria input[type="hidden"]').trigger('change');
            },

            askTitle: function (config) {
                return PopupManager.getUserInput(_.defaults(config, {
                    input: "text",
                    okButtonText: this.msg("button.save")
                }));
            },

            askConfirm: function (config) {
                var callback = config.callback;
                if (!callback) throw new Error("Callback should be specified");
                return PopupManager.displayPrompt(_.defaults(config, {
                    buttons: [
                        {
                            text: this.msg("button.yes"),
                            handler: function () {
                                callback.fn.call(callback.scope);
                                this.destroy();
                            }
                        },
                        {
                            text: this.msg("button.no"),
                            handler: function () {
                                this.destroy();
                            },
                            isDefault: true
                        }
                    ]
                }));
            },

        });

        /*********************************************************/
        /*           DOCUMENT LIBRARY ACTIONS SUPPORT            */
        /*********************************************************/

        _.extend(JournalsPageWidget.prototype, Alfresco.doclib.Actions.prototype, {

            // override for deleting multiple records
            onActionDelete: function (record) {
                if (_.isArray(record)) {
                    this.viewModel.removeRecords(record);
                } else {
                    this.viewModel.removeRecord(record);
                }
            },

            onGroupAction: function (records, action) {
                var self = this;

                if (!self.widgets.waitingDialog) {
                    self.widgets.waitingDialog = new YAHOO.widget.SimpleDialog("group-action-waiting-dialog", {
                        width: "600px",
                        effect:{
                            effect: YAHOO.widget.ContainerEffect.FADE,
                            duration: 0.25
                        },
                        fixedcenter: "contained",
                        modal: true,
                        visible: true,
                        draggable: false,
                        close: false
                    });

                    self.widgets.waitingDialog.setBody('<div class="loading" style="height: 200px"></div>');
                    self.widgets.waitingDialog.setHeader(msg("message.please-wait"));
                    self.widgets.waitingDialog.render(document.body);
                }

                self.widgets.waitingDialog.show();

                var dataObj = {
                    nodes: _.map(records, function(record) { return record.nodeRef(); }),
                    actionId: action.settings().actionId,
                    params: action.settings(),
                    groupType: action.groupType(),
                    journalId: this.viewModel.journal().type().id(),
                    query: this.viewModel.recordsQuery()
                };

                var showError = function (msg) {
                    Alfresco.util.PopupManager.displayMessage({
                        text: msg,
                        displayTime: 5
                    });
                };

                var groupActionPost = function () {
                    Alfresco.util.Ajax.jsonPost({
                        url: Alfresco.constants.PROXY_URI + "api/journals/group-action",
                        dataObj: dataObj,
                        successCallback: {
                            scope: this,
                            fn: function(response) {
                                YAHOO.Bubbling.fire("metadataRefresh");
                                var results = response.json.results;
                                var downloadReportUrl = results && results[0] && results[0].url;

                                if (!self.widgets.gard) {
                                    self.widgets.gard = new YAHOO.widget.SimpleDialog("group-action-result-dialog", {
                                        width: "600px",
                                        effect:{
                                            effect: YAHOO.widget.ContainerEffect.FADE,
                                            duration: 0.25
                                        },
                                        fixedcenter: "contained",
                                        modal: false,
                                        visible: true,
                                        draggable: false,
                                        close: false,
                                        buttons: [ { text: "OK", handler: function() { this.hide()} } ]
                                    });

                                    self.widgets.gard.setHeader(msg("group-action.label.header"));

                                    if (!downloadReportUrl) {
                                        self.widgets.gard.setBody(
                                            $("<table>").append(
                                                $("<thead>").append(
                                                    $("<tr>")
                                                        .append($("<th>", { text: msg("group-action.label.record") }))
                                                        .append($("<th>", { text: msg("group-action.label.status") }))
                                                        .append($("<th>", { text: msg("group-action.label.message") }))
                                                )
                                            ).get(0)
                                        );
                                    }

                                    self.widgets.gard.render(document.body);
                                }
                                if (downloadReportUrl) {
                                    var body = (
                                        '<table  style="width: 100%; height: 60px">' +
                                        '<tr style="text-align: center">' +
                                        '<td>' + msg("group-action.label.report") + '</td>' +
                                        '<td><a class="document-link" onclick="event.stopPropagation()" '
                                        + 'href="' + Alfresco.constants.PROXY_URI + downloadReportUrl + '">' + msg("actions.document.download") + '</a></td>' +
                                        '</tr>' +
                                        '</table>'
                                    );

                                    self.widgets.gard.setBody(body);

                                } else {
                                    $("table tbody", self.widgets.gard.body).remove();

                                    var rtbody = $("<tbody>");
                                    _.each(results, function(result) {
                                        var record = _.find(records, function(rec) {
                                            return rec.nodeRef() == result.nodeRef;
                                        });

                                        var recordName;

                                        if (record) {
                                            var nameAttr   = record.attributes()["cm:name"],
                                                name       = nameAttr && nameAttr[0] && nameAttr[0].hasOwnProperty('str') ? nameAttr[0].str : nameAttr,
                                                titleAttr  = record.attributes()["cm:title"],
                                                title      = titleAttr && titleAttr[0] && titleAttr[0].hasOwnProperty('str') ? titleAttr[0].str : titleAttr;

                                            recordName = title || name;
                                        } else {
                                            recordName = result.nodeRef;
                                        }

                                        rtbody.append(
                                            $("<tr>")
                                                .append($("<td>", { text: recordName }))
                                                .append($("<td>", {
                                                    text: msg("batch-edit.message." + result.status)
                                                }))
                                                .append($("<td>", { text: result.message }))
                                        );
                                    });

                                    $("table", self.widgets.gard.body).append(rtbody);
                                }
                                self.widgets.waitingDialog.hide();
                                self.widgets.gard.show();

                                var error = response.json.error;
                                if (error) {
                                    showError("???????????? [" + error.type + "] message: " + error.message);
                                }
                            }
                        },
                        failureCallback: {
                            scope: this,
                            fn: function(response) {

                                self.widgets.waitingDialog.hide();

                                var msg = ((response || {}).json || {}).message;
                                if (msg) {
                                    msg = msg.replace(/.+\.json\.js': /, "");
                                } else {
                                    msg = Alfresco.util.message('batch-edit.message.ERROR');
                                }

                                showError(msg);
                            }
                        }
                    });
                };

                /** Javascript action */
                if (action.settings().js_action) {
                    var actionFunction = new Function('records', 'parameters', action.settings().js_action);
                    actionFunction(dataObj.nodes, action.settings());
                    return;
                }

                if (action.settings().view) {
                    var title     = action.settings().title || "group-action.label.title";
                    var journalId = action.settings().journalId;
                    var actionId  = action.settings().actionId;
                    var formId    = action.settings().formId;

                    var onCancel = function () {
                        YAHOO.Bubbling.unsubscribe("node-view-cancel", onCancel);
                        self.widgets.waitingDialog.hide();
                    };

                    YAHOO.Bubbling.on("node-view-cancel", onCancel);

                    Citeck.forms.dialog(journalId + "_" + actionId, formId, {
                        scope: this,
                        fn: function (rsp) {
                            if (rsp != null && rsp.result != null && rsp.result.formAttributes) {
                                $.extend(dataObj.params, rsp.result.formAttributes);
                            }

                            groupActionPost();
                        }
                    }, {
                        title: Alfresco.util.message(title)
                    });
                } else {
                    groupActionPost();
                }
            },


            onBatchEdit: function (records, action) {

                var editStatus = {}, ref;

                var defaultValueMapping = {
                    "boolean": false,
                    "text": "",
                    "int": 0,
                    "double": 0,
                    "long": 0,
                    "float": 0,
                    "association": null,
                    "mltext": ""
                };

                var id = Alfresco.util.generateDomId();
                var attribute = action.attribute();
                var datatype = attribute.datatype().name();
                var editValue = ko.observable(defaultValueMapping[datatype]);
                var journalsPage = this;

                var confirmPopup = function (text, onYes, onNo) {
                    Alfresco.util.PopupManager.displayPrompt({
                        title: Alfresco.util.message("message.confirm.title"),
                        text: text,
                        noEscape: true,
                        buttons: [
                            {
                                text: msg("actions.button.ok"),
                                handler: onYes
                            },
                            {
                                text: msg("actions.button.cancel"),
                                handler: onNo,
                                isDefault: true
                            }]
                    });
                };

                var setStatus = function (record, status) {
                    var recordAttributes = record.attributes();
                    editStatus[record.nodeRef()] = {
                        status: status,
                        title: recordAttributes["cm:title"] || recordAttributes["cm:name"]
                    };
                };

                var showResults = function (response) {
                    var panel = new YAHOO.widget.Panel(id + "-results-panel", {
                        width: "40em",
                        fixedcenter: YAHOO.env.ua.mobile === null ? "contained" : false,
                        constraintoviewport: true,
                        underlay: "shadow",
                        close: true,
                        modal: true,
                        visible: true,
                        draggable: true,
                        postmethod: "none", // Will make Dialogs not auto submit <form>s it finds in the dialog
                        hideaftersubmit: false, // Will stop Dialogs from hiding themselves on submits
                        fireHideShowEvents: true
                    });

                    panel.setHeader(msg("batch-edit.header.results") + " " +  action.attribute().displayName());

                    for (ref in editStatus) {
                        if (editStatus[ref].status == "PENDING") {
                            editStatus[ref].status = ((response || {})[ref] || {})[attribute.name()] || "RESPONSE_ERR";
                        }
                    }

                    var body = '<table class="batch-edit-results">';
                    for (ref in editStatus) {
                        var title = editStatus[ref].title;
                        var titleColumn = '';
                        if (title) {
                            if (title.length) {
                                title = title[0];
                            }
                            if (title.hasOwnProperty("str")) {
                                title = title.str;
                            }
                            titleColumn = '<td>' + title + '</td>';
                        }
                        body += '<tr>' + titleColumn + '<td>' + Alfresco.util.message("batch-edit.message."+editStatus[ref].status) + '</td></tr>';
                    }
                    body += '</table>';
                    body += '<div class="form-buttons batch-edit-results-form-buttons"><input id="' + id + '-close-results-btn" type="button" class="batch-edit-results-button" value="' + msg("button.ok") + '" /></div>';

                    panel.setBody(body);
                    panel.render(document.body);

                    Dom.get(id + '-close-results-btn').onclick = function () {
                        panel.destroy();
                    }
                };

                var processRecords = function (records, options) {

                    var nodes = _.map(records, function (r) {
                        return r.nodeRef();
                    });
                    var attributes = {};
                    attributes[attribute.name()] = editValue();

                    Alfresco.util.Ajax.jsonPost({
                        url: Alfresco.constants.PROXY_URI + "api/journals/batch-edit",
                        dataObj: {
                            "nodes": nodes,
                            "attributes": attributes,
                            "skipInStatuses": options.skipInStatuses,
                            "childMode" : options.childMode
                        },
                        successCallback: {
                            scope: this,
                            fn: function (response) {
                                journalsPage.viewModel.performSearch();
                                panel.destroy();
                                showResults(response.json);
                            }
                        },
                        failureCallback: {
                            scope: this,
                            fn: function (response) {
                                Alfresco.util.PopupManager.displayMessage({
                                    text: msg("message.failure") + ':' + response.json.message,
                                    displayTime: 4
                                });
                            }
                        }
                    });
                };

                var onSubmit = function () {
                    var filterByOptions = function (records, idx, result, callback, options) {
                        if (idx >= records.length) {
                            var repoOptions = {};
                            if (options.skipInStatuses) {
                                repoOptions.skipInStatuses = options.skipInStatuses;
                            }

                            repoOptions.childMode = options.childMode || false;

                            callback(result, repoOptions);
                            return;
                        }
                        var recordAttributes = records[idx].attributes();
                        var value = recordAttributes[attribute.name()];

                        var isEmptyValue = true;
                        if (value && value instanceof Array && value.length > 0) {
                            isEmptyValue = false;
                        } else if (value && !(value instanceof Array)) {
                            isEmptyValue = false;
                        }

                        if (!isEmptyValue) {
                            if (!options.changeExistsValue) {
                                setStatus(records[idx], "SKIPPED");
                                filterByOptions(records, idx + 1, result, callback, options);
                            } else {
                                if (options.confirmChange) {
                                    var fieldTitle = attribute.displayName();
                                    var documentTitle = recordAttributes["cm:title"] || recordAttributes["cm:name"];
                                    confirmPopup("?? ?????????????????? '" + documentTitle + "' ???????????????? ???????? '"
                                        + fieldTitle + "' ?????????? '" + value
                                        + "'. ?????????????? ?????? ?????????????????", function () {
                                        this.destroy();
                                        result.push(records[idx]);
                                        filterByOptions(records, idx + 1, result, callback, options);
                                    }, function () {
                                        this.destroy();
                                        setStatus(records[idx], "CANCELLED");
                                        filterByOptions(records, idx + 1, result, callback, options);
                                    })
                                } else {
                                    result.push(records[idx]);
                                    filterByOptions(records, idx + 1, result, callback, options);
                                }
                            }
                        } else {
                            if (options.skipEmptyValues) {
                                setStatus(records[idx], "SKIPPED");
                            } else {
                                result.push(records[idx]);
                            }
                            filterByOptions(records, idx + 1, result, callback, options);
                        }
                        return result;
                    };

                    if (datatype != "association" || editValue() != null) {
                        // Set default values when options are not specified in the configuration
                        var options = {
                            confirmChange: false,
                            skipEmptyValues: false,
                            changeExistsValue: true,
                            skipInStatuses: [],
                            childMode: false
                        };
                        var useFilter = false;
                        var confirmChange = action.settings().confirmChange;
                        if (confirmChange) {
                            if (confirmChange === 'true') {
                                options.confirmChange = true;
                            } else {
                                options.confirmChange = false;
                            }
                            useFilter = true;
                        }
                        var skipEmptyValues = action.settings().skipEmptyValues;
                        if (skipEmptyValues) {
                            if (skipEmptyValues === 'true') {
                                options.skipEmptyValues = true;
                            } else {
                                options.skipEmptyValues = false;
                            }
                            useFilter = true;
                        }
                        var changeExistsValue = action.settings().changeExistsValue;
                        if (changeExistsValue) {
                            if (changeExistsValue === 'true') {
                                options.changeExistsValue = true;
                            } else {
                                options.changeExistsValue = false;
                            }
                            useFilter = true;
                        }
                        var skipInStatusesValue = action.settings().skipInStatuses;
                        if (skipInStatusesValue) {
                            if (skipInStatusesValue.length > 0) {
                                var statusesArray = skipInStatusesValue.split(",");
                                options.skipInStatuses = statusesArray;
                            }
                        }
                        var childModeValue = action.settings().childMode;
                        if (childModeValue) {
                            options.childMode = childModeValue === 'true';
                        }

                        if (useFilter) {
                            filterByOptions(records, 0, [], processRecords, options);
                        } else {
                            processRecords(records, options);
                        }
                    }
                };

                var panel = new YAHOO.widget.Panel(id + "-batch-edit-container", {
                    width: "40em",
                    fixedcenter: YAHOO.env.ua.mobile === null ? "contained" : false,
                    constraintoviewport: true,
                    underlay: "shadow",
                    close: true,
                    modal: true,
                    visible: true,
                    draggable: true,
                    postmethod: "none", // Will make Dialogs not auto submit <form>s it finds in the dialog
                    hideaftersubmit: false, // Will stop Dialogs from hiding themselves on submits
                    fireHideShowEvents: true
                });

                panel.setHeader(msg("batch-edit.header.edit-attribute") + " " +  action.attribute().displayName());

                var body =
                    '<div id="batch-edit-div">' +
                    '<div class="batch-edit-criterion">' +
                    '<div class="criterion-label">' +
                    '<span>' + action.attribute().displayName() + '</span>' +
                    '</div>' +
                    '<!-- ko component: { name: "filter-criterion-value", params: {\
                        fieldId: fieldId,\
                        datatype: action.attribute().datatype().name(),\
                        value: value,\
                        attribute: action.attribute\
                    }} --><!-- /ko -->' +
                    '</div>' +
                    '<div class="form-buttons">' +
                    '<input id="' + id + '-form-submit" type="button" value="' + msg("button.send") + '" />' +
                    '<input id="' + id + '-form-cancel" type="button" value="' + msg("button.cancel") + '" />' +
                    '</div>' +
                    '</div>';

                panel.setBody(body);
                panel.render(document.body);
                ko.applyBindings({
                    "action": action,
                    "fieldId": id + "-field",
                    "value": editValue
                }, Dom.get("batch-edit-div"));

                var submitBtn = document.getElementById(id + "-form-submit");
                submitBtn.onclick = onSubmit;

                var cancelBtn = document.getElementById(id + "-form-cancel");
                cancelBtn.onclick = function () {
                    panel.destroy();
                };

                for (var i in records) {
                    setStatus(records[i], "PENDING");
                }
            }

        });

        return JournalsPageWidget;

    });
