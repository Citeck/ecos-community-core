package ru.citeck.ecos.icase.activity.service;

import org.alfresco.service.cmr.repository.NodeRef;
import ru.citeck.ecos.icase.activity.dto.ActivityRef;
import ru.citeck.ecos.icase.activity.dto.CaseServiceType;
import ru.citeck.ecos.records2.RecordRef;

public interface ActivityCommonService {

    CaseServiceType getCaseType(NodeRef caseRef);

    CaseServiceType getCaseType(RecordRef caseRef);

    ActivityRef composeRootActivityRef(NodeRef caseRef);

    ActivityRef composeRootActivityRef(RecordRef caseRef);

}
