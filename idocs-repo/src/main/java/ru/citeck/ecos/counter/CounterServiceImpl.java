/*
 * Copyright (C) 2008-2018 Citeck LLC.
 *
 * This file is part of Citeck EcoS
 *
 * Citeck EcoS is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Citeck EcoS is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with Citeck EcoS. If not, see <http://www.gnu.org/licenses/>.
 */
package ru.citeck.ecos.counter;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.alfresco.model.ContentModel;
import org.alfresco.service.cmr.repository.ChildAssociationRef;
import org.alfresco.service.cmr.repository.NodeRef;
import org.alfresco.service.cmr.repository.NodeService;
import org.alfresco.service.namespace.NamespaceService;
import org.alfresco.service.namespace.QName;
import org.alfresco.service.transaction.TransactionService;
import org.apache.commons.lang.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import ru.citeck.ecos.commands.CommandsService;
import ru.citeck.ecos.commands.dto.CommandResult;
import ru.citeck.ecos.locks.LockUtils;
import ru.citeck.ecos.model.CounterModel;
import ru.citeck.ecos.records.type.GetNextNumberCommand;
import ru.citeck.ecos.records.type.GetNextNumberResult;
import ru.citeck.ecos.records.type.SetNextNumberCommand;
import ru.citeck.ecos.webapp.api.constants.AppName;
import ru.citeck.ecos.webapp.api.entity.EntityRef;

import java.io.Serializable;
import java.util.Map;
import java.util.Objects;

@Slf4j
public class CounterServiceImpl implements CounterService {

    private static final String COUNTERS_PREFIX = "counter-%s";

    private NodeService nodeService;
    private NodeRef counterRoot;
    private TransactionService transactionService;
    private LockUtils lockUtils;

    private CommandsService commandsService;

    @Override
    public void switchToEmodelCounter(String alfCounterName, String emodelNumTemplateId, String emodelCounterKey) {
        lockUtils.doWithLock(
            String.format(COUNTERS_PREFIX, alfCounterName),
            () -> transactionService.getRetryingTransactionHelper().doInTransaction(() -> {
                NodeRef counterRef = getCounterNodeRef(alfCounterName, true);
                String currentId = (String) nodeService.getProperty(counterRef, CounterModel.PROP_EMODEL_NUM_COUNTER_ID);
                String newId = emodelNumTemplateId + '$' + emodelCounterKey;
                if (Objects.equals(currentId, newId)) {
                    return null;
                }
                log.info(
                    "Switch alfresco counter to emodel counter. " +
                    "alfCounterName: " + alfCounterName +
                    " emodelNumTemplateId: " + emodelNumTemplateId +
                    " emodelCounterKey: " + emodelCounterKey
                );
                nodeService.setProperty(counterRef, CounterModel.PROP_EMODEL_NUM_COUNTER_ID, newId);
                setCounterValue(counterRef, (long) nodeService.getProperty(counterRef, CounterModel.PROP_VALUE));
                return null;
            }, false, true));
    }

    @Override
    public void setCounterLast(final String counterName, final long value) {
        lockUtils.doWithLock(
            String.format(COUNTERS_PREFIX, counterName),
            () -> transactionService.getRetryingTransactionHelper().doInTransaction(() -> {
                NodeRef counter = getCounterNodeRef(counterName, true);
                setCounterValue(counter, value);
                return null;
            }, false, true));
    }

    @Override
    public Long getCounterLast(final String counterName) {
        NodeRef counter = getCounterNodeRef(counterName, false);
        if (counter == null) {
            return null;
        }
        return getCounterValue(counter);
    }

    @Override
    public Long getCounterNext(final String counterName, final boolean increment) {
        return lockUtils.doWithLock(
            String.format(COUNTERS_PREFIX, counterName),
            () -> transactionService.getRetryingTransactionHelper().doInTransaction(() -> {
                    NodeRef counter = getCounterNodeRef(counterName, increment);
                    if (counter == null) {
                        return null;
                    }
                    return getNextCounterValue(counter, increment);
                }, false, true)
            );
    }

    private NodeRef getCounterNodeRef(String counterName, boolean createIfAbsent) {
        NodeRef counter = nodeService.getChildByName(counterRoot, ContentModel.ASSOC_CONTAINS, counterName);
        if (counter == null && createIfAbsent) {
            ChildAssociationRef counterRef = nodeService.createNode(counterRoot, ContentModel.ASSOC_CONTAINS,
                QName.createQName(NamespaceService.CONTENT_MODEL_1_0_URI, counterName), CounterModel.TYPE_COUNTER);
            counter = counterRef.getChildRef();
            nodeService.setProperty(counter, ContentModel.PROP_NAME, counterName);
        }
        return counter;
    }

    private long getCounterValue(NodeRef counter) {
        return getNextCounterValue(counter, false) - 1;
    }

    private void setCounterValue(NodeRef counter, long value) {
        Map<QName, Serializable> properties = nodeService.getProperties(counter);

        String emodelNumCounterId = (String) properties.get(CounterModel.PROP_EMODEL_NUM_COUNTER_ID);
        if (EmodelCounterId.isValidId(emodelNumCounterId)) {
            setNextNumberForEmodel(emodelNumCounterId, value + 1);
        } else {
            nodeService.setProperty(counter, CounterModel.PROP_VALUE, value);
        }
    }

    private long getNextCounterValue(NodeRef counter, boolean increment) {

        Map<QName, Serializable> properties = nodeService.getProperties(counter);

        String emodelNumCounterId = (String) properties.get(CounterModel.PROP_EMODEL_NUM_COUNTER_ID);
        if (StringUtils.isNotBlank(emodelNumCounterId) && emodelNumCounterId.contains("$")) {
            return getNextNumberFromEmodel(emodelNumCounterId, increment);
        }

        long result = (Long) properties.get(CounterModel.PROP_VALUE) + 1;
        if (increment) {
            nodeService.setProperty(counter, CounterModel.PROP_VALUE, result);
        }
        return result;
    }

    private long getNextNumberFromEmodel(String emodelNumCounterId, boolean increment) {

        EmodelCounterId emodelCounter = EmodelCounterId.parse(emodelNumCounterId);

        GetNextNumberCommand command = new GetNextNumberCommand(emodelCounter.templateRef, emodelCounter.counterKey);
        command.setIncrement(increment);

        CommandResult numberRes = commandsService.executeSync(command, AppName.EMODEL);
        throwRespErrorIfRequired(numberRes, emodelCounter);

        GetNextNumberResult result = numberRes.getResultAs(GetNextNumberResult.class);

        Long number = result != null ? result.getNumber() : null;
        if (number == null) {
            throw new IllegalStateException("Number can't be generated");
        }
        return number;
    }

    private void setNextNumberForEmodel(String emodelNumCounterId, long value) {

        EmodelCounterId emodelCounter = EmodelCounterId.parse(emodelNumCounterId);

        SetNextNumberCommand command = new SetNextNumberCommand(
            emodelCounter.templateRef,
            emodelCounter.counterKey,
            value
        );
        CommandResult commandResp = commandsService.executeSync(command, AppName.EMODEL);
        throwRespErrorIfRequired(commandResp, emodelCounter);
    }

    private void throwRespErrorIfRequired(CommandResult commandResult, EmodelCounterId counterRef) {

        Runnable printErrorMsg = () -> log.error(
            "Get next number failed. TemplateRef: " + counterRef.templateRef
                + " counterKey: " + counterRef.counterKey
        );

        commandResult.throwPrimaryErrorIfNotNull(printErrorMsg);

        if (commandResult.getErrors().size() > 0) {
            printErrorMsg.run();
            throw new RuntimeException("Error");
        }
    }

    public void setNodeService(NodeService nodeService) {
        this.nodeService = nodeService;
    }

    public void setCounterRoot(String counterRoot) {
        this.counterRoot = new NodeRef(counterRoot);
    }

    public void setTransactionService(TransactionService transactionService) {
        this.transactionService = transactionService;
    }

    @Autowired
    public void setLockUtils(LockUtils lockUtils) {
        this.lockUtils = lockUtils;
    }

    @Autowired
    public void setCommandsService(CommandsService commandsService) {
        this.commandsService = commandsService;
    }

    @Data
    @AllArgsConstructor
    private static class EmodelCounterId {

        private EntityRef templateRef;
        private String counterKey;

        static boolean isValidId(String value) {
            return StringUtils.isNotBlank(value) && value.indexOf('$') != -1;
        }

        static EmodelCounterId parse(String value) {

            int delimIdx = value.indexOf('$');
            if (delimIdx == -1) {
                throw new RuntimeException("Invalid emodelNumCounterId: '" + value + "'");
            }

            String templateRefLocalId = value.substring(0, delimIdx);
            EntityRef templateRef = EntityRef.create(AppName.EMODEL, "num-template", templateRefLocalId);

            String counterKey = value.substring(delimIdx + 1);

            return new EmodelCounterId(templateRef, counterKey);
        }
    }
}
