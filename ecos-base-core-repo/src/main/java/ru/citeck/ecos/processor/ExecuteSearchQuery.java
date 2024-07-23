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
package ru.citeck.ecos.processor;

import org.alfresco.service.cmr.repository.NodeRef;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import ru.citeck.ecos.records2.RecordRef;
import ru.citeck.ecos.records2.RecordsService;
import ru.citeck.ecos.records2.request.query.QueryConsistency;
import ru.citeck.ecos.records2.request.query.RecordsQuery;
import ru.citeck.ecos.records2.request.query.RecordsQueryResult;
import ru.citeck.ecos.search.CriteriaSearchResults;
import ru.citeck.ecos.search.CriteriaSearchService;
import ru.citeck.ecos.search.SearchCriteria;
import ru.citeck.ecos.webapp.api.entity.EntityRef;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author Anton Fateev <anton.fateev@citeck.ru>
 */
public class ExecuteSearchQuery extends AbstractDataBundleLine {

    private static final String PREDICATE = "predicate";
    private static final String SEARCH_CRITERIA = "searchCriteria";

    private static final String LANGUAGE = "language";
    private static final String HAS_MORE = "hasMore";
    private static final String TOTAL_COUNT = "totalCount";
    private static final String NODES = "nodes";

    private String language;

    private CriteriaSearchService searchService;

    private RecordsService recordsService;

    @Override
    public DataBundle process(DataBundle input) {
        Map<String, Object> model = input.needModel();

        if (PREDICATE.equals(language) && model.get(PREDICATE) != null) {
            return helper.getDataBundle(helper.getContentReader(input), getModelForPredicate(model));
        }

        return helper.getDataBundle(helper.getContentReader(input), getModelForSearchCriteria(model));
    }

    private HashMap<String, Object> getModelForSearchCriteria(Map<String, Object> oldModel) {
        SearchCriteria searchCriteria = (SearchCriteria) oldModel.get(SEARCH_CRITERIA);
        CriteriaSearchResults results = searchService.query(searchCriteria, language);
        List<NodeRef> nodeRefs = results.getResults();

        return getNewModel(oldModel, results.hasMore(), results.getTotalCount(), nodeRefs);
    }

    private HashMap<String, Object> getModelForPredicate(Map<String, Object> oldModel) {
        RecordsQuery query = new RecordsQuery();
        query.setQuery(oldModel.get(PREDICATE));
        query.setConsistency(QueryConsistency.EVENTUAL);
        query.setLanguage(PREDICATE);

        RecordsQueryResult<EntityRef> result = recordsService.queryRecords(query);
        List<EntityRef> recordRefs = result.getRecords();

        List<NodeRef> nodeRefs = new ArrayList<>();
        for (EntityRef recordRef : recordRefs) {
            nodeRefs.add(new NodeRef(recordRef.getLocalId()));
        }

        return getNewModel(oldModel, result.getHasMore(), result.getTotalCount(), nodeRefs);
    }

    private HashMap<String, Object> getNewModel(Map<String, Object> oldModel,
                                                Boolean hasMore,
                                                Long totalCounts,
                                                List<NodeRef> refs) {

        HashMap<String, Object> newModel = new HashMap<>(oldModel);
        newModel.put(HAS_MORE, hasMore);
        newModel.put(LANGUAGE, language);
        newModel.put(TOTAL_COUNT, totalCounts);
        newModel.put(NODES, refs);

        return newModel;
    }

    public void setLanguage(String language) {
        this.language = language;
    }

    @Autowired
    public void setRecordsService(RecordsService recordsService) {
        this.recordsService = recordsService;
    }

    @Autowired
    @Qualifier("criteriaSearchService")
    public void setSearchService(CriteriaSearchService searchService) {
        this.searchService = searchService;
    }
}
