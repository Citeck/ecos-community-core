package alfresco.extension.eapps.types.workflow

import kotlin.Unit
import kotlin.jvm.functions.Function1
import org.apache.commons.lang3.StringUtils
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
import ru.citeck.ecos.commons.json.Json

import javax.xml.parsers.DocumentBuilder
import javax.xml.parsers.DocumentBuilderFactory
import javax.xml.parsers.ParserConfigurationException
import java.util.stream.Collectors

return new ArtifactController<Module, Unit>() {

    private static final Logger log = LoggerFactory.getLogger(ArtifactController.class)
    private static final String MODULE_FILE_PATTERN = "*-module.json"

    private static final Set<String> VALID_DEF_EXT = new HashSet<>(Arrays.asList("xml", "bpmn"))

    @Override
    List<Module> read(@NotNull EcosFile root, Unit config) {

        return root.findFiles("*/" + MODULE_FILE_PATTERN)
            .stream()
            .map({ f -> Optional.ofNullable(readModule(f)) })
            .filter({ o -> o.isPresent() })
            .map( { o -> o.get() })
            .collect(Collectors.toList())
    }

    private static Module readModule(EcosFile jsonFile) {

        WorkflowMetaDto moduleMeta = jsonFile.read({
            input -> Json.mapper.read(input, WorkflowMetaDto.class)
        })
        String defLoc = moduleMeta.getLocation()

        if (defLoc == null || defLoc.isEmpty()) {
            log.error("Definition location is not specified: " + jsonFile.getName())
            return null
        }

        String ext = StringUtils.substringAfterLast(defLoc, ".")
        if (!VALID_DEF_EXT.contains(ext)) {
            log.error("Unknown workflow file extension. Location: " + defLoc + " file: " + jsonFile.getPath())
            return null
        }

        EcosFile definitionFile = jsonFile.getParent().getFile(defLoc)
        if (definitionFile == null) {
            log.error("Definition is not found. Location: " + defLoc + " meta: " + jsonFile.getPath())
            return null
        }

        Module module = new Module()

        byte[] data = definitionFile.readAsBytes()
        module.setXmlData(data)

        String processId
        try {
            processId = getProcessId(data)
        } catch (Exception e) {
            log.error("Workflow definition reading failed. File: " + definitionFile.getPath(), e)
            return null
        }

        if (processId == null || processId.isEmpty()) {
            log.error("Workflow definition doesn't have id attribute. File: " + definitionFile.getPath())
            return null
        }

        module.setId(moduleMeta.getEngineId() + '$' + processId)

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
        EcosFile wfDir = root.getOrCreateDir(name)

        String bpmnName = name + ".bpmn20.xml";
        String[] idParts = module.id.split('\\$')

        WorkflowMetaDto moduleDto = new WorkflowMetaDto()
        moduleDto.setLocation(bpmnName)
        moduleDto.setEngineId(idParts[0])

        wfDir.createFile(name + "-module.json", (Function1<OutputStream, Unit>) {
            OutputStream out -> Json.mapper.write(out, moduleDto)
        })
        wfDir.createFile(bpmnName, (Function1<OutputStream, Unit>) {
            OutputStream out -> out.write(module.xmlData)
        })
    }

    @Override
    ArtifactMeta getMeta(Module module, Unit config) {
        return ArtifactMeta.create()
            .withId(module.id)
            .build()
    }

    static class Module {
        String id
        byte[] xmlData
    }

    static class WorkflowMetaDto {
        String engineId
        String location
    }
}

