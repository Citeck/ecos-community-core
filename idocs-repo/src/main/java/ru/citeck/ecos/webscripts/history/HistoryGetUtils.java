package ru.citeck.ecos.webscripts.history;

import ecos.guava30.com.google.common.cache.CacheBuilder;
import ecos.guava30.com.google.common.cache.CacheLoader;
import ecos.guava30.com.google.common.cache.LoadingCache;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.alfresco.service.namespace.NamespaceService;
import org.alfresco.service.namespace.QName;
import org.apache.commons.lang.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.extensions.surf.util.I18NUtil;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.util.concurrent.TimeUnit;

@Component
@RequiredArgsConstructor(onConstructor_={@Autowired})
public class HistoryGetUtils {

    private LoadingCache<TaskTitleKey, String> taskTitleKeys;
    private final NamespaceService namespaceService;

    @PostConstruct
    public void init() {
        taskTitleKeys = CacheBuilder.newBuilder()
            .expireAfterAccess(5, TimeUnit.MINUTES)
            .maximumSize(200)
            .build(CacheLoader.from(this::getOutcomeTitleKeyImpl));
    }

    @NotNull
    public String getOutcomeTitle(@Nullable String taskTypeShort,
                                  @Nullable String taskDefinitionKey,
                                  @Nullable String outcome) {

        String notNullOutcome = StringUtils.defaultString(outcome);
        String key = getOutcomeTitleKey(taskTypeShort, taskDefinitionKey, outcome);
        if (StringUtils.isBlank(key)) {
            return notNullOutcome;
        }
        String result = I18NUtil.getMessage(key);
        return StringUtils.isNotBlank(result) ? result : notNullOutcome;
    }

    @NotNull
    public String getOutcomeTitleKey(@Nullable String taskType,
                                     @Nullable String taskDefinitionKey,
                                     @Nullable String outcome) {
        String taskTypeShortName;
        if (taskType == null) {
            taskTypeShortName = "";
        } else {
            if (taskType.contains("{")) {
                try {
                    QName qname = QName.resolveToQName(namespaceService, taskType);
                    taskTypeShortName = qname.toPrefixString(namespaceService);
                } catch (IllegalArgumentException e) {
                    taskTypeShortName = taskType;
                }
            } else {
                taskTypeShortName = taskType;
            }
        }
        return taskTitleKeys.getUnchecked(new TaskTitleKey(
            StringUtils.defaultString(taskTypeShortName),
            StringUtils.defaultString(taskDefinitionKey),
            StringUtils.defaultString(outcome)
        ));
    }

    @NotNull
    private String getOutcomeTitleKeyImpl(@Nullable TaskTitleKey taskTitleKey) {

        if (taskTitleKey == null || taskTitleKey.outcome.isEmpty()) {
            return "";
        }

        String taskTypeShort = taskTitleKey.taskType;
        String taskDefinitionKey = taskTitleKey.getTaskDefKey();
        String outcome = taskTitleKey.outcome;

        if (StringUtils.isNotBlank(taskDefinitionKey)) {
            String key = "flowable.form.button." + taskDefinitionKey + "." + outcome + ".label";
            String title = I18NUtil.getMessage(key);
            if (StringUtils.isNotBlank(title)) {
                return key;
            }
        }

        if (StringUtils.isNotBlank(taskTypeShort)) {
            String correctType = taskTypeShort.replaceAll(":", "_");
            String key = "workflowtask." + correctType + ".outcome." + outcome;
            String title = I18NUtil.getMessage(key);
            if (StringUtils.isNotBlank(title)) {
                return key;
            }
        }

        String key = "workflowtask.outcome." + outcome;
        String title = I18NUtil.getMessage(key);
        return StringUtils.isNotBlank(title) ? key : "";
    }

    @Data
    @AllArgsConstructor
    private static class TaskTitleKey {
        @NotNull
        private final String taskType;
        @NotNull
        private final String taskDefKey;
        @NotNull
        private final String outcome;
    }
}
