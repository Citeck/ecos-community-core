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
define([
    'citeck/components/dynamic-tree/error-manager',
    'ecosui!ecos-records'
], function() {

    Citeck = typeof Citeck != "undefined" ? Citeck : {};
    Citeck.util = Citeck.util || {};

    /**
     * Abstract Model
     * Has the ability to store items and their properties and child items.
     */
    Citeck.util.AbstractModel = function(name) {
        Citeck.util.AbstractModel.superclass.constructor.call(this, name || "Citeck.util.AbstractModel");

        this.items = {};
        this.config = {};
    };

    YAHOO.extend(Citeck.util.AbstractModel, Alfresco.component.Base);
    YAHOO.lang.augmentObject(Citeck.util.AbstractModel.prototype, YAHOO.util.EventProvider.prototype);
    YAHOO.lang.augmentObject(Citeck.util.AbstractModel.prototype, Citeck.util.ErrorManager.prototype);
    YAHOO.lang.augmentObject(Citeck.util.AbstractModel.prototype, {

        /**
         * Set model configuration.
         * It results in model initializing.
         * @param config
         * {
         *   formats: {
         *     formatName: {
         *       name: "item-name-with-{placeholders}",
         *       keys: [ "item-key-with-{placeholders}, ... ]
         *     },
         *     ...
         *   },
         *   item: {
         *     "item-key": {
         *       format: "formatName-of-item",
         *       get: "/item/get/url/with/{placeholders}",
         *     },
         *     ...
         *   },
         *   children: {
         *     "item-key": {
         *       format: "formatName-of-children",
         *       get: "/children/get/url/with/{placeholders}",
         *       add: "/children/add/url/with/{parent.placeholders}/and/{item.placeholders}",
         *       delete: "/children/delete/url/with/{parent.placeholders}/and/{item.placeholders}",
         *     },
         *     ...
         *   },
         *   titles: {
         *     "item-key": "item-title-with-{placeholders}",
         *     ...
         *   },
         * }
         * Data should be loaded from specified urls in JSON format.
         * Placeholders can be used to dynamically calculate fields from item.
         */
        setConfig: function(config) {
            this.config = config;
            this._initModel();
        },

        createSpecialItem: function(item, format) {
            this._fillModel([item], format);
        },

        /**
         * Get item from model.
         * @param item - it can be item name, item from model, or item created externally
         */
        getItem: function(item) {
            if(typeof item == "string") {
                return this.items[item];
            } else if(typeof item == "object") {
                return this.items[item._item_name_];
            } else {
                throw "Unknown item type: " + (typeof item);
            }
        },

        /**
         * Get items by property value
         * @param name - property name
         * @param value - property value
         */
        getItemsByProperty: function(name, value) {
            var results = [];
            for(var i in this.items) {
                if(!this.items.hasOwnProperty(i)) continue;
                if(this.items[i][name] == value) {
                    results.push(this.items[i]);
                }
            }
            return results;
        },

        /**
         * Get name of item.
         * @param item - it can be item name, item from model, or item created externally
         */
        getItemName: function(item) {
            item = this.getItem(item);
            return item ? item._item_name_ : null;
        },

        /**
         * Get title of item.
         * @param item - it can be item name, item from model, or item created externally
         */
        getItemTitle: function(item) {
            return this.getItemProperty(item, this.config.titles, true);
        },

        /**
         * Get property of item from a keyed config.
         * @param item - it can be item name, item from model, or item created externally
         * @param config - map of possible values
         * {
         *   "item-key": property-value,
         *   ...
         * }
         * Property values can be any objects: arrays, maps, strings, numbers, ...
         * @param substitute - if property is a string, perform substitution of {placeholders}
         */
        getItemProperty: function(item, config, substitute) {
            item = this.getItem(item);
            if(item == null) return null;
            if(!config) return null;
            var keys = item._item_keys_;
            var value = null;
            for(var i in keys) {
                if(!keys.hasOwnProperty(i)) continue;
                if(keys[i] in config) {
                    value = config[keys[i]];
                    break;
                }
            }
            if(!value) value = config[""];
            if(value && substitute) value = this.renderTemplate(item, value);
            return value;
        },

        /**
         * Render template from item.
         */
        renderTemplate: function(item, template) {
            var templates = this.getTemplatesWithDots(template),
                methodMap = this.splitTemplatesByDots(templates, function(template, name, method) {
                    return {'key': name, 'value': method};
                }),
                templatesMethodMap = this.splitTemplatesByDots(templates, function (template, name, method) {
                    return {'template': template, 'key': name, 'value': method};
                }),
                templatesMap = {};

            if ((templatesMethodMap !== null || typeof templatesMethodMap !== "undefined") && !$.isEmptyObject(templatesMethodMap)) {
                for (var templateName in templatesMethodMap) {
                    var name = templatesMethodMap[templateName].key;
                    var method = templatesMethodMap[templateName].value;
                    if (item[name]) {
                        templatesMap[templateName] = item[name][method].toString();
                    }
                }
            } else {
                templatesMap = this.splitTemplatesByDots(templates, function(template, name, method) {
                    return {'key': template, 'value': '{' + name + '}'};
                });
            }
            template = YAHOO.lang.substitute(template, templatesMap, function(key, value) {
                return value;
            }, false);
            return YAHOO.lang.substitute(template, item, function(key, value) {
                var result = null;
                if (typeof value === "undefined" || value === null) {
                    result = value;
                }
                else {
                    var method = methodMap[key];
                    result = method ? value[method] : value.toString();
                }
                return result;
            }, false);
        },

        getTemplatesWithDots: function(template) {
            var result = [],
                regex = new RegExp('.*?{([^{}\\.]+\\.[^{}]+)}.*?', 'ig');
            var group = regex.exec(template);
            while (group && group.length > 1) {
                result.push(group[1]);
                group = regex.exec(template);
            }
            return result;
        },

        splitTemplatesByDots: function(templates, extractTemplateData) {
            var result = {};
            if (templates && templates.length > 0) {
                for(var i = 0; i < templates.length; i++) {
                    if (templates[i]) {
                        var template = '' + templates[i];
                        pos = template.indexOf('.');
                        if (pos > 0 && pos < template.length - 1) {
                            var name = template.substring(0, pos),
                                method = template.substring(pos + 1);
                            data = extractTemplateData(template, name, method);
                            if (data.template !== 'undefined' || data.template !== null) {
                                result[data.template] = {key: data.key, value: data.value};
                            } else {
                                result[data.key] = data.value;
                            }
                        }
                    }
                }
            }
            return result;
        },

        /**
         * Check, if item can have children (i.e. exists in children config).
         * @param item - it can be item name, item from model, or item created externally
         */
        hasItemChildren: function(item) {
            item = this.getItem(item);
            if(!item) return false;
            var config = this.getItemProperty(item, this.config.children);
            return config && config.get ? true : false;
        },

        // clear model:
        _initModel: function() {
            this.items = {};
            this.createSpecialItem({}, {
                name: "none",
                keys: ["none"],
            });
        },

        // extend item with model-specific properties:
        // _item_name_ - name of item
        // _item_keys_ - array of item keys
        // _item_children_ - array of item's child items
        _extendItem: function(item, format) {
            if(format.calc) {
                format.calc(item);
            }
            item._item_name_ = this.renderTemplate(item, format.name);
            item._item_keys_ = this._getItemKeys(item, format.keys);
            item._item_children_ = item._item_children_ || [];
        },

        // fill the model with items of specified format
        _fillModel: function(items, format) {
            for(var i in items) {
                if(!items.hasOwnProperty(i)) continue;
                var item = items[i];
                this._extendItem(item, format);
                var name = item._item_name_;
                if(this.items[name]) {
                    for(var prop in item) {
                        if(item.hasOwnProperty(prop)) {
                            this.items[name][prop] = item[prop];
                        }
                    }
                    this._extendItem(this.items[name], format);
                } else {
                    this.items[name] = item;
                }
            }
        },

        // calculate item keys for the given key templates
        _getItemKeys: function(item, tpls) {
            var keys = [];
            for(var i in tpls) {
                if(!tpls.hasOwnProperty(i)) continue;
                keys.push(this.renderTemplate(item, tpls[i]));
            }
            return keys;
        },

        // find item in other item's children
        _findChild: function(child, parent) {
            var children = parent._item_children_;
            for(var i in children) {
                if(!children.hasOwnProperty(i)) continue;
                if(children[i]._item_name_ == child._item_name_) {
                    return i;
                }
            }
            return -1;
        },

        // get results from server response
        _getResults: function(response, path, excludeFields) {
            var children = response.json;
            if(path) {
                var fields = path.split(/[.]/);
                for(var i in fields) {
                    if(!fields.hasOwnProperty(i)) continue;
                    if(!children) break;
                    children = children[fields[i]];
                }
            }
            if (excludeFields) {
                children = children.filter(function(item) {
                    return excludeFields.indexOf(item.shortName) == -1;
                });
            }
            return children;
        },

        ecosAddOrRemove: function(parent, child, add) {
            var actionRecord = Citeck.Records.getRecordToEdit('alfresco/authority-group-manager-action@');
            actionRecord.att('type', "add-or-remove");
            actionRecord.att('parent', parent);
            actionRecord.att('child', child);
            actionRecord.att('add', add);
            return actionRecord.save();
        }

    });

    /**
     * Hierarchy model.
     * Has four events:
     * - itemUpdated - model contains new information about the item. args = { item: item }
     * - childrenUpdated - model contains new information about the item children. args = { from: parent, items: children }
     * - childAdded - child was added to item. args = { from: parent, item: child }
     * - childDeleted - child was deleted from item. args = { from: parent, item: child }
     */
    Citeck.util.HierarchyModel = function(name) {
        Citeck.util.HierarchyModel.superclass.constructor.call(this, name || "Citeck.util.HierarchyModel");

        // register model events:
        this.createEvent("itemUpdated");
        this.createEvent("childrenUpdated");
        this.createEvent("childAdded");
        this.createEvent("childDeleted");

    };

    YAHOO.extend(Citeck.util.HierarchyModel, Citeck.util.AbstractModel, {

        /**
         * Update specified item.
         * @param item - it can be item name, item from model, or item created externally
         * @param passive - true for not doing remote queries, all data is already in the model.
         * @param callback - internal parameter, do not use
         */
        updateItem: function(item, passive, callback) {
            item = this.getItem(item);
            if(!item) return false;

            var config = this.getItemProperty(item, this.config.item);
            if(!config) return false;

            var format = this.config.formats[config.format];
            if(!format) return false;

            var oldItem = JSON.parse(JSON.stringify(item));
            var process = function(data) {
                // fill model:
                this._fillModel([data], format);
                // fire event
                if(callback) {
                    callback.call(this, item);
                } else {
                    this.fireEvent("itemUpdated", { item: item, oldItem: oldItem });
                }
            };

            // if there is no update url
            // then suppose, that item was updated externally
            if(passive || !config.get) {
                process.call(this, item);
                return true;
            }

            // get information url:
            var url = encodeURI(this.renderTemplate(item, config.get));

            // get up-to-date item information
            Alfresco.util.Ajax.request({
                url: url,
                successCallback: {
                    scope: this,
                    fn: function(response) {
                        var item = this._getResults(response, config.resultsList);
                        process.call(this, item);
                    },
                },
                failureCallback: {
                    scope: this,
                    fn: this.onFailure
                },
                execScripts: true
            });
            return true;
        },

        /**
         * Update item children.
         * @param item - it can be item name, item from model, or item created externally
         * @param passive - true for not doing remote queries, all data is already in the model (in item._item_children_ field).
         */
        updateChildren: function(item, passive) {
            item = this.getItem(item);
            if(!item) return false;

            var config = this.getItemProperty(item, this.config.children);
            if(!config) return false;

            var format = this.config.formats[config.format];
            if(!format) return false;

            var process = function(items) {
                // fill model
                this._fillModel(items, format);
                // fire event
                this.fireEvent("childrenUpdated", { items: items, from: item });
            };

            if(passive || !config.get) {
                // in passive mode - first ensure, that all items exist in the model
                var items = item._item_children_,
                    existedItems = [],
                    deferredUpdating = false;
                for (var i = 0; i < items.length; i++) {
                    var existingItem = this.getItem(items[i]);
                    // add it to the model in any case (so that local properties could be set)
                    this._fillModel([items[i]], format);
                    if (existingItem) {
                        existedItems.push(existingItem);
                    }
                    else {
                        deferredUpdating = true;
                        // take back from model
                        existingItem = this.getItem(items[i]);
                        // trigger item update
                        this.updateItem(items[i], passive, function(addedItem) {
                            existedItems.push(addedItem);
                            if (existedItems.length == items.length)
                                process.call(this, existedItems);
                        });
                    }
                    items[i] = existingItem;
                }
                // then do processing
                if (!deferredUpdating)
                    process.call(this, existedItems);
                return true;
            }

            var url = encodeURI(this.renderTemplate(item, config.get));

            // get children
            Alfresco.util.Ajax.request({
                url: url,
                successCallback: {
                    scope: this,
                    fn: function(response) {
                        item._item_children_ = this.config ? this._getResults(response, config.resultsList, this.config.excludeFields) : this._getResults(response, config.resultsList);
                        process.call(this, item._item_children_);
                    }
                },
                failureCallback: {
                    scope: this,
                    fn: function(response) {
                        // call standard handler:
                        this.onFailure(response);
                        // issue event
                        process.call(this, []);
                    }
                },
                execScripts: true
            });
            return true;
        },

        /**
         * Add item to another item.
         * @param item - it can be item name, item from model, or item created externally
         * @param parent - it can be item name, item from model, or item created externally
         * @param passive - true for not doing remote queries
         */
        addItem: function(child, parent, passive) {
            parent = this.getItem(parent);
            if(!child || !parent) return false;

            var config = this.getItemProperty(parent, this.config.children);
            if(!config) return false;

            var format = this.config.formats[config.format];
            if(!format) return false;

            // extend child representation, so it could contain name and keys:
            this._extendItem(child, format);

            var process = function() {
                // search for item in children
                var index = this._findChild(child, parent);
                if(index != -1) return;
                // add item to children:
                parent._item_children_.push(child);
                var event = {
                    name: "childAdded",
                    args: {
                        item: child,
                        from: parent
                    }
                };
                // if there is such item already in the model
                if(this.getItem(child)) {
                    // just fire event
                    this.fireEvent(event.name, event.args);
                } else {
                    // add it to model, so updateItem could find it:
                    this._fillModel([child], format);
                    // launch update child item, and fire "childAdded" event on complete
                    this.updateItem(child, passive, function(item) {
                        this.fireEvent(event.name, event.args);
                    });
                }
            }

            if(passive || !config["add"]) {
                process.call(this);
                return true;
            }

            // first set item properties, then parent properties:
            // child element should have enough information to build url:
            var url = config["add"];
            var isOrgstructManagers = config["isOrgstructManagers"];
            if (isOrgstructManagers) {
                this.ecosAddOrRemove(parent.fullName, child.fullName, true).then(() => {
                      Alfresco.util.PopupManager.displayMessage({
                        text: "Authority added",
                        displayTime: 2
                      });
                      process.call(this);
                    }).catch(e => {
                        Alfresco.util.PopupManager.displayMessage({
                          text: "Failure" + " - " + e.message,
                          displayTime: 3
                        });
                    });
            } else {
                url = this.renderTemplate({item: child, parent: parent}, url);
                url = encodeURI(url);

                Alfresco.util.Ajax.jsonPost({
                    url: url,
                    successCallback: {
                        scope: this,
                        fn: process,
                    },
                    failureCallback: {
                        scope: this,
                        fn: this.onFailure
                    },
                });
            }
        },

        /**
         * Delete item from another item.
         * @param item - it can be item name, item from model, or item created externally
         * @param parent - it can be item name, item from model, or item created externally
         * @param passive - true for not doing remote queries
         */
        deleteItem: function(child, parent, passive) {
            child = this.getItem(child);
            parent = this.getItem(parent);
            if(!child || !parent) return false;

            var config = this.getItemProperty(parent, this.config.children);
            if(!config) return false;

            var process = function() {
                // search for item in children
                var index = this._findChild(child, parent);
                if(index == -1) return;
                parent._item_children_.splice(index, 1);
                // fire "deleted" event
                this.fireEvent("childDeleted", { item: child, from: parent });
            }

            if(passive) {
                process.call(this);
                return true;
            }

            // first set item properties, then parent properties:
            var url = config["delete"];
            var isOrgstructManagers = config["isOrgstructManagers"];
            if (isOrgstructManagers) {
                this.ecosAddOrRemove(parent.fullName, child.fullName, false).then(() => {
                      Alfresco.util.PopupManager.displayMessage({
                        text: "Authority removed",
                        displayTime: 2
                      });
                      process.call(this);
                    }).catch(e => {
                        Alfresco.util.PopupManager.displayMessage({
                          text: "Failure" + " - " + e.message,
                          displayTime: 3
                        });
                    });
            } else {
                url = this.renderTemplate({item: child, parent: parent}, url);
                url = encodeURI(url);

                // send request:
                Alfresco.util.Ajax.request({
                    method: Alfresco.util.Ajax.DELETE,
                    url: url,
                    successCallback: {
                        scope: this,
                        fn: process,
                    },
                    failureCallback: {
                        scope: this,
                        fn: this.onFailure
                    },
                });
            }
        },

    });

    return Citeck.util.HierarchyModel;
});
