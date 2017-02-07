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

package com.sixt.service.framework.configuration;

/**
 * To provide configuration data in a way specific to your environment, implement
 * a class with the ConfigurationPlugin annotation and implement this interface.
 * Early in service startup, initialize() will be called, and you will have the
 * opportunity to do whatever you wish.  At Sixt, we have a dedicated microservice
 * to serve the configuration data, and long-polling is implemented to get
 * immediate updates when a configuration value is modified / added.
 */
public interface ConfigurationProvider {

    void initialize();

}
