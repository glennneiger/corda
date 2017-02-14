package net.corda.core.crypto

import java.nio.charset.Charset
import java.util.*
import javax.xml.bind.DatatypeConverter


// This file includes useful encoding methods and extension functions for the most common encoding/decoding operations.

// [ByteArray] encoders

fun ByteArray.toBase58(): String =
        Base58.encode(this)

fun ByteArray.toBase64(): String =
        Base64.getEncoder().encodeToString(this)

/** Convert a byte array to a hex (base 16) capitalized encoded string.*/
fun ByteArray.toHex(): String =
    DatatypeConverter.printHexBinary(this)


// [String] encoders and decoders

/** Base58-String to the actual real [String], i.e. "JxF12TrwUP45BMd" -> "Hello World". */
fun String.base58ToRealString() =
        String(base58ToByteArray(), Charset.defaultCharset())

/** Base64-String to the actual real [String], i.e. "SGVsbG8gV29ybGQ=" -> "Hello World". */
fun String.base64ToRealString() =
        String(base64ToByteArray())

/** HEX-String to the actual real [String], i.e. "48656C6C6F20576F726C64" -> "Hello World". */
fun String.hexToRealString() =
        String(hexToByteArray())

fun String.base58ToByteArray(): ByteArray =
        Base58.decode(this)

fun String.base64ToByteArray(): ByteArray =
        Base64.getDecoder().decode(this)

/** Hex-String to [ByteArray]. Accept any hex form (capitalized, lowercase, mixed). */
fun String.hexToByteArray(): ByteArray =
        DatatypeConverter.parseHexBinary(this);


// Helper vars.
private val HEX_ALPHABET = "0123456789ABCDEF".toCharArray()
