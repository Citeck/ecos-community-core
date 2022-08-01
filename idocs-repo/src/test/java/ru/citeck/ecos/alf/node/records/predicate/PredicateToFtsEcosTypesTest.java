package ru.citeck.ecos.alf.node.records.predicate;

import lombok.val;
import org.junit.Test;
import ru.citeck.ecos.commons.data.ObjectData;
import ru.citeck.ecos.model.EcosTypeModel;
import ru.citeck.ecos.model.lib.type.service.utils.TypeUtils;
import ru.citeck.ecos.records2.predicate.model.Predicates;
import ru.citeck.ecos.webapp.lib.model.type.dto.TypeDef;

import static org.junit.Assert.assertEquals;

public class PredicateToFtsEcosTypesTest extends PredicateToFtsTestBase {

    private static final String baseTypeId = "base";
    private static final String childTypeId = "child";
    private static final String caseTypeId = "case";
    private static final String childWithAlfTypeTypeId = "child-with-alf-type";

    @Test
    public void converterTest() {

        registerTypes();
        registerPrefixes();

        val resultWithExpandedEcosType = converter.convert(
            Predicates.eq("_type", TypeUtils.getTypeRef(baseTypeId))
        );

        assertEquals(
            "=" + EcosTypeModel.PROP_TYPE + ":\"" + baseTypeId + "\"" +
            " OR =" + EcosTypeModel.PROP_TYPE + ":\"" + childTypeId + "\"" +
            " OR =" + EcosTypeModel.PROP_TYPE + ":\"" + caseTypeId + "\"" +
            "__?_type=emodel/type@base" , resultWithExpandedEcosType);

        val resultWithAlfType = converter.convert(
            Predicates.eq("_type", TypeUtils.getTypeRef(childWithAlfTypeTypeId))
        );

        assertEquals(
            "TYPE:\"" + toQName("ecos:test-type") + "\"" +
                "__?_type=" + TypeUtils.getTypeRef(childWithAlfTypeTypeId), resultWithAlfType);

        val resultWithCaseType = converter.convert(
            Predicates.eq("_type", TypeUtils.getTypeRef(caseTypeId))
        );

        // Special case for type "case" to support legacy alfresco cases based on aspect icase:case
        assertEquals("ASPECT:\"" + toQName("icase:case") + "\"", resultWithCaseType);
    }

    @Test
    public void namespaceTest() {

        registerPrefixes();

        assertEquals("{https://ecos.ru}localId", toQName("ecos:localId").toString());
        assertEquals("{https://icase.ru}localId", toQName("icase:localId").toString());
    }

    private void registerPrefixes() {
        registerNsUri("ecos", "https://ecos.ru");
        registerNsUri("icase", "https://icase.ru");
    }

    private void registerTypes() {

        TypeDef.Builder base = TypeDef.create();
        base.withId(baseTypeId);
        base.withParentRef(null);

        registerType(base.build());

        TypeDef.Builder child = TypeDef.create();
        child.withId(childTypeId);
        child.withParentRef(TypeUtils.getTypeRef(base.getId()));

        registerType(child.build());

        TypeDef.Builder caseType = TypeDef.create();
        caseType.withId(caseTypeId);
        caseType.withParentRef(TypeUtils.getTypeRef(base.getId()));
        caseType.withProperties(ObjectData.create("{\"alfType\":\"ecos:case\"}"));

        registerType(caseType.build());

        TypeDef.Builder childWithAlfType = TypeDef.create();
        childWithAlfType.withId(childWithAlfTypeTypeId);
        childWithAlfType.withParentRef(TypeUtils.getTypeRef(caseType.getId()));
        childWithAlfType.withProperties(ObjectData.create("{\"alfType\":\"ecos:test-type\"}"));

        registerType(childWithAlfType.build());
    }
}
