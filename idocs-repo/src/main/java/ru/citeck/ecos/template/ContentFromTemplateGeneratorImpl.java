/*
 * Copyright (C) 2008-2015 Citeck LLC.
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
package ru.citeck.ecos.template;

import org.alfresco.model.ContentModel;
import org.alfresco.model.RenditionModel;
import org.alfresco.repo.version.VersionModel;
import org.alfresco.service.cmr.repository.*;
import org.alfresco.service.cmr.version.VersionService;
import org.alfresco.service.namespace.RegexQNamePattern;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.extensions.surf.util.I18NUtil;

import ru.citeck.ecos.model.DmsModel;
import ru.citeck.ecos.utils.RepoUtils;
import ru.citeck.ecos.exception.ExceptionService;
import ru.citeck.ecos.exception.ExceptionTranslator;

import java.io.*;
import java.nio.charset.Charset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * @author Alexander Nemerov <alexander.nemerov@citeck.ru>
 * date: 06.05.14
 */
class ContentFromTemplateGeneratorImpl implements ContentFromTemplateGenerator {

    private static final String MESSAGE_AUTO_GENERATED = "version.auto.generated";
    private static final String EMPTY_EXTENSION = "";

    private static final Log logger = LogFactory.getLog(ContentFromTemplateGeneratorImpl.class);

    private static final String KEY_DOCUMENT = "document";
    private static final String DOCX_EXTENSION = "docx";

    private NodeService nodeService;
    private VersionService versionService;
    private ContentService contentService;
    private TemplateService templateService;
    private ExceptionService exceptionService;
    private MimetypeService mimetypeService;

    @Override
    public void generateContentByTemplate(NodeRef nodeRef) {
        generateContentByTemplate(nodeRef, null);
    }

    @Override
    public void generateContentByTemplate(NodeRef nodeRef, String historyDescriptionText) {
        // check existence
        if (!nodeService.exists(nodeRef)) {
            logger.debug("Skipped non-existing nodeRef: " + nodeRef);
            return;
        }

        // get template
        List<AssociationRef> assocs = nodeService.getTargetAssocs(nodeRef, DmsModel.ASSOC_TEMPLATE);
        if (assocs == null || assocs.isEmpty()) {
            if (logger.isWarnEnabled()) {
                logger.warn("There is no template (" + DmsModel.ASSOC_TEMPLATE + ")");
            }
            return;
        }
        NodeRef template = assocs.get(0).getTargetRef();

        ContentData templateContent = (ContentData) nodeService.getProperty(template, ContentModel.PROP_CONTENT);
        if (templateContent == null) {
            throw new IllegalStateException("Template " + template + " has no content");
        }

        // get template encoding
        String encoding = "ISO-8859-1";
        String docxTemplateMimetype = mimetypeService.getMimetypesByExtension().get(DOCX_EXTENSION);
        if (nodeService.hasAspect(nodeRef, DmsModel.ASPECT_TEMPLATEABLE)
                && !Objects.equals(templateContent.getEncoding(), "")
                && templateContent.getEncoding() != null
                && !templateContent.getMimetype().equals(docxTemplateMimetype)) {
            encoding = templateContent.getEncoding();
        }

        String mimetype = templateContent.getMimetype();

        ContentWriter contentWriter;
        Writer writer = null;
        try {
            // get content writer:
            contentWriter = contentService.getWriter(nodeRef, ContentModel.PROP_CONTENT, true);
            contentWriter.setMimetype(mimetype);

            /*
             * Do not change specified character-set ISO-8859-1, because of
             * output stream returns bytes, but writer returns characters.
             * So we should not change that output stream. This character-set
             * does not change anything.
             * If the document has a template - encoding is taken from a template.
             * */
            writer = new OutputStreamWriter(contentWriter.getContentOutputStream(), Charset.forName(encoding));

            // process template
            Map<String, Object> model = new HashMap<>();
            model.put(KEY_DOCUMENT, nodeRef);
            try {
                templateService.processTemplate(template.toString(), model, writer);
            } catch (Exception e) {
                logger.error("Content generation failure for " + nodeRef, e);
                ExceptionTranslator translator = exceptionService.getExceptionTranslator(
                        template,
                        DmsModel.PROP_ERROR_MESSAGE_CONFIG);
                throw new RuntimeException(translator.translateException(e), e);
            }
            if (logger.isDebugEnabled())
                logger.debug("Content successfully generated. node=" + nodeRef);

        } finally {
            if (writer != null) {
                try {
                    writer.close();
                } catch (IOException e) {
                    if (logger.isErrorEnabled())
                        logger.error("Failed to close writer for node " + nodeRef, e);
                }
            }
        }

        removeThumbnails(nodeRef);

        Map<String, Serializable> versionProperties = new HashMap<>(3);
        versionProperties.put(
                VersionModel.PROP_DESCRIPTION,
                historyDescriptionText != null ? historyDescriptionText : I18NUtil.getMessage(MESSAGE_AUTO_GENERATED)
        );
        RepoUtils.setUniqueOriginalName(nodeRef, EMPTY_EXTENSION, nodeService, mimetypeService);
        RepoUtils.createVersion(nodeRef, versionProperties, nodeService, versionService);
    }

    private void removeThumbnails (NodeRef nodeRef) {

        List<ChildAssociationRef> thAssocs = nodeService.getChildAssocs(
                nodeRef,
                RenditionModel.ASSOC_RENDITION,
                RegexQNamePattern.MATCH_ALL);

        if (thAssocs != null) {
            for (ChildAssociationRef thAssoc : thAssocs) {
                nodeService.removeChildAssociation(thAssoc);
                if (nodeService.hasAspect(nodeRef, ContentModel.ASPECT_THUMBNAIL_MODIFICATION)) {
                    nodeService.removeAspect(nodeRef, ContentModel.ASPECT_THUMBNAIL_MODIFICATION);
                }
            }
        }
    }

    public void setNodeService(NodeService nodeService) {
        this.nodeService = nodeService;
    }

    public void setVersionService(VersionService versionService) {
        this.versionService = versionService;
    }

    public void setContentService(ContentService contentService) {
        this.contentService = contentService;
    }

    public void setTemplateService(TemplateService templateService) {
        this.templateService = templateService;
    }

    public void setExceptionService(ExceptionService exceptionService) {
        this.exceptionService = exceptionService;
    }

	public void setMimetypeService(MimetypeService mimetypeService) {
		this.mimetypeService = mimetypeService;
	}

}
