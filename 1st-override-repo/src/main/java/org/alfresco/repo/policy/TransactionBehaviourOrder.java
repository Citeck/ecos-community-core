package org.alfresco.repo.policy;

/**
 * This interface helps to order behaviors.
 * 
 * @author Ruslan
 *
 */
public interface TransactionBehaviourOrder {

	int DEFAULT_ORDER = 50;

	int getOrder();

}
