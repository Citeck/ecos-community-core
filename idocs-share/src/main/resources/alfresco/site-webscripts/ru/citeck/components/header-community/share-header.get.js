<import resource="classpath:/alfresco/site-webscripts/org/alfresco/share/imports/share-header.lib.js">
<import resource="classpath:/alfresco/site-webscripts/ru/citeck/components/header-community/share-header.lib.js">
<import resource="classpath:/alfresco/site-webscripts/ru/citeck/components/header/header-tokens.lib.js">
<import resource="classpath:/alfresco/site-webscripts/ru/citeck/components/citeck.lib.js">


// TODO:
// - fix click on open popup item

// GLOBAL VARIABLES
var isMobile = isMobileDevice(context.headers["user-agent"]);
model.isMobile = isMobile;

// ---------------------
// HEADER MENU
// ---------------------

var header = findObjectById(model.jsonModel.widgets, "SHARE_HEADER"),
    appMenuBar = findObjectById(model.jsonModel.widgets, "HEADER_APP_MENU_BAR"),
    userMenuBar = findObjectById(model.jsonModel.widgets, "HEADER_USER_MENU_BAR"),
    search = findObjectById(model.jsonModel.widgets, "HEADER_SEARCH"),
    shareVerticalLayout = findObjectById(model.jsonModel.widgets, "SHARE_VERTICAL_LAYOUT"),
    titleMenu = findObjectById(model.jsonModel.widgets, "HEADER_TITLE_MENU"),
    navigationMenuBar = findObjectById(model.jsonModel.widgets, "HEADER_NAVIGATION_MENU_BAR"),
    customizeUserDashboard = findObjectById(model.jsonModel.widgets, "HEADER_CUSTOMIZE_USER_DASHBOARD"),
    currentSite = page.url.templateArgs.site || getLastSiteFromCookie(),
    accessibleSites = getSitesForUser(user.name),
    isCascadCreateMenu = getMenuConfig("default-ui-create-menu") == "cascad",
    siteData = getSiteData(),
    myTools = [
        { id: "task-journals", url: "journals2/list/tasks", iconImage: "/share/res/components/images/header/my-tasks.png" },
        { id: "my-workflows", url: "my-workflows", iconImage: "/share/res/components/images/header/my-workflows.png" },
        { id: "completed-workflows", url: "completed-workflows#paging=%7C&filter=workflows%7Call", iconImage: "/share/res/components/images/header/completed-workflows.png" },
        { id: "my-content", url: "user/user-content", iconImage: "/share/res/components/images/header/my-content.png" },
        { id: "my-sites", url: "user/user-sites", iconImage: "/share/res/components/images/header/my-sites.png" },
        { id: "my-profile", url: "user/" + encodeURIComponent(user.name) + "/profile", iconImage: "/share/res/components/images/header/my-profile.png" },
        { id: "my-files", url: "context/mine/myfiles", iconImage: "/share/res/components/images/header/my-content.png" },
        { id: "global_journals2", url: "journals2/list/main", iconImage: "/share/res/components/images/header/journals.png" }
    ],
    adminTools = [
        { id: "repository", url: "repository", iconImage: "/share/res/components/images/header/repository.png" },
        { id: "application-menu", url: "console/admin-console/application", iconImage: "/share/res/components/images/header/application.png", label: "header.application.label" },
        { id: "flowable-modeler", url: "flowable-modeler", iconImage: "/share/res/components/images/header/application.png", label: "page.flowable-modeler.title" },
        { id: "groups", url: "console/admin-console/groups", iconImage: "/share/res/components/images/header/groups.png" },
        { id: "users", url: "console/admin-console/users", iconImage: "/share/res/components/images/header/users.png" },
        { id: "categories", url: "console/admin-console/type-manager", iconImage: "/share/res/components/images/header/category-manager.png" },
        { id: "system", url: "journals2/list/system", iconImage: "/share/res/components/images/header/journals.png" },
        { id: "meta_journals", url: "journals2/list/meta", iconImage: "/share/res/components/images/header/journals.png" },
        { id: "templates", url: "journals2/list/templates", iconImage: "/share/res/components/images/header/templates.png" },
        { id: "more", url: "console/admin-console/", iconImage: "/share/res/components/images/header/more.png" }
    ];

var defaultUIMainMenu = getMenuConfig("default-ui-main-menu");
var isSlideMenu = isShouldDisplayLeftMenuForUser(user.name, defaultUIMainMenu);

model.isReactMenu = isSlideMenu;
model.isNewReactMenu = isSlideMenu && (defaultUIMainMenu !== "left-legacy");
model.isCascadeCreateMenu = isCascadCreateMenu;

// ---------------------
// General code
// ---------------------

appMenuBar.config.id = "HEADER_APP_MENU_BAR";
userMenuBar.config.id = "HEADER_USER_MENU_BAR";
userMenuBar.config.widgets = [];

// delete the Title Bar everywhere (exept for Edit Page and Create Page)
if (shareVerticalLayout && shareVerticalLayout.config.widgets.length) {
    shareVerticalLayout.config.widgets = shareVerticalLayout.config.widgets.filter(function(item) {
        if (item.id == "HEADER_TITLE_BAR" && (page.id.indexOf("edit") != -1 || page.id.indexOf("create") != -1 || page.id.indexOf("start") != -1)) {
            item.config.widgets = item.config.widgets.filter(function(item) {
                return item.id == "HEADER_TITLE"
            });
            return item;
        } else {
            return item.id !== "HEADER_TITLE_BAR"
        }

    })
}

// dirty hack: hide the old top menu on the faceted search page
if (isSlideMenu) {
    header.name = "js/citeck/header/emptyMenu";
}


// CUSTOMIZE USER DASHBOARD

if (customizeUserDashboard) {
    customizeUserDashboard.name = "js/citeck/header/citeckMenuItem";
    customizeUserDashboard.config.label = msg.get("customize_dashboard.label");
}


// SEARCH

header.config.widgets.splice(2, 1);
header.config.widgets.splice(1, 0, search);


// HEADER SITE MENU

var siteMenuItems = [];
if (navigationMenuBar && navigationMenuBar.config.widgets.length) {
    for (var w in navigationMenuBar.config.widgets) {
        if (navigationMenuBar.config.widgets[w].id == "HEADER_SITE_MORE_PAGES" && navigationMenuBar.config.widgets[w].config.widgets.length) {
            navigationMenuBar.config.widgets = navigationMenuBar.config.widgets.concat(
                navigationMenuBar.config.widgets[w].config.widgets[0].config.widgets.map(function(widget) {
                    widget.name = "js/citeck/header/citeckMenuItem";
                    return widget;
                }));
            navigationMenuBar.config.widgets.splice(w, 1);
        }
    }
    navigationMenuBar.config.widgets = navigationMenuBar.config.widgets.filter(function(item) {
        item.name = "alfresco/header/AlfMenuItem";
        if (isSlideMenu && item.id == "HEADER_SITE_CALENDAR") {
            return false;
        }
        return user.isAdmin || item.id != "HEADER_SITE_SITE-DOCUMENT-TYPES";
    });
    siteMenuItems = siteMenuItems.concat(navigationMenuBar.config.widgets);
}

if (titleMenu && titleMenu.config.widgets.length) {
    titleMenu.config.widgets = titleMenu.config.widgets.filter(function(item) {
        if(item.id == "HEADER_SITE_INVITE") {
            item.name = "js/citeck/header/citeckMenuItem";
            item.config.label = msg.get("header.menu.invite.altText");
        }
        if(item.id != "HEADER_SITE_CONFIGURATION_DROPDOWN") {
            return item;
        }
    });
    siteMenuItems = siteMenuItems.concat(titleMenu.config.widgets);
}

if (page.url.templateArgs.site && siteData) {
    // If the user is an admin, and a site member, but NOT the site manager then
// add the menu item to let them become a site manager...
    if (user.isAdmin && siteData.userIsMember && !siteData.userIsSiteManager) {
        siteMenuItems.push({
            id: "HEADER_BECOME_SITE_MANAGER",
            name: "alfresco/menus/AlfMenuItem",
            config: {
                id: "HEADER_BECOME_SITE_MANAGER",
                label: "become_site_manager.label",
                iconClass: "alf-cog-icon",
                publishTopic: "ALF_BECOME_SITE_MANAGER",
                publishPayload: {
                    site: page.url.templateArgs.site,
                    siteTitle: siteData.profile.title,
                    user: user.name,
                    userFullName: user.fullName,
                    reloadPage: true
                }
            }
        });
    }

// If the user is a site manager then let them make custmomizations...
    if (siteData.userIsSiteManager) {

        // Add Customize Dashboard
        siteMenuItems.push({
            id: "HEADER_CUSTOMIZE_SITE_DASHBOARD",
            name: "alfresco/menus/AlfMenuItem",
            config: {
                id: "HEADER_CUSTOMIZE_SITE_DASHBOARD",
                label: "customize_dashboard.label",
                iconClass: "alf-cog-icon",
                targetUrl: "site/" + page.url.templateArgs.site + "/customise-site-dashboard"
            }
        });

        // Add the regular site manager options (edit site, customize site, leave site)
        siteMenuItems.push(
            {
                id: "HEADER_EDIT_SITE_DETAILS",
                name: "alfresco/menus/AlfMenuItem",
                config: {
                    id: "HEADER_EDIT_SITE_DETAILS",
                    label: "edit_site_details.label",
                    iconClass: "alf-edit-icon",
                    publishTopic: "ALF_EDIT_SITE",
                    publishPayload: {
                        site: page.url.templateArgs.site,
                        siteTitle: siteData.profile.title,
                        user: user.name,
                        userFullName: user.fullName
                    }
                }
            },
            {
                id: "HEADER_CUSTOMIZE_SITE",
                name: "alfresco/menus/AlfMenuItem",
                config: {
                    id: "HEADER_CUSTOMIZE_SITE",
                    label: "customize_site.label",
                    iconClass: "alf-cog-icon",
                    targetUrl: "site/" + page.url.templateArgs.site + "/customise-site"
                }
            },
            {
                id: "HEADER_LEAVE_SITE",
                name: "alfresco/menus/AlfMenuItem",
                config: {
                    id: "HEADER_LEAVE_SITE",
                    label: "leave_site.label",
                    iconClass: "alf-leave-icon",
                    publishTopic: "ALF_LEAVE_SITE",
                    publishPayload: {
                        site: page.url.templateArgs.site,
                        siteTitle: siteData.profile.title,
                        user: user.name,
                        userFullName: user.fullName
                    }
                }
            }
        );
    } else if (siteData.userIsMember) {
        // If the user is a member of a site then give them the option to leave...
        siteMenuItems.push({
            id: "HEADER_LEAVE_SITE",
            name: "alfresco/menus/AlfMenuItem",
            config: {
                id: "HEADER_LEAVE_SITE",
                label: "leave_site.label",
                iconClass: "alf-leave-icon",
                publishTopic: "ALF_LEAVE_SITE",
                publishPayload: {
                    site: page.url.templateArgs.site,
                    siteTitle: siteData.profile.title,
                    user: user.name,
                    userFullName: user.fullName
                }
            }
        });
    } else if (siteData.profile.visibility != "PRIVATE" || user.isAdmin) {
        // If the member is not a member of a site then give them the option to join...
        siteMenuItems.push({
            id: "HEADER_JOIN_SITE",
            name: "alfresco/menus/AlfMenuItem",
            config: {
                id: "HEADER_JOIN_SITE",
                label: (siteData.profile.visibility == "MODERATED" ? "join_site_moderated.label" : "join_site.label"),
                iconClass: "alf-leave-icon",
                publishTopic: (siteData.profile.visibility == "MODERATED" ? "ALF_REQUEST_SITE_MEMBERSHIP" : "ALF_JOIN_SITE"),
                publishPayload: {
                    site: page.url.templateArgs.site,
                    siteTitle: siteData.profile.title,
                    user: user.name,
                    userFullName: user.fullName
                }
            }
        });
    }
}


if (siteMenuItems.length) {
    userMenuBar.config.widgets = [{
        id: "HEADER_SITE_MENU",
        name: "alfresco/header/AlfMenuBarPopup",
        config: {
            id: "HEADER_SITE_MENU",
            label: "",
            style: isMobile ? "padding-left: 5px; padding-right: 5px" :  "padding-left: 10px;",
            widgets: siteMenuItems
        }
    }];
}

model.siteMenuItems = siteMenuItems;


// DEBUG MENU

var loggingWidgetItems;
if (config.global.flags.getChildValue("client-debug") == "true") {
    var loggingEnabled = false,
        allEnabled     = false,
        warnEnabled    = false,
        errorEnabled   = false;

    if (userPreferences &&
        userPreferences.org &&
        userPreferences.org.alfresco &&
        userPreferences.org.alfresco.share &&
        userPreferences.org.alfresco.share.logging) {

        var loggingPreferences = userPreferences.org.alfresco.share.logging;
        loggingEnabled = loggingPreferences.enabled && true;
        allEnabled = (loggingPreferences.all != null) ?  loggingPreferences.all : false;
        warnEnabled = (loggingPreferences.warn != null) ?  loggingPreferences.warn : false;
        errorEnabled = (loggingPreferences.error != null) ?  loggingPreferences.error : false;
    }

    loggingWidgetItems = [
        {
            name: "js/citeck/menus/citeckMenuGroup",
            config: {
                label: "Quick Settings",
                widgets: [
                    {
                        name: "alfresco/menus/AlfCheckableMenuItem",
                        config: {
                            label: "Debug Logging",
                            value: "enabled",
                            publishTopic: "ALF_LOGGING_STATUS_CHANGE",
                            checked: loggingEnabled
                        }
                    },
                    {
                        name: "alfresco/menus/AlfCheckableMenuItem",
                        config: {
                            label: "Show All Logs",
                            value: "all",
                            publishTopic: "ALF_LOGGING_STATUS_CHANGE",
                            checked: allEnabled
                        }
                    },
                    {
                        name: "alfresco/menus/AlfCheckableMenuItem",
                        config: {
                            label: "Show Warning Messages",
                            value: "warn",
                            publishTopic: "ALF_LOGGING_STATUS_CHANGE",
                            checked: warnEnabled
                        }
                    },
                    {
                        name: "alfresco/menus/AlfCheckableMenuItem",
                        config: {
                            label: "Show Error Messages",
                            value: "error",
                            publishTopic: "ALF_LOGGING_STATUS_CHANGE",
                            checked: errorEnabled
                        }
                    }
                ]
            }
        },
        {
            name: "js/citeck/menus/citeckMenuGroup",
            config: {
                label: "Logging Configuration",
                widgets: [
                    {
                        name: "js/citeck/menus/citeckMenuItem",
                        config: {
                            label: "Update Logging Preferences",
                            publishTopic: "ALF_UPDATE_LOGGING_PREFERENCES"
                        }
                    }
                ]
            }
        }
    ];
}

// USER MENU ITEMS

var availability = "make-" + (user.properties.available === false ? "" : "not") + "available",
    clickEvent = function (event, element) {
        Citeck.forms.dialog("deputy:selfAbsenceEvent", "", {
            scope: this,
            fn: function (node) {
                this.alfPublish("ALF_NAVIGATE_TO_PAGE", {
                    url: this.targetUrl,
                    type: this.targetUrlType,
                    target: this.targetUrlLocation
                });
            }
        }, {
            title: "",
            destination: "workspace://SpacesStore/absence-events"
        })
    },
    userMenuItems = [
        {
            id: "HEADER_USER_MENU_STATUS",
            name: "alfresco/header/CurrentUserStatus"
        },
        {
            id: "HEADER_USER_MENU_MY_PROFILE",
            name: "js/citeck/header/citeckMenuItem",
            config: {
                id: "HEADER_USER_MENU_MY_PROFILE",
                label: "header.my-profile.label",
                iconImage: "/share/res/components/images/header/my-profile.png",
                targetUrl: "user/" + encodeURIComponent(user.name) + "/profile"
            }
        },
        {
            id: "HEADER_USER_MENU_AVAILABILITY",
            name: "js/citeck/header/citeckMenuItem",
            config: {
                id: "HEADER_USER_MENU_AVAILABILITY",
                label: "header." + availability + ".label",
                iconImage: "/share/res/components/images/header/" + availability + ".png",
                targetUrl: "/components/deputy/make-available?available=" + (user.properties.available === false ? "true" : "false"),
                clickEvent: "" + (user.properties.available === false ? "" : clickEvent.toString())
            }
        }
    ];

if (user.capabilities.isMutable) {
    userMenuItems.push({
        id: "HEADER_USER_MENU_PASSWORD",
        name: "js/citeck/header/citeckMenuItem",
        config: {
            id: "HEADER_USER_MENU_PASSWORD",
            label: "header.change-password.label",
            iconImage: "/share/res/components/images/header/change-password.png",
            targetUrl: "user/" + encodeURIComponent(user.name) + "/change-password"
        }
    });
}

// Feedback Tools
userMenuItems.push(
    {
        id: "HEADER_USER_MENU_FEEDBACK",
        name: "js/citeck/menus/citeckMenuItem",
        config: {
            id: "HEADER_USER_MENU_FEEDBACK",
            label: "header.feedback.label",
            iconImage: "/share/res/components/images/header/default-error-report-16.png",
            targetUrl: "https://www.citeck.ru/feedback",
            targetUrlType: "FULL_PATH",
            targetUrlLocation: "NEW"
        }
    },
    {
        id: "HEADER_USER_MENU_REPORTISSUE",
        name: "js/citeck/menus/citeckMenuItem",
        config: {
            id: "HEADER_USER_MENU_REPORTISSUE",
            label: "header.reportIssue.label",
            iconImage: "/share/res/components/images/header/default-feedback-16.png",
            targetUrl: "mailto:support@citeck.ru?subject=Ошибка в работе Citeck ECOS: краткое описание&body=Summary: Короткое описание проблемы (продублировать в теме письма)%0A%0ADescription:%0AПожалуйста, детально опишите возникшую проблему, последовательность действий, которая привела к ней. При необходимости приложите скриншоты.",
            targetUrlType: "FULL_PATH",
            targetUrlLocation: "NEW"
        }
    });

var logoutItemConfig = {
    id: "HEADER_USER_MENU_LOGOUT",
    name: "js/citeck/header/citeckMenuItem",
    config: {
        id: "HEADER_USER_MENU_LOGOUT",
        label: "header.logout.label",
        iconImage: "/share/res/components/images/header/logout.png",
        actionType: "logout"
    }
};

if (!context.externalAuthentication) {
    // Alfresco community version doesn't have LogoutService so we should check
    if (model.jsonModel.services && model.jsonModel.services.indexOf("alfresco/services/LogoutService") > -1) {
        logoutItemConfig.config.publishTopic = "ALF_DOLOGOUT";
    } else {
        logoutItemConfig.config.targetUrl = "dologout";
    }
}

userMenuItems.push(logoutItemConfig);

var HEADER_USER_MENU = {
    id: "HEADER_USER_MENU",
    name: "js/citeck/menus/citeckMenuBarPopup",
    config: {
        id: "HEADER_USER_MENU",
        label: user.fullName,
        widgets: [{
            name: "js/citeck/menus/citeckMenuGroup",
            config: {
                widgets: userMenuItems
            }
        }]
    }
};

// APP MENU ITEMS

appMenuBar.config.widgets = [];
var createSiteClickEvent = isSlideMenu ? "Citeck.module.getCreateSiteInstance().show()"
                                       : function(event, element) {
                                            Citeck.module.getCreateSiteInstance().show();
                                         };


var HEADER_SITES_VARIANTS = {
        id: "HEADER_SITES_VARIANTS",
        name: "js/citeck/menus/citeckMenuGroup",
        config: {
            id: "HEADER_SITES_VARIANTS",
            widgets: buildSitesForUser(accessibleSites)
        }
    },
    HEADER_SITES_SEARCH = {
        id: "HEADER_SITES_SEARCH",
        name: "js/citeck/menus/citeckMenuItem",
        config: {
            id: "HEADER_SITES_SEARCH",
            label: "header.find-sites.label",
            targetUrl: "custom-site-finder"
        }
    },
    HEADER_SITES_CREATE = {
        id: "HEADER_SITES_CREATE",
        name: "js/citeck/menus/citeckMenuItem",
        config: {
            id: "HEADER_SITES_CREATE",
            label: "header.create-site.label",
            clickEvent: createSiteClickEvent.toString(),
            inheriteClickEvent: false
        }
    },
    HEADER_CREATE_VARIANTS = {
        id: "HEADER_CREATE_VARIANTS",
        name: "js/citeck/menus/citeckMenuGroup",
        config: {
            id: "HEADER_CREATE_VARIANTS",
            widgets: buildCreateVariantsForSite(currentSite)
        }
    },
    HEADER_CREATE = {
        id: "HEADER_CREATE",
        name: "alfresco/header/AlfMenuBarPopup",
        config: {
            id: "HEADER_CREATE",
            label: "header.create-variants.label",
            widgets: [ HEADER_CREATE_VARIANTS ]
        }
    },
    HEADER_JOURNALS = {
        id: "HEADER_JOURNALS",
        name: "js/citeck/menus/citeckMenuBarItem",
        config: {
            id: "HEADER_JOURNALS",
            label: "header.journals.label",
            targetUrl: buildSiteUrl(currentSite) + "journals2/list/main",
            movable: { minWidth: 1089 }
        }
    },
    HEADER_DOCUMENTLIBRARY = {
        id: "HEADER_DOCUMENTLIBRARY",
        name: "js/citeck/menus/citeckMenuBarItem",
        config: {
            id: "HEADER_DOCUMENTLIBRARY",
            label: "header.documentlibrary.label",
            targetUrl: buildSiteUrl(currentSite) + "documentlibrary",
            movable: { minWidth: 1171 }
        }
    },
    HEADER_CREATE_WORKFLOW_VARIANTS = {
        id: "HEADER_CREATE_WORKFLOW_VARIANTS",
        name: "js/citeck/menus/citeckMenuGroup",
        config: {
            id: "HEADER_CREATE_WORKFLOW_VARIANTS",
            widgets: [
                {
                    id: "HEADER_CREATE_WORKFLOW_ADHOC",
                    name: "js/citeck/menus/citeckMenuItem",
                    config: {
                        id: "HEADER_CREATE_WORKFLOW_ADHOC",
                        label: "header.create-workflow-adhoc.label",
                        targetUrl: "workflow-start-page?formType=workflowId&formKey=activiti$perform"
                    }
                },
                {
                    id: "HEADER_CREATE_WORKFLOW_CONFIRM",
                    name: "js/citeck/menus/citeckMenuItem",
                    config: {
                        id: "HEADER_CREATE_WORKFLOW_CONFIRM",
                        label: "header.create-workflow-confirm.label",
                        targetUrl: "start-specified-workflow?workflowId=activiti$confirm"
                    }
                }
            ]
        }
    },
    HEADER_CREATE_WORKFLOW = {
        id: "HEADER_CREATE_WORKFLOW",
        name: "alfresco/header/AlfMenuBarPopup",
        config: {
            id: "HEADER_CREATE_WORKFLOW",
            label: "header.create-workflow.label",
            widgets: [ HEADER_CREATE_WORKFLOW_VARIANTS ]
        }
    },
    HEADER_SITES = {
        id: "HEADER_SITES",
        name: "alfresco/header/AlfMenuBarPopup",
        config: {
            id: "HEADER_SITES",
            label: "header.sites.label",
            widgets: [
                HEADER_SITES_VARIANTS,
                {
                    id: "HEADER_SITES_MANAGEMENT",
                    name: "js/citeck/menus/citeckMenuGroup",
                    config: {
                        id: "HEADER_SITES_MANAGEMENT",
                        widgets: [
                            HEADER_SITES_SEARCH,
                            HEADER_SITES_CREATE
                        ]
                    }
                }
            ]
        }
    },
    HEADER_CREATE_CASE = {
        id: "HEADER_CREATE_CASE",
        name: "alfresco/menus/AlfMenuBarPopup",
        config: {
            id: "HEADER_CREATE_CASE",
            widgets: buildCreateVariants(accessibleSites)
        }
    },
    HEADER_LOGGING = {
        id: "HEADER_LOGGING",
        name: "alfresco/header/AlfMenuBarPopup",
        config: {
            id: "HEADER_LOGGING",
            label: "Debug Menu",
            widgets: loggingWidgetItems
        }
    };


// ---------------------
// Slide Menu
// ---------------------
if (isSlideMenu) {

    // USER MENU BAR
    if (user && user.properties && getPhoto(user.properties.nodeRef)) {
        HEADER_USER_MENU.config.iconClass = "user-photo-header";
        HEADER_USER_MENU.config.profileIconSrc = "api/node/content;ecos:photo/" + user.properties.nodeRef.replace(":/", "") + "/image.jpg"
    } else if(isMobile) {
        HEADER_USER_MENU.config.iconSrc = "/share/res/components/images/header/user-profile.png"
    }
    userMenuBar.config.widgets.push(HEADER_USER_MENU);

    // BUILD APP MENU
    var slideMenuConfig = {
        id: "HEADER_SLIDE_MENU",
        isMobile: isMobile,
        userName: user.name,
        logoSrc: getHeaderLogoUrl(),
        logoSrcMobile: getHeaderMobileLogoUrl(),
        widgets: getWidgets()
    };

    model.slideMenuConfig = slideMenuConfig;

    appMenuBar.config.widgets.push({
        id: "HEADER_SLIDE_MENU",
        name: "js/citeck/header/citeckMainSlideMenu",
        config: slideMenuConfig
    },
        HEADER_CREATE_CASE);

    if (!isMobile) {
        if (loggingWidgetItems) {
            appMenuBar.config.widgets.push(HEADER_LOGGING);
        }
    }
} else {
    // ---------------------
    // Old Menu
    // ---------------------
    if (!isMobile) {
        var morePopup = buildMorePopup(false);
        appMenuBar.config.widgets.push(HEADER_SITES, HEADER_CREATE, HEADER_JOURNALS, HEADER_DOCUMENTLIBRARY, HEADER_CREATE_WORKFLOW, morePopup);
        if (loggingWidgetItems) {
            appMenuBar.config.widgets.push(HEADER_LOGGING);
        }
    }

    // USER MENU BAR
    if(isMobile) {
        HEADER_USER_MENU.config.iconSrc = "/share/res/components/images/header/user-profile.png"
    }
    userMenuBar.config.widgets.push(HEADER_USER_MENU);

    // BUILD MOBILE MENU
    var HEADER_MOBILE_JOURNALS = toMobileWidget(HEADER_JOURNALS);
    HEADER_MOBILE_JOURNALS.name = "js/citeck/menus/citeckMenuItem";
    HEADER_MOBILE_JOURNALS.config.movable = null;

    var HEADER_MOBILE_DOCUMENTLIBRARY = toMobileWidget(HEADER_DOCUMENTLIBRARY);
    HEADER_MOBILE_DOCUMENTLIBRARY.name = "js/citeck/menus/citeckMenuItem";
    HEADER_MOBILE_DOCUMENTLIBRARY.config.movable = null;

    var HEADER_MOBILE_CREATE_WORKFLOW_VARIANTS = toMobileWidget(HEADER_CREATE_WORKFLOW_VARIANTS);
    HEADER_MOBILE_CREATE_WORKFLOW_VARIANTS.config.label = "header.create-workflow.label";

    var HEADER_MOBILE_CREATE_VARIANTS = toMobileWidget(HEADER_CREATE_VARIANTS);
    HEADER_MOBILE_CREATE_VARIANTS.config.label = "header.create-variants.label";

    var HEADER_MOBILE_SITES_VARIANTS = toMobileWidget(HEADER_SITES_VARIANTS);
    var HEADER_MOBILE_SITES_SEARCH = toMobileWidget(HEADER_SITES_SEARCH);
    var HEADER_MOBILE_SITES_CREATE = toMobileWidget(HEADER_SITES_CREATE);

    var HEADER_MOBILE_MENU_VARIANTS = {
        id: "HEADER_MOBILE_MENU_VARIANTS",
        name: "js/citeck/menus/citeckMenuGroup",
        config: {
            id: "HEADER_MOBILE_MENU_VARIANTS",
            widgets: [
                HEADER_MOBILE_JOURNALS,
                HEADER_MOBILE_DOCUMENTLIBRARY,
                {
                    id: "HEADER_MOBILE_SITES",
                    name: "js/citeck/menus/citeckMenuGroup",
                    config: {
                        id: "HEADER_MOBILE_SITES",
                        label: "header.sites.label",
                        widgets: [
                            HEADER_MOBILE_SITES_VARIANTS,
                            {
                                id: "HEADER_MOBILE_SITES_MANAGEMENT",
                                name: "js/citeck/menus/citeckMenuGroup",
                                config: {
                                    id: "HEADER_MOBILE_SITES_MANAGEMENT",
                                    widgets: [ HEADER_MOBILE_SITES_SEARCH, HEADER_MOBILE_SITES_CREATE ]
                                }
                            }
                        ]
                    }
                },

                HEADER_MOBILE_CREATE_VARIANTS,
                HEADER_MOBILE_CREATE_WORKFLOW_VARIANTS
            ]
        }
    };

    HEADER_MOBILE_MENU_VARIANTS.config.widgets.push(buildMorePopup(true));

    if (loggingWidgetItems) {
        HEADER_MOBILE_MENU_VARIANTS.config.widgets.push({
            id: "HEADER_LOGGING",
            name: "js/citeck/header/citeckMenuGroup",
            config: {
                id: "HEADER_MOBILE_LOGGING",
                label: "Debug Menu",
                widgets: loggingWidgetItems
            }
        });
    }

    appMenuBar.config.widgets.unshift(
        {
            id: "HEADER_MOBILE_MENU",
            name: "alfresco/header/AlfMenuBarPopup",
            config: {
                id: "HEADER_MOBILE_MENU",
                widgets: [HEADER_MOBILE_MENU_VARIANTS],
                style: "padding-right: 5px;"
            }
        },
        buildLogo(isMobile)
    );

}


// ---------------------
// FUNCTIONS
// ---------------------

function getSitesForUser(username) {
    var result = remote.call("/api/people/" + encodeURIComponent(username) + "/sites");

    if (result.status == 200 && result != "{}") {
        return eval('(' + result + ')');
    }
    return [];
};

function buildMorePopup(isMobile) {
    var tools = myTools;
    tools.push({ id: "orgstruct", url: "orgstruct", iconImage: "/share/res/components/images/header/orgstruct.png" });
    var config = {
        widgets: [buildGroup(isMobile, "my", tools)]
    };

    if (!isMobile) {
        config.label = "header.more.label";
        config.widgets.unshift(buildMovableGroup());
    }

    if (user.isAdmin) config.widgets.push(buildGroup(isMobile, "tools", adminTools));

    return {
        id: "HEADER_" + (isMobile ? "MOBILE_" : "") + "MORE",
        name: isMobile ? "js/citeck/menus/citeckMenuGroup" : "alfresco/header/AlfMenuBarPopup",
        config: config
    };
};

function buildMovableGroup() {
    return {
        id: "HEADER_MORE_MOVABLE_GROUP",
        name: "js/citeck/menus/citeckMenuGroup",
        config: {
            id: "HEADER_MORE_MOVABLE_GROUP",
            label: "",
            movable: { maxWidth: 1170 },
            widgets: buildItems([
                {
                    id: "journals",
                    url: buildSiteUrl(currentSite) + "journals2/list/main",
                    movable: { maxWidth: 1080 }
                },
                {
                    id: "documentlibrary",
                    url: buildSiteUrl(currentSite) + "documentlibrary",
                    movable: { maxWidth: 1170 }
                }
            ], "movable")
        }
    };
};

function buildGroup(isMobile, idGroup, tools) {
var id = "HEADER_" + (isMobile ? "MOBILE_" : "") + "MORE_" + idGroup.toUpperCase() + "_GROUP",
    group = (isMobile ? "MOBILE_" : "") + "MORE_" + idGroup.toUpperCase();

    return {
        id: id,
        name: "js/citeck/menus/citeckMenuGroup",
        config: {
            id: id,
            label: "header." + idGroup.toLowerCase() + ".label",
            widgets: buildItems(tools, group)
        }
    };
};

function buildSitesForUser(sites, anotherItems) {
    var sitesPresets = [];
    if (sites && sites.length > 0) {
        for (var sd = 0; sd < sites.length; sd++) {
            if (isSlideMenu) {
                var site = {
                    id: "HEADER_" + (sites[sd].shortName.replace(/\-/g, "_")).toUpperCase(),
                    url: "/share/page?site=" + sites[sd].shortName,
                    label: sites[sd].title,
                    widgets: buildJournalsListForSite(sites[sd].shortName)
                };

                site.widgets.push({
                        id: "HEADER_" + (sites[sd].shortName.replace(/\-/g, "_")).toUpperCase() + "_DOCUMENTLIBRARY",
                        label: "header.documentlibrary.label",
                        url: "/share/page/" + buildSiteUrl(sites[sd].shortName) + "documentlibrary"
                    }, {
                        id: "HEADER_" + (sites[sd].shortName.replace(/\-/g, "_")).toUpperCase() + "_SITE_CALENDAR",
                        label: "header.calendar.label",
                        url: "/share/page/" + buildSiteUrl(sites[sd].shortName) + "calendar"
                    }
                );

                sitesPresets.push(site);
            } else {
                sitesPresets.push({
                    url: "?site=" + sites[sd].shortName,
                    id: sites[sd].shortName.replace(/-/, "_"),
                    label: sites[sd].title
                });
            }
        }
    }
    if (anotherItems && anotherItems.length) {
        sitesPresets = sitesPresets.concat(anotherItems);
    }
    return isSlideMenu ? sitesPresets : buildItems(sitesPresets, "SITE");
};

function buildCreateVariants(sites) {
    var createCases = [{
            id: "HEADER_CREATE_WORKFLOW",
            name: isCascadCreateMenu ? "alfresco/menus/AlfCascadingMenu" : "js/citeck/menus/citeckMenuGroup",
            config: {
                id: "HEADER_CREATE_WORKFLOW",
                label: "header.create-workflow.label",
                widgets: buildItems([{
                        id: "HEADER_CREATE_WORKFLOW_ADHOC",
                        label: "header.create-workflow-adhoc.label",
                        url: "workflow-start-page?formType=workflowId&formKey=activiti$perform"
                    },
                    {
                        id: "HEADER_CREATE_WORKFLOW_CONFIRM",
                        label: "header.create-workflow-confirm.label",
                        url: "start-specified-workflow?workflowId=activiti$confirm"
                    }
                ])
            }
        }];
    if (sites && sites.length > 0) {
        for (var sd = 0; sd < sites.length; sd++) {
            var siteId = "HEADER_" + (sites[sd].shortName.replace(/\-/g, "_")).toUpperCase();
            createCases.push({
                id: siteId,
                name: isCascadCreateMenu ? "alfresco/menus/AlfCascadingMenu" : "js/citeck/menus/citeckMenuGroup",
                config: {
                    id: siteId,
                    label: sites[sd].title,
                    widgets: buildItems(buildCreateVariantsForSite(sites[sd].shortName, true), sites[sd].shortName, true)
                }
            });
        }
    }
    return createCases;
};

function buildCreateVariantsForSite(sitename, forSlideMenu) {
    var createVariantsPresets = [],
        result = remote.call("/api/journals/create-variants/site/" + encodeURIComponent(sitename));

    if (result.status == 200 && result != "{}") {
        var responseData = eval('(' + result + ')'),
            createVariants = responseData.createVariants;

        if (createVariants && createVariants.length > 0) {
            for (var cv = 0; cv < createVariants.length; cv++) {

                var variant = createVariants[cv];

                createVariantsPresets.push({
                    label: variant.title,
                    id: "HEADER_" + ((sitename + "_" + variant.type).replace(/\-/g, "_")).toUpperCase(),
                    payload: createVariants[cv],
                    clickEvent: 'function(){Citeck.forms.handleHeaderCreateVariant(arguments[2].payload);}'
                });
            }
        }
    }

    return forSlideMenu ? createVariantsPresets : buildItems(createVariantsPresets, "CREATE_VARIANT", true);
}

function buildJournalsListForSite(sitename, journalUrl, request) {
    var journalsResult = [],
        result = remote.call(request ? request : "/api/journals/list?journalsList=site-" + encodeURIComponent(sitename) + "-main");

    if (result.status == 200 && result != "{}") {
        var responseData = eval('(' + result + ')'),
            journals = responseData.journals;

        if (journals && journals.length) {
            for (var j = 0; j < journals.length; j++) {
                var url = (journalUrl ? journalUrl : "/share/page/site/" + sitename + "/journals2/list/main#journal=") + journals[j].nodeRef;
                journalsResult.push({
                    label: journals[j].title,
                    id: "HEADER_" + ((sitename + "_" + journals[j].type).replace(/\-/g, "_")).toUpperCase() + "_JOURNAL",
                    url: url + "&filter=",
                    widgets: buildFiltersForJournal(journals[j].type, url)
                });
            }
        }
    }

    return journalsResult;
}

function buildFiltersForJournal(journalType, filterUrl) {
    var filtersResult = [],
        result = remote.call("/api/journals/filters?journalType=" + journalType);

    if (result.status == 200 && result != "{}") {
        var responseData = eval('(' + result + ')'),
            filters = responseData.filters;

        if (filters && filters.length) {
            for (var f = 0; f < filters.length; f++) {
                filtersResult.push({
                    label: filters[f].title,
                    id: filters[f].type + "-filter",
                    id: "HEADER_" + ((journalType + "_" + filters[f].title).replace(/\-/g, "_")).toUpperCase() + "_FILTER",
                    url: filterUrl + "&filter=" + filters[f].nodeRef
                });
            }
        }
    }

    return filtersResult;
}

function getPhoto(userNodeRef) {
    var result = remote.call("/citeck/node?nodeRef=" + userNodeRef + "&props=ecos:photo");

    if (result.status == 200 && result != "{}") {
        return eval('(' + result + ')').props["ecos:photo"].size;
    }
    return "";
};

function buildToolsItems(items, excludeItems) {
    if (items && items.length) {
        items = items.filter(function(item) {
            if (!excludeItems || excludeItems.indexOf(item.id) == -1) {
                item.label = item.label ? item.label : "header." + item.id + ".label";
                item.id = "HEADER_" + (item.id.replace(/\-/g, "_")).toUpperCase();
                item.url = "/share/page/" + item.url;
                return item;
            }
        });
    }
    return items;
};

function buildLogo(isMobile) {
    return {
        id: isMobile ? "HEADER_MOBILE_LOGO" : "HEADER_LOGO",
        name: "js/citeck/logo/citeckLogo",
        config: {
            id: isMobile ? "HEADER_MOBILE_LOGO" : "HEADER_LOGO",
            logoClasses: "alfresco-logo-only",
            currentTheme: theme,
            logoSrc: isMobile ? getHeaderMobileLogoUrl() : getHeaderLogoUrl(),
            targetUrl: "user/" + encodeURIComponent(user.name) + "/dashboard"
        }
    };
};

function getWidgets() {
    var widgets = [
        {
            id: "HEADER_MENU_TASKS",
            sectionTitle: "header.tasks.label",
            widgets: buildJournalsListForSite("tasks", "/share/page/journals2/list/tasks#journal=", "/api/journals/list?journalsList=tasks")
        },
        {
            id: "HEADER_MENU_SITES",
            sectionTitle: "header.sites.label",
            widgets: buildSitesForUser(accessibleSites,[{
                    id: "HEADER_SITES_SEARCH",
                    label: "header.find-sites.label",
                    url: "/share/page/custom-site-finder"
                },
                {
                    id: "HEADER_SITES_CREATE",
                    label: "header.create-site.label",
                    clickEvent: createSiteClickEvent
                }])
        },
        {
            id: "HEADER_MENU_ORGSTRUCT",
            sectionTitle: "header.orgstruct.label",
            widgets: [{id: "HEADER_MENU_ORGSTRUCT_WIDGET", label: "header.orgstruct.label", url: "/share/page/orgstruct"}]
        },
        {
            id: "HEADER_MORE_MY_GROUP",
            sectionTitle: "header.my.label",
            widgets: buildToolsItems(myTools, "task-journals, my-profile")
        }];

    if (user.isAdmin) {
        widgets.push({
            id: "HEADER_MORE_TOOLS_GROUP",
            sectionTitle: "header.tools.label",
            widgets: buildToolsItems(adminTools)
        });
    }

    return widgets;
};


model.__alf_current_site__ = currentSite;
