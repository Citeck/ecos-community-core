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
}
