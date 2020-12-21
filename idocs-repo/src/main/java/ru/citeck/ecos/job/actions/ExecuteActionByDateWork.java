package ru.citeck.ecos.job.actions;

import org.alfresco.error.AlfrescoRuntimeException;
import org.alfresco.service.ServiceRegistry;
import org.alfresco.service.cmr.dictionary.DictionaryService;
import org.alfresco.service.cmr.repository.NodeRef;
import org.alfresco.service.cmr.repository.NodeService;
import org.alfresco.service.cmr.repository.StoreRef;
import org.alfresco.service.cmr.search.*;
import org.alfresco.service.namespace.QName;
import org.alfresco.util.ISO8601DateFormat;
import org.alfresco.util.ParameterCheck;
import org.apache.commons.lang.time.DateUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import ru.citeck.ecos.icase.CaseStatusService;
import ru.citeck.ecos.service.EcosCoreServices;
import ru.citeck.ecos.utils.TimeUtils;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * @author Pavel Simonov
 * @author Roman Makarskiy
 */
public abstract class ExecuteActionByDateWork implements ActionProcessor {

    private static final Log logger = LogFactory.getLog(ExecuteActionByDateWork.class);

    private static final String TYPE_QUERY = "TYPE:\"%s\"";
    private static final String ASPECT_QUERY = "ASPECT:\"%s\"";
    private static final String DATE_QUERY = "@%s:[MIN TO \"%s\"]";
    private static final String STATUS_FIELD = "@icase\\:caseStatusAssoc_added:";
    private static final String ECOS_STATUS_FIELD = "@icase\\:caseStatusAssoc-prop:";

    protected ServiceRegistry serviceRegistry;

    private QName className;
    private boolean classIsAspect;

    private List<String> includeStatuses = null;
    private List<String> excludeStatuses = null;

    private QName dateField;
    private String dateOffset;
    private int datePrecision;

    private NodeService nodeService;
    private SearchService searchService;
    private ExecuteActionJobRegistry registry;
    private DictionaryService dictionaryService;
    private CaseStatusService caseStatusService;

    private boolean enabled = true;

    public void init() {
        ParameterCheck.mandatory("dateField", dateField);
        ParameterCheck.mandatory("className", className);

        registry.registerWork(this);

        this.nodeService = serviceRegistry.getNodeService();
        this.searchService = serviceRegistry.getSearchService();
        this.dictionaryService = serviceRegistry.getDictionaryService();
        this.caseStatusService = EcosCoreServices.getCaseStatusService(serviceRegistry);

        datePrecision = TimeUtils.getDurationPrecision(dateOffset);
        classIsAspect = dictionaryService.getClass(className).isAspect();
    }

    public List<NodeRef> queryNodes() {
        Date dateSearchValue = getDateSearchValue();
        String query = buildQuery(dateSearchValue);

        SearchParameters searchParameters = new SearchParameters();
        searchParameters.setLimit(0);
        searchParameters.setLimitBy(LimitBy.UNLIMITED);
        searchParameters.setMaxItems(-1);
        searchParameters.setMaxPermissionChecks(Integer.MAX_VALUE);
        searchParameters.setMaxPermissionCheckTimeMillis(Integer.MAX_VALUE);
        searchParameters.setLanguage(SearchService.LANGUAGE_FTS_ALFRESCO);
        searchParameters.addStore(StoreRef.STORE_REF_WORKSPACE_SPACESSTORE);
        searchParameters.setQueryConsistency(QueryConsistency.TRANSACTIONAL_IF_POSSIBLE);
        searchParameters.setQuery(query);

        if (logger.isDebugEnabled()) {
            logger.debug("Query: " + query);
        }

        ResultSet resultSet;
        try {
            resultSet = searchService.query(searchParameters);
        } catch (Exception e) {
            throw new AlfrescoRuntimeException("Nodes search failed. Query: '" + query + "'", e);
        }
        try {
            return filterNodes(resultSet.getNodeRefs(), dateSearchValue);
        } finally {
            if (resultSet != null) {
                resultSet.close();
            }
        }
    }

    private Date getDateSearchValue() {
        return TimeUtils.shiftDate(DateUtils.truncate(new Date(), datePrecision), dateOffset);
    }

    private String buildQuery(Date dateSearchValue) {
        StringBuilder sb = new StringBuilder();
        addQuery(sb, getTypeQuery());
        addQuery(sb, getStatusQuery());
        addQuery(sb, getDateQuery(dateSearchValue));
        return sb.toString();
    }

    private void addQuery(StringBuilder sb, String value) {
        if (!value.isEmpty()) {
            if (sb.length() > 0) {
                sb.append(" AND (").append(value).append(")");
            } else {
                sb.append("(").append(value).append(")");
            }
        }
    }

    private List<NodeRef> filterNodes(List<NodeRef> nodeRefs, Date dateSearchValue) {
        List<NodeRef> result = new ArrayList<>();
        for (NodeRef nodeRef : nodeRefs) {
            if (checkDate(nodeRef, dateSearchValue) && checkStatus(nodeRef) && checkType(nodeRef)) {
                result.add(nodeRef);
            }
        }
        return result;
    }

    private String getTypeQuery() {
        if (classIsAspect) {
            return String.format(ASPECT_QUERY, className);
        } else {
            return String.format(TYPE_QUERY, className);
        }
    }

    private String getStatusQuery() {
        String include = buildStatusesQuery(includeStatuses, true, " OR ");
        String exclude = buildStatusesQuery(excludeStatuses, false, " AND ");
        if (include.isEmpty() && exclude.isEmpty()) {
            return "";
        } else if (!include.isEmpty() && !exclude.isEmpty()) {
            return String.format("(%s) AND (%s)", include, exclude);
        } else {
            return include.isEmpty() ? exclude : include;
        }
    }

    private String getDateQuery(Date date) {
        return String.format(DATE_QUERY, dateField, ISO8601DateFormat.format(date));
    }

    private boolean checkDate(NodeRef nodeRef, Date dateSearchValue) {
        Date nodeDate = (Date) nodeService.getProperty(nodeRef, dateField);
        if (nodeDate != null) {
            nodeDate = DateUtils.truncate(nodeDate, datePrecision);
            return nodeDate.getTime() <= dateSearchValue.getTime();
        }
        return false;
    }

    private boolean checkStatus(NodeRef nodeRef) {
        if (includeStatuses == null && excludeStatuses == null) {
            return true;
        }
        String status = caseStatusService.getStatus(nodeRef);
        return (includeStatuses == null || includeStatuses.contains(status)) &&
                (excludeStatuses == null || !excludeStatuses.contains(status));
    }

    private boolean checkType(NodeRef nodeRef) {
        if (classIsAspect) {
            return nodeService.hasAspect(nodeRef, className);
        } else {
            return className.equals(nodeService.getType(nodeRef));
        }
    }

    private String buildStatusesQuery(List<String> statuses, boolean required, String delimiter) {
        if (statuses == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (String status : statuses) {
            NodeRef statusRef = getStatus(status);
            if (sb.length() > 0) {
                sb.append(delimiter);
            }
            if (!required) {
                sb.append("NOT ");
            }
            sb.append("(")
            .append(STATUS_FIELD).append('"').append(statusRef).append('"')
            .append(" OR ")
            .append(ECOS_STATUS_FIELD).append('"').append(status).append('"')
            .append(")");
        }
        return sb.toString();
    }

    private NodeRef getStatus(String name) {
        NodeRef statusRef = caseStatusService.getStatusByName(name);
        if (statusRef == null) {
            throw new AlfrescoRuntimeException("Status not found: " + name);
        }
        return statusRef;
    }

    @Override
    public String toString() {
        return String.format("ExecuteActionByDateWork:[className:%s, includeStatuses:%s, excludeStatuses:%s, " +
                        "dateField:%s, offset:%s]",
                className, includeStatuses, excludeStatuses, dateField, dateOffset);
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setClassName(QName className) {
        this.className = className;
    }

    public void setIncludeStatuses(List<String> includeStatuses) {
        this.includeStatuses = includeStatuses;
    }

    public void setExcludeStatuses(List<String> excludeStatuses) {
        this.excludeStatuses = excludeStatuses;
    }

    public void setDateField(QName dateField) {
        this.dateField = dateField;
    }

    public void setDateOffset(String dateOffset) {
        this.dateOffset = dateOffset;
    }

    public void setExecuteActionJobRegistry(ExecuteActionJobRegistry executeActionJobRegistry) {
        this.registry = executeActionJobRegistry;
    }

    public void setServiceRegistry(ServiceRegistry serviceRegistry) {
        this.serviceRegistry = serviceRegistry;
    }
}
