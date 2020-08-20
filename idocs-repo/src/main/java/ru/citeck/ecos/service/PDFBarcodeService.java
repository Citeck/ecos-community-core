package ru.citeck.ecos.service;

import com.itextpdf.text.DocumentException;
import com.itextpdf.text.Element;
import com.itextpdf.text.Image;
import com.itextpdf.text.pdf.Barcode;
import com.itextpdf.text.pdf.BarcodeQRCode;
import com.itextpdf.text.pdf.PdfReader;
import com.itextpdf.text.pdf.PdfStamper;
import lombok.extern.slf4j.Slf4j;
import org.alfresco.service.cmr.repository.ContentService;
import org.alfresco.service.cmr.repository.ContentWriter;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.stereotype.Service;
import ru.citeck.ecos.processor.DataBundle;
import ru.citeck.ecos.processor.ProcessorHelper;
import ru.citeck.ecos.utils.UrlUtils;

import java.awt.geom.AffineTransform;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@Service
public class PDFBarcodeService implements ApplicationContextAware {

    private static final String BARCODE_NAME = "Barcode.code128";
    private static final Integer UNDER_MARGIN = 10;
    private static final Integer STAMP_MARGIN = 20;

    private ApplicationContext applicationContext;
    private ContentService contentService;
    private UrlUtils urlUtils;
    private ProcessorHelper helper;

    public DataBundle putBarcodeOnDocument(DataBundle transformedDocument, String barcode) {
        return putBarcodeOnDocument(transformedDocument, barcode, null);
    }

    public DataBundle putBarcodeOnDocument(DataBundle transformedDocument, String barCodeStr, String documentRef) {

        Barcode barCode = null;
        BarcodeQRCode barCodeQRCode = null;

        if (barCodeStr != null) {
            barCode = generateBarCode(barCodeStr);
        }

        if (documentRef != null) {
            barCodeQRCode = generateQrCode(documentRef);
        }

        if (barCode != null || barCodeQRCode != null) {

            ContentWriter writer = contentService.getTempWriter();

            try {
                PdfReader reader = new PdfReader(transformedDocument.getInputStream());
                PdfStamper stamper = new PdfStamper(reader, writer.getContentOutputStream());

                Image imageBarcode = null;
                Image imageQrCode = null;

                int countPages = reader.getNumberOfPages();

                if (countPages > 0 && barCode != null) {
                    imageBarcode = barCode.createImageWithBarcode(stamper.getUnderContent(1), null, null);
                }
                if (barCodeQRCode != null) {
                    imageQrCode = barCodeQRCode.getImage();
                }

                if (imageBarcode != null) {
                    for (int i = 1; i <= countPages; i++) {
                        AffineTransform transformPrefs = AffineTransform.getTranslateInstance(
                            stamper.getReader().getPageSize(i).getWidth() / 2 - imageBarcode.getWidth() / 2,
                            UNDER_MARGIN);
                        transformPrefs.concatenate(AffineTransform.getScaleInstance(
                            imageBarcode.getScaledWidth(), imageBarcode.getScaledHeight()));
                        stamper.getUnderContent(i).addImage(imageBarcode, transformPrefs);
                    }
                }

                if (imageQrCode != null) {
                    float marginFromCenter;
                    if (imageBarcode != null) {
                        marginFromCenter = imageBarcode.getWidth() / 2 + STAMP_MARGIN;
                    } else {
                        marginFromCenter = -imageQrCode.getWidth() / 2;
                    }

                    AffineTransform transformPrefsQrCode = AffineTransform.getTranslateInstance(
                        stamper.getReader().getPageSize(1).getWidth() / 2 + marginFromCenter,
                        UNDER_MARGIN);
                    transformPrefsQrCode.concatenate(AffineTransform.getScaleInstance(imageQrCode.getScaledWidth(),
                        imageQrCode.getScaledHeight()));
                    stamper.getUnderContent(1).addImage(imageQrCode, transformPrefsQrCode);
                }

                stamper.close();
                reader.close();
            } catch (IOException | DocumentException e) {
                log.error("Error while adding barcode or QR-code on document", e);
            }

            Map<String, Object> model = new HashMap<>();
            return helper.getDataBundle(writer.getReader(), model);
        }

        return transformedDocument;
    }


    private Barcode generateBarCode(String barcodeInput) {
        Barcode barcode = applicationContext.getBean(BARCODE_NAME, Barcode.class);
        barcode.setTextAlignment(Element.ALIGN_CENTER);
        barcode.setCode(barcodeInput);
        barcode.setSize(4);
        return barcode;
    }

    private BarcodeQRCode generateQrCode(String documentRef) {
        if (documentRef == null) {
            return null;
        }
        String link = generateLinkForDocument(documentRef);
        if (link == null) {
            return null;
        }

        log.debug("Generate QR for address = " + link);
        return new BarcodeQRCode(link, 1, 1, null);
    }

    private String generateLinkForDocument(String ref) {
        if (ref == null) {
            return null;
        }
        return urlUtils.generateDashboardRefUrl(ref);
    }

    @Override
    @Autowired
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        this.applicationContext = applicationContext;
    }

    @Autowired
    @Qualifier("DataBundleProcessorHelper")
    public void setHelper(ProcessorHelper helper) {
        this.helper = helper;
    }

    @Autowired
    public void setContentService(ContentService contentService) {
        this.contentService = contentService;
    }

    @Autowired
    public void setUrlUtils(UrlUtils urlUtils) {
        this.urlUtils = urlUtils;
    }
}
