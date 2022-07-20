package ru.citeck.ecos.webscripts.webfilter;

import org.junit.Before;
import org.junit.Test;
import org.springframework.stereotype.Controller;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import static org.junit.Assert.*;


public class EcosRewriteParamsRequestFilterTest {

    private MockMvc mockMvc;

    private static final String REQ_PARAM = "alfresco/@workspace://SpacesStore/node-id";
    private static final String REPLACED_PARAM = "workspace://SpacesStore/node-id";

    @Before
    public void setUp() {
        this.mockMvc = MockMvcBuilders.standaloneSetup(new TestController())
            .addFilter(new EcosRewriteParamsRequestFilter())
            .build();
    }

    @Test
    public void testRewriteParams() throws Exception {
        mockMvc.perform(
            MockMvcRequestBuilders.get(
                "/rewriteParamsTest?someRef={someRef}",
                REQ_PARAM
            )
        ).andDo(mvcResult -> {
            assertEquals(REPLACED_PARAM,
                mvcResult.getResponse().getContentAsString());
            }
        );
    }

    @Test
    public void testSolrModelsDiffReq() throws Exception {
        mockMvc.perform(
            MockMvcRequestBuilders.get(
                "/alfresco/service/api/solr/modelsdiff?someRef={someRef}",
                REQ_PARAM
            )
        )
            .andDo(mvcResult -> {
                assertEquals(REQ_PARAM,
                    mvcResult.getResponse().getContentAsString());
            }
        );
    }

    @Test
    public void testRecordsReq() throws Exception {
        mockMvc.perform(
                MockMvcRequestBuilders.get(
                    "/alfresco/s/citeck/ecos/records/query?someRef={someRef}",
                    REQ_PARAM
                )
            )
            .andDo(mvcResult -> {
                    assertEquals(REQ_PARAM,
                        mvcResult.getResponse().getContentAsString());
                }
            );
    }

    @Controller
    @RequestMapping("/")
    private class TestController {
        @ResponseBody
        @RequestMapping("/rewriteParamsTest")
        public String rewriteParams(@RequestParam("someRef") String someRef) {
            return someRef;
        }

        @ResponseBody
        @RequestMapping("/alfresco/service/api/solr/modelsdiff")
        public String modelsdiff(@RequestParam("someRef") String someRef) {
            return someRef;
        }

        @ResponseBody
        @RequestMapping("/alfresco/s/citeck/ecos/records/query")
        public String records(@RequestParam("someRef") String someRef) {
            return someRef;
        }

    }

}
