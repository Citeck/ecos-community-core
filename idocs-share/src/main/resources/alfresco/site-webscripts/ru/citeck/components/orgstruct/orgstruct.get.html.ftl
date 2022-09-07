<#import "orgstruct.lib.ftl" as orgstructlib />

<@markup id="css" >
    <@link rel="stylesheet" type="text/css" href="${url.context}/res/components/people-finder/people-finder.css" group="orgstruct" />
    <@link rel="stylesheet" type="text/css" href="${url.context}/res/components/people-finder/group-finder.css" group="orgstruct" />

    <@link rel="stylesheet" type="text/css" href="${url.context}/res/citeck/components/orgstruct/orgstruct-icons.css" group="orgstruct" />
    <@link rel="stylesheet" type="text/css" href="${url.context}/res/citeck/components/orgstruct/console.css" group="orgstruct" />
</@>

<#assign el=args.htmlid?html>
<#assign rootGroup = "_orgstruct_home_" />
<#assign excludeAuthorities>
    <#if config.scoped["InvariantControlsConfiguration"]?? &&
         config.scoped["InvariantControlsConfiguration"].orgstruct?? &&
         config.scoped["InvariantControlsConfiguration"].orgstruct.attributes["excludeAuthorities"]??>
             '${config.scoped["InvariantControlsConfiguration"].orgstruct.attributes["excludeAuthorities"]}'
    <#else>''</#if>
</#assign>
<script type="text/javascript">//<![CDATA[
    require([
        'components/console/consoletool',
        'components/people-finder/people-finder',
        'components/people-finder/group-finder',
        'modules/simple-dialog',
        'components/form/form',
        'citeck/components/form/constraints',
        'citeck/components/form/select',
        'citeck/components/dynamic-tree/dynamic-toolbar',
        'citeck/components/orgstruct/picker-dialogs',
        'ecosui!ecos-records'
    ], function() {
        Citeck.Records.get('people@' + Alfresco.constants.USERNAME).load({
            'isAdmin':'isAdmin?bool',
            'isOrgstructManagers':'authorities._has.GROUP_ORGSTRUCT_MANAGERS?bool'
        }).then(response => {
            if (response) {
                if (typeof Alfresco.constants.Citeck == "undefined" || !Alfresco.constants.Citeck) {
                    Alfresco.constants.Citeck = {};
                };
                // Get know if current user has access to actions
                Alfresco.constants.Citeck.userIsAdmin = response["isAdmin"];
                Alfresco.constants.Citeck.userIsOrgstructManager = response["isOrgstructManagers"];
                Alfresco.constants.Citeck.isOrgstructActionsAllowed = response["isOrgstructManagers"] || response["isAdmin"];
                // render orgstruct
                require(['citeck/components/orgstruct/console'], function (){
                    new Alfresco.component.ConsoleOrgstruct("${el}").setOptions({
                        currentFilter: "orgstruct",
                        filters: [
                            {
                                name: "orgstruct",
                                model: {
                                    formats: {
                                        "authority": {
                                            name: "authority-{fullName}",
                                            keys: [ "{groupType}-manager-{roleIsManager}", "{authorityType}-{groupType}", "{authorityType}", "available-{available}", "isPersonDisabled-{isPersonDisabled}" ]
                                        }
                                    },
                                    item: {
                                        "GROUP": {
                                            "format": "authority",
                                            "get": "${page.url.context}/proxy/alfresco/api/orgstruct/authority/{fullName}",
                                        },
                                        "USER": {
                                            "format": "authority",
                                            "get": "${page.url.context}/proxy/alfresco/api/orgstruct/authority/{fullName}",
                                        }
                                    },
                                    children: {
                                        "root": {
                                            "format": "authority",
                                            "get": "${page.url.context}/proxy/alfresco/api/orgstruct/group/${rootGroup}/children/?excludeAuthorities=" + ${excludeAuthorities?trim},
                                            "delete": "${page.url.context}/proxy/alfresco/api/groups/${rootGroup}/children/{item.fullName}",
                                        },
                                        "search": {
                                            "format": "authority",
                                            "get": "${page.url.context}/proxy/alfresco/api/orgstruct/group/${rootGroup}/children/?filter={query}&recurse=true",
                                            "delete": "${page.url.context}/proxy/alfresco/api/groups/{item.shortName}",
                                        },
                                        "GROUP": {
                                            "format": "authority",
                                            "get": "${page.url.context}/proxy/alfresco/api/orgstruct/group/{shortName}/children/?showdisabled=" +
                                                    (Alfresco.constants.Citeck.isOrgstructActionsAllowed ? "true" : "false") + "&excludeAuthorities=" + ${excludeAuthorities?trim},
                                            "add": "${page.url.context}/proxy/alfresco/api/groups/{parent.shortName}/children/{item.fullName}",
                                            "delete": "${page.url.context}/proxy/alfresco/api/groups/{parent.shortName}/children/{item.fullName}",
                                            "isOrgstructManagers": Alfresco.constants.Citeck.userIsOrgstructManager
                                        }
                                    },
                                    titles: {
                                        "root": "{title}",
                                        "GROUP": "{displayName} ({shortName})",
                                        "USER": "{firstName} {lastName} ({shortName})",
                                    },
                                    errors: [
                                        {
                                            "regexp": "regexp.add-item-failure-cyclic",
                                            "message": "message.add-item-failure-cyclic"
                                        }
                                    ],
                                },
                                forms: {
                                    destination: {
                                        "root": "GROUP__orgstruct_home_",
                                        "GROUP": "{fullName}",
                                    },
                                    errors: [
                                        {
                                            "regexp": "regexp.create-group-failed-exists",
                                            "message": "message.create-group-failed-exists"
                                        }
                                    ],
                                    nodeId: {
                                        "": "{nodeRef}"
                                    },
                                },
                                toolbar: {
                                    buttons: {
                                        "root": [ "search", Alfresco.constants.Citeck.isOrgstructActionsAllowed ? "createBranch" : "" ],
                                        "search": [ "search", "resetSearch" ],
                                        "GROUP-branch": [
                                            Alfresco.constants.Citeck.isOrgstructActionsAllowed ? "createBranch" : "",
                                            Alfresco.constants.Citeck.isOrgstructActionsAllowed ? "createRole" : "",
                                            Alfresco.constants.Citeck.isOrgstructActionsAllowed ? "addGroup" : "" ],
                                        "GROUP-role": [
                                            Alfresco.constants.Citeck.isOrgstructActionsAllowed ? "addUser" : "",
                                            Alfresco.constants.Citeck.isOrgstructActionsAllowed ? "createUser" : "" ],
                                        "GROUP-group": [],
                                    },
                                },
                                tree: {
                                    sorting: {
                                        "": [
                                            { by: "{authorityType}" }, // GROUP, USER
                                            { by: "{groupType}", descend: true }, // role, branch, <none>
                                            { by: "{roleIsManager}", descend: true }, // managers first
                                            { by: "{firstName}-{lastName}-{displayName}" } // firstName/lastName for users, displayName for groups
                                        ],
                                    },
                                    buttons: {
                                        "GROUP-group": [
                                            Alfresco.constants.Citeck.isOrgstructActionsAllowed ? "convertToBranch" : "",
                                            Alfresco.constants.Citeck.isOrgstructActionsAllowed ? "convertToRole" : "",
                                            Alfresco.constants.Citeck.isOrgstructActionsAllowed ? "editItem" : "",
                                            Alfresco.constants.Citeck.isOrgstructActionsAllowed ? "deleteItem": "" ],
                                        "GROUP-branch": [
                                            Alfresco.constants.Citeck.isOrgstructActionsAllowed ? "convertToGroup" : "",
                                            Alfresco.constants.Citeck.isOrgstructActionsAllowed ? "editItem" : "",
                                            Alfresco.constants.Citeck.isOrgstructActionsAllowed ? "deleteItem": "" ],
                                        "GROUP-role": [
                                            Alfresco.constants.Citeck.isOrgstructActionsAllowed ? "convertToGroup" : "",
                                            Alfresco.constants.Citeck.isOrgstructActionsAllowed ? "editItem" : "",
                                            Alfresco.constants.Citeck.isOrgstructActionsAllowed ? "deleteItem": "" ],
                                        // "USER": [ "editItem", "deleteItem" ]
                                        "USER": [
                                            Alfresco.constants.Citeck.isOrgstructActionsAllowed ? "editItemInplaced" : "",
                                            Alfresco.constants.Citeck.isOrgstructActionsAllowed ? "deleteItem" : ""]
                                    },
                                },
                                list: {
                                    sorting: {
                                        "": [
                                            { by: "{authorityType}" }, // GROUP, USER
                                            { by: "{groupType}", descend: true }, // role, branch, <none>
                                            { by: "{roleIsManager}", descend: true }, // managers first
                                            { by: "{firstName}-{lastName}-{displayName}" } // firstName/lastName for users, displayName for groups
                                        ],
                                    },
                                    buttons: {
                                    "GROUP-group": [
                                        Alfresco.constants.Citeck.isOrgstructActionsAllowed ? "convertToBranch" : "",
                                        Alfresco.constants.Citeck.isOrgstructActionsAllowed || Alfresco.constants.Citeck.isAllowToEdit ? "convertToRole" : "",
                                        Alfresco.constants.Citeck.isOrgstructActionsAllowed ? "editItem" : "",
                                        Alfresco.constants.Citeck.isOrgstructActionsAllowed ? "deleteItem" : "" ],
                                    "GROUP-branch": [
                                        Alfresco.constants.Citeck.isOrgstructActionsAllowed ? "convertToGroup" : "",
                                        Alfresco.constants.Citeck.isOrgstructActionsAllowed ? "editItem" : "",
                                        Alfresco.constants.Citeck.isOrgstructActionsAllowed ? "deleteItem" : "" ],
                                    "GROUP-role": [
                                        Alfresco.constants.Citeck.isOrgstructActionsAllowed ? "convertToGroup" : "",
                                        Alfresco.constants.Citeck.isOrgstructActionsAllowed ? "editItem" : "",
                                        Alfresco.constants.Citeck.isOrgstructActionsAllowed ? "deleteItem" : "" ],
                                    "USER": [
                                        Alfresco.constants.Citeck.isOrgstructActionsAllowed ? "editItemInplaced" : "",
                                        Alfresco.constants.Citeck.isOrgstructActionsAllowed ? "deleteItem" : "" ]
                                    },
                                },
                            },
                            {
                                name: "orgmeta",
                                model: {
                                    formats: {
                                        "groupType": {
                                            name: "groupType-{name}",
                                            keys: [ "groupType-{name}", "groupType" ]
                                        },
                                        "branchType": {
                                            name: "branchType-{name}",
                                            keys: [ "groupSubType", "branchType" ]
                                        },
                                        "roleType": {
                                            name: "roleType-{name}",
                                            keys: [ "groupSubType", "roleType" ],
                                        },
                                    },
                                    item: {
                                        "branchType": {
                                            "format": "branchType",
                                            "get": "${page.url.context}/proxy/alfresco/api/orgmeta/branch/{name}",
                                        },
                                        "roleType": {
                                            "format": "roleType",
                                            "get": "${page.url.context}/proxy/alfresco/api/orgmeta/role/{name}",
                                        },
                                    },
                                    children: {
                                        "root": {
                                            "format": "groupType",
                                            "get": "${page.url.context}/proxy/alfresco/api/orgmeta/",
                                        },
                                        "groupType-branch": {
                                            "format": "branchType",
                                            "get": "${page.url.context}/proxy/alfresco/api/orgmeta/branch",
                                            "delete": "${page.url.context}/proxy/alfresco/api/orgmeta/branch/{item.name}",
                                        },
                                        "groupType-role": {
                                            "format": "roleType",
                                            "get": "${page.url.context}/proxy/alfresco/api/orgmeta/role",
                                            "delete": "${page.url.context}/proxy/alfresco/api/orgmeta/role/{item.name}",
                                        },
                                    },
                                    titles: {
                                        "groupType": "{name}",
                                        "groupType-branch": "${msg("item.branch-type.label")}",
                                        "groupType-role": "${msg("item.role-type.label")}",
                                        "groupSubType": "{title} ({name})",
                                    },
                                    errors: [
                                        {
                                            "regexp": "regexp.delete-group-type-failed-referenced",
                                            "message": "message.delete-group-type-failed-referenced"
                                        }
                                    ],
                                },
                                forms: {
                                    destination: {
                                        "groupType": "{root}",
                                    },
                                    errors: [
                                        {
                                            "regexp": "regexp.create-group-type-failed-exists",
                                            "message": "message.create-group-type-failed-exists"
                                        }
                                    ],
                                    nodeId: {
                                        "": "{nodeRef}"
                                    },
                                },
                                tree: {
                                    buttons: {
                                        "groupType-branch": [ "createBranchType" ],
                                        "groupType-role": [ "createRoleType" ],
                                        "groupSubType": [ "editItem", "deleteItem" ],
                                    },
                                },
                                list: {
                                    buttons: {
                                        "groupType-branch": [ "createBranchType" ],
                                        "groupType-role": [ "createRoleType" ],
                                        "groupSubType": [ "editItem", "deleteItem" ],
                                    },
                                },
                                toolbar: {
                                    buttons: {
                                        "groupType-branch": [ "createBranchType" ],
                                        "groupType-role": [ "createRoleType" ],
                                    }
                                },
                            },
                            {
                                name: "deputies",
                                model: {
                                    formats: {
                                        "authority": {
                                            name: "authority-{fullName}",
                                            keys: ["{groupType}-manager-{roleIsManager}", "{authorityType}-{groupType}", "{authorityType}", "deputy-{deputy}", "manage-{manage}", "authority", "available-{available}"]
                                        },
                                        "member": {
                                            "name": "deputy-{deputy}-isAssistant-{isAssistant}-{userName}",
                                            keys: ["available-{available}", "deputy-{deputy}", "manage-{manage}", "member", "deputy-{deputy}-isAssistant-{isAssistant}", "canDelete-{canDelete}"],
                                            calc: function (item) {
                                                if (typeof item.deputy == "undefined") item.deputy = true;
                                                if (typeof item.isAssistant == "undefined") item.isAssistant = false;
                                            }
                                        },
                                        "deputy": {
                                            name: "deputy-true-isAssistant-{isAssistant}-{userName}",
                                            keys: ["available-{available}", "deputy-true", "manage-false", "member", "canDelete-{canDelete}", "isAssistant-{isAssistant}", "deputy-true-isAssistant-{isAssistant}"]
                                        },
                                    },
                                    item: {
                                        "authority": {
                                            "format": "authority",
                                            "get": "${page.url.context}/proxy/alfresco/api/orgstruct/authority/{fullName}",
                                        },
                                        "deputy-false-isAssistant-false": {
                                            "format": "member",
                                            "get": "${page.url.context}/proxy/alfresco/api/deputy/{userName}",
                                        },
                                        "deputy-false-isAssistant-true": {
                                            "format": "member",
                                            "get": "${page.url.context}/proxy/alfresco/api/deputy/{userName}",
                                        },
                                        "deputy-true-isAssistant-false": {
                                            "format": "deputy",
                                            "get": "${page.url.context}/proxy/alfresco/api/deputy/{userName}",
                                        },
                                        "deputy-true-isAssistant-true": {
                                            "format": "deputy",
                                            "get": "${page.url.context}/proxy/alfresco/api/assistant/{userName}",
                                        },
                                    },
                                    children: {
                                        "search": {
                                            "format": "authority",
                                            "get": "${page.url.context}/proxy/alfresco/api/orgstruct/group/${rootGroup}/children/?filter={query}&recurse=true&role=true&user=true&default=false",
                                        },
                                        "GROUP": {
                                            "format": "member",
                                            "get": "${page.url.context}/proxy/alfresco/api/deputy/{fullName}/members",
                                            "add": "${page.url.context}/proxy/alfresco/api/deputy/{parent.fullName}/deputies?users={item.userName}&addAssistants={item.isAssistant}",
                                            "delete": "${page.url.context}/proxy/alfresco/api/deputy/{parent.fullName}/deputies?users={item.userName}&isAssistants={item.isAssistant}",
                                        },
                                        "USER": {
                                            "format": "deputy",
                                            "get": "${page.url.context}/proxy/alfresco/api/deputy/{fullName}/deputies",
                                            "add": "${page.url.context}/proxy/alfresco/api/deputy/{parent.fullName}/deputies?users={item.userName}&addAssistants={item.isAssistant}",
                                            "delete": "${page.url.context}/proxy/alfresco/api/deputy/{parent.fullName}/deputies?users={item.userName}&isAssistants={item.isAssistant}",
                                        }
                                    },
                                    titles: {
                                        "root": "{title}",
                                        "GROUP": "{displayName} ({shortName})",
                                        "USER": "{firstName} {lastName} ({fullName})",
                                        "member": "{firstName} {lastName} ({userName})",
                                    },
                                },
                                forms: {
                                    nodeId: {
                                        "": "{nodeRef}"
                                    },
                                },
                                toolbar: {
                                    buttons: {
                                        "root": [ "search" ],
                                        "search": [ "search", "resetSearch" ],
                                        "authority": ["addDeputy", "addAssistant"],
                                        "": [],
                                    },
                                },
                                tree: {
                                    buttons: {
                                        "deputy-true": [ "editItem", "deleteItem" ],
                                        "": [ "editItem" ]
                                    },
                                },
                                list: {
                                    buttons: {
                                        "deputy-true": [ "editItem", "deleteItem" ],
                                        "": [ "editItem" ]
                                    },
                                },
                            }
                        ],
                    }).setMessages(${messages});
                });
            }
        });
    });
//]]></script>

<@orgstructlib.renderOrgstructBody el />
