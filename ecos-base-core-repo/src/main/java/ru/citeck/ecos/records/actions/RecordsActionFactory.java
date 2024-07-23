package ru.citeck.ecos.records.actions;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import ru.citeck.ecos.action.group.*;
import ru.citeck.ecos.action.group.impl.BaseGroupAction;
import ru.citeck.ecos.records.RecordGroupActionsService;
import ru.citeck.ecos.webapp.api.entity.EntityRef;

import javax.annotation.PostConstruct;
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Action factory to work with mixed remote/local records
 */
public abstract class RecordsActionFactory implements GroupActionFactory<EntityRef> {

    private RecordGroupActionsService recordsService;
    private GroupActionService groupActionService;

    @PostConstruct
    public void registerFactory() {
        groupActionService.register(this);
        groupActionService.register(new RecordsActionLocalFactory());
    }

    @Override
    public final GroupAction<EntityRef> createAction(GroupActionConfig config) {
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
    protected abstract GroupAction<EntityRef> createRecordsAction(GroupActionConfig config);

    /**
     * Create local action to process results returned by action from "createRecordsAction"
     */
    protected GroupAction<ActionResult<EntityRef>> createResultsAction(GroupActionConfig baseConfig,
                                                                       GroupActionConfig recordsActionConfig) {
        return null;
    }

    protected String getRecordsActionId() {
        return getActionId() + "-local-records";
    }

    class RecordsActionLocalFactory implements GroupActionFactory<EntityRef> {

        @Override
        public GroupAction<EntityRef> createAction(GroupActionConfig config) {
            return createRecordsAction(config);
        }

        @Override
        public String getActionId() {
            return getRecordsActionId();
        }
    }

    class Action extends BaseGroupAction<EntityRef> {

        private GroupAction<ActionResult<EntityRef>> resultsAction;
        private GroupActionConfig recordsActionConfig;

        public Action(GroupActionConfig config) {
            super(config);

            recordsActionConfig = getRecordsActionConfig(config);
            recordsActionConfig.setAsync(false);
            recordsActionConfig.setActionId(getRecordsActionId());

            resultsAction = createResultsAction(config, recordsActionConfig);
            if (resultsAction != null) {
                resultsAction.addListener(results -> {
                    List<ActionResult<EntityRef>> recordsResults = results.stream().map(a ->
                            new ActionResult<>(a.getData().getData(), a.getStatus())).collect(Collectors.toList());
                    onProcessed(recordsResults);
                });
            }
        }

        @Override
        protected void processNodesImpl(List<EntityRef> nodes) {
            ActionResults<EntityRef> results = recordsService.executeAction(nodes, recordsActionConfig);
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

