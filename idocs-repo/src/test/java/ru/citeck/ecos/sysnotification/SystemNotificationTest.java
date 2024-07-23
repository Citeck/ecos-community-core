package ru.citeck.ecos.sysnotification;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.Test;
import ru.citeck.ecos.records2.RecordMeta;
import ru.citeck.ecos.records2.RecordsService;
import ru.citeck.ecos.records2.request.delete.RecordsDeletion;
import ru.citeck.ecos.records2.request.query.RecordsQuery;
import ru.citeck.ecos.records2.request.query.RecordsQueryResult;
import ru.citeck.ecos.records3.RecordsServiceFactory;
import ru.citeck.ecos.sysnotification.api.records.SystemNotificationRecordsDao;
import ru.citeck.ecos.sysnotification.dao.SystemNotificationDao;
import ru.citeck.ecos.sysnotification.dto.SystemNotificationDto;
import ru.citeck.ecos.sysnotification.service.impl.SystemNotificationServiceImpl;
import ru.citeck.ecos.webapp.api.entity.EntityRef;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.*;

public class SystemNotificationTest {
    private static final String SOURCE_ID = "system-notification";
    private static final String MESSAGE_ATTR = "message";
    public static final String TIME_ATTR = "endTime";

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
        RecordsQueryResult<EntityRef> result1 = recordsService.queryRecords(query);
        assertTrue("Result list should be empty", result1.getRecords().isEmpty());
        assertFalse("'hasMore' property should be false", result1.getHasMore());
        assertEquals("'totalCount' property should be 0", 0, result1.getTotalCount());

        // Second query (creation)
        String expectedMessage = "some message";
        Instant expectedTime = Instant.now().plus(1, ChronoUnit.DAYS);

        RecordMeta meta = new RecordMeta(EntityRef.create(SOURCE_ID, ""));
        meta.setAttribute(MESSAGE_ATTR, expectedMessage);
        meta.setAttribute(TIME_ATTR, expectedTime);
        recordsService.mutate(meta);

        List<String> attributes = Arrays.asList(MESSAGE_ATTR, TIME_ATTR);
        RecordsQueryResult<RecordMeta> result2 = recordsService.queryRecords(query, attributes);
        assertFalse("Result list should not be empty", result2.getRecords().isEmpty());
        assertFalse("'hasMore' property should be false", result2.getHasMore());
        assertEquals("'totalCount' property should be 1", 1, result2.getTotalCount());

        RecordMeta recordMeta = result2.getRecords().get(0);
        String actualMessage = recordMeta.getAttribute(MESSAGE_ATTR).getAs(String.class);
        Instant actualTime = recordMeta.getAttribute(TIME_ATTR).getAs(Instant.class);
        assertEquals("Expected message should be equals actual message", expectedMessage, actualMessage);
        assertEquals("Expected time should be equals actual time", expectedTime, actualTime);

        // Third query (deletion)
        RecordsQueryResult<EntityRef> result3 = recordsService.queryRecords(query);
        RecordsDeletion recordsDeletion = new RecordsDeletion();
        recordsDeletion.setRecords(result3.getRecords());
        recordsService.delete(recordsDeletion);

        result3 = recordsService.queryRecords(query);
        assertTrue("Result list should be empty", result3.getRecords().isEmpty());
        assertFalse("'hasMore' property should be false", result3.getHasMore());
        assertEquals("'totalCount' property should be 0", 0, result3.getTotalCount());
    }

    private class SystemNotificationDaoImpl implements SystemNotificationDao {
        private int index = 0;
        private List<SystemNotificationDto> records = new ArrayList<>();

        @NotNull
        @Override
        public RecordsQueryResult<SystemNotificationDto> get(@NotNull RecordsQuery recordsQuery) {
            int totalCount = records.size();
            int maxItems = recordsQuery.getMaxItems();
            int skipCount = recordsQuery.getSkipCount();
            int indexTo = (maxItems < 0) || (skipCount + maxItems > records.size())
                ? records.size()
                : skipCount + maxItems;
            List<SystemNotificationDto> resultRecords = records.subList(skipCount, indexTo);

            RecordsQueryResult<SystemNotificationDto> result = new RecordsQueryResult<>();
            result.setRecords(resultRecords);
            result.setTotalCount(totalCount);
            result.setHasMore((maxItems >= 0) && (totalCount > maxItems + skipCount));

            return result;
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
    }
}
