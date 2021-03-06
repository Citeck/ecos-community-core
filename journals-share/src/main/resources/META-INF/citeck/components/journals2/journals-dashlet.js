/*
 * Copyright (C) 2008-2015 Citeck LLC.
 *
 * This file is part of Citeck EcoS
 *
 * Citeck EcoS is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Citeck EcoS is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with Citeck EcoS. If not, see <http://www.gnu.org/licenses/>.
 */
define(['jquery', 'citeck/utils/knockout.utils', 'citeck/components/journals2/journals'], function(jq, koutils, Journals) {

var PopupManager = Alfresco.util.PopupManager,
	koclass = koutils.koclass,
	JournalsList = koclass('JournalsList'),
	Journal = koclass('Journal'),
	Filter = koclass('Filter'),
	Settings = koclass('Settings'),
	JournalsWidget = koclass('JournalsWidget'),
	JournalsDashlet = koclass('JournalsDashlet', JournalsWidget),
	DashletConfig = koclass('DashletConfig');

JournalsList
	.load('journals', koutils.simpleLoad({
		url: Alfresco.constants.PROXY_URI + "api/journals/list?journalsList={id}"
	}))
	;

DashletConfig
	.property('journalsList', JournalsList)
	.property('journal', Journal)
	.property('filter', Filter)
	.property('settings', Settings)
	.property('actionGroup', String)
	;

JournalsDashlet
	.property('dashletConfig', DashletConfig)
	.property('_dashletConfig', DashletConfig)
	.method('saveConfig', function() {
		this.dashletConfig(this._dashletConfig().clone());
		this.view.saveConfig();
	})
	.method('resetConfig', function() {
		this._dashletConfig(this.dashletConfig().clone());
	})
	.property('mode', String)
	// .computed('actionGroupId', _.constant("none"))
	.computed('actionGroupId', function () {
		if (this.dashletConfig().actionGroup()) {
			return this.dashletConfig().actionGroup();
		}
		return _.constant("none")();
    })

	.init(function() {
		this.dashletConfig.subscribe(function() {
			var config = this.resolve('dashletConfig.clone');
			this._dashletConfig(config);
			this.journalsList(config.journalsList());
			this.journal(config.journal() || this.resolve('journalsList.default'));
			this.filter(config.filter());
			this.settings(config.settings());
		}, this);

		this.maxItems(Citeck.mobile.isMobileDevice() || $("body").hasClass("mobile") ? 5 : 10);

		if (!Citeck.mobile.isMobileDevice()) {
	        YAHOO.Bubbling.on("on-change-mobile-mode", function(l, args) {
	            var itemsCount = args[1].mobileMode ? 5 : 10;
	            if (itemsCount != this.maxItems()) {
	                this.setModel({
	                	"maxItems": itemsCount,
	                	"skipCount": 0
	                })
	            };
	        }, this);
	    }
	})
	;

var JournalsDashletWidget = function(htmlid) {
    JournalsDashletWidget.superclass.constructor.call(this, "Citeck.widgets.JournalsDashlet", htmlid, ["button", "menu", "paginator"], JournalsDashlet);
	this.viewModel.view = this;
};

YAHOO.extend(JournalsDashletWidget, Journals, {

	onEditConfig: function() {
		this.viewModel.resetConfig();
		this.viewModel.mode('config');
	},
	
	saveConfig: function() {
		var viewModel = this.viewModel;
		Alfresco.util.Ajax.jsonPost({
			url: Alfresco.constants.URL_SERVICECONTEXT + "modules/dashlet/config/" + this.options.componentId,
			dataObj: {
				journalsListId: viewModel.resolve('journalsList.id', ''),
				journalId: viewModel.resolve('journal.nodeRef', ''),
				filterId: viewModel.resolve('filter.nodeRef', ''),
				settingsId: viewModel.resolve('settings.nodeRef', '')
			},
			successCallback: {
				scope: this,
				fn: function() {
					PopupManager.displayMessage({
						text: this.msg('message.save-settings.success')
					});
				}
			}
		});
	},

});

return JournalsDashletWidget;

})