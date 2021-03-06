<#assign controlId = fieldId + "-autocompleteControl">
<#assign controlParams = viewScope.region.params>

<div id="${controlId}" class="autocomplete-control"
    data-bind='component: { name: "autocomplete",
        params: {
            <#if controlParams.minQueryLength??>
                minQueryLength: "${controlParams.minQueryLength}",
            </#if>
            <#if controlParams.searchScript??>
                searchScript: "${controlParams.searchScript}",
            </#if>
            <#if controlParams.criteria??>
                criteria: function() {
                    return ko.computed(function() {
                        var inputParam = ${controlParams.criteria?replace("'", "\"")};
                        return _.isFunction(inputParam) ? inputParam() : inputParam;
                    });
                },
            </#if>

            data: $data,
            element: $element
        }
    }'>
</div>