/*
 * Copyright (c) 2019 IBM Corporation and others
 *
 * See the NOTICE file(s) distributed with this work for additional
 * information regarding copyright ownership.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * You may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.eclipse.microprofile.system.test.jaxrs;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

import javax.ws.rs.ApplicationPath;
import javax.ws.rs.core.Application;

import org.apache.cxf.jaxrs.client.JAXRSClientFactory;
import org.junit.platform.commons.support.AnnotationSupport;
import org.junit.platform.commons.support.ReflectionSupport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class JAXRSUtilities {

    static final Logger LOGGER = LoggerFactory.getLogger(JAXRSUtilities.class);

    public static <T> T createRestClient(Class<T> clazz, String appContextRoot, String applicationPath) {
        Objects.requireNonNull(appContextRoot, "Supplied 'appContextRoot' must not be null");
        Objects.requireNonNull(applicationPath, "Supplied 'applicationPath' must not be null");
        String basePath = join(appContextRoot, applicationPath);
        // TODO: Allow the provider list to be customized
        List<Class<?>> providers = Collections.singletonList(JsonBProvider.class);
        LOGGER.info("Building rest client for " + clazz + " with base path: " + basePath + " and providers: " + providers);
        return JAXRSClientFactory.create(basePath, clazz, providers);
    }

    public static <T> T createRestClient(Class<T> clazz, String appContextRoot) {
        String resourcePackage = clazz.getPackage().getName();

        // First check for a javax.ws.rs.core.Application in the same package as the resource
        List<Class<?>> appClasses = ReflectionSupport.findAllClassesInPackage(resourcePackage,
                                                                              c -> Application.class.isAssignableFrom(c) &&
                                                                                   AnnotationSupport.isAnnotated(c, ApplicationPath.class),
                                                                              n -> true);
        if (appClasses.size() == 0) {
            // If not found, check under the 3rd package, so com.foo.bar.*
            // Classpath scanning can be expensive, so we jump straight to the 3rd package from root instead
            // of recursing up one package at a time and scanning the entire CP for each step
            String[] pkgs = resourcePackage.split("(.*)\\.(.*)\\.(.*)\\.", 2);
            if (pkgs.length > 0 && !pkgs[0].isEmpty() && !pkgs[0].equals(resourcePackage)) {
                appClasses = ReflectionSupport.findAllClassesInPackage(pkgs[0],
                                                                       c -> Application.class.isAssignableFrom(c) &&
                                                                            AnnotationSupport.isAnnotated(c, ApplicationPath.class),
                                                                       n -> true);
            }
        }

        if (appClasses.size() == 0) {
            LOGGER.info("No classes implementing 'javax.ws.rs.core.Application' found on classpath to set as context root for " + clazz +
                        ". Defaulting context root to '/'");
            return createRestClient(clazz, appContextRoot, "");
        }

        Class<?> selectedClass = appClasses.get(0);
        if (appClasses.size() > 1) {
            appClasses.sort((c1, c2) -> c1.getCanonicalName().compareTo(c2.getCanonicalName()));
            LOGGER.warn("Found multiple classes implementing 'javax.ws.rs.core.Application' on classpath: " + appClasses +
                        ". Setting context root to the first class discovered (" + selectedClass.getCanonicalName() + ")");
        }
        ApplicationPath appPath = AnnotationSupport.findAnnotation(selectedClass, ApplicationPath.class).get();
        return createRestClient(clazz, appContextRoot, appPath.value());
    }

    private static String join(String firstPart, String secondPart) {
        if (firstPart.endsWith("/") && secondPart.startsWith("/"))
            return firstPart + secondPart.substring(1);
        else if (firstPart.endsWith("/") || secondPart.startsWith("/"))
            return firstPart + secondPart;
        else
            return firstPart + "/" + secondPart;
    }

}
