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

import com.google.common.collect.ImmutableMap;
import lombok.extern.slf4j.Slf4j;
import org.alfresco.model.ContentModel;
import org.alfresco.repo.processor.BaseProcessor;
import org.alfresco.repo.template.FreeMarkerProcessor;
import org.alfresco.service.cmr.repository.ContentReader;
import org.alfresco.service.cmr.repository.NodeRef;
import org.alfresco.service.cmr.repository.TemplateProcessor;
import org.docx4j.XmlUtils;
import org.docx4j.jaxb.XPathBinderAssociationIsPartialException;
import org.docx4j.openpackaging.exceptions.Docx4JException;
import org.docx4j.openpackaging.io3.Save;
import org.docx4j.openpackaging.packages.WordprocessingMLPackage;
import org.docx4j.openpackaging.parts.CustomXmlDataStoragePart;
import org.docx4j.openpackaging.parts.JaxbXmlPartXPathAware;
import org.docx4j.openpackaging.parts.Part;
import org.docx4j.openpackaging.parts.PartName;
import org.docx4j.wml.ContentAccessor;
import org.docx4j.wml.P;
import org.docx4j.wml.Text;
import org.mozilla.javascript.NativeJavaObject;
import org.w3c.dom.Element;
import ru.citeck.ecos.model.DmsModel;

import javax.xml.bind.JAXBElement;
import javax.xml.bind.JAXBException;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * DOCX template processor with support of FreeMarker tags.
 *
 * Following type of tags is supported:
 * 1) freemarker interpolations @{code ${ ... } }
 * 2) freemarker single directives, e.g. @{code [#include ... /]} or @{code [@user-defined-macro ... /]}.
 * 3) special grouping tags @{code [# ... #]}
 * 4) in the template you can output a new line symbol ( like \r\n or \n ),
 *    after processing this symbol is representing as new paragraph in the document with the same style.
 *
 * Note. To include directives, that contain open and close tags (e.g. @{code [#if]...[/#if]}),
 *       one should use grouping tags.
 *       This tags mark beginning and ending of freemarker code.
 *
 * Note. Only square bracket tags are supported,
 * i.e. @{code [#assign ...]}, but not @{code <#assign ...>}.
 *
 * @author Alexander Nemerov
 * @date 24.07.13
 */
@Slf4j
public class DocxFreeMarkerProcessor extends BaseProcessor implements TemplateProcessor {

    private FreeMarkerProcessor processor;
    private String newLineRegexp = "\\r?\\n";
    Pattern newLinePattern = Pattern.compile(newLineRegexp, Pattern.DOTALL);

    protected synchronized WordprocessingMLPackage getWordTemplate(NodeRef templateNode) {
        ContentReader reader = this.services.getContentService()
                .getReader(templateNode, ContentModel.PROP_CONTENT);
        String extension = null;
        String mimetype = reader.getMimetype();
        if (mimetype != null) {
            extension = services.getMimetypeService().getExtension(mimetype);
        }
        if (!"docx".equals(extension)) {
            throw new IllegalStateException("Could not read docx from node: " + templateNode);
        }
        try (InputStream reportStream = reader.getContentInputStream()) {
            WordprocessingMLPackage wordMLPackage = WordprocessingMLPackage.load(reportStream);
            return (WordprocessingMLPackage) wordMLPackage.clone();
        } catch (Docx4JException | IOException e) {
            throw new IllegalStateException("Could not read docx from node", e);
        }
    }

    // @Override
    // first appeared in 4.2.d
    // locale is ignored
    public void process(String template, Object model, Writer out, Locale locale) {
        process(template, model, out);
    }

    @Override
    public void process(String template, Object model, Writer out) {
        log.debug("Start processing template " + template);
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        process(template, model, outputStream);

        try {
            out.write(new String(outputStream.toByteArray(), StandardCharsets.ISO_8859_1));
        } catch (IOException e) {
            log.error("Write failed", e);
        }
    }

    public void process(String template, Object model, OutputStream out) {
        NodeRef templateNodeRef = new NodeRef(template);
        WordprocessingMLPackage wpMLPackage = getWordTemplate(templateNodeRef);

        model = prepareModel(wpMLPackage, model);

        // process each part of document, that has texts
        HashMap<PartName, Part> parts = wpMLPackage.getParts().getParts();
        for(Part part : parts.values()) {
            if(part instanceof JaxbXmlPartXPathAware) {
                try {
                    // get paragraphs of the part
                    @SuppressWarnings("unchecked")
                    List<Object> paragraphs = ((JaxbXmlPartXPathAware<Object>) part).getJAXBNodesViaXPath(".//w:p", true);
                    for (Object paragraph : paragraphs) {
                        if(paragraph instanceof P) {
                            P p = (P)paragraph;
                            List<Text> texts = getTexts(part, paragraph);
                            // process texts
                            if (!texts.isEmpty()) {
                                replaceAllNewLines(texts);
                                processTexts(texts, model);
                                createNewParagraphsIfNeed(part, p, texts);
                            }
                        }
                    }
                } catch (JAXBException | Docx4JException e) {
                    log.error(e.getLocalizedMessage(), e);
                }
            }
        }

        postProcess(wpMLPackage);

        // save processed Wordprocessing ML package
        Save saver = new Save(wpMLPackage);
        try {
            saver.save(out);
        } catch (Docx4JException e) {
            log.error(e.getLocalizedMessage(), e);
        }
    }

    private Object prepareModel(WordprocessingMLPackage wpMLPackage, Object originModel) {

        if (!(originModel instanceof Map)) {
            return originModel;
        }

        List<String> scripts = wpMLPackage
            .getCustomXmlDataStorageParts()
            .values()
            .stream()
            .filter(customXmlPart -> customXmlPart instanceof CustomXmlDataStoragePart)
            .map(customXmlPart -> {
                try {
                    Element documentElement = ((CustomXmlDataStoragePart) customXmlPart).getData().getDocument().getDocumentElement();
                    if (!DmsModel.DMS_NAMESPACE.equals(documentElement.getNamespaceURI())) {
                        return null;
                    }
                    return documentElement.getTextContent();
                } catch (Docx4JException e) {
                    log.error("Unable to read custom xml part data", e);
                    return null;
                }
            })
            .filter(Objects::nonNull)
            .collect(Collectors.toList());

        if (scripts.size() == 0) {
            return originModel;
        }

        Map<Object, Object> resultModel = new HashMap<>((Map<?, ?>) originModel);

        Map<String, Object> scriptModel = ImmutableMap.of(
            "document", resultModel.get("document"),
            "model", resultModel
        );
        scripts.forEach(script -> scriptService.executeScriptString(script, scriptModel));
        resultModel.replaceAll((key, value) -> value instanceof NativeJavaObject ? ((NativeJavaObject) value).unwrap() : value);
        return resultModel;
    }

    protected void postProcess(WordprocessingMLPackage wpMLPackage) {
    }

    @Override
    public void processString(String template, Object model, Writer out) {
        processor.processString(template, model, out);
    }

    public void setProcessor(FreeMarkerProcessor freeMarkerProcessor) {
        this.processor = freeMarkerProcessor;
    }

    public void setNewLineRegexp(String newLineRegexp) {
        this.newLineRegexp = newLineRegexp;
        this.newLinePattern = Pattern.compile(newLineRegexp, Pattern.DOTALL);
    }

    /**
     * Replaces all new line symbols on the space.
     * This method should be executed before processing template, because of
     * we should display all new line symbols after processing as new paragraphs.
     *
     * @param texts - input w:t elements from the paragraph
     */
    private void replaceAllNewLines(List<Text> texts) {
        // The old new-lines are not displayed in the real document, so we replace it on the space ' '.
        for (Text text : texts) {
            if (containsNewLine(text.getValue())) {
                if (log.isDebugEnabled())
                    log.debug("Found new line symbol in the template document (it is replacing on the space), see text: " + text.getValue());
                text.setValue(text.getValue().replaceAll(newLineRegexp, " "));
            }
        }
    }

    private enum LexerState {
        TEXT,
        EXPR,
        STRING
    }

    private void processTexts(List<Text> textParts, Object model) {
        log.debug("Start processing of texts: " + textParts.size());
        LexerState state = LexerState.TEXT;

        Map<Character, Character> parentheses = new HashMap<>();
        parentheses.put(']', '[');
        parentheses.put('}', '{');
        Collection<Character> openingParentheses = parentheses.values();
        Collection<Character> closingParentheses = parentheses.keySet();

        StringBuilder buffer = new StringBuilder();

        // process docx text parts
        Stack<Character> stack = new Stack<>();
        char prevChar = ' ';
        for (Text text : textParts) {
            String textValue = text.getValue();

            char[] chars = textValue.toCharArray();

            for (int i = 0; i < chars.length; i++) {
                switch (state) {
                    case TEXT:
                        if ((chars[i] == '#' || chars[i] == '@') && prevChar == '[') {
                            stack.push('[');
                            state = LexerState.EXPR;
                        }
                        if (chars[i] == '{' && prevChar == '$') {
                            stack.push('{');
                            state = LexerState.EXPR;
                        }
                        break;
                    case EXPR:
                        if (openingParentheses.contains(chars[i])) {
                            stack.push(chars[i]);
                        } else if (closingParentheses.contains(chars[i])) {
                            char last = stack.pop();
                            if (last != parentheses.get(chars[i])) {
                                throw new IllegalStateException("Wrong parentheses: " + last + chars[i]);
                            }
                            if (stack.isEmpty()) {
                                state = LexerState.TEXT;
                            }
                        }
                        if (chars[i] == '"' || chars[i] == '\'') {
                            stack.push(chars[i]);
                            state = LexerState.STRING;
                        }
                        break;
                    case STRING:
                        if (stack.peek().equals('\\')) {
                            stack.pop();
                        } else if (chars[i] == '\\') {
                            stack.push(chars[i]);
                        } else if (chars[i] == '"' || chars[i] == '\'') {
                            char last = stack.peek();
                            if (last == chars[i]) {
                                stack.pop();
                                state = LexerState.EXPR;
                            }
                        }
                        break;
                }
                prevChar = chars[i];
            }

            buffer.append(textValue);

            if (state.equals(LexerState.TEXT) && prevChar != '$' && prevChar != '[') {
                StringWriter textOut = new StringWriter();
                String templatePart = "[#ftl strip_whitespace=false/]"
                        + buffer.toString().replaceAll("\\[\\#(.*)\\#\\]", "$1");
                log.debug("Processing template: " + templatePart);
                processor.processString(templatePart, model, textOut);
                log.debug("Yields result: " + textOut.toString());
                text.setValue(textOut.toString());
                if (textOut.toString().startsWith(" ") || textOut.toString().endsWith(" "))
                    text.setSpace("preserve");
                buffer = new StringBuilder();
            } else {
                text.setValue("");
            }
        }

        if (!state.equals(LexerState.TEXT)) {
            throw new IllegalStateException("Expression not closed, stack is " + stack);
        }
    }

    /**
     * Returns all text blocks of a specified paragraph.
     *
     * @param part - part of the document
     * @param paragraph - specified paragraph
     * @return list of text blocks ({@link Text})
     * @throws JAXBException
     * @throws XPathBinderAssociationIsPartialException
     */
    @SuppressWarnings("unchecked")
    private List<Text> getTexts(Part part, Object paragraph)
            throws JAXBException, Docx4JException {
        List<Object> elements = ((JaxbXmlPartXPathAware<Object>) part).getJAXBNodesViaXPath(".//w:t", paragraph, true);
        List<Text> texts = new ArrayList<>(elements.size());
        for (Object element : elements) {
            if(element instanceof JAXBElement) {
                texts.add((Text) ((JAXBElement<Object>) element).getValue());
            }
        }
        return texts;
    }

    /**
     * If item from the specified {@code texts} contains new line symbol, it
     * divides this paragraph onto several paragraphs.
     *
     * @param p - parent paragraph
     * @param texts - paragraph text items
     * @throws JAXBException
     */
    private void createNewParagraphsIfNeed(Part part, P p, List<Text> texts)
            throws JAXBException {
        if (!(part instanceof ContentAccessor))
            return;

        ContentAccessor contentAccessor = (ContentAccessor)part;
        int paragraphPos = contentAccessor.getContent().indexOf(p);

        if (paragraphPos < 0)
            return;

        if (!hasNewLines(texts))
            return;

        List<TextIndex> indexes = collectTextIndexes(texts);

        int j = 1;
        TextIndex prev = null;
        Object paragraph;
        List<TextIndex> paragraphText = new LinkedList<>();
        for (TextIndex index : indexes) {
            if (prev == null || prev.getNewLineIndex() == index.getNewLineIndex()) {
                paragraphText.add(index);
            }
            else {
                paragraph = createNewParagraph(p, paragraphText, texts);
                contentAccessor.getContent().add(paragraphPos + j, paragraph);
                paragraphText = new LinkedList<>();
                paragraphText.add(index);
                j++;
            }
            prev = index;
        }
        paragraph = createNewParagraph(p, paragraphText, texts);
        contentAccessor.getContent().add(paragraphPos + j, paragraph);
        contentAccessor.getContent().remove(p);
    }

    /**
     * It collects text indexes. Each index contains:
     *  - text item,
     *  - new paragraph index,
     *  - position in the specified {@code texts}
     *
     * @param texts - list of text items of the paragraph
     * @return
     */
    private List<TextIndex> collectTextIndexes(List<Text> texts) {
        List<TextIndex> indexes = new LinkedList<>();
        int textIndex = 0;
        int newLineIndex = 0;
        for (Text text : texts) {
            String[] lines = text.getValue().split(newLineRegexp);
            int i = 0;
            for (String line : lines) {
                indexes.add(new TextIndex(line, newLineIndex, textIndex));
                if (lines.length > 1 && i < lines.length - 1)
                    newLineIndex++;
                i++;
            }
            textIndex++;
        }
        return indexes;
    }

    private boolean hasNewLines(List<Text> texts) {
        boolean result = false;
        for (Text text : texts) {
            if (containsNewLine(text.getValue())) {
                result = true;
                break;
            }
        }
        return result;
    }

    /**
     * It creates new paragraph by specified paragraph template.
     * Current algorithm do:
     * 1. marshals the DOM node of the specified paragraph template.
     * 2. erases all text in all text blocks.
     * 3. sets content only for specified indexes.
     *
     * @param templateParagraph - paragraph template
     * @param paragraphText - list of {@link TextIndex}, which is in new paragraph
     * @param texts - list of the paragraph text blocks, currently it is used
     * only to check size of marshaled paragraph template {@code templateParagraph}.
     * @return
     * @throws JAXBException
     */
    private Object createNewParagraph(
            P templateParagraph,
            List<TextIndex> paragraphText,
            List<Text> texts) throws JAXBException {

        org.w3c.dom.Document doc = XmlUtils.marshaltoW3CDomDocument(templateParagraph);
        List<org.w3c.dom.Node> nodes = XmlUtils.xpath(doc, ".//w:t");
        if (nodes.size() == texts.size()) {
            for (org.w3c.dom.Node node : nodes)
                node.setTextContent("");
            for (TextIndex index : paragraphText) {
                org.w3c.dom.Node node = nodes.get(index.getTextIndex());
                node.setTextContent(index.getText());
            }
        }
        return XmlUtils.unmarshal(doc);
    }

    private boolean containsNewLine(String value) {
        Matcher regexMatcher = newLinePattern.matcher(value);
        return regexMatcher.find();
    }

    private final class TextIndex {
        private final String text;
        private final int newLineIndex;
        private final int textIndex;

        public TextIndex(String text, int newLineIndex, int textIndex) {
            super();
            this.text = text;
            this.newLineIndex = newLineIndex;
            this.textIndex = textIndex;
        }

        public String getText() {
            return text;
        }

        public int getNewLineIndex() {
            return newLineIndex;
        }

        public int getTextIndex() {
            return textIndex;
        }

        @Override
        public int hashCode() {
            final int prime = 31;
            int result = 1;
            result = prime * result + getOuterType().hashCode();
            result = prime * result + newLineIndex;
            result = prime * result + ((text == null) ? 0 : text.hashCode());
            result = prime * result + textIndex;
            return result;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }

            if (obj == null || getClass() != obj.getClass()) {
                return false;
            }

            TextIndex other = (TextIndex) obj;
            if (!getOuterType().equals(other.getOuterType())) {
                return false;
            }

            if (newLineIndex != other.newLineIndex) {
                return false;
            }

            if (!Objects.equals(text, other.text)) {
                return false;
            }

            if (textIndex != other.textIndex) {
                return false;
            }

            return true;
        }

        @Override
        public String toString() {
            return "TextIndex [text=" + text + ", newLineIndex=" + newLineIndex
                    + ", textIndex=" + textIndex + "]";
        }

        private DocxFreeMarkerProcessor getOuterType() {
            return DocxFreeMarkerProcessor.this;
        }

    }

}
