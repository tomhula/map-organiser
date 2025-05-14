<#-- @ftlvariable name="event" type="io.github.tomhula.orisclient.dto.Event" -->
<#-- @ftlvariable name="qrCodeUrl" type="String" -->
<style>
    * {
        padding: 0;
        margin: 0;
        box-sizing: border-box;
    }

    .container {
        display: grid;
        grid-template-columns: 150px 1fr;
        grid-template-rows: 1fr 150px 1fr;
        grid-column-gap: 0;
        grid-row-gap: 0;
    }

    .top {
        grid-area: 1 / 1 / 2 / 2;
    }

    .center {
        grid-area: 2 / 1 / 3 / 2;
    }

    .bottom {
        grid-area: 3 / 1 / 4 / 3;
    }

    .right {
        grid-area: 2 / 2 / 3 / 3;
    }

    .qr-code {
        width: 100%;
    }

    .top {
        display: flex;
        justify-content: center;
        align-items: center;
    }

    .number {
        font-weight: bold;
        font-size: 20px;
    }

    .map-name {
        font-size: 20px;
    }

    .meta {
        display: flex;
        flex-direction: column;
        row-gap: 10px;
    }

    p {
        text-overflow: ellipsis;
        overflow: hidden;
        white-space: nowrap;
    }
</style>
<div class="container">
    <div class="top">
        <p class="number">5</p>
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
