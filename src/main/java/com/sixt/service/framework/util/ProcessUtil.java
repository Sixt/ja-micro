package com.sixt.service.framework.util;

import org.apache.commons.io.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.Charset;
import java.util.List;

public class ProcessUtil {

    private static final Logger logger = LoggerFactory.getLogger(ProcessUtil.class);

    public String runProcess(List<String> command) {
        String output = null;
        String debug = String.join(" ", command);
        logger.debug("Running command {}", debug);
        ProcessBuilder builder = new ProcessBuilder(command);
        try {
            builder.redirectErrorStream(true);
            Process process = builder.start();
            process.waitFor();
            output = IOUtils.toString(process.getInputStream(), Charset.defaultCharset());
            logger.debug("Command returned: {}", output);
        } catch (Exception e) {
            logger.warn("Caught exception running command", e);
        }
        return output;
    }

}
