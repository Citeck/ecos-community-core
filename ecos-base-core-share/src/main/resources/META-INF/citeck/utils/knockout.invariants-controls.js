/*
 * Copyright (C) 2015-2017 Citeck LLC.
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
    'lib/knockout',
    'citeck/utils/knockout.utils',
    'citeck/utils/knockout.components',
    'citeck/components/invariants/invariants',
    'citeck/components/journals2/journals',
    'lib/moment',
    'underscore'
], function ($, ko, koutils, kocomponents, invariants, journals, moment, _) {

// ----------------
// GLOBAL FUNCTIONS
// ----------------

var $html = Alfresco.util.encodeHTML,
    $combine = Alfresco.util.combinePaths,
    koclass = koutils.koclass;

var Event = YAHOO.util.Event,
    Dom = YAHOO.util.Dom;

var JournalType = koclass('JournalType'),
    Node = koclass('invariants.Node');

// TODO: refactoring
// - integrate the calendar into a single function for the date and datetime controls

// ---------------
// HELP
// ---------------

YAHOO.widget.Tooltip.prototype.onContextMouseOut = function (e, obj) {
    var el = this,
        procIds = [ "showProcId", "hideProcId" ];

    if (obj._tempTitle) {
        el.title = obj._tempTitle;
        obj._tempTitle = null;
    }

    for (var p = 0; p < procIds.length; procIds++) {
        if (obj[procIds[p]]) {
            clearTimeout(obj[procIds[p]]);
            obj[procIds[p]] = null;
        }
    }

    obj.fireEvent("contextMouseOut", el, e);

    if (!obj.cfg.getProperty("forceVisible")) {
        obj.hideProcId = setTimeout(function () { obj.hide(); }, obj.cfg.getProperty("hidedelay"));
    }
};

// TODO:
// - init tooltip only if text not empty
function getHintPropertyByCurrentUser(callback) {

    if (!window.getHintPropertyByCurrentUser) {
        window.getHintPropertyByCurrentUser = ko.observable(null);

        Alfresco.util.Ajax.jsonGet({
            url: Alfresco.constants.PROXY_URI + "citeck/search/query",
            dataObj: {
                query: '=cm:userName:"' + Alfresco.constants.USERNAME + '"',
                schema: JSON.stringify({attributes:{'org:showHints':''}})
            },
            successCallback: {
                scope: this,
                fn: function(response) {
                    window.getHintPropertyByCurrentUser(response.json);
                }
            },
            failureCallback: {
                scope: this,
                fn: function() {
                    window.getHintPropertyByCurrentUser({});
                }
            }
        });
    }

    var value = window.getHintPropertyByCurrentUser();
    if (value) {
        callback(value);
    } else {
        koutils.subscribeOnce(window.getHintPropertyByCurrentUser, function(value) {
            if (value) {
                callback(value);
            }
        }, this);
    }
}

ko.components.register("help", {
    viewModel: function(params) {
        kocomponents.initializeParameters.call(this, params);
        var self = this;

        self.containerZIndex = ko.observable(0);
        self.labelZIndex = ko.observable(0);

        // private methods

        this._createTooltip = _.bind(function(text) {
            if (!this.tooltip) {
                this.tooltip = new YAHOO.widget.Tooltip(this.id + "-tooltip", {
                    showDelay: 250,
                    hideDelay: 250,
                    xyoffset: [5, 0],
                    text: text,
                    autodismissdelay: 10000,
                    context: [ this.id ]
                });

                this.tooltip.cfg.addProperty("forceVisible", { value: false });
                this.tooltip.body.setAttribute("style", "white-space: pre-wrap;");

                this.tooltip.contextMouseOverEvent.subscribe(function() {
                    var parent = $("#" + self.id).closest(".yui-panel-container"),
                        zindex = parent.css("z-index") ? parseInt(parent.css("z-index")) + 1 : 10;

                    self.containerZIndex(zindex + 1);
                    self.labelZIndex(zindex - 1);

                    self.tooltip.cfg.setProperty("zIndex", zindex);
                }, this);
            }
        }, this);

        // public methods
        this.onclick = function(data, event) {
            var tooltip = data.tooltip, cfg = tooltip ? tooltip.cfg : null;
            if (cfg) cfg.setProperty("forceVisible",  !cfg.getProperty("forceVisible"));
        };

        // subscriptions
        this.text.subscribe(function(newValue) {
            if (newValue) this._createTooltip(newValue);
            if (this.tooltip) {
                this.tooltip.cfg.setProperty("text", newValue);
                this.tooltip.cfg.setProperty("disabled", !newValue);
            }
        }, this);

        // create tooltip if text already calculated
        this._createTooltip(this.text());

        var func = function (json) {
            var showHintValue = json.results[0].attributes["org:showHints"];
            if (showHintValue === "false") {
                self.tooltip.cfg.setProperty("disabled", true);
            }
        }
        getHintPropertyByCurrentUser(func);
    },
    template: '\
        <span data-bind="style: { \'z-index\': containerZIndex, position: \'relative\' }, attr: { id: id }, if: text, click: onclick">\
            <span data-bind="style: { \'z-index\': labelZIndex, position: \'relative\' }">?</span>\
        </span>'
});

// ---------------
// SELECT
// ---------------

ko.components.register("select", {
    viewModel: function(params) {
        kocomponents.initializeParameters.call(this, params);

        if (this.data.single()) {
            if (!this.valueAllowUnset) {
                koutils.subscribeOnce(this.data.singleValue, function(newValue) {
                    if (!_.contains(this.options(), newValue)) this.newValue(null);
                }, this.data)
            }
        }

        if (this.throttle) this.data.options.extend({ throttle: 500 });

        if (!this.optionsText) {
            if (this.data.optionsText) { this.optionsText = this.data.optionsText; }
            else { this.optionsText = function(option) { return this.getValueTitle(option); }.bind(this.data) };
        }

        if (!this.optionsValue && this.data.optionsValue) {
            this.optionsValue = this.data.optionsValue;
        }

        if (!this.optionsAfterRender && this.data.optionsAfterRender) {
            this.optionsAfterRender = this.data.optionsAfterRender;
        }
    },
    template:
       '<!--ko ifnot: data.multiple -->\
            <select data-bind="attr: { id: id },\
                disable: data.protected,\
                options: data.options,\
                optionsCaption: optionsCaption, optionsText: optionsText, optionsValue: optionsValue,\
                optionsAfterRender: optionsAfterRender,\
                valueAllowUnset: valueAllowUnset,\
                value: data.singleValue\
            "></select>\
        <!-- /ko -->\
        <!-- ko if: data.multiple -->\
            <select data-bind="attr: { id: id, multiple: data.multiple },\
                disable: data.protected,\
                options: data.options,\
                optionsCaption: optionsCaption, optionsText: optionsText, optionsValue: optionsValue,\
                optionsAfterRender: optionsAfterRender,\
                valueAllowUnset: valueAllowUnset,\
                selectedOptions: data.multipleValues\
            "></select>\
        <!-- /ko -->'
});

// ---------------
// NUMBER-GENERATE
// ---------------

ko.components.register("number-generate", {
    viewModel: function(params) {

        var self = this;
        this.id = params.id;
        this.label = params.label || "Generate";
        this.mode = params.mode;
        this.disable = params.disable;
        this.node = params.node;

        this.isButtonMode = this.mode == "button";
        this.isCheckboxMode = this.mode == "checkbox";

        if (_.isFunction(params.template)) {
            this.numTemplate = ko.computed(params.template.bind(this));
        } else {
            this.numTemplate = ko.observable(params.template);
        }
        this._cache = {
            numbers: {}
        };

        if (params.flagOn == 'true') {
            this.flag = ko.observable(true);
            Dom.setAttribute(self.id, "disabled");
        } else {
            this.flag = ko.observable(false);
        }

        if (params.generateOff == 'true') {
            this.flag.subscribe(function (num) {
                var input = Dom.get(self.id);
                if (!num || (!isNaN(num) && num < 0)) {
                    if (input) {
                        input.removeAttribute("disabled");
                        params.value('');
                    }
                } else {
                    if (input && self.isCheckboxMode) {
                        Dom.setAttribute(self.id, "disabled", "disabled");
                        params.value(' ');
                    }
                }
            });
        } else {
            this.generatedNumber = ko.computed(function() {
                if (!self.flag()) {
                    return -1;
                }
                var template = self.numTemplate();
                if (!template) {
                    return -1;
                }
                if (!self._cache.numbers[template]) {
                    self._cache.numbers[template] = ko.computed(function() {
                        return params.enumeration.getNumber(template,  params.node());
                    });
                }
                return self._cache.numbers[template]();
            });

            this.generatedNumber.subscribe(function (num) {
                var input = Dom.get(self.id);

                if (!num || (!isNaN(num) && num < 0)) {
                    if (input) input.removeAttribute("disabled");
                } else {
                    params.value(num);
                    if (input && self.isCheckboxMode) Dom.setAttribute(self.id, "disabled", "disabled");
                }
            });
        }
    },
    template:
       '<!-- ko if: isButtonMode -->\
            <button data-bind="text: label, disable: disable, click: flag"></button>\
        <!-- /ko -->\
        <!-- ko if: isCheckboxMode -->\
            <input style="position: relative; top: 2px;" type="checkbox" name="number-generate" data-bind="checked: flag" />\
             <label style="margin-left: 10px;" data-bind="text: label"></label>\
         <!-- /ko -->\
         '
});

// ---------------
// NUMBER
// ---------------

ko.bindingHandlers.numericKeyInput = {
    init: function (element) {
        $(element).on('keydown', function (event) {
            var keyCode = event.keyCode;
            var allowKeyCodes = [
                46, //delete
                8, //backspace
                9, //tab
                27, //escape
                13, //enter
                188, //comma
                190, //period
                110, //decimal point
                35,36,37,38,39 //end, home, left arrow, up arrow, right arrow
            ];

            if (allowKeyCodes.includes(keyCode) || (keyCode === 65 && event.ctrlKey === true)) {
                return null;
            } else if (event.shiftKey || (keyCode < 48 || keyCode > 57) && (keyCode < 96 || keyCode > 105)) {
                event.preventDefault();
            }
        });
    }
};

ko.components.register("number", {
    viewModel: function(params) {
        var self = this;

        this.id = params.id;
        this.step = params.step && _.isNumber(params.step) ? params.step : "any";
        this.disable = params.disable;
        this.isInteger = params.isInteger == true;
        this.value = params.value;

        this.textInputValue = ko.observable(this.value());

        this.textInputValue.subscribe(function(value){
            var floatVal = parseFloat(value);
            if (value === "" || _.isFinite(floatVal) && floatVal != self.value()) {
                self.value(value);
            }
        });

        self.value.subscribe(function(value) {
            var existing = parseFloat(self.textInputValue());
            if (_.isFinite(value) && value != existing) {
                self.textInputValue(value);
            }
        });

        this.validation = function(data, event) {
            var newValue = document.getElementById(self.id).value + event.key;
            var keyCode = event.keyCode;
            var allowKeyCodes = [
                46, //delete
                8, //backspace
                9, //tab
                27, //escape
                13, //enter
                116, //f5
                35,36,37,38,39 //end, home, left arrow, up arrow, right arrow
            ];

            if (allowKeyCodes.includes(keyCode) ||
                ((keyCode === 65 || keyCode === 67 || keyCode === 86 || keyCode === 88) && event.ctrlKey === true) ) {
                return true;
            }

            newValue = newValue.replace(',', '.');

            if (self.isInteger) {
                var regExp = /^[0-9]*$/;
                return regExp.test(newValue);
            }
            if (newValue && isFinite(newValue)) {
                return true;
            }
            return false;
        };
    },
    template:
       '<input type="number" onfocus="this.focused=true;" onblur="this.focused=false;" data-bind="textInput: textInputValue, disable: disable, attr: { id: id, step: step }, event: { keydown: validation }" />'
});

// ---------------
// TASK-BUTTONS
// ---------------

ko.components.register("task-buttons", {
        viewModel: function(params) {
            var self = this;

            require(['citeck/utils/knockout.utils'], function(koutils) {

                self.buttons = params["buttons"] || [];
                self.buttons = self.buttons.map(function (button) {
                    button.title = Alfresco.util.message(button.title);
                    return button;
                });
                self.attribute = params["attribute"];
                self.node = self.attribute.node();

                self.onClick = function (item) {
                    if (item.actionId) {
                        var redirect = item.redirect ? item.redirect : (window.location.pathname.indexOf("card-details") == -1 ? "/share/page/journals2/list/tasks" : null),
                            onRedirect = function () {
                                if (redirect) {
                                    window.location = redirect;
                                } else {
                                    YAHOO.Bubbling.fire("metadataRefresh");
                                }
                            };
                        if (item.actionId == "submit") {
                            if (self.attribute.value() != item.value) {
                                self.attribute.value(item.value);
                                koutils.subscribeOnce(self.attribute.jsonValue, function () {
                                    self.node.thisclass.save(self.node, onRedirect);
                                });
                            } else {
                                self.node.thisclass.save(self.node, onRedirect);
                            }
                        } else {
                            onRedirect();
                        }
                    }
                };
                self.disabled = ko.computed(function() {
                    return self.attribute.resolve("protected") || self.node.resolve("impl.invalid");
                });
            });
        },
        template:
            '<!-- ko foreach: buttons -->\
                <button data-bind="text: $data.title, click: $component.onClick, disable: $component.disabled" />\
            <!-- /ko -->'
    });

// ---------------
// CUSTOM-ACTION-BUTTON
// ---------------

ko.components.register("custom-action-button", {
        viewModel: function (params) {
            kocomponents.initializeParameters.call(this, params);
            var self = this;

            require(['citeck/utils/knockout.utils'], function (koutils) {
                self.buttonTitle = Alfresco.util.message(params["buttonTitle"]);
                self.node = self.attribute.node();

                self.onClick = function (item) {
                    var redirect = "/share/page/card-details?nodeRef=" + self.node.nodeRef;
                    var onRedirect = function () {
                        window.location = redirect;
                    };

                    var jsonData = '{"outcome": "' + self.outcome + '", "taskType": "' + self.taskType + '"}';
                    if (self.value() != jsonData) {
                        self.value(jsonData);
                        koutils.subscribeOnce(self.value, function () {
                            self.node.thisclass.save(self.node, onRedirect);
                        });
                    } else {
                        self.node.thisclass.save(self.node, onRedirect);
                    }
                };
                self.disabled = ko.computed(function() {
                    var protected = self.attribute.resolve("protected");
                    var isSubmitReady = self.attribute.resolve("node.impl.runtime.isSubmitReady");
                    return !isSubmitReady || protected;
                });
            });
        },
        template:
            '<button data-bind="text: $data.buttonTitle, click: $component.onClick, disable: $component.disabled" />'
    });

// ---------------
// FREE-CONTENT
// ---------------

ko.components.register("free-content", {
    viewModel: function(params) {
        var self = this;
        this.func = params.func;

        if (!this.func) {
            throw Error('Parameter "func" should by specified')
        }

        this.content = ko.computed(function() {
            var result = self.func();
            if (result instanceof HTMLElement) return result.outerHTML;
            if (typeof result == "string") return result;

            throw Error('Parameter "func" should return a String or an HTMLElement');
            return null;
        });
    },
    template:
       '<div data-bind="html: content">'
})

// ---------------
// MULTIPLE-TEXT
// ---------------

ko.components.register("multiple-text", {
    viewModel: function(params) {
        this.value = params["value"];
        this.title = params["title"];
        this.disabled = params["disabled"];
        var self = this;

        this.strings = ko.observableArray([]);

        var String = function(stringValue) {
            this.stringValue = ko.observable(stringValue);

            this.stringValue.subscribe(function(newValue) {
                var stringArray = [];
                if (self.strings().length) {
                    for (var i in self.strings()) {
                        if (self.strings()[i].stringValue()) stringArray.push(self.strings()[i].stringValue());
                    }
                }
                self.value(stringArray)
            });
        };

        koutils.subscribeOnce(this.value, function(newValue) {
            var stringsArray = [];
            if (newValue && newValue.length) {
                if (newValue instanceof Array) {
                    for (var i in newValue) {
                        stringsArray.push(new String(newValue[i]));
                    }
                } else {
                    stringsArray.push(new String(newValue));
                }
                self.strings(stringsArray);
            }
        });

        this.removeString = function(data) {
            self.strings.remove(data);
        };

        this.addString = function() {
            self.strings.push(new String(''));
        };

        this.strings.subscribe(function(newArray) {
            var stringArray = [];
            if (newArray.length) {
                for (var i in newArray) {
                    if (newArray[i].stringValue()) stringArray.push(newArray[i].stringValue());
                }
            }
            self.value(stringArray)
        });

    },
    template:
        '<div class="multiple-text-control">\
            <button class="multiple-text-control-button" data-bind="text: title, click: addString, disable: disabled"></button>\
            <div data-bind="foreach: strings">\
                    <input type="text" data-bind="value: stringValue, disable: $component.disabled" />\
                    <!-- ko ifnot: $component.disabled -->\
                        <a href="#" data-bind="click: $component.removeString"> x </a>\
                    <!-- /ko -->\
            </div>\
        </div>'
});

// ---------------
// CHECKBOX
// ---------------

ko.components.register("checkbox-radio", {
    viewModel: function(params) {
        var self = this;

        this.groupName = params["groupName"];
        this.optionText = params["optionText"];

        this.options = params["options"];
        this.optionsDisabled = params["optionsDisabled"] ? params["optionsDisabled"]() : null;
        this.optionsAndOptionsDisabled = ko.observable([]);

        this.options.subscribe(function(){
            var optionsAndOptionsDisabledTMP = [];
            var disabledFlag;
            if (self.options() && self.options().length > 0) {
                for (var oIndex in self.options()) {
                    disabledFlag = false;
                    if (self.optionsDisabled != null) {
                        for (var odIndex in self.optionsDisabled()) {
                            if (self.optionsDisabled()[odIndex] === self.options()[oIndex].nodeRef) {
                                disabledFlag = true;
                            }
                        }
                    }
                    optionsAndOptionsDisabledTMP.push({element: self.options()[oIndex], disabled: disabledFlag});
                }
                self.optionsAndOptionsDisabled(optionsAndOptionsDisabledTMP);
            }
        });

        this.value = params["value"];
        this.multiple = params["multiple"] || false;
        this.disabled = params["protected"];
    },
    template:
        '<!-- ko foreach: options -->\
            <span class="checkbox-option">\
                <label>\
                    <!-- ko if: $parent.multiple -->\
                        <!-- ko if: $parent.disabled -->\
                            <input type="checkbox" disabled data-bind="checked: ko.computed({\
                                read: function() { if ($parent.value()) return $parent.value().indexOf($data) != -1; },\
                                write: function(newValue) {\
                                    var selectedOptions = $parent.value() || [];\
                                    newValue ? selectedOptions.push($data) : selectedOptions.splice(selectedOptions.indexOf($data), 1);\
                                    $parent.value(selectedOptions);\
                                }\
                            })" />\
                        <!-- /ko -->\
                        <!-- ko ifnot: $parent.disabled -->\
                            <!-- ko if: $parent.optionsAndOptionsDisabled() -->\
                                <!-- ko if: $parent.optionsAndOptionsDisabled()[$index()] -->\
                                    <!-- ko if: $parent.optionsAndOptionsDisabled()[$index()].disabled == true -->\
                                        <input type="checkbox" disabled data-bind="checked: ko.computed({\
                                            read: function() { if ($parent.value()) return $parent.value().indexOf($data) != -1; },\
                                            write: function(newValue) {\
                                                var selectedOptions = $parent.value() || [];\
                                                newValue ? selectedOptions.push($data) : selectedOptions.splice(selectedOptions.indexOf($data), 1);\
                                                $parent.value(selectedOptions);\
                                            }\
                                        })" />\
                                    <!-- /ko -->\
                                    <!-- ko ifnot: $parent.optionsAndOptionsDisabled()[$index()].disabled == true -->\
                                        <input type="checkbox" data-bind="checked: ko.computed({\
                                            read: function() { if ($parent.value()) return $parent.value().indexOf($data) != -1; },\
                                            write: function(newValue) {\
                                                var selectedOptions = $parent.value() || [];\
                                                newValue ? selectedOptions.push($data) : selectedOptions.splice(selectedOptions.indexOf($data), 1);\
                                                $parent.value(selectedOptions);\
                                            }\
                                        })" />\
                                    <!-- /ko -->\
                                <!-- /ko -->\
                                <!-- ko ifnot: $parent.optionsAndOptionsDisabled()[$index()] -->\
                                    <input type="checkbox" data-bind="checked: ko.computed({\
                                        read: function() { if ($parent.value()) return $parent.value().indexOf($data) != -1; },\
                                        write: function(newValue) {\
                                            var selectedOptions = $parent.value() || [];\
                                            newValue ? selectedOptions.push($data) : selectedOptions.splice(selectedOptions.indexOf($data), 1);\
                                            $parent.value(selectedOptions);\
                                        }\
                                    })" />\
                                <!-- /ko -->\
                            <!-- /ko -->\
                            <!-- ko ifnot: $parent.optionsAndOptionsDisabled() -->\
                                <input type="checkbox" data-bind="checked: ko.computed({\
                                    read: function() { if ($parent.value()) return $parent.value().indexOf($data) != -1; },\
                                    write: function(newValue) {\
                                        var selectedOptions = $parent.value() || [];\
                                        newValue ? selectedOptions.push($data) : selectedOptions.splice(selectedOptions.indexOf($data), 1);\
                                        $parent.value(selectedOptions);\
                                    }\
                                })" />\
                            <!-- /ko -->\
                        <!-- /ko -->\
                    <!-- /ko -->\
                    <!-- ko ifnot: $parent.multiple -->\
                        <!-- ko if: $parent.disabled -->\
                        <input type="radio" disabled data-bind="checked: ko.computed({\
                            read: function() { if ($parent.value()) return $parent.value().id; },\
                            write: function(newValue) { $parent.value($data.nodeRef); }\
                        }), attr: { value: $data.id, name: $parent.groupName }" />\
                        <!-- /ko -->\
                        <!-- ko ifnot: $parent.disabled -->\
                            <!-- ko if: $parent.optionsAndOptionsDisabled() -->\
                                <!-- ko if: $parent.optionsAndOptionsDisabled()[$index()] -->\
                                    <!-- ko if: $parent.optionsAndOptionsDisabled()[$index()].disabled == true -->\
                                        <input type="radio" disabled data-bind="checked: ko.computed({\
                                            read: function() { if ($parent.value()) return $parent.value().id; },\
                                            write: function(newValue) { $parent.value($data.nodeRef); }\
                                        }), attr: { value: $data.id, name: $parent.groupName }" />\
                                    <!-- /ko -->\
                                    <!-- ko ifnot: $parent.optionsAndOptionsDisabled()[$index()].disabled == true -->\
                                        <input type="radio"  data-bind="checked: ko.computed({\
                                            read: function() { if ($parent.value()) return $parent.value().id; },\
                                            write: function(newValue) { $parent.value($data.nodeRef); }\
                                        }), attr: { value: $data.id, name: $parent.groupName }" />\
                                    <!-- /ko -->\
                                <!-- /ko -->\
                                <!-- ko ifnot: $parent.optionsAndOptionsDisabled()[$index()] -->\
                                    <input type="radio"  data-bind="checked: ko.computed({\
                                        read: function() { if ($parent.value()) return $parent.value().id; },\
                                        write: function(newValue) { $parent.value($data.nodeRef); }\
                                    }), attr: { value: $data.id, name: $parent.groupName }" />\
                                <!-- /ko -->\
                            <!-- /ko -->\
                            <!-- ko ifnot: $parent.optionsAndOptionsDisabled() -->\
                                 <input type="radio"  data-bind="checked: ko.computed({\
                                    read: function() { if ($parent.value()) return $parent.value().id; },\
                                    write: function(newValue) { $parent.value($data.nodeRef); }\
                                 }), attr: { value: $data.id, name: $parent.groupName }" />\
                            <!-- /ko -->\
                        <!-- /ko -->\
                    <!-- /ko -->\
                    <!-- ko text: $parent.optionText($data) --><!-- /ko -->\
                </label>\
            </span>\
        <!-- /ko -->'
});

// ---------------
// DATETIME
// ---------------

ko.components.register("datetime", {
    viewModel: function(params) {
        var self = this,
            calendarAccessorId = params.fieldId + "-calendarAccessor",
            calendarDialogId = params.fieldId + "-calendarDialog", calendarContainerId = params.fieldId + "-calendarContainer",
            calendarDialog, calendar;
            localization = params.localization;

        this.mode = params.mode == "browser";
        this.fieldId = params["fieldId"];
        this.value = params["value"];
        this.disabled = params["protected"];
        this.isFocus = ko.observable(false);
        this.intermediateValue = ko.observable();
        this.dateFormat = params["dateFormat"];

        this.calendar = function() {
            if (!calendarDialog) {
                var formContainer = $("#" + this.fieldId).closest(".yui-panel-container"),
                    zindex = formContainer.css("z-index") ? parseInt(formContainer.css("z-index")) + 1 : 15;

                calendarDialog = new YAHOO.widget.Dialog(calendarDialogId, {
                    visible:    false,
                    context:    [calendarAccessorId, "tl", "bl"],
                    draggable:  false,
                    close:      true,
                    zindex:     zindex
                });
                calendarDialog.setHeader(localization.labels.header);
                calendarDialog.setBody("<div id=\"" + calendarContainerId + "\"></div>");
                calendarDialog.render(document.body);
            }

            if (!calendar) {
                calendar = new YAHOO.widget.Calendar(calendarContainerId, {
                    LOCALE_WEEKDAYS: "short",
                    LOCALE_MONTHS: "long",
                    START_WEEKDAY: 1,

                    iframe: false,
                    navigator: {
                        strings: {
                            month:  localization.labels.month,
                            year:   localization.labels.year,
                            submit: localization.buttons.submit,
                            cancel: localization.buttons.cancel
                        }
                    }
                });

                // localization months and days
                calendar.cfg.setProperty("MONTHS_LONG", localization.months.split(","));
                calendar.cfg.setProperty("WEEKDAYS_SHORT", localization.days.split(","));

                // selected date
                calendar.selectEvent.subscribe(function() {
                    if (calendar.getSelectedDates().length > 0) {
                        var selectedDate = calendar.getSelectedDates()[0],
                            nowDate = new Date;

                        selectedDate.setHours(nowDate.getHours());
                        selectedDate.setMinutes(nowDate.getMinutes());
                        self.value(selectedDate);
                    }

                    calendarDialog.hide();
                });

                calendar.render();
            }

            if (calendarDialog) calendarDialog.show();
        };

        this.textValue = ko.pureComputed({
            read: function() {
                return self.value() instanceof Date ? moment(self.value()).format(self.dateFormat) : null;
            },
            write: function(newValue) {
                if (newValue) {
                    if (/\d{2,4}-\d{2}-\d{2}(, | )\d{2}:\d{2}(:\d{2}|)/.test(newValue)) {
                        var timeArray = newValue.split(/, | /);
                        timeArray[0] = timeArray[0].split(".").reverse().join("/");

                        var newDate = new Date(timeArray.join("T"));
                        if (newDate != "Invalid Date") {
                            self.value(newDate);
                            return;
                        }
                    }
                }

                if (self.value() != null) self.value(null)
            }
        });

        this.isFocus.subscribe(function(focus) {
            if (!focus) {
                self.value(self.intermediateValue());
            }
        });

        this.dateValue = ko.computed({
            read: function() {
                return  Alfresco.util.toISO8601(self.value(), { milliseconds: false, hideTimezone: true });
            },
            write: function(newValue) {
                if (newValue) {
                    var newDate = new Date(newValue);

                    if (newDate != "Invalid Date") {
                        self.intermediateValue(newDate);
                        return;
                    }
                }

                if (self.intermediateValue() != null) {
                    self.intermediateValue(null);
                }
            }
        });
    },
    template:
       '<input type="text" data-bind="value: textValue, disable: disabled, attr: { placeholder: localization.placeholderFormatIE }" />\
        <!-- ko if: disabled -->\
            <img src="/share/res/components/form/images/calendar.png" class="datepicker-icon">\
        <!-- /ko -->\
        <!-- ko ifnot: disabled -->\
            <a class="calendar-link-button" data-bind="disable: disabled, click: calendar, clickBubble: false, attr: { id: fieldId + \'-calendarAccessor\' }">\
                <img src="/share/res/components/form/images/calendar.png" class="datepicker-icon">\
            </a>\
        <!-- /ko -->'
});

// ---------------
// DATE
// ---------------

ko.bindingHandlers.dateControl = {
    init: function(element, valueAccessor, allBindings) {
        var value = valueAccessor(),
            params = allBindings();

        var localization = params.localization,
            mode = params.mode,
            min = params.min,
            max = params.max;

        var elementId = element.id.replace("-dateControl", ""),
            input = Dom.get(elementId);

        if (input) {
            input.addEventListener("change", function() {
                if (!input.value) {
                    value(null);
                }
            });
        }

        var calendarDialogId = elementId + "-calendarDialog",
            calendarContainerId = elementId + "-calendarContainer",
            calendarAccessorId = elementId + "-calendarAccessor",
            calendarDialog, calendar;

        var showCalendarButton = document.getElementById(calendarAccessorId);
        showCalendarButton.classList.remove("hidden");

        Event.on(showCalendarButton, "click", function() {
            if (!calendarDialog) {
                var formContainer = $(element).closest(".yui-panel-container"),
                    zindex = formContainer.css("z-index") ? parseInt(formContainer.css("z-index")) + 1 : 15;

                calendarDialog = new YAHOO.widget.Dialog(calendarDialogId, {
                    visible:    false,
                    context:    [calendarAccessorId, "tl", "bl", ["beforeShow", "windowResize"], [-210,  5]],
                    draggable:  false,
                    close:      true,
                    zindex:     zindex
                });
                calendarDialog.setHeader(localization.labels.header);
                calendarDialog.setBody("<div id=\"" + calendarContainerId + "\"></div>");
                calendarDialog.render(document.body);
            }

            if (!calendar) {
                calendar = new YAHOO.widget.Calendar(calendarContainerId, {
                    LOCALE_WEEKDAYS: "short",
                    LOCALE_MONTHS: "long",
                    START_WEEKDAY: 1,

                    iframe: false,
                    navigator: {
                        strings: {
                            month:  localization.labels.month,
                            year:   localization.labels.year,
                            submit: localization.buttons.submit,
                            cancel: localization.buttons.cancel
                        }
                    }
                });

                // localization months and days
                calendar.cfg.setProperty("MONTHS_LONG", localization.months.split(","));
                calendar.cfg.setProperty("WEEKDAYS_SHORT", localization.days.split(","));

                // selected date
                calendar.selectEvent.subscribe(function() {
                    if (calendar.getSelectedDates().length > 0) {
                        var selectedDate = calendar.getSelectedDates()[0];

                        value(selectedDate);
                    }
                    calendarDialog.hide();
                });

                calendar.render();
            }

            if (calendarDialog) calendarDialog.show();
        });

        if (mode != "alfresco" && Citeck.HTML5.supportedInputTypes.date) {
            // set max and min attributes
            var date = new Date(),
                year = date.getFullYear();

            if (input) {
                if (max) {
                    input.setAttribute("max", max);
                } else {
                    input.setAttribute("max", (year + 50) + "-12-31");
                }

                if (min) {
                    input.setAttribute("min", min);
                } else {
                    input.setAttribute("min", (year - 25) + "-12-31");
                }

                Dom.setStyle(input, "color", value() ? "" : "lightgray");
                value.subscribe(function(value) {
                    Dom.setStyle(input, "color", value ? "" : "lightgray");
                });
            }
        }
    }
};

ko.components.register('dadata-loader', {
    viewModel: function (params) {
        var that = this;
        var attributes = params.attributes || {};
        var impl = params.runtime.node().impl();
        this.isEnterpriseInstalled = ko.observable(false);

        fetch(Alfresco.constants.PROXY_URI + '/citeck/info/is-ent-installed',{
            method: 'GET',
            credentials: 'include',
            headers: {'Content-type': 'application/json;charset=UTF-8'}
        }).then(function(response) { return response.json();}).then(function (response){
            that.isEnterpriseInstalled(response.isEntInstalled == 'true');
        });

        that.tooltip = Alfresco.util.message('dadata.loader.tooltip');
        that.text = Alfresco.util.message('dadata.loader.text');

        that.onClick = function(){
            var inn = impl.getAttribute('idocs:inn').value();

            if (inn) {
                fetch('/micro/integrations/records/query',{
                    method: 'POST',
                    credentials: 'include',
                    headers: {'Content-type': 'application/json;charset=UTF-8'},
                    body: JSON.stringify({
                        "query": {
                            "sourceId": "dadata-party",
                            "query":{"tin": inn},
                            "attributes": {"subjectName": "subjectName"},
                            "main": true
                        }})
                }).then(function(response) { return response.json();}).then(function (response){
                    var needAsk = false;

                    response = (response.suggestions || [])[0];

                    if (response) {
                        for (var attribute in attributes) {
                            var formAttributeValue = impl.getAttribute(attribute).value();

                            if (formAttributeValue && formAttributeValue !== attributes[attribute](response)) {
                                needAsk = true;
                                break;
                            }
                        }

                        var rewrite = function() {
                            var newValue;

                            for (var attribute in attributes) {
                                newValue = attributes[attribute](response);
                                if (newValue) {
                                    impl.getAttribute(attribute).value(newValue);
                                }
                            }
                        };

                        needAsk
                            ?
                        Alfresco.util.PopupManager.displayPrompt({
                            title: Alfresco.util.message('dadata.loader.text'),
                            text: Alfresco.util.message('dadata.loader.confirm'),
                            noEscape: true,
                            buttons: [
                                {
                                    text: Alfresco.util.message('actions.button.ok'),
                                    handler: function() {
                                        this.hide();
                                        rewrite();
                                        this.destroy();
                                    }
                                },
                                {
                                    text: Alfresco.util.message('actions.button.cancel'),
                                    handler: function() {
                                        this.destroy();
                                    },
                                    isDefault: true
                                }
                            ]
                        })
                            :
                        rewrite();
                    }
                });
            }
        }
    },
    template: '<button class="dadata-loader_btn"  data-bind="click: onClick.bind(this), attr: { title:tooltip }, text: text, visible: isEnterpriseInstalled"></button>'
});


// -------------
// DOCUMENT-SELECT
// -------------

ko.components.register('documentSelect', {
    viewModel: function (params) {
        var that = this;
        var Dom = YAHOO.util.Dom;
        var Event = YAHOO.util.Event;
        var createContextMenu;

        var ID = params['id'];
        var CONTEXT_MENU_ID = ID + '-context-menu';
        var CONTEXT_MENU_BUTTON_ID = CONTEXT_MENU_ID + '-button';
        var JOURNAL_SELECT_ID = ID + '-journal-select';
        var JOURNAL_SELECT_CONTAINER_ID = JOURNAL_SELECT_ID + '-container';
        var JOURNAL_SELECT_BUTTON_ID = JOURNAL_SELECT_ID + '-button';
        var FILE_UPLOAD_ID = ID + '-file-upload';
        var FILE_UPLOAD_INPUT_ID = FILE_UPLOAD_ID + '-fileInput';
        var FILE_UPLOAD_BUTTON_ID = FILE_UPLOAD_ID + '-openFileUploadDialogButton';

        this.contextMenuButtonId = CONTEXT_MENU_BUTTON_ID;
        this.journalSelectContainerId = JOURNAL_SELECT_CONTAINER_ID;
        this.journalSelectId = JOURNAL_SELECT_ID;
        this.journalSelectButtonId = JOURNAL_SELECT_BUTTON_ID;
        this.fileUploadId = FILE_UPLOAD_ID;
        this.fileUploadInputId = FILE_UPLOAD_INPUT_ID;
        this.fileUploadButtonId = FILE_UPLOAD_BUTTON_ID;

        this.value = params.value;
        this.journalValue = ko.observable();
        this.multiple = params.multiple;
        this.options = params.options;
        this.protected = params.protected;
        this.maxCount = params.maxCount;
        this.maxSize = params.maxSize;
        this.alowedFileTypes = params.alowedFileTypes;
        this.type = params.type;
        this.info = params.info;
        this.destinationType = params.destinationType;
        this.destination = this.destinationType === 'USER_FOLDER' ? '/app:company_home/app:user_homes/cm:' + Alfresco.constants.USERNAME : params.destination;
        this.assocType = params.assocType || (this.destinationType === 'USER_FOLDER' ? 'cm:contains' : '');
        this.journalId = ko.observable();
        this.createVariantsVisibility = params.createVariantsVisibility;
        this.journalSelectButtonText = Alfresco.util.message('journal.select-button');
        this.journalLoadButtonText = Alfresco.util.message('journal.load-button');
        this.visibleJournalButton = false;
        this.nodetype = ko.observable();
        this.filterOptions = function (criteria, pagination) {
            if (!this.cache) this.cache = {};

            if (!this.cache.result) {
                this.cache.result = ko.observable([]);
                this.cache.result.extend({notify: 'always'});
            }

            var query = {
                skipCount: 0,
                maxItems: 10
            };

            if (!_.find(criteria, function (criterion) {
                return criterion.predicate === 'journal-id';
            })
            ) {
                if (!this.nodetype()) {
                    return [];
                }

                query['field_1'] = 'type';
                query['predicate_1'] = 'type-equals';
                query['value_1'] = this.nodetype();
            }

            if (pagination) {
                if (pagination.maxItems) query.maxItems = pagination.maxItems;
                if (pagination.skipCount) query.skipCount = pagination.skipCount;
            }

            _.each(criteria, function (criterion, index) {
                query['field_' + (index + 2)] = criterion.attribute;
                query['predicate_' + (index + 2)] = criterion.predicate;
                query['value_' + (index + 2)] = criterion.value;
            });

            if (this.cache.query) {
                if (_.isEqual(query, this.cache.query)) return this.cache.result();
            }

            this.cache.query = query;
            if (_.some(_.keys(query), function (p) {
                return _.some(['field', 'predicate', 'value'], function (ci) {
                    return p.indexOf(ci) !== -1;
                });
            })
            ) {
                Alfresco.util.Ajax.jsonPost({
                    url: Alfresco.constants.PROXY_URI + 'search/criteria-search',
                    dataObj: query,
                    successCallback: {
                        scope: this.cache,
                        fn: function (response) {
                            var result = _.map(response.json.results, function (node) {
                                return new Node(node);
                            });
                            result.pagination = response.json.paging;
                            result.query = response.json.query;
                            this.result(result);
                        }
                    }
                });
            }

            return this.cache.result();
        };

        this.journalValue.subscribe(function(journalValue) {
            var currentValue = that.value() || [];
            if(journalValue){
                that.value(currentValue.concat(journalValue));
                that.journalValue(null);
            }
        });

        createContextMenu = function (params) {
            var siteId = params['siteId'];
            var addable = params['addable'];

            var assocTypeMenu = new YAHOO.widget.ContextMenu(
                CONTEXT_MENU_ID,
                {
                    trigger: CONTEXT_MENU_BUTTON_ID,
                    lazyLoad: true
                }
            );

            var onMenuItemClick = function (journalId) {
                that.journalId(journalId);
                $('#' + JOURNAL_SELECT_BUTTON_ID).click();
            };

            var getMenuItemsByAddable = function (addable, sites) {
                var menuItems = [];
                var assoc;
                var type;
                var text;

                for (var j = 0; j < addable.length; j++) {
                    assoc = addable[j];
                    type = assoc.name;

                    if (type !== '') {
                        if (assoc.direction === 'both' || assoc.direction === 'target' || assoc.direction === 'undirected') {
                            text = Alfresco.util.message('association.' + type.replace(':', '_') + '.target');
                        }
                        if (assoc.direction === 'source') {
                            text = Alfresco.util.message('association.' + type.replace(':', '_') + '.source');
                        }

                        if (text) {
                            menuItems.push({
                                text: text,
                                submenu: getSubmenu(type, sites)
                            });
                        }
                    }
                }

                return menuItems;
            };

            var getSiteItems = function (sites, parentId) {

                parentId = parentId ? ('-' + parentId) : '';

                var menuItems = sites.map(function (item) {
                    var siteId = item.siteId + parentId;

                    return {
                        text: item.siteName,
                        submenu: {
                            id: siteId,
                            itemdata: getJournalsItemdata(item.journals, siteId)
                        }
                    }
                })

                return menuItems;
            };

            var getMenuItemsBySiteId = function (siteId, sites) {
                var menuItems = [];
                var site = sites.filter(function (site) {
                    return site.siteId === siteId;
                })[0];

                if (site) {
                    menuItems = getJournalsItemdata(site.journals, siteId);
                }

                return menuItems;
            };

            var getJournalsItemdata = function (journals, parentId) {
                journals = journals || [];

                parentId = parentId ? ('-' + parentId) : '';

                return journals.map(function (journal) {
                    return {
                        id: ID + parentId + '-' + journal.journalId,
                        text: journal.journalName,
                        onclick: {fn: onMenuItemClick.bind(this, journal.journalId)}
                    }
                })
            };

            var getSubmenu = function (type, sites) {
                var submenu = {
                    id: type.replace(':', '_'),
                    itemdata: getSiteItems(sites, type.replace(':', '-'))
                };

                return submenu;
            };

            YAHOO.util.Connect.asyncRequest(
                'GET',
                Alfresco.constants.PROXY_URI + 'citeck/cardlets/sites-and-journals',
                {
                    success: function (response) {
                        if (response.responseText) {
                            var data = eval('(' + response.responseText + ')');
                            var menuItems = [];
                            var sites;

                            if (data && data.sites && data.sites.length) {
                                sites = data.sites;

                                if (addable.length) {
                                    menuItems = getMenuItemsByAddable(addable, sites);
                                } else if (siteId) {
                                    menuItems = getMenuItemsBySiteId(siteId, sites);
                                } else {
                                    menuItems = getSiteItems(sites);
                                }

                                assocTypeMenu.addItems(menuItems);
                                assocTypeMenu.render(CONTEXT_MENU_BUTTON_ID);
                            }
                        }
                    },
                    failure: function () {
                        var messageEl = Dom.get(this.id + "-message");
                        messageEl.innerHTML = Alfresco.util.message("assocs-load-error");
                    },
                    scope: this
                }
            );

            Event.addListener(
                CONTEXT_MENU_BUTTON_ID,
                'click', function (event) {
                    var xy = YAHOO.util.Event.getXY(event);
                    assocTypeMenu.cfg.setProperty('xy', xy);
                    assocTypeMenu.show();
                }
            );
        };

        createContextMenu(params);
    },
    template: '\
        <a data-bind = "attr: { id: contextMenuButtonId }, text: journalSelectButtonText, disable: protected" class="context-menu-button" ></a>\
        <button data-bind = "attr: { id: fileUploadButtonId }, text: journalLoadButtonText, disable: protected" class="file-upload-open-dialog-button" ></button>\
        <button data-bind="attr: { id: journalSelectButtonId }, text: journalSelectButtonText, visible: visibleJournalButton, disable: protected"></button>\
        <div data-bind = "attr: { id: journalSelectContainerId }" >\
            <div data-bind = \'\
                attr: { id: journalSelectId },\
                journalControl: { value: journalValue, multiple: multiple, options: options }, params: function() {\
                    return {\
                        mode: "collapse",\
                        hightlightSelection: true,\
                        removeSelection: true,\
                        createVariantsSource: "journal-create-variants",\
                        createVariantsVisibility: createVariantsVisibility\
                    }\
                }\
            \'></div>\
        </div>\
        <input data-bind="attr: { id: fileUploadInputId, multiple: multiple }" class="hidden" type="file" />\
        <div data-bind=\'attr: { id: fileUploadId }, fileUploadControl: {\
            type: type,\
            multiple: multiple,\
            value: value,\
            maxCount: maxCount,\
            maxSize: maxSize,\
            alowedFileTypes: alowedFileTypes,\
            draggable: true,\
            destination: destination,\
            assocType: assocType\
        }\' class="file-upload-control"></div>\
    '
});

// -------------
// JOURNAL
// -------------

ko.bindingHandlers.journalControl = {
  init: function(element, valueAccessor, allBindings, data, context) {
    var self = this;
    var allowedFilterValues = data.allowedFilterValues;

    // html elements
    var button  = Dom.get(element.id + "-button"),
        panelId = element.id + "-journalPanel", panel;

    // binding variables
    var settings = valueAccessor(),
        value    = settings.value,
        multiple = settings.multiple,
        params   = allBindings().params(),
        removeSelection = params.removeSelection;
    // sorting
    var sortBy  = params.sortBy;

    // localization
    var localization = {
        title: Alfresco.util.message("form.select.label"),
        search: Alfresco.util.message("journal.search"),
        elementsTab: Alfresco.util.message("journal.elements"),
        filterTab: Alfresco.util.message("journal.filter"),
        createTab: Alfresco.util.message("journal.create"),
        selectedElements: Alfresco.util.message("journal.selected-elements"),
        applyCriteria: Alfresco.util.message("journal.apply-criteria"),
        addFilterCriterion: Alfresco.util.message("journal.add-filter-criterion"),
        submitButton: Alfresco.util.message("button.ok"),
        cancelButton: Alfresco.util.message("button.cancel"),
        nextPageLabel: Alfresco.util.message("journal.pagination.next-page-label"),
        nextPageTitle: Alfresco.util.message("journal.pagination.next-page-title"),
        previousPageLabel: Alfresco.util.message("journal.pagination.previous-page-label"),
        previousPageTitle: Alfresco.util.message("journal.pagination.previous-page-title")
    };

    // params
    var defaultVisibleAttributes    = params.defaultVisibleAttributes,
        defaultSearchableAttributes = params.defaultSearchableAttributes,
        defaultHiddenByType         = params.defaultHiddenByType,

        searchMinQueryLength        = params.searchMinQueryLength,
        searchScript                = _.contains(["criteria-search", "light-search"], params.searchScript) ? params.searchScript : "criteria-search",

        searchCriteria              = params.searchCriteria ? params.searchCriteria() : data.searchCriteria,
        defaultCriteria             = params.defaultCriteria,
        hiddenCriteria              = params.hiddenCriteria || [],
        optionsFilter               = params.optionsFilter ? params.optionsFilter() : null,
        createVariantsVisibility    = params.createVariantsVisibility,
        filterCriteriaVisibility    = ko.observable(false);

      var searchManager = {
          criterias: undefined,
          searchMinQueryLength: undefined,
          criteria: undefined,
          journalType: undefined,
          searchDom: undefined,
          store: {},

          init: function(options){
              var lastValue;

              options = options || {};
              $.extend(this, options);

              lastValue = this.getLast(this.journalType);

              this.setSearchString(lastValue);
              this.search(lastValue);
          },

          save: function(value){
              this.store[this.journalType] = value || '';
          },

          getLast: function(id){
              return this.store[id] || '';
          },

          setSearchString: function(str){
              if(this.searchDom){
                  this.searchDom.value = str;
              }
          },

          search: function(value){
              var searchValue = value ? value.trim() : value;

              this.save(searchValue);

              if (searchValue) {
                  if (this.searchMinQueryLength && searchValue.length < this.searchMinQueryLength) {
                      return false;
                  }

                  if (this.criterias && this.criterias.length > 0) {
                      this.criteria(_.map(this.criterias, function (item) {
                          return _.defaults(_.clone(item), {value: searchValue});
                      }));
                  } else {
                      this.criteria([{attribute: 'cm:name', predicate: 'string-contains', value: searchValue}]);
                  }
              } else {
                  if (this.criterias && this.criterias.length > 0) {
                      this.criteria(_.filter(this.criterias, function (item) {
                          return (item && item.value && item.predicate && item.attribute);
                      }));
                  } else {
                      this.criteria([]);
                  }
              }
          }
      };

    if (defaultVisibleAttributes) {
        defaultVisibleAttributes = _.map(defaultVisibleAttributes.split(","), function(item) { return trim(item) });
    }

    if (defaultSearchableAttributes) {
        defaultSearchableAttributes = _.map(defaultSearchableAttributes.split(","), function(item) { return trim(item) });
    }

    if (defaultHiddenByType) {
        defaultHiddenByType = _.map(defaultHiddenByType.split(","), function(item) { return trim(item) });
    }

    // maxItems
    var maxItems = ko.observable($("body").hasClass("mobile") ? 5 : 10);

    //  initialize criteria
    var criteria = ko.observable([]);
    if (defaultCriteria) criteria(defaultCriteria);

    var submitButtonId           = panelId + "-submitInput",
        cancelButtonId           = panelId + "-cancelInput",
        elementsTabId            = panelId + "-elementsTab",
        elementsPageId           = panelId + "-elementsPage",
        filterTabId              = panelId + "-filterTab",
        filterPageId             = panelId + "-filterPage",
        createTabId              = panelId + "-createTab",
        createPageId             = panelId + "-createPage",
        journalId                = panelId + "-elementsTable",
        selectedJournalId        = panelId + "-selectedElementsTable",
        searchId                 = panelId + "-search",
        filterCriteriaVariantsId = panelId + "-filterCriteriaVariants",
        journalPickerHeaderId    = panelId + "-journal-picker-header",
        iframeId                 = panelId + "-iframe-for-resize-catching";

    // open dialog
    Event.on(button, "click", function(event) {
        event.stopPropagation();
        event.preventDefault();

        var showPanelOnClick = function () {
            var scope = this,
                journalTypeIdFromData = data.journalId && data.journalId() ? data.journalId() : "",
                journalTypeId = journalTypeIdFromData || params.journalType;
            if (!scope.panel || journalTypeIdFromData) {
                var journalType = journalTypeId ? new JournalType(journalTypeId) : (data.journalType || null);
                if (!journalType) { /* so, it is fail */
                }

                var journalTypeOptions = journalType.options();
                if (!journalTypeOptions) {
                    koutils.subscribeOnce(journalType.options, showPanelOnClick, this);
                    return;
                }
                var journal = journalType.journal();
                if (!journal) {
                    koutils.subscribeOnce(journalType.journal, showPanelOnClick, this);
                    return;
                }
                var attributes = journalType.attributes();
                if (!attributes) {
                    koutils.subscribeOnce(journalType.attributes, showPanelOnClick, this);
                    return;
                }

                var selectedElements = ko.observableArray(),
                    selectedFilterCriteria = ko.observableArray(),
                    loading = ko.observable(true),
                    criteriaListShow = ko.observable(false),
                    searchBar = params.searchBar ? params.searchBar == "true" : true,
                    mode = params.mode,
                    dockMode = params.dock ? "dock" : "",
                    pageNumber = ko.observable(1),
                    skipCount = ko.computed(function () {
                        return (pageNumber() - 1) * maxItems()
                    }),
                    additionalOptions = ko.observable([]),
                    options = ko.computed(function (page) {
                        var journalTypeId = data.journalId && data.journalId() || params.journalType;
                        var actualCriteria = criteria();

                        var optionsFilters;
                        var optionsFiltersTemp = [];

                        if (_.isFunction(optionsFilter)) {
                            optionsFilters = optionsFilter() || [];

                            optionsFilters.forEach(function(optionsFilter) {
                                var match = _.find(actualCriteria, function(actualCriterion) {
                                    return optionsFilter.attribute == actualCriterion.attribute;
                                });
                                if (!match) {
                                    optionsFiltersTemp.push(optionsFilter);
                                }
                            });

                            if(optionsFiltersTemp.length){
                                criteria(optionsFiltersTemp.concat(actualCriteria));
                            }
                        }

                        if (hiddenCriteria) {
                            for (var hc in hiddenCriteria) {
                                if (!_.some(actualCriteria, function (criterion) {
                                        return _.isEqual(criterion, hiddenCriteria[hc])
                                    }))
                                    actualCriteria.push(hiddenCriteria[hc]);
                            }
                        }

                        if (journalTypeId) {
                            if (!_.find(actualCriteria, function (criterion) {return criterion.predicate == 'journal-id';})) {
                                actualCriteria.push({
                                    attribute: 'path',
                                    predicate: 'journal-id',
                                    value: journalTypeId
                                });
                            }
                            if (journalTypeIdFromData) {
                                actualCriteria = actualCriteria.filter(function(item) {
                                    if (item.predicate == 'journal-id' && item.value != journalTypeId) {
                                        item.value = journalTypeId;
                                    }
                                    return item;
                                });
                            }
                        }

                        var nudeOptions = data.filterOptions(actualCriteria, {
                            maxItems: maxItems(),
                            skipCount: skipCount(),
                            searchScript: searchScript,
                            sortBy: sortBy
                        }, journalTypeId, allowedFilterValues);
                        var config = nudeOptions.pagination, result;

                        var tempAdditionalOptions = additionalOptions();
                        _.each(additionalOptions(), function (o) {
                            if (_.contains(nudeOptions, o)) {
                                var index = tempAdditionalOptions.indexOf(o);
                                tempAdditionalOptions.splice(index, 1);
                            }
                        });
                        additionalOptions(tempAdditionalOptions);

                        if (additionalOptions().length > 0) {
                            if (nudeOptions.length < maxItems()) {
                                result = _.union(nudeOptions, additionalOptions());

                                if (result.length > maxItems()) result = result.slice(0, maxItems());
                                if (maxItems() - nudeOptions.length < additionalOptions().length) config.hasMore = true;

                                result.pagination = config;
                                loading(_.isUndefined(nudeOptions.pagination));
                                return result;
                            } else {
                                if (!nudeOptions.pagination.hasMore)
                                    nudeOptions.pagination.hasMore = true;
                            }
                        }

                        loading(_.isUndefined(nudeOptions.pagination));
                        return nudeOptions;
                    });

                // reset page after new search
                criteria.subscribe(function (newValue) {
                    pageNumber(1);
                });

                // show loading indicator if page was changed
                pageNumber.subscribe(function (newValue) {
                    loading(true);
                });

                // extend notify
                criteria.extend({notify: 'notifyWhenChangesStop'});
                pageNumber.extend({notify: 'always'});
                options.extend({rateLimit: {method: 'notifyWhenChangesStop', timeout: 0}});

                // get default criteria
                var defaultCriteria = ko.computed(function () {
                    if (defaultSearchableAttributes) return journalType.attributes();
                    return journalType.defaultAttributes();
                });

                // add default criteria to selectedFilterCriteria
                koutils.subscribeOnce(ko.computed(function () {
                    selectedFilterCriteria.removeAll();
                    var dc = defaultCriteria();

                    if (defaultSearchableAttributes) {
                        var validAttributes = [];
                        for (var i = 0; i < dc.length; i++) {
                            if (defaultSearchableAttributes.indexOf(dc[i].name()) != -1) validAttributes.push(dc[i]);
                        }
                        dc = validAttributes;
                    }

                    // add default option's filter criteria from view
                    if (optionsFilter) {
                        criteria(optionsFilter() && optionsFilter().length ? optionsFilter() : []);
                    }

                    if (dc) {
                        for (var i in dc) {
                            var newCriterion = _.clone(dc[i]);
                            newCriterion.value = ko.observable();
                            newCriterion.predicateValue = ko.observable();
                            if (optionsFilter && optionsFilter() && optionsFilter().length) {
                                for (var nValue in optionsFilter()) {
                                    if (newCriterion.name() == optionsFilter()[nValue].attribute) {
                                        newCriterion.predicateValue(optionsFilter()[nValue].predicate);
                                        newCriterion.value(optionsFilter()[nValue].value);
                                        break;
                                    }
                                }
                            }
                            selectedFilterCriteria.push(newCriterion);
                        }
                    }
                }), defaultCriteria.dispose);

                var optimalWidth = (function () {
                    var maxContainerWidth = screen.width - 60,
                        countOfAttributes = (function () {
                            if (defaultVisibleAttributes) return defaultVisibleAttributes.length;
                            if (journalType.defaultAttributes()) return journalType.defaultAttributes().length;
                        })();

                    if (countOfAttributes > 5) {
                        var potentialWidth = 150 * countOfAttributes;
                        return (potentialWidth >= maxContainerWidth ? maxContainerWidth : potentialWidth) + "px";
                    }

                    return "800px";
                })();

                if (!scope.panel) {
                    scope.panel = new YAHOO.widget.Panel(panelId, {
                        //width:          optimalWidth,
                        visible: false,
                        fixedcenter: true,
                        draggable: true,
                        modal: true,
                        zindex: 5,
                        close: true
                    });
                }

                // hide dialog on click 'esc' button
                scope.panel.cfg.queueProperty("keylisteners", new YAHOO.util.KeyListener(document, {keys: 27}, {
                    fn: scope.panel.hide,
                    scope: scope.panel,
                    correctScope: true
                }));

                scope.panel.setHeader(localization.title || 'Journal Picker');
                scope.panel.setBody('\
                <iframe id="' + iframeId + '" name="zFrame" style="position:absolute;z-index:-1;width:50%;height:50%;border:none;outline:none;"></iframe>\
                <div class="journal-picker-header ' + mode + ' ' + dockMode + '" id="' + journalPickerHeaderId + '">\
                    <a id="' + elementsTabId + '" class="journal-tab-button ' + (mode == "collapse" ? 'hidden' : '') + ' selected">' + localization.elementsTab + '</a>\
                    <a id="' + filterTabId + '" class="journal-tab-button">' + localization.filterTab + '</a>\
                    <!-- ko if: createVariantsVisibility -->\
                        <!-- ko component: { name: "createObjectButton", params: {\
                            scope: scope,\
                            source: createVariantsSource,\
                            callback: callback,\
                            buttonTitle: buttonTitle,\
                            virtualParent: virtualParent,\
                            journalType: journalType\
                        }} --><!-- /ko -->\
                    <!-- /ko -->\
                    ' + (searchBar ? '<div class="journal-search"><input type="search" placeholder="' + localization.search + '" class="journal-search-input" id="' + searchId + '" /></div>' : '') + '\
                </div>\
                <div class="journal-picker-page-container ' + mode + '">\
                    <div class="filter-page hidden" id="' + filterPageId + '">\
                        <div class="selected-filter-criteria-container" data-bind="if: filterCriteriaVisibility">\
                            <!-- ko component: { name: \'filter-criteria-table\',\
                                params: {\
                                    htmlId: htmlId,\
                                    itemId: itemId,\
                                    journalType: journalType,\
                                    applyCriteria: applyCriteria,\
                                    selectedFilterCriteria: selectedFilterCriteria,\
                                    defaultFilterCriteria: defaultFilterCriteria\
                                }\
                            } --> <!-- /ko -->\
                        </div>\
                        <div class="filter-criteria-actions">\
                            <ul>\
                                <li class="filter-criteria-option">\
                                    <a class="apply-criteria filter-criteria-button" data-bind="click: applyCriteria">' + localization.applyCriteria + '</a>\
                                </li>\
                                <li class="filter-criteria-option">\
                                    <a class="filter-criteria-button" data-bind="click: addFilterCriterion">' + localization.addFilterCriterion + '</a>\
                                    <div class="filter-criteria-variants" data-bind="visible: criteriaListShow">\
                                        <ul class="filter-criteria-list" data-bind="foreach: journalType.searchableAttributes">\
                                            <li class="filter-criteria-list-option">\
                                                <a class="filter-criterion" data-bind="text: displayName, click: $root.selectFilterCriterion"></a>\
                                            </li>\
                                        </ul>\
                                    </div>\
                                </li>\
                            </ul>\
                        </div>\
                    </div>\
                    <div class="elements-page" id="' + elementsPageId + '">\
                        <div class="journal-container" id="' + journalId + '">\
                            <!-- ko component: { name: \'journal\',\
                                params: {\
                                    sourceElements: elements,\
                                    targetElements: selectedElements,\
                                    journalType: journalType,\
                                    columns: columns,\
                                    hidden: hidden,\
                                    page: page,\
                                    loading: loading,\
                                    hightlightSelection: hightlightSelection,\
                                    afterSelectionCallback: afterSelectionCallback,\
                                    options: {\
                                        multiple: multiple,\
                                        pagination: true,\
                                        localization: {\
                                            nextPageLabel: "' + localization.nextPageLabel + '",\
                                            nextPageTitle: "' + localization.nextPageTitle + '",\
                                            previousPageLabel: "' + localization.previousPageLabel + '",\
                                            previousPageTitle: "' + localization.previousPageTitle + '"\
                                        }\
                                    },\
                                }\
                            } --><!-- /ko -->\
                        </div>\
                        <!-- ko if: dock -->\
                            <div class="journal-capture">' + localization.selectedElements + '</div>\
                            <div class="journal-container selected-elements" id="' + selectedJournalId + '">\
                                <!-- ko component: { name: \'journal\',\
                                    params: {\
                                        sourceElements: selectedElements,\
                                        journalType: journalType,\
                                        columns: columns\
                                    }\
                                } --><!-- /ko -->\
                            </div>\
                        <!-- /ko -->\
                    </div>\
                    <div class="create-page hidden" id="' + createPageId + '"></div>\
                </div>\
            ');
                scope.panel.setFooter('\
                <div class="buttons">\
                    <input type="submit" value="' + localization.submitButton + '" id="' + submitButtonId + '">\
                    <input type="button" value="' + localization.cancelButton + '" id="' + cancelButtonId + '">\
                </div>\
            ');

                scope.panel.render(document.body);

                // panel submit and cancel buttons
                Event.on(submitButtonId, "click", function (event) {
                    if (selectedElements() && selectedElements().length) {
                        value(removeSelection
                            ? (multiple() ? (value() ? value().concat(selectedElements()) : selectedElements()) : selectedElements())
                            : ko.utils.unwrapObservable(selectedElements))
                    }
                    scope.panel.hide();
                }, {selectedElements: removeSelection ? value() : selectedElements, panel: scope.panel}, true);

                Event.on(cancelButtonId, "click", function (event) {
                    this.hide();
                }, scope.panel, true);

                scope.panel.hideEvent.subscribe(function () {
                    if (removeSelection) {
                        selectedElements([]);
                    }
                });

                // tabs listener
                Event.on(journalPickerHeaderId, "click", function (event) {
                    event.stopPropagation();

                    var filterTab = Dom.get(filterTabId),
                        elementsTab = Dom.get(elementsTabId);

                    var filterPage = Dom.get(filterPageId),
                        elementsPage = Dom.get(elementsPageId),
                        createPage = Dom.get(createPageId);

                    if (event.target.tagName == "A") {
                        if ($(event.target).hasClass("journal-tab-button")) {
                            if (event.target.id == filterTabId) filterCriteriaVisibility(!filterCriteriaVisibility());

                            switch (mode) {
                                case "full":
                                    $(event.target)
                                        .addClass("selected")
                                        .parent()
                                        .children()
                                        .filter(".selected:not(#" + event.target.id + ")")
                                        .removeClass("selected");

                                    var pageId = event.target.id.replace(/Tab$/, "Page"),
                                        page = Dom.get(pageId);

                                    $(page)
                                        .removeClass("hidden")
                                        .parent()
                                        .children()
                                        .filter("div:not(#" + pageId + ")")
                                        .addClass("hidden");

                                    $("button.selected", $(event.target).parent())
                                        .removeClass("selected");

                                    break;

                                case "collapse":
                                    // switch page if elements hidden
                                    if ($(elementsPage).hasClass("hidden")) {
                                        $(createPage).addClass("hidden");
                                        $(elementsPage).removeClass("hidden");
                                    }

                                    // clear tab selection
                                    var buttons = Dom.getElementsBy(function (element) {
                                        return element.className.indexOf("selected") != -1
                                    }, "button", journalPickerHeaderId);

                                    _.each(buttons, function (element) {
                                        element.classList.remove("selected");
                                    });

                                    $(filterTab).toggleClass("selected");
                                    $(filterPage).toggleClass("hidden");
                                    break;
                            }
                        }
                    }
                })

                // search listener
                if (searchBar) {
                    var searchDom = Dom.get(searchId);

                    searchManager.init({
                        criterias: _.isFunction(searchCriteria) ? searchCriteria() : searchCriteria,
                        searchMinQueryLength: searchMinQueryLength,
                        criteria: criteria,
                        journalType: data.journalId && data.journalId() || params.journalType,
                        searchDom: searchDom
                    });

                    Event.on(searchId, "keypress", function (event) {
                        if (event.keyCode == 13) {
                            event.stopPropagation();

                            searchManager.search(searchDom.value);
                        }
                    });
                }


                // say knockout that we have something on elements page
                ko.applyBindings({
                    elements: options,
                    selectedElements: selectedElements,
                    multiple: multiple,
                    journalType: journalType,
                    page: pageNumber,
                    loading: loading,
                    columns: defaultVisibleAttributes,
                    hidden: defaultHiddenByType,
                    dock: params.dock,
                    hightlightSelection: params.hightlightSelection,
                    afterSelectionCallback: function (data, event) {
                        if (!multiple() && event.type == "dblclick") {
                            value(data);
                            scope.panel.hide();
                        }
                    }
                }, Dom.get(elementsPageId));

                // say knockout that we have something on search page
                ko.applyBindings({
                    htmlId: element.id,
                    itemId: data.nodetype(),
                    journalType: journalType,
                    defaultFilterCriteria: defaultSearchableAttributes,
                    selectedFilterCriteria: selectedFilterCriteria,
                    filterCriteriaVisibility: filterCriteriaVisibility,
                    criteria: criteria,
                    criteriaListShow: criteriaListShow,
                    selectFilterCriterion: function (data, event) {
                        // clone criterion and add value observables
                        var newCriterion = _.clone(data);
                        newCriterion.value = ko.observable();
                        newCriterion.predicateValue = ko.observable();
                        selectedFilterCriteria.push(newCriterion);

                        // hide drop-down menu
                        criteriaListShow(!criteriaListShow());
                    },
                    applyCriteria: function (data, event) {
                        var selectedCriteria = selectedFilterCriteria();

                        if (!selectedCriteria.length) {
                            criteria(optionsFilter && optionsFilter() ? optionsFilter() : []);
                        } else {
                            var criteriaList = getCriteriaList(selectedCriteria);

                            // add filters, which by default are not in the journal
                            if (optionsFilter && optionsFilter() && optionsFilter().length) {
                                optionsFilter().forEach(function(item) {
                                    var filterCriteria = _.find(selectedCriteria, function(filter) {
                                        return item.attribute == filter.name();
                                    });
                                    if (!filterCriteria) {
                                        criteriaList.push(item);
                                    }
                                });

                            }
                            criteria(criteriaList);
                        }
                    },
                    addFilterCriterion: function (data, event) {
                        criteriaListShow(!criteriaListShow());
                    }
                }, Dom.get(filterPageId));

                // say knockout that we have something on create tab for create page
                ko.applyBindings({
                    scope: data,
                    buttonTitle: localization.createTab,
                    journalType: journalType,
                    createVariantsVisibility: createVariantsVisibility,
                    callback: function (variant) {
                        var scCallback = function (node) {
                            if (mode == "collapse") {
                                // clear create page
                                var createPage = Dom.get(createPageId);
                                Dom.addClass(createPage, "hidden");
                                createPage.innerHTML = "";

                                // show elements page
                                var elementsPage = Dom.get(elementsPageId);
                                Dom.removeClass(elementsPage, "hidden");

                                // change tab selection
                                var buttons = Dom.getElementsBy(function (element) {
                                    return element.className.indexOf("selected") != -1
                                }, "button", journalPickerHeaderId);

                                _.each(buttons, function (element) {
                                    element.classList.remove("selected");
                                });
                            }
                        };

                        Citeck.forms.formContent(variant.type(), variant.formId(), {
                                response: function (response) {
                                    Dom.get(createPageId).innerHTML = response;

                                    // hide other pages and remove selection from other tabs
                                    Dom.removeClass(elementsTabId, "selected");
                                    Dom.removeClass(filterTabId, "selected");
                                    Dom.addClass(elementsPageId, "hidden");
                                    Dom.addClass(filterPageId, "hidden");

                                    // show create page and hightlight tab
                                    Dom.removeClass(createPageId, "hidden");
                                    var createButton = Dom.getElementsBy(function (el) {
                                        return el.tagName == "BUTTON";
                                    }, "button", journalPickerHeaderId);
                                    Dom.addClass(createButton, "selected");
                                },

                                submit: function (node) {
                                    scCallback(node);
                                },
                                cancel: scCallback
                            },
                            {
                                destination: variant.destination(),
                                fieldId: data.name()
                            });

                    },
                    virtualParent: params.virtualParent,
                    createVariantsSource: params.createVariantsSource
                }, Dom.get(journalPickerHeaderId));

                if (value()) selectedElements(multiple() ? value() : [value()]);


                if (!Citeck.mobile.isMobileDevice()) {
                    YAHOO.Bubbling.on("change-mobile-mode", function (l, args) {
                        var itemsCount = args[1].mobileMode ? 5 : 10;
                        if (itemsCount != maxItems()) {
                            pageNumber(1);
                            maxItems(itemsCount);
                        }
                        ;
                    });
                }

                // reload filterOptions request if was created new object
                YAHOO.Bubbling.on("object-was-created", function (layer, args) {
                    if (args[1].fieldId == data.name()) {
                        if (args[1].value) {
                            additionalOptions(_.union(additionalOptions(), [args[1].value]));
                            if (!data.multiple()) selectedElements.removeAll();
                            selectedElements.push(args[1].value);
                        }

                        criteria(_.clone(criteria()));
                    }
                });
            }

            var filterTab = Dom.get(filterTabId);
            if (!filterCriteriaVisibility() && params.defaultFiltersVisibility == "true" && filterTab) {
                filterTab.click();
            }
            scope.panel.show();
            scope.panel.center();

            // calc max height
            var clientHeightZ = scope.panel.element.clientHeight - scope.panel.body.clientHeight;
            if (clientHeightZ > 100) {
                clientHeightZ = 82;
            }

            var maxHeight = YAHOO.util.Dom.getViewportHeight()
                - clientHeightZ
                - 60 // gap to screen edge
            ;

            // set min & max height for panel container
            scope.panel.body.style.minHeight = '423px';
            scope.panel.body.style.maxHeight = maxHeight + 'px';
            scope.panel.body.style.overflowY = 'auto';
            scope.panel.element.style.minWidth = '320px';
            scope.panel.element.style.maxWidth = 'calc(100% - 20px)';

            // set min height for create page
            $(scope.panel.body).find('.create-page').css({'min-height': '385px', 'height': 'initial'});

            vIFrame = $('#' + iframeId)[0].contentWindow;
            // center the panel on resize
            vIFrame.addEventListener('resize', _.throttle(function () {
                scope.panel.center();
            }, 200));
        }
        showPanelOnClick.call(this);
    });
  }
}

// -------------
// CREATE OBJECT
// -------------

var CreateVariant = koclass('CreateVariant'),
    CreateVariantsByJournal = koclass('controls.CreateVariantsByJournal'),
    CreateVariantsByType = koclass('controls.CreateVariantsByType'),
    CreateVariantsByView = koclass('controls.CreateVariantsByView'),
    CreateObjectButton = koclass('controls.CreateObjectButton'),
    CreateObjectLink = koclass('controls.CreateObjectLink');

CreateVariantsByJournal
    .key('journal', String)
    .property('createVariants', [CreateVariant])
    .load('createVariants', koutils.simpleLoad({
        url: Alfresco.constants.PROXY_URI + "api/journals/create-variants/journal/{journal}",
        resultsMap: { createVariants: 'createVariants' }
    }))
    ;

CreateVariantsByType
    .key('type', String)
    .property('createVariants', [CreateVariant])
    .load('createVariants', koutils.simpleLoad({
        url: Alfresco.constants.PROXY_URI + "api/journals/create-variants/nodetype/{type}",
        resultsMap: { createVariants: 'createVariants' }
    }))
    ;

CreateVariantsByView
    .key('type', String)
    .property('createVariants', [CreateVariant])
    .load('createVariants', koutils.simpleLoad({
        url: Alfresco.constants.PROXY_URI + "citeck/invariants/create-views?type={type}",
        resultsMap: { createVariants: 'createVariants' }
    }))
    ;

CreateObjectButton
    .key('id', String)

    .property('scope', Object)
    .property('constraint', Function)
    .property('constraintMessage', String)
    .property('source', String)
    .property('customType', String)
    .property('buttonTitle', String)
    .property('journalType', JournalType)
    .property('parentRuntime', String)
    .property('virtualParent', Boolean)
    .property('callback', Function)
    .property('baseRef', String)
    .property('rootAttributeName', String)

    .shortcut('protected', 'scope.protected')
    .shortcut('nodetype', 'scope.nodetype')

    .computed('createVariants', function() {

        var list = null;

        if (this.source() == 'create-custom-type' && this.customType()) {
            list = new CreateVariantsByView(this.customType());
        } else if (this.source() == 'create-views' && this.nodetype()) {
            list = new CreateVariantsByView(this.nodetype());
        } else if (this.source() == 'type-create-variants' && this.nodetype()) {
            list = new CreateVariantsByType(this.nodetype());
        } else if (this.source() == 'journal-create-variants' && this.journalType()) {
            list = new CreateVariantsByJournal(this.journalType().id());
        }
        return list ? list.createVariants() : [];
    })
    .method('execute', function(createVariant) {
        if (this.callback() && _.isFunction(this.callback())) {
            var callback = this.callback();
            callback(createVariant);
        } else {
            Citeck.forms.dialog(createVariant.type(), createVariant.formId(),
                {
                    scope: this,
                    fn: function(value) {
                        if (this.constraint() && _.isFunction(this.constraint())) {
                            var constraint = ko.computed(this.constraint(), {
                                oldValue: this.scope().multipleValues(),
                                newValue: value
                            });

                            koutils.subscribeOnce(ko.pureComputed(function() {
                                var result = constraint();
                                if (result != null || result != undefined) return result;
                            }), function(result) {
                                if (_.isBoolean(result)) {
                                    if (result) { this.scope().lastValue(value); }
                                    else {
                                        Alfresco.util.PopupManager.displayMessage({
                                            text: this.constraintMessage() || Alfresco.util.message("create-object.message")
                                        });
                                    }
                                }
                            }, this);
                        } else {
                            this.scope().lastValue(value);
                        }

                        YAHOO.Bubbling.fire("object-was-created", {
                            fieldId: this.scope().name(),
                            value: value
                        });
                    }
                },
                {
                    title: this.buttonTitle() + ": " + createVariant.title(),
                    destination: createVariant.destination(),
                    parentRuntime: this.parentRuntime(),
                    virtualParent: this.virtualParent(),
                    baseRef: this.baseRef(),
                    rootAttributeName: this.rootAttributeName()
                }
            );
        }
    })
    ;


ko.components.register('createObjectButton', {
    viewModel: CreateObjectButton,
    template:
       '<!-- ko if: protected() || createVariants().length == 0 --> \
            <button class="create-object-button" disabled="disabled" data-bind="text: buttonTitle"></button> \
        <!-- /ko --> \
        <!-- ko if: !protected() && createVariants().length == 1 --> \
            <button class="create-object-button" data-bind="text: buttonTitle, attr: { title: createVariants()[0].title() }, click: execute.bind($data, createVariants()[0])"></button> \
        <!-- /ko --> \
        <!-- ko if: !protected() && createVariants().length > 1 --> \
            <div class="yui-overlay yuimenu button-menu" data-bind="attr: { id: id() + \'-create-menu\' }"> \
                <div class="bd"> \
                    <ul data-bind="foreach: createVariants" class="first-of-type"> \
                        <li class="yuimenuitem"> \
                            <a class="yuimenuitemlabel" data-bind="text: title, click: $parent.execute.bind($parent, $data), css: { \'default-create-variant\': isDefault }"></a> \
                        </li> \
                    </ul> \
                </div> \
            </div> \
            <span class="create" data-bind="yuiButton: { type: \'menu\', menu: id() + \'-create-menu\' }"> \
                <span class="first-child"> \
                    <button data-bind="text: buttonTitle"></button> \
                </span> \
            </span> \
        <!-- /ko -->'
});

// ------------
// AUTOCOMPLETE
// ------------

ko.components.register("autocomplete", {
    viewModel: function(params) {
        var self = this;

        this.defaults = {
            criteria: [{ attribute: "cm:name", predicate: "string-contains" }],
            searchScript: "criteria-search",
            maxItems: 10
        }

        this.labels = {
            label: Alfresco.util.message("autocomplete.label"),
            help: Alfresco.util.message("autocomplete.help"),
            empty: Alfresco.util.message("autocomplete.empty"),
            more: Alfresco.util.message("autocomplete.more")
        }

        this.cache = {
            criteria: [],
            options: []
        }

        // base variables
        this.element = params.element;
        this.data  = params.data;
        this.value = this.data["singleValue"];
        this.disabled = this.data["protected"];

        this.searchScript = params.searchScript || self.defaults.searchScript;
        this.searchCriteria = _.isFunction(params["criteria"]) ? params["criteria"]() : params["criteria"];
        this.minQueryLength = params.minQueryLength;
        this.maxItems = params.maxItems || self.defaults.maxItems;

        // observables
        this.containerVisibility = ko.observable(false);
        this.highlightedElement = ko.observable();
        this.searchQuery = ko.observable();
        this.searching = ko.observable(false);

        this.componentFocused = ko.observable(false);
        this.searchFocused = ko.observable(true);

        this.hasMore = ko.observable(false);
        this.skipCount = ko.observable(0);


        // computed
        this.label = ko.pureComputed(function() {
            return self.value() ? self.data.getValueTitle(self.value()) : self.labels.label;
        });

        this.searchInput = ko.computed({
            read: function() {
                return self.searchQuery();
            },
            write: function(newValue) {
                if (!newValue || (!self.minQueryLength || (newValue.length >= self.minQueryLength))) {
                    self.searchQuery(newValue);
                    self.searching(true);
                }
            }
        });

        this.criteria = ko.pureComputed(function() {
            var criterias = _.isFunction(self.searchCriteria) ? self.searchCriteria() : self.searchCriteria;

            if (self.searchQuery()) {
                return _.map(criterias || self.defaults.criteria, function(item) {
                    return _.defaults(_.clone(item), { value: self.searchQuery() });
                });
            } else {
                return _.filter(criterias || self.defaults.criteria, function(item) {
                    return (item && item.value && item.predicate && item.attribute);
                });
            }
        }).extend({ rateLimit: { timeout: 500, method: "notifyWhenChangesStop" } });

        this.options = ko.pureComputed(function() {
            var result = self.data.filterOptions(self.criteria(), {
                maxItems: 10,
                skipCount: self.skipCount(),
                searchScript: self.searchScript
            });
            if (result.pagination) return result;
        });

        this.visibleOptions = ko.pureComputed(function() {
            if (self.options() && self.options().pagination) {
                self.cache.options = self.cache.options.concat(self.options());
                return self.cache.options;
            }
        });


        // subscription and events
        this.containerVisibility.subscribe(function() {
            self.searchFocused(true);
        });

        this.criteria.subscribe(function(newValue) {
            self.skipCount(0);
            self.cache.options = [];
        });

        this.options.subscribe(function(newValue) {
            if (newValue && newValue.pagination) {
                self.hasMore(newValue.pagination.hasMore);
                self.searching(false);

                if (newValue.length > 0 && self.skipCount() == 0)
                    self.highlightedElement(newValue[0]);

            }
        });

        // public methods
        this.clear = function(data, event) { if (event.which == 1) self.value(null) };
        this.toggleContainer = function(data, event) { if (event.which == 1) self.containerVisibility(!self.containerVisibility()); };
        this.renderHandler = function(element, data) { if(this.foreach()[this.foreach().length - 1] === data) self.searching(false); };

        this.more = function(element, data) {
            self.skipCount(self.skipCount() + 10);
            self.searchFocused(true);
        };

        this.selectItem = function(data, event) {
            if (event.which == 1) {
                self.value(data);
                self.containerVisibility(false);
                self.highlightedElement(data);
            }
        };

        this.keyAction = function(data, event) {
            if ([9, 13, 27, 38, 40].indexOf(event.keyCode) != -1) {
                // apply element for value
                if (event.keyCode == 9 || event.keyCode == 13) {
                    self.value(self.highlightedElement());
                }

                // close container
                if (event.keyCode == 9 || event.keyCode == 13 || event.keyCode == 27) {
                    self.containerVisibility(false);

                    // restore focus on component
                    self.componentFocused(true);
                }

                // move selection
                if (event.keyCode == 38 || event.keyCode == 40) {
                    var selectedIndex = self.visibleOptions().indexOf(self.highlightedElement()),
                        nextSelectIndex = event.keyCode == 38 ? selectedIndex - 1 : selectedIndex + 1;

                    if (selectedIndex != -1 && self.visibleOptions()[nextSelectIndex]) {
                        self.highlightedElement(self.visibleOptions()[nextSelectIndex])
                    };

                    // TODO:
                    // - select 'more' throuth keyboard
                }

                return false;
            }

            return true;
        };

        this.keyManagment = function(data, event) {
            if ([40, 46].indexOf(event.keyCode) != -1) {
                // open container if 'down'
                if (event.keyCode == 40) self.containerVisibility(true);

                // clear if 'delete'
                if (event.keyCode == 46) self.value(null);

                return false;
            }

            return true;
        };

        // blur
        $("body").click(function(event, a) {
            var node = event.target,
                body = document.getElementById("Share");

            while (node && node != body) {
                if (node == self.element) return;
                node = node.parentNode;
            }

            self.containerVisibility(false);
        });
    },
    template:
       '<!-- ko if: disabled -->\
            <div class="autocomplete-select disabled" tabindex="0">\
                <span class="autocomplete-value" data-bind="text: label"></span>\
                <div class="autocomplete-twister"></div>\
            </div>\
        <!-- /ko -->\
        <!-- ko ifnot: disabled -->\
            <div class="autocomplete-select" tabindex="0"\
                data-bind="\
                    event: { mousedown: toggleContainer, keydown: keyManagment }, mousedownBubble: false,\
                    css: { opened: containerVisibility },\
                    hasFocus: componentFocused">\
                <span class="autocomplete-value" data-bind="text: label"></span>\
                <!-- ko if: value -->\
                    <a class="clear-button" data-bind="event: { mousedown: clear }, mousedownBubble: false">x</a>\
                <!-- /ko -->\
                <div class="autocomplete-twister"></div>\
            </div>\
        <!-- /ko -->\
        <!-- ko if: containerVisibility -->\
            <div class="autocomplete-container" data-bind="css: { loading: searching() }">\
                <div class="autocomplete-search-container">\
                    <!-- ko ifnot: Citeck.HTML5.supportAttribute("placeholder") -->\
                        <div class="help-message" data-bind="text: labels.help"></div>\
                    <!-- /ko -->\
                    <input type="text" class="autocomplete-search"\
                        data-bind="\
                            textInput: searchInput,\
                            event: { keydown: keyAction },\
                            keydownBubble: false,\
                            attr: { placeholder: labels.help },\
                            hasFocus: searchFocused">\
                    <div class="loading-indicator" data-bind="css: { hidden: !searching() }"></div>\
                </div>\
                <!-- ko if: visibleOptions() && !searching() -->\
                    <!-- ko if: visibleOptions().length > 0 -->\
                        <ul class="autocomplete-list" data-bind="foreach: { data: visibleOptions, afterRender: renderHandler }">\
                            <li data-bind="\
                                event: { mousedown: $parent.selectItem }, mousedownBubble: false,\
                                css: { selected: $parent.highlightedElement() == $data }">\
                                <a data-bind="text: $parent.data.getValueTitle($data)"></a>\
                            </li>\
                        </ul>\
                    <!-- /ko -->\
                    <!-- ko if: visibleOptions().length == 0 -->\
                        <span class="autocomplete-message empty-message" data-bind="text: labels.empty"></span>\
                    <!-- /ko -->\
                <!-- /ko -->\
                <!-- ko if: hasMore -->\
                    <div class="autocomplete-more">\
                        <a data-bind="click: more, attr: { title: labels.more }">...</a>\
                    </div>\
                <!-- /ko -->\
            </div>\
        <!-- /ko -->'
});

// ---------------
// SELECT 2
// ---------------

// TODO:
// - load filters only if it requered

ko.components.register("select2", {
    viewModel: function(params) {
        var self = this;
        kocomponents.initializeParameters.call(this, params);

        this.id = this.element.id;

        this._listMode = self.mode == "list";
        this._tableMode = self.mode == "table";

        if (this.forceOptions) {
            this.forceOptions = this.forceOptions();
            this.forceOptions.extend({ rateLimit: { timeout: 250, method: "notifyWhenChangesStop" } });
        }

        if (this.optionsFilter && this.optionsFilter()) {
            this.optionsFilter = this.optionsFilter();
        }

        if (this._tableMode) {
            if (this.defaultVisibleAttributes)
                this.defaultVisibleAttributes =  _.map(this.defaultVisibleAttributes.split(","), function(a) { return trim(a); })

            if (this.defaultSearchableAttributes)
                this.defaultSearchableAttributes =  _.map(this.defaultSearchableAttributes.split(","), function(a) { return trim(a); })

            this.journalType = this.journalTypeId ? new JournalType(this.journalTypeId) : null;
            if (!this.journalType) { /* so, it is fail */ }
        }


        // localization
        var localization = this.localization = {
            select: Alfresco.util.message("button.select"),

            // table
            title: Alfresco.util.message("form.select.label"),
            search: Alfresco.util.message("journal.search"),
            filterTab: Alfresco.util.message("journal.filter"),
            createTab: Alfresco.util.message("journal.create"),
            selectedElements: Alfresco.util.message("journal.selected-elements"),
            applyCriteria: Alfresco.util.message("journal.apply-criteria"),
            addFilterCriterion: Alfresco.util.message("journal.add-filter-criterion"),
            submitButton: Alfresco.util.message("button.ok"),
            cancelButton: Alfresco.util.message("button.cancel"),
            nextPageLabel: Alfresco.util.message("journal.pagination.next-page-label"),
            nextPageTitle: Alfresco.util.message("journal.pagination.next-page-title"),
            previousPageLabel: Alfresco.util.message("journal.pagination.previous-page-label"),
            previousPageTitle: Alfresco.util.message("journal.pagination.previous-page-title"),

            // list
            label: Alfresco.util.message("autocomplete.label"),
            help: Alfresco.util.message("autocomplete.help"),
            empty: Alfresco.util.message("autocomplete.empty"),
            more: Alfresco.util.message("autocomplete.more")
        };


        // private methods
        // ---------------

        this._optionTitle = function(option) {
            if (self.optionsText) return self.optionsText(option);
            return self.getValueTitle(option);
        };

        this._addValues = function(values) {
            self.value(self.multiple() ? _.union(self.value(), values) : values[0]);
        }

        // observables
        // -----------

        this.containerVisibility = ko.observable(false);
        this.highlightedElement = ko.observable();
        this.searchQuery = ko.observable();

        this.hasMore = ko.observable(false);
        this.count = ko.observable(this.step);
        this.page = ko.observable(1);

        // for list mode
        this.componentFocused = ko.observable(false);
        this.searchFocused = ko.observable(true);

        // for table mode
        this.panel;
        this.criteria = ko.observable([]);
        this.selectedElements = ko.observableArray();
        this.selectedFilterCriteria = ko.observableArray();
        this.additionalOptions = ko.observable([]);
        this._criteriaListShow = ko.observable(false);
        this._filterVisibility = ko.observable(false);
        this.searchBarVisibility = params.searchBarVisibility ? params.searchBarVisibility == "true" : true;


        // computed
        // --------

        // get default searchable attributes
        this.searchableAttributes = ko.computed(function() {
            if (this.journalType) {
                return this.journalType.searchableAttributes();
            } else if (this.options().length) {
                return this.options()[0].impl().attributes();
            }

            return [];
        }, this, { deferEvaluation: true });

        // get default filter criteria
        this.defaultCriteria = ko.computed(function() {
            if (this.journalType) {
                if (this.defaultSearchableAttributes) return this.journalType.attributes();
                return this.journalType.defaultAttributes();
            } else if (this.options().length) {
                var firstElement = this.options()[0].impl();
                if (this.defaultSearchableAttributes) return firstElement.attributes();

                var title = firstElement.attribute("cm:title");
                if (title) return [ title ];
                return [ firstElement.attribute("cm:name") ];
            }

            return [];
        }, this, { deferEvaluation: true });

        this.label = ko.pureComputed(function() {
            return this.value() ? this.getValueTitle(this.value())() : this.localization.label;
        }, this);

        this.visibleOptions = ko.pureComputed(function() {
            var preparedOptions = (this.forceOptions ? this.forceOptions() : this.options()) || [];

            if (this.additionalOptions().length) {
                preparedOptions = _.union(preparedOptions, this.additionalOptions());
            }

            if (this.criteria().length) {
                this.criteria().forEach(function(criterion) {
                    if (criterion.predicate.indexOf("string") != -1) {
                        if (criterion.allowMultipleFilterValue) {
                            criterion.value = _.map(criterion.value, function(item) {
                                return item.toLowerCase();
                            });
                        } else {
                            criterion.value = criterion.value.toLowerCase();
                        }
                    }
                    preparedOptions = _.filter(preparedOptions, function(option) {
                        var attributeComputed = self.optionFilter ? self.optionFilter(option, criterion.attribute) : option.impl().attribute(criterion.attribute),
                            attributeValue = self.optionFilter ? attributeComputed() : attributeComputed.value();

                        if (attributeValue != null) {
                            if (criterion.predicate.indexOf("string") != -1) {
                                attributeValue = attributeValue.toLowerCase();
                            }

                            switch (criterion.predicate) {
                                case "boolean-true":
                                    return attributeValue === true;
                                case "boolean-false":
                                    return attributeValue === false;

                                case "string-starts-with":
                                    if (criterion.allowMultipleFilterValue) {
                                        return _.some(criterion.value, function(item) {
                                            return attributeValue.startsWith(item);
                                        });
                                    }
                                    return attributeValue.startsWith(criterion.value);
                                case "string-ends-with":
                                    if (criterion.allowMultipleFilterValue) {
                                        return _.some(criterion.value, function(item) {
                                            return attributeValue.endsWith(item);
                                        });
                                    }
                                    return attributeValue.endsWith(criterion.value);

                                case "date-greater-or-equal":
                                    return new Date(attributeValue) >= new Date(criterion.value);
                                case "date-less-or-equal":
                                    return new Date(attributeValue) <= new Date(criterion.value);

                                case "number-less-than":
                                    return attributeValue < Number(criterion.value);
                                case "number-less-or-equal":
                                    return attributeValue <= Number(criterion.value);
                                case "number-greater-than":
                                    return attributeValue > Number(criterion.value);
                                case "number-greater-or-equal":
                                    return attributeValue >= Number(criterion.value);

                                case "assoc-contains":
                                    return attributeValue.nodeRef.indexOf(criterion.value) != -1;
                                case "assoc-not-contains":
                                    return attributeValue.nodeRef.indexOf(criterion.value) == -1;
                            }

                            if (criterion.predicate.indexOf("not-equals") != -1) {
                                if (criterion.allowMultipleFilterValue) {
                                    return _.every(criterion.value, function(item) {
                                        return attributeValue != item;
                                    });
                                }
                                return attributeValue != criterion.value;
                            } else if (criterion.predicate.indexOf("equals") != -1) {
                                if (criterion.allowMultipleFilterValue) {
                                    return _.some(criterion.value, function(item) {
                                        return attributeValue == item;
                                    });
                                }
                                return attributeValue == criterion.value;
                            } else if (criterion.predicate.indexOf("not-empty") != -1) {
                                return !_.isEmpty(attributeValue);
                            } else if (criterion.predicate.indexOf("empty") != -1) {
                                return _.isEmpty(attributeValue);
                            } else if (criterion.predicate.indexOf("not-contains") != -1) {
                                if (criterion.allowMultipleFilterValue) {
                                    return _.some(criterion.value, function(item) {
                                        return attributeValue.indexOf(item) == -1;
                                    });
                                }
                                return attributeValue.indexOf(criterion.value) == -1;
                            } else if (criterion.predicate.indexOf("contains") != -1) {
                                if (criterion.allowMultipleFilterValue) {
                                    return _.some(criterion.value, function(item) {
                                        return attributeValue.indexOf(item) != -1;
                                    });
                                }
                                return attributeValue.indexOf(criterion.value) != -1;
                            } else if (criterion.predicate.indexOf("not-empty") != -1) {
                                return !_.isEmpty(attributeValue);
                            } else if (criterion.predicate.indexOf("empty") != -1) {
                                return _.isEmpty(attributeValue);
                            }
                        }
                    })
                });
            }

            if (this.searchQuery()) {
                preparedOptions = _.filter(preparedOptions, function(option) {
                    var searchString = self.searchQuery().toLowerCase(),
                        labelString  = self._optionTitle(option)();

                    if (labelString) {
                        labelString = labelString.toLowerCase();
                        switch (self.searchPredicat) {
                            case "startsWith":
                                return labelString.startsWith(searchString);

                            case "contains":
                                return labelString.indexOf(searchString) != -1;
                        }
                    }

                    return false;
                });
            }

            // pagination for list
            if (this._listMode) {
                if (this.count() < preparedOptions.length) {
                    this.hasMore(true);
                    return preparedOptions.slice(0, this.count());
                }
            }

            // pagination for table
            if (this._tableMode) {
                var startIndex = this.step * this.page() - this.step, endIndex = this.step * this.page();
                this.hasMore(this.step * this.page() < preparedOptions.length);

                return _.map(preparedOptions.slice(startIndex, endIndex), function(option) {
                    if (option.toString().indexOf("invariants.Node") == -1) return new Node(option);
                    return option;
                });
            }

            this.hasMore(false);
            return preparedOptions;
        }, this);


        // extends
        // -------

        this.searchQuery.extend({ rateLimit: { timeout: 250, method: "notifyWhenChangesStop" } });


        // subscription and events
        // -----------------------

        this.searchQuery.subscribe(function() { self.count(self.step); });

        if (this._listMode) {
            this.containerVisibility.subscribe(function() { self.searchFocused(true); });
            this.visibleOptions.subscribe(function(newValue) { if (newValue.length > 0) self.highlightedElement(newValue[0]); });
        }

        if (this._tableMode) {
            // add default criteria to selectedFilterCriteria
            koutils.subscribeOnce(ko.computed(function() {
                self.selectedFilterCriteria.removeAll();
                var dc = self.defaultCriteria();

                if (self.defaultSearchableAttributes) {
                    var validAttributes = [];
                    for (var i = 0; i < dc.length; i++) {
                        if (self.defaultSearchableAttributes.indexOf(dc[i].name()) != -1) validAttributes.push(dc[i]);
                    }
                    dc = validAttributes;
                }

                // add default option's filter criteria from view
                if (self.optionsFilter) {
                    self.criteria(self.optionsFilter() && self.optionsFilter().length ? self.optionsFilter() : []);
                }

                if (dc.length) {
                    for (var i in dc) {
                        var newCriterion = _.clone(dc[i]);
                        newCriterion.value = ko.observable();
                        newCriterion.predicateValue = ko.observable();

                        // add value of default option's filter criteria on filter form
                        if (self.optionsFilter && self.optionsFilter() && self.optionsFilter().length && newCriterion.name) {
                            var filterCriterion = _.find(self.optionsFilter, function(item) {
                                return item.attribute == newCriterion.name();
                            });
                            if (filterCriterion) newCriterion.value(filterCriterion.value);
                        }
                        self.selectedFilterCriteria.push(newCriterion);
                    }
                }
            }), self.defaultCriteria.dispose);
        }


        // public methods
        // --------------

        this.clear = function(data, event) { if (event.which == 1) self.value(null) };
        this.toggleContainer = function(data, event) { if (event.which == 1) self.containerVisibility(!self.containerVisibility()); };

        this.selectItem = function(data, event) {
            if (event.which == 1) {
                if (self.optionsValue) {
                    var optionValue = self.optionsValue(data);
                    self.value(optionValue());
                } else { self.value(data);  }    // put item to value
                self.containerVisibility(false); // close container after select item
                self.highlightedElement(data);   // highlight the selected item
            }
        };

        this.keyAction = function(data, event) {
            if ([9, 13, 27, 38, 40].indexOf(event.keyCode) != -1) {
                // apply element for value
                if (event.keyCode == 9 || event.keyCode == 13) {
                    self.value(self.highlightedElement());
                }

                // close container
                if (event.keyCode == 9 || event.keyCode == 13 || event.keyCode == 27) {
                    self.containerVisibility(false); // close container after select item
                    self.componentFocused(true);     // restore focus on component
                }

                // move selection
                if (event.keyCode == 38 || event.keyCode == 40) {
                    var selectedIndex = self.options().indexOf(self.highlightedElement()),
                        nextSelectIndex = event.keyCode == 38 ? selectedIndex - 1 : selectedIndex + 1;

                    if (selectedIndex != -1 && self.options()[nextSelectIndex]) {
                        self.highlightedElement(self.options()[nextSelectIndex]); // highlight next or previous item
                    };
                }

                return false;
            }

            return true;
        };

        this.keyManagment = function(data, event) {
            if ([40, 46].indexOf(event.keyCode) != -1) {
                // open container if 'down'
                if (event.keyCode == 40) self.containerVisibility(true);

                // clear if 'delete'
                if (event.keyCode == 46) self.value(null);

                return false;
            }

            return true;
        };

        this.more = function(element, data) {
            self.count(self.count() + self.step);
        };

        var elementsPageId          = this.id + "-panel-elementsPage",
            filterTabId             = this.id + "-panel-filterTab",
            filterPageId            = this.id + "-panel-filterPage",
            createPageId            = this.id + "-panel-createPage",
            journalPickerHeaderId   = this.id + "-panel-journalPickerHeader";

        this.journalPicker = function(data, event) {
            if (!data.panel) {
                // Auto-fit width
                var optimalWidth = (function() {
                    var maxContainerWidth = screen.width - 200,
                        countOfAttributes = (function() {
                            if (data.defaultVisibleAttributes) return data.defaultVisibleAttributes.length;
                            if (data.journalType) return data.journalType.defaultAttributes().length;
                            return 1;
                        })();

                    if (countOfAttributes > 5) {
                        var potentialWidth = 150 * countOfAttributes;
                        return (potentialWidth >= maxContainerWidth ? maxContainerWidth : potentialWidth) + "px";
                    }

                    return "800px";
                })();

                // initialize panel
                data.panel = new YAHOO.widget.Panel(data.id + "-panel", {
                    width:          optimalWidth,
                    visible:        false,
                    fixedcenter:    true,
                    draggable:      true,
                    modal:          true,
                    zindex:         5,
                    close:          true
                });

                // hide dialog on click 'esc' button
                data.panel.cfg.queueProperty("keylisteners", new YAHOO.util.KeyListener(document, { keys: 27 }, {
                    fn: data.panel.hide,
                    scope: data.panel,
                    correctScope: true
                }));

                // build panel header, body and footer
                data.panel.setHeader(data.localization.title);
                data.panel.setBody('\
                    <div class="journal-picker-header collapse" id="' + journalPickerHeaderId + '">\
                        <!-- ko if: filters -->\
                            <a id="' + filterTabId + '" class="journal-tab-button" data-bind="text: labels.filterTab, click: filter, clickBubble: false"></a>\
                        <!-- /ko -->\
                        <!-- ko if: createVariantsVisibility -->\
                            <!-- ko component: { name: "createObjectButton", params: {\
                                scope: scope,\
                                source: createVariantsSource,\
                                callback: callback,\
                                buttonTitle: labels.createTab,\
                                virtualParent: virtualParent,\
                                journalType: journalType\
                            }} --><!-- /ko -->\
                        <!-- /ko -->\
                        ' + (data.searchBarVisibility ? '\
                            <div class="journal-search">\
                                <input type="search" class="journal-search-input" data-bind="\
                                    textInput: searchQuery,\
                                    attr: { placeholder: labels.search }\
                                " />\
                            </div>': '') + '\
                    </div>\
                    <div class="journal-picker-page-container">\
                        <!-- ko if: filters -->\
                            <div class="filter-page hidden" id="' + filterPageId + '">\
                                <!-- ko if: filterVisibility -->\
                                    <div class="selected-filter-criteria-container">\
                                        <!-- ko component: { name: \'filter-criteria-table\',\
                                            params: {\
                                                htmlId: htmlId,\
                                                itemId: itemId,\
                                                journalType: journalType,\
                                                selectedFilterCriteria: selectedFilterCriteria,\
                                                defaultFilterCriteria: defaultFilterCriteria\
                                            }\
                                        } --> <!-- /ko -->\
                                    </div>\
                                    <div class="filter-criteria-actions">\
                                        <ul>\
                                            <li class="filter-criteria-option">\
                                                <a class="apply-criteria filter-criteria-button" data-bind="click: applyCriteria, text: labels.applyCriteria"></a>\
                                            </li>\
                                            <li class="filter-criteria-option">\
                                                <a class="filter-criteria-button" data-bind="click: addFilterCriterion, text: labels.addFilterCriterion"></a>\
                                                <div class="filter-criteria-variants" data-bind="visible: criteriaListShow">\
                                                    <!-- ko if: journalType -->\
                                                        <ul class="filter-criteria-list" data-bind="foreach: journalType.searchableAttributes">\
                                                            <li class="filter-criteria-list-option">\
                                                                <a class="filter-criterion" data-bind="text: displayName, click: $root.selectFilterCriterion"></a>\
                                                            </li>\
                                                        </ul>\
                                                    <!-- /ko -->\
                                                    <!-- ko ifnot: journalType -->\
                                                        <ul class="filter-criteria-list" data-bind="foreach: defaultSearchableAttributes">\
                                                            <li class="filter-criteria-list-option">\
                                                                <a class="filter-criterion" data-bind="text: title, click: $root.selectFilterCriterion"></a>\
                                                            </li>\
                                                        </ul>\
                                                    <!-- /ko -->\
                                                </div>\
                                            </li>\
                                        </ul>\
                                    </div>\
                                <!-- /ko -->\
                            </div>\
                        <!-- /ko -->\
                        <div class="elements-page" id="' + elementsPageId + '">\
                            <div class="journal-container">\
                                <!-- ko component: { name: \'journal\',\
                                    params: {\
                                        sourceElements: elements,\
                                        targetElements: selectedElements,\
                                        journalType: journalType,\
                                        columns: columns,\
                                        hightlightSelection: true,\
                                        afterSelectionCallback: afterSelectionCallback,\
                                        options: { multiple: multiple, pagination: false },\
                                    }\
                                } --><!-- /ko -->\
                                <div class="journal-pagination">\
                                    <span class="previous-page">\
                                        <!-- ko if: page() - 1 > 0 -->\
                                            <a data-bind="click: previousPage,\
                                                          text: labels.previousPageLabel,\
                                                          attr: { title: labels.previousPageTitle }"><--</a>\
                                        <!-- /ko -->\
                                        <!-- ko ifnot: page() - 1 > 0 -->\
                                            <!-- ko text: labels.previousPageLabel --><!-- /ko -->\
                                        <!-- /ko -->\
                                    </span>\
                                    <span class="page-label">\
                                        <span class="start-page" data-bind="text: page() * maxItems - maxItems + 1"></span>\
                                        <span class="dash">-</span>\
                                        <span class="end-page" data-bind="text: page() * maxItems"></span>\
                                    </span>\
                                    <span class="next-page">\
                                        <!-- ko if: hasMore -->\
                                            <a data-bind="click: nextPage,\
                                                          text: labels.nextPageLabel,\
                                                          attr: { title: labels.nextPageTitle }">--></a>\
                                        <!-- /ko -->\
                                        <!-- ko ifnot: hasMore -->\
                                            <!-- ko text: labels.nextPageLabel --><!-- /ko -->\
                                        <!-- /ko -->\
                                    </span>\
                                </div>\
                            </div>\
                        </div>\
                        <div class="create-page hidden" id="' + createPageId + '"></div>\
                    </div>\
                ');
                data.panel.setFooter('\
                    <div class="buttons">\
                        <input type="submit" data-bind="value: labels.submitButton, click: submit, clickBubble: false" >\
                        <input type="button" data-bind="value: labels.cancelButton, click: cancel, clickBubble: false" >\
                    </div>\
                ');

                data.panel.render(document.body);


                // bindings for journal panel of table mode
                // ----------------------------------------

                ko.applyBindings({
                    // header
                    labels: data.localization,
                    filters: data.filters,
                    searchQuery: data.searchQuery,
                    createVariantsVisibility: data.createVariantsVisibility,
                    callback: function(variant) {
                        var scCallback = function(node) {
                            // clear create page
                            var createPage = Dom.get(createPageId);
                            Dom.addClass(createPage, "hidden");
                            createPage.innerHTML = "";

                            // show elements page
                            var elementsPage = Dom.get(elementsPageId);
                            Dom.removeClass(elementsPage, "hidden");

                            // change tab selection
                            var buttons = Dom.getElementsBy(function(element) {
                                return element.className.indexOf("selected") != -1
                              }, "button", journalPickerHeaderId);

                            _.each(buttons, function(element) {
                                element.classList.remove("selected");
                            });
                        };

                        Citeck.forms.formContent(variant.type(), variant.formId(), {
                            response: function(response) {
                                Dom.get(createPageId).innerHTML = response;

                                // hide other pages and remove selection from other tabs
                                // Dom.removeClass(elementsTabId, "selected");
                                Dom.removeClass(filterTabId, "selected");
                                Dom.addClass(elementsPageId, "hidden");
                                Dom.addClass(filterPageId, "hidden");

                                // show create page and hightlight tab
                                Dom.removeClass(createPageId, "hidden");
                                var createButton = Dom.getElementsBy(function(el) {
                                    return el.tagName == "BUTTON";
                                }, "button", journalPickerHeaderId);
                                Dom.addClass(createButton, "selected");
                            },

                            submit: function(node) {
                                self.additionalOptions(_.union(self.additionalOptions(), [node ]));
                                scCallback(node);
                            },
                            cancel: scCallback
                        },
                        {
                            destination: variant.destination(),
                            fieldId: data.name()
                        });

                    },
                    virtualParent: data.virtualParent,
                    createVariantsSource: data.createVariantsSource,
                    scope: data,

                    // filters
                    htmlId: data.id,
                    itemId: data.nodetype,
                    criteriaListShow: data._criteriaListShow,
                    filterVisibility: data._filterVisibility,
                    selectedFilterCriteria: data.selectedFilterCriteria,
                    defaultFilterCriteria: data.defaultFilterCriteria,
                    defaultSearchableAttributes: data.searchableAttributes,
                    selectFilterCriterion: function(data, event) {
                        // clone criterion and add value observables
                        var newCriterion = _.clone(data);
                        newCriterion.value = ko.observable();
                        newCriterion.predicateValue = ko.observable();

                        if (self.selectedFilterCriteria.indexOf(newCriterion) != -1)
                            self.selectedFilterCriteria.push(newCriterion);

                        // hide drop-down menu
                        self._criteriaListShow(!self._criteriaListShow());
                    },
                    applyCriteria: function(data, event) {
                        if (self.selectedFilterCriteria().length == 0) {
                            self.criteria([]);
                            return;
                        }
                        self.criteria(getCriteriaList(self.selectedFilterCriteria(), true));
                    },
                    addFilterCriterion: function(data, event) {
                        self._criteriaListShow(!self._criteriaListShow());
                    },
                    filter: function(data, event) {
                        var filterTab = Dom.get(filterTabId);

                        var filterPage = Dom.get(filterPageId),
                            elementsPage = Dom.get(elementsPageId),
                            createPage = Dom.get(createPageId);

                        if ($(elementsPage).hasClass("hidden")) {
                            $(createPage).addClass("hidden");
                            $(elementsPage).removeClass("hidden");
                        }

                        data.filterVisibility(!data.filterVisibility());

                        // clear tab selection
                        var buttons = Dom.getElementsBy(function(element) {
                            return element.className.indexOf("selected") != -1
                          }, "button", journalPickerHeaderId);

                        _.each(buttons, function(element) {
                          element.classList.remove("selected");
                        });

                        $(filterTab).toggleClass("selected");
                        $(filterPage).toggleClass("hidden");
                    },

                    // pagination
                    maxItems: data.step,
                    page: data.page,
                    hasMore: data.hasMore,
                    previousPage: function(data, event) { data.page(data.page() - 1); },
                    nextPage: function(data, event) { data.page(data.page() + 1); },

                    // body
                    elements: data.visibleOptions,
                    selectedElements: data.selectedElements,
                    multiple: data.multiple,
                    journalType: data.journalType,
                    columns: data.defaultVisibleAttributes,
                    afterSelectionCallback: function(data, event) {
                        if (!self.multiple() && event.type == "dblclick") {
                            self._addValues([ data ]);
                            self.panel.hide();
                            self.selectedElements.removeAll();
                        }
                    },
                    createPageVisibility: data._createPageVisibility,
                    elementPageVisibility: data._elementPageVisibility
                }, data.panel.body);

                ko.applyBindings({
                    labels: data.localization,
                    submit: function(el, data) {
                        self._addValues(ko.utils.unwrapObservable(self.selectedElements));
                        self.panel.hide();
                        self.selectedElements.removeAll();
                    },
                    cancel: function(el, data) {
                        self.panel.hide();
                        self.selectedElements.removeAll();
                    }
                }, data.panel.footer);
            }

            var filterTab = Dom.get(filterTabId);
            if (!data._filterVisibility() && params.defaultFiltersVisibility == "true" && filterTab) {
                filterTab.click();
            }
            data.panel.show();
        }

        // blur
        if (this._listMode) {
            $("body").click(function(event, a) {
                var node = event.target, body = document.getElementById("Share");

                while (node && node != body) {
                    if (node == self.element) return;
                    node = node.parentNode;
                }

                self.containerVisibility(false);
            });
        }

    },
    template:
       '<!-- ko if: _listMode -->\
            <!-- ko if: disabled -->\
                <div class="select2-select disabled" tabindex="0">\
                    <span class="select2-value" data-bind="text: label"></span>\
                    <div class="select2-twister"></div>\
                </div>\
            <!-- /ko -->\
            <!-- ko ifnot: disabled -->\
                <div class="select2-select" tabindex="0"\
                    data-bind="\
                        event: { mousedown: toggleContainer, keydown: keyManagment }, mousedownBubble: false,\
                        css: { opened: containerVisibility },\
                        hasFocus: componentFocused">\
                    <span class="select2-value" data-bind="text: label"></span>\
                    <!-- ko if: value -->\
                        <a class="clear-button" data-bind="event: { mousedown: clear }, mousedownBubble: false">x</a>\
                    <!-- /ko -->\
                    <div class="select2-twister"></div>\
                </div>\
            <!-- /ko -->\
            <!-- ko if: containerVisibility -->\
                <div class="select2-container">\
                    <div class="select2-search-container">\
                        <!-- ko ifnot: Citeck.HTML5.supportAttribute("placeholder") -->\
                            <div class="help-message" data-bind="text: localization.help"></div>\
                        <!-- /ko -->\
                        <input type="text" class="select2-search" data-bind="\
                            textInput: searchQuery,\
                            event: { keydown: keyAction },\
                            keydownBubble: false,\
                            attr: { placeholder: localization.help },\
                            hasFocus: searchFocused">\
                    </div>\
                    <!-- ko if: visibleOptions -->\
                        <!-- ko if: visibleOptions().length > 0 -->\
                            <ul class="select2-list" data-bind="foreach: visibleOptions">\
                                <li data-bind="\
                                    event: { mousedown: $parent.selectItem }, mousedownBubble: false,\
                                    css: { selected: $parent.highlightedElement() == $data }">\
                                    <a data-bind="text: $component._optionTitle($data)"></a>\
                                </li>\
                            </ul>\
                        <!-- /ko -->\
                        <!-- ko if: visibleOptions().length == 0 -->\
                            <span class="select2-message empty-message" data-bind="text: localization.empty"></span>\
                        <!-- /ko -->\
                    <!-- /ko -->\
                    <div class="select2-more" data-bind="style: hasMore() ? \'\' : {display: \'none\'}">\
                        <a data-bind="click: more, attr: { title: localization.more }">...</a>\
                    </div>\
                </div>\
            <!-- /ko -->\
        <!-- /ko -->\
        <!-- ko if: _tableMode -->\
            <button class="select2-control-button" data-bind="disable: disabled, text: localization.select, click: journalPicker"></button>\
        <!-- /ko -->'
});

// -----------
// FILE UPLOAD
// -----------

ko.bindingHandlers.fileUploadControl = {
    init: function (element, valueAccessor, allBindings, data, context) {
        var settings = valueAccessor(),
            value = settings.value,
            multiple = settings.multiple,
            type = settings.type,
            alowedFileTypes = (settings.alowedFileTypes || '').toLowerCase().split(' ').join('').split(','),
            maxSize = settings.maxSize || '',
            maxCount = settings.maxCount || '',
            properties = settings.properties,
            importUrl = settings.importUrl,
            draggable = settings.draggable,
            destination = settings.destination || "workspace://SpacesStore/attachments-root",
            assocType = settings.assocType || "sys:children";

        var uploadFiles = function (files) {
            var loadedFiles = ko.observable(0);

            if (files.length === 0) {
                return;
            }

            if (maxCount > 0 && files.length > maxCount) {
                Alfresco.util.PopupManager.displayPrompt({
                    title: 'Error',
                    text: Alfresco.util.message('file-count-restrict') + ': ' + maxCount
                });
            }

            for (var i = 0, count = files.length; i < count; i++) {
                if (!checkFile(files[i])) {
                    event.target.value = '';
                    return;
                }
            }

            loadedFiles.subscribe(function (newValue) {
                if (newValue == files.length) {
                    // enable button
                    $(element).removeClass("loading");
                    $(openFileUploadDialogButton).removeAttr("disabled");

                    //for reload file
                    $(input).val("");
                }
            });

            // disable upload button
            $(element).addClass("loading");
            $(openFileUploadDialogButton).attr("disabled", "disabled");

            for (var i = 0; i < files.length; i++) {
                var request = new XMLHttpRequest();

                (function (file) {
                    // loading failure.
                    request.addEventListener("error", function (event) {
                        console.log("loaded failure")
                        loadedFiles(loadedFiles() + 1);
                    }, false);

                    // request finished
                    request.addEventListener("readystatechange", function (event) {
                        var target = event.target;
                        if (target.readyState == 4) {
                            var result = JSON.parse(target.responseText || "{}");

                            if (target.status == 200) {
                                // push new file to uploaded files library
                                if (multiple()) {
                                    var currentValues = value();
                                    if (result.strings && result.strings.length) {
                                        result.strings.forEach(function (item) {
                                            currentValues.push(item);
                                        });
                                        value(currentValues);
                                    } else if (result.errorMessage) {
                                        Alfresco.util.PopupManager.displayPrompt({
                                            title: Alfresco.util.message("message.import-errors"),
                                            text: result.errorMessage
                                        });
                                    } else if (result.nodeRef) {
                                        currentValues.push(result.nodeRef);
                                        value(currentValues);
                                    }

                                } else {
                                    //TODO: remove previous node if parent == attachments-root?
                                    value(result.nodeRef);
                                }
                            }

                            if (target.status == 500) {
                                var errorMessage = result.message ? result.message : Alfresco.util.message("message.load-failed");
                                Alfresco.util.PopupManager.displayPrompt({
                                    title: target.statusText,
                                    text: errorMessage
                                });
                            }

                            loadedFiles(loadedFiles() + 1);

                            YAHOO.Bubbling.fire('file-uploaded-' + data.info().name().replace(':', '_'), file);
                        }
                    }, false)
                })(files[i]);

                var formData = new FormData;
                formData.append("filedata", files[i]);
                formData.append("filename", files[i].name);
                formData.append("destination", destination);
                formData.append("siteId", null);
                formData.append("containerId", null);
                formData.append("uploaddirectory", null);
                formData.append("majorVersion", false);
                formData.append("overwrite", false);
                formData.append("thumbnails", null);

                if (properties) {
                    for (var p in properties) {
                        formData.append("property_" + p, properties[p]);
                    }
                }

                var href = Alfresco.constants.PROXY_URI + (importUrl ? importUrl : "api/citeck/upload?assoctype=" + assocType + "&details=true");
                if (type) href += "&contenttype=" + type;

                request.open("POST", href, true);
                request.send(formData);
            }
        };

        if (_.isNumber(maxSize)) {
            maxSize = +maxSize;
        } else {
            maxSize = 0;
        }

        if (_.isNumber(maxCount)) {
            maxCount = +maxCount;
        } else {
            maxCount = 0;
        }

        // Invariants global object
        var Node = koutils.koclass('invariants.Node');

        // check browser support
        if (!window.File && !window.FileList) {
            throw new Error("The File APIs are not supported in this browser.")
            return;
        }

        // elements
        var input = Dom.get(element.id + "-fileInput"),
            openFileUploadDialogButton = Dom.get(element.id + "-openFileUploadDialogButton");
        var $field = $(input).closest('.form-field');

        if (draggable) {
            Event.addListener($field, "dragover", function (e) {
                e.dataTransfer.dropEffect = Math.floor(YAHOO.env.ua.gecko) === 1 ? "move" : "copy";
                e.stopPropagation();
                e.preventDefault();
            }, this, true);
            Event.addListener($field, "dragleave", function (e) {
                e.stopPropagation();
                e.preventDefault();
            }, this, true);
            Event.addListener($field, "drop", function (e) {
                try {
                    if (e.dataTransfer.files !== undefined && e.dataTransfer.files !== null && e.dataTransfer.files.length > 0) {
                        uploadFiles(e.dataTransfer.files);
                    }
                }
                catch (exception) {
                    Alfresco.logger.error("fileUploadControl: The following error occurred when files were dropped onto the Document List: ", exception);
                }
                e.stopPropagation();
                e.preventDefault();
            }, this, true);
        }

        // click on input[file] button
        Event.on(openFileUploadDialogButton, 'click', function (event) {
            $(input).click();
        });

        function checkFile(file) {
            function b2mb(val) {
                if (!val) return '';
                var res = Math.round((val / 1024 / 1024) * 1000) / 1000;
                if (res < 1) {
                    res = Math.round((val / 1024) * 1000) / 1000;
                    return res + ' Kb'
                }
                ;
                return res + ' Mb'
            };

            if (!file) return false;

            var result = true,
                ext = '',
                arr = [];

            // check file type
            arr = file.name.split('.');
            ext = (arr.length > 1 ? arr[arr.length - 1] : "").toLowerCase();

            result = alowedFileTypes[0] == '' || !!~alowedFileTypes.indexOf(ext);
            if (!result) {
                Alfresco.util.PopupManager.displayPrompt({
                    title: 'Error',
                    text: Alfresco.util.message('incorrect-file-type')
                });
                return false;
            }

            // check file size
            if (file.size && file.size > 0 && maxSize > 0) {
                result = file.size <= maxSize;
            } else {
                result = true;
            }

            if (!result) {
                Alfresco.util.PopupManager.displayPrompt({
                    title: 'Error',
                    text: Alfresco.util.message('file-larger-than-allowed') + ' ' + b2mb(maxSize)
                });
                return false;
            }

            return true;
        }

        // get files from input[file]
        Event.on(input, 'change', function (event) {
            uploadFiles(event.target.files);
        });
    }
};

// ---------
// ORGSTRUCT
// ---------

ko.bindingHandlers.orgstructControl = {
    init: function(element, valueAccessor, allBindings, data, context) {
        var self = this;

        // default option
        var options = {
            allowedAuthorityType: "USER",
            allowedGroupType: "",
            allowedGroupSubType: "",
            rootGroup: ko.observable("_orgstruct_home_")
        };

        // from fake model option
        if (data.allowedAuthorityType && data.allowedAuthorityType())
            options.allowedAuthorityType = data.allowedAuthorityType();

        var settings = valueAccessor(),
            value = settings.value,
            multiple = settings.multiple,
            params = allBindings().params() ? allBindings().params() : {};

        var showVariantsButton = Dom.get(element.id + "-showVariantsButton"),
            orgstructPanelId = element.id + "-orgstructPanel", orgstructPanel, resize,
            tree, selectedItems;

        var rootGroupFunction;
        if (!params.rootGroup && params.rootGroupFunction && _.isFunction(params.rootGroupFunction)) {
            rootGroupFunction = ko.computed(params.rootGroupFunction);

            options.rootGroup(rootGroupFunction());

            rootGroupFunction.subscribe(function (newValue) { options.rootGroup(newValue) });
        }

        // concat default and new options
        if (params) concatOptionsWithObservable(options, params);

        Event.on(showVariantsButton, "click", function(event) {
            event.stopPropagation();
            event.preventDefault();

            if (!orgstructPanel) {
                orgstructPanel = new YAHOO.widget.Panel(orgstructPanelId, {
                    width:          "800px",
                    visible:        false,
                    fixedcenter:    true,
                    draggable:      true,
                    modal:          true,
                    zindex:         5,
                    close:          true
                });

                // hide dialog on click 'esc' button
                orgstructPanel.cfg.queueProperty("keylisteners", new YAHOO.util.KeyListener(document, { keys: 27 }, {
                    fn: orgstructPanel.hide,
                    scope: orgstructPanel,
                    correctScope: true
                }));

                var orgstructSearchBoxId = orgstructPanelId + "-searchBox",
                    orgstructSearchId = orgstructPanelId + "-searchInput",
                    orgstructTreeId = orgstructPanelId + "-treePicker",
                    orgstructSubmitButtonId = orgstructPanelId + "-submitInput",
                    orgstructCancelButtonId = orgstructPanelId + "-cancelInput";

                orgstructPanel.setHeader(Alfresco.util.message("orgstruct.picker"));
                orgstructPanel.setBody('\
                    <div class="orgstruct-header">\
                        <div class="orgstruct-search" id="' + orgstructSearchBoxId + '">\
                            <input class="search-input" type="text" value="" id="' + orgstructSearchId + '">\
                            <div class="search-icon"></div>\
                        </div>\
                    </div>\
                    <div class="yui-g orgstruct-layout">\
                        <div class="yui-u first panel-left resizable-panel" id="first-panel">\
                            <div class="orgstruct-tree" id="' + orgstructTreeId + '"></div>\
                        </div>\
                        <div class="yui-u panel-right" id="second-panel">\
                            <ul class="orgstruct-selected-items"></ul>\
                        </div>\
                    </div>\
                ');
                orgstructPanel.setFooter('\
                    <div class="buttons">\
                        <input type="submit" value="' + params.submitButtonTitle + '" id="' + orgstructSubmitButtonId + '">\
                        <input type="button" value="' + params.cancelButtonTitle + '" id="' + orgstructCancelButtonId + '">\
                    </div>\
                ');

                orgstructPanel.render(document.body);

                // initialize resize
                resize = new YAHOO.util.Resize("first-panel", {
                    handles: ['r'],
                    minWidth: 200,
                    maxWidth: 600
                });

                resize.on('resize', function(ev) {
                    Dom.setStyle(secondPanel, 'width', (800 - ev.width - 10) + 'px');
                });

                //  initialize tree
                var tree = new YAHOO.widget.TreeView(orgstructTreeId),
                    firstPanel = Dom.get("first-panel"),
                    secondPanel = Dom.get("second-panel");

                selectedItems = Dom.getElementsByClassName("orgstruct-selected-items", tree.body)[0];

                // initialize tree function
                tree.fn = {
                    loadNodeData: function(node, fnLoadComplete) {
                        YAHOO.util.Connect.asyncRequest('GET', tree.fn.buildTreeNodeUrl(node.data.shortName, null, params.excludeAuthorities), {
                            success: function (oResponse) {
                                var results = YAHOO.lang.JSON.parse(oResponse.responseText), item, treeNode;
                                if (results) {
                                    for (var i = 0; i < results.length; i++) {
                                        item = results[i];

                                        treeNode = this.buildTreeNode(item, node, false);
                                        if (item.authorityType == "USER") {
                                            treeNode.isLeaf = true;
                                        }
                                    }
                                }

                                oResponse.argument.fnLoadComplete();
                            },

                            failure: function(oResponse) {
                                // error
                            },

                            scope: tree.fn,
                            argument: {
                                "node": node,
                                "fnLoadComplete": fnLoadComplete
                            }
                        });
                    },

                    loadRootNodes: function(tree, scope, query) {
                        YAHOO.util.Connect.asyncRequest('GET', tree.fn.buildTreeNodeUrl(options.rootGroup(), query, params.excludeAuthorities), {
                            success: function(oResponse) {
                                var results = YAHOO.lang.JSON.parse(oResponse.responseText),
                                    rootNode = tree.getRoot(), treeNode,
                                    expanded = true;

                                if (results) {
                                    tree.removeChildren(rootNode);

                                    if (results.length > 1) expanded = false;
                                    for (var i = 0; i < results.length; i++) {
                                        treeNode = this.buildTreeNode(results[i], rootNode, expanded);
                                        if (results[i].authorityType == "USER") {
                                            treeNode.isLeaf = true;
                                        }
                                    }
                                }

                                tree.draw();
                            },

                            failure: function(oResponse) {
                                //draw empty tree, if group not found
                                if (oResponse.status == 404) {
                                    tree.removeChildren(tree.getRoot());
                                    tree.draw();
                                }
                            },

                            scope: tree.fn
                        });
                    },

                    buildTreeNode: function(p_oItem, p_oParent, p_expanded) {
                        var authorityType = p_oItem.authorityType;
                        var groupType = p_oItem.groupType;
                        var textNode = new YAHOO.widget.TextNode({
                                label: $html(p_oItem[tree.fn.getNodeLabelKey(p_oItem)]) || p_oItem.displayName || p_oItem.shortName,
                                nodeRef: p_oItem.nodeRef,
                                shortName: p_oItem.shortName,
                                displayName: p_oItem.displayName,
                                fullName: p_oItem.fullName,
                                authorityType: authorityType,
                                groupType: groupType,
                                groupSubType: p_oItem.groupSubType,
                                available: p_oItem.available,
                                editable : false
                        }, p_oParent, p_expanded);

                        // add nessessary classes
                        if (authorityType) {
                            textNode.contentStyle += " authorityType-" + authorityType;
                            textNode.contentStyle += " available-" + p_oItem.available;
                        }
                        if (groupType) {
                            textNode.contentStyle += " groupType-" + groupType.toUpperCase();
                        }

                        // selectable elements
                        if (options.allowedAuthorityType.indexOf(authorityType) !== -1) {
                            if (authorityType === "GROUP") {
                                if (!options.allowedGroupType || options.allowedGroupType.indexOf(groupType.toUpperCase()) !== -1) {
                                    if (!options.allowedGroupSubType || options.allowedGroupSubType.indexOf(p_oItem.groupSubType.toUpperCase()) !== -1) {
                                        textNode.className = "selectable";
                                    }
                                }
                            }

                            if (authorityType === "USER") {
                                textNode.className = "selectable";
                            }
                        }

                        return textNode;
                    },

                    buildTreeNodeUrl: function (group, query, excludeAuthorities) {
                        var uriTemplate ="api/orgstruct/v2/group/" + Alfresco.util.encodeURIPath(group) + "/children?branch=true&role=true&group=true&user=true";
                        if (query) {
                            uriTemplate += "&filter=" + encodeURI(query) + "&recurse=true";
                        }
                        if (excludeAuthorities) {
                            uriTemplate += "&excludeAuthorities=" + excludeAuthorities;
                        }
                        return  Alfresco.constants.PROXY_URI + uriTemplate;
                    },

                    onNodeClicked: function(args) {
                        var textNode = args.node,
                            object = textNode.data,
                            event = args.event;

                        var existsSelectedItems = [];

                        $("li.selected-object", this.selectedItems).each(function() {
                            existsSelectedItems.push(this.id);
                        });

                        // return if element exists
                        if (existsSelectedItems.indexOf(textNode.data.nodeRef) !== -1) return false;

                        if (options.allowedAuthorityType.indexOf(object.authorityType) !== -1) {
                            if (object.authorityType === "GROUP") {
                                if ((options.allowedGroupType && options.allowedGroupType.indexOf(object.groupType.toUpperCase()) === -1) ||
                                (options.allowedGroupSubType && options.allowedGroupSubType.indexOf(object.groupSubType.toUpperCase()) === -1)) {
                                        return false;
                                    }
                                }


                            if (options.nodeSelectConstraintCallback) {
                                if (!options.nodeSelectConstraintCallback(textNode, options.context)) { return false; }
                            }


                            if (existsSelectedItems.length === 0 || (existsSelectedItems.length > 0 && multiple())) {
                                $(this.selectedItems).append(createSelectedObject({
                                    id: object.nodeRef,
                                    label: object[tree.fn.getNodeLabelKey(object)] || object.displayName,
                                    aType: textNode.data.authorityType,
                                    gType: textNode.data.groupType,
                                    available: textNode.data.available
                                }));

                                // remove selectable state
                                $("table.selectable", textNode.getEl())
                                    .first()
                                    .removeClass("selectable")
                                    .addClass("unselectable selected");

                                return false;
                            }
                        }

                        return false;
                    },

                    onSearch: function(event) {
                        if(event.which == 13) {
                            event.stopPropagation();

                            var input = event.target,
                                query = input.value;

                            if (query.length > 1) {
                                this.fn.loadRootNodes(this, this.fn, query)
                            } else if (query.length == 0) {
                                this.fn.loadRootNodes(this, this.fn)
                            }
                        }
                    },

                    getNodeLabelKey: function(node) {
                        var label = "";
                        if (params.labels) {
                            switch (node.authorityType) {
                                case "USER":
                                    label = params.labels["USER"];
                                    break;

                                case "GROUP":
                                    label = params.labels["GROUP"];
                                    break;
                            }
                        }
                        return label;
                    }
                };

                 // initialise treeView
                tree.setDynamicLoad(tree.fn.loadNodeData);
                tree.fn.loadRootNodes(tree, tree);

                // Register tree-level listeners
                tree.subscribe("clickEvent", tree.fn.onNodeClicked, {
                    selectedItems: selectedItems,
                    multiple: multiple
                }, true);

                // search listener
                Event.addListener(orgstructSearchId, "keypress", tree.fn.onSearch, tree, true);

                // value subscribe
                value.subscribe(function(newValue) {
                    clearUnselectedElements(tree);
                    updatedControlValue(newValue, selectedItems, tree);
                });

                // update tree after change rootGroup
                options.rootGroup.subscribe(function(newValue) {
                    tree.fn.loadRootNodes(tree, tree);
                });

                // second panel delete listener
                Event.addListener(secondPanel, "click", function(event) {
                    if (event.target.tagName == "LI") {
                        var node = tree.getNodeByProperty("nodeRef", event.target.id);
                        if (node) { $("table", node.getEl()).first().removeClass("selected unselectable").addClass("selectable") };
                        $(event.target).remove();
                    }
                });


                // panel button listentes
                Event.addListener(orgstructSubmitButtonId, "click", function(event) {
                    this.hide();

                    var selectedItemsNodeRefs = [];

                    $("li.selected-object", selectedItems).each(function(index) {
                        selectedItemsNodeRefs.push(this.id);
                    });

                    if (selectedItemsNodeRefs.length > 0) {
                        value(selectedItemsNodeRefs);
                    } else {
                        value(null);
                    }
                }, orgstructPanel, true);

                Event.addListener(orgstructCancelButtonId, "click", function(event) {
                    this.hide();
                    clearUnselectedElements(tree);
                    updatedControlValue(value(), selectedItems, tree);
                }, orgstructPanel, true);

                // for first run
                updatedControlValue(value(), selectedItems, tree);
            }

            orgstructPanel.show();
        })
    }
}

// ----------------
// PRIVATE FUNCTION
// ----------------

function getCriteriaList(selectedFilterCriteria, isSelect2) {
    var filteredCriteria = _.filter(selectedFilterCriteria, function(criteria) { //criteria chosen by the user
        if (criteria.predicateValue() && criteria.predicateValue().indexOf("empty") != -1) {
            return criteria.predicateValue() && criteria.name();
        }
        return criteria.value() && criteria.predicateValue() && criteria.name();
    });

    var criteriaList = [];

    _.each(filteredCriteria, function(criteria) {
        var criteriaValue                     = criteria.value(),
            predicateValue                    = criteria.predicateValue(),
            criteriaName                      = criteria.name(),
            separator                         = criteria.separator(),
            allowableMultipleFilterPredicates = criteria.allowableMultipleFilterPredicates();

        if (criteriaValue &&
            criteria.allowMultipleFilterValue() &&
            criteriaValue.indexOf(separator) != -1 &&
            allowableMultipleFilterPredicates.indexOf(predicateValue) != -1) {

            var values = _.uniq(_.map((criteriaValue.split(separator)).filter(Boolean), function (value) {
                return trim(value);
            }));

            if (isSelect2) {
                criteriaList.push({
                    attribute: criteriaName,
                    predicate: predicateValue,
                    allowMultipleFilterValue: true,
                    value: values
                });
            } else {
                _.each(values, function(value) {
                    criteriaList.push({
                        attribute: criteriaName,
                        predicate: predicateValue,
                        value: value
                    });
                });
            }

        } else {
            criteriaList.push({
                attribute: criteriaName,
                predicate: predicateValue,
                value: criteriaValue
            });
        }
    });
    return criteriaList;
}

function clearUnselectedElements(tree) {
    $("table.unselectable.selected", tree.getEl())
        .removeClass("unselectable selected")
        .addClass("selectable");
}

function createSelectedObject(options) {
    if (!options.id || !options.label) {
        throw new Error("Required parameters not found");
        return;
    }

    var li = $("<li>", { "class": "selected-object", html: options.label, id: options.id });
    li.click(function() { $(this).remove() });

    if (options.aType) {
        li.addClass("authorityType-" + options.aType);
        li.addClass("available-" + options.available);
    }
    if (options.gType) li.addClass("groupType-" + options.gType.toUpperCase());

    return li;
}

function updatedControlValue(valueObject, ulSelected, tree) {
    $(ulSelected).html("");

    if (valueObject) {
        var valueArray = [];

        if (typeof valueObject == "object") {
            if (valueObject instanceof Array) {
                for (var i in valueObject) {
                    if (_.isObject(valueObject[i]) && valueObject[i].toString().indexOf("invariatns.Node") != -1) {
                        valueArray.push(valueObject[i].nodeRef)
                    } else if (_.isString(valueObject[i]) && Citeck.utils.isNodeRef(valueObject[i])) {
                        valueArray.push(valueObject[i]);
                    }
                }
            } else {
                valueArray.push(valueObject.nodeRef);
            }
        }

        if (typeof valueObject == "string") {
            valueArray = valueObject.split(",");
        }

        for (var i in valueArray) {
            YAHOO.util.Connect.asyncRequest("GET", Alfresco.constants.PROXY_URI + "api/orgstruct/authority?nodeRef=" + valueArray[i],
                {
                    success: function(response) {
                        var results = YAHOO.lang.JSON.parse(response.responseText);
                        if (results) {
                            var newLi = createSelectedObject({
                                    id: results.nodeRef,
                                    label: results[tree.fn.getNodeLabelKey(results)] || results.displayName,
                                    aType: results.authorityType,
                                    gType: results.groupType
                                }),
                                existFlag = false,
                                textNode = tree.getNodeByProperty("nodeRef", results.nodeRef);

                            $(ulSelected)
                                .children()
                                .each(function(index) {
                                    if ($(this).attr("id") == newLi.attr("id")) { existFlag = true; }
                                });

                            if (!existFlag) {
                                $(ulSelected).append(newLi);
                            }

                            $("table.selectable", textNode.getEl())
                                .first()
                                .removeClass("selectable")
                                .addClass("unselectable selected");
                        }
                    },

                    failure: function(response) {
                    // error
                },
                scope: self
            });
        }
    }
}

function updateList(list, eachCallback) {
    var newList = [];
    if (list && list.length > 0) {
        for (var i in list) {
            newList[i] = eachCallback(list[i]);
        }
    }

    return newList;
}

function deleteNode(nodeRef, callback) {
    YAHOO.util.Connect.asyncRequest('DELETE',
                                    Alfresco.constants.PROXY_URI + "citeck/node?nodeRef=" + nodeRef,
                                    callback);
}

function printableLongFileName(name, limit) {
    if (!name) {
        throw new Error("Does not exist 'name' argument");
        return null;
    }

    if (!limit) {
        throw new Error("Does not exist 'limit' argument");
        return null;
    }

    if (name.length > limit) {
        var startName = name.slice(0, limit/2-2),
            endName = name.slice(-(limit/2-2));

        return startName + "..." + endName;
    }

    return name;
}

function truncate(string, limit) {
    if (!string) {
        throw new Error("Does not exist 'string' argument");
        return null;
    }

    if (!limit) {
        throw new Error("Does not exist 'limit' argument");
        return null;
    }

    if (string.length > limit) {
        return string.slice(0, limit-3) + "..."
    }

    return string;
}

function getAttribute(node, attributeName) {
    var impl = node.impl(),
        attribute = impl.attribute(attributeName);
    return attribute && attribute.value() || null;
}

function getAttributeObservable(node, attributeName) {
    var attributeValueObservable = ko.observable();

    node.impl().attributes.subscribe(function() {
        attributeValueObservable(getAttribute(node, attributeName));
    })

    return attributeValueObservable;
}

function attributeValue(node, attributeName, callback) {
    var attributeValue = getAttribute(node, attributeName);
    if (attributeValue) {
       callback(attributeValue);
    } else {
        getAttributeObservable(node, attributeName).subscribe(function(newAttributeValue) {
            callback(newAttributeValue);
        })
    }
}

function trim(string) {
    return String(string).replace(/^\s+|\s+$/g, '');
}

function sortingCreateVariants(variants) {
    var defaultVariantIndex,
        defaultVariant;

    for (var i in variants) {
        if (variants[i].isDefault) {
            defaultVariantIndex = i;
            break;
        }
    }

    if (defaultVariantIndex) {
        defaultVariant = variants.splice(defaultVariantIndex, 1);
        variants.splice(0, 0, defaultVariant[0]);
    }
}

function concatOptionsWithObservable(defaultOptions, newOptions) {
    for (var key in newOptions) {
        var newValue = newOptions[key],
            oldValue = defaultOptions[key];

        if (newValue && newValue != oldValue) {
            if (ko.isObservable(oldValue)) {
                if (oldValue() != newValue) defaultOptions[key](newOptions[key]);
            } else {
               defaultOptions[key] = newOptions[key];
            }
        }
    }
}

})
