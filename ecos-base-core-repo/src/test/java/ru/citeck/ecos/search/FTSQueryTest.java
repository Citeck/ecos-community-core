package ru.citeck.ecos.search;

import org.alfresco.service.namespace.QName;
import org.junit.Test;
import ru.citeck.ecos.search.ftsquery.FTSQuery;

import static org.junit.Assert.*;

public class FTSQueryTest {

    @Test
    public void test() {

        FTSQuery query = FTSQuery.createRaw();

        Exception ex = null;
        try {
            query.and();
        } catch (Exception e) {
            ex = e;
        }
        assertNull(ex);
        assertEquals("()", query.getQuery());

        query = FTSQuery.createRaw();
        QName field = QName.createQName("123", "123");
        String value = "test";
        query.exact(field, value).and().exact(field, value);

        String fieldQuery = "=" + field + ":\"" + value + "\"";
        String fieldAndFieldQuery = fieldQuery + " AND " + fieldQuery;

        assertEquals(fieldAndFieldQuery, query.getQuery());

        query = FTSQuery.createRaw();
        query.exact(field, value);
        query.and();
        assertEquals(fieldQuery, query.getQuery());

        query = FTSQuery.createRaw();
        query.exact(field, value);
        query.and().or().and().and().or().exact(field, value);

        String fieldOrFieldQuery = fieldQuery + " OR " + fieldQuery;
        assertEquals(fieldOrFieldQuery, query.getQuery());

        query = FTSQuery.createRaw();
        query.open()
                .exact(field, value)
                .and()
            .close();

        assertEquals(fieldQuery, query.getQuery());
        assertEquals("()", FTSQuery.createRaw().getQuery());

        query = FTSQuery.createRaw();
        query.open().open().exact(field, value).close().close();

        assertEquals(fieldQuery, query.getQuery());

        query = FTSQuery.createRaw();
        query.open().open().exact(field, value).and().or().exact(field, value).and().close().close();
        assertEquals(fieldOrFieldQuery, query.getQuery());

        query = FTSQuery.createRaw()
                        .exact(field, value).and()
                        .exact(field, value).and().open()
                            .exact(field, value).or()
                            .exact(field, value).or()
                            .empty(field).or()
                        .close().or()
                        .exact(field, value);

        assertEquals(fieldQuery + " AND " + fieldQuery + " AND " +
                "(" + fieldQuery + " OR " + fieldQuery + " OR " +
                    "(ISNULL:\"" + field + "\" OR ISUNSET:\"" + field + "\")" +
                ") OR " + fieldQuery, query.getQuery()) ;

        query = FTSQuery.createRaw()
                        .open()
                        .exact(field, value).and()
                        .open().open().close().close()
                        .close();
        assertEquals(fieldQuery, query.getQuery());

        query = FTSQuery.createRaw()
            .not().not().exact(field, value);
        assertEquals(fieldQuery, query.getQuery());
    }

    @Test
    public void testAlwaysFalseTerm() {
        QName att1 = QName.createQName("111", "111");
        QName att2 = QName.createQName("222", "222");
        QName att3 = QName.createQName("333", "333");

        assertEquals("", FTSQuery.createRaw()
            .exact(att1, "val1").and()
            .alwaysFalse()
            .getQuery());

        assertEquals("", FTSQuery.createRaw()
            .type(att1).and()
            .alwaysFalse()
            .getQuery());

        assertEquals("", FTSQuery.createRaw()
            .exact(att1, "val1").and()
            .exact(att2, "val2").and()
            .alwaysFalse()
            .getQuery());

        assertEquals("", FTSQuery.createRaw()
            .type(att1).and()
            .exact(att2, "val2").and()
            .alwaysFalse()
            .getQuery());

        assertEquals("", FTSQuery.createRaw()
            .exact(att1, "val1").and()
            .exact(att2, "val2").and()
            .exact(att3, "val3").and()
            .alwaysFalse()
            .getQuery());

        assertEquals("", FTSQuery.createRaw()
            .alwaysFalse().and()
            .exact(att1, "val1").and()
            .exact(att2, "val2").and()
            .exact(att3, "val3")
            .getQuery());

        assertEquals("", FTSQuery.createRaw()
            .alwaysFalse().and()
            .type(att1).and()
            .exact(att2, "val2").and()
            .exact(att3, "val3")
            .getQuery());

        assertEquals("={111}111:\"val1\"", FTSQuery.createRaw()
            .exact(att1, "val1").or()
            .alwaysFalse()
            .getQuery());

        assertEquals("TYPE:\"{111}111\"", FTSQuery.createRaw()
            .type(att1).or()
            .alwaysFalse()
            .getQuery());

        assertEquals("", FTSQuery.createRaw()
            .exact(att1, "val1").and()
            .open()
            .alwaysFalse()
            .close()
            .getQuery());

        assertEquals("", FTSQuery.createRaw()
            .type(att1).and()
            .open()
            .alwaysFalse()
            .close()
            .getQuery());

        assertEquals("={111}111:\"val1\"", FTSQuery.createRaw()
            .exact(att1, "val1").or()
            .open()
            .alwaysFalse()
            .close()
            .getQuery());

        assertEquals("TYPE:\"{111}111\"", FTSQuery.createRaw()
            .type(att1).or()
            .open()
            .alwaysFalse()
            .close()
            .getQuery());

        assertEquals("", FTSQuery.createRaw()
            .exact(att1, "val1").and()
            .open()
            .alwaysFalse().and()
            .exact(att2, "val2")
            .close()
            .getQuery());

        assertEquals("={111}111:\"val1\" AND ={222}222:\"val2\"", FTSQuery.createRaw()
            .exact(att1, "val1").and()
            .open()
            .alwaysFalse().or()
            .exact(att2, "val2")
            .close()
            .getQuery());

        assertEquals("TYPE:\"{111}111\" AND ={222}222:\"val2\"", FTSQuery.createRaw()
            .type(att1).and()
            .open()
            .alwaysFalse().or()
            .exact(att2, "val2")
            .close()
            .getQuery());

        assertEquals("={111}111:\"val1\"", FTSQuery.createRaw()
            .exact(att1, "val1").or()
            .open()
            .alwaysFalse().and()
            .exact(att2, "val2")
            .close()
            .getQuery());

        assertEquals("TYPE:\"{111}111\"", FTSQuery.createRaw()
            .type(att1).or()
            .open()
            .alwaysFalse().and()
            .exact(att2, "val2")
            .close()
            .getQuery());

        assertEquals("={111}111:\"val1\" OR ={222}222:\"val2\"", FTSQuery.createRaw()
            .exact(att1, "val1").or()
            .open()
            .alwaysFalse().or()
            .exact(att2, "val2")
            .close()
            .getQuery());

        assertEquals("={111}111:\"abc\" AND ={222}222:\"abc\"", FTSQuery.createRaw()
            .open()
            .exact(att1, "abc")
            .and()
            .exact(att2, "abc")
            .close()

            .or()

            .open()
            .exact(att3, "def")
            .and()
            .exact(att2, "gtc")
            .and()
            .alwaysFalse()
            .close()
            .getQuery());

        assertEquals("={111}111:\"abc\" AND ={222}222:\"abc\"", FTSQuery.createRaw()
            .open()
            .exact(att1, "abc")
            .and()
            .exact(att2, "abc")
            .close()

            .or()

            .open()
            .exact(att3, "def")
            .and()
            .exact(att2, "gtc")
            .and()
            .not().alwaysTrue()
            .close()
            .getQuery());

        assertEquals("(={111}111:\"abc\" AND ={222}222:\"abc\") OR (={333}333:\"def\" AND ={222}222:\"gtc\")", FTSQuery.createRaw()
            .open()
            .exact(att1, "abc")
            .and()
            .exact(att2, "abc")
            .close()
            .or()
            .open()
            .exact(att3, "def")
            .and()
            .exact(att2, "gtc")
            .and()
            .not().alwaysFalse()
            .close()
            .getQuery());
    }

    @Test
    public void standardQueryTest() {
        QName att1 = QName.createQName("1", "1");
        QName att2 = QName.createQName("2", "2");
        QName att3 = QName.createQName("3", "3");
        QName att4 = QName.createQName("4", "4");

        assertEquals("={1}1:\"abc\"", FTSQuery.createRaw()
            .exact(att1, "abc")
            .getQuery());

        assertEquals("={1}1:\"abc\" OR @{1}1:\"def\"", FTSQuery.createRaw()
            .exact(att1, "abc")
            .or()
            .value(att1, "def")
            .getQuery());

        assertEquals("={1}1:\"abc\" AND @{1}1:\"def\"", FTSQuery.createRaw()
            .exact(att1, "abc")
            .and()
            .value(att1, "def")
            .getQuery());

        assertEquals("={1}1:\"abc\" AND NOT @{1}1:\"def\"", FTSQuery.createRaw()
            .exact(att1, "abc")
            .and()
            .not().value(att1, "def")
            .getQuery());

        assertEquals("NOT ={1}1:\"abc\" AND NOT @{1}1:\"def\"", FTSQuery.createRaw()
            .not().exact(att1, "abc")
            .and()
            .not().value(att1, "def")
            .getQuery());

        assertEquals("={1}1:\"abc\" AND (={2}2:\"abc\" OR ={2}2:\"def\" OR ={2}2:\"gtc\")", FTSQuery.createRaw()
            .exact(att1, "abc")
            .and()
            .open()
            .exact(att2, "abc")
            .or()
            .exact(att2, "def")
            .or()
            .exact(att2, "gtc")
            .close()
            .getQuery());

        assertEquals("={1}1:\"abc\" AND ={2}2:\"abc\" OR ={3}3:\"def\" AND ={4}4:\"gtc\"", FTSQuery.createRaw()
            .exact(att1, "abc")
            .and()
            .exact(att2, "abc")
            .or()
            .exact(att3, "def")
            .and()
            .exact(att4, "gtc")
            .getQuery());

        assertEquals("(={1}1:\"abc\" AND ={2}2:\"abc\") OR (={3}3:\"def\" AND ={4}4:\"gtc\")", FTSQuery.createRaw()
            .open()
            .exact(att1, "abc")
            .and()
            .exact(att2, "abc")
            .close()
            .or()
            .open()
            .exact(att3, "def")
            .and()
            .exact(att4, "gtc")
            .close()
            .getQuery());
    }
}
