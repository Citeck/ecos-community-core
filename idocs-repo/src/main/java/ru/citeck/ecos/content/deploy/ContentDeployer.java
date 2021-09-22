package ru.citeck.ecos.content.deploy;

import org.alfresco.model.ContentModel;
import org.alfresco.repo.security.authentication.AuthenticationUtil;
import org.alfresco.repo.transaction.RetryingTransactionHelper;
import org.alfresco.repo.version.VersionModel;
import org.alfresco.service.ServiceRegistry;
import org.alfresco.service.cmr.repository.*;
import org.alfresco.service.cmr.version.VersionService;
import org.alfresco.service.cmr.version.VersionType;
import org.alfresco.service.namespace.QName;
import org.alfresco.util.ParameterCheck;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.beans.factory.BeanNameAware;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEvent;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.extensions.surf.util.AbstractLifecycleBean;
import ru.citeck.ecos.content.ContentData;
import ru.citeck.ecos.content.RepoContentDAO;
import ru.citeck.ecos.content.metadata.MetadataExtractor;
import ru.citeck.ecos.model.EcosContentModel;

import javax.annotation.PostConstruct;
import java.io.ByteArrayInputStream;
import java.io.Serializable;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

public class ContentDeployer<T> extends AbstractLifecycleBean implements BeanNameAware {

    private static final Log logger = LogFactory.getLog(ContentDeployer.class);

    private RetryingTransactionHelper txnHelper;

    private Set<QName> metadataKeys = Collections.singleton(ContentModel.PROP_NAME);
    private RepoContentDAO<T> repoContentDAO;

    private NodeService nodeService;
    private ContentService contentService;
    private VersionService versionService;
    private MimetypeService mimetypeService;

    private MetadataExtractor<T> metadataExtractor;

    private String beanName;
    private String artifactType;
    private List<String> locations;

    private boolean enabled = true;

    private boolean deployIfNotExistsOnly = false;

    public ContentDeployer() {
    }

    public ContentDeployer(String artifactType) {
        this.artifactType = artifactType;
    }

    @PostConstruct
    public void init() {
        ParameterCheck.mandatory("repoContentDAO", repoContentDAO);
        if (metadataExtractor == null) {
            metadataExtractor = new MetadataExtractor<T>() {};
        }
    }

    public void load() {

        if (locations == null || locations.isEmpty()) {
            logger.info(beanName + ": nothing to deploy");
            return;
        }

        AuthenticationUtil.runAsSystem(() ->
                txnHelper.doInTransaction(() -> {
                    deployInTxn(locations);
                    return null;
                }, false)
        );
    }

    private void deployInTxn(List<String> locations) {

        logger.info(beanName + ": deploying " + artifactType + " (" + locations.size() + " locations)");

        Map<Map<QName, Serializable>, ContentInfo> contentInfo = new HashMap<>();

        for (int i = 0; i < locations.size(); i++) {

            ContentInfo info = getContentInfo(locations.get(i), i);
            if (info != null) {
                contentInfo.put(info.keys, info);
            }
        }

        List<ContentInfo> infoList = new ArrayList<>(contentInfo.values());
        infoList.sort(null);

        for (ContentInfo info : infoList) {

            if (logger.isDebugEnabled()) {
                logger.debug(beanName + ": deploying " + artifactType + ": " + info.location);
            }
            deployImpl(info);
            if (logger.isDebugEnabled()) {
                logger.debug(beanName + ": successfully deployed " + artifactType + ": " + info.location);
            }
        }
    }

    public void load(String location) {
        AuthenticationUtil.runAsSystem(() ->
            txnHelper.doInTransaction(() -> {
                deployImpl(getContentInfo(location, 0));
                return null;
            }, false)
        );
    }

    private ContentInfo getContentInfo(String location, int idx) {

        ContentInfo info = new ContentInfo(idx);
        info.location = location;

        try {
            // default protocol is classpath
            Resource resource = location.contains(":") ? new UrlResource(location) : new ClassPathResource(location);
            info.data = IOUtils.toByteArray(resource.getInputStream());
            info.url = resource.getURL().toString();
        } catch (Exception e) {
            throw new IllegalStateException("Could not deploy " + artifactType + ", location: " + location
                    + ", deployer: " + beanName, e);
        }

        info.parsedObject = repoContentDAO.getContentDAO().read(info.data);

        info.metadata = new HashMap<>(metadataExtractor.getMetadata(info.parsedObject));

        if (!info.metadata.containsKey(ContentModel.PROP_NAME)) {
            info.metadata.put(ContentModel.PROP_NAME, FilenameUtils.getName(location));
        }

        info.keys = new HashMap<>();
        metadataKeys.forEach(keyName -> {
            Serializable value = info.metadata.get(keyName);
            info.keys.put(keyName, value);
        });

        if (!checkKeys(info.keys)) {
            logger.warn("Content keys is incorrect. Ignore it. File: " + location);
            return null;
        }

        return info;
    }

    private boolean checkKeys(Map<QName, Serializable> keys) {

        if (keys.isEmpty() || keys.values().stream().allMatch(Objects::isNull)) {
            logger.warn("Content keys is empty");
            return false;
        }

        AtomicBoolean isKeysCorrect = new AtomicBoolean(true);
        keys.forEach((k, v) -> {
            if (v instanceof NodeRef && !nodeService.exists((NodeRef) v)) {
                isKeysCorrect.set(false);
                logger.warn("Value doesn't exists: key: " + k + " value: " + v);
            }
        });

        return isKeysCorrect.get();
    }

    private void deployImpl(ContentInfo info) {

        if (info == null) {
            return;
        }

        ByteArrayInputStream inputBytesStream = new ByteArrayInputStream(info.data);

        Optional<? extends ContentData<?>> data = repoContentDAO.getFirstContentData(info.keys, false);
        NodeRef contentNode = data.map(ContentData::getNodeRef).orElse(null);

        if (contentNode == null) {
            contentNode = repoContentDAO.createNode(info.metadata);
        } else if (deployIfNotExistsOnly) {
            return;
        }

        String deployedChecksum = (String) nodeService.getProperty(contentNode,
                                                                   EcosContentModel.PROP_DEPLOYED_CHECKSUM);

        String checksum = DigestUtils.md5Hex(info.data);

        if (deployedChecksum == null || !deployedChecksum.equals(checksum)) {
            logger.info(beanName + ": deploy " + artifactType + ": " + info.location);

            Map<String, Serializable> versProps = Collections.singletonMap(VersionModel.PROP_VERSION_TYPE,
                                                                           VersionType.MAJOR);

            versionService.createVersion(contentNode, versProps, false);

            inputBytesStream.reset();
            String mimetype = mimetypeService.guessMimetype(info.location, inputBytesStream);

            inputBytesStream.reset();
            ContentWriter writer = contentService.getWriter(contentNode, ContentModel.PROP_CONTENT, true);
            writer.setEncoding("UTF-8");
            writer.setMimetype(mimetype);
            writer.putContent(inputBytesStream);

            nodeService.setProperty(contentNode, EcosContentModel.PROP_DEPLOYED_CHECKSUM, checksum);
        }
    }

    @Autowired
    public void setServiceRegistry(ServiceRegistry serviceRegistry) {
        txnHelper = serviceRegistry.getRetryingTransactionHelper();
        contentService = serviceRegistry.getContentService();
        nodeService = serviceRegistry.getNodeService();
        versionService = serviceRegistry.getVersionService();
        mimetypeService = serviceRegistry.getMimetypeService();
    }

    @Override
    protected void onBootstrap(ApplicationEvent event) {
        if (enabled) {
            load();
        }
    }

    @Override
    protected void onShutdown(ApplicationEvent event) {
        // NOOP
    }

    @Override
    public void setBeanName(String beanName) {
        this.beanName = beanName;
    }

    public List<String> getLocations() {
        return locations;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public void setLocation(String location) {
        this.locations = Collections.singletonList(location);
    }

    public void setLocations(List<String> locations) {
        this.locations = new ArrayList<>(locations);
    }

    public void setDeployIfNotExistsOnly(boolean deployIfNotExistsOnly) {
        this.deployIfNotExistsOnly = deployIfNotExistsOnly;
    }

    public String getBeanName() {
        return beanName;
    }

    public void addLocation(String location) {
        if (this.locations == null) {
            locations = new ArrayList<>();
        }
        locations.add(location);
    }

    public String getArtifactType() {
        return artifactType;
    }

    public void setArtifactType(String artifactType) {
        this.artifactType = artifactType;
    }

    public void setMetadataKeys(Set<QName> metadataKeys) {
        this.metadataKeys = metadataKeys;
    }

    public void setRepoContentDAO(RepoContentDAO<T> repoContentDAO) {
        this.repoContentDAO = repoContentDAO;
    }

    public void setMetadataExtractor(MetadataExtractor<T> metadataExtractor) {
        this.metadataExtractor = metadataExtractor;
    }

    class ContentInfo implements Comparable<ContentInfo> {

        String url;
        String location;

        int order;
        byte[] data;
        T parsedObject;

        Map<QName, Serializable> keys;
        Map<QName, Serializable> metadata;

        ContentInfo(int order) {
            this.order = order;
        }

        @Override
        public int compareTo(ContentInfo o) {
            return Integer.compare(order, o.order);
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            ContentInfo that = (ContentInfo) o;
            return Objects.equals(keys, that.keys);
        }

        @Override
        public int hashCode() {
            return Objects.hash(keys);
        }
    }
}
