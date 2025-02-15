package com.hipoom;

import java.io.BufferedWriter;
import java.io.Closeable;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.io.Reader;
import java.io.StringWriter;
import java.io.Writer;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;

/**
 * @author ZhengHaiPeng
 * @since 4/18/24 00:15 PM
 */
@SuppressWarnings({"unused", "CallToPrintStackTrace"})
public class Files {

    /* ======================================================= */
    /* Fields                                                  */
    /* ======================================================= */

    public static final int CODE_SUCCESS = 0;

    /**
     * 文件名中不应该包含的字符。
     */
    public static final String[] INVALID_FILE_NAME_CHARS = {
        // for windows
        "\"", "*", "<", ">", "?", "|",
        // for unix
        "\000", ":"
    };



    /* ======================================================= */
    /* Public Methods                                          */
    /* ======================================================= */

    /**
     * Create directory if not exist.
     *
     * @param dir the target directory. cannot be null.
     *
     * @return
     *  0: exist already or create success.
     * -1: target file exist, but it's a file instead of directory.
     * -2: create target dir failed.
     */
    public static int ensureDirectory(File dir) {
        if (dir.exists()) {
            if (dir.isDirectory()) {
                return CODE_SUCCESS;
            }
            return -1;
        }
        boolean isSuccess = dir.mkdirs();
        return isSuccess ? CODE_SUCCESS : -2;
    }

    /**
     * Create target path's parent directory if not exist.
     *
     * @param target target file.
     *
     * @return
     *  0: exist already or create success.
     * -1: target's parent file is null.
     * -2: create target's parent dir failed.
     */
    public static int ensureParentDirectory(File target) {
        File parent = target.getParentFile();
        if (parent == null) {
            return -1;
        }

        if (parent.exists()) {
            return CODE_SUCCESS;
        }

        boolean isSuccess = parent.mkdirs();
        return isSuccess ? CODE_SUCCESS : -2;
    }

    /**
     * Create new file if not exist.
     *
     * @param target The target file.
     * @return
     *  0: create success or exist already.
     * -1: create new file failed.
     * -2: catch exception when create new file.
     * -3: create parent dir failed.
     */
    public static int createNewFileIfNotExist(File target) {
        if (target.exists()) {
            return CODE_SUCCESS;
        }

        // before create the target file, we need to ensure parent dir exist
        int code = ensureParentDirectory(target);

        // if target's parent is null, maybe want to create in root dir,
        // so we need to handle situations where the code is -1.
        if (code == CODE_SUCCESS || code == -1) {
            try {
                boolean isSuccess = target.createNewFile();
                return isSuccess ? CODE_SUCCESS : -1;
            } catch (IOException e) {
                e.printStackTrace();
                return -2;
            }
        }

        return -3;
    }


    /**
     * Delete file or directory.
     *
     * @param file need delete file or dir.
     * @return true if delete success; false otherwise.
     */
    public static boolean delete(File file) {
        if (file == null || !file.exists()) {
            return true;
        }

        if (file.isFile()) {
            return file.delete();
        }

        File[] children = file.listFiles();

        // if no children, delete this empty dir.
        if (children == null) {
            return file.delete();
        }

        // delete recursive
        for (File child : children) {
            boolean isSuccess = delete(child);
            if (!isSuccess) {
                return false;
            }
        }

        // after delete all children, delete empty dir.
        return file.delete();
    }


    /**
     * Copy from stream 'is' to 'os'.
     *
     * @param is the input stream
     * @param inPolicy closing policy for input stream.
     * @param os the output stream
     * @param outPolicy closing policy for input stream.
     *
     * @return true if copy success; false otherwise.
     */
    public static boolean copy(InputStream is, AutoClosePolicy inPolicy, OutputStream os, AutoClosePolicy outPolicy) {
        byte[] bytes = new byte[8 * 1024];
        try {
            int length = is.read(bytes);
            while (length >= 0) {
                os.write(bytes, 0, length);
                length = is.read(bytes);
            }
            return true;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        } finally {
            if (inPolicy == AutoClosePolicy.CLOSE) {
                closeQuietly(is);
            }
            if (outPolicy == AutoClosePolicy.CLOSE) {
                closeQuietly(os);
            }
        }
    }

    public static boolean copy(Reader reader, AutoClosePolicy inPolicy, Writer writer) {
        char[] buffer = new char[8 * 1024];
        try {
            int length = reader.read(buffer);
            while (length >= 0) {
                writer.write(buffer, 0, length);
                length = reader.read(buffer);
            }
            return true;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        } finally {
            if (inPolicy == AutoClosePolicy.CLOSE) {
                closeQuietly(reader);
            }
        }
    }

    /**
     * Copy a file or a directory from {@code source} to {@code destination}.
     *
     * @param source the source file or dir.
     * @param destination the destination file or dir.
     * @param policy Processing policy when copying files if the destination file already exist.
     *
     * @return
     *  0: copy success
     * -1: the source file doesn't exist.
     * -2: if policy is Overwrite, but delete old destination file failed.
     * -3: failed to create directory for the destination file.
     * -4: failed to create file stream.
     * -5: an exception occurred while copying the file stream.
     */
    public static int copy(File source, File destination, DstFileExistPolicy policy) {
        if (source.exists()) {
            return -1;
        }

        if (source.isFile()) {
            return copyFile(source, destination, policy);
        }

        File[] children = source.listFiles();
        // if no children exist, just create empty dst dir.
        if (children == null || children.length == 0) {
            int code = ensureDirectory(destination);
            if (code == CODE_SUCCESS || code == -1) {
                return CODE_SUCCESS;
            }
            return -3;
        }

        boolean isSuccess;
        for (File child : children) {
            int code = copy(child, new File(destination, child.getName()), policy);
            if (code != CODE_SUCCESS) {
                return code;
            }
        }

        return CODE_SUCCESS;
    }


    /**
     * Move file from source to destination.
     *
     * @param source source file or directory.
     * @param destination dst file or directory.
     * @param policy Processing policy when copying files if the destination file already exist.
     *
     * @return
     *  0: move success.
     * -1: the source file doesn't exist.
     * -2: if policy is Overwrite, and delete old destination file failed.
     * -3: failed to create directory for the destination file.
     * -4: failed to move file, because the source file has been opened.
     * -5: failed to copy then delete.
     */
    public static int rename(File source, File destination, DstFileExistPolicy policy) {
        if (!source.exists()) {
            return -1;
        }

        boolean isDstExist = destination.exists();
        if (isDstExist) {
            switch (policy) {
                case GiveUp: {
                    return CODE_SUCCESS;
                }
                case ThrowException: {
                    throw new RuntimeException("The destination file already exist.");
                }
                case Overwrite:
                default: {
                    boolean isSuccess = delete(destination);
                    if (!isSuccess) {
                        return -2;
                    }
                } // end default/Overwrite
            } // end switch
        } // end if

        // if create target's parent dir failed, return -3
        int code = ensureParentDirectory(destination);
        if (code == -2) {
            return -3;
        }

        boolean isSuccess = source.renameTo(destination);
        if (isSuccess) {
            return CODE_SUCCESS;
        }

        // if file rename failed, maybe file opened already.
        code = isFileOpened(source);
        // if file opened, return -4
        if (code == CODE_SUCCESS) {
            return -4;
        }

        // if the file was not opened, try copy then delete old file.
        code = moveByCopyThenDelete(source, destination);
        if (code != CODE_SUCCESS) {
            return -5;
        }

        return CODE_SUCCESS;
    }

    /**
     * @return
     *  0: success
     * -1: failed to copy
     * -2: failed to delete
     */
    public static int moveByCopyThenDelete(File src, File dst) {
        int code = copyFile(src, dst, DstFileExistPolicy.Overwrite);
        if (code != CODE_SUCCESS) {
            return -1;
        }

        boolean isDeleteSuccess = delete(src);
        if (!isDeleteSuccess) {
            return -2;
        }

        return CODE_SUCCESS;
    }


    /**
     * Read short text from input stream.
     *
     * @param is the input stream.
     * @param policy closing policy for input stream.
     *
     * @return text read from input stream.
     */
    public static String readText(InputStream is, AutoClosePolicy policy) {
        InputStreamReader reader = new InputStreamReader(is);
        StringWriter writer = new StringWriter();
        boolean isSuccess = copy(reader, policy, writer);
        if (isSuccess) {
            String res = writer.toString();
            closeQuietly(writer);
            return res;
        }
        closeQuietly(writer);
        return null;
    }

    /**
     * Read text from file.
     */
    public static String readText(File file) {
        if (!file.exists()) {
            return null;
        }
        if (!file.isFile()) {
            return null;
        }

        FileInputStream fis;
        try {
            fis = new FileInputStream(file);
        } catch (FileNotFoundException e) {
            return null;
        }
        return readText(fis, AutoClosePolicy.CLOSE);
    }


    /**
     * Write text into file.
     *
     * @return
     *  0: success to write text, or target file exist already and policy is GiveUp.
     * -1: failed to create the directory of target file.
     * -2: failed to delete old file.
     * -3: failed to create target file.
     * -4: an exception occurred while writing.
     */
    public static int writeText(File file, String text, DstFileExistPolicy policy) {
        int code = ensureParentDirectory(file);
        if (code != CODE_SUCCESS) {
            return -1;
        }

        boolean isDstExist = file.exists();
        if (isDstExist) {
            switch (policy) {
                case GiveUp: {
                    return 0;
                }
                case ThrowException: {
                    throw new RuntimeException("The destination file already exist.");
                }
                case Overwrite:
                default: {
                    boolean isSuccess = delete(file);
                    if (!isSuccess) {
                        return -2;
                    }
                } // end default/Overwrite
            } // end switch
        } // end if

        // 写入文件
        try {
            // 创建新文件
            boolean isCreateSuccess = file.createNewFile();
            if (!isCreateSuccess) {
                return -3;
            }
            // 写入
            BufferedWriter writer = new BufferedWriter(new FileWriter(file));
            writer.write(text);
            closeQuietly(writer);
        } catch (Exception e) {
            e.printStackTrace();
            return -4;
        }

        return CODE_SUCCESS;
    }


    /**
     * Close stream or other closeable obj quietly.
     *
     * @param closeable file stream or other closeable obj.
     */
    public static void closeQuietly(Closeable... closeable) {
        if (closeable == null) {
            return;
        }
        for (Closeable c : closeable) {
            if (c == null) {
                continue;
            }
            try {
                c.close();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    /**
     * Create FileInputStream safely.
     */
    public static FileInputStream inputStream(File file) {
        try {
            return new FileInputStream(file);
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }
        return null;
    }

    /**
     * Create FileOutputStream safely.
     */
    public static FileOutputStream outputStream(File file) {
        try {
            int code = ensureParentDirectory(file);
            if (code != CODE_SUCCESS && code != -1) {
                return null;
            }
            return new FileOutputStream(file);
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }
        return null;
    }

    /**
     * Check if the file has been opened.
     *
     * @return
     *  0: the file was not opened.
     * -1: the file has been opened.
     * -2: an exception occurred while checking.
     * -3: failed to create FileChanel obj.
     */
    public static int isFileOpened(File file) {
        try {
            FileLock lock;
            try (RandomAccessFile access = new RandomAccessFile(file, "rw")) {
                FileChannel channel = access.getChannel();
                if (channel == null) {
                    return -3;
                }
                lock = channel.tryLock();
                // access/channel/lock will close automatically
            }
            if (lock == null) {
                return -1;
            }

            try {
                if (lock.isValid()) {
                    lock.close();
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
            return 0;
        } catch (Exception e) {
            return -2;
        }
    }

    /**
     *
     *
     * @param file the file try lock.
     * @param callback callback when file lock success.
     */
    public static void lockFile(File file, LockFileCallback callback) {
        if (file == null || callback == null) {
            return;
        }

        if (!file.exists()) {
            callback.onLockFinish(-1, null);
            return;
        }

        FileInputStream fis = inputStream(file);
        if (fis == null) {
            callback.onLockFinish(-2, null);
            return;
        }

        FileChannel fc = fis.getChannel();
        try {
            FileLock lock = fc.lock();
            if (lock == null) {
                callback.onLockFinish(-3, null);
                return;
            }

            try {
                callback.onLockFinish(0, file);
            } catch (Exception e) {
                e.printStackTrace();
            }

            // release lock
            lock.release();
        } catch (Exception e) {
            e.printStackTrace();
        }

        closeQuietly(fc, fis);
    }

    /**
     * 判断一个字符串是否可以用作文件名。
     */
    public static boolean isFileNameValid(String name) {
        for (String key : INVALID_FILE_NAME_CHARS) {
            if (name.contains(key)) {
                return false;
            }
        }
        return true;
    }



    /* ======================================================= */
    /* Private Methods                                         */
    /* ======================================================= */

    /**
     * Copy one file (not directory) from {@code source} to {@code destination}.
     *
     * @param source the source file.
     * @param destination the destination file.
     * @param policy Processing policy when copying files if the destination file already exist.
     *
     * @return
     *  0: copy success
     * -1: the source file doesn't exist.
     * -2: if policy is Overwrite, but delete old destination file failed.
     * -3: failed to create directory for the destination file.
     * -4: failed to create file stream.
     * -5: an exception occurred while copying the file stream.
     */
    private static int copyFile(File source, File destination, DstFileExistPolicy policy) {
        if (!source.exists()) {
            return -1;
        }

        boolean isDstExist = destination.exists();
        if (isDstExist) {
            switch (policy) {
                case GiveUp: {
                    return CODE_SUCCESS;
                }
                case ThrowException: {
                    throw new RuntimeException("The destination file already exist.");
                }
                case Overwrite:
                default: {
                    boolean isSuccess = delete(destination);
                    if (!isSuccess) {
                        return -2;
                    }
                } // end default/Overwrite
            } // end switch
        } // end if

        // ensure that the directory of the destination file exist.
        // if target's parent is null, maybe want to create in root dir,
        // so we need to handle situations where the code is -1.
        int code = ensureParentDirectory(destination);
        if (code == -2) {
            return -3;
        }

        FileInputStream fis = inputStream(source);
        FileOutputStream fos = outputStream(destination);
        if (fis == null || fos == null) {
            closeQuietly(fis);
            closeQuietly(fos);
            return -4;
        }

        boolean isSuccess = copy(fis, AutoClosePolicy.CLOSE, fos, AutoClosePolicy.CLOSE);
        return isSuccess ? CODE_SUCCESS : -5;
    }



    /* ======================================================= */
    /* Inner Class                                             */
    /* ======================================================= */

    /**
     * Processing policy when copying files if the destination file already exist.
     */
    public enum DstFileExistPolicy {
        Overwrite,
        GiveUp,
        ThrowException,
    }

    /**
     * Closing policy for file stream.
     */
    public enum AutoClosePolicy {
        CLOSE,
        DON_NOT_CLOSE
    }

    /**
     * 锁定文件后的回调。
     */
    public interface LockFileCallback {

        /**
         * @param code 0: Success,
         *             -1: file not exist;
         *             -2: open file failed;
         *             -3: lock file failed;
         *             -4: other.
         * @param file null if code != 0.
         */
        void onLockFinish(int code, File file);

    }
}