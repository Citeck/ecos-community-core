package ru.citeck.ecos.service;

import org.alfresco.service.ServiceRegistry;
import org.alfresco.service.namespace.QName;
import ru.citeck.ecos.icase.CaseStatusAssocDao;
import ru.citeck.ecos.icase.element.CaseElementService;
import ru.citeck.ecos.icase.CaseStatusService;
import ru.citeck.ecos.icase.activity.service.CaseActivityService;
import ru.citeck.ecos.icase.timer.CaseTimerService;

import static ru.citeck.ecos.service.CiteckServices.CITECK_NAMESPACE;

/**
 * @author Pavel Simonov
 */
public final class EcosCoreServices {

    public static final QName CASE_TIMER_SERVICE = QName.createQName(CITECK_NAMESPACE, "caseTimerService");
    public static final QName CASE_TEMPLATE_REGISTRY = QName.createQName(CITECK_NAMESPACE, "caseTemplateRegistry");
    public static final QName CASE_ELEMENT_SERVICE = QName.createQName(CITECK_NAMESPACE, "caseElementService");
    public static final QName CASE_STATUS_SERVICE = QName.createQName(CITECK_NAMESPACE, "caseStatusService");
    public static final QName CASE_STATUS_ASSOC_DAO = QName.createQName(CITECK_NAMESPACE, "caseStatusAssocDao");
    public static final QName CASE_ACTIVITY_SERVICE = QName.createQName(CITECK_NAMESPACE, "caseActivityService");

    public static CaseTimerService getCaseTimerService(ServiceRegistry services) {
        return (CaseTimerService) services.getService(CASE_TIMER_SERVICE);
    }

    public static CaseElementService getCaseElementService(ServiceRegistry services) {
        return (CaseElementService) services.getService(CASE_ELEMENT_SERVICE);
    }

    public static CaseStatusService getCaseStatusService(ServiceRegistry services) {
        return (CaseStatusService) services.getService(CASE_STATUS_SERVICE);
    }

    public static CaseActivityService getCaseActivityService(ServiceRegistry services) {
        return (CaseActivityService) services.getService(CASE_ACTIVITY_SERVICE);
    }

    public static CaseStatusAssocDao getCaseStatusAssocDao(ServiceRegistry services) {
        return (CaseStatusAssocDao) services.getService(CASE_STATUS_ASSOC_DAO);
    }
}
