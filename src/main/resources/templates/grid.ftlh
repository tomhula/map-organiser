<#-- @ftlvariable name="events" type="java.util.List<io.github.tomhula.GridEvent>" -->
<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>Event Grid</title>
    <style>
        *
        {
            padding: 0;
            margin: 0;
            box-sizing: border-box;
        }

        .grid-container
        {
            display: grid;
            grid-template-columns: repeat(3, 500px); /* 3 columns */
            gap: 30px;
        }

        .cell-container
        {
            display: grid;
            grid-template-columns: 100px 1fr;
            grid-template-rows: repeat(3, min-content);
            grid-gap: 0;
        }

        .top { grid-area: 1 / 1 / 2 / 2; }
        .center { grid-area: 2 / 1 / 3 / 2; }
        .bottom { grid-area: 3 / 1 / 4 / 3; }
        .right { grid-area: 2 / 2 / 3 / 3; }

        .qr-code
        {
            width: 100%;
        }

        .top
        {
            display: flex;
            justify-content: center;
            align-items: center;
        }

        .number
        {
            font-weight: bold;
            font-size: 30px;
            
            margin-bottom: 5px;
        }

        .map-name
        {
            font-size: 15px;
            white-space: nowrap;
            overflow: hidden;
            text-overflow: ellipsis;
            
            margin-top: 15px;
        }

        .meta
        {
            display: flex;
            flex-direction: column;
            row-gap: 10px;
            height: 100%;

            margin-left: 20px;
            font-size: 18px;
        }
    </style>
</head>
<body>
<div class="grid-container">
    <#list events as gridEvent>
        <#assign number = gridEvent?index + 1>

        <div class="cell-container">
            <div class="top">
                <p class="number">${number}</p>
            </div>
            <div class="center">
                <img class="qr-code" src="${gridEvent.qrCodeUrl}" alt="QR Code">
            </div>
            <div class="bottom">
                <p class="map-name">${gridEvent.map!"Neznámá mapa"}</p>
            </div>
            <div class="right">
                <div class="meta">
                    <p>${gridEvent.date}</p>
                    <p>${gridEvent.name}</p>
                    <p>${gridEvent.place!"Neznámé místo"}</p>
                </div>
            </div>
        </div>
    </#list>
</div>
</body>
</html>
