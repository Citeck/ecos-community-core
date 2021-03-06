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

import org.alfresco.repo.policy.ClassPolicy;
import org.alfresco.service.cmr.repository.NodeRef;
import org.alfresco.service.namespace.QName;

import ru.citeck.ecos.icase.element.config.ElementConfigDto;
import ru.citeck.ecos.service.CiteckServices;

public interface CaseElementPolicies {
    
    interface OnCaseElementAddPolicy extends ClassPolicy {
        String NAMESPACE = CiteckServices.CITECK_NAMESPACE;
        QName QNAME = QName.createQName(NAMESPACE, "onCaseElementAdd");

        //without this fields TransactionBehaviourQueue takes invocations
        //with a same first argument as totally equals and processes only one
        Arg ARG_0 = Arg.KEY;
        Arg ARG_1 = Arg.KEY;
        Arg ARG_2 = Arg.KEY;

        /**
         * Called after case element was added.
         * 
         * @param caseRef case to which element was added
         * @param element element which was added
         * @param config element type configuration
         */
        void onCaseElementAdd(NodeRef caseRef, NodeRef element, ElementConfigDto config);
    }

    interface OnCaseElementUpdatePolicy extends ClassPolicy {
        String NAMESPACE = CiteckServices.CITECK_NAMESPACE;
        QName QNAME = QName.createQName(NAMESPACE, "onCaseElementUpdate");

        //without this fields TransactionBehaviourQueue takes invocations
        //with a same first argument as totally equals and processes only one
        Arg ARG_0 = Arg.KEY;
        Arg ARG_1 = Arg.KEY;
        Arg ARG_2 = Arg.KEY;

        /**
         * Called after case element was updated.
         * 
         * @param caseRef case in which element was updated
         * @param element element which was updated
         * @param config element type configuration
         */
        void onCaseElementUpdate(NodeRef caseRef, NodeRef element, ElementConfigDto config);
        
    }

    interface OnCaseElementRemovePolicy extends ClassPolicy {
        String NAMESPACE = CiteckServices.CITECK_NAMESPACE;
        QName QNAME = QName.createQName(NAMESPACE, "onCaseElementRemove");

        //without this fields TransactionBehaviourQueue takes invocations
        //with a same first argument as totally equals and processes only one
        Arg ARG_0 = Arg.KEY;
        Arg ARG_1 = Arg.KEY;
        Arg ARG_2 = Arg.KEY;

        /**
         * Called after case element was removed.
         * 
         * @param caseRef case to which element was removed
         * @param element element which was removed
         * @param config element type configuration
         */
        void onCaseElementRemove(NodeRef caseRef, NodeRef element, ElementConfigDto config);

    }
}
