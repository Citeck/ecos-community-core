function extractMetadata(file) {
    // Extract metadata - via repository action for now.
    // This should use the MetadataExtracter API to fetch properties, allowing for possible failures.
    var emAction = actions.create("extract-metadata");
    if (emAction != null) {
        // Call using readOnly = false, newTransaction = false
        emAction.execute(file, false, false);
    }
}

function exitUpload(statusCode, statusMsg) {
    status.code = statusCode;
    status.message = statusMsg;
    status.redirect = true;
}

function getTempFileName(filename, counter) {
    var dotIndex = filename.lastIndexOf(".");
    if (dotIndex == -1) {
        return filename + "-" + counter;
    }
    if (dotIndex == 0) {
        return counter + filename;
    }

    return filename.substring(0, dotIndex) + "-" + counter + filename.substring(dotIndex);
}

function getNewFile(contentType, destNode, formdata) {
    filename = formdata.fields[1].value;
    if (contentType === null) {
        return destNode.createFile(filename);
    }
    return destNode.createFile(filename, contentType);
}

function getNewFileName(oldFullName, newFullName) {

    if (oldFullName == newFullName) {
        return oldFullName;
    }

    var lastPointIndexOld = oldFullName.lastIndexOf('.');
    var oldName;
    var oldExtension = '';
    if (lastPointIndexOld > 0) {
        oldName = oldFullName.substring(0, lastPointIndexOld);
        oldExtension = oldFullName.substring(lastPointIndexOld + 1);
    } else {
        oldName = oldFullName;
    }

    var lastPointIndexNew = newFullName.lastIndexOf('.');
    var newExtension = oldExtension;
    if (lastPointIndexNew > 0) {
        newExtension = newFullName.substring(lastPointIndexNew + 1);
    }

    return oldName + (newExtension ? "." + newExtension : '');
}

function main() {
    try {
        var filename = null,
            content = null,
            mimetype = null,
            siteId = null, site = null,
            containerId = null, container = null,
            destination = null,
            destNode = null,
            thumbnailNames = null;

        // Upload specific
        var uploadDirectory = null,
            contentType = null,
            aspects = [],
            overwrite = true; // If a filename clashes for a versionable file

        // Update specific
        var updateNodeRef = null,
            majorVersion = false,
            description = "";

        // Prevents Flash- and IE8-sourced "null" values being set for those parameters where they are invalid.
        // Note: DON'T use a "!==" comparison for "null" here.
        var fnFieldValue = function (p_field) {
            return p_field.value.length() > 0 && p_field.value != "null" ? p_field.value : null;
        };

        // allow the locale to be set via an argument
        if (args["lang"] != null) {
            utils.setLocale(args["lang"]);
        }

        var uploadConfig = new XML(config.script),
            autoVersion = uploadConfig.autoVersion.toString() == "" ? null : uploadConfig.autoVersion.toString() == "true",
            autoVersionProps = uploadConfig.autoVersionProps.toString() == "" ? null : uploadConfig.autoVersionProps.toString() == "true";

        //MNT-7213 When alf_data runs out of disk space, Share uploads result in a success message, but the files do not appear
        if (formdata.fields.length == 0) {
            exitUpload(404, " No disk space available");
            return;
        }

        // Parse file attributes
        for each(field in formdata.fields) {
            switch (String(field.name).toLowerCase()) {
                case "filename": {
                    filename = fnFieldValue(field);
                    break;
                }
                case "filedata": {
                    if (field.isFile) {
                        filename = filename ? filename : field.filename;
                        content = field.content;
                        mimetype = field.mimetype;
                    }
                    break;
                }
                case "siteid": {
                    siteId = fnFieldValue(field);
                    break;
                }
                case "containerid": {
                    containerId = fnFieldValue(field);
                    break;
                }
                case "destination": {
                    destination = fnFieldValue(field);
                    break;
                }
                case "uploaddirectory": {
                    uploadDirectory = fnFieldValue(field);
                    if ((uploadDirectory !== null) && (uploadDirectory.length() > 0)) {
                        if (uploadDirectory.charAt(uploadDirectory.length() - 1) != "/") {
                            uploadDirectory = uploadDirectory + "/";
                        }
                        // Remove any leading "/" from the uploadDirectory
                        if (uploadDirectory.charAt(0) == "/") {
                            uploadDirectory = uploadDirectory.substr(1);
                        }
                    }
                    break;
                }
                case "updatenoderef": {
                    updateNodeRef = fnFieldValue(field);
                    break;
                }
                case "description": {
                    description = field.value;
                    break;
                }
                case "contenttype": {
                    contentType = field.value;
                    break;
                }
                case "aspects": {
                    aspects = field.value != "-" ? field.value.split(",") : [];
                    break;
                }
                case "majorversion": {
                    majorVersion = field.value == "true";
                    break;
                }
                case "overwrite": {
                    overwrite = field.value == "true";
                    break;
                }
                case "thumbnails": {
                    thumbnailNames = field.value;
                    break;
                }
            }
        }

        // Ensure mandatory file attributes have been located. Need either destination, or site + container or updateNodeRef
        if ((filename === null || content === null) || (destination === null && (siteId === null || containerId === null) && updateNodeRef === null)) {
            exitUpload(400, "Required parameters are missing");
            return;
        }

        /**
         * Site or Non-site?
         */
        if (siteId !== null && siteId.length() > 0) {
            /**
             * Site mode.
             * Need valid site and container. Try to create container if it doesn't exist.
             */
            site = siteService.getSite(siteId);
            if (site === null) {
                exitUpload(404, "Site (" + siteId + ") not found.");
                return;
            }

            container = site.getContainer(containerId);
            if (container === null) {
                try {
                    // Create container since it didn't exist
                    container = site.createContainer(containerId);
                } catch (e) {
                    // Error could be that it already exists (was created exactly after our previous check) but also something else
                    container = site.getContainer(containerId);
                    if (container === null) {
                        // Container still doesn't exist, then re-throw error
                        throw e;
                    }
                    // Since the container now exists we can proceed as usual
                }
            }

            if (container === null) {
                exitUpload(404, "Component container (" + containerId + ") not found.");
                return;
            }

            destNode = container;
        } else if (destination !== null) {
            /**
             * Non-Site mode.
             * Need valid destination nodeRef.
             */
            destNode = search.findNode(destination);
            if (destNode === null) {
                exitUpload(404, "Destination (" + destination + ") not found.");
                return;
            }
        }

        /**
         * Update existing or Upload new?
         */
        if (updateNodeRef !== null) {
            /**
             * Update existing file specified in updateNodeRef
             */
            if (updateNodeRef.startsWith("alfresco/@")){
                updateNodeRef = updateNodeRef.replace("alfresco/@", "");
            }
            var updateNode = search.findNode(updateNodeRef);
            if (updateNode === null) {
                exitUpload(404, "Node specified by updateNodeRef (" + updateNodeRef + ") not found.");
                return;
            }

            var oldName = updateNode.name;
            var workingcopy = updateNode.hasAspect("cm:workingcopy");
            if (!workingcopy && updateNode.isLocked) {
                // We cannot update a locked document (except working copy as per MNT-8736)
                exitUpload(404, "Cannot update locked document '" + updateNodeRef + "', supply a reference to its working copy instead.");
                return;
            }

            if (!workingcopy) {
                // Ensure the file is versionable (autoVersion and autoVersionProps read from config)
                if (autoVersion != null && autoVersionProps != null) {
                    updateNode.ensureVersioningEnabled(autoVersion, autoVersionProps);
                } else {
                    updateNode.ensureVersioningEnabled();
                }

                // It's not a working copy, do a check out to get the actual working copy
                updateNode = updateNode.checkoutForUpload();
            }

            // Update the working copy content
            updateNode.properties.content.write(content, false, true);
            updateNode.properties.content.guessMimetype(filename);
            var newName = getNewFileName(oldName, formdata.fields[1].value);
            if (oldName != newName) {
                updateNode.name = newName;
            }
            // check it in again, with supplied version history note

            // Extract the metadata
            // (The overwrite policy controls which if any parts of
            //  the document's properties are updated from this)
            extractMetadata(updateNode);

            updateNode = updateNode.checkin(description, majorVersion);
            if (aspects.length != 0) {
                for (var i = 0; i < aspects.length; i++) {
                    if (!updateNode.hasAspect(aspects[i])) {
                        updateNode.addAspect(aspects[i]);
                    }
                }
            }

            // Record the file details ready for generating the response
            model.document = updateNode;
            return;
        }

        /**
         * Upload new file to destNode (calculated earlier) + optional subdirectory
         */
        if (uploadDirectory !== null && uploadDirectory.length > 0) {
            var child = destNode.childByNamePath(uploadDirectory);
            if (child === null) {
                exitUpload(404, "Cannot upload file since upload directory '" + uploadDirectory + "' does not exist.");
                return;
            }

            // MNT-12565
            while (child.isDocument) {
                if (child.parent === null) {
                    exitUpload(404, "Cannot upload file. You do not have permissions to access the parent folder for the document.");
                    return;
                }
                child = child.parent;
            }

            destNode = child;
        }

        /**
         * Existing file handling.
         */
        var existingFile = destNode.childByNamePath(filename);
        if (existingFile !== null) {
            // File already exists, decide what to do
            if (existingFile.hasAspect("cm:versionable") && overwrite) {
                // Upload component was configured to overwrite files if name clashes
                existingFile.properties.content.write(content, false, true);
                existingFile.properties.content.guessMimetype(filename);

                // Extract the metadata
                // (The overwrite policy controls which if any parts of
                //  the document's properties are updated from this)
                extractMetadata(existingFile);

                // Record the file details ready for generating the response
                model.document = existingFile;

                // MNT-8745 fix: Do not clean formdata temp files to allow for retries. Temp files will be deleted later when GC call DiskFileItem#finalize() method or by temp file cleaner.
                return;
            } else {
                // Upload component was configured to find a new unique name for clashing filenames
                var counter = 1,
                    tmpFilename;

                while (existingFile !== null) {
                    tmpFilename = getTempFileName(filename, counter);

                    existingFile = destNode.childByNamePath(tmpFilename);
                    counter++;
                }
                filename = tmpFilename;
            }
        }

        /**
         * Create a new file.
         */
        var newFile = getNewFile(contentType, destNode);
        // Use the appropriate write() method so that the mimetype already guessed from the original filename is
        // maintained - as upload may have been via Flash - which always sends binary mimetype and would overwrite it.
        // Also perform the encoding guess step in the write() method to save an additional Writer operation.
        newFile.properties.content.write(content, false, true);
        newFile.properties.content.guessMimetype(filename);
        newFile.save();


        // NOTE: Removal of first request for thumbnails to improve upload performance
        //       Thumbnails are still requested by Share on first render of the doclist image.

        // Additional aspects?
        if (aspects.length > 0) {
            for (var i = 0; i < aspects.length; i++) {
                newFile.addAspect(aspects[i]);
            }
        }

        // Extract the metadata
        extractMetadata(newFile);

        // TODO (THOR-175) - review
        // Ensure the file is versionable (autoVersion and autoVersionProps read from config)
        if (autoVersion != null && autoVersionProps != null) {
            newFile.ensureVersioningEnabled(autoVersion, autoVersionProps);
        } else {
            newFile.ensureVersioningEnabled();
        }

        // Record the file details ready for generating the response
        model.document = newFile;
    } catch (e) {
        if (!e.message) {
            e.code = 500;
            e.message = "Unexpected error occurred during upload of new content.";
            throw e;
        }
        if (e.message.indexOf("AccessDeniedException") != -1) {
            e.code = 403;
        }
        if (e.message.indexOf("org.alfresco.service.cmr.usage.ContentQuotaException") == 0) {
            e.code = 413;
        }
        if (e.message.indexOf("org.alfresco.repo.content.ContentLimitViolationException") == 0) {
            e.code = 409;
        }

        throw e;
    }
}

main();
