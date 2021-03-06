package ru.citeck.ecos.model;

import org.alfresco.service.namespace.QName;

public interface CasePerformModel {

    String NAMESPACE = "http://www.citeck.ru/model/workflow/case-perform/1.0";

    String PREFIX = "wfcp";

    QName TYPE_PERFORM_TASK = QName.createQName(NAMESPACE, "performTask");
    QName TYPE_PERFORM_RESULT = QName.createQName(NAMESPACE, "performResult");
    QName TYPE_PERFORM_CASE_TASK = QName.createQName(NAMESPACE, "performCaseTask");

    QName ASPECT_HAS_PERFORM_RESULTS = QName.createQName(NAMESPACE, "hasPerformResults");
    QName ASPECT_BASE_PERFORM_DATA = QName.createQName(NAMESPACE, "basePerformData");

    QName PROP_PERFORM_OUTCOME = QName.createQName(NAMESPACE, "performOutcome");
    QName PROP_RESULT_OUTCOME = QName.createQName(NAMESPACE, "resultOutcome");
    QName PROP_RESULT_DATE = QName.createQName(NAMESPACE, "resultDate");
    QName PROP_OUTCOMES_WITH_MANDATORY_COMMENT = QName.createQName(NAMESPACE, "outcomesWithMandatoryComment");
    QName PROP_ABORT_OUTCOMES = QName.createQName(NAMESPACE, "abortOutcomes");
    QName PROP_COMMENT = QName.createQName(NAMESPACE, "comment");
    QName PROP_FORM_KEY = QName.createQName(NAMESPACE, "formKey");
    QName PROP_SYNC_ROLES_TO_WORKFLOW = QName.createQName(NAMESPACE, "syncRolesToWorkflow");
    QName PROP_SYNC_WORKFLOW_TO_ROLES = QName.createQName(NAMESPACE, "syncWorkflowToRoles");

    QName ASSOC_PERFORMER = QName.createQName(NAMESPACE, "performer");
    QName ASSOC_CASE_ROLE = QName.createQName(NAMESPACE, "caseRole");
    QName ASSOC_PERFORMERS = QName.createQName(NAMESPACE, "performers");
    QName ASSOC_CANDIDATES = QName.createQName(NAMESPACE, "candidates");
    QName ASSOC_RESULT_PERSON = QName.createQName(NAMESPACE, "resultPerson");
    QName ASSOC_RESULT_PERFORMER = QName.createQName(NAMESPACE, "resultPerformer");
    QName ASSOC_PERFORMERS_ROLES = QName.createQName(NAMESPACE, "performersRoles");
    QName ASSOC_PERFORM_RESULTS = QName.createQName(NAMESPACE, "performResults");
}
