package ru.citeck.ecos.workflow.utils;

import org.alfresco.repo.security.authentication.AuthenticationUtil;
import org.alfresco.repo.security.authentication.AuthenticationUtil.RunAsWork;
import org.alfresco.service.cmr.repository.NodeRef;
import org.alfresco.service.cmr.repository.NodeService;
import org.alfresco.service.cmr.repository.StoreRef;
import org.alfresco.service.cmr.search.ResultSet;
import org.alfresco.service.cmr.search.SearchService;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.joda.time.DateTime;
import org.quartz.JobDataMap;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import ru.citeck.ecos.job.AbstractLockedJob;

import java.text.SimpleDateFormat;
import java.util.List;

public class RemoveDetachedJob extends AbstractLockedJob
{
	private static Log logger = LogFactory.getLog(RemoveDetachedJob.class);

	private static final Object PARAM_SEARCH_SERVICE = "searchService";
	private static final Object PARAM_NODE_SERVICE = "NodeService";

	private int minusDays;
	  
    public void setMinusDays(int timeout) {
    	this.minusDays = timeout;
    }
	

	@SuppressWarnings("unused")
	@Override
	public void executeJob(JobExecutionContext context) throws JobExecutionException {
        JobDataMap data = context.getJobDetail().getJobDataMap();

        final SearchService searchService = (SearchService) data.get(PARAM_SEARCH_SERVICE);
		final NodeService nodeService = (NodeService) data.get(PARAM_NODE_SERVICE);
		final int minusDayz = minusDays;
		
		Integer deletedNodes = AuthenticationUtil.runAs(new RunAsWork<Integer>() {

			@Override
			public Integer doWork() throws Exception {
				ResultSet rs = null;
				DateTime dateTime = new DateTime().minusDays(minusDayz);
				
				SimpleDateFormat dt1 = new SimpleDateFormat("yyyy-MM-dd");
		    	String query = String.format("PATH:\"/cm:attachmentsRoot/*\" AND @cm\\:created:[MIN TO \"%s\"]", dt1.format(dateTime.toDate()));
				
				rs = searchService.query(StoreRef.STORE_REF_WORKSPACE_SPACESSTORE, SearchService.LANGUAGE_LUCENE, query);
				List<NodeRef> refs = rs.getNodeRefs();
				int deleted = 0;
				
				for (NodeRef ref : refs) {
					nodeService.deleteNode(ref);
					deleted++;
				}
				
				return deleted;
			}
			
		}, AuthenticationUtil.getSystemUserName());
		if(logger.isInfoEnabled()) {
			logger.info("Removed " + deletedNodes + " orphaned nodes");
		}
	}

}
