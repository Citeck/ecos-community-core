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
package ru.citeck.ecos.journals;

import com.fasterxml.jackson.databind.node.ObjectNode;
import org.alfresco.service.cmr.repository.NodeRef;
import org.alfresco.service.namespace.QName;
import ru.citeck.ecos.graphql.journal.JGqlPageInfoInput;
import ru.citeck.ecos.invariants.InvariantDefinition;
import ru.citeck.ecos.journals.invariants.CriterionInvariantsProvider;
import ru.citeck.ecos.records2.RecordRef;
import ru.citeck.ecos.records2.request.query.RecordsQueryResult;
import ru.citeck.ecos.records2.request.result.RecordsResult;

import java.io.InputStream;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface JournalService {
    
    String JOURNALS_CONTAINER = "journals";

    void deployJournalTypes(InputStream inputStream);

    JournalType getJournalType(String id);

    JournalType getJournalType(NodeRef nodeRef);

    JournalType needJournalType(String journalId);

    Optional<JournalType> getJournalForType(QName typeName);

    Collection<JournalType> getAllJournalTypes();

    void clearCache();

    List<InvariantDefinition> getCriterionInvariants(String journalId, String attribute);

    void registerCriterionInvariantsProvider(CriterionInvariantsProvider provider);

    NodeRef getJournalRef(String id);

    Long getRecordsCount(String journal);

    default RecordsQueryResult<RecordRef> getRecords(String journalId,
                                                     String query,
                                                     String language,
                                                     JGqlPageInfoInput pageInfo) {

        return getRecords(journalId, query, language, pageInfo, false);
    }

    RecordsQueryResult<RecordRef> getRecords(String journalId,
                                             String query,
                                             String language,
                                             JGqlPageInfoInput pageInfo,
                                             boolean debug);

    default RecordsResult<ObjectNode> getRecordsWithData(String journalId,
                                                         String query,
                                                         String language,
                                                         JGqlPageInfoInput pageInfo) {

        return getRecordsWithData(journalId, query, language, pageInfo, false);
    }

    RecordsResult<ObjectNode> getRecordsWithData(String journalId,
                                                 String query,
                                                 String language,
                                                 JGqlPageInfoInput pageInfo,
                                                 boolean debug);

    String getJournalGqlSchema(String journalId);

    String getUIType(String journalId);
}
