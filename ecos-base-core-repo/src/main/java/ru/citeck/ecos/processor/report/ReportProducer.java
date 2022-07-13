/*
 * Copyright (C) 2008-2018 Citeck LLC.
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
package ru.citeck.ecos.processor.report;

import org.alfresco.repo.dictionary.constraint.ListOfValuesConstraint;
import org.alfresco.repo.i18n.MessageService;
import org.alfresco.repo.template.BaseContentNode.TemplateContentData;
import org.alfresco.repo.template.TemplateNode;
import org.alfresco.service.cmr.dictionary.*;
import org.alfresco.service.cmr.repository.NodeRef;
import org.alfresco.service.namespace.NamespaceService;
import org.alfresco.service.namespace.QName;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.extensions.surf.util.I18NUtil;
import org.springframework.util.ClassUtils;
import ru.citeck.ecos.attr.NodeAttributeService;
import ru.citeck.ecos.processor.AbstractDataBundleLine;
import ru.citeck.ecos.processor.DataBundle;
import ru.citeck.ecos.records2.RecordRef;
import ru.citeck.ecos.records2.RecordsService;
import ru.citeck.ecos.service.AlfrescoServices;
import ru.citeck.ecos.service.CiteckServices;
import ru.citeck.ecos.template.TemplateNodeService;
import ru.citeck.ecos.utils.DictionaryUtils;

import java.io.Serializable;
import java.text.SimpleDateFormat;
import java.util.*;

/**
 * Create list with report data to output
 *
 * @author Alexey Moiseev <alexey.moiseev@citeck.ru>
 */
public class ReportProducer extends AbstractDataBundleLine {

    private static final String NODES = "nodes";
    private static final String REPORT_COLUMNS = "reportColumns";
    private static final String COLUMN_ATTR = "attribute";
    private static final String COLUMN_DATE_FORMAT = "dateFormat";
    private static final String DEFAULT_DATE_FORMAT = "dd.MM.yyyy HH:mm";

    public static final String DATA_TYPE_ATTR = "type";
    public static final String DATA_VALUE_ATTR = "value";
    public static final String DATA_HYPERLINK_URL_ATTR = "url";

    public static final String DATA_TYPE_STRING = "String";
    public static final String DATA_TYPE_DOUBLE = "Double";
    public static final String DATA_TYPE_INTEGER = "Integer";
    public static final String DATA_TYPE_HYPERLINK = "Hyperlink";

    private static final String ROW_NUM = "rowNum";
    private static final String TASK_TYPE = "wfm:taskType";

    private TemplateNodeService templateNodeService;
    private NodeAttributeService nodeAttributeService;
    private NamespaceService namespaceService;
    private MessageService messageService;
    private DictionaryService dictionaryService;
    private RecordsService recordsService;

    private String personFirstName;
    private String personLastName;
    private String personMiddleName;

    @Override
    public void init() {
        super.init();
        nodeAttributeService = (NodeAttributeService) serviceRegistry.getService(CiteckServices.NODE_ATTRIBUTE_SERVICE);
        messageService = (MessageService) serviceRegistry.getService(AlfrescoServices.MESSAGE_SERVICE);
        namespaceService = serviceRegistry.getNamespaceService();
        dictionaryService = serviceRegistry.getDictionaryService();
    }

    @Autowired
    public void setRecordsService(RecordsService recordsService) {
        this.recordsService = recordsService;
    }

    @SuppressWarnings("unchecked")
    @Override
    public DataBundle process(DataBundle input) {
        Map<String, Object> model = input.needModel();

        HashMap<String, Object> newModel = new HashMap<>(model);

        List<Map<String, String>> reportColumns = (List<Map<String, String>>) model.get(REPORT_COLUMNS);
        List<NodeRef> nodes = (List<NodeRef>) model.get(NODES);
        newModel.put("reportData", produceReportData(reportColumns, nodes));

        return new DataBundle(newModel);
    }

    private List<List<Map<String, Object>>> produceReportData(List<Map<String, String>> reportColumns, List<NodeRef> nodes) {
        List<List<Map<String, Object>>> res = new ArrayList<>();

        if (reportColumns == null || reportColumns.isEmpty() || nodes == null || nodes.isEmpty()) {
            return res;
        }

        int i = 0;
        for (NodeRef node : nodes) {
            if (node == null) {
                continue;
            }

            List<Map<String, Object>> row = new ArrayList<>();

            for (Map<String, String> col : reportColumns) {
                Map<String, Object> data = new HashMap<>();

                // default type
                data.put(DATA_TYPE_ATTR, DATA_TYPE_STRING);

                String colAttribute = col.get(COLUMN_ATTR);
                String colDateFormat = col.get(COLUMN_DATE_FORMAT);

                if (colAttribute != null) {
                    if (colAttribute.equals(ROW_NUM)) {
                        data.put(DATA_TYPE_ATTR, DATA_TYPE_INTEGER);
                        data.put(DATA_VALUE_ATTR, i + 1);
                    } else if (!colAttribute.contains(".")) {
                        QName colAttrQName = QName.resolveToQName(namespaceService, colAttribute);
                        Object value = nodeAttributeService.getAttribute(node, colAttrQName);
                        QName typeQName = getAttributeTypeName(colAttrQName);
                        if (typeQName != null) {
                            if (typeQName.equals(DataTypeDefinition.DOUBLE)) {
                                data.put(DATA_TYPE_ATTR, DATA_TYPE_DOUBLE);
                            } else if (typeQName.equals(DataTypeDefinition.INT)) {
                                data.put(DATA_TYPE_ATTR, DATA_TYPE_INTEGER);
                            }
                        }
                        if (colAttribute.equals(TASK_TYPE)) {
                            value = recordsService.getAttribute(RecordRef.valueOf(node.toString()),
                                    TASK_TYPE).asText();
                        }

                        data.put(DATA_VALUE_ATTR, getFormattedValue(colAttrQName, value, colDateFormat));
                    } else {
                        String[] path = colAttribute.split("\\.");
                        HashMap<String, Object> value = evalNestedAttributeValue(node, path, 0);
                        QName colAttrQName = null;
                        if (value != null) {
                            data.putAll(value);
                            colAttrQName = QName.resolveToQName(namespaceService, path[path.length - 1]);
                        }
                        String formattedValue = getFormattedValue(colAttrQName, value != null ? value.get(DATA_VALUE_ATTR) : null, colDateFormat);
                        data.put(DATA_VALUE_ATTR, formattedValue);
                    }
                }

                row.add(data);
            }

            res.add(row);
            i++;
        }

        return res;
    }

    @SuppressWarnings("rawtypes")
    private HashMap<String, Object> evalNestedAttributeValue(NodeRef nodeRef, String[] path, int level) {

        if (path.length == 0) {
            return null;
        }

        QName colAttrQName = QName.resolveToQName(namespaceService, path[level]);
        Object value = nodeAttributeService.getAttribute(nodeRef, colAttrQName);

        if (path.length == level + 1) {
            HashMap<String, Object> data = new HashMap<>();
            QName typeQName = getAttributeTypeName(colAttrQName);
            if (typeQName != null) {
                if (typeQName.equals(DataTypeDefinition.DOUBLE)) {
                    data.put(DATA_TYPE_ATTR, DATA_TYPE_DOUBLE);
                } else if (typeQName.equals(DataTypeDefinition.INT)) {
                    data.put(DATA_TYPE_ATTR, DATA_TYPE_INTEGER);
                }
            }
            if (path[level].equals(TASK_TYPE)) {
                value = recordsService.getAttribute(RecordRef.valueOf(nodeRef.toString()),
                    TASK_TYPE).asText();
            }
            data.put(DATA_VALUE_ATTR, value);
            return data;
        } else {
            if (!(value instanceof ArrayList)) {
                return null;
            }
            ArrayList assocs = (ArrayList) value;
            if (assocs.size() != 1) {
                return null;
            }
            Object assoc = assocs.get(0);
            if (!(assoc instanceof NodeRef)) {
                return null;
            }
            return evalNestedAttributeValue((NodeRef) assoc, path, level + 1);
        }
    }

    @SuppressWarnings("rawtypes")
    private String getFormattedValue(QName property, Object value, String dateFormat) {
        StringBuilder res = new StringBuilder();

        if ((dateFormat == null) || (dateFormat.isEmpty())) {
            dateFormat = DEFAULT_DATE_FORMAT;
        }

        SimpleDateFormat sdf = new SimpleDateFormat(dateFormat);

        if (value == null) {
            return res.toString();
        }

        if (value instanceof Boolean) {
            boolean bValue = (Boolean) value;
            res = new StringBuilder(I18NUtil.getMessage(bValue ? "label.yes" : "label.no"));
        } else if (ClassUtils.isPrimitiveOrWrapper(value.getClass())) {
            res = new StringBuilder(String.valueOf(value));
        } else if (value instanceof String) {
            res = new StringBuilder(getLabel(property, (String) value));
        } else if (value instanceof List) {
            for (Object o : (List) value) {
                String itemValue = getFormattedValue(property, o, dateFormat);
                if ((res.length() > 0) && !itemValue.isEmpty()) {
                    res.append(", ");
                }
                res.append(itemValue);
            }
        } else if (value instanceof Date) {
            res = new StringBuilder(sdf.format((Date) value));
        } else if (value instanceof TemplateNode) {
            res = new StringBuilder(getNodeAsString((TemplateNode) value));
        } else if (value instanceof NodeRef) {
            res = new StringBuilder(getNodeAsString(new TemplateNode((NodeRef) value, serviceRegistry, null)));
        } else if (value instanceof QName) {
            res = new StringBuilder(shortQName(value.toString()));
        } else if (value instanceof TemplateContentData) {
            TemplateContentData data = (TemplateContentData) value;
            res = new StringBuilder("/api/node/" + (data.getUrl() != null ? data.getUrl().replaceFirst("/d/d/", "")
                    : ""));
        } else if (value.toString() != null) {
            res = new StringBuilder(value.toString());
        }

        return res.toString();
    }

    private String getLabel(QName property, String value) {
        if (dictionaryService.getProperty(property) == null) return value;

        Set<ConstraintDefinition> constraints =
                DictionaryUtils.getAllConstraintsForProperty(property, serviceRegistry.getDictionaryService());

        for (ConstraintDefinition constraintDefinition : constraints) {

            Constraint constraint = constraintDefinition.getConstraint();
            if (!(constraint instanceof ListOfValuesConstraint)) continue;

            ListOfValuesConstraint constraintList = (ListOfValuesConstraint) constraint;
            List<String> allowedValues = constraintList.getAllowedValues();

            if (!allowedValues.contains(value)) continue;

            return constraintList.getDisplayLabel(value, messageService);
        }
        return value;
    }

    private String getNodeAsString(TemplateNode node) {
        String result = "";

        if (node.hasPermission("Read")) {
            if (node.getTypeShort().equals("cm:person")) {
                Map<String, Serializable> properties = node.getProperties();
                if (properties != null) {
                    result = appendValue(result, properties, personLastName, "cm:lastName");
                    result = appendValue(result, properties, personFirstName, "cm:firstName");
                    result = appendValue(result, properties, personMiddleName, "cm:middleName");
                }
            } else if (node.getTypeShort().equals("cm:authorityContainer")) {
                Map<String, Serializable> properties = node.getProperties();
                Serializable displayProperty = properties.get("cm:authorityDisplayName");
                if (displayProperty == null) {
                    displayProperty = properties.get("cm:authorityName");
                }
                if (displayProperty == null) {
                    displayProperty = properties.get("cm:name");
                }
                result = displayProperty.toString();
            } else if (node.getProperties().get("cm:title") != null) {
                result = node.getProperties().get("cm:title").toString();
            } else if (node.getName() != null) {
                result = node.getName();
            }

        }

        return result;
    }

    private String appendValue(String result, Map<String, Serializable> props, String customKey, String defaultKey) {
        if (!customKey.startsWith("${") && props.get(customKey) != null) {
            result += (!result.isEmpty()) ? " " : "";
            result += props.get(customKey);
        } else if (props.get(defaultKey) != null) {
            result += (!result.isEmpty()) ? " " : "";
            result += props.get(defaultKey);
        }
        return result;
    }

    private QName getAttributeTypeName(QName qName) {
        PropertyDefinition property = serviceRegistry.getDictionaryService().getProperty(qName);
        if (property != null) {
            return property.getDataType().getName();
        }
        return null;
    }

    private String shortQName(String s) {
        QName qname = QName.resolveToQName(serviceRegistry.getNamespaceService(), s);
        return qname.toPrefixString(namespaceService);
    }

    public TemplateNodeService getTemplateNodeService() {
        return templateNodeService;
    }

    public void setTemplateNodeService(TemplateNodeService templateNodeService) {
        this.templateNodeService = templateNodeService;
    }

    public void setPersonFirstName(String personFirstName) {
        this.personFirstName = personFirstName;
    }

    public void setPersonLastName(String personLastName) {
        this.personLastName = personLastName;
    }

    public void setPersonMiddleName(String personMiddleName) {
        this.personMiddleName = personMiddleName;
    }
}
