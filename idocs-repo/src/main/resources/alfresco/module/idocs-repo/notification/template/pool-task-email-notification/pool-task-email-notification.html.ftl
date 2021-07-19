<#escape x as x?html>
<html>
<head>
  <style type="text/css"></style>
</head>
<body bgcolor="white">
  <div style="font-size: 14px; margin: 0px 0px 0px 0px; padding-top: 0px; border-top: 0px solid #aaaaaa;"> 
    <p> 
      <#assign taskUrl = web_url + "/v2/dashboard?recordRef=wftask@" + taskId />
      Задача
      <p>
        <a href="${taskUrl}">${taskUrl}</a> 
      </p>
      передана в пул.<br>
    </p> 
  </div>
</body>
</html>
</#escape>