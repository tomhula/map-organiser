<#-- @ftlvariable name="events" type="java.util.List<io.github.tomhula.EventWithQrCode>" -->
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
            grid-template-columns: repeat(3, 1fr); /* 3 columns */
            grid-template-rows: repeat(4, 1fr);    /* 4 rows */
            gap: 5mm;
        }

        .cell-container
        {
            display: grid;
            grid-template-columns: 150px 1fr;
            grid-template-rows: 1fr 150px 1fr;
            grid-column-gap: 0;
            grid-row-gap: 0;
        }
        .top
        {
            grid-area: 1 / 1 / 2 / 2;
        }
        .center
        {
            grid-area: 2 / 1 / 3 / 2;
        }
        .bottom
        {
            grid-area: 3 / 1 / 4 / 3;
        }
        .right
        {
            grid-area: 2 / 2 / 3 / 3;
        }
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
            font-size: 20px;
        }
        .map-name
        {
            font-size: 20px;
        }
        .meta
        {
            display: flex;
            flex-direction: column;
            row-gap: 10px;
        }
        .meta p
        {
            text-overflow: ellipsis;
            overflow: hidden;
            white-space: nowrap;
        }
    </style>
</head>
<body>
    <div class="grid-container">
        <#list events as eventWithQrCodeUrl>
            <#assign event = eventWithQrCodeUrl.event>
            <#assign qrCodeUrl = eventWithQrCodeUrl.qrCodeUrl>
            <#assign number = eventWithQrCodeUrl?index + 1>
        
            <div class="cell-wrapper">
                <div class="cell-container">
                    <div class="cell-container">
                        <div class="top">
                            <p class="number">${number}</p>
                        </div>
                        <div class="center">
                            <img class="qr-code" src="${qrCodeUrl}" alt="QR Code">
                        </div>
                        <div class="bottom">
                            <p class="map-name">${event.name}</p>
                        </div>
                        <div class="right">
                            <div class="meta">
                                <p>${event.date}</p>
                                <p>${event.name}</p>
                                <p>${(event.place)!"Unknown place"}</p>
                            </div>
                        </div>
                    </div>
                </div>
            </div>
        </#list>
    </div>
</body>
</html>
