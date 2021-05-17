package ru.citeck.ecos.model;

import org.alfresco.service.namespace.QName;

public interface EcosVacationModel {
    // model
    String PREFIX = "ecosvm";

    //Namespaces
    String NAMESPACE = "http://www.citeck.ru/model/content/ecosvm/1.0";

    //Aspects
    QName ASPECT_VACATION_SCHEDULE = QName.createQName(NAMESPACE, "vacationSchedule");
    QName ASPECT_ACCOUNT_SETTINGS = QName.createQName(NAMESPACE, "accountSettings");
    QName ASPECT_PERSON_VACATION_SETTINGS = QName.createQName(NAMESPACE, "personVacationSettings");

    //Properties
    QName PROP_IS_SCHEDULED_VACATION = QName.createQName(NAMESPACE, "isScheduledVacation");

    QName PROP_IRREGULAR_WORK_HOURS = QName.createQName(NAMESPACE, "irregularWorkHours");
    QName PROP_EMPLOYMENT_DATE = QName.createQName(NAMESPACE, "employmentDate");

    QName PROP_ANNUAL_VACATION_REMAINS = QName.createQName(NAMESPACE, "annualVacationRemains");
    QName PROP_IRREGULAR_WORK_VACATION_REMAINS = QName.createQName(NAMESPACE, "irregularWorkVacationRemains");
    QName PROP_NORTH_VACATION_REMAINS = QName.createQName(NAMESPACE, "northVacationRemains");
    QName PROP_HARMFUL_WORK_VACATION_REMAINS = QName.createQName(NAMESPACE, "harmfulWorkVacationRemains");
    QName PROP_SPECIFIC_VACATION_REMAINS = QName.createQName(NAMESPACE, "specificVacationRemains");
    QName PROP_FULL_ANNUAL_VACATION_REMAINS = QName.createQName(NAMESPACE, "fullAnnualVacationRemains");
    QName PROP_FULL_IRREGULAR_WORK_VACATION_REMAINS = QName.createQName(NAMESPACE, "fullIrregularWorkVacationRemains");
    QName PROP_FULL_NORTH_VACATION_REMAINS = QName.createQName(NAMESPACE, "fullNorthVacationRemains");
    QName PROP_FULL_HARMFUL_WORK_VACATION_REMAINS = QName.createQName(NAMESPACE, "fullHarmfulWorkVacationRemains");
}
