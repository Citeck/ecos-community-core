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
define(['js/citeck/modules/utils/citeck'], function() {

    Citeck.namespace('invariants');

    Citeck.invariants.NodeViewManager = function(key) {
        this.key = key;

        this.options = {
            onsubmit: null,
            oncancel: null,
            redirect: false
        };

        this.behaviours = {
            back: this.goBack,
            card: this.goToCard,
            redirect: this.redirect
        };

        this.behavioursDraft = {
            back: this.goBack,
            card: this.goToCardDraft,
            redirect: this.redirect
        };

        this.defaultBehaviour = this.behaviours.back;

        YAHOO.Bubbling.on("node-view-submit", this.onSubmit, this);
        YAHOO.Bubbling.on("node-view-cancel", this.onCancel, this);
    };

    Citeck.invariants.NodeViewManager.prototype = {

        setOptions: function(options) {
            _.each(options, function(value, key) {
                if(value != null) {
                    this.options[key] = value;
                }
            }, this);
            return this;
        },

        onSubmit: function(layer, args) {
            if (this.key != args[1].key) return;
            var behaviours = args[1].isDraft ? this.behavioursDraft : this.behaviours;
            var node = args[1].node;
            var isDraft = node.impl().getAttribute('invariants:isDraft').value();

            node.thisclass.save(node, {
                scope: this,
                fn: function(result) {
                  var submitBehaviour = this.options.redirect ?
                                        behaviours.redirect :
                                        (behaviours[this.options.onsubmit] || this.defaultBehaviour);
                  submitBehaviour.call(this, result, isDraft);
                }
            });
        },

        onCancel: function(layer, args) {
            if (this.key != args[1].key) return;
            var node = args[1].node;
            var cancelBehaviour = this.behaviours[this.options.oncancel] || this.defaultBehaviour;
            cancelBehaviour.call(this, node);
        },

        goBack: function(node) {

            if (history.length > 1) {
                history.go(-1);
            } else if(document.referrer) {
                document.location.href = document.referrer;
            } else {
                document.location.href = Alfresco.constants.URL_CONTEXT;
            }
        },

        goToCard: function(node, isDraft) {

            if (this.isV2Page()) {
                document.location.href = "/v2/dashboard?recordRef="  + node.nodeRef;
                return;
            }

            var showStartMessage = true;
            if (!isDraft && location.pathname.indexOf('node-edit-page') !== -1) {
                showStartMessage = false;
            }

            var redirectUrl = Alfresco.constants.URL_PAGECONTEXT + "card-details?nodeRef=" + node.nodeRef;
            if (showStartMessage) {
                redirectUrl += "&showStartMsg=true";
            }

            document.location.href = redirectUrl;
        },

        goToCardDraft: function(node) {

            if (this.isV2Page()) {
                document.location.href = "/v2/dashboard?recordRef="  + node.nodeRef;
                return;
            }

            document.location.href = Alfresco.constants.URL_PAGECONTEXT + "card-details?nodeRef=" + node.nodeRef;
        },

        isV2Page: function() {
            var link = window.location.pathname || '';
            return /share\/.+-page-v2.*/.test(link);
        },

        redirect: function(node) {
            var link = _.clone(this.options.onsubmit);
            _.each(link.match(/{{\w+(:\w+|)}}/g), function(property) {
                var propertyName = property.replace(/({{|}})/g, ""),
                    propertyValue = _.contains(propertyName, ":") ? node.properties[propertyName] : node[propertyName],
                    templateRegExp = new RegExp(property, "g");
                link = link.replace(templateRegExp, propertyValue)
            });

            document.location.href = link;
        }

    };

    return Citeck.invariants.NodeViewManager;
});
