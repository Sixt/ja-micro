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

import com.google.protobuf.GeneratedMessage;
import com.google.protobuf.GeneratedMessageV3;
import com.google.protobuf.Message;
import com.sixt.service.framework.protobuf.ProtobufUtil;
import com.sixt.service.framework.rpc.RpcClient;
import com.sixt.service.framework.rpc.RpcClientFactory;
import com.sixt.service.framework.servicetest.service.ServiceMethod;
import io.github.lukehutch.fastclasspathscanner.FastClasspathScanner;
import io.github.lukehutch.fastclasspathscanner.scanner.ScanResult;
import org.apache.commons.io.FileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipInputStream;

public class RpcMethodScanner {

    private static final Logger logger = LoggerFactory.getLogger(RpcMethodScanner.class);

    private RpcClientFactory rpcClientFactory;

    public RpcMethodScanner(RpcClientFactory rpcClientFactory) {
        this.rpcClientFactory = rpcClientFactory;
    }

    @SuppressWarnings("unchecked")
    public Map<String, ServiceMethod<Message>> getMethodHandlers(String serviceName) {
        String serviceNameUnderscores = serviceName.replace("-", "_");

        List<RpcMethodDefinition> rpcMethodDefinitions = getRpcMethodDefinitions(serviceNameUnderscores);

        // get the generated proto classes
        List<String> protoClasses = getGeneratedProtoClasses(serviceNameUnderscores);

        Map<String, ServiceMethod<Message>> serviceMethods = new HashMap<>();
        for (RpcMethodDefinition def : rpcMethodDefinitions) {
            try {
                if (serviceMethods.containsKey(def.getMethodName())) {
                    continue;
                }

                logger.info("Adding endpoint {} for {}", def.getMethodName(), serviceName);

                Class<? extends Message> protobufClass = findProtobufClass(protoClasses, def, def.getResponseType());
                RpcClient rpcClient = rpcClientFactory.newClient(serviceName,
                        def.getMethodName(), protobufClass).build();
                serviceMethods.put(def.getMethodName(), new ServiceMethod(rpcClient, protobufClass));
            } catch (ClassNotFoundException ex) {
                logger.error("Error while adding RPC endpoint for service " + serviceName, ex);
            }
        }
        return serviceMethods;
    }

    public List<RpcMethodDefinition> getRpcMethodDefinitions(String serviceName) {
        // first try: search classpath
        List<RpcMethodDefinition> rpcMethodDefinitions = searchClasspath(serviceName);

        // if rpcMethodDefinitions still empty, : search local directory for proto file
        if (rpcMethodDefinitions.isEmpty()) {
            rpcMethodDefinitions = searchDirectory(System.getProperty("user.dir"), serviceName);
        }

        if (rpcMethodDefinitions.isEmpty()) {
            logger.warn("No RPC endpoints found for {}", serviceName);
        }

        return rpcMethodDefinitions;
    }

    public List<String> getGeneratedProtoClasses(String serviceName) {
        FastClasspathScanner cpScanner = new FastClasspathScanner();
        ScanResult scanResult = cpScanner.scan();
        List<String> oldProtobuf = scanResult.getNamesOfSubclassesOf(GeneratedMessage.class);
        List<String> newProtobuf = scanResult.getNamesOfSubclassesOf(GeneratedMessageV3.class);
        List<String> retval = Stream.concat(oldProtobuf.stream(),
                newProtobuf.stream()).collect(Collectors.toList());
        String[] packageTokens = serviceName.split("\\.");
        return retval.stream().filter(s -> protoFilePackageMatches(s, packageTokens)).collect(Collectors.toList());
    }

    //has to roughly match.  com.example.api.foo matches com.example.foo
    protected boolean protoFilePackageMatches(String protoClass, String[] packageTokens) {
        int index = 0;
        for (int i = 0; i < packageTokens.length; i++) {
            index = protoClass.indexOf(packageTokens[i], index);
            if (index == -1) {
                return false;
            }
        }
        return true;
    }

    @SuppressWarnings("unchecked")
    public Class<? extends Message> findProtobufClass(List<String> protoClasses, RpcMethodDefinition def, String typeName)
            throws ClassNotFoundException {

        Set<String> matched = new HashSet<>();

        for (String pbClass : protoClasses) {
            if (pbClass.endsWith("." + typeName) || pbClass.endsWith("$" + typeName)) {
                matched.add(pbClass);
            }
        }
        if (matched.size() == 0) {
            return null;
        } else if (matched.size() == 1) {
            return (Class<? extends Message>) Class.forName(matched.iterator().next());
        }

        // There was some ambiguity. This can happen if types exists with the same name in different proto files.
        // First resolution strategy: try to match the package names
        Set<String> matchedOnPackageName = matched.stream().filter(m -> m.contains(def.getPackageName())).collect(Collectors.toSet());
        if (matchedOnPackageName.size() == 1) {
            return (Class<? extends Message>) Class.forName(matchedOnPackageName.iterator().next());
        }

        // Second resolution strategy: It can still happen that two types exist within the same package, but with different
        // "Outer classes". These outer classes are generated by the the rules outlined here:
        // https://developers.google.com/protocol-buffers/docs/reference/java-generated
        // Firstly, the file name will be converted into a classname. If it conflicts with an existing type, an OuterClass
        // suffix is added.
        // It is therefore sufficient for us to search for the class name generated from the proto file name (we'll match
        // it whether or not the "OuterClass" suffix is added).
        // NOTE: We cannot resolve this if the Outer Class name is overwritten manually within the proto file.
        String outerClassName = ProtobufUtil.toClassName(def.getSourceFile().getName());
        if (outerClassName != null) {
            Set<String> matchedOnFileName = matched.stream().filter(m -> m.contains(outerClassName)).collect(Collectors.toSet());
            if (matchedOnFileName.size() == 1) {
                return (Class<? extends Message>) Class.forName(matchedOnFileName.iterator().next());
            }
        }

        // Giving up
        throw new AmbiguousTypeException("The type name: " + typeName + " could not be resolved to a class. " +
                "The following candidates exist: " + matched + ". " +
                "Please disambiguate by either a) Ensuring unique type names, b) Ensuring unique package names " +
                "c) Ensuring unique protobuf file names");
    }

    private List<RpcMethodDefinition> searchClasspath(String serviceName) {

        logger.info("Scanning classpath for proto files of {}", serviceName);

        List<RpcMethodDefinition> rpcMethodDefinitions = new ArrayList<>();
        String classpath = System.getProperty("java.class.path");
        String jars[] = classpath.split(":");
        for (String jar : jars) {
            try {
                rpcMethodDefinitions.addAll(searchJar(jar, serviceName));
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        return rpcMethodDefinitions;
    }

    private List<RpcMethodDefinition> searchDirectory(String directory, String serviceName) {

        logger.info("Scanning {} for proto files of {}", directory, serviceName);

        List<RpcMethodDefinition> rpcMethodDefinitions = new ArrayList<>();

        File scanDir = new File(directory);

        List<File> protoFiles = (List<File>) FileUtils.listFiles(scanDir, new String[]{"proto"}, true);

        // if no proto files have been found also scan the parent directory. we need this for the go services
        if (protoFiles.isEmpty()) {
            protoFiles = (List<File>) FileUtils.listFiles(scanDir.getParentFile(), new String[]{"proto"}, true);
        }

        for (File f : protoFiles) {
            try {
                rpcMethodDefinitions.addAll(inspectProtoFile(new FileInputStream(f), serviceName));
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        return rpcMethodDefinitions;
    }

    private List<RpcMethodDefinition> searchJar(String jarpath, String serviceName) throws IOException {

        List<RpcMethodDefinition> defs = new ArrayList<>();

        File jarFile = new File(jarpath);
        if (!jarFile.exists() || jarFile.isDirectory()) {
            return defs;
        }

        ZipInputStream zip = new ZipInputStream(new FileInputStream(jarFile));
        ZipEntry ze;

        while ((ze = zip.getNextEntry()) != null) {
            String entryName = ze.getName();
            if (entryName.endsWith(".proto")) {
                ZipFile zipFile = new ZipFile(jarFile);
                try (InputStream in = zipFile.getInputStream(ze)) {
                    defs.addAll(inspectProtoFile(in, serviceName));
                }
                zipFile.close();
            }
        }

        zip.close();

        return defs;
    }

    private List<RpcMethodDefinition> inspectProtoFile(InputStream instream, String serviceName) throws IOException {
        File tempFile = File.createTempFile("proto", null);
        tempFile.delete();
        Files.copy(instream, tempFile.toPath());
        SixtProtoParser parser = new SixtProtoParser(tempFile, serviceName);
        List<RpcMethodDefinition> methodDefinitions = parser.getRpcMethods();
        tempFile.delete();
        instream.close();
        return methodDefinitions;
    }

    public static class AmbiguousTypeException extends RuntimeException {
        AmbiguousTypeException(String message) {
            super(message);
        }
    }
}
