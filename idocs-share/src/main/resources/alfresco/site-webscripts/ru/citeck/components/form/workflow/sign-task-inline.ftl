<#import "/ru/citeck/components/form/ftl-forms.lib.ftl" as forms />

<#assign canReturnToConfirm = (form.data.prop_wfsgn_canReturnToConfirm!)?string == "true" />
<#assign canReturnToCorrect = (form.data.prop_wfsgn_canReturnToCorrect!)?string == "true" />

<#if canReturnToConfirm>
	<@forms.setMandatoryFields
	fieldNames = [
		"assoc_wfsgn_confirmers"
	] condition = "prop_wfsgn_signOutcome == 'ToConfirm'" />
</#if>

<@forms.renderFormsRuntime formId=formId />

<@formLib.renderFormContainer formId=formId>
	
	<#if canReturnToConfirm>
	<@forms.renderField field = "assoc_wfsgn_confirmers" />
	</#if>
	
	<@forms.renderField field = "prop_bpm_comment" extension = {
		"label": msg("workflow.field.comment"),
		"control": {
			"template": "/org/alfresco/components/form/controls/textarea.ftl",
			"params": {}
		}
	} />
	
	<#assign outcomes = "Signed|" + msg("sign-task.outcome.Signed") + "#alf#" + "Declined|" + msg("sign-task.outcome.Declined") />
	<#if canReturnToConfirm>
		<#assign outcomes = outcomes + "#alf#" + "ToConfirm|" + msg("sign-task.outcome.ToConfirm") />
	</#if>
	<#if canReturnToCorrect>
		<#assign outcomes = outcomes + "#alf#" + "ToCorrect|" + msg("sign-task.outcome.ToCorrect") />
	</#if>
	
	<@forms.renderField field = "prop_wfsgn_signOutcome" extension = {
		"control": {
			"template": "/org/alfresco/components/form/controls/workflow/activiti-transitions.ftl",
			"params": {
				"options": outcomes
			}
		}
	} />
	
</@>
