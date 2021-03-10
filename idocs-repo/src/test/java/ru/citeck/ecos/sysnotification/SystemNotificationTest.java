package ru.citeck.ecos.sysnotification;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.Test;
import ru.citeck.ecos.records2.RecordMeta;
import ru.citeck.ecos.records2.RecordRef;
import ru.citeck.ecos.records2.RecordsService;
import ru.citeck.ecos.records2.RecordsServiceFactory;
import ru.citeck.ecos.records2.request.delete.RecordsDeletion;
import ru.citeck.ecos.records2.request.query.RecordsQuery;
import ru.citeck.ecos.records2.request.query.RecordsQueryResult;
import ru.citeck.ecos.sysnotification.api.records.SystemNotificationRecordsDao;
import ru.citeck.ecos.sysnotification.dao.SystemNotificationDao;
import ru.citeck.ecos.sysnotification.dto.SystemNotificationDto;
import ru.citeck.ecos.sysnotification.service.impl.SystemNotificationServiceImpl;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import static org.junit.Assert.*;

public class SystemNotificationTest {
    private static final String SOURCE_ID = "system-notification";

    @Test
    public void test() {
        // Init
        SystemNotificationDao systemNotificationDao = new SystemNotificationDaoImpl();
        SystemNotificationServiceImpl systemNotificationService = new SystemNotificationServiceImpl();
        systemNotificationService.setSystemNotificationDao(systemNotificationDao);

        SystemNotificationRecordsDao recordsDao = new SystemNotificationRecordsDao();
        recordsDao.setSystemNotificationService(systemNotificationService);

        RecordsService recordsService = new RecordsServiceFactory().getRecordsService();
        recordsService.register(recordsDao);

        RecordsQuery query = new RecordsQuery();
        query.setSourceId(SOURCE_ID);

        // First query (empty)
        RecordsQueryResult<RecordRef> result = recordsService.queryRecords(query);
        assertTrue("Result list should be empty", result.getRecords().isEmpty());
        assertFalse("'hasMore' property should be false", result.getHasMore());
        assertEquals("'totalCount' property should be 0", 0, result.getTotalCount());

        // Second query (creation)
        RecordMeta meta = new RecordMeta(RecordRef.create(SOURCE_ID, ""));
        meta.setAttribute("message", "some message");
        meta.setAttribute("time", Instant.now().plus(1, ChronoUnit.DAYS));
        recordsService.mutate(meta);

        result = recordsService.queryRecords(query);
        assertFalse("Result list should not be empty", result.getRecords().isEmpty());
        assertFalse("'hasMore' property should be false", result.getHasMore());
        assertEquals("'totalCount' property should be 1", 1, result.getTotalCount());

        // Third query (deletion)
        RecordsDeletion recordsDeletion = new RecordsDeletion();
        recordsDeletion.setRecords(result.getRecords());
        recordsService.delete(recordsDeletion);

        result = recordsService.queryRecords(query);
        assertTrue("Result list should be empty", result.getRecords().isEmpty());
        assertFalse("'hasMore' property should be false", result.getHasMore());
        assertEquals("'totalCount' property should be 0", 0, result.getTotalCount());
    }

    private class SystemNotificationDaoImpl implements SystemNotificationDao {
        private int index = 0;
        private List<SystemNotificationDto> records = new ArrayList<>();

        @NotNull
        @Override
        public List<SystemNotificationDto> get(int maxItems, int skipCount, boolean onlyActive) {
            List<SystemNotificationDto> filteredRecords = onlyActive
                ? records.stream()
                .filter(r -> (r.getTime() != null) && (r.getTime().toEpochMilli() > Instant.now().toEpochMilli()))
                .collect(Collectors.toList())
                : records;

            int indexTo = (maxItems < 0) || (skipCount + maxItems > filteredRecords.size())
                ? filteredRecords.size()
                : skipCount + maxItems;

            return filteredRecords.subList(skipCount, indexTo);
        }

        @Nullable
        @Override
        public SystemNotificationDto get(@NotNull String id) {
            for (SystemNotificationDto record: records) {
                if (record.getId().equals(id)) {
                    return record;
                }
            }
            return null;
        }

        @NotNull
        @Override
        public SystemNotificationDto save(@NotNull SystemNotificationDto systemNotificationDto) {
            systemNotificationDto.setId("id-" + index++);
            records.add(systemNotificationDto);
            return systemNotificationDto;
        }

        @Override
        public void delete(@NotNull String id) {
            for (int i = 0; i < records.size(); i++) {
                if (records.get(i).getId().equals(id)) {
                    records.remove(i);
                    break;
                }
            }
        }

        @Override
        public long getTotalCount() {
            return records.size();
        }
    }
}
