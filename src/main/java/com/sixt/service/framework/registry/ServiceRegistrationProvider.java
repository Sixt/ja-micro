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

package com.sixt.service.framework.registry;

import com.sixt.service.framework.MethodHandlerDictionary;

public interface ServiceRegistrationProvider {

    /**
     * Called during service startup with a populated MethodHandlerDictionary.
     * This dictionary cannot be dynamically altered during runtime.  Typically,
     * the implementer should report the initial service state information here.
     */
    void initialize(MethodHandlerDictionary methodHandlers);

}
