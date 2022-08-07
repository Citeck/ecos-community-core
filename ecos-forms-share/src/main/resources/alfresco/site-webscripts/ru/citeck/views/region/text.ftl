<#assign params = viewScope.region.params!{} />
<#assign textValueInput>
    <#if params.validation?? && params.validation == 'false'>textValue
    <#else>textValidationValue</#if>
</#assign>

<#assign disabled>
    <#if params.disabled??>
        ko.computed(function() { return ${params.disabled}; });
    <#else>protected</#if>
</#assign>

<#assign maxlength>
    <#if params.maxlength??>
        maxlength="${params.maxlength}"
    </#if>
</#assign>

<#assign customStyle>
    <#if params.showClearButton?? && params.showClearButton == 'true'>
        style="width: calc(100% - 16px);"
    </#if>
</#assign>

<input id="${fieldId}" type="text" data-bind="textInput: ${textValueInput?trim}, disable: ${disabled?trim}" ${maxlength} ${customStyle}/>

<#if params.showClearButton?? && params.showClearButton == 'true'>
    <span class="value-item-actions">
        <a class="delete-value-item"
           data-bind="click: function() { if (!protected()) {value(null);} }, css: { 'delete-value-item-disabled': protected() }">
        </a>
    </span>
</#if>
