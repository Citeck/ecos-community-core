import React, {Component} from 'ecosui!react';
import BootstrapTable from 'js/citeck/lib/react-bootstrap-table-next/react-bootstrap-table-next';
import filterFactory, { textFilter, dateFilter } from 'js/citeck/lib/react-bootstrap-table-next/react-bootstrap-table2-filter';

import 'xstyle!js/citeck/lib/react-bootstrap-table-next/react-bootstrap-table.css';
import 'xstyle!js/citeck/lib/react-bootstrap-table-next/react-bootstrap-table2-filter.css';
import 'xstyle!js/citeck/modules/grid/grid.css';

export default class Grid extends Component  {
    _setFilter(column){
        if(!column.filter){
            switch (column.type) {
                case 'date':
                    column.filter = dateFilter({
                        placeholder: ' '
                    });
                    break;
                default:
                    column.filter = textFilter({
                        placeholder: ' '
                    });
                    break;
            }
        }

        return column;
    }

    _setWidth(column){
        column.style = {
            ...column.style,
            width: column.width
        };

        return column;
    }

    _setAdditionalOptions(props){
        props.columns = props.columns.map(column => {
            if(props.filter) {
                column = this._setFilter(column);
            }

            if(column.width) {
                column = this._setWidth(column);
            }

            return column;
        });

        return props;
    }

    render() {
        let props = {
            ...this.props,
            noDataIndication: () => 'Нет элементов в списке',
            classes: 'table_table-layout_auto table_header-nowrap',
            bootstrap4: true,
            bordered: false,
            filter: filterFactory(),
            // rowEvents: {
            //     onMouseEnter: e => {
            //         let $currentTr = $(e.target).closest('tr').addClass('tr_overlay-container');
            //
            //         $('<div class="tr_overlay">test</div>').appendTo($currentTr).mouseleave(function() {
            //             $currentTr.removeClass('tr_overlay-container');
            //             $(this).remove();
            //         });
            //     }
            // }
        };

        props = this._setAdditionalOptions(props);

        return <BootstrapTable {...props}/>;
    }
}
