package ru.citeck.ecos.eform.service;

import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.alfresco.model.ContentModel;
import org.alfresco.repo.transaction.AlfrescoTransactionSupport;
import org.alfresco.service.cmr.repository.NodeRef;
import org.alfresco.service.cmr.repository.NodeService;
import org.alfresco.service.namespace.NamespaceService;
import org.alfresco.service.namespace.QName;
import org.alfresco.util.GUID;
import org.alfresco.util.transaction.TransactionListenerAdapter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.ConcurrencyFailureException;
import org.springframework.stereotype.Component;

import java.io.Serializable;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

@Component
public class TempFilesParentComponent {

    private static final NodeRef ROOT_NODE_REF = new NodeRef("workspace://SpacesStore/eform-files-temp-root");

    private static final int DIRECTORY_MAX_CHILDREN = 5000;

    /**
     * Random instance ID to ensure that multiple instances of alfresco will not conflict with each other
     */
    private final String daoInstanceId = generateComponentInstanceId();

    private RootDirectory newFilesParent;
    private final Object newFilesParentLock = new Object();

    private final AtomicBoolean newFilesParentReady = new AtomicBoolean();
    private String timeContainerKey;
    private NodeRef timeContainerRef;
    private final Object timeContainerLock = new Object();

    @Autowired
    private NodeService nodeService;

    /**
     * Note: this method increment local children counter of parent node
     * without any checking of real data to improve performance
     */
    public NodeRef getOrCreateParentForNewTempFile() {

        String currentTimeKey = LocalDate.now() + "-" + daoInstanceId;

        synchronized (newFilesParentLock) {
            if (!isValidParentDirectory(newFilesParent, currentTimeKey)) {
                newFilesParentReady.set(false);
                newFilesParent = createNewRootDirectory(currentTimeKey);
                AlfrescoTransactionSupport.bindListener(new TransactionListenerAdapter() {
                    @Override
                    public void afterRollback() {
                        // something went wrong and we should reset created root node
                        newFilesParent = null;
                    }
                    @Override
                    public void afterCommit() {
                        newFilesParentReady.set(true);
                    }
                });
            } else if (!newFilesParentReady.get()) {
                throw new ConcurrencyFailureException("Parent directory is unready");
            }
        }
        newFilesParent.count.incrementAndGet();
        return newFilesParent.nodeRef;
    }

    @NotNull
    private RootDirectory createNewRootDirectory(@NotNull String timeKey) {

        NodeRef timeDirectory = getTimeDirectory(timeKey);

        String name = GUID.generate();
        Map<QName, Serializable> props = new HashMap<>();
        props.put(ContentModel.PROP_NAME, name);

        NodeRef nodeRef = nodeService.createNode(
            timeDirectory,
            ContentModel.ASSOC_CHILDREN,
            QName.createQName(NamespaceService.CONTENT_MODEL_1_0_URI, name),
            ContentModel.TYPE_CONTAINER,
            props
        ).getChildRef();

        return new RootDirectory(timeKey, nodeRef);
    }
    @NotNull
    private NodeRef getTimeDirectory(@NotNull String timeKey) {
        synchronized (timeContainerLock) {
            if (timeKey.equals(timeContainerKey) && timeContainerRef != null) {
                if (!nodeService.exists(timeContainerRef)) {
                    throw new ConcurrencyFailureException("Timer container created, but not accessible yet");
                }
                return timeContainerRef;
            }

            Map<QName, Serializable> props = new HashMap<>();
            props.put(ContentModel.PROP_NAME, timeKey);

            timeContainerKey = timeKey;
            timeContainerRef = nodeService.createNode(
                ROOT_NODE_REF,
                ContentModel.ASSOC_CHILDREN,
                QName.createQName(NamespaceService.CONTENT_MODEL_1_0_URI, timeKey),
                ContentModel.TYPE_CONTAINER,
                props
            ).getChildRef();

            AlfrescoTransactionSupport.bindListener(new TransactionListenerAdapter() {
                @Override
                public void afterRollback() {
                    // something went wrong and we should reset created root node
                    timeContainerKey = "";
                    timeContainerRef = null;
                }
            });
        }
        return timeContainerRef;
    }

    private static String generateComponentInstanceId() {
        return UUID.randomUUID()
            .toString()
            .substring(0, 20)
            .toLowerCase()
            .replace("-", "");
    }

    private boolean isValidParentDirectory(@Nullable RootDirectory directory, @NotNull String timeKey) {
        return directory != null
            && directory.timeKey.equals(timeKey)
            && directory.count.get() < DIRECTORY_MAX_CHILDREN;
    }

    @Data
    @RequiredArgsConstructor
    private static class RootDirectory {
        @NotNull
        private final String timeKey;
        @NotNull
        private final NodeRef nodeRef;
        @NotNull
        private final AtomicLong count = new AtomicLong();
    }
}
