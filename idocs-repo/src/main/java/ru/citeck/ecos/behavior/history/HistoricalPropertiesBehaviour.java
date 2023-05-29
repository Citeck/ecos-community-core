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
package ru.citeck.ecos.behavior.history;

import org.alfresco.model.ContentModel;
import org.alfresco.repo.node.NodeServicePolicies;
import org.alfresco.repo.policy.Behaviour.NotificationFrequency;
import org.alfresco.repo.policy.PolicyComponent;
import org.alfresco.service.cmr.dictionary.AssociationDefinition;
import org.alfresco.service.cmr.dictionary.DictionaryService;
import org.alfresco.service.cmr.repository.AssociationRef;
import org.alfresco.service.cmr.repository.ChildAssociationRef;
import org.alfresco.service.cmr.repository.NodeRef;
import org.alfresco.service.cmr.repository.NodeService;
import org.alfresco.service.namespace.QName;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import ru.citeck.ecos.behavior.JavaBehaviour;
import ru.citeck.ecos.history.HistoryService;
import ru.citeck.ecos.history.HistoryUtils;
import ru.citeck.ecos.model.ClassificationModel;
import ru.citeck.ecos.model.HistoryModel;

import java.io.Serializable;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class HistoricalPropertiesBehaviour implements
		NodeServicePolicies.OnCreateNodePolicy,
		NodeServicePolicies.OnUpdatePropertiesPolicy,
		NodeServicePolicies.OnCreateAssociationPolicy,
		NodeServicePolicies.OnDeleteAssociationPolicy,
		NodeServicePolicies.OnCreateChildAssociationPolicy,
		NodeServicePolicies.OnDeleteChildAssociationPolicy,
		NodeServicePolicies.BeforeDeleteNodePolicy {

	private PolicyComponent policyComponent;
	private NodeService nodeService;
	private HistoryService historyService;

	private QName className;
	private List<QName> allowedProperties;
	private List<QName> ignoreAssocsWithTypes;
	private DictionaryService dictionaryService;
	private static final Map<String,Long> createdNodes = new HashMap<>();
	protected boolean enableHistoryOnCreateNode;
	protected boolean enableHistoryOnUpdateProps;
	protected boolean enableHistoryOnAddAssocs;
	protected boolean enableHistoryOnDeleteAssocs;
	protected boolean enableHistoryOnAddChildAssocs;
	protected boolean enableHistoryOnDeleteChildAssocs;

	private static final Log logger = LogFactory.getLog(HistoricalPropertiesBehaviour.class);

	public void init() {
		//added
		policyComponent.bindClassBehaviour(NodeServicePolicies.OnCreateNodePolicy.QNAME,
				className, new JavaBehaviour(this, "onCreateNode", NotificationFrequency.FIRST_EVENT));

		policyComponent.bindClassBehaviour(NodeServicePolicies.OnUpdatePropertiesPolicy.QNAME, className,
				new JavaBehaviour(this, "onUpdateProperties", NotificationFrequency.EVERY_EVENT));

		policyComponent.bindAssociationBehaviour(NodeServicePolicies.OnCreateAssociationPolicy.QNAME, className,
				new JavaBehaviour(this, "onCreateAssociation", NotificationFrequency.TRANSACTION_COMMIT));

		policyComponent.bindAssociationBehaviour(NodeServicePolicies.OnDeleteAssociationPolicy.QNAME, className,
				new JavaBehaviour(this, "onDeleteAssociation", NotificationFrequency.TRANSACTION_COMMIT)
		);
		policyComponent.bindAssociationBehaviour(NodeServicePolicies.OnCreateChildAssociationPolicy.QNAME, className,
				new JavaBehaviour(this, "onCreateChildAssociation", NotificationFrequency.TRANSACTION_COMMIT)
		);
		policyComponent.bindAssociationBehaviour(NodeServicePolicies.OnDeleteChildAssociationPolicy.QNAME, className,
				new JavaBehaviour(this, "onDeleteChildAssociation")
		);
		policyComponent.bindClassBehaviour(NodeServicePolicies.BeforeDeleteNodePolicy.QNAME, ContentModel.TYPE_CONTENT,
				new JavaBehaviour(this, "beforeDeleteNode", NotificationFrequency.EVERY_EVENT)
		);
	}

	public void setPolicyComponent(PolicyComponent policyComponent) {
		this.policyComponent = policyComponent;
	}

	public NodeService getNodeService() {
		return nodeService;
	}

	public void setNodeService(NodeService nodeService) {
		this.nodeService = nodeService;
	}

	public void setHistoryService(HistoryService historyService) {
		this.historyService = historyService;
	}

	public void setDictionaryService(DictionaryService dictionaryService) {
		this.dictionaryService = dictionaryService;
	}

	@Override
	public void onCreateNode(ChildAssociationRef childAssocRef) {
		logger.debug("HistoricalPropertiesBehaviour onCreateNode="+this);
		logger.debug("HistoricalPropertiesBehaviour onCreateNode="+childAssocRef);
		NodeRef nodeRef = childAssocRef.getChildRef();
		NodeRef.Status status = nodeService.getNodeStatus(nodeRef);
		synchronized (createdNodes) {
			createdNodes.put(nodeRef.getId(),status.getDbTxnId());
		}
		logger.debug("HistoricalPropertiesBehaviour onCreateNode1="+isNewNode(nodeRef));
	}

	private boolean isNewNode(NodeRef nodeRef) {
		NodeRef.Status status = nodeService.getNodeStatus(nodeRef);
		synchronized (createdNodes) {
			Long createdDbTxnId = createdNodes.get(nodeRef.getId());
			if (createdDbTxnId!=null) {
				if (createdDbTxnId.equals(status.getDbTxnId())) {
					return true;
				} else {
					// Remove from cache if not new
					createdNodes.remove(nodeRef.getId());
					return false;
				}
			} else {
				return false;
			}
		}
	}

	@Override
	public void onUpdateProperties(final NodeRef nodeRef, Map<QName, Serializable> before, Map<QName, Serializable> after) {

		logger.debug("HistoricalPropertiesBehaviour onUpdateProperties="+this);
		logger.debug("HistoricalPropertiesBehaviour onUpdateProperties="+nodeRef);
		logger.debug("HistoricalPropertiesBehaviour onUpdateProperties="+isNewNode(nodeRef));

		logger.debug("onUpdateProperties event");

		if (!isNewNode(nodeRef)
            && enableHistoryOnUpdateProps
            && nodeService.exists(nodeRef)
            && className!=null
            && className.equals(nodeService.getType(nodeRef))) {

		    if (before.get(ContentModel.PROP_NODE_UUID)!=null ) {

				logger.debug("onUpdateProperties edit mode");
				Map<QName, Serializable> properties = nodeService.getProperties(nodeRef);

				for(Map.Entry<QName, Serializable> entry : properties.entrySet()) {

				    Object propBefore = before.get(entry.getKey());
					Object propAfter = after.get(entry.getKey());

					if (allowedProperties!=null && allowedProperties.contains(entry.getKey())) {

						if ((propBefore != null && !propBefore.equals(propAfter))
                            || (propBefore == null && (propAfter != null && !"".equals(propAfter)))) {

							logger.debug("onUpdateProperties propBefore "+propBefore+" propAfter "+propAfter);
                            String comment;
                            if (ContentModel.PROP_CONTENT.equals(entry.getKey())) {
								comment = HistoryUtils.getKeyValue(entry.getKey(), dictionaryService);
							} else {
                                comment = HistoryUtils.getKeyValue(entry.getKey(), dictionaryService)
                                    + ": "
                                    + HistoryUtils.getKeyValue(entry.getKey(), propBefore, dictionaryService, nodeService)
                                    + " -> "
                                    + HistoryUtils.getKeyValue(entry.getKey(), propAfter, dictionaryService, nodeService);
                            }
							historyService.persistEvent(HistoryModel.TYPE_BASIC_EVENT, HistoryUtils.eventProperties(
								HistoryUtils.NODE_UPDATED,
                                nodeRef,
                                entry.getKey(),
                                String.valueOf(propAfter),
                                comment,
                                null,
                                null
							));
						}
					}
				}
			}
		}
	}

	@Override
	public void onCreateAssociation(AssociationRef nodeAssocRef) {

	    logger.debug("HistoricalPropertiesBehaviour onCreateAssociation="+this);
		logger.debug("onCreateAssociation event");

		AssociationDefinition assoc = dictionaryService.getAssociation(nodeAssocRef.getTypeQName());
        NodeRef nodeSource = nodeAssocRef.getSourceRef();
        NodeRef nodeTarget = nodeAssocRef.getTargetRef();

        if (!isNewNode(nodeAssocRef.getSourceRef())
            && enableHistoryOnAddAssocs
            && nodeService.exists(nodeSource)
            && className != null
            && className.equals(nodeService.getType(nodeSource))
            && allowedProperties!=null
            && allowedProperties.contains(nodeAssocRef.getTypeQName())) {

            HistoryUtils.addResourceTransaction(HistoryUtils.ASSOC_ADDED, nodeAssocRef);
            if (assoc != null) {
				historyService.persistEvent(
						HistoryModel.TYPE_BASIC_EVENT,
						HistoryUtils.eventProperties(
							HistoryUtils.ASSOC_ADDED,
                            nodeSource,
                            assoc.getName(),
                            nodeTarget.toString(),
                            HistoryUtils.getAssocComment(
                                nodeAssocRef,
                                null,
                                dictionaryService,
                                nodeService),
                            null,
                            null
						));
				HistoryUtils.addUpdateResourceToTransaction(
				    HistoryUtils.ASSOC_ADDED,
                    historyService,
                    dictionaryService,
                    nodeService);
			}
		}
	}

	@Override
	public void onDeleteAssociation(AssociationRef nodeAssocRef) {

	    logger.debug("onDeleteAssociation event");

		NodeRef nodeSource = nodeAssocRef.getSourceRef();
		AssociationDefinition assoc = dictionaryService.getAssociation(nodeAssocRef.getTypeQName());

		if (!isNewNode(nodeAssocRef.getSourceRef())
            && enableHistoryOnDeleteAssocs
            && nodeService.exists(nodeSource)
            && className != null
            && className.equals(nodeService.getType(nodeSource))
            && allowedProperties!=null
            && allowedProperties.contains(nodeAssocRef.getTypeQName())) {

		    HistoryUtils.addResourceTransaction(HistoryUtils.ASSOC_REMOVED, nodeAssocRef);
			if  (assoc != null) {
				historyService.persistEvent(
						HistoryModel.TYPE_BASIC_EVENT,
						HistoryUtils.eventProperties(
						    HistoryUtils.ASSOC_REMOVED,
                            nodeSource, assoc.getName(),
                            null,
                            HistoryUtils.getAssocComment(
                                null,
                                nodeAssocRef,
                                dictionaryService,
                                nodeService),
                            null,
                            null
						));
				HistoryUtils.addUpdateResourceToTransaction(
				    HistoryUtils.ASSOC_REMOVED, historyService, dictionaryService, nodeService);
			}
		}
	}

	@Override
	public void onCreateChildAssociation(ChildAssociationRef childAssociationRef, boolean isNew) {

	    logger.debug("HistoricalPropertiesBehaviour onCreateChildAssociation=" + this);
		logger.debug("onCreateChildAssociation event");

		AssociationDefinition assoc = dictionaryService.getAssociation(childAssociationRef.getTypeQName());
        NodeRef nodeSource = childAssociationRef.getParentRef();
        NodeRef nodeTarget = childAssociationRef.getChildRef();

        if (enableHistoryOnAddChildAssocs
            && nodeService.exists(nodeSource)
            && className!=null
            && className.equals(nodeService.getType(nodeSource))
            && allowedProperties!=null
            && allowedProperties.contains(childAssociationRef.getTypeQName())
            && nodeService.exists(nodeTarget)) {

            HistoryUtils.addResourceTransaction(HistoryUtils.CHILD_ASSOC_ADDED, childAssociationRef);
            if (assoc != null
                && (ignoreAssocsWithTypes == null
                    || !ignoreAssocsWithTypes.contains(nodeService.getType(nodeTarget)))) {

                historyService.persistEvent(HistoryModel.TYPE_BASIC_EVENT, HistoryUtils.eventProperties(
						HistoryUtils.ASSOC_ADDED, nodeSource, assoc.getName(), nodeTarget.toString(), null,
						nodeService.getProperty(nodeTarget, ClassificationModel.PROP_DOCUMENT_TYPE),
						nodeService.getProperty(nodeTarget, ClassificationModel.PROP_DOCUMENT_KIND)
				));
                HistoryUtils.addUpdateChildAssocResourceToTransaction(HistoryUtils.CHILD_ASSOC_ADDED, historyService,
                    dictionaryService, nodeService, "");
			}
		}
	}

	@Override
	public void onDeleteChildAssociation(ChildAssociationRef childAssociationRef) {

		logger.debug("onDeleteChildAssociation event");

		AssociationDefinition assoc = dictionaryService.getAssociation(childAssociationRef.getTypeQName());
		NodeRef nodeSource = childAssociationRef.getParentRef();
		NodeRef nodeTarget = childAssociationRef.getChildRef();

		if (enableHistoryOnDeleteChildAssocs
            && nodeService.exists(nodeSource)
            && className!=null
            && className.equals(nodeService.getType(nodeSource))
            && allowedProperties != null
            && allowedProperties.contains(childAssociationRef.getTypeQName())) {

            HistoryUtils.addResourceTransaction(HistoryUtils.CHILD_ASSOC_REMOVED, childAssociationRef);
			if (assoc != null
                && (ignoreAssocsWithTypes == null || nodeService.exists(nodeTarget)
                && !ignoreAssocsWithTypes.contains(nodeService.getType(nodeTarget)))) {

				historyService.persistEvent(HistoryModel.TYPE_BASIC_EVENT, HistoryUtils.eventProperties(
						HistoryUtils.ASSOC_REMOVED, nodeSource, assoc.getName(), null, null,
						nodeService.getProperty(nodeTarget, ClassificationModel.PROP_DOCUMENT_TYPE),
						nodeService.getProperty(nodeTarget, ClassificationModel.PROP_DOCUMENT_KIND)
				));
                HistoryUtils.addUpdateChildAssocResourceToTransaction(
                    HistoryUtils.CHILD_ASSOC_REMOVED,
                    historyService,
                    dictionaryService,
                    nodeService,
                    ""
                );
			}
		}
	}


	@Override
	public void beforeDeleteNode(NodeRef nodeTarget) {

		logger.debug("beforeDeleteNode event");

		ChildAssociationRef childAssociationRef = nodeService.getPrimaryParent(nodeTarget);
		NodeRef nodeSource = childAssociationRef.getParentRef();
		if (!nodeService.exists(nodeSource)) {
			return;
		}
		AssociationDefinition assoc = dictionaryService.getAssociation(childAssociationRef.getTypeQName());

		if (enableHistoryOnDeleteChildAssocs
            && nodeService.exists(nodeSource)
            && className != null
            && className.equals(nodeService.getType(nodeSource))
            && allowedProperties!=null
            && allowedProperties.contains(childAssociationRef.getTypeQName())) {

			HistoryUtils.addResourceTransaction(HistoryUtils.CHILD_ASSOC_REMOVED, childAssociationRef);
			if (assoc != null
                && (ignoreAssocsWithTypes == null || nodeService.exists(nodeTarget)
                && !ignoreAssocsWithTypes.contains(nodeService.getType(nodeTarget)))) {

			    historyService.persistEvent(HistoryModel.TYPE_BASIC_EVENT, HistoryUtils.eventProperties(
						HistoryUtils.ASSOC_REMOVED, nodeSource, assoc.getName(), null, null,
						nodeService.getProperty(nodeTarget, ClassificationModel.PROP_DOCUMENT_TYPE),
						nodeService.getProperty(nodeTarget, ClassificationModel.PROP_DOCUMENT_KIND)
				));
				HistoryUtils.addUpdateChildAssocResourceToTransaction(
				    HistoryUtils.CHILD_ASSOC_REMOVED,
                    historyService,
                    dictionaryService,
                    nodeService,
                    HistoryUtils.getChildAssocComment(null, childAssociationRef, dictionaryService, nodeService,
                        "")
                );
			}
		}
	}


	public void setAllowedProperties(List<QName> allowedProperties) {
		this.allowedProperties = allowedProperties;
	}

	public void setClassName(QName className) {
		this.className = className;
	}

	public void setIgnoreAssocsWithTypes(List<QName> ignoreAssocsWithTypes) {
		this.ignoreAssocsWithTypes = ignoreAssocsWithTypes;
	}

	public void setEnableHistoryOnUpdateProps(Boolean enableHistoryOnUpdateProps) {
		this.enableHistoryOnUpdateProps = enableHistoryOnUpdateProps;
	}

	public void setEnableHistoryOnAddAssocs(Boolean enableHistoryOnAddAssocs) {
		this.enableHistoryOnAddAssocs = enableHistoryOnAddAssocs;
	}

	public void setEnableHistoryOnDeleteAssocs(Boolean enableHistoryOnDeleteAssocs) {
		this.enableHistoryOnDeleteAssocs = enableHistoryOnDeleteAssocs;
	}

	public void setEnableHistoryOnAddChildAssocs(Boolean enableHistoryOnAddChildAssocs) {
		this.enableHistoryOnAddChildAssocs = enableHistoryOnAddChildAssocs;
	}

	public void setEnableHistoryOnDeleteChildAssocs(Boolean enableHistoryOnDeleteChildAssocs) {
		this.enableHistoryOnDeleteChildAssocs = enableHistoryOnDeleteChildAssocs;
	}
}
