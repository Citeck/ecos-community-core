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
define([
    'lib/knockout',
    'citeck/utils/knockout.utils',
    'ecosui!menu-api',
    'ecosui!journalsApi',
    'ecosui!user-in-groups-list-helper',
    'underscore',
    'citeck/components/invariants/invariants',
    'citeck/components/dynamic-tree/cell-formatters',
    'citeck/components/dynamic-tree/action-renderer'
], function(ko, koutils, MenuApi, JournalsApi, checkFunctionalAvailabilityHelper, _) {

    if (!Citeck) Citeck = {};
    if (!Citeck.constants) Citeck.constants = {};

    var menuApi = new MenuApi();
    var journalsApi = JournalsApi;

var logger = Alfresco.logger,
        noneActionGroupId = "none",
        buttonsActionGroupId = "buttons",
        defaultActionGroupId = "injournal",
        defaultActionFormatter = null,
        customRecordLoader = ko.observable(),
        BulkLoader = Citeck.utils.BulkLoader,
        journalsListIdRegexp = new RegExp('^([^-]+)(-(.+))?-([^-]+)$'),
        koclass = koutils.koclass,
        formatters = Citeck.format,
        msg = Alfresco.util.message;

var defaultFormatters = {
    "qname": formatters.qname(false),
    "date": formatters.date("dd.MM.yyyy"),
    "datetime": formatters.date("dd.MM.yyyy HH:mm"),
    "noderef": formatters.node(),
    "category": formatters.node(),
    "association": formatters.node(),
    "boolean": formatters.bool(msg('label.yes'), msg('label.no')),
    "filesize": formatters.fileSize("attributes['cm:content']"),
    "mimetype": formatters.icon(16, "attributes['cm:name']"),
    "typeName": formatters.typeName(),
    "int": formatters.parseInt(),
    "long": formatters.parseInt(),
    "double": formatters.parseFloat(),
    "float": formatters.parseFloat()
};

// class declarations:
var criteriaCounter = 0,
    s = String,
    n = Number,
    b = Boolean,
    o = Object,
    d = Date,
    JournalsList = koclass('JournalsList'),
    JournalType = koclass('JournalType'),
    Journal = koclass('Journal'),
    Filter = koclass('Filter'),
    Settings = koclass('Settings'),
    Criterion = koclass('Criterion'),
    CreateVariant = koclass('CreateVariant'),
    Invariant = koclass('invariants.Invariant'),
    Attribute = koclass('Attribute'),
    AttributeInfo = koclass('AttributeInfo'),
    AttributeFilter = koclass('AttributeFilter'),
    Predicate = koclass('Predicate'),
    PredicateList = koclass('PredicateList'),
    Datatype = koclass('Datatype'),
    FormInfo = koclass('FormInfo'),
    Action = koclass('Action'),
    Record = koclass('Record'),
    Column = koclass('Column'),
    ActionsColumn = koclass('ActionsColumn'),
    JournalsWidget = koclass('JournalsWidget'),
    SortBy = koclass('SortBy'),
    Node = koclass('invariants.Node'),
    QName = koclass('invariants.QName'),
    last;

// class definitions:
JournalsList
    .key('id', s)
    .property('documentNodeRef', s)
    .property('title', s)
    .property('journals', [ Journal ])
    .property('uiservJournals', [ Journal ])
    .property('default', Journal)
    .computed('allJournals', function() {

        var journals = (this.journals() || []).slice();
        var uiservJournals = this.uiservJournals() || [];
        var journalsId = _.map(journals, function(j) { return j.resolve('type.id', ''); });

        for (var i = 0; i < uiservJournals.length; i++) {

            var journal = uiservJournals[i];
            if (journal) {
                var id = journal.resolve('type.id', '');
                var journalIdx = journalsId.indexOf(id);
                if (journalIdx >= 0) {
                    journals[journalIdx] = journal;
                    continue;
                }
            }
            journals.push(journal);
        }

        return journals;
    })
    .computed('scope', function() {
        return this.id() ? this.id().replace(journalsListIdRegexp, '$1') : '';
    })
    .computed('scopeId', function() {
        return this.id() ? this.id().replace(journalsListIdRegexp, '$3') : '';
    })
    .computed('listId', function() {
        return this.id() ? this.id().replace(journalsListIdRegexp, '$4') : '';
    })
    .constructor([String, String], function(id, nodeRef) {
        var jl = new JournalsList(id);
        jl.documentNodeRef(nodeRef);
        return jl;
    }, true)
    ;

var featuredProperty = function(featureName) {
    return function() {
        var attribute = this.attribute();
        return attribute ? attribute[featureName + 'Evaluator'](this.invariantsModel()).value : null;
    }
};

CreateVariant
    .property('url', s)
    .load('url', function() {
      YAHOO.util.Connect.asyncRequest('GET', Alfresco.constants.URL_SERVICECONTEXT + "citeck/components/templates/url-template", {
          success: function(response) {
              var result = response.responseText;
              this.url(result);
          },
          scope: this
        }
      );
    })

    .property('title', s)
    .property('destination', s)
    .property('type', s)
    .property('formId', s)
    .property('canCreate', b)
    .property('isDefault', b)
    .property('journal', Journal)
    .property('createArguments', s)
    .property('formKey', s)
    .property('recordRef', s)
    .property('attributes', o)

    .method('onClick', function () {

        var self = this;

        var options = this.resolve("journal.type.options") || {};
        var redirectionMethod = options["createVariantRedirectionMethod"] || "card";

        var createParams = Citeck.forms.parseCreateArguments(this.createArguments());
        var formKey = this.formId() || this.formKey();

        Citeck.forms.createRecord(this.recordRef(), this.type(), this.destination(), function() {

            var url = self.url();
            if (!url) {
                koutils.subscribeOnce(self.url, function () {
                    window.location = self.link();
                });
            } else {
                window.location = self.link();
            }
        }, redirectionMethod, formKey, this.attributes(), { params: createParams });
    })

    .computed('link', function () {

        var defaultUrlTemplate = 'create-content?itemId={type}&destination={destination}&viewId={formId}',
                urlTemplate = this.url() ? this.url().replace(/(^\s+|\s+$)/g,'') : defaultUrlTemplate;

        if (this.createArguments()) {
            urlTemplate += "&" + this.createArguments();
        }

        // redirect back after submit from journal page only
        var options = this.resolve("journal.type.options");
        if (window.location.pathname.indexOf("journals2") != -1 && options) {
            var redirectionMethod = options["createVariantRedirectionMethod"] || "card";
            urlTemplate += "&onsubmit=" + encodeURIComponent(redirectionMethod);
        }

        return Alfresco.util.siteURL(YAHOO.lang.substitute(urlTemplate, this, function(key, value) {
            if (typeof value == "function") { return value(); }
            return value;
        }));
    })
    ;

    Criterion
        .constructor([Criterion], function(criterion) {
            var that = Criterion.call(this, criterion.shortModel());
            that.attributeProperty(criterion.attributeProperty());
            return that;
        })
        .property('field', AttributeInfo) //WARNING: attribute link in this object is changed when new journal is loaded
        .property('attributeProperty', Attribute)
        .property('predicate', Predicate)
        .property('persistedValue', o)
        .property('newValue', o) // value, set by user

        .computed('attribute', function () {
            return this.attributeProperty() || this.resolve('field.attribute');
        })

        .shortcut('nodetype', 'attribute._info.nodetype')
        .shortcut('datatype', 'attribute._info.datatype')
        .shortcut('journalType', 'attribute._info.journalType')
        .shortcut('title', 'attribute._info.customDisplayName')
        .shortcut('separator', 'attribute._info.separator')
        .shortcut('allowMultipleFilterValue', 'attribute._info.allowMultipleFilterValue') // multiple value, using separator
        .shortcut('allowableMultipleFilterPredicates', 'attribute._info.allowableMultipleFilterPredicates')
        .shortcut('multipleFilterMaxCount', 'attribute._info.multipleFilterMaxCount')
        .shortcut('name', 'field.name')

        /*====== Value ======*/

        .computed('valueClass', function () {
            var datatype = this.resolve('datatype.name');
            switch (datatype) {
                case 'noderef':
                case 'category':
                case 'association':
                    return Node;
                case 'typeName':
                case 'qname':
                    return QName;
                case 'date':
                case 'datetime':
                    return d;
                case 'boolean':
                    return b;
            }
            return s;
        })

        .method('convertValue', function(value, multiple) {

            if (value == null || value === "") return multiple ? [] : null;

            var instantiate = _.partial(koutils.instantiate, _, this.valueClass() || s);
            if (_.isArray(value)) {
                return multiple ? _.map(value, instantiate) : instantiate(value[0]);
            } else {
                return multiple ? [ instantiate(value) ] : instantiate(value) ;
            }
        })

        .computed('value', {
            read: function() {
                return this.convertValue(this.rawValue(), this.multiple());
            },
            write: function(value) {
                this.newValue(this.convertValue(value, true));
            }
        })

        .computed('rawValue', function() {
            var result = this.invariantValue() || this.newValue();
            if (result == null) {
                result = this.persistedValue();
            }
            return result;
        })

        .computed('singleValue', {
            read: function() {
                return this.convertValue(this.rawValue(), false);
            },
            write: function(value) {
                this.value(value);
            }
        })

        .computed('multipleValues', {
            read: function() {
                return this.convertValue(this.rawValue(), true);
            },
            write: function(value) {
                this.value(value);
            }
        })

        .computed('empty', function() {
            return this.value() == null
                || this.multiple() && this.value().length == 0
                || this.valueClass() == String && this.value().length == 0;
        })

        .computed('textValue', {
            read: function() { return this.getValueText(this.value()); },
            write: function(value) {
                if(value == null || value == "") {
                    return this.value(null);
                } else {
                    return this.value(value);
                }
            }
        })

        .method('filterOptions', function(criteria, pagination, paramJournalType, allowedFilterValues) {

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
                    return criterion.predicate == 'journal-id';
                })) {
                if (!this.nodetype()) {
                    return [];
                }

                query['field_1'] = "type";
                query['predicate_1'] = "type-equals";
                query['value_1'] = this.nodetype();
            }

            if (pagination) {
                if (pagination.maxItems) query.maxItems = pagination.maxItems;
                if (pagination.skipCount) query.skipCount = pagination.skipCount;
            }

            var lastIndex = 1;
            _.each(criteria, function(criterion, index) {
                query['field_' + (index + 2)] = criterion.attribute;
                query['predicate_' + (index + 2)] = criterion.predicate;
                query['value_' + (index + 2)] = criterion.value;
                lastIndex = index + 2;
            });

            if (allowedFilterValues && allowedFilterValues[paramJournalType]) {
                query['field_' + (lastIndex + 1)] = allowedFilterValues[paramJournalType].field;
                query['predicate_' + (lastIndex + 1)] = allowedFilterValues[paramJournalType].predicate;
                query['value_' + (lastIndex + 1)] = allowedFilterValues[paramJournalType].values.join(',');
            }

            if(this.cache.query) {
                if(_.isEqual(query, this.cache.query)) return this.cache.result();
            }

            this.cache.query = query;
            if (_.some(_.keys(query), function(p) {
                    return _.some(["field", "predicate", "value"], function(ci) {
                        return p.indexOf(ci) != -1;
                    });
                })) {
                Alfresco.util.Ajax.jsonPost({
                    url: Alfresco.constants.PROXY_URI + "search/criteria-search",
                    dataObj: query,
                    successCallback: {
                        scope: this.cache,
                        fn: function(response) {
                            var result = _.map(response.json.results, function(node) {
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
        })

        .method('getValueDescription', function () {
            return this.getValueTitle(arguments);
        })

        .method('getValueTitle', function (value, postprocessing) {
            var model = this.getInvariantsModel(value, this.cache = this.cache || {});
            var attribute = this.attribute();
            var result = attribute ? attribute.valueTitleCriterionEvaluator(model).value : null;
            if (postprocessing) {
                result = postprocessing(result);
            }
            if (!result) {
                if (value instanceof Node) {

                    switch (value.typeShort) {
                        case "cm:person":
                            result = value.properties["cm:firstName"] + " " + value.properties["cm:lastName"];
                            break;
                        case "cm:authorityContainer":
                            result = value.properties["cm:authorityDisplayName"] || value.properties["cm:authorityName"];
                            break;
                        default:
                            result = value.properties['cm:title'] || value.properties['cm:name'];
                    }
                }
                if (!result) {
                    result = Alfresco.util.message("label.none");
                }
            }
            return result;
        })

        .method('getValueText', function(value) {

            if (value == null) return "";

            if (_.isArray(value)) {
                return _.map(value, this.getValueText, this).join(",");
            }

            var valueClass = this.valueClass();
            if (valueClass == null) return "" + value;
            if (valueClass == o) return value.toString();
            if (valueClass == s) return "" + value;
            if (valueClass == b) return value ? "true" : "false";
            if (valueClass == Node) return value.nodeRef;
            if (valueClass == QName) return value.fullQName();

            var datatype = this.datatype();
            if (valueClass == n) {
                if(datatype == 'd:int' || datatype == 'd:long') {
                    return "" + Math.floor(value);
                }
                return "" + value;
            }
            if (valueClass == d) {
                var type = this.resolve('datatype.name');
                if(type == 'date') {
                    var year = value.getFullYear(),
                        month = value.getMonth() + 1,
                        date = value.getDate();
                    return (year > 1000 ? "" : year > 100 ? "0" : year > 10 ? "00" : "000") + year
                        + (month < 10 ? "-0" : "-") + month
                        + (date  < 10 ? "-0" : "-") + date;
                }

                return Alfresco.util.toISO8601(value);
            }

            throw {
                message: "Value class is not supported",
                valueClass: valueClass,
                datatype: datatype
            };
        })

        .computed('options', {
            read: function() {
                return this.convertValue(this.invariantOptions(), true) || [];
            },
            pure: true
        })
        .computed('single', function() { return !this.multiple(); })

        /*====== Invariants ======*/

        .computed('invariantsModel', function() {
            return this.getInvariantsModel(this.value, this.cache = this.cache || {});
        })
        .method('getInvariantsModel', function(value, cache) {
            var model = {};
            Object.defineProperty(model, 'value', typeof value == "function" ? { get: value } : { value: value });
            Object.defineProperty(model, 'cache', { value: cache });
            return model;
        })

        .computed('invariantValue', featuredProperty('valueCriterion'))
        .computed('invariantOptions', featuredProperty('optionsCriterion'))
        .computed('invariantDefault', featuredProperty('defaultCriterion'))
        .computed('multiple', featuredProperty('multipleCriterion'))
        .computed('mandatory', featuredProperty('mandatoryCriterion'))
        .computed('relevant', featuredProperty('relevantCriterion'))
        .computed('protected', featuredProperty('protectedCriterion'))

        /*====== Other ======*/

        .computed('shortModel', function() {
            return {
                field: this.name(),
                predicate: this.resolve('predicate.id'),
                persistedValue: this.textValue()
            };
        })
        .computed('id', function() {
            if(typeof this._id == "undefined") {
                this._id = criteriaCounter++;
            }
            return this._id;
        })
        .computed('query', function() {
            var name                              = this.name(),
                textValue                         = this.textValue(),
                result                            = [],
                separator                         = this.separator(),
                predicateId                       = this.resolve('predicate.id'),
                allowableMultipleFilterPredicates = this.allowableMultipleFilterPredicates();

            if (textValue &&
                this.allowMultipleFilterValue() &&
                textValue.indexOf(separator) != -1 &&
                allowableMultipleFilterPredicates.indexOf(predicateId) != -1) {

                var values = _.uniq((textValue.split(separator)).filter(Boolean));

                _.each(values, function(value) {
                    result.push({
                        field: name,
                        predicate: predicateId,
                        value: value.trim()
                    });
                });

            } else {

                result.push({
                    field: name,
                    predicate: predicateId,
                    value: textValue
                });
            }
            return result;
        })
        .method('validateForError', function() {
            var textValue                         = this.textValue(),
                separator                         = this.separator(),
                predicateId                       = this.resolve('predicate.id'),
                allowableMultipleFilterPredicates = this.allowableMultipleFilterPredicates(),
                multipleFilterMaxCount            = this.multipleFilterMaxCount();

            if (textValue &&
                this.allowMultipleFilterValue() &&
                textValue.indexOf(separator) != -1 &&
                allowableMultipleFilterPredicates.indexOf(predicateId) != -1) {

                var values = _.uniq((textValue.split(separator)).filter(Boolean));

                if (multipleFilterMaxCount !== -1 && values.length > multipleFilterMaxCount) {
                    throw {
                        errorName: "Too many multiple values",
                        actualCount: values.length,
                        maxCount: multipleFilterMaxCount
                    };
                }
            }
        })
        .init(function() {
            var predicateId = this.resolve('predicate.id');
            if (predicateId && predicateId.indexOf("boolean") != -1){
                this.value("boolean");
            }
        })
    ;

JournalType
    .key('id', s)
    .property('journal', Journal)
    .property('options', o)
    .property('formInfo', FormInfo)
    .property('attributes', [ Attribute ])
    .property('filters', [ Filter ])
    .property('settings', [ Settings ])
    .property('groupActions', [ Action ])
    .property('datasource', s)
    .property('gqlschema', s)

    .computed('visibleAttributes', function() {
        return _.invoke(_.filter(this.attributes(), function(attr) {
            return attr.visible();
        }), '_info');
    })
    .computed('searchableAttributes', function() {
        return _.invoke(_.filter(this.attributes(), function(attr) {
            return attr.searchable();
        }), '_info');
    })
    .computed('sortableAttributes', function() {
        return _.invoke(_.filter(this.attributes(), function(attr) {
            return attr.sortable();
        }), '_info');
    })
    .computed('groupableAttributes', function() {
        return _.invoke(_.filter(this.attributes(), function(attr) {
            return attr.groupable();
        }), '_info');
    })
    .computed('defaultAttributes', function() {
        return _.invoke(_.filter(this.attributes(), function(attr) {
            return attr.isDefault();
        }), '_info');
    })
    .computed('defaultSearchableAttributes', function(attr) {
        return _.intersection(this.defaultAttributes(), this.searchableAttributes());
    })
    .computed('defaultFilter', function() {
        var criteria = _.map(this.defaultSearchableAttributes(), function(attr) {
            var predicates = attr.resolve("datatype.predicates");
            if (!predicates || predicates.length == 0) return;
            return { field: attr.name(), predicate: predicates[0].id(), value: "" }
        });
        var loadingCriteria = _.filter(criteria, function(item) {
            return item == undefined;
        });
        if (criteria.length && !loadingCriteria.length) {
            return new Filter({
                nodeRef: null,
                title: null,
                permissions: { Write: false, Delete: false},
                journalTypes: [ this.id() ],
                criteria: _.compact(criteria)
            });
        }
        return null;
    })
    .computed('defaultSettings', function() {
        return new Settings({
            nodeRef: null,
            title: null,
            permissions: { Write: false, Delete: false},
            journalTypes: [ this.id() ],
            visibleAttributes: _.invoke(this.defaultAttributes(), 'name')
        });
    })

    .method('attribute', function(name) {
        return _.find(this.attributes(), function(attr) {
            return attr.name() == name;
        });
    })


    ;

Journal
    .key('nodeRef', s)
    .property('title', s)
    .property('type', JournalType)
    .property('criteria', [ Criterion ])
    .property('createVariants', [ CreateVariant ])
    .property('predicate', o)

    .computed('availableCreateVariants', function() {
        return _.filter(this.createVariants(), function(variant) {
            return variant.canCreate() !== false;
        });
    })

    .shortcut('options', 'type.options')

    .init(function() {
        this.criteria.extend({ rateLimit: 0 });
    })
    ;

Filter
    .key('nodeRef', s)
    .property('title', s)
    .property('permissions', o)
    .property('journalTypes', [ JournalType ])
    .property('criteria', [ Criterion ])
    .computed('valid', function() {
        return this.criteria().length > 0;
    })
    .computed('shortModel', function() {
        return {
            criteria: _.invoke(this.criteria(), 'shortModel')
        };
    })
    .computed('saveModel', function() {
        return {
            criteria: _.invoke(this.criteria(), 'shortModel'),
            title: this.title(),
            journalTypes: _.invoke(this.journalTypes(), 'id')
        };
    })
    .computed('usableCriteria', function() {
        return _.filter(this.criteria(), function(criterion) {
            if (!criterion || !criterion.predicate() || !criterion.predicate().id()) {
                return false;
            }

            var predicateId = criterion.predicate().id();

            return predicateId.indexOf('join-by') > -1 ||
                !!(predicateId.indexOf("choose") == -1 && (criterion.value() || predicateId.indexOf("empty") != -1));

        });
    })
    .init(function() {
        this.criteria.extend({ rateLimit: 0 });
    })
    ;

Settings
    .key('nodeRef', s)
    .property('title', s)
    .property('permissions', o)
    .property('journalTypes', [ JournalType ])
    .property('visibleAttributes', [ AttributeInfo ])
    .computed('valid', function() {
        return this.visibleAttributes().length > 0;
    })
    .computed('shortModel', function() {
        return {
            visibleAttributes: _.invoke(this.visibleAttributes(), 'name')
        };
    })
    .computed('saveModel', function() {
        return {
            visibleAttributes: _.invoke(this.visibleAttributes(), 'name'),
            title: this.title(),
            journalTypes: _.invoke(this.journalTypes(), 'id')
        };
    })
    ;

var notNull = function(value) { return value !== null; };
var isFalse = function(value) { return value === false; };

var filterFeatureEvaluator = function(featureName, requiredClass, defaultValue, isTerminate) {
    return function(model) {

        var invariant,
            invariantValue = null,
            invariants = this['criterionInvariants']();

        invariant = _.find(invariants, function(invariant) {
            if (invariant.feature() == featureName) {
                invariantValue = invariant.evaluate(model);
                return isTerminate(invariantValue, invariant);
            }
        });

        return {
            invariant: invariant,
            value: koutils.instantiate(invariant != null ? invariantValue : defaultValue, requiredClass)
        }
    };
};

Attribute
    .property('name', s)
    .property('_info', AttributeInfo)
    .property('visible', b)
    .property('searchable', b)
    .property('sortable', b)
    .property('groupable', b)
    .property('isDefault', b)
    .property('settings', o)
    .property('batchEdit', [ Action ])
    .property('criterionInvariants', [ Invariant ])

    .shortcut('type', '_info.type')
    .shortcut('displayName', '_info.displayName')
    .shortcut('datatype', '_info.datatype')
    .shortcut('nodetype', '_info.nodetype')
    .shortcut('journalType', '_info.journalType')
    .shortcut('labels', '_info.labels', {})
    .shortcut('customDisplayName', '_info.customDisplayName')

    .method('valueCriterionEvaluator', filterFeatureEvaluator('value', o, null, notNull))
    .method('defaultCriterionEvaluator', filterFeatureEvaluator('default', o, null, notNull))
    .method('optionsCriterionEvaluator', filterFeatureEvaluator('options', o, null, notNull))
    .method('valueTitleCriterionEvaluator', filterFeatureEvaluator('value-title', s, '', notNull))
    .method('valueDescriptionCriterionEvaluator', filterFeatureEvaluator('value-description', s, '', notNull))
    .method('valueOrderCriterionEvaluator', filterFeatureEvaluator('value-order', n, 0, notNull))
    .method('relevantCriterionEvaluator', filterFeatureEvaluator('relevant', b, true, notNull))
    .method('multipleCriterionEvaluator', filterFeatureEvaluator('multiple', b, false, notNull))
    .method('mandatoryCriterionEvaluator', filterFeatureEvaluator('mandatory', b, false, notNull))
    .method('protectedCriterionEvaluator', filterFeatureEvaluator('protected', b, false, notNull))

    .init(function() {
        this.model({ _info: {name: this.name(), attribute: this} });
        var self = this;
        _.each(this.batchEdit(), function (a) {
            a.attribute(self);
        });
    })
    ;

AttributeInfo
    .key('name', s)
    .property('type', s)
    .property('displayName', s)
    .property('datatype', Datatype)
    .property('labels', o)
    .property('nodetype', s)
    .property('journalType', JournalType)
    .property('attribute', Attribute)

    .shortcut('separator', 'attribute.settings.separator', ';')
    .shortcut('multipleFilterMaxCount', 'attribute.settings.multipleFilterMaxCount', -1)

    .method('customDisplayName', function() {
        var optionLabel = null;
        if (this.attribute() && this.attribute().settings()) {
            optionLabel = this.attribute().settings().customLabel;
        }
        if (optionLabel) {
            return Alfresco.util.message(optionLabel);
        }
        return this.displayName();
    })
    .method('allowMultipleFilterValue', function() {
        return this.resolve('attribute.settings.allowMultipleFilterValue') == 'true';
    })
    .method('allowableMultipleFilterPredicates', function() {
        var allowableMultipleFilterPredicates = ["string-equals", "string-not-equals"];

        if (this.resolve('attribute.settings.allowableMultipleFilterPredicates')) {
            allowableMultipleFilterPredicates = _.map(this.resolve('attribute.settings.allowableMultipleFilterPredicates').split(','), function (item) {
                return item.trim();
            });
        }

        return allowableMultipleFilterPredicates;
    })
    .computed('customDisplayNameText', function() {
        return this.customDisplayName();
    })
    ;

Datatype
    .key('name', s)
    .property('predicateList', PredicateList)
    .shortcut('predicates', 'predicateList.predicates', [])

    .load('predicateList', function(datatype) {
        YAHOO.util.Connect.asyncRequest(
            'GET',
            Alfresco.constants.URL_PAGECONTEXT + "search/search-predicates?datatype=" + datatype.name(),
            {
                success: function(response) {
                    var result = JSON.parse(response.responseText),
                        predicates = [];

                    for (var i in result.predicates) {
                        predicates.push(new Predicate(result.predicates[i]))
                    };

                    this.predicateList(new PredicateList({
                        id: result.datatype,
                        predicates: predicates
                    }));
                },

                failure: function(response) {
                    // error
                },

                scope: this
            }
        );
    })

    ;

Predicate
    .key('id', s)
    .property('label', s)
    .property('needsValue', b)
    ;

PredicateList
    .key('id', s)
    .property('predicates', [ Predicate ])
    .init(function() {
        if (this.predicates() && this.predicates()[0].id().indexOf("boolean") != -1) {
            var choosePredicate = new Predicate({
                id: "choose",
                label: Alfresco.util.message("form.select.label"),
                needsValue: false
            });
            this.predicates().unshift(choosePredicate);
        }
    })
    ;

FormInfo
    .property('type', s)
    .property('formId', s)
    ;

Record
    .key('nodeRef', s)
    .property('attributes', o)
    .property('permissions', o)
    .computed('aspects', function() {
        var resAspectList = this.attributes()['attr:aspects'] || [];
        var aspectList = [];
        for (var i = 0; i < resAspectList.length; i++) {
            var list = resAspectList[i];
            for (var attrKey in list) {
                if (list.hasOwnProperty(attrKey)) {
                    var attr = list[attrKey];
                    if (attr && attr.name == 'shortName' && attr.val) {
                        var value = attr.val[0];
                        if (value && value.str) {
                            aspectList.push(value.str);
                        }
                    }
                }
            }
        }
        return aspectList;
    })
    .computed('isDocument', function() {
        var attr = this.attributes()['attr:isDocument'];
        return attr === true || ((attr || [])[0] || {str: 'false'}).str == 'true';
    })
    .computed('isContainer', function() {
        var attr = this.attributes()['attr:isContainer'];
        return attr === true || ((attr || [])[0] || {str: 'false'}).str == 'true';
    })
    .property('selected', b)
    .load('selected', function() { this.selected(false) })

    .computed('isDoclibNode', function() {
        if(this.isDocument() === true || this.isContainer() === true) {
            return true;
        }
        if(this.isDocument() === false && this.isContainer() === false) {
            return false;
        }
        return false;
    })
    .property('doclib', o) // document library record data
    .method('hasAspect', function(aspect) {
        return _.contains(this.aspects(), aspect);
    })
    .method('hasPermission', function(permission) {
        //if we see record we have Read permission
        if (permission === "Read") {
            return true;
        }
        //if permissions is unknown allow to send requests
        return (this.permissions() || {})[permission] !== false;
    })
    ;

Column
    .property('id', s)
    .property('attKey', s)
    .computed('key', function() {
        var key = this.attKey();
        if (key) {
            return key;
        }
        var id = this.id();
        return id.match(':') ? 'attributes[\'' + id + '\']' : id;
    })
    .property('_info', AttributeInfo)
    .init(function() {
        this.model({ _info: this.id() });
    })
    .property('formatter', o)
    .property('sortable', b)
    .shortcut('label', '_info.customDisplayName')
    .shortcut('datatype', '_info.datatype.name')
    .shortcut('labels', '_info.labels')
    ;

Action
    .key('id', s)
    .property('attribute', Attribute)
    .property('groupType', s)
    .property('func', s)
    .property('label', s)
    .property('isDoclib', b)
    .property('permission', s)
    .property('requiredAspect', s)
    .property('forbiddenAspect', s)
    .property('syncMode', s)
    .property('settings', o)
    ;

ActionsColumn
    .property('id', s)
    .shortcut('key', 'id')
    .property('formatter', o)
    .constant('sortable', false)
    .property('label', s)
    ;


SortBy
    .property('id', s)
    .property('order', s)
    .computed('query', function() {
        return {
            attribute: this.id(),
            order: this.order()
        };
    })
    ;

JournalsWidget
    .property('documentNodeRef', s)
    .property('journalsLists', [JournalsList])
    .property('journals', [Journal])
    .property('journalsList', JournalsList)
    .property('journal', Journal)
    .property('filter', Filter)
    .property('settings', Settings)
    .property('_filter', Filter)
    .property('_settings', Settings)

    .shortcut('filters', 'journal.type.filters', [])
    .shortcut('settingsList', 'journal.type.settings', [])
    .shortcut('currentFilter', 'filter', 'journal.type.defaultFilter', null)
    .shortcut('currentSettings', 'settings', 'journal.type.defaultSettings', null)

    .computed('journalsListId', {
        read: function() {
            return this.resolve('journalsList.id', '');
        },
        write: function(value) {
            value ? this.journalsList(new JournalsList(value, this.documentNodeRef())) : this.journalsList(null);
        }
    })
    .computed('journalId', {
        read: function() {
            return this.resolve('journal.nodeRef', '');
        },
        write: function(value) {
            value ? this.journal(new Journal(value)) : this.journal(this.resolve('journalsList.default'));
        }
    })
    .computed('filterId', {
        read: function() {
            var filter = this.filter();
            if(!filter) return "";
            if(filter.nodeRef()) return filter.nodeRef();
            return JSON.stringify(filter.shortModel());
        },
        write: function(value) {
            if(!value) {
                this.filter(null);
                return;
            } else if(value.match('^workspace')) {
                this.filter(new Filter(value));
            } else {
                this.filter(new Filter(_.defaults(JSON.parse(value), {
                    nodeRef: null,
                    title: "",
                    criteria: [],
                    journalTypes: [],
                    permissions: { Write: true, Delete: true },
                })));
            }
        }
    })
    .computed('settingsId', {
        read: function() {
            var settings = this.settings();
            if(!settings) return "";
            if(settings.nodeRef()) return settings.nodeRef();
            return JSON.stringify(settings.shortModel());
        },
        write: function(value) {
            if(!value) {
                this.settings(null);
                return;
            } else if(value.match('^workspace')) {
                this.settings(new Settings(value));
            } else {
                this.settings(new Settings(_.defaults(JSON.parse(value), {
                    nodeRef: null,
                    title: "",
                    visibleAttributes: [],
                    journalTypes: [],
                    permissions: { Write: true, Delete: true },
                })));
            }
        }
    })

    // paging
    .property('skipCount', n)
    .property('maxItems', n)
    .property('defaultMaxItems', n)
    .property('totalItems', n)
    .property('hasMore', b)
    .property('adaptScroll', b, false)
    .computed('totalEstimate', function() {
        var total = this.totalItems();
        if(typeof total != "undefined" && total !== null) {
            return total;
        } else {
            // allow one page only
            return this.skipCount() + this.maxItems() + (this.hasMore() ? 1 : 0);
        }
    })
    .computed('skipCountId', koutils.numberSerializer('skipCount'))
    .computed('maxItemsId', koutils.numberSerializer('maxItems'))
    .property('records', [ Record ])

    .computed('recordsQuery', function() {

        var journal = this.journal();
        if (!journal) {
            return null;
        }
        var journalCriteria = journal.criteria();
        if (!journalCriteria) {
            return null;
        }
        var filter = this.currentFilter();
        if (!filter) {
            return null;
        }
        var filterCriteria = filter.usableCriteria();
        if (!filterCriteria) {
            return null;
        }

        return JSON.stringify(
            this.formatCriteria(_.flatten(_.flatten([
                journalCriteria,
                filterCriteria
            ]).map(function(c) { return c.query(); })))
        );
    })

    .method('formatCriteria', function(criteria) {

        var query = {};

        if (!criteria) {
            return query;
        }

        for (var i = 0; i < criteria.length; i++) {
            var criterion = criteria[i];
            query['field_' + i] = criterion.field;
            query['predicate_' + i] = criterion.predicate;
            query['value_' + i] = criterion.value;
        }

        return query;
    })

    .method('adaptScrollTop', function() {
        if (this.view && this.view.id) {
            var $dashlet = $(Dom.get(this.view.id));
            var skipCount = this.skipCount();
            var inWindow = function(element){
                var scrollTop = $(window).scrollTop();
                var windowHeight = $(window).height();
                var elementTop = element.offset().top;

                return (scrollTop <= elementTop && (element.height() + elementTop) < (scrollTop + windowHeight));
            };

            if (!inWindow($dashlet) && this._lastSkipCount !== undefined && this._lastSkipCount !== skipCount) {
                $('html, body').animate({
                    scrollTop: $dashlet.offset().top
                }, 1000);
            }

            this._lastSkipCount = skipCount;
        }
    })

    // selected records
    .computed('selectedRecords', function() {
        return _.filter(this.records(), function(record) {
            return record.selected();
        });
    })
    .computed('selectedRecordsAreDoclib', function() {
        return _.all(this.selectedRecords(), function(record) {
            return record.isDoclibNode();
        });
    })
    .computed('selectedRecordsAllowedPermissions', function() {
        var records = this.selectedRecords(),
            recordsPermissions = _.invoke(records, 'permissions'),
            allPermissions = _.flatten(_.map(recordsPermissions, _.pairs), true);
        return _.reduce(allPermissions, function(permissions, permission) {
            var name = permission[0], allowed = permission[1];
            if(!_.has(permissions, name) || !allowed) {
                   permissions[name] = allowed;
            }
            return permissions;
        }, {});
    })

    // datatable interface: fields, columns, records
    .shortcut('actionGroupId', 'journal.type.options.actionGroupId', defaultActionGroupId)
    .shortcut('actionFormatter', 'journal.type.options.actionFormatter', defaultActionFormatter)
    .computed('columns', function() {
        var visibleAttributes = this.resolve('currentSettings.visibleAttributes', []),
            journalType = this.resolve('journal.type'),
            recordUrl = this.recordUrl(),
            linkSupplied = recordUrl == null,
            recordLinkAttribute = this.recordLinkAttribute() || "cm:name",
            recordPriorityAttribute = this.recordPriorityAttribute() || "cm:name";

        if (!linkSupplied) {
            linkSupplied = !!(_.find(visibleAttributes, function(attr) {
                return recordLinkAttribute.indexOf(attr.name()) >= 0;
            }));
        }

        // set priority attribute to the first
        var priorityAttribute = recordPriorityAttribute.split(",").map(function(attr) { return attr.trim() }).reverse();
        for(var pa in priorityAttribute) {
            var attribute = _.find(visibleAttributes, function(attr) { return attr.name() == priorityAttribute[pa] });
            if (attribute) {
                var index = visibleAttributes.indexOf(attribute);
                visibleAttributes.splice(index, 1);
                visibleAttributes.unshift(attribute);
            }
        }

        // init columns
        var columns = _.map(visibleAttributes, function(attr) {
            var options = journalType ? journalType.attribute(attr.name()) : null,
                formatter = null,
                includeLink = false,
                withoutMultiple = false,
                labelByCode = null;

            if (options) {
                formatter = options.settings().formatter;
                withoutMultiple = options.settings().withoutMultiple;
            }
            if (attr.labels()) {
                labelByCode = formatters.labelByCode(attr.labels());
            }

            if (formatter) {
                formatter = formatters.loadedFormatter(formatter);
            } else if (labelByCode) {
                var classPrefix = attr.name().replace(/\W/g, '_') + "-";
                formatter = formatters._code(labelByCode, classPrefix, classPrefix);
                includeLink = !linkSupplied;
            } else if (attr.datatype()) {
                formatter = defaultFormatters[attr.datatype().name()];
                if (!formatter) includeLink = !linkSupplied;
            } else {
                formatter = formatters.loading();
            }

            if (recordLinkAttribute) {
                if (recordLinkAttribute.indexOf(attr.name()) != -1) includeLink = true;
            }

            if(includeLink) {
                formatter = formatters.doubleClickLink(recordUrl, this.recordIdField(), formatter, this.linkTarget());
                linkSupplied = true;
            }

            if (!withoutMultiple && formatter) {
                formatter = formatters.multiple(formatter);
            } else if (labelByCode) {
                formatter = formatters.transformUseLabel(labelByCode, formatter);
            } else {
                formatter = formatters.valueStrFormatter(!withoutMultiple, formatter);
            }

            return {
                id: attr.name(),
                attKey: 'attributes[\'' + attr.name() + '\']',
                sortable: options ? options.sortable() : false,
                formatter: formatter
            };
        }, this);

        columns = _.map(columns, Column);

        // init action column. Not for mobile version
        if (!Citeck.mobile.isMobileDevice() && !Citeck.mobile.hasTouchEvent()) {
            var actionGroupId = this.actionGroupId();
            var actionFormatter = this.actionFormatter();
            if (actionFormatter) {
                columns.unshift(new ActionsColumn({
                    id: 'actions',
                    label: this.msg("column.actions"),
                    formatter: formatters.jsActionsFormatter(actionFormatter)
                }));
            } else if(actionGroupId == buttonsActionGroupId) {
                columns.unshift(new ActionsColumn({
                    id: 'actions',
                    label: this.msg("column.actions"),
                    formatter: formatters.buttons()
                }));
            } else if(actionGroupId != noneActionGroupId) {
                columns.unshift(new ActionsColumn({
                    id: 'actions',
                    label: this.msg("column.actions"),
                    formatter: formatters.journalActions()
                }));
            }
        }

        // init selected column
        columns.unshift(new ActionsColumn({
            id: 'selected',
            label: '<input type="checkbox" data-action="select-all" />',
            formatter: formatters.checkbox('selected')
        }));
        return columns;
    })
    .computed('fields', function() {
        var defaultFields = [
            { key: 'nodeRef' },
            { key: 'type' }
        ];
        var attributes = this.resolve('journal.type.attributes', []);
        return _.map(attributes, function(attr) {
            var id = attr.name();
            return {
                key: 'attributes[\'' + id + '\']'
            };
        }).concat(defaultFields);
    })
    .property('sortBy', [ SortBy ])
    .computed('propSortBy', {
        read: function () {
            var result = this.sortBy();
            if (!result || result.length == 0) {
                result = this.defaultSortBy();
            }
            return result;
        },
        write: function (value) {
            this.sortBy(value);
        }
    })
    .computed('defaultSortBy', function () {
        var options = this.resolve('journal.type.options');
        var result = [];
        if (options && options['defaultSortBy']) {
            var data = eval(options['defaultSortBy']);
            for (var i in data) {
                result.push(new SortBy(data[i]));
            }
        }
        return result;
    })
    .computed('sortByQuery', function() {
        return _.invoke(this.propSortBy() || [], 'query');
    })
    .computed('sortByFirst', {
        read: function() {
            if(this.propSortBy().length == 0) {
                return null;
            }
            var sortBy = this.propSortBy()[0];
            return {
                key: 'attributes[\'' + sortBy.id() + '\']',
                dir: sortBy.order() == 'asc' ? 'yui-dt-asc' : 'yui-dt-desc'
            };
        },
        write: function(value) {
            this.propSortBy([
                new SortBy({
                    id: value.key.replace(/^attributes\[\'(.*)\'\]$/, '$1'),
                    order: value.dir == 'yui-dt-asc' ? 'asc' : 'desc'
                })
            ]);
        }
    })
    .computed('loading', function() {
        return !this.recordsLoaded() || this.externalLoading();
    })
    .property('externalLoading', b, false)
    .property('selectedId', s)
    .shortcut('recordIdField', 'journal.type.options.doubleClickId', 'nodeRef')
    .shortcut('recordUrl', 'journal.type.options.doubleClickLink', null)
    .shortcut('linkTarget', 'journal.type.options.linkTarget', '_self')
    .shortcut('recordLinkAttribute', 'journal.type.options.clickLinkAttribute', null)
    .shortcut('recordPriorityAttribute', 'journal.type.options.priorityAttribute', null)
    .computed('gotoAddress', function() {
        var id = this.selectedId(),
            url = this.recordUrl();
        if(!id || !url) return null;
        return YAHOO.lang.substitute(url, {
            id: id
        });
    })
    .computed('dependencies', function() {
        var journalOptions = this.resolve('journal.type.options', {});
        return _.compact([ journalOptions.js, journalOptions.css ]);
    })
    .property('multiActions', [ Action ])
    .computed('actionsOnFiltered', function() {

        var journalType = this.resolve('journal.type');

        if (journalType) {
            var groupActions = journalType.groupActions();
            return (groupActions || []).filter(function (action) {
                return action.groupType() == 'filtered';
            });
        }

        return [];
    })
    .computed('allowedMultiActions', function() {
        var records = this.selectedRecords(),
            doclibMode = _.all(records, function(record) {
                return record.isDoclibNode();
            }),
            hasPermission = function(record, permission) {
                return record.hasPermission(permission);
            },
            hasAspect = function(record, aspect) {
                return record.hasAspect(aspect);
            };

        if(records.length == 0) return [];

        var actions = this.multiActions(),
            journal = this.journal(),
            journalType = journal.type();

        if (journal && journalType) {
            // get attribute actions
            var attributes = journalType.attributes();
            actions = _.uniq(_.reduce(attributes, function(actions, att){
                return actions.concat(att.batchEdit());
            }, actions));

            // get group actions
            var groupActions = journalType.groupActions();
            actions = actions.concat(groupActions);
        }

        var filteredActions = _.filter(actions, function(action) {
            // sync mode check:
            if (action.syncMode() != null) return false;

            if (action.groupType() == 'filtered') return false;

            // doclib mode check:
            if (!doclibMode && action.isDoclib()) return false;

            // permission check
            var permission = action.permission();
            if (permission && !_.all(records, _.partial(hasPermission, _, permission))) {
                return false;
            }

            // required aspect check
            var requiredAspect = action.requiredAspect();
            if (requiredAspect && !_.all(records, _.partial(hasAspect, _, requiredAspect))) {
                return false;
            }

            // forbidden aspect check
            var forbiddenAspectArray = action.forbiddenAspect();

            if (forbiddenAspectArray != null && forbiddenAspectArray.length > 0) {
                var forbiddenAspect = forbiddenAspectArray.split(",");

                for (var i = 0; i < forbiddenAspect.length; i++) {
                    if (forbiddenAspect[i] && _.any(records, _.partial(hasAspect, _, forbiddenAspect[i]))) {
                        return false;
                    }
                }
            }

            return true;
        });

        return filteredActions;
    })

    .property('newJournalsPageEnable', b)
    .property('uiType', o)
    .computed('fullscreenLink', function() {
        var self = this;
        var newJournalsPageEnable = this.newJournalsPageEnable();
        var uiType = this.uiType() || {};

        var journalsList = this.journalsList(),
            journalId = this.journalId(),
            filterId = this.filterId(),
            settingsId = this.settingsId(),
            prefix = '',
            postfix = '',
            site = '',
            tokens = {
                journal: this.journalId(),
                filter: this.filterId(),
                settings: this.settingsId(),
                skipCount: this.skipCountId(),
                maxItems: this.maxItemsId()
            },
            hash = _.map(tokens, function(value, key) {
                return key + '=' + value;
            }).join('&');
        if(journalsList != null) {
            if(journalsList.scope() != 'global') {
            	site = journalsList.scopeId();
                prefix = journalsList.scope() + '/' + site + '/';
            }
            postfix = '/list/' + journalsList.listId();
        }

        var link = YAHOO.lang.substitute('{context}{prefix}journals2{postfix}#{hash}', {
            context: Alfresco.constants.URL_PAGECONTEXT,
            prefix: prefix,
            postfix: postfix,
            hash: hash
        });

        var uiTypeKey = !!journalId ? journalId : site;
        if (uiType.key != uiTypeKey) {
            var promice = !!journalId
                ? journalsApi.getJournalUIType(journalId)
                : MenuApi.getSiteUiType(site);

            promice.then(function (type) {
                self.uiType({
                    key: uiTypeKey,
                    type: type
                });
            })
        }

        switch (uiType.type) {
            case 'share': newJournalsPageEnable = false; break;
            case 'react': newJournalsPageEnable = true; break;
        }

        if (newJournalsPageEnable === null) {
            self.newJournalsPageEnable(false);

            var isNewJournalsPageEnable = Citeck.Records.get('ecos-config@new-journals-page-enable').load('.bool');
            var isJournalAvailibleForUser = checkFunctionalAvailabilityHelper
                .checkFunctionalAvailabilityForUser("default-ui-new-journals-access-groups");

            Promise.all([isNewJournalsPageEnable, isJournalAvailibleForUser])
                .then(function (values) {
                    self.newJournalsPageEnable(values.includes(true));
                })
                .catch(function () {});
        } else if (newJournalsPageEnable === true) {
            link = menuApi.getNewJournalPageUrl({
                listId: journalsList.id(),
                siteName: null,
                journalRef: journalId
            });
        }

        return link;
    })

    .init(function() {

        this.columns.extend({ rateLimit: 100 });
        this.records.extend({ rateLimit: 100 });
        this.recordsQueryDataImpl.extend({
            rateLimit: {
                timeout: 100,
                method: "notifyWhenChangesStop"
            }
        });

        this.journal.subscribe(function() {
            // reset filter and settings
            this.filter(null);
            this.settings(null);
            this.propSortBy([]);
            this.skipCount(0);
            this.selectedId(null);
            this.maxItems(this.defaultMaxItems() || 10);
        }, this);
        this.currentFilter.subscribe(function() {
            this.skipCount(0);
            this._filter(this.resolve('currentFilter.clone'));
        }, this);
        this.currentSettings.subscribe(function() {
            this._settings(this.resolve('currentSettings.clone'));
        }, this);

        this.recordsQueryData.subscribe(this.performSearch, this);
    })

    // required to allow setup rateLimit
    .computed('recordsQueryData', function() {
        return this.recordsQueryDataImpl();
    })

    .computed('recordsQueryDataImpl', function() {

        var recordsQuery = this.recordsQuery();

        if (!recordsQuery) {
            return null;
        }

        var result = {
            journalId: this.journalId(),
            query: recordsQuery,
            pageInfo: {
                sortBy: this.sortByQuery(),
                skipCount: this.skipCount() || 0,
                maxItems: this.maxItems() || this.defaultMaxItems() || 10
            }
        };

        if (Citeck.constants.DEBUG) {
            result.debug = true;
        }
        return result;
    })

    .method('performSearch', function() {
        var error = this.validateForError();
        if (error) {
            var startMessagePart = msg("label.too-many-multiple-values.start");
            var endMessagePart = msg("label.too-many-multiple-values.end");
            Alfresco.util.PopupManager.displayPrompt({
                text: startMessagePart + " " + error.maxCount + " " + endMessagePart
            });
            return;
        }
        this.recordsLoaded(false);
        this.records.reload();
    })
    .property('recordsLoaded', b, false)
    .property('createReportType', s)
    .property('createReportDownload', b)
    .property('createReportFormId', s)
    .computed('createReportTarget', function() {
        if (this.createReportDownload() == true)
            return "_self";
        else
            return '_blank';
    })
    .computed('createReportLink', function() {
        var isDownload = (this.createReportDownload() == true);
        var token = "";

        if (Alfresco.util.CSRFPolicy && Alfresco.util.CSRFPolicy.isFilterEnabled()) {
            token = "&" + Alfresco.util.CSRFPolicy.getParameter() + "="
                        + encodeURIComponent(Alfresco.util.CSRFPolicy.getToken());
        }

        return Alfresco.constants.PROXY_URI + "report/criteria-report?download=" + isDownload + token;
    })
    .computed('createReportQuery', function() {
        var journal = this.journal();
        if (journal) {
            var journalCriteria = journal.criteria();
            if (journal.criteria.loaded()) {
                var filter = this.currentFilter();
                if (filter) {
                    var filterCriteria = filter.usableCriteria();
                    if (filter.criteria.loaded()) {

                        var query = this.formatCriteria(_.flatten(_.flatten([
                            journalCriteria,
                            filterCriteria
                        ]).map(function(c) { return c.query(); })));

                        query.sortBy = this.sortByQuery();
                        query.reportType = this.createReportType();
                        query.reportTitle = journal.title();

                        var reportColumns = [];
                        var customReportAttributes = this.customReportAttributes();
                        var visibleAttributes = this.resolve('currentSettings.visibleAttributes', []);

                        if (customReportAttributes && customReportAttributes.length > 0) {
                            reportColumns.push({
                                attribute: "rowNum",
                                title: "№"
                            });

                            for (var i = 0; i < customReportAttributes.length; i++) {
                                reportColumns.push({
                                    attribute: customReportAttributes[i].name._value(),
                                    title: customReportAttributes[i].customDisplayName()
                                });
                            }
                        } else if (visibleAttributes) {
                            reportColumns.push({
                                attribute: "rowNum",
                                title: "№"
                            });

                            for (var i = 0; i < visibleAttributes.length; i++) {
                                reportColumns.push({
                                    attribute: visibleAttributes[i].name._value(),
                                    title: visibleAttributes[i].customDisplayName()
                                });
                            }
                        }

                        query.reportColumns = reportColumns;
                        query.reportFilename = query.reportTitle + "." + query.reportType;

                        return JSON.stringify(query);
                    }
                }
            }
        }

        return "{}";
    })
    .computed('customReportAttributes', function() {
        var allAttributes = this.resolve('journal.type.attributes');
        if (!allAttributes) {
            return [];
        }

        var rawCustomReportAttributes = this.resolve('journal.options.customReportAttributes', "");
        if (!rawCustomReportAttributes) {
            return [];
        }
        var customReportAttributes = rawCustomReportAttributes.split(",").map(function(attr) { return attr.trim() });

        var resultAttributes = [];
        for (var i in customReportAttributes) {
            var attributeName = customReportAttributes[i];
            var attribute = _.find(allAttributes, function(attribute) {
                return attribute.name() === attributeName;
            });
            if (attribute) {
                resultAttributes.push(attribute);
            }
        }
        return resultAttributes;
    })
    .method('createReport', function(reportType, isDownload) {
        this.createReportType(reportType);
        this.createReportDownload(isDownload);
        var reportForm = Dom.get(this.createReportFormId());

        if (this.createReportQuery() != "{}")
            reportForm.submit();
    })
    .method('createReportFormInit', function(reportFormId) {
        this.createReportFormId(reportFormId);
    })
    .computed('reportButtonDisabled', function() {
        var records = this.records();
        if (typeof records != "undefined" && records !== null)
            return (records.length == 0);
        else
            return true;
    })

    .method('selectAllRecords', function() {
        _.each(this.records(), function(record) {
            record.selected(true);
        });
    })
    .method('selectInvertRecords', function() {
        _.each(this.records(), function(record) {
            record.selected(!record.selected());
        });
    })
    .method('deselectAllRecords', function() {
        _.each(this.records(), function(record) {
            record.selected(false);
        });
    })
    .method('validateForError', function() {
        var filter = this._filter();
        if (filter) {
            var filterCriteria = filter.usableCriteria();
            if (filter.criteria.loaded()) {
                try {
                    filterCriteria.forEach(function (criteria) {
                        criteria.validateForError();
                    });
                } catch (err) {
                    if (err.errorName && err.errorName === "Too many multiple values") {
                        return err;
                    }
                }
            }
        }
        return null;
    })

    /*********************************************************/
    /*             FILTERS AND SETTINGS FUNCTIONS            */
    /*********************************************************/

    .methods({
        addCriterion: function(field, predicate, value) {
            // TODO add default predicate according to journal field settings
            this._filter().criteria.push(new Criterion({
                field: field,
                predicate: predicate || null,
                value: value || ""
            }));
        },

        applyCriteria: function() {
            var error = this.validateForError();
            if (error) {
                var startMessagePart = msg("label.too-many-multiple-values.start");
                var endMessagePart = msg("label.too-many-multiple-values.end");
                Alfresco.util.PopupManager.displayPrompt({
                    text: startMessagePart + " " + error.maxCount + " " + endMessagePart
                });
                return;
            }
            this.skipCount(0);
            this.filter(this._filter());
        },

        clearCriteria: function() {
            this.filter(null);
            this._filter(this.currentFilter().clone());
        },

        applySettings: function() {
            this.settings(this._settings().clone());
        },

        resetSettings: function() {
            this.settings(null);
            this._settings(this.currentSettings().clone());
        },
    })

    /*********************************************************/
    /*            SELECT, SAVE AND REMOVE METHODS            */
    /*********************************************************/

    .methods({

        selectJournalsList: function(id) {
            this.journalsList(id ? new JournalsList(id) : null);
        },

        selectJournal: function(journalId) {
            this.journal(journalId ? new Journal(journalId) : null);
        },

        selectFilter: function(filterId) {
            this.filter(filterId ? new Filter(filterId) : null);
        },

        selectSettings: function(settingsId) {
            this.settings(settingsId ? new Settings(settingsId) : null);
        },

        saveFilter: function() {
            if(!this.resolve('_filter.valid', false)) return;
            this._filter().journalTypes.push(this.journal().type());
            Filter.save(this._filter(), {
                scope: this,
                fn: function(newFilter) {
                    this.filter(newFilter);
                    this.journal().type().filters.push(newFilter);
                }
            });
        },

        removeFilter: function(filter) {
            if(!filter.nodeRef()) return;
            Filter.remove(filter, {
                scope: this,
                fn: function() {
                    var journalType = this.resolve('journal.type');
                    if(journalType) {
                        journalType.filters.remove(filter);
                    }
                    if(this.filter() == filter) {
                        this.filter(null);
                    }
                }
            });
        },

        saveSettings: function() {
            if(!this.resolve('_settings.valid', false)) return;
            this._settings().journalTypes.push(this.journal().type());
            Settings.save(this._settings(), {
                scope: this,
                fn: function(newSettings) {
                    this.settings(newSettings);
                    this.journal().type().settings.push(newSettings);
                }
            });
        },

        removeSettings: function(settings) {
            if(!settings.nodeRef()) return;
            Settings.remove(settings, {
                scope: this,
                fn: function() {
                    var journalType = this.resolve('journal.type');
                    if(journalType) {
                        journalType.settings.remove(settings);
                    }
                    if(this.settings() == settings) {
                        this.settings(null);
                    }
                }
            });
        },

        _removeRecord: function(record) {
            Record.remove(record, {
                scope: this,
                fn: function() {
                    this.records.remove(record);
                }
            });
        },

        removeRecord: function(record) {
            if(!record.id()) return;
            this._removeRecord(record);
        },

        removeRecords: function(records) {
            _.each(records, this._removeRecord, this);
        },
    })

    ;

/*********************************************************/
/*                        REST API                       */
/*********************************************************/

JournalsList
    .load('uiservJournals', function (viewModel) {

        var fromCache = uiservListJournalsCache[this.id()];

        if (fromCache) {
            fromCache.then(function (res) {
                viewModel.model({ uiservJournals: res });
            });
        } else {
            uiservListJournalsCache[this.id()] = Citeck.Records.query({
                    sourceId: 'uiserv/journal_v0',
                    language: 'list-id',
                    query: {
                        listId: this.id()
                    }
                },
                {
                    title: 'title'
                })
                .then(function (res) { return res.records; })
                .catch(function () { return []; })
                .then(function (records) {
                    var result = [];
                    for (var i = 0; i < records.length; i++) {
                        var rec = records[i];
                        var id = rec.id.replace("uiserv/journal_v0@", '');
                        result.push({
                            "nodeRef": id,
                            "title": rec.title,
                            "type": id
                        });
                    }
                    viewModel.model({ uiservJournals: result });
                    return result;
                });
        }
    })
    .load('*', koutils.simpleLoad({
        url: Alfresco.constants.PROXY_URI + "api/journals/list?journalsList={id}&nodeRef={documentNodeRef}"
    }))
    ;

var journalTypeModelPostProcessing = function (model) {
    return {
        id: model.id,
        attributes: model.attributes,
        options: model.settings,
        groupActions: model.groupActions,
        datasource: model.datasource,
        formInfo: {
            type: model.settings ? model.settings.type : "",
            formId: model.settings ? model.settings.formId : ""
        }
    }
};

JournalType
    .load('filters', koutils.simpleLoad({
        url: Alfresco.constants.PROXY_URI + "api/journals/filters?journalType={id}",
        resultsMap: { filters: 'filters' }
    }))
    .load('settings', koutils.simpleLoad({
        url: Alfresco.constants.PROXY_URI + "api/journals/settings?journalType={id}",
        resultsMap: { settings: 'settings' }
    }))
    .load('gqlschema', koutils.simpleLoad({
        url: Alfresco.constants.PROXY_URI + "api/journals/gql-schema?journalId={id}",
        resultsMap: { gqlschema: 'schema' }
    }))
    .load('*', function (viewModel) {
        Citeck.Records.get("uiserv/journal_v0@" + this.id())
            .load(".json")
            .catch(function () { return {}; })
            .then(function (resp) {
                if (resp && resp.type && resp.type.id) {
                    var model = journalTypeModelPostProcessing(resp.type);
                    model.journal = resp.nodeRef;
                    viewModel.model(model);
                } else {
                    koutils.simpleLoad({
                        url: Alfresco.constants.PROXY_URI + "api/journals/types/{id}",
                        resultsMap: journalTypeModelPostProcessing,
                        postprocessing: function(model) { model["journal"] = this; }
                    })(viewModel);
                }
            });
    });

Journal
    .load('*', function (viewModel) {

        var self = this;

        Citeck.Records.get("uiserv/journal_v0@" + viewModel.nodeRef())
            .load(".json")
            .catch(function () { return {}; })
            .then(function (resp) {

                var postprocessing = function(model) {

                    model = Object.assign({}, model);

                    for (var c in model.createVariants) {
                        model.createVariants[c]["journal"] = self;
                    }
                    if (model.type && model.type.id) {
                        model.type = journalTypeModelPostProcessing(model.type);
                        model.type.journal = self;
                    }
                    return model;
                }

                if (resp && resp.nodeRef) {
                    viewModel.model(postprocessing(resp));
                } else {
                    var nodeRef = self.nodeRef();

                    var loadImpl = function (nodeRef) {
                        koutils.simpleLoad({
                            url: Alfresco.constants.PROXY_URI + "api/journals/journals-config?nodeRef=" + nodeRef,
                            postprocessing: postprocessing
                        })(viewModel);
                    };

                    if (nodeRef && nodeRef.indexOf("workspace://") === -1) {
                        Citeck.Records.query({
                            query: 'TYPE:"journal:journal" AND =journal:journalType:"' + nodeRef + '"',
                            language: 'fts-alfresco'
                        }).then(function (resp) {
                            if (resp && resp.records && resp.records.length) {
                                loadImpl(resp.records[0]);
                            } else {
                                throw new Error("Journal nodeRef can't be received for id: '" + nodeRef + "'");
                            }
                        });
                    } else {
                        loadImpl(nodeRef);
                    }
                }
        });
    });

var uiservListJournalsCache = {};

Filter
    .load('*', koutils.simpleLoad({
        url: Alfresco.constants.PROXY_URI + "api/journals/filter?nodeRef={nodeRef}"
    }))
    .save(koutils.simpleSave({
        url: Alfresco.constants.PROXY_URI + "api/journals/filter",
        toRequest: function(filter) {
            return filter.saveModel();
        },
        toResult: function(model) {
            return new Filter(model);
        }
    }))
    .remove(koutils.simpleSave({
        method: "DELETE",
        url: Alfresco.constants.PROXY_URI + "api/journals/filter?nodeRef={nodeRef}"
    }))
    ;

Settings
    .load('*', koutils.simpleLoad({
        url: Alfresco.constants.PROXY_URI + "api/journals/selected-attributes?nodeRef={nodeRef}"
    }))
    .save(koutils.simpleSave({
        url: Alfresco.constants.PROXY_URI + "api/journals/settings-save",
        toRequest: function(settings) {
            return settings.saveModel();
        },
        toResult: function(model) {
            return new Settings(model);
        }
    }))
    .remove(koutils.simpleSave({
        method: "DELETE",
        url: Alfresco.constants.PROXY_URI + "api/journals/settings?nodeRef={nodeRef}"
    }))
    ;

AttributeInfo
    .load('*', koutils.bulkLoad(new BulkLoader({
        url: Alfresco.constants.PROXY_URI + "components/journals/journals-metadata",
        method: "GET",
        emptyFn: function() {
            return { attributes: [] };
        },
        addFn: function(query, id) {
            if(id) {
                query.attributes.push(id);
                return true;
            } else {
                return false;
            }
        },
        getFn: function(response) {
            return response.json.attributes;
        }
    }), 'name'))
    ;

JournalsWidget
    .load('journalsLists', koutils.simpleLoad({
        url: Alfresco.constants.PROXY_URI + "api/journals/lists",
        resultsMap: { journalsLists: 'journalsLists' }
    }))
    .load('journals', koutils.simpleLoad({
        url: Alfresco.constants.PROXY_URI + "api/journals/all",
        resultsMap: { journals: 'journals' }
    }))
    .load('records', function() {

        var self = this;

        var load = function(iteration) {

            if (!iteration) {
                iteration = 1;
            }

            if (self.records.loaded()) {
                logger.debug("Records are already loaded, skipping");
                return;
            }

            var recordsQuery = this.recordsQueryData();
            if (!recordsQuery) {
                logger.debug("Records query data is not ready, skipping");
                koutils.subscribeOnce(this.recordsQueryData, function() { load.call(self, iteration); });
                return;
            }

            var successCallback = function (data) {

                var actualQuery = self.recordsQueryData();

                koutils.subscribeOnce(self.loading, function() {
                    if (self.adaptScroll()) {
                        self.adaptScrollTop();
                    }
                }, self);

                if (iteration < 4 && !_.isEqual(recordsQuery, actualQuery)) {
                    load.call(self, iteration + 1);
                    return;
                }

                if (iteration >= 4) {
                    console.error("Infinite loop? Iterations: " + iteration);
                }

                var records = data.records;

                records = _.map(records, function(node) {
                    var record = {attributes: {}};
                    for (var key in node) {
                        var item = node[key];
                        if (key === "id") {
                            record['nodeRef'] = item;
                        } else {
                            if (item && item.name) {
                                record.attributes[item.name] = item ? item.val : [];
                            } else {
                                record.attributes[key] = item ? item : [];
                            }
                        }
                    }
                    return record;
                });

                customRecordLoader(new Citeck.utils.DoclibRecordLoader(self.actionGroupId()));

                koutils.subscribeOnce(self.records, function() {
                    self.recordsLoaded(true);
                }, self);

                self.model({
                    records: records,
                    skipCount: recordsQuery.pageInfo.skipCount,
                    maxItems: recordsQuery.pageInfo.maxItems,
                    totalItems: data.totalCount,
                    hasMore: data.hasMore
                });
            };

            var journalType = this.journal().type();
            var isUiservJournal = !!(journalType.options() || {})["__uiserv__"];
            var datasource = journalType.datasource ? journalType.datasource() || "" : "";

            if (isUiservJournal || datasource.indexOf('/') >= 0) {

                var queryImpl = function () {

                    var query = {
                        sourceId: datasource,
                        page: {
                            maxItems: recordsQuery.pageInfo.maxItems,
                            skipCount: recordsQuery.pageInfo.skipCount
                        },
                        sortBy: recordsQuery.sortBy
                    };

                    if (self.journal().predicate()) {
                        query.language = 'predicate';
                        query.query = self.journal().predicate();
                    } else {
                        query.language = 'criteria';
                        query.query = recordsQuery.query;
                    }

                    var body = {
                        query: query
                    }
                    var schema = "{}";
                    if (!isUiservJournal) {
                        schema = journalType.gqlschema();
                    }
                    if (schema !== "{}") {
                        body.schema = schema;
                    } else {
                        var atts = self.resolve('currentSettings.visibleAttributes', []);
                        var attributes = {};
                        for (var i = 0; i < atts.length; i++) {
                            var att = atts[i];
                            attributes[att.name()] = att.name() + "[]";
                        }

                        attributes['attr:isDocument'] = 'attr:isDocument?bool';
                        attributes['attr:isContainer'] = 'attr:isContainer?bool';

                        body.attributes = attributes;
                    }

                    Alfresco.util.Ajax.jsonPost({
                        url: '/share/api/records/query',
                        dataObj: body,
                        successCallback: {
                            scope: this,
                            fn: function(response) {
                                var resp = response.json;
                                resp.records = (resp.records || []).map(function (r) {
                                    var flat = {};
                                    for (var att in r.attributes) {
                                        if (r.attributes.hasOwnProperty(att)) {
                                            flat[att] = r.attributes[att];
                                        }
                                    }
                                    flat.id = r.id;
                                    return flat;
                                });
                                successCallback(resp);
                            }
                        }
                    });
                };

                if (!isUiservJournal && !journalType.gqlschema.loaded()) {
                    koutils.subscribeOnce(journalType.gqlschema, queryImpl, this);
                    journalType.gqlschema()
                } else {
                    queryImpl();
                }

            } else {

                delete recordsQuery.journalId;

                Alfresco.util.Ajax.jsonPost({
                    url: Alfresco.constants.PROXY_URI + "/api/journals/records?journalId=" + journalType.id(),
                    dataObj: recordsQuery,
                    successCallback: {
                        scope: this,
                        fn: function(response) {
                            successCallback(response.json);
                        }
                    }
                });
            }
        };
        load.call(this);
    })
    ;

Record
    // TODO define load method - to load selected records
    .load('doclib', function(record) {
        if(record.isDoclibNode() === true) {
            var recordNodeRef = record.nodeRef(),
                recodrId = recordNodeRef ? recordNodeRef.replace(/.*@/, "") : "";
            customRecordLoader().load(recodrId, function(id, model) {
                record.model({ doclib: model });
            });
        } else if(record.isDoclibNode() === false) {
            record.doclib(null);
        } else {
            // if it is not loaded yet - do not do anything
        }
    })
    .remove(koutils.simpleSave({
        method: "DELETE",
        url: Alfresco.constants.PROXY_URI + "citeck/node?nodeRef={nodeRef}"
    }))
    ;


/*********************************************************/
/*              KNOCKOUT PERFORMANCE TUNING              */
/*********************************************************/


var rateLimit = { rateLimit: { timeout: 5, method: "notifyWhenChangesStop" } };

JournalsWidget
//	.extend('*', { logChange: true })
//	.extend('columns', { rateLimit: 0 })
//	.extend('records', { rateLimit: 0 })
//	.extend('*', { rateLimit: 0 })
    ;

AttributeInfo
    .extend('*', rateLimit)
    ;

Datatype
    .extend('*', rateLimit)
    ;

Column
    .extend('*', rateLimit)
    ;

// Journals widget class

var Journals = function(name, htmlid, dependencies, ViewModelClass) {
    Journals.superclass.constructor.call(this, name || "Citeck.widgets.Journals", htmlid, dependencies);
    this.viewModel = new ViewModelClass({});

    // inject msg method
    this.viewModel.msg = this.bind(this.msg);
};

YAHOO.extend(Journals, Alfresco.component.Base, {

    options: {

        predicateLists: [],

    },

    onReady: function() {
        // init objects from cache
        this.initCachedObjects();

        // init viewmodel
        this.viewModel.model(this.options.model);

        // init views
        ko.applyBindings(this.viewModel, Dom.get(this.id));
    },

    initCachedObjects: function() {
        _.each(this.options.cache, function(models, className) {
            var constructor = koclass(className);
            _.each(models, function(model) {
                if(!model) return;
                new constructor(model);
            });
        });
    },

});

return Journals;

})
