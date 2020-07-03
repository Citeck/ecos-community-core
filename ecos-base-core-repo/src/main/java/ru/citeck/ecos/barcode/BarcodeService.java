package ru.citeck.ecos.barcode;

import com.google.zxing.*;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import lombok.extern.slf4j.Slf4j;
import org.alfresco.service.cmr.repository.NodeRef;
import org.alfresco.service.namespace.QName;
import org.apache.commons.lang.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import ru.citeck.ecos.barcode.exception.UnsupportedBarcodeTypeException;
import ru.citeck.ecos.processor.exception.BarcodeInputException;
import ru.citeck.ecos.utils.NodeUtils;

import javax.xml.bind.DatatypeConverter;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Hashtable;

/**
 * This service needed to work with barcode in business logic layer.
 * <p>
 * It's very required to bring here new logic to interact and handle barcodes.
 * <p>
 */
@Slf4j
@Service
public class BarcodeService {

    private static final String UTF8_CONTENT_ENCODING = "UTF-8";
    private static final String PNG_IMAGE_FORMAT = "png";

    private NodeUtils nodeUtils;

    @Autowired
    public BarcodeService(NodeUtils nodeUtils) {
        this.nodeUtils = nodeUtils;
    }

    /**
     * Common method to generate and get barcode as string in BASE64 format.
     *
     * @param nodeRef       node reference in alfresco
     * @param propertyQName node's property as {@link QName} based on which will be generated barcode
     * @param width         width of result image of barcode
     * @param height        height of result image of barcode
     * @param format        format of barcode
     */
    public String getBarcodeAsBase64FromProp(NodeRef nodeRef, QName propertyQName, int width, int height,
                                             BarcodeFormat format) {

        String barcodePropertyValue;
        try {
            barcodePropertyValue = nodeUtils.getProperty(nodeRef, propertyQName);
        } catch (Exception e) {
            throw new BarcodeInputException(e);
        }

        if (StringUtils.isEmpty(barcodePropertyValue)) {
            log.debug("Property for barcode is empty. NodeRef: " + nodeRef + " prop: " + propertyQName);
            return "";
        }

        return getBarcodeAsBase64FromContent(barcodePropertyValue, width, height, format);
    }

    /**
     * Convert content of barcode to barcode and then return String in BASE64 format.
     *
     * @param barcodeContent barcode content
     * @param width          barcode width
     * @param height         barcode height
     * @param format         format of barcode
     */
    public String getBarcodeAsBase64FromContent(String barcodeContent, int width, int height,
                                                BarcodeFormat format) {

        BitMatrix matrix;
        try {
            Hashtable<EncodeHintType, String> hints = new Hashtable<>(1);
            hints.put(EncodeHintType.CHARACTER_SET, UTF8_CONTENT_ENCODING);

            Writer writer = new MultiFormatWriter();
            matrix = writer.encode(barcodeContent, format, width, height, hints);
        } catch (WriterException e) {
            throw new RuntimeException("Error encode barcode", e);
        }

        String base64;
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {

            MatrixToImageWriter.writeToStream(matrix, PNG_IMAGE_FORMAT, out);
            base64 = DatatypeConverter.printBase64Binary(out.toByteArray());

        } catch (IOException e) {
            throw new RuntimeException("Error encode barcode", e);
        }

        return base64;
    }

    /**
     * Converting barcode type from string into {@link BarcodeFormat}
     *
     * @param barcodeType type of barcode
     */
    //TODO: implement mapping for all formats
    public BarcodeFormat getBarcodeFormatByType(String barcodeType) {
        switch (barcodeType) {
            case "code-128":
                return BarcodeFormat.CODE_128;
            case "code-39":
                return BarcodeFormat.CODE_39;
            case "code-93":
                return BarcodeFormat.CODE_93;
            default:
                throw new UnsupportedBarcodeTypeException(barcodeType);
        }
    }
}
