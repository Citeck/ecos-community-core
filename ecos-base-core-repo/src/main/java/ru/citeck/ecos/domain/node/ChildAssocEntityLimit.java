/*
 * Copyright (C) 2005-2010 Alfresco Software Limited.
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
