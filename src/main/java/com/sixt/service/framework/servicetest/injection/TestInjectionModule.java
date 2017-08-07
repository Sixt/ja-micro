/**
 * Copyright 2016-2017 Sixt GmbH & Co. Autovermietung KG
 * Licensed under the Apache License, Version 2.0 (the "License"); you may 
 * not use this file except in compliance with the License. You may obtain a 
 * copy of the License at http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software 
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT 
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the 
 * License for the specific language governing permissions and limitations 
 * under the License.
 */

package com.sixt.service.framework.servicetest.injection;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.sixt.service.framework.MethodHandlerDictionary;
import com.sixt.service.framework.ServiceProperties;
import com.sixt.service.framework.rpc.LoadBalancer;
import com.sixt.service.framework.rpc.LoadBalancerImpl;
import org.eclipse.jetty.client.HttpClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.ServerSocket;
import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class TestInjectionModule extends AbstractModule {

	private static final Logger logger = LoggerFactory.getLogger(TestInjectionModule.class);

	private ServiceProperties serviceProperties = new ServiceProperties();
	private MethodHandlerDictionary methodHandlerDictionary = new MethodHandlerDictionary();
	private ServerSocket serverSocket;

	public TestInjectionModule(String serviceName, ServiceProperties props) {
		this.serviceProperties = props;
		if (props.getProperty("registry") == null) {
            serviceProperties.addProperty("registry", "consul");
        }
        if (props.getProperty("registryServer") == null) {
            serviceProperties.addProperty("registryServer", "localhost:8500");
        }
        if (props.getProperty("kafkaServer") == null) {
            serviceProperties.addProperty("kafkaServer", "localhost:9092");
        }
        serverSocket = getRandomPort();
		serviceProperties.initialize(new String[0]);
        serviceProperties.setServicePort(serverSocket.getLocalPort());
        serviceProperties.setServiceName(serviceName);
	}

	public TestInjectionModule(String serviceName) {
	    this(serviceName, new ServiceProperties());
	}

	@Override
	protected void configure() {
		bind(ServiceProperties.class).toInstance(serviceProperties);
		bind(ServerSocket.class).toInstance(serverSocket);
        bind(LoadBalancer.class).to(LoadBalancerImpl.class);
	}

	@Provides
	public MethodHandlerDictionary getMethodHandlers() {
		return methodHandlerDictionary;
	}

	@Provides
	public HttpClient getHttpClient() {
		HttpClient client = new HttpClient();
		client.setFollowRedirects(false);
		client.setMaxConnectionsPerDestination(32);
		client.setConnectTimeout(100);
		client.setAddressResolutionTimeout(100);
		//You can set more restrictive timeouts per request, but not less, so
		//  we set the maximum timeout of 1 hour here.
		client.setIdleTimeout(60 * 60 * 1000);
		try {
			client.start();
		} catch (Exception e) {
			logger.error("Error building http client", e);
		}
		return client;
	}

	@Provides
	public ExecutorService getExecutorService() {
		return Executors.newCachedThreadPool();
	}

	private ServerSocket getRandomPort() {
		Random rng = new Random();
		while (true) {
			int port = rng.nextInt(1000) + 42000;
			try {
				return new ServerSocket(port);
			} catch (IOException ex) {
				continue;
			}
		}
	}

	public ServiceProperties getServiceProperties() {
		return serviceProperties;
	}
}
