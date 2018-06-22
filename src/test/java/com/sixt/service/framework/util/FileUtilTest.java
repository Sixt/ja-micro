package com.sixt.service.framework.util;

import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class FileUtilTest {

    @Test
    public void stripPath() {

        assertThat(FileUtil.stripPath("/usr/local/blah/blah/myfileName.txt")).isEqualTo("myfileName.txt");
        assertThat(FileUtil.stripPath("C:/Program\\ Files/Applications/SomeFile.app")).isEqualTo("SomeFile.app");
        assertThat(FileUtil.stripPath("SomeFile.app")).isEqualTo("SomeFile.app");
        assertThat(FileUtil.stripPath("/home/jbloggs/SomeFile.longfileextension")).isEqualTo("SomeFile.longfileextension");
        assertThat(FileUtil.stripPath("")).isEqualTo("");
        assertThat(FileUtil.stripPath("  ")).isEqualTo("  ");
        assertThat(FileUtil.stripPath(null)).isEqualTo("");

    }

    @Test
    public void stripExtension() {

        assertThat(FileUtil.stripExtension("/usr/local/blah/blah/myfileName.txt")).isEqualTo("/usr/local/blah/blah/myfileName");
        assertThat(FileUtil.stripExtension("C:/Program\\ Files/Applications/SomeFile.app")).isEqualTo("C:/Program\\ Files/Applications/SomeFile");
        assertThat(FileUtil.stripExtension("SomeFile.app")).isEqualTo("SomeFile");
        assertThat(FileUtil.stripExtension("/home/jbloggs/SomeFile.longfileextension")).isEqualTo("/home/jbloggs/SomeFile");
        assertThat(FileUtil.stripExtension("")).isEqualTo("");
        assertThat(FileUtil.stripExtension("  ")).isEqualTo("  ");
        assertThat(FileUtil.stripExtension(null)).isEqualTo("");

    }

}