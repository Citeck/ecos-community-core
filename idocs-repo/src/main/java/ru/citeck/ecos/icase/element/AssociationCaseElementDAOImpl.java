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
package ru.citeck.ecos.icase.element;

import org.alfresco.error.AlfrescoRuntimeException;
import org.alfresco.repo.node.NodeServicePolicies;
import org.alfresco.repo.policy.BehaviourDefinition;
import org.alfresco.service.cmr.repository.AssociationRef;
import org.alfresco.service.cmr.repository.ChildAssociationRef;
import org.alfresco.service.cmr.repository.NodeRef;
import org.alfresco.service.cmr.repository.NodeService;
import org.alfresco.service.namespace.QName;
import org.alfresco.service.namespace.RegexQNamePattern;
import ru.citeck.ecos.behavior.ParameterizedJavaBehaviour;
import ru.citeck.ecos.icase.element.config.AssocsConfigDto;
import ru.citeck.ecos.model.ICaseModel;
import ru.citeck.ecos.model.ICaseTemplateModel;
import ru.citeck.ecos.utils.RepoUtils;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * @author Anton Fateev <anton.fateev@citeck.ru>
 */
public class AssociationCaseElementDAOImpl extends AbstractCaseElementDAO<AssocsConfigDto> {

    @Override
    public QName getElementConfigType() {
        return ICaseModel.TYPE_ASSOC_CONFIG;
    }

    enum AssociationType {
        TARGET {
            @Override
            public List<NodeRef> getTarget(NodeRef caseNode, QName assocName,
                                           AssociationCaseElementDAOImpl config) {
                return config.searchTargetAssocs(caseNode, assocName);
            }

            @Override
            public List<NodeRef> getSource(NodeRef element, QName assocName,
                                           AssociationCaseElementDAOImpl config) {
                return config.searchSourceAssocs(element, assocName);
            }

            @Override
            public void create(NodeRef caseNode, NodeRef element,
                               QName assocName, AssociationCaseElementDAOImpl config) {
                config.nodeService.createAssociation(caseNode, element, assocName);
            }

            @Override
            public void remove(NodeRef caseNode, NodeRef element,
                               QName assocName, AssociationCaseElementDAOImpl config) {
                config.nodeService.removeAssociation(caseNode, element, assocName);
            }

            @Override
            public NodeRef getCaseNode(AssociationRef assocRef) {
                return assocRef.getSourceRef();
            }

            @Override
            public NodeRef getElementNode(AssociationRef assocRef) {
                return assocRef.getTargetRef();
            }
        },

        SOURCE {
            @Override
            public List<NodeRef> getTarget(NodeRef caseNode, QName assocName,
                                           AssociationCaseElementDAOImpl config) {
                return config.searchSourceAssocs(caseNode, assocName);
            }

            @Override
            public List<NodeRef> getSource(NodeRef element, QName assocName,
                                           AssociationCaseElementDAOImpl config) {
                return config.searchTargetAssocs(element, assocName);
            }

            @Override
            public void create(NodeRef caseNode, NodeRef element,
                               QName assocName, AssociationCaseElementDAOImpl config) {
                config.nodeService.createAssociation(element, caseNode, assocName);
            }

            @Override
            public void remove(NodeRef caseNode, NodeRef element,
                               QName assocName, AssociationCaseElementDAOImpl config) {
                config.nodeService.removeAssociation(element, caseNode, assocName);
            }

            @Override
            public NodeRef getCaseNode(AssociationRef assocRef) {
                return assocRef.getTargetRef();
            }

            @Override
            public NodeRef getElementNode(AssociationRef assocRef) {
                return assocRef.getSourceRef();
            }
        },

        CHILD {
            @Override
            public List<NodeRef> getTarget(NodeRef caseNode, QName assocName,
                                           AssociationCaseElementDAOImpl config) {
                return config.searchChildAssocs(caseNode, assocName);
            }

            @Override
            public List<NodeRef> getSource(NodeRef element, QName assocName,
                                           AssociationCaseElementDAOImpl config) {
                return config.searchParentAssocs(element, assocName);
            }

            @Override
            public void create(NodeRef caseNode, NodeRef element,
                               QName assocName, AssociationCaseElementDAOImpl config) {
                NodeService nodeService = config.nodeService;
                ChildAssociationRef elementAssoc = nodeService.getPrimaryParent(element);
                nodeService.moveNode(element, caseNode, assocName, elementAssoc.getQName());
            }

            @Override
            public void remove(NodeRef caseNode, NodeRef element,
                               QName assocName, AssociationCaseElementDAOImpl config) {
                config.removeChildElement(caseNode, element, assocName);
            }

            public NodeRef getCaseNode(ChildAssociationRef assocRef) {
                return assocRef.getParentRef();
            }

            public NodeRef getElementNode(ChildAssociationRef assocRef) {
                return assocRef.getChildRef();
            }

        };

        public static AssociationType forName(String name) {
            for (AssociationType type : values()) {
                if (type.toString().equalsIgnoreCase(name)) {
                    return type;
                }
            }
            return null;
        }

        public abstract List<NodeRef> getTarget(NodeRef caseNode,
                                                QName assocName, AssociationCaseElementDAOImpl config);

        public abstract List<NodeRef> getSource(NodeRef element,
                                                QName assocName, AssociationCaseElementDAOImpl config);

        public abstract void create(NodeRef caseNode, NodeRef element,
                                    QName assocName, AssociationCaseElementDAOImpl config);

        public abstract void remove(NodeRef caseNode, NodeRef element,
                                    QName assocName, AssociationCaseElementDAOImpl config);

        public NodeRef getCaseNode(AssociationRef assocRef) {
            throw new IllegalStateException();
        }

        public NodeRef getElementNode(AssociationRef assocRef) {
            throw new IllegalStateException();
        }

        public NodeRef getCaseNode(ChildAssociationRef assocRef) {
            throw new IllegalStateException();
        }

        public NodeRef getElementNode(ChildAssociationRef assocRef) {
            throw new IllegalStateException();
        }
    }

    private AssociationType needAssocType(AssocsConfigDto config) {
        AssociationType associationType = AssociationType.forName(config.getAssocType());
        if (associationType == null) {
            throw new IllegalArgumentException("Incorrect association type: " + config.getAssocType());
        }
        return associationType;
    }

    @Override
    public List<NodeRef> get(NodeRef caseNode, AssocsConfigDto config)
            throws AlfrescoRuntimeException, IllegalArgumentException {

        if (!nodeService.exists(caseNode)) {
            throw new IllegalArgumentException("Cannot get nodeRefs without case node");
        }
        if (config == null) {
            throw new IllegalArgumentException("Cannot get nodeRefs without config");
        }
        AssociationType associationType = needAssocType(config);
        QName assocName = config.getAssocName();
        try {
            List<NodeRef> elements = associationType.getTarget(caseNode, assocName, this);
            QName elementType = config.getElementType();
            return filterByClass(elements, elementType);
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Throwable e) {
            throw new AlfrescoRuntimeException("Can not get elements from case. caseNode=" + caseNode + "; config=" + config, e);
        }
    }

    @Override
    protected List<NodeRef> getCasesImpl(NodeRef element, AssocsConfigDto config)
            throws AlfrescoRuntimeException, IllegalArgumentException {
        AssociationType associationType = needAssocType(config);
        QName assocName = config.getAssocName();
        try {
            return associationType.getSource(element, assocName, this);
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Throwable e) {
            throw new AlfrescoRuntimeException("Can not get cases for element. element=" + element + "; config=" + config, e);
        }
    }

    @Override
    public void add(NodeRef nodeRef, NodeRef caseNode, AssocsConfigDto config)
            throws AlfrescoRuntimeException, IllegalArgumentException {

        if (!nodeService.exists(caseNode)) {
            throw new IllegalArgumentException("Cannot create the association without caseNode");
        }
        if (!nodeService.exists(nodeRef)) {
            throw new IllegalArgumentException("Cannot create the association without nodeRef");
        }
        if (config == null) {
            throw new IllegalArgumentException("Cannot create the association without config");
        }
        AssociationType associationType = needAssocType(config);
        QName assocName = config.getAssocName();
        try {
            associationType.create(caseNode, nodeRef, assocName, this);
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Throwable e) {
            throw new AlfrescoRuntimeException("Can not add node reference to case. nodeRef=" + nodeRef + "; caseNode=" + caseNode + "; config=" + config, e);
        }
    }

    @Override
    public void remove(NodeRef nodeRef, NodeRef caseNode, AssocsConfigDto config)
            throws AlfrescoRuntimeException, IllegalArgumentException {

        if (!nodeService.exists(caseNode)) {
            throw new IllegalArgumentException("Cannot remove the association without caseNode");
        }
        if (!nodeService.exists(nodeRef)) {
            throw new IllegalArgumentException("Cannot remove the association without nodeRef");
        }
        if (config == null) {
            throw new IllegalArgumentException("Cannot remove the association without config");
        }
        AssociationType associationType = needAssocType(config);
        QName assocName = config.getAssocName();
        try {
            associationType.remove(caseNode, nodeRef, assocName, this);
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Throwable e) {
            throw new AlfrescoRuntimeException("Can not remove node reference to case. nodeRef=" + nodeRef + "; caseNode=" + caseNode + "; config=" + config, e);
        }
    }

    @Override
    public void copyElementsToTemplate(NodeRef caseNodeRef, NodeRef template, AssocsConfigDto config) {
        AssociationType associationType = needAssocType(config);
        if (AssociationType.CHILD == associationType) {
            List<NodeRef> elements = associationType.getTarget(caseNodeRef, config.getAssocName(), this);
            for (NodeRef element : elements) {
                copyElement(element, template, ICaseTemplateModel.ASSOC_INTERNAL_ELEMENTS);
            }
        } else {
            super.copyElementsToTemplate(caseNodeRef, template, config);
        }
    }

    @Override
    public void copyElementsFromTemplate(NodeRef template, NodeRef caseNodeRef, AssocsConfigDto config) {
        AssociationType associationType = needAssocType(config);
        if (AssociationType.CHILD == associationType) {
            QName assocName = config.getAssocName();
            List<NodeRef> elements = RepoUtils.getChildrenByAssoc(template,
                    ICaseTemplateModel.ASSOC_INTERNAL_ELEMENTS, nodeService);
            for (NodeRef element : elements) {
                copyElement(element, caseNodeRef, assocName);
            }
        } else {
            super.copyElementsFromTemplate(template, caseNodeRef, config);
        }
    }

    private void copyElement(NodeRef element, NodeRef destination, QName assocType) {
        NodeRef copy = copyService.copy(element, destination, assocType,
                nodeService.getPrimaryParent(element).getQName(),
                true);
        caseElementService.registerElementCopy(element, copy);
    }

    private List<NodeRef> searchSourceAssocs(NodeRef nodeRef, QName association) {
        List<AssociationRef> sourceAssocs = nodeService.getSourceAssocs(nodeRef, association);
        ArrayList<NodeRef> caseObjects = new ArrayList<>();
        for (AssociationRef sourceAssoc : sourceAssocs) {
            caseObjects.add(sourceAssoc.getSourceRef());
        }
        return caseObjects;
    }

    private List<NodeRef> searchTargetAssocs(NodeRef nodeRef, QName association) {
        List<AssociationRef> targetAssocs = nodeService.getTargetAssocs(nodeRef, association);
        ArrayList<NodeRef> caseObjects = new ArrayList<>();
        for (AssociationRef targetAssoc : targetAssocs) {
            caseObjects.add(targetAssoc.getTargetRef());
        }
        return caseObjects;
    }

    private List<NodeRef> searchChildAssocs(NodeRef nodeRef, QName association) {
        List<ChildAssociationRef> childAssocs = nodeService.getChildAssocs(nodeRef, association, RegexQNamePattern.MATCH_ALL);
        ArrayList<NodeRef> caseObjects = new ArrayList<>(childAssocs.size());
        for (ChildAssociationRef childAssoc : childAssocs) {
            caseObjects.add(childAssoc.getChildRef());
        }
        return caseObjects;
    }

    private List<NodeRef> searchParentAssocs(NodeRef nodeRef, QName association) {
        List<ChildAssociationRef> parentAssocs = nodeService.getParentAssocs(nodeRef, association, RegexQNamePattern.MATCH_ALL);
        ArrayList<NodeRef> caseObjects = new ArrayList<>(parentAssocs.size());
        for (ChildAssociationRef parentAssoc : parentAssocs) {
            caseObjects.add(parentAssoc.getParentRef());
        }
        return caseObjects;
    }

    private void removeChildElement(NodeRef caseNode, NodeRef element,
                                    QName assocName) {
        // first check if the association is primary
        ChildAssociationRef primaryAssoc = nodeService.getPrimaryParent(element);
        if (matches(primaryAssoc, caseNode, assocName)) {
            nodeService.deleteNode(element);
            return;
        }

        // otherwise remove all matching parent assocs
        // (presumably, there are less parent assocs of element, than child
        // assocs of case)
        List<ChildAssociationRef> parentAssocs = nodeService.getParentAssocs(
                element, assocName, RegexQNamePattern.MATCH_ALL);
        for (ChildAssociationRef parentAssoc : parentAssocs) {
            if (matches(parentAssoc, caseNode, assocName)) {
                nodeService.removeChildAssociation(parentAssoc);
            }
        }
    }

    private static boolean matches(ChildAssociationRef assoc, NodeRef requiredParent, QName requiredType) {
        return assoc.getParentRef().equals(requiredParent)
                && assoc.getTypeQName().equals(requiredType);
    }

    @Override
    public Set<BehaviourDefinition<?>> intializeBehaviours(AssocsConfigDto config) {
        Set<BehaviourDefinition<?>> behaviours = new HashSet<>(2);
        AssociationType associationType = needAssocType(config);
        QName assocName = config.getAssocName();
        switch (associationType) {
            case CHILD:
                behaviours.add(policyComponent.bindAssociationBehaviour(NodeServicePolicies.OnCreateChildAssociationPolicy.QNAME,
                        ICaseModel.ASPECT_CASE, assocName, ParameterizedJavaBehaviour.newInstance(this, "onCaseElementAdd", config)));
                behaviours.add(policyComponent.bindAssociationBehaviour(NodeServicePolicies.OnDeleteChildAssociationPolicy.QNAME,
                        ICaseModel.ASPECT_CASE, assocName, ParameterizedJavaBehaviour.newInstance(this, "onCaseElementRemove", config)));
                break;
            case TARGET:
                behaviours.add(policyComponent.bindAssociationBehaviour(NodeServicePolicies.OnCreateAssociationPolicy.QNAME,
                        ICaseModel.ASPECT_CASE, assocName, ParameterizedJavaBehaviour.newInstance(this, "onCaseElementAdd", config)));
                behaviours.add(policyComponent.bindAssociationBehaviour(NodeServicePolicies.OnDeleteAssociationPolicy.QNAME,
                        ICaseModel.ASPECT_CASE, assocName, ParameterizedJavaBehaviour.newInstance(this, "onCaseElementRemove", config)));
                break;
            case SOURCE:
                QName elementType = config.getElementType();
                behaviours.add(policyComponent.bindAssociationBehaviour(NodeServicePolicies.OnCreateAssociationPolicy.QNAME,
                        elementType, assocName, ParameterizedJavaBehaviour.newInstance(this, "onCaseElementAdd", config)));
                behaviours.add(policyComponent.bindAssociationBehaviour(NodeServicePolicies.OnDeleteAssociationPolicy.QNAME,
                        elementType, assocName, ParameterizedJavaBehaviour.newInstance(this, "onCaseElementRemove", config)));
                break;
        }
        return behaviours;
    }

    @Override
    public AssocsConfigDto createConfig(NodeRef configRef) {
        AssocsConfigDto configDto = new AssocsConfigDto(configRef);
        configDto.setAssocName((QName) nodeService.getProperty(configRef, ICaseModel.PROP_ASSOC_NAME));
        configDto.setAssocType((String) nodeService.getProperty(configRef, ICaseModel.PROP_ASSOC_TYPE));
        return configDto;
    }

    private class BehaviourEnvironment {
        AssociationType associationType;
        QName requiredType;
        NodeRef caseRef;
        NodeRef element;
        AssocsConfigDto config;

        public BehaviourEnvironment(AssociationRef assocRef, AssocsConfigDto config) {
            this.config = config;
            if (config == null)  {
                return;
            }
            associationType = needAssocType(config);
            requiredType = config.getElementType();
            caseRef = associationType.getCaseNode(assocRef);
            element = associationType.getElementNode(assocRef);
        }

        public BehaviourEnvironment(ChildAssociationRef assocRef, AssocsConfigDto config) {
            this.config = config;
            if (config == null) {
                return;
            }
            associationType = needAssocType(config);
            if (associationType != AssociationType.CHILD) {
                return;
            }

            requiredType = config.getElementType();
            caseRef = associationType.getCaseNode(assocRef);
            element = associationType.getElementNode(assocRef);
        }

        public boolean isValid() {
            return config != null
                    && caseRef != null && nodeService.exists(caseRef)
                    && element != null && nodeService.exists(element)
                    && nodeService.hasAspect(caseRef, ICaseModel.ASPECT_CASE)
                    && RepoUtils.isSubClass(element, requiredType, nodeService, dictionaryService);
        }

        public void invokeCaseElementAddEvent() {
            if (!isValid()) {
                return;
            }
            caseElementService.invokeOnCaseElementAdd(caseRef, element, config);
        }

        public void invokeCaseElementRemoveEvent() {
            if (!isValid()) {
                return;
            }
            caseElementService.invokeOnCaseElementRemove(caseRef, element, config);
        }

    }

    public void onCaseElementAdd(AssociationRef assocRef, AssocsConfigDto config) {
        BehaviourEnvironment env = new BehaviourEnvironment(assocRef, config);
        env.invokeCaseElementAddEvent();
    }

    public void onCaseElementRemove(AssociationRef assocRef, AssocsConfigDto config) {
        BehaviourEnvironment env = new BehaviourEnvironment(assocRef, config);
        env.invokeCaseElementRemoveEvent();
    }

    public void onCaseElementAdd(ChildAssociationRef assocRef, boolean isNewNode, AssocsConfigDto config) {
        BehaviourEnvironment env = new BehaviourEnvironment(assocRef, config);
        env.invokeCaseElementAddEvent();
    }

    public void onCaseElementRemove(ChildAssociationRef assocRef, AssocsConfigDto config) {
        BehaviourEnvironment env = new BehaviourEnvironment(assocRef, config);
        env.invokeCaseElementRemoveEvent();
    }

}
