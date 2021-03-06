package ru.citeck.ecos.action.group;

import com.fasterxml.jackson.databind.node.NullNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.alfresco.repo.security.authentication.AuthenticationUtil;
import org.alfresco.repo.transaction.AlfrescoTransactionSupport;
import org.alfresco.repo.transaction.RetryingTransactionHelper;
import org.alfresco.service.transaction.TransactionService;
import org.alfresco.util.transaction.TransactionListenerAdapter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.extensions.surf.util.I18NUtil;
import ru.citeck.ecos.action.group.impl.CustomTxnGroupAction;
import ru.citeck.ecos.action.group.impl.GroupActionExecutor;
import ru.citeck.ecos.action.group.impl.GroupActionExecutorFactory;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * @author Pavel Simonov
 */
public class GroupActionServiceImpl implements GroupActionService {

    private static final String ALREADY_RUNNING_MSG = "The action is already running. " +
                                                      "You can not start several identical actions!";

    private TransactionService transactionService;

    private Map<String, GroupActionFactory<?>> processorFactories = new HashMap<>();

    private Set<ActionExecution<?>> activeActions;

    @Autowired
    public GroupActionServiceImpl(TransactionService transactionService) {
        this.transactionService = transactionService;
        activeActions = Collections.newSetFromMap(new ConcurrentHashMap<>());
    }

    private <T> ActionResults<T> executeImpl(ActionExecution<T> execution) {
        if (activeActions.add(execution)) {
            try {
                return execution.run();
            } finally {
                activeActions.remove(execution);
            }
        } else {
            throw new IllegalStateException(ALREADY_RUNNING_MSG);
        }
    }

    private <T> ActionResults<T> executeInTxn(ActionExecution<T> execution) {
        RetryingTransactionHelper txnHelper = transactionService.getRetryingTransactionHelper();
        return txnHelper.doInTransaction(() -> executeImpl(execution), execution.isReadOnly(), false);
    }

    @Override
    public <T> ActionResults<T> execute(Iterable<T> nodes, GroupAction<T> action) {

        String author = AuthenticationUtil.getFullyAuthenticatedUser();
        ActionExecution<T> execution = new ActionExecution<>(nodes, action, author);

        if (activeActions.contains(execution)) {
            throw new IllegalStateException(ALREADY_RUNNING_MSG);
        }

        if (action.isAsync()) {

            final String currentUser = AuthenticationUtil.getFullyAuthenticatedUser();
            final Locale locale = I18NUtil.getLocale();

            AlfrescoTransactionSupport.bindListener(new TransactionListenerAdapter() {
                @Override
                public void afterCommit() {
                    new Thread(() -> {

                        AuthenticationUtil.setRunAsUser(currentUser);
                        I18NUtil.setLocale(locale);

                        executeInTxn(execution);

                    }).start();
                }
            });

            return new ActionResults<>();

        } else {

            return executeInTxn(execution);
        }
    }

    @Override
    public <T> ActionResults<T> execute(Iterable<T> nodes,
                                        Consumer<T> action,
                                        GroupActionConfig config) {

        return execute(nodes, new CustomTxnGroupAction<>(transactionService, action, config));
    }

    @Override
    public <T> ActionResults<T> execute(Iterable<T> nodes,
                                        Function<T, ActionStatus> action,
                                        GroupActionConfig config) {

        return execute(nodes, new CustomTxnGroupAction<>(transactionService, action, config));
    }

    @Override
    public <T> ActionResults<T> execute(Iterable<T> nodes,
                                        GroupActionConfig config) {

        return execute(nodes, createAction(config));
    }

    @Override
    public <T> GroupAction<T> createAction(GroupActionConfig config) {

        @SuppressWarnings("unchecked")
        GroupActionFactory<T> factory = (GroupActionFactory<T>) processorFactories.get(config.getActionId());
        if (factory == null) {
            throw new IllegalArgumentException("Action not found: '" + config.getActionId() + "'");
        }

        checkParams(config.getParams(), factory.getMandatoryParams());

        return factory.createAction(config);
    }

    private void checkParams(ObjectNode params, String[] mandatoryParams) {
        List<String> missing = new ArrayList<>(mandatoryParams.length);
        for (String param : mandatoryParams) {
            if (!params.has(param) || params.get(param) instanceof NullNode) {
                missing.add(param);
            }
        }
        if (!missing.isEmpty()) {
            throw new IllegalStateException("Mandatory parameters are missing: " + String.join(", ", missing));
        }
    }

    @Override
    public void cancelAllActions() {
        for (ActionExecution execution : activeActions) {
            execution.cancel();
        }
        activeActions.clear();
    }

    @Override
    public List<ActionExecution<?>> getActiveActions() {
        return new ArrayList<>(activeActions);
    }

    @Override
    public void register(GroupActionFactory factory) {
        processorFactories.put(factory.getActionId(), factory);
    }

    @Override
    public void register(GroupActionExecutor executor) {
        register(new GroupActionExecutorFactory(executor, transactionService));
    }
}
