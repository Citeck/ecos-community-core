<#import "/ru/citeck/search/search-macros.ftl" as search />
<#escape x as jsonUtils.encodeJSONString(x)>
{
    "documentTasks": [
    <#list data.tasks as task>
        {
            "nodeRef": "${task.nodeRef}",
			"attributes": {
				<#if task.task.properties??>
					<@search.propertiesJSON task.task.properties/>
				</#if>
				<#if task.task.associations??>
					<#if (task.task.associations?size > 0 && task.task.properties?? && task.task.properties?size > 0)>
						,
					</#if>
						<@search.associationsJSON task.task.associations/>
				</#if>
			},
            "taskTitle": "${task.title!""}",
            "dueDateTime": "${task.dueDateTime!""}"
        }<#if task_has_next>,</#if>
    </#list>
    ]
}
</#escape>
