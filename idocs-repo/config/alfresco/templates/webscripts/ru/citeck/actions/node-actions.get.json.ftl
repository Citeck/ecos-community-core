<#escape x as jsonUtils.encodeJSONString(x)>
[
<#list actions as action>{
    "title": "${action.title}",
    "url": "${action.url}",
    "docNodeRef": "${action.node}",
    "actionType": "${action.actionType}"
}<#if action_has_next>,</#if></#list>
]
</#escape>