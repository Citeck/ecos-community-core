/**
 * Copyright (C) 2005-2013 Alfresco Software Limited.
 *
 * This file is part of Alfresco
 *
 * Alfresco is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Alfresco is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with Alfresco. If not, see <http://www.gnu.org/licenses/>.
 */

/**
 * Document Details Version component.
 *
 * @namespace Alfresco
 * @class Alfresco.DocumentVersions
 */
(function()
{
   /**
    * YUI Library aliases
    */
   var Dom = YAHOO.util.Dom,
      Event = YAHOO.util.Event,
      Selector = YAHOO.util.Selector;

   /**
    * Alfresco Slingshot aliases
    */
   var $html = Alfresco.util.encodeHTML,
      $userProfileLink = Alfresco.util.userProfileLink,
      $userAvatar = Alfresco.Share.userAvatar;

   Citeck.widget.DocumentVersionsMinimalistic = function(htmlId) {
      Citeck.widget.DocumentVersionsMinimalistic.superclass.constructor.call(this, "Citeck.widget.DocumentVersionsMinimalistic", htmlId, ["datasource", "datatable", "paginator", "history", "animation"]);

      YAHOO.Bubbling.on("metadataRefresh", this.doRefresh, this);
      return this;
   };

   YAHOO.extend(Citeck.widget.DocumentVersionsMinimalistic, Alfresco.component.Base,
   {
      /**
       * Object container for initialization options
       *
       * @property options
       * @type {object} object literal
       */
      options: {
         /**
          * Reference to the current document
          *
          * @property nodeRef
          * @type string
          */
         nodeRef: null,

         /**
          * Current siteId, if any.
          *
          * @property siteId
          * @type string
          */
         siteId: "",

         /**
          * The name of container that the node lives in, will be used when uploading new versions.
          *
          * @property containerId
          * @type string
          */
         containerId: null
      },

      /**
       * The latest version of the document
       *
       * @property latestVersion
       * @type {Object}
       */
      latestVersion: null,

      /**
       * A cached copy of the version history to limit duplicate calls.
       * 
       * @property versionCache
       * @type {Object} XHR response object
       */
      versionCache: null,
      
      /**
       * Fired by YUI when parent element is available for scripting
       *
       * @method onReady
       */
      onReady: function() {
          var containerElement = Dom.get(this.id + "-olderVersions");
          if (!containerElement) return;
        
          this.widgets.alfrescoDataTable = new Alfresco.util.DataTable({
            dataSource:{
              url: Alfresco.constants.PROXY_URI + "api/version?nodeRef=" + this.options.nodeRef,
              doBeforeParseData: this.bind(function(oRequest, oFullResponse) {
                  // Versions are returned in an array but must be placed in an object to be able to be parse by yui
                  // Also skip the first version since that is the current version
                  this.latestVersion = oFullResponse.splice(0, 1)[0];
                  Dom.get(this.id + "-currentVersion").innerHTML = this.getDocumentVersionMarkup(this.latestVersion);

                  // Cache the version data for other components (e.g. HistoricPropertiesViewer)
                  this.versionCache = oFullResponse;
                  
                  return ({
                     "data" : oFullResponse
                  });
               })
            },
            dataTable:
            {
               container: this.id + "-olderVersions",
               columnDefinitions: [
                  { key: "version", sortable: false, formatter: this.bind(this.renderCellVersion) }
               ],
               config: {
                  MSG_EMPTY: this.msg("message.noVersions")
               }
            }
         });
         
          // Resize event handler - adjusts the filename container DIV to a size relative to the container width
          Event.addListener(window, "resize", function() { 
            var width = (Dom.getViewportWidth() * 0.25) + "px",
                nodes = YAHOO.util.Selector.query('h3.thin', this.id + "-body");
            for (var i=0; i<nodes.length; i++) {
               nodes[i].style.width = width;
            }
          }, this, true);
      },

      /**
       * Version renderer
       *
       * @method renderCellVersion
       */
      renderCellVersion: function DocumentVersions_renderCellVersions(elCell, oRecord, oColumn, oData) {
         elCell.innerHTML = this.getDocumentVersionMarkup(oRecord.getData());
      },

      /**
       * Builds and returns the markup for a version.
       *
       * @method getDocumentVersionMarkup
       * @param doc {Object} The details for the document
       */
      getDocumentVersionMarkup: function DocumentVersions_getDocumentVersionMarkup(doc) {
        var downloadURL = Alfresco.constants.PROXY_URI + '/api/node/content/' + doc.nodeRef.replace(":/", "") + '/' + doc.name + '?a=true',
            html = '';

        html += '<div class="version-number"><span>' + $html(doc.label) + '</span></div>';
         
        // ACTIONS
        html += '<div class="version-actions">';

        if (this.options.allowNewVersionUpload) {
          html += '<a href="#" name=".onRevertVersionClick" rel="' + doc.label + '" class="' + this.id + ' revert" title="' + this.msg("label.revert") + '">&nbsp;</a>';
        }

        if (doc == this.latestVersion) {
          html += '<a href="#" name=".onUploadNewVersionClick" class="' + this.id + ' new-version" title="' + this.msg("label.newVersion") + '">&nbsp;</a>';
        }

        html += '<a href="' + downloadURL + '" class="download" title="' + this.msg("label.download") + '">&nbsp;</a>';
        html += '<a href="#" name=".onViewHistoricPropertiesClick" rel="' + doc.nodeRef + '" class="' + this.id + ' historicProperties" title="' + this.msg("label.historicProperties") + '">&nbsp;</a>';
        html += '</div>';
        // END ACTIONS

        // comments
        if (doc.description) { 
          html += '<div class="version-comments">' + $html(doc.description, true) + '</div>';
        }

        return html;
      },

      /**
       * Called when a "onRevertVersionClick" link has been clicked for a version.
       * Will display the revert version dialog.
       *
       * @method onRevertVersionClick
       * @param version
       */
      onRevertVersionClick: function(version) {
        // Find the version through the index and display the revert dialog for the version
        Alfresco.module.getRevertVersionInstance().show({
          filename: this.latestVersion.name,
          nodeRef: this.options.nodeRef,
          version: version,
          onRevertVersionComplete: {
            fn: this.onRevertVersionComplete,
            scope: this
          }
        });
      },

      /**
       * Fired by the Revert Version component after a successful revert.
       * Will display a message and reload the page.
       *
       * @method onRevertVersionComplete
       */
      onRevertVersionComplete: function() {
         Alfresco.util.PopupManager.displayMessage({
            text: this.msg("message.revertComplete")
         });

         // MNT-9235: Firing this event, because 'Alfresco.WebPreview' module
         // is isolated and this action modifies content of current node
         // without reloading of the page (ALF-6621)...
         YAHOO.Bubbling.fire("previewChangedEvent");

         // Fire metadatarefresh so components may refresh themselves
         YAHOO.Bubbling.fire("metadataRefresh", {});
      },

      /**
       * Called when a "onViewHistoricPropertiesClick" link has been clicked for a version.
       * Will display the Properties dialogue for that version.
       *
       * @method onViewHistoricPropertiesClick
       * @param version
       */
      onViewHistoricPropertiesClick: function DocumentVersions_onViewHistoricPropertiesClick(nodeRef) {

         // Call the Hictoric Properties Viewer Module
         Alfresco.module.getHistoricPropertiesViewerInstance().show({
            filename: this.latestVersion.name,
            currentNodeRef: this.options.nodeRef,
            latestVersion: this.latestVersion,
            nodeRef: nodeRef
         });

      },
      
      
      
      /**
       * Called when the "onUploadNewVersionClick" link has been clicked.
       * Will display the upload dialog in new version mode.
       *
       * @method onUploadNewVersionClick
       */
      onUploadNewVersionClick: function DocumentVersions_onUploadNewVersionClick()
      {
         if (!this.modules.fileUpload)
         {
            this.modules.fileUpload = Alfresco.getFileUploadInstance();
         }

         var current = this.latestVersion,
            displayName =  current.name,
            extensions = "*";

         if (displayName && new RegExp(/[^\.]+\.[^\.]+/).exec(displayName))
         {
            // Only add a filtering extension if filename contains a name and a suffix
            extensions = "*" + displayName.substring(displayName.lastIndexOf("."));
         }

         this.modules.fileUpload.show(
         {
            siteId: this.options.siteId,
            containerId: this.options.containerId,
            updateNodeRef: this.options.nodeRef,
            updateFilename: displayName,
            updateVersion: current.label,
            overwrite: true,
            suppressRefreshEvent: true,
            filter: [
               {
                  description: this.msg("label.filter-description", displayName),
                  extensions: extensions
               }],
            mode: this.modules.fileUpload.MODE_SINGLE_UPDATE,
            onFileUploadComplete:
            {
               fn: this.onNewVersionUploadComplete,
               scope: this
            }
         });
      },

      /**
       * Called when the upload new version dialog is finished uploading the new version.
       * Will display succes or failure and repload the page if everything went ok.
       *
       * @method onNewVersionUploadComplete
       */
      onNewVersionUploadComplete: function DocumentVersions_onNewVersionUploadComplete(complete)
      {
         if (complete.failed.length == 0 && complete.successful.length > 0)
         {
            // No activities in Repository mode
            if (this.options.siteId != null && this.options.siteId.length != 0)
            {
               try
               {
                  Alfresco.util.Ajax.jsonPost(
                  {
                     url: Alfresco.constants.PROXY_URI + "slingshot/doclib/activity",
                     dataObj:
                     {
                        fileName: complete.successful[0].fileName,
                        nodeRef: complete.successful[0].nodeRef,
                        site: this.options.siteId,
                        type: "file-updated",
                        page: "document-details"
                     }
                  });
               }
               catch (e)
               {
                  // Ignore, not important enough to bother user about
               }
            }

            // ALF-13561 fix, refresh page using correct nodeRef
            YAHOO.lang.later(0, this, function()
            {
               window.location = window.location.href.split("?")[0] + "?nodeRef=" + complete.successful[0].nodeRef;
            });
         }
      },

      /**
       * Refresh component in response to metadataRefresh event
       *
       * @method doRefresh
       */
      doRefresh: function DocumentVersions_doRefresh() {
         YAHOO.Bubbling.unsubscribe("metadataRefresh", this.doRefresh, this);
         this.refresh('citeck/components/document-versions-minimalistic?nodeRef={nodeRef}' + (this.options.siteId ? '&site={siteId}' :  ''));
      }
   });
})();
