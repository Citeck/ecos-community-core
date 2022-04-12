package ru.citeck.ecos.alf.node.records.predicate;

import lombok.val;
import org.alfresco.repo.i18n.MessageService;
import org.alfresco.service.cmr.repository.NodeService;
import org.alfresco.service.cmr.search.SearchService;
import org.alfresco.service.namespace.NamespaceService;
import org.alfresco.service.namespace.QName;
import org.apache.commons.lang.StringUtils;
import org.junit.Before;
import org.mockito.Mock;
import org.mockito.Mockito;
import ru.citeck.ecos.commons.data.DataValue;
import ru.citeck.ecos.commons.data.ObjectData;
import ru.citeck.ecos.config.EcosConfigService;
import ru.citeck.ecos.domain.model.alf.service.AlfAutoModelService;
import ru.citeck.ecos.model.lib.type.service.utils.TypeUtils;
import ru.citeck.ecos.node.EcosTypeService;
import ru.citeck.ecos.node.etype.EcosTypeAlfTypeService;
import ru.citeck.ecos.records.language.predicate.PredicateToFtsAlfrescoConverter;
import ru.citeck.ecos.records.language.predicate.converters.delegators.ConvertersDelegator;
import ru.citeck.ecos.records.language.predicate.converters.impl.ComposedPredicateToFtsConverter;
import ru.citeck.ecos.records.language.predicate.converters.impl.EmptyPredicateToFtsConverter;
import ru.citeck.ecos.records.language.predicate.converters.impl.NotPredicateToFtsConverter;
import ru.citeck.ecos.records.language.predicate.converters.impl.ValuePredicateToFtsConverter;
import ru.citeck.ecos.records.type.TypeDto;
import ru.citeck.ecos.records2.RecordRef;
import ru.citeck.ecos.records2.querylang.QueryLangService;
import ru.citeck.ecos.search.AssociationIndexPropertyRegistry;
import ru.citeck.ecos.utils.AuthorityUtils;
import ru.citeck.ecos.utils.DictUtils;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

import static org.mockito.Mockito.when;

public abstract class PredicateToFtsTestBase {

    protected PredicateToFtsAlfrescoConverter converter;

    private final Map<RecordRef, TypeDto> ecosTypes = new LinkedHashMap<>();
    private final Map<String, String> namespaceUri = new LinkedHashMap<>();

    private NamespaceService namespaceService;

    @Before
    public void beforeEach() {

        ecosTypes.clear();
        namespaceUri.clear();

        val queryLangService = Mockito.mock(QueryLangService.class);
        val ecosTypeAlfTypeService = ecosTypeAlfTypeServiceMock();
        val alfAutoModelService = Mockito.mock(AlfAutoModelService.class);
        val dictUtils = Mockito.mock(DictUtils.class);
        val authorityUtils = Mockito.mock(AuthorityUtils.class);
        val associationIndexPropertyRegistry = new AssociationIndexPropertyRegistry();
        val ecosConfigService = Mockito.mock(EcosConfigService.class);
        val messageService = Mockito.mock(MessageService.class);
        val nodeService = Mockito.mock(NodeService.class);
        val searchService = Mockito.mock(SearchService.class);
        val ecosTypeService = ecosTypeServiceMock();
        namespaceService = namespaceServiceMock();

        converter = new PredicateToFtsAlfrescoConverter(
            queryLangService,
            ecosTypeAlfTypeService
        );
        converter.setAlfAutoModelService(alfAutoModelService);
        converter.setAuthorityUtils(authorityUtils);

        val delegator = new ConvertersDelegator();
        converter.setDelegator(delegator);

        val composedPredicateToFtsConverter = new ComposedPredicateToFtsConverter();
        composedPredicateToFtsConverter.setDelegator(delegator);
        delegator.setComposedPredicateToFtsConverter(composedPredicateToFtsConverter);

        val notPredicateToFtsConverter = new NotPredicateToFtsConverter();
        notPredicateToFtsConverter.setDelegator(delegator);
        delegator.setNotPredicateToFtsConverter(notPredicateToFtsConverter);

        val emptyPredicateToFtsConverter = new EmptyPredicateToFtsConverter();
        emptyPredicateToFtsConverter.setDictUtils(dictUtils);
        emptyPredicateToFtsConverter.setAssociationIndexPropertyRegistry(associationIndexPropertyRegistry);
        delegator.setEmptyPredicateToFtsConverter(emptyPredicateToFtsConverter);

        val valuePredicateToFtsConverter = new ValuePredicateToFtsConverter();
        valuePredicateToFtsConverter.setDelegator(delegator);
        valuePredicateToFtsConverter.setAssociationIndexPropertyRegistry(associationIndexPropertyRegistry);
        valuePredicateToFtsConverter.setDictUtils(dictUtils);
        valuePredicateToFtsConverter.setAuthorityUtils(authorityUtils);
        valuePredicateToFtsConverter.setEcosConfigService(ecosConfigService);
        valuePredicateToFtsConverter.setEcosTypeService(ecosTypeService);
        valuePredicateToFtsConverter.setMessageService(messageService);
        valuePredicateToFtsConverter.setNamespaceService(namespaceService);
        valuePredicateToFtsConverter.setNodeService(nodeService);
        valuePredicateToFtsConverter.setSearchService(searchService);
        delegator.setValuePredicateToFtsConverter(valuePredicateToFtsConverter);
    }

    private NamespaceService namespaceServiceMock() {

        val namespaceService = Mockito.mock(NamespaceService.class);

        when(namespaceService.getNamespaceURI(Mockito.any())).thenAnswer(a -> {
            val prefix = (String) a.getArguments()[0];
            return namespaceUri.get(prefix);
        });

        return namespaceService;
    }

    private EcosTypeAlfTypeService ecosTypeAlfTypeServiceMock() {

        val ecosTypeAlfTypeService = Mockito.mock(EcosTypeAlfTypeService.class);

        when(ecosTypeAlfTypeService.getAlfTypeToSearch(Mockito.any())).thenAnswer(a -> {
            val typeRef = (RecordRef) a.getArguments()[0];
            TypeDto typeDto = ecosTypes.get(typeRef);
            if (typeDto == null) {
                return null;
            }
            ObjectData inhAttributes = typeDto.getInhAttributes();
            if (inhAttributes == null) {
                return null;
            }
            String result = inhAttributes.get("alfType").asText();
            return StringUtils.isBlank(result) ? null : result;
        });

        return ecosTypeAlfTypeService;
    }

    private EcosTypeService ecosTypeServiceMock() {

        val ecosTypeService = Mockito.mock(EcosTypeService.class);

        when(ecosTypeService.expandTypeWithChildren(Mockito.any())).thenAnswer(a -> {
            val typeRef = (RecordRef) a.getArguments()[0];
            return ecosTypes.values()
                .stream()
                .filter(t -> typeRef.equals(t.getParentRef()))
                .collect(Collectors.toList());
        });

        when(ecosTypeService.getDescendantTypes(Mockito.any())).thenAnswer(a -> {
            val typeRef = (RecordRef) a.getArguments()[0];
            return ecosTypes.values()
                .stream()
                .filter(t -> typeRef.equals(t.getParentRef()))
                .map(t -> TypeUtils.getTypeRef(t.getId()))
                .collect(Collectors.toList());
        });

        when(ecosTypeService.getTypeDef(Mockito.any())).thenAnswer(a -> {
            val typeRef = (RecordRef) a.getArguments()[0];
            return ecosTypes.get(typeRef);
        });

        return ecosTypeService;
    }

    public QName toQName(String prefixedValue) {
        return QName.resolveToQName(namespaceService, prefixedValue);
    }

    protected void registerNsUri(String prefix, String uri) {
        namespaceUri.put(prefix, uri);
    }

    protected void registerType(TypeDto typeDto) {
        this.ecosTypes.put(TypeUtils.getTypeRef(typeDto.getId()), typeDto);
    }
}
