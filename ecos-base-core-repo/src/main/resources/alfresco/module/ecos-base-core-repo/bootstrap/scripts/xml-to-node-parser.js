/**
 * @author Roman Makarskiy
 */
const METHOD_SAVE = "save";
const METHOD_DELETE = "delete";
const STATUS_NEW = "New";
const STATUS_READY = "Ready";
const STATUS_IN_PROGRESS = "In progress";
const STATUS_COMPLETE = "Complete";
const STATUS_ERROR = "Error";
const STATUS_DELETING = "Deleting";
var status = STATUS_COMPLETE;

var parser = {
    parserScriptName: "xml-to-node-parser.js",
    parserData: {
        uuidFromProp: "",
        uuidPrefix: "",
        cmNameFromProp: "",
        cmNamePrefix: "",
        cmTitleRuFromProp: "",
        cmTitleEnFromProp: "",
        titles: {
            en: "",
            ru: ""
        },
        path: "",
        type: "",
        updateEnabled: false,
        identityProp: ""
    },
    processNodes: function (method, xmlData) {
        var xmlDataNode = search.findNode(xmlData);
        if (!xmlDataNode || xmlDataNode.typeShort != "xni:data") {
            logger.warn(this.parserScriptName + ": failed find xml data - " + xmlData);
            return false;
        }

        var content = xmlDataNode.content;
        var xml = new XML(content.replaceAll("(?s)<\\?xml .*?\\?>\\s*", ""));

        this.parserData.path = xml.path;
        this.parserData.type = xml.type;
        if (xml.ecosType && ('' + xml.ecosType)) {
            this.parserData.ecosType = '' + xml.ecosType;
        } else {
            this.parserData.ecosType = null;
        }

        var root = this.helper.getRootNodeByPath(this.parserData.path, this.parserData.ecosType);
        if (!root) {
            return false;
        }

        var startTime = new Date().getTime();
        this.parserData.uuidFromProp = xml.uuidFromProp;
        this.parserData.cmNameFromProp = xml.cmNameFromProp;
        this.parserData.cmNamePrefix = xml.cmNamePrefix;
        this.parserData.uuidPrefix = xml.uuidPrefix;
        this.parserData.cmTitleRuFromProp = xml.cmTitle_RU_fromProp;
        this.parserData.cmTitleEnFromProp = xml.cmTitle_EN_fromProp;
        this.parserData.updateEnabled = xml.updateEnabled === "true";
        this.parserData.identityProp = xml.identityProp;

        var objects = this.helper.getObjects(xml);
        var objCount = objects.length;

        logger.warn(this.parserScriptName + " Start processing...method: " + method
            + ", type: " + this.parserData.type
            + " ecosType: " + this.parserData.ecosType);
        logger.warn(this.parserScriptName + " Found " + objCount + " objects in XNI data. Processing in progress...");

        switch (method + "") {
            case METHOD_SAVE:
                this.saveNodes(root, objects);
                status = STATUS_COMPLETE;
                break;
            case METHOD_DELETE:
                this.deleteNodes(root);
                status = STATUS_READY;
                break;
            default:
                logger.error(this.parserScriptName + " Incorrect method: " + method);
        }

        setStatusAsync(xmlDataNode, status);
        var endTime = new Date().getTime();
        var executedTime = endTime - startTime;
        logger.warn(this.parserScriptName + " Processing ends in " + this.helper.millisToMinAndSeconds(executedTime)
            + " (min:sec)" + " (" + executedTime + " ms)");
    },
    saveNodes: function (root, objects) {
        batchExecuter.processArray({
            items: objects,
            batchSize: 200,
            //TODO: Fix multi threads import. In Alfresco 5 multithreaded javascript batch processor does not work correctly - SQL Duplicate key exception.
            threads: 1,
            onNode: function (row) {
                var propObj = parser.getProperties(row, parser.parserData.ecosType);

                var existingNode = parser.helper.searchByUuid(propObj);
                if (existingNode) {
                    if (parser.parserData.updateEnabled) {
                        parser.updateNode(existingNode, propObj, row);
                        return;
                    } else {
                        logger.warn(parser.parserScriptName + " Cannot create node, because node with uuid: '"
                            + propObj['sys:node-uuid'] + "' already exists.");
                        status = STATUS_ERROR;
                        return;
                    }
                }

                existingNode = parser.helper.searchByCmName(parser.parserData.path, propObj);
                if (existingNode) {
                    if (parser.parserData.updateEnabled) {
                        parser.updateNode(existingNode, propObj, row);
                        return;
                    } else {
                        logger.warn(parser.parserScriptName + " Cannot create node, because node with cm:name - '"
                            + propObj['cm:name'] + "' already exists.");
                        status = STATUS_ERROR;
                        return;
                    }
                }

                existingNode = parser.helper.searchByIdentityProp(parser.parserData.path,
                    parser.parserData.identityProp, propObj);
                if (existingNode) {
                    parser.updateNode(
                        existingNode,
                        propObj,
                        row,
                        parser.parserData.ecosType
                    );
                } else {
                    parser.createNode(
                        root,
                        parser.parserData.type,
                        "cm:contains",
                        propObj,
                        row,
                        parser.parserData.ecosType
                    );
                }
            }
        });
    },
    createNode: function (root, type, assocType, props, row, ecosType) {
        var createdNode;
        if (ecosType) {
            var rec = Records.get("@");
            for (var prop in props) {
                var propValue = props[prop];
                rec.att(prop, propValue);
            }
            if (assocType) {
                rec.att("_parentAtt", assocType);
            }
            if (root) {
                rec.att("_parent", '' + root.nodeRef);
            }
            createdNode = search.findNode(rec.save().getLocalId());
        } else {
            createdNode = root.createNode(null, type, props, assocType);
        }
        logger.warn("[xml-to-node] New nodeRef: " + createdNode.nodeRef);
        parser.helper.fillNodeTitle(createdNode);
        parser.helper.fillAssocs(row, createdNode);
    },
    updateNode: function (node, props, row, ecosType) {
        if (ecosType) {
            var rec = Records.get(node);
            for (var prop in props) {
                var propValue = props[prop];
                rec.att(prop, propValue);
            }
            rec.save();
        } else {
            this.mergeProperties(node, props);
            node.save();
        }
        parser.helper.fillNodeTitle(node);
        parser.helper.fillAssocs(row, node);
    },
    deleteNodes: function (rootFolder) {
        logger.warn(this.parserScriptName + " Actual object count: " + rootFolder.children.length);
        batchExecuter.processArray({
            items: rootFolder.children,
            batchSize: 100,
            threads: 4,
            onNode: function (row) {
                row.remove();
            }
        });
    },
    getProperties: function (obj, ecosType) {
        var propObj = {};
        if (ecosType) {
            if (ecosType.indexOf('emodel/type@') === -1) {
                propObj['_type'] = 'emodel/type@' + ecosType;
            } else {
                propObj['_type'] = ecosType;
            }
        }

        var properties = obj.properties;
        var propCount = properties.*.length();

        var propValueForUuid = "";
        var propValueForCmName = "";

        var propAtts = ecosTypeService.getAttsIdListByType(ecosType);

        for (var i = 0; i < propCount; i++) {
            var prop = properties.child(i);

            var propType = prop.name().toString();
            if (propAtts.indexOf(propType) === -1) {
                propType = propType.split('_').join(':');
            }

            var propValue = prop.toString();

            if (propType != "cm:title:ru" && propType != "cm:title:en") {
                propObj[propType] = propValue;
            }

            if (!propValueForUuid) {
                propValueForUuid = this.helper.getUuidValue(propValue, prop);
            }

            if (!propValueForCmName) {
                propValueForCmName = this.helper.getCmNameValue(propValue, prop);
            }

            this.helper.fillTitleData(propValue, prop);
        }

        if (propValueForUuid) {
            propObj['sys:node-uuid'] = propValueForUuid.toString();
        }
        if (propValueForCmName) {
            propObj['cm:name'] = propValueForCmName.toString();
        }

        return propObj;
    },
    mergeProperties: function (node, propObj) {
        for (var key in propObj) {
            node.properties[key] = propObj[key];
        }
    },
    helper: {
        getObjects: function (xml) {
            var objects = [];
            for each (var i in xml.object) {
                objects.push(i);
            }
            return objects;
        },
        fillAssocs: function (obj, node) {
            var assocsData = obj.associations.association;

            if (assocsData.length() == 0) {
                return;
            }

            for each(var assocData in assocsData) {

                if (assocData.cm_user.length() > 0) {
                    if (assocData.cm_user.username.length() == 0 || assocData.assocType.length() == 0) {
                        logger.error(parser.parserScriptName
                            + " fillAssocs() - cm_user: (name) or (assocType) parameter is empty. Method aborted.");
                        return;
                    }

                    var userNode = this.searchUserByUserName(assocData.cm_user);
                    if (!userNode) {
                        logger.error(this.parserScriptName + " cannot find user by username: "
                            + assocData.cm_user.username);
                        return;
                    }

                    if (!this.isAssocExists(node, userNode, assocData.assocType)) {
                        node.createAssociation(userNode, assocData.assocType);
                    }
                } else if (assocData.cm_authority.length() > 0) {
                    if (assocData.cm_authority.name.length() == 0 || assocData.assocType.length() == 0) {
                        logger.error(parser.parserScriptName
                            + " fillAssocs() - cm_authority: (name) or (assocType) parameter is empty. Method aborted.");
                        return;
                    }

                    var targetGroup = this.createGroup(assocData.cm_authority);
                    if (!targetGroup) {
                        logger.error(this.parserScriptName + " cannot create association with authority: "
                            + assocData.cm_authority.name);
                        return;
                    }

                    var groupNode = search.findNode(targetGroup.groupNodeRef);
                    if (!this.isAssocExists(node, groupNode, assocData.assocType)) {
                        node.createAssociation(groupNode, assocData.assocType);
                    }

                    if (assocData.cm_authority.parent_groups.group.length() > 0) {
                        var parentGroups = assocData.cm_authority.parent_groups.group;
                        for each (var parentGroup in parentGroups) {
                            var createdParentGroup = this.createGroup(parentGroup);
                            if (createdParentGroup && !this.childGroupExists(createdParentGroup, targetGroup.shortName)) {
                                createdParentGroup.addAuthority("GROUP_" + targetGroup.shortName);
                            }
                        }
                    }

                    if (assocData.cm_authority.child_groups.group.length() > 0) {
                        var childGroups = assocData.cm_authority.child_groups.group;
                        for each (var childGroup in childGroups) {
                            var createdChildGroup = this.createGroup(childGroup);
                            if (createdChildGroup && !this.childGroupExists(targetGroup, childGroup.name)) {
                                targetGroup.addAuthority("GROUP_" + childGroup.name);
                            }
                        }
                    }

                } else {
                    var targetNode = null;

                    if (assocData.uuid.length() > 0) {

                        if (assocData.assocType.length() == 0) {
                            logger.error(parser.parserScriptName + " cannot fill assocs from uuid: " + assocData.uuid
                                + ", because assocType is empty.");
                            return;
                        }

                        var uuid = assocData.uuid.toString().toLowerCase();
                        targetNode = search.findNode("workspace://SpacesStore/" + uuid);
                    } else {
                        if (assocData.assocType.length() == 0 || assocData.targetNodeType.length() == 0
                            || assocData.propId.length() == 0 || assocData.propValue.length() == 0
                            || assocData.targetNodePath.length() == 0) {
                            logger.error(parser.parserScriptName
                                + " cannot fill assocs from propId, because one of mandatory parameter is empty");
                            return;

                        }

                        var targetNodeRoot = this.getRootNodeByPath(assocData.targetNodePath);

                        if (!targetNodeRoot) {
                            return;
                        }

                        targetNode = this.getChildByProperty(targetNodeRoot, assocData.propId, assocData.propValue);
                    }

                    if (!targetNode || !targetNode.exists()) {
                        logger.error(this.parserScriptName + " targetNode does not exists. Information: " +
                            "\ntargetNodeRoot: " + targetNodeRoot.nodeRef + " prop id: "
                            + assocData.propId + " prop value: " + assocData.propValue + " uuid: " + assocData.uuid);
                    } else {
                        if (!this.isAssocExists(node, targetNode, assocData.assocType)) {
                            node.createAssociation(targetNode, assocData.assocType);
                        }
                    }
                }

            }
        },
        childGroupExists: function (group, childShortName) {
            var childGroup = this.getChildGroupByNameOrNullNotFound(group, childShortName);
            return childGroup != null;
        },
        getChildGroupByNameOrNullNotFound: function (group, childShortName) {
            var maxItems = 200;
            var skipCount = 0;
            var childGroups = group.getChildGroups(maxItems, skipCount);

            for each (var childGroup in childGroups) {
                if (childGroup.shortName == childShortName) {
                    return childGroup;
                }
            }
            return null;
        },
        searchUserByUserName: function(userObj) {
            var username = userObj.username;
            return people.getPerson(username);
        },
        createGroup: function (groupObj) {
            var groupName = groupObj.name;
            var groupDisplayName = groupObj.displayName;
            var createIfNotExists = groupObj.createIfNotExists;

            var targetGroup = groups.getGroup(groupName);

            if (!targetGroup) {
                if (createIfNotExists == 'true') {
                    if (groupDisplayName.length() == 0) {
                        groupDisplayName = groupName;
                    }
                    targetGroup = groups.createRootGroup(groupName, groupDisplayName)
                } else {
                    logger.error(this.parserScriptName + " cannot fill group: "
                        + groupName + ", because this group doesn't exists and "
                        + "parameter (createIfNotExists) not equals (true)");
                    return null;
                }
            }
            return targetGroup;
        },
        fillNodeTitle: function (node) {
            if (parser.parserData.titles.en.length > 0) {
                utils.setLocale("en");
                node.properties['cm:title'] = parser.parserData.titles.en;
                node.save();

                utils.setLocale("en_US");
                node.properties['cm:title'] = parser.parserData.titles.en;
                node.save();
            }
            if (parser.parserData.titles.ru.length > 0) {
                utils.setLocale("ru_RU");
                node.properties['cm:title'] = parser.parserData.titles.ru;
                node.save();
            }
            parser.parserData.titles.en = "";
            parser.parserData.titles.ru = "";
        },
        fillTitleData: function (propValue, prop) {
            if (parser.parserData.cmTitleRuFromProp && parser.parserData.cmTitleRuFromProp.toString().length > 0) {
                if (parser.parserData.cmTitleRuFromProp.toString() == prop.name().toString()) {
                    parser.parserData.titles.ru = propValue;
                }
            }

            if (parser.parserData.cmTitleEnFromProp && parser.parserData.cmTitleEnFromProp.toString().length > 0) {
                if (parser.parserData.cmTitleEnFromProp.toString() == prop.name().toString()) {
                    parser.parserData.titles.en = propValue;
                }
            }

            if (prop.name().toString() == "cm_title_ru") {
                parser.parserData.titles.ru = propValue;
            }

            if (prop.name().toString() == "cm_title_en") {
                parser.parserData.titles.en = propValue;
            }
        },
        getCmNameValue: function (propValue, prop) {
            var cmNameValue = "";
            if (parser.parserData.cmNameFromProp && parser.parserData.cmNameFromProp.toString().length > 0) {
                if (parser.parserData.cmNameFromProp.toString() == prop.name().toString()) {
                    if (parser.parserData.cmNamePrefix && parser.parserData.cmNamePrefix.toString().length > 0) {
                        cmNameValue = parser.parserData.cmNamePrefix.toString();
                    }
                    var correctPropValue = propValue.split(".").join("");
                    correctPropValue = correctPropValue.split("/").join("-");

                    cmNameValue = cmNameValue + correctPropValue;
                }
            }
            return cmNameValue;
        },
        getUuidValue: function (propValue, prop) {
            var uuidValue = "";
            if (parser.parserData.uuidFromProp && parser.parserData.uuidFromProp.toString().length > 0) {
                if (parser.parserData.uuidFromProp.toString() == prop.name().toString()) {
                    if (parser.parserData.uuidPrefix && parser.parserData.uuidPrefix.toString().length > 0) {
                        uuidValue = parser.parserData.uuidPrefix.toString();
                    }
                    var correctUuid = propValue.split(' ').join('-').toLowerCase();
                    correctUuid = correctUuid.split(".").join("");
                    correctUuid = correctUuid.split("_").join("-");
                    correctUuid = correctUuid.split(",").join("");

                    uuidValue = uuidValue + correctUuid
                }
            }
            return uuidValue;
        },
        /*TODO: fix a long search, while a large number of children*/
        getChildByProperty: function (parent, property, value) {
            var children = parent.children;

            for each (var child in children) {
                if (child.properties[property] == value) {
                    return child;
                }
            }
            return null;
        },
        getRootNodeByPath: function (path, ecosType) {
            var rootNode = null;
            if (path) {
                rootNode = (search.selectNodes(path) || [])[0];
            }
            if (!rootNode && ecosType) {
                rootNode = ecosTypeService.getRootForType(ecosType, true);
            }
            if (!rootNode) {
                logger.error(this.parserScriptName + " cannot find root node by path: " + path);
            }
            return rootNode;
        },
        searchByUuid: function (propObj) {
            if (propObj['sys:node-uuid']) {
                return search.findNode("workspace://SpacesStore/" + propObj['sys:node-uuid']);
            }
            return null;
        },
        searchByCmName: function (path, propObj) {
            if (propObj['cm:name']) {
                var root = this.getRootNodeByPath(path);
                if (root) {
                    return root.childByNamePath(propObj['cm:name']);
                }
            }
            return null;
        },
        searchByIdentityProp: function (path, propName, propObj) {
            if (propName && propObj[propName]) {
                var root = this.getRootNodeByPath(path);
                if (root) {
                    return this.getChildByProperty(root, propName, propObj[propName]);
                }
            }
            return null;
        },
        isAssocExists: function (sourceNode, targetNode, assocType) {
            if (!sourceNode || !targetNode || !assocType) {
                return false;
            }

            var assocs = sourceNode.assocs[assocType];
            if (!assocs || assocs.length == 0) {
                return false;
            }

            for (var i in assocs) {
                if (assocs[i] == targetNode) {
                    return true;
                }
            }
            return false;
        },
        millisToMinAndSeconds: function (millis) {
            var minutes = Math.floor(millis / 60000);
            var seconds = ((millis % 60000) / 1000).toFixed(0);
            return minutes + ":" + (seconds < 10 ? '0' : '') + seconds;
        }
    }
};

function setStatusAsync(node, status) {
    batchExecuter.processArray({
        items: [node],
        batchSize: 1,
        threads: 1,
        onNode: function (row) {
            row.properties["xni:status"] = status;
            row.save();
        }
    });
}
