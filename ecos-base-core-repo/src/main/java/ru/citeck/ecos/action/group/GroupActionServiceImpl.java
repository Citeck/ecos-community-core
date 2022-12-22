package ru.citeck.ecos.action.group;

import com.fasterxml.jackson.databind.node.NullNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.alfresco.repo.security.authentication.AuthenticationUtil;
import org.alfresco.repo.transaction.RetryingTransactionHelper;
import org.alfresco.service.transaction.TransactionService;
import org.springframework.beans.factory.annotation.Autowired;
import ru.citeck.ecos.action.group.impl.CustomTxnGroupAction;
import ru.citeck.ecos.action.group.impl.GroupActionExecutor;
import ru.citeck.ecos.action.group.impl.GroupActionExecutorFactory;
import ru.citeck.ecos.context.lib.auth.AuthContext;
import ru.citeck.ecos.context.lib.auth.data.AuthData;
import ru.citeck.ecos.records.RecordsConfiguration;
import ru.citeck.ecos.records3.record.request.RequestContext;
import ru.citeck.ecos.utils.TransactionUtils;

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

    private final TransactionService transactionService;
    private final RecordsConfiguration recordsConfiguration;

    private Map<String, GroupActionFactory<?>> processorFactories = new HashMap<>();

    private Set<ActionExecution<?>> activeActions;

    @Autowired
    public GroupActionServiceImpl(TransactionService transactionService, RecordsConfiguration recordsConfiguration) {
        this.transactionService = transactionService;
        this.recordsConfiguration = recordsConfiguration;
        activeActions = Collections.newSetFromMap(new ConcurrentHashMap<>());
    }

    private <T> ActionResults<T> executeImpl(ActionExecution<T> execution) {
        if (activeActions.add(execution)) {
            try {
                return RequestContext.doWithCtxJ(recordsConfiguration, b -> {}, ctx -> execution.run());
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
            final AuthData authData = AuthContext.getCurrentFullAuth();
            TransactionUtils.doAfterCommit(() ->
                // Execute action as user. Not as system
                AuthContext.runAsJ(authData, () -> {
                    executeImpl(execution);
                })
            );
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
