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
package ru.citeck.ecos.processor.pdf;

import java.awt.geom.AffineTransform;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;

import org.alfresco.error.AlfrescoRuntimeException;
import org.alfresco.service.cmr.repository.ContentReader;
import org.alfresco.service.cmr.repository.ContentService;
import org.alfresco.service.cmr.repository.ContentWriter;

import com.itextpdf.text.Rectangle;
import com.itextpdf.text.pdf.PdfContentByte;
import com.itextpdf.text.pdf.PdfImportedPage;
import com.itextpdf.text.pdf.PdfReader;
import com.itextpdf.text.pdf.PdfStamper;

import ru.citeck.ecos.processor.CompositeDataBundleProcessor;
import ru.citeck.ecos.processor.DataBundleProcessor;
import ru.citeck.ecos.processor.AbstractDataBundleLine;
import ru.citeck.ecos.processor.DataBundle;
import ru.citeck.ecos.processor.transform.AffineTransformCalculator;

/**
 * PDF Stamp is Data Bundle Decorator (Line), that puts stamp on each PDF page.
 * To produce the stamp an external Data Bundle Processor is used.
 * To calculate the position of stamp on the page an external Affine Transform Calculator is used.
 * 
 * @author Sergey Tiunov
 *
 */
public class PDFStamp extends AbstractDataBundleLine
{
    private ContentService contentService;
    private AffineTransformCalculator transformCalculator;

    private DataBundleProcessor stampProcessor;
    private List<DataBundleProcessor> stampProcessors;
    private Boolean foreground;

    public void init() {
        this.contentService = serviceRegistry.getContentService();
        if(stampProcessor instanceof CompositeDataBundleProcessor) {
            ((CompositeDataBundleProcessor)stampProcessor).setProcessors(stampProcessors);
        }
    }

    @Override
    public DataBundle process(DataBundle input) {

        // get main stream:
        InputStream pdfInputStream = input.getInputStream();

        // get stamp stream:
        List<DataBundle> stampInputs = new ArrayList<>(1);
        stampInputs.add(new DataBundle(input.getModel()));
        List<DataBundle> stampOutputs = stampProcessor.process(stampInputs);

        InputStream pdfStampStream = null;
        if (!stampOutputs.isEmpty()) {
            pdfStampStream = stampOutputs.get(0).getInputStream();
        } else {
            return input;
        }

        ContentWriter contentWriter = contentService.getTempWriter();
        helper.putContentProperties(contentWriter, input.getModel());

        OutputStream outputStream = contentWriter.getContentOutputStream();

        // do actual stamping
        stampPDF(outputStream, pdfInputStream, pdfStampStream);

        ContentReader contentReader = contentWriter.getReader();
        if(contentReader != null && contentReader.exists()) {
            return new DataBundle(input, contentReader.getContentInputStream());
        } else {
            return null;
        }
    }

    // stamp pdf with stamp
    private void stampPDF(OutputStream outputStream, InputStream pdfInputStream, InputStream pdfStampStream) {

        PdfReader reader = null;
        PdfReader stampReader = null;
        PdfStamper stamper = null;

        try {
            reader = new PdfReader(pdfInputStream);
            stamper = new PdfStamper(reader, outputStream);
            stampReader = new PdfReader(pdfStampStream);

            PdfImportedPage stamp = stamper.getImportedPage(stampReader, 1);
            Rectangle stampSize = stampReader.getPageSize(1);

            int numOfPages = reader.getNumberOfPages();
            for (int i = 1; i <= numOfPages; i++) {
                // calculate affine transform:
                Rectangle pageSize = reader.getPageSize(i);

                // get transformation matrix
                double[] m = new double[6];
                AffineTransform transform = transformCalculator.calculate(stampSize.getWidth(), stampSize.getHeight(), pageSize.getWidth(), pageSize.getHeight());
                transform.getMatrix(m);

                PdfContentByte page = foreground ? stamper.getOverContent(i) : stamper.getUnderContent(i);
                page.addTemplate(stamp, (float) m[0], (float) m[1], (float) m[2], (float) m[3], (float) m[4], (float) m[5]);

            }
        } catch (Exception e) {
            throw new AlfrescoRuntimeException("Caught exception", e);
        } finally {
            if(reader != null) {
                reader.close();
            }
            if(stampReader != null) {
                stampReader.close();
            }
            if(stamper != null) {
                try {
                    stamper.close();
                } catch (Exception e) {
                    throw new AlfrescoRuntimeException("Caught exception", e);
                }
            }
        }
    }


    /**
     * Set affine transform calculator.
     * It is used to calculate stamp position on the page.
     *
     * @param transformCalculator
     */
    public void setTransformCalculator(AffineTransformCalculator transformCalculator) {
        this.transformCalculator = transformCalculator;
    }

    /**
     * Set Data Bundle Processor, that is used to produce stamp.
     *
     * @param stampProcessor
     */
    public void setStampProcessor(DataBundleProcessor stampProcessor) {
        this.stampProcessor = stampProcessor;
    }

    public void setStampProcessors(List<DataBundleProcessor> stampProcessors) {
        this.stampProcessors = stampProcessors;
    }

    /**
     * Specify if the stamp should be set above the page (foreground = true), or under it (foreground = false).
     *
     * @param foreground
     */
    public void setForeground(Boolean foreground) {
        this.foreground = foreground;
    }

}
