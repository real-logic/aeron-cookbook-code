package com.aeroncookbook.archive;

import java.io.File;
import java.io.IOException;

public class Utils
{
    public static File createTempDir()
    {
        final File tempDir;
        try
        {
            tempDir = File.createTempFile("archive", "tmp");
        } catch (IOException ex)
        {
            throw new RuntimeException(ex);
        }

        if (!tempDir.delete())
        {
            throw new IllegalStateException("Cannot delete tmp file!");
        }

        if (!tempDir.mkdir())
        {
            throw new IllegalStateException("Cannot create folder!");
        }

        return tempDir;
    }
}
