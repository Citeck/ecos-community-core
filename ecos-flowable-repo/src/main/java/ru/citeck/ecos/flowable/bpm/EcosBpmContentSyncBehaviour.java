package ru.citeck.ecos.flowable.bpm;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.Getter;
import org.alfresco.model.ContentModel;
import org.alfresco.repo.content.ContentServicePolicies;
import org.alfresco.repo.node.NodeServicePolicies;
import org.alfresco.repo.policy.Behaviour;
import org.alfresco.service.cmr.repository.*;
import org.alfresco.service.namespace.QName;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.flowable.ui.modeler.domain.Model;
import org.flowable.bpmn.BpmnAutoLayout;
import org.flowable.bpmn.converter.BpmnXMLConverter;
import org.flowable.bpmn.model.BpmnModel;
import org.flowable.bpmn.model.Process;
import org.flowable.bpmn.model.StartEvent;
import org.flowable.editor.language.json.converter.BpmnJsonConverter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.extensions.webscripts.Format;
import ru.citeck.ecos.behavior.base.AbstractBehaviour;
import ru.citeck.ecos.behavior.base.PolicyMethod;
import ru.citeck.ecos.model.EcosBpmModel;

import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.Serializable;
import java.util.*;
import java.util.function.Function;

public class EcosBpmContentSyncBehaviour extends AbstractBehaviour
                                         implements NodeServicePolicies.OnCreateNodePolicy,
                                                    ContentServicePolicies.OnContentPropertyUpdatePolicy,
                                                    NodeServicePolicies.OnUpdatePropertiesPolicy {

    private static final QName PROP_XML = ContentModel.PROP_CONTENT;
    private static final QName PROP_JSON = EcosBpmModel.PROP_JSON_MODEL;
    private static final QName PROP_THUMBNAIL = EcosBpmModel.PROP_THUMBNAIL;
    private static final QName PROP_MODEL_IMG = EcosBpmModel.PROP_MODEL_IMAGE;

    private static final int THUMBNAIL_WIDTH = 300;

    private static final String ENCODING = "UTF-8";
    private static final String THUMBNAIL_MIMETYPE = "image/png";

    private static final Log logger = LogFactory.getLog(EcosBpmContentSyncBehaviour.class);

    private ContentService contentService;

    private ObjectMapper objectMapper = new ObjectMapper();
    private FlowableImageService imageService;

    @Override
    protected void beforeInit() {
        setClassName(EcosBpmModel.TYPE_PROCESS_MODEL);
        contentService = serviceRegistry.getContentService();
    }

    @PolicyMethod(policy = NodeServicePolicies.OnCreateNodePolicy.class,
                  frequency = Behaviour.NotificationFrequency.TRANSACTION_COMMIT,
                  runAsSystem = true)
    public void onCreateNode(ChildAssociationRef childAssocRef) {

        NodeRef nodeRef = childAssocRef.getChildRef();

        Map<QName, Serializable> props = nodeService.getProperties(nodeRef);

        Serializable content = props.get(PROP_XML);

        if (content == null) {

            String procId = (String) props.get(EcosBpmModel.PROP_PROCESS_ID);
            String name = (String) props.get(ContentModel.PROP_TITLE);

            BpmnModel bpmnModel = new BpmnModel();
            Process process = new Process();
            bpmnModel.addProcess(process);

            process.setId(procId);
            process.setName(name);

            StartEvent startEvent = new StartEvent();
            startEvent.setId("start");
            process.addFlowElement(startEvent);

            ContentWriter writer = contentService.getWriter(nodeRef, PROP_XML, true);
            BpmnXMLConverter xmlConverter = new BpmnXMLConverter();
            writer.putContent(new ByteArrayInputStream(xmlConverter.convertToXML(bpmnModel, ENCODING)));

        } else {

            BpmnModel model = readModelFromXml(nodeRef);

            if (model != null) {

                List<Process> processes = model.getProcesses();
                if (!processes.isEmpty()) {
                    nodeService.addProperties(nodeRef, readProperties(processes.get(0)));
                }
            }
        }
    }

    @PolicyMethod(policy = ContentServicePolicies.OnContentPropertyUpdatePolicy.class,
                  frequency = Behaviour.NotificationFrequency.TRANSACTION_COMMIT,
                  runAsSystem = true)
    public void onContentPropertyUpdate(NodeRef nodeRef,
                                        QName propertyQName,
                                        ContentData beforeValue,
                                        ContentData afterValue) {

        BpmnModel model = null;
        if (PROP_XML.equals(propertyQName)) {
            model = xmlToJson(nodeRef, afterValue);
        } else if (PROP_JSON.equals(propertyQName)) {
            model = jsonToXml(nodeRef, afterValue);
        }

        if (model != null) {

            Process process = model.getProcesses()
                                   .stream()
                                   .findFirst()
                                   .orElse(null);

            nodeService.addProperties(nodeRef, readProperties(process));
        }
    }

    private Map<QName, Serializable> readProperties(Process process) {

        if (process == null) {
            return Collections.emptyMap();
        }

        Map<QName, Serializable> nodeProps = new HashMap<>();
        nodeProps.put(EcosBpmModel.PROP_PROCESS_ID, process.getId());
        nodeProps.put(ContentModel.PROP_TITLE, process.getName());
        nodeProps.put(ContentModel.PROP_DESCRIPTION, process.getDocumentation());

        return nodeProps;
    }

    @PolicyMethod(policy = NodeServicePolicies.OnUpdatePropertiesPolicy.class,
                  frequency = Behaviour.NotificationFrequency.TRANSACTION_COMMIT,
                  runAsSystem = true)
    public void onUpdateProperties(NodeRef nodeRef,
                                   Map<QName, Serializable> before,
                                   Map<QName, Serializable> after) {

        List<QName> procProps = Arrays.asList(
                EcosBpmModel.PROP_PROCESS_ID,
                ContentModel.PROP_TITLE,
                ContentModel.PROP_DESCRIPTION
        );

        Map<QName, String> changed = new HashMap<>();
        after.forEach((prop, value) -> {
            if (procProps.contains(prop) && !Objects.equals(before.get(prop), value)) {
                if (value instanceof MLText) {
                    Object beforeVal = before.get(prop);
                    if (beforeVal instanceof MLText) {
                        changed.put(prop, getChangedValue((MLText) beforeVal, (MLText) value));
                    } else {
                        changed.put(prop, ((MLText) value).getClosestValue(Locale.ENGLISH));
                    }
                } else if (value instanceof String) {
                    changed.put(prop, (String) value);
                }
            }
        });

        if (changed.isEmpty()) {
            return;
        }

        BpmnModel bpmnModel = readModelFromXml(nodeRef);
        Process process;

        if (bpmnModel != null && !bpmnModel.getProcesses().isEmpty()) {
            process = bpmnModel.getProcesses().get(0);
        } else {
            process = null;
        }
        if (process != null) {
            changed.forEach((prop, value) -> {
                if (EcosBpmModel.PROP_PROCESS_ID.equals(prop)) {
                    process.setId(value);
                } else if (ContentModel.PROP_TITLE.equals(prop)) {
                    process.setName(value);
                } else if (ContentModel.PROP_DESCRIPTION.equals(prop)) {
                    process.setDocumentation(value);
                }
            });

            BpmnXMLConverter xmlConverter = new BpmnXMLConverter();
            byte[] bytes = xmlConverter.convertToXML(bpmnModel, ENCODING);
            writeBytes(nodeRef, PROP_XML, Format.XML.mimetype(), bytes);
        }
    }

    private String getChangedValue(MLText before, MLText after) {
        for (Map.Entry<Locale, String> entry : after.entrySet()) {
            if (!Objects.equals(before.get(entry.getKey()), entry.getValue())) {
                String value = entry.getValue();
                return value == null ? "" : value;
            }
        }
        return after.getClosestValue(Locale.ENGLISH);
    }

    private BpmnModel xmlToJson(NodeRef nodeRef, ContentData data) {

        return convert(nodeRef, data, Format.JSON.mimetype(), PROP_JSON, input -> {

            BpmnModel bpmnModel = readModelFromXml(input);

            if (bpmnModel.getLocationMap().isEmpty()) {
                BpmnAutoLayout bpmnLayout = new BpmnAutoLayout(bpmnModel);
                bpmnLayout.execute();
            }

            BpmnJsonConverter bpmnJsonConverter = new BpmnJsonConverter();
            ObjectNode modelNode = bpmnJsonConverter.convertToJson(bpmnModel);

            generateImages(nodeRef, modelNode);

            try {
                return new BpmnModelData(bpmnModel, objectMapper.writeValueAsBytes(modelNode));
            } catch (JsonProcessingException e) {
                throw new IllegalStateException("Could not write JSON", e);
            }
        });
    }

    private BpmnModel readModelFromXml(NodeRef nodeRef) {

        ContentReader reader = contentService.getReader(nodeRef, PROP_XML);
        if (reader == null || !reader.exists()) {
            return null;
        }

        try (InputStream in = reader.getContentInputStream()) {

            return readModelFromXml(in);

        } catch (IOException e) {
            logger.error(e);
        }

        return null;
    }

    private BpmnModel readModelFromXml(InputStream input) {

        BpmnXMLConverter xmlConverter = new BpmnXMLConverter();
        XMLInputFactory xmlFactory = XMLInputFactory.newInstance();
        XMLStreamReader xmlStreamReader;

        try {
            xmlStreamReader = xmlFactory.createXMLStreamReader(input, ENCODING);
        } catch (XMLStreamException e) {
            throw new IllegalStateException("Could not read XML", e);
        }

        return xmlConverter.convertToBpmnModel(xmlStreamReader);
    }

    private BpmnModel jsonToXml(NodeRef nodeRef, ContentData data) {

        return convert(nodeRef, data, Format.XML.mimetype(), PROP_XML, input -> {

            BpmnJsonConverter jsonConverter = new BpmnJsonConverter();

            JsonNode jsonModel;
            try {
                jsonModel = objectMapper.readTree(input);
            } catch (IOException e) {
                throw new IllegalStateException("Could not read JSON", e);
            }
            generateImages(nodeRef, jsonModel);

            BpmnModel bpmnModel = jsonConverter.convertToBpmnModel(jsonModel);
            BpmnXMLConverter xmlConverter = new BpmnXMLConverter();

            return new BpmnModelData(bpmnModel, xmlConverter.convertToXML(bpmnModel, ENCODING));
        });
    }

    private void generateImages(NodeRef nodeRef, JsonNode node) {

        Model model = new Model();
        model.setId(nodeRef.toString());

        if (node instanceof ObjectNode) {

            BpmnJsonConverter bpmnJsonConverter = new BpmnJsonConverter();
            BpmnModel bpmnModel = bpmnJsonConverter.convertToBpmnModel(node);

            generateImage(nodeRef, bpmnModel, PROP_MODEL_IMG, Integer.MAX_VALUE);
            generateImage(nodeRef, bpmnModel, PROP_THUMBNAIL, THUMBNAIL_WIDTH);

        } else {
            logger.error("Wrong type of node: " + (node != null ? node.getClass() : null));
        }
    }

    private void generateImage(NodeRef nodeRef, BpmnModel model, QName targetProp, int maxWidth) {

        byte[] modelImage = imageService.generateImage(model, maxWidth);
        writeBytes(nodeRef, targetProp, THUMBNAIL_MIMETYPE, modelImage);
    }

    private BpmnModel convert(NodeRef nodeRef,
                              ContentData data,
                              String targetMimetype,
                              QName targetProp,
                              Function<InputStream, BpmnModelData> convert) {

        if (!nodeService.exists(nodeRef)) {
            return null;
        }

        ContentReader reader = contentService.getRawReader(data.getContentUrl());
        if (!reader.exists()) {
            return null;
        }

        try (InputStream input = reader.getContentInputStream()) {

            BpmnModelData bpmnModelData = convert.apply(input);
            writeBytes(nodeRef, targetProp, targetMimetype, bpmnModelData.getBytes());

            return bpmnModelData.getModel();

        } catch (IOException e) {
            throw new IllegalStateException("Could not convert", e);
        }
    }

    private void writeBytes(NodeRef nodeRef, QName property, String mimetype, byte[] bytes) {

        if (bytes == null) {
            return;
        }

        ContentWriter writer = contentService.getWriter(nodeRef, property, true);
        writer.setEncoding(ENCODING);
        writer.setMimetype(mimetype);
        writer.putContent(new ByteArrayInputStream(bytes));
    }

    @Autowired
    public void setImageService(FlowableImageService imageService) {
        this.imageService = imageService;
    }

    private static class BpmnModelData {

        @Getter private BpmnModel model;
        @Getter private byte[] bytes;

        BpmnModelData(BpmnModel model, byte[] bytes) {
            this.model = model;
            this.bytes = bytes;
        }
    }
}
