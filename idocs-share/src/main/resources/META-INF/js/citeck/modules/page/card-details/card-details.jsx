import React from "ecosui!react";
import ReactDOM from "ecosui!react-dom";
import {utils as CiteckUtils} from 'js/citeck/modules/utils/citeck';
import 'citeck/mobile/mobile';
import 'underscore'
import CardDetails from './components';
import { Provider } from 'react-redux';
import { createStore, applyMiddleware } from 'redux';
import thunk from 'js/citeck/lib/redux-thunk';
import 'js/citeck/modules/utils/yui-panel-lazy-patch';

import {
    rootReducer,
    registerReducers
} from './reducers';

import {
    setCardMode,
    setPageArgs,
    fetchCardlets,
    fetchNodeInfo,
    fetchStartMessage
} from "./actions";

require("xstyle!./card-details.css");

const DEFAULT_CARD_MODE = "default";
const SHOW_MESSAGE_PARAM_NAME = "showStartMsg";
const FORCE_OLD_CARD_DETAILS_PARAM_NAME = "forceOld";

const store = createStore(
    rootReducer,
    applyMiddleware(thunk)
);

window.__REDUX_STORE__ = store;

function getCurrentCardMode() {
    return CiteckUtils.getURLParameterByName("mode") || DEFAULT_CARD_MODE;
}

function CardDetailsRoot(props) {
    return <Provider store={store}>
        <CardDetails {...props} />
    </Provider>;
}

export function renderPage (elementId, props) {

    let forceOld = CiteckUtils.getURLParameterByName(FORCE_OLD_CARD_DETAILS_PARAM_NAME) === 'true';
    if (!forceOld && (props.nodeBaseInfo || {}).isOldCardDetailsRequired !== true) {
        window.location = "/v2/dashboard?recordRef=" + props.pageArgs.nodeRef;
        return;
    }

    store.dispatch(setPageArgs(props.pageArgs));

    let promises = [];

    promises.push(
        store.dispatch(fetchNodeInfo(props.pageArgs.nodeRef))
    );

    promises.push(
        store.dispatch(fetchCardlets(props.pageArgs.nodeRef)).then(() => {
            store.dispatch(setCardMode(getCurrentCardMode(), registerReducers));
        })
    );

    if (CiteckUtils.getURLParameterByName(SHOW_MESSAGE_PARAM_NAME) === 'true') {
        promises.push(store.dispatch(fetchStartMessage(props.pageArgs.nodeRef)));
    }

    Promise.all(promises).then(() => {

        window.__CARD_DETAILS_START = new Date().getTime();

        window.onpopstate = function() {
            store.dispatch(setCardMode(getCurrentCardMode(), registerReducers));
        };

        YAHOO.Bubbling.on('metadataRefresh', () => {
            store.dispatch(fetchNodeInfo(props.pageArgs.nodeRef));
        });

        ReactDOM.render(React.createElement(CardDetailsRoot, props), document.getElementById(elementId));
    });
}
