/*
 * Copyright (C) 2016 Citeck LLC.
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

define(['lib/knockout'], function(ko) {


    var koValue = function(value) {
        return typeof value == "function" ? value() : value;
    };

    var Get = YAHOO.util.Get;


    // COMPONENTS
    // ----------

    ko.components.register("filter-criterion-value", {
        viewModel: function(params) {
            var self = this;
            initializeParameters.call(this, params);

            this.html = ko.observable("");
            this.valueContainerId = this.fieldId + "-value";

            this.nestedViewModel = {
                "fieldId": this.fieldId,

                "mandatory": ko.observable(false),
                "protected": ko.observable(false),
                "multiple": ko.observable(false),
                "relevant": ko.observable(true),

                "value": this.value,

                "options": ko.observable([]),
                "optionsText": function(o) { return o.attributes["cm:name"]; },
                "optionsValue": function(o) { return o.nodeRef; }
 
            }

            if (this.datatype) {
                this.templateName = defineTemplateByDatatype(this.datatype);

                if (this.datatype == "association" && this.nodetype()) {
                    var query = {
                        field_1: "type",
                        predicate_1: "type-equals",
                        value_1: this.nodetype(),
                        skipCount: 0,
                        maxItems: 10,
                        sortBy: []
                    };

                    Alfresco.util.Ajax.jsonPost({
                        url: Alfresco.constants.PROXY_URI + "search/criteria-search",
                        dataObj: query,
                        successCallback: {
                            scope: this,
                            fn: function(response) { 
                                this.nestedViewModel.options(response.json ? response.json.results : []);
                            }
                        }
                    });
                }

                Alfresco.util.Ajax.request({
                    url: Alfresco.constants.URL_PAGECONTEXT + "citeck/components/region/get-region?fieldId=" + this.fieldId + "&template=" + this.templateName,
                    successCallback: {
                        scope: this,
                        fn: function(response) {
                            this.html(prepareHTMLByTemplate(response.serverResponse.responseText));
                        }
                    }
                });
            }

            this.html.subscribe(function(newValue) {            
                var zeroEl = $("<div>", { html: newValue });
                $("#" + self.valueContainerId).append(zeroEl);

                ko.cleanNode(zeroEl);
                ko.applyBindings(self.nestedViewModel, zeroEl[0]);
            });
        }, 
        template: 
           '<div class="criterion-value" data-bind="attr: { id: valueContainerId }"></div>'
    });


    // TODO:
    // - refactoring 'filter-criteria' and 'list-of-selected-criterion'. Combine methods

    ko.components.register("filter-criteria", {
        viewModel: function(params) {
            var self = this;
            initializeParameters.call(this, params);
      
            this.remove = function(data, event) {
                self.filter().criteria.remove(data);
            }

            this.nodetype = function(data) {
                return ko.computed(function() {
                    return self.journalType.attribute(data.resolve("field.name", null)).nodetype();
                });
            }
        },
        template: 
           '<div class="filter-criteria" data-bind="\
                attr: { id: id + \'-filter-criteria\' },\
                foreach: filter().criteria()\
            ">\
                <div class="criterion">\
                    <div class="criterion-actions">\
                        <a class="criterion-remove"\
                           data-bind="click: $component.remove,\
                                      attr: { title: Alfresco.util.message(\'button.remove-criterion\') }\
                        "></a>\
                    </div>\
                    <div class="criterion-field" data-bind="with: field">\
                        <input type="hidden" data-bind="attr: { name: \'field_\' + $parent.id() }, value: name" />\
                        <label data-bind="text: displayName"></label>\
                    </div>\
                    <div class="criterion-predicate">\
                        <!-- ko if: resolve(\'field.datatype.predicates.length\', 0) == 0 -->\
                            <input type="hidden" data-bind="attr: { name: \'predicate_\' + id() }, value: predicate().id()" />\
                        <!-- /ko -->\
                        <!-- ko if: resolve(\'field.datatype.predicates.length\', 0) > 0 -->\
                            <select data-bind="attr: { name: \'predicate_\' + id() },\
                                               value: predicate,\
                                               options: resolve(\'field.datatype.predicates\'),\
                                               optionsText: \'label\'\
                            "></select>\
                        <!-- /ko -->\
                    </div>\
                    <!-- ko component: { name: "filter-criterion-value", params: {\
                        fieldId: $component.id + "-criterion-" + id(),\
                        datatype: resolve(\'field.datatype.name\', null),\
                        nodetype: $component.nodetype($data),\
                        value: value\
                    }} --><!-- /ko -->\
                </div>\
            </div>'
    });

    ko.components.register('list-of-selected-criterion', {
        viewModel: function(params) {
            var self = this;
            initializeParameters.call(this, params);

            self.remove = function(data, event) {
                self.selectedFilterCriteria.remove(data);
            };

            this.nodetype = function(data) {
                return ko.computed(function() {
                    return self.journalType.attribute(data.name()).nodetype();
                });
            }
        },
        template: 
           '<table class="selected-criteria-list">\
                <tbody>\
                    <!-- ko foreach: selectedFilterCriteria -->\
                        <tr>\
                            <td class="action-col"><a class="remove-selected-criterion" data-bind="click: $component.remove">X</a></td>\
                            <td class="name-col"><span class="selected-criterion-name" data-bind="text: displayName"></span></td>\
                            <td class="predicate-col" data-bind="with: datatype">\
                                <select class="predicate" data-bind="options: predicates,\
                                                                     optionsText: \'label\',\
                                                                     optionsValue: \'id\',\
                                                                     value: $parent.predicateValue"></select>\
                            </td>\
                            <td class="value-col">\
                                <!-- ko component: { name: "filter-criterion-value", params: {\
                                    fieldId: $component.htmlId + "-criterion-" + $index(),\
                                    datatype: resolve(\'datatype.name\', null),\
                                    nodetype: $component.nodetype($data),\
                                    value: value\
                                }} --><!-- /ko -->\
                            </td>\
                        </tr>\
                    <!-- /ko -->\
                </tbody>\
            </table>'
    });

    ko.components.register('journal', {
        viewModel: function(params) {
            if (!params.sourceElements && !params.journalType) {
                throw new Error("Required parameters are missing");
                return;
            }

            var self = this;
            initializeParameters.call(this, params);

            // options
            self.options = {
                multiple: false,
                pagination: false,
                loading: false,
                localization: { 
                    nextPageLabel: "-->", 
                    nextPageTitle: "-->", 
                    previousPageLabel: "<--", 
                    previousPageTitle: "<--" 
                } 
            };
            Citeck.utils.concatOptions(self.options, params.options);

            if (!self.loading) { self.loading = self.options.loading; }

            // methods
            self.selectElement = function(data, event) {
                if (self.targetElements) {
                    if (self.options.multiple && (ko.isObservable(self.options.multiple) ? self.options.multiple() : self.options.multiple)) {
                        if (self.targetElements.indexOf(data) == -1) self.targetElements.push(data);
                    } else {
                        self.targetElements([data]);
                    }
                };

                if (self.callback) self.callback(data, event);
            };

            self.nextPage = function(data, event) {
                self.page(self.page() + 1);
            };

            self.previousPage = function(data, event) {
                self.page(self.page() - 1);
            };

            self.displayText = function(value, attr) {
                if (value) {
                    // if string
                    if (typeof value == "string") {
                        if (attr.labels() && attr.labels()[value]) 
                            return attr.labels()[value];
                    }

                    // if object
                    if (typeof value == "object") {
                        if (value instanceof Date) return value.toLocaleString();
                        if (isInvariantsObject(value)) return value.name;
                    }

                    return value;
                }

                return null;
            };
        },
        template:
           '<!-- ko if: loading -->\
                <div class="loading"></div>\
            <!-- /ko -->\
            <table class="journal">\
                <thead>\
                    <!-- ko if: columns ? true : false -->\
                        <tr data-bind="foreach: columns">\
                            <!-- ko if: $component.journalType.attribute($data) -->\
                                <!-- ko with: $component.journalType.attribute($data) -->\
                                    <th data-bind="text: displayName"></th>\
                                <!-- /ko -->\
                            <!-- /ko -->\
                        </tr>\
                    <!-- /ko -->\
                    <!-- ko ifnot: columns ? true : false -->\
                        <tr data-bind="foreach: $component.journalType.defaultAttributes">\
                            <th data-bind="text: displayName"></th>\
                        </tr>\
                    <!-- /ko -->\
                </thead>\
                <tbody data-bind="foreach: sourceElements">\
                    <!-- ko if: $component.columns ? true : false -->\
                        <tr class="journal-element" data-bind="attr: { id: nodeRef },\
                                                               foreach: $component.columns,\
                                                               click: $component.selectElement, clickBubble: false">\
                           <!-- ko if: $component.journalType.attribute($data) ? true : false -->\
                                <!-- ko with: $component.journalType.attribute($data) -->\
                                    <!-- ko if: $parents[1].properties[$data.name()] -->\
                                        <td data-bind="text: $component.displayText($parents[1].properties[$data.name()], $data)"></td>\
                                    <!-- /ko -->\
                                    <!-- ko ifnot: $parents[1].properties[$data.name()] -->\
                                        <!-- ko with: $parents[1].impl().attribute($data.name()) -->\
                                            <td data-bind="text: $data.valueTitle() || $data.textValue()"></td>\
                                        <!-- /ko -->\
                                    <!-- /ko -->\
                                <!-- /ko -->\
                            <!-- /ko -->\
                        </tr>\
                    <!-- /ko -->\
                    <!-- ko ifnot: $component.columns ? true : false -->\
                        <tr class="journal-element" data-bind="attr: { id: nodeRef },\
                                                               foreach: $component.journalType.defaultAttributes,\
                                                               click: $component.selectElement, clickBubble: false">\
                            <!-- ko if: $parent.properties[$data.name()] -->\
                                <td data-bind="text: $component.displayText($parent.properties[$data.name()], $data)"></td>\
                            <!-- /ko -->\
                            <!-- ko ifnot: $parent.properties[$data.name()] -->\
                                <!-- ko with: $parent.impl().attribute($data.name()) -->\
                                    <td data-bind="text: $data.valueTitle() || $data.textValue()"></td>\
                                <!-- /ko -->\
                            <!-- /ko -->\
                        </tr>\
                    <!-- /ko -->\
                </tbody>\
            </table>\
            <!-- ko if: options.pagination && sourceElements -->\
                <!-- ko with: sourceElements().pagination -->\
                    <!-- ko if: ($component.page() - 1 > 0) || hasMore -->\
                        <div class="journal-pagination">\
                            <span class="previous-page">\
                                <!-- ko if: $component.page() - 1 > 0 -->\
                                    <a data-bind="click: $component.previousPage,\
                                                  text: $component.options.localization.previousPageLabel,\
                                                  attr: { title: $component.options.localization.previousPageTitle }"><--</a>\
                                <!-- /ko -->\
                                <!-- ko ifnot: $component.page() - 1 > 0 -->\
                                    <!-- ko text: $component.options.localization.previousPageLabel --><!-- /ko -->\
                                <!-- /ko -->\
                            </span>\
                            <span class="page-label">\
                                <span class="start-page" data-bind="text: $component.page() * maxItems - maxItems + 1"></span>\
                                <span class="dash">-</span>\
                                <span class="end-page" data-bind="text: $component.page() * maxItems"></span>\
                            </span>\
                            <span class="next-page">\
                                <!-- ko if: hasMore -->\
                                    <a data-bind="click: $component.nextPage,\
                                                  text: $component.options.localization.nextPageLabel,\
                                                  attr: { title: $component.options.localization.nextPageTitle }">--></a>\
                                <!-- /ko -->\
                                <!-- ko ifnot: hasMore -->\
                                    <!-- ko text: $component.options.localization.nextPageLabel --><!-- /ko -->\
                                <!-- /ko -->\
                            </span>\
                        </div>\
                    <!-- /ko -->\
                <!-- /ko -->\
            <!-- /ko -->'
    });


    // BINDINGS
    // ----------

    ko.bindingHandlers.templateSetter = (function() {
        var updateTemplate = function(element, valueAccessor) {
            var cfg = valueAccessor(),
                url = cfg.url,
                fieldName = cfg.field,
                templateName = cfg.name;
            if(typeof templateName != "function") {
                return;
            }
            
            var templateEl = valueAccessor.templateEl;
            if(!templateEl) {
                templateEl = document.createElement("SCRIPT");
                templateEl.id = Alfresco.util.generateDomId();
                templateEl.type = "html/template";
                element.appendChild(templateEl);
                valueAccessor.templateEl = templateEl;
            }
            
            // once template is set, no change is needed
            if(templateName() == templateEl.id) {
                return;
            }
            
            // if it is loading - no more requests permited
            if(valueAccessor.loading) {
                return;
            }
            
            Alfresco.util.Ajax.request({
                url: url,
                execScripts: true,
                successCallback: {
                    scope: this,
                    fn: function(response) {
                        var html = response.serverResponse.responseText;
                        
                        // support value bindings
                        if(fieldName) {
                            html = _.reduce([
                                    'name="' + fieldName + '"', 
                                    "name='" + fieldName + "'", 
                                    'name="' + fieldName + '_added"', 
                                    "name='" + fieldName + "_added'"], 
                                function(html, pattern) {
                                    return html.replace(new RegExp('(' + pattern + ')', 'gi'), '$1 data-bind="value: value"');
                                }, html);
                        }
                        
                        templateEl.innerHTML = html;
                        templateName(templateEl.id);
                        valueAccessor.loading = false;
                    }
                },
                failureCallback: {
                    scope: this,
                    fn: function(response) {
                        valueAccessor.loading = false;
                    }
                }
            });
            valueAccessor.loading = true;
        };
        return {
            init: updateTemplate,
            update: updateTemplate
        };
    })();

    ko.bindingHandlers.gotoAddress = (function() {
        var gotoAddress = function(element, valueAccessor) {
            var value = koValue(valueAccessor());
            if(value) window.location = Alfresco.util.siteURL(value);
        };
        return {
            init: gotoAddress,
            update: gotoAddress
        };
    })();

    ko.virtualElements.allowedBindings.gotoAddress = true;

    ko.bindingHandlers.dependencies = (function() {
        var updateDependencies = function(element, valueAccessor) {
            if(!element.currentDeps) element.currentDeps = {};
            var currentDeps = element.currentDeps,
                oldDeps = _.keys(currentDeps),
                newDeps = koValue(valueAccessor());
            
            // old dependencies;
            _.each(_.difference(oldDeps, newDeps), function(dep) {
                if(typeof currentDeps[dep].purge == "function") {
                        currentDeps[dep].purge();
                }
                delete currentDeps[dep];
            });
            
                
            // new dependencies
            _.each(_.difference(newDeps, oldDeps), function(dep) {
                var config = {
                    data: dep,
                    onSuccess: function(o) {
    //                  valueAccessor().notifySubscribers(o.data, "loaded")
                        currentDeps[o.data] = o;
                    }
                };
                if(dep.match(/\.js$/)) {
                    Get.script(Alfresco.constants.URL_RESCONTEXT + dep, config);
                } else if(dep.match(/\.css$/)) {
                    Get.css(Alfresco.constants.URL_RESCONTEXT + dep, config);
                } else {
                    logger.warn("Unknown dependency type: " + dep);
                }
            });
        };
        return {
            init: updateDependencies,
            update: updateDependencies
        };
    })();

    ko.virtualElements.allowedBindings.dependencies = true;



    // FUNCTIONS LIBRARY
    //-------------------
    
    function initializeParameters(params) {
        for (var p in params) { this[p] = params[p] }
    }

    function defineTemplateByDatatype(datatype) {
        var templateName =  _.contains(["text", "date", "datetime"], datatype) ? datatype : "";
        if (!templateName) {
            switch (datatype) {
                case "association":
                    templateName = "select";
                    break;
                case "float":
                case "long":
                case "int":
                case "double":
                    templateName = "number";
                    break;
                case "mltext":
                default:
                    templateName = "text";
                    break;                 
            }
        } 
        return templateName;
    }

    function prepareHTMLByTemplate(html) {
        var fixedHTML = html.replace("textInput: textValue", "value: value");
        fixedHTML = fixedHTML.replace("value: textValue", "value: value");
        fixedHTML = fixedHTML.replace("optionsText: function(item) { return getValueTitle(item) }", 
                                      "optionsText: optionsText, optionsValue: optionsValue");
        return fixedHTML;
    }

    function isInvariantsObject(object) {
        return object.toString().toLowerCase().indexOf("invariants") != -1
    }



});


