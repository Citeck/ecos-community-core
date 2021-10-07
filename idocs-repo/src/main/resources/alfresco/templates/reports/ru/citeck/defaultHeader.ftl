<!DOCTYPE html PUBLIC "-//W3C//DTD XHTML 1.0 Transitional//EN" "http://www.w3.org/TR/xhtml1/DTD/xhtml1-transitional.dtd">
<html xmlns="http://www.w3.org/1999/xhtml" dir="ltr" lang="ru-ru" xml:lang="ru-ru">
<head>
    <title><#if reportTitle??>${reportTitle}</#if></title>
    <meta http-equiv="Content-Type" content="text/html; charset=UTF-8" />
    <style type="text/css">
        body {
            text-align: center;
            font: 9pt Arial, Tahoma, Verdana;
        }

        table.report {
            border: 1px solid #bbb !important;
            border-collapse: collapse !important;
            border-spacing: 0 !important;
            width: 96%;
            margin: 10px 2% 10px 2%;
            table-layout: auto;
        }

        table.report td {
            border: 1px solid #bbb;
            font: 9pt Arial, Tahoma, Verdana;
            padding: 5px;
            text-align: left;
            width: auto !important;
        }

        table.report td.colheader {
            font-weight: bold;
            text-align: center;
            background-color: #ddd;
        }

        table.report td.even {
            background-color: #f9f9f9;
        }

        table.report td.integer {
            text-align: right;
            width: 1%;
        }

    </style>
</head>
<body>
<#if reportTitle??>
    <h2>${reportTitle}</h2>
</#if>

<#if columnTitles??>
<table class="report" border="1">
    <tr>
        <#list columnTitles as columnTitle>
            <td class="colheader">${columnTitle}</th>
        </#list>
    </tr>
    </#if>
