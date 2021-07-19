package ru.citeck.ecos.graphql.node;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.alfresco.model.ContentModel;
import org.alfresco.repo.node.MLPropertyInterceptor;
import org.alfresco.repo.security.authentication.AuthenticationUtil;
import org.alfresco.repo.security.permissions.AccessDeniedException;
import org.alfresco.service.cmr.dictionary.AssociationDefinition;
import org.alfresco.service.cmr.dictionary.DictionaryService;
import org.alfresco.service.cmr.dictionary.PropertyDefinition;
import org.alfresco.service.cmr.repository.AssociationRef;
import org.alfresco.service.cmr.repository.ChildAssociationRef;
import org.alfresco.service.cmr.repository.NodeRef;
import org.alfresco.service.namespace.QName;
import ru.citeck.ecos.graphql.AlfGqlContext;

import java.io.Serializable;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Slf4j
public class GqlAlfNode {

    private final NodeRef nodeRef;

    @Getter(lazy = true)
    private final QName type = evalType();
    @Getter(lazy = true)
    private final Map<QName, Serializable> properties = evalProperties();
    @Getter(lazy = true)
    private final Map<QName, List<NodeRef>> targetAssocs = evalTargetAssocs();
    @Getter(lazy = true)
    private final Map<QName, List<NodeRef>> childAssocs = evalChildAssocs();
    @Getter(lazy = true)
    private final List<GqlQName> aspects = evalAspects();
    @Getter(lazy = true)
    private final GqlAlfNode parent = evalParent();

    private final Map<QName, List<NodeRef>> assocs = new ConcurrentHashMap<>();
    private final Map<QName, Attribute> attributes = new ConcurrentHashMap<>();

    private AlfGqlContext context;

    public GqlAlfNode(NodeRef nodeRef, AlfGqlContext context) {
        this.nodeRef = nodeRef;
        this.context = context;
    }

    public String displayName() {

        QName type = getType();

        if (type.equals(ContentModel.TYPE_PERSON)) {

            Attribute firstName = getAttribute(ContentModel.PROP_FIRSTNAME);
            Attribute lastName = getAttribute(ContentModel.PROP_LASTNAME);

            Optional value = firstName.value();
            StringBuilder result = new StringBuilder();
            if (value.isPresent()) {
                result.append(value.get());
            }
            value = lastName.value();
            if (value.isPresent()) {
                if (result.length() > 0) {
                    result.append(" ");
                }
                result.append(value.get());
            }

            if (result.length() == 0) {
                Attribute userName = getAttribute(ContentModel.PROP_USERNAME);
                result.append(userName.value().orElse(nodeRef.toString()));
            }

            return result.toString();

        } else if (type.equals(ContentModel.TYPE_AUTHORITY_CONTAINER)) {

            Attribute displayName = getAttribute(ContentModel.PROP_AUTHORITY_DISPLAY_NAME);
            Attribute authorityName = getAttribute(ContentModel.PROP_AUTHORITY_NAME);

            return displayName.value().orElseGet(() -> authorityName.value().orElse(null));

        } else {
            Attribute title = getAttribute(ContentModel.PROP_TITLE);
            Attribute name = getAttribute(ContentModel.PROP_NAME);
            return title.value().orElseGet(() -> name.value().orElse(null));
        }
    }

    public GqlAlfNode parent() {
        return getParent();
    }

    public List<GqlQName> aspects() {
        return getAspects();
    }

    public List<String> aspectNames() {
        return getAspects().stream()
                           .map(GqlQName::shortName)
                           .collect(Collectors.toList());
    }

    public String nodeRef() {
        return nodeRef.toString();
    }

    public String type() {
        return getType().toPrefixString();
    }

    public Optional<GqlQName> typeQName() {
        return context.getQName(getType());
    }

    public boolean isContainer() {
        DictionaryService dd = context.getDictionaryService();
        QName type = getType();
        return dd.isSubClass(type, ContentModel.TYPE_FOLDER) &&
              !dd.isSubClass(type, ContentModel.TYPE_SYSTEM_FOLDER);
    }

    public boolean isDocument() {
        DictionaryService dd = context.getDictionaryService();
        return dd.isSubClass(getType(), ContentModel.TYPE_CONTENT);
    }

    public Attribute attribute(String name) {
        return getAttribute(QName.resolveToQName(context.getNamespaceService(), name));
    }

    private Attribute getAttribute(QName qname) {
        if (qname == null) {
            return null;
        }
        return attributes.computeIfAbsent(qname, attName -> {
            QName prefixedName = attName.getPrefixedQName(context.getNamespaceService());
            return new Attribute(prefixedName, getAttributeType(prefixedName), this, context);
        });
    }

    public List<Attribute> attributes(List<Attribute.Type> types,
                                      List<String> names) {
        if (names != null) {
            List<Attribute> result = new ArrayList<>();
            names.forEach(name -> result.add(attribute(name)));
            return result;
        } else {
            if (types == null) {
                types = Collections.singletonList(Attribute.Type.PROP);
            }
            return getAttributesByTypes(types);
        }
    }

    private List<Attribute> getAttributesByTypes(List<Attribute.Type> types) {

        if (types.contains(Attribute.Type.PROP)) {
            addAttributes(getProperties(), Attribute.Type.PROP);
        }

        if (types.contains(Attribute.Type.ASSOC)) {
            addAttributes(getTargetAssocs(), Attribute.Type.ASSOC);
        }

        if (types.contains(Attribute.Type.CHILD_ASSOC)) {
            addAttributes(getChildAssocs(), Attribute.Type.CHILD_ASSOC);
        }

        return new ArrayList<>(attributes.values());
    }

    private void addAttributes(Map<QName, ?> toAdd, Attribute.Type attrType) {
        toAdd.forEach((key, value) -> addAttribute(key, value, attrType));
    }

    private void addAttribute(QName name, Object value, Attribute.Type attrType) {
        if (!attributes.containsKey(name)) {
            QName prefixedQName = name.getPrefixedQName(context.getNamespaceService());
            Attribute attr = new Attribute(prefixedQName, value, attrType, this, context);
            attributes.put(prefixedQName, attr);
        }
    }

    public Object getAttributeValue(QName name, Attribute.Type type) {
        switch (type) {
            case PROP:
                return getProperties().get(name);
            case ASSOC:
                return getTargetAssoc(name);
            case CHILD_ASSOC:
                return getChildAssoc(name);
        }
        return null;
    }

    private List<NodeRef> getTargetAssoc(QName assocName) {
        return assocs.computeIfAbsent(assocName, name ->
            context.getNodeService()
                   .getTargetAssocs(nodeRef, name)
                   .stream()
                   .map(AssociationRef::getTargetRef)
                   .collect(Collectors.toCollection(ArrayList::new))
        );
    }

    private List<NodeRef> getChildAssoc(QName assocName) {
        return assocs.computeIfAbsent(assocName, name ->
            context.getNodeService()
                   .getChildAssocs(nodeRef, name, q -> true)
                   .stream()
                   .map(ChildAssociationRef::getChildRef)
                   .collect(Collectors.toCollection(ArrayList::new))
        );
    }

    private GqlAlfNode evalParent() {
        ChildAssociationRef assoc = context.getNodeService().getPrimaryParent(nodeRef);
        return new GqlAlfNode(assoc.getParentRef(), context);
    }

    private List<GqlQName> evalAspects() {
        return context.getQNames(context.getNodeService().getAspects(nodeRef));
    }

    private QName evalType() {
        return context.getNodeService()
                      .getType(nodeRef)
                      .getPrefixedQName(context.getNamespaceService());
    }

    private Map<QName, Serializable> evalProperties() {
        boolean mlAwareBefore = MLPropertyInterceptor.setMLAware(true);
        try {
            return context.getNodeService().getProperties(nodeRef);
        } catch (AccessDeniedException e) {
            log.error("Access denied for user '" + AuthenticationUtil.getFullyAuthenticatedUser() +
                "' to nodeRef: '" + nodeRef + "'. Empty properties will be returned.");
            return Collections.emptyMap();
        } finally {
            MLPropertyInterceptor.setMLAware(mlAwareBefore);
        }
    }

    private Map<QName, List<NodeRef>> evalTargetAssocs() {

        List<AssociationRef> assocRefs = context.getNodeService().getTargetAssocs(nodeRef, q -> true);

        Map<QName, List<NodeRef>> result = new HashMap<>();
        assocRefs.forEach(assocRef -> {
            List<NodeRef> nodes = result.computeIfAbsent(assocRef.getTypeQName(),
                                                         k -> new ArrayList<>(4));
            nodes.add(assocRef.getTargetRef());
        });

        result.forEach(assocs::putIfAbsent);

        return result;
    }

    private Map<QName, List<NodeRef>> evalChildAssocs() {

        List<ChildAssociationRef> assocsRefs = context.getNodeService().getChildAssocs(nodeRef, q -> true, q -> true);

        Map<QName, List<NodeRef>> result = new HashMap<>();
        assocsRefs.forEach(assocRef -> {
            List<NodeRef> nodes = result.computeIfAbsent(assocRef.getTypeQName(),
                                                         k -> new ArrayList<>(4));
            nodes.add(assocRef.getChildRef());
        });

        result.forEach(assocs::putIfAbsent);

        return result;
    }

    private Attribute.Type getAttributeType(QName name) {
        Attribute.Type result;
        PropertyDefinition propDef = context.getDictionaryService().getProperty(name);
        if (propDef == null) {
            AssociationDefinition assocDef = context.getDictionaryService().getAssociation(name);
            if (assocDef == null) {
                result = Attribute.Type.UNKNOWN;
            } else {
                result = assocDef.isChild() ? Attribute.Type.CHILD_ASSOC : Attribute.Type.ASSOC;
            }
        } else {
            result = Attribute.Type.PROP;
        }
        return result;
    }

    @Override
    public String toString() {
        return nodeRef.toString();
    }
}
