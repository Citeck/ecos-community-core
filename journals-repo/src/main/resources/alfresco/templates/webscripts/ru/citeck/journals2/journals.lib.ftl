<#import "/ru/citeck/invariants/invariants.lib.ftl" as invariants />

<#macro renderJournal journal full=true customCreateVariantsJson="[]" predicate="">
{
    "nodeRef": "${journal.nodeRef}"
    , "title": "${journal.properties["cm:title"]!}"
    , "type": "${journal.properties["journal:journalType"]}"
    <#if full>
    , "criteria": <@renderCriteria journal.childAssocs["journal:searchCriteria"]![] />
    <#if predicate?has_content>
    , "predicate": ${predicate}
    </#if>
    , "createVariants":
        <#if customCreateVariantsJson != "[]">
            ${customCreateVariantsJson}
        <#else>
        [
            <#if journal.childAssocs["journal:createVariants"]??>
                <#list journal.childAssocs["journal:createVariants"] as createVariant>
                    <@renderCreateVariant createVariant /><#if createVariant_has_next>,</#if>
                </#list>
            </#if>
        ]
        </#if>
    </#if>
}
</#macro>

<#macro renderJournals journals full=false>
<#escape x as jsonUtils.encodeJSONString(x)>
[
    <#list journals as journal>
        <@renderJournal journal full /><#if journal_has_next>,</#if>
    </#list>
]
</#escape>
</#macro>

<#macro renderFilter filter full=true>
<#escape x as jsonUtils.encodeJSONString(x)>
{
    "nodeRef": "${filter.nodeRef}"
    , "title": "${filter.properties["cm:title"]!}"
    , "permissions": <@renderPermissions filter />
    <#if full>
    , "journalTypes": <@renderJournalTypes filter.properties["journal:journalTypes"]![] />
    , "criteria": <@renderCriteria filter.childAssocs["journal:searchCriteria"]![] />
    </#if>
}
</#escape>
</#macro>

<#macro renderFilters filters full=false>
<#escape x as jsonUtils.encodeJSONString(x)>
[
    <#list filters as filter>
        <@renderFilter filter full /><#if filter_has_next>,</#if>
    </#list>
]
</#escape>
</#macro>

<#macro renderSettingsItem settings full=true>
<#escape y as jsonUtils.encodeJSONString(y)>
{
    "nodeRef": "${settings.nodeRef}"
    , "title": "${settings.properties["cm:title"]}"
    , "permissions": <@renderPermissions settings />
    <#if full>
    , "journalTypes": <@renderJournalTypes settings.properties["journal:journalTypes"]![] />
    , "visibleAttributes": [
    <#if settings.properties["journal:visibleAttributes"]??>
        <#list settings.properties["journal:visibleAttributes"] as attr>
        "${attr}"<#if attr_has_next>, </#if>
        </#list>
    </#if>
    ]
    , "maxRows": ${(settings.properties["journal:maxRows"]!10)?c}
    <#assign groupBy = settings.properties["journal:groupByAttribute"]! />
    <#assign sortBy = settings.properties["journal:sortByAttribute"]! />
    , "groupByAttribute": <#if groupBy != "">"${groupBy}"<#else>null</#if>
    , "sortByAttribute": <#if sortBy != "">"${sortBy}"<#else>null</#if>
    , "sortByAsc": ${(settings.properties["journal:sortByAsc"]!"null")?string}
    </#if>
}
</#escape>
</#macro>

<#macro renderSettingsList settingsList full=false>
<#escape x as jsonUtils.encodeJSONString(x)>
[
    <#list settingsList as settings>
        <@renderSettingsItem settings full /><#if settings_has_next>,</#if>
    </#list>
]
</#escape>
</#macro>

<#macro renderJournalTypes journalTypes>
<#escape y as jsonUtils.encodeJSONString(y)>
[ <#list journalTypes as journalType>"${journalType}"<#if journalType_has_next>,</#if> </#list>]
</#escape>
</#macro>

<#macro renderCriterion criterion>
<#assign valueTemplate = (criterion.properties["journal:criterionValue"]!"")?replace("#{","${")?interpret />
<#escape y as jsonUtils.encodeJSONString(y)>
{
    "field": "${shortQName(criterion.properties["journal:fieldQName"])}",
    "predicate": "${criterion.properties["journal:predicate"]}",
    "persistedValue": "<@valueTemplate/>"
}
</#escape>
</#macro>

<#macro renderCriteria criteria>
<#escape y as jsonUtils.encodeJSONString(y)>
[
<#list criteria as criterion>
    <@renderCriterion criterion /><#if criterion_has_next>,</#if>
</#list>
]
</#escape>
</#macro>

<#macro renderCreateVariant createVariant>
    <#escape x as jsonUtils.encodeJSONString(x)>
    {
        "title": "${createVariant.properties["cm:title"]!createVariant.properties["cm:name"]}",
        "destination": <#if createVariant.assocs["journal:destination"]??>"${createVariant.assocs["journal:destination"][0].nodeRef}"<#else>null</#if>,
        "type": "<#if createVariant.properties["journal:type"]??>${shortQName(createVariant.properties["journal:type"])}</#if>",
        "formId": "${createVariant.properties["journal:formId"]!}",
        "canCreate": <#if createVariant.assocs["journal:destination"]?? && createVariant.assocs["journal:destination"]?size != 0>${createVariant.assocs["journal:destination"][0].hasPermission("CreateChildren")?string("true","false")}<#else>false</#if>,
        "isDefault": ${(createVariant.properties["journal:isDefault"]!false)?string},
        "createArguments": "${(createVariant.properties["journal:createArguments"]!"")}",
        "recordRef": "${(createVariant.properties["journal:recordRef"]!"")}",
        "formKey": "${(createVariant.properties["journal:formKey"]!"")}"
    }
    </#escape>
</#macro>

<#macro renderCreateVariants createVariants>
<#escape y as jsonUtils.encodeJSONString(y)>
[
<#list createVariants as createVariant>
    <@renderCreateVariant createVariant /><#if createVariant_has_next>,</#if>
</#list>
]
</#escape>
</#macro>

<#macro renderPermissions node>
<#escape y as jsonUtils.encodeJSONString(y)>
{
        <#list [ "Write", "Delete" ] as perm>
        "${perm}": ${node.hasPermission(perm)?string}<#if perm_has_next>,</#if>
        </#list>
}
</#escape>
</#macro>

