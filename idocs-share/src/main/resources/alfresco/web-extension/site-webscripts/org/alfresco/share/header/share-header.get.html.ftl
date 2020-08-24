
<#assign isNewLeftMenuFlag = (isNewReactMenu!false) />

<@markup id="css" >
   <#if config.global.header?? && config.global.header.dependencies?? && config.global.header.dependencies.css??>
      <#list config.global.header.dependencies.css as cssFile>
         <@link href="${url.context}/res${cssFile}" group="header"/>
      </#list>
   </#if>
</@>

<@markup id="js">
   <#if config.global.header?? && config.global.header.dependencies?? && config.global.header.dependencies.js??>
      <#list config.global.header.dependencies.js as jsFile>
         <@script src="${url.context}/res${jsFile}" group="header"/>
      </#list>
   </#if>

   <#if __alf_current_site__?has_content>
      <@inlineScript group="header">
         var __alf_lastsite__ = "${__alf_current_site__}", expirationDate = new Date();
         expirationDate.setFullYear(expirationDate.getFullYear() + 1);
         document.cookie="alf_lastsite=" + __alf_lastsite__ + "; path=/; expires=" + expirationDate.toUTCString() + ";";
      </@>
   </#if>
</@>

<@markup id="widgets">
   <@inlineScript group="dashlets">
      <#if page.url.templateArgs.site??>
         Alfresco.constants.DASHLET_RESIZE = ${siteData.userIsSiteManager?string} && YAHOO.env.ua.mobile === null;
      <#else>
         Alfresco.constants.DASHLET_RESIZE = ${((page.url.templateArgs.userid!"-") = (user.name!""))?string} && YAHOO.env.ua.mobile === null;
      </#if>
   </@>
   <#if isReactMenu>
        <script type="text/javascript">//<![CDATA[

            function getElementHeight(element) {
                var style = window.getComputedStyle(element);
                return element.clientHeight + parseInt(style['margin-top'], 10) + parseInt(style['margin-bottom'], 10);
            }

            function getBaseContainerHeight() {
                var height = [];
                var alfrescoHeader = document.querySelector('#alf-hd');
                if (alfrescoHeader) {
                    height.push(getElementHeight(alfrescoHeader));
                }
                var alfrescoFooter = document.querySelector('#alf-ft');
                if (alfrescoFooter) {
                    height.push(getElementHeight(alfrescoFooter));
                }

                if (!height.length) {
                    return '100%';
                }

                var addPx = function(i) {
                    return i + 'px';
                };

                return 'calc(100vh - (' + height.map(addPx).join(' + ') + '))';
            }

            var legacySiteMenuItems = ${jsonUtils.toJSONString(siteMenuItems)};

            <#if isNewLeftMenuFlag>
                require(['ecosui!header'], function(Header) {
                    Header.render('share-header', {
                        hideSiteMenu: !Array.isArray(legacySiteMenuItems) || !legacySiteMenuItems.length,
                        legacySiteMenuItems
                    }, function () {
                        var basePageContainer = document.createElement('div');
                        basePageContainer.classList.add('ecos-base-page');
                        basePageContainer.style.height = getBaseContainerHeight();

                        var slideMenuContainer = document.createElement('div');
                        slideMenuContainer.setAttribute('id', 'slide-menu');

                        var bd = document.querySelector('#bd');
                        bd.classList.add('ecos-main-area');

                        var alfHd = document.querySelector('#alf-hd');

                        alfHd.after(basePageContainer);
                        basePageContainer.prepend(slideMenuContainer);
                        basePageContainer.appendChild(bd);

                        require([
                            'ecosui!slide-menu'
                        ], function (SlideMenu) {
                            SlideMenu.render('slide-menu');
                        });
                    });
                });
            <#else>
                require([
                    'ecosui!header-legacy'
                ], function(ShareHeader) {
                    ShareHeader.render('share-header', {
                        userName: "${((user.name)!"")?js_string}",
                        userFullname: "${((user.fullName)!"")?js_string}",
                        userNodeRef: "${((user.properties.nodeRef)!"")?js_string}",
                        userIsAvailable: "${((user.properties.available)!"")?string}",
                        userIsMutable: "${((user.capabilities.isMutable)!"")?string}",
                        isExternalAuthentication: "${((context.externalAuthentication)!"")?string}",
                        siteMenuItems: legacySiteMenuItems,
                        isCascadeCreateMenu: "${isCascadeCreateMenu?string}"
                    });
                });
                require([
                    'ecosui!slide-menu-legacy'
                ], function(SlideMenu) {
                    SlideMenu.render('slide-menu');
                });
            </#if>
        //]]></script>
   <#else>
      <@processJsonModel group="share"/>
   </#if>
</@>

<@markup id="html">
   <div id="share-header"></div>
<#if isNewLeftMenuFlag == false>
   <div id="slide-menu"></div>
</#if>
</@>
