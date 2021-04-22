package ru.citeck.ecos.records.source.common;

import ecos.com.google.common.cache.CacheBuilder;
import ecos.com.google.common.cache.CacheLoader;
import ecos.com.google.common.cache.LoadingCache;
import lombok.Data;
import org.alfresco.service.cmr.workflow.WorkflowDefinition;
import org.alfresco.service.cmr.workflow.WorkflowService;
import org.apache.commons.lang.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.extensions.surf.util.I18NUtil;
import org.springframework.stereotype.Component;
import ru.citeck.ecos.records.source.alf.AlfNodesRecordsDAO;
import ru.citeck.ecos.records2.graphql.meta.annotation.MetaAtt;
import ru.citeck.ecos.records2.graphql.meta.value.MetaEdge;
import ru.citeck.ecos.records2.graphql.meta.value.MetaField;
import ru.citeck.ecos.records2.source.common.AttributesMixin;

import javax.annotation.PostConstruct;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

@Component
public class WorkflowNameMixin implements
    AttributesMixin<Class<WorkflowNameMixin.WorkflowNameAtts>, WorkflowNameMixin.WorkflowNameAtts> {

    public static final String ATTRIBUTE_NAME = "wfm_workflowDisplayName";

    private final WorkflowService workflowService;
    private final AlfNodesRecordsDAO alfNodesRecordsDao;

    private final LoadingCache<String, String> workflowDispNameCache;

    @Autowired
    public WorkflowNameMixin(@Qualifier("WorkflowService")
                             WorkflowService workflowService,
                             AlfNodesRecordsDAO alfNodesRecordsDao) {

        this.workflowService = workflowService;
        this.alfNodesRecordsDao = alfNodesRecordsDao;

        workflowDispNameCache = CacheBuilder.newBuilder()
            .maximumSize(100)
            .expireAfterWrite(1, TimeUnit.MINUTES)
            .build(CacheLoader.from(this::getDispNameByWorkflowName));
    }

    @PostConstruct
    public void setup() {
        alfNodesRecordsDao.addAttributesMixin(this);
    }

    @Override
    public List<String> getAttributesList() {
        return Collections.singletonList(ATTRIBUTE_NAME);
    }

    @Override
    public Object getAttribute(String attribute, WorkflowNameAtts meta, MetaField field) throws Exception {
        if (StringUtils.isBlank(meta.workflowId)) {
            return "";
        }
        return workflowDispNameCache.getUnchecked(meta.workflowId);
    }

    @Override
    public MetaEdge getEdge(String attribute, WorkflowNameAtts meta, Supplier<MetaEdge> base, MetaField field) {
        return new MetaEdge() {
            @Override
            public String getName() {
                return attribute;
            }

            @Override
            public Object getValue(@NotNull MetaField field) throws Exception {
                return getAttribute(attribute, meta, field);
            }

            @Override
            public boolean isProtected() {
                return true;
            }

            @Override
            public boolean isSearchable() {
                return false;
            }

            @Override
            public String getTitle() {
                return I18NUtil.getMessage("wfm_workflowMirrorModel.property.wfm_workflowName.title");
            }
        };
    }

    @Override
    public Class<WorkflowNameAtts> getMetaToRequest() {
        return WorkflowNameAtts.class;
    }

    private String getDispNameByWorkflowName(String workflowName) {

        WorkflowDefinition wfDef = workflowService.getDefinitionByName(workflowName);

        String title = wfDef.getTitle();
        if (StringUtils.isBlank(title)) {
            title = workflowName;
        }
        return title;
    }

    @Data
    public static class WorkflowNameAtts {
        @MetaAtt("wfm:workflowName")
        private String workflowId;
    }
}
