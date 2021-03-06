<#if inlineEdit!false>
	<#assign fieldParams = viewScope.field.params!{} />
	<#assign hideInlineEditButton = fieldParams.hideInlineEditButton!"false" />
	<#assign useViewTemplate = fieldParams.useViewTemplate!"false" />

	<div class="block-region block-region-label">
		<@views.renderRegion "label" />

		<!-- ko if: inlineEditVisibility -->
		<@views.renderRegion "help" />
		<!-- /ko -->

		<#if hideInlineEditButton=="false">
			<span class="form-field-inline-mode-changer"
				data-bind="click: inlineEditChanger, clickBubble: false, css: { 'save-mode': inlineEditVisibility }">
				<!-- ko if: inlineEditVisibility --><i class="fa fa-floppy-o" aria-hidden="true"></i><!-- /ko -->
				<!-- ko ifnot: inlineEditVisibility --><i class="fa fa-pencil-square-o" aria-hidden="true"></i><!-- /ko -->
			</span>
		</#if>

		<!-- ko ifnot: inlineEditVisibility -->
		<span class="form-field-inline-icons">
			<!-- ko if: invalid -->
			<i class="fa fa-exclamation-circle warning" aria-hidden="true"></i>
			<!-- /ko -->
		</span>
		<!-- /ko -->
	</div>

	<!-- ko ifnot: inlineEditVisibility -->
	<div class="block-region block-region-input">
		<#assign defaultTemplate="" />
		<#list viewScope.field.regions as region>
			<#if region.name == "input" && region.template?? && (region.template?contains("view-table") || useViewTemplate=="true")>
				<@views.renderElement region />
				<#assign defaultTemplate = region.template />
			</#if>
		</#list>
		<#if !defaultTemplate?has_content><@views.renderTemplate "view" /></#if>
	</div>
	<!-- /ko -->

	<!-- ko if: inlineEditVisibility -->
	<#if hideInlineEditButton=="true">
		<div class="block-region block-region-input">
			<#assign defaultTemplate="" />
			<#list viewScope.field.regions as region>
				<#if region.name == "input" && region.template?? && (region.template?contains("view-table") || useViewTemplate=="true")>
					<@views.renderElement region />
					<#assign defaultTemplate = region.template />
				</#if>
			</#list>
			<#if !defaultTemplate?has_content><@views.renderTemplate "view" /></#if>
		</div>
	<#else>
		<#if hasRegion("input")>
			<div class="block-region block-region-input">
				<@views.renderRegion "input" />
			</div>
		</#if>

		<#if hasRegion("select")>
			<div class="block-region block-region-select">
				<@views.renderRegion "select" />
			</div>
		</#if>

		<@views.renderRegion "message" />
	</#if>
	<!-- /ko -->

	<#function hasRegion regionName>
		<#if viewScope.field.regions?has_content>
			<#list viewScope.field.regions as region>
				<#if region.name == regionName><#return true></#if>
			</#list>
			<#return false>
		</#if>
	</#function>
<#else>
	<div class="block-region block-region-label">
		<@views.renderRegion "label" />	
	</div>

	<div class="block-region block-region-input">
		<@views.renderRegion "input" />
	</div>
</#if>