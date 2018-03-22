package ru.citeck.ecos.flowable.services.impl;

import com.fasterxml.jackson.databind.node.ObjectNode;
import org.alfresco.model.ContentModel;
import org.alfresco.service.cmr.repository.ContentReader;
import org.alfresco.service.cmr.repository.ContentService;
import org.alfresco.service.cmr.repository.NodeRef;
import org.alfresco.service.cmr.repository.NodeService;
import org.apache.commons.lang3.StringUtils;
import org.flowable.app.domain.editor.AbstractModel;
import org.flowable.app.domain.editor.Model;
import org.flowable.bpmn.BpmnAutoLayout;
import org.flowable.bpmn.converter.BpmnXMLConverter;
import org.flowable.bpmn.model.BpmnModel;
import org.flowable.editor.language.json.converter.BpmnJsonConverter;
import org.flowable.engine.ManagementService;
import org.flowable.engine.common.impl.cmd.CustomSqlExecution;
import org.flowable.engine.impl.cmd.AbstractCustomSqlExecution;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.util.CollectionUtils;
import ru.citeck.ecos.config.EcosConfigService;
import ru.citeck.ecos.flowable.FlowableIntegrationNotInitializedException;
import ru.citeck.ecos.flowable.services.FlowableModelerService;

import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import java.io.InputStream;
import java.util.Date;
import java.util.List;
import java.util.UUID;

/**
 * @author Roman Makarskiy
 */
public class FlowableModelerServiceImpl implements FlowableModelerService {

    private static final Logger logger = LoggerFactory.getLogger(FlowableModelerServiceImpl.class);

    private static final String IMPORT_CONFIG_KEY = "flowable-process-import-to-modeler-already-executed";
    private static final String FLOWABLE_ADMIN = "admin";
    private static final int DEFAULT_VERSION = 1;

    @Autowired
    @Qualifier("NodeService")
    private NodeService nodeService;

    @Autowired
    @Qualifier("ecosConfigService")
    private EcosConfigService ecosConfigService;

    @Autowired
    private ContentService contentService;
    private ManagementService managementService;

    private List<String> locations;

    @Override
    public void importProcessModel(NodeRef nodeRef) {
        if (!integrationIsInitialized()) {
            throw new FlowableIntegrationNotInitializedException("Integration is not initialized");
        }

        if (nodeRef == null || !nodeService.exists(nodeRef)) {
            throw new IllegalArgumentException("NodeRef does not exist");
        }

        ContentReader reader = contentService.getReader(nodeRef, ContentModel.PROP_CONTENT);
        if (reader == null) {
            throw new IllegalArgumentException("NodeRef do not have content");
        }

        InputStream inputStream = reader.getContentInputStream();
        logger.info("Start import process to Flowable Modeler from " + nodeRef);
        importProcessModel(inputStream);
        logger.info("Import finished");
    }

    @Override
    public void importProcessModel(InputStream inputStream) {
        if (!integrationIsInitialized()) {
            throw new FlowableIntegrationNotInitializedException("Integration is not initialized");
        }

        BpmnXMLConverter xmlConverter = new BpmnXMLConverter();
        XMLInputFactory xmlFactory = XMLInputFactory.newInstance();
        XMLStreamReader xmlStreamReader;

        try {
            xmlStreamReader = xmlFactory.createXMLStreamReader(inputStream);
        } catch (XMLStreamException e) {
            throw new IllegalStateException("Could not create XML streamReader.", e);
        }

        BpmnModel bpmnModel = xmlConverter.convertToBpmnModel(xmlStreamReader);
        if (CollectionUtils.isEmpty(bpmnModel.getProcesses())) {
            throw new IllegalArgumentException("No process found in metadata");
        }

        if (bpmnModel.getLocationMap().size() == 0) {
            BpmnAutoLayout bpmnLayout = new BpmnAutoLayout(bpmnModel);
            bpmnLayout.execute();
        }

        BpmnJsonConverter bpmnJsonConverter = new BpmnJsonConverter();
        ObjectNode modelNode = bpmnJsonConverter.convertToJson(bpmnModel);

        org.flowable.bpmn.model.Process process = bpmnModel.getMainProcess();
        String name = process.getId();
        if (StringUtils.isNotEmpty(process.getName())) {
            name = process.getName();
        }
        String description = process.getDocumentation();
        String key = process.getId();

        Model model = new Model();
        model.setId(UUID.randomUUID().toString());
        model.setName(name);
        model.setKey(key);
        model.setDescription(description);
        model.setCreated(new Date());
        model.setCreatedBy(FLOWABLE_ADMIN);
        model.setLastUpdated(new Date());
        model.setLastUpdatedBy(FLOWABLE_ADMIN);
        model.setVersion(DEFAULT_VERSION);
        model.setModelEditorJson(modelNode.toString());
        model.setModelType(AbstractModel.MODEL_TYPE_BPMN);

        CustomSqlExecution<ModelMapper, String> insertModelSqlExecution =
                new AbstractCustomSqlExecution<ModelMapper, String>(ModelMapper.class) {
                    @Override
                    public String execute(ModelMapper modelMapper) {
                        modelMapper.insertModel(model);
                        return null;
                    }
                };

        managementService.executeCustomSql(insertModelSqlExecution);

        logger.info("Process imported.");
    }

    @Override
    public void importProcessModel() {
        if (!integrationIsInitialized()) {
            throw new FlowableIntegrationNotInitializedException("Integration is not initialized");
        }

        logger.info("Start import process to Flowable Modeler from locations");

        if (CollectionUtils.isEmpty(locations)) {
            logger.info("Nothing import to Flowable Modeler");
            return;
        }

        locations.forEach(location -> {
            try {
                Resource resource = location.contains(":") ? new UrlResource(location) : new ClassPathResource(location);
                String path = resource.getURL().toString();
                logger.info("Import: " + path);

                importProcessModel(resource.getInputStream());
                ecosConfigService.setValue(IMPORT_CONFIG_KEY, Boolean.TRUE.toString(), null);
            } catch (Exception e) {
                ecosConfigService.setValue(IMPORT_CONFIG_KEY, Boolean.FALSE.toString(), null);
                throw new IllegalStateException("Could not import process to Flowable Modeler. Location: " + location, e);
            }
        });
    }

    public boolean integrationIsInitialized() {
        return this.managementService != null && modelTableIsExist();
    }

    public void setManagementService(ManagementService managementService) {
        this.managementService = managementService;
    }

    public void setLocations(List<String> locations) {
        this.locations = locations;
    }

    private boolean modelTableIsExist() {
        CustomSqlExecution<ModelMapper, String> sqlExecution =
                new AbstractCustomSqlExecution<ModelMapper, String>(ModelMapper.class) {
                    @Override
                    public String execute(ModelMapper modelMapper) {
                        return modelMapper.getTableSchema();
                    }
                };

        String tableSchema = managementService.executeCustomSql(sqlExecution);

        return StringUtils.isNotBlank(tableSchema);
    }
}
