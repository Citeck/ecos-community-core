package ru.citeck.ecos.webscripts.orgstruct;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.collect.Iterables;
import com.sun.org.apache.xpath.internal.operations.Bool;
import lombok.extern.slf4j.Slf4j;
import org.alfresco.error.AlfrescoRuntimeException;
import org.alfresco.model.ContentModel;
import org.alfresco.repo.forms.FormException;
import org.alfresco.repo.security.authentication.AuthenticationUtil;
import org.alfresco.service.ServiceRegistry;
import org.alfresco.service.cmr.repository.NodeRef;
import org.alfresco.service.cmr.repository.NodeService;
import org.alfresco.service.cmr.repository.StoreRef;
import org.alfresco.service.cmr.search.PermissionEvaluationMode;
import org.alfresco.service.cmr.search.ResultSet;
import org.alfresco.service.cmr.search.SearchService;
import org.alfresco.service.cmr.security.AuthenticationService;
import org.alfresco.service.cmr.security.AuthorityService;
import org.alfresco.service.cmr.security.AuthorityType;
import org.alfresco.service.namespace.QName;
import org.alfresco.util.Pair;
import org.apache.commons.lang.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.extensions.webscripts.*;
import ru.citeck.ecos.config.EcosConfigService;
import ru.citeck.ecos.model.DeputyModel;
import ru.citeck.ecos.model.EcosModel;
import ru.citeck.ecos.model.OrgStructModel;
import ru.citeck.ecos.orgstruct.OrgMetaService;
import ru.citeck.ecos.orgstruct.OrgStructService;
import ru.citeck.ecos.search.ftsquery.FTSQuery;
import ru.citeck.ecos.utils.ConfigUtils;
import ru.citeck.ecos.utils.PersonUtils;

import java.io.IOException;
import java.io.Serializable;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Slf4j
public class ChildrenGet extends AbstractWebScript {

    private static final String TEMPLATE_PARAM_GROUPNAME = "groupname";

    private static final String PARAM_ROLE = "role";
    private static final String PARAM_USER = "user";
    private static final String PARAM_GROUP = "group";
    private static final String PARAM_BRANCH = "branch";
    private static final String PARAM_FILTER = "filter";
    private static final String PARAM_DEFAULT = "default";
    private static final String PARAM_RECURSE = "recurse";
    private static final String PARAM_SUB_TYPES = "subTypes";
    private static final String PARAM_SHOW_DISABLED = "showdisabled";
    private static final String PARAM_ADD_ADMIN_GROUP = "addAdminGroup";
    private static final String PARAM_EXCLUDE_AUTHORITIES = "excludeAuthorities";

    private static final String CONFIG_KEY_SHOW_INACTIVE = "orgstruct-show-inactive-user-only-for-admin";
    private static final String CONFIG_KEY_HIDE_INACTIVE_FOR_ALL = "hide-disabled-users-for-everyone";
    private static final String CONFIG_KEY_HIDE_IN_ORGSTRUCT = "hide-in-orgstruct";

    private static final String GROUP_ALFRESCO_ADMINISTRATORS = "GROUP_ALFRESCO_ADMINISTRATORS";

    private static final String GROUP_PREFIX = "GROUP_";
    private static final int DEFAULT_RESULTS_LIMIT = 50;

    private static final String INVALID_LOGIC_MESSAGE = "Invalid logic in excluded groups notation";

    private NodeService nodeService;
    private SearchService searchService;
    private OrgMetaService orgMetaService;
    private OrgStructService orgStructService;
    private AuthorityService authorityService;
    private EcosConfigService ecosConfigService;
    private AuthenticationService authenticationService;

    private LoadingCache<RequestParams, List<Pair<NodeRef, String>>> authoritiesCache;

    private ObjectMapper objectMapper = new ObjectMapper();

    @Autowired
    public ChildrenGet(ServiceRegistry serviceRegistry,
                       @Qualifier("ecosConfigService") EcosConfigService ecosConfigService,
                       OrgStructService orgStructService,
                       OrgMetaService orgMetaService) {

        this.orgMetaService = orgMetaService;
        this.orgStructService = orgStructService;
        this.ecosConfigService = ecosConfigService;
        this.nodeService = serviceRegistry.getNodeService();
        this.searchService = serviceRegistry.getSearchService();
        this.authorityService = serviceRegistry.getAuthorityService();
        this.authenticationService = serviceRegistry.getAuthenticationService();

        authoritiesCache = CacheBuilder.newBuilder()
                .expireAfterWrite(5, TimeUnit.MINUTES)
                .maximumSize(200)
                .build(CacheLoader.from(options ->
                        AuthenticationUtil.runAsSystem(() -> getAuthorities(options))
                ));
    }

    @Override
    public void execute(WebScriptRequest req, WebScriptResponse res) throws IOException {

        RequestParams params = getRequestParams(req);

        if (params.groupRef == null) {
            res.getWriter().write("Group " + params.filterOptions.rootGroup + " not found");
            res.setStatus(Status.STATUS_NOT_FOUND);
            return;
        }

        List<Pair<NodeRef, String>> authoritiesRaw = authoritiesCache.getUnchecked(params);
        List<Authority> authorities = formatAuthorities(authoritiesRaw);

        res.setContentType(Format.JSON.mimetype() + ";charset=UTF-8");
        objectMapper.writeValue(res.getWriter(), authorities);
        res.setStatus(Status.STATUS_OK);
    }

    private List<Authority> formatAuthorities(List<Pair<NodeRef, String>> authorities) {

        List<Authority> result = new ArrayList<>();

        Map<String, Boolean> roleIsManager = new HashMap<>();

        List<NodeRef> roles = orgMetaService.getAllSubTypes("role");

        roles.forEach(ref -> {
            Map<QName, Serializable> props = nodeService.getProperties(ref);
            String name = (String) props.get(ContentModel.PROP_NAME);
            Boolean isManager = Boolean.TRUE.equals(props.get(OrgStructModel.PROP_ROLE_IS_MANAGER));
            roleIsManager.put(name, isManager);
        });

        for (Pair<NodeRef, String> authorityRefName : authorities) {

            Authority authority;
            if (authorityRefName.getSecond().startsWith(GROUP_PREFIX)) {
                authority = formatGroupAuthority(authorityRefName, roleIsManager);
            } else {
                authority = formatUserAuthority(authorityRefName);
            }
            authority.nodeRef = authorityRefName.getFirst().toString();
            authority.fullName = authorityRefName.getSecond();
            authority.shortName = authorityService.getShortName(authorityRefName.getSecond());

            result.add(authority);
        }
        return result;
    }

    private Authority formatUserAuthority(Pair<NodeRef, String> authority) {

        Map<QName, Serializable> props = nodeService.getProperties(authority.getFirst());

        UserAuthority result = new UserAuthority();

        result.setAvailable((Boolean) props.get(DeputyModel.PROP_AVAILABLE));
        result.setPersonDisabled((Boolean) props.get(EcosModel.PROP_IS_PERSON_DISABLED));

        result.firstName = (String) props.get(ContentModel.PROP_FIRSTNAME);
        result.lastName = (String) props.get(ContentModel.PROP_LASTNAME);
        result.email = (String) props.get(ContentModel.PROP_EMAIL);
        result.displayName = result.firstName + " " + result.lastName;

        return result;
    }

    private Authority formatGroupAuthority(Pair<NodeRef, String> authorityRefName, Map<String, Boolean> roleIsManager) {

        Map<QName, Serializable> props = nodeService.getProperties(authorityRefName.getFirst());

        String branchType = (String) props.get(OrgStructModel.PROP_BRANCH_TYPE);
        String roleType = (String) props.get(OrgStructModel.PROP_ROLE_TYPE);

        Authority authority;

        if (StringUtils.isNotBlank(branchType)) {
            BranchAuthority branchAuthority = new BranchAuthority();
            branchAuthority.groupSubType = branchType;
            authority = branchAuthority;
        } else if (StringUtils.isNotBlank(roleType)) {
            RoleAuthority roleAuthority = new RoleAuthority();
            roleAuthority.groupSubType = roleType;
            roleAuthority.roleIsManager = roleIsManager.getOrDefault(roleType, false);
            authority = roleAuthority;
        } else {
            authority = new GroupAuthority();
        }

        authority.displayName = (String) props.get(ContentModel.PROP_AUTHORITY_DISPLAY_NAME);
        if (StringUtils.isBlank(authority.displayName)) {
            authority.displayName = (String) props.get(ContentModel.PROP_AUTHORITY_NAME);
        }

        return authority;
    }

    private List<Pair<NodeRef, String>> getAuthorities(RequestParams params) {

        FilterOptions filterOptions = params.filterOptions;
        Stream<Pair<NodeRef, String>> authorities;

        if (params.recurse && StringUtils.isNotBlank(filterOptions.filter)) {
            authorities = findAuthorities(filterOptions);
        } else {
            boolean immediate = !params.recurse;
            String rootGroup = filterOptions.rootGroup;
            Set<String> children = authorityService.getContainedAuthorities(null, rootGroup, immediate);
            authorities = children.stream().map(this::getAuthorityNameRef);
        }

        Map<String, Boolean> inRootGroupCache = new HashMap<>();
        List<Pair<NodeRef, String>> results = authorities.filter(auth -> filterAuthority(auth, filterOptions, inRootGroupCache))
            .limit(filterOptions.limit)
            .collect(Collectors.toList());

        results.addAll(getAdminGroup(filterOptions));

        return results;
    }

    private List<Pair<NodeRef, String>> getAdminGroup(FilterOptions filterOptions) {
        boolean isAddAdminGroup = filterOptions.group
            && filterOptions.addAdminGroup
            && filterOptions.rootGroup.equals("GROUP__orgstruct_home_")
            && StringUtils.isNotBlank(filterOptions.filter);

        List<Pair<NodeRef, String>> results = Collections.emptyList();
        if (isAddAdminGroup) {
            List<NodeRef> groups = FTSQuery.createRaw().type(ContentModel.TYPE_AUTHORITY_CONTAINER)
                .and().open()
                .value(ContentModel.PROP_AUTHORITY_NAME, GROUP_ALFRESCO_ADMINISTRATORS).and()
                .value(ContentModel.PROP_AUTHORITY_NAME, filterOptions.filter)
                .close().query(searchService);

            if (groups != null) {
                results = groups.stream()
                    .map(this::getAuthorityNameRef)
                    .collect(Collectors.toList());
            }
        }

        return results;
    }

    private Stream<Pair<NodeRef, String>> findAuthorities(FilterOptions filterOptions) {

        FTSQuery query = FTSQuery.createRaw()
                .eventual()
                .permissionsMode(PermissionEvaluationMode.NONE)
                .maxItems(100);

        boolean notEmpty = false;

        String filter = filterOptions.filter;
        if (filterOptions.group) {
            notEmpty = true;
            query.open()
                .value(ContentModel.PROP_AUTHORITY_DISPLAY_NAME, filter).or()
                .value(ContentModel.PROP_AUTHORITY_NAME, filter)
                .close();
        }
        if (filterOptions.user) {
            if (notEmpty) {
                query.or();
            }
            query.open()
                    .value(ContentModel.PROP_USERNAME, filterOptions.filter)
                    .or();
            if (filterOptions.filterTokens.size() == 2) {
                List<String> tokens = new ArrayList<>(filterOptions.filterTokens);
                query.value(ContentModel.PROP_FIRSTNAME, tokens.get(0)).and()
                        .value(ContentModel.PROP_LASTNAME, tokens.get(1)).or()
                        .value(ContentModel.PROP_FIRSTNAME, tokens.get(1)).and()
                        .value(ContentModel.PROP_LASTNAME, tokens.get(0));
            } else {
                query.value(ContentModel.PROP_FIRSTNAME, filter).or()
                        .value(ContentModel.PROP_LASTNAME, filter);
            }
            query.close();
        }

        return query.query(searchService)
                .stream()
                .map(this::getAuthorityNameRef);
    }

    private boolean filterAuthority(Pair<NodeRef, String> authority,
                                    FilterOptions options,
                                    Map<String, Boolean> inRootGroupCache) {

        String authorityName = authority.getSecond();
        if (StringUtils.isBlank(authorityName)) {
            return false;
        }
        if (options.excludeAuthorities.contains(authorityName)) {
            return false;
        }
        if (!isInRootGroup(authorityName, options.rootGroup, inRootGroupCache)) {
            return false;
        }
        if (!options.showDisabled && authorityIsDisabled(authority)) {
            return false;
        }
        if (groupIsNotAllow(authorityName, options)) {
            return false;
        }
        return true;
    }

    private boolean groupIsNotAllow(String authorityName, FilterOptions options){
        if (!authorityName.startsWith(GROUP_PREFIX)) {
            return false;
        }

        String groupType = orgStructService.getGroupType(authorityName);
        if (StringUtils.isBlank(groupType)) {
            groupType = "group";
        }
        switch (groupType) {
            case "role":
                if (!options.role) {
                    return true;
                }
                break;
            case "user":
                if (!options.user) {
                    return true;
                }
                break;
            case "group":
                if (!options.group) {
                    return true;
                }
                break;
            case "branch":
                if (!options.branch) {
                    return true;
                }
                break;
        }

        return false;
    }

    private boolean authorityIsDisabled(Pair<NodeRef, String> authority) {
        String authorityName = authority.getSecond();

        boolean isDisabled = !authorityName.startsWith(GROUP_PREFIX);
        if (isDisabled) {
            isDisabled = !authenticationService.getAuthenticationEnabled(authorityName);
            isDisabled = isDisabled || PersonUtils.isPersonDisabled(authority.getFirst(), nodeService);
        }

        return isDisabled;
    }

    private Pair<NodeRef, String> getAuthorityNameRef(String name) {
        return new Pair<>(authorityService.getAuthorityNodeRef(name), name);
    }

    private Pair<NodeRef, String> getAuthorityNameRef(NodeRef nodeRef) {
        Map<QName, Serializable> props = nodeService.getProperties(nodeRef);
        String authorityName = (String) props.get(ContentModel.PROP_USERNAME);
        if (authorityName == null) {
            authorityName = (String) props.get(ContentModel.PROP_AUTHORITY_NAME);
        }
        return new Pair<>(nodeRef, authorityName);
    }

    private Boolean isInRootGroup(String authorityName, String rootGroup, Map<String, Boolean> cache) {

        if (StringUtils.isBlank(authorityName)) {
            return false;
        }

        Boolean result = cache.get(authorityName);

        if (result == null) {

            Set<String> containers =
                    authorityService.getContainingAuthorities(AuthorityType.GROUP, authorityName, true);

            result = false;
            if (containers.contains(rootGroup)) {
                result = true;
            } else {
                for (String container : containers) {
                    if (isInRootGroup(container, rootGroup, cache)) {
                        result = true;
                        break;
                    }
                }
            }
            cache.put(authorityName, result);
        }

        return result;
    }

    private RequestParams getRequestParams(WebScriptRequest req) {

        RequestParams params = new RequestParams();
        params.filterOptions = getFilterOptions(req);
        params.groupRef = authorityService.getAuthorityNodeRef(params.filterOptions.rootGroup);
        params.recurse = Boolean.TRUE.toString().equals(req.getParameter(PARAM_RECURSE));

        return params;
    }

    private Set<String> strToSet(String value) {
        if (StringUtils.isNotBlank(value)) {
            String[] arr = value.split(",");
            return new HashSet<>(Arrays.asList(arr));
        }
        return Collections.emptySet();
    }

    private List<String> strToList(String value, String delimiter) {
        if (StringUtils.isNotBlank(value)) {
            String[] arr = value.split(delimiter);
            return new ArrayList<>(Arrays.asList(arr));
        }
        return Collections.emptyList();
    }

    private FilterOptions getFilterOptions(WebScriptRequest req) {

        Map<String, String> templateVars = req.getServiceMatch().getTemplateVars();
        String rootGroupName = templateVars.get(TEMPLATE_PARAM_GROUPNAME);

        if (StringUtils.isBlank(rootGroupName)) {
            throw new AlfrescoRuntimeException("Template parameter " + TEMPLATE_PARAM_GROUPNAME + " is undefined");
        }

        if (!rootGroupName.startsWith(GROUP_PREFIX)) {
            rootGroupName = GROUP_PREFIX + rootGroupName;
        }

        FilterOptions options = new FilterOptions();

        Boolean defaultEnabled = !Boolean.FALSE.toString().equals(req.getParameter(PARAM_DEFAULT));

        options.rootGroup = rootGroupName;
        options.role = ConfigUtils.strToBool(req.getParameter(PARAM_ROLE), defaultEnabled);
        options.user = ConfigUtils.strToBool(req.getParameter(PARAM_USER), defaultEnabled);
        options.group = ConfigUtils.strToBool(req.getParameter(PARAM_GROUP), defaultEnabled);
        options.branch = ConfigUtils.strToBool(req.getParameter(PARAM_BRANCH), defaultEnabled);
        options.limit = DEFAULT_RESULTS_LIMIT;
        options.subTypes = strToSet(req.getParameter(PARAM_SUB_TYPES));
        options.addAdminGroup = ConfigUtils.strToBool(req.getParameter(PARAM_ADD_ADMIN_GROUP), Boolean.FALSE);

        Function<String, String> addStars = str -> {
            if (StringUtils.isNotBlank(str)) {
                if (!str.startsWith("*")) {
                    str = "*" + str;
                }
                if (!str.endsWith("*")) {
                    str = str + "*";
                }
            }
            return str;
        };

        String filter = req.getParameter(PARAM_FILTER);

        options.filter = addStars.apply(filter);

        if (StringUtils.isNotBlank(filter)) {
            options.filterTokens =
                    Arrays.stream(options.filter.split("(?<!\\\\) "))
                            .map(addStars)
                            .collect(Collectors.toSet());
        } else {
            options.filterTokens = Collections.emptySet();
        }

        String currentAuthority = authenticationService.getCurrentUserName();
        options.userIsAdmin = StringUtils.isNotBlank(currentAuthority) &&
                authorityService.isAdminAuthority(currentAuthority);

        options.showDisabled = false;
        Boolean hideInactiveForAll =
                ConfigUtils.strToBool((String) ecosConfigService.getParamValue(CONFIG_KEY_HIDE_INACTIVE_FOR_ALL), null);

        if (hideInactiveForAll == null || !hideInactiveForAll) {
            Boolean showInactiveOnlyForAdmin =
                    ConfigUtils.strToBool((String) ecosConfigService.getParamValue(CONFIG_KEY_SHOW_INACTIVE), null);

            if (showInactiveOnlyForAdmin == null || !showInactiveOnlyForAdmin) {
                options.showDisabled = ConfigUtils.strToBool(req.getParameter(PARAM_SHOW_DISABLED), defaultEnabled);
            } else {
                options.showDisabled = options.userIsAdmin;
            }
        }

        Set<String> excludeAuthorities = new HashSet<>();
        excludeAuthorities.addAll(getExcludedGroups(strToSet(req.getParameter(PARAM_EXCLUDE_AUTHORITIES))));
        excludeAuthorities.addAll(strToSet((String) ecosConfigService.getParamValue(CONFIG_KEY_HIDE_IN_ORGSTRUCT)));

        options.excludeAuthorities = new HashSet<>();
        for (String auth : excludeAuthorities) {
            options.excludeAuthorities.add(auth);
            if (!auth.startsWith(GROUP_PREFIX)) {
                options.excludeAuthorities.add(GROUP_PREFIX + auth);
            }
        }

        return options;
    }

    private List<String> getExcludedGroups(Set<String> rootAuthorities) {
        List<String> excludedChildGroups = new ArrayList<>();
        List<String> excludedMainGroups = new ArrayList<>();

        for (String rootGroupName : rootAuthorities) {
            List<String> parsedLogicList = strToList(rootGroupName, "/");
            if (parsedLogicList.size() == 1) {
                excludedMainGroups.add(rootGroupName);
            } else if (parsedLogicList.size() == 2) {
                String markerElement = Iterables.getLast(parsedLogicList);
                String groupElement = parsedLogicList.get(0);
                if (markerElement.equals("**")) {
                    excludedChildGroups.addAll(authorityService.getContainedAuthorities(AuthorityType.GROUP,
                        groupElement,
                        false));
                } else if (markerElement.equals("*")) {
                    excludedChildGroups.addAll(authorityService.getContainedAuthorities(AuthorityType.GROUP,
                        groupElement,
                        true));
                }
                excludedMainGroups.add(groupElement);
            } else {
                log.error(INVALID_LOGIC_MESSAGE);
            }
        }

        excludedMainGroups.addAll(filterContainingAuthorities(excludedChildGroups
            .stream()
            .distinct()
            .collect(Collectors.toList()), excludedMainGroups));

        return excludedMainGroups;
    }

    private List<String> filterContainingAuthorities(List<String> childAuthorities, List<String> mainAuthorities) {
        List<String> fullAuthoritiesList = new ArrayList<>();
        fullAuthoritiesList.addAll(childAuthorities);
        fullAuthoritiesList.addAll(mainAuthorities);
        List<String> filteredAuthorities = new ArrayList<>(fullAuthoritiesList);
        for (String authority : childAuthorities) {
            Set<String> parentAuthorities = authorityService.getContainingAuthorities(AuthorityType.GROUP,
                authority,
                true)
                .stream()
                .filter(parentGroupStr -> orgStructService.getGroupType(parentGroupStr) != null)
                .collect(Collectors.toSet());
            if (!fullAuthoritiesList.containsAll(parentAuthorities)) {
                filteredAuthorities.remove(authority);
                filteredAuthorities.removeAll(authorityService.getContainedAuthorities(AuthorityType.GROUP,
                    authority,
                    false));
            }
        }
        return filteredAuthorities;
    }

    private static class RequestParams {

        boolean recurse;
        NodeRef groupRef;
        FilterOptions filterOptions;

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            RequestParams that = (RequestParams) o;
            return recurse == that.recurse &&
                    Objects.equals(groupRef, that.groupRef) &&
                    filterOptions.equals(that.filterOptions);
        }

        @Override
        public int hashCode() {
            int result = (recurse ? 1 : 0);
            result = 31 * result + Objects.hashCode(groupRef);
            result = 31 * result + filterOptions.hashCode();
            return result;
        }
    }

    private static class FilterOptions {

        boolean role;
        boolean user;
        boolean group;
        boolean branch;
        boolean showDisabled;
        boolean userIsAdmin;
        boolean addAdminGroup;
        int limit;
        String filter;
        Set<String> filterTokens;
        String rootGroup;
        Set<String> subTypes;
        Set<String> excludeAuthorities;

        @Override
        public boolean equals(Object o) {

            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }

            FilterOptions that = (FilterOptions) o;

            return userIsAdmin == that.userIsAdmin &&
                    branch == that.branch &&
                    role == that.role &&
                    group == that.group &&
                    user == that.user &&
                    showDisabled == that.showDisabled &&
                    limit == that.limit &&
                    Objects.equals(filter, that.filter) &&
                    Objects.equals(rootGroup, that.rootGroup) &&
                    subTypes.equals(that.subTypes) &&
                    excludeAuthorities.equals(that.excludeAuthorities);
        }

        @Override
        public int hashCode() {
            int result = (branch ? 1 : 0);
            result = 31 * result + (role ? 1 : 0);
            result = 31 * result + (group ? 1 : 0);
            result = 31 * result + (user ? 1 : 0);
            result = 31 * result + (showDisabled ? 1 : 0);
            result = 31 * result + (userIsAdmin ? 1 : 0);
            result = 31 * result + (addAdminGroup ? 1 : 0);
            result = 31 * result + Objects.hashCode(rootGroup);
            result = 31 * result + Objects.hashCode(filter);
            result = 31 * result + limit;
            result = 31 * result + subTypes.hashCode();
            result = 31 * result + excludeAuthorities.hashCode();
            return result;
        }
    }

    private static class Authority {

        public String nodeRef;
        public String fullName;
        public String shortName;
        public String displayName;

        public final String authorityType;

        public Authority(String authorityType) {
            this.authorityType = authorityType;
        }
    }

    private static class UserAuthority extends Authority {

        public String firstName;
        public String lastName;
        public String email;

        public boolean available = true;
        public boolean isPersonDisabled = false;

        UserAuthority() {
            super(AuthorityType.USER.toString());
        }

        void setAvailable(Boolean value) {
            if (value != null) {
                this.available = value;
            }
        }

        void setPersonDisabled(Boolean value) {
            if (value != null) {
                this.isPersonDisabled = value;
            }
        }
    }

    private static class GroupAuthority extends Authority {

        public final String groupType;

        GroupAuthority(String type) {
            super(AuthorityType.GROUP.toString());
            groupType = type;
        }

        GroupAuthority() {
            this("group");
        }
    }

    private static class BranchAuthority extends GroupAuthority {

        public String groupSubType;

        BranchAuthority() {
            super("branch");
        }
    }

    private static class RoleAuthority extends GroupAuthority {

        public String groupSubType;
        public boolean roleIsManager;

        RoleAuthority() {
            super("role");
        }
    }
}
