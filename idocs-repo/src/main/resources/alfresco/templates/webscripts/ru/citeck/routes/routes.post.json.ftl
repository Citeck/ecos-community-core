<#escape x as jsonUtils.encodeJSONString(x)>
    {   
        <#if data??>
            "route": {
                <#if data.properties["route:precedence"]?has_content>
                    "precedence": "${data.properties["route:precedence"]?string}",
                </#if>

                "name": "${data.name?string}",
                "nodeRef": "${data.nodeRef?string}",
                
                <#-- Stages -->
                "stages": [
                    <#if data.childAssocs["route:stages"]?has_content>
                        <#list data.childAssocs["route:stages"] as stage>
                            {
                                <#if stage.properties["cm:displayName"]??>
                                    "displayName": "${stage.properties["cm:displayName"]?string}",
                                </#if>
                                <#if stage.properties["route:dueDateExpr"]??>
                                    "dueDateExpr": "${stage.properties["route:dueDateExpr"]?string}",
                                </#if>
                                <#if stage.properties["cm:position"]??>
                                    "position": "${stage.properties["cm:position"]?string}",
                                </#if>

                                "name": "${stage.properties["cm:name"]?string}",
                                "nodeRef": "${stage.nodeRef?string}",

                                <#-- Participants -->
                                "participants": [
                                    <#if stage.childAssocs["route:participants"]?has_content>
                                        <#list stage.childAssocs["route:participants"] as participant>
                                            {
                                                <#if participant.properties["cm:position"]??>
                                                    "position": "${participant.properties["cm:position"]?string}",
                                                </#if>

                                                <#if participant.assocs["route:authority"]??>
                                                    "authority": {
                                                        <#assign authority = participant.assocs["route:authority"][0]>
                                                        
                                                        <#-- for authority user -->
                                                        <#if authority.properties["cm:displayName"]??>
                                                            "displayName": "${authority.properties["cm:displayName"]?string}",
                                                        <#elseif authority.properties["cm:firstName"]?? && authority.properties["cm:lastName"]??>
                                                            "displayName": "${authority.properties["cm:firstName"]?string} ${authority.properties["cm:lastName"]?string}",
                                                        </#if>
                                                        <#if authority.properties["cm:firstName"]??>
                                                            "firstName": "${authority.properties["cm:firstName"]?string}",
                                                        </#if>
                                                        <#if authority.properties["cm:lastName"]??>
                                                            "lastName": "${authority.properties["cm:lastName"]?string}",
                                                        </#if>
                                                        <#if authority.properties["cm:email"]??>
                                                            "email": "${authority.properties["cm:email"]?string}",
                                                        </#if>

                                                        <#-- for authority group -->
                                                        <#if authority.properties["cm:authorityDisplayName"]??>
                                                            "authorityDisplayName": "${authority.properties["cm:authorityDisplayName"]?string}",
                                                        </#if>
                                                        <#if authority.properties["cm:authorityName"]??>
                                                            "authorityName": "${authority.properties["cm:authorityName"]?string}",
                                                        </#if>

                                                        "name": "${authority.name}",
                                                        "nodeRef": "${authority.nodeRef}"
                                                    },
                                                </#if>

                                                "name": "${participant.name?string}",
                                                "nodeRef": "${participant.nodeRef?string}"
                                            }<#if participant_has_next>,</#if>
                                        </#list>
                                    </#if>
                                ]
                            }<#if stage_has_next>,</#if>
                        </#list>
                    </#if>
                ]
            }
        </#if>
    }
</#escape>