package ru.citeck.ecos.action.group.impl;

import ru.citeck.ecos.action.group.*;
import ru.citeck.ecos.action.group.exception.ElementsLimitException;
import ru.citeck.ecos.action.group.exception.ErrorsLimitException;
import ru.citeck.ecos.action.group.exception.GroupActionException;

import java.util.*;

/**
 * @author Pavel Simonov
 */
public abstract class BaseGroupAction<T> implements GroupAction<T> {

    private final List<T> input = new ArrayList<>();
    private final List<ActionResult<T>> output = new ArrayList<>();

    protected final GroupActionConfig config;

    private int errorsCount = 0;
    private int processedCount = 0;
    private int receivedElements = 0;

    private List<ResultsListener<T>> listeners = new ArrayList<>();

    public BaseGroupAction(GroupActionConfig config) {
        this.config = config != null ? config : new GroupActionConfig();
    }

    @Override
    public final boolean process(T remoteRef) {
        input.add(remoteRef);
        int batchSize = config.getBatchSize();
        if (batchSize > 0 && input.size() >= batchSize) {
            processNodes();
        }
        if (++receivedElements >= config.getElementsLimit()) {
            processNodes();
            return false;
        }
        return true;
    }

    @Override
    public final ActionResults<T> complete() {
        if (!input.isEmpty()) {
            processNodes();
        }
        onComplete();

        ActionResults<T> results = new ActionResults<>();
        results.setResults(output);
        results.setErrorsCount(errorsCount);
        results.setProcessedCount(processedCount);
        if (receivedElements > config.getElementsLimit()) {
            results.setCancelCause(new ElementsLimitException(
                "Received size: " + receivedElements +
                    " Items limit: " + config.getElementsLimit())
            );
        }

        return results;
    }

    @Override
    public ActionResults<T> cancel(Throwable cause) {
        onCancel(cause);

        ActionResults<T> results = new ActionResults<>();
        results.setResults(output);
        results.setErrorsCount(errorsCount);
        results.setProcessedCount(processedCount);
        results.setCancelCause(cause);

        return results;
    }

    @Override
    public boolean isAsync() {
        return config.isAsync();
    }

    @Override
    public long getTimeout() {
        return config.getTimeout();
    }

    @Override
    public void addListener(ResultsListener<T> listener) {
        listeners.add(listener);
    }

    @Override
    public void onProcessed(List<ActionResult<T>> results) {

        listeners.forEach(l -> l.onProcessed(results));

        Exception lastError = null;
        String lastErrorMsg = null;

        for (ActionResult<T> result : results) {
            ActionStatus status = result.getStatus();
            if (status != null && ActionStatus.STATUS_ERROR.equals(status.getKey())) {
                lastErrorMsg = status.getMessage();
                lastError = status.getException();
                if (lastErrorMsg == null && lastError != null) {
                    lastErrorMsg = lastError.getMessage();
                }
                errorsCount++;
            }
            if (config.getMaxResults() > output.size()) {
                output.add(result);
            }
        }
        int errorsLimit = config.getErrorsLimit();
        if (errorsLimit > 0 && errorsCount >= errorsLimit) {
            throw new ErrorsLimitException("Group action max errors limit is reached! " +
                                           "LastError: " + lastErrorMsg, lastError);
        }
    }

    @Override
    public boolean isReadOnly() {
        return config.isReadOnly();
    }

    private void processNodes() {
        if (!input.isEmpty()) {
            processNodesImpl(input);
            processedCount += input.size();
            input.clear();
        }
    }

    protected void onComplete() {
    }

    protected void onCancel(Throwable cause) {
    }

    protected void processNodesImpl(List<T> nodes) {
        List<ActionResult<T>> results = new ArrayList<>();
        for (T node : nodes) {
            ActionStatus status = processImpl(node);
            results.add(new ActionResult<>(node, status));
        }
        onProcessed(results);
    }

    protected ActionStatus processImpl(T nodeRef) {
        throw new GroupActionException("Method not implemented");
    }

    public GroupActionConfig getConfig() {
         return config;
    }

    public int getErrorsCount() {
        return errorsCount;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        BaseGroupAction that = (BaseGroupAction) o;

        return Objects.equals(config, that.config);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(config);
    }

    @Override
    public String toString() {
        return getClass().getName() + "@" + Integer.toHexString(hashCode()) +
                " config: " + config + " errors: " + errorsCount;
    }
}
