<import resource="classpath:/alfresco/templates/webscripts/org/alfresco/slingshot/documentlibrary-v2/evaluator.lib.js">
<import resource="classpath:/alfresco/templates/webscripts/org/alfresco/slingshot/documentlibrary-v2/filters.lib.js">
<import resource="classpath:/alfresco/templates/webscripts/org/alfresco/slingshot/documentlibrary-v2/parse-args.lib.js">

const REQUEST_MAX = 1000;

/**
* Method that performs the actual loading of the nodes.
*
* Note!
* Will optimize performance by using ScriptNode.childFileFolders for directory listings
* In other words when the "path" filter is used.
*
* @method doclist_getAllNodes
* @param parsedArgs {Object}
* @param filterParams {Object}
* @param query {String}
* @param totalItemCount {int}
* @return {object} Returns the node and corresponding pagination metadata
* {
    *    allNodes: {Array}
*    totalRecords: {int}
*    requestTotalCountMax: {int}
*    paged: {boolean}
*    query: {String}
* }
*/
function doclist_getAllNodes(parsedArgs, filterParams, query, totalItemCount)
{
    var filter = args.filter,
    totalRecords = 0,
    requestTotalCountMax = 0,
    paged = false,
    allNodes = [];

    var node = search.findNode(parsedArgs.nodeRef)
    var assosoation_type = parsedArgs.type.replace('_',':')
    allNodes = node.childAssocs[assosoation_type]
    return {
    allNodes: allNodes,
    totalRecords: totalRecords,
    requestTotalCountMax: requestTotalCountMax,
    paged: paged,
    query: query
    };
}

/**
* Main entry point: Create collection of documents and folders in the given space
*
* @method doclist_main
*/
function doclist_main()
{
    // Use helper function to get the arguments
    var parsedArgs = ParseArgs.getParsedArgs();
    if (parsedArgs === null)
    {
    return;
    }

var filter = args.filter,
items = [];

// Try to find a filter query based on the passed-in arguments
var allNodes = [],
totalRecords = 0,
requestTotalCountMax = 0,
paged = false,
favourites = Common.getFavourites(),
filterParams = Filters.getFilterParams(filter, parsedArgs,
            {
                favourites: favourites
                }),
query = filterParams.query,
allSites = (parsedArgs.nodeRef == "alfresco://sites/home");

if (logger.isLoggingEnabled())
logger.log("doclist.lib.js - NodeRef: " + parsedArgs.nodeRef + " Query: " + query);

var totalItemCount = filterParams.limitResults ? parseInt(filterParams.limitResults, 10) : -1;
// For all sites documentLibrary query we pull in all available results and post filter
if (totalItemCount === 0) totalItemCount = -1;
else if (allSites) totalItemCount = (totalItemCount > 0 ? totalItemCount * 10 : 500);


var allNodesResult = doclist_getAllNodes(parsedArgs, filterParams, query, totalItemCount);
allNodes = allNodesResult.allNodes;
totalRecords = allNodesResult.totalRecords;
requestTotalCountMax = allNodesResult.requestTotalCountMax;
paged = allNodesResult.paged;
query = allNodesResult.query;


if (logger.isLoggingEnabled() && allNodes) {
    logger.log("doclist.lib.js - query results: " + allNodes.length);
    }
// Generate the qname path match regex required for all sites 'documentLibrary' results match
var pathRegex;
if (allSites)
   {
       // escape the forward slash characters in the qname path
       // TODO: replace with java.lang.String regex match for performance
       var pathMatch = new String(parsedArgs.rootNode.qnamePath).replace(/\//g, '\\/') + "\\/.*\\/cm:documentLibrary\\/.*";
       pathRegex = new RegExp(pathMatch, "gi");
       if (logger.isLoggingEnabled())
       logger.log("doclist.lib.js - will match results using regex: " + pathMatch);
       }

// Ensure folders and folderlinks appear at the top of the list
var folderNodes = [],
documentNodes = [];

for each (node in allNodes)
   {
       if (totalItemCount !== 0)
       {
       try
       {
       if (!allSites || node.qnamePath.match(pathRegex))
       {
       totalItemCount--;
       if (node.isContainer || node.isLinkToContainer)
       {
       folderNodes.push(node);
       }
else
               {
                   documentNodes.push(node);
                   }
}
}
catch (e)
         {
             // Possibly an old indexed node - ignore it
             }
} else break;
}

// Node type counts
var folderNodesCount = folderNodes.length,
documentNodesCount = documentNodes.length,
nodes;

if (parsedArgs.type === "documents")
   {
       nodes = documentNodes;
       totalRecords -= folderNodesCount;
       }
else
   {
       // TODO: Sorting with folders at end -- swap order of concat()
       nodes = folderNodes.concat(documentNodes);
       }

if (logger.isLoggingEnabled())
logger.log("doclist.lib.js - totalRecords: " + totalRecords);

// Pagination
var pageSize = args.size || nodes.length,
pagePos = args.pos || "1",
startIndex = (pagePos - 1) * pageSize;

if (!paged)
   {
       // Trim the nodes array down to the page size
       nodes = nodes.slice(startIndex, pagePos * pageSize);
       }

// Common or variable parent container?
var parent = null;

if (!filterParams.variablePath)
   {
       // Parent node permissions (and Site role if applicable)
       parent = Evaluator.run(parsedArgs.pathNode, true);
       }

var isThumbnailNameRegistered = thumbnailService.isThumbnailNameRegistered(THUMBNAIL_NAME),
thumbnail = null,
locationNode,
item;

// Loop through and evaluate each node in this result set
for each (node in nodes)
   {
       // Get evaluated properties.
       item = Evaluator.run(node);
       if (item !== null)
       {
       item.isFavourite = (favourites[item.node.nodeRef] === true);
       item.likes = Common.getLikes(node);

       // Does this collection of nodes have potentially differering paths?
       if (filterParams.variablePath || item.isLink)
       {
       locationNode = item.isLink ? item.linkedNode : item.node;
       // Ensure we have Read permissions on the destination on the link object
       if (!locationNode.hasPermission("Read")) continue;
       location = Common.getLocation(locationNode, parsedArgs.libraryRoot);
       // Parent node
       if (node.parent != null && node.parent.isContainer && node.parent.hasPermission("Read"))
       {
       item.parent = Evaluator.run(node.parent, true);
       }
}
else
         {
             location =
             {
                 site: parsedArgs.location.site,
                 siteTitle: parsedArgs.location.siteTitle,
                 sitePreset: parsedArgs.location.sitePreset,
                 container: parsedArgs.location.container,
                 containerType: parsedArgs.location.containerType,
                 path: parsedArgs.location.path,
                 file: node.name
             };
}

// Resolved location
item.location = location;

// Check: thumbnail type is registered && node is a cm:content subtype && valid inputStream for content property
var is = item.node.properties ? (item.node.properties.content ? (item.node.properties.content.inputStream ? item.node.properties.content.inputStream : null) : null) : null;
try
         {
             if (isThumbnailNameRegistered && item.node.isSubType("cm:content") && (null != is))
             {
             // Make sure we have a thumbnail.
             thumbnail = item.node.getThumbnail(THUMBNAIL_NAME);
             if (thumbnail === null)
             {
             // No thumbnail, so queue creation
             item.node.createThumbnail(THUMBNAIL_NAME, true);
             }
}
}
finally
         {
             if (null != is)
             {
             is.close();
             }
}

items.push(item);
}
else
      {
          --totalRecords;
          }
}

// Array Remove - By John Resig (MIT Licensed)
var fnArrayRemove = function fnArrayRemove(array, from, to)
   {
       var rest = array.slice((to || from) + 1 || array.length);
       array.length = from < 0 ? array.length + from : from;
       return array.push.apply(array, rest);
       };

/**
* De-duplicate orignals for any existing working copies.
* This can't be done in evaluator.lib.js as it has no knowledge of the current filter or UI operation.
* Note: This may result in pages containing less than the configured amount of items (50 by default).
*/
for each (item in items)
   {
       if (item.workingCopy && item.workingCopy.isWorkingCopy)
       {
       var workingCopySource = String(item.workingCopy.sourceNodeRef);
       for (var i = 0, ii = items.length; i < ii; i++)
       {
       if (String(items[i].node.nodeRef) == workingCopySource)
       {
       fnArrayRemove(items, i);
       --totalRecords;
       break;
       }
}
}
}

var paging =
   {
       totalRecords: totalRecords,
       startIndex: startIndex
       };

if (paged && (totalRecords == requestTotalCountMax))
   {
       paging.totalRecordsUpper = requestTotalCountMax;
       }

return (
   {
       luceneQuery: query,
       paging: paging,
       container: parsedArgs.rootNode,
       parent: parent,
       onlineEditing: utils.moduleInstalled("org.alfresco.module.vti"),
       itemCount:
       {
       folders: folderNodesCount,
       documents: documentNodesCount
       },
items: items,
customJSON: slingshotDocLib.getJSON()
});
}


model.doclist = doclist_main();