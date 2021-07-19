package ru.citeck.ecos.records.actions;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.beans.factory.annotation.Autowired;
import ru.citeck.ecos.action.group.*;
import ru.citeck.ecos.action.group.impl.BaseGroupAction;
import ru.citeck.ecos.records.RecordGroupActionsService;
import ru.citeck.ecos.records2.RecordRef;

import javax.annotation.PostConstruct;
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Action factory to work with mixed remote/local records
 */
public abstract class RecordsActionFactory implements GroupActionFactory<RecordRef> {

    private RecordGroupActionsService recordsService;
    private GroupActionService groupActionService;

    @PostConstruct
    public void registerFactory() {
        groupActionService.register(this);
        groupActionService.register(new RecordsActionLocalFactory());
    }

    @Override
    public final GroupAction<RecordRef> createAction(GroupActionConfig config) {
        return new Action(config);
    }

    /**
     * Return config for base action with every recordRef
     * This config will be send to RecordsService and can be executed remotely
     * Because of that config must contain only data which can be serialized by jackson ObjectMapper
     *
     * @see ObjectMapper
     */
    protected abstract GroupActionConfig getRecordsActionConfig(GroupActionConfig baseConfig);

    /**
     * Create action to process local records
     */
    protected abstract GroupAction<RecordRef> createRecordsAction(GroupActionConfig config);

    /**
     * Create local action to process results returned by action from "createRecordsAction"
     */
    protected GroupAction<ActionResult<RecordRef>> createResultsAction(GroupActionConfig baseConfig,
                                                                       GroupActionConfig recordsActionConfig) {
        return null;
    }

    protected String getRecordsActionId() {
        return getActionId() + "-local-records";
    }

    class RecordsActionLocalFactory implements GroupActionFactory<RecordRef> {

        @Override
        public GroupAction<RecordRef> createAction(GroupActionConfig config) {
            return createRecordsAction(config);
        }

        @Override
        public String getActionId() {
            return getRecordsActionId();
        }
    }

    class Action extends BaseGroupAction<RecordRef> {

        private GroupAction<ActionResult<RecordRef>> resultsAction;
        private GroupActionConfig recordsActionConfig;

        public Action(GroupActionConfig config) {
            super(config);

            recordsActionConfig = getRecordsActionConfig(config);
            recordsActionConfig.setAsync(false);
            recordsActionConfig.setActionId(getRecordsActionId());

            resultsAction = createResultsAction(config, recordsActionConfig);
            if (resultsAction != null) {
                resultsAction.addListener(results -> {
                    List<ActionResult<RecordRef>> recordsResults = results.stream().map(a ->
                            new ActionResult<>(a.getData().getData(), a.getStatus())).collect(Collectors.toList());
                    onProcessed(recordsResults);
                });
            }
        }

        @Override
        protected void processNodesImpl(List<RecordRef> nodes) {
            ActionResults<RecordRef> results = recordsService.executeAction(nodes, recordsActionConfig);
            if (results.getCancelCause() != null) {
                throw new RuntimeException(results.getCancelCause());
            }
            if (resultsAction != null) {
                results.getResults().forEach(r -> resultsAction.process(r));
            } else {
                onProcessed(results.getResults());
            }
        }

        @Override
        protected void onComplete() {
            if (resultsAction != null) {
                resultsAction.complete();
            }
        }

        @Override
        protected void onCancel(Throwable cause) {
            if (resultsAction != null) {
                resultsAction.cancel(cause);
            }
        }

        @Override
        public void close() throws IOException {
            if (resultsAction != null) {
                resultsAction.close();
            }
        }
    }

    @Autowired
    public void setRecordsService(RecordGroupActionsService recordsService) {
        this.recordsService = recordsService;
    }

    @Autowired
    public void setGroupActionService(GroupActionService groupActionService) {
        this.groupActionService = groupActionService;
    }
}

