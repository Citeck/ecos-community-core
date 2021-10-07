<#if (nodes?size > 0)>
    <#assign rowNum = rowIdx>
    <#list nodes as rowData>
        <#if (rowNum % 2) == 1>
            <#assign rowClass = "odd" />
        <#else>
            <#assign rowClass = "even" />
        </#if>
        <tr>
            <#list rowData as cellData>
                <#if cellData.integerValue == true>
                <td class="integer ${rowClass}">
                <#else>
                    <td class="${rowClass}">
                </#if>
                <#if cellData.url?? && cellData.url?length &gt; 0>
                    <a href="${cellData.url}" target="_blank">${cellData.value!"Link"}</a>
                <#else>
                    ${cellData.value!""}
                </#if>
                </td>
            </#list>
        </tr>
        <#assign rowNum = rowNum + 1>
    </#list>
</#if>
