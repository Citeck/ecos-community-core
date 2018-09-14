import React from 'react';
import { Dropdown } from 'react-bootstrap';
import CustomToggle from 'js/citeck/modules/header/components/dropdown-menu-custom-toggle';
import DropDownMenuGroup from 'js/citeck/modules/header/components/dropdown-menu-group';

const CreateCaseWidget = ({ items }) => {
    const menuListItems = items && items.length > 0 ? items.map((item, key) => {
        return (
            <DropDownMenuGroup
                key={key}
                label={item.label}
                items={item.items}
            />
        );
    }) : null;

    return (
        <div id='HEADER_CREATE_CASE'>
            <Dropdown className="custom-dropdown-menu" pullLeft>
                <CustomToggle bsRole="toggle" className="create-case-dropdown-menu__toggle custom-dropdown-menu__toggle">
                    <i className={"fa fa-plus"} />
                </CustomToggle>
                <Dropdown.Menu className="custom-dropdown-menu__body">
                    {menuListItems}
                </Dropdown.Menu>
            </Dropdown>
        </div>
    )
};

export default CreateCaseWidget;