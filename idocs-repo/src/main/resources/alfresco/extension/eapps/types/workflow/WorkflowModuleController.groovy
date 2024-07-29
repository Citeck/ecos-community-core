package alfresco.extension.eapps.types.workflow

import kotlin.Unit
import org.jetbrains.annotations.NotNull
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.w3c.dom.Document
import org.w3c.dom.NamedNodeMap
import org.w3c.dom.Node
import org.xml.sax.SAXException
import ru.citeck.ecos.apps.artifact.ArtifactMeta
import ru.citeck.ecos.apps.artifact.controller.ArtifactController
import ru.citeck.ecos.commons.io.file.EcosFile
import ru.citeck.ecos.commons.utils.FileUtils

import javax.xml.parsers.DocumentBuilder
import javax.xml.parsers.DocumentBuilderFactory
import javax.xml.parsers.ParserConfigurationException
import java.nio.charset.StandardCharsets
import java.util.stream.Collectors

class Module {
    String id
    byte[] xmlData
}

return new ArtifactController<Module, Unit>() {

    private static final Logger log = LoggerFactory.getLogger(ArtifactController.class)

    @Override
    List<Module> read(@NotNull EcosFile root, Unit config) {

        return root.findFiles("**.{bpmn,bpmn20.xml}")
            .stream()
            .map({ f -> Optional.ofNullable(readModule(f)) })
            .filter({ o -> o.isPresent() })
            .map( { o -> o.get() })
            .collect(Collectors.toList())
    }

    private static Module readModule(EcosFile bpmnFile) {

        def fileContent = bpmnFile.readAsString()
        if (!fileContent.contains("xmlns:flowable=")) {
            return null
        }

        Module module = new Module()

        byte[] data = fileContent.getBytes(StandardCharsets.UTF_8)
        module.setXmlData(data)

        String processId
        try {
            processId = getProcessId(data)
        } catch (Exception e) {
            log.error("Workflow definition reading failed. File: " + bpmnFile.getPath(), e)
            return null
        }

        if (processId == null || processId.isEmpty()) {
            log.error("Workflow definition doesn't have id attribute. File: " + bpmnFile.getPath())
            return null
        }

        module.setId("flowable\$" + processId)

        return module
    }

    private static String getProcessId(byte[] data) throws ParserConfigurationException, IOException, SAXException {

        DocumentBuilderFactory docBuilderFactory = DocumentBuilderFactory.newInstance()
        DocumentBuilder docBuilder = docBuilderFactory.newDocumentBuilder()
        Document document = docBuilder.parse(new ByteArrayInputStream(data))

        NamedNodeMap processAtts = document.getElementsByTagName("process")
            .item(0)
            .getAttributes()

        return getXmlAttValue(processAtts, "id")
    }

    private static String getXmlAttValue(NamedNodeMap atts, String name) {
        Node item = atts.getNamedItem(name)
        if (item == null) {
            return null
        }
        return item.getNodeValue()
    }

    @Override
    void write(@NotNull EcosFile root, Module module, Unit config) {

        String name = FileUtils.getValidName(module.id, "")
        root.createFile(name + ".bpmn20.xml", module.xmlData)
    }

    @Override
    ArtifactMeta getMeta(Module module, Unit config) {
        return ArtifactMeta.create()
            .withId(module.id)
            .build()
    }
}

