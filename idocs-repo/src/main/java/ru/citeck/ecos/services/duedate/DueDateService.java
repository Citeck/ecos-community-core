package ru.citeck.ecos.services.duedate;

import ru.citeck.ecos.records2.RecordRef;

/**
 * Service for calculation of dueDate.
 */
public interface DueDateService {

    /**
     * Method to return dueDate for userTask with given userName and working days.
     *
     * @param userName  userName string (e.g. admin)
     * @param days      number of the working days to add
     * @return dueDate string in ISO 8601 format (YYYY-MM-DDThh:mm:ss±hh)
     */
    String getDueDateForUser(String userName, int days);

    /**
     * Method to return dueDate for userTask with given userName, working days and hours.
     *
     * @param userName  userName string (e.g. admin)
     * @param days      number of the working days to add
     * @param hours     number of working hours to add
     * @return dueDate string in ISO 8601 format (YYYY-MM-DDThh:mm:ss±hh)
     */
    String getDueDateForUser(String userName, int days, int hours);

    /**
     * Method to return dueDate for userTask with given groupName and working days.
     *
     * @param groupName  groupName in orgstructure
     * @param days      number of the working days to add
     * @return dueDate string in ISO 8601 format (YYYY-MM-DDThh:mm:ss±hh)
     */
    String getDueDateForGroup(String groupName, int days);

    /**
     * Method to return dueDate for userTask with given groupName, working days and hours.
     *
     * @param groupName  groupName in orgstructure
     * @param days      number of the working days to add
     * @param hours     number of working hours to add
     * @return dueDate string in ISO 8601 format (YYYY-MM-DDThh:mm:ss±hh)
     */
    String getDueDateForGroup(String groupName, int days, int hours);

    /**
     * Method to return dueDate for userTask with given calendarName and working days.
     *
     * @param calendarName  calendar name string
     * @param days          number of the working days to add
     * @return dueDate string in ISO 8601 format (YYYY-MM-DDThh:mm:ss±hh)
     */
    String getDueDateForCalendar(String calendarName, int days);

    /**
     * Method to return dueDate for userTask with given calendarName, working days and hours.
     *
     * @param calendarName  calendar name string
     * @param days      number of the working days to add
     * @param hours     number of working hours to add
     * @return dueDate string in ISO 8601 format (YYYY-MM-DDThh:mm:ss±hh)
     */
    String getDueDateForCalendar(String calendarName, int days, int hours);

    /**
     * Returns due date for document or null.
     *
     * @param documentRef document ref
     * @param days number of the working days to add
     * @return due date string in ISO 8601 format (YYYY-MM-DDThh:mm:ss±hh) or null
     */
    String getDueDateForDocument(RecordRef documentRef, int days);

    /**
     * Method to return dueDate for document.
     *
     * @param documentRef document ref
     * @param days number of the working days to add
     * @param hours number of the working hours to add
     * @return due date string in ISO 8601 format (YYYY-MM-DDThh:mm:ss±hh) or null
     */
    String getDueDateForDocument(RecordRef documentRef, int days, int hours);

}
