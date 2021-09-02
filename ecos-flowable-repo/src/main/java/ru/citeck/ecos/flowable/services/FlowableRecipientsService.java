package ru.citeck.ecos.flowable.services;

import org.alfresco.service.cmr.repository.NodeRef;
import ru.citeck.ecos.records2.RecordRef;

import java.util.Set;

/**
 * This service helps getting recipients data from case roles. Uses in flowable process.
 *
 * @author Roman Makarskiy
 */
public interface FlowableRecipientsService {

    String EMAIL_SEPARATOR = ",";

    /**
     * Method to getting emails addresses from case role, its usually uses in email task.
     * Email addresses must be comma {@code EMAIL_SEPARATOR} separated.
     *
     * @param document     nodeRef of document
     * @param caseRoleName name of case role
     * @return emails comma {@code EMAIL_SEPARATOR} separated from case role
     */
    String getRoleEmails(NodeRef document, String caseRoleName);

    String getRoleEmails(RecordRef document, String caseRoleName);

    /**
     * Method to get user email
     *
     * @param username user name {@code ContentModel.PROP_USERNAME}
     * @return user email {@code ContentModel.PROP_EMAIL}
     */
    String getUserEmail(String username);

    /**
     * Method to getting emails addresses from authority, its usually uses in email task.
     * Email addresses must be comma {@code EMAIL_SEPARATOR} separated.
     *
     * @param authority authority id
     * @return emails comma {@code EMAIL_SEPARATOR} separated
     */
    String getAuthorityEmails(String authority);

    /**
     * Method to get groups names {@code ContentModel.PROP_AUTHORITY_NAME} from case role,
     * its usually uses in recipients groups in user task.
     *
     * @param document     nodeRef of document
     * @param caseRoleName name of case role
     * @return groups names from case role
     */
    Set<String> getRoleGroups(NodeRef document, String caseRoleName);

    Set<String> getRoleGroups(RecordRef document, String caseRoleName);

    /**
     * Method to get users names {@code ContentModel.PROP_USERNAME} from case role,
     * its usually uses in recipients users in user task.
     *
     * @param document     nodeRef of document
     * @param caseRoleName name {@code ICaseRoleModel.PROP_VARNAME} of case role
     * @return users names from case role
     */
    Set<String> getRoleUsers(NodeRef document, String caseRoleName);

    Set<String> getRoleUsers(RecordRef document, String caseRoleName);
}
