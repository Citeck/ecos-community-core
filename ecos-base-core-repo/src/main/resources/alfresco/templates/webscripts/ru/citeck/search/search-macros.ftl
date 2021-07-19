<#if personFirstName??>
    <#assign _personFirstName = {"key": "cm:firstName", "value": personFirstName} />
<#else>
    <#assign _personFirstName = "cm:firstName" />
</#if>
<#if personLastName??>
    <#assign _personLastName = {"key": "cm:lastName", "value": personLastName} />
<#else>
    <#assign _personLastName = "cm:lastName" />
</#if>
<#if personMiddleName??>
    <#assign _personMiddleName = {"key": "cm:middleName", "value": personMiddleName} />
<#else>
    <#assign _personMiddleName = "cm:middleName" />
</#if>

<#macro printNodes nodes excludeAttributes=[]>
    <#escape x as jsonUtils.encodeJSONString(x)>
        <#list nodes as node>
            <@printNode node excludeAttributes/><#if node_has_next>,</#if>
        </#list>
    </#escape>
</#macro>

<#macro printNode node excludeAttributes=[]>
    <#escape x as jsonUtils.encodeJSONString(x)>
        {
            "nodeRef": "${node.nodeRef}",
            "parent": <#if node.parent??>"${node.parent.nodeRef}"<#else>null</#if>,
            "type": "${node.getTypeShort()}",
            "classNames": [
                <#list nodeService.getNodeClasses(node) as className>
                "${shortQName(className)}"<#if className_has_next>,</#if>
                </#list>
            ],
            "isDocument": ${node.isDocument?string},
            "isContainer": ${node.isContainer?string},
            "attributes": {
                <#assign attributes = nodeService.getAttributes(node) />
                <#list attributes?keys as key>
                    <#if !excludeAttributes?seq_contains(key)>
                        "${key}": <#if !(attributes[key]??)>
                            null
                        <#elseif nodeService.isContentProperty(attributes[key])>
                            <@printValue node.properties[key] />
                        <#elseif nodeService.isDateProperty(key)>
                            <@printValue value=attributes[key] dateFormat="yyyy-MM-dd" />
                        <#else>
                            <@printValue attributes[key] />
                        </#if><#if key_has_next>,</#if>
                    </#if>
                </#list>
            },
			"permissions": {
			<#assign permissions = nodeService.getAllowedPermissions(node) />
			<#list [ "CancelCheckOut", "ChangePermissions", "CreateChildren", "Delete", "Write" ] as permission>
				<#if !permissions?seq_contains(permission)>
				"${permission}": ${node.hasPermission(permission)?string},
				</#if>
			</#list>
			<#list permissions as permission>
				"${permission}": true<#if permission_has_next>,</#if>
			</#list>
			}
        }
    </#escape>
</#macro>

<#macro printType type>
    <#escape x as jsonUtils.encodeJSONString(x)>
        {
            "type": "${type}",
            "attributes": {
                <#assign attributes = nodeService.getAttributes(type) />
                <#list attributes?keys as key>
                "${key}": <#if attributes[key]??><@printValue attributes[key] /><#else>null</#if><#if key_has_next>,</#if>
                </#list>
            },
            "permissions": {}
        }
    </#escape>
</#macro>

<#macro propertiesJSON properties>
    <#escape x as jsonUtils.encodeJSONString(x)>
        <#list properties?keys as property>
            <#assign propertyName = shortQName(property)>
            "${propertyName}": <#if properties[propertyName]??><@printValue properties[propertyName]/><#else>null</#if><#if property_has_next>,</#if>
        </#list>
    </#escape>
</#macro>

<#macro printValue value dateFormat = "null">
<#compress>
    <#escape x as jsonUtils.encodeJSONString(x)>
        <#if value?is_boolean == true>
            ${value?string("true", "false")}
        <#elseif value?is_number == true>
            ${value?c}
        <#elseif value?is_sequence == true>
        [
            <#list value as item>
                <#if item??><@printValue item/><#else>null</#if><#if item_has_next>,</#if>
            </#list>
        ]
        <#elseif value?is_date == true>
            <#if dateFormat?? && dateFormat != "null">
                "${value?string(dateFormat)}"
            <#else>
                "${xmldate(value)}"
            </#if>
        <#elseif value?is_hash>
            <#if value.getClass??>
                <#assign valueClass = value.getClass().toString() />
            <#else>
                <#assign valueClass = "" />
            </#if>
            <#if nodeService.isContentProperty(value)>
            {
                "class": "${valueClass}",
                "mimetype": <#if value.mimetype??>"${value.mimetype!}"<#else>null</#if>,
                "encoding": <#if value.encoding??>"${value.encoding!}"<#else>null</#if>,
                "size": ${value.size?c},
                <#assign contentUrl = nodeService.getContentUrl(value)! />
                "url": <#if contentUrl != "">"${contentUrl}"<#else>null</#if>
            }
            <#elseif valueClass == "class org.alfresco.repo.template.TemplateNode">
                <@nodeJSON value />
            <#elseif valueClass == "class ru.citeck.ecos.role.script.TemplateCaseRole">
                <@nodeJSON value />
            <#elseif valueClass == "class org.alfresco.service.cmr.repository.NodeRef">
                <@nodeJSON companyhome.nodeByReference[value] />
            <#elseif valueClass == "class java.util.Locale">
                "${value}"
            <#elseif valueClass == "class org.alfresco.service.namespace.QName">
            {
                "fullQName": "${value}",
                "shortQName": "${shortQName(value)}"
            }
            <#else>
            {
                <#if value.toString??>
                    <#assign valueString = value.toString() />
                <#else>
                    <#assign valueString = "" />
                </#if>
                "class": "${valueClass}",
                "value": "${valueString}"
            }
            </#if>
        <#else>
            "${value}"
        </#if>
    </#escape>
</#compress>
</#macro>

<#macro nodeJSON node>
    <#escape x as jsonUtils.encodeJSONString(x)>
    {
        "nodeRef": "${node.nodeRef}",
        "type": "${node.typeShort}",
    <#if node.hasPermission("Read")>
        "classNames": [
            <#list nodeService.getNodeClasses(node) as className>
            "${shortQName(className)}"<#if className_has_next>,</#if>
            </#list>
        ],
        <#assign propNames = getExtraProp(node) />
        <#list propNames as prop>
            <#if prop?is_string>
        "${prop}": <#if node.properties[prop]??><@printValue node.properties[prop]/><#else>null</#if>,
            <#else>
            "${prop.key}": <#if node.properties[prop.value]??><@printValue node.properties[prop.value]/><#elseif node.properties[prop.key]??><@printValue node.properties[prop.key]/><#else>null</#if>,
            </#if>
        </#list>
        "displayName": "${node.properties["cm:title"]!node.properties["itmpl:generatedName"]!node.name}"
    <#else>
        "displayName": ""
    </#if>
    }
    </#escape>
</#macro>

<#function getExtraProp node>
    <#assign extraProps = {
        "cm:person": [ "cm:userName", _personFirstName, _personLastName, _personMiddleName ],
        "cm:authorityContainer": [ "cm:authorityName", "cm:authorityDisplayName" ],
        "bpm:workflowTask": ["bpm:status", "wfm:taskType", "wfm:assignee", "wfm:actors"]
    } />
    <#list extraProps?keys as typeName>
        <#if nodeService.isSubType(node, typeName)?string == "true">
            <#return extraProps[typeName]>
        </#if>
    </#list>
    <#return []>
</#function>

<#macro associationsJSON associations>
    <#escape x as jsonUtils.encodeJSONString(x)>
        <#list associations?keys as association>
            <#assign assocName = shortQName(association)>
            "${assocName}": [
                <#list associations[association] as assoc>
                    <@nodeJSON assoc />
                    <#if assoc_has_next>,</#if>
                </#list>
            ]<#if association_has_next>,</#if>
        </#list>
    </#escape>
</#macro>
