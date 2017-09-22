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

package com.sixt.service.framework.util;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;

public class ReflectionUtil {

    //hacky and dirty, augment if you find the need...
    public static Class<?> findSubClassParameterType(Object instance, int parameterIndex)
            throws ClassNotFoundException {

        if (instance instanceof MockMethodHandler) {
            if (parameterIndex == 0) {
                return ((MockMethodHandler) instance).getRequestType();
            } else {
                return ((MockMethodHandler) instance).getResponseType();
            }
        }

        Class<?> clazz = instance.getClass();
        Type[] genericInterfaces = clazz.getGenericInterfaces();

        if (genericInterfaces.length > 0) {
            return retrieveGenericParameterTypes(genericInterfaces, parameterIndex);
        } else if (clazz.getSuperclass() != null) {
            return retrieveGenericParameterTypes(
                clazz.getSuperclass().getGenericInterfaces(),
                parameterIndex
            );
        } else {
            return null;
        }
    }

    private static Class<?> retrieveGenericParameterTypes(
        final Type[] genericInterfaces,
        final Integer parameterIndex
    ) throws ClassNotFoundException {
        int count = 0;
        for (Type genericInterface : genericInterfaces) {
            if (genericInterface instanceof ParameterizedType) {
                Type[] genericTypes = ((ParameterizedType) genericInterface).getActualTypeArguments();
                for (Type genericType : genericTypes) {
                    if (parameterIndex == count) {
                        return Class.forName(genericType.getTypeName());
                    } else {
                        count++;
                    }
                }
            }
        }

        return null;
    }

}
