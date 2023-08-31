package ru.citeck.ecos.utils;

import org.alfresco.util.Pair;
import org.junit.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.*;

public class NodeUtilsTest {

    private final NodeUtils nodeUtils = new NodeUtils();

    @Test
    public void isNodeRefWithUuidTest() {

        List<Pair<String, Boolean>> refsToTest = Arrays.asList(
            new Pair<>("workspace://SpacesStore/e0821c4c-6d3f-44ac-89e0-8196996b7417", true),
            new Pair<>("workspace://OthersStore/e0821c4c-6d3f-44ac-89e0-8196996b7417", false),
            new Pair<>("workspace://SpacesStore/e0821c4c-6d3f-44ac-89e0A8196996b7417", false),
            new Pair<>("workspace://SpacesStore/e0821c4c-6d3f-44ac-89e0-8196996b7417-alias-1", false),
            new Pair<>("workspace://SpacesStore/custom-uuid", false),
            new Pair<>("custom-string", false),
            new Pair<>("", false),
            new Pair<>(null, false)
        );

        for (Pair<String, Boolean> ref : refsToTest) {
            assertEquals(ref.toString(), ref.getSecond(), NodeUtils.isNodeRefWithUuid(ref.getFirst()));
        }
    }

    @Test
    public void isNodeRefTest() {
        assertFalse(nodeUtils.isNodeRef("workspace://SpacesStore/51978b08-9aa0-4be8-b876-48dc7b9f6d50_8/h"));
        assertFalse(nodeUtils.isNodeRef("archive://SpacesStore/51978b08-9aa0-4be8-b876-48dc7b9f6d50_8/h"));
        assertFalse(nodeUtils.isNodeRef("deleted://SpacesStore/51978b08-9aa0-4be8-b876-48dc7b9f6d50_8/h"));
        assertFalse(nodeUtils.isNodeRef("workspace://NonstandardStoreName/51978b08-9aa0-4be8-b876-48dc7b9f6d50_8/h"));
        assertTrue(nodeUtils.isNodeRef("workspace://SpacesStore/51978b08-9aa0-4be8-b876-48dc7b9f6d50"));
        assertTrue(nodeUtils.isNodeRef("archive://SpacesStore/51978b08-9aa0-4be8-b876-48dc7b9f6d50"));
        assertTrue(nodeUtils.isNodeRef("deleted://SpacesStore/51978b08-9aa0-4be8-b876-48dc7b9f6d50"));
        assertTrue(nodeUtils.isNodeRef("workspace://NonstandardStoreName/51978b08-9aa0-4be8-b876-48dc7b9f6d50"));
        assertTrue(nodeUtils.isNodeRef("workspace://SpacesStore/custom-node-id"));
        assertTrue(nodeUtils.isNodeRef("archive://SpacesStore/custom-node-id"));
        assertTrue(nodeUtils.isNodeRef("deleted://SpacesStore/custom-node-id"));
        assertTrue(nodeUtils.isNodeRef("workspace://NonstandardStoreName/custom-node-id"));
    }
}
