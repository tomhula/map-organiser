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
import java.awt.Graphics2D
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
    val cellsPerColumn = floor(A4_HEIGHT_MM / cellWidthMm).toInt()
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
    val cellHeightPx = cellWidthPx // Square cells

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
                val qrCodeY = y + (cellHeightPx - qrCodeSize) / 2

                g2d.drawImage(qrCodeImage, qrCodeX, qrCodeY, qrCodeSize, qrCodeSize, null)

                // Draw index number above QR code
                g2d.color = Color.BLACK
                g2d.font = Font("Arial", Font.BOLD, cellWidthPx / 10)
                val indexText = (i + 1).toString()
                val indexTextWidth = g2d.fontMetrics.stringWidth(indexText)
                g2d.drawString(indexText, x + (cellWidthPx - indexTextWidth) / 2, qrCodeY - 5)

                // Draw event.map text below QR code
                // Since we don't know the exact structure of the Event class,
                // we'll try to access a "map" property or use toString() as fallback
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
                            try {
                                // Try to access the name property if map is not available
                                val nameProperty = event.javaClass.getMethod("getName")
                                nameProperty.invoke(event)?.toString() ?: "Event #${i + 1}"
                            } catch (e: Exception) {
                                // Last resort: use toString or just the index
                                event.toString().takeIf { it != event.javaClass.name } ?: "Event #${i + 1}"
                            }
                        }
                    }
                } else {
                    "Event #${i + 1}"
                }

                g2d.font = Font("Arial", Font.PLAIN, cellWidthPx / 12)
                val mapTextWidth = g2d.fontMetrics.stringWidth(mapText)
                g2d.drawString(
                    mapText, 
                    x + (cellWidthPx - mapTextWidth) / 2, 
                    qrCodeY + qrCodeSize + g2d.fontMetrics.height
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
