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

require([
    'ecosui!ecos-records',
    'ecosui!react-dom',
    'ecosui!user-in-groups-list-helper'
], function(Records, ReactDOM, checkFunctionalAvailabilityHelper) {

    if (typeof Citeck == "undefined") Citeck = {};
    Citeck.forms = Citeck.forms || {};

    var Event = YAHOO.util.Event;

    // get parent form of element
    Citeck.forms.getForm = function(id) {
        var elem = Dom.get(id);
        while(elem && elem.nodeName.toUpperCase() != "FORM") {
            elem = elem.parentElement;
        }
        return elem;
    };

    Citeck.forms.onChange = function(fields, func, id) {
        for(var i in fields) {
            if (!fields.hasOwnProperty(i)) continue;
            Event.on(fields[i], "change", function(e) {
                func(id);
            });
            Event.on(fields[i], "click", function(e) {
                YAHOO.lang.later(100, this, function() {
                    func(id);
                }, []);
            });
            Event.on(fields[i], "blur", function(e) {
                func(id);
            });
        }
        YAHOO.Bubbling.on("renderCurrentValue", function(layer, args) {
            var control = args[1].eventGroup;
            if (control && fields.indexOf(control.id) != -1) {
                YAHOO.lang.later(0, this, func, id);
            }
        });

        var onFormContentReady = function(layer, args) {
            Event.onAvailable(id, function() {
                func(id);
            });
        };

        var onFormContainerDestroyed = function(layer, args) {
            YAHOO.Bubbling.unsubscribe("formContentReady", onFormContentReady);
            YAHOO.Bubbling.unsubscribe("formContainerDestroyed", onFormContainerDestroyed);
        };

        YAHOO.Bubbling.on("formContentReady", onFormContentReady);
        YAHOO.Bubbling.on("formContainerDestroyed", onFormContainerDestroyed);

    };

    // element display conditions map
    // there can be only one condition for one element
    var displayConditions = {};

    /**
     * Set conditional display of element.
     * Condition is specified in terms of form fields.
     * @param id - element to be manipulated
     * @param condition - string condition to be evaluated
     *        (if it is evaluated to true, element is visible, otherwise - hidden)
     * @param fields - field ids, that should trigger display update
     */
    Citeck.forms.displayConditional = function(id, condition, fields) {
        displayConditions[id] = condition;
        Citeck.forms.onChange(fields, Citeck.forms.updateDisplay, id);
    };

    /**
     * Update display of element.
     * Evaluates condition of element (if specified), and displays/hides it
     * @param id - element, which should be displayed/hidden
     */
    Citeck.forms.updateDisplay = function(id) {
        var condition = displayConditions[id];
        if (!condition)
            return;
        try {
            if (Citeck.forms.evaluate(id, condition)) {
                Dom.removeClass(id, "hidden");
            } else {
                Dom.addClass(id, "hidden");
            }
        } catch(e) {
            // ...
        }
    };

    var valueExpressions = {};

    Citeck.forms.valueComputed = function(id, expression, fields) {
        valueExpressions[id] = expression;
        Citeck.forms.onChange(fields, Citeck.forms.updateValue, id);
    };

    Citeck.forms.updateValue = function(id) {
        var expression = valueExpressions[id],
            input = Dom.get(id);
        if (!input || !expression)
            return;
        try {
            input.value = Citeck.forms.evaluate(id, expression);
        } catch(e) {
            // do nothing
        }
    };

    /**
     * It evaluates specified expression in the form context of the element
     * specified by id.
     *
     * This function can throws an exception, if expression can not be
     * evaluated.
     *
     * @param id - element id, it is used to find out form context
     * @param expression - input expression
     * @returns eval result or null, It returns null if form context is not found.
     * @throws exception - it throws an exception if expression can not
     * be evaluated.
     */
    Citeck.forms.evaluate = function(id, expression) {
        var result = null;
        var form = Citeck.forms.getForm(id);
        if (form) {
            var formUI = Alfresco.util.ComponentManager.get(form.id);
            var formData = formUI.formsRuntime.getFormData();
            with(formData) {
                result = eval(expression);
            }
        }
        return result;
    };

    Citeck.forms.simpleDeleteDialog = function(successCallback, failureCallback) {
        Alfresco.util.PopupManager.displayPrompt({
            title: Alfresco.util.message("message.confirm.delete.1.title"),
            text: Alfresco.util.message("message.confirm.delete"),
            noEscape: true,
            buttons: [
                {
                    text: Alfresco.util.message("button.delete"),
                    handler: function() {
                        if (successCallback && _.isFunction(successCallback)) successCallback();
                        this.hide();
                    }
                },
                {
                    text: Alfresco.util.message("button.cancel"),
                    handler: function() {
                        if (failureCallback && _.isFunction(failureCallback)) failureCallback();
                        this.hide();
                    },
                    isDefault: true
                }
            ]
        });
    };

    // duplicate item on table template
    Citeck.forms.duplicateValue = function (record, parent, params) {
        params = params || {};
        var showDialogAfterDuplicate = params.showDialogAfterDuplicate || false;
        var needPullForDuplicate = params.needPullForDuplicate || "";
        var cloneParent = params.cloneParent || false;

        var attributes =  record.resolve('allData.attributes');
        if (attributes && record && record.typeShort()) {

            record.inSubmitProcess(true);
            var data = {
                attributes: {},
                view: {'class': record.typeShort(), id: "", kind: "", mode: "create", template: "table", params: {}}
            };

            var ignoredAttributes = ["attr:noderef", "attr:parentassoc", "attr:aspects"];
            if (!cloneParent) {
                ignoredAttributes.push("attr:parent");
            }
            for (var key in attributes) {
                if (attributes[key] && ignoredAttributes.indexOf(key) == -1) {
                    if (key == 'attr:types') {
                        data.attributes[key] = [record.typeShort()]
                    } else {
                        data.attributes[key] = attributes[key];
                    }
                }
            }

            var requests = {requests: []};
            if (needPullForDuplicate) {
                var attributeKeysForPull = needPullForDuplicate.split(",");
                for (var j = 0; j < attributeKeysForPull.length; j ++) {
                    var attributeKeyForPull = attributeKeysForPull[j];
                    var request = {
                        nodeRef: record.nodeRef(),
                        attribute: attributeKeyForPull
                    };
                    requests.requests.push(request);
                }
            }

            var addVariant = function () {
                var url = Alfresco.constants.PROXY_URI + "citeck/invariants/view?type=" + record.typeShort();
                Alfresco.util.Ajax.jsonPost({
                    url: url,
                    dataObj: data,
                    requestContentType: Alfresco.util.Ajax.JSON,
                    successCallback: {
                        fn: function (response) {
                            if (response && response.json && response.json.result) {
                                if (showDialogAfterDuplicate) {
                                    Citeck.forms.dialog(
                                        response.json.result,
                                        null,
                                        function () {
                                            var arr = parent.value();
                                            arr.push(response.json.result);
                                            parent.value(arr);
                                        },
                                        {
                                            baseRef: params.baseRef,
                                            rootAttributeName: params.rootAttributeName,
                                            parentRuntime: params.parentRuntime,
                                            virtualParent: params.virtualParent
                                        }
                                    );
                                } else {
                                    var arr = parent.value();
                                    arr.push(response.json.result);
                                    parent.value(arr);
                                }
                            }
                            record.inSubmitProcess(false);
                        }
                    },
                    failureCallback: {
                        fn: function (response) {
                            Alfresco.util.PopupManager.displayMessage({
                                text: Alfresco.util.message("message.request-error")
                            });
                            record.inSubmitProcess(false);
                        }
                    }
                });
            };

            if (requests.requests.length > 0) {
                Alfresco.util.Ajax.jsonPost({
                    url: Alfresco.constants.PROXY_URI + "citeck/attributes/values",
                    dataObj: requests,
                    requestContentType: Alfresco.util.Ajax.JSON,
                    successCallback: {
                        fn: function (response) {
                            var pulledAttributes = response.json.attributes;
                            for (var i = 0; i < pulledAttributes.length; i++) {
                                var pulledAttribute = pulledAttributes[i];
                                if (pulledAttribute && pulledAttribute.persisted) {
                                    if (typeof pulledAttribute.value === 'object') {
                                        if (Array.isArray(pulledAttribute.value)) {
                                            if (pulledAttribute.value.length === 1) {
                                                data.attributes[pulledAttribute.attribute] = pulledAttribute.value[0].nodeRef;
                                            } else {
                                                var pulledAttributeValue = [];
                                                for (var ii = 0; ii < pulledAttribute.value.length; ii++) {
                                                    pulledAttributeValue.push(pulledAttribute.value[ii].nodeRef);
                                                }
                                                data.attributes[pulledAttribute.attribute] = pulledAttributeValue;
                                            }
                                        } else if (pulledAttribute.value.shortQName) {
                                            data.attributes[pulledAttribute.attribute] = pulledAttribute.value.shortQName;
                                        } else {
                                            data.attributes[pulledAttribute.attribute] = pulledAttribute.nodeRef;
                                        }
                                    } else {
                                        data.attributes[pulledAttribute.attribute] = pulledAttribute.value;
                                    }
                                }
                            }
                            addVariant();
                            record.inSubmitProcess(false);
                        }
                    }
                });
            } else {
                addVariant();
                record.inSubmitProcess(false);
            }
        }
    };

    Citeck.forms.formContent = function(itemId, formId, callback, params) {
        var itemKind, mode, paramName;
        formId = formId || "";

        if (Citeck.utils.isNodeRef(itemId)) {
            paramName = 'nodeRef';
            itemKind = 'node';
            mode = 'edit';
        } else {
            paramName = 'type';
            itemKind = 'type';
            mode = 'create';
        }

        params = params || {};
        var msg = Alfresco.util.message,
            id = Alfresco.util.generateDomId(),
            viewId = id + "-body",
            header = params.title || msg("actions.document.dialog-form"),
            destination = params.destination || "",
            destinationAssoc = params.destinationAssoc || "";

        var responseCallback, submitCallback, cancelCallback;
        if (callback) {
            if (callback.response) responseCallback = callback.response;
            if (callback.submit) submitCallback = callback.submit;
            if (callback.cancel) cancelCallback = callback.cancel;
        }

        var newFormContent = function() {
            var dataObj = {
                htmlid: viewId,
                mode: mode,
                viewId: formId
            };

            dataObj[paramName] = itemId;
            for(var name in params) {
                if (params[name] != null)
                    dataObj['param_' + name] = params[name];
            }

            Alfresco.util.Ajax.request({
                method: "GET",
                url: Alfresco.constants.URL_SERVICECONTEXT + "citeck/components/node-view",
                dataObj: dataObj,
                execScripts: true,
                successCallback: {
                    fn: function(response) {
                        YAHOO.Bubbling.on("node-view-submit", function(layer, args) {
                            var runtime = args[1].runtime;
                            if (runtime.key() != viewId) return;

                            var node = args[1].node;
                            node.thisclass.save(node, function(persistedNode) {
                                if (submitCallback instanceof Function)
                                    submitCallback(persistedNode);

                                YAHOO.Bubbling.fire("object-was-created", {
                                    fieldId: node.name || params.fieldId,
                                    value: persistedNode
                                });

                                runtime.node().impl().reset(true);
                            });
                        });

                        YAHOO.Bubbling.on("node-view-cancel", function(layer, args) {
                            var runtime = args[1].runtime;
                            if (runtime.key() != viewId) return;

                            if (cancelCallback instanceof Function)
                                cancelCallback();

                            runtime.node().impl().reset();
                        });

                        if (responseCallback instanceof Function)
                            responseCallback(response.serverResponse.responseText);
                    }
                }
            });
        };

        var checkUrl = YAHOO.lang.substitute(Alfresco.constants.PROXY_URI + "citeck/invariants/view-check?{paramName}={itemId}&viewId={formId}&mode={mode}", {
            paramName: paramName,
            itemId: itemId,
            mode: mode,
            formId: formId
        });

        Alfresco.util.Ajax.jsonGet({
            url: checkUrl,
            successCallback: { fn: function(response) {
                if (response.json.exists) {
                    newFormContent();
                } else if (response.json.defaultExists) {
                    formId = "";
                    newFormContent();
                }
            }}
        });
    };

    Citeck.forms.loaderPanel = function() {
        if (!this._loaderPanel) {
            this._loaderPanel = new YAHOO.widget.Panel(Alfresco.util.generateDomId(), {
                visible: false,
                close: false,
                modal: true
            });

            this._loaderPanel.setHeader(Alfresco.util.message("message.loading.form"));
            this._loaderPanel.setBody(
                '<div class="loader-panel">' +
                    '<div class="loading-indicator"></div>' +
                    '<div class="loader-panel__message">' + Alfresco.util.message("message.loading.form") + '</div>' +
                '</div>'
            );

            this._loaderPanel.render(document.body);
        }

        this._loaderPanel.show();
        this._loaderPanel.center();

        return this._loaderPanel;
    };

    Citeck.forms.editRecord = function (config) {

        var recordRef = config.recordRef,
            fallback = config.fallback,
            forceNewForm = config.forceNewForm,
            formKey = config.formKey,
            formId = config.formId;

        var showForm = function(recordRef) {

            if (recordRef) {

                var params = {
                    attributes: config.attributes || {},
                    onSubmit: config.onSubmit,
                    options: config.options
                };
                if (formId) {
                    params.formId = config.formId;
                } else if (formKey) {
                    params.formKey = config.formKey
                }

                Citeck.forms.eform(recordRef, {
                    params: params,
                    class: 'ecos-modal_width-lg',
                    isBigHeader: true,
                    formContainer: config.formContainer || null
                });
            } else {
                fallback();
            }
        };

        var isFormsEnabled;
        if (!forceNewForm) {
            isFormsEnabled = Citeck.Records.get('ecos-config@ecos-forms-enable').load('.bool');
        } else {
            isFormsEnabled = Promise.resolve(true);
        }
        var isShouldDisplayFormsForUser = checkFunctionalAvailabilityHelper
            .checkFunctionalAvailabilityForUser("default-ui-new-forms-access-groups");

        Promise.all([isFormsEnabled, isShouldDisplayFormsForUser]).then(function (values) {
            if (values.includes(true)) {
                if (formId) {
                    showForm(recordRef);
                } else {
                    require(['ecosui!ecos-form-utils'], function(utils) {
                        utils.default.hasForm(recordRef, formKey).then(function (result) {
                            if (result) {
                                showForm(recordRef);
                            } else {
                                showForm(null);
                            }
                        });
                    });
                }
            } else {
                showForm(null);
            }
        }).catch(function (e) {
            console.error(e);
            showForm(null);
        });
    };

    Citeck.forms.parseCreateArguments = function (createArgs) {
        if (!createArgs) {
            return {};
        }
        var params = {};
        try {
            var args = createArgs.split("&");
            for (var i = 0; i < args.length; i++) {
                var keyValue = (args[i] || '').split("=");
                if (keyValue.length == 2) {
                    var key = keyValue[0] || '';
                    var value = keyValue[1] || '';
                    if (key.indexOf("param_") === 0) {
                        params[key.substring("param_".length)] = value;
                    }
                }
            }
        } catch (e) {
            //protection for hotfix
            //todo: remove it in develop
            console.error(e);
        }
        return params;
    };

    Citeck.forms.handleHeaderCreateVariant = function (variant) {

        var params = Citeck.forms.parseCreateArguments(variant.createArguments);
        var attributes = variant.attributes || {};

        Citeck.forms.createRecord(variant.recordRef, variant.type, variant.destination, function() {

            var createArguments = "type=" + variant.type +
                "&viewId=" + variant.formId +
                "&destination=" + variant.destination;

            if (variant.createArguments) {
                createArguments += "&" + variant.createArguments;
            }

            window.location = "/share/page/node-create?" + createArguments;
        }, null, null, attributes, { params: params });
    };

    Citeck.forms.createRecord = function (recordRef,
                                          type,
                                          destination,
                                          fallback,
                                          redirectionMethod,
                                          formKey,
                                          attributes,
                                          options) {

        var createAttributes = attributes || {};
        if (destination) {
            createAttributes["_parent"] = destination;
        }

        var config = {
            recordRef: recordRef || 'dict@' + type,
            attributes: createAttributes,
            options: options,
            forceNewForm: formKey || !type,
            fallback: fallback,
            onSubmit: function (record, form) {

                if (record.id && record.id.indexOf('workspace://SpacesStore/') === 0
                    && form.options.formMode === 'CREATE') {

                    if (!redirectionMethod || redirectionMethod === 'card') {
                        window.location = Alfresco.util.siteURL("card-details?nodeRef=" + record.id);
                    }
                }
            }
        }

        if (formKey && formKey.indexOf("uiserv/form@") !== -1) {
            config.formId = formKey;
        } else {
            config.formKey = formKey;
        }

        Citeck.forms.editRecord(config);
    };

    var confirmIdx = 0;
    Citeck.forms.confirm = function (text, okCallback, cancelCallback) {
        var confirmId = 'ecos-confirm-' + confirmIdx++;
        var contentId = confirmId + '-content';
        var submitBtnId = contentId + '-submit';
        var cancelBtnId = contentId + '-cancel';

        require(['ecosui!ecos-modal', 'ecosui!ecos-form'], function (Modal) {
            var modal = new Modal.default();

            modal.open(
                '<h3 class="ecos-caption ecos-caption_middle">' + text + '</h3>' +
                '<div class="text-center" style="margin-top: 15px">' +
                '<button id="'+cancelBtnId+'" disabled class="ecos-btn ecos-btn_x-step_15" type="button">' + Alfresco.util.message('actions.button.cancel') + '</button>' +
                '<button id="'+submitBtnId+'" disabled class="ecos-btn ecos-btn_blue" type="button">' + Alfresco.util.message('actions.button.ok') + '</button>' +
                '</div>',
                {
                    rawHtml: true,
                    reactstrapProps: {
                        onExit: function() {typeof cancelCallback === 'function' && cancelCallback();}
                    }
                },
                function() {
                    var submitBtn = document.getElementById(submitBtnId);

                    var onSubmit = function() {
                        typeof okCallback === 'function' && okCallback();
                        if (typeof cancelCallback === 'function') {
                            cancelCallback = function() {};
                        }
                        modal.close(function() {
                            submitBtn.removeEventListener('click', onSubmit);
                        });
                    };

                    var cancelBtn = document.getElementById(cancelBtnId);

                    var onCancel = function() {
                        modal.close(function() {
                            cancelBtn.removeEventListener('click', onCancel);
                        });
                    };

                    submitBtn.disabled = false;
                    submitBtn.addEventListener('click', onSubmit);

                    cancelBtn.disabled = false;
                    cancelBtn.addEventListener('click', onCancel);
                }
            );
        });
    };

    Citeck.forms.eform = function (record, config) {

        if (!config) {
            config = {};
        }
        if (!config.reactstrapProps) {
            config.reactstrapProps = {};
        }
        if (!config.reactstrapProps.backdrop) {
            config.reactstrapProps.backdrop = 'static';
        }
        if (!config.reactstrapProps.keyboard) {
            config.reactstrapProps.keyboard = false;
        }

        require(['ecosui!react', 'ecosui!react-dom', 'ecosui!ecos-form', 'ecosui!ecos-modal'], function (React, ReactDOM, EcosForm, Modal) {

            var modal = null;
            if (!config.formContainer) {
                modal = new Modal.default();
            }

            var formParams = Object.assign({
                record: record
            }, config.params || {});

            var configParams = config.params || {};

            formParams['options'] = configParams.options || {};

            formParams['onSubmit'] = function (record, form) {
                if (modal) {
                    modal.close();
                }
                if (configParams.onSubmit) {
                    configParams.onSubmit(record, form);
                }
            };
            formParams['onFormCancel'] = function (record, form) {
                if (modal) {
                    modal.close();
                }
                if (configParams.onFormCancel) {
                    configParams.onFormCancel(record, form);
                }
            };
            formParams['onReady'] = function () {
                setTimeout(function (record, form) {
                    if (configParams.onReady) {
                        configParams.onReady(record, form);
                    }
                }, 100);
            };

            Citeck.Records.get(record).load({
                'displayName': '.disp',
                'formMode': '_formMode'
            }).then(function(recordData) {

                var displayName = recordData.displayName || '';
                var formMode = recordData.formMode || 'EDIT';

                if (formMode === 'CREATE') {
                    Citeck.Records.get(record).reset();
                }

                var options = formParams.options || {};
                options.formMode = formMode;
                formParams.options = options;

                var prefixId;

                prefixId = 'eform.header.' + formMode + ".title";
                var prefix = Alfresco.util.message(prefixId);

                if (!prefix || prefix === prefixId) {
                    config.header = displayName;
                } else {
                    config.header = prefix + " " + displayName;
                }

                var formInstance = React.createElement(EcosForm.default, formParams);

                if (config.formContainer) {
                    var container = config.formContainer;
                    if (typeof config.formContainer == "string") {
                        container = document.getElementById(config.formContainer)
                    }
                    ReactDOM.render(formInstance, container);
                } else {
                    modal.open(formInstance, config);
                }
            });
        });
    };

    Citeck.forms.dialog = function (itemId, formId, callback, params) {
        var itemKind, mode, paramName;
        formId = formId || "";
        params = params || {};

        var withoutSaving = params.withoutSaving;

        if (Citeck.utils.isNodeRef(itemId)) {
            paramName = 'nodeRef';
            itemKind = 'node';
            if (params.mode === 'view') {
                mode = 'view';
            } else {
                mode = 'edit';
            }
        } else if (Citeck.utils.isShortQName(itemId)) {
            if (withoutSaving == true) {
                paramName = 'withoutSavingType';
                itemKind = 'withoutSavingType';
            } else {
                paramName = 'type';
                itemKind = 'type';
            }
            mode = 'create';
        } else {
            paramName = 'groupAction';
            itemKind = 'groupAction';
            mode = 'create';
        }

        var msg = Alfresco.util.message,
            id = Alfresco.util.generateDomId(),
            viewId = id + "-body",
            header = params.title || msg("actions.document.dialog-form"),
            destination = params.destination || "",
            destinationAssoc = params.destinationAssoc || "",
            forceOldDialog = params.forceOldDialog || false,
            width = params.width || "500px",
            height = params.height || "auto";

        var newDialog = function() {
            var dataObj = { htmlid: viewId, mode: mode, viewId: formId };
            dataObj[paramName] = itemId;

            for(var name in params) {
                if (params[name] != null) dataObj['param_' + name] = params[name];
            }

            Alfresco.util.Ajax.request({
                method: "GET",
                url: Alfresco.constants.URL_SERVICECONTEXT + "citeck/components/node-view",
                dataObj: dataObj,
                execScripts: true,
                successCallback: {
                    fn: function(response) {
                        var panel = new YAHOO.widget.Panel(id, {
                            width: width,
                            height: height,
                            fixedcenter:  "contained",
                            constraintoviewport: true,
                            visible: false,
                            close: false, // Because not access to runtime
                            modal: true,
                            postmethod: "none", // Will make Dialogs not auto submit <form>s it finds in the dialog
                            hideaftersubmit: false, // Will stop Dialogs from hiding themselves on submits
                            fireHideShowEvents: true
                        });

                        // hide dialog on click 'esc' button
                        panel.cfg.queueProperty("keylisteners", new YAHOO.util.KeyListener(document, { keys: 27 }, {
                            fn: panel.hide,
                            scope: panel,
                            correctScope: true
                        }));

                        panel.setHeader(header);
                        panel.setBody(response.serverResponse.responseText);
                        panel.render(document.body);

                        var clientHeight = panel.element.clientHeight;

                        var onSubmit = function(layer, args) {
                            var runtime = args[1].runtime;
                            if (runtime.key() != viewId) return;

                            var node = args[1].node;

                            // save node
                            node.thisclass.save(node, function(persistedNode) {
                                _.isFunction(callback) ? callback(persistedNode) : callback.fn.call(callback.scope, persistedNode);
                                clientHeight = panel.element.clientHeight;
                                panel.hide();
                                runtime.terminate();
                            });
                        };

                        var onCancel = function(layer, args) {
                            var runtime = args[1].runtime;
                            if (runtime.key() != viewId) return;
                            clientHeight = panel.element.clientHeight;
                            panel.hide();
                            runtime.terminate();
                        };

                        YAHOO.Bubbling.on("node-view-submit", onSubmit);
                        YAHOO.Bubbling.on("node-view-cancel", onCancel);

                        panel.subscribe("hide", function() {
                            // unsubscribe
                            YAHOO.Bubbling.unsubscribe("node-view-submit", onSubmit);
                            YAHOO.Bubbling.unsubscribe("node-view-cancel", onCancel);

                            // destory panel
                            _.defer(_.bind(panel.destroy, panel));

                            //scroll to parent element
                            if (clientHeight > document.documentElement.clientHeight) {
                                $("html, body").animate({ scrollTop: panel.element.offsetTop - document.documentElement.clientHeight * 0.2 }, 600);
                            }

                            // clear DOM
                            $("[id^='" + id + "']").remove();
                        });

                        // max-height
                        if (height != "auto") {
                            var maxHeight = screen.height - 200;
                            $(panel.body)
                                .css("max-height", maxHeight - 33 + "px").addClass("fixed-size")
                                .parent().css("max-height", maxHeight + "px");

                            $(".ecos-form > .form-fields", panel.body)
                                .css("max-height", maxHeight - 70 + "px");
                        }

                        var loaderPanel = Citeck.forms.loaderPanel();
                        var component = Alfresco.util.ComponentManager.get(viewId+'-form');

                        if (component) {
                            component.runtime.loaded.subscribe(function(){
                                var timerId = setTimeout(function(){
                                    loaderPanel.hide();
                                    panel.show();
                                    panel.center();
                                    clearTimeout(timerId);
                                }, 100);
                            });
                        } else {
                            loaderPanel.hide();
                            panel.show();
                            panel.center();
                        }
                    }
                }
            });
        };

        var oldDialog = function() {
            var templateUrl = YAHOO.lang.substitute(Alfresco.constants.URL_SERVICECONTEXT + "components/form?itemKind={itemKind}&itemId={itemId}&mode={mode}&submitType={submitType}&formId={formId}&showCancelButton=true&destination={destination}", {
                itemKind: itemKind,
                itemId: itemId,
                mode: mode,
                submitType: "json",
                formId: formId,
                destination: destination
            });

            var editDetails = new Alfresco.module.SimpleDialog(id);
            editDetails.setOptions({
                    width: width,
                    height: height,
                    templateUrl: templateUrl,
                    actionUrl: null,
                    destroyOnHide: true,
                    doBeforeDialogShow: {
                        fn: Citeck.utils.fnBeforeDialogShow({ header: header }),
                    },
                    onSuccess: {
                        fn: function(response) {
                            var result = response.json.persistedObject;
                            _.isFunction(callback) ? callback(result) : callback.fn.call(callback.scope, result);
                        }
                    },
                    onFailure: {
                        fn: function(response) {
                            Alfresco.util.PopupManager.displayMessage({ text: this.msg("message.failure") });
                        },
                        scope: this
                    }
                }
            );

            editDetails.show();
        };

        if (forceOldDialog) {
            oldDialog();
            return;
        }

        var showOldForms = function () {

            var checkUrl = YAHOO.lang.substitute(Alfresco.constants.PROXY_URI + "citeck/invariants/view-check?{paramName}={itemId}&viewId={formId}&mode={mode}", {
                paramName: paramName,
                itemId: itemId,
                mode: mode,
                formId: formId
            });

            Alfresco.util.Ajax.jsonGet({
                url: checkUrl,
                successCallback: {
                    fn: function(response) {
                        if (response.json.exists) {
                            newDialog();
                        } else if (response.json.defaultExists) {
                            formId = "";
                            newDialog();
                        } else {
                            oldDialog();
                        }
                    }},
                failureCallback: {
                    fn: function(response) {
                        oldDialog();
                    }
                }
            });
        };

        var editConfig = {
            recordRef: itemId,
            fallback: function() {
                showOldForms();
            }
        };

        if (params.formKey) {
            editConfig.formKey = params.formKey;
        }

        try {
            Citeck.forms.editRecord(editConfig);
        } catch (e) {
            console.error(e);
            showOldForms();
        }
    };

    Citeck.forms.showViewInplaced = function(itemId, formId, callback, params) {
        var listId = params.listId;
        var itemKind, mode, paramName;
        formId = formId || "";

        if (!Citeck.utils.isNodeRef(itemId) || params.mode === 'create') {
            paramName = 'type';
            itemKind = 'type';
            mode = 'create';
        } else {
            paramName = 'nodeRef';
            itemKind = 'node';
            if (params.mode === 'edit') {
                mode = 'edit';
            } else {
                mode = 'view';
            }
        }

        params = params || {};
        var self = this,
            msg = Alfresco.util.message,
            containerId = Alfresco.util.generateDomId(),
            header = params.title || msg("actions.document.dialog-form"),
            destination = params.destination || "",
            destinationAssoc = params.destinationAssoc || "",
            width = params.width || "500px",
            height = params.height || "auto"
        ;
        // create container
        var container = document.createElement('div');
        container.id = '' + containerId;

        var newDialog = function() {
            var dataObj = { htmlid: containerId, mode: mode, viewId: formId };
            dataObj[paramName] = itemId;
            if (mode === 'create') dataObj["destination"] = destination;

            for(var name in params) {
                if (params[name] != null) dataObj['param_' + name] = params[name];
            }

            Alfresco.util.Ajax.request({
                method: "GET",
                url: Alfresco.constants.URL_SERVICECONTEXT + "citeck/components/node-view",
                dataObj: dataObj,
                execScripts: true,
                successCallback: {
                    fn: function(response) {
                        $('.orgstruct-console .selected-item-details .toolbar').hide();
                        $('.orgstruct-console .selected-item-details .dynamic-tree-list>.ygtvitem').hide();

                        YAHOO.Bubbling.fire("metadataRefresh");
                        // get right section - block were we will show the views
                        var showArea = $('#' + listId);

                        // remove height limitation
                        showArea.parent().css('height', 'initial');

                        // save form params
                        $(container).attr('data-itemId', itemId);
                        $(container).attr('data-formId', formId);
                        $(container).attr('data-listId', listId);

                        // add unique class name that will be used later to clear right section
                        $(container).addClass('user-profile-view-inner-container');

                        // add server response (user form) into the container
                        container.innerHTML = response.serverResponse.responseText;

                        YAHOO.Bubbling.fire("showViewInplacedDone");

                        // define submit handler
                        var onSubmit = function(layer, args) {
                            var runtime = args[1].runtime;
                            if (runtime.key() != self.containerId) {
                                return;
                            };
                            self.containerId = null;

                            var node = args[1].node;

                            // save node
                            node.thisclass.save(node, function(persistedNode) {
                                _.isFunction(callback) ? callback(persistedNode) : callback.fn.call(callback.scope, persistedNode);
                                persistedNode.impl().reset(true);
                                runtime.terminate();
                            });

                            var container = $('.user-profile-view-inner-container');
                            var itemId = $(container).attr('data-itemId');
                            var formId = $(container).attr('data-formId');
                            var listId = $(container).attr('data-listId');

                            // clear junk created by onViewItem
                            YAHOO.Bubbling.fire("clearViewJunk");

                            // go to view mode
                            if (mode != 'create') {
                                var viewItem = new Citeck.forms.showViewInplaced(itemId, formId, function () {}, {listId: listId, mode: 'view'});
                            }

                            YAHOO.Bubbling.fire("metadataRefresh");
                        };

                        // define cancel handler
                        var onCancel = function(layer, args) {
                            var runtime = args[1].runtime;
                            if (runtime.key() != self.containerId) {
                                return;
                            };
                            self.containerId = null;
                            runtime.terminate();

                            // get saved form params
                            var container = $('.user-profile-view-inner-container');
                            var itemId = $(container).attr('data-itemId');
                            var formId = $(container).attr('data-formId');
                            var listId = $(container).attr('data-listId');

                            // clear junk created by onViewItem
                            YAHOO.Bubbling.fire("clearViewJunk");

                            // open form in view mode
                            if (mode != 'create') {
                                var viewItem = new Citeck.forms.showViewInplaced(itemId, formId, function () {}, {listId: listId, mode: 'view'});
                            }
                        };

                        YAHOO.Bubbling.on("node-view-submit", onSubmit.bind(self));
                        YAHOO.Bubbling.on("node-view-cancel", onCancel.bind(self));
                        // ------------------------------------------------------------------------------

                        // append the container into the right section
                        $(container).appendTo(showArea);
                    }
                }
            });
        };

        if (mode != 'create') {
            var checkUrl = YAHOO.lang.substitute(Alfresco.constants.PROXY_URI + "citeck/invariants/view-check?{paramName}={itemId}&viewId={formId}&mode={mode}", {
                paramName: paramName,
                itemId: itemId,
                mode: mode,
                formId: formId
            });

            Alfresco.util.Ajax.jsonGet({
                url: checkUrl,
                successCallback: { fn: function(response) {
                    if (response.json.exists) {
                        newDialog();
                    } else {
                        formId = "";
                        newDialog();
                    }
                }},
                failureCallback: { fn: function(response) {
                    console.log('Error when call Citeck.forms.showViewInplaced [2]:', response);
                }}
            });
        } else {
            newDialog();
        };

        self.containerId = containerId;
    };


    /**
     * Authority name validation handler, tests that the given field is a valid
     * authority name.
     *
     * @method authorityName
     * @param field
     *            {object} The element representing the field the validation is
     *            for
     * @param args
     *            {object} Not used
     * @param event
     *            {object} The event that caused this handler to be called,
     *            maybe null
     * @param form
     *            {object} The forms runtime class instance the field is being
     *            managed by
     * @param silent
     *            {boolean} Determines whether the user should be informed upon
     *            failure
     * @param message
     *            {string} Message to display when validation fails, maybe null
     * @static
     */
    Alfresco.forms.validation.authorityName = function mandatory(field, args,
                                                                 event, form, silent, message) {
        if (Alfresco.logger.isDebugEnabled())
            Alfresco.logger.debug("Validating authority name of field '"
                + field.id + "'");

        var valid = field.value.match(/^\w+$/);

        if (!valid && !silent && form) {
            // if the keyCode from the event is the TAB or SHIFT keys don't show
            // the error
            if (event && event.keyCode != 9 && event.keyCode != 16 || !event) {
                var msg = (message != null) ? message
                    : "is invalid name for group.";
                form.addError(form.getFieldLabel(field.id) + " " + msg, field);
            }
        }

        return valid;
    };

    /**
     * Conditional Mandatory validator. The field is mandatory only if specified
     * condition is true.
     *
     * @method mandatoryIf
     * @param args -
     *            args.condition is string condition to be evaluated
     */
    Alfresco.forms.validation.mandatoryIf = function mandatoryConditionalValidator(
        field, args, event, form, silent, message) {
        // if condition is not met, then it is valid
        var data = form.getFormData();
        with (data) {
            try {
                if (eval(args.condition) == false) {
                    return true;
                }
            } catch(e) {
                // if exception is caught, it is considered as false
                return true;
            }
        }

        // otherwise consult mandatory
        return Alfresco.forms.validation.mandatory(field, args, event, form,
            silent, message);
    };

    Alfresco.forms.validation.checkInnKpp = function checkinnkpp(field, args, event, form, silent, message) {
        var formData = form.getFormData();
        var checkError = true;

        window.contractorVar = window.contractorVar || {};

        if (window.contractorVar.inn == formData.prop_dms_INN && window.contractorVar.kpp == formData.prop_dms_KPP) {
            return window.contractorVar.checkError;
        }
        window.contractorVar.inn= formData.prop_dms_INN;
        window.contractorVar.kpp = formData.prop_dms_KPP;

        if (formData.prop_dms_INN.length > 0) {
            var request = new XMLHttpRequest();
            var itemFieldId = field.id.replace(field.name, "prop_sys_node-uuid");
            var itemId= jQuery('#'+itemFieldId).val();
            request.open("GET", Alfresco.constants.PROXY_URI + 'sample/validinnkpp/search?inn=' + formData.prop_dms_INN + '&kpp=' + formData.prop_dms_KPP + '&itemId='+itemId, false);
            request.onload = function (response) {
                var result = response.target.response;
                if (result != "0") {
                    checkError = false;
                }
                else
                    checkError = true;
            };
            request.send(null);
        }
        window.contractorVar.checkError = checkError;
        return checkError;
    };

    Alfresco.forms.validation.checkunique = function checkunique(field, args, event, form, silent, message) {
        var paramsStr = location.search
        paramsStr = paramsStr.substring(1, paramsStr.length);
        var params = paramsStr.split('&')
        var sysuuid;
        for (var i = 0; i < params.length; i++) {
            var param = params[i]
            if (param.split('nodeRef=').length > 1) {
                param = decodeURIComponent(param)
                var array = param.split("/");
                sysuuid = array[array.length-1]
                break;
            }
        }

        var formData = form.getFormData();
        var checkError = true;
        var value = field.value
        if (value == null || value == '') {
            return true;
        }


        var request = new XMLHttpRequest();

        if (!sysuuid) {
            var f = Dom.get(form.formId);
            var url = f.attributes.action.nodeValue;
            var re =  /^.*api[/]\w+[/](\w+)[/](\w+)[/](.+)[/]formprocessor.*$/;
            sysuuid = url.match(re) ? url.replace(re, '$3') : null;
        }

        request.open("GET", Alfresco.constants.PROXY_URI + 'sample/valid-constraint/search?field=' + field.name.replace('prop_', '') + '&value=' + encodeURIComponent(value) + '&uuid=' + sysuuid, false);
        request.onload = function (response) {
            var result = response.target.responseText;
            if (result != "0") {
                checkError = false;
            }
            else
                checkError = true;
        };
        request.send(null);

        return checkError;
    };

    /**
     * Conditional Mandatory validator. The field is mandatory only if specified
     * condition is true.
     *
     * @method mandatoryIf
     * @param args -
     *            args.condition is string condition to be evaluated
     */
    Alfresco.forms.validation.booleanValue = function booleanValueValidator(
        field, args, event, form, silent, message) {
        return field.value + "" == args.value + "";
    };

    Alfresco.forms.validation.passportConsent = function passportConsentValidator(
        field, args, event, form, silent, message) {
        return field.value + "" == "true";
    };

//  This function adds event handler on form submit process.
//  It checks user confirmation before form submitting.
//
//  @param formId - form identifier
//  @param messages - object, which contains key/value of messages
//      which depends on result of outcome of the property shown in form.
//      key looks like: <prop_wfcf_someProp>|<value> ; if key is not mandatory, it is empty "".
//      value is a shown message for specified key
    Citeck.forms.promptBeforeSubmit = function(formId, messages) {
        YAHOO.Bubbling.on("beforeFormRuntimeInit", function (layer, args) {
            if (Alfresco.util.hasEventInterest(formId, args)) {
                var form = Citeck.forms.getForm(formId);
                if (!form)
                    return;
                var formUI = Alfresco.util.ComponentManager.get(form.id);
                if (!formUI)
                    return;
                var formsRuntime = formUI.formsRuntime;
                if (!formsRuntime)
                    return;
                var submitInvoked = formsRuntime._submitInvoked;
                if (!submitInvoked)
                    return;

                var customSubmit = function(event) {
                    var formData = formsRuntime.getFormData();
                    var message = null;
                    for (var key in messages) {
                        if (messages.hasOwnProperty(key) === true) {
                            if (key === "") {
                                message = messages[key];
                                break;
                            }
                            else {
                                var pos = key.indexOf('|'),
                                    k = key,
                                    v = "";
                                if (pos >= 0 && pos < key.length) {
                                    var k = key.substr(0, pos),
                                        v = key.substr(pos + 1);
                                }
                                if (formData[k] == v) {
                                    message = messages[key];
                                    break;
                                }
                            }
                        }
                    }
                    if (message) {
                        Alfresco.util.PopupManager.displayPrompt({
                            title: Alfresco.util.message("prompt.header.warning"),
                            text: message,
                            noEscape: true,
                            buttons: [
                                {
                                    text: Alfresco.util.message("button.ok"),
                                    handler: function dlA_onActionOk()
                                    {
                                        submitInvoked.call(formsRuntime, event);
                                        this.destroy();
                                    }
                                },
                                {
                                    text: Alfresco.util.message("button.cancel"),
                                    handler: function dlA_onActionCancel()
                                    {
                                        YAHOO.Bubbling.fire("metadataRefresh");
                                        this.destroy();
                                    },
                                    isDefault: true
                                }]
                        });
                    }
                };
                formsRuntime._submitInvoked = customSubmit;
            }
        });
    };

});
