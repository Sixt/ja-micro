package com.sixt.service.framework.kafka;

import org.apache.commons.lang3.StringUtils;

import java.util.Properties;

public class SaslConfigurator {

    public void configureSasl(Properties props, String username, String password) {
        if (StringUtils.isNotBlank(username) && StringUtils.isNotBlank(password)) {
            String jaasTemplate = "org.apache.kafka.common.security.plain.PlainLoginModule " +
                    "required username=\"%s\" password=\"%s\";";
            props.put("security.protocol", "SASL_PLAINTEXT");
            props.put("sasl.mechanism", "PLAIN");
            props.put("sasl.jaas.config", String.format(jaasTemplate, username, password));
        }
    }

}
