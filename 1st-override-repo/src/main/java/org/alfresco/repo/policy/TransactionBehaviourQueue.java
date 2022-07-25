package org.alfresco.repo.policy;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.*;

import org.alfresco.error.AlfrescoRuntimeException;
import org.alfresco.repo.policy.Policy.Arg;
import org.alfresco.repo.security.authentication.AuthenticationUtil;
import org.alfresco.repo.transaction.AlfrescoTransactionSupport;
import org.alfresco.repo.transaction.TransactionListener;
import org.alfresco.repo.transaction.TransactionalResourceHelper;
import org.alfresco.util.GUID;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

/**
 * Fixed by @author Ruslan Vildanov
 * I have changed only {@link TransactionBehaviourQueue#execute(ExecutionContext)}.
 * I have fixed exceptions messages, because of some of these messages is shown
 * to the user.
 * <p>
 * Transaction Behaviour Queue.
 * <p>
 * Responsible for keeping a record of behaviours to execute at the end of a transaction.
 */
public class TransactionBehaviourQueue implements TransactionListener {
    /**
     * Id used in equals and hash
     */
    private String id = GUID.generate();

    // Transaction Keys for Behaviour Execution state
    private static final String QUEUE_CONTEXT_KEY = TransactionBehaviourQueue.class.getName() + ".context";
    private static final String EXECUTION_CONTEXT_NUMBER_KEY = TransactionBehaviourQueue.class.getName() + ".number";
    private static final Log logger = LogFactory.getLog(TransactionBehaviourQueue.class);

    /**
     * Queue a behaviour for end-of-transaction execution
     *
     * @param <P>
     * @param behaviour
     * @param definition
     * @param policyInterface
     * @param method
     * @param args
     */
    @SuppressWarnings("unchecked")
    public <P extends Policy> void queue(Behaviour behaviour, PolicyDefinition<P> definition,
                                         P policyInterface, Method method, Object[] args) {

        // Construct queue context, if required
        QueueContext queueContext = AlfrescoTransactionSupport.getResource(QUEUE_CONTEXT_KEY);
        if (queueContext == null) {
            queueContext = new QueueContext();
            AlfrescoTransactionSupport.bindResource(QUEUE_CONTEXT_KEY, queueContext);
            AlfrescoTransactionSupport.bindListener(this);
        }

        // Determine if behaviour instance has already been queued

        // Identity of ExecutionContext is Behaviour + KEY argument(s)
        ExecutionInstanceKey key = new ExecutionInstanceKey(behaviour, definition.getArguments(), args);

        ExecutionContext executionContext = queueContext.index.get(key);

        if (executionContext == null) {
            // Context does not exist
            // Create execution context for behaviour
            executionContext = new ExecutionContext<P>();
            executionContext.method = method;
            executionContext.args = args;
            executionContext.policyInterface = policyInterface;
            executionContext.order = getOrder(behaviour);
            executionContext.authenticatedUser = AuthenticationUtil.getFullyAuthenticatedUser();
            executionContext.number = TransactionalResourceHelper.incrementCount(EXECUTION_CONTEXT_NUMBER_KEY);

            // Defer or execute now?
            if (!queueContext.committed) {
                // queue behaviour for deferred execution
                addContextIntoQueue(executionContext, queueContext.queue);
            } else {
                // execute now
                execute(executionContext);
            }
            queueContext.index.put(key, executionContext);
        } else {
            // Context does already exist
            // Update behaviour instance execution context, in particular, argument state that is marked END_TRANSACTION
            Arg[] argDefs = definition.getArguments();
            for (int i = 0; i < argDefs.length; i++) {
                if (argDefs[i].equals(Arg.END_VALUE)) {
                    executionContext.args[i] = args[i];
                }
            }
        }
    }

    private int getOrder(Behaviour behaviour) {
        if (behaviour instanceof TransactionBehaviourOrder) {
            return ((TransactionBehaviourOrder) behaviour).getOrder();
        } else {
            return TransactionBehaviourOrder.DEFAULT_ORDER;
        }
    }

    private void addContextIntoQueue(ExecutionContext executionContext,
                                     Queue<ExecutionContext> queue) {

        queue.add(executionContext);

        if (logger.isDebugEnabled()) {
            Object policyInterface = executionContext.policyInterface;
            if (policyInterface == null) {
                policyInterface = "";
            }
            int order = executionContext.order;
            logger.debug("queue offer: order=" + order +
                         "; queue.size=" + queue.size() +
                         ";\npolicyInterface=" + policyInterface);
        }
    }

    /* (non-Javadoc)
     * @see org.alfresco.repo.transaction.TransactionListener#flush()
     */
    public void flush() {
    }

    /* (non-Javadoc)
     * @see org.alfresco.repo.transaction.TransactionListener#beforeCommit(boolean)
     */
    public void beforeCommit(boolean readOnly) {
        QueueContext queueContext = AlfrescoTransactionSupport.getResource(QUEUE_CONTEXT_KEY);
        ExecutionContext<?> context = queueContext.queue.poll();
        while (context != null) {
            if (logger.isDebugEnabled()) {
                int order = context.order;
                int queueSize = queueContext.queue.size();
                logger.debug("queue.poll: order=" + order + "; queue.size=" + queueSize +
                        ";\npolicyInterface=" + context.policyInterface);
            }
            String currentUser = AuthenticationUtil.getFullyAuthenticatedUser();
            ExecutionContext<?> finalContext = context;
            if (currentUser == null && context.authenticatedUser != null) {
                AuthenticationUtil.runAs(() -> AuthenticationUtil.runAsSystem(() -> {
                    execute(finalContext);
                    return null;
                }), context.authenticatedUser);
            } else {
                AuthenticationUtil.runAsSystem(() -> {
                    execute(finalContext);
                    return null;
                });
            }
            context = queueContext.queue.poll();
        }
        queueContext.committed = true;
    }

    /* (non-Javadoc)
     * @see org.alfresco.repo.transaction.TransactionListener#beforeCompletion()
     */
    public void beforeCompletion() {
    }

    /* (non-Javadoc)
     * @see org.alfresco.repo.transaction.TransactionListener#afterCommit()
     */
    public void afterCommit() {
    }

    /* (non-Javadoc)
     * @see org.alfresco.repo.transaction.TransactionListener#afterRollback()
     */
    public void afterRollback() {
    }


    /**
     * Execution Instance Key - to uniquely identify an ExecutionContext
     */
    private class ExecutionInstanceKey {
        public ExecutionInstanceKey(Behaviour behaviour, Arg[] argDefs, Object[] args) {
            this.behaviour = behaviour;

            for (int i = 0; i < argDefs.length; i++) {
                if (argDefs[i].equals(Arg.KEY)) {
                    keys.add(args[i]);
                }
            }
        }

        Behaviour behaviour;
        ArrayList<Object> keys = new ArrayList<>();

        /**
         * @see java.lang.Object#hashCode()
         */
        @Override
        public int hashCode() {
            int key = behaviour.hashCode();
            for (int i = 0; i < keys.size(); i++) {
                key = (37 * key) + keys.get(i).hashCode();
            }
            return key;
        }

        /**
         * @see java.lang.Object#equals(java.lang.Object)
         */
        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (obj instanceof ExecutionInstanceKey) {
                ExecutionInstanceKey that = (ExecutionInstanceKey) obj;
                if (this.behaviour.equals(that.behaviour)) {
                    if (keys.size() != that.keys.size()) {
                        // different number of keys
                        return false;
                    }
                    if (keys.containsAll(that.keys)) {
                        // yes keys are equal
                        return true;
                    }
                }
                // behavior is different
                return false;
            } else {
                // Object is wrong type
                return false;
            }
        } // equals
    } // ExecutionInstanceKey

    /**
     * Execute behaviour as described in execution context
     *
     * @param context
     */
    private void execute(ExecutionContext context) {
        try {
            context.method.invoke(context.policyInterface, context.args);
        } catch (IllegalArgumentException | IllegalAccessException e) {
            throw new AlfrescoRuntimeException(e.getMessage(), e);
        } catch (InvocationTargetException e) {
            Throwable t = e.getTargetException();
            throw new AlfrescoRuntimeException(
                    t != null ?
                            t.getMessage() :
                            "Failed to execute transaction-level behaviour " +
                                    context.method + " in transaction " + AlfrescoTransactionSupport.getTransactionId(),
                    t);
        }
    }

    /**
     * @see java.lang.Object#hashCode()
     */
    @Override
    public int hashCode() {
        return this.id.hashCode();
    }

    /**
     * @see java.lang.Object#equals(java.lang.Object)
     */
    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj instanceof TransactionBehaviourQueue) {
            TransactionBehaviourQueue that = (TransactionBehaviourQueue) obj;
            return (this.id.equals(that.id));
        } else {
            return false;
        }
    }

    /**
     * Behaviour execution Context
     *
     * @param <P>
     */
    private class ExecutionContext<P extends Policy> {
        Method method;
        Object[] args;
        P policyInterface;
        int order;
        String authenticatedUser;
        int number;
    }

    private class OrderComparator implements Comparator<ExecutionContext> {
        @Override
        public int compare(ExecutionContext c1, ExecutionContext c2) {
            int result = Integer.compare(c1.order, c2.order);
            if (result == 0) {
                result = Integer.compare(c1.number, c2.number);
            }
            return result;
        }
    }

    /**
     * Queue Context
     */
    private class QueueContext {
        Queue<ExecutionContext> queue = new PriorityQueue<>(new OrderComparator());
        Map<ExecutionInstanceKey, ExecutionContext> index = new HashMap<>();
        boolean committed = false;
    }

}
