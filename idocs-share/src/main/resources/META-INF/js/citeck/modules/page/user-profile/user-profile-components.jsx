import React, { useContext, useState, useEffect } from "ecosui!react";
import SurfRegion from "../../surf/surf-region";
import { fetchAllDocumentsNodeRefs, getFolderNodeRef, getTempFolderNodeRef } from './user-profile-api';
import { UserProfileContext } from './user-profile-context';
import { DOCUMENTS_TAB, setCurrentTabUrl } from './user-profile-util';

export const EditUserForm = () => {
    const context = useContext(UserProfileContext);
    const { userRef, userName, mode, canWrite, theme } = context.rootProps;

    let className = 'user-profile__edit-form_mode-edit';
    let editLink = null;

    if (mode === "view") {
        className = 'user-profile__edit-form_mode-view';

        if (canWrite) {
            editLink = (
                <a className="user-profile__edit-link" href={'/share/page/user/' + userName + '/profile?mode=edit'}>
                    {Alfresco.util.message('user-profile.edit-link.label')}
                </a>
            );
        }
    }

    return (
        <div className={className}>
            <SurfRegion
                args={{
                    regionId: mode === 'edit' ? 'node-edit' : 'node-view',
                    scope: 'page',
                    pageid: 'user-profile',
                    nodeRef: userRef,
                    htmlid: 'cardlet-remote-node-view',
                    mode: mode,
                    theme: theme,
                    // cacheAge: 600
                }}
            />
            {editLink}
        </div>
    );
};

export const Documents = () => {
    const context = useContext(UserProfileContext);
    const { userRef, theme } = context.rootProps;

    const [folderNodeRef, setFolderNodeRef] = useState(null);

    useEffect(() => {
        getFolderNodeRef(userRef).then(nodeRef => {
            if (nodeRef) {
                setFolderNodeRef(nodeRef);
            } else {
                getTempFolderNodeRef().then(tempNodeRef => {
                    if (tempNodeRef) {
                        setFolderNodeRef(tempNodeRef);
                    }
                })
            }
        });
    }, [userRef]);

    if (!folderNodeRef) {
        return null;
    }

    const onPrintAll = () => {
        fetchAllDocumentsNodeRefs(folderNodeRef).then(nodeRefs => {
            // console.log('onPrintAll nodeRefs', nodeRefs);
        })
    };

    const onDownloadAll = () => {
        fetchAllDocumentsNodeRefs(folderNodeRef).then(nodeRefs => {
            require(['components/download/archive-and-download'], function() {
                let downloadDialog = Alfresco.getArchiveAndDownloadInstance();
                const config = { nodesToArchive: [] };

                for (let i = 0; i < nodeRefs.length; i++) {
                    config.nodesToArchive.push({"nodeRef": nodeRefs[i]})
                }

                downloadDialog.show(config);
            });
        })
    };

    return (
        <div className='user-profile-documents-tab'>
            <SurfRegion
                args={{
                    regionId: 'archive-and-download',
                    scope: 'template',
                    pageid: 'card-details',
                    theme: theme,
                    cacheAge: 600
                }}
            />

            <div className='user-profile-documents-tab__case-documents'>
                <div className='user-profile-documents-tab__case-documents-buttons'>
                    <button
                        onClick={onPrintAll}
                        className='user-profile-documents-tab__case-documents-button'
                    >
                        {Alfresco.util.message('user-profile.documents.print-all-button.label')}
                    </button>
                    <button
                        onClick={onDownloadAll}
                        className='user-profile-documents-tab__case-documents-button'
                    >
                        {Alfresco.util.message('user-profile.documents.download-all-button.label')}
                    </button>
                </div>
                <SurfRegion
                    args={{
                        regionId: 'case-documents',
                        scope: 'page',
                        pageid: 'card-details',
                        nodeRef: folderNodeRef,
                        htmlid: 'cardlet-remote-case-documents',
                        theme: theme,
                        cacheAge: 600
                    }}
                />
            </div>
            <div className='user-profile-documents-tab__case-levels'>
                <SurfRegion
                    args={{
                        regionId: 'case-levels',
                        scope: 'page',
                        pageid: 'card-details',
                        nodeRef: folderNodeRef,
                        htmlid: 'cardlet-remote-case-levels',
                        theme: theme,
                        cacheAge: 600,
                    }}
                />
            </div>
            <div className='user-profile-documents-tab__advanced-associations'>
                <SurfRegion
                    args={{
                        regionId: 'advanced-associations',
                        scope: 'page',
                        pageid: 'card-details',
                        nodeRef: folderNodeRef,
                        htmlid: 'cardlet-remote-advanced-associations',
                        theme: theme,
                        cacheAge: 600,
                    }}
                />
            </div>
        </div>
    );
};

export const Tab = (props) => {
    const context = useContext(UserProfileContext);

    const onClick = () => {
        context.setCurrentTab(props.id);
        setCurrentTabUrl(props.id);
    };

    let className = "header-tab";
    if (props.isActive) {
        className += " current";
    }

    return (
        <span className={className}>
            <a onClick={onClick}>{props.title}</a>
        </span>
    );
};

export const UserProfileRoot = ({ el }) => {
    const context = useContext(UserProfileContext);

    return (
        <div id={`${el}-body`} className="user-profile node-view static user-profile-wrapper">
            <div id="profile-view">
                <div id="card-details-tabs" className="header-tabs">
                    {context.tabs.map(tab => {
                        return <Tab key={`card-mode-link-${tab.id}`} {...tab} />
                    })}
                </div>
                {context.currentTab === DOCUMENTS_TAB ? <Documents /> : <EditUserForm />}
            </div>
        </div>
    );
};
