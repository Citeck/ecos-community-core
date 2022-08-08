package ru.citeck.ecos.utils;

import org.alfresco.util.Pair;
import org.junit.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;

public class NodeUtilsTest {

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
}
