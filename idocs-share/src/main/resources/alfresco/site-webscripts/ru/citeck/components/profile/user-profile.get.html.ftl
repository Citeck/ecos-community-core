<#import "/ru/citeck/views/views.lib.ftl" as views />

<#assign el = args.htmlid?html />

<#if view??>
    <@standalone>
        <@markup id="css" >
            <@views.nodeViewStyles />
        </@>

        <@markup id="js">
            <@views.nodeViewScripts />
        </@>

        <@markup id="widgets">
            <@views.nodeViewWidget nodeRef=userRef />
        </@>

        <div id="user-profile-root"></div>

        <script type="text/javascript">//<![CDATA[

        require(['js/citeck/modules/page/user-profile/user-profile'], function(UserProfile) {
            UserProfile.render('user-profile-root', {
                el: "${((el)!"")?js_string}",
                userRef: "${((userRef)!"")?js_string}",
                userName: "${((userName)!"")?js_string}",
                mode: "${((mode)!"")?js_string}",
                <#if mode == "view" && writeMode>
                    canWrite: true,
                </#if>
            });
        });

        //]]></script>
    </@>
</#if>
