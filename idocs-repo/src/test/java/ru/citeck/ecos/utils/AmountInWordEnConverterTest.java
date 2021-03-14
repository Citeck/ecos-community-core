package ru.citeck.ecos.utils;

import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.springframework.extensions.surf.util.I18NUtil;
import ru.citeck.ecos.utils.converter.amount.AmountInWordConverter;
import ru.citeck.ecos.utils.converter.amount.AmountInWordConverterFactory;

import java.util.Locale;

import static org.junit.Assert.assertEquals;

/**
 * @author Roman.Makarskiy on 10/10/2016.
 */
@Ignore
public class AmountInWordEnConverterTest {

    private AmountInWordConverter enConverter;

    @Before
    public void initializationConverter() {
        I18NUtil.setLocale(Locale.ENGLISH);
        enConverter = new AmountInWordConverterFactory().getConverter();
    }

    @Test
    public void convertRubTest() {
        assertEquals("One million two hundred four thousand nine hundred twenty-four ruble 67 kopeck", enConverter.convert(1204924.67, "RUB"));
        assertEquals("Twenty-five thousand eight hundred ninety-two ruble 25 kopeck", enConverter.convert(25892.25, "RUB"));
        assertEquals("Two trillion two hundred fifty-eight billion four hundred forty-one million five hundred fifty-eight thousand four hundred fifty-six rubles 52 kopeck", enConverter.convert(2258441558456.51997, "RUB"));
        assertEquals("Three ruble 21 kopeck", enConverter.convert(3.21, "RUB"));
        assertEquals("Ten rubles 00 kopeck", enConverter.convert(10, "RUB"));
        assertEquals("Thirty-one ruble 80 kopeck", enConverter.convert(31.8, "RUB"));

    }

    @Test
    public void convertExponentTest() {
        assertEquals("Nine billion one hundred eighty million rubles 00 kopeck", enConverter.convert(9.18E+09, "RUB"));
        assertEquals("One million forty-one thousand two hundred ninety-eight rubles 30 kopeck", enConverter.convert(1.0412983E+06, "RUB"));
    }

    @Test
    public void convertEurTest() {
        assertEquals("Three hundred twenty-two euro 07 cents", enConverter.convert(322.07, "EUR"));
        assertEquals("Nineteen thousand seven hundred thirteen euro 21 cent", enConverter.convert(19713.21, "EUR"));
        assertEquals("Three hundred forty-nine thousand four hundred euro 02 cent", enConverter.convert(349400.02, "EUR"));
    }

    @Test
    public void convertUsdTest() {
        assertEquals("Two hundred nine US dollar 00 cents", enConverter.convert(209.00, "USD"));
        assertEquals("Thirty-three US dollar 70 cents", enConverter.convert(33.7, "USD"));
        assertEquals("Ten thousand US dollar 00 cents", enConverter.convert(10000, "USD"));
    }

    @Test
    public void convertJpyTest() {
        assertEquals("One thousand one hundred ninety-nine japanese yen 00 sen", enConverter.convert(1199.00, "JPY"));
        assertEquals("Nine japanese yen 50 sen", enConverter.convert(9.50, "JPY"));
    }

    @Test
    public void convertUahTest() {
        assertEquals("Three hryvnia 21 kopeck", enConverter.convert(3.21, "UAH"));
        assertEquals("Ten hryvnia 00 kopeck", enConverter.convert(10, "UAH"));
    }

    @Test
    public void convertGbpTest() {
        assertEquals("One thousand one hundred ninety-nine pounds Sterling 02 penny", enConverter.convert(1199.02, "GBP"));
        assertEquals("Twenty-two pound Sterling 50 pence", enConverter.convert(22.50, "GBP"));
    }

    @Test
    public void convertByrTest() {
        assertEquals("Three hundred twenty-two belarusian ruble 07 kopeck", enConverter.convert(322.07, "BYR"));
        assertEquals("Nineteen thousand seven hundred thirteen belarusian rubles 21 kopeck", enConverter.convert(19713.21, "BYR"));
        assertEquals("Three hundred forty-nine thousand four hundred belarusian rubles 02 kopeck", enConverter.convert(349400.02, "BYR"));
    }

}
