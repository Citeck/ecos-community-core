package ru.citeck.ecos.eform.webscripts;

import lombok.extern.log4j.Log4j;
import org.alfresco.service.cmr.repository.NodeRef;
import org.alfresco.service.cmr.repository.NodeService;
import org.alfresco.service.cmr.search.SearchService;
import org.apache.commons.lang.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.extensions.webscripts.Cache;
import org.springframework.extensions.webscripts.DeclarativeWebScript;
import org.springframework.extensions.webscripts.Status;
import org.springframework.extensions.webscripts.WebScriptRequest;
import ru.citeck.ecos.eform.model.EcosEformFileModel;
import ru.citeck.ecos.search.ftsquery.FTSQuery;
import ru.citeck.ecos.utils.RepoUtils;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Allow to process delete request from file control on eform. This request should delete only temp files <br>
 * {@link EcosEformFileModel#TYPE_TEMP_FILE}, finding by {@link EcosEformFileModel#PROP_TEMP_FILE_ID}. <br>
 *
 * @author Roman Makarskiy
 */
@Log4j
public class FileEformDelete extends DeclarativeWebScript {

    private static final String PARAM_FORM = "form";
    private static final String WASTE_FIRST_CHAR_ID = "/";
    private static final String MODEL_RESULT = "result";

    private NodeService nodeService;
    private SearchService searchService;

    @Override
    protected Map<String, Object> executeImpl(WebScriptRequest req, Status status, Cache cache) {
        final String fileId = req.getParameter(PARAM_FORM);
        if (StringUtils.isBlank(fileId)) {
            status.setCode(Status.STATUS_BAD_REQUEST, "Parameter '" + PARAM_FORM + "' should be set.");
            return null;
        }

        NodeRef nodeToDelete = deleteFile(fileId);
        String resultModel = nodeToDelete != null ? nodeToDelete.toString() : "";

        Map<String, Object> result = new HashMap<>();
        result.put(MODEL_RESULT, resultModel);
        return result;
    }

    private NodeRef deleteFile(String fileId) {
        fileId = StringUtils.removeStart(fileId, WASTE_FIRST_CHAR_ID);

        List<NodeRef> query = FTSQuery.create()
                .type(EcosEformFileModel.TYPE_TEMP_FILE)
                .and().value(EcosEformFileModel.PROP_TEMP_FILE_ID, fileId)
                .query(searchService);

        if (query.isEmpty()) {
            //its ok, because file maybe already deleted or moved from temp directory. Just return null.
            return null;
        }

        if (query.size() > 1) {
            log.warn("Collision found. File id <" + fileId + ">. List: " + query);
        }

        NodeRef nodeToDelete = query.get(0);
        //delete temp file without moving to archive
        RepoUtils.deleteNode(nodeToDelete, nodeService);

        if (log.isDebugEnabled()) {
            log.debug(String.format("Delete file with id <%s>, nodeRef <%s>", fileId, nodeToDelete));
        }

        return nodeToDelete;
    }

    @Autowired
    public void setNodeService(NodeService nodeService) {
        this.nodeService = nodeService;
    }

    @Autowired
    public void setSearchService(SearchService searchService) {
        this.searchService = searchService;
    }
}
