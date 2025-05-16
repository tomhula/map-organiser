<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <title>Region Index</title>
    <style>
        *
        {
            padding: 0;
            margin: 0;
            box-sizing: border-box;
        }

        body
        {
            max-width: 1000px;
            font-size: 7px;
        }

        h1
        {
            margin-bottom: 10px;
            text-align: center;
        }

        .entry
        {
            display: flex;
            justify-content: space-between;
            position: relative;
        }

        .entry::before
        {
            content: "";
            position: absolute;
            top: 75%;
            left: 0;
            right: 0;
            border-bottom: 1px dotted #aaa;
            z-index: 0;
        }

        .topic
        {
            background: white;
            padding-right: 8px;
            position: relative;
            padding-left: 10px;
        }

        .pages
        {
            background: white;
            padding-left: 8px;
            z-index: 1;
        }
    </style>
</head>
<body>
<h1>Rejstřík podle mapy</h1>
<#list mapIndex?keys as map>
    <div class="entry">
        <div class="topic">${map}</div>
        <div class="pages">
            ${mapIndex[map]}
        </div>
    </div>
</#list>
</body>
</html>
