(function() {

    YAHOO.Bubbling.fire("registerAction", { actionName: "onActionDeployBpmProcess",
        fn: function (record) {

            fetch(Alfresco.constants.PROXY_URI + 'citeck/ecos/bpm/process/deploy?nodeRef=' + record.nodeRef, {
                method: 'POST',
                credentials: 'include'
            }).then(function(resp) {

                Alfresco.util.PopupManager.displayMessage({
                    text: Alfresco.util.message('actions.deploy-ecos-bpm-process.success'),
                    displayTime: 5
                });

            }).catch(function(error) {

                Alfresco.util.PopupManager.displayMessage({
                    text: Alfresco.util.message('actions.deploy-ecos-bpm-process.failure'),
                    displayTime: 5
                });

                console.error(error);
            });
        }
    });

})();