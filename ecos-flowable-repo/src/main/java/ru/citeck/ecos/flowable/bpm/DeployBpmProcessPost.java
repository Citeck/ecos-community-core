package ru.citeck.ecos.flowable.bpm;

import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import org.alfresco.model.ContentModel;
import org.alfresco.service.cmr.repository.ContentReader;
import org.alfresco.service.cmr.repository.ContentService;
import org.alfresco.service.cmr.repository.NodeRef;
import org.alfresco.service.cmr.repository.NodeService;
import org.alfresco.service.namespace.QName;
import org.alfresco.util.ParameterCheck;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.extensions.webscripts.AbstractWebScript;
import org.springframework.extensions.webscripts.Status;
import org.springframework.extensions.webscripts.WebScriptRequest;
import org.springframework.extensions.webscripts.WebScriptResponse;
import ru.citeck.ecos.eapps.WorkflowArtifact;
import ru.citeck.ecos.eapps.WorkflowArtifactHandler;
import ru.citeck.ecos.model.EcosBpmModel;

import java.io.InputStream;
import java.io.Serializable;
import java.util.Map;

@RequiredArgsConstructor(onConstructor_={@Autowired})
public class DeployBpmProcessPost extends AbstractWebScript {

    private static final String PARAM_NODE_REF = "nodeRef";

    private final ContentService contentService;
    private final NodeService nodeService;
    private final WorkflowArtifactHandler workflowArtifactHandler;

    @Override
    @SneakyThrows
    public void execute(WebScriptRequest req, WebScriptResponse res) {

        String nodeRefStr = req.getParameter(PARAM_NODE_REF);
        ParameterCheck.mandatoryString(PARAM_NODE_REF, nodeRefStr);
        nodeRefStr = nodeRefStr.replace("alfresco/@", "");

        NodeRef nodeRef = new NodeRef(nodeRefStr);
        Map<QName, Serializable> props = nodeService.getProperties(nodeRef);

        String engineId = (String) props.get(EcosBpmModel.PROP_ENGINE);
        if (StringUtils.isBlank(engineId)) {
            engineId = "flowable";
        }
        String processId = (String) props.get(EcosBpmModel.PROP_PROCESS_ID);
        if (StringUtils.isBlank(processId)) {
            throw new RuntimeException("Process ID is empty for nodeRef: " + nodeRefStr);
        }

        ContentReader reader = contentService.getReader(nodeRef, ContentModel.PROP_CONTENT);

        try (InputStream in = reader.getContentInputStream()) {
            WorkflowArtifact artifact = new WorkflowArtifact();
            artifact.setId(engineId + "$" + processId);
            artifact.setXmlData(IOUtils.toByteArray(in));
            workflowArtifactHandler.fireWorkflowChanged(artifact);
            workflowArtifactHandler.deployArtifact(artifact);
        }

        res.setStatus(Status.STATUS_OK);
    }
}
