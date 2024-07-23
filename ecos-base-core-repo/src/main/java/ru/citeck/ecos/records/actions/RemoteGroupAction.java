package ru.citeck.ecos.records.actions;

import ru.citeck.ecos.action.group.ActionResult;
import ru.citeck.ecos.action.group.ActionStatus;
import ru.citeck.ecos.action.group.GroupActionConfig;
import ru.citeck.ecos.action.group.impl.BaseGroupAction;
import ru.citeck.ecos.records.RecordInfo;
import ru.citeck.ecos.records.RecordsGroupActionPost;
import ru.citeck.ecos.remote.RestConnection;
import ru.citeck.ecos.webapp.api.entity.EntityRef;

import java.util.*;
import java.util.stream.Collectors;

public class RemoteGroupAction<T> extends BaseGroupAction<RecordInfo<T>> {

    private final String targetAction;
    private final GroupActionConfig targetConfig;
    private final RestConnection restConn;

    private final String groupActionUrl;

    public RemoteGroupAction(GroupActionConfig config,
                             RestConnection restConn,
                             String groupActionUrl,
                             String targetAction,
                             GroupActionConfig targetConfig) {
        super(config);
        this.groupActionUrl = groupActionUrl;
        this.targetAction = targetAction;
        this.targetConfig = targetConfig;
        this.restConn = restConn;
    }

    @Override
    protected void processNodesImpl(List<RecordInfo<T>> nodes) {

        Map<EntityRef, RecordInfo<T>> infoMapping = new HashMap<>();

        RecordsGroupActionPost.ActionData data = new RecordsGroupActionPost.ActionData();
        data.config = targetConfig;

        data.nodes = nodes.stream().map(info -> {
            EntityRef id = EntityRef.valueOf(info.getRef().getId());
            infoMapping.put(id, info);
            return id;
        }).collect(Collectors.toList());

        RecordsGroupActionPost.Response response =
                restConn.jsonPost(groupActionUrl, data, RecordsGroupActionPost.Response.class);

        List<ActionResult<RecordInfo<T>>> results = new ArrayList<>();

        if (response != null) {
            for (ActionResult<EntityRef> result : response.results.getResults()) {
                RecordInfo<T> info = infoMapping.get(result.getData());
                results.add(new ActionResult<>(info, result.getStatus()));
            }
        } else {
            ActionStatus status = new ActionStatus(ActionStatus.STATUS_ERROR);
            status.setMessage("Remote action failed");
            nodes.forEach(info -> results.add(new ActionResult<>(info, status)));
        }

        onProcessed(results);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        if (!super.equals(o)) {
            return false;
        }

        RemoteGroupAction that = (RemoteGroupAction) o;

        return Objects.equals(targetAction, that.targetAction) &&
               Objects.equals(targetConfig, that.targetConfig) &&
               Objects.equals(groupActionUrl, that.groupActionUrl);
    }

    @Override
    public int hashCode() {
        int result = super.hashCode();
        result = 31 * result + Objects.hashCode(targetAction);
        result = 31 * result + Objects.hashCode(targetConfig);
        result = 31 * result + Objects.hashCode(groupActionUrl);
        return result;
    }
}
