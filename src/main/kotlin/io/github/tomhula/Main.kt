package io.github.tomhula

import io.github.tomhula.orisclient.Oris
import io.github.tomhula.orisclient.OrisImpl
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.LocalDate
import qrcode.QRCode
import java.awt.Color
import java.awt.Font
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.io.File
import java.io.IOException
import javax.imageio.ImageIO
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.min

// A4 dimensions in millimeters
const val A4_WIDTH_MM = 210
const val A4_HEIGHT_MM = 297

// Default cell width in centimeters
const val DEFAULT_CELL_WIDTH_CM = 3.0

fun main(args: Array<String>) = runBlocking {
    val userRegNum = args[0]
    // Default cell width is 3cm, but can be overridden by passing a second argument
    val cellWidthCm = if (args.size > 1) args[1].toDoubleOrNull() ?: DEFAULT_CELL_WIDTH_CM else DEFAULT_CELL_WIDTH_CM

    val oris: Oris = OrisImpl()

    val userId = oris.getUser(userRegNum)!!.id
    val userEventEntries = oris.getUserEventEntries(userId, dateFrom = LocalDate(2018, 1, 1))

    println("Found ${userEventEntries.size} user event entries for user $userId")

    val eventsDeferred = userEventEntries
        .sortedBy { it.eventDate }
        .map { async { oris.getEvent(it.eventId) } }

    val events = eventsDeferred.awaitAll()

    val eventQrCodes = events.associateWith {
        QRCode.ofSquares()
            .withInnerSpacing(0)
            .build("https://oris.orientacnisporty.cz/Zavod?id=${it.id}")
            .renderToBytes()
    }

    // Create QR code grid images
    val qrCodeGridImages = createQrCodeGridImages(events, eventQrCodes, cellWidthCm)

    // Save images to files
    qrCodeGridImages.forEachIndexed { index, image ->
        saveImageToFile(image, "qr_code_grid_${index + 1}.png")
    }

    println("Generated ${qrCodeGridImages.size} QR code grid images")
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
