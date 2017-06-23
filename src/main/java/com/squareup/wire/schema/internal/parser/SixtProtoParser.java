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

package com.squareup.wire.schema.internal.parser;

import com.squareup.wire.schema.Location;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;

// This class needed to be in the squareup package because of package-access stuff
public class SixtProtoParser {

    protected File input;
    protected String serviceName;

    public SixtProtoParser(File input, String serviceName) {
        this.input = input;
        this.serviceName = serviceName;
    }

    public List<RpcMethodDefinition> getRpcMethods() throws IOException {
        List<RpcMethodDefinition> retval = new ArrayList<>();
        ProtoParser parser = new ProtoParser(Location.get(input.getCanonicalPath()), gulpFile(input));
        ProtoFileElement element = parser.readProtoFile();
        if (protoFileMatchesPackage(element)) {
            List<ServiceElement> services = element.services();
            if (services != null) {
                for (ServiceElement service : services) {
                    String name = service.name();
                    List<RpcElement> rpcs = service.rpcs();
                    if (rpcs != null) {
                        for (RpcElement rpc : rpcs) {
                            RpcMethodDefinition def = new RpcMethodDefinition(name + "." + rpc.name(),
                                                                              rpc.requestType(), rpc.responseType()
                            );
                            retval.add(def);
                        }
                    }
                }
            }
        }
        return retval;
    }

    public boolean protoFileMatchesPackage(ProtoFileElement element) {
        String packageFound = element.packageName();
        if (packageFound == null) {
            List<OptionElement> options = element.options();
            if (options != null) {
                for (OptionElement option : options) {
                    if (option.name().equals("java_package")) {
                        if (option.value().toString().contains(serviceName)) {
                            System.out.println("Please update protofile for " + serviceName);
                            return true;
                        }
                    }
                }
            }
        } else {
            return matchesServiceName(packageFound);
        }
        return false;
    }

    boolean matchesServiceName(final String candidate) {
        return candidate.equals(serviceName) || candidate.matches("^" + serviceName.replace(".", "\\.") + "\\..+");
    }

    protected char[] gulpFile(File input) throws FileNotFoundException {
        StringBuilder sb = new StringBuilder();
        Scanner scanner = new Scanner(input);

        if (scanner.hasNextLine()) {
            sb.append(scanner.nextLine());
            while (scanner.hasNextLine()) {
                sb.append('\n');
                sb.append(scanner.nextLine());
            }
        }

        scanner.close();

        return sb.toString().toCharArray();
    }
}
