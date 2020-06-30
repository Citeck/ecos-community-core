<#macro printData data format>
{
  ${(format == 'json')?string('"data"', 'data')}: {
  <#list data as line>
    "${line.key}": "${line.value}"<#if line_has_next>,</#if>
  </#list>
  }
}
</#macro>

<#if args.jsonEscape?? && args.jsonEscape == 'false' >
	<#-- The escaped output hides real values of the required properties -->
	<@printData data=data format=args.format!'' />
<#else>
	<#escape x as jsonUtils.encodeJSONString(x)>
		<@printData data=data format=args.format!'' />
	</#escape>
</#if>
