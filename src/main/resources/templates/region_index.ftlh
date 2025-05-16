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
            font-size: 8px;
        }

        h2
        {
            font-size: 1.2em;
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
            margin-left: 10px;
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
<#list regionIndex?keys as section>
    <h2>${section!"Neznámé"}</h2>
    <#assign topics = regionIndex[section]>
    <#list topics?keys as topic>
        <div class="entry">
            <div class="topic">${topic!"Neznámé"}</div>
            <div class="pages">
                ${topics[topic]?join(", ")}
            </div>
        </div>
    </#list>
</#list>
</body>
</html>
