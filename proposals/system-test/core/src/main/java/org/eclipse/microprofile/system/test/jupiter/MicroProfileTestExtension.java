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
package org.eclipse.microprofile.system.test.jupiter;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.List;

import javax.inject.Inject;

import org.eclipse.microprofile.system.test.jaxrs.JAXRSUtilities;
import org.eclipse.microprofile.system.test.testcontainers.TestcontainersConfiguration;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.ExtensionConfigurationException;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.platform.commons.support.AnnotationSupport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * JUnit Jupiter extension that is applied whenever the <code>@MicroProfileTest</code> is used on a test class.
 * Currently this is tied to Testcontainers managing runtime build/deployment, but in a future version
 * it could be refactored to allow for a different framework managing the runtime build/deployment.
 */
public class MicroProfileTestExtension implements BeforeAllCallback {

    static final Logger LOGGER = LoggerFactory.getLogger(MicroProfileTestExtension.class);

    @Override
    public void beforeAll(ExtensionContext context) throws Exception {
        Class<?> testClass = context.getRequiredTestClass();
        // For now this is hard-coded to using Testcontainers for container management.
        // In the future, this could be configurable to something besides Testcontainers
        TestcontainersConfiguration config = new TestcontainersConfiguration(testClass);
        config.applyConfiguration();
        config.startContainers();
        injectRestClients(testClass, config);
    }

    private static void injectRestClients(Class<?> clazz, TestcontainersConfiguration config) throws Exception {
        List<Field> restClientFields = AnnotationSupport.findAnnotatedFields(clazz, Inject.class);
        if (restClientFields.size() == 0)
            return;

        String mpAppURL = config.getApplicationURL();

        for (Field restClientField : restClientFields) {
            if (!Modifier.isPublic(restClientField.getModifiers()) ||
                !Modifier.isStatic(restClientField.getModifiers()) ||
                Modifier.isFinal(restClientField.getModifiers())) {
                throw new ExtensionConfigurationException("REST-client field must be public, static, and non-final: " + restClientField.getName());
            }

            Object restClient = JAXRSUtilities.createRestClient(restClientField.getType(), mpAppURL);
            restClientField.set(null, restClient);
            LOGGER.debug("Injecting rest client for " + restClientField);
        }
    }
}
