/**
 * CommenstList component.
 *
 * Displays a list of comments and a editor for creating new ones.
 *
 * @namespace Alfresco
 * @class Alfresco.CommentsList
 */
(function()
{

   /**
   * YUI Library aliases
   */
   var Dom = YAHOO.util.Dom,
      Selector = YAHOO.util.Selector;

    /**
    * Alfresco Slingshot aliases
    */
   var $html = Alfresco.util.encodeHTML,
      $userProfileLink = Alfresco.util.userProfileLink,
      $userAvatar = Alfresco.Share.userAvatar;

   /**
    * CommentsList constructor.
    *
    * @param {String} htmlId The HTML id of the parent element
    * @return {Alfresco.CommentsList} The new Comment instance
    * @constructor
    */
   Alfresco.CommentsList = function Alfresco_CommentsList(htmlId)
   {
      Alfresco.CommentsList.superclass.constructor.call(this, "Alfresco.CommentsList", htmlId, ["datasource", "datatable", "paginator", "history", "animation"]);

      YAHOO.Bubbling.on("editorInitialized", this.onEditorInitialized, this);
      YAHOO.Bubbling.on("commentNode", this.onCommentNode, this);
	  YAHOO.Bubbling.on("versionReverted", function(){ this.widgets.alfrescoDataTable.reloadDataTable(); }, this);

      this.busy = false;
      this.hashChecked = false;
      return this;
   };

   YAHOO.extend(Alfresco.CommentsList, Alfresco.component.Base,
   {
      /**
       * Object container for initialization options
       *
       * @property options
       * @type object
       */
      options:
      {
         /**
          * Reference to the node to list and add comments for
          *
          * @property nodeRef
          * @type string
          */
         nodeRef: null,

         /**
          * Current siteId.
          *
          * @property siteId
          * @type string
          */
         siteId: null,

         /**
          * The activity parameters if any
          *
          * @property activity
          * @type object
          */
         activity: null,

         /**
          * Config parameters to pass into the editor.
          *
          * @property editorConfig
          * @type Object
          */
         editorConfig: {}
      },

      /**
       * Tells whether an action is currently ongoing.
       *
       * @property busy
       * @type boolean
       * @see _setBusy/_releaseBusy
       */
      busy: null,

      /**
       * Flag indicating location.hash has been checked
       *
       * @property hashChecked
       * @type boolean
       */
      hashChecked: null,

      /**
       * Fired by YUI when parent element is available for scripting.
       * Component initialisation, including instantiation of YUI widgets and event listener binding.
       *
       * @method onReady
       */
      onReady: function CommentList_onReady()
      {
         var editFormWrapper = document.createElement("div");
         Dom.addClass(editFormWrapper, "comments-list");
         Dom.addClass(editFormWrapper, "hidden");
         Dom.get(this.id + "-body").appendChild(editFormWrapper);
         this.widgets.editFormWrapper = editFormWrapper;

         YAHOO.util.Event.addListener(window, "resize", function ()
         {
            if (this.currentEditedRowId)
            {
               this.synchronizeElements(this.widgets.editFormWrapper, this.currentEditedRowId + "-form-container");
            }
         }, this, true);

         this.setupCommentList();
         this.setupAddCommentForm();
      },

      /**
       * Sets up the datatable to list comments
       *
       * @method setupCommentList
       */
      setupCommentList: function CommentsList_setupCommentList()
      {
         var url = Alfresco.constants.URL_SERVICECONTEXT + "components/node/" + this.options.nodeRef.replace(":/", "") + "/comments?reverse=true";
         this.widgets.alfrescoDataTable = new Alfresco.util.DataTable(
         {
            dataSource:
            {
               url: url,
               pagingResolver: function (currentSkipCount, currentMaxItems)
               {
                  // Comment webscript uses other pagination parameters than the default setting
                  return "startIndex=" + currentSkipCount + "&" + "pageSize=" + currentMaxItems;
               },
               config:
               {
                  responseSchema:
                  {
                     // result list & pagination response attributes are not using standard pattern
                     resultsList: "items",
                     metaFields:
                     {
                        paginationRecordOffset: "startIndex",
                        paginationRowsPerPage: "pageSize",
                        totalRecords: "total"
                     }
                  }
               },
               doBeforeParseData: this.bind(this.handlePermissions)
            },
            dataTable:
            {
               container: this.id + "-comments-list",
               columnDefinitions:
               [
                  { key: "comment", sortable: false, formatter: this.bind(this.renderCellComment) }
               ],
               config:
               {
                  MSG_EMPTY: this.msg("message.noComments")
               }
            },
            paginator:
            {
               history: false, // We don't want need each pagination to be added to the browser history
               config:
               {
                  containers: [ this.id + "-paginator-top", this.id + "-paginator-bottom" ],
                  rowsPerPage: this.options.maxItems
               }
            }
         });

         // Display the hr lines once the data has been loaded
         this.widgets.alfrescoDataTable.getDataTable().subscribe("beforeRenderEvent", function()
         {
            Dom.removeClass(Selector.query("hr.hidden"), "hidden");
         }, this, this);
      },

      /**
       * Update UI to reflect current user's permissions
       *
       * @method handlePermissions
       */
      handlePermissions: function CommentsList_handlePermissions(oRequest, oFullResponse)
      {
         // Examine the user permissions on the parent node
         var permissions = oFullResponse.nodePermissions || {};
         if (permissions["create"])
         {
            Dom.removeClass(this.id + "-actions", "hidden");
         }

         // Return response unmodified
         return oFullResponse;
      },

      /**
       * Sets up the form for adding comments
       *
       * @method setupAddCommentForm
       */
      setupAddCommentForm: function CommentsList_setupAddCommentForm()
      {
         Dom.get(this.id + '-add-form-container').innerHTML = this.formMarkup(this.id + '-add', Alfresco.constants.USERNAME, null);
         this.widgets.addCommentEditor = this.setupCommentForm(this.id + '-add', this.options.nodeRef, false).editor;
      },

      /**
       * Sets up a form for a given domid
       *
       * @method setupCommentForm
       * @param rowId {string} The dom id in which the comment widgets are placed
       * @param nodeRef {string} The comments id/nodeRef
       * @param editMode {string} Set to true oif form shall be rendered in edit mode, false is create mode
       */
      setupCommentForm: function CommentsList_setupCommentForm(rowId, nodeRef, editMode)
      {
         var formId = rowId + '-form',
            formContainer = Dom.get(rowId + '-form-container'),
            url;
         if (editMode)
         {
            url = 'api/comment/node/' + nodeRef.replace(':/', '');
         }
         else
         {
            url = 'api/node/' + nodeRef.replace(':/', '') + '/comments';
         }
         Dom.get(formId).setAttribute("action", Alfresco.constants.PROXY_URI + url);

         // register the submitButton
         var submitButton = new YAHOO.widget.Button(rowId + "-submit",
         {
            type: "submit"
         });
         submitButton.set("label", this.msg(editMode ? 'button.save' : 'button.addComment'));

         // register the cancel button
         var cancelButton = new YAHOO.widget.Button(rowId + "-cancel");
         cancelButton.subscribe("click", function()
         {
            if (this.widgets.commentForm != null)
            {
               this.widgets.commentForm.hideErrorContainer();
            }
            this.restoreEditForm();
         }, this, true);
         cancelButton.set("label", this.msg('button.cancel'));

         // instantiate the simple editor we use for the form
         var editor = new Alfresco.util.RichEditor(Alfresco.constants.HTML_EDITOR, rowId + '-content', this.options.editorConfig);
         this.widgets.editor = editor;
         editor.addPageUnloadBehaviour(this.msg("message.unsavedChanges.comment"));
         editor.render();

         // Add validation to the rich text editor
         var keyUpIdentifier = (Alfresco.constants.HTML_EDITOR === 'YAHOO.widget.SimpleEditor') ? 'editorKeyUp' : 'onKeyUp';
         this.widgets.editor.subscribe(keyUpIdentifier, function (e)
         {
            /**
             * Doing a form validation on every key stroke is process consuming, below we try to make sure we only do
             * a form validation if it's necessarry.
             * NOTE: Don't check for zero-length in commentsLength, due to HTML <br>, <span> tags, etc. possibly
             * being present. Only a "Select all" followed by delete will clean all tags, otherwise leftovers will
             * be there even if the form looks empty.
             */
            if (this.widgets.editor.getContent().length < 20 || !this.widgets.commentForm.isValid())
            {
               // Submit was disabled and something has been typed, validate and submit will be enabled
               this.widgets.editor.save();
               this.widgets.commentForm.validate()
            }
         }, this, true);

         // This IE specific code addresses ALF-11666. Without this code it is not possible to paste and submit
         // a comment. The problem only occurred on the first post after loading the page, but this solution ensures
         // that the paste and post will always work. Similar behaviour has been noticed on Chrome and Firefox but
         // a second click of the post button will always successfully post the comment.
         if (YAHOO.env.ua.ie > 0)
         {
            editor.subscribe("onPaste", function(ed, e)
            {
               editor.setContent("work around");
               editor.save();
            });
         }

         // create the form that does the validation/submit
         if (this.widgets.commentForm != null)
         {
            this.widgets.commentForm.hideErrorContainer();
         }
         var commentForm = new Alfresco.forms.Form(formId);
         this.widgets.commentForm = commentForm;
         commentForm.addValidation(rowId + "-content", Alfresco.forms.validation.mandatory, null);
         commentForm.setSubmitElements(submitButton);
         commentForm.setAjaxSubmitMethod(editMode ? Alfresco.util.Ajax.PUT : Alfresco.util.Ajax.POST);
         commentForm.setAJAXSubmit(true,
         {
            successCallback:
            {
               fn: function CommentsList_success(response, args)
               {
                  this.restoreEditForm();
                  Dom.addClass(formContainer, "hidden");
                  this._releaseBusy();
                  this.widgets.alfrescoDataTable.reloadDataTable();
                  cancelButton.set("disabled", false);
               },
               scope: this
            },
            failureMessage: this.msg("message.savecomment.failure"),
            failureCallback:
            {
               fn: function CommentsList_success(response, args)
               {
                  this._releaseBusy();
                  cancelButton.set("disabled", false);
               },
               scope: this
            }
         });
         commentForm.setSubmitAsJSON(true);
         commentForm.doBeforeFormSubmit =
         {
            fn: function(form)
            {
               this._setBusy(this.msg("message.wait"));
               cancelButton.set("disabled", true);
               // this.widgets.editor.disable();
               // Make sure the editors content is saved down to the form
               editor.save();
               editor.getEditor().undoManager.clear();
               editor.getEditor().nodeChanged();
            },
            scope: this
         };
         commentForm.doBeforeAjaxRequest =
         {
            fn: function(config, obj)
            {
               if (this.options.activity)
               {
                  config.dataObj.itemTitle = this.options.activity.itemTitle;
                  config.dataObj.page = this.options.activity.page;
                  config.dataObj.pageParams = YAHOO.lang.JSON.stringify(this.options.activity.pageParams);
               }
               return true;
            },
            scope: this
         };
         commentForm.init();
         return {
            form: commentForm,
            editor: editor
         }
      },

      /**
       * Restore UI after an edit operation
       *
       * @method restoreEditForm
       */
      restoreEditForm: function CommentsList_restoreEditForm()
      {
         if (this.currentEditedRowId)
         {
            // Restore the currently opened row
            var formContainer = Dom.get(this.currentEditedRowId + "-form-container"),
               commentContainer = Dom.get(this.currentEditedRowId + "-comment-container");
            if (formContainer && commentContainer)
            {
               // Hide form and display comment again
               Dom.removeClass(formContainer.parentNode, "theme-bg-color-4");
               Dom.addClass(formContainer, "hidden");
               Dom.removeClass(commentContainer, "hidden");
               Dom.addClass(this.widgets.editFormWrapper, "hidden");
            }
         }
         Dom.addClass(this.id + "-add-form-container", "hidden");
         Dom.removeClass(this.widgets.onAddCommentClick.get("element"), "hidden");
      },

      /**
       * Renders a comment inside a cell
       *
       * @method renderCellComment
       */
      renderCellComment: function CommentsList_renderCellComment(elCell, oRecord, oColumn, oData)
      {
         // todo: Move this to use js templating when we have it
         var data = oRecord.getData(),
            html = '',
            rowId = this.id + '-' + oRecord.getId(),
            permissions = data.permissions;

         // Display comment
         html += '<div id="' + rowId + '-comment-container" class="comment-details">';
         html += '   <div class="icon">' + $userAvatar(data.author.username) + '</div>';
         html += '   <div class="details">';
         html += '      <span class="info">';
         html += $userProfileLink(data.author.username, data.author.firstName + ' ' + data.author.lastName, 'class="theme-color-1"') + ' ';
         html += Alfresco.util.relativeTime(Alfresco.util.fromISO8601(data.modifiedOnISO)) + '<br/>';
         html += '      </span>';
         html += '      <span class="comment-actions">';
         if (permissions["edit"])
         {
            html += '       <a href="#" name=".onEditCommentClick" rel="' + oRecord.getId() + '" title="' + this.msg("link.editComment") + '" class="' + this.id + ' edit-comment">&nbsp;</a>';
         }
         if (permissions["delete"])
         {
            html += '       <a href="#" name=".onConfirmDeleteCommentClick" rel="' + oRecord.getId() + '" title="' + this.msg("link.deleteComment") + '" class="' + this.id + ' delete-comment">&nbsp;</a>';
         }
         html += '      </span>';
         html += '      <div class="comment-content">' + (data.content || "") + '</div>';
         html += '   </div>';
         html += '   <div class="clear"></div>';
         html += '</div>';
         html += '<div id="' + rowId + '-form-container" class="comment-form hidden">';
         html += '   &nbsp;<!-- EMPTY SPACE FOR FLOATING COMMENT FORM -->';
         html += '</div>';

         // Note! we will initialize form when somebody clicks edit
         elCell.innerHTML = html;
      },

      /**
       * Creates and returns the markup for a comment form
       *
       * @method formMarkup
       * @param rowId {string} Uniqiue dom id
       * @param userName {string} The username whose avatar should be displayed
       * @param comment {string} The actual comment
       */
      formMarkup: function CommentsList_formMarkup(rowId, userName, comment)
      {
         // todo: Move this to use js templating when we have it
         var html = '';
         html += '<div id="' + rowId + '-actual-form-container" class="comment-form">';
         html += '   <h2 class="thin dark">' + this.msg(comment ? "header.edit" : "header.add") + '</h2>';
         html += $userAvatar(userName);
         html += '   <form id="' + rowId + '-form" method="POST" action="">';
         if (this.options.siteId)
         {
            html += '      <input type="hidden" name="site" value="' + this.options.siteId + '" />';
         }
         html += '      <textarea name="content" id="' + rowId + '-content" style="width: 100%">' + (comment || '') + '</textarea>';
         html += '      <div class="buttons">';
         html += '         <input type="submit" id="' + rowId + '-submit" value=""/>';
         html += '         <input type="reset"  id="' + rowId + '-cancel" value="" />';
         html += '      </div>';
         html += '   </form>';
         html += '   <div class="clear"></div>';
         html += '</div>';

         return html;
      },

      /**
       * Event handler called once editor is ready for use
       *
       * @method onEditorInitialized
       */
      onEditorInitialized: function CommentsList_onEditorInitialized()
      {
         if (!this.hashChecked && window.location.hash == "#comment")
         {
            this.hashChecked = true;
            // Ensure comments form is visible and in view
            this.onAddCommentClick(true);
            Dom.get(this.id + "-add-comment").scrollIntoView();
         }
      },

      /**
       * Event handler called when another component wants to comment a nodeRef
       *
       * @method onCommentNode
       */
      onCommentNode: function CommentsList_onCommentNode(event, args)
      {
         if (this.options.nodeRef == args[1])
         {
            // Ensure comments form is visible and in view
            this.onAddCommentClick();
            Dom.get(this.id + "-add-comment").scrollIntoView();
         }
      },

      /**
       * Called when the "onAddCommentClick" button has been clicked.
       * Will display the add comment form.
       *
       * @method onAddCommentClick
       */
      onAddCommentClick: function CommentsList_onAddCommentClick(hackArg)
      {
         this.restoreEditForm();

         // This is a quite unashamed hack that has been added to solve the issues raised
         // by ALF-18688. The forms runtime validation has tried to be applied in this
         // context and is not working properly between editing/adding/cancelling comments.
         // In order to resolve the issues this was the only fix that was found after several
         // hours of investigation. "hackArg" is only passed from "onEditorInitialized" function
         // which ensures the form renders correctly on page load, but without the hack the call
         // to "setupAddCommentForm" ensures posts are successful when adding a new comment after
         // editing an existing one.
         if (hackArg == null)
         {
            this.setupAddCommentForm();
         }
         Dom.addClass(this.widgets.onAddCommentClick.get("element"), "hidden");
         this.widgets.addCommentEditor.setContent("");
         this.widgets.addCommentEditor.save();
         Dom.removeClass(this.id + "-add-form-container", "hidden");
         this.widgets.addCommentEditor.focus();
      },

      /**
       * Called when the "onEditCommentClick" button was clicked.
       * Will display an inline edit comment form.
       *
       * @method onEditCommentClick
       * @param recordId
       */
      onEditCommentClick: function CommentsList_onEditCommentClick(recordId)
      {
         // Hide previously opened form and restore row
         this.restoreEditForm();

         var comment =  this.widgets.alfrescoDataTable.getData(recordId),
            rowId = this.id + '-' + recordId,
            formContainer = Dom.get(rowId + '-form-container'),
            commentEl = Dom.get(rowId + '-comment-container');

         this.currentEditedRowId = rowId;

         // Hide the row and display the empty form container
         Dom.addClass(formContainer.parentNode, "theme-bg-color-4");
         Dom.addClass(commentEl, "hidden");
         Dom.removeClass(formContainer, "hidden");

         // Create form markup inside the absolute positioned div
         this.widgets.editFormWrapper.innerHTML = this.formMarkup(rowId, comment.author.username, comment.content);

         // Initialize form with editor
         this.setupCommentForm(rowId, comment.nodeRef, true);

         // make sure the new form is placed above the empty form placeholder in the datatable
         this.synchronizeElements(this.widgets.editFormWrapper, formContainer);

         // Display the form
         Dom.removeClass(this.widgets.editFormWrapper, "hidden");
      },

      /**
       * Helper function to position DOM elements
       *
       * @method synchronizeElements
       */
      synchronizeElements: function synchronizeElements(syncEl, sourceEl)
      {
         var sourceYuiEl = new YAHOO.util.Element(sourceEl),
            syncYuiEl = new YAHOO.util.Element(syncEl),
            region = YAHOO.util.Dom.getRegion(sourceYuiEl.get("id")),
            scrollElem = document.getElementById("bd");

         var yOffset = 0,
             xOffset = 0;

         if (scrollElem) {
             var scrollElemRegion = YAHOO.util.Dom.getRegion(scrollElem);
             yOffset = scrollElem.scrollTop - scrollElemRegion.top;
             xOffset = -scrollElemRegion.left;
         }
         syncYuiEl.setStyle("position", "absolute");
         syncYuiEl.setStyle("left", xOffset + region.left + "px");
         syncYuiEl.setStyle("top", yOffset + region.top + "px");
         syncYuiEl.setStyle("width", region.width + "px");
         syncYuiEl.setStyle("height", region.height + "px");
      },

      /**
       * Called when the "onConfirmDeleteCommentClick" link was clicked.
       * Will display a confirmation dialog befpre deleting the comment.
       *
       * @method onConfirmDeleteCommentClick
       * @param recordId
       */
      onConfirmDeleteCommentClick: function CommentsList_onConfirmDeleteCommentClick(recordId)
      {
         var comment =  this.widgets.alfrescoDataTable.getData(recordId),
            me = this;
         Alfresco.util.PopupManager.displayPrompt(
         {
            title: this.msg("message.confirm.delete.title"),
            text: this.msg("message.confirm.delete"),
            buttons: [
            {
               text: this.msg("button.delete"),
               handler: function CommentsList_onConfirmDeleteCommentClick_delete()
               {
                  this.destroy();
                  me.deleteComment.call(me, comment);
               }
            },
            {
               text: this.msg("button.cancel"),
               handler: function CommentsList_onConfirmDeleteCommentClick_cancel()
               {
                  this.destroy();
               },
               isDefault: true
            }]
         });
      },

      /**
       * Will delete the comment.
       *
       * @method deleteComment
       */
      deleteComment: function CommentsList_deleteComment(comment)
      {
			 var self = this;
         // show busy message
         if (!this._setBusy(this.msg('message.wait')))
         {
            return;
         }


         // ajax request success handler
         var success = function CommentsList_deleteComment_success(response, object)
         {
            // remove busy message
            this._releaseBusy();
            if (this.widgets.alfrescoDataTable.lastResultCount == 1 &&
                this.widgets.alfrescoDataTable.currentSkipCount > 0)
            {
               var adt = this.widgets.alfrescoDataTable;
               adt.currentSkipCount = adt.currentSkipCount - adt.currentMaxItems;
            }

			 // show delete success message
            this.widgets.displayMessage = Alfresco.util.PopupManager.displayMessage({
              text: self.msg("message.delete.success"),
              displayTime: 0
            });

            setTimeout(function() {
              if (self.widgets.displayMessage) {
                self.widgets.displayMessage.destroy();
              }
            }, 1500)
            this.widgets.alfrescoDataTable.reloadDataTable();
         };

         // ajax request failure handler
         var failure = function CommentsList_deleteComment_failure(response, object)
         {
            // remove busy message
            this._releaseBusy();

            // show delete failure message
            this.widgets.displayMessage = Alfresco.util.PopupManager.displayMessage({
              text: self.msg("message.delete.failure"),
              displayTime: 0
            });

            setTimeout(function() {
              if (self.widgets.displayMessage) {
                self.widgets.displayMessage.destroy();
              }
            }, 2000)
         };

         // put together the request url to delete the comment
         var url = Alfresco.constants.PROXY_URI + "api/comment/node/" + comment.nodeRef.replace(":/", ""),
            params = false;
         if (this.options.siteId)
         {
            url += params ? "&" : "?";
            url += "site=" + encodeURIComponent(this.options.siteId);
            params = true;
         }
         if (this.options.activity)
         {
            url += params ? "&" : "?";
            url += "itemTitle=" + encodeURIComponent(this.options.activity.itemTitle) + "&";
            url += "page=" + encodeURIComponent(this.options.activity.page) + "&";
            url += "pageParams=" + encodeURIComponent(YAHOO.lang.JSON.stringify(this.options.activity.pageParams));
            params = true;
         }

         // execute ajax request
         Alfresco.util.Ajax.jsonDelete(
         {
            url: url,
 //           successMessage: this.msg("message.delete.success"),
            successCallback:
            {
               fn: success,
               scope: this
            },
  //          failureMessage: this.msg("message.delete.failure"),
            failureCallback:
            {
               fn: failure,
               scope: this
            }
         });
      },


      /**
       * Displays the provided busyMessage but only in case
       * the component isn't busy set.
       *
       * @method _setBusy
       * @protected
       * @return true if the busy state was set, false if the component is already busy
       */
      _setBusy: function CommentsList__setBusy(busyMessage)
      {
         if (this.busy)
         {
            return false;
         }
         this.busy = true;
         this.widgets.busyMessage = Alfresco.util.PopupManager.displayMessage(
         {
            text: busyMessage,
            spanClass: "wait",
            displayTime: 0
         });
         return true;
      },

      /**
       * Removes the busy message and marks the component as non-busy
       *
       * @method _releaseBusy
       * @protected
       */
      _releaseBusy: function CommentsList__releaseBusy()
      {
         if (this.busy)
         {
            this.widgets.busyMessage.destroy();
            this.busy = false;
            return true;
         }
         else
         {
            return false;
         }
      }

   });
})();
