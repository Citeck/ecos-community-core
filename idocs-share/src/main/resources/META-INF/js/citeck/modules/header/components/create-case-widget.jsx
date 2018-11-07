import React from 'react';
import { connect } from 'react-redux';
import { Dropdown } from 'react-bootstrap';
import CustomToggle from './dropdown-menu-custom-toggle';
import DropDownMenuGroup from './dropdown-menu-group';
import DropdownMenuCascade from './dropdown-menu-cascade';

const CreateCaseWidget = ({ items, isCascade }) => {
    let menuListItems = null;
    if (Array.isArray(items) && items.length > 0) {
        menuListItems = items.map((item, key) => {
            return isCascade ? (
                <DropdownMenuCascade key={key} label={item.label} items={item.items} id={item.id} />
            ) : (
                <DropDownMenuGroup key={key} label={item.label} items={item.items} id={item.id} />
            );
        });
    }

    let dropdownMenuClasses = ['custom-dropdown-menu__body'];
    if (isCascade) {
        dropdownMenuClasses.push('cascade');
    }

    return (
        <div id='HEADER_CREATE_CASE'>
            <Dropdown className='custom-dropdown-menu' pullLeft>
                <CustomToggle bsRole='toggle' className='create-case-dropdown-menu__toggle custom-dropdown-menu__toggle'>
                    <i className={'fa fa-plus'} />
                </CustomToggle>
                <Dropdown.Menu bsRole='menu' className={dropdownMenuClasses.join(' ')} id='HEADER_CREATE_CASE__DROPDOWN'>
                    {menuListItems}
                </Dropdown.Menu>
            </Dropdown>
        </div>
    )
};

const mapStateToProps = (state) => ({
    items: state.caseMenu.items,
    isCascade: state.caseMenu.isCascade,
});

export default connect(mapStateToProps)(CreateCaseWidget);
