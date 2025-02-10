package com.hipoom.files

import java.io.InputStream
import java.io.OutputStream

/**
 * @author ZhengHaiPeng
 * @since 2025/2/1 18:46
 */


fun InputStream.readText(): String {
    return this.bufferedReader().readText()
}


/**
 * 拷贝 [this] 接下来 [length] 长度的数据到 [output] 中。
 */
fun InputStream.copy(output: OutputStream, length: Int, bufferSize: Int) {
    val buffer = ByteArray(bufferSize)
    var copied = 0
    var nextCopyLength: Int
    while (copied < length) {
        nextCopyLength = if (length - copied >= bufferSize) {
            bufferSize
        } else {
            length - copied
        }
        read(buffer, 0, nextCopyLength)
        output.write(buffer, 0, nextCopyLength)
        copied += nextCopyLength
    }
}