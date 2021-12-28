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
package ru.citeck.ecos.workflow.confirm;

import org.activiti.engine.delegate.DelegateExecution;
import org.activiti.engine.delegate.Expression;
import org.alfresco.service.cmr.repository.NodeRef;
import org.apache.commons.lang.StringUtils;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import ru.citeck.ecos.workflow.listeners.AbstractExecutionListener;

import java.util.*;

public class PrecedenceToJsonListener extends AbstractExecutionListener {

    public static final String FIELD_STAGES = "stages";
    public static final String FIELD_CONFIRMERS = "confirmers";
    public static final String FIELD_NODEREF = "nodeRef";
    public static final String FIELD_DUEDATE = "dueDate";
    public static final String FIELD_AMOUNT_HOURS = "amountHours";
    public static final String SPLITTER = "_";

    private Expression var;
    private Expression precedence;

    @Override
    protected void notifyImpl(DelegateExecution execution) throws Exception {
        String variableName = (String) var.getValue(execution);
        String precedenceLine = (String) precedence.getValue(execution);
        execution.setVariable(variableName, convertPrecedence(precedenceLine));
    }

    @SuppressWarnings("unchecked")
    public static JSONObject convertPrecedence(String precedence) {
        JSONObject result = new JSONObject();
        JSONArray stages = new JSONArray();
        result.put(FIELD_STAGES, stages);
        if (precedence != null && !precedence.isEmpty()) {
            String[] stageLines = precedence.split("[,]");
            for (String line : stageLines) {
                JSONObject stage = new JSONObject();

                // due date is not supported in old format
                stage.put(FIELD_DUEDATE, 0L);
                JSONArray confirmers = new JSONArray();
                stage.put(FIELD_CONFIRMERS, confirmers);
                String[] confirmLines = line.split("[|]");
                for (String confirmer : confirmLines) {
                    if (!confirmer.isEmpty()) {
                        JSONObject conf = new JSONObject();
                        String[] splittedStrings = confirmer.split(SPLITTER);
                        String ref = splittedStrings[0];
                        String timePeriod = "";
                        if (splittedStrings.length > 2) {
                            ref = composeRef(splittedStrings);
                            timePeriod = splittedStrings[splittedStrings.length - 1];
                        }
                        conf.put(FIELD_NODEREF, ref);
                        // full name is not supported in old format
                        conf.put("fullName", ref);
                        // 'can cancel' is not supported in old format
                        conf.put("canCancel", false);
                        if (splittedStrings.length > 1) {
                            if (StringUtils.isBlank(timePeriod)) {
                                timePeriod = splittedStrings[1];
                            }
                            conf.put(FIELD_AMOUNT_HOURS, getNumberOfHoursForStage(timePeriod));
                        }
                        confirmers.add(conf);
                    }
                }
                stages.add(stage);
            }
        }
        return result;
    }

    public static List<Stage> getStages(String precedence) {

        List<Stage> stages = new ArrayList<>();

        if (StringUtils.isBlank(precedence)) {
            return stages;
        }

        JSONObject parsed = convertPrecedence(precedence);
        JSONArray precStages = (JSONArray) parsed.get(PrecedenceToJsonListener.FIELD_STAGES);

        if (precStages == null) {
            return stages;
        }

        for (Object stageObj : precStages) {

            JSONObject precStage = (JSONObject) stageObj;
            JSONArray precConfirmers = (JSONArray) precStage.get(PrecedenceToJsonListener.FIELD_CONFIRMERS);

            if (precConfirmers != null) {

                Stage stage = new Stage();

                for (Object confirmerObj : precConfirmers) {

                    JSONObject precConfirmer = (JSONObject) confirmerObj;
                    String nodeRef = (String) precConfirmer.get(PrecedenceToJsonListener.FIELD_NODEREF);
                    if (nodeRef != null && NodeRef.isNodeRef(nodeRef)) {
                        stage.participants.add(new NodeRef(nodeRef));
                    }

                    Double hours = (Double) precConfirmer.get(FIELD_AMOUNT_HOURS);
                    if (hours != null && hours > 0) {
                        stage.hours = hours;
                    }
                }
                stages.add(stage);
            }
        }

        return stages;
    }

    public static Set<NodeRef> getAllConfirmers(String precedence) {

        if (StringUtils.isBlank(precedence)) {
            return Collections.emptySet();
        }

        Set<NodeRef> authorities = new HashSet<>();
        for (Stage stage : getStages(precedence)) {
            authorities.addAll(stage.participants);
        }
        return authorities;
    }

    private static double getNumberOfHoursForStage(String timeStage) {
        String time = timeStage.split("/")[0];
        String timeType = timeStage.split("/")[1];
        if ("m".equals(timeType)) {
            return (double) Math.round(Double.parseDouble(time) * 30 * 24 * 1000) / 1000;
        } else if ("d".equals(timeType)) {
            return (double) Math.round(Double.parseDouble(time) * 24 * 1000) / 1000;
        } else {
            return Double.parseDouble(time);
        }
    }

    private static String composeRef(String[] splittedStrings) {
        StringBuilder stringBuilder = new StringBuilder(splittedStrings[0]);
        for (int i = 1; i < splittedStrings.length - 1; i++) {
            stringBuilder.append(SPLITTER);
            stringBuilder.append(splittedStrings[i]);
        }
        return stringBuilder.toString();
    }

    public static class Stage {
        public List<NodeRef> participants = new ArrayList<>();
        public Double hours;
    }
}
