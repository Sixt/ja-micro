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

package com.sixt.service.framework;

import com.google.inject.Module;
import org.junit.Test;

import java.io.PrintStream;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

public class AbstractServiceTest_initializeGuice {

    @Test
    public void initializeGuice_ProvideImmutableListViaExtendingService_NoProblem() throws Exception {
        // given
        final AbstractService service = new AbstractService() {
            @Override
            public void displayHelp(PrintStream out) {

            }

            @Override
            protected List<Module> getGuiceModules() {
                return Collections.emptyList();
            }
        };

        // when
        service.initializeGuice();

        // then
        // no exception should be raised
        assertThat(service.injector).isNotNull();
    }

    @Test
    public void initializeGuice_ProvideNullListViaExtendingService_NoProblem() throws Exception {
        // given
        final AbstractService service = new AbstractService() {
            @Override
            public void displayHelp(PrintStream out) {

            }

            @Override
            protected List<Module> getGuiceModules() {
                return null;
            }
        };

        // when
        service.initializeGuice();

        // then
        // no exception should be raised
        assertThat(service.injector).isNotNull();
    }

}