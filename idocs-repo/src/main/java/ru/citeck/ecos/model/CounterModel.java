/*
 * Copyright (C) 2008-2015 Citeck LLC.
 *
 * This file is part of Citeck EcoS
 *
 * Citeck EcoS is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Citeck EcoS is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with Citeck EcoS. If not, see <http://www.gnu.org/licenses/>.
 */
package ru.citeck.ecos.model;

import org.alfresco.service.namespace.QName;

public interface CounterModel {

	public static final String NAMESPACE = "http://www.citeck.ru/model/counter/1.0";

	public static final QName TYPE_COUNTER = QName.createQName(NAMESPACE, "counter");
	public static final QName PROP_VALUE = QName.createQName(NAMESPACE, "value");

	public static final QName TYPE_AUTONUMBER_TEMPLATE = QName.createQName(NAMESPACE, "autonumberTemplate");
	public static final QName PROP_COMMON_TEMPLATE = QName.createQName(NAMESPACE, "commonTemplate");
	public static final QName PROP_COUNTER_TEMPLATE = QName.createQName(NAMESPACE, "counterTemplate");
	public static final QName PROP_INITIAL_VALUE_TEMPLATE = QName.createQName(NAMESPACE, "initialValueTemplate");
	public static final QName PROP_NUMBER_TEMPLATE = QName.createQName(NAMESPACE, "numberTemplate");
	public static final QName PROP_ERROR_MESSAGE_CONFIG = QName.createQName(NAMESPACE, "errorMessageConfig");
	public static final QName PROP_CHILDREN_COUNT_VALUE = QName.createQName(NAMESPACE, "childrenCountValue");

	public static final QName PROP_EMODEL_NUM_COUNTER_ID = QName.createQName(NAMESPACE, "emodelNumCounterId");
}
