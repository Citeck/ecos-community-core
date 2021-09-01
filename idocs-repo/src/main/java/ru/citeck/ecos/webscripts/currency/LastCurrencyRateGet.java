package ru.citeck.ecos.webscripts.currency;

import lombok.Setter;
import org.alfresco.service.cmr.repository.NodeRef;
import org.springframework.extensions.webscripts.*;
import ru.citeck.ecos.currency.CurrencyService;

import java.math.BigDecimal;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

public class LastCurrencyRateGet extends DeclarativeWebScript {

    private static final String BASE_CURRENCY_PARAM_NAME = "baseCurrency";
    private static final String TARGET_CURRENCY_PARAM_NAME = "targetCurrency";
    private static final String DATE_PARAM_NAME = "date";
    private static final String MANUAL_CONVERSION_PARAM_NAME = "manualConversion";
    private static final String DATE_FORMAT = "yyyy-MM-dd";
    private static final ThreadLocal<SimpleDateFormat> SDF = ThreadLocal.withInitial(() ->
        new SimpleDateFormat(DATE_FORMAT));

    @Setter
    private CurrencyService currencyService;

    @Override
    protected Map<String, Object> executeImpl(WebScriptRequest req, Status status, Cache cache) {
        String baseCurrencyParam = getParameter(req, BASE_CURRENCY_PARAM_NAME);
        String targetCurrencyParam = getParameter(req, TARGET_CURRENCY_PARAM_NAME);
        String dateParam = getParameter(req, DATE_PARAM_NAME);

        String forceConversionParam = req.getParameter(MANUAL_CONVERSION_PARAM_NAME);
        boolean forceConversion = Boolean.parseBoolean(forceConversionParam);

        Date date;
        try {
            date = SDF.get().parse(dateParam);
        } catch (ParseException e) {
            throw new WebScriptException("Parameter date must be in " + DATE_FORMAT + " format");
        }

        NodeRef baseCurrency = new NodeRef(baseCurrencyParam);
        NodeRef targetCurrency = new NodeRef(targetCurrencyParam);

        BigDecimal result = forceConversion
                ? currencyService.getLastCurrencyRateWithManualConversion(baseCurrency, targetCurrency, date)
                : currencyService.getLastCurrencyRate(baseCurrency, targetCurrency, date);

        return createModel(result);
    }

    private String getParameter(WebScriptRequest req, String paramName) {
        String param = req.getParameter(paramName);

        if (param == null) {
            throw new WebScriptException("Parameter " + paramName + " is mandatory.");
        }

        return param;
    }

    private Map<String, Object> createModel(BigDecimal rate) {
        Map<String, Object> model = new HashMap<>();
        model.put("data", rate.doubleValue());
        return model;
    }
}
