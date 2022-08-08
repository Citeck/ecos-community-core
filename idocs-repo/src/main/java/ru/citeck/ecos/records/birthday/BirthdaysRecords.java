package ru.citeck.ecos.records.birthday;

import org.alfresco.service.cmr.repository.NodeRef;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import ru.citeck.ecos.records.models.UserWithAvatarDto;
import ru.citeck.ecos.records2.RecordRef;
import ru.citeck.ecos.records2.RecordsService;
import ru.citeck.ecos.records2.graphql.meta.value.MetaField;
import ru.citeck.ecos.records2.request.delete.RecordsDelResult;
import ru.citeck.ecos.records2.request.delete.RecordsDeletion;
import ru.citeck.ecos.records2.request.mutation.RecordsMutResult;
import ru.citeck.ecos.records2.request.query.RecordsQuery;
import ru.citeck.ecos.records2.request.query.RecordsQueryResult;
import ru.citeck.ecos.records2.source.dao.local.v2.LocalRecordsCrudDao;

import java.util.List;
import java.util.stream.Collectors;

/**
 * @author Roman Makarskiy
 */
@Component
public class BirthdaysRecords extends LocalRecordsCrudDao<UserWithAvatarDto> {

    private static final String ID = "birthdays";
    private static final String ERROR_UNSUPPORTED_OPERATION = "This operation not supported";

    {
        setId(ID);
    }

    private final BirthdaysUtils birthdaysUtils;
    private final RecordsService recordsService;

    @Autowired
    public BirthdaysRecords(BirthdaysUtils birthdaysUtils, RecordsService recordsService) {
        this.birthdaysUtils = birthdaysUtils;
        this.recordsService = recordsService;
    }

    @Override
    public RecordsQueryResult<UserWithAvatarDto> queryLocalRecords(RecordsQuery recordsQuery, MetaField metaField) {
        RecordsQueryResult<UserWithAvatarDto> result = new RecordsQueryResult<>();

        List<UserWithAvatarDto> birthdays = getBirthdays();
        result.addRecords(birthdays);
        result.setTotalCount(birthdays.size());
        result.setHasMore(false);

        return result;
    }

    private List<UserWithAvatarDto> getBirthdays() {
        return birthdaysUtils.search()
                .stream()
                .map(this::toUserDTO)
                .collect(Collectors.toList());
    }

    private UserWithAvatarDto toUserDTO(NodeRef user) {
        return recordsService.getMeta(RecordRef.create("", user.toString()), UserWithAvatarDto.class);
    }

    @Override
    public RecordsMutResult save(List<UserWithAvatarDto> list) {
        throw new IllegalArgumentException(ERROR_UNSUPPORTED_OPERATION);
    }

    @Override
    public RecordsDelResult delete(RecordsDeletion recordsDeletion) {
        throw new IllegalArgumentException(ERROR_UNSUPPORTED_OPERATION);
    }

    @Override
    public List<UserWithAvatarDto> getValuesToMutate(List<RecordRef> list) {
        throw new IllegalArgumentException(ERROR_UNSUPPORTED_OPERATION);
    }

    @Override
    public List<UserWithAvatarDto> getLocalRecordsMeta(List<RecordRef> list, MetaField metaField) {
        throw new IllegalArgumentException(ERROR_UNSUPPORTED_OPERATION);
    }
}
