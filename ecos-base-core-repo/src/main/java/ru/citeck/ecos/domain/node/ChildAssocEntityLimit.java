package ru.citeck.ecos.domain.node;

import org.alfresco.repo.domain.node.ChildAssocEntity;

/**
 * Bean for <b>alf_child_assoc</b> table with limit
 */
public class ChildAssocEntityLimit extends ChildAssocEntity {

    private Integer limit;

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder(512);
        sb.append(this.getClass().getSimpleName())
            .append("[ ID=").append(getId())
            .append(", typeQNameId=").append(getTypeQNameId())
            .append(", childNodeNameCrc=").append(getChildNodeNameCrc())
            .append(", childNodeName=").append(getChildNodeName())
            .append(", qnameNamespaceId=").append(getQnameNamespaceId())
            .append(", qnameLocalName=").append(getQnameLocalName())
            .append(", qnameCrc=").append(getQnameCrc())
            .append(", parentNode=").append(getParentNode())
            .append(", childNode=").append(getChildNode())
            .append(", limit=").append(getLimit())
            .append("]");
        return sb.toString();
    }

    public Integer getLimit() { return limit; }

    public void setLimit(Integer limit) {
        this.limit = limit;
    }
}
