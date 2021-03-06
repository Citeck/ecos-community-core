package ru.citeck.ecos.content.dao.xml;

import org.apache.commons.io.IOUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.core.io.ClassPathResource;
import org.xml.sax.SAXException;
import ru.citeck.ecos.content.dao.ContentDAO;

import javax.xml.XMLConstants;
import javax.xml.bind.*;
import javax.xml.namespace.QName;
import javax.xml.transform.stream.StreamSource;
import javax.xml.validation.Schema;
import javax.xml.validation.SchemaFactory;
import java.io.*;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class XmlContentDAO<T> implements ContentDAO<T> {

    private static final Log logger = LogFactory.getLog(XmlContentDAO.class);

    private QName rootNodeQName;
    private String rootPackage;

    private List<String> schemaFiles = Collections.emptyList();
    private volatile Schema schema = null;

    @Override
    public T read(InputStream stream) throws IOException {

        try {

            JAXBContext jaxbContext = JAXBContext.newInstance(rootPackage);
            Unmarshaller unmarshaller = jaxbContext.createUnmarshaller();
            unmarshaller.setSchema(getSchema());

            Object result = unmarshaller.unmarshal(stream);
            if (result instanceof JAXBElement) {
                result = ((JAXBElement) result).getValue();
            }
            @SuppressWarnings("unchecked")
            T readResult = (T) result;
            return readResult;

        } catch (JAXBException e) {
            throw new IllegalArgumentException("Can not parse stream", e);
        }
    }

    @Override
    public void write(T value, OutputStream stream) throws IOException {
        try {

            JAXBContext context = JAXBContext.newInstance(rootPackage);
            Marshaller marshaller = context.createMarshaller();
            marshaller.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, true);
            marshaller.setSchema(getSchema());

            @SuppressWarnings("unchecked")
            Class<T> clazz = (Class<T>) value.getClass();
            JAXBElement<T> element = new JAXBElement<>(rootNodeQName, clazz, value);

            marshaller.marshal(element, stream);
        } catch (JAXBException e) {
            throw new IllegalArgumentException("Can not write to stream", e);
        }
    }

    public Schema getSchema() throws IOException {

        if (schema != null) {
            return schema;
        }

        synchronized (this) {

            if (schema != null) {
                return schema;
            }

            try {

                Map<String, File> schemaFileByName = new HashMap<>();

                StreamSource[] sources = new StreamSource[schemaFiles.size()];
                for (int i = 0; i < sources.length; i++) {
                    File schemaFile = new ClassPathResource(schemaFiles.get(i)).getFile();
                    sources[i] = new StreamSource(schemaFile);
                    sources[i].setSystemId(schemaFile.toURI().toString());
                    schemaFileByName.put(schemaFile.getName(), schemaFile);
                }

                SchemaFactory schemaFactory = SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI);
                schemaFactory.setResourceResolver((type, namespaceURI, publicId, systemId, baseURI) ->
                    new XmlLSInput(type, namespaceURI, publicId, systemId, baseURI) {
                        @Override
                        protected byte[] getXsdData(String systemId) {
                            File file = schemaFileByName.get(systemId);
                            try (InputStream fis = new FileInputStream(file)) {
                                return IOUtils.toByteArray(fis);
                            } catch (IOException e) {
                                logger.error("Cannot read schema: " + systemId, e);
                            }
                            return null;
                        }
                    }
                );

                schema = schemaFactory.newSchema(sources);

            } catch (SAXException e) {
                logger.error("Cannot read schema files", e);
            }
        }

        return schema;
    }

    public void setRootNodeQName(String rootNodeQName) {
        this.rootNodeQName = QName.valueOf(rootNodeQName);
    }

    public void setRootPackage(String rootPackage) {
        this.rootPackage = rootPackage;
    }

    public void setSchemaFiles(List<String> schemaFiles) {
        this.schemaFiles = schemaFiles;
    }
}
