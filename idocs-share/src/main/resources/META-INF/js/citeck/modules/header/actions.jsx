import { makeSiteMenuItems, makeUserMenuItems, t } from './misc/util'


/* MODAL */
export const SHOW_MODAL = 'SHOW_MODAL';
export const HIDE_MODAL = 'HIDE_MODAL';

export function showModal(payload) {
    return {
        type: SHOW_MODAL,
        payload
    }
}

export function hideModal() {
    return {
        type: HIDE_MODAL
    }
}
/* ---------------- */



/* Create case menu */
export const CREATE_CASE_WIDGET_SET_ITEMS = 'CREATE_CASE_WIDGET_SET_ITEMS';

export function setCreateCaseWidgetItems(payload) {
    return {
        type: CREATE_CASE_WIDGET_SET_ITEMS,
        payload
    }
}
/* ---------------- */



/* User */
export const USER_SET_NAME = 'USER_SET_NAME';
export const USER_SET_FULLNAME = 'USER_SET_FULLNAME';
export const USER_SET_NODE_REF = 'USER_SET_NODE_REF';
export const USER_SET_IS_ADMIN = 'USER_SET_IS_ADMIN';
export const USER_SET_IS_AVAILABLE = 'USER_SET_IS_AVAILABLE';
export const USER_SET_PHOTO = 'USER_SET_PHOTO';


export function setUserName(payload) {
    return {
        type: USER_SET_NAME,
        payload
    }
}

export function setUserFullName(payload) {
    return {
        type: USER_SET_FULLNAME,
        payload
    }
}

export function setUserNodeRef(payload) {
    return {
        type: USER_SET_NODE_REF,
        payload
    }
}

export function setUserIsAdmin(payload) {
    return {
        type: USER_SET_IS_ADMIN,
        payload
    }
}

export function setUserIsAvailable(payload) {
    return {
        type: USER_SET_IS_AVAILABLE,
        payload
    }
}

export function setUserPhoto(payload) {
    return {
        type: USER_SET_PHOTO,
        payload
    }
}
/* ---------------- */



/* User menu */
export const USER_MENU_SET_ITEMS = 'USER_MENU_SET_ITEMS';

export function setUserMenuItems(payload) {
    return {
        type: USER_MENU_SET_ITEMS,
        payload
    }
}

export function loadUserMenuPhoto(userNodeRef) {
    return (dispatch, getState, api) => {
        if (!userNodeRef) {
            return;
        }

        api.getPhotoSize(userNodeRef).then(size => {
            if (size > 0) {
                let photoUrl = window.Alfresco.constants.PROXY_URI + "api/node/content;ecos:photo/" + userNodeRef.replace(":/", "") + "/image.jpg";
                dispatch(setUserPhoto(photoUrl));
            }
        });
    }
}
/* ---------------- */



/* Site menu */
export const SITE_MENU_SET_CURRENT_SITE_ID = 'SITE_MENU_SET_CURRENT_SITE_ID';
export const SITE_MENU_SET_SITE_MENU_ITEMS = 'SITE_MENU_SET_SITE_MENU_ITEMS';

export function setCurrentSiteId(payload) {
    return {
        type: SITE_MENU_SET_CURRENT_SITE_ID,
        payload
    }
}

export function setCurrentSiteMenuItems(payload) {
    return {
        type: SITE_MENU_SET_SITE_MENU_ITEMS,
        payload
    }
}
/* ---------------- */




/* COMMON */

// TODO
// export function loadTopMenuData(siteId, userName) {
//     fetch('someUrl').then(result => {
//         dispatch(setCreateCaseWidgetItems(result.createCaseMenu));
//         dispatch(setCurrentSiteData({ items: result.siteMenu }));
//         dispatch(setUserMenuItems(result.userMenu));
//     });
// }

export function loadTopMenuData(siteId, userName, userIsAvailable) {
    return (dispatch, getState, api) => {
        let promises = [];

        let allSites = [];
        const getCreateCaseMenuDataRequest = api.getSitesForUser(userName).then(sites => {
            let promises = [];
            for (let site of sites) {
                allSites.push(site);
                promises.push(new Promise((resolve, reject) => {
                    api.getCreateVariantsForSite(site.shortName).then(variants => {
                        resolve(variants)
                    })
                }))
            }

            return Promise.all(promises);
        }).then(variants => {
            let menuItems = [];
            menuItems.push(
                {
                    id: "HEADER_CREATE_WORKFLOW",
                    label: "header.create-workflow.label",
                    items: [
                        {
                            id: "HEADER_CREATE_WORKFLOW_ADHOC",
                            label: "header.create-workflow-adhoc.label",
                            targetUrl: "/share/page/start-specified-workflow?workflowId=activiti$perform"
                        },
                        {
                            id: "HEADER_CREATE_WORKFLOW_CONFIRM",
                            label: "header.create-workflow-confirm.label",
                            targetUrl: "/share/page/start-specified-workflow?workflowId=activiti$confirm"
                        },
                    ],
                },
            );

            for (let i in allSites) {
                let createVariants = [];
                for (let variant of variants[i]) {
                    createVariants.push({
                        id: "HEADER_" + ((allSites[i].shortName + "_" + variant.type).replace(/\-/g, "_")).toUpperCase(),
                        label: variant.title,
                        targetUrl: "/share/page/node-create?type=" + variant.type + "&viewId=" + variant.formId + "&destination=" + variant.destination
                    });
                }
                const siteId = "HEADER_" + (allSites[i].shortName.replace(/\-/g, "_")).toUpperCase();
                menuItems.push({
                    id: siteId,
                    label: allSites[i].title,
                    items: createVariants
                });
            }

            return menuItems;
        });

        let getSiteDataRequest = Promise.resolve([]); // temporary hack
        if (siteId) {
            const defaultSiteData = {
                id: '',
                profile: {
                    title: "",
                    shortName: "",
                    visibility: "PRIVATE",
                },

                userIsSiteManager: false,
                userIsMember: false,
                userIsDirectMember: false
            };

            getSiteDataRequest = api.getSiteData(siteId).then(profile => {
                return { ...defaultSiteData, profile };
            }).then(currentSiteData => {
                return api.getSiteUserMembership(siteId, userName).then(result => {
                    return {
                        ...currentSiteData,
                        userIsMember: true,
                        userIsDirectMember: !(result.isMemberOfGroup),
                        userIsSiteManager: result.role === "SiteManager"
                    }
                });
            }).then(currentSiteData => {
                const state = getState();
                const user = state.user;

                return makeSiteMenuItems(user, { ...currentSiteData, siteId });
            }).catch(() => []);
        }

        promises.push(getCreateCaseMenuDataRequest, getSiteDataRequest);

        Promise.all(promises).then(([createCaseMenu, siteMenu]) => {
            return {
                'createCaseMenu': createCaseMenu,
                'siteMenu': siteMenu,
                'userMenu': makeUserMenuItems(userName, userIsAvailable),
            };
        }).then(result => {
            dispatch(setCreateCaseWidgetItems(result.createCaseMenu));
            dispatch(setCurrentSiteMenuItems(result.siteMenu));
            dispatch(setUserMenuItems(result.userMenu));
        });
    }
}

export function leaveSiteRequest({ site, siteTitle, user, userFullName }) {
    return (dispatch, getState, api) => {
        const url = Alfresco.constants.PROXY_URI + `api/sites/${encodeURIComponent(site)}/memberships/${encodeURIComponent(user)}`;
        return fetch(url, {
            method: "DELETE"
        }).then(resp => {
            if (resp.status !== 200) {
                return dispatch(showModal({
                    content: t("message.leave-failure", {"0": userFullName, "1": siteTitle}),
                    buttons: [
                        {
                            label: t('button.close-modal'),
                            isCloseButton: true
                        }
                    ]
                }));
            }

            dispatch(showModal({
                content: t("message.leaving", {"0": userFullName, "1": siteTitle})
            }));

            window.location.href = Alfresco.constants.URL_PAGECONTEXT + "user/" + encodeURIComponent(user) + "/dashboard";
        }).catch(err => {
            console.log('error', err);
        });
    }
}
