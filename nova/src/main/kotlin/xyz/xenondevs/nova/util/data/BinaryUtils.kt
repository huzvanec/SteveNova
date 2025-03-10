package xyz.xenondevs.nova.util.data

import io.netty.buffer.ByteBuf
import io.netty.buffer.Unpooled
import java.io.ByteArrayOutputStream
import java.util.*
import java.util.zip.Deflater
import java.util.zip.Deflater.DEFAULT_COMPRESSION
import java.util.zip.Inflater

internal fun ByteBuf.writeByte(byte: Byte): ByteBuf = writeByte(byte.toInt())

internal fun ByteBuf.toByteArray(): ByteArray {
    markReaderIndex()
    readerIndex(0)
    val bytes = ByteArray(readableBytes())
    readBytes(bytes)
    resetReaderIndex()
    return bytes
}

internal fun ByteArray.toByteBuf(allocator: () -> ByteBuf = Unpooled::buffer): ByteBuf {
    val buf = allocator()
    buf.writeBytes(this)
    return buf
}

internal fun ByteArray.compress(compressType: Int = DEFAULT_COMPRESSION): ByteArray {
    val deflater = Deflater(compressType)
    val buffer = ByteArray(512)
    deflater.setInput(this)
    deflater.finish()
    
    ByteArrayOutputStream().use { out ->
        while (!deflater.finished()) {
            val count = deflater.deflate(buffer)
            out.write(buffer, 0, count)
        }
        return out.toByteArray()
    }
}

internal fun ByteArray.decompress(): ByteArray {
    val inflater = Inflater()
    val buffer = ByteArray(512)
    inflater.setInput(this)
    
    ByteArrayOutputStream().use { out ->
        while (!inflater.finished()) {
            val count = inflater.inflate(buffer)
            out.write(buffer, 0, count)
        }
        return out.toByteArray()
    }
}

internal fun ByteArray.encodeBase64(): String = Base64.getEncoder().encodeToString(this)

internal fun String.decodeBase64(): ByteArray = Base64.getDecoder().decode(this)

internal fun ByteBuf.writeUUID(uuid: UUID) {
    writeLong(uuid.mostSignificantBits)
    writeLong(uuid.leastSignificantBits)
}

fun ByteBuf.readUUID(): UUID =
    UUID(readLong(), readLong())