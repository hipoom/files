package com.hipoom.files

import com.hipoom.Files
import java.io.File

/**
 * @author ZhengHaiPeng
 * @since 2025/2/1 14:58
 */


fun String.toFile(): File {
    return File(this)
}

fun File.child(name: String): File {
    return File(this, name)
}

/**
 * Create directory if not exist.
 *
 * @return
 *  0: exist already or create success.
 * -1: target file exist, but it's a file instead of directory.
 * -2: create target dir failed.
 */
fun File.ensureDirectory(): Int {
    return Files.ensureDirectory(this)
}

/**
 * Create target path's parent directory if not exist.
 *
 * @return
 *  0: exist already or create success.
 * -1: target's parent file is null.
 * -2: create target's parent dir failed.
 */
fun File.ensureParentDirectory(): Int {
    return Files.ensureParentDirectory(this)
}

/**
 * Create new file if not exist.
 *
 * @return
 * 0: create success or exist already.
 * -1: create new file failed.
 * -2: catch exception when create new file.
 * -3: create parent dir failed.
 */
fun File.createNewFileIfNotExist(): Int {
    return Files.createNewFileIfNotExist(this)
}

/**
 * Create new file if not exist.
 * 如果文件不存在，创建新文件.
 *
 * @param onDidCreate callback on did create [this] file.
 */
fun File.createNewFileIfNotExist(onDidCreate: File.() -> Unit): File {
    if (exists()) {
        return this
    }
    File(parent).ensureDirectory()
    createNewFile()
    onDidCreate(this)
    return this
}
