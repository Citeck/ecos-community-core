/*
 * Copyright (C) 2008-2017 Citeck LLC.
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
 * Default cell formatters for YUI DataTable.
 */
define([
    'js/citeck/modules/utils/citeck',
    'components/form/date',
    'xstyle!./cell-formatters.css',
    'citeck/components/form/constraints'
], function() {

    Citeck = typeof Citeck != "undefined" ? Citeck : {};
    Citeck.format = Citeck.format || {};

    var typeNameCache = {};
    var repoMessageCache = {};
    var loadedFormattersCache = {};

    var workflowDefinitions = null;
    var _workflowDefinitionsListeners = [];
    var _workflowDefinitionsReqSent = false;

    var getWorkflowDefinitions = function (callback) {

        if (!workflowDefinitions) {

            _workflowDefinitionsListeners.push(callback);

            if (!_workflowDefinitionsReqSent) {

                var setWorkflowDefinitions = function(definitions) {
                    workflowDefinitions = definitions;
                    for (var idx in _workflowDefinitionsListeners) {
                        _workflowDefinitionsListeners[idx].call(this, definitions);
                    }
                    _workflowDefinitionsListeners = [];
                };

                Alfresco.util.Ajax.jsonGet({
                    url: Alfresco.constants.PROXY_URI + "api/workflow-definitions",
                    successCallback: {
                        fn: function(response) {
                            setWorkflowDefinitions(response.json.data);
                        }
                    },
                    failureCallback: {
                        fn: function(response) {
                            setWorkflowDefinitions([]);
                        }
                    }
                });
                _workflowDefinitionsReqSent = true;
            }
        } else {
            callback(workflowDefinitions);
        }
    };

    YAHOO.lang.augmentObject(Citeck.format, {

        parseInt: function() {
            return function(elCell, oRecord, oColumn, value) {
                var number;
                if (_.isObject(value)) {
                    number = value.str;
                } else {
                    number = value;
                }
                var result = parseInt(number);
                if (isNaN(result)) {
                    return;
                }
                var textNode = document.createTextNode(result);
                elCell.appendChild(textNode);
            }
        },

        parseFloat: function() {
            return function(elCell, oRecord, oColumn, value) {
                var number;
                if (_.isObject(value)) {
                    number = value.str;
                } else {
                    number = value;
                }
                var result = parseFloat(number);
                if (isNaN(result)) {
                    return;
                }
                var textNode = document.createTextNode(result);
                elCell.appendChild(textNode);
            }
        },

        empty: function() {
            return function(elCell, oRecord, oColumn, sData) {
                elCell.innerHTML = "";
            };
        },

        evalFormatter: function(formatterExpr) {
            try {
                if(!formatterExpr.match(/[)]$/)) {
                    formatterExpr += '()';
                }
                with(Citeck.format) {
                    return eval(formatterExpr);
                }
            } catch(e) {
                return null;
            }
        },

        valueStrFormatter: function(multiple, formatter) {
            var single = function (elCell, oRecord, oColumn, oData) {
                var value = oData;
                if (_.isArray(value)) {
                    value = value.length ? value[0] : null;
                }
                if (value) {
                    value = value.hasOwnProperty('str') ? value.str : value;
                }
                if (formatter) {
                    formatter(elCell, oRecord, oColumn, value);
                } else {
                    elCell.innerHTML = value || "";
                }
            };
            if (multiple) {
                return this.multiple(single);
            } else {
                return single;
            }
        },

        loadedFormatter: function(formatterExpr) {
            if(loadedFormattersCache[formatterExpr]) {
                return loadedFormattersCache[formatterExpr];
            }
            return loadedFormattersCache[formatterExpr] = function(elCell, oRecord, oColumn, oData) {
                var formatter = Citeck.format.evalFormatter(formatterExpr);
                if(formatter != null) {
                    loadedFormattersCache[formatterExpr] = formatter;
                    return formatter.apply(this, arguments);
                } else {
                    elCell.innerHTML = "";
                    _.delay(loadedFormattersCache[formatterExpr], 100, elCell, oRecord, oColumn, oData);
                }
            };
        },

        checkbox: function(callbackName) {
            return function(elCell, oRecord, oColumn, sData) {
                elCell.innerHTML = '<input type="checkbox" data-bind="checked: '+callbackName+'" />';
            };
        },

        encodeHTML: function() {
            return function(elCell, oRecord, oColumn, sData) {
                elCell.innerHTML = Alfresco.util.encodeHTML(sData, true);
            };
        },

        fileSize: function(contentKey) {
            return function(elCell, oRecord, oColumn, oData) {
                var content = oRecord.getData(contentKey);
                if(!content) {
                    elCell.innerHTML = '';
                    return;
                }
                elCell.innerHTML = Alfresco.util.formatFileSize(content.size);
            }
        },

        contentFileSize: function(contentKey) {
            return function(elCell, oRecord, oColumn, oData) {
                var content = oRecord.getData(contentKey);
                if(!content) {
                    elCell.innerHTML = '';
                    return;
                }
                elCell.innerHTML = Alfresco.util.formatFileSize(content);
            }
        },

        fileType : function(contentKey) {
            return function(elCell, oRecord, oColumn, oData) {
                var content = oRecord.getData(contentKey);
                if (!content) {
                    elCell.innerHTML = '';
                    return;
                }
                elCell.innerHTML = content.mimetype;
            }
        },

        bool: function(trueLabel, falseLabel) {
            return function(elCell, oRecord, oColumn, sData) {
                if(!sData) {
                    elCell.innerHTML = falseLabel;
                    return;
                }
                var bool = sData.hasOwnProperty('str') ? sData.str : sData;
                elCell.innerHTML = bool === "true" || bool === true ? trueLabel : falseLabel;
            };
        },

        date: function Citeck_format_date(pattern) {
            if (!pattern) {
                pattern = 'dd.MM.yyyy';
            }

            return function(elCell, oRecord, oColumn, sData) {
                var text = sData && sData.hasOwnProperty('str') ? sData.str : sData;
                if (!text) {
                    elCell.innerHTML = '';
                    return;
                }
                var date = Alfresco.util.fromISO8601(text);
                elCell.innerHTML = date.toString(pattern);
            };
        },

        datetime: function(pattern) {
            return Citeck.format.date(pattern || 'dd.MM.yyyy HH:mm:ss');
        },

        date_iso: function(pattern) {
            if (!pattern) {
                pattern = 'dd.MM.yyyy';
            }
            return function(elCell, oRecord, oColumn, sData) {
                if (!sData) {
                    elCell.innerHTML = '';
                    return;
                }
                var date = Alfresco.util.fromISO8601(sData.iso8601);
                elCell.innerHTML = date.toString(pattern);
            };
        },

        dateOrDateTime: function Citeck_format_date_or_datetime(patternDate, patternDateTime) {
            if (!patternDate) {
                patternDate = 'dd.MM.yyyy';
            }
            if (!patternDateTime) {
                patternDateTime = 'dd.MM.yyyy HH:mm';
            }

            return function(elCell, oRecord, oColumn, sData) {
                var text = sData && sData.hasOwnProperty('str') ? sData.str : sData;
                if (!text) {
                    elCell.innerHTML = '';
                    return;
                }
                var date = Alfresco.util.fromISO8601(text),
                    hours = date.getHours(),
                    minutes = date.getMinutes(),
                    seconds = date.getSeconds(),
                    pattern = (hours == 0 && minutes == 0 && seconds == 0) ? patternDate : patternDateTime;
                elCell.innerHTML = date.toString(pattern);
            };
        },

        // alias for backwards compatibility
        dateFormat: function(pattern) {
            return Citeck.format.date(pattern);
        },

        // alias for backwards compatibility
        dateFormatter: function(pattern) {
            return Citeck.format.date(pattern);
        },

        qname: function(full) {
            return function(elCell, oRecord, oColumn, sData) {
                if (!sData) {
                    elCell.innerHTML = '';
                    return;
                }
                var field = full ? 'fullQName' : 'shortQName';
                if (sData.hasOwnProperty(field)) {
                    elCell.innerHTML = sData[field];
                } else {
                    field = full ? 'fullName' : 'shortName';
                    for (var attrKey in sData) {
                        if (!sData.hasOwnProperty(attrKey)) {
                            continue;
                        }
                        var attrObj = sData[attrKey];
                        if (attrObj.name == field) {
                            elCell.innerHTML = (attrObj.val || []).map(function(v) { return v.str; }).join(", ");
                            return;
                        }
                    }
                }
            };
        },

        icon: function(iconSize, namePath) {
            return function(elCell, oRecord, oColumn, sData) {
                var name = sData || oRecord.getData(namePath);
                elCell.innerHTML = '<span class="icon16"><img src="' + Alfresco.constants.URL_RESCONTEXT + 'components/images/filetypes/' + Alfresco.util.getFileIcon(name, null, iconSize) + '"/></span>';
            };
        },

        iconName: function(iconSize, namePath) {
            return function(elCell, oRecord, oColumn, sData) {
                var name = sData || oRecord.getData(namePath) || '';
                elCell.innerHTML = '<span class="icon16"><img src="' + Alfresco.constants.URL_RESCONTEXT + 'components/images/filetypes/' + Alfresco.util.getFileIcon(name, null, iconSize) + '"/>&nbsp;' + name + '</span>';
            };
        },

        iconContentName: function(iconSize, contentPath, namePath) {
            return function(elCell, oRecord, oColumn, sData) {
                var name = sData || oRecord.getData(namePath) || '';
                var content = oRecord.getData(contentPath);
                if (!content || !content.url) {
                    elCell.innerHTML = name;
                } else {
                    elCell.innerHTML = '<span class="icon16"><img src="' + Alfresco.constants.URL_RESCONTEXT + 'components/images/filetypes/' + Alfresco.util.getFileIcon(content.url, null, iconSize) + '"/>&nbsp;' + name + '</span>';
                }
            };
        },

        avatar: function(namePath) {
            return function(elCell, oRecord, oColumn, sData) {
                var name = sData || oRecord.getData(namePath);
                elCell.innerHTML = '<span class="icon"><img src="' + Alfresco.constants.PROXY_URI + 'slingshot/profile/avatar/' + name + '"/></span>';
            };
        },

        userName: function(userNamePath, firstNamePath, lastNamePath, plainText) {
            return function(elCell, oRecord, oColumn, sData) {
                var userName = oRecord.getData(userNamePath),
                    firstName = oRecord.getData(firstNamePath),
                    lastName = oRecord.getData(lastNamePath);
                elCell.innerHTML = Citeck.utils.formatUserName({
                    userName: userName,
                    firstName: firstName,
                    lastName: lastName
                }, plainText);
            };
        },

        node: function(plainText, nameKey) {
            return function(elCell, oRecord, oColumn, sData) {
                if (nameKey) {
                    elCell.innerHTML = oRecord.getData(nameKey) && oRecord.getData(nameKey).displayName ? oRecord.getData(nameKey).displayName : "";
                    return;
                }
                if(!sData) {
                    elCell.innerHTML = "";
                    return;
                }
                if(sData.type == "cm:person") {
                    elCell.innerHTML = Citeck.utils.formatUserName({
                        userName: sData["cm:userName"],
                        firstName: sData["cm:firstName"],
                        lastName: sData["cm:lastName"]
                    }, plainText);
                    return;
                }
                if(sData.type == "cm:authorityContainer") {
                    elCell.innerHTML = sData["cm:authorityDisplayName"] || sData["cm:authorityName"] || sData.displayName || "";
                    return;
                }
                elCell.innerHTML = (sData.displayName ? sData.displayName.value || sData.displayName : "") ||
                                   (sData.hasOwnProperty('str') ? sData.str : sData);
            };
        },

        nodeRef: function(props) {
            return function(elCell, oRecord, oColumn, sData) {
                if(!sData) {
                    elCell.innerHTML = "";
                    return;
                }
                else {
                    Alfresco.util.Ajax.request({
                        url: Alfresco.constants.PROXY_URI + "citeck/node?nodeRef=" + sData[0] + (props ? '&props=' + props + '&replaceColon=_' : ''),
                        successCallback: {
                            scope: this,
                            fn: function(response) {
                                if (response.json && response.json.props) {
                                    props = props ? ('' + props).split(',') : ["cm:name"];
                                    var value = '';
                                    for (var i = 0; i < props.length; i++) {
                                        var v1 = response.json.props[props[i]];
                                        var v2 = response.json.props[props[i].replace(':', '_')];
                                        value += (v1 || v2 || ' ') + ( i < props.length - 1 ? ', ' : '');
                                    }
                                    elCell.innerHTML = value;
                                }
                            }
                        },
                        failureCallback: {
                            scope: this,
                            fn: function(response) {}
                        },
                        execScripts: true
                    });
                }
            };
        },

        userOrGroup: function(showUsersOfGroup) {
            return function(elCell, oRecord, oColumn, sData) {
                if(!sData) {
                    elCell.innerHTML = "";
                    return;
                }
                else {
                    var nodeRefs = [];
                    if (typeof sData === 'object' && sData.length) {
                        nodeRefs = sData;
                    }
                    else {
                        nodeRefs.push(sData);
                    }

                    for (var i = 0; i < nodeRefs.length; i++) {
                        var currentNodeRef = nodeRefs[i];
                        if (currentNodeRef instanceof Object) {
                            currentNodeRef = currentNodeRef.nodeRef;
                        }

                        if (!currentNodeRef) {
                            continue;
                        }

                        Alfresco.util.Ajax.request({
                            url: Alfresco.constants.PROXY_URI + "citeck/node?nodeRef=" + currentNodeRef,
                            successCallback: {
                                scope: this,
                                fn: function(response) {
                                    if (response.json && response.json.props) {
                                        var displayName ='';
                                        if (elCell.innerHTML)
                                            elCell.innerHTML += '<br />';

                                        if(response.json.props["cm:authorityDisplayName"]) {
                                            displayName = response.json.props["cm:authorityDisplayName"];
                                            if (showUsersOfGroup) {
                                                Alfresco.util.Ajax.request({
                                                    url: Alfresco.constants.PROXY_URI + "api/orgstruct/group/" + response.json.props["cm:authorityName"] + "/children",
                                                    successCallback: {
                                                        scope: this,
                                                        fn: function(response) {
                                                            if (response.json && response.json.length) {
                                                                var results = _.map(response.json, function(node) {
                                                                    return {text: node.displayName,
                                                                        value: node.nodeRef};
                                                                });
                                                                this.allUsersOfGroup = new YAHOO.widget.Button(elCell, {
                                                                    type: "menu",
                                                                    menu: results,
                                                                    menuclassname: "users-of-group-menu-button",
                                                                    label: displayName
                                                                });

                                                                this.allUsersOfGroup.addClass('users-of-group');
                                                            } else {
                                                                elCell.innerHTML += displayName;
                                                            }
                                                        }
                                                    }
                                                });
                                            } else {
                                                elCell.innerHTML += displayName;
                                            }
                                        }
                                        else if(response.json.props["cm:firstName"] || response.json.props["cm:lastName"])
                                        {
                                            elCell.innerHTML += response.json.props["cm:lastName"] +" "+response.json.props["cm:firstName"];
                                        }
                                    }
                                }
                            }, failureCallback: { scope: this, fn: function(response) {} }, execScripts: true
                        });
                    }
                }
            };
        },

        user: function(plainText) {
            return function(elCell, oRecord, oColumn, sData) {
                if(!sData) {
                    elCell.innerHTML = "";
                    return;
                }
                else
                {
                    Alfresco.util.Ajax.request({
                        url: Alfresco.constants.PROXY_URI + "api/people/" + sData,
                        successCallback: {
                            scope: this,
                            fn: function(response) {
                                if (response.json && response.json) {
                                    elCell.innerHTML = response.json["lastName"]+" "+response.json["firstName"];
                                }
                            }
                        }, failureCallback: { scope: this, fn: function(response) {} }, execScripts: true
                    });
                }
            };
        },

        labelByCode: function(labels) {
            return function(el, oRecord, oColumn, sData) {
                var value = sData && sData.hasOwnProperty('str') ? sData.str : sData;
                return labels[value] || value || "";
            }
        },
        _code: function(labelByCode, tdClassPrefix, trClassPrefix) {
            return function(el, oRecord, oColumn, sData) {
                var td = el.parentElement,
                    tr = td.parentElement,
                    data = sData && sData.hasOwnProperty('str') ? sData.str : sData;
                el.innerHTML = labelByCode(el, oRecord, oColumn, sData);
                if(tdClassPrefix) {
                    Dom.addClass(td, tdClassPrefix + data);
                }
                if(trClassPrefix) {
                    Dom.addClass(tr, trClassPrefix + data);
                }
            }
        },
        code: function(labels, tdClassPrefix, trClassPrefix) {
            var labelByCode = Citeck.format.labelByCode(labels);
            return Citeck.format._code(labelByCode, tdClassPrefix, trClassPrefix);
        },

        multiple: function(singleFormatter) {
            return function(elCell, oRecord, oColumn, sData) {
                if(YAHOO.lang.isArray(sData)) {
                    var texts = [];
                    for(var i = 0, ii = sData.length; i < ii; i++) {
                        singleFormatter(elCell, oRecord, oColumn, sData[i]);
                        texts[i] = elCell.innerHTML;
                    }
                    elCell.innerHTML = texts.reduce(function (resultStr, text) {
                        return resultStr += (resultStr && text ? ", " : "") + text;
                    }, "");
                } else {
                    singleFormatter(elCell, oRecord, oColumn, sData);
                }
            };
        },

        loading: function() {
            return function(elCell, oRecord, oColumn, sData) {
                elCell.innerHTML = '<span class="column-loading"></span>';
            };
        },

        message: function(prefix) {
            return function(elCell, oRecord, oColumn, sData) {
                elCell.innerHTML = Alfresco.util.message(prefix + sData);
            };
        },

        repoMessage: function(prefix) {
            var cache = repoMessageCache;
            return function(elCell, oRecord, oColumn, sData) {
                if (!sData) { return; }
                var key = (prefix || "") + sData;
                if(cache[key]) {
                    elCell.innerHTML = cache[key];
                } else {
                    Alfresco.util.Ajax.jsonGet({
                        url: Alfresco.constants.PROXY_URI + "citeck/message?key=" + key,
                        successCallback: {
                            fn: function(response) {
                                elCell.innerHTML = cache[key] = response.serverResponse.responseText;
                            }
                        },
                        failureCallback: {
                            fn: function(response) {
                                elCell.innerHTML = cache[key] = key;
                            }
                        }
                    });
                    elCell.innerHTML = '<span class="column-loading"></span>';
                }
            };
        },

        percent: function () {
            return this.valueStrFormatter(true, function(elCell, oRecord, oColumn, sData) {
                if (sData == null) {
                    elCell.innerHTML = '';
                } else {
                    elCell.innerHTML = (100 * sData) + "%";
                }
            });
        },

        workflowPriority: function() {
            return function(elCell, oRecord, oColumn, sData) {
                var codes = {
                        "1": "high",
                        "2": "medium",
                        "3": "low"
                    },
                    value = sData && sData.hasOwnProperty('str') ? sData.str : sData,
                    priority = codes[value] || null;
                if(priority) {
                    elCell.innerHTML = '<span class="priority-' + priority + '" title="' + Alfresco.util.message('priority.' + priority) + '"></span>';
                } else {
                    elCell.innerHTML = '';
                }
            };
        },

        workflowName: function() {

            return function(elCell, oRecord, oColumn, sData) {

                if (!sData) {
                    elCell.innerHTML = "";
                    return;
                }
                var value = sData.hasOwnProperty("str") ? sData.str : sData;

                getWorkflowDefinitions(function (definitions) {

                    var found = false;

                    for (var i=0; i < definitions.length; i++) {
                        if (definitions[i].name == value) {
                            elCell.innerHTML = definitions[i].title;
                            found = true;
                            break;
                        }
                    }
                    if (!found) {
                        elCell.innerHTML = "";
                    }
                });
            };
        },
        typeName: function(key) {
            var cache = typeNameCache;
            return function(elCell, oRecord, oColumn, sData) {
                if (!sData) { return; }
                if (sData.displayName) {
                    elCell.innerHTML = sData.displayName;
                    return;
                }
                var typeQName = null;
                var sDataValues = _.values(sData);
                if (sDataValues.length) {
                    if (sDataValues[0].name == "classTitle") {
                        var value = sDataValues[0].val || [];
                        elCell.innerHTML = value.length ? value[0].str : '';
                        return;
                    } else if (sDataValues[0].name == "shortName") {
                        typeQName = ((sDataValues[0].val || [])[0] || { str: null }).str;
                    }
                }
                if (!typeQName) {
                    typeQName = key ? sData[key] : sData;
                }
                if (cache[typeQName]) {
                    elCell.innerHTML = cache[typeQName];
                } else {
                    Alfresco.util.Ajax.jsonGet({
                        url: Alfresco.constants.PROXY_URI + "api/classes/" + typeQName.replace(':','_'),
                        successCallback: {
                            fn: function(response) {
                                elCell.innerHTML = cache[typeQName] = response.json.title;
                            }
                        },
                        failureCallback: {
                            fn: function(response) {
                                elCell.innerHTML = cache[typeQName] = typeQName;
                            }
                        }
                    });
                    elCell.innerHTML = '<span class="column-loading"></span>';
                }
            };
        },

        taskHistoryOutcome: function() {
            var cache = repoMessageCache;
            return function(elCell, oRecord, oColumn, sData) {
                if (!sData) { return; }

                var getMessages = function(keys, onSuccess, onFailure) {
                    var result = {};
                    var notCachedKeys = [];
                    for (var k = 0; k < keys.length; k++) {
                        var key = keys[k];
                        var cachedValue = cache[key];
                        if (cachedValue) {
                            result[key] = cachedValue;
                        } else {
                            notCachedKeys.push(key);
                        }
                    }
                    if (notCachedKeys.length > 0) {
                        Alfresco.util.Ajax.jsonPost({
                            url: Alfresco.constants.PROXY_URI + "citeck/util/messages",
                            dataObj: {"keys" : notCachedKeys},
                            successCallback: {
                                fn: function(response) {
                                    for (var i = 0; i < notCachedKeys.length; i++) {
                                        var key = notCachedKeys[i];
                                        result[key] = response.json[key];
                                        cache[key] = response.json[key];
                                    }
                                    onSuccess(result);
                                }
                            },
                            failureCallback: {
                                fn: function() {
                                    for (var i = 0; i < notCachedKeys.length; i++) {
                                        result[notCachedKeys[i]] = notCachedKeys[i];
                                    }
                                    onFailure(result);
                                }
                            }
                        });
                    } else {
                        onSuccess(result);
                    }
                };

                var typeQName = oRecord.getData()['attributes["event:taskType"]']["shortQName"];
                var outcome = sData;

                elCell.innerHTML = '<span class="column-loading"></span>';

                var keyByType = "workflowtask." + typeQName.replace(/:/g, "_") + ".outcome." + outcome;
                var globalKey = "workflowtask.outcome." + outcome;

                getMessages([keyByType, globalKey],
                    function(msgs) {
                        if (msgs[keyByType] != keyByType) {
                            elCell.innerHTML = msgs[keyByType];
                        } else if (msgs[globalKey] != globalKey) {
                            elCell.innerHTML = msgs[globalKey];
                        } else {
                            elCell.innerHTML = outcome;
                        }
                    },
                    function (msgs) {
                        elCell.innerHTML = outcome;
                    });
            };
        },

        journalLink: function (journalNodeRef, assocFieldName, target) {
            return Citeck.format.siteURL('journals2/list/main?site=contracts#journal=' + journalNodeRef +
            '&filter={%22criteria%22:[{%22field%22:%22' + assocFieldName + '%22,%22predicate%22:%22assoc-contains%22,%22value%22:%22{nodeRef}%22}]}',
                '{data}', target);
        },

        documentDetailsLink: function (target) {
            return Citeck.format.siteURL('document-details?nodeRef={nodeRef}', '{displayName}', target)
        },

        folderDetailsLink: function (target) {
            return Citeck.format.siteURL('folder-details?nodeRef={nodeRef}', '{displayName}', target)
        },

        documentLink: function () {
            return Citeck.format.siteURL('card-details?nodeRef={nodeRef}', '{displayName}', null)
        },

        folderLink: function () {
            return Citeck.format.siteURL('card-details?nodeRef={nodeRef}', '{displayName}', null)
        },

        caseLink: function () {
            return Citeck.format.siteURL('card-details?nodeRef={nodeRef}', '{displayName}', null)
        },

        siteURL: function (urlTemplate, labelTemplate, target) {
            if (!target) target = '_self';
            if (!urlTemplate) urlTemplate = '';
            return function (elCell, oRecord, oColumn, sData) {
                if (sData) {
                    if (!_.isObject(sData)) {
                        sData = {
                            data: sData,
                            nodeRef: oRecord._oData.nodeRef,
                            displayName: sData.str || sData.toString()
                        };
                    } else {
                        if (!sData.hasOwnProperty('nodeRef')) {
                            sData['nodeRef'] = sData['id'];
                        }
                        if (!sData.hasOwnProperty('displayName')) {
                            sData['displayName'] = sData['str'];
                        }
                    }
                    var url = Alfresco.util.siteURL(YAHOO.lang.substitute(urlTemplate, sData));
                    var label = YAHOO.lang.substitute(labelTemplate, sData);
                    elCell.innerHTML = '<a class="document-link" onclick="event.stopPropagation()" '
                                     + 'href="' + url + '" target="' + target + '">' + label + '</a>';
                }
            }
        },

        webscriptLink: function (urlTemplate, label) {
            return function (elCell, oRecord, oColumn, sData) {
                if (sData) {
                    var link = document.createElement('a');
                    link.className = "document-link";
                    link.href = '#';
                    link.text = Alfresco.util.message(label);
                    link.onclick = function (event) {
                        event.stopPropagation();
                        event.preventDefault();
                        window.location = Alfresco.constants.PROXY_URI + YAHOO.lang.substitute(urlTemplate, sData);
                    };

                    elCell.appendChild(link);
                }
            }
        },

        downloadContent: function (keyToNodeRef) {
            var downloadUrl = Alfresco.constants.PROXY_URI + "/citeck/print/content?nodeRef=",
                downloadImage = Alfresco.constants.URL_RESCONTEXT + "/components/documentlibrary/actions/document-download-16.png",
                title = Alfresco.util.message("actions.document.download");
            return function (elCell, oRecord) {
                var nodeRefToDownload = oRecord.getData(keyToNodeRef),
                    downloadUrlResult = downloadUrl + nodeRefToDownload;
                elCell.innerHTML = '<div class="document-download">' + '<a class="simple-link" onclick="event.stopPropagation()" '
                    + 'href="' + downloadUrlResult + '" style="background-image: url(' + downloadImage + ')" ' +
                    'title="' + title +'"/>' + '</div>';
            }
        },

        previewContent: function() {
            var downloadUrl = Alfresco.constants.PROXY_URI + "/citeck/print/content?download=false&nodeRef=",
                downloadImage = "/share/res/components/documentlibrary/actions/document-view-details-16.png",
                title = Alfresco.util.message("actions.document.view-details");

            return function (elCell, oRecord) {
                var nodeRef = oRecord.getData("nodeRef");
                var downloadUrlResult = downloadUrl + nodeRef;
                elCell.innerHTML = '<div class="document-download">' + '<a class="simple-link" onclick="event.stopPropagation()" '
                    + 'href="' + downloadUrlResult + '" style="background-image: url(' + downloadImage + ')" ' +
                    'title="' + title +'"  target="_blank"/>' + '</div>';
            }
        },

        doubleClickLink: function(urlTemplate, fieldId, formatter, target) {
            if (!target) target = '_self';
            if (!urlTemplate) urlTemplate = '';

            return function (elCell, oRecord, oColumn, sData) {
                var label;
                if (formatter) {
                    formatter.apply(this, arguments);
                    label = elCell.innerHTML;
                }
                if (!label && sData) {
                    label = sData.hasOwnProperty('str') ? sData.str : sData;
                }
                if (!label) {
                    label = Alfresco.util.message("label.none");
                }

                var url = Alfresco.constants.URL_PAGECONTEXT + YAHOO.lang.substitute(urlTemplate, { id: oRecord.getData(fieldId) });

                elCell.innerHTML = '<a class="document-link" onclick="event.stopPropagation()" '
                                 + 'href="' + url + '" target="' + target + '">' + label + '</a>';
            }
        },

        /**
         * Actions: Edit & Remove
         * Event: "actionNonContentButtonClicked"
         * Control: table-children [dynamic-table.js]
         * This action contains formatter, which creates 2 buttons: edit & remove.
         * */
        actionsNonContent: function(params) {
            /**
             * Hard Code!
             * @oRecord mandatory must contain parameter 'nodeRef' !!! NodeRef will be sent with fire-event.
             * */
            return function(elCell, oRecord, oColumn, sData) {
                var nodeRef = oRecord.getData("nodeRef");
                var panelId = "yui-actions-non-content-buttonsPanel-" + oRecord.getCount();
                if (params && params.panelID)
                    panelId = panelId + "-" + params.panelID;
                var evnBtn = { "eventType": "", "nodeRef": nodeRef, "_item_name_": nodeRef, "source": {} };
                elCell.innerHTML = '';
                var div = document.createElement("div");
                div.id = panelId;
                elCell.appendChild(div);

                /**
                 * @containerId is html-identifier of the buttons container.
                 * @type_evn type_evn is type of event. It will be sent through fire-event and it will be a part of
                 *  the css-class name.
                 * */
                var createButton = function(pnl, type_evn) {
                    // creating button
                    var btnTag = document.createElement('div');
                    btnTag.className = "btn-" + type_evn;
                    btnTag.onclick = function() {
                        evnBtn.eventType = type_evn;
                        evnBtn.elementId = this.id;
                        evnBtn.elementTag = this.tagName;
                        YAHOO.Bubbling.fire("actionNonContentButtonClicked", evnBtn);
                    };
                    pnl.appendChild(btnTag);
                    return btnTag;
                };
                // creating buttons
                createButton(div, "edit").setAttribute("title", Alfresco.util.message('title.table-children.editItem'));
                createButton(div, "remove").setAttribute("title", Alfresco.util.message('title.table-children.removeItem'));
            }
        },

        /**
         * Actions: Start workflow
         * Event: "actionNonContentButtonClicked"
         * Control: table-children [dynamic-table.js]
         * This action contains formatter, which creates 2 buttons: edit & remove.
         * */
        actionStartWorkflow: function(params) {
            /**
             * Hard Code!
             * @oRecord mandatory must contain parameter 'nodeRef' !!! NodeRef will be sent with fire-event.
             * */
            return function(elCell, oRecord, oColumn, sData) {
                var nodeRef = oRecord.getData("nodeRef");
                var actionTitle;
                var workflowId = "";
                var formId = "";
                var wf_params = "";
                var panelId = "yui-actions-non-content-buttonsPanel-" + oRecord.getCount();
                if (params && params.panelID)
                    panelId = panelId + "-" + params.panelID;
                if (params && params.actionTitle)
                    actionTitle = params.actionTitle;
                if (params && params.workflowId)
                {
                    wf_params = wf_params+"workflowId="+params.workflowId;
                }
                if (params && params.formId)
                {
                    wf_params = wf_params+"&formId="+params.formId;
                }
                if (params && params.packageItems)
                {
                    wf_params = wf_params+"&packageItems="+params.packageItems;
                }
                else
                {
                    wf_params = wf_params+"&packageItems="+nodeRef;
                }
                var evnBtn = { "eventType": "", "nodeRef": nodeRef, "_item_name_": nodeRef, "source": {}, "wf_params": wf_params};
                elCell.innerHTML = '';
                var div = document.createElement("div");
                div.id = panelId;
                elCell.appendChild(div);

                /**
                 * @containerId is html-identifier of the buttons container.
                 * @type_evn type_evn is type of event. It will be sent through fire-event and it will be a part of
                 *  the css-class name.
                 * */
                var createButton = function(pnl, type_evn) {
                    // creating button
                    var btnTag = document.createElement('div');
                    btnTag.className = "btn-" + type_evn;
                    btnTag.innerText = actionTitle ? actionTitle:"Начать бизнес процесс";
                    btnTag.onclick = function() {
                        evnBtn.eventType = type_evn;
                        evnBtn.elementId = this.id;
                        evnBtn.elementTag = 'DIV';
                        YAHOO.Bubbling.fire("actionNonContentButtonClicked", evnBtn);
                    };
                    pnl.appendChild(btnTag);
                    return btnTag;
                };
                // creating buttons
                createButton(div, "start-workflow").setAttribute("title", actionTitle ? actionTitle:"Начать бизнес процесс");
            }
        },

        dynamicTablePredicate: function(params) {
            return function(elCell, oRecord, oColumn, sData) {
                var msgId = "predicate." + oRecord.getData("journal_predicate");
                var res = Alfresco.util.message.call(this, msgId, "", null);
                if (msgId === res)
                    elCell.innerHTML = oRecord.journal_predicate;
                else
                    elCell.innerHTML = res;
            }
        },

        dynamicTableShortQName: function(params) {
            //sle
            return function(elCell, oRecord, oColumn, sData) {
                var QName = oRecord.getData("journal_fieldQName");
                var attrFullName = QName.replace('{', '%7B').replace('}', '%7D');
                elCell.innerHTML = QName;
                Alfresco.util.Ajax.request({
                    url: "/share/proxy/alfresco/search/search-attributes?attrFull=" + attrFullName,
                    successCallback: {
                        scope: this,
                        fn: function(response) {
                            if (response.json && response.json.attributes && response.json.attributes.length > 0) {
                                elCell.innerHTML = response.json.attributes[0].shortName;
                            }
                        }
                    }, failureCallback: { scope: this, fn: function(response) {} }, execScripts: true
                });
            }
        },

        taskOutcome: function() {
            var formatterScope = this;
            return function (elCell, oRecord, oColumn, sData) {
                var getParentElement = function (item, parentNodeName) {
                    var parent = item.parent().get(0);
                    return parent.nodeName == parentNodeName ? parent : getParentElement(item.parent(), parentNodeName);
                };

                var htmlid = _.uniqueId("inline-form-"),
                    taskId = oRecord._oData.taskId;

                var checkMirror = function () {
                    Alfresco.util.Ajax.jsonGet({
                        url: Alfresco.constants.PROXY_URI + "citeck/mirror-task/status?taskId=" + taskId,
                        successCallback: {
                            fn: function (response) {
                                if (response.json.status == "Completed" && response.json.completionDate) {
                                    YAHOO.Bubbling.fire("metadataRefresh");
                                } else {
                                    setTimeout(function () { checkMirror() }, 1000);
                                    console.dir("WorkflowMirrorService could not find the mirror for given task!");
                                }
                            }
                        }
                    });
                };

                var th = this.getThEl(oColumn),
                    td = this.getTdEl(elCell),
                    tr = this.getTrEl(elCell);
                Dom.addClass(th, "hide-column");
                Dom.addClass(td, "hide-column");

                var move = function() {
                    var ntr = document.createElement("TR"),
                        ntd = document.createElement("TD");
                    ntd.colSpan = this.getColumnSet().getDefinitions().length;
                    ntr.appendChild(ntd);
                    ntd.appendChild(elCell);
                    elCell.innerHTML = '<div class="loading-form"></div>';
                    Dom.insertAfter(ntr, tr);
                    this.unsubscribe('renderEvent', move, this);
                };
                this.subscribe('renderEvent', move, this, true);

                var showLegacyForm = function () {

                    Alfresco.util.Ajax.jsonGet({
                        url: Alfresco.constants.PROXY_URI + "citeck/invariants/view-check?taskId=" + taskId,
                        successCallback: {
                            scope: this,
                            fn: function(response) {
                                if(response.serverResponse.status == 200 && response.json.exists) { // form for flowable task
                                    Alfresco.util.Ajax.request({
                                        method: "GET",
                                        url: Alfresco.constants.URL_SERVICECONTEXT + "citeck/components/node-view?formType=taskId&formKey=" + taskId + "&htmlid=" + htmlid,
                                        execScripts: true,
                                        successCallback: {
                                            fn: function(response) {
                                                elCell.innerHTML = response.serverResponse.responseText;
                                            }
                                        }
                                    });
                                } else {

                                    YAHOO.Bubbling.on("beforeFormRuntimeInit", function(layer, args) {
                                        if (Alfresco.util.hasEventInterest(htmlid + "-form", args)) {
                                            args[1].runtime.setAJAXSubmit(true, {
                                                successCallback: {
                                                    scope: this,
                                                    fn: function(response) {
                                                        //setTimeout(function () { checkMirror() }, 1000);
                                                        YAHOO.Bubbling.fire("metadataRefresh");
                                                    }
                                                },
                                                failureCallback: {
                                                    scope: this,
                                                    fn: formatterScope.onFailure
                                                }
                                            });
                                        }
                                    });

                                    Citeck.utils.loadHtml(Alfresco.constants.URL_SERVICECONTEXT + "citeck/form/inline", {
                                        itemKind: 'task',
                                        itemId: taskId,
                                        formId: 'inline',
                                        submitType: 'json',
                                        htmlid: htmlid,
                                        showSubmitButton: false
                                    }, elCell, null, {
                                        scope: this,
                                        fn: formatterScope.onFailure
                                    });
                                }
                            }
                        }
                    });
                };

                Citeck.Records.get('ecos-config@ecos-forms-card-enable').load('.bool').then(function(isEnable) {
                    if (isEnable) {
                        Citeck.forms.editRecord({
                            recordRef: "wftask@" + taskId,
                            fallback: showLegacyForm,
                            formContainer: elCell,
                            onSubmit: function(record) {
                                YAHOO.Bubbling.fire("metadataRefresh");
                            }
                        });
                    } else {
                        showLegacyForm();
                    }
                }).catch(function() {
                    showLegacyForm();
                });
            }
        },

        taskButtons: function() {
            var formatterScope = this;
            return function (elCell, oRecord, oColumn, sData) {
                var th = this.getThEl(oColumn),
                    td = this.getTdEl(elCell),
                    tr = this.getTrEl(elCell);
                Dom.addClass(th, "hide-column");
                Dom.addClass(td, "hide-column");

                var move = function() {
                    this.unsubscribe('renderEvent', move, this);
                    var ntr = document.createElement("TR"),
                        ntd = document.createElement("TD");
                    ntd.colSpan = this.getColumnSet().getDefinitions().length;
                    ntr.appendChild(ntd);
                    ntd.appendChild(elCell);
                    Dom.insertAfter(ntr, tr);
                    var taskId = oRecord._oData.taskId;
                    var htmlid = _.uniqueId("task-buttons-");

                    var disableActionButtons = function(disabled)
                    {
                        if (reassignButton)
                        {
                            reassignButton.set("disabled", disabled)
                        }
                        if (releaseButton)
                        {
                            releaseButton.set("disabled", disabled)
                        }
                        if (claimButton)
                        {
                            claimButton.set("disabled", disabled)
                        }
                    }
                    var updateTaskProperties = function (properties, action)
                    {
                        disableActionButtons(true);
                        YAHOO.lang.later(2000, this, function()
                        {
                            if (this.isRunning)
                            {
                                    var feedbackMessage = Alfresco.util.PopupManager.displayMessage(
                                    {
                                        text: Alfresco.util.message("message." + action),
                                        spanClass: "wait",
                                        displayTime: 0
                                    });
                            }
                        }, []);

                         // Run rules for folder (and sub folders)
                        if (!this.isRunning)
                        {
                            this.isRunning = true;

                            // Start/stop inherit rules from parent folder
                            Alfresco.util.Ajax.jsonPut(
                            {
                                url: Alfresco.constants.PROXY_URI + "citeck/tasks/change-task-owner/" + taskId,
                                dataObj: properties,
                                successCallback:
                                {
                                    fn: function(response, action)
                                    {
                                        //this.isRunning = false;
                                        var data = response.json.data;
                                        if (data)
                                        {
                                            Alfresco.util.PopupManager.displayMessage(
                                            {
                                                text: Alfresco.util.message("message." + action + ".success")
                                            });

                                            YAHOO.lang.later(3000, this, function(data)
                                            {
                                                if (data.owner && data.owner.userName == Alfresco.constants.USERNAME)
                                                {
                                                    // Let the user keep working on the task since he claimed it
                                                    document.location.reload();
                                                }
                                                else
                                                {
                                                    // Check referrer and fall back to user dashboard if unavailable.
                                                    if(this.referrerValue)
                                                    {
                                                        // Take the user to the most suitable place
                                                        this.navigateForward(true);
                                                    } else {
                                                     // ALF-20001. If referrer isn't available, either because there was no previous page
                                                     // (because the user navigated directly to the page via an emailed link)
                                                     // or because the referrer header has been blocked, fall back to user dashboard.
                                                     document.location.href = Alfresco.constants.URL_CONTEXT;
                                                    }
                                                }
                                            }, data);
                                        }
                                    },
                                    obj: action,
                                    scope: this
                                },
                                failureCallback:
                                {
                                    fn: function(response)
                                    {
                                        this.isRunning = false;
                                        disableActionButtons(false);
                                        Alfresco.util.PopupManager.displayPrompt(
                                        {
                                            title: Alfresco.util.message("message.failure"),
                                            text: Alfresco.util.message("message." + action + ".failure")
                                        });
                                    },
                                    scope: this
                                }
                            });
                        }
                    }


                    var onClaimButtonClick = function ()
                    {
                        updateTaskProperties(
                        {
                            "cm_owner": Alfresco.constants.USERNAME,
                            "action": "claim"
                        }, "claim");
                    };

                    var onReleaseButtonClick = function ()
                    {
                        updateTaskProperties(
                        {
                            "cm_owner": null,
                            "action": "release"
                        }, "release");
                    };

                    var reassignPanel;

                    var onReassignButtonClick = function (layer, args)
                    {
                        var peopleFinder = Alfresco.util.ComponentManager.get(htmlid + "-peopleFinder");
                        //var reassignPanel = Alfresco.util.ComponentManager.get(htmlid + "-reassignPanel");
                        peopleFinder.clearResults();
                        reassignPanel.show();
                    };

                    elCell.innerHTML='';
                    var actionsDiv = document.createElement('div');
                    actionsDiv.className ="actions";

                    elCell.appendChild(actionsDiv)

                    //var buttonsHTML = '<div class="actions" id="'+htmlid+'">'
                    var claimable = oRecord._oData.claimable;

                    actionsDiv.id=htmlid;
                    if (claimable=="true") {
                        var claimButton = new YAHOO.widget.Button({
                            type: "button",
                            container: htmlid,
                            label: Alfresco.util.message("button.claim"),
                            onclick: {
                                fn: onClaimButtonClick,
                            },
                        });
                        //Alfresco.util.createYUIButton(actionsDiv, "claim", this.onClaimButtonClick1, [], button);
                        Dom.removeClass(Selector.query(".actions .claim", htmlid), "hidden");

                        //buttonsHTML+='<span class="claim" id="'+htmlid+'-claim-span"><button id="'+htmlid+'-claim">'+Alfresco.util.message("button.claim")+'</button></span> ';
                    }

                    var reassignable = oRecord._oData.reassignable;
                    if (reassignable=="true") {
                        var reassignButton = new YAHOO.widget.Button({
                            type: "button",
                            container: htmlid,
                            label: Alfresco.util.message("button.reassign"),
                            onclick: {
                                fn: onReassignButtonClick,
                            },
                        });
                        Dom.removeClass(Selector.query(".actions .reassign", htmlid), "hidden");
                        //buttonsHTML+='<span class="reassign" id="'+htmlid+'-reassign-span"><button id="'+htmlid+'-reassign">'+Alfresco.util.message("button.reassign")+'</button></span> ';
                    }

                    var releasable = oRecord._oData.releasable;
                    if (releasable=="true") {
                        var releaseButton = new YAHOO.widget.Button({
                            type: "button",
                            container: htmlid,
                            label: Alfresco.util.message("button.release"),
                            onclick: {
                                fn: onReleaseButtonClick,
                            },
                        });
                        Dom.removeClass(Selector.query(".actions .release", htmlid), "hidden");
                        //buttonsHTML+='<span class="release" id="'+htmlid+'-release-span"><button id="'+htmlid+'-release">'+Alfresco.util.message("button.release")+'</button></span>';
                    }
                    elCell.innerHTML+='<div style="display: none;"> <div id="'+htmlid+'-reassignPanel" class="task-edit-header reassign-panel"> <div class="hd">'+Alfresco.util.message("panel.reassign.header")+'</div> <div class="bd"> <div style="margin: auto 10px;"> <div id="'+htmlid+'-peopleFinder"></div> </div> </div> </div> </div>';


                    //+htmlid+'-peopleFinder
                    var finderHtmlId = htmlid + "-peopleFinder";
                    var url = Alfresco.constants.URL_SERVICECONTEXT + "components/people-finder/people-finder";

                    Citeck.utils.loadHtml(url, {htmlid: finderHtmlId}, Dom.get(finderHtmlId), function() {

                        // Create the Assignee dialog
                        reassignPanel = Alfresco.util.createYUIPanel(htmlid + "-reassignPanel");

                        // Find the People Finder by container ID
                        var peopleFinder = Alfresco.util.ComponentManager.get(htmlid + "-peopleFinder");

                        // Set the correct options for our use
                        peopleFinder.setOptions({
                            singleSelectMode: true,
                            addButtonLabel: Alfresco.util.message("button.select")
                        });

                        // Make sure we listen for events when the user selects a person
                        YAHOO.Bubbling.on("personSelected", onPersonSelected, this);
                    });

                    //var onPeopleFinderLoaded =
                    var onPersonSelected = function(e, args)
                    {
                        // This is a "global" event so we ensure the event is for the current panel by checking panel visibility.
                        var peopleFinder = Alfresco.util.ComponentManager.get(htmlid + "-peopleFinder");
                        //var reassignPanel = Alfresco.util.ComponentManager.get(htmlid + "-reassignPanel");
                        if (Alfresco.util.hasEventInterest(peopleFinder, args))
                        {
                            reassignPanel.hide();
                            updateTaskProperties(
                            {
                                "cm_owner": args[1].userName,
                                "action": "claim"
                            }, "reassign");
                        }
                    };
                };
                this.subscribe('renderEvent', move, this, true);
            }
        },

        taskAttachments: function() {
            return function(elCell, oRecord, oColumn, oData) {
                if(!oData || oData.length == 0) {
                    elCell.innerHTML = "";
                    return;
                }
                elCell.innerHTML = '<img src="' + Alfresco.constants.URL_RESCONTEXT + 'citeck/images/attachment-16.png" />'
            }
        },

        taskLink: function() {
            return function(elCell, oRecord, oColumn, sData) {
                if(!sData) {
                    elCell.innerHTML = "";
                    return;
                }
                elCell.innerHTML = '<a title="' + Alfresco.util.message("button.view.detailed") + '" href="' + Alfresco.constants.URL_PAGECONTEXT + 'task-details?taskId=' + sData + '" target="_blank">' +
                    '<img src="' + Alfresco.constants.URL_RESCONTEXT + 'citeck/images/task-16.png"></a>';
            }
        },

        lastTask: function(isDisplayOnlyOneActor) {
            var cache = typeNameCache;
            return function(elCell, oRecord, oColumn, sData) {
                function getOwner() {
                    var owner = '';
                    var assignee = sData['wfm:assignee'];
                    var actors = sData['wfm:actors'];
                    if (assignee) {
                        owner = (assignee['type'] === "cm:person") ? assignee['cm:lastName'] + ' ' + assignee['cm:firstName'] : assignee['cm:authorityDisplayName'] || assignee['cm:authorityName'] || assignee.displayName;
                    } else if (actors && actors.length != 0) {
                        if (isDisplayOnlyOneActor){
                            if(actors[0]) {
                                var actor = actors[0];
                                owner = (actor.type == "cm:person") ? actor['cm:lastName'] + ' ' + actor['cm:firstName'] : actor['cm:authorityDisplayName'] || actor['cm:authorityName'] || actor.displayName;
                            }
                        }else {
                            owner = actors.reduce(function (actorsList, actor) {
                                return actorsList += (actorsList ? ", " : "") +
                                    (actor.type == "cm:person" ? actor['cm:lastName'] + ' ' + actor['cm:firstName'] : actor['cm:authorityDisplayName'] || actor['cm:authorityName'] || actor.displayName);
                            }, owner);
                        }
                    }
                    return owner;
                }

                function setType(id, type) {
                    var element = $('#' + id).get(0);
                    if (element) {
                        element.innerHTML = " (" + type + ")";
                    }
                }

                if (!sData) return;

                elCell.innerHTML = '';
                if (sData['bpm:status'] == "Not Yet Started") {
                    var taskId = _.uniqueId('last-task-type-');
                    elCell.innerHTML = getOwner() + '<span id="' + taskId + '"></`span>';

                    if(cache[sData.type]) {
                        elCell.innerHTML = getOwner() + " (" + cache[sData.type] + ")";
                    } else {
                        Alfresco.util.Ajax.jsonGet({
                            url: Alfresco.constants.PROXY_URI + "api/classes/" + sData.type.replace(':', '_'),
                            successCallback: {
                                fn: function (response) {
                                    setType(taskId, cache[sData.type] = response.json.title);
                                }
                            },
                            failureCallback: {
                                fn: function (response) {
                                    setType(taskId, cache[sData.type] = sData.type);
                                }
                            }
                        });
                    }
                }

            }
        },

        overdueNodeRef: function(propertyName) {
            return function (elCell, oRecord, oColumn, sData) {
                if (!sData) {
                    elCell.innerHTML = "";
                    return;
                }
                else {
                    var propValue = oRecord._oData["attributes['" + propertyName + "']"]
                        && oRecord._oData["attributes['" + propertyName + "']"][0]
                        ? oRecord._oData["attributes['" + propertyName + "']"][0].str : false;
                    if (propValue && propValue === "true") {
                        Dom.addClass(elCell.parentElement.parentElement, "yui-overdue-node");
                        var currentElement = elCell.parentElement.nextElementSibling;
                        while (currentElement != null) {
                            if (currentElement.children[0] && currentElement.children[0].children[0]
                                && currentElement.children[0].children[0].localName == "a") {
                                for (var i = 0; i < currentElement.children[0].children.length; i++) {
                                    Dom.addClass(currentElement.children[0].children[i], "yui-overdue-node");
                                }
                            }
                            currentElement = currentElement.nextElementSibling;
                        }
                    }
                }
                elCell.innerHTML = sData.hasOwnProperty('str') ? sData.str : sData;
            };
        },

        onFailure : function(response) {
            var failure = Alfresco.util.message("message.failure");
            var errorMsg = failure;
            if (response.json && response.json.message) {
                errorMsg = response.json.message;
            } else if (response.message) {
                errorMsg = response.message;
            }
            Alfresco.util.PopupManager.displayPrompt({
                title : failure,
                text : errorMsg
            });
        },

    // TODO move to external module in maxxium project
        maxxiumAddressFormatter : function(prefix) {
            return function(elCell, oRecord, oColumn, sData) {
                var index = oRecord.getData("attributes['" + prefix + "Index']");
                var region = oRecord.getData("attributes['" + prefix + "Region']");
                var city = oRecord.getData("attributes['" + prefix + "City']");
                var street = oRecord.getData("attributes['" + prefix + "Street']");
                var house = oRecord.getData("attributes['" + prefix + "House']");
                elCell.innerHTML  = (region ? region : '') + (city ? ', ' + city : '') + (street ? ', ' + street : '') + (house ? ', ' + house : '') + (index ? ', ' + index : '');
            };
        },

        propertyDisplayName: function(key) {
            return function(elCell, oRecord, oColumn, sData) {
                if (!sData) return;
                var doc = oRecord.getData(key);

                elCell.innerHTML = '';
                        Alfresco.util.Ajax.jsonGet({
                            url: Alfresco.constants.PROXY_URI + "api/classes/" + doc.type.replace(':', '_'),
                            successCallback: {
                                fn: function (response) {
                                    if(response.json.properties[sData.shortQName])
                                    {
                                        elCell.innerHTML = response.json.properties[sData.shortQName].title;
                                    }
                                    else
                                    {
                                        if(response.json.associations[sData.shortQName])
                                        {
                                            elCell.innerHTML = response.json.associations[sData.shortQName].title;
                                        }
                                        else
                                        {
                                            if(response.json.childassociations[sData.shortQName])
                                            {
                                                if(sData.shortQName=='cm:contains')
                                                {
                                                    elCell.innerHTML = Alfresco.util.message('dochist.assoc.contains');
                                                }
                                                else
                                                {
                                                    elCell.innerHTML = response.json.childassociations[sData.shortQName].title;
                                                }
                                            }
                                            else
                                            {
                                                elCell.innerHTML = sData.shortQName;
                                            }
                                        }
                                    }
                                }
                            },
                            failureCallback: {
                                fn: function (response) {
                                    elCell.innerHTML = sData.shortQName;
                                }
                            }
                        });
                }

        },

        fieldsListFormatter : function(fields, separator) {
            if (!fields) {
                fields = [];
            }
            return function(elCell, oRecord, oColumn, sData) {
                var cellHtml = '';
                for(var i = 0; i < fields.length; i++) {
                    var field = fields[i];
                    var fieldValue = oRecord.getData("attributes['" + field + "']") ? oRecord.getData("attributes['" + field + "']") : '';
                    cellHtml += cellHtml? separator + fieldValue : fieldValue;
                }
                elCell.innerHTML = cellHtml;
            };
        },

        historyChanges : function() {
            return function(elCell, oRecord, oColumn, sData) {
                var taskType = oRecord.getData('attributes["event:taskType"]');
                var wfType = oRecord.getData('attributes["event:workflowType"]');
                var propertyName = oRecord.getData('attributes["event:propertyName"]');
                if(taskType)
                {
                    Alfresco.util.Ajax.jsonGet({
                        url: Alfresco.constants.PROXY_URI + "api/classes/" + taskType.shortQName.replace(':','_'),
                        successCallback: {
                            fn: function(response) {
                                elCell.innerHTML = response.json.title;
                            }
                        },
                        failureCallback: {
                            fn: function(response) {
                                elCell.innerHTML = taskType.shortQName;
                            }
                        }
                    });
                }
                else
                {
                    if(wfType)
                    {
                        Alfresco.util.Ajax.jsonGet({
                            url: Alfresco.constants.PROXY_URI + "api/workflow-definitions",
                            successCallback: {
                                fn: function(response) {
                                    var data = response.json.data;
                                    for(var i=0; i<data.length; i++)
                                    {
                                        if(data[i].name==wfType)
                                        {
                                            elCell.innerHTML = data[i].title;
                                            break;
                                        }
                                    }
                                }
                            },
                            failureCallback: {
                                fn: function(response) {
                                    elCell.innerHTML = '';
                                }
                            }
                        });
                    }
                    else
                    {
                        if(propertyName)
                        {
                            var doc = oRecord.getData('attributes["event:document"][0]');
                            Alfresco.util.Ajax.jsonGet({
                                url: Alfresco.constants.PROXY_URI + "api/get-all-properties",
                                successCallback: {
                                    fn: function (response) {
                                        var data = response.json;
                                        for(var i=0; i<data.length; i++)
                                        {
                                            if(data[i].prefixedName==propertyName.shortQName)
                                            {
                                                elCell.innerHTML = data[i].title;
                                                break;
                                            }
                                        }
                                        if(elCell.innerHTML=='')
                                        {
                                            if(propertyName.shortQName=='cm:contains')
                                            {
                                                var targetNodeKind = oRecord.getData('attributes["event:targetNodeKind"]');
                                                var targetNodeType = oRecord.getData('attributes["event:targetNodeType"]');
                                                if(targetNodeKind)
                                                {
                                                    elCell.innerHTML = targetNodeKind.displayName;
                                                }
                                                else
                                                if(targetNodeType)
                                                {
                                                    elCell.innerHTML = targetNodeType.displayName;
                                                }
                                                else
                                                    elCell.innerHTML = Alfresco.util.message('dochist.assoc.contains');
                                            }
                                            else
                                            {
                                                Alfresco.util.Ajax.jsonGet({
                                                    url: Alfresco.constants.PROXY_URI + "api/get-all-association",
                                                    successCallback: {
                                                        fn: function (response) {
                                                            var data = response.json;
                                                            for(var i=0; i<data.length; i++)
                                                            {
                                                                if(data[i].prefixedName==propertyName.shortQName)
                                                                {
                                                                    elCell.innerHTML = data[i].title;
                                                                    break;
                                                                }
                                                            }
                                                        }
                                                    },
                                                    failureCallback: {
                                                        fn: function (response) {
                                                            elCell.innerHTML = propertyName.shortQName;
                                                        }
                                                    }
                                                });
                                            }
                                        }
                                    }
                                },
                                failureCallback: {
                                    fn: function (response) {
                                        elCell.innerHTML = propertyName.shortQName;
                                    }
                                }
                            });
                        }
                    }
                }
            };
        },

        userAssocActions: function(sourceRef, assocTypes) {
            return function(elCell, oRecord, oColumn, sData) {
                var targetRef = oRecord.getData("nodeRef");
                var recordUserId = oRecord.getData('attributes["cm:userName"]');
                var panelId = "yui-actions-non-content-buttonsPanel-" + oRecord.getCount();
                var userId = Alfresco.constants.USERNAME;
                elCell.innerHTML = '';
                var div = document.createElement("div");
                div.id = panelId;
                elCell.appendChild(div);
                /**
                 * @type_evn type_evn is type of event. It will be sent through fire-event and it will be a part of
                 *  the css-class name.
                 * */
                var createButton = function(pnl, type_evn, className, action) {
                    var btnTag = document.createElement('div');
                    btnTag.className = "btn-" + type_evn + " " + className;
                    btnTag.onclick = action;
                    btnTag.style = "width:auto;";
                    pnl.appendChild(btnTag);
                    return btnTag;
                };

                var buttonRemoveAction = function() {
                    Alfresco.util.PopupManager.displayPrompt({
                        title: Alfresco.util.message("message.confirm.delete.1.title", 1),
                        text: Alfresco.util.message("message.confirm.delete"),
                        noEscape: true,
                        buttons: [
                            {
                                text: Alfresco.util.message("actions.button.ok"),
                                handler: function dlA_onActionOk()
                                {
                                    Alfresco.util.Ajax.request({
                                        url: Alfresco.constants.PROXY_URI + "citeck/remove-assocs?sourceRef="+sourceRef+
                                                                            "&targetRef="+targetRef+"&assocTypes="+assocTypes,
                                        method: Alfresco.util.Ajax.DELETE,
                                        successCallback: {
                                            fn: function (response) {
                                                YAHOO.Bubbling.fire("metadataRefresh");
                                            },
                                            scope: this
                                        },
                                        failureMessage: Alfresco.util.message("message.delete.failure", "", targetRef),
                                        scope: this
                                    });
                                    this.destroy();
                                }
                            },
                            {
                                text: Alfresco.util.message("actions.button.cancel"),
                                handler: function dlA_onActionCancel()
                                {
                                    this.destroy();
                                },
                                isDefault: true
                            }]
                    });
                };

                if(sourceRef && targetRef && assocTypes) {

                    var hasPermissionUrl = Alfresco.constants.PROXY_URI +
                                            'citeck/has-permission?nodeRef=' +
                                            sourceRef + '&permission=Write';

                    YAHOO.util.Connect.asyncRequest(
                        'GET',
                        hasPermissionUrl, {
                            success: function (response) {
                                if (response.responseText.trim() == "true" || userId == recordUserId) {
                                    var remove_btn = createButton(div, "remove", "remove-link", buttonRemoveAction);
                                    remove_btn.setAttribute("title", Alfresco.util.message('title.table-children.removeItem'));
                                }
                            },
                            scope: this
                        }
                    );
                }
            }
        },


        assocOrProps: function(props, delimeter) {
            if (!props) props = "cm:name";

            var propsArr = props.split(",").map(function (p) {return p.trim();});

            if (delimeter == null) delimeter = ", ";

            return function(elCell, oRecord, oColumn, sData) {


                var request = function(nodeRef) {
                    Alfresco.util.Ajax.request({
                        url: Alfresco.constants.PROXY_URI + "citeck/node?nodeRef=" + nodeRef + "&props=" + props + "&replaceColon=_",
                        successCallback: {
                            fn: function(response) {
                                if (response.json && response.json.props) {
                                    if (elCell.innerHTML) elCell.innerHTML += "<br>";
                                    elCell.innerHTML += _.values(response.json.props).join(delimeter);
                                }
                            }
                        },
                        failureCallback: {
                            fn: function(response) {
                                elCell.innerHTML = sData;
                            }
                        },
                        execScripts: true
                    });
                }

                if (!sData || !(sData instanceof Object || sData instanceof String)) {
                    elCell.innerHTML = "";
                    return;
                }

                if (sData instanceof String) {
                    if (sData.indexOf("workspace") != -1) { request(sData) }
                    else if (/^-?\d*(\.\d+)?$/.test(sData)) { elCell.innerHTML = parseFloat(sData) }
                    else { elCell.innerHTML = sData }
                }

                if (sData instanceof Object) {
                    var renderRequest = function(object) {
                        var hasReqData = propsArr.every(function(k) {
                            return _.find(_.values(object), function (val) {
                                return val && val.name == k;
                            });
                        });
                        if (hasReqData) {
                            if (elCell.innerHTML) elCell.innerHTML += "<br>";
                            var values = [];
                            _.values(object).map(function (item) {
                                if (item.val) {
                                    item.val.map(function (val) {
                                        if (val.str) {
                                            values.push(val.str);
                                        }
                                    });
                                } else {
                                    values.push(item);
                                }
                            });
                            elCell.innerHTML = values.join(delimeter);
                        } else if (_.has(object, "nodeRef")) {
                            request(object.nodeRef)
                        } else {
                            if (elCell.innerHTML) elCell.innerHTML += "<br>";
                            elCell.innerHTML = _.values(object).join(delimeter)
                        }
                    };

                    if (sData instanceof Array) {
                        for (var d = 0; d < sData.length; d++) {
                            if (sData[d]) renderRequest(sData[d]);
                        }
                    } else {renderRequest(sData);}
                }
            };
        },

        // change property to another property if original is not exist
        replaceable: function(attributeName, formatter, direction) {
            return function (elCell, oRecord, oColumn, sData) {
                var anotherAttribute = oRecord.getData(attributeName);

                if ((direction || sData == undefined) && anotherAttribute) {
                    if (formatter.another)  {
                        formatter.another(elCell, oRecord, oColumn, anotherAttribute);
                        return;
                    }

                    elCell.innerHTML = anotherAttribute;
                    return;
                }

                if (formatter.original) {
                    formatter.original(elCell, oRecord, oColumn, sData);
                    return
                }

                elCell.innerHTML = sData;
                return
            }
        },

        transformUseLabel: function(labelByCode, formatter) {
            return function(elCell, oRecord, oColumn, sData) {
                if(YAHOO.lang.isArray(sData)) {
                    var texts = [];
                    for (var i = 0, ii = sData.length; i < ii; i++) {
                        texts[i] = labelByCode(elCell, oRecord, oColumn, sData[i]);
                    }
                    formatter(elCell, oRecord, oColumn, texts);
                } else {
                    var text = labelByCode(elCell, oRecord, oColumn, sData);
                    formatter(elCell, oRecord, oColumn, text);
                }
            };
        },
        truncateVertical: function(target) {
            if (!target) target = 3;
            return function(elCell, oRecord, oColumn, sData) {
                if(YAHOO.lang.isArray(sData)) {
                    if (sData.length > target) {
                        elCell.innerHTML = '<div class="truncatedInfo">'
                            + '<div>'
                            + sData.slice(0, target).reduce(function (resultStr, text) {
                                return resultStr += (resultStr && text ? ",<br />" : "") + text;
                            }, "")
                            + ',<br /> ...'
                            + '</div>'

                            + '<div class="untruncatedInfo">'
                            + sData.reduce(function (resultStr, text) {
                                return resultStr += (resultStr && text ? ",<br />" : "") + text;
                            }, "")
                            + '</div>'
                            + '</div>';
                    } else {
                        elCell.innerHTML = sData.reduce(function (resultStr, text) {
                            return resultStr += (resultStr && text ? ",<br />" : "") + text;
                        }, "");
                    }
                } else {
                    elCell.innerHTML = sData;
                }
            };
        },

        truncate: function (target) {
            if (!target) target = 100;
            return function (elCell, oRecord, oColumn, sData) {
                if (sData) {
                    var result = sData;
                    if (result.length > target) {
                        result = result.slice(0, target - 3) + "...";
                    }
                    elCell.innerHTML = result;
                }
            }
        },

        downloadSign: function (attributeName, message) {
            return function (elCell, oRecord, oColumn, sData) {
                var linkValue = sData ? sData : oRecord.getData(attributeName),
                    redirection = sData ? Alfresco.constants.PROXY_URI + sData : Alfresco.constants.PROXY_URI + "/acm/getDecodeESign?nodeRef=" + linkValue.nodeRef,
                    signLink = document.createElement('a');
                signLink.className = "document-link";
                signLink.onclick = function (event) {
                    event.stopPropagation();
                    event.preventDefault();
                    if (!linkValue) {
                        Alfresco.util.PopupManager.displayPrompt({
                            title: Alfresco.util.message("actions.sign.download.title"),
                            text: Alfresco.util.message("actions.sign.download.text"),
                            noEscape: true,
                            buttons: [
                                {
                                    text: Alfresco.util.message("actions.button.ok"),
                                    handler: function empt_ok() {
                                        this.destroy();
                                    },
                                    isDefault: true
                                }]
                        });
                    } else {
                        window.location = redirection;
                    }
                };
                signLink.href = '#';
                if (message) {
                    signLink.text = Alfresco.util.message(message);
                } else {
                    signLink.text = Alfresco.util.message("button.financial-request-documents.download-sign");
                }

                elCell.appendChild(signLink);
            }
        },

        getChildAssociationProperty: function (associationName, propertyName, options) {
            return function (elCell, oRecord, oColumn) {
                var childAssociations = oRecord.getData('childAssociations');
                var childAssociation = _.find( childAssociations, function(item) { return item.name == associationName; });
                if (childAssociation) {
                    var anotherPropertyName = (options || {}).anotherPropertyName;
                    var formatter = (options || {}).formatter;

                    var property = null;
                    if (propertyName == 'nodeRef') {
                        property = { nodeRef: childAssociation.nodeRef };
                    } else if (childAssociation['attributes'][propertyName]) {
                        property = childAssociation['attributes'][propertyName];
                    } else if (anotherPropertyName) {
                        property = childAssociation['attributes'][anotherPropertyName];
                    }

                    if (!property) {
                        return;
                    }

                    if (formatter) {
                        options.formatter(elCell, oRecord, oColumn, property);
                    } else {
                        elCell.innerHTML = property;
                    }
                }
            }
        },

        inactivityTaskPeriod: function () {
            return function (elCell, oRecord, oColumn, sData) {
                var _MS_PER_DAY = 1000 * 60 * 60 * 24;
                var startDate = oRecord.getData()["attributes['bpm:startDate']"];
                var status = oRecord.getData()["attributes['bpm:status']"];
                if (startDate && startDate !== null) {
                    var stDate = new Date(startDate);
                    var currDate = new Date();
                    var currUTC = Date.UTC(currDate.getFullYear(), currDate.getMonth(), currDate.getDate());
                    var startUTC = Date.UTC(stDate.getFullYear(), stDate.getMonth(), stDate.getDate());
                    var daysDiff = Math.floor((currUTC - startUTC) / _MS_PER_DAY);
                    if (daysDiff !== 0 && status !== 'Completed') {
                        elCell.innerHTML = daysDiff;
                    } else {
                        elCell.innerHTML = "";
                    }
                } else {
                    elCell.innerHTML = "";
                }
            };
        },

        scannerIcon: function () {
            return function (elCell, oRecord, oColumn, sData) {
                var wfmDocument = (oRecord.getData("attributes['wfm:document']") || [])[0];

                if (wfmDocument == null || wfmDocument.nodeRef == null) {
                    return;
                }

                Alfresco.util.Ajax.jsonGet({
                    url: Alfresco.constants.PROXY_URI + "api/citeck/has-scan?nodeRef=" + wfmDocument.nodeRef,
                    successCallback: {
                        fn: function(response) {
                            if (response.json && response.json.hasScan && response.json.hasScan == "true") {
                                elCell.innerHTML = '<img src="'+ Alfresco.constants.URL_RESCONTEXT + 'citeck/components/dynamic-tree/icons/scanner.png">';
                            }
                        }
                    }
                });
            };
        },

        taskTitle: function () {
            var typeName = this.typeName(null);

            return function(elCell, oRecord, oColumn, sData) {
                if (!sData) {
                    elCell.innerHTML = "";
                    return;
                }
                var title = oRecord.getData('taskTitle');
                if (title) {
                    elCell.innerHTML = title;
                    return;
                }
                title = (oRecord.getData("attributes['cm:title']") || [])[0];
                if (!title) {
                    title = (oRecord.getData("attributes['cwf:taskTitle']") || [])[0];
                }
                if (title && title.hasOwnProperty("str")) {
                    elCell.innerHTML = title.str;
                } else {
                    if (sData && sData.hasOwnProperty("str")) {
                        sData = sData.str;
                    }
                    typeName.call(this, elCell, oRecord, oColumn, sData);
                }
            };
        },

        dueDateTime: function () {
            var date = this.date('dd.MM.yyyy HH:mm');
            var dateOrDateTime = this.dateOrDateTime();

            return function(elCell, oRecord, oColumn, sData) {
                var dueDateTime = oRecord.getData('attributes["cwf:dueDateTime"]') || (oRecord.getData('attributes[\'cwf:dueDateTime\']') || [])[0];
                if (dueDateTime) {
                    sData = dueDateTime.str || dueDateTime;
                    date.call(this, elCell, oRecord, oColumn, sData);
                    return;
                }

                dueDateTime = oRecord.getData('attributes["bpm:dueDate"]') || (oRecord.getData('attributes[\'bpm:dueDate\']') || [])[0];
                if (dueDateTime) {
                    sData = dueDateTime.str || dueDateTime;
                    dateOrDateTime.call(this, elCell, oRecord, oColumn, sData);
                    return;
                }

                elCell.innerHTML = '';
            };
        },

        formatterFromString: function (expression) {
            return function (elCell, oRecord, oColumn, sData) {
                eval(expression);
            }
        }
    });

    return Citeck.format;
});
