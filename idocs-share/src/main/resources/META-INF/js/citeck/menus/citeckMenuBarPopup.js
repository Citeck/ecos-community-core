/**
 * @module alfresco/menus/AlfMenuBarPopup
 * @extends external:dijit/PopupMenuBarItem
 * @mixes module:alfresco/core/Core
 * @mixes module:alfresco/core/CoreWidgetProcessing
 * @mixes module:alfresco/core/CoreRwd
 * @mixes module:alfresco/menus/_AlfPopupCloseMixin
 * @author Dave Draper
 */
define(["dojo/_base/declare",
        "dijit/PopupMenuBarItem",
        "alfresco/core/Core",
        "alfresco/core/CoreWidgetProcessing",
        "alfresco/core/CoreRwd",
        "alfresco/menus/_AlfPopupCloseMixin",
        "dojo/dom-construct",
        "dojo/dom-class",
        "alfresco/menus/AlfMenuGroups"],
    function(declare, PopupMenuBarItem, AlfCore, CoreWidgetProcessing, AlfCoreRwd, _AlfPopupCloseMixin, domConstruct, domClass) {

        return declare([PopupMenuBarItem, AlfCore, CoreWidgetProcessing, AlfCoreRwd, _AlfPopupCloseMixin], {

            /**
             * The scope to use for i18n messages.
             *
             * @instance
             * @type {string}
             * @default
             */
            i18nScope: "org.alfresco.Menus",

            /**
             * An array of the CSS files to use with this widget.
             *
             * @instance
             * @type {object[]}
             * @default [{cssFile:"./css/AlfMenuBarPopup.css"}]
             */
            cssRequirements: [{cssFile:"./css/AlfMenuBarPopup.css"}],

            /**
             * Used to indicate whether or not to display a down arrow that indicates that this is a drop-down menu.
             * True by default.
             *
             * @instance
             * @type {boolean}
             * @default
             */
            showArrow: true,

            /**
             * @instance
             * @type {string}
             * @default
             */
            iconAltText: "",

            /**
             * If an optional iconSrc is provided, the menu icon will be rendered using that image instead of
             * the transparent one.
             *
             * @instance
             * @type {string}
             * @default
             */
            iconSrc: "",

            profileIconSrc: "",

            /**
             * This CSS class is added to the container node when an icon is to be included with the label. By
             * default it simply makes room for the icon - but this can be overridden.
             *
             * @instance
             * @type {string}
             * @default
             */
            labelWithIconClass: "alf-menu-bar-popup-label-node",

            /**
             * It's important to perform label encoding before buildRendering occurs (e.g. before postCreate)
             * to ensure that an unencoded label isn't set and then replaced.
             *
             * @instance
             */
            postMixInProperties: function alfresco_menus_AlfMenuBarPopup__postMixInProperties() {
                if (this.label) {
                    this.label = this.encodeHTML(this.message(this.label));
                }
                if (this.title) {
                    this.title = this.message(this.title);
                }
                if (!this.iconAltText && this.label) {
                    this.iconAltText = this.label;
                }
                else if (this.iconAltText) {
                    this.iconAltText = this.message(this.iconAltText);
                }
                this.inherited(arguments);
            },

            /**
             * Creates a DOM element to hold an icon for the menu.
             *
             * @instance
             * @since 1.0.59
             */
            createIconNode: function alfresco_menus_AlfMenuBarPopup__createIconNode() {
                if (this.iconClass && this.iconClass !== "dijitNoIcon")
                {
                    if (this.profileIconSrc) {
                        this.iconNode = domConstruct.create("div", {
                            className: this.iconClass + " alfresco-menus-AlfMenuBarPopup__icon alfresco-menus-AlfMenuItemIconMixin",
                            style: "float: right",
                            innerHTML: '<div style="background-image: url(' + Alfresco.constants.PROXY_URI + this.profileIconSrc + ')"/>'
                        }, this.focusNode, "last");
                    } else {
                        this.iconNode = domConstruct.create("img", {
                            className: this.iconClass + " alfresco-menus-AlfMenuBarPopup__icon alfresco-menus-AlfMenuItemIconMixin",
                            src: (this.iconSrc ? this.iconSrc : require.toUrl("alfresco/menus/css/images/transparent-20.png")),
                            title: this.message(this.iconAltText),
                            alt: this.message(this.iconAltText),
                            role: "button"
                        }, this.focusNode, "first");
                    }

                    if (this.label) {
                        domClass.add(this.containerNode, this.labelWithIconClass);
                    }
                }
            },

            /**
             * Sets the label of the menu item that represents the popup and creates a new alfresco/menus/AlfMenuGroups
             * instance containing all of the widgets to be displayed in the popup. Ideally the array of widgets should
             * be instances of alfresco/menus/AlfMenuGroup (where instance has its own list of menu items). However, this
             * widget should be able to accommodate any widget.
             *
             * @instance
             */
            postCreate: function alfresco_menus_AlfMenuBarPopup__postCreate() {
                domClass.add(this.domNode, "alfresco-menus-AlfMenuBarPopup");

                if (this.showArrow) {
                    // Add in the "arrow" image to indicate a drop-down menu. We do this with DOM manipulation
                    // rather than overriding the default template for such a minor change. This means that we
                    // have some protection against changes to the template in future Dojo releases.
                    var arrowWrapperNode = domConstruct.create("span", {
                        className: "alfresco-menus-AlfMenuBarPopup__text-wrapper"
                    }, this.focusNode, "first");
                    this.createIconNode();
                    domConstruct.place(this.textDirNode, arrowWrapperNode);
                    domConstruct.create("span", {
                        className: "alfresco-menus-AlfMenuBarPopup__arrow",
                        innerHTML: "???"
                    }, this.focusNode, "last");
                } else {
                    this.createIconNode();
                }

                this.inherited(arguments);

                // A class in the hierarchy (PopupMenuItem) is expecting a "popup" attribute that contains the
                // dropdown menu item. We are going to construct this from the widgets provided.
                this.createWidget({
                    name: "alfresco/menus/AlfMenuGroups",
                    assignTo: "popup",
                    config: {
                        widgets: this.widgets
                    }
                });

                // Call the method provided by the _AlfPopupCloseMixin to handle popup close events...
                this.registerPopupCloseEvent();
            }
        });
    });