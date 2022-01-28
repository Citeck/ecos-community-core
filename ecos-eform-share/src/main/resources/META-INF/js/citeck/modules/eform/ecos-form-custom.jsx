
import Records from 'ecosui!ecos-records';
import React from 'ecosui!react';
import ReactDOM from 'ecosui!react-dom';
import EcosFormLocaleEditor from 'ecosui!eform-locale-editor';
import EcosFormBuilder from 'ecosui!eform-builder';

let formPanelIdx = 0;
let editors = {};

export default class EcosFormCustom {

    constructor(params) {

        this.form = params.form;
        this.recordId = params.recordId;

        let formId = 'eform-editor-form-panel' + formPanelIdx++;

        this.contentId = formId + '-content';
    }

    init() {
        Records.get(this.recordId).load(['i18n?json', 'definition?json'], true);
    }

    isSystemForm() {
        return ['form@DEFAULT', 'form@ECOS_FORM'].indexOf(this.recordId) >= 0;
    }

    showFormEditor() {

        let self = this;
        let record = Records.get(self.recordId);

        let processFormDefinition = function (loadedFormDefinition) {
            let onSubmit = formDefinition => record.att("definition?json", formDefinition);
            self._showEditorComponent("formBuilder", EcosFormBuilder, loadedFormDefinition, onSubmit);
        };

        let defAtts = {
            definition: 'definition?json'
        };

        Records.get(self.recordId).load(defAtts).then(data => {

            if (!data.definition) {
                Records.get('eform@DEFAULT').load(defAtts).then(data => {
                    processFormDefinition(data.definition);
                });
            } else {
                processFormDefinition(data.definition);
            }
        });
    }

    showLocaleEditor() {

        let self = this;
        let record = Records.get(self.recordId);

        record.load('i18n?json').then(i18n => {
            let onSubmit = i18n => record.att("i18n?json", i18n);
            self._showEditorComponent("localeEditor", EcosFormLocaleEditor, i18n, onSubmit);
        });
    }

    _showEditorComponent(componentKey, component, showData, onSubmit) {
        let self = this;

        if (!editors[componentKey]) {

            let editor = React.createElement(component);

            let container = document.createElement('div');
            container.id = self.contentId + "-" + componentKey;
            document.body.appendChild(container);

            editors[componentKey] = ReactDOM.render(editor, container);
        }

        editors[componentKey].show(showData, onSubmit);
    }
}
