package com.sixt.service.framework.util;

import java.io.File;

public class FileUtil {

    public static String stripPath(String fileName) {
        if (fileName == null) {
            return "";
        }
        return fileName.substring(fileName.lastIndexOf(File.separatorChar)+1, fileName.length());
    }

    public static String stripExtension(String fileName) {
        if (fileName == null) {
            return "";
        }
        if (!fileName.contains(".")) {
            return fileName;
        }
        return fileName.substring(0, fileName.lastIndexOf('.'));
    }

}
