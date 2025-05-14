package io.github.tomhula

import io.github.tomhula.orisclient.Oris
import io.github.tomhula.orisclient.OrisImpl
import io.github.tomhula.orisclient.dto.Event
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.LocalDate
import qrcode.QRCode
import java.awt.Color
import java.awt.Font
import java.awt.FontMetrics
import java.awt.RenderingHints
import java.awt.Toolkit
import java.awt.event.KeyEvent
import java.awt.event.KeyListener
import java.awt.image.BufferedImage
import java.io.File
import java.io.IOException
import javax.imageio.ImageIO
import javax.swing.ImageIcon
import javax.swing.JFrame
import javax.swing.JLabel
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.min


val oris: Oris = OrisImpl()

// A4 dimensions in millimeters
const val A4_WIDTH_MM = 210
const val A4_HEIGHT_MM = 297

// Default cell width in centimeters
const val DEFAULT_CELL_WIDTH_CM = 3.0

fun main(args: Array<String>) = runBlocking {
    val userRegNum = args[0]
    // Default cell width is 3cm, but can be overridden by passing a second argument
    val cellWidthCm = if (args.size > 1) args[1].toDoubleOrNull() ?: DEFAULT_CELL_WIDTH_CM else DEFAULT_CELL_WIDTH_CM

    /*val events = getUserEvents(userRegNum)
        .sortedBy { it.date }*/

    val lastEvent = oris.getEvent(8222)
    val lastEventCellImage = createEventCell(
        number = 254,
        event = lastEvent,
        widthPx = 1000,
        qrCodeSizePx = 100,
        numberFontSizePx = 50,
        numberMarginPx = 10,
        metaMarginPx = 30,
        metaFontSizePx = 20,
        metaLineSpacingPx = 10,
        mapNameFontSizePx = 20,
        mapNameMarginPx = 10,
        dpi = Toolkit.getDefaultToolkit().getScreenResolution().also { println("Screen resolution: $it dpi") }
    )

    saveImageToFile(lastEventCellImage, "last_event_cell.png")

    showImage(lastEventCellImage)

    /*val eventQrCodes = events.associateWith(::createEventQrCode)

    // Create QR code grid images
    val qrCodeGridImages = createQrCodeGridImages(events, eventQrCodes, cellWidthCm)

    // Save images to files
    qrCodeGridImages.forEachIndexed { index, image ->
        saveImageToFile(image, "qr_code_grid_${index + 1}.png")
    }

    println("Generated ${qrCodeGridImages.size} QR code grid images")*/
}

private suspend fun getUserEvents(userRegNum: String): List<Event>
{
    val userId = oris.getUser(userRegNum)?.id

    if (userId == null)
        throw RuntimeException("User with registration number $userRegNum not found")

    val userEventEntries = oris.getUserEventEntries(userId)

    return coroutineScope { 
        val eventsDeferred = userEventEntries.map { userEventEntry ->
            async { oris.getEvent(userEventEntry.eventId) }
        }
        eventsDeferred.awaitAll()
    }
}

private fun createEventQrCode(event: Event): ByteArray
{
    return QRCode.ofSquares()
        .withInnerSpacing(0)
        .build("https://oris.orientacnisporty.cz/Zavod?id=${event.id}")
        .renderToBytes()
}

private fun showImage(image: BufferedImage)
{
    val frame = JFrame()
    frame.add(JLabel(ImageIcon(image)))
    frame.pack()
    // To show the frame in the center of the screen
    frame.setLocationRelativeTo(null)
    frame.defaultCloseOperation = JFrame.EXIT_ON_CLOSE
    frame.isVisible = true
    frame.addKeyListener(object : KeyListener {
        override fun keyTyped(e: KeyEvent?) = Unit
        override fun keyPressed(e: KeyEvent?) = Unit
        override fun keyReleased(e: KeyEvent?)
        {
            if (e?.keyCode == KeyEvent.VK_ESCAPE)
                frame.dispose()
        }
    })
}

private fun mmToPx(mm: Float, dpi: Int): Int = (mm * dpi / 25.4f).toInt()

private fun ptToPx(pt: Float, dpi: Int): Int = (pt * dpi / 72f).toInt()

private fun ptToPx(pt: Int, dpi: Int): Int = ptToPx(pt.toFloat(), dpi)

private fun pxToPt(px: Int, dpi: Int): Float = px * 72f / dpi

/**
 * Sizes are in pixels
 *
 * @param numberMarginPx The space between the qrcode and the number
 * @param metaMarginPx The space between the qrcode and the meta-text
 * @param mapNameFontSizePx Font size for the map name text
 * @param mapNameMarginPx The space between the map name and the content above it
 * */
private fun createEventCell(
    event: Event,
    number: Int,
    widthPx: Int,
    qrCodeSizePx: Int,
    numberFontSizePx: Int,
    numberMarginPx: Int,
    metaFontSizePx: Int,
    metaLineSpacingPx: Int,
    mapNameFontSizePx: Int,
    mapNameMarginPx: Int,
    metaMarginPx: Int,
    dpi: Int
): BufferedImage 
{
    // Extract repeated code to a properly named function
    fun truncateText(text: String, fontMetrics: FontMetrics, maxWidth: Int): String 
    {
        if (fontMetrics.stringWidth(text) <= maxWidth) 
            return text

        var truncatedText = text
        while (fontMetrics.stringWidth("$truncatedText...") > maxWidth && truncatedText.isNotEmpty())
            truncatedText = truncatedText.substring(0, truncatedText.length - 1)
        
        return "$truncatedText..."
    }

    val metaTextWidthRatio = 0.9 // Meta text takes 90% of the available width
    val mapTextWidthRatio = 0.9 // Map text takes 90% of the total width

    val qrCode = createEventQrCode(event)
    val qrCodeImage = ImageIO.read(qrCode.inputStream())

    // Use the provided QR code size parameter
    val qrCodeSize = qrCodeSizePx

    val numberHeight = numberFontSizePx
    val metaTextHeight = 3 * metaFontSizePx + 2 * metaLineSpacingPx
    val mapNameHeight = mapNameFontSizePx

    // Calculate total height: number + margin + max(qrcode, meta text) + margin + map name
    val contentHeight = numberHeight + numberMarginPx + 
                       Math.max(qrCodeSize, metaTextHeight) + 
                       mapNameMarginPx + mapNameHeight

    val cellImage = BufferedImage(widthPx, contentHeight, BufferedImage.TYPE_INT_RGB)
    val g2d = cellImage.createGraphics()

    // Set white background
    g2d.color = Color.WHITE
    g2d.fillRect(0, 0, widthPx, contentHeight)

    // Enable anti-aliasing for better text quality
    g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
    g2d.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)

    // Draw the number centered above the QR code itself
    g2d.color = Color.BLACK
    g2d.font = Font("Arial", Font.BOLD, pxToPt(numberFontSizePx, dpi = dpi).toInt())
    val numberText = number.toString()
    val numberTextWidth = g2d.fontMetrics.stringWidth(numberText)
    g2d.drawString(numberText, (qrCodeSize - numberTextWidth) / 2, numberHeight)

    // Draw the QR code on the left side
    val qrCodeX = 0
    val qrCodeY = numberHeight + numberMarginPx
    g2d.drawImage(qrCodeImage, qrCodeX, qrCodeY, qrCodeSize, qrCodeSize, null)

    // Draw the meta text (date, name, location) next to the QR code
    g2d.font = Font("Arial", Font.PLAIN, pxToPt(metaFontSizePx, dpi = dpi).toInt())
    val metaFontMetrics = g2d.fontMetrics

    // Calculate available width for meta text
    val metaTextX = qrCodeSize + metaMarginPx
    val availableMetaWidth = widthPx - metaTextX
    val maxMetaTextWidth = (availableMetaWidth * metaTextWidthRatio).toInt()

    // Truncate texts if needed
    val dateText = truncateText(event.date.toString(), metaFontMetrics, maxMetaTextWidth)
    val nameText = truncateText(event.name, metaFontMetrics, maxMetaTextWidth)
    // Using a placeholder for location since it's not available in the Event class
    val locationText = truncateText(event.place ?: "Unknown place", metaFontMetrics, maxMetaTextWidth)

    // Calculate starting position for meta text
    val metaY = qrCodeY

    // Draw the first line (date)
    g2d.drawString(
        dateText,
        metaTextX,
        metaY + metaFontSizePx
    )

    // Draw the second line (name)
    g2d.drawString(
        nameText,
        metaTextX,
        metaY + metaFontSizePx + metaLineSpacingPx + metaFontSizePx
    )

    // Draw the third line (location)
    g2d.drawString(
        locationText,
        metaTextX,
        metaY + metaFontSizePx + metaLineSpacingPx + metaFontSizePx + metaLineSpacingPx + metaFontSizePx
    )

    // Draw the map name below everything
    g2d.font = Font("Arial", Font.PLAIN, pxToPt(mapNameFontSizePx, dpi = dpi).toInt())
    val mapFontMetrics = g2d.fontMetrics
    val maxMapTextWidth = (widthPx * mapTextWidthRatio).toInt()

    // Truncate map text if needed
    val mapText = truncateText(event.map ?: "Unknown map", mapFontMetrics, maxMapTextWidth)

    // Calculate position for map text
    val mapY = qrCodeY + qrCodeSize.coerceAtLeast(metaTextHeight) + mapNameMarginPx

    // Draw the map text (left-aligned to the entire image)
    g2d.drawString(
        mapText,
        0, // Left-aligned with the left edge of the entire image
        mapY + mapNameFontSizePx
    )

    g2d.dispose()
    return cellImage
}

/**
 * Creates grid images of QR codes with A4 proportions.
 * 
 * @param events List of events
 * @param eventQrCodes Map of events to their QR code byte arrays
 * @param cellWidthCm Width of each cell in centimeters
 * @return List of BufferedImage objects representing the grid images
 */
fun <T> createQrCodeGridImages(
    events: List<T>, 
    eventQrCodes: Map<T, ByteArray>,
    cellWidthCm: Double
): List<BufferedImage> {
    // Convert cell width from cm to mm
    val cellWidthMm = cellWidthCm * 10

    // Calculate how many cells can fit in a row and column on an A4 page
    val cellsPerRow = floor(A4_WIDTH_MM / cellWidthMm).toInt()
    // Use cell height (which is now taller) to calculate how many cells fit vertically
    val cellHeightMm = cellWidthMm * 1.3 // Match the height ratio we use for pixels
    val cellsPerColumn = floor(A4_HEIGHT_MM / cellHeightMm).toInt()
    val cellsPerPage = cellsPerRow * cellsPerColumn

    // Calculate how many pages we need
    val totalPages = ceil(events.size.toDouble() / cellsPerPage).toInt()

    // Define DPI (dots per inch) for the image
    // 300 DPI is a good resolution for printing
    val dpi = 300

    // Convert A4 dimensions from mm to pixels at the specified DPI
    // 1 inch = 25.4 mm
    val pageWidthPx = (A4_WIDTH_MM / 25.4 * dpi).toInt()
    val pageHeightPx = (A4_HEIGHT_MM / 25.4 * dpi).toInt()

    // Calculate cell dimensions in pixels
    val cellWidthPx = (cellWidthMm / 25.4 * dpi).toInt()
    // Make cell height taller than width to accommodate text above and below
    val cellHeightPx = (cellWidthPx * 1.3).toInt() // Increased height for text space

    // Calculate padding between cells
    val horizontalPadding = (pageWidthPx - cellsPerRow * cellWidthPx) / (cellsPerRow + 1)
    val verticalPadding = (pageHeightPx - cellsPerColumn * cellHeightPx) / (cellsPerColumn + 1)

    // Create a list to hold all the grid images
    val gridImages = mutableListOf<BufferedImage>()

    // Create each page
    for (page in 0 until totalPages) {
        // Create a new image with A4 proportions
        val gridImage = BufferedImage(pageWidthPx, pageHeightPx, BufferedImage.TYPE_INT_RGB)
        val g2d = gridImage.createGraphics()

        // Set white background
        g2d.color = Color.WHITE
        g2d.fillRect(0, 0, pageWidthPx, pageHeightPx)

        // Enable anti-aliasing for better text quality
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        g2d.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)

        // Calculate how many events to display on this page
        val startIndex = page * cellsPerPage
        val endIndex = min((page + 1) * cellsPerPage, events.size)

        // Draw each QR code cell
        for (i in startIndex until endIndex) {
            val event = events[i]
            val qrCodeBytes = eventQrCodes[event] ?: continue

            // Calculate position in the grid
            val gridPosition = i - startIndex
            val row = gridPosition / cellsPerRow
            val col = gridPosition % cellsPerRow

            // Calculate pixel position
            val x = horizontalPadding + col * (cellWidthPx + horizontalPadding)
            val y = verticalPadding + row * (cellHeightPx + verticalPadding)

            // Draw the QR code
            try {
                val qrCodeImage = ImageIO.read(qrCodeBytes.inputStream())

                // Calculate QR code size (80% of cell width)
                val qrCodeSize = (cellWidthPx * 0.8).toInt()
                val qrCodeX = x + (cellWidthPx - qrCodeSize) / 2
                // Position QR code higher in the cell to leave more space for text below
                val qrCodeY = y + (cellHeightPx - qrCodeSize) / 3

                g2d.drawImage(qrCodeImage, qrCodeX, qrCodeY, qrCodeSize, qrCodeSize, null)

                // Draw index number above QR code
                g2d.color = Color.BLACK
                g2d.font = Font("Arial", Font.BOLD, cellWidthPx / 10)
                val indexText = (i + 1).toString()
                val indexTextWidth = g2d.fontMetrics.stringWidth(indexText)
                // Position the index number with more space above the QR code
                g2d.drawString(indexText, x + (cellWidthPx - indexTextWidth) / 2, qrCodeY - g2d.fontMetrics.height / 2)

                // Draw three lines of text below QR code: date, name, and map
                // Since we don't know the exact structure of the Event class,
                // we'll try to access properties using reflection

                // Get event date (formatted as d.M.yyyy)
                val dateText = if (event != null) {
                    try {
                        val dateProperty = event.javaClass.getMethod("getDate") ?: 
                                          event.javaClass.getMethod("getEventDate")
                        val date = dateProperty.invoke(event)
                        // Try to format the date as d.M.yyyy
                        try {
                            // Check if it's a kotlinx.datetime.LocalDate
                            if (date is LocalDate) {
                                "${date.dayOfMonth}.${date.monthNumber}.${date.year}"
                            } else {
                                // Try to access day, month, year properties
                                val dayMethod = date?.javaClass?.getMethod("getDay") ?: 
                                               date?.javaClass?.getMethod("getDayOfMonth")
                                val monthMethod = date?.javaClass?.getMethod("getMonth") ?: 
                                                 date?.javaClass?.getMethod("getMonthNumber") ?: 
                                                 date?.javaClass?.getMethod("getMonthValue")
                                val yearMethod = date?.javaClass?.getMethod("getYear")

                                val day = dayMethod?.invoke(date)?.toString() ?: "?"
                                val month = monthMethod?.invoke(date)?.toString() ?: "?"
                                val year = yearMethod?.invoke(date)?.toString() ?: "????"

                                "$day.$month.$year"
                            }
                        } catch (e: Exception) {
                            // If formatting fails, just use toString
                            date?.toString() ?: "Unknown date"
                        }
                    } catch (e: Exception) {
                        try {
                            val dateField = event.javaClass.getDeclaredField("date") ?: 
                                           event.javaClass.getDeclaredField("eventDate")
                            dateField.isAccessible = true
                            dateField.get(event)?.toString() ?: "Unknown date"
                        } catch (e: Exception) {
                            "Unknown date"
                        }
                    }
                } else {
                    "Unknown date"
                }

                // Get event name
                val nameText = if (event != null) {
                    try {
                        val nameProperty = event.javaClass.getMethod("getName")
                        nameProperty.invoke(event)?.toString() ?: "Unknown event"
                    } catch (e: Exception) {
                        try {
                            val nameField = event.javaClass.getDeclaredField("name")
                            nameField.isAccessible = true
                            nameField.get(event)?.toString() ?: "Unknown event"
                        } catch (e: Exception) {
                            "Event #${i + 1}"
                        }
                    }
                } else {
                    "Event #${i + 1}"
                }

                // Get event map
                val mapText = if (event != null) {
                    try {
                        val mapProperty = event.javaClass.getMethod("getMap")
                        mapProperty.invoke(event)?.toString() ?: "Unknown map"
                    } catch (e: Exception) {
                        try {
                            val mapField = event.javaClass.getDeclaredField("map")
                            mapField.isAccessible = true
                            mapField.get(event)?.toString() ?: "Unknown map"
                        } catch (e: Exception) {
                            "Unknown map"
                        }
                    }
                } else {
                    "Unknown map"
                }

                g2d.font = Font("Arial", Font.PLAIN, cellWidthPx / 24)
                val fontMetrics = g2d.fontMetrics
                val maxTextWidth = cellWidthPx * 0.9 // 90% of cell width to leave some margin

                // Function to truncate text if it's too long
                fun truncateText(text: String): String {
                    if (fontMetrics.stringWidth(text) <= maxTextWidth) {
                        return text
                    }

                    var truncatedText = text
                    while (fontMetrics.stringWidth(truncatedText + "...") > maxTextWidth && truncatedText.isNotEmpty()) {
                        truncatedText = truncatedText.substring(0, truncatedText.length - 1)
                    }
                    return truncatedText + "..."
                }

                // Truncate each line to fit the cell width
                val truncatedDateText = truncateText(dateText)
                val truncatedNameText = truncateText(nameText)
                val truncatedMapText = truncateText(mapText)

                // Calculate vertical positions for the three lines
                val line1Y = qrCodeY + qrCodeSize + (fontMetrics.height * 1.2).toInt()
                val line2Y = line1Y + (fontMetrics.height * 1.2).toInt()
                val line3Y = line2Y + (fontMetrics.height * 1.2).toInt()

                // Draw the three lines of text
                // Line 1: Date
                g2d.drawString(
                    truncatedDateText,
                    x + (cellWidthPx - fontMetrics.stringWidth(truncatedDateText)) / 2,
                    line1Y
                )

                // Line 2: Name
                g2d.drawString(
                    truncatedNameText,
                    x + (cellWidthPx - fontMetrics.stringWidth(truncatedNameText)) / 2,
                    line2Y
                )

                // Line 3: Map
                g2d.drawString(
                    truncatedMapText,
                    x + (cellWidthPx - fontMetrics.stringWidth(truncatedMapText)) / 2,
                    line3Y
                )
            } catch (e: IOException) {
                println("Error drawing QR code for event $i: ${e.message}")
            }
        }

        g2d.dispose()
        gridImages.add(gridImage)
    }

    return gridImages
}

/**
 * Saves a BufferedImage to a file.
 * 
 * @param image The image to save
 * @param fileName The name of the file
 */
fun saveImageToFile(image: BufferedImage, fileName: String) {
    try {
        val outputFile = File(fileName)
        ImageIO.write(image, "PNG", outputFile)
        println("Saved image to ${outputFile.absolutePath}")
    } catch (e: IOException) {
        println("Error saving image: ${e.message}")
    }
}
