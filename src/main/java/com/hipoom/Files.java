package com.hipoom;

import java.io.Closeable;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * @author ZhengHaiPeng
 * @since 4/18/24 00:15 PM
 */
@SuppressWarnings("unused")
public class Files {

    /* ======================================================= */
    /* Fields                                                  */
    /* ======================================================= */

    public static final int CODE_SUCCESS = 0;



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
        if (children == null || children.length == 0) {
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


    public static int rename(File source, File destination, DstFileExistPolicy policy) {
        if (!source.exists()) {
            return -1;
        }


    }


    /**
     * Close stream or other closeable obj quietly.
     *
     * @param closeable file stream or other closeable obj.
     */
    public static void closeQuietly(Closeable... closeable) {
        if (closeable == null || closeable.length == 0) {
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

    public static void main(String[] args) {
        int code = copy(new File("/Users/zhp/Downloads/AndroidScaffold/gradle"), new File("/Users/zhp/Downloads/AndroidScaffold/gradle-copy"), DstFileExistPolicy.Overwrite);
        System.out.println(code);
    }
}