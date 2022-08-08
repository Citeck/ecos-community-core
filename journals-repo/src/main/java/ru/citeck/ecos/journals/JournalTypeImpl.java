/*
 * Copyright (C) 2008-2019 Citeck LLC.
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

import org.alfresco.service.ServiceRegistry;
import org.alfresco.service.namespace.NamespacePrefixResolver;
import org.apache.commons.lang.StringUtils;
import ru.citeck.ecos.journals.xml.Formatter;
import ru.citeck.ecos.journals.xml.*;
import ru.citeck.ecos.records2.RecordRef;
import ru.citeck.ecos.search.SearchCriteriaSettingsRegistry;
import ru.citeck.ecos.utils.EcosI18NUtils;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

class JournalTypeImpl implements JournalType {

    private static final String UI_ACTION_SOURCE = "action";
    private static final String UI_ACTION_APP_NAME = "uiserv";

    private final String id;
    private final String datasource;

    private final String groupBy;
    private final String predicate;

    private final Map<String, String> options;
    private final List<String> attributes;
    private final List<RecordRef> actions;
    private final List<JournalGroupAction> groupActions;

    private final BitSet defaultAttributes;
    private final BitSet visibleAttributes;
    private final BitSet searchableAttributes;
    private final BitSet sortableAttributes;
    private final BitSet groupableAttributes;

    private final Map<String, Map<String, String>> attributeOptions;
    private final Map<String, List<JournalBatchEdit>> batchEdit;
    private final Map<String, JournalCriterion> criterion;
    private final Map<String, JournalFormatter> formatters;

    private final List<CreateVariant> createVariants;

    public JournalTypeImpl(Journal journal, NamespacePrefixResolver prefixResolver, ServiceRegistry serviceRegistry,
                           SearchCriteriaSettingsRegistry searchCriteriaSettingsRegistry) {

        this.id = journal.getId();
        this.options = Collections.unmodifiableMap(getOptions(journal.getOption()));
        this.groupActions = Collections.unmodifiableList(getGroupActions(journal, serviceRegistry));
        this.actions = Collections.unmodifiableList(getActions(journal));
        this.predicate = journal.getPredicate() != null ? journal.getPredicate().getValue() : null;
        this.groupBy = journal.getGroupBy() != null ? journal.getGroupBy().getValue() : null;
        this.createVariants = convertCreateVariants(journal.getCreate());
        this.formatters = new ConcurrentHashMap<>();

        List<Header> headers = journal.getHeaders().getHeader();
        List<String> allAttributes = new ArrayList<>(headers.size());

        String datasource = journal.getDatasource();
        this.datasource = StringUtils.isNotBlank(datasource) ? datasource : "";

        batchEdit = new HashMap<>();
        criterion = new HashMap<>();

        defaultAttributes = new BitSet(allAttributes.size());
        visibleAttributes = new BitSet(allAttributes.size());
        searchableAttributes = new BitSet(allAttributes.size());
        sortableAttributes = new BitSet(allAttributes.size());
        groupableAttributes = new BitSet(allAttributes.size());

        this.attributeOptions = new TreeMap<>();

        int index = 0;
        for (Header header : headers) {

            String attributeKey = header.getKey();

            allAttributes.add(attributeKey);
            if (header.isDefault()) {
                defaultAttributes.set(index);
            }
            if (header.isVisible()) {
                visibleAttributes.set(index);
            }
            if (header.isSearchable()) {
                searchableAttributes.set(index);
            }
            if (header.isSortable()) {
                sortableAttributes.set(index);
            }
            if (header.isGroupable()) {
                groupableAttributes.set(index);
            }

            JournalFormatter formatter = readFormatter(header.getFormatter());
            if (formatter != null) {
                formatters.put(header.getKey(), formatter);
            }

            Map<String, String> headerOptions = Collections.unmodifiableMap(getOptions(header.getOption()));
            this.attributeOptions.put(attributeKey, headerOptions);

            List<JournalBatchEdit> attributeBatchEdit = new ArrayList<>();
            for (BatchEdit batchEdit : header.getBatchEdit()) {
                attributeBatchEdit.add(new JournalBatchEdit(batchEdit, journal.getId(),
                        attributeKey, prefixResolver,
                        serviceRegistry));
            }

            batchEdit.put(attributeKey, attributeBatchEdit);
            criterion.put(attributeKey, new JournalCriterion(attributeKey, header.getCriterion(), journal.getId(),
                    prefixResolver, searchCriteriaSettingsRegistry));

            index++;
        }

        this.attributes = Collections.unmodifiableList(allAttributes);
    }

    private JournalFormatter readFormatter(Formatter formatter) {

        if (formatter == null) {
            return null;
        }

        JournalFormatter result = new JournalFormatter();
        result.setName(formatter.getName());
        result.setParams(getOptions(formatter.getParam()));

        return result;
    }

    private List<CreateVariant> convertCreateVariants(CreateVariants variants) {

        if (variants == null) {
            return Collections.emptyList();
        }

        List<ru.citeck.ecos.journals.xml.CreateVariant> variantsList = variants.getVariant();
        List<CreateVariant> resultVariants = new ArrayList<>();

        if (variantsList != null) {

            variantsList.forEach(v -> {

                CreateVariant resultVariant = new CreateVariant();
                resultVariant.setTitle(EcosI18NUtils.getMLText(v.getTitle()));
                resultVariant.setFormKey(v.getFormKey());
                resultVariant.setRecordRef(v.getRecordRef());

                Map<String, String> attributes = new HashMap<>();
                for (Option opt : v.getAttribute()) {
                    attributes.put(opt.getName(), opt.getValue());
                }
                resultVariant.setAttributes(attributes);

                resultVariants.add(resultVariant);
            });
        }

        return resultVariants;
    }

    @Override
    public String getId() {
        return id;
    }

    @Override
    public Map<String, String> getOptions() {
        return options;
    }

    @Override
    public List<String> getAttributes() {
        return attributes;
    }

    @Override
    public List<String> getDefaultAttributes() {
        return getFeaturedAttributes(defaultAttributes);
    }

    @Override
    public List<String> getVisibleAttributes() {
        return getFeaturedAttributes(visibleAttributes);
    }

    @Override
    public List<String> getSearchableAttributes() {
        return getFeaturedAttributes(searchableAttributes);
    }

    @Override
    public List<String> getSortableAttributes() {
        return getFeaturedAttributes(sortableAttributes);
    }

    @Override
    public List<String> getGroupableAttributes() {
        return getFeaturedAttributes(groupableAttributes);
    }

    @Override
    public Map<String, String> getAttributeOptions(String attributeKey) {
        Map<String, String> result = attributeOptions.get(attributeKey);
        return result != null ? result : Collections.emptyMap();
    }

    @Override
    public boolean isAttributeDefault(String attributeKey) {
        return checkFeature(attributeKey, defaultAttributes);
    }

    @Override
    public boolean isAttributeVisible(String attributeKey) {
        return checkFeature(attributeKey, visibleAttributes);
    }

    @Override
    public boolean isAttributeSearchable(String attributeKey) {
        return checkFeature(attributeKey, searchableAttributes);
    }

    @Override
    public boolean isAttributeSortable(String attributeKey) {
        return checkFeature(attributeKey, sortableAttributes);
    }

    @Override
    public boolean isAttributeGroupable(String attributeKey) {
        return checkFeature(attributeKey, groupableAttributes);
    }

    @Override
    public List<JournalBatchEdit> getBatchEdit(String attributeKey) {
        List<JournalBatchEdit> batchEdit = this.batchEdit.get(attributeKey);
        List<JournalBatchEdit> result = new ArrayList<>(batchEdit.size());
        for (JournalBatchEdit edit : batchEdit) {
            if (edit.getEvaluator().evaluate()) {
                result.add(edit);
            }
        }
        return result;
    }

    @Override
    public List<RecordRef> getActions() {
       return actions;
    }

    @Override
    public List<JournalGroupAction> getGroupActions() {
        List<JournalGroupAction> result = new ArrayList<>(groupActions.size());
        for (JournalGroupAction action : groupActions) {
            if (action.getEvaluator().evaluate()) {
                result.add(action);
            }
        }
        return result;
    }

    @Override
    public JournalCriterion getCriterion(String attributeKey) {
        return criterion.get(attributeKey);
    }

    private List<String> getFeaturedAttributes(BitSet featuredAttributes) {
        List<String> result = new LinkedList<>();
        for (int i = featuredAttributes.nextSetBit(0); i >= 0; i = featuredAttributes.nextSetBit(i + 1)) {
            result.add(attributes.get(i));
        }
        return Collections.unmodifiableList(result);
    }

    private boolean checkFeature(String attributeKey, BitSet featuredAttributes) {
        int index = attributes.indexOf(attributeKey);
        return index >= 0 && featuredAttributes.get(index);
    }

    private static Map<String, String> getOptions(List<Option> options) {
        if (options == null) {
            return Collections.emptyMap();
        }
        Map<String, String> optionMap = new HashMap<>(options.size());
        for (Option option : options) {
            optionMap.put(option.getName(), option.getValue().trim());
        }
        return optionMap;
    }

    private static List<RecordRef> getActions(Journal journal) {
        if (journal.getActions() == null) {
            return Arrays.asList(
                RecordRef.create(UI_ACTION_APP_NAME, UI_ACTION_SOURCE, "download-zip"),
                RecordRef.create(UI_ACTION_APP_NAME, UI_ACTION_SOURCE, "content-download"),
                RecordRef.create(UI_ACTION_APP_NAME, UI_ACTION_SOURCE, "edit"),
                RecordRef.create(UI_ACTION_APP_NAME, UI_ACTION_SOURCE, "delete"),
                RecordRef.create(UI_ACTION_APP_NAME, UI_ACTION_SOURCE, "view-dashboard"),
                RecordRef.create(UI_ACTION_APP_NAME, UI_ACTION_SOURCE, "view-dashboard-in-background")
            );
        }
        return journal.getActions().getAction()
                .stream()
                .map(action -> RecordRef.valueOf(action.getRef()))
                .collect(Collectors.toList());
    }

    private static List<JournalGroupAction> getGroupActions(Journal journal, ServiceRegistry serviceRegistry) {
        List<JournalGroupAction> result = new ArrayList<>();
        if (journal.getGroupActions() != null) {
            List<GroupAction> rawActions = journal.getGroupActions().getAction();
            for (GroupAction action : rawActions) {
                result.add(new JournalGroupAction(action, journal.getId(), serviceRegistry));
            }
        }
        return result;
    }

    public String getGroupBy() {
        return groupBy;
    }

    public String getPredicate() {
        return predicate;
    }

    @Override
    public JournalFormatter getFormatter(String attributeKey) {
        return formatters.get(attributeKey);
    }

    @Override
    public String getDataSource() {
        return datasource;
    }

    @Override
    public List<CreateVariant> getCreateVariants() {
        return createVariants;
    }
}
