
define([
    'js/citeck/modules/utils/citeck',
    'ecosui!ecos-records'
], function () {

    var siteDashboardPattern = /^\/share\/page\/site\/([^\/]*)\/dashboard\/?$/;
    var userDashboardPattern = /^\/share\/page\/user\/[^\/]*\/dashboard\/?$/;

    var pageTemplatesToTest = [
        siteDashboardPattern,
        userDashboardPattern,
        /^\/share\/page\/?$/,
        /^\/share\/?$/
    ];

    var extractRecordRef = function (url) {

        if (siteDashboardPattern.test(url)) {
            return "site@" + url.match(siteDashboardPattern)[1];
        }
        return null;
    };

    try {
        let forceOld = Citeck.utils.getURLParameterByName("forceOld") === 'true';

        if (forceOld) {
            return {};
        }

        var isForceOldDashboard;
        var recordRefParam = extractRecordRef(window.location.pathname) || "";
        if (recordRefParam) {
            recordRefParam = "?recordRef=" + recordRefParam;
            isForceOldDashboard = Promise.resolve(false);
        } else {
            isForceOldDashboard = Citeck.Records.get('ecos-config@force-old-user-dashboard-enabled').load('.bool');
        }

        isForceOldDashboard.then(isForceOldDashboard => {

            if (isForceOldDashboard) {
                return;
            }

            let isAnyTemplateMatch = false;
            for (let template of pageTemplatesToTest) {
                if (template.test(window.location.pathname)) {
                    isAnyTemplateMatch = true;
                    break;
                }
            }
            if (!isAnyTemplateMatch) {
                return;
            }

            if (Citeck.newUIRedirectCheckingPerformed) {
                return;
            }
            Citeck.newUIRedirectCheckingPerformed = true;

            Alfresco.util.Ajax.jsonGet({
                url: Alfresco.constants.PROXY_URI + 'citeck/ecos/new-ui-info-get' + recordRefParam,
                successCallback: {
                    fn: function(response) {
                        if (!response || !response.json || !response.json.newUIRedirectUrl) {
                            console.log("Strange response:", response);
                            return;
                        }
                        if (response.json.recordUIType === "react"
                            || (response.json.recordUIType !== "share" && response.json.newUIEnabled)) {

                            window.location.href = response.json.newUIRedirectUrl;
                        }
                    }
                },
                failureCallback: {
                    fn: function(response) {
                        console.error("jsonGet failed. Response: ", response);
                    }
                }
            });
        });

    } catch (e) {
        console.error("[new-ui-redirect.js] Error", e, this);
    }

    return {};
});
